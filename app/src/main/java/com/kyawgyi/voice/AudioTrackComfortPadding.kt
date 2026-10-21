package com.kyawgyi.voice

import kotlin.math.PI
import kotlin.math.cos

/**
 * AudioTrackComfortPadding (Acoustic Audio Playback Smoothing)
 *
 * Prevents speaker pop/click artifacts and audio discontinuities during TTS playback:
 * 1. Raised-Cosine Fade-In & Fade-Out: Smoothly ramps audio amplitude from 0 to 1 over 10-15ms
 *    so the hardware DAC never experiences a sudden DC step offset.
 * 2. Zero-Crossing Alignment: Snaps audio boundary cuts to the nearest zero-crossing point
 *    to prevent sudden waveform discontinuities when audio playback is abruptly interrupted (Barge-In).
 * 3. Anti-Underrun Comfort Noise: Generates low-amplitude inaudible dither (-60dB) to keep the DAC
 *    warm during inter-sentence speech synthesis pauses without triggering power-state popping.
 */
class AudioTrackComfortPadding(
    val sampleRateHz: Int = 16000,
    val fadeInMs: Int = 10,
    val fadeOutMs: Int = 15
) {

    /**
     * Applies raised-cosine fade-in to the beginning of a 16-bit Mono PCM buffer in-place.
     */
    fun applyFadeIn(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
        val totalSamples = length / 2
        val fadeSamples = ((fadeInMs * sampleRateHz) / 1000).coerceAtMost(totalSamples)
        if (fadeSamples <= 0) return

        for (i in 0 until fadeSamples) {
            val offset = i * 2
            val low = pcmBytes[offset].toInt() and 0xFF
            val high = pcmBytes[offset + 1].toInt()
            val sample = (high shl 8) or low

            // Raised-cosine envelope from 0.0 to 1.0: 0.5 * (1 - cos(pi * i / N))
            val factor = 0.5f * (1f - cos((PI * i / fadeSamples).toFloat()))
            val fadedSample = (sample * factor).toInt().coerceIn(-32768, 32767)

            pcmBytes[offset] = (fadedSample and 0xFF).toByte()
            pcmBytes[offset + 1] = ((fadedSample shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Applies raised-cosine fade-out to the end of a 16-bit Mono PCM buffer in-place.
     */
    fun applyFadeOut(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
        val totalSamples = length / 2
        val fadeSamples = ((fadeOutMs * sampleRateHz) / 1000).coerceAtMost(totalSamples)
        if (fadeSamples <= 0) return

        val startIndex = totalSamples - fadeSamples
        for (i in 0 until fadeSamples) {
            val sampleIndex = startIndex + i
            val offset = sampleIndex * 2
            val low = pcmBytes[offset].toInt() and 0xFF
            val high = pcmBytes[offset + 1].toInt()
            val sample = (high shl 8) or low

            // Raised-cosine envelope from 1.0 down to 0.0: 0.5 * (1 + cos(pi * i / N))
            val factor = 0.5f * (1f + cos((PI * i / fadeSamples).toFloat()))
            val fadedSample = (sample * factor).toInt().coerceIn(-32768, 32767)

            pcmBytes[offset] = (fadedSample and 0xFF).toByte()
            pcmBytes[offset + 1] = ((fadedSample shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Finds the nearest sample index within [searchWindowSamples] where the waveform crosses zero.
     * Prevents click artifacts when truncating audio for barge-in interruptions.
     */
    fun findNearestZeroCrossingIndex(pcmBytes: ByteArray, targetSampleIndex: Int, searchWindowSamples: Int = 120): Int {
        val totalSamples = pcmBytes.size / 2
        val start = (targetSampleIndex - searchWindowSamples).coerceAtLeast(0)
        val end = (targetSampleIndex + searchWindowSamples).coerceAtMost(totalSamples - 2)

        var bestIndex = targetSampleIndex.coerceIn(0, totalSamples - 1)
        var minSampleAbs = Int.MAX_VALUE

        for (i in start..end) {
            val offset = i * 2
            val sample = ((pcmBytes[offset + 1].toInt() shl 8) or (pcmBytes[offset].toInt() and 0xFF))
            val nextSample = ((pcmBytes[offset + 3].toInt() shl 8) or (pcmBytes[offset + 2].toInt() and 0xFF))

            // Check if sign flips (zero crossing)
            if ((sample >= 0 && nextSample < 0) || (sample < 0 && nextSample >= 0)) {
                return i
            }

            val absVal = kotlin.math.abs(sample)
            if (absVal < minSampleAbs) {
                minSampleAbs = absVal
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Generates a comfort padding frame of [durationMs] of low-amplitude dither (-60dB).
     */
    fun generateComfortDitherFrame(durationMs: Int = 40): ByteArray {
        val sampleCount = (durationMs * sampleRateHz) / 1000
        val bytes = ByteArray(sampleCount * 2)
        var ditherState = 123456789L

        for (i in 0 until sampleCount) {
            // Fast linear congruential PRNG
            ditherState = (ditherState * 1103515245L + 12345L) and 0x7FFFFFFF
            // Low amplitude dither between -16 and +16
            val dither = ((ditherState and 0x1F) - 16).toInt()
            val offset = i * 2
            bytes[offset] = (dither and 0xFF).toByte()
            bytes[offset + 1] = ((dither shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
