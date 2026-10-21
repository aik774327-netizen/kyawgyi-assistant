package com.example.googlehome.service

/**
 * Execution Tiers for Multi-Protocol Smart Home Automation
 */
enum class ExecutionTier(
    val displayName: String,
    val targetSlaMs: Long,
    val descriptionMM: String
) {
    TIER_1_MATTER_LOCAL(
        displayName = "Matter / Direct LAN",
        targetSlaMs = 150L,
        descriptionMM = "ကြိုးမဲ့ Local Wi-Fi / Thread Fabric မှတစ်ဆင့် တိုက်ရိုက်ထိန်းချုပ်ခြင်း"
    ),
    TIER_2_DIRECT_API(
        displayName = "Google HomeGraph API",
        targetSlaMs = 350L,
        descriptionMM = "Google Smart Home Cloud / Webhook Bridge မှတစ်ဆင့် တိုက်ရိုက်ထိန်းချုပ်ခြင်း"
    ),
    TIER_3_ASSISTANT_INTENT(
        displayName = "Google Assistant Fallback",
        targetSlaMs = 500L,
        descriptionMM = "Direct Connection ကြာမြင့်ချိန် (>500ms) သို့မဟုတ် ကွန်ရက်ကျချိန်တွင် Assistant သို့ လွှဲပြောင်းခြင်း"
    ),
    TIER_4_OFFLINE_RECOVERY(
        displayName = "Offline Rollback",
        targetSlaMs = 0L,
        descriptionMM = "ကွန်ရက်လုံးဝမရှိချိန်တွင် UI မူလအနေအထား ပြန်ပြင်ခြင်းနှင့် အသံဖြင့် သတိပေးခြင်း"
    )
}

/**
 * Reactive StateFlow Model for Smart Home Command Execution and Latency Monitoring
 */
sealed class SmartHomeExecutionState {
    data object Idle : SmartHomeExecutionState()

    data class Executing(
        val deviceId: String,
        val deviceName: String,
        val actionType: String,
        val targetState: String,
        val tier: ExecutionTier,
        val startTimeMs: Long = System.currentTimeMillis()
    ) : SmartHomeExecutionState()

    data class Success(
        val deviceId: String,
        val deviceName: String,
        val actionType: String,
        val targetState: String,
        val latencyMs: Long,
        val route: String,
        val speechFeedback: String,
        val completedAt: Long = System.currentTimeMillis()
    ) : SmartHomeExecutionState()

    data class FallbackTriggered(
        val deviceId: String,
        val deviceName: String,
        val originalRoute: String,
        val fallbackRoute: String,
        val reasonMM: String,
        val latencyMs: Long,
        val speechFeedback: String,
        val triggeredAt: Long = System.currentTimeMillis()
    ) : SmartHomeExecutionState()

    data class Failed(
        val deviceId: String,
        val deviceName: String,
        val actionType: String,
        val errorReasonMM: String,
        val latencyMs: Long,
        val canRetry: Boolean = true,
        val failedAt: Long = System.currentTimeMillis()
    ) : SmartHomeExecutionState()
}

data class ProtocolStrategyResult(
    val isSuccess: Boolean,
    val latencyMs: Long,
    val executionRoute: String,
    val userFeedbackMM: String,
    val requiresFallback: Boolean = false,
    val fallbackReasonMM: String = ""
)

data class SmartHomeExecutionResult(
    val isSuccess: Boolean,
    val deviceId: String,
    val deviceName: String,
    val actionType: String,
    val targetState: String,
    val speechFeedback: String,
    val executionRoute: String // DIRECT_MATTER_LOCAL, GOOGLE_HOMEGRAPH_API, GOOGLE_ASSISTANT_FALLBACK, OFFLINE_ROLLBACK
)

