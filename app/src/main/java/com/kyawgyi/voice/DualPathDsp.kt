package com.kyawgyi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dual-path processed frame containing separate tailored signals for:
 * 1. [vadPathPcmBytes] / [vadRmsEnergy]: High-pass & noise-gated signal for crisp VAD / Wake-Word detection.
 * 2. [asrPathPcmBytes] / [asrNormalizedFloats]: DC-blocked, tonal-preserving linear signal for Sherpa-ONNX / Vosk.
 */
data class DualPathDspFrame(
    val vadPathPcmBytes: ByteArray,
    val asrPathPcmBytes: ByteArray,
    val asrNormalizedFloats: FloatArray,
    val vadRmsEnergy: Double,
    val asrRmsEnergy: Double,
    val dcOffsetRemoved: Float
)

/**
 * DualPathDsp (Requirements 10 — Split VAD/Wake-Word & Neural ASR Digital Signal Processor)
 *
 * Separates microphone conditioning into two specialized signal paths:
 * - **Path 1 (VAD & Wake-Word Path)**: Applies a 1st-order 85Hz high-pass filter (removes fan/wind/AC hum)
 *   and soft noise-floor gating so VAD onset/offset decisions are immune to low-frequency rumble.
 * - **Path 2 (Neural ASR Path)**: Applies only DC-offset removal and gentle dynamic gain conditioning
 *   without aggressive spectral subtraction, preserving Burmese creaky/high tones and nasal finals
 *   for Sherpa-ONNX Zipformer and Vosk.
 */
class DualPathDsp(
    private val sampleRateHz: Int = 16000,
    private val noiseGateThresholdShort: Int = 95
) {

    // High-pass filter state for VAD path (~85Hz cutoff at 16kHz -> alpha ~ 0.967)
    private val hpAlpha = 0.967f
    private var prevRawSample = 0f
    private var prevHpSample = 0f

    /**
     * Processes a 16kHz 16-bit Little-Endian Mono PCM frame into split VAD and ASR paths.
     */
    @Synchronized
    fun processFrame(
        pcmBytes: ByteArray,
        agcGainMultiplier: Float = 1.0f
    ): DualPathDspFrame {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount <= 0) {
            return DualPathDspFrame(
                vadPathPcmBytes = pcmBytes,
                asrPathPcmBytes = pcmBytes,
                asrNormalizedFloats = FloatArray(0),
                vadRmsEnergy = 0.0,
                asrRmsEnergy = 0.0,
                dcOffsetRemoved = 0f
            )
        }

        val inShorts = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inShorts)

        // Compute mean DC offset across the frame
        var meanSum = 0.0
        for (s in inShorts) {
            meanSum += s.toDouble()
        }
        val dcOffset = (meanSum / sampleCount).toFloat()

        val vadOutBuffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        val asrOutBuffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        val asrFloats = FloatArray(sampleCount)

        var vadSumSq = 0.0
        var asrSumSq = 0.0
        val effectiveGain = agcGainMultiplier.coerceIn(0.25f, 4.0f)

        for (i in 0 until sampleCount) {
            val rawCentered = inShorts[i].toFloat() - dcOffset

            // Path 1: High-Pass Filter + Soft Noise Gate for VAD
            val hp = hpAlpha * (prevHpSample + rawCentered - prevRawSample)
            prevRawSample = rawCentered
            prevHpSample = hp

            val gatedVad = if (abs(hp) < noiseGateThresholdShort) hp * 0.25f else hp
            val vadShort = gatedVad.roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            vadOutBuffer.putShort(vadShort)
            vadSumSq += vadShort.toDouble() * vadShort.toDouble()

            // Path 2: Tonal-Preserving DC-centered + AGC scaled signal for Neural ASR
            val asrScaled = rawCentered * effectiveGain
            val asrShort = asrScaled.roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            asrOutBuffer.putShort(asrShort)
            asrFloats[i] = (asrShort / 32768.0f).coerceIn(-1.0f, 1.0f)
            asrSumSq += asrShort.toDouble() * asrShort.toDouble()
        }

        return DualPathDspFrame(
            vadPathPcmBytes = vadOutBuffer.array(),
            asrPathPcmBytes = asrOutBuffer.array(),
            asrNormalizedFloats = asrFloats,
            vadRmsEnergy = sqrt(vadSumSq / sampleCount),
            asrRmsEnergy = sqrt(asrSumSq / sampleCount),
            dcOffsetRemoved = dcOffset
        )
    }

    @Synchronized
    fun reset() {
        prevRawSample = 0f
        prevHpSample = 0f
    }
}
