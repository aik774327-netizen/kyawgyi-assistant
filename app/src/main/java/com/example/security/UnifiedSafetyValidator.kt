package com.example.security

import android.util.Log
import com.example.pipeline.contract.RiskLevel
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticIntent

/**
 * Unified Safety Gate Evaluation Result
 */
data class UnifiedSafetyEvaluation(
    val isPermitted: Boolean,
    val requiresUserConfirmation: Boolean,
    val requiresClarification: Boolean = false,
    val riskLevel: RiskLevel,
    val confirmationPromptMM: String? = null,
    val rejectionReasonMM: String? = null,
    val auditLogReason: String
)

/**
 * Unified Safety Validator (Stage 16 Production Hardening)
 *
 * Implements strict authoritative safety policies:
 * 1. HIGH / CRITICAL risk actions ALWAYS require explicit confirmation — AI confidence (even 1.00) CANNOT bypass confirmation.
 * 2. LOW risk commands:
 *    - confidence >= 0.85: execute directly
 *    - 0.65 to 0.84: request Burmese clarification
 *    - < 0.65: politely reject in Burmese
 */
object UnifiedSafetyValidator {

    private const val TAG = "UnifiedSafetyValidator"

    // High & Critical Risk action classifications
    private val CRITICAL_ACTIONS = setOf(
        "PAYMENT", "PURCHASE", "ACCOUNT_SECURITY", "DESTRUCTIVE_ACTION",
        "FACTORY_RESET", "FORMAT", "WIPE"
    )

    private val HIGH_RISK_ACTIONS = setOf(
        "CALL", "SEND_MESSAGE", "SEND_SMS", "DELETE", "DELETE_ITEM", "UNINSTALL_APP",
        "SYSTEM_SETTINGS", "MODIFY_SYSTEM"
    )

    private val MEDIUM_RISK_ACTIONS = setOf(
        "WIFI_TOGGLE", "BLUETOOTH_TOGGLE", "SET_VOLUME", "FLASHLIGHT", "ALARM", "TIMER"
    )

    /**
     * Authoritative Safety Evaluation
     */
    fun evaluate(
        actionType: String,
        target: String?,
        confidence: Float,
        parameters: Map<String, Any?> = emptyMap()
    ): UnifiedSafetyEvaluation {
        val upperAction = actionType.trim().uppercase()

        // 1. Critical Risk Check (Financial / Destructive)
        if (CRITICAL_ACTIONS.contains(upperAction)) {
            val prompt = when (upperAction) {
                "PAYMENT", "PURCHASE" -> "ငွေပေးချေမှု/ငွေလွှဲခြင်းဆိုင်ရာ လုပ်ဆောင်ချက် ဖြစ်ပါသည်။ အမှန်တကယ် ဆက်လက်လုပ်ဆောင်မှာ သေချာပါသလားခင်ဗျာ။"
                else -> "${target ?: "အရာဝတ္ထု"} ကို ဖျက်ဆီး/ဖယ်ရှားမည့် လုပ်ဆောင်ချက် ဖြစ်ပါသည်။ အမှန်တကယ် ပြုလုပ်မှာ သေချာပါသလားခင်ဗျာ။"
            }
            return UnifiedSafetyEvaluation(
                isPermitted = true,
                requiresUserConfirmation = true,
                requiresClarification = false,
                riskLevel = RiskLevel.CRITICAL,
                confirmationPromptMM = prompt,
                auditLogReason = "Critical risk action gated by mandatory confirmation"
            )
        }

        // 2. High Risk Check (Communication / Modification / Deletion)
        if (HIGH_RISK_ACTIONS.contains(upperAction)) {
            val prompt = when (upperAction) {
                "CALL" -> "${target ?: "ရွေးချယ်ထားသူ"} ထံသို့ ဖုန်းခေါ်ဆိုပေးရမလားခင်ဗျာ။"
                "SEND_MESSAGE", "SEND_SMS" -> "${target ?: "လက်ခံသူ"} ဆီသို့ မက်ဆေ့ခ်ျ ပေးပို့ရမလားခင်ဗျာ။"
                "DELETE", "DELETE_ITEM", "UNINSTALL_APP" -> "${target ?: "အရာဝတ္ထု"} ကို အမှန်တကယ် ဖျက်ပစ်မှာ သေချာပါသလားခင်ဗျာ။"
                else -> "${target ?: actionType} ဆက်တင် ပြင်ဆင်မှုကို အတည်ပြုပေးပါခင်ဗျာ။"
            }
            return UnifiedSafetyEvaluation(
                isPermitted = true,
                requiresUserConfirmation = true,
                requiresClarification = false,
                riskLevel = RiskLevel.HIGH,
                confirmationPromptMM = prompt,
                auditLogReason = "High-risk operation gated by mandatory confirmation regardless of confidence ($confidence)"
            )
        }

        // 3. Medium Risk Check (Network, Flashlight, Alarms)
        val assignedRisk = if (MEDIUM_RISK_ACTIONS.contains(upperAction)) RiskLevel.MEDIUM else RiskLevel.LOW

        // 4. Confidence Threshold Policy for Safe / Medium Actions
        return when {
            confidence >= 0.85f -> {
                UnifiedSafetyEvaluation(
                    isPermitted = true,
                    requiresUserConfirmation = false,
                    requiresClarification = false,
                    riskLevel = assignedRisk,
                    auditLogReason = "High confidence ($confidence >= 0.85) authorized for direct execution"
                )
            }
            confidence >= 0.65f -> {
                val clarifyPrompt = when (upperAction) {
                    "OPEN_APP" -> "${target ?: "အက်ပ်"} ကို ဖွင့်ပေးရမလားခင်ဗျာ။"
                    "VOLUME_UP", "VOLUME_DOWN", "SET_VOLUME" -> "အသံအတိုးအကျယ်ကို ပြောင်းလဲပေးရမလားခင်ဗျာ။"
                    "WIFI_TOGGLE" -> "Wi-Fi ကို အဖွင့်/အပိတ် ပြုလုပ်ပေးရမလားခင်ဗျာ။"
                    "FLASHLIGHT" -> "ဓာတ်မီးကို အဖွင့်/အပိတ် ပြုလုပ်ပေးရမလားခင်ဗျာ။"
                    else -> "${target ?: actionType} ကို လုပ်ဆောင်ပေးရမလားခင်ဗျာ။"
                }
                UnifiedSafetyEvaluation(
                    isPermitted = true,
                    requiresUserConfirmation = true,
                    requiresClarification = true,
                    riskLevel = assignedRisk,
                    confirmationPromptMM = clarifyPrompt,
                    auditLogReason = "Medium confidence (0.65 <= $confidence < 0.85) requires Burmese clarification"
                )
            }
            else -> {
                UnifiedSafetyEvaluation(
                    isPermitted = false,
                    requiresUserConfirmation = false,
                    requiresClarification = false,
                    riskLevel = assignedRisk,
                    rejectionReasonMM = "ပြောကြားချက်ကို ကောင်းစွာ နားမလည်လိုက်ပါခင်ဗျာ။ ကျေးဇူးပြု၍ ထပ်မံ ပြောကြားပေးပါဦး။",
                    auditLogReason = "Low confidence ($confidence < 0.65) rejected politely"
                )
            }
        }
    }
}
