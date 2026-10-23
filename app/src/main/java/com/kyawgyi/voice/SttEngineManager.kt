package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Immutable UI state snapshot exposed by [SttEngineManager.uiState] via thread-safe
 * Compare-And-Swap ([MutableStateFlow.update]) transitions.
 */
data class SttStreamingUiState(
    val engineState: SttEngineState = SttEngineState.IDLE,
    val activeEngineType: SttEngineType = SttEngineType.SYSTEM_ON_DEVICE,
    val isStreamingFromCaptureModule: Boolean = false,
    val isModelLoaded: Boolean = false,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val totalUtterancesProcessed: Int = 0,
    val streamedChunksCount: Long = 0L,
    val streamedBytesCount: Long = 0L,
    val currentRmsEnergy: Double = 0.0,
    val currentRmsDbFs: Float = -90f,
    val peakRmsEnergy: Double = 0.0,
    val isSpeechActive: Boolean = false,
    val lastInferenceLatencyMs: Double = 0.0,
    val droppedEchoFramesCount: Long = 0L,
    val activeInputRouteLabel: String = AudioInputRoute.BUILTIN_MIC.displayLabel,
    val preRollFlushedBytesCount: Long = 0L,
    val isMicrophoneMuted: Boolean = false,
    val registeredHotwordsCount: Int = HotwordBiasing.DEFAULT_MYANMAR_HOTWORDS.size,
    val failoverSwitchCount: Int = 0,
    val bufferPoolHitRatioPercent: Float = 100f,
    val isCallPreempted: Boolean = false,
    val ttsVoiceDataReady: Boolean = false,
    val powerProfileLabel: String = VoicePowerProfile.BALANCED.displayLabel,
    val bargeInCount: Int = 0,
    val currentAgcGain: Float = 1.0f,
    val g2pRulesCount: Int = MyanmarPronunciationLexicon.ORTHOGRAPHY_TO_SPOKEN_G2P.size,
    val persistedCustomLexiconsCount: Int = 0,
    val lastLoopbackStatus: String = "NOT RUN",
    val nativeBinarySummary: String = "Not inspected yet",
    val lastErrorMessage: String? = null,
    val lastUpdatedTimestampMs: Long = 0L
)

/**
 * SttEngineManager (Layer 2 Hybrid & Model-Based Streaming Speech-to-Text Manager)
 *
 * Coordinates streaming audio buffers from [AudioCaptureModule] to the Android
 * System On-Device ASR engine. The former Sherpa-ONNX and Vosk tiers were
 * heuristic fakes with no real model or native library and were removed in
 * the 2026-10-22 fake-data removal pass.
 *
 * Guarantees thread-safe [StateFlow] updates (`engineState` and `uiState`) using atomic CAS
 * (`MutableStateFlow.update`) and serialized coroutine stream dispatchers.
 */
class SttEngineManager(
    private val context: Context,
    val modelManager: ModelManager = ModelManager(context),
    val audioCaptureModule: AudioCaptureModule = AudioCaptureModule.getInstance(context),
    val hotwordBiasing: HotwordBiasing = HotwordBiasing(),
    val codeSwitchNormalizer: CodeSwitchNormalizer = CodeSwitchNormalizer(),
    val failureMemory: FailureMemory = FailureMemory(),
    val dualSttFailover: DualSttFailover = DualSttFailover(modelManager, failureMemory),
    val acousticToneGenerator: AcousticToneGenerator = AcousticToneGenerator(context),
    val myanmarTtsVoiceDataChecker: MyanmarTtsVoiceDataChecker = MyanmarTtsVoiceDataChecker(context, modelManager),
    val confidenceRejectionGate: ConfidenceRejectionGate = ConfidenceRejectionGate(),
    val ttsBargeInController: TtsBargeInController = TtsBargeInController(),
    val voicePowerManager: VoicePowerManager = VoicePowerManager(context),
    val modelSafetyChecker: ModelSafetyChecker = ModelSafetyChecker(context),
    val myanmarVerbalizer: MyanmarVerbalizer = MyanmarVerbalizer(),
    val streamingTtsQueue: StreamingTtsQueue = StreamingTtsQueue(),
    val sileroNeuralVadAdapter: SileroNeuralVadAdapter = SileroNeuralVadAdapter(context),
    val neuralWakeWordKwsAdapter: NeuralWakeWordKwsAdapter = NeuralWakeWordKwsAdapter(context),
    val voiceAssetManifest: VoiceAssetManifest = VoiceAssetManifest(context, modelManager),
    val audioDiagnosticsManager: AudioDiagnosticsManager = AudioDiagnosticsManager(context)
) {

    companion object {
        private const val TAG = "SttEngineManager"

        @Volatile
        private var instance: SttEngineManager? = null

        fun getInstance(context: Context): SttEngineManager {
            return instance ?: synchronized(this) {
                instance ?: SttEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val streamingMutex = Mutex()
    private var captureStreamJob: Job? = null
    private val isCaptureStreamingActive = AtomicBoolean(false)

    private val _engineState = MutableStateFlow(SttEngineState.IDLE)
    val engineState: StateFlow<SttEngineState> = _engineState.asStateFlow()

    private val _uiState = MutableStateFlow(SttStreamingUiState())
    val uiState: StateFlow<SttStreamingUiState> = _uiState.asStateFlow()

    private val _transcriptionFlow = MutableSharedFlow<Pair<String, Boolean>>(
        replay = 1,
        extraBufferCapacity = 32
    )
    val transcriptionFlow: SharedFlow<Pair<String, Boolean>> = _transcriptionFlow.asSharedFlow()

    val systemEngine = SystemOnDeviceSttEngine(context)

    private val geminiApiService = com.example.data.GeminiApiService(context)

    /** Test seam (same pattern as ErrorLearningWorkflowEngine's
     * ttsProbeFactoryForTesting): replaces the live Gemini transcription
     * call so manager-level online-STT behavior is unit-testable with
     * fakes. Production code never sets this. */
    @Volatile
    internal var transcribeAudioOverrideForTesting: (suspend (ByteArray, String) -> String)? = null

    /**
     * §7 (2026-10-23): the Gemini online STT engine. Its transcripts
     * enter through the same listener as the system engine's (wired in
     * init), and its failures surface via [onOnlineEngineError] —
     * classified message in uiState + failover to the system engine.
     */
    val onlineEngine = GeminiOnlineSttEngine(
        transcribe = { wavBytes, languageTag ->
            transcribeAudioOverrideForTesting?.invoke(wavBytes, languageTag)
                ?: geminiApiService.transcribeAudio(wavBytes, languageTag)
        },
        onError = { message -> onOnlineEngineError(message) }
    )

    @Volatile
    var activeEngine: SttEngine? = null
        private set

    @Volatile
    private var lastPartialTranscript: String = ""

    @Volatile
    private var lastFinalTranscript: String = ""

    private val totalFinalUtterances = AtomicInteger(0)
    private val totalStreamedChunks = AtomicLong(0L)
    private val totalStreamedBytes = AtomicLong(0L)
    private val droppedEchoFrames = AtomicLong(0L)
    private val totalPreRollFlushedBytes = AtomicLong(0L)
    private val wasPreviousFrameSpeech = AtomicBoolean(false)

    val customLexiconRepository = com.kyawgyi.voice.data.CustomLexiconRepository(
        com.kyawgyi.voice.data.VoiceLexiconDatabase.getDatabase(context).customLexiconDao()
    )
    val telephonyPreemptionManager = TelephonyPreemptionManager(context) { isCallActive ->
        val callState = if (isCallActive) {
            android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
        } else {
            android.telephony.TelephonyManager.CALL_STATE_IDLE
        }
        audioCaptureModule.telephonyPreemption.onCallStateChanged(
            newCallState = callState,
            isCurrentlyListening = _engineState.value == SttEngineState.LISTENING
        )
    }

    init {
        telephonyPreemptionManager.registerCallStateListener()

        // Default engine: the Android system recognizer. (The Sherpa-ONNX/
        // Vosk tiers and their placeholder models were removed in the
        // 2026-10-22 fake-data removal pass.) Since §7 (2026-10-23) the
        // Gemini online engine is a second real engine; the persisted
        // VoiceSettings selection below may replace this default.
        activeEngine = systemEngine

        // Populate Myanmar cities, townships, honorifics & contacts into HotwordBiasing
        ProperNounHotwordDataset.populateHotwordBiasing(context, hotwordBiasing)

        // Sync Room persistent custom lexicons into HotwordBiasing on background coroutine
        managerScope.launch {
            runCatching {
                val syncedCount = customLexiconRepository.syncIntoHotwordBiasing(hotwordBiasing)
                val binaryStatus = NativeLibraryLoader.inspectStatus(context)
                _uiState.update { current ->
                    current.copy(
                        persistedCustomLexiconsCount = syncedCount,
                        registeredHotwordsCount = hotwordBiasing.getAllEntries().size,
                        nativeBinarySummary = binaryStatus.statusSummary
                    )
                }
            }
        }

        // Wire engine transcript callbacks to thread-safe StateFlow updater
        systemEngine.setTranscriptListener { text, isFinal -> onEngineTranscriptReceived(text, isFinal) }
        onlineEngine.setTranscriptListener { text, isFinal -> onEngineTranscriptReceived(text, isFinal) }

        // Honor the persisted user selection (VoiceSettings) so the
        // Settings choice actually drives this path; default remains
        // the system recognizer.
        val preferredType = try {
            VoiceSettings.getInstance(context).preferredSttEngine
        } catch (e: Exception) {
            SttEngineType.SYSTEM_ON_DEVICE
        }
        if (preferredType == SttEngineType.GEMINI_ONLINE) {
            activeEngine = onlineEngine
        }

        syncUiStateWithActiveEngine()
    }

    private fun syncUiStateWithActiveEngine() {
        val currentType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
        // For the online engine there is no on-device model to load:
        // readiness is per-call (key + network) and failures surface
        // per utterance, so "model loaded" means "no local model
        // required" rather than claiming a check we did not run.
        val modelReady = when (currentType) {
            SttEngineType.GEMINI_ONLINE -> true
            SttEngineType.SYSTEM_ON_DEVICE -> systemEngine.isRecognitionAvailable()
        }
        _uiState.update { current ->
            current.copy(
                activeEngineType = currentType,
                isModelLoaded = modelReady,
                lastUpdatedTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun selectEngine(type: SttEngineType) {
        synchronized(this) {
            val old = activeEngine
            old?.stopListening()

            activeEngine = when (type) {
                SttEngineType.SYSTEM_ON_DEVICE -> systemEngine
                SttEngineType.GEMINI_ONLINE -> onlineEngine
            }
            Log.i(TAG, "Switched active STT Engine to: ${activeEngine?.engineType}")
            syncUiStateWithActiveEngine()
        }
    }

    fun startListening() {
        // FIX-06: register our stopper, then claim the microphone
        // (preempts any current owner).
        com.example.engine.VoiceSessionArbiter.registerStopper(
            com.example.engine.VoiceSessionOwner.IR_SERVICE
        ) { stopListening() }
        com.example.engine.VoiceSessionArbiter.requestOwnership(
            com.example.engine.VoiceSessionOwner.IR_SERVICE
        )
        Log.i(TAG, "SttEngineManager starting listening session (active=${activeEngine?.engineType})...")
        setEngineStateThreadSafe(SttEngineState.LISTENING)
        // Exactly ONE engine runs per session. The former defensive
        // co-activation of the system recognizer beside a non-system
        // active engine is removed: with a real second engine
        // (GEMINI_ONLINE) it would double-capture the microphone
        // (a FIX-06 single-owner violation) and double-report
        // transcripts. Failover, not co-activation, is the recovery
        // path (recordEngineFailureAndFailover).
        activeEngine?.startListening()
        syncUiStateWithActiveEngine()
    }

    fun stopListening() {
        // FIX-06: hand the microphone back.
        com.example.engine.VoiceSessionArbiter.releaseOwnership(
            com.example.engine.VoiceSessionOwner.IR_SERVICE
        )
        Log.i(TAG, "SttEngineManager stopping listening session.")
        activeEngine?.stopListening()
        setEngineStateThreadSafe(SttEngineState.IDLE)
        _uiState.update { current ->
            current.copy(
                engineState = SttEngineState.IDLE,
                isSpeechActive = false,
                currentRmsEnergy = 0.0,
                currentRmsDbFs = -90f,
                lastUpdatedTimestampMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Connects [AudioCaptureModule.audioBufferFlow] directly to the system STT engine
     * on a background coroutine and performs lock-free, thread-safe [StateFlow]
     * updates for the UI.
     */
    fun startAudioCaptureStreaming(
        captureModule: AudioCaptureModule = this.audioCaptureModule
    ) {
        managerScope.launch {
            streamingMutex.withLock {
                if (isCaptureStreamingActive.get()) {
                    Log.d(TAG, "AudioCaptureModule streaming is already active.")
                    return@withLock
                }
                startListening()
                isCaptureStreamingActive.set(true)
                _uiState.update { current ->
                    current.copy(
                        isStreamingFromCaptureModule = true,
                        engineState = SttEngineState.LISTENING,
                        lastErrorMessage = null,
                        lastUpdatedTimestampMs = System.currentTimeMillis()
                    )
                }

                captureStreamJob?.cancel()
                captureStreamJob = managerScope.launch {
                    try {
                        captureModule.audioBufferFlow.collect { packet ->
                            if (!isCaptureStreamingActive.get()) return@collect
                            streamAudioBufferPacket(packet)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in AudioCaptureModule stream collector: ${e.message}", e)
                        _uiState.update { current ->
                            current.copy(
                                isStreamingFromCaptureModule = false,
                                lastErrorMessage = e.message,
                                lastUpdatedTimestampMs = System.currentTimeMillis()
                            )
                        }
                    }
                }

                captureModule.startCapture()
            }
        }
    }

    /**
     * Stops streaming audio buffers from [AudioCaptureModule] and resets capture state thread-safely.
     */
    fun stopAudioCaptureStreaming(captureModule: AudioCaptureModule = this.audioCaptureModule) {
        managerScope.launch {
            streamingMutex.withLock {
                isCaptureStreamingActive.set(false)
                captureStreamJob?.cancel()
                captureStreamJob = null
                captureModule.stopCapture()
                stopListening()
                _uiState.update { current ->
                    current.copy(
                        isStreamingFromCaptureModule = false,
                        engineState = SttEngineState.IDLE,
                        isSpeechActive = false,
                        lastUpdatedTimestampMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    /**
     * Collects any cold or hot [Flow] of 16kHz 16-bit Mono PCM [ByteArray] buffers,
     * converts each buffer via [AudioCaptureModule.buildPacketFromPcm], routes it to the
     * model-based STT engine, and updates UI [StateFlow] thread-safely.
     */
    suspend fun collectAudioBufferStream(pcmFlow: Flow<ByteArray>) {
        if (_engineState.value != SttEngineState.LISTENING) {
            startListening()
        }
        var seq = 0L
        pcmFlow.collect { pcmBytes ->
            seq++
            val packet = AudioCaptureModule.buildPacketFromPcm(pcmBytes, seq)
            streamAudioBufferPacket(packet)
        }
    }

    /**
     * Routes a pre-normalized [AudioBufferPacket] from [AudioCaptureModule] to the
     * active STT engine via `processAudio` and performs an atomic [StateFlow]
     * update for the UI.
     */
    fun streamAudioBufferPacket(packet: AudioBufferPacket) {
        if (_engineState.value != SttEngineState.LISTENING && _engineState.value != SttEngineState.PROCESSING) {
            return
        }

        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive || aecManager.isMicPaused) {
            val bargedIn = ttsBargeInController.evaluateFrameDuringTts(
                frameRmsEnergy = packet.rmsEnergy,
                isSpeechLikeZcr = packet.isSpeechFrame,
                onInterruptTts = {
                    aecManager.onTtsPlaybackStopped()
                }
            )
            if (!bargedIn) {
                val dropped = droppedEchoFrames.incrementAndGet()
                _uiState.update { current ->
                    current.copy(
                        droppedEchoFramesCount = dropped,
                        isSpeechActive = false,
                        bargeInCount = ttsBargeInController.state.value.totalBargeInInterruptions,
                        lastUpdatedTimestampMs = System.currentTimeMillis()
                    )
                }
                return
            }
        }

        val engine = activeEngine
        val startNs = System.nanoTime()

        // If speech just transitioned from SILENCE -> SPEECH, drain the 500ms PreRollPcmRingBuffer
        // and prepend it to the active STT engine so initial Burmese consonants are never clipped!
        val wasSpeech = wasPreviousFrameSpeech.getAndSet(packet.isSpeechFrame)
        if (packet.isSpeechFrame && !wasSpeech) {
            val preRollBytes = audioCaptureModule.preRollRingBuffer.drainPreRollBytes()
            if (preRollBytes.isNotEmpty()) {
                totalPreRollFlushedBytes.addAndGet(preRollBytes.size.toLong())
                engine?.processAudio(preRollBytes)
            }
        }

        engine?.processAudio(packet.pcmBytes)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0

        val chunks = totalStreamedChunks.incrementAndGet()
        val bytes = totalStreamedBytes.addAndGet(packet.pcmBytes.size.toLong())
        val captureSnapshot = audioCaptureModule.captureState.value
        val failoverSnapshot = dualSttFailover.state.value
        val ttsVoiceStatus = myanmarTtsVoiceDataChecker.status.value

        _uiState.update { current ->
            current.copy(
                engineState = _engineState.value,
                activeEngineType = engine?.engineType ?: current.activeEngineType,
                streamedChunksCount = chunks,
                streamedBytesCount = bytes,
                currentRmsEnergy = packet.rmsEnergy,
                currentRmsDbFs = packet.rmsDbFs,
                peakRmsEnergy = max(current.peakRmsEnergy, packet.rmsEnergy),
                isSpeechActive = packet.isSpeechFrame,
                lastInferenceLatencyMs = latencyMs,
                activeInputRouteLabel = captureSnapshot.activeInputRoute,
                preRollFlushedBytesCount = totalPreRollFlushedBytes.get(),
                isMicrophoneMuted = captureSnapshot.isMicrophoneMuted,
                registeredHotwordsCount = hotwordBiasing.getAllEntries().size,
                failoverSwitchCount = failoverSnapshot.failoverSwitchCount,
                bufferPoolHitRatioPercent = captureSnapshot.bufferPoolHitRatioPercent,
                isCallPreempted = captureSnapshot.isCallPreempted,
                ttsVoiceDataReady = !ttsVoiceStatus.requiresVoiceDataDownload,
                powerProfileLabel = voicePowerManager.state.value.profile.displayLabel,
                bargeInCount = ttsBargeInController.state.value.totalBargeInInterruptions,
                currentAgcGain = captureSnapshot.currentAgcGain,
                lastUpdatedTimestampMs = packet.timestampMs
            )
        }
    }

    /**
     * Ingests incoming 16kHz PCM audio chunk, forwards it to the active STT engine,
     * and updates [uiState] thread-safely.
     */
    fun feedPcmChunk(pcmChunk: ByteArray) {
        if (_engineState.value != SttEngineState.LISTENING) return

        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive || aecManager.isMicPaused) {
            val dropped = droppedEchoFrames.incrementAndGet()
            _uiState.update { current ->
                current.copy(
                    droppedEchoFramesCount = dropped,
                    isSpeechActive = false,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
            return
        }

        val startNs = System.nanoTime()
        activeEngine?.processAudio(pcmChunk)
        val latencyMs = (System.nanoTime() - startNs) / 1_000_000.0

        val chunks = totalStreamedChunks.incrementAndGet()
        val bytes = totalStreamedBytes.addAndGet(pcmChunk.size.toLong())
        val packet = AudioCaptureModule.buildPacketFromPcm(pcmChunk, chunks)

        _uiState.update { current ->
            current.copy(
                engineState = _engineState.value,
                activeEngineType = activeEngine?.engineType ?: current.activeEngineType,
                streamedChunksCount = chunks,
                streamedBytesCount = bytes,
                currentRmsEnergy = packet.rmsEnergy,
                currentRmsDbFs = packet.rmsDbFs,
                peakRmsEnergy = max(current.peakRmsEnergy, packet.rmsEnergy),
                isSpeechActive = packet.isSpeechFrame,
                lastInferenceLatencyMs = latencyMs,
                lastUpdatedTimestampMs = packet.timestampMs
            )
        }
    }

    /**
     * Triggers the 3-Second Mic Check & Playback Loopback diagnostic asynchronously and updates [uiState].
     */
    fun triggerThreeSecondMicLoopbackCheck(durationMs: Int = 3000) {
        managerScope.launch {
            _uiState.update { it.copy(lastLoopbackStatus = "RECORDING (${durationMs / 1000}s)...") }
            audioDiagnosticsManager.runThreeSecondMicCheck(durationMs)
            val diag = audioDiagnosticsManager.state.value
            _uiState.update {
                it.copy(
                    lastLoopbackStatus = "${"%.1f".format(diag.rmsDbFs)} dBFS (${if (diag.lastTestPassed) "PASS ✓" else "LOW"})",
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    private fun setEngineStateThreadSafe(newState: SttEngineState) {
        _engineState.update { newState }
        _uiState.update { current ->
            current.copy(
                engineState = newState,
                lastUpdatedTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun updateEngineState(newState: SttEngineState) {
        setEngineStateThreadSafe(newState)
    }

    fun emitTranscript(transcript: String, isFinal: Boolean) {
        onEngineTranscriptReceived(transcript, isFinal)
    }

    /**
     * Records live transcripts recognized by UI/Service SpeechRecognizers (VoiceEngine, SpeechHandler, Chatbox Mic)
     * into SttEngineManager health telemetry without double-dispatching HybridPipelineController.
     */
    fun recordLiveExternalTranscript(transcript: String, isFinal: Boolean) {
        if (transcript.isBlank()) return
        val homophoneFixed = MyanmarPronunciationLexicon.disambiguateSttHomophones(transcript)
        val biased = hotwordBiasing.applyHotwordBiasToTranscript(homophoneFixed)
        lastPartialTranscript = biased
        if (isFinal) {
            val normalizedFinal = codeSwitchNormalizer.normalizeSttTranscript(biased)
            lastFinalTranscript = normalizedFinal
            val count = totalFinalUtterances.incrementAndGet()
            _uiState.update { current ->
                current.copy(
                    partialTranscript = normalizedFinal,
                    finalTranscript = normalizedFinal,
                    totalUtterancesProcessed = count,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
        } else {
            _uiState.update { current ->
                current.copy(
                    partialTranscript = biased,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    data class SttEngineHealthReport(
        val activeEngineType: SttEngineType,
        val engineState: SttEngineState,
        val isSystemSttAvailable: Boolean,
        val lastPartialTranscript: String,
        val lastFinalTranscript: String,
        val totalUtterancesProcessed: Int,
        val totalStreamedChunks: Long = 0L,
        val totalStreamedBytes: Long = 0L,
        val lastRmsDbFs: Float = -90f,
        val lastInferenceLatencyMs: Double = 0.0
    )

    fun getHealthReport(): SttEngineHealthReport {
        val snapshot = _uiState.value
        return SttEngineHealthReport(
            activeEngineType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE,
            engineState = _engineState.value,
            isSystemSttAvailable = systemEngine.isRecognitionAvailable(),
            lastPartialTranscript = lastPartialTranscript,
            lastFinalTranscript = lastFinalTranscript,
            totalUtterancesProcessed = totalFinalUtterances.get(),
            totalStreamedChunks = totalStreamedChunks.get(),
            totalStreamedBytes = totalStreamedBytes.get(),
            lastRmsDbFs = snapshot.currentRmsDbFs,
            lastInferenceLatencyMs = snapshot.lastInferenceLatencyMs
        )
    }

    /**
     * Ensures a usable engine is selected WITHOUT overriding the
     * user's choice: the selection stands unless the selected engine
     * is the online engine AND [FailureMemory] has it quarantined
     * after repeated failures — then the system recognizer takes over.
     * (Before §7 this forced SYSTEM_ON_DEVICE unconditionally, which
     * was correct only while no other real engine existed.)
     */
    @Synchronized
    fun ensureHealthyEngineSelected(): SttEngineType {
        val current = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
        if (current == SttEngineType.GEMINI_ONLINE &&
            !failureMemory.isEngineHealthy(current.name, System.currentTimeMillis())
        ) {
            selectEngine(SttEngineType.SYSTEM_ON_DEVICE)
            return SttEngineType.SYSTEM_ON_DEVICE
        }
        return current
    }

    fun finalizeCurrentUtterance() {
        // §7: for the online engine the VAD endpoint IS the utterance
        // boundary — flush the buffered PCM to Gemini. The transcript
        // re-enters asynchronously via the engine listener ->
        // onEngineTranscriptReceived, the same entry the system
        // recognizer's finals use.
        val engine = activeEngine
        if (engine is GeminiOnlineSttEngine && engine.finishUtterance()) {
            return
        }
        val transcriptToFinalize = lastPartialTranscript.trim()
        if (transcriptToFinalize.isNotBlank()) {
            Log.i(TAG, "VAD Endpoint triggered: Committing active transcript -> \"$transcriptToFinalize\"")
            lastPartialTranscript = ""
            onEngineTranscriptReceived(transcriptToFinalize, isFinal = true)
        } else {
            Log.d(TAG, "VAD Endpoint triggered with empty transcript. Resetting to IDLE.")
            setEngineStateThreadSafe(SttEngineState.IDLE)
        }
    }

    /**
     * Records a runtime failure on the current STT engine via [DualSttFailover] and [FailureMemory],
     * automatically switching to the next healthy engine tier.
     */
    @Synchronized
    fun recordEngineFailureAndFailover(reason: String): SttEngineType {
        val currentType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
        val nextType = dualSttFailover.onEngineFailure(
            failedEngine = currentType,
            reason = reason,
            isSystemAvailable = systemEngine.isRecognitionAvailable()
        )
        selectEngine(nextType)
        _uiState.update { current ->
            current.copy(
                activeEngineType = nextType,
                failoverSwitchCount = dualSttFailover.state.value.failoverSwitchCount,
                lastErrorMessage = reason,
                lastUpdatedTimestampMs = System.currentTimeMillis()
            )
        }
        return nextType
    }

    private fun onEngineTranscriptReceived(transcript: String, isFinal: Boolean) {
        val homophoneFixed = MyanmarPronunciationLexicon.disambiguateSttHomophones(transcript)
        val biased = hotwordBiasing.applyHotwordBiasToTranscript(homophoneFixed)
        lastPartialTranscript = biased
        if (isFinal) {
            val currentType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
            dualSttFailover.onUtteranceSucceeded(currentType)
            val normalizedFinal = codeSwitchNormalizer.normalizeSttTranscript(biased)
            lastFinalTranscript = normalizedFinal
            val count = totalFinalUtterances.incrementAndGet()
            _engineState.update { SttEngineState.PROCESSING }
            _uiState.update { current ->
                current.copy(
                    engineState = SttEngineState.PROCESSING,
                    partialTranscript = normalizedFinal,
                    finalTranscript = normalizedFinal,
                    totalUtterancesProcessed = count,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
            _transcriptionFlow.tryEmit(Pair(normalizedFinal, true))
        } else {
            _uiState.update { current ->
                current.copy(
                    partialTranscript = biased,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
            _transcriptionFlow.tryEmit(Pair(biased, false))
        }
    }

    /**
     * §7: a Gemini online STT failure (already classified into an
     * honest Burmese message by the service layer) is surfaced in
     * uiState AND fails over to the system recognizer for subsequent
     * utterances — the failure is never hidden and the online engine
     * is never silently retried in a loop.
     */
    private fun onOnlineEngineError(message: String) {
        Log.w(TAG, "Gemini online STT failed: $message")
        if (activeEngine?.engineType == SttEngineType.GEMINI_ONLINE) {
            // Still the active engine: record + fail over (this also
            // publishes the message as uiState.lastErrorMessage).
            recordEngineFailureAndFailover(message)
        } else {
            // The user already switched engines manually; surface the
            // failure without recording it against the wrong engine.
            _uiState.update { current ->
                current.copy(
                    lastErrorMessage = message,
                    lastUpdatedTimestampMs = System.currentTimeMillis()
                )
            }
        }
    }

    fun release() {
        isCaptureStreamingActive.set(false)
        captureStreamJob?.cancel()
        captureStreamJob = null
        stopListening()
        systemEngine.release()
        onlineEngine.release()
    }
}
