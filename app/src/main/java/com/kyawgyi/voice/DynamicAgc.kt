package com.kyawgyi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * DynamicAgc (Requirements 10 — Noise-Gated Software Automatic Gain Control & Soft Limiter)
 *
 * Normalizes far-field and near-field microphone levels toward a target speech RMS (~4200 / -18 dBFS)
 * with:
 * - **Noise-Floor Gate**: Frames below [noiseGateRmsFloor] are NOT amplified, preventing background
 *   hiss from being boosted during silence.
 * - **Fast Attack / Slow Release**: Rapidly lowers gain on sudden loud peaks to prevent clipping,
 *   and smoothly increases gain on quiet far-field speech.
 */
class DynamicAgc(
    val targetRms: Double = 4200.0,
    val noiseGateRmsFloor: Double = 180.0,
    val minGain: Float = 0.5f,
    val maxGain: Float = 3.2f,
    private val attackAlpha: Float = 0.35f,
    private val releaseAlpha: Float = 0.08f
) {

    @Volatile
    var currentGain: Float = 1.0f
        private set

    /**
     * Computes updated AGC gain for [pcmBytes] and returns a gain-normalized 16-bit PCM array.
     */
    @Synchronized
    fun processPcm16Le(pcmBytes: ByteArray): ByteArray {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount <= 0) return pcmBytes

        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        var sumSq = 0.0
        var peakAbs = 1
        for (s in shorts) {
            val v = s.toInt()
            sumSq += v.toDouble() * v.toDouble()
            val a = abs(v)
            if (a > peakAbs) peakAbs = a
        }
        val frameRms = sqrt(sumSq / sampleCount)

        if (frameRms >= noiseGateRmsFloor) {
            val desiredGain = (targetRms / frameRms.coerceAtLeast(1.0)).toFloat().coerceIn(minGain, maxGain)
            // Prevent clipping on peak sample
            val headroomMaxGain = (29000f / peakAbs.toFloat()).coerceAtLeast(minGain)
            val safeDesiredGain = minOf(desiredGain, headroomMaxGain).coerceIn(minGain, maxGain)

            val alpha = if (safeDesiredGain < currentGain) attackAlpha else releaseAlpha
            currentGain += alpha * (safeDesiredGain - currentGain)
        } else {
            // Gently decay gain back toward unity during silence
            currentGain += releaseAlpha * (1.0f - currentGain)
        }

        if (abs(currentGain - 1.0f) < 0.03f) {
            return pcmBytes
        }

        val outBuf = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in shorts) {
            val scaled = (s * currentGain).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            outBuf.putShort(scaled)
        }
        return outBuf.array()
    }

    @Synchronized
    fun reset() {
        currentGain = 1.0f
    }
}
