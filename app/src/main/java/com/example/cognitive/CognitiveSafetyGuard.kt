package com.example.cognitive

import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticIntent

/**
 * Cognitive Safety Guard
 * Enforces execution isolation and risk-based validation before passing ActionPlan to Android execution layer.
 */
class CognitiveSafetyGuard {

    enum class RiskLevel {
        SAFE,
        MODERATE,
        HIGH,
        CRITICAL
    }

    data class EvaluationResult(
        val isPermitted: Boolean,
        val riskLevel: RiskLevel,
        val requiresExplicitConfirmation: Boolean,
        val confirmationPromptMM: String? = null,
        val explanation: String
    )

    fun evaluate(
        intent: SemanticIntent,
        target: String?,
        confidence: Float,
        confidenceLevel: ConfidenceLevel,
        parameters: Map<String, Any?>
    ): EvaluationResult {
        // 1. Critical Actions (Financial / Account / Permanent Deletion)
        when (intent) {
            SemanticIntent.PAYMENT -> {
                return EvaluationResult(
                    isPermitted = false,
                    riskLevel = RiskLevel.CRITICAL,
                    requiresExplicitConfirmation = true,
                    confirmationPromptMM = "ငွေပေးချေမှုကို အမှန်တကယ် ပြုလုပ်မှာ သေချာပါသလားခင်ဗျာ။",
                    explanation = "Critical payment action requires explicit secondary confirmation"
                )
            }
            SemanticIntent.UNINSTALL_APP, SemanticIntent.DELETE_ITEM -> {
                return EvaluationResult(
                    isPermitted = false,
                    riskLevel = RiskLevel.HIGH,
                    requiresExplicitConfirmation = true,
                    confirmationPromptMM = "${target ?: "အရာဝတ္ထု"} ကို အမှန်တကယ် ဖျက်ပစ်မှာ သေချာပါသလားခင်ဗျာ။",
                    explanation = "High-risk data/app deletion action requires user confirmation"
                )
            }
            SemanticIntent.SEND_SMS -> {
                return EvaluationResult(
                    isPermitted = true,
                    riskLevel = RiskLevel.MODERATE,
                    requiresExplicitConfirmation = true,
                    confirmationPromptMM = "${target ?: "လက်ခံသူ"} ဆီသို့ မက်ဆေ့ခ်ျ ပို့ဆောင်ပေးရမလားခင်ဗျာ။",
                    explanation = "Outbound SMS messaging requires recipient verification"
                )
            }
            SemanticIntent.CALL_CONTACT -> {
                // Strict threshold for outbound calling
                if (target.isNullOrBlank()) {
                    return EvaluationResult(
                        isPermitted = false,
                        riskLevel = RiskLevel.MODERATE,
                        requiresExplicitConfirmation = true,
                        confirmationPromptMM = "မည်သူ့ထံ ဖုန်းခေါ်ဆိုပေးရမလဲခင်ဗျာ။",
                        explanation = "Target contact missing for phone call"
                    )
                }
                val isHighConfidence = confidence >= 0.85f && confidenceLevel == ConfidenceLevel.EXECUTE
                return EvaluationResult(
                    isPermitted = isHighConfidence,
                    riskLevel = RiskLevel.MODERATE,
                    requiresExplicitConfirmation = !isHighConfidence,
                    confirmationPromptMM = if (!isHighConfidence) "$target ထံသို့ ဖုန်းခေါ်ဆိုပေးရမလားခင်ဗျာ။" else null,
                    explanation = if (isHighConfidence) "Permitted high-confidence call" else "Moderate confidence call requires confirmation"
                )
            }
            else -> {
                // System Utilities, Media, Connectivity, Alarms, Navigation
                val isPermitted = confidenceLevel == ConfidenceLevel.EXECUTE
                val requiresClarification = confidenceLevel == ConfidenceLevel.ASK_FOR_CLARIFICATION
                return EvaluationResult(
                    isPermitted = isPermitted,
                    riskLevel = RiskLevel.SAFE,
                    requiresExplicitConfirmation = requiresClarification,
                    confirmationPromptMM = if (requiresClarification) "${intent.defaultDescriptionMM} ကို ပြုလုပ်ပေးရမလားခင်ဗျာ။" else null,
                    explanation = "Routine safe action (confidence=$confidence)"
                )
            }
        }
    }
}
