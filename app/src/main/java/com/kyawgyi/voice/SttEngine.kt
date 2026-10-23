package com.kyawgyi.voice

/**
 * Supported Speech-to-Text engine types in the KyawGyi Voice Assistant pipeline.
 *
 * The former SHERPA_ONNX and VOSK tiers were heuristic fakes (no real model
 * or native library ever existed) and were removed in the 2026-10-22
 * fake-data removal pass. Persisted preference values naming the removed
 * engines fail [Enum.valueOf] and fall back to [SYSTEM_ON_DEVICE] in
 * VoiceSettings.
 *
 * [GEMINI_ONLINE] (added 2026-10-23, §7 plan) is the Gemini audio
 * transcription engine: buffered 16 kHz PCM is sent to the Gemini API
 * through GeminiApiService (shared key pool + failure classifier) and
 * the returned transcript re-enters the pipeline through the exact
 * same listener path as the system recognizer. It requires internet
 * and a user-configured API key; failures surface honestly and the
 * manager fails over to [SYSTEM_ON_DEVICE].
 */
enum class SttEngineType {
    SYSTEM_ON_DEVICE,
    GEMINI_ONLINE
}

/**
 * Operational states of an individual STT Engine.
 */
enum class SttEngineState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

/**
 * Universal interface for all pluggable on-device and local Speech-to-Text engines.
 */
interface SttEngine {
    val engineType: SttEngineType
    val isRunning: Boolean

    fun startListening()
    fun stopListening()
    fun processAudio(pcmChunk: ByteArray)
    fun setTranscriptListener(listener: (transcript: String, isFinal: Boolean) -> Unit)
    fun release()
}
