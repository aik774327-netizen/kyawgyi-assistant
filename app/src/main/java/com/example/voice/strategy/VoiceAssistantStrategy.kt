package com.example.voice.strategy

import kotlinx.coroutines.flow.Flow

/**
 * Result representation returned by any VoiceAssistantStrategy (Online Gemini or Offline Sherpa-ONNX).
 */
sealed class AssistantResult {

  /**
   * Successfully processed voice command.
   * [transcript]: Recognized user utterance in Burmese or English.
   * [responseBurmese]: Spoken Burmese feedback for the user.
   * [actionKey]: Optional identified action identifier (e.g. "KENNEDE_FAN_POWER", "TOGGLE_FLASHLIGHT").
   * [functionCall]: Optional structured function call payload returned by Gemini.
   * [isOfflineFallback]: True if produced by local offline engine.
   * [latencyMs]: Total round-trip latency in milliseconds.
   */
  data class Success(
    val transcript: String,
    val responseBurmese: String,
    val actionKey: String? = null,
    val functionCall: FunctionCallPayload? = null,
    val isOfflineFallback: Boolean = false,
    val latencyMs: Long = 0L
  ) : AssistantResult()

  /**
   * Failed processing attempt.
   */
  data class Error(
    val errorMessage: String,
    val cause: Throwable? = null,
    val isRetryable: Boolean = true
  ) : AssistantResult()
}

/**
 * Structured Function Call payload returned by online or local model.
 */
data class FunctionCallPayload(
  val name: String,
  val arguments: Map<String, String> = emptyMap()
)

/**
 * Clean Strategy Interface for Voice Processing Engines.
 * Both Online (Gemini API) and Offline (Sherpa-ONNX / Local Engine) implement this contract.
 */
interface VoiceAssistantStrategy {

  /**
   * Identifies the engine strategy name.
   */
  val engineName: String

  /**
   * Processes the raw 16kHz PCM 16-bit Mono audio stream from [CentralAudioRecorder].
   * @param audioFlow Stream of raw PCM bytes.
   * @return [AssistantResult] containing recognized transcript, voice reply, and function calls.
   */
  suspend fun processAudioStream(audioFlow: Flow<ByteArray>): AssistantResult
}
