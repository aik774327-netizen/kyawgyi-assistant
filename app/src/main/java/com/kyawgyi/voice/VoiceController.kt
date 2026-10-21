package com.kyawgyi.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SttEngineId { AUTO, SHERPA_ONNX, VOSK, SYSTEM_ON_DEVICE }
enum class ModelStatus { READY, MISSING, ERROR }
enum class EngineState { MISSING_MODEL, READY, LISTENING, PROCESSING, SPEAKING, ERROR }

sealed interface SttState {
    data object Idle : SttState
    data object Listening : SttState
    data class Partial(val text: String) : SttState
    data class Final(val text: String) : SttState
    data class Error(val message: String, val cause: Throwable? = null) : SttState
}

interface SuspendSttEngine {
    val id: SttEngineId
    val state: StateFlow<SttState>
    suspend fun prepare(): ModelStatus
    suspend fun start()
    suspend fun stop()
    fun close()
}

interface SttEngineFactory {
    fun get(id: SttEngineId): SuspendSttEngine
}

data class SttManagerState(
    val engine: SttEngineId,
    val state: EngineState,
    val message: String? = null
)

/** Serializes lifecycle changes and runs the blocking AudioRecord loop outside the mutex. */
class SuspendSttEngineManager(
    private val factory: SttEngineFactory,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SttManagerState(SttEngineId.AUTO, EngineState.READY))
    val state: StateFlow<SttManagerState> = _state
    private var active: SuspendSttEngine? = null
    private var selected = SttEngineId.AUTO
    private var listenJob: Job? = null

    suspend fun select(requested: SttEngineId): ModelStatus {
        val shouldResume = mutex.withLock { listenJob?.isActive == true }
        stop()
        return mutex.withLock {
            val actual = if (requested == SttEngineId.AUTO) resolveAuto() else requested
            val engine = factory.get(actual)
            _state.value = SttManagerState(actual, EngineState.PROCESSING)
            val status = runCatching { engine.prepare() }.getOrElse {
                _state.value = SttManagerState(actual, EngineState.ERROR, it.message)
                return@withLock ModelStatus.ERROR
            }
            if (status != ModelStatus.READY) {
                _state.value = SttManagerState(actual, EngineState.MISSING_MODEL, "Model is not installed")
                return@withLock status
            }
            active = engine
            selected = requested
            _state.value = SttManagerState(actual, EngineState.READY)
            if (shouldResume) launchListeningLocked(engine)
            status
        }
    }

    suspend fun start() = mutex.withLock {
        val engine = active ?: error("Select an STT engine before start")
        if (listenJob?.isActive != true) launchListeningLocked(engine)
    }

    private fun launchListeningLocked(engine: SuspendSttEngine) {
        _state.value = SttManagerState(engine.id, EngineState.LISTENING)
        listenJob = scope.launch(Dispatchers.Default) {
            runCatching { engine.start() }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.value = SttManagerState(engine.id, EngineState.ERROR, e.message)
                }
            }
        }
    }

    suspend fun stop() {
        val job = mutex.withLock { listenJob.also { listenJob = null } }
        active?.let { runCatching { it.stop() } }
        job?.cancel()
        job?.join()
        mutex.withLock { active?.let { _state.value = SttManagerState(it.id, EngineState.READY) } }
    }

    private suspend fun resolveAuto(): SttEngineId =
        listOf(SttEngineId.SHERPA_ONNX, SttEngineId.VOSK)
            .firstOrNull { factory.get(it).prepare() == ModelStatus.READY }
            ?: SttEngineId.SYSTEM_ON_DEVICE

    fun activeEngine(): SuspendSttEngine? = active
    suspend fun close() {
        stop()
        mutex.withLock {
            active?.close()
            active = null
        }
    }
}

class VoiceController(
    private val stt: SuspendSttEngineManager,
    private val tts: TtsEngine,
    private val scope: CoroutineScope,
    private val commandHandler: suspend (String) -> String,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(EngineState.READY)
    val state: StateFlow<EngineState> = _state
    private var observeJob: Job? = null
    private var engineStateJob: Job? = null
    private var pipelineJob: Job? = null
    private var listeningRequested = false

    /** Start this once from the service/view-model scope. It never creates duplicate collectors. */
    fun observe() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            stt.state.collect { managerState ->
                if (managerState.state != EngineState.SPEAKING) _state.value = managerState.state
                val engine = stt.activeEngine()
                engineStateJob?.cancel()
                if (engine != null) {
                    engineStateJob = launch {
                        engine.state.collect { value ->
                            when (value) {
                                is SttState.Final -> if (value.text.isNotBlank()) enqueueCommand(value.text)
                                is SttState.Error -> _state.value = EngineState.ERROR
                                is SttState.Listening, is SttState.Partial ->
                                    if (_state.value != EngineState.SPEAKING) _state.value = EngineState.LISTENING
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun select(id: SttEngineId): ModelStatus = stt.select(id)

    suspend fun start() = mutex.withLock {
        listeningRequested = true
        stt.start()
        _state.value = EngineState.LISTENING
    }

    suspend fun stop() = mutex.withLock {
        listeningRequested = false
        pipelineJob?.cancel()
        pipelineJob = null
        stt.stop()
        tts.stop()
        _state.value = EngineState.READY
    }

    private fun enqueueCommand(rawText: String) {
        pipelineJob?.cancel()
        pipelineJob = scope.launch {
            val response = runCatching { commandHandler(rawText.normalizedMyanmar()) }
                .getOrElse { "တောင်းပန်ပါတယ်။ အမိန့်ကို မလုပ်ဆောင်နိုင်သေးပါ။" }
            mutex.withLock {
                if (!listeningRequested) return@withLock
                _state.value = EngineState.SPEAKING
                // Deterministic echo gate: stop AudioRecord before AudioTrack begins.
                stt.stop()
                try {
                    tts.speak(response)
                } finally {
                    tts.stop()
                    if (listeningRequested) {
                        stt.start()
                        _state.value = EngineState.LISTENING
                    } else {
                        _state.value = EngineState.READY
                    }
                }
            }
        }
    }

    fun close() {
        observeJob?.cancel()
        engineStateJob?.cancel()
        pipelineJob?.cancel()
        scope.launch {
            stop()
            stt.close()
        }
        tts.close()
    }
}
