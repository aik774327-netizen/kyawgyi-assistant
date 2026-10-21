package com.example.voice

import kotlinx.coroutines.flow.Flow

/**
 * Result returned by an execution strategy (Online or Offline).
 */
data class AssistantResult(
    val isSuccess: Boolean,
    val transcription: String,
    val spokenResponse: String,
    val functionCall: FunctionCallPayload? = null,
    val executionEngine: EngineType,
    val latencyMs: Long = 0L,
    val errorMessage: String? = null
)

enum class EngineType {
    GEMINI_ONLINE,
    HYBRID_FALLBACK
}

data class FunctionCallPayload(
    val name: String,
    val arguments: Map<String, Any?>
)

/**
 * VoiceAssistantStrategy
 *
 * Pluggable Strategy interface for consuming the central PCM 16kHz audio stream.
 */
interface VoiceAssistantStrategy {
    val engineType: EngineType

    /**
     * Consumes incoming raw PCM audio stream and resolves user intent.
     * @param audioFlow SharedFlow or Flow of raw 16kHz 16-bit Mono PCM byte arrays.
     */
    suspend fun processAudioStream(audioFlow: Flow<ByteArray>): AssistantResult
}
