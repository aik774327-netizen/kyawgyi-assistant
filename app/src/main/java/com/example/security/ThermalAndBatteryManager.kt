package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Thermal Operating States
 */
enum class ThermalState {
    NORMAL,
    WARM,
    HOT,
    CRITICAL
}

/**
 * Aggregate Battery & Resource Consumption Metrics (No raw voice data)
 */
data class BatteryHealthMetrics(
    val batteryStartLevel: Int = -1,
    val currentBatteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isDeviceIdleMode: Boolean = false,
    val isScreenOn: Boolean = true,
    val thermalState: ThermalState = ThermalState.NORMAL,
    val totalWakeLockDurationMs: Long = 0L,
    val wakeWordActiveDurationMs: Long = 0L,
    val sttActiveDurationMs: Long = 0L,
    val sessionDurationMs: Long = 0L
)

/**
 * ThermalAndBatteryManager (Stage 16 Production Hardening)
 *
 * Monitors system power state, device idle/doze status, thermal headroom, and enforces
 * strictly bounded WakeLock lifetimes (max 60s) to prevent battery drain.
 */
class ThermalAndBatteryManager(
    private val context: Context,
    private val onThermalStateChanged: (ThermalState) -> Unit = {},
    private val onPowerSaveChanged: (Boolean) -> Unit = {}
) {

    companion object {
        private const val TAG = "ThermalBatteryMgr"
        private const val MAX_WAKELOCK_TIMEOUT_MS = 60_000L // Strictly 60s maximum

        @Volatile
        private var instance: ThermalAndBatteryManager? = null

        fun getInstance(context: Context): ThermalAndBatteryManager {
            return instance ?: synchronized(this) {
                instance ?: ThermalAndBatteryManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _thermalState = MutableStateFlow(ThermalState.NORMAL)
    val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _isDeviceIdleMode = MutableStateFlow(false)
    val isDeviceIdleMode: StateFlow<Boolean> = _isDeviceIdleMode.asStateFlow()

    private var initialBatteryLevel: Int = -1
    private val totalWakeLockDurationMs = AtomicLong(0L)
    private val wakeWordActiveDurationMs = AtomicLong(0L)
    private val sttActiveDurationMs = AtomicLong(0L)
    private val sessionStartTimestamp = System.currentTimeMillis()

    private var thermalListener: Any? = null
    private var powerBroadcastReceiver: BroadcastReceiver? = null

    init {
        initializeThermalMonitoring()
        initializeBatteryMonitoring()
    }

    private fun initializeThermalMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    val mappedState = when (status) {
                        PowerManager.THERMAL_STATUS_NONE,
                        PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NORMAL
                        PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARM
                        PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.HOT
                        PowerManager.THERMAL_STATUS_CRITICAL,
                        PowerManager.THERMAL_STATUS_EMERGENCY,
                        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                        else -> ThermalState.NORMAL
                    }
                    _thermalState.value = mappedState
                    onThermalStateChanged(mappedState)
                    Log.i(TAG, "Android Thermal status changed: $status -> $mappedState")
                }
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
                thermalListener = listener
            } catch (e: Throwable) {
                Log.w(TAG, "Thermal status listener unavailable on this device", e)
            }
        }
    }

    private fun initializeBatteryMonitoring() {
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        powerBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.action?.let { action ->
                    when (action) {
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val isSave = powerManager?.isPowerSaveMode == true
                            _isPowerSaveMode.value = isSave
                            onPowerSaveChanged(isSave)
                            Log.i(TAG, "Power save mode changed: $isSave")
                        }
                        PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                            val isIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                powerManager?.isDeviceIdleMode == true
                            } else false
                            _isDeviceIdleMode.value = isIdle
                            Log.i(TAG, "Device idle / Doze state changed: $isIdle")
                        }
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            if (initialBatteryLevel == -1 && level > 0) {
                                initialBatteryLevel = level
                            }
                        }
                    }
                }
            }
        }

        try {
            context.registerReceiver(powerBroadcastReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register power broadcast receiver", e)
        }

        // Initialize state
        _isPowerSaveMode.value = powerManager?.isPowerSaveMode == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            _isDeviceIdleMode.value = powerManager?.isDeviceIdleMode == true
        }
    }

    /**
     * Executes an operation with a bounded WakeLock (strictly released in finally {})
     */
    fun <T> runWithBoundedWakeLock(tag: String, maxDurationMs: Long = MAX_WAKELOCK_TIMEOUT_MS, block: () -> T): T {
        val boundedDuration = minOf(maxDurationMs, MAX_WAKELOCK_TIMEOUT_MS)
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KyawGyi:$tag")
        val start = System.currentTimeMillis()

        try {
            wakeLock?.acquire(boundedDuration)
            return block()
        } finally {
            val elapsed = System.currentTimeMillis() - start
            totalWakeLockDurationMs.addAndGet(elapsed)
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing wakeLock: $tag", e)
            }
        }
    }

    fun recordWakeWordActiveTime(durationMs: Long) {
        wakeWordActiveDurationMs.addAndGet(durationMs)
    }

    fun recordSttActiveTime(durationMs: Long) {
        sttActiveDurationMs.addAndGet(durationMs)
    }

    /**
     * Get aggregate privacy-safe battery and resource health metrics
     */
    fun getMetrics(): BatteryHealthMetrics {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val currentLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryHealthMetrics(
            batteryStartLevel = initialBatteryLevel,
            currentBatteryLevel = currentLevel,
            isCharging = isCharging,
            isPowerSaveMode = _isPowerSaveMode.value,
            isDeviceIdleMode = _isDeviceIdleMode.value,
            isScreenOn = powerManager?.isInteractive == true,
            thermalState = _thermalState.value,
            totalWakeLockDurationMs = totalWakeLockDurationMs.get(),
            wakeWordActiveDurationMs = wakeWordActiveDurationMs.get(),
            sttActiveDurationMs = sttActiveDurationMs.get(),
            sessionDurationMs = System.currentTimeMillis() - sessionStartTimestamp
        )
    }

    fun destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null && powerManager != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing thermal listener", e)
            }
        }
        powerBroadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering power receiver", e)
            }
        }
    }
}
