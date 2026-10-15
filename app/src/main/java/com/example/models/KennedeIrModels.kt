package com.example.models

enum class KennedeButton(val key: String, val label: String, val burmeseLabel: String) {
  POWER("power", "POWER", "ပါဝါ (ဖွင့်/ပိတ်)"),
  VOLUME_UP("volume_up", "VOL +", "အသံတိုးမြှင့်"),
  VOLUME_DOWN("volume_down", "VOL -", "အသံလျှော့ချ"),
  MUTE("mute", "MUTE", "အသံပိတ်"),
  MODE("mode", "MODE", "လုပ်ဆောင်ချက် မုဒ်"),
  SPEED("speed", "SPEED", "လေအရှိန်"),
  OSCILLATION("oscillation", "OSCILLATION", "ဘယ်/ညာ လှည့်"),
  LIGHT("light", "LIGHT", "မီးရောင်"),
  TIMER("timer", "TIMER", "အချိန်မှတ်စနစ်"),
  CENTER("center", "CENTER", "အလယ်ခလုတ်")
}

data class IrButtonProfile(
  val captured: Boolean = false,
  val protocol: String? = null,
  val address: String? = null,
  val command: String? = null,
  val rawData: String? = null,
  val bits: Int? = null,
  val rawTiming: List<Int> = emptyList(),
  val repeatCount: Int = 0
)

data class KennedeIrDatabase(
  val device: String = "KENNEDE KN-5996HRB",
  val carrierFrequency: Int = 38000,
  val carrier_frequency_assumed: Int = 38000,
  val buttons: Map<String, IrButtonProfile> = emptyMap()
)

data class Esp32Status(
  val device: String = "KENNEDE KN-5996HRB",
  val esp32: Boolean = false,
  val receiver: Boolean = false,
  val transmitter: Boolean = false,
  val carrierFrequency: Int = 38000,
  val learning: Boolean = false,
  val learningTarget: String = "",
  val ip: String = "",
  val rssi: Int = 0,
  val learned: Map<String, Boolean> = emptyMap()
)

data class IrEventLog(
  val timestamp: String,
  val device: String = "KENNEDE KN-5996HRB",
  val button: String,
  val action: String, // "send", "learn", "export", "import"
  val protocol: String? = null,
  val rawData: String? = null,
  val success: Boolean,
  val message: String? = null,
  val error: String? = null
)

/**
 * Data model for Item 6: Smart Fan Automation, Scheduling & EcoFlow Power Linkage.
 */
data class KennedeFanAutomationState(
  val sleepTimerMinutes: Int = 0, // 0 = off, 15, 30, 45, 60, 120
  val sleepTimerRemainingSeconds: Int = 0,
  val sleepTimerActive: Boolean = false,
  val ecoFlowPowerLinkEnabled: Boolean = true,
  val lowBatteryThreshold: Int = 20, // EcoFlow battery percentage threshold
  val autoTurnOffLightOnLowBattery: Boolean = true,
  val autoReduceSpeedOnLowBattery: Boolean = true,
  val quietNightModeActive: Boolean = false,
  val batchCalibrationActive: Boolean = false,
  val batchCalibrationStep: Int = 0, // 0..10
  val batchCalibrationTotal: Int = 10,
  val batchCalibrationTarget: KennedeButton? = null,
  val lastAutomationEvent: String? = null,
  val lastAutomationTimestamp: Long = 0L
)
