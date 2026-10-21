package com.example.nlu

import java.util.Locale

/**
 * BurmeseTtsTextNormalizer (Layer 6 Myanmar Linguistics & Layer 2 TTS Pre-Synthesis G2P)
 *
 * Transforms raw response strings containing numbers, symbols, percentages, time expressions,
 * currencies, phone numbers, and English technical acronyms into natural spoken Burmese words
 * before passing to F5MyanmarTtsClient / MyanmarTTSEngine:
 * 1. Percentages: "85%" / "၈၅%" -> "ရှစ်ဆယ့်ငါး ရာခိုင်နှုန်း"
 * 2. Time Expressions: "12:30 PM" / "နေ့လည် ၁၂:၃၀" -> "မွန်းလွဲ တစ်ဆယ့်နှစ် နာရီ သုံးဆယ် မိနစ်"
 * 3. Currency Amounts: "5,000 MMK" / "5000 Ks" -> "ငါးထောင် ကျပ်"
 * 4. Phone Numbers: "0978953210" -> "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည"
 * 5. Standalone Numbers: "150" -> "တစ်ရာ့ငါးဆယ်"
 * 6. Technical Acronyms (G2P): "Wi-Fi" -> "ဝိုင်ဖိုင်", "Bluetooth" -> "ဘလူးတုသ်", "SMS" -> "အက်စ်အမ်အက်စ်"
 */
object BurmeseTtsTextNormalizer {

    private val DIGIT_WORDS = arrayOf(
        "သုည",
        "တစ်",
        "နှစ်",
        "သုံး",
        "လေး",
        "ငါး",
        "ခြောက်",
        "ခုနစ်",
        "ရှစ်",
        "ကိုး"
    )

    private val ACRONYM_G2P_MAP = listOf(
        Regex("(?i)\\bwi-?fi\\b") to "ဝိုင်ဖိုင်",
        Regex("(?i)\\bbluetooth\\b") to "ဘလူးတုသ်",
        Regex("(?i)\\byoutube\\b") to "ယူကျု",
        Regex("(?i)\\bsms\\b") to "အက်စ်အမ်အက်စ်",
        Regex("(?i)\\bgps\\b") to "ဂျီပီအက်စ်",
        Regex("(?i)\\busb\\b") to "ယူအက်စ်ဘီ",
        Regex("(?i)\\bai\\b") to "အေအိုင်",
        Regex("(?i)\\bhotspot\\b") to "ဟော့စပေါ့",
        Regex("(?i)\\bdata\\b") to "ဒေတာ",
        Regex("(?i)\\bkbz\\s*pay\\b") to "ကေဘီဇက်ပေး",
        Regex("(?i)\\bkpay\\b") to "ကေပေး",
        Regex("(?i)\\bwave\\s*pay\\b") to "ဝေ့ဖ်ပေး",
        Regex("(?i)\\baya\\s*pay\\b") to "အေရာပေး",
        Regex("(?i)\\bcb\\s*pay\\b") to "စီဘီပေး",
        Regex("(?i)\\bmpt\\b") to "အမ်ပီတီ",
        Regex("(?i)\\batom\\b") to "အက်တမ်",
        Regex("(?i)\\booredoo\\b") to "အူရီဒူး",
        Regex("(?i)\\bmytel\\b") to "မိုင်တဲလ်",
        Regex("(?i)\\bgmail\\b") to "ဂျီမေးလ်",
        Regex("(?i)\\bchrome\\b") to "ခရုမ်း",
        Regex("(?i)\\bspotify\\b") to "စပေါ်တီဖိုင်",
        Regex("(?i)\\bgrab\\b") to "ဂရက်ဘ်",
        Regex("(?i)\\botp\\b") to "အိုတီပီ",
        Regex("(?i)\\bpin\\b") to "ပင်နံပါတ်",
        Regex("(?i)\\bsim\\b") to "ဆင်းမ်ကတ်",
        Regex("(?i)\\bcpu\\b") to "စီပီယူ",
        Regex("(?i)\\bram\\b") to "ရမ်",
        Regex("(?i)\\bapk\\b") to "အေပီကေ"
    )

    private val BURMESE_MONTH_NAMES = arrayOf(
        "",
        "ဇန်နဝါရီလ",
        "ဖေဖော်ဝါရီလ",
        "မတ်လ",
        "ဧပြီလ",
        "မေလ",
        "ဇွန်လ",
        "ဇူလိုင်လ",
        "ဩဂုတ်လ",
        "စက်တင်ဘာလ",
        "အောက်တိုဘာလ",
        "နိုဝင်ဘာလ",
        "ဒီဇင်ဘာလ"
    )

    /**
     * Pali-derived & Voicing Sandhi (အသံပြောင်း) Exceptional Pronunciation Lexicon
     * for natural Myanmar TTS prosody when required by phonetic/naturalizer synthesis.
     */
    val IRREGULAR_PRONUNCIATION_LEXICON: Map<String, String> = mapOf(
        "မင်္ဂလာပါ" to "မင်ဂလာပါ",
        "မင်္ဂလာ" to "မင်ဂလာ",
        "ပြဿနာ" to "ပြသနာ",
        "ပညာ" to "ပျဉ်ညာ",
        "သမ္မတ" to "သမဒ",
        "ဥက္ကဋ္ဌ" to "အုတ်ကဌ",
        "လက်ဖက်ရည်" to "လဖက်ရည်",
        "လက်ဖက်" to "လဖက်",
        "ပုဂံ" to "ဗဂံ",
        "စကား" to "ဇဂါး",
        "စပါး" to "ဇပါး",
        "တံခါး" to "ဒဂါး",
        "ကုဋေ" to "ကဒေ",
        "ဘဏ်" to "ဘန်",
        "ဒဏ်" to "ဒန်"
    )

    /**
     * Converts a non-negative integer into natural spoken Burmese words with proper
     * creaky tone sandhi (အောက်မြစ် '့' in ဆယ့်, ရာ့, ထောင့်).
     */
    fun numberToBurmeseWords(number: Long): String {
        if (number < 0) return "အနုတ် " + numberToBurmeseWords(-number)
        if (number in 0..9) return DIGIT_WORDS[number.toInt()]

        if (number in 10..99) {
            val tens = (number / 10).toInt()
            val rem = (number % 10).toInt()
            return if (rem == 0) {
                "${DIGIT_WORDS[tens]}ဆယ်"
            } else {
                "${DIGIT_WORDS[tens]}ဆယ့်${DIGIT_WORDS[rem]}"
            }
        }

        if (number in 100..999) {
            val hundreds = (number / 100).toInt()
            val rem = number % 100
            return if (rem == 0L) {
                "${DIGIT_WORDS[hundreds]}ရာ"
            } else {
                "${DIGIT_WORDS[hundreds]}ရာ့${numberToBurmeseWords(rem)}"
            }
        }

        if (number in 1000..9999) {
            val thousands = (number / 1000).toInt()
            val rem = number % 1000
            return if (rem == 0L) {
                "${DIGIT_WORDS[thousands]}ထောင်"
            } else {
                "${DIGIT_WORDS[thousands]}ထောင့်${numberToBurmeseWords(rem)}"
            }
        }

        if (number in 10000..99999) {
            val tenThousands = (number / 10000).toInt()
            val rem = number % 10000
            return if (rem == 0L) {
                "${DIGIT_WORDS[tenThousands]}သောင်း"
            } else {
                "${DIGIT_WORDS[tenThousands]}သောင်း${numberToBurmeseWords(rem)}"
            }
        }

        if (number in 100000..999999) {
            val lakhs = (number / 100000).toInt()
            val rem = number % 100000
            return if (rem == 0L) {
                "${DIGIT_WORDS[lakhs]}သိန်း"
            } else {
                "${DIGIT_WORDS[lakhs]}သိန်း${numberToBurmeseWords(rem)}"
            }
        }

        if (number in 1_000_000..9_999_999) {
            val millions = (number / 1_000_000).toInt()
            val rem = number % 1_000_000
            return if (rem == 0L) {
                "${DIGIT_WORDS[millions]}သန်း"
            } else {
                "${DIGIT_WORDS[millions]}သန်း${numberToBurmeseWords(rem)}"
            }
        }

        if (number in 10_000_000..999_999_999) {
            val crores = number / 10_000_000
            val rem = number % 10_000_000
            val crorePrefix = numberToBurmeseWords(crores)
            return if (rem == 0L) {
                "${crorePrefix}ကုဋေ"
            } else {
                "${crorePrefix}ကုဋေ${numberToBurmeseWords(rem)}"
            }
        }

        // Fallback for very large numbers: read digit by digit
        return expandDigitsIndividually(number.toString())
    }

    /**
     * Expands a digit string digit-by-digit into spoken words (ideal for phone numbers & codes).
     * e.g. "0978" -> "သုည ကိုး ခုနစ် ရှစ်"
     */
    fun expandDigitsIndividually(digitsStr: String): String {
        val western = BurmeseNumberNormalizer.convertMyanmarDigitsToWestern(digitsStr)
        return western.mapNotNull { ch ->
            if (ch in '0'..'9') DIGIT_WORDS[ch - '0'] else null
        }.joinToString(" ")
    }

    /**
     * Normalizes full text for natural Myanmar TTS synthesis.
     */
    fun normalizeForTts(rawText: String): String {
        if (rawText.isBlank()) return rawText

        // 1. Convert any Myanmar script digits ('၀'..'၉') to Western ('0'..'9') for unified regex processing
        var text = BurmeseNumberNormalizer.convertMyanmarDigitsToWestern(rawText)

        // 2. Expand Phone Numbers (09xxxxxxxxx) digit-by-digit so TTS does not read them as hundreds of millions
        val phoneRegex = Regex("(?<!\\d)(09\\d{7,9})(?!\\d)")
        text = phoneRegex.replace(text) { match ->
            expandDigitsIndividually(match.groupValues[1])
        }

        // 3. Expand Time Expressions (e.g., "12:30 PM", "နေ့လည် 12:30", "09:15 AM", "14:30")
        val timeRegex = Regex("(?i)(?:(နံနက်|မနက်|နေ့လည်|မွန်းလွဲ|ညနေ|ည)\\s*)?(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?")
        text = timeRegex.replace(text) { match ->
            val myanmarPeriod = match.groupValues[1]
            val rawHour = match.groupValues[2].toIntOrNull() ?: 0
            val rawMinute = match.groupValues[3].toIntOrNull() ?: 0
            val amPm = match.groupValues[4].uppercase(Locale.ROOT)

            val periodPrefix = when {
                myanmarPeriod == "နေ့လည်" || myanmarPeriod == "မွန်းလွဲ" -> "မွန်းလွဲ "
                myanmarPeriod.isNotEmpty() -> "$myanmarPeriod "
                amPm == "PM" -> "မွန်းလွဲ "
                amPm == "AM" -> "နံနက် "
                rawHour in 12..15 -> "မွန်းလွဲ "
                rawHour in 16..18 -> "ညနေ "
                rawHour >= 19 -> "ည "
                else -> ""
            }

            val displayHour = when {
                rawHour > 12 -> rawHour - 12
                rawHour == 0 -> 12
                else -> rawHour
            }

            val hourWords = "${numberToBurmeseWords(displayHour.toLong())} နာရီ"
            val minuteWords = if (rawMinute > 0) {
                " ${numberToBurmeseWords(rawMinute.toLong())} မိနစ်"
            } else {
                ""
            }

            "$periodPrefix$hourWords$minuteWords".trim()
        }

        // 4. Expand Percentages (e.g., "85%" -> "ရှစ်ဆယ့်ငါး ရာခိုင်နှုန်း")
        val percentRegex = Regex("(\\d+)\\s*%")
        text = percentRegex.replace(text) { match ->
            val num = match.groupValues[1].toLongOrNull()
            if (num != null) {
                "${numberToBurmeseWords(num)} ရာခိုင်နှုန်း"
            } else {
                match.value
            }
        }

        // 5. Expand Currency Amounts (e.g., "5,000 MMK", "15000 Ks", "5000 ကျပ်", "100 USD", "500 THB")
        val currencyRegex = Regex("(?i)(\\d{1,3}(?:,\\d{3})+|[0-9]+)\\s*(MMK|Ks|ကျပ်|USD|\\$|THB|ဘတ်|SGD)")
        text = currencyRegex.replace(text) { match ->
            val cleanDigits = match.groupValues[1].replace(",", "")
            val unitRaw = match.groupValues[2].uppercase(Locale.ROOT)
            val amount = cleanDigits.toLongOrNull()
            val unitSpoken = when (unitRaw) {
                "USD", "$" -> "ဒေါ်လာ"
                "THB", "ဘတ်" -> "ဘတ်"
                "SGD" -> "စင်ကာပူ ဒေါ်လာ"
                else -> "ကျပ်"
            }
            if (amount != null) {
                "${numberToBurmeseWords(amount)} $unitSpoken"
            } else {
                match.value
            }
        }

        // 5b. Expand ISO Dates (YYYY-MM-DD) into spoken Burmese dates
        val isoDateRegex = Regex("\\b(20\\d{2})-(0?[1-9]|1[0-2])-(0?[1-9]|[12]\\d|3[01])\\b")
        text = isoDateRegex.replace(text) { match ->
            val year = match.groupValues[1].toLongOrNull() ?: 2026L
            val month = match.groupValues[2].toIntOrNull() ?: 1
            val day = match.groupValues[3].toLongOrNull() ?: 1L
            val monthName = BURMESE_MONTH_NAMES.getOrElse(month) { "" }
            "${numberToBurmeseWords(year)} ခုနှစ် $monthName ${numberToBurmeseWords(day)} ရက်"
        }

        // 5c. Expand Measurement Units (°C, km, GB, MB, mAh, kHz, Hz)
        val unitRegex = Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*(°C|km|gb|mb|mah|khz|hz)\\b")
        text = unitRegex.replace(text) { match ->
            val numPart = match.groupValues[1]
            val unitPart = match.groupValues[2].lowercase(Locale.ROOT)
            val unitSpoken = when (unitPart) {
                "°c" -> "ဒီဂရီ စင်တီဂရိတ်"
                "km" -> "ကီလိုမီတာ"
                "gb" -> "ဂစ်ဂါဘိုက်"
                "mb" -> "မက်ဂါဘိုက်"
                "mah" -> "မီလီအမ်ပီယာနာရီ"
                "khz" -> "ကီလိုဟာ့ဇ်"
                "hz" -> "ဟာ့ဇ်"
                else -> unitPart
            }
            "$numPart $unitSpoken"
        }

        // 5d. Expand Decimal Numbers (e.g., "3.14" -> "သုံး ဒသမ တစ် လေး")
        val decimalRegex = Regex("(?<!\\d)(\\d{1,6})\\.(\\d{1,4})(?!\\d)")
        text = decimalRegex.replace(text) { match ->
            val intPart = match.groupValues[1].toLongOrNull()
            val fracPart = match.groupValues[2]
            if (intPart != null) {
                "${numberToBurmeseWords(intPart)} ဒသမ ${expandDigitsIndividually(fracPart)}"
            } else {
                match.value
            }
        }

        // 6. Expand remaining standalone numbers (e.g. "150" -> "တစ်ရာ့ငါးဆယ်")
        val standaloneNumberRegex = Regex("(?<!\\d)(\\d{1,9})(?!\\d)")
        text = standaloneNumberRegex.replace(text) { match ->
            val num = match.groupValues[1].toLongOrNull()
            if (num != null) {
                numberToBurmeseWords(num)
            } else {
                match.value
            }
        }

        // 7. Apply English Technical Acronym G2P expansions
        for ((regex, spokenMyanmar) in ACRONYM_G2P_MAP) {
            text = regex.replace(text, spokenMyanmar)
        }

        return text.trim().replace(Regex("\\s+"), " ")
    }
}
