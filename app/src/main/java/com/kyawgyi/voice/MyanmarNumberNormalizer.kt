package com.kyawgyi.voice

import com.example.nlu.BurmeseNumberNormalizer
import com.example.nlu.BurmeseTtsTextNormalizer

/**
 * MyanmarNumberNormalizer (Requirements 10 — Bidirectional Burmese Number, Currency & Digit Normalizer)
 *
 * Bridges:
 * 1. **STT / Intent Direction ([spokenBurmeseToDigits])**: Converts spoken Burmese number phrases
 *    (e.g. `"သုံးသောင်းငါးထောင်"` -> `"35000"`, `"သုည ကိုး နှစ် သုံး"` -> `"0923"`) and Myanmar digits
 *    (`၀-၉`) into canonical ASCII digits (`0-9`) for phone dialing, alarms, timers, and transfers.
 * 2. **TTS / Verbalization Direction ([digitsToSpokenBurmese])**: Expands numbers, currency (`ကျပ်`, `MMK`),
 *    percentages (`%`), decimals (`ဒသမ`), and phone numbers (`09...`) into natural Burmese place-value
 *    words (`သိန်း`, `သောင်း`, `ထောင်`, `ရာ`, `ဆယ်`).
 */
class MyanmarNumberNormalizer {

    fun spokenBurmeseToDigits(text: String): String {
        if (text.isBlank()) return text
        val withAsciiDigits = BurmeseNumberNormalizer.convertMyanmarDigitsToWestern(text)
        return BurmeseNumberNormalizer.normalizeSpokenUtterance(withAsciiDigits)
    }

    fun digitsToSpokenBurmese(text: String): String {
        if (text.isBlank()) return text
        return BurmeseTtsTextNormalizer.normalizeForTts(text)
    }

    fun numberToBurmeseWords(value: Long): String {
        return BurmeseTtsTextNormalizer.numberToBurmeseWords(value)
    }
}
