package com.example.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.util.Log
import com.example.security.ThermalAndBatteryManager
import com.example.security.ThermalState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Physical Hardware Test Result Status
 */
enum class PhysicalValidationStatus {
    PASS,
    FAIL,
    PENDING,
    NOT_TESTED,
    BLOCKED
}

/**
 * Noise Environment Profile
 */
enum class NoiseProfile(val targetDBSPL: String, val description: String) {
    QUIET_ROOM("<35 dB", "Quiet indoor bedroom / study room"),
    LIVING_ROOM("~50 dB", "Normal household conversation / TV background"),
    STREET_MARKET("~65 dB", "Busy street / market / tea shop background"),
    LOUD_VEHICLE("~70 dB", "Vehicle cabin / loud ambient traffic")
}

/**
 * Acoustic Distance Category
 */
enum class AcousticDistance(val distanceMeters: Float, val label: String) {
    NEAR_FIELD(1.0f, "1m (Near Field)"),
    MID_FIELD(3.0f, "3m (Mid Field)"),
    FAR_FIELD(5.0f, "5m (Far Field)")
}

/**
 * Real-time Hardware and System State Snapshot
 */
data class SystemTelemetrySnapshot(
    val batteryLevelPercent: Int = -1,
    val batteryTemperatureCelsius: Float = -1f,
    val isCharging: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val isDeviceIdleMode: Boolean = false,
    val isScreenOn: Boolean = true,
    val thermalState: ThermalState = ThermalState.NORMAL,
    val availableRamMb: Long = -1L,
    val totalRamMb: Long = -1L,
    val memoryUsagePercent: Float = -1f,
    val wakeLockHeld: Boolean = false,
    val foregroundServiceActive: Boolean = false,
    val microphoneInUse: Boolean = false,
    val audioFocusState: String = "IDLE",
    val cpuUsagePercent: Float? = null
)

/**
 * Structured Evidence Record for a Physical Device Test Scenario
 */
data class PhysicalDeviceTestScenarioResult(
    val scenarioId: String,
    val distance: AcousticDistance,
    val noiseProfile: NoiseProfile,
    val status: PhysicalValidationStatus = PhysicalValidationStatus.NOT_TESTED,
    val wakeWordDetected: Boolean = false,
    val falseAcceptanceCount: Int = 0,
    val sttWordErrorRate: Float? = null,
    val wakeWordDetectionLatencyMs: Long? = null,
    val sttLatencyMs: Long? = null,
    val speechEndToActionLatencyMs: Long? = null,
    val endToEndCommandLatencyMs: Long? = null,
    val uiRenderLatencyMs: Long? = null,
    val batteryDeltaPercent: Float? = null,
    val batteryTemperatureDelta: Float? = null,
    val thermalState: String? = null,
    val hadAnrOrCrash: Boolean = false,
    val backgroundPersistenceMaintained: Boolean = false,
    val dozeRecoverySuccess: Boolean = false,
    val telemetrySnapshot: SystemTelemetrySnapshot? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val failureReason: String? = null,
    val notes: String = "Physical device run not executed in automated JVM container"
)

/**
 * Hardware Specification Metadata
 */
data class DeviceHardwareProfile(
    val manufacturer: String,
    val model: String,
    val deviceCodename: String,
    val androidVersion: Int,
    val apiLevel: Int,
    val buildDisplayId: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val hardwareChipset: String,
    val isRunningOnJvmOrEmulator: Boolean
)

/**
 * Physical Hardware Validation Report
 */
data class PhysicalHardwareValidationReport(
    val hardwareProfile: DeviceHardwareProfile,
    val overallPhysicalStatus: PhysicalValidationStatus,
    val totalScenariosTested: Int,
    val passedScenariosCount: Int,
    val scenarioResults: List<PhysicalDeviceTestScenarioResult>,
    val systemSnapshot: SystemTelemetrySnapshot,
    val certificationNotes: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Export complete evidence report as formatted JSON
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("reportTimestamp", timestamp)
        root.put("overallPhysicalStatus", overallPhysicalStatus.name)
        root.put("totalScenariosTested", totalScenariosTested)
        root.put("passedScenariosCount", passedScenariosCount)
        root.put("certificationNotes", certificationNotes)

        val hw = JSONObject()
        hw.put("manufacturer", hardwareProfile.manufacturer)
        hw.put("model", hardwareProfile.model)
        hw.put("deviceCodename", hardwareProfile.deviceCodename)
        hw.put("androidVersion", hardwareProfile.androidVersion)
        hw.put("apiLevel", hardwareProfile.apiLevel)
        hw.put("buildDisplayId", hardwareProfile.buildDisplayId)
        hw.put("totalRamMb", hardwareProfile.totalRamMb)
        hw.put("availableRamMb", hardwareProfile.availableRamMb)
        hw.put("hardwareChipset", hardwareProfile.hardwareChipset)
        hw.put("isRunningOnJvmOrEmulator", hardwareProfile.isRunningOnJvmOrEmulator)
        root.put("hardwareProfile", hw)

        val sys = JSONObject()
        sys.put("batteryLevelPercent", systemSnapshot.batteryLevelPercent)
        sys.put("batteryTemperatureCelsius", systemSnapshot.batteryTemperatureCelsius)
        sys.put("isCharging", systemSnapshot.isCharging)
        sys.put("isPowerSaveMode", systemSnapshot.isPowerSaveMode)
        sys.put("isDeviceIdleMode", systemSnapshot.isDeviceIdleMode)
        sys.put("isScreenOn", systemSnapshot.isScreenOn)
        sys.put("thermalState", systemSnapshot.thermalState.name)
        sys.put("availableRamMb", systemSnapshot.availableRamMb)
        sys.put("totalRamMb", systemSnapshot.totalRamMb)
        sys.put("memoryUsagePercent", systemSnapshot.memoryUsagePercent)
        sys.put("wakeLockHeld", systemSnapshot.wakeLockHeld)
        sys.put("foregroundServiceActive", systemSnapshot.foregroundServiceActive)
        sys.put("microphoneInUse", systemSnapshot.microphoneInUse)
        sys.put("audioFocusState", systemSnapshot.audioFocusState)
        sys.put("cpuUsagePercent", systemSnapshot.cpuUsagePercent)
        root.put("systemSnapshot", sys)

        val scenariosArray = JSONArray()
        for (scenario in scenarioResults) {
            val sc = JSONObject()
            sc.put("scenarioId", scenario.scenarioId)
            sc.put("distance", scenario.distance.name)
            sc.put("distanceMeters", scenario.distance.distanceMeters)
            sc.put("noiseProfile", scenario.noiseProfile.name)
            sc.put("targetDBSPL", scenario.noiseProfile.targetDBSPL)
            sc.put("status", scenario.status.name)
            sc.put("wakeWordDetected", scenario.wakeWordDetected)
            sc.put("falseAcceptanceCount", scenario.falseAcceptanceCount)
            sc.put("sttWordErrorRate", scenario.sttWordErrorRate)
            sc.put("wakeWordDetectionLatencyMs", scenario.wakeWordDetectionLatencyMs)
            sc.put("sttLatencyMs", scenario.sttLatencyMs)
            sc.put("speechEndToActionLatencyMs", scenario.speechEndToActionLatencyMs)
            sc.put("endToEndCommandLatencyMs", scenario.endToEndCommandLatencyMs)
            sc.put("uiRenderLatencyMs", scenario.uiRenderLatencyMs)
            sc.put("batteryDeltaPercent", scenario.batteryDeltaPercent)
            sc.put("batteryTemperatureDelta", scenario.batteryTemperatureDelta)
            sc.put("thermalState", scenario.thermalState)
            sc.put("hadAnrOrCrash", scenario.hadAnrOrCrash)
            sc.put("backgroundPersistenceMaintained", scenario.backgroundPersistenceMaintained)
            sc.put("dozeRecoverySuccess", scenario.dozeRecoverySuccess)
            sc.put("timestamp", scenario.timestamp)
            sc.put("failureReason", scenario.failureReason)
            sc.put("notes", scenario.notes)
            scenariosArray.put(sc)
        }
        root.put("scenarios", scenariosArray)

        return root.toString(2)
    }

    /**
     * Export scenario test evidence as structured CSV
     */
    fun toCsv(): String {
        val sb = StringBuilder()
        sb.append("scenario_id,distance_m,noise_profile,target_db,status,wake_detected,wake_latency_ms,stt_latency_ms,e2e_latency_ms,battery_delta_pct,thermal_state,anr_or_crash,failure_reason,timestamp\n")
        for (s in scenarioResults) {
            sb.append("${s.scenarioId},")
            sb.append("${s.distance.distanceMeters},")
            sb.append("${s.noiseProfile.name},")
            sb.append("${s.noiseProfile.targetDBSPL},")
            sb.append("${s.status.name},")
            sb.append("${s.wakeWordDetected},")
            sb.append("${s.wakeWordDetectionLatencyMs ?: ""},")
            sb.append("${s.sttLatencyMs ?: ""},")
            sb.append("${s.endToEndCommandLatencyMs ?: ""},")
            sb.append("${s.batteryDeltaPercent ?: ""},")
            sb.append("${s.thermalState ?: ""},")
            sb.append("${s.hadAnrOrCrash},")
            sb.append("\"${s.failureReason ?: ""}\",")
            sb.append("${s.timestamp}\n")
        }
        return sb.toString()
    }
}

/**
 * PhysicalHardwareTestHarness (Stage 17 Production Hardening / Candidate Preparation)
 *
 * Collects structured evidence on actual Android OEM hardware across distance and noise regimes.
 * Supports export to JSON and CSV formats.
 * When executed in non-physical JVM/CI containers, honestly reports NOT_TESTED.
 */
object PhysicalHardwareTestHarness {

    private const val TAG = "PhysicalTestHarness"

    /**
     * Capture live device system telemetry snapshot
     */
    fun captureSystemTelemetry(context: Context?): SystemTelemetrySnapshot {
        if (context == null) return SystemTelemetrySnapshot()

        var batteryLevel = -1
        var batteryTemp = -1f
        var isCharging = false

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            if (batteryStatus != null) {
                val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryLevel = (level * 100) / scale
                }
                val tempRaw = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (tempRaw > 0) {
                    batteryTemp = tempRaw / 10.0f
                }
                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery status", e)
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode ?: false
        val isDeviceIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isDeviceIdleMode ?: false
        } else false
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager?.isInteractive ?: true
        } else {
            @Suppress("DEPRECATION")
            powerManager?.isScreenOn ?: true
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioFocusState = if (audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audioManager?.mode == AudioManager.MODE_IN_CALL
        ) "CALL_ACTIVE" else "AUDIO_NORMAL"

        var totalRamMb = -1L
        var availRamMb = -1L
        var memPct = -1f

        try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            totalRamMb = memInfo.totalMem / (1024 * 1024)
            availRamMb = memInfo.availMem / (1024 * 1024)
            if (totalRamMb > 0) {
                memPct = ((totalRamMb - availRamMb).toFloat() / totalRamMb) * 100f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading memory info", e)
        }

        val thermalState = try {
            ThermalAndBatteryManager.getInstance(context).thermalState.value
        } catch (e: Exception) {
            ThermalState.NORMAL
        }

        return SystemTelemetrySnapshot(
            batteryLevelPercent = batteryLevel,
            batteryTemperatureCelsius = batteryTemp,
            isCharging = isCharging,
            isPowerSaveMode = isPowerSave,
            isDeviceIdleMode = isDeviceIdle,
            isScreenOn = isScreenOn,
            thermalState = thermalState,
            availableRamMb = availRamMb,
            totalRamMb = totalRamMb,
            memoryUsagePercent = memPct,
            wakeLockHeld = false,
            foregroundServiceActive = false,
            microphoneInUse = false,
            audioFocusState = audioFocusState,
            cpuUsagePercent = null
        )
    }

    /**
     * Inspect host device hardware specifications
     */
    fun getHardwareProfile(context: Context? = null): DeviceHardwareProfile {
        val isJvmOrRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                Build.BRAND.equals("generic", ignoreCase = true)

        var totalRam = -1L
        var availRam = -1L
        try {
            val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            totalRam = memInfo.totalMem / (1024 * 1024)
            availRam = memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            // JVM fallback
        }

        return DeviceHardwareProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            deviceCodename = Build.DEVICE,
            androidVersion = Build.VERSION.SDK_INT,
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplayId = Build.DISPLAY,
            totalRamMb = totalRam,
            availableRamMb = availRam,
            hardwareChipset = Build.HARDWARE,
            isRunningOnJvmOrEmulator = isJvmOrRobolectric
        )
    }

    /**
     * Generate standard validation report template.
     * Marks all unverified physical tests strictly as NOT_TESTED.
     */
    fun generateEvidenceReport(
        context: Context? = null,
        actualResults: List<PhysicalDeviceTestScenarioResult> = emptyList()
    ): PhysicalHardwareValidationReport {
        val profile = getHardwareProfile(context)
        val snapshot = captureSystemTelemetry(context)

        val fullMatrix = mutableListOf<PhysicalDeviceTestScenarioResult>()

        for (distance in AcousticDistance.entries) {
            for (noise in NoiseProfile.entries) {
                val scenarioId = "PHYS_${distance.name}_${noise.name}"
                val existing = actualResults.firstOrNull { it.scenarioId == scenarioId }
                if (existing != null) {
                    fullMatrix.add(existing)
                } else {
                    fullMatrix.add(
                        PhysicalDeviceTestScenarioResult(
                            scenarioId = scenarioId,
                            distance = distance,
                            noiseProfile = noise,
                            status = PhysicalValidationStatus.NOT_TESTED,
                            notes = "Physical acoustic lab validation pending field deployment"
                        )
                    )
                }
            }
        }

        val passedCount = fullMatrix.count { it.status == PhysicalValidationStatus.PASS }
        val overallStatus = when {
            fullMatrix.any { it.status == PhysicalValidationStatus.FAIL } -> PhysicalValidationStatus.FAIL
            passedCount == fullMatrix.size && fullMatrix.isNotEmpty() -> PhysicalValidationStatus.PASS
            passedCount > 0 -> PhysicalValidationStatus.PENDING
            else -> PhysicalValidationStatus.NOT_TESTED
        }

        val notes = if (profile.isRunningOnJvmOrEmulator) {
            "Host environment is JVM/Robolectric test harness. Physical microphone acoustic tests are marked NOT_TESTED as mandated by Stage 17 integrity rules."
        } else {
            "Physical device connected: ${profile.manufacturer} ${profile.model} (API ${profile.apiLevel})"
        }

        return PhysicalHardwareValidationReport(
            hardwareProfile = profile,
            overallPhysicalStatus = overallStatus,
            totalScenariosTested = fullMatrix.size,
            passedScenariosCount = passedCount,
            scenarioResults = fullMatrix,
            systemSnapshot = snapshot,
            certificationNotes = notes
        )
    }
}
