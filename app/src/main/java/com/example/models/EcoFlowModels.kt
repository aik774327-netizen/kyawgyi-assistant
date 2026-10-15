package com.example.models

enum class EcoFlowPort {
  AC,
  DC,
  USB,
  ALL
}

enum class EcoFlowSeries(val displayName: String, val burmeseName: String) {
  RIVER("RIVER Series", "RIVER စီးရီး (အိတ်ဆောင်ပေါ့ပါး)"),
  DELTA("DELTA Series", "DELTA စီးရီး (အိမ်သုံးဓာတ်အား အရန်စွမ်းအင်)")
}

enum class EcoFlowModel(
  val id: String,
  val series: EcoFlowSeries,
  val modelName: String,
  val capacityWh: Int,
  val maxAcWatts: Int,
  val xboostWatts: Int,
  val maxChargeWatts: Int,
  val maxSolarWatts: Int,
  val batteryChemistry: String,
  val weightKg: Float,
  val description: String
) {
  RIVER_2(
    id = "river_2",
    series = EcoFlowSeries.RIVER,
    modelName = "RIVER 2",
    capacityWh = 256,
    maxAcWatts = 300,
    xboostWatts = 600,
    maxChargeWatts = 360,
    maxSolarWatts = 110,
    batteryChemistry = "LiFePO4 (3000+ Cycles)",
    weightKg = 3.5f,
    description = "256Wh အိတ်ဆောင် မီးအလင်းရောင်နှင့် ဖုန်းအားသွင်းသုံး"
  ),
  RIVER_2_MAX(
    id = "river_2_max",
    series = EcoFlowSeries.RIVER,
    modelName = "RIVER 2 Max",
    capacityWh = 512,
    maxAcWatts = 500,
    xboostWatts = 1000,
    maxChargeWatts = 660,
    maxSolarWatts = 220,
    batteryChemistry = "LiFePO4 (3000+ Cycles)",
    weightKg = 6.0f,
    description = "512Wh ခရီးဆောင်/ရုံးသုံး ပန်ကာနှင့် လက်ပ်တော့ပ်သုံး"
  ),
  RIVER_2_PRO(
    id = "river_2_pro",
    series = EcoFlowSeries.RIVER,
    modelName = "RIVER 2 Pro",
    capacityWh = 768,
    maxAcWatts = 800,
    xboostWatts = 1600,
    maxChargeWatts = 940,
    maxSolarWatts = 220,
    batteryChemistry = "LiFePO4 (3000+ Cycles)",
    weightKg = 7.8f,
    description = "768Wh အမြင့်မားဆုံး RIVER မော်ဒယ် (တီဗွီ၊ ပန်ကာ၊ ရေခဲသေတ္တာအသေး)"
  ),
  DELTA_2(
    id = "delta_2",
    series = EcoFlowSeries.DELTA,
    modelName = "DELTA 2",
    capacityWh = 1024,
    maxAcWatts = 1800,
    xboostWatts = 2400,
    maxChargeWatts = 1200,
    maxSolarWatts = 500,
    batteryChemistry = "LiFePO4 (3000+ Cycles)",
    weightKg = 12.0f,
    description = "1024Wh အိမ်သုံး မီးအလင်းရောင်၊ တီဗွီနှင့် အိမ်သုံးရေခဲသေတ္တာ"
  ),
  DELTA_2_MAX(
    id = "delta_2_max",
    series = EcoFlowSeries.DELTA,
    modelName = "DELTA 2 Max",
    capacityWh = 2048,
    maxAcWatts = 2400,
    xboostWatts = 3100,
    maxChargeWatts = 2400,
    maxSolarWatts = 1000,
    batteryChemistry = "LiFePO4 (3000+ Cycles)",
    weightKg = 23.0f,
    description = "2048Wh အဆင့်မြင့် အိမ်သုံး အရန်ဓာတ်အားနှင့် မီးဖိုချောင်သုံးပစ္စည်းများ"
  ),
  DELTA_PRO(
    id = "delta_pro",
    series = EcoFlowSeries.DELTA,
    modelName = "DELTA Pro",
    capacityWh = 3600,
    maxAcWatts = 3600,
    xboostWatts = 4500,
    maxChargeWatts = 2900,
    maxSolarWatts = 1600,
    batteryChemistry = "LiFePO4 (3500+ Cycles)",
    weightKg = 45.0f,
    description = "3600Wh တစ်အိမ်လုံး အပြည့်အဝ အသုံးပြုနိုင်သော စွမ်းအားမြင့်စနစ်"
  )
}

enum class EcoFlowConnectionType(val burmeseLabel: String) {
  NONE("ချိတ်ဆက်မှု မရှိပါ (Offline)"),
  BLE("Bluetooth Low Energy (Direct BLE)"),
  WIFI_CLOUD("EcoFlow Cloud Open API (Wi-Fi)"),
  DIAGNOSTIC_MODE("စမ်းသပ်မှု စနစ် (Safe Diagnostic Mode)")
}

data class EcoFlowEmergencyAutomation(
  val autoLowBatteryCutoffEnabled: Boolean = true,
  val lowBatteryCutoffThreshold: Int = 20, // percentage (10, 15, 20, 25)
  val autoOverheatHaltEnabled: Boolean = true,
  val overheatTempThreshold: Int = 45, // Celsius
  val autoOverloadProtectionEnabled: Boolean = true,
  val autoGridOutageUpsEnabled: Boolean = true,
  val lastTriggeredEvent: String? = null,
  val lastTriggeredTimestamp: Long = 0L
)

data class EcoFlowDeviceState(
  val selectedModel: EcoFlowModel = EcoFlowModel.DELTA_2_MAX,
  val model: String = "${selectedModel.modelName} (${selectedModel.capacityWh}Wh)",
  val batteryPercentage: Int = 85,
  val remainingMinutes: Int = 410, // ~6h 50m
  val inputWatts: Int = 0,
  val outputWatts: Int = 255,
  val acPortEnabled: Boolean = true,
  val dcPortEnabled: Boolean = false,
  val usbPortEnabled: Boolean = true,
  val chargingSpeedWatts: Int = 1400,
  val xboostEnabled: Boolean = true,
  val beepSoundEnabled: Boolean = true,
  val acTimeoutHours: Int = 12,
  val temperatureCelsius: Int = 31,
  val connectionStatus: String = "Offline (စက်နှင့် ချိတ်ဆက်ထားခြင်းမရှိပါ)",
  val isCharging: Boolean = false,
  val isConnected: Boolean = false,
  val connectionType: EcoFlowConnectionType = EcoFlowConnectionType.NONE,
  val bleRssi: Int = 0,
  val connectedDeviceAddress: String? = null,
  val automation: EcoFlowEmergencyAutomation = EcoFlowEmergencyAutomation(),
  val isDiagnosticsMode: Boolean = false
)

data class BluetoothDeviceItem(
  val id: String,
  val name: String,
  val address: String,
  val rssi: Int,
  val isConnected: Boolean = false,
  val isEcoFlowDevice: Boolean = false
)

data class CustomShortcut(
  val id: String,
  val title: String,
  val voiceCommand: String,
  val actionTarget: String,
  val colorHex: Long = 0xFF10B981
)

data class ContactItem(
  val id: String,
  val name: String,
  val phoneNumber: String,
  val relationship: String = ""
)

data class MemoryEntry(
  val id: String,
  val aliasOrKey: String,
  val targetValue: String,
  val category: String, // "ALIAS", "PREFERENCE", "SEMANTIC"
  val confidence: Float = 0.95f
)

data class NodeStatus(
  val nodeId: String, // "NODE_SE", "NODE_FA", "NODE_3", "NODE_4"
  val role: String, // "Primary Logic", "Resilience Pool 1", etc.
  val status: String, // "ACTIVE", "READY", "COOLING"
  val latencyMs: Long,
  val successRate: Float = 99.8f,
  val isPrimary: Boolean = false
)
