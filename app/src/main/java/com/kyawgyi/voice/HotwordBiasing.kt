package com.kyawgyi.voice

import java.io.File

/**
 * Represents a weighted hotword/phrase entry for Sherpa-ONNX and Vosk contextual biasing.
 */
data class HotwordEntry(
    val phrase: String,
    val canonicalReplacement: String = phrase,
    val boostScore: Float = 2.0f,
    val category: String = "DOMAIN"
)

/**
 * HotwordBiasing (Requirements 9 — Contextual Hotword & Domain Phrase Biasing)
 *
 * Provides:
 * 1. Sherpa-ONNX `hotwords.txt` generation (`<phrase> :<boostScore>` per line).
 * 2. Vosk JSON grammar list generation (`["phrase1", "phrase2", ...]`).
 * 3. Post-ASR fuzzy phonetic/homophone hotword rescoring for Myanmar proper nouns,
 *    payment apps (KBZPay, WavePay, AYA Pay, CB Pay), telecom operators (MPT, ATOM, Ooredoo, Mytel),
 *    and system assistant commands.
 */
class HotwordBiasing(
    initialEntries: List<HotwordEntry> = DEFAULT_MYANMAR_HOTWORDS
) {

    companion object {
        const val DEFAULT_HOTWORDS_SCORE = 2.0f

        val DEFAULT_MYANMAR_HOTWORDS: List<HotwordEntry> = listOf(
            // Wake words & Assistant Persona
            HotwordEntry("ဟေး ကျော်ကြီး", "ဟေး ကျော်ကြီး", 3.0f, "WAKE_WORD"),
            HotwordEntry("ကိုကျော်ကြီး", "ကိုကျော်ကြီး", 2.8f, "WAKE_WORD"),
            // Myanmar Mobile Banking & Wallets
            HotwordEntry("ကေဘီဇက်ပေး", "KBZPay", 2.6f, "FINTECH"),
            HotwordEntry("ဝေ့ဗ်ပေး", "WavePay", 2.6f, "FINTECH"),
            HotwordEntry("ဧရာပေး", "AYA Pay", 2.4f, "FINTECH"),
            HotwordEntry("စီဘီပေး", "CB Pay", 2.4f, "FINTECH"),
            // Myanmar Telecom Operators
            HotwordEntry("အမ်ပီတီ", "MPT", 2.3f, "TELECOM"),
            HotwordEntry("အက်တမ်", "ATOM", 2.3f, "TELECOM"),
            HotwordEntry("အူရီဒူး", "Ooredoo", 2.3f, "TELECOM"),
            HotwordEntry("မိုင်တယ်", "Mytel", 2.3f, "TELECOM"),
            // Core System Controls & Connectivity
            HotwordEntry("ဝိုင်ဖိုင်", "Wi-Fi", 2.2f, "SYSTEM"),
            HotwordEntry("ဘလူးတုသ်", "Bluetooth", 2.2f, "SYSTEM"),
            HotwordEntry("ဟော့စပေါ့တ်", "Hotspot", 2.2f, "SYSTEM"),
            HotwordEntry("လက်နှိပ်ဓာတ်မီး", "လက်နှိပ်ဓာတ်မီး", 2.2f, "SYSTEM"),
            HotwordEntry("ဖုန်းခေါ်ပေးပါ", "ဖုန်းခေါ်ပေးပါ", 2.0f, "COMMAND"),
            HotwordEntry("အသံတိုးပေးပါ", "အသံတိုးပေးပါ", 2.0f, "COMMAND"),
            HotwordEntry("အသံချဲ့ပေးပါ", "အသံချဲ့ပေးပါ", 2.0f, "COMMAND"),
            HotwordEntry("ဘက်ထရီဘယ်လောက်ကျန်လဲ", "ဘက်ထရီဘယ်လောက်ကျန်လဲ", 2.0f, "COMMAND")
        )

        /**
         * Near-homophone / acoustic confusion variants commonly produced by Myanmar ASR
         * mapped to their canonical hotword phrase.
         */
        private val FUZZY_HOTWORD_ALIASES: Map<String, String> = mapOf(
            "ကေဘီဇတ်ပေး" to "ကေဘီဇက်ပေး",
            "ကေဘီဇက်ပေ" to "ကေဘီဇက်ပေး",
            "ဝေဗ်ပေး" to "ဝေ့ဗ်ပေး",
            "ဝေ့ပေး" to "ဝေ့ဗ်ပေး",
            "ဘလူးတု" to "ဘလူးတုသ်",
            "ဟော့စပေါ့" to "ဟော့စပေါ့တ်",
            "အမ်ဘီတီ" to "အမ်ပီတီ",
            "အော်ရီဒူး" to "အူရီဒူး"
        )
    }

    private val entries = LinkedHashMap<String, HotwordEntry>()

    init {
        initialEntries.forEach { entry ->
            entries[entry.phrase] = entry
        }
    }

    @Synchronized
    fun registerHotword(
        phrase: String,
        canonicalReplacement: String = phrase,
        boostScore: Float = DEFAULT_HOTWORDS_SCORE,
        category: String = "CUSTOM"
    ) {
        val clean = phrase.trim()
        if (clean.isEmpty()) return
        entries[clean] = HotwordEntry(
            phrase = clean,
            canonicalReplacement = canonicalReplacement.trim().ifEmpty { clean },
            boostScore = boostScore.coerceIn(0.5f, 5.0f),
            category = category
        )
    }

    @Synchronized
    fun registerDynamicContacts(contactNames: Iterable<String>, boostScore: Float = 2.4f) {
        for (name in contactNames) {
            val clean = name.trim()
            if (clean.isNotEmpty()) {
                entries[clean] = HotwordEntry(
                    phrase = clean,
                    canonicalReplacement = clean,
                    boostScore = boostScore,
                    category = "CONTACT"
                )
            }
        }
    }

    @Synchronized
    fun getAllEntries(): List<HotwordEntry> = entries.values.toList()

    /**
     * Generates Sherpa-ONNX compatible `hotwords.txt` content (`<phrase> :<boostScore>` per line).
     */
    @Synchronized
    fun buildSherpaHotwordsFileContent(): String =
        entries.values.joinToString("\n") { entry ->
            "${entry.phrase} :${String.format(java.util.Locale.US, "%.1f", entry.boostScore)}"
        }

    /**
     * Writes `hotwords.txt` to [targetFile] for Sherpa-ONNX recognizer configuration.
     */
    @Synchronized
    fun writeSherpaHotwordsFile(targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(buildSherpaHotwordsFileContent(), Charsets.UTF_8)
        return targetFile
    }

    /**
     * Generates a Vosk-compatible JSON array of grammar phrases (`["phrase1", "phrase2", ...]`).
     */
    @Synchronized
    fun buildVoskGrammarJson(): String {
        val quoted = entries.values.map { entry ->
            "\"${entry.phrase.replace("\"", "\\\"")}\""
        }
        return "[${quoted.joinToString(", ")}]"
    }

    /**
     * Applies post-ASR contextual hotword normalization and fuzzy acoustic correction
     * to a raw transcript while preserving surrounding Burmese words.
     */
    @Synchronized
    fun applyHotwordBiasToTranscript(rawTranscript: String): String {
        if (rawTranscript.isBlank()) return rawTranscript
        var text = rawTranscript.trim()

        // Step 1: Resolve acoustic near-miss variants to canonical Burmese hotword (longest first)
        val sortedAliases = FUZZY_HOTWORD_ALIASES.entries.sortedByDescending { it.key.length }
        for ((variant, canonicalBurmese) in sortedAliases) {
            if (text.contains(variant)) {
                text = text.replace(Regex("${Regex.escape(variant)}(?![\\u1038\\u1037])"), canonicalBurmese)
            }
        }

        // Step 2: Apply canonical replacements for registered hotwords (longest phrase first)
        val sortedEntries = entries.values.sortedByDescending { it.phrase.length }
        for (entry in sortedEntries) {
            if (entry.canonicalReplacement != entry.phrase && text.contains(entry.phrase)) {
                text = text.replace(entry.phrase, entry.canonicalReplacement)
            }
        }

        return text
    }
}
