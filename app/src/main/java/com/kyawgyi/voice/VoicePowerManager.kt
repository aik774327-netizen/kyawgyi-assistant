package com.kyawgyi.voice

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Adaptive power and thermal operating profile for the KyawGyi Voice Pipeline.
 */
enum class VoicePowerProfile(
    val recommendedOnnxThreads: Int,
    val vadIdlePollIntervalMs: Long,
    val displayLabel: String
) {
    HIGH_PERFORMANCE(recommendedOnnxThreads = 4, vadIdlePollIntervalMs = 20L, displayLabel = "HIGH_PERF (4 Threads)"),
    BALANCED(recommendedOnnxThreads = 2, vadIdlePollIntervalMs = 30L, displayLabel = "BALANCED (2 Threads)"),
    BATTERY_SAVER(recommendedOnnxThreads = 1, vadIdlePollIntervalMs = 50L, displayLabel = "BATTERY_SAVER (1 Thread)"),
    THERMAL_THROTTLED(recommendedOnnxThreads = 1, vadIdlePollIntervalMs = 60L, displayLabel = "THERMAL_SAFE (1 Thread)")
}

/**
 * Snapshot of [VoicePowerManager] state.
 */
data class VoicePowerState(
    val profile: VoicePowerProfile = VoicePowerProfile.BALANCED,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = true,
    val isPowerSaveMode: Boolean = false,
    val thermalStatus: Int = 0
)

/**
 * VoicePowerManager (Requirements 10 — Battery & Thermal-Aware Voice Pipeline Governor)
 *
 * Monitors battery percentage, charging status, Android Power Saver mode, and device thermal
 * status (`PowerManager.getCurrentThermalStatus` on API 29+) to automatically scale ONNX Runtime
 * CPU thread counts (`1`, `2`, or `4` threads) and idle VAD cadence so continuous listening never
 * overheats the device or drains battery.
 */
class VoicePowerManager(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _state = MutableStateFlow(VoicePowerState())
    val state: StateFlow<VoicePowerState> = _state.asStateFlow()

    init {
        refreshPowerProfile()
    }

    /**
     * Queries OS battery & thermal status and updates [state] atomically.
     */
    fun refreshPowerProfile(): VoicePowerState {
        val pm = powerManager
        val isSaver = runCatching { pm?.isPowerSaveMode == true }.getOrDefault(false)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            runCatching { pm.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        } else {
            0
        }

        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 85) ?: 85
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val batteryPct = ((level * 100) / scale).coerceIn(0, 100)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val resolvedProfile = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                thermal >= PowerManager.THERMAL_STATUS_MODERATE -> VoicePowerProfile.THERMAL_THROTTLED
            isSaver || (!charging && batteryPct <= 20) -> VoicePowerProfile.BATTERY_SAVER
            charging && batteryPct >= 50 -> VoicePowerProfile.HIGH_PERFORMANCE
            else -> VoicePowerProfile.BALANCED
        }

        val updated = VoicePowerState(
            profile = resolvedProfile,
            batteryPercent = batteryPct,
            isCharging = charging,
            isPowerSaveMode = isSaver,
            thermalStatus = thermal
        )
        _state.update { updated }
        return updated
    }
}
