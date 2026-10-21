package com.kyawgyi.voice

import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.BurmeseTtsTextNormalizer

/** Canonical Burmese text normalization extension used across the KyawGyi voice pipeline. */
fun String.normalizedMyanmar(): String = BurmeseNluPreprocessor.normalize(this)

/** Normalizes common Burmese-English aliases before intent parsing or TTS. */
class MyanmarSpeechPhoneticNaturalizer(
    private val codeSwitchNormalizer: CodeSwitchNormalizer = CodeSwitchNormalizer(),
    private val myanmarNumberNormalizer: MyanmarNumberNormalizer = MyanmarNumberNormalizer(),
    private val myanmarVerbalizer: MyanmarVerbalizer = MyanmarVerbalizer(myanmarNumberNormalizer, codeSwitchNormalizer)
) {
    private val aliases = linkedMapOf(
        "ယူကျု့ဘ်" to "YouTube", "ယူကျု" to "YouTube",
        "တယ်လီဂရမ်" to "Telegram", "တစ်တော့ခ်" to "TikTok", "တစ်တော့" to "TikTok",
        "ကေဘီဇက်ပေး" to "KBZPay", "ဝေ့ဗ်ပေး" to "WavePay",
        "ဆက်တင်စ်" to "Settings", "ဆက်တင်" to "Settings",
        "ဝိုင်ဖိုင်" to "Wi-Fi", "ဘလူးတုသ်" to "Bluetooth", "ဘလူးတု" to "Bluetooth",
        "ဟော့စပေါ့တ်" to "Hotspot", "ဟော့စပေါ့" to "Hotspot",
        "ဒေတာ" to "mobile data",
    )
    private val digits = mapOf(
        "သုည" to '0', "ဇီးရိုး" to '0', "တစ်" to '1', "နှစ်" to '2', "သုံး" to '3',
        "သံုး" to '3', "လေး" to '4', "ငါး" to '5', "ခြောက်" to '6',
        "ခုနစ်" to '7', "ခုနှစ်" to '7', "ရှစ်" to '8', "ကိုး" to '9',
    )

    fun normalizeForIntent(input: String): String {
        val homophoneResolved = MyanmarPronunciationLexicon.disambiguateSttHomophones(input)
        var out = codeSwitchNormalizer.normalizeSttTranscript(homophoneResolved)
        aliases.entries.sortedByDescending { it.key.length }.forEach { (from, to) ->
            out = out.replace(from, to, ignoreCase = true)
        }
        return out
    }

    /** Converts a digit phrase while retaining non-number command text. */
    fun normalizeSpokenDigits(input: String): String {
        val tokens = input.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return input
        val converted = buildString {
            tokens.forEach { token ->
                val digit = digits[token.trim('၊', '။', ',', '.')]
                if (digit != null) append(digit) else append(token)
            }
        }
        return if (converted.any { it.isDigit() }) converted else input
    }

    /** Written response expansion for F5/Android TTS. */
    fun normalizeForTts(input: String): String {
        val verbalized = myanmarVerbalizer.verbalizeForTts(input)
        return MyanmarPronunciationLexicon.applyTtsPronunciationG2P(verbalized)
    }
}
