package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ErrorCategory(
  val burmeseTitle: String,
  val explanationBurmese: String,
  val badgeColorHex: Long
) {
  TECHNICAL(
    "နည်းပညာပိုင်း (Technical)",
    "စနစ် Code၊ API Protocol၊ ချိတ်ဆက်မှုပြတ်တောက်ခြင်း သို့မဟုတ် Hardware Driver ချွတ်ယွင်းမှုကြောင့် ဖြစ်ပွားသော ချို့ယွင်းချက်",
    0xFFEF4444
  ),
  RESOURCE(
    "အရင်းအမြစ် (Resource)",
    "လိုအပ်သော IoT Hardware၊ Bluetooth/WiFi ချိတ်ဆက်မှု၊ API Key သို့မဟုတ် System Permission မရှိသေးခြင်းကြောင့် ဖြစ်ပွားသော ချို့ယွင်းချက်",
    0xFFF59E0B
  ),
  REAL_WORLD_MISMATCH(
    "လက်တွေ့မကိုက်ညီမှု (Real-world Mismatch)",
    "တောင်းဆိုထားသော ပစ္စည်း၊ အဆက်အသွယ် သို့မဟုတ် အချက်အလက်သည် လက်တွေ့လောကတွင် တည်ရှိမနေခြင်း သို့မဟုတ် စနစ်တွင် မတွေ့ရှိရခြင်း",
    0xFF3B82F6
  ),
  MOCK_DETECTED(
    "အတုအယောင်/ဟန်ပြ (Mock Detected)",
    "စနစ်သည် အတုအယောင် လုပ်ကြံဒေတာများဖြင့် ဟန်ပြလုပ်ဆောင်ခြင်းကို လုံးဝ လက်မခံပါ (စစ်မှန်သောချိတ်ဆက်မှုသာ လက်ခံသည်)",
    0xFFA855F7
  )
}

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val actionKey: String,
  val rawCommand: String,
  val category: String, // from ErrorCategory.name
  val categoryBurmese: String,
  val categoryExplanation: String = "",
  val reasonDetail: String,
  val failedMethod: String = "",
  val alternativeAdvice: String,
  val retryCount: Int = 1,
  val isResolved: Boolean = false
)

@Entity(tableName = "learned_memories")
data class MemoryEntity(
  @PrimaryKey val id: String,
  val aliasOrKey: String,
  val targetValue: String,
  val category: String, // "ALIAS", "PREFERENCE", "SEMANTIC"
  val confidence: Float = 0.95f,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_shortcuts")
data class ShortcutEntity(
  @PrimaryKey val id: String,
  val title: String,
  val voiceCommand: String,
  val actionTarget: String,
  val colorHex: Long = 0xFF10B981
)

@Entity(tableName = "ecoflow_config")
data class EcoFlowConfigEntity(
  @PrimaryKey val id: Int = 1,
  val accessKey: String = "",
  val secretKey: String = "",
  val deviceSn: String = "DC2M-SAMPLE-2048",
  val hostUrl: String = "https://api.ecoflow.com",
  val useHardwareBle: Boolean = false
)

/**
 * Item 1: Room Database persistence for KENNEDE KN-5996HRB Smart Fan Automation Settings.
 * Preserves user-configured low-battery thresholds, sleep timer preferences, and feature toggles.
 */
@Entity(tableName = "fan_automation_config")
data class FanAutomationConfigEntity(
  @PrimaryKey val id: Int = 1,
  val ecoFlowPowerLinkEnabled: Boolean = true,
  val lowBatteryThreshold: Int = 20,
  val autoTurnOffLightOnLowBattery: Boolean = true,
  val autoReduceSpeedOnLowBattery: Boolean = true,
  val quietNightModeActive: Boolean = false,
  val defaultSleepTimerMinutes: Int = 30,
  val updatedAt: Long = System.currentTimeMillis()
)
