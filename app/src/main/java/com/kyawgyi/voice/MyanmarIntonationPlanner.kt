package com.kyawgyi.voice

/**
 * Burmese sentence intonation contour classification for neural & system TTS prosody control.
 */
enum class MyanmarIntonationContour(
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val trailingPauseMs: Int
) {
    DECLARATIVE(pitchMultiplier = 1.00f, rateMultiplier = 0.96f, trailingPauseMs = 420),
    INTERROGATIVE(pitchMultiplier = 1.07f, rateMultiplier = 0.98f, trailingPauseMs = 380),
    POLITE_IMPERATIVE(pitchMultiplier = 1.03f, rateMultiplier = 0.95f, trailingPauseMs = 360),
    EMPHATIC_ALERT(pitchMultiplier = 1.05f, rateMultiplier = 1.02f, trailingPauseMs = 460)
}

/**
 * Planned Burmese prosody segment with target pitch, rate, and pause duration.
 */
data class PlannedMyanmarPhrase(
    val text: String,
    val contour: MyanmarIntonationContour,
    val pitchMultiplier: Float,
    val rateMultiplier: Float,
    val pauseAfterMs: Int
)

/**
 * MyanmarIntonationPlanner (Requirements 10 — Burmese Sentence Cadence & Prosody Planner)
 *
 * Splits Burmese text along traditional punctuation (`၊` Pote-Hti and `။` Pote-Ma) and
 * analyzes sentence-final particles (`လား`, `လဲ`, `နော်`, `ပါ`, `ခင်ဗျာ`, `ရှင့်`) to assign
 * natural pitch (`f0`), speaking rate, and breath pause durations for TTS synthesis.
 */
class MyanmarIntonationPlanner {

    companion object {
        private val QUESTION_PARTICLES = listOf("လား", "လဲ", "နော်", "ပါလိမ့်", "မလား")
        private val POLITE_PARTICLES = listOf("ပါခင်ဗျာ", "ပါရှင့်", "ပေးပါ", "ပါနော်", "ခင်ဗျာ", "ရှင့်")
        private val ALERT_KEYWORDS = listOf("သတိ", "အရေးပေါ်", "ချက်ချင်း", "မှားယွင်း")
    }

    /**
     * Plans prosody contours for each clause/sentence in [burmeseText].
     */
    fun planIntonation(burmeseText: String): List<PlannedMyanmarPhrase> {
        val cleaned = burmeseText.trim()
        if (cleaned.isEmpty()) return emptyList()

        // Split keeping Burmese punctuation delimiters
        val rawClauses = cleaned.split(Regex("(?<=[၊။?!])")).map { it.trim() }.filter { it.isNotEmpty() }
        if (rawClauses.isEmpty()) {
            val contour = classifyClause(cleaned)
            return listOf(
                PlannedMyanmarPhrase(
                    text = cleaned,
                    contour = contour,
                    pitchMultiplier = contour.pitchMultiplier,
                    rateMultiplier = contour.rateMultiplier,
                    pauseAfterMs = contour.trailingPauseMs
                )
            )
        }

        return rawClauses.map { clause ->
            val contour = classifyClause(clause)
            val isMinorClause = clause.endsWith("၊")
            val pauseMs = if (isMinorClause) 220 else contour.trailingPauseMs
            PlannedMyanmarPhrase(
                text = clause,
                contour = contour,
                pitchMultiplier = contour.pitchMultiplier,
                rateMultiplier = contour.rateMultiplier,
                pauseAfterMs = pauseMs
            )
        }
    }

    fun classifyClause(clause: String): MyanmarIntonationContour {
        val stripped = clause.trimEnd('၊', '။', '?', '!', ' ')
        return when {
            clause.endsWith("?") || QUESTION_PARTICLES.any { stripped.endsWith(it) } ->
                MyanmarIntonationContour.INTERROGATIVE
            ALERT_KEYWORDS.any { stripped.contains(it) } ->
                MyanmarIntonationContour.EMPHATIC_ALERT
            POLITE_PARTICLES.any { stripped.endsWith(it) } ->
                MyanmarIntonationContour.POLITE_IMPERATIVE
            else -> MyanmarIntonationContour.DECLARATIVE
        }
    }
}
