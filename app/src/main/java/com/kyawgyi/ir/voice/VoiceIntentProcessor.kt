package com.kyawgyi.ir.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.kyawgyi.ir.repository.IrViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceState(
  val isListening: Boolean = false,
  val speechRms: Float = 0f,
  val lastRecognizedText: String = "",
  val lastDetectedAction: String? = null,
  val feedbackMessage: String = "အသံဖြင့် အမိန့်ပေးရန် မိုက်ခရိုဖုန်းကို နှိပ်ပါ (ဥပမာ- 'ကျော်ကြီး ပန်ကာဖွင့်')",
  val error: String? = null
)

/**
 * Maps Burmese and English voice commands to IrViewModel triggers using Android SpeechRecognizer.
 * Example commands:
 * - "ကျော်ကြီး ပန်ကာဖွင့်" -> Transmits POWER command
 * - "ကျော်ကြီး မီးဖွင့်" -> Transmits LIGHT command
 * - "ကျော်ကြီး လေအရှိန်တင်" -> Transmits SPEED command
 * - "ကျော်ကြီး ဘယ်ညာလှည့်" -> Transmits OSCILLATION command
 * - "ကျော်ကြီး အလယ်ခလုတ်" -> Transmits CENTER command
 */
class VoiceIntentProcessor(
  private val context: Context,
  private val viewModel: IrViewModel,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : RecognitionListener {

  private val _state = MutableStateFlow(VoiceState())
  val state: StateFlow<VoiceState> = _state.asStateFlow()

  private var speechRecognizer: SpeechRecognizer? = null

  init {
    initRecognizer()
  }

  private fun initRecognizer() {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(this@VoiceIntentProcessor)
      }
    } else {
      _state.value = _state.value.copy(
        error = "ဤစက်ပစ္စည်းတွင် Speech Recognition စနစ် မပါရှိပါ"
      )
    }
  }

  fun startListening() {
    if (speechRecognizer == null) {
      initRecognizer()
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "my-MM")
      putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("my-MM", "my", "en-US"))
      putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    try {
      speechRecognizer?.startListening(intent)
      _state.value = _state.value.copy(
        isListening = true,
        feedbackMessage = "နားထောင်နေပါသည်... စကားပြောပါ",
        error = null
      )
    } catch (e: Exception) {
      _state.value = _state.value.copy(
        isListening = false,
        error = "မိုက်ခရိုဖုန်း ဖွင့်၍မရပါ: ${e.message}"
      )
    }
  }

  fun stopListening() {
    speechRecognizer?.stopListening()
    _state.value = _state.value.copy(isListening = false)
  }

  fun destroy() {
    speechRecognizer?.destroy()
    speechRecognizer = null
  }

  /**
   * Evaluates Burmese voice text and matches to IR actions.
   */
  fun processVoiceIntent(spokenText: String): Boolean {
    val clean = spokenText.trim().lowercase(Locale.getDefault())

    var matchedKey: String? = null
    var actionDescription = ""

    when {
      // 1. POWER (ပန်ကာဖွင့်/ပိတ်)
      clean.contains("ဖွင့်") || clean.contains("ပိတ်") || clean.contains("ပါဝါ") ||
        clean.contains("power") || clean.contains("fan on") || clean.contains("fan off") ||
        clean.contains("ပန်ကာ") -> {
        matchedKey = "power"
        actionDescription = "ပါဝါ (POWER) ထုတ်လွှင့်သည်"
      }

      // 2. LIGHT (မီးရောင် အဖွင့်/အပိတ်)
      clean.contains("မီး") || clean.contains("light") || clean.contains("မီးရောင်") -> {
        matchedKey = "light"
        actionDescription = "မီးရောင် (LIGHT) ထုတ်လွှင့်သည်"
      }

      // 3. SPEED (လေအရှိန် အဆင့်ဆင့်)
      clean.contains("လေ") || clean.contains("အရှိန်") || clean.contains("speed") ||
        clean.contains("မြန်") || clean.contains("တင်") -> {
        matchedKey = "speed"
        actionDescription = "လေအရှိန် (SPEED) ထုတ်လွှင့်သည်"
      }

      // 4. OSCILLATION (ဘယ်ညာလှည့်/ရပ်)
      clean.contains("လှည့်") || clean.contains("လည်") || clean.contains("oscillation") ||
        clean.contains("swing") || clean.contains("ဘယ်ညာ") -> {
        matchedKey = "oscillation"
        actionDescription = "ဘယ်ညာလှည့် (OSCILLATION) ထုတ်လွှင့်သည်"
      }

      // 5. CENTER (အလယ်ခလုတ်)
      clean.contains("အလယ်") || clean.contains("center") || clean.contains("လယ်") -> {
        matchedKey = "center"
        actionDescription = "အလယ်ခလုတ် (CENTER) ထုတ်လွှင့်သည်"
      }

      // 6. API Key Failover / Rotation by Voice ("သော့ပြောင်း", "api သော့")
      clean.contains("သော့ပြောင်း") || clean.contains("api သော့") || clean.contains("ခွဲတမ်း") || clean.contains("switch key") -> {
        matchedKey = "apikey_rotate"
        val apiKeyManager = com.kyawgyi.ir.apikey.ApiKeyManager.getInstance(context)
        val next = apiKeyManager.switchToNextAvailableKey("Voice command switch request")
        actionDescription = if (next != null) "API သော့ '${next.label}' သို့ ပြောင်းလဲပြီးပါပြီ" else "API သော့ အားလုံး ပုံမှန် အလုပ်လုပ်နေသည်"
      }
    }

    if (matchedKey != null) {
      _state.value = _state.value.copy(
        lastRecognizedText = spokenText,
        lastDetectedAction = actionDescription,
        feedbackMessage = "အမိန့်ကို လက်ခံရရှိသည်: $actionDescription"
      )
      // Execute command on IrViewModel if not purely an API key voice action
      if (matchedKey != "apikey_rotate") {
        viewModel.transmitButton(matchedKey)
      }
      viewModel.logVoiceCommand(spokenText, matchedKey, isSuccess = true)
      return true
    } else {
      _state.value = _state.value.copy(
        lastRecognizedText = spokenText,
        lastDetectedAction = null,
        feedbackMessage = "\"$spokenText\" နှင့် ကိုက်ညီသော ခလုတ် ရှာမတွေ့ပါ"
      )
      viewModel.logVoiceCommand(spokenText, null, isSuccess = false)
      return false
    }
  }

  // SpeechRecognizer RecognitionListener callbacks
  override fun onReadyForSpeech(params: Bundle?) {
    _state.value = _state.value.copy(feedbackMessage = "အဆင်သင့်ဖြစ်ပါပြီ... စကားပြောပါ")
  }

  override fun onBeginningOfSpeech() {
    _state.value = _state.value.copy(isListening = true)
  }

  override fun onRmsChanged(rmsdB: Float) {
    _state.value = _state.value.copy(speechRms = rmsdB)
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    _state.value = _state.value.copy(isListening = false, feedbackMessage = "အသံကို ခွဲခြမ်းစိတ်ဖြာနေပါသည်...")
  }

  override fun onError(error: Int) {
    val errorText = when (error) {
      SpeechRecognizer.ERROR_NO_MATCH -> "အသံဖမ်းယူမှု မရှင်းလင်းပါ (No match)"
      SpeechRecognizer.ERROR_NETWORK -> "ကွန်ရက်ချိတ်ဆက်မှု မရှိပါ"
      SpeechRecognizer.ERROR_AUDIO -> "အသံဖမ်းယူရာတွင် ချို့ယွင်းချက်ရှိပါသည်"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "အသံထွက်မကြားရပါ (Timeout)"
      else -> "အသံဖမ်းယူမှု အမှားအယွင်း ($error)"
    }
    _state.value = _state.value.copy(isListening = false, error = errorText, feedbackMessage = errorText)
  }

  override fun onResults(results: Bundle?) {
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      val recognized = matches[0]
      processVoiceIntent(recognized)
    } else {
      _state.value = _state.value.copy(feedbackMessage = "အသံထွက်ကို နားမလည်နိုင်ပါ")
    }
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
      _state.value = _state.value.copy(lastRecognizedText = matches[0])
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}
}
