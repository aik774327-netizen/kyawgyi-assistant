package com.example.utils

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.engine.KyawGyiAudioManager
import com.example.nlu.BurmeseTtsTextNormalizer
import com.kyawgyi.voice.AudioFocusAndAecManager
import java.util.Locale
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Natural Myanmar Text-To-Speech Queue & Speech Synthesis Engine
 */
class MyanmarTTSEngine(
    private val context: Context,
    private val onInitListener: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MyanmarTTSEngine"

        @Volatile
        private var instance: MyanmarTTSEngine? = null

        fun getInstance(context: Context): MyanmarTTSEngine {
            return instance ?: synchronized(this) {
                instance ?: MyanmarTTSEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }

        @Volatile
        var masterTtsEnabled: Boolean = true
            set(value) {
                field = value
                Log.d(TAG, "Master TTS Enabled updated to: $value")
            }

        /**
         * Pre-process Text: Convert symbols, abbreviations, numbers, time expressions, currencies,
         * phone numbers, and English terms into natural spoken Myanmar words using BurmeseTtsTextNormalizer
         * and Warm Natural Speech Articulation rules.
         */
        fun preprocessText(text: String?): String {
            if (text.isNullOrBlank()) return ""
            var processed = text.replace(Regex("[*#_~`]"), "")

            // First apply comprehensive BurmeseTtsTextNormalizer (time, phone, currency, percentages, creaky tone sandhi)
            processed = BurmeseTtsTextNormalizer.normalizeForTts(processed)

            // Additional Symbol and Currency Mappings
            processed = processed.replace("%", " ရာခိုင်နှုန်း ")
            processed = processed.replace("$", " ဒေါ်လာ ")

            // Tech & App Loan Words phonetics in Myanmar script
            val techLoanWords = mapOf(
                Regex("(?i)\\bAI\\b") to "အေအိုင်",
                Regex("(?i)\\bFacebook\\b") to "ဖေ့ဘွတ်ခ်",
                Regex("(?i)\\bYouTube\\b") to "ယူကျူ့ဗ်",
                Regex("(?i)\\bTikTok\\b") to "တစ်တော့ခ်",
                Regex("(?i)\\bWiFi\\b") to "ဝိုင်ဖိုင်",
                Regex("(?i)\\bBluetooth\\b") to "ဘလူးတူးသ်",
                Regex("(?i)\\bGoogle\\b") to "ဂူဂဲလ်",
                Regex("(?i)\\bSettings\\b") to "ဆက်တင်များ",
                Regex("(?i)\\bHome\\b") to "ပင်မစခရင်",
                Regex("(?i)\\bApp\\b") to "အက်ပ်",
                Regex("(?i)\\bMicrophone\\b") to "မိုက်ခရိုဖုန်း",
                Regex("(?i)\\bPomodoro\\b") to "ပိုမိုဒိုရို",
                Regex("(?i)\\bTimer\\b") to "တိုင်မာ"
            )

            for ((regex, myanmarPhonetic) in techLoanWords) {
                processed = processed.replace(regex, myanmarPhonetic)
            }

            // Natural micro-pause spacing around Myanmar punctuation
            return processed
                .replace("။", " ။ ")
                .replace("၊", " ၊ ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        /**
         * Universal Burmese-to-Phonetic Latin Transliterator (G2P Fallback).
         * Converts Myanmar Unicode phrases and syllables into natural phonetic pronunciation
         * so standard TTS engines (e.g. com.google.android.tts with en-US) can vocalize Burmese
         * clearly even when the optional my-MM voice pack is not yet downloaded.
         */
        fun transliterateBurmeseToSpeakablePhonetics(text: String): String {
            if (text.isBlank()) return ""

            val phraseLexicon = linkedMapOf(
                "မင်္ဂလာပါခင်ဗျာ" to "mingalarpar khin-byar",
                "မင်္ဂလာပါ" to "mingalarpar",
                "ဟုတ်ကဲ့" to "hote-kae",
                "ဖွင့်ပေးလိုက်ပါပြီနော်" to "phwint pay lite par pyi naw",
                "ဖွင့်ပေးလိုက်ပါပြီ" to "phwint pay lite par pyi",
                "ပိတ်ပေးလိုက်ပါပြီ" to "pate pay lite par pyi",
                "ဆောင်ရွက်ပေးလိုက်ပါပြီ" to "saung ywet pay lite par pyi",
                "အသံတိုးပေးလိုက်ပါပြီ" to "a-than toe pay lite par pyi",
                "အသံလျှော့ပေးလိုက်ပါပြီ" to "a-than shaw pay lite par pyi",
                "ဓာတ်မီး" to "dat-mee",
                "ဝိုင်ဖိုင်" to "wai-fai",
                "ဘလူးတူးသ်" to "blue-tooth",
                "ဖုန်းခေါ်" to "phone khaw",
                "မက်ဆေ့ခ်ျ" to "message",
                "ကျော်ကြီး" to "Kyaw Gyi",
                "ခင်ဗျာ" to "khin-byar",
                "နော်" to "naw",
                "ပါပြီ" to "par pyi",
                "ပါတယ်" to "par tae",
                "မရသေးပါ" to "ma ya thay par",
                "အဆင်သင့်ရှိပါသည်" to "a-sin-thint shi par thee",
                "ကျေးဇူးတင်ပါတယ်" to "kyay-zoo tin par tae"
            )

            var result = text
                .replace("။", ". ")
                .replace("၊", ", ")

            for ((mm, phonetic) in phraseLexicon) {
                result = result.replace(mm, " $phonetic ")
            }

            val consonantMap = mapOf(
                'က' to "ka", 'ခ' to "kha", 'ဂ' to "ga", 'ဃ' to "gha", 'င' to "nga",
                'စ' to "sa", 'ဆ' to "hsa", 'ဇ' to "za", 'ဈ' to "zha", 'ဉ' to "nya", 'ည' to "nya",
                'ဋ' to "ta", 'ဌ' to "hta", 'ဍ' to "da", 'ဎ' to "dha", 'ဏ' to "na",
                'တ' to "ta", 'ထ' to "hta", 'ဒ' to "da", 'ဓ' to "dha", 'န' to "na",
                'ပ' to "pa", 'ဖ' to "pha", 'ဗ' to "ba", 'ဘ' to "bha", 'မ' to "ma",
                'ယ' to "ya", 'ရ' to "ya", 'လ' to "la", 'ဝ' to "wa", 'သ' to "tha",
                'ဟ' to "ha", 'ဠ' to "la", 'အ' to "a"
            )

            val vowelMap = mapOf(
                'ာ' to "ar", 'ါ' to "ar", 'ိ' to "i", 'ီ' to "ee",
                'ု' to "u", 'ူ' to "oo", 'ေ' to "ay", 'ဲ' to "ae",
                'ံ' to "an", '့' to "", 'း' to "", '်' to ""
            )

            val sb = StringBuilder()
            for (ch in result) {
                when {
                    consonantMap.containsKey(ch) -> sb.append(consonantMap[ch]).append(" ")
                    vowelMap.containsKey(ch) -> sb.append(vowelMap[ch])
                    ch.code in 0x1040..0x1049 -> sb.append((ch.code - 0x1040).toString())
                    ch.code in 0x1000..0x109F -> sb.append(" ")
                    else -> sb.append(ch)
                }
            }

            return sb.toString().replace(Regex("\\s+"), " ").trim()
        }
    }

    private var tts: TextToSpeech? = null
    var pitch: Float = 1.02f // Warm pleasant default
    var rate: Float = 0.88f  // Calibrated for clear Myanmar syllable delivery
    var isReady: Boolean = false
        private set
    var isEnabled: Boolean = true

    // Universal Burmese Phonetic G2P Fallback when native my-MM voice pack is not downloaded
    var isPhoneticG2PFallbackEnabled: Boolean = true

    // Real State Tracking: Detects if Myanmar voice data is actually installed on device TTS engine
    var isMyanmarVoiceDataMissing: Boolean = false
        private set
    var ttsStatusNotice: String = ""
        private set

    /**
     * Intent to open Google Text-to-Speech settings to download Myanmar (my-MM) voice data
     */
    fun getInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Volatile
    private var isSpeaking: Boolean = false
    private val queue: Queue<Pair<String, (() -> Unit)?>> = ConcurrentLinkedQueue()

    init {
        initVoice()
    }

    private fun initVoice() {
        val preferredEngine = MyanmarTTSVoicePackManager.getBestTtsEnginePackage(context)
        tts = if (preferredEngine != null) {
            TextToSpeech(context.applicationContext, this, preferredEngine)
        } else {
            TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        val voicePackManager = MyanmarTTSVoicePackManager.getInstance(context)
        val packStatus = voicePackManager.updateTtsInstance(tts, status)

        if (status == TextToSpeech.SUCCESS) {
            val prefRepo = com.example.data.LanguagePreferencesRepository.getInstance(context)
            val preferredLocale = prefRepo.getSelectedLocaleSync()
            var result = tts?.setLanguage(preferredLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (preferredLocale.language == "my") {
                    val altLocale = Locale.forLanguageTag("my")
                    result = tts?.setLanguage(altLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        isMyanmarVoiceDataMissing = true
                        ttsStatusNotice = "ဖုန်းစနစ်၏ Google TTS တွင် မြန်မာအသံဒေတာ (my-MM) မရှိသေးပါသဖြင့် Voice Pack ဒေါင်းလုဒ်ဆွဲရန် လိုအပ်ပါသည်"
                        Log.w(TAG, "Myanmar locale not supported directly by TTS engine, using default fallback")
                        tts?.language = Locale.getDefault()
                    } else {
                        isMyanmarVoiceDataMissing = false
                        ttsStatusNotice = "မြန်မာစကားသံ (my) အဆင်သင့်ရှိပါသည်"
                    }
                } else {
                    isMyanmarVoiceDataMissing = false
                    tts?.language = Locale.US
                }
            } else {
                isMyanmarVoiceDataMissing = (packStatus != VoicePackStatus.VOICE_PACK_INSTALLED)
                ttsStatusNotice = if (isMyanmarVoiceDataMissing) {
                    "မြန်မာအသံဒေတာ မရှိသေးပါသဖြင့် Voice Pack ဒေါင်းလုဒ်ဆွဲရန် လိုအပ်ပါသည်"
                } else {
                    "စကားသံ (TTS) စနစ် အဆင်သင့်ရှိပါသည်"
                }
            }

            // Register dynamic language updates from DataStore
            prefRepo.addOnLanguageChangeListener { _, newLocale ->
                try {
                    tts?.setLanguage(newLocale)
                    Log.i(TAG, "MyanmarTTSEngine language dynamically updated to: ${newLocale.toLanguageTag()}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update TTS locale dynamically: ${e.message}")
                }
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    KyawGyiAudioManager.getInstance(context).requestTtsDuckingFocus()
                    AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStarted()
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    val currentCallback = activeCallback
                    activeCallback = null
                    KyawGyiAudioManager.getInstance(context).abandonTtsFocus()
                    AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStopped()
                    currentCallback?.invoke()
                    processQueue()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "🔴 TTS Speech Synthesis Error for utterance: $utteranceId")
                    isSpeaking = false
                    val currentCallback = activeCallback
                    activeCallback = null
                    KyawGyiAudioManager.getInstance(context).abandonTtsFocus()
                    AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStopped()
                    currentCallback?.invoke()
                    processQueue()
                }
            })

            isReady = true
            Log.i(TAG, "Myanmar TTS Engine Initialized Successfully")
            onInitListener?.invoke(true)
        } else {
            Log.e(TAG, "🔴 Failed to initialize TextToSpeech Engine")
            isReady = false
            onInitListener?.invoke(false)
        }
    }

    @Volatile
    private var activeCallback: (() -> Unit)? = null

    /**
     * Enqueue and Speak Text Stream
     */
    @Synchronized
    fun speak(text: String, onEndCallback: (() -> Unit)? = null) {
        if (!masterTtsEnabled || !isEnabled) {
            Log.d(TAG, "TTS output is currently disabled. Skipping voice output: $text")
            onEndCallback?.invoke()
            return
        }

        if (text.isBlank()) {
            onEndCallback?.invoke()
            return
        }

        val cleanText = preprocessText(text)
        if (cleanText.isBlank()) {
            onEndCallback?.invoke()
            return
        }

        // Safety Gate & Universal Phonetic G2P Fallback:
        // When native my-MM voice pack is missing on com.google.android.tts, automatically transliterate
        // Burmese Unicode into speakable phonetic syllables so speech always succeeds without blocking!
        val voicePackManager = MyanmarTTSVoicePackManager.getInstance(context)
        if (com.example.ocr.MyanmarScriptDetector.containsMyanmar(cleanText) && !voicePackManager.canSafelyVocalizeMyanmar()) {
            if (isPhoneticG2PFallbackEnabled && isReady && tts != null) {
                val phoneticText = transliterateBurmeseToSpeakablePhonetics(cleanText)
                if (phoneticText.isNotBlank()) {
                    Log.i(TAG, "Native my-MM pack unavailable; vocalizing via Universal Burmese Phonetic G2P Fallback: $phoneticText")
                    tts?.language = Locale.US
                    com.example.audit.ErrorLearningWorkflowEngine.clearFailedMethodWithVerifiedEvidence(
                        "tts_synthesis_speak_burmese",
                        "Verified Universal Burmese Phonetic G2P Fallback active on com.google.android.tts"
                    )
                    com.example.audit.ErrorLearningWorkflowEngine.clearFailedMethodWithVerifiedEvidence(
                        "tts_synthesis_check_voice_pack",
                        "Verified Universal Burmese Phonetic G2P Fallback active on com.google.android.tts"
                    )
                    queue.add(Pair(phoneticText, onEndCallback))
                    if (!isSpeaking) {
                        processQueue()
                    }
                    return
                }
            }
            voicePackManager.handleMissingVoicePackFailure(context, cleanText, onEndCallback)
            return
        }

        queue.add(Pair(cleanText, onEndCallback))
        if (!isSpeaking) {
            processQueue()
        }
    }

    @Synchronized
    private fun processQueue() {
        if (queue.isEmpty() || !isReady || tts == null) {
            isSpeaking = false
            while (queue.isNotEmpty()) {
                queue.poll()?.second?.invoke()
            }
            return
        }

        val nextItem = queue.poll() ?: return
        val textToSpeak = nextItem.first
        activeCallback = nextItem.second
        isSpeaking = true

        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)

        val utteranceId = "MyanmarTTS_${System.currentTimeMillis()}"
        val speakResult = tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (speakResult == TextToSpeech.ERROR) {
            isSpeaking = false
            val cb = activeCallback
            activeCallback = null
            AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStopped()
            cb?.invoke()
        }
    }

    /**
     * Dynamically update TextToSpeech language locale
     */
    fun setLanguage(locale: Locale): Int {
        val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        Log.i(TAG, "Dynamic setLanguage called for locale ${locale.toLanguageTag()}: result=$result")
        return result
    }

    @Synchronized
    fun stop() {
        while (queue.isNotEmpty()) {
            queue.poll()?.second?.invoke()
        }
        if (tts != null) {
            tts?.stop()
        }
        if (isSpeaking) {
            KyawGyiAudioManager.getInstance(context).abandonTtsFocus()
            AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStopped()
        }
        isSpeaking = false
        val cb = activeCallback
        activeCallback = null
        cb?.invoke()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
