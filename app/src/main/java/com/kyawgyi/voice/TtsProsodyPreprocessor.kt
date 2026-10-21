package com.kyawgyi.voice

/**
 * TtsProsodyPreprocessor (Additional 12 — Burmese G2P, Pause Marker & Intonation Preprocessor for TTS)
 *
 * Combines:
 * 1. [MyanmarVerbalizer] (numbers, currency, percentages, units, English loanwords)
 * 2. [MyanmarPronunciationLexicon] (Exceptional Burmese G2P "ရေးတော့အမှန် ဖတ်တော့အသံ")
 * 3. Burmese punctuation pause markers (`၊` -> 250ms pause, `။` -> 500ms pause)
 * 4. Clause splitting for [StreamingTtsQueue] zero-latency sentence pipelining.
 */
class TtsProsodyPreprocessor(
    private val verbalizer: MyanmarVerbalizer = MyanmarVerbalizer()
) {

    /**
     * Prepares a Burmese text string with G2P pronunciation and prosodic pause tags.
     */
    fun prepare(text: String): String {
        if (text.isBlank()) return ""
        val verbalized = verbalizer.verbalizeForTts(text)
        val phonetic = MyanmarPronunciationLexicon.applyTtsPronunciationG2P(verbalized)
        return phonetic
            .replace("၊", "၊ <pause:250>")
            .replace("။", "။ <pause:500>")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Prepares clean spoken Burmese text without XML/tag markers for engines that read raw text directly.
     */
    fun prepareCleanSpokenText(text: String): String {
        if (text.isBlank()) return ""
        val verbalized = verbalizer.verbalizeForTts(text)
        return MyanmarPronunciationLexicon.applyTtsPronunciationG2P(verbalized)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Splits a multi-sentence Burmese response into ordered prosodic clauses (`PlannedMyanmarPhrase`)
     * so [StreamingTtsQueue] can begin playing the very first clause immediately.
     */
    fun splitIntoStreamingClauses(text: String): List<PlannedMyanmarPhrase> {
        val cleanSpoken = prepareCleanSpokenText(text)
        return verbalizer.verbalizeWithIntonation(cleanSpoken)
    }
}
