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

        // The Android system recognizer is the only real STT engine in this app.
        // (The Sherpa-ONNX/Vosk tiers and their placeholder models were removed
        // in the 2026-10-22 fake-data removal pass.)
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

        syncUiStateWithActiveEngine()
    }

    private fun syncUiStateWithActiveEngine() {
        val currentType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
        val modelReady = systemEngine.isRecognitionAvailable()
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

            // SYSTEM_ON_DEVICE is the only engine type that exists.
            activeEngine = systemEngine
            Log.i(TAG, "Switched active STT Engine to: ${activeEngine?.engineType}")
            syncUiStateWithActiveEngine()
        }
    }

    fun startListening() {
        Log.i(TAG, "SttEngineManager starting listening session (active=${activeEngine?.engineType})...")
        setEngineStateThreadSafe(SttEngineState.LISTENING)
        activeEngine?.startListening()
        // Defensive: if the active engine were ever not the system engine,
        // co-activate it so live microphone speech still produces real transcripts.
        if (activeEngine != systemEngine && systemEngine.isRecognitionAvailable()) {
            systemEngine.startListening()
        }
        syncUiStateWithActiveEngine()
    }

    fun stopListening() {
        Log.i(TAG, "SttEngineManager stopping listening session.")
        activeEngine?.stopListening()
        if (activeEngine != systemEngine) {
            systemEngine.stopListening()
        }
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
     * Ensures the active engine is the Android system recognizer — the only
     * real STT engine in this app (the Sherpa-ONNX/Vosk tiers were removed).
     */
    @Synchronized
    fun ensureHealthyEngineSelected(): SttEngineType {
        if (activeEngine?.engineType != SttEngineType.SYSTEM_ON_DEVICE) {
            selectEngine(SttEngineType.SYSTEM_ON_DEVICE)
        }
        return SttEngineType.SYSTEM_ON_DEVICE
    }

    fun finalizeCurrentUtterance() {
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

    fun release() {
        isCaptureStreamingActive.set(false)
        captureStreamJob?.cancel()
        captureStreamJob = null
        stopListening()
        systemEngine.release()
    }
}
