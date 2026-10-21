package com.kyawgyi.voice

import kotlin.math.abs
import kotlin.math.tanh

/**
 * AudioClippingLimiter (Advanced Acoustic DSP)
 *
 * Real-time zero-allocation 16-bit Mono PCM soft-knee limiter and anti-clipping protector:
 * 1. Inspects raw audio samples for saturation (clipping >= 32000 or <= -32000).
 * 2. When peak amplitude exceeds [kneeThreshold], applies a continuous hyperbolic tangent (tanh)
 *    soft-limiting curve so loud or close-to-mic speech maintains harmonic fidelity.
 * 3. Prevents ASR/STT decoder hallucinations caused by harmonic distortion when users shout
 *    or speak in noisy, high-gain environments.
 */
class AudioClippingLimiter(
    val kneeThreshold: Float = 0.82f, // Soft limiting kicks in at ~82% full scale
    val hardLimit: Float = 0.98f      // Never exceed 98% of int16 full scale
) {

    data class LimiterMetrics(
        val peakAmplitudeRatio: Float = 0f,
        val clippedSamplesCount: Int = 0,
        val wasLimited: Boolean = false,
        val attenuationDb: Float = 0f
    )

    private var totalClippedSamples = 0L
    private var totalFramesProcessed = 0L

    /**
     * Applies soft limiting in-place to [pcm16] bytes (little-endian 16-bit PCM).
     * Returns acoustic diagnostic metrics for telemetry.
     */
    fun processPcm16LeInPlace(pcm16: ByteArray, length: Int = pcm16.size): LimiterMetrics {
        if (length < 2) return LimiterMetrics()

        var peakRaw = 0
        var clippedCount = 0
        var limitedCount = 0

        val sampleCount = length / 2
        val kneeInt = (kneeThreshold * 32767f).toInt()
        val hardLimitInt = (hardLimit * 32767f).toInt()

        for (i in 0 until sampleCount) {
            val offset = i * 2
            val low = pcm16[offset].toInt() and 0xFF
            val high = pcm16[offset + 1].toInt()
            val sample = (high shl 8) or low

            val absSample = abs(sample)
            if (absSample > peakRaw) {
                peakRaw = absSample
            }

            if (absSample >= 32500) {
                clippedCount++
            }

            // Apply soft-knee compression if exceeding knee threshold
            if (absSample > kneeInt) {
                limitedCount++
                val sign = if (sample >= 0) 1f else -1f
                val normalized = absSample / 32767f
                // Smooth hyperbolic tangent soft limiter curve
                val excess = (normalized - kneeThreshold) / (1f - kneeThreshold)
                val compressedExcess = tanh(excess.toDouble()).toFloat() * (hardLimit - kneeThreshold)
                val newNormalized = (kneeThreshold + compressedExcess).coerceIn(0f, hardLimit)
                val newSample = (sign * newNormalized * 32767f).toInt().coerceIn(-32768, 32767)

                pcm16[offset] = (newSample and 0xFF).toByte()
                pcm16[offset + 1] = ((newSample shr 8) and 0xFF).toByte()
            }
        }

        totalClippedSamples += clippedCount
        totalFramesProcessed++

        val peakRatio = (peakRaw / 32767f).coerceIn(0f, 1f)
        val wasLimited = limitedCount > 0

        return LimiterMetrics(
            peakAmplitudeRatio = peakRatio,
            clippedSamplesCount = clippedCount,
            wasLimited = wasLimited,
            attenuationDb = if (wasLimited) 20f * kotlin.math.log10(peakRatio.coerceAtLeast(0.001f)) else 0f
        )
    }

    fun getTotalClippedSamples(): Long = totalClippedSamples
    fun getTotalFramesProcessed(): Long = totalFramesProcessed

    fun reset() {
        totalClippedSamples = 0L
        totalFramesProcessed = 0L
    }
}
