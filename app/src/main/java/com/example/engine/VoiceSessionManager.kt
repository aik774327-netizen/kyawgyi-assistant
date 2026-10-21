package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.nlu.BurmeseNluPreprocessor
import com.example.router.KyawGyiSmartRouter
import com.example.utils.MyanmarSTEngine
import com.example.utils.MyanmarTTSEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/**
 * Human-Centric Voice Session Manager for Kyaw Gyi Voice Assistant
 *
 * Implements a stateful, debounced, pause-tolerant voice pipeline:
 * IDLE -> WAKE_WORD_LISTENING -> WAKE_WORD_DETECTED -> COMMAND_LISTENING ->
 * USER_SPEAKING -> USER_SILENCE_CONFIRMATION -> PROCESSING_STT -> LOCAL_NLU/AI_THINKING ->
 * EXECUTING_COMMAND -> SPEAKING_TTS -> SESSION_COMPLETE -> WAKE_WORD_LISTENING.
 *
 * Features:
 * 1. No rapid Mic ON/OFF toggling.
 * 2. Multi-pause Burmese conversational tolerance.
 * 3. Partial STT will NEVER trigger command execution.
 * 4. Music playback preservation (transient ducking during TTS only).
 * 5. Multi-turn conversational context tracking.
 * 6. Acoustic echo cancellation (ignores input during TTS).
 */
class VoiceSessionManager(
    private val context: Context,
    private val config: VoiceSessionConfig = VoiceSessionConfig()
) {

    companion object {
        private const val TAG = "VoiceSessionManager"

        @Volatile
        private var instance: VoiceSessionManager? = null

        fun getInstance(context: Context): VoiceSessionManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceSessionManager(context.applicationContext).also { instance = it }
            }
        }

        // Burmese sentence completion particles & honorifics
        private val COMPLETION_PARTICLES = listOf(
            "ပါ", "ပေးပါ", "ဖွင့်ပါ", "ပိတ်ပါ", "ရှာပါ", "ရှာပေးပါ", "ဖွင့်ပေးပါ",
            "ပြပါ", "ရွေးပါ", "ကူးပါ", "ဖျက်ပါ", "လုပ်ပေးပါ", "ပြောပါ", "နော်",
            "ဦး", "ပေးဦး", "တယ်", "မယ်", "ချင်တယ်", "သွားပါ", "ခေါ်ပါ", "ပို့ပါ"
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateMutex = Mutex()

    // State Flows
    private val _currentState = MutableStateFlow(VoiceSessionState.IDLE)
    val currentState: StateFlow<VoiceSessionState> = _currentState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow("အသင့်ရှိပါသည်")
    val userFeedbackMessage: StateFlow<String> = _userFeedbackMessage.asStateFlow()

    // Sub-systems
    private val audioManager = KyawGyiAudioManager.getInstance(context)
    private val contextManager = CommandContextManager.getInstance()
    private val smartRouter = KyawGyiSmartRouter(context)
    private val stEngine = MyanmarSTEngine()
    private var ttsEngine: MyanmarTTSEngine? = null

    // SpeechRecognizer
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSpeechRecognizerActive = false

    // Timing & Debounce trackers
    private var sessionStartTime = 0L
    private var lastSpeechTimestamp = 0L
    private var lastMicToggleTime = 0L
    private var accumulatedSpeechBuffer = StringBuilder()

    // Active Jobs
    private var initialSilenceTimeoutJob: Job? = null
    private var silenceConfirmationJob: Job? = null
    private var maxDurationWatchdogJob: Job? = null

    // Listeners
    private val listeners = mutableListOf<VoiceSessionListener>()

    init {
        ttsEngine = MyanmarTTSEngine(context) { ready ->
            Log.d(TAG, "MyanmarTTSEngine init result: $ready")
        }
    }

    fun addListener(listener: VoiceSessionListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: VoiceSessionListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyStateChanged(state: VoiceSessionState, message: String) {
        _currentState.value = state
        _userFeedbackMessage.value = message
        val listenersCopy = synchronized(listeners) { ArrayList(listeners) }
        for (l in listenersCopy) {
            l.onStateChanged(state, message)
        }
        Log.i(TAG, "[STATE TRANSITION] -> $state | Message: $message")
    }

    private fun notifyEvent(event: VoiceSessionEvent, payload: String? = null) {
        val listenersCopy = synchronized(listeners) { ArrayList(listeners) }
        for (l in listenersCopy) {
            l.onEvent(event, payload)
        }
        Log.d(TAG, "[EVENT] -> $event | Payload: $payload")
    }

    /**
     * Start Wake-word Listening Mode (Ambient Background State)
     */
    fun startWakeWordMode() {
        scope.launch {
            stateMutex.withLock {
                if (_currentState.value == VoiceSessionState.SPEAKING_TTS) {
                    Log.d(TAG, "Cannot start wake word mode while TTS is speaking.")
                    return@launch
                }
                cancelAllTimers()
                accumulatedSpeechBuffer.clear()
                _liveTranscript.value = ""
                notifyStateChanged(VoiceSessionState.WAKE_WORD_LISTENING, "ကျော်ကြီး နားထောင်ရန် အသင့်ရှိပါသည်")
                notifyEvent(VoiceSessionEvent.MIC_READY)
            }
        }
    }

    /**
     * Triggered when Wake Word ("ကျော်ကြီး") is detected
     */
    fun onWakeWordDetected() {
        scope.launch {
            stateMutex.withLock {
                if (_currentState.value == VoiceSessionState.SPEAKING_TTS) {
                    return@launch
                }
                try {
                    com.example.audio.GlobalAudioStateManager.getInstance(context).muteAllMediaPlayback("VoiceSessionManager (ကျော်ကြီး)")
                } catch (e: Exception) {
                    Log.w(TAG, "GlobalAudioStateManager wake notification error: ${e.message}")
                }
                notifyEvent(VoiceSessionEvent.MIC_STARTED, "WAKE_WORD")
                notifyStateChanged(VoiceSessionState.WAKE_WORD_DETECTED, "ဗျာ... ကျော်ကြီး အသင့်ရှိပါတယ်ခင်ဗျာ")

                // Smoothly transition to Command Listening after brief greeting / grace period
                delay(config.postWakeGracePeriodMs)
                startCommandListeningInternal()
            }
        }
    }

    /**
     * Start Command Listening Session (User starts speaking their query)
     */
    fun startCommandListening() {
        scope.launch {
            stateMutex.withLock {
                startCommandListeningInternal()
            }
        }
    }

    private fun startCommandListeningInternal() {
        val now = System.currentTimeMillis()
        if (now - lastMicToggleTime < config.micDebounceIntervalMs) {
            Log.d(TAG, "Mic toggle debounced.")
            return
        }
        lastMicToggleTime = now
        sessionStartTime = now
        lastSpeechTimestamp = now
        accumulatedSpeechBuffer.clear()
        _liveTranscript.value = ""

        cancelAllTimers()
        notifyStateChanged(VoiceSessionState.COMMAND_LISTENING, "ပြောလို့ရပါပြီ၊ နားထောင်နေပါသည်...")
        notifyEvent(VoiceSessionEvent.MIC_STARTED)

        // Launch SpeechRecognizer if available on main thread
        startRecognizerListening()

        // Schedule Initial Silence Timeout (if user never speaks after prompt)
        initialSilenceTimeoutJob = scope.launch {
            delay(config.initialListeningTimeoutMs)
            if (_currentState.value == VoiceSessionState.COMMAND_LISTENING) {
                Log.d(TAG, "Initial silence timeout reached without speech.")
                notifyEvent(VoiceSessionEvent.TIMEOUT_OCCURRED, "INITIAL_SILENCE")
                speakFeedback("အသံ မကြားရပါခင်ဗျာ", onComplete = {
                    startWakeWordMode()
                })
            }
        }

        // Schedule Maximum Command Duration Watchdog
        maxDurationWatchdogJob = scope.launch {
            delay(config.maximumCommandDurationMs)
            if (_currentState.value == VoiceSessionState.USER_SPEAKING || _currentState.value == VoiceSessionState.COMMAND_LISTENING) {
                Log.d(TAG, "Maximum command duration (20s) reached. Forcing STT finalization.")
                finalizeCommandUtterance()
            }
        }
    }

    private fun startRecognizerListening() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing.")
            notifyStateChanged(VoiceSessionState.ERROR, "Microphone ခွင့်ပြုချက် လိုအပ်ပါသည်")
            return
        }

        // Real Battery Optimization Check (Prevents unexpected screen-off mic drop)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isBatteryOptimized = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            !(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
        } else false
        if (isBatteryOptimized) {
            Log.w(TAG, "⚠️ Battery optimization is active for ${context.packageName}. Background listening will be restricted during Doze/Screen-Off.")
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device.")
            return
        }

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(InternalRecognitionListener())
                    }
                }
                val selectedLang = try {
                    com.example.data.LanguagePreferencesRepository.getInstance(context).getSelectedLanguageSync()
                } catch (e: Exception) {
                    "my-MM"
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }
                speechRecognizer?.startListening(intent)
                isSpeechRecognizerActive = true
                Log.d(TAG, "SpeechRecognizer listening started for $selectedLang")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting SpeechRecognizer: ${e.localizedMessage}")
            }
        }
    }

    private fun stopRecognizerListening() {
        mainHandler.post {
            try {
                if (isSpeechRecognizerActive) {
                    speechRecognizer?.stopListening()
                    isSpeechRecognizerActive = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping SpeechRecognizer: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Classifies pause type for Burmese conversational flow
     */
    fun classifyPause(pauseDurationMs: Long, currentTranscript: String): PauseType {
        val hasCompletionParticle = hasBurmeseCompletionParticle(currentTranscript)
        val silenceThreshold = if (hasCompletionParticle) {
            config.speechEndCompletePhraseSilenceMs
        } else {
            config.speechEndSilenceTimeoutMs
        }

        return when {
            pauseDurationMs < config.shortPauseThresholdMs -> PauseType.SHORT_PAUSE
            pauseDurationMs < silenceThreshold -> PauseType.NORMAL_PAUSE
            else -> PauseType.FINAL_SILENCE
        }
    }

    /**
     * Checks if current utterance ends with a typical Burmese sentence completion particle
     */
    fun hasBurmeseCompletionParticle(text: String): Boolean {
        val clean = stEngine.normalizeMyanmarText(text).trim()
        if (clean.isEmpty()) return false
        return COMPLETION_PARTICLES.any { clean.endsWith(it) }
    }

    /**
     * Called when SpeechRecognizer or VAD delivers partial transcript.
     * CRITICAL RULE: Partial transcript MUST NEVER execute any command.
     */
    fun onPartialResultsReceived(partialText: String) {
        scope.launch {
            stateMutex.withLock {
                if (_currentState.value == VoiceSessionState.SPEAKING_TTS) return@launch

                val clean = BurmeseNluPreprocessor.normalize(partialText).trim()
                if (clean.isEmpty()) return@launch

                initialSilenceTimeoutJob?.cancel()
                lastSpeechTimestamp = System.currentTimeMillis()
                accumulatedSpeechBuffer.clear()
                accumulatedSpeechBuffer.append(clean)
                _liveTranscript.value = clean

                if (_currentState.value != VoiceSessionState.USER_SPEAKING) {
                    notifyStateChanged(VoiceSessionState.USER_SPEAKING, "နားထောင်နေဆဲ...")
                    notifyEvent(VoiceSessionEvent.SPEECH_STARTED, clean)
                } else {
                    notifyEvent(VoiceSessionEvent.PARTIAL_TRANSCRIPT, clean)
                }

                val listenersCopy = synchronized(listeners) { ArrayList(listeners) }
                for (l in listenersCopy) {
                    l.onPartialTranscript(clean)
                }

                // Schedule Dynamic Silence Confirmation based on pause classification
                scheduleSilenceConfirmationTimer(clean)
            }
        }
    }

    private fun scheduleSilenceConfirmationTimer(currentTranscript: String) {
        silenceConfirmationJob?.cancel()
        val hasParticle = hasBurmeseCompletionParticle(currentTranscript)
        val silenceWaitMs = if (hasParticle) {
            config.speechEndCompletePhraseSilenceMs
        } else {
            config.speechEndSilenceTimeoutMs
        }

        silenceConfirmationJob = scope.launch {
            delay(silenceWaitMs)
            stateMutex.withLock {
                if (_currentState.value == VoiceSessionState.USER_SPEAKING) {
                    Log.d(TAG, "Silence confirmed ($silenceWaitMs ms reached). Finalizing speech.")
                    notifyStateChanged(VoiceSessionState.USER_SILENCE_CONFIRMATION, "စကားပြီးဆုံးမှု အတည်ပြုနေပါသည်...")
                    notifyEvent(VoiceSessionEvent.SILENCE_CONFIRMED)
                    finalizeCommandUtterance()
                }
            }
        }
    }

    /**
     * Finalize user command utterance after confirmed silence or final STT result
     */
    private fun finalizeCommandUtterance() {
        scope.launch {
            stateMutex.withLock {
                cancelAllTimers()
                stopRecognizerListening()

                val rawFinal = accumulatedSpeechBuffer.toString().trim()
                if (rawFinal.isEmpty()) {
                    Log.d(TAG, "Finalized utterance is empty. Returning to wake word mode.")
                    startWakeWordMode()
                    return@launch
                }

                // Relocated Layer 2 Post-ASR Preprocessing: Diacritic Re-ordering and Unicode Sanitization
                val preprocessedText = BurmeseNluPreprocessor.normalize(rawFinal)
                val normalizedText = stEngine.normalizeMyanmarText(preprocessedText)
                notifyStateChanged(VoiceSessionState.PROCESSING_STT, "အသံကို စာသားပြောင်းလဲနေပါသည်...")
                notifyEvent(VoiceSessionEvent.COMMAND_FINALIZED, normalizedText)

                val listenersCopy = synchronized(listeners) { ArrayList(listeners) }
                for (l in listenersCopy) {
                    l.onFinalTranscript(normalizedText, 0.95f)
                }

                // Execute Command Pipeline via SmartRouter & Local NLU
                processAndExecuteCommand(normalizedText, rawFinal)
            }
        }
    }

    private fun processAndExecuteCommand(normalizedText: String, rawOriginal: String) {
        scope.launch(Dispatchers.IO) {
            try {
                notifyStateChanged(VoiceSessionState.LOCAL_NLU, "အမိန့်ကို စစ်ဆေးနေပါသည်...")

                // Execute through KyawGyiSmartRouter
                val responseText = smartRouter.processVoiceInput(rawOriginal)

                // Track context
                contextManager.recordTurn(
                    userQuery = rawOriginal,
                    intentType = "COMMAND_EXECUTION",
                    responseText = responseText
                )

                notifyStateChanged(VoiceSessionState.EXECUTING_COMMAND, "အမိန့်ကို လုပ်ဆောင်နေပါသည်...")
                notifyEvent(VoiceSessionEvent.COMMAND_EXECUTED, responseText)

                // Speak response with TTS and ducking audio focus
                speakFeedback(responseText) {
                    notifyStateChanged(VoiceSessionState.SESSION_COMPLETE, "ပြီးပါပြီ")
                    notifyEvent(VoiceSessionEvent.TTS_COMPLETED)
                    startWakeWordMode()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in command execution: ${e.localizedMessage}")
                speakFeedback("အမိန့် လုပ်ဆောင်ရာတွင် အဆင်မပြေပါခင်ဗျာ") {
                    startWakeWordMode()
                }
            }
        }
    }

    /**
     * Safe Spoken Feedback with Audio Focus Ducking
     */
    fun speakFeedback(text: String, onComplete: (() -> Unit)? = null) {
        scope.launch {
            stateMutex.withLock {
                notifyStateChanged(VoiceSessionState.SPEAKING_TTS, text)
                notifyEvent(VoiceSessionEvent.TTS_STARTED, text)

                // Request transient ducking audio focus so background music is not killed
                audioManager.requestTtsDuckingFocus()

                ttsEngine?.speak(text) {
                    // Release audio focus immediately when TTS finishes
                    audioManager.abandonTtsFocus()
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Safe Debounced Stop Session
     */
    fun stopSession() {
        scope.launch {
            stateMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - sessionStartTime < config.minimumSessionDurationMs) {
                    Log.d(TAG, "Stop request ignored: Minimum session duration (500ms) not reached.")
                    return@launch
                }
                cancelAllTimers()
                stopRecognizerListening()
                audioManager.abandonTtsFocus()
                notifyStateChanged(VoiceSessionState.IDLE, "စနစ် အနားယူနေပါသည်")
                notifyEvent(VoiceSessionEvent.MIC_STOPPED)
            }
        }
    }

    private fun cancelAllTimers() {
        initialSilenceTimeoutJob?.cancel()
        initialSilenceTimeoutJob = null
        silenceConfirmationJob?.cancel()
        silenceConfirmationJob = null
        maxDurationWatchdogJob?.cancel()
        maxDurationWatchdogJob = null
    }

    fun destroy() {
        cancelAllTimers()
        stopRecognizerListening()
        audioManager.abandonTtsFocus()
        ttsEngine?.shutdown()
        ttsEngine = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.localizedMessage}")
        }
    }

    /**
     * RecognitionListener implementation for Android SpeechRecognizer
     */
    private inner class InternalRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "STT Engine onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "STT Engine onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "STT Engine onEndOfSpeech")
        }

        override fun onError(error: Int) {
            Log.w(TAG, "SpeechRecognizer onError code: $error")
            // Error 7 = ERROR_NO_MATCH, Error 8 = ERROR_RECOGNIZER_BUSY, Error 6 = ERROR_SPEECH_TIMEOUT
            if (_currentState.value == VoiceSessionState.USER_SPEAKING) {
                // If user had already spoken partial text, finalize with current buffer
                if (accumulatedSpeechBuffer.isNotEmpty()) {
                    finalizeCommandUtterance()
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognized = matches?.firstOrNull() ?: ""
            if (recognized.isNotBlank()) {
                accumulatedSpeechBuffer.clear()
                accumulatedSpeechBuffer.append(recognized)
            }
            finalizeCommandUtterance()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull() ?: ""
            if (partial.isNotBlank()) {
                onPartialResultsReceived(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
