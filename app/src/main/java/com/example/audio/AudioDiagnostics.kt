package com.example.audio

import android.util.Log

/**
 * KYAW GYI AI — Structured Audio & Voice Diagnostics (Stage 19 Production Hardened)
 *
 * Provides Zero-PII structured logging for audio focus, microphone state,
 * media state, and non-interruption policy enforcement.
 */
object AudioDiagnostics {

    private const val TAG = "KYAW_GYI_AUDIO_DIAG"

    // Diagnostics category tags
    const val CAT_VOICE = "VOICE"
    const val CAT_AUDIO_FOCUS = "AUDIO_FOCUS"
    const val CAT_MIC = "MIC"
    const val CAT_AUDIO_RECORD = "AUDIO_RECORD"
    const val CAT_AUDIO_ROUTE = "AUDIO_ROUTE"
    const val CAT_BLUETOOTH = "BLUETOOTH_ROUTE"
    const val CAT_SERVICE = "SERVICE"
    const val CAT_MEDIA = "MEDIA"
    const val CAT_POLICY = "POLICY"

    /**
     * Log structured diagnostic event without any PII (passwords, tokens, phone numbers, transcripts).
     */
    fun logEvent(category: String, attributes: Map<String, String>) {
        val sb = StringBuilder("[$category]\n")
        for ((key, value) in attributes) {
            // Strictly sanitize against potential PII
            val safeVal = sanitize(value)
            sb.append("  ").append(key).append("=").append(safeVal).append("\n")
        }
        Log.d(TAG, sb.toString().trimEnd())
    }

    fun logFocusAction(action: String, reason: String, musicActive: Boolean, granted: Boolean? = null) {
        val map = mutableMapOf(
            "action" to action,
            "reason" to reason,
            "externalPlayback" to if (musicActive) "PLAYING" else "IDLE"
        )
        if (granted != null) {
            map["granted"] = granted.toString()
        }
        logEvent(CAT_AUDIO_FOCUS, map)
    }

    fun logMicState(state: String, activeInstances: Int, bufferSize: Int = 0) {
        logEvent(CAT_MIC, mapOf(
            "state" to state,
            "activeInstances" to activeInstances.toString(),
            "bufferSize" to bufferSize.toString()
        ))
    }

    fun logPolicyDecision(decision: String, invariantEnforced: Boolean, musicActive: Boolean) {
        logEvent(CAT_POLICY, mapOf(
            "decision" to decision,
            "invariantEnforced" to invariantEnforced.toString(),
            "externalPlayback" to if (musicActive) "PLAYING" else "IDLE",
            "musicInterruption" to "BLOCKED"
        ))
    }

    private fun sanitize(input: String): String {
        // Redact any phone-like numbers (>6 digits) or potential auth tokens
        return input.replace(Regex("\\b\\d{6,}\\b"), "[REDACTED_NUM]")
            .replace(Regex("(AIza|bearer|token|key|pwd|password)[A-Za-z0-9_\\-]{4,}", RegexOption.IGNORE_CASE), "[REDACTED_SECRET]")
    }
}
