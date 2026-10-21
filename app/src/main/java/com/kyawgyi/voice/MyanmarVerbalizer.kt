package com.kyawgyi.voice

/**
 * MyanmarVerbalizer (Requirements 10 — Burmese Symbol, Unit & Technical Token Verbalizer for TTS)
 *
 * Expands symbols (`%`, `°C`, `km/h`, `mAh`, `GHz`, `MHz`, `+`, `=`, `/`), currency codes (`MMK`, `USD`),
 * numbers, and code-switched English terms into natural Burmese spoken words so offline Myanmar
 * neural TTS (Sherpa-ONNX VITS / F5-TTS) never encounters unpronounceable symbols.
 */
class MyanmarVerbalizer(
    private val numberNormalizer: MyanmarNumberNormalizer = MyanmarNumberNormalizer(),
    private val codeSwitchNormalizer: CodeSwitchNormalizer = CodeSwitchNormalizer(),
    private val intonationPlanner: MyanmarIntonationPlanner = MyanmarIntonationPlanner()
) {

    companion object {
        private val UNIT_AND_SYMBOL_MAP: LinkedHashMap<Regex, String> = linkedMapOf(
            Regex("(?i)(\\d+)\\s*°C\\b") to "$1 ဒီဂရီ စင်တီဂရိတ်",
            Regex("(?i)(\\d+)\\s*°F\\b") to "$1 ဒီဂရီ ဖာရင်ဟိုက်",
            Regex("(?i)(\\d+)\\s*km/h\\b") to "တစ်နာရီ $1 ကီလိုမီတာ",
            Regex("(?i)(\\d+)\\s*mAh\\b") to "$1 မီလီအမ်ပီယာနာရီ",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*GHz\\b") to "$1 ဂစ်ဂါဟက်ဇ်",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*MHz\\b") to "$1 မက်ဂါဟက်ဇ်",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*kHz\\b") to "$1 ကီလိုဟက်ဇ်",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*GB\\b") to "$1 ဂစ်ဂါဘိုက်",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*MB\\b") to "$1 မက်ဂါဘိုက်",
            Regex("(?i)(\\d+(?:\\.\\d+)?)\\s*KB\\b") to "$1 ကီလိုဘိုက်",
            Regex("(?i)\\bUSD\\s*(\\d+)") to "အမေရိကန်ဒေါ်လာ $1",
            Regex("(?i)(\\d+)\\s*USD\\b") to "အမေရိကန်ဒေါ်လာ $1"
        )
    }

    /**
     * Verbalizes [rawText] into fully spoken Burmese text ready for neural or system TTS.
     */
    fun verbalizeForTts(rawText: String): String {
        if (rawText.isBlank()) return rawText
        var text = rawText.trim()

        // Step 1: Expand technical units & measurement symbols before number expansion
        for ((pattern, replacement) in UNIT_AND_SYMBOL_MAP) {
            text = text.replace(pattern, replacement)
        }

        // Step 2: Expand numbers, currency, percentages, phone numbers, and dates
        text = numberNormalizer.digitsToSpokenBurmese(text)

        // Step 3: Expand English loanwords & acronyms into Burmese phonetic script
        text = codeSwitchNormalizer.normalizeTtsResponse(text)

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Verbalizes [rawText] and returns prosody-planned phrases with pitch, rate, and pause targets.
     */
    fun verbalizeWithIntonation(rawText: String): List<PlannedMyanmarPhrase> {
        val spoken = verbalizeForTts(rawText)
        return intonationPlanner.planIntonation(spoken)
    }
}
