package com.example.nlu

import android.util.Log
import java.util.regex.Pattern

/**
 * BurmeseNluPreprocessor (Layer 2 & 5 - Myanmar Linguistic Preprocessor)
 *
 * Implements high-precision Myanmar language preprocessing:
 * 1. Syllable Segmentation (သရ/ဗျည်း အက္ခရာ အခြေခံ စာလုံးခွဲခြမ်းစိတ်ဖြာမှု)
 * 2. Diacritic Order Normalization (Unicode NFC & canonical stacking order)
 * 3. Zero-Width Space, Tone & Dialect Normalization
 * 4. Offline Intent Slot Extraction (Contact, Application, Setting, Parameter)
 */
object BurmeseNluPreprocessor {

    private const val TAG = "BurmeseNluPreprocessor"

    // Zero-width characters & punctuation to clean
    private val INVISIBLE_CHARS_REGEX = Regex("[\u200B\u200C\u200D\uFEFF]")

    // Canonical Myanmar Syllable Breaker Regex (Consonant + Medials + Vowels + Virama + Asat + Tones)
    // Accurately matches complete syllables including e-sound + tall-aa + asat (e.g. ခေါ်, ဖုန်း, ပေး, ပါ)
    private val MYANMAR_SYLLABLE_PATTERN = Pattern.compile(
        "([က-အဣ-ဧဩ-ဪဿ](?:[\u103B-\u103E]*)(?:[\u102B-\u1032\u1036]*)(?:[\u1039][က-အ])?(?:[က-အ]?[\u103A])*(?:[\u1037\u1038]*)|[0-9၀-၉]+|[a-zA-Z]+|[^က-အ0-9၀-၉a-zA-Z\\s]+)"
    )

    // Diacritic Canonical Ordering Re-alignment Regex
    // Correct Order: Consonant -> Medial (ျ, ြ, ွ, ှ) -> Vowel (ေ, ိ, ီ, ု, ူ, ဲ) -> Anusvara (ံ) -> Asat (်) -> Tone (့, း)
    private val TONE_REORDER_REGEX = Regex("([\u1037\u1038])([\u1036\u103A])")

    /**
     * Canonical text normalization before NLU classification.
     */
    fun normalize(rawText: String?): String {
        if (rawText.isNullOrBlank()) return ""

        var text = rawText.replace(INVISIBLE_CHARS_REGEX, "")

        // Normalize Zawgyi-like misordered tones
        text = TONE_REORDER_REGEX.replace(text, "$2$1")

        // Standardize common speech dialect contractions
        text = text.replace("ဖွင့်", "ဖွင့်")
            .replace("ပိတ်ပါဦး", "ပိတ်ပါ")
            .replace("ပိတ်ပါဦး", "ပိတ်ပါ")
            .replace(Regex("ဖုန်းခေါ(?!်)"), "ဖုန်းခေါ်")
            .replace("ဆက်သွယ်", "ဆက်")
            .replace("သီချင်းဖွင့်", "သီချင်း ဖွင့်")

        // Spoken-to-Written Number & Phone Normalization
        text = BurmeseNumberNormalizer.normalizeSpokenUtterance(text)

        // Code-Switching & Phonetic Lexicon Normalization (Wi-Fi, Bluetooth, YouTube, Data, Volume, etc.)
        text = BurmesePhoneticLexicon.normalizeCodeSwitching(text)

        // Normalize whitespace
        text = text.trim().replace(Regex("\\s+"), " ")
        return text
    }

    /**
     * Segments continuous Myanmar text into phonological syllables.
     * e.g., "ဖုန်းခေါ်ပေးပါ" -> ["ဖုန်း", "ခေါ်", "ပေး", "ပါ"]
     */
    fun segmentSyllables(text: String?): List<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()

        val syllables = mutableListOf<String>()
        val matcher = MYANMAR_SYLLABLE_PATTERN.matcher(normalized)

        while (matcher.find()) {
            val syllable = matcher.group().trim()
            if (syllable.isNotEmpty()) {
                syllables.add(syllable)
            }
        }

        return syllables
    }

    /**
     * Joins syllables with explicit syllable delimiter (e.g., for phoneme synthesis or regex matching).
     */
    fun getSyllableDelimitedText(text: String?, delimiter: String = " "): String {
        return segmentSyllables(text).joinToString(delimiter)
    }

    /**
     * Slot Extraction Data Class
     */
    data class ExtractedSlots(
        val intentCandidate: String,
        val contactName: String? = null,
        val targetApp: String? = null,
        val settingKey: String? = null,
        val settingValue: String? = null,
        val searchQuery: String? = null,
        val originalText: String
    )

    /**
     * Extracts structured slots from Myanmar natural text for offline regex fallback.
     */
    fun extractSlots(rawInput: String?): ExtractedSlots {
        val normalized = normalize(rawInput)
        val lower = normalized.lowercase()

        // 1. Connectivity Settings (Wi-Fi, Bluetooth, Flashlight)
        if (lower.contains("wifi") || lower.contains("ဝိုင်ဖိုင်") || lower.contains("ဝိုင်ဖာ")) {
            val enable = !lower.contains("ပိတ်") && !lower.contains("off")
            return ExtractedSlots(
                intentCandidate = "ACTION_WIFI",
                settingKey = "WIFI",
                settingValue = if (enable) "ON" else "OFF",
                originalText = normalized
            )
        }

        if (lower.contains("bluetooth") || lower.contains("ဘလူးတု") || lower.contains("လူးတု")) {
            val enable = !lower.contains("ပိတ်") && !lower.contains("off")
            return ExtractedSlots(
                intentCandidate = "ACTION_BLUETOOTH",
                settingKey = "BLUETOOTH",
                settingValue = if (enable) "ON" else "OFF",
                originalText = normalized
            )
        }

        if (lower.contains("ဓာတ်မီး") || lower.contains("မီးသီး") || lower.contains("flashlight") || lower.contains("torch")) {
            val enable = !lower.contains("ပိတ်") && !lower.contains("off")
            return ExtractedSlots(
                intentCandidate = "ACTION_FLASHLIGHT",
                settingKey = "FLASHLIGHT",
                settingValue = if (enable) "ON" else "OFF",
                originalText = normalized
            )
        }

        // 2. Phone Call Slot Matching (handles e.g. "မောင်မောင် ဆီ ဖုန်းခေါ်ပေးပါ", "ဖုန်းခေါ်မယ် မောင်မောင်")
        if (lower.contains("ဖုန်းခေါ်") || lower.contains("ခေါ်ပေး") || lower.contains("ဆက်ပေး") || (lower.contains("ဖုန်း") && (lower.contains("ခေါ်") || lower.contains("ဆက်"))) || lower.contains("call")) {
            val contact = normalized
                .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
                .replace(Regex("(ဖုန်းခေါ်ပေးပါ|ဖုန်းခေါ်ပါ|ဖုန်းခေါ်မယ်|ဖုန်းခေါ်|ခေါ်ပေးပါ|ခေါ်ပါ|ခေါ်မယ်|ဆက်ပေးပါ|ဆက်ပါ|call)"), "")
                .replace(Regex("(ဆီသို့|ဆီ|ထံ|သို့|ကို)"), "")
                .replace(Regex("(ပါ|ပေးပါ|ပေး)"), "")
                .trim()
            if (contact.isNotEmpty()) {
                return ExtractedSlots(
                    intentCandidate = "ACTION_CALL",
                    contactName = contact,
                    originalText = normalized
                )
            }
        }

        // 3. Application Launch (handles e.g. "YouTube ဖွင့်ပေးပါ", "ဖွင့်မယ် Facebook")
        if (lower.contains("ဖွင့်") || lower.contains("open") || lower.contains("launch")) {
            val appCandidate = normalized
                .replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
                .replace(Regex("(ဖွင့်ပေးပါ|ဖွင့်ပါဦး|ဖွင့်ပါ|ဖွင့်မယ်|ဖွင့်|open|launch)"), "")
                .replace(Regex("(အက်ပ်|app|application)"), "")
                .replace(Regex("(ပါ|ပေးပါ|ပေး)"), "")
                .trim()
            if (appCandidate.isNotEmpty() && !appCandidate.contains("wifi") && !appCandidate.contains("ဓာတ်မီး")) {
                val resolvedEntry = BurmesePhoneticLexicon.resolveTerm(appCandidate)
                val target = if (resolvedEntry != null) {
                    when (resolvedEntry.category) {
                        BurmesePhoneticLexicon.TargetCategory.APP_YOUTUBE -> "YouTube"
                        BurmesePhoneticLexicon.TargetCategory.APP_FACEBOOK -> "Facebook"
                        BurmesePhoneticLexicon.TargetCategory.APP_MESSENGER -> "Messenger"
                        BurmesePhoneticLexicon.TargetCategory.APP_VIBER -> "Viber"
                        BurmesePhoneticLexicon.TargetCategory.APP_TELEGRAM -> "Telegram"
                        BurmesePhoneticLexicon.TargetCategory.APP_TIKTOK -> "TikTok"
                        BurmesePhoneticLexicon.TargetCategory.APP_CAMERA -> "Camera"
                        BurmesePhoneticLexicon.TargetCategory.APP_GALLERY -> "Gallery"
                        BurmesePhoneticLexicon.TargetCategory.APP_CHROME -> "Chrome"
                        BurmesePhoneticLexicon.TargetCategory.APP_GMAIL -> "Gmail"
                        BurmesePhoneticLexicon.TargetCategory.APP_MAPS -> "Google Maps"
                        BurmesePhoneticLexicon.TargetCategory.APP_SETTINGS -> "Settings"
                        else -> appCandidate
                    }
                } else {
                    appCandidate
                }
                return ExtractedSlots(
                    intentCandidate = "ACTION_OPEN_APP",
                    targetApp = target,
                    originalText = normalized
                )
            }
        }

        // 4. Fallback Search Query
        val query = normalized.replace(Regex("^(ဟေး\\s*ကျော်ကြီး|ကျော်ကြီး)\\s*"), "")
        return ExtractedSlots(
            intentCandidate = "GENERAL_QUERY",
            searchQuery = query,
            originalText = normalized
        )
    }
}
