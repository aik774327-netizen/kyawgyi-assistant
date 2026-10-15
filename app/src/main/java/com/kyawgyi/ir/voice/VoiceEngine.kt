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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * VoiceEngine encapsulates Android's TextToSpeech (TTS) engine initialized for Burmese (my-MM)
 * and integrates SpeechRecognizer to handle voice intent processing for Kyaw Gyi assistant.
 */
class VoiceEngine(
  private val context: Context,
  private val onIntentDetected: ((String, String) -> Unit)? = null
) : RecognitionListener {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  // =========================================================================
  // TTS State & Engine
  // =========================================================================
  private var textToSpeech: TextToSpeech? = null
  private val _isTtsReady = MutableStateFlow(false)
  val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  // =========================================================================
  // SpeechRecognizer State
  // =========================================================================
  private var speechRecognizer: SpeechRecognizer? = null

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _recognizedText = MutableStateFlow("")
  val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

  private val _lastDetectedAction = MutableStateFlow<String?>(null)
  val lastDetectedAction: StateFlow<String?> = _lastDetectedAction.asStateFlow()

  private val _statusMessage = MutableStateFlow("မိုက်ခရိုဖုန်း အသင့်အနေအထားဖြစ်ပါသည်")
  val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

  private val _audioRms = MutableStateFlow(0f)
  val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

  companion object {
    val BURMESE_LOCALE: Locale = Locale.forLanguageTag("my-MM")
  }

  init {
    initTextToSpeech()
    initSpeechRecognizer()
  }

  private fun initTextToSpeech() {
    textToSpeech = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        val tts = textToSpeech ?: return@TextToSpeech
        val result = tts.setLanguage(BURMESE_LOCALE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          tts.setLanguage(Locale("my", "MM"))
        }
        tts.setPitch(1.0f)
        tts.setSpeechRate(0.92f) // Natural pacing for Burmese vocal inflections

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
    burmeseText: String,
    onComplete: (() -> Unit)? = null
  ) {
    val tts = textToSpeech
    if (tts == null || !_isTtsReady.value) {
      onComplete?.invoke()
      return
    }

    val utteranceId = "kyawgyi_${System.currentTimeMillis()}"
    if (onComplete != null) {
      tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(id: String?) {
          _isSpeaking.value = true
        }

        override fun onDone(id: String?) {
          if (id == utteranceId) {
            _isSpeaking.value = false
            mainHandler.post { onComplete() }
          }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) {
          if (id == utteranceId) {
            _isSpeaking.value = false
            mainHandler.post { onComplete() }
          }
        }
      })
    }

    val params = Bundle().apply {
      putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }
    tts.speak(burmeseText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
  }

  fun stopSpeaking() {
    textToSpeech?.stop()
    _isSpeaking.value = false
  }

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

  fun startListening(customPrompt: String? = null) {
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
        if (customPrompt != null) {
          putExtra(RecognizerIntent.EXTRA_PROMPT, customPrompt)
        }
      }

      try {
        speechRecognizer?.startListening(intent)
        _isListening.value = true
        _statusMessage.value = "စကားသံကို နားထောင်နေပါသည်..."
      } catch (e: Exception) {
        _isListening.value = false
        _statusMessage.value = "SpeechRecognizer စတင်မရပါ: ${e.localizedMessage}"
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

  // =========================================================================
  // Voice Intent Processing
  // =========================================================================

  fun processVoiceCommandIntent(recognizedText: String) {
    val normalized = recognizedText.lowercase().trim()
    _recognizedText.value = recognizedText

    val (action, responseBurmese) = when {
      normalized.contains("ပန်ကာဖွင့်") || normalized.contains("ဖွင့်") || normalized.contains("turn on fan") ->
        "POWER" to "ကျော်ကြီး ပန်ကာကို ဖွင့်ပေးလိုက်ပါပြီ ခင်ဗျာ"

      normalized.contains("ပန်ကာပိတ်") || normalized.contains("ပိတ်") || normalized.contains("turn off") ->
        "POWER" to "ပန်ကာကို ပိတ်ပေးလိုက်ပါပြီ"

      normalized.contains("မီး") || normalized.contains("light") ->
        "LIGHT" to "မီးအလင်းရောင် စနစ်ကို ပြောင်းလဲပေးလိုက်ပါပြီ"

      normalized.contains("အရှိန်") || normalized.contains("speed") || normalized.contains("လေအရှိန်") ->
        "SPEED" to "လေအရှိန် အဆင့်ကို ပြောင်းလဲပေးလိုက်ပါပြီ"

      normalized.contains("ဘယ်ညာ") || normalized.contains("လှည့်") || normalized.contains("swing") || normalized.contains("osc") ->
        "OSCILLATION" to "ဘယ်ညာလှည့်ခြင်း စနစ်ကို ပြောင်းလဲပေးလိုက်ပါပြီ"

      normalized.contains("အလယ်") || normalized.contains("center") ->
        "CENTER" to "အလယ်ဗဟိုခလုတ် နှိပ်ပေးလိုက်ပါပြီ"

      normalized.contains("eco") || normalized.contains("ဘက်ထရီ") || normalized.contains("station") ->
        "ECOFLOW" to "EcoFlow ဘက်ထရီ စွမ်းအင်အခြေအနေကို စစ်ဆေးနေပါသည်"

      else ->
        "UNKNOWN" to "အမိန့်ကို လက်ခံရရှိပါသည်: $recognizedText"
    }

    _lastDetectedAction.value = action
    _statusMessage.value = responseBurmese

    onIntentDetected?.invoke(action, recognizedText)
    speak(responseBurmese)
  }

  // =========================================================================
  // SpeechRecognizer Callbacks
  // =========================================================================

  override fun onReadyForSpeech(params: Bundle?) {
    _isListening.value = true
  }

  override fun onBeginningOfSpeech() {
    _isListening.value = true
  }

  override fun onRmsChanged(rmsdB: Float) {
    _audioRms.value = rmsdB
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    _isListening.value = false
    _audioRms.value = 0f
  }

  override fun onError(error: Int) {
    _isListening.value = false
    _audioRms.value = 0f
    val errorMsg = when (error) {
      SpeechRecognizer.ERROR_NO_MATCH -> "အသံမရှင်းလင်းပါ၊ ထပ်မံပြောကြားပေးပါ"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "စကားသံ မကြားရပါ"
      SpeechRecognizer.ERROR_AUDIO -> "အသံဖမ်းယူမှု ချို့ယွင်းချက်ရှိပါသည်"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်"
      SpeechRecognizer.ERROR_NETWORK -> "ကွန်ရက် ချိတ်ဆက်မှု အားနည်းနေပါသည်"
      else -> "အသံဖမ်းယူမှု အဆင်မပြေပါ (Error: $error)"
    }
    _statusMessage.value = errorMsg
  }

  override fun onResults(results: Bundle?) {
    _isListening.value = false
    _audioRms.value = 0f
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      val text = matches[0]
      processVoiceCommandIntent(text)
    }
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      _recognizedText.value = matches[0]
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}

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
