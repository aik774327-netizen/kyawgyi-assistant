package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class MyanmarTtsEngine(private val context: Context) {

  private var tts: TextToSpeech? = null
  private var isInitialized = false
  private var isMyanmarSupported = false
  private val myanmarLocale = Locale("my", "MM")

  var onSpeechStarted: (() -> Unit)? = null
  var onSpeechCompleted: (() -> Unit)? = null
  var onSpeechError: ((String) -> Unit)? = null

  init {
    initializeEngine()
  }

  private fun initializeEngine() {
    try {
      // Prefer Google TTS engine for best natural Burmese voice rendering
      val googleTtsPackage = "com.google.android.tts"
      tts = TextToSpeech(context.applicationContext, { status ->
        if (status == TextToSpeech.SUCCESS) {
          configureWarmMyanmarVoice()
        } else {
          isInitialized = false
          onSpeechError?.invoke("TextToSpeech initialization failed with status: $status")
        }
      }, googleTtsPackage)
    } catch (_: Exception) {
      // Fallback to default TTS engine
      try {
        tts = TextToSpeech(context.applicationContext) { status ->
          if (status == TextToSpeech.SUCCESS) {
            configureWarmMyanmarVoice()
          }
        }
      } catch (e: Exception) {
        isInitialized = false
      }
    }
  }

  private fun configureWarmMyanmarVoice() {
    val engine = tts ?: return

    // Setup warm audio attributes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
      engine.setAudioAttributes(audioAttributes)
    }

    // Set warm, natural conversational parameters
    // Pitch: 1.02f (friendly, gentle, warm tone)
    // Rate: 0.94f (relaxed, pleasant Burmese conversational cadence)
    engine.setPitch(1.02f)
    engine.setSpeechRate(0.94f)

    // Check Myanmar locale support
    val result = engine.isLanguageAvailable(myanmarLocale)
    if (result >= TextToSpeech.LANG_AVAILABLE) {
      engine.language = myanmarLocale
      isMyanmarSupported = true
      selectBestVoice(engine)
    } else {
      val myResult = engine.isLanguageAvailable(Locale("my"))
      if (myResult >= TextToSpeech.LANG_AVAILABLE) {
        engine.language = Locale("my")
        isMyanmarSupported = true
        selectBestVoice(engine)
      } else {
        // Myanmar voice pack not yet downloaded in Android OS
        isMyanmarSupported = false
        engine.language = Locale.getDefault()
      }
    }

    // Utterance progress listener
    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String?) {
        onSpeechStarted?.invoke()
      }

      override fun onDone(utteranceId: String?) {
        onSpeechCompleted?.invoke()
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String?) {
        onSpeechError?.invoke("Speech error in utterance: $utteranceId")
      }

      override fun onError(utteranceId: String?, errorCode: Int) {
        onSpeechError?.invoke("Speech error code: $errorCode")
      }
    })

    isInitialized = true
  }

  private fun selectBestVoice(engine: TextToSpeech) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      try {
        val voices = engine.voices ?: return
        val burmeseVoices = voices.filter { voice ->
          voice.locale.language == "my" || voice.locale.country == "MM"
        }

        // Prefer non-network high quality warm voices
        val bestVoice = burmeseVoices.firstOrNull { voice ->
          !voice.isNetworkConnectionRequired && voice.quality >= Voice.QUALITY_NORMAL
        } ?: burmeseVoices.firstOrNull()

        if (bestVoice != null) {
          engine.voice = bestVoice
        }
      } catch (_: Exception) {}
    }
  }

  /**
   * Speak Myanmar text with natural warm cadence.
   */
  fun speak(text: String, utteranceId: String = "KyawGyiSpeech_${System.currentTimeMillis()}") {
    if (!isInitialized || tts == null) {
      initializeEngine()
      return
    }

    val sanitizedText = sanitizeBurmeseForSpeech(text)
    val engine = tts ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val params = Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
      }
      engine.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    } else {
      val map = HashMap<String, String>()
      map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
      @Suppress("DEPRECATION")
      engine.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, map)
    }
  }

  /**
   * Stop speaking immediately.
   */
  fun stop() {
    try {
      tts?.stop()
    } catch (_: Exception) {}
  }

  /**
   * Clean up formatting symbols, technical tags, and emojis so speech flows naturally.
   */
  private fun sanitizeBurmeseForSpeech(input: String): String {
    var cleaned = input
    // Remove technical markdown prefixes
    cleaned = cleaned.replace(Regex("⚠️|\\[[^\\]]+\\]|•|[*_`#]"), " ")
    // Convert common technical labels into pleasant spoken Burmese
    cleaned = cleaned.replace("EcoFlow", "အီကိုဖလို")
    cleaned = cleaned.replace("DELTA 2 Max", "ဒယ်လ်တာ တူး မက်စ်")
    cleaned = cleaned.replace("X-Boost", "အိတ်စ် ဘုစ်")
    cleaned = cleaned.replace("WiFi", "ဝိုင်ဖိုင်")
    cleaned = cleaned.replace("Bluetooth", "ဘလူးတုသ်")
    cleaned = cleaned.replace("Viber", "ဗိုက်ဘာ")
    cleaned = cleaned.replace("Telegram", "တယ်လီဂရမ်")
    cleaned = cleaned.replace("KBZPay", "ကေဘီဇက် ပေး")
    cleaned = cleaned.replace("WavePay", "ဝေ့ပေး")
    cleaned = cleaned.replace("Facebook", "ဖေ့စ်ဘုတ်")
    cleaned = cleaned.replace("YouTube", "ယူကျု")
    cleaned = cleaned.replace("W", " ဝပ်")
    cleaned = cleaned.replace("%", " ရာခိုင်နှုန်း")
    cleaned = cleaned.replace("°C", " ဒီဂရီ ဆဲလ်စီးယပ်စ်")
    return cleaned.trim()
  }

  fun isMyanmarVoiceInstalled(): Boolean = isMyanmarSupported

  fun shutdown() {
    try {
      tts?.stop()
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    isInitialized = false
  }
}
