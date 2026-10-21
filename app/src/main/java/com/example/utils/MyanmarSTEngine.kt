package com.example.utils

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Myanmar Speech-To-Text & NLP Normalization Engine
 * Features: Phonetic Normalization, Spoken Digits Conversion, Levenshtein Distance, Fuzzy Intent Matcher (>80% accuracy)
 */
class MyanmarSTEngine {

    data class TranscriptResult(
        val raw: String,
        val clean: String,
        val isFinal: Boolean,
        val confidence: Float = 1.0f
    )

    data class RegisteredCommand(
        val id: String,
        val phrase: String,
        val payload: Any? = null
    )

    data class IntentMatchResult(
        val command: RegisteredCommand,
        val score: Int
    )

    /**
     * Phonetic & Unicode Normalization for Myanmar Script
     */
    fun normalizeMyanmarText(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        
        // Normalize Unicode to NFC
        var normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        
        // Strip Zero-Width Spaces (\u200B, \u200C, \u200D, \uFEFF)
        normalized = normalized.replace(Regex("[\\u200B\\u200C\\u200D\\uFEFF]"), "")
        
        // Deduplicate tone dots (့+) and visarga (း+)
        normalized = normalized.replace(Regex("့+"), "့")
        normalized = normalized.replace(Regex("း+"), "း")

        // Common STT Phonetic Variations & Misrecognitions mapping
        val soundAlikeMap = mapOf(
            "ကျောကြီး" to "ကျော်ကြီး",
            "ကျောဂီ" to "ကျော်ကြီး",
            "ကျော်ဂီ" to "ကျော်ကြီး",
            "ကြော်ကြီး" to "ကျော်ကြီး",
            "ဖေ့ဘုတ်" to "ဖေ့ဘွတ်ခ်",
            "ယူကျု" to "ယူကျူ့ဗ်",
            "မိုင်းခရက်" to "မိုက်ခရိုဖုန်း",
            "မိုက်" to "မိုက်ခရိုဖုန်း"
        )

        for ((misspoken, corrected) in soundAlikeMap) {
            normalized = normalized.replace(misspoken, corrected)
        }
        
        // Remove internal spaces
        normalized = normalized.replace(Regex("\\s+"), "")
        
        return normalized.trim()
    }

    /**
     * Spoken Myanmar Words to Digits Conversion Matrix
     */
    fun convertSpokenNumbersToDigits(text: String?): String {
        if (text.isNullOrEmpty()) return ""

        // Preserve protected words/names from single-digit replacement
        val protectedWords = listOf("လေးဖြူ", "မျိုးကြီး", "အလွမ်းလေး", "နည်းနည်းလေး", "လေးပါ", "ဖွင့်ပေးပါဦးလေး")
        val placeholderMap = mutableMapOf<String, String>()
        var protectedText: String = text
        protectedWords.forEachIndexed { index, word ->
            if (protectedText.contains(word)) {
                val token = "___PROT_${index}___"
                placeholderMap[token] = word
                protectedText = protectedText.replace(word, token)
            }
        }

        val wordMap = listOf(
            "သုည" to "၀",
            "တစ်ဆယ့်ငါး" to "၁၅",
            "တစ်ဆယ်" to "၁၀",
            "တစ်ရာ" to "၁၀၀",
            "တစ်ထောင်" to "၁၀၀၀",
            "ခုနစ်" to "၇",
            "ခြောက်" to "၆",
            "ရှစ်" to "၈",
            "ကိုး" to "၉",
            "ငါး" to "၅",
            "လေး" to "၄",
            "သုံး" to "၃",
            "နှစ်" to "၂",
            "တစ်" to "၁"
        )

        var result: String = protectedText
        for ((word, digit) in wordMap) {
            result = result.replace(word, digit)
        }

        // Restore protected words
        for ((token, original) in placeholderMap) {
            result = result.replace(token, original)
        }

        return result
    }

    /**
     * Process raw transcript with normalization and spoken digits conversion
     */
    fun processTranscript(rawTranscript: String, isFinal: Boolean, confidence: Float = 1.0f): TranscriptResult {
        val normalized = normalizeMyanmarText(rawTranscript)
        val textWithDigits = convertSpokenNumbersToDigits(normalized)
        return TranscriptResult(
            raw = rawTranscript,
            clean = textWithDigits,
            isFinal = isFinal,
            confidence = confidence
        )
    }

    /**
     * Levenshtein Distance Matrix Calculation
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val matrix = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) matrix[i][0] = i
        for (j in 0..b.length) matrix[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                matrix[i][j] = min(
                    min(
                        matrix[i - 1][j] + 1,
                        matrix[i][j - 1] + 1
                    ),
                    matrix[i - 1][j - 1] + cost
                )
            }
        }
        return matrix[a.length][b.length]
    }

    /**
     * Dynamic Confidence Threshold Phonetic Intent Matcher (>80% accuracy constraint)
     */
    fun matchIntent(
        inputSpeech: String,
        registeredCommands: List<RegisteredCommand>,
        thresholdPercent: Int = 80
    ): IntentMatchResult? {
        val cleanInput = normalizeMyanmarText(inputSpeech)
        if (cleanInput.isEmpty()) return null

        var bestMatch: IntentMatchResult? = null
        var highestScore = 0

        registeredCommands.forEach { cmd ->
            val cleanCmd = normalizeMyanmarText(cmd.phrase)
            if (cleanCmd.isNotEmpty()) {
                val maxLen = max(cleanInput.length, cleanCmd.length)
                if (maxLen > 0) {
                    val distance = levenshteinDistance(cleanInput, cleanCmd)
                    val similarityScore = ((maxLen - distance).toDouble() / maxLen.toDouble()) * 100.0

                    if (similarityScore > highestScore && similarityScore >= thresholdPercent) {
                        highestScore = similarityScore.toInt()
                        bestMatch = IntentMatchResult(
                            command = cmd,
                            score = highestScore
                        )
                    }
                }
            }
        }

        return bestMatch
    }
}
