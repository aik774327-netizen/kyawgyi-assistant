package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.utils.MyanmarTTSEngine
import java.util.Locale

/**
 * Kyaw Gyi Core Engine
 * Manages Speech-To-Text (STT), Text-To-Speech (TTS), and Audio Focus with background music preservation.
 */
class KyawGyiCoreEngine(
    private val context: Context,
    private val callback: EngineCallback
) : TextToSpeech.OnInitListener, RecognitionListener {

    private val TAG = "KYAW_GYI_CORE"

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val audioManager = KyawGyiAudioManager.getInstance(context)

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isTtsReady = false
    private var isListening = false

    init {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            initSpeechRecognizer()
        } else {
            mainHandler.post { initSpeechRecognizer() }
        }
        initTTS()
    }

    // -------------------------------------------------------------
    // 1. SPEECH RECOGNIZER (STT) INITIALIZATION
    // -------------------------------------------------------------
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(this)
                Log.i(TAG, "SpeechRecognizer Ready.")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing SpeechRecognizer", e)
            }
        } else {
            Log.e(TAG, "Speech Recognition is NOT available on this hardware.")
            callback.onError(-1, "စက်တွင် အသံဖမ်းယူစနစ် (Speech Engine) မရှိပါ")
        }
    }

    fun startListening() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }

        if (isListening) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing before starting speech recognizer")
            callback.onError(-2, "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်")
            return
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer is null. Attempting re-init.")
            initSpeechRecognizer()
            if (speechRecognizer == null) {
                callback.onError(-1, "စက်တွင် အသံဖမ်းယူစနစ် အဆင်သင့်မဖြစ်သေးပါ")
                return
            }
        }

        // Interrupt TTS if speaking
        if (tts?.isSpeaking == true) {
            tts?.stop()
            audioManager.abandonTtsFocus()
        }

        // Note: Do NOT request exclusive audio focus during microphone listening.
        // This ensures background music (YouTube / Spotify) continues playing uninterrupted.

        val prefRepo = com.example.data.LanguagePreferencesRepository.getInstance(context)
        val selectedLang = prefRepo.getSelectedLanguageSync()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1800L)
            putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1400L)
            putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 500L)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            callback.onStateChanged(EngineState.LISTENING, "နားထောင်နေပါသည်...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            isListening = false
            callback.onError(-2, "အသံဖမ်းယူမှု စတင်၍ မရပါ: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }

        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer", e)
        }
        isListening = false
        callback.onStateChanged(EngineState.IDLE, "နားထောင်မှု ရပ်တန့်ခဲ့သည်")
    }

    // -------------------------------------------------------------
    // 2. TEXT-TO-SPEECH (TTS) INITIALIZATION
    // -------------------------------------------------------------
    private fun initTTS() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val prefRepo = com.example.data.LanguagePreferencesRepository.getInstance(context)
            val selectedLocale = prefRepo.getSelectedLocaleSync()
            val result = tts?.setLanguage(selectedLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (selectedLocale.language == "my") {
                    tts?.setLanguage(Locale.forLanguageTag("my"))
                } else {
                    tts?.language = Locale.getDefault()
                }
            }

            prefRepo.addOnLanguageChangeListener { _, newLocale ->
                try {
                    tts?.setLanguage(newLocale)
                } catch (ignored: Exception) {}
            }

            // TTS Utterance progress listener with audio focus restoration
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    callback.onStateChanged(EngineState.SPEAKING, "အသံထွက်နေပါသည်...")
                }

                override fun onDone(utteranceId: String?) {
                    audioManager.abandonTtsFocus()
                    callback.onStateChanged(EngineState.IDLE, "အသင့်ရှိပါသည်")
                }

                override fun onError(utteranceId: String?) {
                    audioManager.abandonTtsFocus()
                    callback.onError(-3, "TTS အသံထွက် ထုတ်ရာတွင် အမှားဖြစ်ပေါ်ခဲ့သည်")
                }
            })

            isTtsReady = true
            Log.i(TAG, "TTS Engine Ready.")
        } else {
            Log.e(TAG, "TTS Engine Initialization Failed!")
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || !MyanmarTTSEngine.masterTtsEnabled) {
            Log.d(TAG, "TTS Engine is not ready or disabled. Skipping speak.")
            return
        }

        val cleanText = MyanmarTTSEngine.preprocessText(text)
        if (cleanText.isBlank()) return

        // Stop active listening if speaking
        if (isListening) {
            stopListening()
        }

        // Request transient ducking audio focus so background music ducks smoothly instead of stopping
        audioManager.requestTtsDuckingFocus()

        val utteranceId = "KYAW_GYI_UTTERANCE_${System.currentTimeMillis()}"
        tts?.setSpeechRate(0.9f)
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    // -------------------------------------------------------------
    // 3. SPEECH RECOGNITION CALLBACKS
    // -------------------------------------------------------------
    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull() ?: ""

        Log.i(TAG, "Speech Result: $recognizedText")
        callback.onStateChanged(EngineState.PROCESSING, "အဓိပ္ပာယ်ဖော်ယူနေသည်...")
        callback.onSpeechResult(recognizedText)
    }

    override fun onError(error: Int) {
        isListening = false
        val errorMessage = mapSpeechError(error)
        Log.e(TAG, "Speech Error Code: $error ($errorMessage)")
        callback.onError(error, errorMessage)
    }

    private fun mapSpeechError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "အသံဖမ်းယူမှု အဆင်မပြေပါ"
            SpeechRecognizer.ERROR_CLIENT -> "Client စနစ် အမှားဖြစ်ပေါ်သည်"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်"
            SpeechRecognizer.ERROR_NETWORK -> "ကွန်ရက် ချိတ်ဆက်မှု အဆင်မပြေပါ"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ကွန်ရက် ချိတ်ဆက်မှု အချိန်ကျော်လွန်သွားပါသည်"
            SpeechRecognizer.ERROR_NO_MATCH -> "မည်သည့် စကားသံကိုမျှ ရွေးချယ်၍ မရပါ"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech Engine အလုပ်ရှုပ်နေပါသည်"
            SpeechRecognizer.ERROR_SERVER -> "Server ဘက်တွင် အမှားဖြစ်ပေါ်သည်"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "စကားပြောသံ မကြားရပါ"
            else -> "မသိနိုင်သော အမှားဖြစ်ပေါ်သည် (Code: $error)"
        }
    }

    fun destroy() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { destroy() }
            return
        }

        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.destroy()
            speechRecognizer = null

            tts?.setOnUtteranceProgressListener(null)
            tts?.stop()
            tts?.shutdown()
            tts = null

            audioManager.abandonTtsFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying KyawGyiCoreEngine", e)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        callback.onStateChanged(EngineState.PROCESSING, "စာသားပြောင်းလဲနေသည်...")
    }
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
