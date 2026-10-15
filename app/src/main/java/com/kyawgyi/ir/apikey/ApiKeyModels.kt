package com.kyawgyi.ir.apikey

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Tiers of managed API keys to support free quota preference and cost-optimization.
 */
enum class ApiKeyTier(val displayName: String, val burmeseLabel: String) {
  FREE_TIER("Free Quota Tier", "အခမဲ့ ခွဲတမ်းသော့ (Default)"),
  PRO_TIER("Pro / Paid Tier", "ပရို အဆင့်မြင့်သော့"),
  BACKUP_TIER("Emergency Backup", "အရေးပေါ် အရန်သော့")
}

/**
 * Health and operational status of a key.
 */
enum class ApiKeyStatus(val displayName: String, val burmeseLabel: String) {
  HEALTHY_ACTIVE("Active & Healthy", "ပုံမှန် အသုံးပြုနေသည်"),
  APPROACHING_QUOTA("Approaching Quota Warning", "ခွဲတမ်းကုန်လုနီးပါး သတိပေးချက်"),
  RATE_LIMITED_429("Rate Limited (429)", "နှုန်းကန့်သတ်မိထားသည် (429)"),
  QUOTA_EXHAUSTED("Quota Exhausted (403)", "ခွဲတမ်း ကုန်ဆုံးသွားသည်"),
  COOLING_DOWN("Cooling Down / Awaiting Reset", "ခွဲတမ်းပြန်သတ်မှတ်ချိန် စောင့်ဆိုင်းနေသည်"),
  DISABLED("Disabled", "ပိတ်ထားသည်")
}

/**
 * Managed API key definition with quota tracking and failover parameters.
 */
@JsonClass(generateAdapter = true)
data class ManagedApiKey(
  @Json(name = "id") val id: String,
  @Json(name = "label") val label: String,
  @Json(name = "maskedKey") val maskedKey: String, // e.g. "AIzaSy...7X9k" for UI display
  @Json(name = "tier") val tier: ApiKeyTier = ApiKeyTier.FREE_TIER,
  @Json(name = "maxQuota") val maxQuota: Int = 1000,
  @Json(name = "usedQuota") val usedQuota: Int = 0,
  @Json(name = "warningThreshold") val warningThreshold: Float = 0.80f, // 80% usage triggers warning
  @Json(name = "criticalThreshold") val criticalThreshold: Float = 0.95f, // 95% usage triggers proactive rotation
  @Json(name = "status") val status: ApiKeyStatus = ApiKeyStatus.HEALTHY_ACTIVE,
  @Json(name = "isDefaultFreeTier") val isDefaultFreeTier: Boolean = false,
  @Json(name = "consecutiveErrors") val consecutiveErrors: Int = 0,
  @Json(name = "lastUsedTimestamp") val lastUsedTimestamp: Long = 0L,
  @Json(name = "lastErrorTimestamp") val lastErrorTimestamp: Long = 0L,
  @Json(name = "cooldownUntilTimestamp") val cooldownUntilTimestamp: Long = 0L,
  @Json(name = "quotaResetTimestamp") val quotaResetTimestamp: Long = 0L
) {
  val usagePercentage: Float
    get() = if (maxQuota > 0) (usedQuota.toFloat() / maxQuota.toFloat()).coerceIn(0f, 1f) else 0f

  val remainingQuota: Int
    get() = (maxQuota - usedQuota).coerceAtLeast(0)

  val isApproachingQuota: Boolean
    get() = usagePercentage >= warningThreshold && usagePercentage < 1f

  val isExhausted: Boolean
    get() = usagePercentage >= 1f || status == ApiKeyStatus.QUOTA_EXHAUSTED

  val isCoolingDown: Boolean
    get() = System.currentTimeMillis() < cooldownUntilTimestamp

  val canBeUsed: Boolean
    get() = status != ApiKeyStatus.DISABLED &&
      !isExhausted &&
      !isCoolingDown &&
      status != ApiKeyStatus.RATE_LIMITED_429
}

/**
 * Quota pre-warning notification payload.
 */
data class ApiKeyQuotaAlert(
  val keyId: String,
  val keyLabel: String,
  val currentUsage: Int,
  val maxQuota: Int,
  val usagePercentage: Float,
  val messageBurmese: String,
  val messageEnglish: String,
  val timestamp: Long = System.currentTimeMillis(),
  val severity: AlertSeverity = AlertSeverity.WARNING
)

enum class AlertSeverity {
  INFO,
  WARNING,
  CRITICAL
}

/**
 * Audit log of an API key rotation or failover.
 */
data class ApiKeyRotationLog(
  val timestamp: Long = System.currentTimeMillis(),
  val fromKeyId: String?,
  val toKeyId: String,
  val reason: String,
  val isAutomatic: Boolean = true
)


