package com.example.security

enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH,
    CRITICAL
}

data class PolicyEvaluation(
    val isAllowed: Boolean,
    val riskLevel: RiskLevel,
    val requiresUserConfirmation: Boolean,
    val confirmationPromptMM: String? = null,
    val reason: String = "Allowed by policy"
)

/**
 * Enterprise Risk Policy for KYAW GYI AI
 * Validates whether intent/action execution adheres to safety policies,
 * requiring explicit user confirmation for high-risk operations (financial, deletion, dangerous intents).
 */
object RiskPolicy {

    private val CRITICAL_KEYWORDS = listOf("pay", "payment", "transfer", "ငွေလွှဲ", "ငွေပေး", "money")
    private val DESTRUCTIVE_KEYWORDS = listOf("format", "factory_reset", "wipe", "ဖျက်ချ", "စက်ပြန်စတင်")

    fun evaluate(action: String, target: String, parameters: Map<String, String> = emptyMap()): PolicyEvaluation {
        val lowerAction = action.lowercase()
        val lowerTarget = target.lowercase()

        // Critical Risk: Financial / Funds Transfer
        if (CRITICAL_KEYWORDS.any { lowerAction.contains(it) || lowerTarget.contains(it) }) {
            return PolicyEvaluation(
                isAllowed = false,
                riskLevel = RiskLevel.CRITICAL,
                requiresUserConfirmation = true,
                confirmationPromptMM = "ငွေလွှဲခြင်း/ငွေပေးချေမှုဆိုင်ရာ လုပ်ဆောင်ချက် ဖြစ်သောကြောင့် အတည်ပြုချက် လိုအပ်ပါသည်ခင်ဗျာ။",
                reason = "Financial transaction protection triggered"
            )
        }

        // High Risk: Destructive Wipe / Mass Deletion
        if (DESTRUCTIVE_KEYWORDS.any { lowerAction.contains(it) || lowerTarget.contains(it) }) {
            return PolicyEvaluation(
                isAllowed = false,
                riskLevel = RiskLevel.HIGH,
                requiresUserConfirmation = true,
                confirmationPromptMM = "$target ဆိုင်ရာ အချက်အလက်များ ဖျက်ဆီးမည့် လုပ်ဆောင်ချက် ဖြစ်ပါသည်။ အမှန်တကယ် ပြုလုပ်မှာ သေချာပါသလားခင်ဗျာ။",
                reason = "Destructive action protection triggered"
            )
        }

        // Moderate Risk: Direct Dialing / SMS
        if (lowerAction.contains("call") || lowerAction.contains("sms") || lowerAction.contains("phone")) {
            return PolicyEvaluation(
                isAllowed = true,
                riskLevel = RiskLevel.MODERATE,
                requiresUserConfirmation = false,
                reason = "Communication intent evaluated"
            )
        }

        // Routine Safe: App Open, Volume, Brightness, Flashlight, Wi-Fi, Media
        return PolicyEvaluation(
            isAllowed = true,
            riskLevel = RiskLevel.SAFE,
            requiresUserConfirmation = false,
            reason = "Standard system operation"
        )
    }
}
