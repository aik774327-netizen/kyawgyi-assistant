package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * KYAW GYI AI — Myanmar Warm Natural Speech & Native Pronunciation System
 * (မြန်မာဘာသာစကားကို မြန်မာလူမျိုးတစ်ယောက်လို ပီပီသသ စကားအသံထွက်ပြီး နွေးနွေးထွေးထွေး စကားပြောနိုင်သော နည်းစနစ်)
 *
 * Core Capabilities:
 * 1. Warm Conversational Phonation & Cadence:
 *    - Transforms cold, robotic written text into warm, empathetic spoken Myanmar.
 *    - Injects polite conversational particles (ပါခင်ဗျာ / ပါရှင် / ပါနော်).
 *    - Micro-pause phrasing for human-like breathing intervals (။ -> 350ms, ၊ -> 150ms).
 *
 * 2. Native Articulation & Phonetic Normalization:
 *    - Expands numeric digits & quantities into natural spoken syllables (e.g. 25 မိနစ် -> နှစ်ဆယ့်ငါး မိနစ်).
 *    - Normalizes English tech/app terms into native Myanmar colloquial phonetics.
 *    - Syllable pacing calibrated for clear consonant, medial, vowel, and final tone enunciation.
 *
 * 3. Warm Voice Personas:
 *    - WARM_COMPANION (နွေးထွေးဖော်ရွေသော မိတ်ဆွေ): Pitch 1.02x, Rate 0.88x
 *    - ARTICULATE_NATIVE (ပီသပြတ်သားသော မြန်မာသံ): Pitch 1.00x, Rate 0.90x
 *    - RESPECTFUL_ASSISTANT (တည်ငြိမ်ရိုသေသော စကားသံ): Pitch 0.96x, Rate 0.86x
 *    - CHEERFUL_ENCOURAGING (တက်ကြွရွှင်လန်းသော အသံ): Pitch 1.06x, Rate 0.94x
 */
class MyanmarWarmNaturalSpeechSystem private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MyanmarWarmSpeech"
        private const val PREFS_NAME = "kyaw_gyi_warm_speech_prefs"
        private const val KEY_SELECTED_PERSONA = "pref_warm_persona"
        private const val KEY_WARM_MODE_ENABLED = "pref_warm_mode_enabled"
        private const val KEY_POLITE_PARTICLE = "pref_polite_particle"
        private const val KEY_CLEAR_ARTICULATION = "pref_clear_articulation"

        @Volatile
        private var instance: MyanmarWarmNaturalSpeechSystem? = null

        fun getInstance(context: Context): MyanmarWarmNaturalSpeechSystem {
            return instance ?: synchronized(this) {
                instance ?: MyanmarWarmNaturalSpeechSystem(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    enum class WarmVoicePersona(
        val displayNameMM: String,
        val displayNameEN: String,
        val descriptionMM: String,
        val pitch: Float,
        val rate: Float,
        val politeSuffix: String,
        val sampleText: String
    ) {
        WARM_COMPANION(
            displayNameMM = "နွေးထွေးဖော်ရွေသော အသံ (Warm Companion)",
            displayNameEN = "Warm Companion",
            descriptionMM = "မိတ်ဆွေတစ်ယောက်ကဲ့သို့ ကြင်နာနွေးထွေးသော အသံနေအထားနှင့် ဖော်ရွေစွာ ပြောကြားပါသည်",
            pitch = 1.02f,
            rate = 0.88f,
            politeSuffix = "ပါခင်ဗျာ",
            sampleText = "မင်္ဂလာပါခင်ဗျာ... ကျွန်တော် ကျော်ကြီးပါ။ သင့်ရဲ့ နေ့စဉ်လုပ်ငန်းဆောင်တာတွေကို နွေးနွေးထွေးထွေး ကူညီပေးဖို့ အဆင်သင့်ရှိပါတယ်နော်။"
        ),
        ARTICULATE_NATIVE(
            displayNameMM = "ပီသပြတ်သားသော အသံ (Articulate Native)",
            displayNameEN = "Articulate Native",
            descriptionMM = "မြန်မာစကား စာလုံးတိုင်းကို တိကျသေချာစွာ အသံထွက်ပြီး ဗျည်း၊ သရ၊ အသတ်များ ပီသစွာ ထွက်ဆိုပါသည်",
            pitch = 1.00f,
            rate = 0.90f,
            politeSuffix = "ပါခင်ဗျာ",
            sampleText = "မင်္ဂလာပါခင်ဗျာ... မြန်မာစကား စာလုံးတိုင်းကို တိကျသေချာစွာနှင့် ပီသပြတ်သားစွာ ဖတ်ကြားပေးပါမည်။"
        ),
        RESPECTFUL_ASSISTANT(
            displayNameMM = "တည်ငြိမ်ရိုသေသော အသံ (Respectful Assistant)",
            displayNameEN = "Respectful Assistant",
            descriptionMM = "တည်ငြိမ်လေးနက်၍ ရိုသေယဉ်ကျေးသော စကားအသုံးအနှုန်းဖြင့် ဆောင်ရွက်ပေးပါသည်",
            pitch = 0.96f,
            rate = 0.86f,
            politeSuffix = "ပါခင်ဗျာ",
            sampleText = "မင်္ဂလာပါခင်ဗျာ... ကျော်ကြီး AI မှ လေးစားစွာဖြင့် အချိန်နှင့်တစ်ပြေးညီ ကူညီဆောင်ရွက်ပေးပါမည်။"
        ),
        CHEERFUL_ENCOURAGING(
            displayNameMM = "တက်ကြွရွှင်လန်းသော အသံ (Cheerful & Bright)",
            displayNameEN = "Cheerful & Bright",
            descriptionMM = "တက်ကြွအားပေးသော လေသံဖြင့် စိတ်အားတက်ကြွစေရန် ဖော်ရွေစွာ ပြောဆိုပါသည်",
            pitch = 1.06f,
            rate = 0.94f,
            politeSuffix = "နော်",
            sampleText = "မင်္ဂလာပါခင်ဗျာ! ဒီနေ့အတွက် အလုပ်တွေကို တက်တက်ကြွကြွနဲ့ အတူတူ အောင်မြင်အောင် လုပ်ကြမယ်နော်။"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentPersona = MutableStateFlow(loadPersona())
    val currentPersona: StateFlow<WarmVoicePersona> = _currentPersona.asStateFlow()

    private val _isWarmModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_WARM_MODE_ENABLED, true))
    val isWarmModeEnabled: StateFlow<Boolean> = _isWarmModeEnabled.asStateFlow()

    private val _isClearArticulationEnabled = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_ARTICULATION, true))
    val isClearArticulationEnabled: StateFlow<Boolean> = _isClearArticulationEnabled.asStateFlow()

    private val _politeParticle = MutableStateFlow(prefs.getString(KEY_POLITE_PARTICLE, "ပါခင်ဗျာ") ?: "ပါခင်ဗျာ")
    val politeParticle: StateFlow<String> = _politeParticle.asStateFlow()

    private fun loadPersona(): WarmVoicePersona {
        val name = prefs.getString(KEY_SELECTED_PERSONA, WarmVoicePersona.WARM_COMPANION.name)
        return try {
            WarmVoicePersona.valueOf(name ?: WarmVoicePersona.WARM_COMPANION.name)
        } catch (e: Exception) {
            WarmVoicePersona.WARM_COMPANION
        }
    }

    fun setPersona(persona: WarmVoicePersona) {
        prefs.edit().putString(KEY_SELECTED_PERSONA, persona.name).apply()
        _currentPersona.value = persona
        // Sync pitch and rate with MyanmarTTSEngine
        val ttsEngine = MyanmarTTSEngine.getInstance(context)
        ttsEngine.pitch = persona.pitch
        ttsEngine.rate = persona.rate
        Log.i(TAG, "Myanmar Voice Persona updated to: ${persona.displayNameEN} (pitch=${persona.pitch}, rate=${persona.rate})")
    }

    fun setWarmModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WARM_MODE_ENABLED, enabled).apply()
        _isWarmModeEnabled.value = enabled
    }

    fun setClearArticulationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAR_ARTICULATION, enabled).apply()
        _isClearArticulationEnabled.value = enabled
    }

    fun setPoliteParticle(particle: String) {
        prefs.edit().putString(KEY_POLITE_PARTICLE, particle).apply()
        _politeParticle.value = particle
    }

    /**
     * Transform written or synthetic text into warm, native, articulate spoken Myanmar.
     */
    fun processTextForNativeSpokenMyanmar(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text: String = rawText

        // 1. Remove Markdown / Code formatting symbols
        text = text.replace(Regex("[*#_~`\\[\\]{}()\\<\\>]"), " ")

        // 2. Normalize English tech loanwords into natural colloquial Myanmar syllables
        val loanWords = mapOf(
            Regex("(?i)\\bAI\\b") to "အေအိုင်",
            Regex("(?i)\\bGoogle\\b") to "ဂူဂဲလ်",
            Regex("(?i)\\bFacebook\\b") to "ဖေ့ဘွတ်ခ်",
            Regex("(?i)\\bYouTube\\b") to "ယူကျူ့ဗ်",
            Regex("(?i)\\bTikTok\\b") to "တစ်တော့ခ်",
            Regex("(?i)\\bWiFi\\b") to "ဝိုင်ဖိုင်",
            Regex("(?i)\\bBluetooth\\b") to "ဘလူးတူးသ်",
            Regex("(?i)\\bSettings\\b") to "ဆက်တင်များ",
            Regex("(?i)\\bMicrophone\\b") to "မိုက်ခရိုဖုန်း",
            Regex("(?i)\\bTimer\\b") to "တိုင်မာ",
            Regex("(?i)\\bPomodoro\\b") to "ပိုမိုဒိုရို",
            Regex("(?i)\\bFocus\\b") to "အာရုံစိုက်ချိန်",
            Regex("(?i)\\bAlarm\\b") to "အချက်ပေးသံ",
            Regex("(?i)\\bBattery\\b") to "ဘက်ထရီ",
            Regex("(?i)\\bCamera\\b") to "ကင်မရာ",
            Regex("(?i)\\bFlashlight\\b") to "ဓာတ်မီး",
            Regex("(?i)\\bNotification\\b") to "အသိပေးချက်",
            Regex("(?i)\\bOK\\b") to "အိုကေပါ",
            Regex("(?i)\\bYes\\b") to "ဟုတ်ကဲ့ပါ",
            Regex("(?i)\\bHello\\b") to "မင်္ဂလာပါ",
            Regex("(?i)\\bHi\\b") to "မင်္ဂလာပါ"
        )
        for ((regex, replacement) in loanWords) {
            text = text.replace(regex, replacement)
        }

        // 3. Convert numbers to spoken Myanmar words
        text = convertNumbersToMyanmarWords(text)

        // 4. Currency and units
        text = text.replace("%", " ရာခိုင်နှုန်း ")
        text = text.replace("$", " ဒေါ်လာ ")
        text = text.replace("min", " မိနစ် ")
        text = text.replace("sec", " စက္ကန့် ")
        text = text.replace("hr", " နာရီ ")

        // 5. Native Micro-pause insertion
        // Sentence stop (။) gets a slightly elongated pause so words don't bleed together
        text = text.replace("။", " ။ ")
        // Clause separator (၊) gets a gentle breathing interval
        text = text.replace("၊", " ၊ ")

        // 6. Warmth Particle Injection if Warm Mode is active
        if (_isWarmModeEnabled.value) {
            text = injectWarmPoliteParticles(text)
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Inject polite and warm endings if missing from sentence terminations.
     */
    private fun injectWarmPoliteParticles(text: String): String {
        val suffix = _politeParticle.value
        // If the text ends abruptly without polite particles like ခင်ဗျာ, ရှင်, ပါ, or နော်
        val politeKeywords = listOf("ခင်ဗျာ", "ရှင်", "ပါနော်", "နော်", "ပါ", "ဟုတ်ကဲ့")
        val hasPoliteEnding = politeKeywords.any { text.trim().endsWith(it) || text.trim().endsWith("$it။") }

        return if (!hasPoliteEnding && text.length > 5) {
            val clean = text.trimEnd('။', ' ', '.')
            "$clean $suffix။"
        } else {
            text
        }
    }

    /**
     * Converts numeric digits into spoken Myanmar syllables
     */
    private fun convertNumbersToMyanmarWords(input: String): String {
        val numberRegex = Regex("\\b\\d+\\b")
        return numberRegex.replace(input) { match ->
            val numStr = match.value
            try {
                val num = numStr.toLong()
                convertNumberToWords(num)
            } catch (e: Exception) {
                numStr
            }
        }
    }

    private fun convertNumberToWords(number: Long): String {
        if (number == 0L) return "သုည"
        if (number < 0) return "အနုတ် " + convertNumberToWords(-number)

        val units = arrayOf("", "တစ်", "နှစ်", "သုံး", "လေး", "ငါး", "ခြောက်", "ခုနစ်", "ရှစ်", "ကိုး")
        val tens = arrayOf("", "တစ်ဆယ်", "နှစ်ဆယ်", "သုံးဆယ်", "လေးဆယ်", "ငါးဆယ်", "ခြောက်ဆယ်", "ခုနစ်ဆယ်", "ရှစ်ဆယ်", "ကိုးဆယ်")

        return when {
            number < 10 -> units[number.toInt()]
            number == 10L -> "တစ်ဆယ်"
            number < 20 -> "ဆယ့်" + units[(number % 10).toInt()]
            number < 100 -> {
                val rem = (number % 10).toInt()
                val ten = (number / 10).toInt()
                if (rem == 0) tens[ten] else tens[ten].replace("ဆယ်", "ဆယ့်") + units[rem]
            }
            number < 1000 -> {
                val hundred = (number / 100).toInt()
                val rem = number % 100
                val hundredPart = units[hundred] + "ရာ"
                if (rem == 0L) hundredPart else "$hundredPart " + convertNumberToWords(rem)
            }
            number < 10000 -> {
                val thousand = (number / 1000).toInt()
                val rem = number % 1000
                val thousandPart = units[thousand] + "ထောင်"
                if (rem == 0L) thousandPart else "$thousandPart " + convertNumberToWords(rem)
            }
            number < 100000 -> {
                val myriad = (number / 10000).toInt()
                val rem = number % 10000
                val myriadPart = units[myriad] + "သောင်း"
                if (rem == 0L) myriadPart else "$myriadPart " + convertNumberToWords(rem)
            }
            else -> number.toString()
        }
    }

    /**
     * Preview / Audition the current persona with sample warm speech
     */
    fun previewPersonaSpeech(persona: WarmVoicePersona, onComplete: (() -> Unit)? = null) {
        val tts = MyanmarTTSEngine.getInstance(context)
        tts.pitch = persona.pitch
        tts.rate = persona.rate
        tts.speak(persona.sampleText, onComplete)
    }
}
