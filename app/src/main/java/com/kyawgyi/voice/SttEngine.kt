package com.kyawgyi.voice

/**
 * Supported Speech-to-Text engine types in the KyawGyi Voice Assistant pipeline.
 */
enum class SttEngineType {
    SHERPA_ONNX,
    VOSK,
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
