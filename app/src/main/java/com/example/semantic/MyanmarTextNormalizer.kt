package com.example.semantic

import com.example.semantic.dictionary.MyanmarSynonymDictionary
import java.text.Normalizer
import java.util.Locale

/**
 * Myanmar Text Normalizer for MSIL
 * Performs canonical Unicode normalization, Zawgyi detection/conversion,
 * digit unification, polite particle extraction, filler word stripping, and wake-word removal.
 */
class MyanmarTextNormalizer {

    data class NormalizedOutput(
        val originalText: String,
        val cleanedText: String,
        val strippedText: String,
        val detectedWakeWord: String? = null,
        val politeParticles: List<String> = emptyList(),
        val isPolite: Boolean = false
    )

    companion object {
        private val WAKE_WORDS = listOf(
            "ဟေး ကျော်ကြီး", "ကျော်ကြီးရေ", "ကျော်ကြီး", "ဟေးကျော်ကြီး", "hey kyaw gyi", "kyaw gyi"
        )

        private val BURMESE_DIGIT_MAP = mapOf(
            '၀' to '0', '၁' to '1', '၂' to '2', '၃' to '3', '၄' to '4',
            '၅' to '5', '၆' to '6', '၇' to '7', '၈' to '8', '၉' to '9'
        )

        private val SPOKEN_BURMESE_NUMBERS = mapOf(
            "သုည" to "0", "တစ်" to "1", "တစ္" to "1", "နှစ်" to "2", "နွစ္" to "2",
            "သုံး" to "3", "သံုး" to "3", "လေး" to "4", "ငါး" to "5",
            "ခြောက်" to "6", "ခုနစ်" to "7", "ခုနှစ်" to "7", "ရှစ်" to "8", "ကိုး" to "9",
            "ဆယ်" to "10"
        )
    }

    /**
     * Complete normalization pipeline
     */
    fun normalize(rawText: String): NormalizedOutput {
        if (rawText.isBlank()) {
            return NormalizedOutput(rawText, "", "")
        }

        // 1. Unicode NFC & Zero-width characters stripping
        var text = Normalizer.normalize(rawText, Normalizer.Form.NFC)
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .trim()

        // 2. Convert Zawgyi characters if present
        text = convertZawgyiToUnicodeIfNeeded(text)

        // 3. Burmese digits to ASCII
        val digitSb = StringBuilder()
        for (ch in text) {
            digitSb.append(BURMESE_DIGIT_MAP[ch] ?: ch)
        }
        text = digitSb.toString()

        // 4. Spoken numbers conversion (e.g. မနက် ခုနစ် နာရီ -> မနက် 7 နာရီ)
        for ((word, digit) in SPOKEN_BURMESE_NUMBERS) {
            text = text.replace(" $word ", " $digit ")
            if (text.startsWith("$word ")) {
                text = "$digit " + text.substring(word.length + 1)
            }
            if (text.endsWith(" $word")) {
                text = text.substring(0, text.length - word.length - 1) + " $digit"
            }
        }

        // 5. Wake word detection & stripping
        var detectedWakeWord: String? = null
        val lowerText = text.lowercase(Locale.ROOT)
        for (ww in WAKE_WORDS) {
            if (lowerText.startsWith(ww.lowercase(Locale.ROOT))) {
                detectedWakeWord = ww
                text = text.substring(ww.length).trim()
                break
            }
        }

        val cleanedText = text.trim()

        // 6. Polite particles detection & stripping
        val foundPolite = mutableListOf<String>()
        var stripped = cleanedText

        for (particle in MyanmarSynonymDictionary.POLITE_PARTICLES) {
            if (stripped.endsWith(" $particle") || stripped.endsWith(particle)) {
                foundPolite.add(particle)
                stripped = if (stripped.endsWith(" $particle")) {
                    stripped.substring(0, stripped.length - particle.length - 1).trim()
                } else {
                    stripped.substring(0, stripped.length - particle.length).trim()
                }
            } else if (stripped.contains(" $particle ")) {
                foundPolite.add(particle)
                stripped = stripped.replace(" $particle ", " ").trim()
            }
        }

        // 7. Conversational fillers stripping
        for (filler in MyanmarSynonymDictionary.CONVERSATIONAL_FILLERS) {
            if (stripped.startsWith("$filler ")) {
                stripped = stripped.substring(filler.length).trim()
            }
            if (stripped.endsWith(" $filler")) {
                stripped = stripped.substring(0, stripped.length - filler.length - 1).trim()
            }
            stripped = stripped.replace(" $filler ", " ").trim()
        }

        return NormalizedOutput(
            originalText = rawText,
            cleanedText = cleanedText,
            strippedText = stripped.ifBlank { cleanedText },
            detectedWakeWord = detectedWakeWord,
            politeParticles = foundPolite,
            isPolite = foundPolite.isNotEmpty()
        )
    }

    private fun convertZawgyiToUnicodeIfNeeded(input: String): String {
        // Fast heuristic for actual Zawgyi-specific visual codepoints
        val hasZawgyiSpecificChars = input.any { ch -> ch in '\u1060'..'\u1097' && ch !in '\u1060'..'\u1064' }
        if (!hasZawgyiSpecificChars) return input

        return input
            .replace("ျမ", "မြ")
            .replace("ႏ", "န")
            .replace("႔", "့")
            .replace("သို႔", "သို့")
            .replace("ေသာ", "သော")
            .replace("ေတာ့", "တော့")
    }
}
