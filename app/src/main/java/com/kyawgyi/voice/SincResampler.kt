package com.kyawgyi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * SincResampler (Requirements 9 — Windowed-Sinc Anti-Aliasing Audio Resampler)
 *
 * Resamples arbitrary input sample rates (e.g. 8kHz Bluetooth SCO, 44.1kHz, 48kHz USB/Wired Headset)
 * cleanly to 16kHz 16-bit Little-Endian Mono PCM or normalized FloatArray [-1.0f, 1.0f]
 * for Sherpa-ONNX Zipformer and Vosk STT engines.
 *
 * Uses a Blackman-Harris windowed Sinc kernel with automatic low-pass cutoff scaling
 * during decimation (downsampling) to prevent aliasing artifacts in Burmese sibilants and tones.
 */
class SincResampler(
    private val halfWindowLobes: Int = 8
) {

    companion object {
        const val TARGET_SAMPLE_RATE_HZ = 16000
    }

    /**
     * Resamples 16-bit Little-Endian Mono PCM bytes from [inputSampleRateHz] to [outputSampleRateHz].
     * Fast-paths with zero copy overhead when [inputSampleRateHz] == [outputSampleRateHz].
     */
    fun resamplePcm16Le(
        pcmBytes: ByteArray,
        inputSampleRateHz: Int,
        outputSampleRateHz: Int = TARGET_SAMPLE_RATE_HZ
    ): ByteArray {
        if (pcmBytes.isEmpty() || inputSampleRateHz <= 0 || outputSampleRateHz <= 0) return pcmBytes
        if (inputSampleRateHz == outputSampleRateHz) return pcmBytes

        val sampleCount = pcmBytes.size / 2
        if (sampleCount <= 1) return pcmBytes

        val inShorts = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inShorts)

        val inFloats = FloatArray(sampleCount) { idx ->
            (inShorts[idx] / 32768.0f).coerceIn(-1.0f, 1.0f)
        }

        val outFloats = resampleFloatWaveform(
            inputSamples = inFloats,
            inputSampleRateHz = inputSampleRateHz,
            outputSampleRateHz = outputSampleRateHz
        )

        val outBuffer = ByteBuffer.allocate(outFloats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in outFloats) {
            val s = (sample * 32767.0f).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            outBuffer.putShort(s)
        }
        return outBuffer.array()
    }

    /**
     * Resamples normalized [-1.0f, 1.0f] float audio waveform from [inputSampleRateHz] to [outputSampleRateHz].
     */
    fun resampleFloatWaveform(
        inputSamples: FloatArray,
        inputSampleRateHz: Int,
        outputSampleRateHz: Int = TARGET_SAMPLE_RATE_HZ
    ): FloatArray {
        if (inputSamples.isEmpty() || inputSampleRateHz <= 0 || outputSampleRateHz <= 0) return inputSamples
        if (inputSampleRateHz == outputSampleRateHz) return inputSamples

        val ratio = outputSampleRateHz.toDouble() / inputSampleRateHz.toDouble()
        val outputLength = (inputSamples.size * ratio).roundToInt().coerceAtLeast(1)
        val output = FloatArray(outputLength)

        // Anti-aliasing low-pass cutoff scale (< 1.0 when downsampling, 1.0 when upsampling)
        val cutoffScale = min(1.0, ratio)
        val filterRadius = (halfWindowLobes / cutoffScale).toInt().coerceAtLeast(halfWindowLobes)

        for (outIdx in 0 until outputLength) {
            val srcCenter = outIdx / ratio
            val centerIndex = floor(srcCenter).toInt()

            var acc = 0.0
            var weightSum = 0.0

            val startIdx = (centerIndex - filterRadius + 1).coerceAtLeast(0)
            val endIdx = (centerIndex + filterRadius).coerceAtMost(inputSamples.size - 1)

            for (inIdx in startIdx..endIdx) {
                val delta = srcCenter - inIdx
                val scaledDelta = delta * cutoffScale
                val sincVal = normalizedSinc(scaledDelta)
                val windowPos = (delta / filterRadius.toDouble()).coerceIn(-1.0, 1.0)
                val windowVal = blackmanHarrisWindow(windowPos)
                val weight = sincVal * windowVal * cutoffScale

                acc += inputSamples[inIdx] * weight
                weightSum += weight
            }

            val normalizedSample = if (weightSum > 1e-9) (acc / weightSum) else 0.0
            output[outIdx] = normalizedSample.toFloat().coerceIn(-1.0f, 1.0f)
        }

        return output
    }

    private fun normalizedSinc(x: Double): Double {
        if (kotlin.math.abs(x) < 1e-7) return 1.0
        val pix = PI * x
        return sin(pix) / pix
    }

    /**
     * 4-term Blackman-Harris window over normalized interval [-1.0, 1.0].
     */
    private fun blackmanHarrisWindow(normPos: Double): Double {
        val phase = PI * (normPos + 1.0) // maps [-1, 1] -> [0, 2*PI]
        return 0.35875 -
            0.48829 * cos(phase) +
            0.14128 * cos(2.0 * phase) -
            0.01168 * cos(3.0 * phase)
    }
}
