package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Status snapshot of the 12 production voice engineering subsystems.
 */
data class VoiceEngine12Status(
    val isInitialized: Boolean = false,
    val activeSttEngine: SttEngineType = SttEngineType.SHERPA_ONNX,
    val isAecActive: Boolean = false,
    val isTelephonyPreempted: Boolean = false,
    val isMicMuted: Boolean = false,
    val currentGain: Float = 1.0f,
    val bufferPoolHitRatePercent: Float = 100f,
    val activeHotwordsCount: Int = 0,
    val assistantState: AssistantState = AssistantState.IDLE,
    val isFloatingOverlayRunning: Boolean = false
)

/**
 * VoiceEngine12 (Master Unified Facade for the 12 Core Voice Engineering Capabilities)
 *
 * Integrates all 12 hardened subsystems into a single high-level API:
 * 1. Model Management & Zero-copy noCompress memory mapping
 * 2. AudioFocus & Acoustic Echo Cancellation (AEC) Self-Listening Guard
 * 3. AudioBufferPool - Zero-allocation PCM 16kHz byte/float buffers
 * 4. DualSttFailover - Automatic engine health quarantine and fallback
 * 5. CodeSwitchNormalizer & HotwordBiasing - Myanmar-English bilingual loanwords
 * 6. StreamingTtsQueue - Sentence chunking with low time-to-first-audio
 * 7. FailureMemory - Offline diagnostic error-learning
 * 8. VoiceForegroundService & HeadsetPushToTalkReceiver
 * 9. TelephonyPreemption - Phone call mic preemption
 * 10. DynamicAgc & AudioClippingLimiter - Level control and anti-clipping
 * 11. MyanmarVerbalizer & MyanmarIntonationPlanner - Spoken units and prosody
 * 12. VoicePowerManager & FloatingAssistantService - Power-aware lifecycle & overlay
 */
class VoiceEngine12 private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VoiceEngine12"

        @Volatile
        private var instance: VoiceEngine12? = null

        fun getInstance(context: Context): VoiceEngine12 {
            return instance ?: synchronized(this) {
                instance ?: VoiceEngine12(context.applicationContext).also { instance = it }
            }
        }
    }

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 12 Subsystems instances
    val aecManager = AudioFocusAndAecManager.getInstance(context)
    val audioBufferPool = AudioBufferPool(defaultByteBufferSize = 1280, defaultFloatBufferSize = 640)
    val failureMemory = FailureMemory(quarantineThreshold = 2, baseCooldownMs = 30_000L)
    val modelManager = ModelManager(context)
    val dualSttFailover = DualSttFailover(modelManager, failureMemory)
    val hotwordBiasing = HotwordBiasing()
    val codeSwitchNormalizer = CodeSwitchNormalizer()
    val screenContextHotwordManager = ScreenContextHotwordManager(hotwordBiasing)
    val streamingTtsQueue = StreamingTtsQueue()
    val dynamicAgc = DynamicAgc(targetRms = 4200.0, noiseGateRmsFloor = 180.0)
    val clippingLimiter = AudioClippingLimiter(kneeThreshold = 0.82f, hardLimit = 0.98f)
    val verbalizer = MyanmarVerbalizer()
    val intonationPlanner = MyanmarIntonationPlanner()
    val powerManager = VoicePowerManager(context)
    val muteMonitor = MicrophoneMuteMonitor(context)
    val twoStageWakeWord = TwoStageWakeWordDetector(context)
    val comfortPadding = AudioTrackComfortPadding(sampleRateHz = 16000)

    val telephonyPreemption = TelephonyPreemption(
        context = context,
        onPreemptPause = {
            Log.i(TAG, "Telephony interruption -> Pausing voice ingestion")
            _status.update { it.copy(isTelephonyPreempted = true) }
        },
        onPreemptResume = {
            Log.i(TAG, "Telephony interruption resolved -> Resuming voice ingestion")
            _status.update { it.copy(isTelephonyPreempted = false) }
        }
    )

    private val _status = MutableStateFlow(VoiceEngine12Status())
    val status: StateFlow<VoiceEngine12Status> = _status.asStateFlow()

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        Log.i(TAG, "Initializing VoiceEngine12 master coordinator...")
        engineScope.launch {
            // Observe Assistant State
            AssistantStateStore.getInstance().state.collect { state ->
                _status.update { it.copy(assistantState = state) }
            }
        }

        engineScope.launch {
            // Observe Mic Mute Monitor
            muteMonitor.muteStatus.collect { muteState ->
                _status.update { it.copy(isMicMuted = muteState.isEffectivelyMuted) }
            }
        }

        _status.update {
            it.copy(
                isInitialized = true,
                activeHotwordsCount = hotwordBiasing.getAllEntries().size,
                currentGain = dynamicAgc.currentGain
            )
        }
        Log.i(TAG, "VoiceEngine12 successfully initialized.")
    }

    /**
     * Conditions a raw 16-bit Mono PCM buffer through the dual-DSP conditioning chain:
     * AGC amplification -> Soft-Knee Tanh Limiter -> Pre-roll buffer.
     */
    fun processIncomingPcmFrame(rawPcm: ByteArray, length: Int = rawPcm.size): ByteArray {
        // Step 1: Dynamic Gain Control
        dynamicAgc.processPcm16Le(rawPcm)
        // Step 2: Soft Limiter to prevent clipping
        clippingLimiter.processPcm16LeInPlace(rawPcm, length)
        // Step 3: Inspect for hardware silence mute
        muteMonitor.inspectPcmFrame(rawPcm)
        return rawPcm
    }

    /**
     * Verbalizes and synthesizes a response text for offline speech synthesis.
     */
    fun prepareTextForTts(rawText: String): String {
        return verbalizer.verbalizeForTts(rawText)
    }

    /**
     * Starts the system floating assistant overlay bubble.
     */
    fun startFloatingAssistant() {
        FloatingAssistantService.start(context)
        _status.update { it.copy(isFloatingOverlayRunning = true) }
    }

    /**
     * Stops the system floating assistant overlay bubble.
     */
    fun stopFloatingAssistant() {
        FloatingAssistantService.stop(context)
        _status.update { it.copy(isFloatingOverlayRunning = false) }
    }
}
