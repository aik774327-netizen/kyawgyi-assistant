package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enterprise Dual-Mode Myanmar STT Engine:
 * Mode 1: AI Gateway WAV Audio Recording -> AI Gateway Multimodal & Grammar Engine.
 * Mode 2: Android Native SpeechRecognizer -> AI Gateway Grammar Refiner.
 */
class MyanmarSttManager(private val context: Context) {

  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  val wavRecorder = WavAudioRecorder(context, coroutineScope)
  val aiGatewayClient = AiGatewaySttClient(context)

  private var speechRecognizer: SpeechRecognizer? = null
  private var isListeningNow = false

  // Settings
  private val _useWavAiGateway = MutableStateFlow(true)
  val useWavAiGateway: StateFlow<Boolean> = _useWavAiGateway.asStateFlow()

  private val _isRecognizing = MutableStateFlow(false)
  val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

  private val _rmsLevel = MutableStateFlow(0f)
  val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

  private val _partialText = MutableStateFlow("")
  val partialText: StateFlow<String> = _partialText.asStateFlow()

  private val _lastRawTranscript = MutableStateFlow("")
  val lastRawTranscript: StateFlow<String> = _lastRawTranscript.asStateFlow()

  private val _lastCorrectedText = MutableStateFlow("")
  val lastCorrectedText: StateFlow<String> = _lastCorrectedText.asStateFlow()

  private val _lastResultDetails = MutableStateFlow<AiGatewaySttResult?>(null)
  val lastResultDetails: StateFlow<AiGatewaySttResult?> = _lastResultDetails.asStateFlow()

  var onFinalResult: ((String) -> Unit)? = null
  var onRawResult: ((String) -> Unit)? = null
  var onGrammarDetailResult: ((AiGatewaySttResult) -> Unit)? = null
  var onErrorOccurred: ((String, Int) -> Unit)? = null
  var onSpeechStateChanged: ((String) -> Unit)? = null

  init {
    // Connect WAV recorder live RMS to public flow
    coroutineScope.launch {
      wavRecorder.rmsLevel.collect { rms ->
        if (_useWavAiGateway.value && _isRecognizing.value) {
          _rmsLevel.value = rms
        }
      }
    }

    wavRecorder.onRecordingFinished = { wavBytes, _ ->
      processWavAudio(wavBytes)
    }

    wavRecorder.onError = { errorMsg ->
      _isRecognizing.value = false
      _rmsLevel.value = 0f
      onErrorOccurred?.invoke(errorMsg, -1)
    }
  }

  fun setUseWavAiGateway(enabled: Boolean) {
    _useWavAiGateway.value = enabled
  }

  fun initialize() {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(createListener())
      }
    }
  }

  fun startListening(languageCode: String = "my-MM") {
    if (_useWavAiGateway.value) {
      // MODE 1: WAV Audio Recording
      val started = wavRecorder.startRecording()
      if (started) {
        isListeningNow = true
        _isRecognizing.value = true
        _partialText.value = ""
        onSpeechStateChanged?.invoke("AI Gateway အသံဖမ်းယူနေပါသည် (WAV အဖြစ် ပြောင်းလဲမည်)...")
      }
      return
    }

    // MODE 2: Android SpeechRecognizer
    if (speechRecognizer == null) {
      initialize()
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
      putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "my", "en-US"))
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
      putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    try {
      speechRecognizer?.startListening(intent)
      isListeningNow = true
      _isRecognizing.value = true
      onSpeechStateChanged?.invoke("အသံဖမ်းယူရန် စတင်နားထောင်နေပါသည်...")
    } catch (e: Exception) {
      isListeningNow = false
      _isRecognizing.value = false
      onErrorOccurred?.invoke("SpeechRecognizer စတင်မရပါ: ${e.localizedMessage ?: "Unknown"}", -1)
    }
  }

  fun stopListening() {
    if (_useWavAiGateway.value) {
      if (_isRecognizing.value) {
        onSpeechStateChanged?.invoke("WAV အသံကို စစ်ဆေးပြီး AI Gateway သို့ ပေးပို့နေပါသည်...")
        wavRecorder.stopRecording()
      }
      return
    }

    try {
      speechRecognizer?.stopListening()
    } catch (_: Exception) {}
    isListeningNow = false
    _isRecognizing.value = false
    _rmsLevel.value = 0f
  }

  fun cancel() {
    if (_useWavAiGateway.value) {
      wavRecorder.cancelRecording()
    }
    try {
      speechRecognizer?.cancel()
    } catch (_: Exception) {}
    isListeningNow = false
    _isRecognizing.value = false
    _rmsLevel.value = 0f
  }

  fun destroy() {
    cancel()
    try {
      speechRecognizer?.destroy()
    } catch (_: Exception) {}
    speechRecognizer = null
    coroutineScope.cancel()
  }

  private fun processWavAudio(wavBytes: ByteArray) {
    _isRecognizing.value = false
    _rmsLevel.value = 0f

    coroutineScope.launch {
      onSpeechStateChanged?.invoke("မြန်မာစာလုံးပေါင်းနှင့် သဒ္ဒါကို တိကျစွာ ပြင်ဆင်နေပါသည်...")
      val result = aiGatewayClient.transcribeAndCorrectWav(wavBytes)
      result.onSuccess { data ->
        _lastRawTranscript.value = data.rawTranscript
        _lastCorrectedText.value = data.correctedText
        _lastResultDetails.value = data
        _partialText.value = data.correctedText

        val cacheTag = if (data.isCached) " [Cached-Saved Credit]" else ""
        onSpeechStateChanged?.invoke("အောင်မြင်ပါသည်$cacheTag: ${data.correctedText}")

        onRawResult?.invoke(data.rawTranscript)
        onGrammarDetailResult?.invoke(data)
        onFinalResult?.invoke(data.correctedText)
      }.onFailure { error ->
        onSpeechStateChanged?.invoke("အမှားဖြစ်ပွားပါသည်: ${error.localizedMessage}")
        onErrorOccurred?.invoke(error.localizedMessage ?: "STT Synthesis failed", -1)
      }
    }
  }

  private fun createListener(): RecognitionListener {
    return object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {
        _isRecognizing.value = true
        onSpeechStateChanged?.invoke("စကားပြောနိုင်ပါပြီ (အသံကို စောင့်ဆိုင်းနေပါသည်)...")
      }

      override fun onBeginningOfSpeech() {
        onSpeechStateChanged?.invoke("စကားပြောသံကို ဖမ်းယူနေပါသည်...")
      }

      override fun onRmsChanged(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _rmsLevel.value = normalized
      }

      override fun onBufferReceived(buffer: ByteArray?) {}

      override fun onEndOfSpeech() {
        onSpeechStateChanged?.invoke("စကားသံ ဖမ်းယူမှု ပြီးဆုံးပါပြီ၊ အဖြေရှာဖွေနေပါသည်...")
        _rmsLevel.value = 0f
      }

      override fun onError(error: Int) {
        isListeningNow = false
        _isRecognizing.value = false
        _rmsLevel.value = 0f

        val burmeseError = when (error) {
          SpeechRecognizer.ERROR_NO_MATCH -> "အသံကို ရှင်းလင်းစွာ မခွဲခြားနိုင်ပါ၊ ထပ်မံ ပြောဆိုပေးပါ"
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "စကားသံ မကြားရသဖြင့် စောင့်ဆိုင်းချိန် ကုန်ဆုံးသွားပါသည်"
          SpeechRecognizer.ERROR_AUDIO -> "မိုက်ခရိုဖုန်း အသံဖမ်းယူမှု ချို့ယွင်းနေပါသည်"
          SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "⚠️ အင်တာနက် လိုင်းမရှိပါ (သို့မဟုတ် Google Speech တွင် Offline Myanmar Pack မရှိပါ)။ အောက်ပါ Quick Action Chips များကို အသုံးပြုနိုင်ပါသည်"
          SpeechRecognizer.ERROR_SERVER -> "Google Speech ဆာဗာ ချိတ်ဆက်၍မရပါ (ဆာဗာ အရင်းအမြစ် ချို့ယွင်းချက်)။ Quick Action Chips များကို အသုံးပြုနိုင်ပါသည်"
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "မိုက်ခရိုဖုန်း အသုံးပြုခွင့် (Record Audio Permission) မရှိပါ (အရင်းအမြစ် လိုအပ်ချက်)"
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "စကားသံ အာရုံခံစနစ် အလုပ်များနေပါသည်"
          else -> "အသံဖမ်းယူမှု အမှားဖြစ်ပွားသည် (Error Code: $error)"
        }

        onErrorOccurred?.invoke(burmeseError, error)
      }

      override fun onResults(results: Bundle?) {
        isListeningNow = false
        _isRecognizing.value = false
        _rmsLevel.value = 0f

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          val rawMatch = matches[0].trim()
          _lastRawTranscript.value = rawMatch
          onRawResult?.invoke(rawMatch)

          // Perform Myanmar grammar & orthography correction via AI Gateway
          coroutineScope.launch {
            onSpeechStateChanged?.invoke("AI Gateway မြန်မာစာလုံးပေါင်း ပြင်ဆင်နေပါသည်...")
            val grammarRes = aiGatewayClient.correctMyanmarGrammar(rawMatch)
            _lastCorrectedText.value = grammarRes.correctedText
            _partialText.value = grammarRes.correctedText

            val simulatedResult = AiGatewaySttResult(
              rawTranscript = rawMatch,
              correctedText = grammarRes.correctedText,
              corrections = grammarRes.corrections,
              confidence = 0.95f,
              durationSec = 1.5f,
              isCached = grammarRes.isCached,
              model = "native-stt+ai-gateway",
              audioHash = aiGatewayClient.computeSha256(rawMatch.toByteArray())
            )
            _lastResultDetails.value = simulatedResult
            onGrammarDetailResult?.invoke(simulatedResult)
            onFinalResult?.invoke(grammarRes.correctedText)
          }
        } else {
          onErrorOccurred?.invoke("အသံကို မဖော်ထုတ်နိုင်ပါ", SpeechRecognizer.ERROR_NO_MATCH)
        }
      }

      override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          val text = matches[0].trim()
          _partialText.value = text
        }
      }

      override fun onEvent(eventType: Int, params: Bundle?) {}
    }
  }
}
