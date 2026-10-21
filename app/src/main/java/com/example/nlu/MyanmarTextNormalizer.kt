package com.example.nlu

import java.text.Normalizer
import java.util.Locale

/**
 * Stage 1 - Myanmar Text Normalizer
 *
 * Normalizes natural spoken and written Myanmar text/speech without destroying the underlying semantic meaning.
 * Handles:
 * - Myanmar Unicode standard normalization (NFC, diacritic order, zero-width spaces)
 * - Basic Zawgyi detection & conversion
 * - Spoken dialect and colloquial tone variations
 * - Polite particles (ပါ, ပါဦး, ပါရစေ, ခင်ဗျာ, ရှင်, လေး, စမ်းပါ, လိုက်, နော်, တချက်လောက်, တစ်ချက်လောက်)
 * - Filler words and conversational pauses (ဟိုဟာ, အဲဒါ, ဟိုလေ, နေဦး, ဘာလဲ, အမ်)
 * - Wake word and attention prefixes (ဟေး ကျော်ကြီး, ကျော်ကြီးရေ, ကျော်ကြီး, ဟေ့)
 * - Burmese numerals (၀-၉) and spoken number words to standard digits
 * - Mixed Myanmar + English code-switching ("Camera ဖွင့်", "Call လုပ်ပေး", "Music play လုပ်ပေး")
 */
object MyanmarTextNormalizer {

    // Burmese numerals mapping
    private val BURMESE_DIGITS = mapOf(
        '၀' to '0', '၁' to '1', '၂' to '2', '၃' to '3', '၄' to '4',
        '၅' to '5', '၆' to '6', '၇' to '7', '၈' to '8', '၉' to '9'
    )

    // Spoken number words
    private val SPOKEN_DIGIT_WORDS = listOf(
        "သုည" to "0",
        "တစ်" to "1", "တစ္" to "1", "တ" to "1",
        "နှစ်" to "2", "နွစ္" to "2",
        "သုံး" to "3", "သံုး" to "3",
        "လေး" to "4",
        "ငါး" to "5",
        "ခြောက်" to "6", "ေျခာက္" to "6",
        "ခုနစ်" to "7", "ခုနှစ်" to "7", "ခုန်" to "7",
        "ရှစ်" to "8", "ရွစ္" to "8",
        "ကိုး" to "9"
    )

    // Wake words to strip from command head
    private val WAKE_WORD_PATTERNS = listOf(
        Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီးရေ|ကျော်ကြီး|ဟေ့\\s*ကျော်ကြီး|ဟေ့|ဟိုင်း|အေအိုင်|မင်္ဂလာပါ\\s*ကျော်ကြီး|မင်္ဂလာပါ|မင်္ဂလာပါခင်ဗျာ)\\s*([,၊\\s]+)?", RegexOption.IGNORE_CASE),
        Regex("^(hey\\s*kyaw\\s*gyi|kyaw\\s*gyi|hey|hi|hello)\\s*([,၊\\s]+)?", RegexOption.IGNORE_CASE)
    )

    // Filler words and stuttering artifacts
    private val FILLER_WORDS = listOf(
        "ဟိုဟာ", "အဲဒါ", "ဟိုလေ", "နေဦး", "နေအုံး", "ဘာလဲ", "ဟိုဟာလေ", "အမ်", "အာ",
        "ဟို", "အဲ", "ဟာ", "ဆိုတော့", "စသဖြင့်", "တစ်ခုလောက်", "တစ်ချက်လောက်", "တချက်လောက်", "ခဏလောက်", "နည်းနည်းလောက်"
    )

    // Spoken Dialect & Pronunciation tone corrections (without replacing Myanmar app/tool nouns)
    private val DIALECT_CORRECTIONS = mapOf(
        "ပွင့်" to "ဖွင့်",
        "ဖွင့်" to "ဖွင့်",
        "ဖှင့်" to "ဖွင့်",
        "ဖွင်" to "ဖွင့်",
        "ပိတ်ပါဦး" to "ပိတ်ပါ",
        "ပိတ်ပစ်" to "ပိတ်",
        "မောင်း" to "ဖွင့်",
        "စ" to "ဖွင့်",
        "ဆက်လိုက်" to "ဆက်",
        "ခေါ်လိုက်" to "ခေါ်",
        "ပို့လိုက်" to "ပို့",
        "လှမ်းဆက်" to "ဆက်",
        "လှမ်းခေါ်" to "ခေါ်",
        "ကြည့်မယ်" to "ကြည့်",
        "ဖွင့်ပြ" to "ဖွင့်",
        "ပြပေး" to "ပြ",
        "ကျောကြီး" to "ကျော်ကြီး",
        "ကျောဂီ" to "ကျော်ကြီး",
        "ကျော်ဂီ" to "ကျော်ကြီး",
        "ကြော်ကြီး" to "ကျော်ကြီး"
    )

    // Polite markers and endings to remove for root semantic parsing
    private val POLITE_POSTFIXES = listOf(
        "ပါဦးခင်ဗျာ", "ပါဦးခင်ဗျ", "ပါဦးရှင်", "ပါရစေ", "ပါအုံး", "ပါဦး",
        "ပေးပါဦး", "ပေးပါအုံး", "ပေးပါခင်ဗျာ", "ပေးပါခင်ဗျ", "ပေးပါရှင်", "ပေးပါ",
        "ပေးစမ်းပါ", "ပေးစမ်း", "ပေးဦး", "ပေးအုံး", "လိုက်ပါ", "လိုက်ပါဦး", "လိုက်ဦး",
        "ပါ", "ခင်ဗျာ", "ခင်ဗျ", "ရှင်", "နော်", "စမ်းပါ", "စမ်း", "ဦး", "အုံး", "လေး"
    )

    /**
     * Primary Normalization Pipeline
     * Returns cleaned, standardized Unicode text with wake words stripped and phonetic variances resolved.
     */
    fun normalize(rawInput: String?): String {
        if (rawInput.isNullOrBlank()) return ""

        var text = rawInput.trim()

        // 1. Convert Zawgyi to Unicode if detected
        if (isZawgyi(text)) {
            text = zawgyiToUnicode(text)
        }

        // 2. Unicode Canonical Decomposition & Composition (NFC)
        text = Normalizer.normalize(text, Normalizer.Form.NFC)

        // 3. Strip zero-width spaces, invisible characters & control chars
        text = text.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF\\u00A0]"), " ")

        // 4. Strip Wake Word & Prefix greetings
        for (pattern in WAKE_WORD_PATTERNS) {
            text = text.replace(pattern, "")
        }

        // 5. Replace Burmese Digits with standard ASCII Digits
        text = convertBurmeseNumerals(text)

        // 6. Deduplicate repeated tone markers and punctuation
        text = text.replace(Regex("့+"), "့")
        text = text.replace(Regex("း+"), "း")
        text = text.replace(Regex("[\\s,၊။]+"), " ").trim()

        // 7. Strip Conversational Fillers
        for (filler in FILLER_WORDS) {
            text = text.replace(Regex("\\b${Regex.escape(filler)}\\b|(?<=[\\s]|^)${Regex.escape(filler)}(?=[\\s]|\$)"), " ")
        }

        // 8. Apply spoken verb and tone corrections
        for ((misspoken, corrected) in DIALECT_CORRECTIONS) {
            text = text.replace(misspoken, corrected)
        }

        // Clean extra spacing
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Extracts politeness level before stripping polite particles
     */
    fun extractPoliteness(rawInput: String): PolitenessLevel {
        val text = rawInput.lowercase(Locale.ROOT)
        return when {
            text.contains("အခုချက်ချင်း") || text.contains("အမြန်") -> PolitenessLevel.URGENT
            text.contains("ပါဦး") || text.contains("ပါရစေ") || text.contains("ခင်ဗျာ") ||
                text.contains("ရှင်") || text.contains("ပေးပါ") || text.contains("လေး") -> PolitenessLevel.POLITE
            text.contains("လိုက်") || text.contains("ဦး") || text.contains("နော်") -> PolitenessLevel.CASUAL
            else -> PolitenessLevel.DIRECT
        }
    }

    /**
     * Strips polite postfixes to yield the core semantic verb phrase
     */
    fun stripPoliteParticles(text: String): String {
        var clean = text
        for (postfix in POLITE_POSTFIXES) {
            if (clean.endsWith(postfix)) {
                clean = clean.substring(0, clean.length - postfix.length).trim()
            }
        }
        return clean.trim()
    }

    /**
     * Converts Burmese numerals (e.g. ၀၉၁၂၃၄၅၆၇၈) into ASCII digits (0912345678)
     */
    fun convertBurmeseNumerals(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(BURMESE_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Converts spoken Myanmar number sequences (e.g. "သုည ကိုး တစ် နှစ် သုံး") into digits
     */
    fun normalizeSpokenPhoneSequence(input: String): String {
        var result = convertBurmeseNumerals(input)
        for ((word, digit) in SPOKEN_DIGIT_WORDS) {
            result = result.replace(word, digit)
        }
        // Remove spaces between numbers
        return result.replace(Regex("(?<=\\d)\\s+(?=\\d)"), "")
    }

    /**
     * Detects if a string is encoded in Zawgyi
     */
    fun isZawgyi(input: String): Boolean {
        // Zawgyi-specific character combinations heuristic
        val zgSpecific = Regex("[ႏ႕႐႑႒႓႔႖႗႘႙]|္[က-အ]|ေ[က-အ]ာ|([က-အ]ျ[က-အ])")
        return zgSpecific.containsMatchIn(input)
    }

    /**
     * Minimal Zawgyi to Unicode Converter
     */
    fun zawgyiToUnicode(input: String): String {
        var text = input
        text = text.replace("ႏ", "န")
        text = text.replace("႕", "့")
        text = text.replace("္", "်")
        text = text.replace("ေမမေ", "မေမေ")
        text = text.replace("ဖုန္း", "ဖုန်း")
        return text
    }
}
