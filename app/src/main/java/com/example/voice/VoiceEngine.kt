package com.example.voice

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages Android's TextToSpeech (TTS) engine configured for Burmese (my-MM) localization,
 * and SpeechRecognizer integration for capturing user voice commands.
 */
class VoiceEngine(
  private val context: Context,
  private val onCommandRecognized: ((String) -> Unit)? = null
) : RecognitionListener {

  private val mainHandler = Handler(Looper.getMainLooper())

  // TTS State
  private var textToSpeech: TextToSpeech? = null
  private val _isTtsReady = MutableStateFlow(false)
  val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  // SpeechRecognizer State
  private var speechRecognizer: SpeechRecognizer? = null
  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _recognizedText = MutableStateFlow("")
  val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

  private val _rmsLevel = MutableStateFlow(0f)
  val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

  private var activeResultCallback: ((String) -> Unit)? = null
  private var activeErrorCallback: ((String) -> Unit)? = null

  companion object {
    val BURMESE_LOCALE: Locale = Locale.forLanguageTag("my-MM")
  }

  init {
    initTextToSpeech()
    initSpeechRecognizer()
  }

  // =====================================================================
  // TEXT TO SPEECH (TTS) - BURMESE (my-MM) LOCALIZATION
  // =====================================================================

  private fun initTextToSpeech() {
    textToSpeech = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val tts = textToSpeech ?: return@TextToSpeech
        val result = tts.setLanguage(BURMESE_LOCALE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          // Fallback to generic Locale("my", "MM") or default
          tts.setLanguage(Locale("my", "MM"))
        }
        tts.setPitch(1.0f)
        tts.setSpeechRate(0.92f) // Optimal pacing for clear Burmese tone

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
          }

          override fun onDone(utteranceId: String?) {
            _isSpeaking.value = false
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            _isSpeaking.value = false
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            _isSpeaking.value = false
          }
        })
        _isTtsReady.value = true
      } else {
        _isTtsReady.value = false
      }
    }
  }

  fun speak(
    text: String,
    queueMode: Int = TextToSpeech.QUEUE_FLUSH,
    utteranceId: String = "kyawgyi_tts_${System.currentTimeMillis()}",
    onDone: (() -> Unit)? = null
  ) {
    val tts = textToSpeech
    if (tts == null || !_isTtsReady.value) {
      onDone?.invoke()
      return
    }

    if (onDone != null) {
      val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {
          _isSpeaking.value = true
        }

        override fun onDone(id: String?) {
          if (id == utteranceId) {
            _isSpeaking.value = false
            mainHandler.post { onDone() }
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) {
          if (id == utteranceId) {
            _isSpeaking.value = false
            mainHandler.post { onDone() }
          }
        }
      }
      tts.setOnUtteranceProgressListener(listener)
    }

    val params = Bundle().apply {
      putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }
    tts.speak(text, queueMode, params, utteranceId)
  }

  fun stopSpeaking() {
    textToSpeech?.stop()
    _isSpeaking.value = false
  }

  // =====================================================================
  // SPEECH RECOGNIZER - VOICE COMMAND CAPTURE
  // =====================================================================

  private fun initSpeechRecognizer() {
    mainHandler.post {
      if (SpeechRecognizer.isRecognitionAvailable(context)) {
        try {
          speechRecognizer?.destroy()
          speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@VoiceEngine)
          }
        } catch (_: Exception) {}
      }
    }
  }

  fun startListening(
    onResult: ((String) -> Unit)? = null,
    onError: ((String) -> Unit)? = null
  ) {
    this.activeResultCallback = onResult ?: onCommandRecognized
    this.activeErrorCallback = onError

    mainHandler.post {
      if (speechRecognizer == null) {
        initSpeechRecognizer()
      }

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "my-MM")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "my-MM")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "my-MM")
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "en-US"))
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
      }

      try {
        speechRecognizer?.startListening(intent)
        _isListening.value = true
      } catch (e: Exception) {
        _isListening.value = false
        activeErrorCallback?.invoke("SpeechRecognizer စတင်၍မရပါ: ${e.localizedMessage}")
      }
    }
  }

  fun stopListening() {
    mainHandler.post {
      try {
        speechRecognizer?.stopListening()
      } catch (_: Exception) {}
      _isListening.value = false
    }
  }

  fun cancelListening() {
    mainHandler.post {
      try {
        speechRecognizer?.cancel()
      } catch (_: Exception) {}
      _isListening.value = false
      _rmsLevel.value = 0f
    }
  }

  // =====================================================================
  // RECOGNITION LISTENER CALLBACKS
  // =====================================================================

  override fun onReadyForSpeech(params: Bundle?) {
    _isListening.value = true
  }

  override fun onBeginningOfSpeech() {
    _isListening.value = true
  }

  override fun onRmsChanged(rmsdB: Float) {
    _rmsLevel.value = rmsdB
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    _isListening.value = false
    _rmsLevel.value = 0f
  }

  override fun onError(error: Int) {
    _isListening.value = false
    _rmsLevel.value = 0f

    val burmeseErrorMessage = when (error) {
      SpeechRecognizer.ERROR_AUDIO -> "အသံဖမ်းယူမှု ချို့ယွင်းချက်ရှိပါသည် (Audio error)"
      SpeechRecognizer.ERROR_CLIENT -> "စနစ်ဆိုင်ရာ ချို့ယွင်းချက် (Client error)"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်"
      SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "⚠️ အင်တာနက် လိုင်းမရှိပါ (သို့မဟုတ် Offline Myanmar Pack မရှိပါ)။ အောက်ပါ Quick Action Chips များကို အသုံးပြုနိုင်ပါသည်"
      SpeechRecognizer.ERROR_NO_MATCH -> "အသံကို ရှင်းလင်းစွာ မခွဲခြားနိုင်ပါ (No match)"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "စကားသံ အာရုံခံစနစ် အလုပ်များနေပါသည်"
      SpeechRecognizer.ERROR_SERVER -> "ဆာဗာ ချို့ယွင်းချက် (Server error)"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "စကားသံ မကြားရသဖြင့် စောင့်ဆိုင်းချိန် ကုန်ဆုံးပါသည်"
      else -> "စကားသံ ဖမ်းယူမှု မအောင်မြင်ပါ (Error code: $error)"
    }

    activeErrorCallback?.invoke(burmeseErrorMessage)
  }

  override fun onResults(results: Bundle?) {
    _isListening.value = false
    _rmsLevel.value = 0f
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      val bestMatch = matches[0]
      _recognizedText.value = bestMatch
      activeResultCallback?.invoke(bestMatch)
    }
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      _recognizedText.value = matches[0]
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}

  // =====================================================================
  // CLEANUP & LIFECYCLE
  // =====================================================================

  fun shutdown() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
        speechRecognizer = null
      } catch (_: Exception) {}

      try {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
      } catch (_: Exception) {}

      _isListening.value = false
      _isSpeaking.value = false
      _isTtsReady.value = false
    }
  }
}
