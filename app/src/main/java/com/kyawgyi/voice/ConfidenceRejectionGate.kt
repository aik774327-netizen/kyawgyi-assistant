package com.kyawgyi.voice

/**
 * Decision outcome returned by [ConfidenceRejectionGate].
 */
enum class GateDecision {
    ACCEPTED,
    CLARIFICATION_NEEDED,
    REJECTED_NOISE
}

/**
 * Detailed evaluation result from [ConfidenceRejectionGate].
 */
data class ConfidenceGateEvaluation(
    val decision: GateDecision,
    val rawTranscript: String,
    val effectiveConfidence: Float,
    val burmeseOrLatinValidRatio: Float,
    val clarificationPromptBurmese: String? = null,
    val reason: String
)

/**
 * ConfidenceRejectionGate (Requirements 10 — Anti-Hallucination & Low-Confidence ASR Gate)
 *
 * Filters out low-confidence ASR hypotheses, background TV/radio chatter, and single-phoneme
 * breath/cough artifacts before they reach the NLU Command Router:
 * - [GateDecision.ACCEPTED]: High confidence & valid Burmese/English command tokens -> execute intent.
 * - [GateDecision.CLARIFICATION_NEEDED]: Medium confidence on plausible speech -> ask polite Burmese clarification.
 * - [GateDecision.REJECTED_NOISE]: Very low confidence, low RMS energy, or gibberish artifact -> silently ignore.
 */
class ConfidenceRejectionGate(
    val acceptConfidenceThreshold: Float = 0.58f,
    val clarificationConfidenceThreshold: Float = 0.35f,
    val minSpeechRmsEnergy: Double = 140.0
) {

    companion object {
        const val DEFAULT_CLARIFICATION_PROMPT =
            "သေချာမကြားလိုက်ရလို့ နောက်တစ်ခေါက်လောက် ပြန်ပြောပေးပါဦးခင်ဗျာ။"
    }

    /**
     * Evaluates whether [transcript] should be accepted, asked for clarification, or rejected as noise.
     */
    fun evaluate(
        transcript: String,
        confidenceScore: Float = 0.88f,
        peakRmsEnergy: Double = 1200.0
    ): ConfidenceGateEvaluation {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return ConfidenceGateEvaluation(
                decision = GateDecision.REJECTED_NOISE,
                rawTranscript = "",
                effectiveConfidence = 0f,
                burmeseOrLatinValidRatio = 0f,
                reason = "Empty transcript"
            )
        }

        val validCharCount = trimmed.count { ch ->
            ch in '\u1000'..'\u109F' || ch.isLetterOrDigit()
        }
        val validRatio = validCharCount.toFloat() / trimmed.length.coerceAtLeast(1).toFloat()

        // Single-character non-digit noise artifact check
        if (validCharCount <= 1 && !trimmed.first().isDigit()) {
            return ConfidenceGateEvaluation(
                decision = GateDecision.REJECTED_NOISE,
                rawTranscript = trimmed,
                effectiveConfidence = confidenceScore * 0.4f,
                burmeseOrLatinValidRatio = validRatio,
                reason = "Single-character acoustic artifact"
            )
        }

        // Normalize confidence when Android SpeechRecognizer returns 0.0f or -1.0f (unspecified confidence)
        val normalizedConfidence = if (confidenceScore <= 0.0f) 0.85f else confidenceScore.coerceIn(0f, 1f)

        if (peakRmsEnergy < minSpeechRmsEnergy && normalizedConfidence < acceptConfidenceThreshold) {
            return ConfidenceGateEvaluation(
                decision = GateDecision.REJECTED_NOISE,
                rawTranscript = trimmed,
                effectiveConfidence = normalizedConfidence,
                burmeseOrLatinValidRatio = validRatio,
                reason = "Below minimum speech RMS energy ($peakRmsEnergy < $minSpeechRmsEnergy)"
            )
        }

        return when {
            normalizedConfidence >= acceptConfidenceThreshold && validRatio >= 0.4f -> {
                ConfidenceGateEvaluation(
                    decision = GateDecision.ACCEPTED,
                    rawTranscript = trimmed,
                    effectiveConfidence = normalizedConfidence,
                    burmeseOrLatinValidRatio = validRatio,
                    reason = "Accepted (conf=${"%.2f".format(normalizedConfidence)})"
                )
            }
            normalizedConfidence >= clarificationConfidenceThreshold && validRatio >= 0.35f -> {
                ConfidenceGateEvaluation(
                    decision = GateDecision.CLARIFICATION_NEEDED,
                    rawTranscript = trimmed,
                    effectiveConfidence = normalizedConfidence,
                    burmeseOrLatinValidRatio = validRatio,
                    clarificationPromptBurmese = DEFAULT_CLARIFICATION_PROMPT,
                    reason = "Borderline confidence (${"%.2f".format(normalizedConfidence)})"
                )
            }
            else -> {
                ConfidenceGateEvaluation(
                    decision = GateDecision.REJECTED_NOISE,
                    rawTranscript = trimmed,
                    effectiveConfidence = normalizedConfidence,
                    burmeseOrLatinValidRatio = validRatio,
                    reason = "Rejected low confidence (${"%.2f".format(normalizedConfidence)})"
                )
            }
        }
    }
}
