package com.kyawgyi.voice

/**
 * Supported Speech-to-Text engine types in the KyawGyi Voice Assistant pipeline.
 *
 * Only the Android system recognizer remains: the former SHERPA_ONNX and VOSK
 * tiers were heuristic fakes (no real model or native library ever existed)
 * and were removed in the 2026-10-22 fake-data removal pass. Persisted
 * preference values naming the removed engines fail [Enum.valueOf] and fall
 * back to [SYSTEM_ON_DEVICE] in VoiceSettings.
 */
enum class SttEngineType {
    SYSTEM_ON_DEVICE
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
