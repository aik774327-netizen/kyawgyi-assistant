package com.kyawgyi.ir.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Sealed hierarchy representing voice intents processed from recognized speech.
 */
sealed class VoiceIntent(open val rawQuery: String) {
    data class WakeWordDetected(val wakeWord: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class CreateTask(val taskTitle: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class StartTimer(val minutes: Int, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class OpenApp(val appTarget: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class CloseApp(val appTarget: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class CallContact(val contactTarget: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class SendSms(val contactTarget: String, val message: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class ConfirmAction(override val rawQuery: String) : VoiceIntent(rawQuery)
    data class CancelAction(override val rawQuery: String) : VoiceIntent(rawQuery)
    data class PlayMusic(val songTarget: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class ToggleFlashlight(val enable: Boolean, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class ToggleWifi(val enable: Boolean, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class ToggleBluetooth(val enable: Boolean, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class AdjustVolume(val isIncrease: Boolean, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class AdjustBrightness(val isIncrease: Boolean, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class CheckBattery(override val rawQuery: String) : VoiceIntent(rawQuery)
    data class GetStatus(override val rawQuery: String) : VoiceIntent(rawQuery)
    data class StopListening(override val rawQuery: String) : VoiceIntent(rawQuery)
    data class OpenSettings(val destinationName: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class GeneralQuery(val prompt: String, override val rawQuery: String) : VoiceIntent(rawQuery)
    data class Unknown(override val rawQuery: String) : VoiceIntent(rawQuery)
}

/**
 * VoiceEngine manages:
 * 1. Android's TextToSpeech (TTS) engine initialized with 'my-MM' (Myanmar) locale.
 * 2. SpeechRecognizer integration for processing continuous voice commands and intents.
 */
class VoiceEngine(
    private val context: Context,
    private val onIntentRecognized: ((VoiceIntent) -> Unit)? = null,
    private val onSpeechText: ((String) -> Unit)? = null,
    private val onPartialSpeechText: ((String) -> Unit)? = null,
    private val onSpeechStatusChanged: ((String) -> Unit)? = null,
    private val onRecognitionFinished: (() -> Unit)? = null
) : TextToSpeech.OnInitListener, RecognitionListener {

    companion object {
        private const val TAG = "KyawGyi_VoiceEngine"
        val MYANMAR_LOCALE = Locale.forLanguageTag("my-MM")
        val MYANMAR_ALT_LOCALE = Locale.forLanguageTag("my")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val languageRepo = com.example.data.LanguagePreferencesRepository.getInstance(context)
    private var currentLanguageCode: String = languageRepo.getSelectedLanguageSync()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _isOfflineSpeechWarning = MutableStateFlow(false)
    val isOfflineSpeechWarning: StateFlow<Boolean> = _isOfflineSpeechWarning.asStateFlow()

    private var shouldKeepListening = false
    private var consecutiveErrors = 0
    private var isDestroyed = false

    init {
        initializeTTS()
        initializeSpeechRecognizer()
        observeLanguagePreferences()
    }

    private fun observeLanguagePreferences() {
        coroutineScope.launch {
            try {
                languageRepo.selectedLanguageState.collect { newLang ->
                    if (newLang.isNotBlank() && newLang != currentLanguageCode) {
                        Log.i(TAG, "DataStore language preference updated: $newLang")
                        updateLocale(newLang)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notice observing DataStore language: ${e.message}")
            }
        }
    }

    /**
     * Updates language locale dynamically for both TTS and STT.
     */
    fun updateLocale(languageCode: String) {
        currentLanguageCode = languageCode
        applyLocaleToTts(languageCode)
        if (_isListening.value) {
            stopListeningInternal()
            mainHandler.postDelayed({
                if (shouldKeepListening && !isDestroyed) {
                    startListening()
                }
            }, 300)
        }
    }

    // =========================================================================
    // TEXT-TO-SPEECH (TTS) INITIALIZATION & CONTROL (DATASTORE LOCALE BACKED)
    // =========================================================================

    private fun initializeTTS() {
        try {
            Log.i(TAG, "Initializing TextToSpeech engine with DataStore locale '$currentLanguageCode'...")
            val preferredEngine = com.example.utils.MyanmarTTSVoicePackManager.getBestTtsEnginePackage(context)
            tts = if (preferredEngine != null) {
                TextToSpeech(context.applicationContext, this, preferredEngine)
            } else {
                TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech: ${e.message}", e)
        }
    }

    private fun applyLocaleToTts(langCode: String) {
        val targetLocale = if (langCode.startsWith("en", ignoreCase = true)) {
            Locale.US
        } else {
            var localeResult = tts?.setLanguage(MYANMAR_LOCALE) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale 'my-MM' not fully supported by default engine, trying alternative 'mya-MMR'...")
                localeResult = tts?.setLanguage(MYANMAR_ALT_LOCALE) ?: TextToSpeech.LANG_NOT_SUPPORTED
            }

            if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Myanmar locale missing in system TTS engine. Fallback to US locale.")
                Locale.US
            } else {
                MYANMAR_LOCALE
            }
        }
        tts?.setLanguage(targetLocale)
        Log.i(TAG, "TTS successfully configured with locale: ${targetLocale.toLanguageTag()} (code: $langCode)")
    }

    override fun onInit(status: Int) {
        val voicePackManager = com.example.utils.MyanmarTTSVoicePackManager.getInstance(context)
        voicePackManager.updateTtsInstance(tts, status)

        if (status == TextToSpeech.SUCCESS) {
            applyLocaleToTts(currentLanguageCode)

            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.95f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    // Resume listening if continuous background listening is active
                    if (shouldKeepListening && !isDestroyed) {
                        mainHandler.postDelayed({
                            startListening()
                        }, 500)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    Log.w(TAG, "TTS playback error code: $errorCode")
                }
            })

            _isTtsReady.value = true
            Log.i(TAG, "TextToSpeech initialized successfully.")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
            _isTtsReady.value = false
        }
    }

    /**
     * Synthesizes and speaks text using Myanmar TTS
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, utteranceId: String = "VoiceEngine_Utterance") {
        if (text.isBlank()) return

        // Layer 6 & Layer 2 TTS Text Normalizer & G2P Expansion (Percentages, Time, Currency, Phone Numbers, Acronyms)
        val normalizedSpeechText = com.example.nlu.BurmeseTtsTextNormalizer.normalizeForTts(text)

        // Temporarily pause recognizer while speaking to prevent self-triggering
        if (_isListening.value) {
            stopListeningInternal()
        }

        // Universal Burmese Phonetic G2P Fallback when native my-MM voice pack is missing
        val voicePackManager = com.example.utils.MyanmarTTSVoicePackManager.getInstance(context)
        val finalSpeechPayload = if (
            com.example.ocr.MyanmarScriptDetector.containsMyanmar(normalizedSpeechText) &&
            !voicePackManager.canSafelyVocalizeMyanmar()
        ) {
            tts?.language = Locale.US
            com.example.utils.MyanmarTTSEngine.transliterateBurmeseToSpeakablePhonetics(normalizedSpeechText)
        } else {
            normalizedSpeechText
        }

        if (_isTtsReady.value && tts != null) {
            _isSpeaking.value = true
            tts?.speak(finalSpeechPayload, queueMode, null, utteranceId)
            Log.d(TAG, "Speaking (Normalized): $finalSpeechPayload [Raw: $text]")
        } else {
            Log.w(TAG, "TTS not ready yet, unable to speak text.")
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    // =========================================================================
    // SPEECH RECOGNIZER & INTENT PROCESSING
    // =========================================================================

    private var useDefaultLocaleFallback = false

    private fun initializeSpeechRecognizer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { initializeSpeechRecognizer() }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer is not available on this system.")
            return
        }

        destroySpeechRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceEngine)
            }
            Log.i(TAG, "SpeechRecognizer instance created successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SpeechRecognizer: ${e.message}", e)
        }
    }

    /**
     * Starts listening for speech commands with Myanmar language preference
     */
    fun startListening() {
        if (isDestroyed) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }

        // Ensure raw AudioRecord wake-word capture is released before SpeechRecognizer acquires mic
        try {
            com.example.wakeword.KyawGyiWakeWordEngine.getInstance(context).stopListening()
        } catch (_: Exception) {}

        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }

        shouldKeepListening = true

        val effectiveLang = if (useDefaultLocaleFallback) {
            Locale.getDefault().toLanguageTag()
        } else {
            currentLanguageCode
        }

        val isOnline = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, effectiveLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, effectiveLang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "my", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (!isOnline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            onSpeechStatusChanged?.invoke("အသံနားထောင်နေပါသည် (Listening...)")
            Log.d(TAG, "SpeechRecognizer started listening with locale: '$effectiveLang' (online=$isOnline)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        shouldKeepListening = false
        stopListeningInternal()
    }

    private fun stopListeningInternal() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListeningInternal() }
            return
        }

        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        }
        _isListening.value = false
    }

    // =========================================================================
    // RECOGNITION LISTENER CALLBACKS
    // =========================================================================

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        consecutiveErrors = 0
        Log.d(TAG, "SpeechRecognizer: Ready for speech")
        onSpeechStatusChanged?.invoke("အသံလက်ခံရန် အဆင်သင့်ဖြစ်ပါပြီ")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "SpeechRecognizer: Beginning of speech")
        onSpeechStatusChanged?.invoke("အသံဖမ်းယူနေပါသည်...")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Microphone audio power level in dB
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        Log.d(TAG, "SpeechRecognizer: End of speech")
        onSpeechStatusChanged?.invoke("အသံဆန်းစစ်နေပါသည်...")
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "အသံဖမ်းယူမှု အမှား (Audio recording error)"
            SpeechRecognizer.ERROR_CLIENT -> "စနစ်ဆိုင်ရာ ချို့ယွင်းချက် (Client error)"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည် (Permissions needed)"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> {
                _isOfflineSpeechWarning.value = true
                "အင်တာနက် မရှိပါသဖြင့် အသံဖမ်းယူမှု မအောင်မြင်ပါ (အင်တာနက်ဖွင့်ပါ သို့မဟုတ် အော့ဖ်လိုင်းစကားသံ ဒေါင်းလုဒ်ရယူပါ)"
            }
            SpeechRecognizer.ERROR_NO_MATCH -> {
                _isOfflineSpeechWarning.value = false
                "နားမလည်ပါ (ရှင်းလင်းစွာ ထပ်မံပြောဆိုပါ)"
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "လုပ်ဆောင်နေဆဲဖြစ်ပါသည် (Recognizer busy)"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "စကားသံ မကြားရပါ (Speech timeout)"
            // Error 12 = ERROR_LANGUAGE_NOT_SUPPORTED, Error 13 = ERROR_LANGUAGE_UNAVAILABLE
            12, 13 -> {
                useDefaultLocaleFallback = true
                "မြန်မာအသံ پیکမရှိသေးသဖြင့် Multi-Language Fallback စနစ်သို့ ပြောင်းလဲချိတ်ဆက်နေပါသည်..."
            }
            else -> "Speech recognizer error $error"
        }
        Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
        onSpeechStatusChanged?.invoke(errorMsg)

        consecutiveErrors++
        onRecognitionFinished?.invoke()

        // Automatically restart listening if background listening is enabled and not destroyed
        if (shouldKeepListening && !isDestroyed) {
            val restartDelay = if (consecutiveErrors > 3) 2500L else 1200L
            mainHandler.postDelayed({
                if (shouldKeepListening && !isDestroyed) {
                    startListening()
                }
            }, restartDelay)
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        _isOfflineSpeechWarning.value = false
        consecutiveErrors = 0

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val rawText = matches?.firstOrNull()?.trim() ?: ""
        // Layer 6 Preprocessor: Diacritics, BurmeseNumberNormalizer, and BurmesePhoneticLexicon Code-Switching
        val text = com.example.nlu.BurmeseNluPreprocessor.normalize(rawText)

        if (text.isNotBlank()) {
            Log.i(TAG, "Speech Recognized: '$rawText' -> Normalized: '$text'")
            _lastRecognizedText.value = text
            try {
                com.kyawgyi.voice.SttEngineManager.getInstance(context).recordLiveExternalTranscript(text, isFinal = true)
            } catch (_: Exception) {}
            onSpeechText?.invoke(text)

            // Process recognized speech into structured voice intent
            val intent = processVoiceIntent(text)
            Log.i(TAG, "Processed Voice Intent: $intent")
            onIntentRecognized?.invoke(intent)
        }

        onRecognitionFinished?.invoke()

        // Keep listening in background if continuous mode is enabled
        if (shouldKeepListening && !isDestroyed) {
            mainHandler.postDelayed({
                if (shouldKeepListening && !isDestroyed) {
                    startListening()
                }
            }, 800)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partialText = matches?.firstOrNull()?.trim() ?: ""
        if (partialText.isNotBlank()) {
            try {
                com.kyawgyi.voice.SttEngineManager.getInstance(context).recordLiveExternalTranscript(partialText, isFinal = false)
            } catch (_: Exception) {}
            onPartialSpeechText?.invoke(partialText)

            val lower = partialText.lowercase()
            if (lower.contains("ကျော်ကြီး") || lower.contains("kyaw gyi") || lower.contains("kyawgyi")) {
                Log.i(TAG, "Wake word detected in partial speech! Instantly muting media playback...")
                try {
                    com.example.audio.GlobalAudioStateManager.getInstance(context)
                        .muteAllMediaPlayback("VoiceEngine (Partial Wake Word)")
                } catch (e: Exception) {
                    Log.w(TAG, "Audio muting notice: ${e.message}")
                }
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // =========================================================================
    // VOICE INTENT PROCESSING LOGIC
    // =========================================================================

    /**
     * Analyzes transcribed Myanmar & English text to determine user intent.
     */
    fun processVoiceIntent(rawSpeech: String): VoiceIntent {
        var lower = rawSpeech.lowercase().trim()

        // Strip wake word prefix if present
        val containsWakeWord = lower.contains("ကျော်ကြီး") || lower.contains("kyaw gyi") || lower.contains("kyawgyi")
        if (containsWakeWord) {
            try {
                com.example.audio.GlobalAudioStateManager.getInstance(context)
                    .onWakeWordDetected("VoiceEngine (Kyaw Gyi wake word)")
            } catch (e: Exception) {
                Log.w(TAG, "Audio muting notice: ${e.message}")
            }
            lower = lower
                .replace("ကျော်ကြီး", "")
                .replace("kyaw gyi", "")
                .replace("kyawgyi", "")
                .trim()

            // If utterance only contained the wake word itself, return WakeWordDetected
            if (lower.isBlank() || lower == "ဗျာ" || lower == "မင်္ဂလာပါ") {
                return VoiceIntent.WakeWordDetected("Kyaw Gyi", rawSpeech)
            }
        }

        // Intercept pending user confirmations for sensitive native operations (Call/SMS)
        val confirmationCoordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
        if (confirmationCoordinator.hasPendingAction()) {
            if (confirmationCoordinator.isConfirmation(lower)) {
                Log.i(TAG, "User voice confirmation received for pending communication!")
                return VoiceIntent.ConfirmAction(rawSpeech)
            } else if (confirmationCoordinator.isCancellation(lower)) {
                Log.i(TAG, "User voice cancellation received for pending communication!")
                return VoiceIntent.CancelAction(rawSpeech)
            }
        }

        return when {
            // Confirmation / Cancellation keywords
            confirmationCoordinator.isConfirmation(lower) && (lower.length <= 15 || lower.startsWith("ဟုတ်")) -> {
                VoiceIntent.ConfirmAction(rawSpeech)
            }
            confirmationCoordinator.isCancellation(lower) && (lower.length <= 15 || lower.contains("မခေါ်") || lower.contains("မလုပ်")) -> {
                VoiceIntent.CancelAction(rawSpeech)
            }

            // Stop / Cancel intents (အသံနားထောင်ခြင်း ရပ်တန့်ရန်)
            lower == "ရပ်" || lower == "stop" || lower == "cancel" ||
                    lower.contains("နားထောင်တာရပ်") || lower.contains("စကားပြောတာရပ်") || lower.contains("stop listening") -> {
                VoiceIntent.StopListening(rawSpeech)
            }

            // Task / Todo creation intents
            lower.contains("တာဝန်") || lower.contains("မှတ်") || lower.contains("လုပ်ဆောင်ရန်") ||
                    lower.contains("task") || lower.contains("todo") || lower.contains("reminder") -> {
                val cleanTitle = rawSpeech
                    .replace(Regex("(?i)(တာဝန်အသစ်|တာဝန်မှတ်မယ်|မှတ်ထားပါ|လုပ်ဆောင်ရန်|task|todo|add task)"), "")
                    .trim()
                val finalTitle = if (cleanTitle.isNotBlank()) cleanTitle else rawSpeech
                VoiceIntent.CreateTask(finalTitle, rawSpeech)
            }

            // Timer / Pomodoro / Focus intents
            lower.contains("အချိန်") || lower.contains("မိနစ်") || lower.contains("နာရီ") ||
                    lower.contains("timer") || lower.contains("focus") || lower.contains("pomodoro") -> {
                val minutesMatch = Regex("(\\d+)\\s*(မိနစ်|minutes|min)").find(lower)
                val minutes = minutesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 25
                VoiceIntent.StartTimer(minutes, rawSpeech)
            }

            // SMS / Text messaging intents (ဥပမာ.. စာပို့၊ မက်ဆေ့ပို့၊ sms ပို့၊ send message)
            lower.contains("မက်ဆေ့") || lower.contains("စာပို့") || lower.contains("sms") || lower.contains("မက်ဆေ့ခ်ျ") -> {
                val cleanTarget = rawSpeech
                    .replace(Regex("(?i)(မက်ဆေ့ခ်ျပို့ပါ|မက်ဆေ့ခ်ျပို့ပေးပါ|မက်ဆေ့ခ်ျပို့|မက်ဆေ့ပို့ပါ|မက်ဆေ့ပို့ပေးပါ|မက်ဆေ့ပို့|စာပို့ပါ|စာပို့ပေးပါ|စာပို့|sms ပို့|send sms|send message)"), "")
                    .trim()
                val target = if (cleanTarget.isNotBlank()) cleanTarget else rawSpeech
                VoiceIntent.SendSms(target, "", rawSpeech)
            }

            // Phone Call / Contact calling intents (ဥပမာ.. ဖုန်းခေါ်၊ ဖုန်းဆက်၊ call [name])
            lower.contains("ဖုန်းခေါ်") || lower.contains("ဖုန်းဆက်") || lower.contains("ခေါ်ပေး") ||
                    lower.contains("ခေါ်ပါ") || lower.contains("ဆက်ပေး") || lower.contains("call ") ||
                    lower.startsWith("call") || lower.contains("dial") -> {
                val cleanTarget = rawSpeech
                    .replace(Regex("(?i)(ဖုန်းခေါ်ပါ|ဖုန်းခေါ်ပေးပါ|ဖုန်းခေါ်|ဖုန်းဆက်ပါ|ဖုန်းဆက်ပေးပါ|ဖုန်းဆက်|ခေါ်ပေးပါ|ခေါ်ပါ|ဆက်သွယ်ပါ|call to|call|dial)"), "")
                    .trim()
                val target = if (cleanTarget.isNotBlank()) cleanTarget else rawSpeech
                VoiceIntent.CallContact(target, rawSpeech)
            }

            // Music / MP3 Song playback intents (ဥပမာ.. သီချင်းဖွင့်၊ mp3 ဖွင့်၊ play [song])
            lower.contains("သီချင်း") || lower.contains("mp3") || lower.contains("play ") ||
                    lower.startsWith("play") || lower.contains("music") || lower.contains("သီချင်းဖွင့်") -> {
                val cleanSong = rawSpeech
                    .replace(Regex("(?i)(သီချင်းဖွင့်ပါ|သီချင်းဖွင့်ပေးပါ|သီချင်းဖွင့်|mp3 ဖွင့်ပါ|mp3 ဖွင့်|ဖွင့်ပေးပါ|ဖွင့်ပါ|ဖွင့်|play song|play music|play)"), "")
                    .trim()
                val songTitle = if (cleanSong.isNotBlank()) cleanSong else rawSpeech
                VoiceIntent.PlayMusic(songTitle, rawSpeech)
            }

            // Flashlight / Torch intents (ဥပမာ.. ဓာတ်မီးဖွင့်၊ ဓာတ်မီးပိတ်၊ flashlight on/off)
            lower.contains("ဓာတ်မီး") || lower.contains("flashlight") || lower.contains("torch") -> {
                val isOff = lower.contains("ပိတ်") || lower.contains("off") || lower.contains("close")
                VoiceIntent.ToggleFlashlight(enable = !isOff, rawQuery = rawSpeech)
            }

            // Wi-Fi intents (ဥပမာ.. ဝိုင်ဖိုင်ဖွင့်၊ ဝိုင်ဖိုင်ပိတ်၊ wifi on/off)
            lower.contains("ဝိုင်ဖိုင်") || lower.contains("wifi") || lower.contains("wi-fi") -> {
                val isOff = lower.contains("ပိတ်") || lower.contains("off") || lower.contains("close")
                VoiceIntent.ToggleWifi(enable = !isOff, rawQuery = rawSpeech)
            }

            // Bluetooth intents (ဥပမာ.. ဘလူးတုဖွင့်၊ ဘလူးတုပိတ်၊ bluetooth on/off)
            lower.contains("ဘလူးတု") || lower.contains("bluetooth") -> {
                val isOff = lower.contains("ပိတ်") || lower.contains("off") || lower.contains("close")
                VoiceIntent.ToggleBluetooth(enable = !isOff, rawQuery = rawSpeech)
            }

            // Volume adjustment intents (ဥပမာ.. အသံတိုး၊ အသံလျှော့၊ အသံချဲ့၊ volume up/down)
            lower.contains("အသံ") && (lower.contains("တိုး") || lower.contains("လျှော့") || lower.contains("ချဲ့") || lower.contains("မြှင့်") || lower.contains("down") || lower.contains("up")) ||
                    lower.contains("volume") -> {
                val isIncrease = lower.contains("တိုး") || lower.contains("ချဲ့") || lower.contains("မြှင့်") || lower.contains("up") || lower.contains("increase")
                VoiceIntent.AdjustVolume(isIncrease = isIncrease, rawQuery = rawSpeech)
            }

            // Brightness adjustment intents (ဥပမာ.. အလင်းတိုး၊ အလင်းလျှော့၊ brightness up/down)
            lower.contains("အလင်း") || lower.contains("brightness") -> {
                val isIncrease = lower.contains("တိုး") || lower.contains("မြှင့်") || lower.contains("တင်") || lower.contains("up") || lower.contains("increase")
                VoiceIntent.AdjustBrightness(isIncrease = isIncrease, rawQuery = rawSpeech)
            }

            // Battery check intents (ဥပမာ.. ဘက်ထရီ၊ battery status)
            lower.contains("ဘက်ထရီ") || lower.contains("battery") -> {
                VoiceIntent.CheckBattery(rawSpeech)
            }

            // Direct System Settings & Permissions Navigation (ဥပမာ.. ဆက်တင်ဖွင့်၊ wifi setting ဖွင့်၊ bluetooth setting ဖွင့်၊ accessibility ခွင့်ပြုချက်ဖွင့်)
            (lower.contains("ဆက်တင်") || lower.contains("setting") || lower.contains("ခွင့်ပြုချက်") || lower.contains("permission")) &&
                    (lower.contains("ဖွင့်") || lower.contains("သွား") || lower.contains("ပြ") || lower.contains("open") || lower.contains("show")) -> {
                val resolvedDest = com.example.router.DirectSettingsRouter.getInstance(context).resolveSettingsDestination(rawSpeech)
                VoiceIntent.OpenSettings(resolvedDest?.name ?: "APPLICATION_DETAILS", rawSpeech)
            }

            // App opening intents (ဥပမာ.. Facebook ဖွင့်၊ YouTube ဖွင့်ပါ၊ open camera)
            lower.contains("ဖွင့်") || lower.contains("open") || lower.contains("launch") -> {
                val appTarget = rawSpeech
                    .replace(Regex("(?i)(အပလီကေးရှင်းဖွင့်ပါ|အပလီကေးရှင်း|အက်ပ်ဖွင့်ပါ|အက်ပ်|ဖွင့်ပေးပါ|ဖွင့်ပါ|ဖွင့်|open app|open|launch)"), "")
                    .trim()
                VoiceIntent.OpenApp(appTarget, rawSpeech)
            }

            // App closing / Home intents (ဥပမာ.. Facebook ပိတ်၊ ပိတ်လိုက်ပါ၊ close app)
            lower.contains("ပိတ်") || lower.contains("close") || lower.contains("exit") -> {
                val appTarget = rawSpeech
                    .replace(Regex("(?i)(အပလီကေးရှင်းပိတ်ပါ|အပလီကေးရှင်း|အက်ပ်ပိတ်ပါ|အက်ပ်|ပိတ်ပေးပါ|ပိတ်လိုက်ပါ|ပိတ်ပါ|ပိတ်|close app|close|exit)"), "")
                    .trim()
                VoiceIntent.CloseApp(appTarget, rawSpeech)
            }

            // Status checking intents
            lower.contains("အခြေအနေ") || lower.contains("status") || lower.contains("ဘယ်လိုလဲ") || lower.contains("check") -> {
                VoiceIntent.GetStatus(rawSpeech)
            }

            // General query fallback
            else -> {
                VoiceIntent.GeneralQuery(rawSpeech, rawSpeech)
            }
        }
    }

    // =========================================================================
    // CLEANUP & LIFECYCLE
    // =========================================================================

    private fun destroySpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    fun shutdown() {
        isDestroyed = true
        shouldKeepListening = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Notice cancelling scope: ${e.message}")
        }

        destroySpeechRecognizer()

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        _isTtsReady.value = false
        _isListening.value = false
        _isSpeaking.value = false
    }
}
