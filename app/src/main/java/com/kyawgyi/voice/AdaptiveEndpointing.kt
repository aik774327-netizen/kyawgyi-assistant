package com.kyawgyi.voice

import kotlin.math.sqrt

/**
 * Lightweight endpoint detector for already-framed 16 kHz PCM.
 * Feed each PCM chunk before/alongside the ASR decoder.
 */
class AdaptiveEndpointing(
    private val sampleRate: Int = 16_000,
    private val minSilenceMs: Long = 1_200,
    private val maxSilenceMs: Long = 1_500,
    private val energyThreshold: Float = 0.012f,
    private val minimumSpeechMs: Long = 220,
) {
    private var speechStartedAt = 0L
    private var lastSpeechAt = 0L
    private var silenceStartedAt = 0L

    fun reset() {
        speechStartedAt = 0
        lastSpeechAt = 0
        silenceStartedAt = 0
    }

    /** Returns true exactly when the current utterance should be finalized. */
    fun accept(pcm: FloatArray, nowMs: Long = System.currentTimeMillis()): Boolean {
        val rms = if (pcm.isEmpty()) 0f else sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size).toFloat()
        val voiced = rms >= energyThreshold
        val frameMs = (pcm.size * 1_000L / sampleRate).coerceAtLeast(1L)
        if (voiced) {
            if (speechStartedAt == 0L) speechStartedAt = nowMs
            lastSpeechAt = nowMs
            silenceStartedAt = 0L
            return false
        }
        if (speechStartedAt == 0L) return false
        if (silenceStartedAt == 0L) silenceStartedAt = nowMs
        val speechMs = (lastSpeechAt - speechStartedAt).coerceAtLeast(frameMs)
        val silenceMs = nowMs - silenceStartedAt
        val dynamicLimit = if (speechMs < 700L) maxSilenceMs else minSilenceMs
        return speechMs >= minimumSpeechMs && silenceMs >= dynamicLimit
    }
}
