package com.example.semantic

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Semantic Confidence Engine for MSIL
 * Calibrates confidence scores based on intent clarity, entity completeness,
 * phonetic certainty, and contextual resolution.
 */
class SemanticConfidenceEngine {

    data class ConfidenceAssessment(
        val confidence: Float,
        val level: ConfidenceLevel,
        val explanation: String
    )

    fun evaluateConfidence(
        intent: SemanticIntent,
        baseConfidence: Float,
        entities: List<SemanticEntity>,
        isContextInherited: Boolean,
        isPolite: Boolean
    ): ConfidenceAssessment {
        if (intent == SemanticIntent.UNKNOWN) {
            return ConfidenceAssessment(
                confidence = 0.35f,
                level = ConfidenceLevel.ASK_USER_TO_REPEAT,
                explanation = "Intent could not be mapped to any known semantic action"
            )
        }

        var score = baseConfidence

        // Boost for clear entities
        if (entities.isNotEmpty()) {
            score += 0.03f
        }

        // Slight adjustment for context inheritance
        if (isContextInherited) {
            score = score.coerceAtLeast(0.88f)
        }

        // Politeness particles indicate structured speech
        if (isPolite) {
            score += 0.02f
        }

        // Cap score between 0.0 and 0.99
        val finalScore = score.coerceIn(0.10f, 0.99f)

        val level = when {
            finalScore >= 0.85f -> ConfidenceLevel.EXECUTE
            finalScore >= 0.60f -> ConfidenceLevel.ASK_FOR_CLARIFICATION
            else -> ConfidenceLevel.ASK_USER_TO_REPEAT
        }

        val explanation = when (level) {
            ConfidenceLevel.EXECUTE -> "High confidence semantic match: $intent (score=$finalScore)"
            ConfidenceLevel.ASK_FOR_CLARIFICATION -> "Moderate confidence. Needs user confirmation: $intent (score=$finalScore)"
            ConfidenceLevel.ASK_USER_TO_REPEAT -> "Low confidence. Request user to repeat: (score=$finalScore)"
        }

        return ConfidenceAssessment(
            confidence = finalScore,
            level = level,
            explanation = explanation
        )
    }
}
