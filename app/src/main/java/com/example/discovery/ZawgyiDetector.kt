package com.example.discovery

import java.util.regex.Pattern

/**
 * Universal Zawgyi & Unicode Auto-Detector and Converter for Myanmar Text.
 * Specifically engineered for System Contact Resolvers, SMS, and Voice Intent Routers
 * to eliminate phonetic mismatches when contacts/messages are stored in legacy Zawgyi font.
 */
object ZawgyiDetector {

    enum class MyanmarEncoding {
        UNICODE,
        ZAWGYI,
        NON_MYANMAR
    }

    // 1. Explicit Zawgyi-only visual code points that never exist in valid Unicode Myanmar text
    // \u1064 (Kinzi), \u106a, \u106b, \u1071-\u1074 (medials), \u107e-\u1084 (Ya-yit variants),
    // \u1085 (auk-ka-myit), \u1086 (wa-swe), \u1088 (Zawgyi Na), \u108a-\u108e, \u108f, \u1090-\u1092, \u1097,
    // \u1033 (tall aa), \u1034 (double i)
    private val ZAWGYI_ONLY_CHARS = Regex("[\\u1064\\u106A\\u106B\\u1071-\\u1074\\u107E-\\u1086\\u1088\\u108A-\\u108E\\u108F\\u1090-\\u1092\\u1097\\u1033\\u1034]")

    // 2. Pre-posed e (\u1031) preceding a consonant:
    // In Unicode, \u1031 strictly follows the base consonant (e.g. မေ).
    // In Zawgyi, \u1031 strictly precedes the consonant (e.g. ေမ).
    private val PREPOSED_E_PATTERN = Regex("(?:^|[\\s\\p{Punct}\\u104a\\u104b\\u102c\\u102b\\u103a\\u1038\\u1037])\\u1031+[\\u1000-\\u1021]")

    // 3. Zawgyi Asat using virama \u1039 at syllable/word boundaries or followed by tone marks:
    // In Unicode, \u1039 is virama and MUST be followed by a subjoined consonant [\u1000-\u1021].
    // In Zawgyi, \u1039 is used as visible Asat (e.g. ေမာင္, သန္း, ခင္).
    private val ZAWGYI_ASAT_PATTERN = Regex("[\\u1000-\\u1021]\\u1039(?:[\\s\\p{Punct}\\u104a\\u104b\\u1038\\u1037]|$)")

    // 4. Zawgyi Oo (ဦး \u1025\u1038 or \u1025\u102e\u1038) vs Unicode (ဦး \u1026)
    private val ZAWGYI_OO_PATTERN = Regex("\\u1025[\\u102e\\u102d]?\\u1038")

    // 5. Pre-posed medials (e.g. ျ or ြ before consonant)
    private val PREPOSED_MEDIAL_PATTERN = Regex("[\\u103b\\u103c\\u107e-\\u1084][\\u1000-\\u1021]")

    // 6. Pre-posed e with medials (e.g. ေကျာ, ေမာ)
    private val PREPOSED_E_MEDIAL_PATTERN = Regex("\\u1031[\\u1000-\\u1021][\\u103b\\u103c\\u103d\\u103e]*[\\u102c\\u102b]")

    /**
     * Detects if the input text contains Myanmar characters.
     */
    fun containsMyanmar(input: CharSequence?): Boolean {
        if (input.isNullOrBlank()) return false
        for (i in 0 until input.length) {
            val ch = input[i]
            if (ch in '\u1000'..'\u109F' || ch in '\uAA60'..'\uAA7F') {
                return true
            }
        }
        return false
    }

    /**
     * Determines whether the given Myanmar text is in Zawgyi font encoding.
     * Returns false if text contains no Myanmar characters or is standard Unicode.
     */
    fun isZawgyi(input: CharSequence?): Boolean {
        if (input.isNullOrBlank()) return false
        val text = input.toString()
        if (!containsMyanmar(text)) return false

        // Fast path 1: Zawgyi-only visual characters
        if (ZAWGYI_ONLY_CHARS.containsMatchIn(text)) return true

        // Fast path 2: Zawgyi Oo (ဦး)
        if (ZAWGYI_OO_PATTERN.containsMatchIn(text)) return true

        // Fast path 3: Preposed e (ေမ, ေမာင္, ေကျာ်, ေအး, ေဖ, ေလး)
        if (PREPOSED_E_PATTERN.containsMatchIn(text) || PREPOSED_E_MEDIAL_PATTERN.containsMatchIn(text)) return true

        // Fast path 4: Zawgyi virama used as asat (င္, န္, တ္, စ္, ပ္, သန္း)
        if (ZAWGYI_ASAT_PATTERN.containsMatchIn(text)) return true

        // Fast path 5: Preposed ya-pin or ya-yit
        if (PREPOSED_MEDIAL_PATTERN.containsMatchIn(text)) return true

        // Heuristic scoring for ambiguous edge cases
        var zgScore = 0
        var uniScore = 0

        // Check for Unicode standard sequences
        // Consonant + e (Unicode standard: e.g. ကေ, မေ)
        if (Regex("[\\u1000-\\u1021][\\u103b\\u103c\\u103d\\u103e]*\\u1031").containsMatchIn(text)) {
            uniScore += 2
        }
        // Unicode independent vowels (ဦး, ဣ, ဤ, ဧ, ဩ, ဪ)
        if (Regex("[\\u1023-\\u1024\\u1026-\\u102a]").containsMatchIn(text)) {
            uniScore += 3
        }
        // Unicode standard Asat (\u103a)
        if (text.contains('\u103A')) {
            uniScore += 1
        }
        // Unicode subjoined consonants (\u1039[\u1000-\u1021])
        if (Regex("\\u1039[\\u1000-\\u1021]").containsMatchIn(text)) {
            uniScore += 1
        }

        return zgScore > uniScore
    }

    /**
     * Detects encoding category.
     */
    fun detectEncoding(input: CharSequence?): MyanmarEncoding {
        if (input.isNullOrBlank() || !containsMyanmar(input)) return MyanmarEncoding.NON_MYANMAR
        return if (isZawgyi(input)) MyanmarEncoding.ZAWGYI else MyanmarEncoding.UNICODE
    }

    /**
     * Converts a Zawgyi encoded string into standard Myanmar Unicode.
     * If the input is already Unicode or non-Myanmar, it returns cleanly without corrupting the text.
     */
    fun zawgyiToUnicode(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text = input

        // 1. Standardize Zawgyi Independent Vowels & Common Prefixes
        text = text.replace(ZAWGYI_OO_PATTERN, "\u1026") // ဦး / ဦး -> ဦး
        text = text.replace("ေဒါ်", "ဒေါ်")
        text = text.replace("ေသာ", "သော")
        text = text.replace("ေတာ့", "တော့")
        text = text.replace("ေတာင်", "တောင်")
        text = text.replace("ေအာင်", "အောင်")
        text = text.replace("ႏ", "\u1014") // Zawgyi Na -> Unicode Na
        text = text.replace("႔", "\u1037") // Zawgyi Auk-ka-myit -> Unicode Auk-ka-myit
        text = text.replace("႕", "\u1037")
        text = text.replace("႐", "\u101b") // Zawgyi Ra
        text = text.replace("႑", "\u100f") // Zawgyi Na-gyi
        text = text.replace("႒", "\u100b") // Zawgyi Hta
        text = text.replace("႓", "\u1003") // Zawgyi Gha
        text = text.replace("ၹ", "\u1007") // Zawgyi Za
        text = text.replace("႖", "\u1010")
        text = text.replace("ႍ", "\u1021\u102d\u102f") // အို

        // 2. Zawgyi Ra-medials (ရရစ်) & Medial Glyph Standardization
        text = text.replace(Regex("[\\u107e-\\u1083]"), "\u103c")
        text = text.replace("\u1086", "\u103d") // Zawgyi wa-swe
        text = text.replace("\u1033", "\u102b") // Zawgyi tall aa
        text = text.replace("\u1034", "\u102e") // Zawgyi double i

        // Reorder preposed ya-yit (ြ + Consonant -> Consonant + ြ)
        text = text.replace(Regex("\\u103c([\\u1000-\\u1021])"), "$1\u103c")
        // Reorder preposed ya-pin (ျ + Consonant -> Consonant + ျ)
        text = text.replace(Regex("\\u103b([\\u1000-\\u1021])"), "$1\u103b")

        // 3. Pre-posed 'ေ' (\u1031) Reordering:
        // In Zawgyi: \u1031 + Consonant + (Medials)
        // In Unicode: Consonant + (Medials) + \u1031
        text = text.replace(Regex("\\u1031+([\\u1000-\\u1021])([\\u103b\\u103c\\u103d\\u103e]*)"), "$1$2\u1031")

        // 4. Zawgyi Asat Conversion:
        // In Zawgyi, \u1039 is frequently typed for Asat (\u103a).
        // Convert \u1039 followed by tone/space/punct/end of string into \u103a
        text = text.replace(Regex("\\u1039(?=[\\u1038\\u1037\\s\\p{Punct}\\u104a\\u104b]|$)"), "\u103a")
        // Common terminal consonants with \u1039 -> \u103a
        text = text.replace(Regex("([\\u1000-\\u1021])\\u1039(?![\\u1000-\\u1021])"), "$1\u103a")

        // 5. Common Syllable Tone / Vowel Canonical Order in Unicode:
        // Unicode standard: Base -> Medials -> e -> Upper/Lower vowels -> Vowel aa -> Anusvara -> Asat -> Auk-ka-myit -> Visarga
        text = text.replace("\u102f\u102d", "\u102d\u102f") // ို
        text = text.replace("\u1037\u1036", "\u1036\u1037") // ုံ့
        text = text.replace("\u1038\u1037", "\u1037\u1038") // ့း
        text = text.replace("\u103a\u1037", "\u1037\u103a") // ့် -> ့်

        return text
    }

    /**
     * Safely ensures the text is in Unicode. If Zawgyi is detected, converts to Unicode;
     * otherwise returns original text unchanged.
     */
    fun ensureUnicode(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return if (isZawgyi(input)) {
            zawgyiToUnicode(input)
        } else {
            input
        }
    }
}
