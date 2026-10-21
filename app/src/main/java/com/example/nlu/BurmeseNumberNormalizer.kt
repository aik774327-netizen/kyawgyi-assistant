package com.example.nlu

import android.util.Log
import java.util.regex.Pattern

/**
 * BurmeseNumberNormalizer (Layer 6 Myanmar Linguistics & Layer 2 ASR Normalization)
 *
 * Implements high-precision conversion of spoken and written Myanmar numbers:
 * 1. Spoken Digit Sequences -> Western Digits ("သုည ကိုး ခုနစ် ရှစ်..." -> "0978...")
 * 2. Myanmar Digits -> Western Digits ("၀၉၇၈..." -> "0978...")
 * 3. Compound / Grouped Spoken Numbers -> Integer Values ("တစ်ရာ့ငါးဆယ်" -> "150", "တစ်သောင်း" -> "10000")
 * 4. Myanmar Phone Number Extractor (E.164 and local 09-prefixed standards)
 */
object BurmeseNumberNormalizer {

    private const val TAG = "BurmeseNumberNormalizer"

    // Single digit words map
    private val SPOKEN_DIGIT_MAP = mapOf(
        "သုည" to "0",
        "တစ်" to "1",
        "တစ" to "1",
        "နှစ်" to "2",
        "နှစ" to "2",
        "သုံး" to "3",
        "လေး" to "4",
        "ငါး" to "5",
        "ခြောက်" to "6",
        "ခွောက်" to "6",
        "ခုနစ်" to "7",
        "ခုနှစ်" to "7",
        "ခွန်" to "7",
        "ခုန်" to "7",
        "ရှစ်" to "8",
        "ရစ်" to "8",
        "ကိုး" to "9"
    )

    // Myanmar script digits mapping
    private val MYANMAR_DIGIT_MAP = mapOf(
        '၀' to '0',
        '၁' to '1',
        '၂' to '2',
        '၃' to '3',
        '၄' to '4',
        '၅' to '5',
        '၆' to '6',
        '၇' to '7',
        '၈' to '8',
        '၉' to '9'
    )

    // Multipliers for grouped / compound spoken numbers
    private val MULTIPLIERS = mapOf(
        "ဆယ်" to 10L,
        "ဆယ့်" to 10L,
        "ဆယ" to 10L,
        "ရာ" to 100L,
        "ရာ့" to 100L,
        "ထောင်" to 1000L,
        "ထောင့်" to 1000L,
        "သောင်း" to 10000L,
        "သိန်း" to 100000L,
        "သန်း" to 1000000L,
        "ကုဋေ" to 10000000L
    )

    private val DIGIT_VALUES = mapOf(
        "သုည" to 0L,
        "တစ်" to 1L,
        "တစ" to 1L,
        "နှစ်" to 2L,
        "သုံး" to 3L,
        "လေး" to 4L,
        "ငါး" to 5L,
        "ခြောက်" to 6L,
        "ခုနစ်" to 7L,
        "ခုနှစ်" to 7L,
        "ခွန်" to 7L,
        "ရှစ်" to 8L,
        "ကိုး" to 9L
    )

    /**
     * Converts any Myanmar script digit characters ('၀'..'၉') to Western digits ('0'..'9').
     */
    fun convertMyanmarDigitsToWestern(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        for (ch in text) {
            val western = MYANMAR_DIGIT_MAP[ch]
            if (western != null) {
                sb.append(western)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Replaces spoken digit names in phone number contexts into Western digits.
     * e.g., "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည" -> "0978953210"
     */
    fun normalizeSpokenDigits(text: String): String {
        if (text.isBlank()) return text

        var result = convertMyanmarDigitsToWestern(text)

        // Regex matching sequences of spoken digit words separated by spaces, punctuation, or string bounds
        val spokenWords = SPOKEN_DIGIT_MAP.keys.sortedByDescending { it.length }
        val pattern = Regex("(?:^|(?<=\\s|[^က-အ\\u102B-\\u103E]))(" + spokenWords.joinToString("|") { Pattern.quote(it) } + ")(?=$|\\s|[^က-အ\\u102B-\\u103E])")

        var prev = ""
        while (prev != result) {
            prev = result
            result = pattern.replace(result) { matchResult ->
                val word = matchResult.groupValues[1]
                SPOKEN_DIGIT_MAP[word] ?: word
            }
        }

        // Collapse spaced single digits that look like phone numbers or code sequences (e.g. "0 9 7 8 9" -> "09789")
        val digitSpacedRegex = Regex("(?<!\\d)\\d(?:\\s+\\d)+(?!\\d)")
        result = digitSpacedRegex.replace(result) { match ->
            match.value.replace(Regex("\\s+"), "")
        }

        return result
    }

    /**
     * Converts compound Myanmar spoken number phrases into integer values.
     * e.g.,
     * - "တစ်ဆယ်" -> 10
     * - "တစ်ရာ့ငါးဆယ်" -> 150
     * - "တစ်သောင်း ငါးထောင်" -> 15000
     * - "နှစ်ဆယ့်ငါး" -> 25
     * - "ကိုးဆယ်" -> 90
     */
    fun parseSpokenCompoundNumber(phrase: String): Long? {
        val clean = phrase.trim()
            .replace(" ", "")
            .replace("တစ်", "၁")
            .replace("နှစ်", "၂")
            .replace("သုံး", "၃")
            .replace("လေး", "၄")
            .replace("ငါး", "၅")
            .replace("ခြောက်", "၆")
            .replace("ခုနစ်", "၇")
            .replace("ခုနှစ်", "၇")
            .replace("ရှစ်", "၈")
            .replace("ကိုး", "၉")

        // Direct single digit check
        DIGIT_VALUES[clean]?.let { return it }

        var total = 0L
        var currentChunk = 0L
        var currentDigit = 1L

        var i = 0
        val len = clean.length
        var hasMatched = false

        while (i < len) {
            val ch = clean[i]

            // Check if char is 1-9 digit
            if (ch in '၁'..'၉') {
                currentDigit = (ch - '၁' + 1).toLong()
                hasMatched = true
                i++
                continue
            }

            var matchedMult = 0L
            var multLen = 0

            for ((multWord, multVal) in MULTIPLIERS.entries.sortedByDescending { it.key.length }) {
                if (clean.startsWith(multWord, i)) {
                    matchedMult = multVal
                    multLen = multWord.length
                    break
                }
            }

            if (matchedMult > 0L) {
                hasMatched = true
                if (matchedMult >= 100000L) {
                    currentChunk += currentDigit * (if (matchedMult == 100000L) 100000L else matchedMult)
                    total += currentChunk
                    currentChunk = 0L
                    currentDigit = 0L
                } else {
                    currentChunk += currentDigit * matchedMult
                    currentDigit = 0L
                }
                i += multLen
            } else {
                i++
            }
        }

        total += currentChunk + currentDigit
        return if (hasMatched && total > 0L) total else null
    }

    /**
     * Extracts a clean phone number from an input string, supporting both spoken words,
     * Myanmar digits, and standard Western digits.
     * e.g.:
     * - "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည ကို ခေါ်ပါ" -> "0978953210"
     * - "၀၉-၄၂၀၀၁၂၃၄၅" -> "09420012345"
     * - "+959 789 532 100" -> "09789532100"
     */
    fun extractPhoneNumber(text: String): String? {
        if (text.isBlank()) return null

        // 1. First convert all spoken words and Myanmar digits to Western digits
        val normalized = normalizeSpokenDigits(text)

        // 2. Pattern for Myanmar phone numbers:
        // Matches +959 or 959 or 09 followed by digits and hyphens/spaces
        val phonePattern = Regex("(?:\\+?959|\\+?95\\s*9|09)[0-9\\s\\-]{7,14}")
        val match = phonePattern.find(normalized) ?: return null

        var cleanNumber = match.value.replace(Regex("[\\s\\-]"), "")

        // Normalize international +95 / 95 to local standard 09
        cleanNumber = when {
            cleanNumber.startsWith("+95") -> "0" + cleanNumber.removePrefix("+95")
            cleanNumber.startsWith("959") -> "0" + cleanNumber.removePrefix("95")
            else -> cleanNumber
        }

        // Valid Myanmar mobile numbers are 9 to 11 digits starting with 09
        return if (cleanNumber.matches(Regex("^09\\d{7,9}$"))) {
            cleanNumber
        } else {
            null
        }
    }

    /**
     * Normalizes all spoken numbers and phone numbers in a sentence before intent processing.
     * e.g., "အသံ ငါးဆယ် ရာခိုင်နှုန်း တင်ပါ" -> "အသံ 50% တင်ပါ"
     *       "၀၉၇၈၉၅၃၂၁၀ ကို ဖုန်းခေါ်ပေးပါ" -> "0978953210 ကို ဖုန်းခေါ်ပေးပါ"
     */
    fun normalizeSpokenUtterance(rawUtterance: String): String {
        if (rawUtterance.isBlank()) return rawUtterance

        // 1. Normalize spoken digits & Myanmar digits
        var result = normalizeSpokenDigits(rawUtterance)

        // 2. Normalize percentage phrases (e.g. "ငါးဆယ် ရာခိုင်နှုန်း" -> "50%")
        val percentRegex = Regex("(တစ်ရာ|ကိုးဆယ်|ရှစ်ဆယ်|ခုနစ်ဆယ်|ခုနှစ်ဆယ်|ခြောက်ဆယ်|ငါးဆယ်|လေးဆယ်|သုံးဆယ်|နှစ်ဆယ်|တစ်ဆယ်|ငါး|သုည)\\s*(?:ရာခိုင်နှုန်း|ရာခိုင်နူန်း|%)")
        result = percentRegex.replace(result) { match ->
            val numWord = match.groupValues[1]
            val value = parseSpokenCompoundNumber(numWord) ?: numWord
            "$value%"
        }

        return result
    }
}
