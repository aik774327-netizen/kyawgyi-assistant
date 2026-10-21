package com.example.audit

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResolutionPath {
    EXACT_FASTPATH,
    LOCAL_SEMANTIC,
    CONTEXTUAL_MEMORY,
    SLOT_EXTRACTION,
    ONLINE_GEMINI_FALLBACK,
    LEARNED_ALIAS,
    DYNAMIC_FUZZY,
    AWAITING_RETRY,
    BLOCKED_BY_RISK_POLICY
}

data class AuditLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawQuery: String,
    val normalizedQuery: String,
    val resolutionPath: ResolutionPath,
    val targetAction: String,
    val confidence: Float,
    val isSuccess: Boolean,
    val details: String = "",
    val executionTimeMs: Long = 0
)

/**
 * Centralized Audit Logger for KYAW GYI AI
 * Strictly logs and monitors all intent resolutions, execution paths,
 * security guardrail decisions, and fallback behaviors.
 */
object AuditLogger {

    private const val TAG = "KYAW_GYI_AUDIT"
    private const val MAX_LOG_HISTORY = 200

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val PHONE_PATTERN = Regex("""\b(?:09|\+?959|\d{2,3})[0-9\- ]{6,12}\b|\b\d{7,11}\b""")
    private val MYANMAR_DIGIT_PHONE_PATTERN = Regex("""[၀-၉]{7,12}""")

    fun sanitizePii(text: String): String {
        if (text.isBlank()) return text
        return text
            .replace(PHONE_PATTERN, "[REDACTED_PHONE]")
            .replace(MYANMAR_DIGIT_PHONE_PATTERN, "[REDACTED_PHONE]")
    }

    fun log(
        rawQuery: String,
        normalizedQuery: String,
        resolutionPath: ResolutionPath,
        targetAction: String,
        confidence: Float,
        isSuccess: Boolean,
        details: String = "",
        executionTimeMs: Long = 0
    ): AuditLogEntry {
        val entry = AuditLogEntry(
            rawQuery = rawQuery,
            normalizedQuery = normalizedQuery,
            resolutionPath = resolutionPath,
            targetAction = targetAction,
            confidence = confidence,
            isSuccess = isSuccess,
            details = details,
            executionTimeMs = executionTimeMs
        )

        val sanitizedQuery = sanitizePii(rawQuery)
        val sanitizedDetails = sanitizePii(details)

        Log.i(
            TAG,
            "[$resolutionPath] query=\"$sanitizedQuery\" -> action=\"$targetAction\" " +
                    "(conf=${"%.2f".format(confidence)}, success=$isSuccess, ${executionTimeMs}ms) details=$sanitizedDetails"
        )

        val updated = (_auditLogs.value + entry).takeLast(MAX_LOG_HISTORY)
        _auditLogs.value = updated
        return entry
    }
}
