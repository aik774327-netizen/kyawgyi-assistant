package com.example.ocr

/**
 * MYANMAR SCRIPT DETECTOR
 *
 * အသိအမှတ်ပြုထားသော Script အမျိုးအစားများ:
 * - MYANMAR_UNICODE (မြန်မာ ယူနီကုဒ် စာသား)
 * - LATIN (အင်္ဂလိပ် / ကိန်းဂဏန်း စာသား)
 * - MIXED (မြန်မာနှင့် အင်္ဂလိပ် ရောနှောစာသား)
 * - UNKNOWN / EMPTY (စာသားမတွေ့ရှိခြင်း)
 */
object MyanmarScriptDetector {

    enum class ScriptType {
        MYANMAR_UNICODE,
        LATIN,
        MIXED,
        UNKNOWN
    }

    /**
     * Checks if a single character is within the Myanmar Unicode block.
     * Myanmar Block: U+1000..U+109F
     * Myanmar Extended-A: U+AA60..U+AA7F
     * Myanmar Extended-B: U+A9E0..U+A9FF
     */
    fun isMyanmarChar(c: Char): Boolean {
        val code = c.code
        return (code in 0x1000..0x109F) ||
                (code in 0xAA60..0xAA7F) ||
                (code in 0xA9E0..0xA9FF)
    }

    /**
     * Checks if the given string contains at least one Myanmar Unicode character.
     */
    fun containsMyanmar(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.any { isMyanmarChar(it) }
    }

    /**
     * Checks if a single character is within the Latin alphabet.
     */
    fun isLatinChar(c: Char): Boolean {
        val code = c.code
        return (code in 0x0041..0x005A) || (code in 0x0061..0x007A)
    }

    /**
     * Analyzes a given string and determines its primary script distribution.
     */
    fun detectScript(text: String?): ScriptType {
        if (text.isNullOrBlank()) return ScriptType.UNKNOWN

        var myanmarCount = 0
        var latinCount = 0
        var digitCount = 0
        var otherCount = 0

        for (c in text) {
            when {
                isMyanmarChar(c) -> myanmarCount++
                isLatinChar(c) -> latinCount++
                c.isDigit() -> digitCount++
                !c.isWhitespace() -> otherCount++
            }
        }

        val totalChars = myanmarCount + latinCount + digitCount + otherCount
        if (totalChars == 0) return ScriptType.UNKNOWN

        val myanmarRatio = myanmarCount.toFloat() / totalChars
        val latinRatio = (latinCount + digitCount).toFloat() / totalChars

        return when {
            myanmarRatio > 0.6f -> ScriptType.MYANMAR_UNICODE
            myanmarRatio > 0.15f && latinRatio > 0.15f -> ScriptType.MIXED
            latinRatio > 0.6f -> ScriptType.LATIN
            myanmarCount > 0 -> ScriptType.MIXED
            else -> ScriptType.LATIN
        }
    }

    /**
     * Detects if the extracted string looks like ML Kit Latin "hallucination / garbage approximation"
     * when it tries to read Myanmar rounded glyphs using a Latin-only model.
     * (e.g., lines containing primarily lone 'o', 'c', 'e', 'oo', 'oco', 'oe', 'co').
     */
    fun isProbableLatinOcrGarbage(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val clean = text.trim().lowercase()

        // Short strings made only of circular Latin glyphs typical of failed Burmese OCR
        val suspiciousTokens = setOf("o", "c", "e", "oo", "co", "oc", "oco", "ceo", "oe", "ooo", "oooo", "coo")
        val tokens = clean.split(Regex("[\\s,.:;\\-_/]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false

        val suspiciousCount = tokens.count { it in suspiciousTokens }
        val suspiciousRatio = suspiciousCount.toFloat() / tokens.size

        return tokens.size >= 2 && suspiciousRatio >= 0.7f
    }
}
