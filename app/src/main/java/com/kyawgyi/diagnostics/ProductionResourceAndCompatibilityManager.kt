package com.kyawgyi.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.kyawgyi.pipeline.AndroidCapabilityMatrix
import com.kyawgyi.voice.ModelManager
import java.util.concurrent.ConcurrentHashMap

/**
 * ITEM 23 — Production Resource & Concurrency Manager
 *
 * Governs CPU, RAM, Battery, Thermal state, and mutual exclusion of heavy AI tasks
 * so STT, TTS, OCR, and Gemini cloud requests do not collide or exhaust device memory.
 */
class KyawGyiResourceManager(private val context: Context) {

    companion object {
        private const val TAG = "KyawGyiResourceMgr"
        const val MIN_SAFE_AVAILABLE_RAM_MB = 120L
        const val CRITICAL_BATTERY_PERCENT = 5

        @Volatile
        private var instance: KyawGyiResourceManager? = null

        fun getInstance(context: Context): KyawGyiResourceManager {
            return instance ?: synchronized(this) {
                instance ?: KyawGyiResourceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class AiWorkloadType {
        STT_RECOGNITION,
        TTS_SYNTHESIS,
        SCREEN_OCR,
        GEMINI_CLOUD_REASONING
    }

    data class ResourceSnapshot(
        val availableRamMb: Long,
        val totalRamMb: Long,
        val isLowMemory: Boolean,
        val batteryPercent: Int,
        val isPowerSaveMode: Boolean,
        val thermalStatus: Int,
        val isThermalThrottling: Boolean,
        val activeWorkloads: Set<AiWorkloadType>
    )

    data class WorkloadAcquisitionResult(
        val granted: Boolean,
        val workloadType: AiWorkloadType,
        val rejectionReason: String? = null,
        val snapshot: ResourceSnapshot
    )

    private val activeWorkloads = ConcurrentHashMap.newKeySet<AiWorkloadType>()

    fun captureResourceSnapshot(): ResourceSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val availMb = if (memInfo.availMem > 0) memInfo.availMem / (1024 * 1024) else 512L
        val totalMb = if (memInfo.totalMem > 0) memInfo.totalMem / (1024 * 1024) else 2048L

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 80

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = pm?.isPowerSaveMode == true
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
            pm.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        val isThrottling = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermal >= PowerManager.THERMAL_STATUS_SEVERE

        return ResourceSnapshot(
            availableRamMb = availMb,
            totalRamMb = totalMb,
            isLowMemory = memInfo.lowMemory || availMb < MIN_SAFE_AVAILABLE_RAM_MB,
            batteryPercent = batteryPct,
            isPowerSaveMode = powerSave,
            thermalStatus = thermal,
            isThermalThrottling = isThrottling,
            activeWorkloads = activeWorkloads.toSet()
        )
    }

    /**
     * Requests a slot to run a heavy AI workload.
     * Prevents STT + TTS acoustic collision and blocks heavy OCR/Cloud tasks during severe thermal or low-RAM states.
     */
    @Synchronized
    fun acquireWorkloadSlot(workload: AiWorkloadType): WorkloadAcquisitionResult {
        val snapshot = captureResourceSnapshot()

        // 1. Acoustic mutual exclusion: STT and TTS must not run simultaneously
        if (workload == AiWorkloadType.STT_RECOGNITION && activeWorkloads.contains(AiWorkloadType.TTS_SYNTHESIS)) {
            return WorkloadAcquisitionResult(
                granted = false,
                workloadType = workload,
                rejectionReason = "TTS synthesis is currently active; STT blocked to prevent acoustic self-trigger",
                snapshot = snapshot
            )
        }
        if (workload == AiWorkloadType.TTS_SYNTHESIS && activeWorkloads.contains(AiWorkloadType.STT_RECOGNITION)) {
            // Preempt STT when TTS starts speaking
            activeWorkloads.remove(AiWorkloadType.STT_RECOGNITION)
        }

        // 2. Heavy workload concurrency cap (do not run OCR + Gemini + STT simultaneously)
        if ((workload == AiWorkloadType.SCREEN_OCR || workload == AiWorkloadType.GEMINI_CLOUD_REASONING) &&
            ( snapshot.isLowMemory || snapshot.isThermalThrottling )
        ) {
            return WorkloadAcquisitionResult(
                granted = false,
                workloadType = workload,
                rejectionReason = "Resource protection active (LowMemory=${snapshot.isLowMemory}, ThermalThrottling=${snapshot.isThermalThrottling})",
                snapshot = snapshot
            )
        }

        activeWorkloads.add(workload)
        Log.d(TAG, "Workload slot acquired: $workload (active=${activeWorkloads})")
        return WorkloadAcquisitionResult(
            granted = true,
            workloadType = workload,
            snapshot = captureResourceSnapshot()
        )
    }

    @Synchronized
    fun releaseWorkloadSlot(workload: AiWorkloadType) {
        activeWorkloads.remove(workload)
    }

    @Synchronized
    fun clearAllWorkloads() {
        activeWorkloads.clear()
    }
}

/**
 * ITEM 27 — Device & Android Compatibility Matrix Evaluator
 *
 * Evaluates real runtime conditions across Android versions, screen/lock state,
 * audio route (Speaker vs Bluetooth Headset), network, battery/thermal, permissions,
 * Accessibility service, and offline model presence.
 */
class DeviceCompatibilityEvaluator(private val context: Context) {

    data class DeviceCompatibilityMatrixReport(
        val androidSdkInt: Int,
        val androidRelease: String,
        val deviceManufacturerAndModel: String,
        val isScreenInteractive: Boolean,
        val isDeviceLocked: Boolean,
        val isBluetoothAudioRouted: Boolean,
        val activeAudioOutputRoute: String,
        val isNetworkOnline: Boolean,
        val isMeteredOrSlowNetwork: Boolean,
        val isBatteryLow: Boolean,
        val isThermalThrottled: Boolean,
        val hasRecordAudioPermission: Boolean,
        val hasCallPhonePermission: Boolean,
        val hasSendSmsPermission: Boolean,
        val hasOverlayPermission: Boolean,
        val isAccessibilityEnabled: Boolean,
        val isOfflineModelInstalled: Boolean,
        val foregroundMicAllowedInCurrentState: Boolean
    )

    fun evaluate(): DeviceCompatibilityMatrixReport {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked ?: false

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val outputDevices = try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val hasBtHeadset = outputDevices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val routeLabel = if (hasBtHeadset) "BLUETOOTH_HEADSET" else "BUILTIN_SPEAKER"

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val isOnline = caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val isSlowNet = isOnline && downstreamKbps in 1..499

        val resourceSnap = KyawGyiResourceManager.getInstance(context).captureResourceSnapshot()

        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        val hasAccessibility = AndroidCapabilityMatrix.isAccessibilityServiceEnabled(context)

        val modelManager = ModelManager(context)
        val hasOfflineModel = modelManager.isSherpaOnnxModelAvailable() || modelManager.isVoskModelAvailable()

        // Android 14+ (API 34+) restricts starting FOREGROUND_SERVICE_TYPE_MICROPHONE from background
        // unless RECORD_AUDIO is granted and app is in foreground or an exempt state
        val fgMicAllowed = hasMic && (isInteractive || hasAccessibility)

        return DeviceCompatibilityMatrixReport(
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            deviceManufacturerAndModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            isScreenInteractive = isInteractive,
            isDeviceLocked = isLocked,
            isBluetoothAudioRouted = hasBtHeadset,
            activeAudioOutputRoute = routeLabel,
            isNetworkOnline = isOnline,
            isMeteredOrSlowNetwork = isSlowNet,
            isBatteryLow = resourceSnap.batteryPercent <= 15,
            isThermalThrottled = resourceSnap.isThermalThrottling,
            hasRecordAudioPermission = hasMic,
            hasCallPhonePermission = hasCall,
            hasSendSmsPermission = hasSms,
            hasOverlayPermission = hasOverlay,
            isAccessibilityEnabled = hasAccessibility,
            isOfflineModelInstalled = hasOfflineModel,
            foregroundMicAllowedInCurrentState = fgMicAllowed
        )
    }
}
