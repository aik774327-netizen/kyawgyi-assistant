package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BatteryOptimizationMode(
    val titleMM: String,
    val titleEN: String,
    val descriptionMM: String,
    val samplingIntervalMs: Long,
    val vadSensitivityBoost: Float
) {
    BALANCED(
        titleMM = "အသင့်အတင့် စွမ်းအင်သုံးစွဲမှု (Balanced)",
        titleEN = "Balanced Adaptive Mode",
        descriptionMM = "စွမ်းအင်ချွေတာမှုနှင့် အသံတုံ့ပြန်မှု မြန်ဆန်မှုကို မျှတစွာ ချိန်ညှိထားပါသည်။ (Standard Recommended)",
        samplingIntervalMs = 2000L,
        vadSensitivityBoost = 1.0f
    ),
    MAX_SAVER(
        titleMM = "အထူး ဘက်ထရီ ချွေတာမှု (Ultra Power Saver)",
        titleEN = "Ultra Battery Saver",
        descriptionMM = "ဘက်ထရီအားကုန်သက်သာစေရန် Audio Sampling ကို လျှော့ချပြီး CPU အသုံးပြုမှုကို အနည်းဆုံးဖြစ်စေပါသည်။",
        samplingIntervalMs = 4000L,
        vadSensitivityBoost = 0.7f
    ),
    PERFORMANCE(
        titleMM = "အမြင့်ဆုံး စွမ်းဆောင်ရည် (High Responsiveness)",
        titleEN = "High Performance Mode",
        descriptionMM = "Wake Word နှင့် အသံအမိန့်များကို အချိန်မဆိုင်း တုံ့ပြန်နိုင်ရန် အမြဲ အဆင်သင့် နားထောင်ပေးပါသည်။",
        samplingIntervalMs = 1000L,
        vadSensitivityBoost = 1.3f
    )
}

object KyawGyiBatteryOptimizer {

    private const val PREFS_NAME = "kyawgyi_battery_prefs"
    private const val KEY_MODE = "battery_mode"
    private const val KEY_AUTO_PAUSE_ON_BATTERY_LOW = "auto_pause_on_battery_low"

    private val _batteryMode = MutableStateFlow(BatteryOptimizationMode.BALANCED)
    val batteryMode: StateFlow<BatteryOptimizationMode> = _batteryMode.asStateFlow()

    private val _autoPauseOnLowBattery = MutableStateFlow(true)
    val autoPauseOnLowBattery: StateFlow<Boolean> = _autoPauseOnLowBattery.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedModeName = prefs.getString(KEY_MODE, BatteryOptimizationMode.BALANCED.name)
        _batteryMode.value = try {
            BatteryOptimizationMode.valueOf(savedModeName ?: BatteryOptimizationMode.BALANCED.name)
        } catch (e: Exception) {
            BatteryOptimizationMode.BALANCED
        }
        _autoPauseOnLowBattery.value = prefs.getBoolean(KEY_AUTO_PAUSE_ON_BATTERY_LOW, true)
    }

    fun setBatteryMode(context: Context, mode: BatteryOptimizationMode) {
        _batteryMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setAutoPauseOnLowBattery(context: Context, enabled: Boolean) {
        _autoPauseOnLowBattery.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_PAUSE_ON_BATTERY_LOW, enabled).apply()
    }

    fun isSystemPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode == true
    }
}
