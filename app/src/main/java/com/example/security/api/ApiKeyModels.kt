package com.example.security.api

import org.json.JSONObject

enum class ApiKeyTier {
    FREE_TIER,  // Standard Gemini free tier (15 RPM, 1500 RPD)
    PAID_TIER,  // Pay-as-you-go or higher quota tier
    CUSTOM      // User-provided private key
}

enum class ApiKeyHealthStatus {
    HEALTHY,            // Ready and working normally (< 80% quota)
    NEAR_LIMIT_80,      // Approaching quota limit (>= 80% daily quota)
    RATE_LIMITED_RPM,   // Hit per-minute rate limit (auto-cooldown 60s)
    EXHAUSTED_429,      // Quota exhausted (429 ResourceExhausted)
    IN_COOLDOWN,        // In temporary backoff cooldown window
    ERROR               // Invalid key or network failure
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class ManagedApiKey(
    val id: String,
    val rawKey: String,
    val label: String,
    val tier: ApiKeyTier = ApiKeyTier.FREE_TIER,
    val status: ApiKeyHealthStatus = ApiKeyHealthStatus.HEALTHY,
    val dailyLimit: Int = 1500,
    val requestsToday: Int = 0,
    val minuteLimit: Int = 15,
    val requestsThisMinute: Int = 0,
    val lastMinuteWindow: Long = System.currentTimeMillis() / 60000,
    val lastUsedTimestamp: Long = 0L,
    val cooldownUntilTimestamp: Long = 0L,
    val consecutive429Count: Int = 0,
    val totalCalls: Int = 0,
    val totalFailures: Int = 0,
    val lastError: String? = null
) {
    val maskedKey: String
        get() = SecureKeyVault.maskKey(rawKey)

    val dailyUsagePercent: Int
        get() = if (dailyLimit > 0) ((requestsToday.toFloat() / dailyLimit.toFloat()) * 100).toInt().coerceIn(0, 100) else 0

    val isAvailable: Boolean
        get() {
            if (rawKey.isBlank() || rawKey == "MY_GEMINI_API_KEY") return false
            val now = System.currentTimeMillis()
            if (now < cooldownUntilTimestamp) return false
            if (status == ApiKeyHealthStatus.EXHAUSTED_429 && now < cooldownUntilTimestamp) return false
            return true
        }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("rawKey", rawKey)
        json.put("label", label)
        json.put("tier", tier.name)
        json.put("status", status.name)
        json.put("dailyLimit", dailyLimit)
        json.put("requestsToday", requestsToday)
        json.put("minuteLimit", minuteLimit)
        json.put("requestsThisMinute", requestsThisMinute)
        json.put("lastMinuteWindow", lastMinuteWindow)
        json.put("lastUsedTimestamp", lastUsedTimestamp)
        json.put("cooldownUntilTimestamp", cooldownUntilTimestamp)
        json.put("consecutive429Count", consecutive429Count)
        json.put("totalCalls", totalCalls)
        json.put("totalFailures", totalFailures)
        json.put("lastError", lastError ?: "")
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ManagedApiKey {
            return ManagedApiKey(
                id = json.optString("id", "key_${System.currentTimeMillis()}"),
                rawKey = json.optString("rawKey", ""),
                label = json.optString("label", "API Key"),
                tier = runCatching { ApiKeyTier.valueOf(json.optString("tier", ApiKeyTier.FREE_TIER.name)) }.getOrDefault(ApiKeyTier.FREE_TIER),
                status = runCatching { ApiKeyHealthStatus.valueOf(json.optString("status", ApiKeyHealthStatus.HEALTHY.name)) }.getOrDefault(ApiKeyHealthStatus.HEALTHY),
                dailyLimit = json.optInt("dailyLimit", 1500),
                requestsToday = json.optInt("requestsToday", 0),
                minuteLimit = json.optInt("minuteLimit", 15),
                requestsThisMinute = json.optInt("requestsThisMinute", 0),
                lastMinuteWindow = json.optLong("lastMinuteWindow", System.currentTimeMillis() / 60000),
                lastUsedTimestamp = json.optLong("lastUsedTimestamp", 0L),
                cooldownUntilTimestamp = json.optLong("cooldownUntilTimestamp", 0L),
                consecutive429Count = json.optInt("consecutive429Count", 0),
                totalCalls = json.optInt("totalCalls", 0),
                totalFailures = json.optInt("totalFailures", 0),
                lastError = json.optString("lastError").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class ApiQuotaAlert(
    val id: String = "alert_${System.currentTimeMillis()}_${(100..999).random()}",
    val timestamp: Long = System.currentTimeMillis(),
    val keyLabel: String,
    val message: String,
    val severity: AlertSeverity
)

data class KeyRotationRecord(
    val id: String = "rot_${System.currentTimeMillis()}_${(100..999).random()}",
    val timestamp: Long = System.currentTimeMillis(),
    val fromKeyLabel: String,
    val toKeyLabel: String,
    val reason: String
)

data class ApiKeyPoolOverview(
    val activeKeyLabel: String = "None",
    val activeKeyMasked: String = "Not Configured",
    val activeKeyTier: ApiKeyTier = ApiKeyTier.FREE_TIER,
    val activeKeyStatus: ApiKeyHealthStatus = ApiKeyHealthStatus.HEALTHY,
    val activeKeyUsagePercent: Int = 0,
    val totalKeys: Int = 0,
    val availableKeysCount: Int = 0,
    val isAutoRevertToFreeEnabled: Boolean = true,
    val totalRotationsCount: Int = 0,
    val alerts: List<ApiQuotaAlert> = emptyList(),
    val recentRotations: List<KeyRotationRecord> = emptyList(),
    val keysList: List<ManagedApiKey> = emptyList()
)
