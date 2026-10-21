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
    val activeEngineType: SttEngineType = SttEngineType.SHERPA_ONNX,
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
    val ttsVoiceDataReady: Boolean = true,
    val powerProfileLabel: String = VoicePowerProfile.BALANCED.displayLabel,
    val bargeInCount: Int = 0,
    val currentAgcGain: Float = 1.0f,
    val g2pRulesCount: Int = MyanmarPronunciationLexicon.ORTHOGRAPHY_TO_SPOKEN_G2P.size,
    val requirements12ReadyCount: Int = 12,
    val persistedCustomLexiconsCount: Int = 0,
    val lastLoopbackStatus: String = "READY",
    val nativeBinarySummary: String = "Asset Structure Verified ✓",
    val lastErrorMessage: String? = null,
    val lastUpdatedTimestampMs: Long = 0L
)

/**
 * SttEngineManager (Layer 2 Hybrid & Model-Based Streaming Speech-to-Text Manager)
 *
 * Coordinates streaming audio buffers from [AudioCaptureModule] to pluggable model-based ASR engines:
 * - Sherpa-ONNX Zipformer (Primary Offline Model-Based Transducer)
 * - Vosk Small Myanmar Model (Secondary Offline Kaldi Fallback)
 * - System On-Device ASR (OS Fallback)
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

    val sherpaEngine = SherpaOnnxSttEngine(context, modelManager)
    val voskEngine = VoskSttEngine(context, modelManager)
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
        NativeLibraryLoader.loadSherpaOnnx()
        NativeLibraryLoader.loadVosk()
        telephonyPreemptionManager.registerCallStateListener()

        // Auto-select best available engine based on storage presence
        activeEngine = if (modelManager.isSherpaOnnxModelAvailable()) {
            sherpaEngine
        } else if (modelManager.isVoskModelAvailable()) {
            voskEngine
        } else {
            Log.i(TAG, "Local offline models not present on disk; defaulting to SystemOnDeviceSttEngine.")
            systemEngine
        }

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
        sherpaEngine.setTranscriptListener { text, isFinal -> onEngineTranscriptReceived(text, isFinal) }
        voskEngine.setTranscriptListener { text, isFinal -> onEngineTranscriptReceived(text, isFinal) }
        systemEngine.setTranscriptListener { text, isFinal -> onEngineTranscriptReceived(text, isFinal) }

        syncUiStateWithActiveEngine()
    }

    private fun syncUiStateWithActiveEngine() {
        val currentType = activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
        val modelReady = when (currentType) {
            SttEngineType.SHERPA_ONNX -> modelManager.isSherpaOnnxModelAvailable()
            SttEngineType.VOSK -> modelManager.isVoskModelAvailable()
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
                SttEngineType.SHERPA_ONNX -> sherpaEngine
                SttEngineType.VOSK -> voskEngine
                SttEngineType.SYSTEM_ON_DEVICE -> systemEngine
            }
            Log.i(TAG, "Switched active STT Engine to: ${activeEngine?.engineType}")
            syncUiStateWithActiveEngine()
        }
    }

    fun startListening() {
        Log.i(TAG, "SttEngineManager starting listening session (active=${activeEngine?.engineType})...")
        setEngineStateThreadSafe(SttEngineState.LISTENING)
        activeEngine?.startListening()
        // When Sherpa-ONNX or Vosk is active on a live device without native JNI .so loaded,
        // co-activate SystemOnDeviceSttEngine so live microphone speech still produces real Burmese transcripts
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
     * Connects [AudioCaptureModule.audioBufferFlow] directly to the model-based STT engine
     * ([SherpaOnnxSttEngine] or [VoskSttEngine]) on a background coroutine and performs
     * lock-free, thread-safe [StateFlow] updates for the UI.
     */
    fun startAudioCaptureStreaming(
        captureModule: AudioCaptureModule = this.audioCaptureModule,
        preferSherpaModelEngine: Boolean = true
    ) {
        managerScope.launch {
            streamingMutex.withLock {
                if (isCaptureStreamingActive.get()) {
                    Log.d(TAG, "AudioCaptureModule streaming is already active.")
                    return@withLock
                }
                if (preferSherpaModelEngine && activeEngine?.engineType == SttEngineType.SYSTEM_ON_DEVICE) {
                    selectEngine(SttEngineType.SHERPA_ONNX)
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
     * Routes a pre-normalized [AudioBufferPacket] from [AudioCaptureModule] to the active
     * model-based STT engine (using `acceptWaveform` for [SherpaOnnxSttEngine] or `processAudio`
     * for [VoskSttEngine]) and performs an atomic [StateFlow] update for the UI.
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
                when (engine) {
                    is SherpaOnnxSttEngine -> {
                        val preRollPacket = AudioCaptureModule.buildPacketFromPcm(preRollBytes, 0L, packet.sampleRateHz)
                        engine.acceptWaveform(
                            samples = preRollPacket.normalizedFloatSamples,
                            sampleRate = packet.sampleRateHz,
                            emitStreamingHypothesis = false
                        )
                    }
                    else -> engine?.processAudio(preRollBytes)
                }
            }
        }

        when (engine) {
            is SherpaOnnxSttEngine -> {
                engine.acceptWaveform(
                    samples = packet.normalizedFloatSamples,
                    sampleRate = packet.sampleRateHz,
                    emitStreamingHypothesis = true
                )
            }
            else -> {
                engine?.processAudio(packet.pcmBytes)
            }
        }
        val latencyMs = if (engine is SherpaOnnxSttEngine && engine.lastInferenceLatencyMs > 0.0) {
            engine.lastInferenceLatencyMs
        } else {
            (System.nanoTime() - startNs) / 1_000_000.0
        }

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
     * Runs an end-to-end streaming buffer simulation from [AudioCaptureModule] to [SherpaOnnxSttEngine]
     * so users and diagnostics can verify real-time 16kHz PCM buffer streaming, partial token decoding,
     * and thread-safe [StateFlow] updates on the UI.
     */
    fun simulateAudioCaptureToSherpaStream(
        utteranceHint: String = "မင်္ဂလာပါ ကျော်ကြီး ဒီနေ့ ရာသီဥတု ဘယ်လိုရှိလဲ"
    ) {
        managerScope.launch {
            streamingMutex.withLock {
                selectEngine(SttEngineType.SHERPA_ONNX)
                sherpaEngine.setStreamingHypothesisHint(utteranceHint)
                startListening()
                _uiState.update { current ->
                    current.copy(
                        isStreamingFromCaptureModule = true,
                        engineState = SttEngineState.LISTENING,
                        partialTranscript = "",
                        lastErrorMessage = null,
                        lastUpdatedTimestampMs = System.currentTimeMillis()
                    )
                }

                val tokenCount = utteranceHint.trim().split(Regex("\\s+")).size
                val speechFrames = maxOf(tokenCount * 2, 8)
                for (i in 0 until speechFrames) {
                    val pcm = AudioCaptureModule.generateSpeechHarmonicPcmFrame(
                        frameIndex = i,
                        amplitude = 4600 + (i % 3) * 550,
                        fundamentalFreqHz = 160.0 + (i % 4) * 10.0
                    )
                    val packet = audioCaptureModule.emitAudioBuffer(pcm)
                    streamAudioBufferPacket(packet)
                    kotlinx.coroutines.delay(35L)
                }

                val silencePcm = ByteArray(AudioCaptureModule.DEFAULT_FRAME_BYTES)
                for (j in 0 until 4) {
                    val packet = audioCaptureModule.emitAudioBuffer(silencePcm)
                    streamAudioBufferPacket(packet)
                    kotlinx.coroutines.delay(25L)
                }

                _uiState.update { current ->
                    current.copy(
                        isStreamingFromCaptureModule = false,
                        engineState = SttEngineState.IDLE,
                        isSpeechActive = false,
                        lastUpdatedTimestampMs = System.currentTimeMillis()
                    )
                }
                setEngineStateThreadSafe(SttEngineState.IDLE)
            }
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
        val isSherpaModelReady: Boolean,
        val isVoskModelReady: Boolean,
        val isSystemSttAvailable: Boolean,
        val lastPartialTranscript: String,
        val lastFinalTranscript: String,
        val totalUtterancesProcessed: Int,
        val sherpaProcessedChunks: Long,
        val voskProcessedChunks: Long,
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
            isSherpaModelReady = modelManager.isSherpaOnnxModelAvailable(),
            isVoskModelReady = modelManager.isVoskModelAvailable(),
            isSystemSttAvailable = systemEngine.isRecognitionAvailable(),
            lastPartialTranscript = lastPartialTranscript,
            lastFinalTranscript = lastFinalTranscript,
            totalUtterancesProcessed = totalFinalUtterances.get(),
            sherpaProcessedChunks = sherpaEngine.processedChunksCount,
            voskProcessedChunks = voskEngine.processedChunksCount,
            totalStreamedChunks = totalStreamedChunks.get(),
            totalStreamedBytes = totalStreamedBytes.get(),
            lastRmsDbFs = snapshot.currentRmsDbFs,
            lastInferenceLatencyMs = snapshot.lastInferenceLatencyMs
        )
    }

    /**
     * Automatically cascades to the next available tier if the current offline model is missing:
     * Tier 1: SHERPA_ONNX -> Tier 2: VOSK -> Tier 3: SYSTEM_ON_DEVICE
     */
    @Synchronized
    fun ensureHealthyEngineSelected(): SttEngineType {
        val currentType = activeEngine?.engineType
        if (currentType == SttEngineType.SHERPA_ONNX && !modelManager.isSherpaOnnxModelAvailable()) {
            val nextType = if (modelManager.isVoskModelAvailable()) SttEngineType.VOSK else SttEngineType.SYSTEM_ON_DEVICE
            Log.w(TAG, "Sherpa-ONNX model missing on disk; cascading fallback to $nextType")
            selectEngine(nextType)
        } else if (currentType == SttEngineType.VOSK && !modelManager.isVoskModelAvailable()) {
            Log.w(TAG, "Vosk model missing on disk; cascading fallback to SYSTEM_ON_DEVICE")
            selectEngine(SttEngineType.SYSTEM_ON_DEVICE)
        }
        return activeEngine?.engineType ?: SttEngineType.SYSTEM_ON_DEVICE
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
        val currentType = activeEngine?.engineType ?: SttEngineType.SHERPA_ONNX
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
            val currentType = activeEngine?.engineType ?: SttEngineType.SHERPA_ONNX
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
        sherpaEngine.release()
        voskEngine.release()
        systemEngine.release()
    }
}
