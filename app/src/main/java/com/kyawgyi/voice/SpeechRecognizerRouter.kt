package com.kyawgyi.voice

import kotlinx.coroutines.CoroutineScope

/** Chooses exactly one direct-PCM engine while CentralAudioRecorder owns the mic. */
class SpeechRecognizerRouter(
    private val recorder: CentralAudioRecorder,
    private val sherpa: DirectPcmRecognizer,
    private val vosk: DirectPcmRecognizer?,
    private val systemRecognizer: DirectPcmRecognizer?,
    private val scope: CoroutineScope,
) {
    enum class Engine { SHERPA_ONNX, VOSK, SYSTEM }
    private var active: DirectPcmRecognizer? = null
    private var selected = Engine.SHERPA_ONNX

    fun select(engine: Engine) {
        if (active != null) stop()
        selected = engine
    }

    fun prepare() {
        engineFor(selected).prepare()
    }

    fun start() {
        check(active == null) { "An STT engine is already active" }
        // One source only; engines consume CentralAudioRecorder.dspPcmFlow.
        if (!isRecorderRunning()) recorder.start()
        val chosen = engineFor(selected)
        chosen.start()
        active = chosen
    }

    fun stop() {
        active?.stop()
        active = null
    }

    fun close() {
        stop()
        sherpa.close()
        vosk?.close()
        systemRecognizer?.close()
        recorder.close()
    }

    private fun engineFor(engine: Engine): DirectPcmRecognizer = when (engine) {
        Engine.SHERPA_ONNX -> sherpa
        Engine.VOSK -> vosk ?: error("Vosk direct-PCM engine is not configured")
        Engine.SYSTEM -> systemRecognizer ?: error("System recognizer is not configured")
    }

    private fun isRecorderRunning(): Boolean = recorder.running.value
}
