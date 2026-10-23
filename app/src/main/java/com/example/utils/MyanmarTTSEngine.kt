package com.example.utils

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.GeminiApiService
import com.example.engine.KyawGyiAudioManager
import com.example.nlu.BurmeseTtsTextNormalizer
import com.kyawgyi.voice.AudioFocusAndAecManager
import com.kyawgyi.voice.AudioTrackPcmPlayer
import com.kyawgyi.voice.GeminiTtsEngine
import com.kyawgyi.voice.TtsEngineType
import com.kyawgyi.voice.VoiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    }

    private var tts: TextToSpeech? = null
    var pitch: Float = 1.02f // Warm pleasant default
    var rate: Float = 0.88f  // Calibrated for clear Myanmar syllable delivery
    var isReady: Boolean = false
        private set
    var isEnabled: Boolean = true

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

    // §7 (2026-10-23): Gemini online TTS routing. Created lazily on
    // first online utterance so the default SYSTEM selection has zero
    // new footprint. The engine's fallback is null ON PURPOSE: the
    // fallback for an online failure is THIS class's system path
    // (speakViaSystem, with the FIX-02 voice gate) — routing handles
    // it, so Burmese text is never fed to an ungated foreign voice.
    private val onlineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var geminiTtsEngineOrNull: GeminiTtsEngine? = null

    private fun currentTtsEngineSelection(): TtsEngineType =
        try {
            VoiceSettings.getInstance(context).preferredTtsEngine
        } catch (e: Exception) {
            TtsEngineType.SYSTEM
        }

    private fun geminiTtsEngine(): GeminiTtsEngine {
        geminiTtsEngineOrNull?.let { return it }
        val service = GeminiApiService(context)
        return GeminiTtsEngine(
            synthesize = { text, voice -> service.synthesizeSpeech(text, voice) },
            player = AudioTrackPcmPlayer(context),
            fallback = null,
            onError = { message ->
                // Surface the classified failure where the UI already
                // shows TTS status; never claim the utterance spoke.
                ttsStatusNotice = message
                Log.w(TAG, "Gemini online TTS unavailable: $message")
            },
            // speak() input has already been through preprocessText()
            // (which includes BurmeseTtsTextNormalizer) — do not
            // normalize a second time.
            textNormalizer = { it }
        ).also { geminiTtsEngineOrNull = it }
    }

    /**
     * §7: online-first utterance. On ANY online failure (already
     * surfaced via the engine's onError) the utterance falls back to
     * the system path; on success the completion callback fires here.
     */
    private fun speakOnlineFirst(cleanText: String, onEndCallback: (() -> Unit)?) {
        val engine = geminiTtsEngine()
        onlineScope.launch {
            val spokenOnline = try {
                engine.speak(cleanText)
                true
            } catch (e: Exception) {
                false
            }
            if (spokenOnline) {
                onEndCallback?.invoke()
            } else {
                speakViaSystem(cleanText, onEndCallback)
            }
        }
    }

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
            // FIX-02: bind the audit's selected installed Myanmar voice
            // explicitly, instead of relying on engine locale defaults.
            voicePackManager.selectedMyanmarVoice()?.let { chosenVoice ->
                try {
                    tts?.voice = chosenVoice
                    Log.i(TAG, "Applied selected Myanmar TTS voice: ${chosenVoice.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not apply selected Myanmar voice: ${e.message}")
                }
            }
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

        // §7 (2026-10-23): when the user has selected Gemini online
        // TTS, try it first; any failure (surfaced by the engine's
        // onError) falls back to the system path below for this
        // utterance. Default selection is SYSTEM — unchanged behavior.
        if (currentTtsEngineSelection() == TtsEngineType.GEMINI_ONLINE) {
            speakOnlineFirst(cleanText, onEndCallback)
            return
        }

        speakViaSystem(cleanText, onEndCallback)
    }

    @Synchronized
    private fun speakViaSystem(cleanText: String, onEndCallback: (() -> Unit)?) {
        // FIX-02 (device defect B2 — robotic Burmese voice): the old
        // phonetic fallback transliterated Burmese into Latin phonetics
        // and read it with a US English voice whenever the my-MM voice
        // data was missing. That is exactly the robotic voice the user
        // reported, and it dishonestly presented itself as a Burmese
        // reply. It is REMOVED (the dead transliterator itself was
        // deleted under ISSUE-045). When no installed Myanmar voice
        // exists, the user now hears one honest English notice
        // (plus the recovery Toast/event with install guidance) — the
        // Burmese text itself is never fed to a foreign voice.
        val voicePackManager = MyanmarTTSVoicePackManager.getInstance(context)
        if (com.example.ocr.MyanmarScriptDetector.containsMyanmar(cleanText)) {
            val decision = BurmeseSpeechPolicy.decide(
                containsMyanmar = true,
                voiceChoice = voicePackManager.currentMyanmarVoiceChoice()
            )
            if (decision == BurmeseSpeechDecision.HONEST_NOTICE_NO_MYANMAR_VOICE) {
                if (isReady && tts != null) {
                    // UI surfacing + failure record (no completion
                    // callback here — the notice utterance carries it).
                    voicePackManager.handleMissingVoicePackFailure(context, cleanText, null)
                    tts?.language = Locale.US
                    queue.add(Pair(BurmeseSpeechPolicy.ENGLISH_NOTICE, onEndCallback))
                    if (!isSpeaking) {
                        processQueue()
                    }
                } else {
                    voicePackManager.handleMissingVoicePackFailure(context, cleanText, onEndCallback)
                }
                return
            }
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
        geminiTtsEngineOrNull?.stop()
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
        geminiTtsEngineOrNull?.close()
        geminiTtsEngineOrNull = null
        onlineScope.cancel()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
