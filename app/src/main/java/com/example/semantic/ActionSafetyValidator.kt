package com.example.semantic

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticIntent

/**
 * Action Safety Validator for MSIL
 * Enforces safety guardrails for potentially dangerous or irreversible actions.
 */
class ActionSafetyValidator {

    data class SafetyResult(
        val isSafeToExecuteDirectly: Boolean,
        val requiresConfirmation: Boolean,
        val confirmationPromptMM: String? = null,
        val safetyRiskLevel: String = "SAFE" // SAFE, MODERATE, HIGH, CRITICAL
    )

    fun validate(
        intent: SemanticIntent,
        target: String?,
        confidenceLevel: ConfidenceLevel,
        parameters: Map<String, Any?>
    ): SafetyResult {
        // Critical or dangerous actions always require safety checks
        when (intent) {
            SemanticIntent.PAYMENT -> {
                return SafetyResult(
                    isSafeToExecuteDirectly = false,
                    requiresConfirmation = true,
                    confirmationPromptMM = "ငွေပေးချေမှုကို အမှန်တကယ် ပြုလုပ်မှာ သေချာပါသလားခင်ဗျာ။",
                    safetyRiskLevel = "CRITICAL"
                )
            }
            SemanticIntent.UNINSTALL_APP, SemanticIntent.DELETE_ITEM -> {
                return SafetyResult(
                    isSafeToExecuteDirectly = false,
                    requiresConfirmation = true,
                    confirmationPromptMM = "${target ?: "အရာဝတ္ထု"} ကို အမှန်တကယ် ဖျက်ပစ်မှာ သေချာပါသလားခင်ဗျာ။",
                    safetyRiskLevel = "HIGH"
                )
            }
            SemanticIntent.SEND_SMS -> {
                return SafetyResult(
                    isSafeToExecuteDirectly = false,
                    requiresConfirmation = true,
                    confirmationPromptMM = "${target ?: "လက်ခံသူ"} ဆီသို့ မက်ဆေ့ခ်ျ ပို့ဆောင်ပေးရမလားခင်ဗျာ။",
                    safetyRiskLevel = "MODERATE"
                )
            }
            SemanticIntent.CALL_CONTACT -> {
                // If target is missing, needs confirmation / clarification
                if (target.isNullOrBlank()) {
                    return SafetyResult(
                        isSafeToExecuteDirectly = false,
                        requiresConfirmation = true,
                        confirmationPromptMM = "မည်သူ့ထံ ဖုန်းခေါ်ဆိုပေးရမလဲခင်ဗျာ။",
                        safetyRiskLevel = "MODERATE"
                    )
                }
                // For valid contact target, if confidence is high, execute or safe
                val needsConfirm = confidenceLevel != ConfidenceLevel.EXECUTE
                return SafetyResult(
                    isSafeToExecuteDirectly = !needsConfirm,
                    requiresConfirmation = needsConfirm,
                    confirmationPromptMM = if (needsConfirm) "$target ထံသို့ ဖုန်းခေါ်ဆိုပေးရမလားခင်ဗျာ။" else null,
                    safetyRiskLevel = "MODERATE"
                )
            }
            else -> {
                // Routine system actions (OPEN_APP, VOLUME, WIFI, ALARM, NEXT_TRACK, etc.)
                val requiresClarification = confidenceLevel == ConfidenceLevel.ASK_FOR_CLARIFICATION
                return SafetyResult(
                    isSafeToExecuteDirectly = confidenceLevel == ConfidenceLevel.EXECUTE,
                    requiresConfirmation = requiresClarification,
                    confirmationPromptMM = if (requiresClarification) "${intent.defaultDescriptionMM} ကို ပြုလုပ်ပေးရမလားခင်ဗျာ။" else null,
                    safetyRiskLevel = "SAFE"
                )
            }
        }
    }
}
