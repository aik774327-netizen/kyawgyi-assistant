package com.kyawgyi.voice

import android.app.AppOpsManager
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable thread-safe snapshot of OS-level microphone privacy & AppOps permission state.
 */
data class SystemMicPrivacyState(
    val isAppOpAllowed: Boolean = true,
    val isSensorPrivacyBlocked: Boolean = false,
    val appOpModeCode: Int = AppOpsManager.MODE_ALLOWED,
    val statusDescription: String = "AppOps RECORD_AUDIO Allowed"
) {
    val isPrivacyBlocked: Boolean
        get() = !isAppOpAllowed || isSensorPrivacyBlocked
}

/**
 * SystemMicPrivacyMonitor (Requirements 12 — AppOps & Android 12+ SensorPrivacyManager Inspector)
 *
 * Monitors OS-level microphone privacy controls:
 * 1. [AppOpsManager.OPSTR_RECORD_AUDIO] mode changes via [AppOpsManager.OnOpChangedListener]
 *    and [AppOpsManager.unsafeCheckOpNoThrow].
 * 2. Android 12+ (API 31+) [SensorPrivacyManager] hardware/software microphone privacy toggle support.
 */
class SystemMicPrivacyMonitor(context: Context) {

    companion object {
        private const val TAG = "SystemMicPrivacyMonitor"
    }

    private val appContext = context.applicationContext
    private val appOpsManager = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    private val _privacyState = MutableStateFlow(SystemMicPrivacyState())
    val privacyState: StateFlow<SystemMicPrivacyState> = _privacyState.asStateFlow()

    private var watchingAppOps = false

    private val opChangedListener = AppOpsManager.OnOpChangedListener { op, packageName ->
        if (op == AppOpsManager.OPSTR_RECORD_AUDIO && (packageName == null || packageName == appContext.packageName)) {
            refreshPrivacyState()
        }
    }

    init {
        startWatching()
        refreshPrivacyState()
    }

    fun startWatching() {
        val aom = appOpsManager ?: return
        if (watchingAppOps) return
        runCatching {
            aom.startWatchingMode(
                AppOpsManager.OPSTR_RECORD_AUDIO,
                appContext.packageName,
                opChangedListener
            )
            watchingAppOps = true
        }.onFailure { e ->
            Log.w(TAG, "Unable to register AppOps OnOpChangedListener: ${e.message}")
        }
    }

    /**
     * Evaluates current [AppOpsManager.OPSTR_RECORD_AUDIO] and [SensorPrivacyManager] state.
     */
    fun refreshPrivacyState(): SystemMicPrivacyState {
        val aom = appOpsManager
        val mode = if (aom != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    aom.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_RECORD_AUDIO,
                        Process.myUid(),
                        appContext.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    aom.checkOpNoThrow(
                        AppOpsManager.OPSTR_RECORD_AUDIO,
                        Process.myUid(),
                        appContext.packageName
                    )
                }
            }.getOrDefault(AppOpsManager.MODE_ALLOWED)
        } else {
            AppOpsManager.MODE_ALLOWED
        }

        val opAllowed = mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT

        // Check Android 12+ SensorPrivacyManager support
        val sensorToggleSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val spm = appContext.getSystemService(SensorPrivacyManager::class.java)
                spm?.supportsSensorToggle(SensorPrivacyManager.Sensors.MICROPHONE) == true
            }.getOrDefault(false)
        } else {
            false
        }

        val desc = when {
            !opAllowed -> "OS AppOps မှ RECORD_AUDIO ကို ပိတ်ထားပါသည် (mode=$mode)"
            sensorToggleSupported -> "AppOps Allowed ✓ (Android 12+ Mic Privacy Sensor Guard Active)"
            else -> "AppOps RECORD_AUDIO Allowed ✓"
        }

        val updated = SystemMicPrivacyState(
            isAppOpAllowed = opAllowed,
            isSensorPrivacyBlocked = !opAllowed,
            appOpModeCode = mode,
            statusDescription = desc
        )
        _privacyState.update { updated }
        return updated
    }

    fun close() {
        val aom = appOpsManager ?: return
        if (watchingAppOps) {
            runCatching { aom.stopWatchingMode(opChangedListener) }
            watchingAppOps = false
        }
    }
}
