package com.example.telemetry

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.regex.Pattern

/**
  * Standardized Telemetry Event Types for Kyaw Gyi AI
  */
enum class TelemetryEventType {
    COMMAND_RECEIVED,
    NORMALIZATION_COMPLETED,
    REFERENCE_RESOLVED,
    INTENT_CLASSIFIED,
    PLAN_CREATED,
    SAFETY_CHECKED,
    CONFIRMATION_REQUESTED,
    CONFIRMATION_RECEIVED,
    ACTION_STARTED,
    ACTION_COMPLETED,
    ACTION_FAILED,
    PIPELINE_ERROR,
    MICROPHONE_STARTED,
    MICROPHONE_STOPPED,
    WAKE_WORD_DETECTED,
    APP_DISCOVERY_STARTED,
    APP_DISCOVERY_COMPLETED,
    APP_RESOLUTION_STARTED,
    APP_CANDIDATES_FOUND,
    APP_MATCH_SELECTED,
    APP_MATCH_AMBIGUOUS,
    APP_MATCH_REJECTED,
    APP_LAUNCH_STARTED,
    APP_LAUNCH_SUCCESS,
    APP_LAUNCH_FAILED
}

/**
  * Privacy-safe telemetry event model
  */
data class TelemetryEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: TelemetryEventType,
    val sessionId: String?,
    val sanitizedDetails: String,
    val metadata: Map<String, String> = emptyMap(),
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
  * TelemetryManager - Responsible for PII redaction and structured observability event tracking.
  * Ensures no passwords, raw audio buffers, credentials, or private message contents are retained.
  */
object TelemetryManager {

    private const val TAG = "KYAW_GYI_TELEMETRY"
    private const val MAX_EVENT_BUFFER = 300

    private val _events = MutableStateFlow<List<TelemetryEvent>>(emptyList())
    val events: StateFlow<List<TelemetryEvent>> = _events.asStateFlow()

    // Regex patterns for sensitive PII
    private val PHONE_PATTERN = Pattern.compile("(\\+?959|09)\\d{7,9}")
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}")
    private val CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b")
    private val TOKEN_PATTERN = Pattern.compile("(?i)(password|token|bearer|key|secret|pin)\\s*([:=]|is)\\s*['\"]?([^'\"\\s]+)['\"]?")

    /**
     * Redacts sensitive personally identifiable information from strings
     */
    fun redactPii(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var sanitized = input
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PHONE]")
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")
        sanitized = CARD_PATTERN.matcher(sanitized).replaceAll("[REDACTED_CARD]")
        sanitized = TOKEN_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED_SECRET]")
        return sanitized
    }

    /**
     * Record a privacy-safe telemetry event
     */
    fun recordEvent(
        eventType: TelemetryEventType,
        sessionId: String? = null,
        details: String = "",
        metadata: Map<String, String> = emptyMap(),
        latencyMs: Long = 0L
    ): TelemetryEvent {
        val sanitized = redactPii(details)
        val sanitizedMeta = metadata.mapValues { redactPii(it.value) }

        val event = TelemetryEvent(
            eventType = eventType,
            sessionId = sessionId,
            sanitizedDetails = sanitized,
            metadata = sanitizedMeta,
            latencyMs = latencyMs
        )

        Log.d(TAG, "[$eventType] session=$sessionId latency=${latencyMs}ms details=$sanitized")

        val currentList = _events.value
        _events.value = (currentList + event).takeLast(MAX_EVENT_BUFFER)
        return event
    }

    /**
     * Clear telemetry memory buffer
     */
    fun clearEvents() {
        _events.value = emptyList()
    }
}
