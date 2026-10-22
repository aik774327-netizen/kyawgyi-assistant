package com.example.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.audit.AuditLogger
import com.example.audit.ResolutionPath
import com.example.data.FailureCategory
import com.example.engine.KyawGyiAppLauncher
import com.example.ocr.FailureMemoryEngine
import com.example.security.RiskPolicy
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder

/**
 * FIX-04: returns the first camera id whose flash unit is present,
 * or null when none is. Pure (the capability probe is injected) so
 * the selection policy is unit-testable without camera hardware.
 */
fun firstFlashCapableCameraId(
    cameraIds: List<String>,
    hasFlashUnit: (String) -> Boolean
): String? = cameraIds.firstOrNull { id -> runCatching { hasFlashUnit(id) }.getOrDefault(false) }

data class CommandResult(
    val isSuccess: Boolean,
    val action: String,
    val target: String,
    val parameters: Map<String, String> = emptyMap(),
    val executionStarted: Boolean = false,
    val executionConfirmed: Boolean = false,
    val errorCode: String? = null,
    val userMessage: String,
    val failurePreWarning: String? = null
)

open class KyawGyiActionExecutor(val context: Context) {

    private val TAG = "KYAW_GYI_ACTIONS"
    private val systemActionHandler = GenericSystemActionHandler(context)
    private val appLauncher = KyawGyiAppLauncher(context)

    fun confirmPendingCommunication(): CommandResult = CallSmsConfirmationCoordinator.getInstance().executeConfirmed(context)
    fun cancelPendingCommunication(reason: String? = null): CommandResult = CallSmsConfirmationCoordinator.getInstance().cancelPending(reason)
    fun hasPendingCommunication(): Boolean = CallSmsConfirmationCoordinator.getInstance().hasPendingAction()

    // -------------------------------------------------------------
    // 1. GENERIC SYSTEM ACTIONS (VOLUME / BRIGHTNESS / MUTE / PERCENTAGES)
    // -------------------------------------------------------------
    fun executeSystemAction(rawCommand: String): CommandResult? {
        val parsed = systemActionHandler.parseCommand(rawCommand) ?: return null
        val policy = RiskPolicy.evaluate("SYSTEM_CONTROL", parsed.target.name)
        if (!policy.isAllowed) {
            AuditLogger.log(rawCommand, rawCommand, ResolutionPath.BLOCKED_BY_RISK_POLICY, "SYSTEM_CONTROL", 1.0f, false, policy.reason)
            return CommandResult(
                isSuccess = false,
                action = "SYSTEM_CONTROL",
                target = parsed.target.name,
                errorCode = "BLOCKED_BY_POLICY",
                userMessage = policy.confirmationPromptMM ?: "ဤလုပ်ဆောင်ချက်ကို လုပ်ဆောင်ခွင့် မရှိပါခင်ဗျာ။"
            )
        }

        val startTime = System.currentTimeMillis()
        val result = systemActionHandler.executeAction(parsed)
        val elapsed = System.currentTimeMillis() - startTime

        AuditLogger.log(
            rawQuery = rawCommand,
            normalizedQuery = rawCommand,
            resolutionPath = ResolutionPath.EXACT_FASTPATH,
            targetAction = "SYSTEM_${parsed.target.name}",
            confidence = 0.98f,
            isSuccess = result.isSuccess,
            details = "level=${result.currentLevelPercent}%, requiresPermission=${result.requiresPermission}",
            executionTimeMs = elapsed
        )

        return CommandResult(
            isSuccess = result.isSuccess,
            action = "SET_${parsed.target.name}",
            target = parsed.target.name,
            parameters = mapOf("level" to "${result.currentLevelPercent}%"),
            executionStarted = true,
            executionConfirmed = result.isSuccess,
            userMessage = result.messageMM
        )
    }

    // -------------------------------------------------------------
    // 2. FLASHLIGHT (ဓာတ်မီး ထိန်းချုပ်ခြင်း - အတုအယောင် မပါဘဲ တကယ့် Hardware စစ်ဆေးမှု & Failure Memory)
    // -------------------------------------------------------------
    fun setFlashlight(enable: Boolean): CommandResult {
        val opName = if (enable) "ENABLE_TORCH" else "DISABLE_TORCH"
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("FLASHLIGHT_ACTION", opName)

        // Real Hardware Feature Check (No Mock / No Fake Simulation)
        val hasFlash = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH)
        if (!hasFlash) {
            AuditLogger.log("flashlight $enable", "flashlight", ResolutionPath.EXACT_FASTPATH, "FLASHLIGHT", 0.0f, false, "NO_FLASH_HARDWARE")
            FailureMemoryEngine.recordFailure(
                component = "FLASHLIGHT_ACTION",
                operation = opName,
                category = FailureCategory.RESOURCE,
                rootCauseMm = "ဤဖုန်းတွင် ဓာတ်မီး (Camera Flash) Hardware မပါရှိပါ",
                technicalDetails = "PackageManager.hasSystemFeature(FEATURE_CAMERA_FLASH) returned false",
                suggestedRemediationMm = "မျက်နှာပြင် အလင်းရောင် အမြင့်ဆုံးတင်၍ အလင်းရယူပါ"
            )
            val baseMsg = "ဤဖုန်းတွင် ဓာတ်မီး (Camera Flash) Hardware မပါရှိပါခင်ဗျာ။"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            return CommandResult(
                isSuccess = false,
                action = if (enable) "TURN_ON_FLASH" else "TURN_OFF_FLASH",
                target = "FLASHLIGHT",
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "NO_FLASH_HARDWARE",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }

        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraIds = cameraManager?.cameraIdList?.toList().orEmpty()
            if (cameraManager == null || cameraIds.isEmpty()) {
                FailureMemoryEngine.recordFailure(
                    component = "FLASHLIGHT_ACTION",
                    operation = opName,
                    category = FailureCategory.TECHNICAL,
                    rootCauseMm = "Camera စနစ်အား ရယူ၍ မရနိုင်ပါ",
                    technicalDetails = "CameraManager.cameraIdList is empty or service unavailable",
                    suggestedRemediationMm = "စက်ကို restart ချပြီး ပြန်လည်စမ်းသပ်ပါ"
                )
                val baseMsg = "Camera စနစ်အား ရယူ၍ မရနိုင်ပါသဖြင့် ဓာတ်မီး မဖွင့်နိုင်ပါခင်ဗျာ။"
                val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
                return CommandResult(
                    isSuccess = false,
                    action = if (enable) "TURN_ON_FLASH" else "TURN_OFF_FLASH",
                    target = "FLASHLIGHT",
                    executionStarted = false,
                    executionConfirmed = false,
                    errorCode = "CAMERA_SERVICE_UNAVAILABLE",
                    userMessage = finalMsg,
                    failurePreWarning = preWarning
                )
            }

            // FIX-04: choose a camera that actually HAS a flash unit.
            // The pre-fix code used cameraIdList.firstOrNull(), which
            // on multi-camera devices can select a flash-less camera
            // and fail (or silently target the wrong hardware). If no
            // camera reports FLASH_INFO_AVAILABLE, fail honestly.
            val cameraId = firstFlashCapableCameraId(cameraIds) { id ->
                try {
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (e: Exception) {
                    false
                }
            }
            if (cameraId == null) {
                FailureMemoryEngine.recordFailure(
                    component = "FLASHLIGHT_ACTION",
                    operation = opName,
                    category = FailureCategory.RESOURCE,
                    rootCauseMm = "Flash ပါသော Camera မရှိပါ",
                    technicalDetails = "No camera reports FLASH_INFO_AVAILABLE among $cameraIds",
                    suggestedRemediationMm = "မျက်နှာပြင် အလင်းရောင် အမြင့်ဆုံးတင်၍ အလင်းရယူပါ"
                )
                val baseMsg = "ဤဖုန်းတွင် Flash ပါသော Camera မရှိပါသဖြင့် ဓာတ်မီး မဖွင့်နိုင်ပါခင်ဗျာ။"
                val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
                return CommandResult(
                    isSuccess = false,
                    action = if (enable) "TURN_ON_FLASH" else "TURN_OFF_FLASH",
                    target = "FLASHLIGHT",
                    executionStarted = false,
                    executionConfirmed = false,
                    errorCode = "NO_FLASH_CAMERA",
                    userMessage = finalMsg,
                    failurePreWarning = preWarning
                )
            }

            cameraManager.setTorchMode(cameraId, enable)
            val baseMsg = if (enable) "ဟုတ်ကဲ့၊ ဓာတ်မီး ဖွင့်ပေးလိုက်ပါပြီ။" else "ဟုတ်ကဲ့၊ ဓာတ်မီး ပိတ်ပေးလိုက်ပါပြီ။"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            
            AuditLogger.log("flashlight $enable", "flashlight", ResolutionPath.EXACT_FASTPATH, "FLASHLIGHT", 1.0f, true)
            
            CommandResult(
                isSuccess = true,
                action = if (enable) "TURN_ON_FLASH" else "TURN_OFF_FLASH",
                target = "FLASHLIGHT",
                executionStarted = true,
                executionConfirmed = true,
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        } catch (e: Exception) {
            Log.e(TAG, "Real Flashlight Hardware Error", e)
            AuditLogger.log("flashlight $enable", "flashlight", ResolutionPath.EXACT_FASTPATH, "FLASHLIGHT", 0.0f, false, e.localizedMessage ?: "Unknown Flashlight Error")
            FailureMemoryEngine.recordFailure(
                component = "FLASHLIGHT_ACTION",
                operation = opName,
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                rootCauseMm = "ဓာတ်မီး ဟာ့ဒ်ဝဲကို အခြားအက်ပ်မှ အသုံးပြုနေခြင်း သို့မဟုတ် OS ကန့်သတ်ချက်ကြောင့် ဖြစ်ပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "ကင်မရာဖွင့်ထားသော အက်ပ်များကို ပိတ်ပြီးမှ ထပ်မံကြိုးစားပါ"
            )
            val baseMsg = "ဓာတ်မီး ${if (enable) "ဖွင့်" else "ပိတ်"}ရာတွင် အမှားဖြစ်ပေါ်ပါသည်: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = if (enable) "TURN_ON_FLASH" else "TURN_OFF_FLASH",
                target = "FLASHLIGHT",
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "FLASH_HARDWARE_ERROR",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 2.1 BATTERY STATUS (ဘက်ထရီ အခြေအနေ စစ်ဆေးခြင်း)
    // -------------------------------------------------------------
    fun getBatteryStatus(): CommandResult {
        return try {
            val ifilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0
            val chargingText = if (isCharging) " (အားသွင်းနေပါသည်)" else ""
            val msg = "လက်ရှိ ဖုန်းဘက်ထရီမှာ $batteryPct%$chargingText ကျန်ရှိပါသည်ခင်ဗျာ။"
            CommandResult(
                isSuccess = true,
                action = "BATTERY_STATUS",
                target = "BATTERY",
                parameters = mapOf("percent" to "$batteryPct%"),
                executionStarted = true,
                executionConfirmed = true,
                userMessage = msg
            )
        } catch (e: Exception) {
            CommandResult(
                isSuccess = false,
                action = "BATTERY_STATUS",
                target = "BATTERY",
                errorCode = "BATTERY_ERROR",
                userMessage = "ဘက်ထရီ အခြေအနေ စစ်ဆေးရာတွင် အမှားဖြစ်ပေါ်ပါသည်: ${e.message}"
            )
        }
    }

    // -------------------------------------------------------------
    // 3. WI-FI CONTROL (ဝိုင်ဖိုင် ထိန်းချုပ်ခြင်း - Android OS ကန့်သတ်ချက် အမှန်အတိုင်း အသိပေးခြင်း & Failure Memory)
    // -------------------------------------------------------------
    open fun setWifi(enable: Boolean): CommandResult {
        val opName = if (enable) "TURN_ON_WIFI" else "TURN_OFF_WIFI"
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("WIFI_CONTROL", opName)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 (API 29) and above: Security policy prevents 3rd-party apps from directly toggling Wi-Fi
                // Request Automated Fallback via Accessibility Service if permission is granted
                val accessibilityActive = com.example.service.KyawGyiAccessibilityService.instance != null
                if (accessibilityActive) {
                    com.example.service.KyawGyiAccessibilityService.requestAutomatedWifiToggle(enable)
                }

                val panelIntent = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                } catch (e: Exception) {
                    Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

                try {
                    context.startActivity(panelIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Panel launch failed, falling back to ACTION_WIFI_SETTINGS: ${e.message}")
                    val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }

                // FIX-04 honesty contract: opening the panel (and even
                // queueing the accessibility click automation) is NOT a
                // Wi-Fi state change. The pre-fix result claimed
                // isSuccess=true / executionConfirmed=true here — an
                // ISSUE-030-class false success. Panel-only outcomes
                // now report isSuccess=false / executionConfirmed=false
                // with errorCode PANEL_ONLY_NO_DIRECT_TOGGLE, and the
                // message says plainly that the state has not changed.
                val baseMsg = if (accessibilityActive) {
                    "Wi-Fi Panel ဖွင့်၍ Accessibility စနစ်ဖြင့် ခလုတ် နှိပ်ခိုင်းထားပါသည်ခင်ဗျာ။ သို့သော် Wi-Fi state ပြောင်းလဲမှု အတည်ပြုချက် မရသေးပါခင်ဗျာ — Panel ပေါ်တွင် စစ်ဆေးပေးပါ။"
                } else {
                    "Android 10+ လုံခြုံရေး စည်းမျဉ်းအရ Wi-Fi ကို တိုက်ရိုက် ဖွင့်/ပိတ်ခွင့် မရှိသဖြင့် Wi-Fi Panel ကို ဖွင့်ပေးထားပါသည်ခင်ဗျာ။ Wi-Fi state မပြောင်းသေးပါ — Panel ထဲက ခလုတ်ကို ကိုယ်တိုင် နှိပ်ပေးပါခင်ဗျာ။"
                }
                val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
                CommandResult(
                    isSuccess = false,
                    action = if (accessibilityActive) "AUTO_TOGGLE_WIFI_PANEL" else "OPEN_WIFI_PANEL",
                    target = "WIFI_SETTINGS",
                    parameters = mapOf(
                        "automatedFallback" to accessibilityActive.toString(),
                        "targetEnable" to enable.toString()
                    ),
                    executionStarted = true,
                    executionConfirmed = false,
                    errorCode = "PANEL_ONLY_NO_DIRECT_TOGGLE",
                    userMessage = finalMsg,
                    failurePreWarning = preWarning
                )
            } else {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(enable)
                if (success) {
                    val baseMsg = if (enable) "ဟုတ်ကဲ့၊ ဝိုင်ဖိုင် တိုက်ရိုက် ဖွင့်ပေးလိုက်ပါပြီ။" else "ဟုတ်ကဲ့၊ ဝိုင်ဖိုင် တိုက်ရိုက် ပိတ်ပေးလိုက်ပါပြီ။"
                    val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
                    CommandResult(
                        isSuccess = true,
                        action = if (enable) "TURN_ON_WIFI" else "TURN_OFF_WIFI",
                        target = "WIFI",
                        executionStarted = true,
                        executionConfirmed = true,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                } else {
                    FailureMemoryEngine.recordFailure(
                        component = "WIFI_CONTROL",
                        operation = opName,
                        category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                        rootCauseMm = "ဝိုင်ဖိုင်အား တိုက်ရိုက် ပြောင်းလဲခွင့်ကို စနစ်မှ ငြင်းပယ်ပါသည်",
                        technicalDetails = "WifiManager.setWifiEnabled returned false",
                        suggestedRemediationMm = "စနစ် ဆက်တင်ထဲမှ Wi-Fi ခလုတ်ကို ဖွင့်/ပိတ်ပါ"
                    )
                    val baseMsg = "ဝိုင်ဖိုင်အား တိုက်ရိုက် ပြောင်းလဲခွင့်ကို စနစ်မှ ငြင်းပယ်ပါသည်ခင်ဗျာ။"
                    val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
                    CommandResult(
                        isSuccess = false,
                        action = "TOGGLE_WIFI",
                        target = "WIFI",
                        executionStarted = false,
                        executionConfirmed = false,
                        errorCode = "WIFI_TOGGLE_REJECTED",
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wifi Error", e)
            FailureMemoryEngine.recordFailure(
                component = "WIFI_CONTROL",
                operation = opName,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "ဝိုင်ဖိုင် စနစ် ပြောင်းလဲရာတွင် နည်းပညာပိုင်း ချို့ယွင်းချက် ဖြစ်ပေါ်ပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "စနစ် ဆက်တင် (Settings) မှတစ်ဆင့် တိုက်ရိုက် ကြိုးစားပါ"
            )
            val baseMsg = "ဝိုင်ဖိုင် စနစ်ကို ပြောင်းလဲ၍ မရပါခင်ဗျာ: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "TOGGLE_WIFI",
                target = "WIFI",
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "WIFI_RESTRICTED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 3.1 BLUETOOTH CONTROL (ဘလူးတုသ် ထိန်းချုပ်ခြင်း - Android OS လုံခြုံရေး & Accessibility Automation)
    // -------------------------------------------------------------
    fun setBluetooth(enable: Boolean): CommandResult {
        val opName = if (enable) "TURN_ON_BLUETOOTH" else "TURN_OFF_BLUETOOTH"
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("BLUETOOTH_CONTROL", opName)

        return try {
            val accessibilityActive = com.example.service.KyawGyiAccessibilityService.instance != null
            if (accessibilityActive) {
                com.example.service.KyawGyiAccessibilityService.requestAutomatedBluetoothToggle(enable)
            }

            val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)

            // FIX-04 honesty contract (same as setWifi): opening the
            // Bluetooth Settings page is NOT a Bluetooth state change.
            // The pre-fix result claimed isSuccess=true /
            // executionConfirmed=true; direct toggle is forbidden to
            // third-party apps (restrictions tightened further in
            // API 33+), so the honest outcome is panel-only.
            val baseMsg = if (accessibilityActive) {
                "Bluetooth ဆက်တင် ဖွင့်၍ Accessibility စနစ်ဖြင့် ခလုတ် နှိပ်ခိုင်းထားပါသည်ခင်ဗျာ။ သို့သော် Bluetooth state ပြောင်းလဲမှု အတည်ပြုချက် မရသေးပါခင်ဗျာ — ဆက်တင် Page ပေါ်တွင် စစ်ဆေးပေးပါ။"
            } else {
                "Android လုံခြုံရေး စည်းမျဉ်းအရ Bluetooth ကို တိုက်ရိုက် ဖွင့်/ပိတ်ခွင့် မရှိသဖြင့် Bluetooth ဆက်တင် Page ကို ဖွင့်ပေးထားပါသည်ခင်ဗျာ။ Bluetooth state မပြောင်းသေးပါ — Page ထဲက ခလုတ်ကို ကိုယ်တိုင် နှိပ်ပေးပါခင်ဗျာ။"
            }
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = if (accessibilityActive) "AUTO_TOGGLE_BLUETOOTH" else "OPEN_BLUETOOTH_SETTINGS",
                target = "BLUETOOTH_SETTINGS",
                parameters = mapOf(
                    "automatedFallback" to accessibilityActive.toString(),
                    "targetEnable" to enable.toString()
                ),
                executionStarted = true,
                executionConfirmed = false,
                errorCode = "PANEL_ONLY_NO_DIRECT_TOGGLE",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth Error", e)
            FailureMemoryEngine.recordFailure(
                component = "BLUETOOTH_CONTROL",
                operation = opName,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "Bluetooth စနစ် ပြောင်းလဲရာတွင် နည်းပညာပိုင်း ချို့ယွင်းချက် ဖြစ်ပေါ်ပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "စနစ် ဆက်တင် (Settings) မှတစ်ဆင့် တိုက်ရိုက် ကြိုးစားပါ"
            )
            val baseMsg = "Bluetooth စနစ်ကို ပြောင်းလဲ၍ မရပါခင်ဗျာ: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "TOGGLE_BLUETOOTH",
                target = "BLUETOOTH",
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "BLUETOOTH_RESTRICTED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 4. OPEN APP (အပလီကေးရှင်းများ ဖွင့်ပေးခြင်း - Dynamic & Universal Launch Intelligence & Failure Memory)
    // -------------------------------------------------------------
    fun openApplication(packageNameOrQuery: String, appNameMM: String): CommandResult {
        val targetQuery = appNameMM.ifBlank { packageNameOrQuery }
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("APP_LAUNCH", targetQuery)

        // ISSUE-036: pre-execution enforcement of the Error-Learning
        // Failed-Method Memory under the ONE canonical key. Previously this
        // executor (and every router that calls it directly — SmartRouter,
        // IntentRouter, Dispatcher, ActionPlanExecutor) never consulted the
        // engine's memory at all, so ER-007's block gated nothing here.
        // The memory is consulted WITHOUT a Context on purpose: this is a
        // pure block check; live re-verification (which may clear a block)
        // belongs to the pipeline guard / viewer paths (ISSUE-034/039).
        val canonicalFailureKey = com.example.audit.FailedMethodKeys.canonical("APP_LAUNCH", targetQuery)
        val engineWarning = com.example.audit.ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = canonicalFailureKey,
            context = null
        )
        if (engineWarning != null) {
            return CommandResult(
                isSuccess = false,
                action = "OPEN_APP",
                target = targetQuery,
                parameters = mapOf("appName" to appNameMM),
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "FAILED_METHOD_BLOCKED",
                userMessage = "${engineWarning.warningBanner} ${engineWarning.simpleExplanationMm}",
                failurePreWarning = preWarning
            )
        }

        return try {
            val pm = context.packageManager
            val universalLauncher = com.example.discovery.UniversalAppLauncher.getInstance(context)

            // 1. Direct verified package launch
            if (packageNameOrQuery.contains(".")) {
                val launchResult = universalLauncher.launchPackageDirectly(
                    packageName = packageNameOrQuery,
                    applicationLabel = appNameMM.ifBlank { packageNameOrQuery }
                )
                if (launchResult.success) {
                    AuditLogger.log(
                        rawQuery = appNameMM,
                        normalizedQuery = packageNameOrQuery,
                        resolutionPath = ResolutionPath.EXACT_FASTPATH,
                        targetAction = "OPEN_APP",
                        confidence = 1.0f,
                        isSuccess = true,
                        details = "directPackage=$packageNameOrQuery"
                    )
                    val finalMsg = if (preWarning != null) "$preWarning\n\n${launchResult.userMessageMM}" else launchResult.userMessageMM
                    return CommandResult(
                        isSuccess = true,
                        action = "OPEN_APP",
                        target = packageNameOrQuery,
                        parameters = mapOf("appName" to appNameMM),
                        executionStarted = true,
                        executionConfirmed = true,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
            }

            // 2. Dynamic Universal Semantic Resolution & Launch
            val query = appNameMM.ifBlank { packageNameOrQuery }
            val launchResult = kotlinx.coroutines.runBlocking {
                universalLauncher.resolveAndLaunch(query)
            }

            val targetPkg = launchResult.packageName ?: packageNameOrQuery
            val label = launchResult.applicationLabel ?: appNameMM

            AuditLogger.log(
                rawQuery = appNameMM,
                normalizedQuery = packageNameOrQuery,
                resolutionPath = if (launchResult.success) ResolutionPath.DYNAMIC_FUZZY else ResolutionPath.AWAITING_RETRY,
                targetAction = "OPEN_APP",
                confidence = launchResult.resolutionResult?.confidence ?: (if (launchResult.success) 0.95f else 0.0f),
                isSuccess = launchResult.success,
                details = "package=$targetPkg error=${launchResult.error}"
            )

            if (!launchResult.success) {
                FailureMemoryEngine.recordFailure(
                    component = "APP_LAUNCH",
                    operation = targetQuery,
                    category = FailureCategory.RESOURCE,
                    rootCauseMm = "အပလီကေးရှင်း '$targetQuery' အား စက်ထဲတွင် မတွေ့ရှိပါ သို့မဟုတ် ဖွင့်၍မရပါ",
                    technicalDetails = "UniversalAppLauncher error: ${launchResult.error}",
                    suggestedRemediationMm = "အက်ပ်အမည် အသံထွက် မှန်ကန်မှု ရှိမရှိ စစ်ဆေးပါ သို့မဟုတ် Google Play မှ ထည့်သွင်းပါ"
                )
            }

            val finalMsg = if (preWarning != null) "$preWarning\n\n${launchResult.userMessageMM}" else launchResult.userMessageMM

            CommandResult(
                isSuccess = launchResult.success,
                action = "OPEN_APP",
                target = targetPkg,
                parameters = mapOf("appName" to label),
                executionStarted = launchResult.success,
                executionConfirmed = launchResult.success,
                errorCode = launchResult.error,
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        } catch (e: Exception) {
            Log.e(TAG, "App Launch Error", e)
            FailureMemoryEngine.recordFailure(
                component = "APP_LAUNCH",
                operation = targetQuery,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "အက်ပ်ဖွင့်ရာတွင် စနစ်ဆိုင်ရာ ချို့ယွင်းချက် ဖြစ်ပေါ်ပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "ဖုန်းမျက်နှာပြင်မှတစ်ဆင့် အက်ပ်ကို ကိုယ်တိုင်ဖွင့်ကြည့်ပါ",
                exception = e
            )
            val baseMsg = "အက်ပ်ဖွင့်ရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "OPEN_APP",
                target = packageNameOrQuery,
                parameters = mapOf("appName" to appNameMM),
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "EXECUTION_FAILED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 4b. CLOSE APP (အပလီကေးရှင်းများ ပိတ်ပေးခြင်း / ပင်မမျက်နှာပြင်သို့ ပြန်လည်ရောက်ရှိခြင်း & Failure Memory)
    // -------------------------------------------------------------
    fun closeApplication(packageNameOrQuery: String = "", appNameMM: String = ""): CommandResult {
        val targetQuery = appNameMM.ifBlank { packageNameOrQuery.ifBlank { "ACTIVE_APP" } }
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("APP_CLOSE", targetQuery)

        return try {
            val label = appNameMM.ifBlank { packageNameOrQuery.ifBlank { "အက်ပ်" } }

            // 1. If Accessibility Service is running, use it to perform Home or Back.
            //    Its Boolean return is this mechanism's dispatch result — record it
            //    (previously it was captured but discarded for reporting, ISSUE-027).
            val accessibilityService = com.example.service.KyawGyiAccessibilityService.instance
            var homeViaAccessibility = false
            if (accessibilityService != null) {
                homeViaAccessibility = accessibilityService.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )
            }

            // 2. Also send Home intent to ensure device returns to launcher / minimizes app.
            //    Record whether this dispatch itself succeeded instead of letting a
            //    failure vanish into the generic catch (ISSUE-027).
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            var homeViaIntent = false
            try {
                context.startActivity(homeIntent)
                homeViaIntent = true
            } catch (e: Exception) {
                Log.d(TAG, "Home intent dispatch failed: ${e.message}")
            }

            // What was actually performed: at most, a Home-screen navigation dispatch.
            // Whether the named app actually closed cannot be verified through Android
            // public APIs, so it is never claimed in the result or the audit log below.
            val homeDispatched = homeViaAccessibility || homeViaIntent

            // 3. Kill background processes if specific package is targeted
            if (packageNameOrQuery.contains(".")) {
                try {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                    am?.killBackgroundProcesses(packageNameOrQuery)
                } catch (e: Exception) {
                    Log.d(TAG, "killBackgroundProcesses notice: ${e.message}")
                }
            }

            // 4. Clear active context if closed app was in context
            try {
                com.example.engine.CommandContextManager.getInstance().resetContext()
            } catch (ignored: Exception) {}

            if (!homeDispatched) {
                // Neither mechanism dispatched: report an honest failure (and audit it)
                // instead of the previous unconditional success.
                FailureMemoryEngine.recordFailure(
                    component = "APP_CLOSE",
                    operation = targetQuery,
                    category = FailureCategory.TECHNICAL,
                    rootCauseMm = "ပင်မစာမျက်နှာသို့ ပြန်ရန် လုပ်ဆောင်ချက် မအောင်မြင်ပါ",
                    technicalDetails = "GLOBAL_ACTION_HOME returned $homeViaAccessibility and the Home intent failed to dispatch",
                    suggestedRemediationMm = "Accessibility Service ခွင့်ပြုချက် ပေးထားခြင်း ရှိမရှိ စစ်ဆေးပါ"
                )
                AuditLogger.log(
                    rawQuery = appNameMM,
                    normalizedQuery = packageNameOrQuery,
                    resolutionPath = ResolutionPath.EXACT_FASTPATH,
                    targetAction = "CLOSE_APP",
                    confidence = 0.0f,
                    isSuccess = false,
                    details = "target=$packageNameOrQuery, accessibility=$homeViaAccessibility, homeIntent=$homeViaIntent, homeDispatched=false, appClosureVerified=false"
                )
                val failMsg = "ပင်မစာမျက်နှာသို့ ပြန်သွား၍ မရပါခင်ဗျာ။"
                val finalFailMsg = if (preWarning != null) "$preWarning\n\n$failMsg" else failMsg
                return CommandResult(
                    isSuccess = false,
                    action = "CLOSE_APP",
                    target = packageNameOrQuery,
                    parameters = mapOf("appName" to label),
                    executionStarted = false,
                    executionConfirmed = false,
                    errorCode = "HOME_NAVIGATION_FAILED",
                    userMessage = finalFailMsg,
                    failurePreWarning = preWarning
                )
            }

            // Honest report (ISSUE-027): the device was navigated to the Home screen.
            // The named app being closed is UNVERIFIABLE via public APIs, so the
            // message says exactly that and executionConfirmed stays false; isSuccess
            // covers only the Home-navigation dispatch that actually happened.
            val baseMsg = if (label.isNotBlank() && label != "အက်ပ်") {
                "ဟုတ်ကဲ့၊ ပင်မစာမျက်နှာသို့ ပြန်သွားပါပြီခင်ဗျာ။ $label အက်ပ် ပိတ်သွားကြောင်းကိုတော့ အတည်မပြုနိုင်ပါခင်ဗျာ။"
            } else {
                "ဟုတ်ကဲ့၊ ပင်မစာမျက်နှာသို့ ပြန်သွားပါပြီခင်ဗျာ။ လက်ရှိအက်ပ် ပိတ်သွားကြောင်းကိုတော့ အတည်မပြုနိုင်ပါခင်ဗျာ။"
            }
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg

            AuditLogger.log(
                rawQuery = appNameMM,
                normalizedQuery = packageNameOrQuery,
                resolutionPath = ResolutionPath.EXACT_FASTPATH,
                targetAction = "CLOSE_APP",
                confidence = 0.95f,
                isSuccess = true,
                details = "target=$packageNameOrQuery, accessibility=$homeViaAccessibility, homeIntent=$homeViaIntent, homeDispatched=true, appClosureVerified=false"
            )

            CommandResult(
                isSuccess = true,
                action = "CLOSE_APP",
                target = packageNameOrQuery,
                parameters = mapOf("appName" to label),
                executionStarted = true,
                executionConfirmed = false,
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        } catch (e: Exception) {
            Log.e(TAG, "App Close Error", e)
            FailureMemoryEngine.recordFailure(
                component = "APP_CLOSE",
                operation = targetQuery,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "အက်ပ်ပိတ်ရာတွင် စနစ်ဆိုင်ရာ ချို့ယွင်းချက် ဖြစ်ပေါ်ပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "Accessibility Service ခွင့်ပြုချက် ပေးထားခြင်း ရှိမရှိ စစ်ဆေးပါ"
            )
            val baseMsg = "အက်ပ်ပိတ်ရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "CLOSE_APP",
                target = packageNameOrQuery,
                parameters = mapOf("appName" to appNameMM),
                executionStarted = false,
                executionConfirmed = false,
                errorCode = "CLOSE_FAILED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 5. SEARCH MEDIA / APP
    // -------------------------------------------------------------
    fun searchMedia(query: String, targetAppPackage: String = "com.google.android.youtube", appNameMM: String = "YouTube"): CommandResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return CommandResult(
                isSuccess = false,
                action = "SEARCH_MEDIA",
                target = targetAppPackage,
                errorCode = "EMPTY_QUERY",
                userMessage = "ရှာဖွေလိုသော အမည်ကို မသိရှိရသေးပါခင်ဗျာ။"
            )
        }

        return try {
            val isAppInstalled = context.packageManager.getLaunchIntentForPackage(targetAppPackage) != null
            if (isAppInstalled && targetAppPackage == "com.google.android.youtube") {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", cleanQuery)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                CommandResult(
                    isSuccess = true,
                    action = "SEARCH_MEDIA",
                    target = targetAppPackage,
                    parameters = mapOf("query" to cleanQuery, "appName" to appNameMM),
                    executionStarted = true,
                    executionConfirmed = true,
                    userMessage = "$appNameMM တွင် $cleanQuery ကို ရှာဖွေပေးနေပါပြီ။"
                )
            } else {
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                CommandResult(
                    isSuccess = true,
                    action = "SEARCH_MEDIA_WEB_FALLBACK",
                    target = "BROWSER",
                    parameters = mapOf("query" to cleanQuery),
                    executionStarted = true,
                    executionConfirmed = true,
                    userMessage = "$cleanQuery ကို YouTube ရှာဖွေမှု ရလဒ် ဖွင့်ပေးလိုက်ပါပြီ။"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search Media Error", e)
            CommandResult(
                isSuccess = false,
                action = "SEARCH_MEDIA",
                target = targetAppPackage,
                errorCode = "SEARCH_FAILED",
                userMessage = "$cleanQuery ကို ရှာဖွေလို့ မရသေးပါဘူး။"
            )
        }
    }

    // -------------------------------------------------------------
    // 6. PLAY MEDIA (LOCAL MP3 & YOUTUBE STREAMING)
    // -------------------------------------------------------------
    fun playLocalAudioTrack(rawQuery: String): CommandResult {
        return try {
            val musicController = com.example.audio.MusicController.getInstance(context)
            val outcome = kotlinx.coroutines.runBlocking {
                musicController.playSongByVoiceIntent(rawQuery)
            }

            when (outcome) {
                is com.example.audio.VoicePlayOutcome.Success -> {
                    CommandResult(
                        isSuccess = true,
                        action = "PLAY_LOCAL_MP3",
                        target = outcome.song.title,
                        parameters = mapOf(
                            "title" to outcome.song.title,
                            "fileName" to outcome.song.fileName,
                            "filePath" to outcome.song.filePath,
                            "matchType" to outcome.matchType
                        ),
                        executionStarted = true,
                        executionConfirmed = true,
                        userMessage = outcome.userMessageMM
                    )
                }
                is com.example.audio.VoicePlayOutcome.PermissionRequired -> {
                    CommandResult(
                        isSuccess = false,
                        action = "PLAY_LOCAL_MP3",
                        target = rawQuery,
                        executionStarted = false,
                        executionConfirmed = false,
                        userMessage = outcome.userMessageMM
                    )
                }
                is com.example.audio.VoicePlayOutcome.NotFound,
                is com.example.audio.VoicePlayOutcome.Error -> {
                    // Seamless Fallback: If not found in local files, search & play on YouTube/external
                    val cleanTitle = musicController.extractSongTitleFromVoice(rawQuery)
                    playMedia(query = if (cleanTitle.isNotBlank()) cleanTitle else rawQuery)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play Local Audio Track Error", e)
            playMedia(rawQuery)
        }
    }

    fun pauseMusic(): CommandResult {
        val musicPlayer = com.example.audio.KyawGyiMusicPlayer.getInstance(context)
        val msg = musicPlayer.pause()
        return CommandResult(
            isSuccess = true,
            action = "PAUSE_MUSIC",
            target = "AUDIO_PLAYER",
            executionStarted = true,
            executionConfirmed = true,
            userMessage = msg
        )
    }

    fun stopMusic(): CommandResult {
        val musicPlayer = com.example.audio.KyawGyiMusicPlayer.getInstance(context)
        val msg = musicPlayer.stop()
        return CommandResult(
            isSuccess = true,
            action = "STOP_MUSIC",
            target = "AUDIO_PLAYER",
            executionStarted = true,
            executionConfirmed = true,
            userMessage = msg
        )
    }

    fun playMedia(query: String, artist: String? = null, title: String? = null, targetAppPackage: String = "com.google.android.youtube"): CommandResult {
        val cleanQuery = query.trim()
        val displayName = when {
            !artist.isNullOrBlank() && !title.isNullOrBlank() -> "${artist}ရဲ့ ${title} သီချင်း"
            !title.isNullOrBlank() -> "$title သီချင်း"
            !artist.isNullOrBlank() -> "${artist} သီချင်း"
            else -> cleanQuery
        }

        return try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val isYoutubeInstalled = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube") != null

            if (isYoutubeInstalled) {
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage("com.google.android.youtube")
                    putExtra("query", cleanQuery)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)

                CommandResult(
                    isSuccess = true,
                    action = "PLAY_MEDIA",
                    target = targetAppPackage,
                    parameters = mapOf(
                        "query" to cleanQuery,
                        "artist" to (artist ?: ""),
                        "title" to (title ?: "")
                    ),
                    executionStarted = true,
                    executionConfirmed = true,
                    userMessage = "ဟုတ်ကဲ့၊ $displayName ကို ဖွင့်ပေးလိုက်ပါပြီ။"
                )
            } else {
                val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)

                CommandResult(
                    isSuccess = true,
                    action = "PLAY_MEDIA_WEB_FALLBACK",
                    target = "BROWSER",
                    parameters = mapOf("query" to cleanQuery),
                    executionStarted = true,
                    executionConfirmed = true,
                    userMessage = "ဟုတ်ကဲ့၊ $displayName ကို YouTube Web တွင် ဖွင့်ပေးလိုက်ပါပြီ။"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play Media Error", e)
            val fallbackTitle = title ?: cleanQuery
            CommandResult(
                isSuccess = false,
                action = "PLAY_MEDIA",
                target = targetAppPackage,
                errorCode = "PLAYBACK_FAILED",
                userMessage = "$fallbackTitle သီချင်းကို မဖွင့်နိုင်သေးပါဘူး။ YouTube ကို စစ်ဆေးပေးပါနော်။"
            )
        }
    }

    // -------------------------------------------------------------
    // 7. CALL PHONE / CONTACT (ဖုန်းတိုက်ရိုက် ခေါ်ဆိုခြင်း - User Confirmation Required & Failure Memory)
    // -------------------------------------------------------------
    fun callContact(contactOrPhone: String, bypassConfirmation: Boolean = false): CommandResult {
        val cleanQuery = contactOrPhone.trim()
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("PHONE_CALL", cleanQuery)

        if (cleanQuery.isBlank()) {
            return CommandResult(
                isSuccess = false,
                action = "CALL_PHONE",
                target = "",
                errorCode = "EMPTY_CONTACT",
                userMessage = "ဖုန်းခေါ်ဆိုလိုသော နာမည် သို့မဟုတ် ဖုန်းနံပါတ် မသိရှိရသေးပါခင်ဗျာ。"
            )
        }

        return try {
            val contactResolver = com.example.discovery.DeviceContactResolver.getInstance(context)
            val matchResult = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                contactResolver.resolveContact(cleanQuery)
            }

            when (matchResult) {
                is com.example.discovery.ContactMatchResult.Ambiguous -> {
                    val names = matchResult.candidates.joinToString(", ") { it.displayName }
                    CommandResult(
                        isSuccess = false,
                        action = "CALL_PHONE",
                        target = cleanQuery,
                        errorCode = "AMBIGUOUS_CONTACT",
                        userMessage = "‘$cleanQuery’ ဖြင့် တူညီသော အဆက်အသွယ်များ ($names) ရှိနေပါသည်။ မည်သူ့ထံ ခေါ်ဆိုလိုပါသလဲခင်ဗျာ။",
                        failurePreWarning = preWarning
                    )
                }
                is com.example.discovery.ContactMatchResult.Found -> {
                    val displayName = matchResult.contact.displayName
                    val phone = matchResult.contact.phoneNumber

                    // Google Play & OS Security Policy: Require explicit user confirmation step
                    if (!bypassConfirmation) {
                        val coordinator = CallSmsConfirmationCoordinator.getInstance()
                        val prompt = coordinator.stageCall(cleanQuery, displayName, phone)
                        val finalPrompt = if (preWarning != null) "$preWarning\n\n$prompt" else prompt
                        return CommandResult(
                            isSuccess = true,
                            action = "CALL_PHONE",
                            target = displayName,
                            parameters = mapOf(
                                "contactName" to displayName,
                                "phoneNumber" to phone,
                                "requiresConfirmation" to "true"
                            ),
                            executionStarted = false,
                            executionConfirmed = false,
                            userMessage = finalPrompt,
                            failurePreWarning = preWarning
                        )
                    }

                    val outcome = contactResolver.makePhoneCall(
                        phoneNumber = phone,
                        contactName = displayName
                    )
                    if (!outcome.isSuccess) {
                        FailureMemoryEngine.recordFailure(
                            component = "PHONE_CALL",
                            operation = cleanQuery,
                            category = FailureCategory.TECHNICAL,
                            rootCauseMm = "ဖုန်းခေါ်ဆိုမှု စတင်ရန် မအောင်မြင်ပါ",
                            technicalDetails = outcome.userMessageMM,
                            suggestedRemediationMm = "ဖုန်းခေါ်ဆိုမှု ခွင့်ပြုချက် (CALL_PHONE) ပေးထားခြင်း ရှိမရှိ စစ်ဆေးပါ"
                        )
                    }
                    val finalMsg = if (preWarning != null) "$preWarning\n\n${outcome.userMessageMM}" else outcome.userMessageMM
                    CommandResult(
                        isSuccess = outcome.isSuccess,
                        action = "CALL_PHONE",
                        target = displayName,
                        parameters = mapOf(
                            "contactName" to displayName,
                            "phoneNumber" to phone,
                            "isDirectCall" to outcome.isDirectCall.toString(),
                            "userExplicitlyConfirmed" to "true"
                        ),
                        executionStarted = outcome.isSuccess,
                        executionConfirmed = outcome.isSuccess,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
                is com.example.discovery.ContactMatchResult.PermissionRequired,
                is com.example.discovery.ContactMatchResult.NotFound -> {
                    if (!bypassConfirmation) {
                        val coordinator = CallSmsConfirmationCoordinator.getInstance()
                        val prompt = coordinator.stageCall(cleanQuery, cleanQuery, cleanQuery)
                        val finalPrompt = if (preWarning != null) "$preWarning\n\n$prompt" else prompt
                        return CommandResult(
                            isSuccess = true,
                            action = "CALL_PHONE",
                            target = cleanQuery,
                            parameters = mapOf(
                                "contactName" to cleanQuery,
                                "phoneNumber" to cleanQuery,
                                "requiresConfirmation" to "true"
                            ),
                            executionStarted = false,
                            executionConfirmed = false,
                            userMessage = finalPrompt,
                            failurePreWarning = preWarning
                        )
                    }

                    val outcome = contactResolver.makePhoneCall(cleanQuery, cleanQuery)
                    if (!outcome.isSuccess) {
                        FailureMemoryEngine.recordFailure(
                            component = "PHONE_CALL",
                            operation = cleanQuery,
                            category = FailureCategory.RESOURCE,
                            rootCauseMm = "ဖုန်းနံပါတ် သို့မဟုတ် အဆက်အသွယ် မတွေ့ရှိပါ",
                            technicalDetails = outcome.userMessageMM,
                            suggestedRemediationMm = "ဖုန်းနံပါတ်ကို တိုက်ရိုက်ပြော၍ ခေါ်ဆိုပါ"
                        )
                    }
                    val finalMsg = if (preWarning != null) "$preWarning\n\n${outcome.userMessageMM}" else outcome.userMessageMM
                    CommandResult(
                        isSuccess = outcome.isSuccess,
                        action = "CALL_PHONE",
                        target = cleanQuery,
                        parameters = mapOf(
                            "phoneNumber" to cleanQuery,
                            "isDirectCall" to outcome.isDirectCall.toString(),
                            "userExplicitlyConfirmed" to "true"
                        ),
                        executionStarted = outcome.isSuccess,
                        executionConfirmed = outcome.isSuccess,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Call Contact Error", e)
            FailureMemoryEngine.recordFailure(
                component = "PHONE_CALL",
                operation = cleanQuery,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "ဖုန်းခေါ်ဆိုရာတွင် အမှားဖြစ်ပွားပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "ဖုန်းအက်ပ်ကို တိုက်ရိုက်ဖွင့်၍ ခေါ်ဆိုပါ"
            )
            val baseMsg = "ဖုန်းခေါ်ဆိုရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "CALL_PHONE",
                target = cleanQuery,
                errorCode = "CALL_FAILED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    /**
     * Send an SMS message or open the SMS composer with contact/phone and message pre-filled.
     * Enforces user confirmation before dispatching.
     */
    fun sendSms(contactOrPhone: String, messageText: String, bypassConfirmation: Boolean = false): CommandResult {
        val cleanQuery = contactOrPhone.trim()
        val cleanMsg = messageText.trim()
        val preWarning = FailureMemoryEngine.getRepeatWarningIfFailed("SEND_SMS", cleanQuery)

        if (cleanQuery.isBlank()) {
            return CommandResult(
                isSuccess = false,
                action = "SEND_MESSAGE",
                target = "",
                errorCode = "EMPTY_RECIPIENT",
                userMessage = "မက်ဆေ့ခ်ျ ပေးပို့လိုသော လက်ခံသူ အမည် သို့မဟုတ် ဖုန်းနံပါတ် မသိရှိရသေးပါခင်ဗျာ။"
            )
        }

        return try {
            val contactResolver = com.example.discovery.DeviceContactResolver.getInstance(context)
            val matchResult = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                contactResolver.resolveContact(cleanQuery)
            }

            when (matchResult) {
                is com.example.discovery.ContactMatchResult.Ambiguous -> {
                    val names = matchResult.candidates.joinToString(", ") { it.displayName }
                    CommandResult(
                        isSuccess = false,
                        action = "SEND_MESSAGE",
                        target = cleanQuery,
                        errorCode = "AMBIGUOUS_CONTACT",
                        userMessage = "‘$cleanQuery’ ဖြင့် တူညီသော အဆက်အသွယ်များ ($names) ရှိနေပါသည်။ မည်သူ့ထံ စာပို့လိုပါသလဲခင်ဗျာ။",
                        failurePreWarning = preWarning
                    )
                }
                is com.example.discovery.ContactMatchResult.Found -> {
                    val displayName = matchResult.contact.displayName
                    val phone = matchResult.contact.phoneNumber

                    if (!bypassConfirmation) {
                        val coordinator = CallSmsConfirmationCoordinator.getInstance()
                        val prompt = coordinator.stageSms(cleanQuery, displayName, phone, cleanMsg)
                        val finalPrompt = if (preWarning != null) "$preWarning\n\n$prompt" else prompt
                        return CommandResult(
                            isSuccess = true,
                            action = "SEND_MESSAGE",
                            target = displayName,
                            parameters = mapOf(
                                "contactName" to displayName,
                                "phoneNumber" to phone,
                                "message" to cleanMsg,
                                "requiresConfirmation" to "true"
                            ),
                            executionStarted = false,
                            executionConfirmed = false,
                            userMessage = finalPrompt,
                            failurePreWarning = preWarning
                        )
                    }

                    val outcome = contactResolver.sendSmsOrOpenComposer(
                        phoneNumber = phone,
                        message = cleanMsg,
                        contactName = displayName
                    )
                    if (!outcome.isSuccess) {
                        FailureMemoryEngine.recordFailure(
                            component = "SEND_SMS",
                            operation = cleanQuery,
                            category = FailureCategory.TECHNICAL,
                            rootCauseMm = "မက်ဆေ့ခ်ျ ပေးပို့ခြင်း မအောင်မြင်ပါ",
                            technicalDetails = outcome.userMessageMM,
                            suggestedRemediationMm = "SMS ပေးပို့ခွင့် ခွင့်ပြုချက် (SEND_SMS) စစ်ဆေးပါ"
                        )
                    }
                    val finalMsg = if (preWarning != null) "$preWarning\n\n${outcome.userMessageMM}" else outcome.userMessageMM
                    CommandResult(
                        isSuccess = outcome.isSuccess,
                        action = "SEND_MESSAGE",
                        target = displayName,
                        parameters = mapOf(
                            "contactName" to displayName,
                            "phoneNumber" to phone,
                            "message" to cleanMsg,
                            "isDirectSend" to outcome.isDirectSend.toString(),
                            "userExplicitlyConfirmed" to "true"
                        ),
                        executionStarted = outcome.isSuccess,
                        executionConfirmed = outcome.isSuccess,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
                is com.example.discovery.ContactMatchResult.PermissionRequired,
                is com.example.discovery.ContactMatchResult.NotFound -> {
                    if (!bypassConfirmation) {
                        val coordinator = CallSmsConfirmationCoordinator.getInstance()
                        val prompt = coordinator.stageSms(cleanQuery, cleanQuery, cleanQuery, cleanMsg)
                        val finalPrompt = if (preWarning != null) "$preWarning\n\n$prompt" else prompt
                        return CommandResult(
                            isSuccess = true,
                            action = "SEND_MESSAGE",
                            target = cleanQuery,
                            parameters = mapOf(
                                "contactName" to cleanQuery,
                                "phoneNumber" to cleanQuery,
                                "message" to cleanMsg,
                                "requiresConfirmation" to "true"
                            ),
                            executionStarted = false,
                            executionConfirmed = false,
                            userMessage = finalPrompt,
                            failurePreWarning = preWarning
                        )
                    }

                    val outcome = contactResolver.sendSmsOrOpenComposer(cleanQuery, cleanMsg, cleanQuery)
                    if (!outcome.isSuccess) {
                        FailureMemoryEngine.recordFailure(
                            component = "SEND_SMS",
                            operation = cleanQuery,
                            category = FailureCategory.RESOURCE,
                            rootCauseMm = "မက်ဆေ့ခ်ျ ပေးပို့မည့် ဖုန်းနံပါတ် သို့မဟုတ် အဆက်အသွယ် မတွေ့ရှိပါ",
                            technicalDetails = outcome.userMessageMM,
                            suggestedRemediationMm = "ဖုန်းနံပါတ် တိုက်ရိုက်ရိုက်ထည့်၍ ပေးပို့ပါ"
                        )
                    }
                    val finalMsg = if (preWarning != null) "$preWarning\n\n${outcome.userMessageMM}" else outcome.userMessageMM
                    CommandResult(
                        isSuccess = outcome.isSuccess,
                        action = "SEND_MESSAGE",
                        target = cleanQuery,
                        parameters = mapOf(
                            "phoneNumber" to cleanQuery,
                            "message" to cleanMsg,
                            "isDirectSend" to outcome.isDirectSend.toString(),
                            "userExplicitlyConfirmed" to "true"
                        ),
                        executionStarted = outcome.isSuccess,
                        executionConfirmed = outcome.isSuccess,
                        userMessage = finalMsg,
                        failurePreWarning = preWarning
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send SMS Error", e)
            FailureMemoryEngine.recordFailure(
                component = "SEND_SMS",
                operation = cleanQuery,
                category = FailureCategory.TECHNICAL,
                rootCauseMm = "မက်ဆေ့ခ်ျ ပေးပို့ရာတွင် အမှားဖြစ်ပွားပါသည်",
                technicalDetails = "${e.javaClass.simpleName}: ${e.localizedMessage}",
                suggestedRemediationMm = "မက်ဆေ့ခ်ျအက်ပ်မှတစ်ဆင့် တိုက်ရိုက်ပေးပို့ပါ"
            )
            val baseMsg = "မက်ဆေ့ခ်ျ ပေးပို့ရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}"
            val finalMsg = if (preWarning != null) "$preWarning\n\n$baseMsg" else baseMsg
            CommandResult(
                isSuccess = false,
                action = "SEND_MESSAGE",
                target = cleanQuery,
                errorCode = "SEND_SMS_FAILED",
                userMessage = finalMsg,
                failurePreWarning = preWarning
            )
        }
    }

    // -------------------------------------------------------------
    // 10. DIRECT SYSTEM SETTINGS & PERMISSIONS NAVIGATION
    // -------------------------------------------------------------
    fun openDirectSettings(destination: com.example.router.SettingsDestination): CommandResult {
        val success = com.example.router.DirectSettingsRouter.getInstance(context).openSettings(destination)
        val msg = if (success) {
            "ဟုတ်ကဲ့၊ ${destination.titleMM} ကို တိုက်ရိုက် ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။"
        } else {
            "ဟုတ်ကဲ့၊ စနစ် ဆက်တင် စာမျက်နှာကို ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။"
        }
        return CommandResult(
            isSuccess = success,
            action = "OPEN_SETTINGS_${destination.name}",
            target = destination.name,
            parameters = mapOf("destination" to destination.name, "title" to destination.titleMM),
            executionStarted = true,
            executionConfirmed = success,
            userMessage = msg
        )
    }

    // -------------------------------------------------------------
    // 11. SCREEN TEXT READING WITH DUAL-STAGE OCR FALLBACK
    // -------------------------------------------------------------
    fun readScreenText(): CommandResult {
        val resultText = com.example.service.KyawGyiVoiceSession.readScreenContentAloud(context)
        return CommandResult(
            isSuccess = true,
            action = "READ_SCREEN",
            target = "SCREEN_CONTENT",
            parameters = mapOf("screenText" to resultText),
            executionStarted = true,
            executionConfirmed = true,
            userMessage = resultText
        )
    }

    // -------------------------------------------------------------
    // 12. BLUETOOTH LOW ENERGY (BLE) PERIPHERAL & GATT DISPATCH
    // -------------------------------------------------------------
    fun scanBleDevices(durationMs: Long = 10000L): CommandResult {
        val bleManager = com.example.ble.BlePeripheralManager.getInstance(context)
        if (!bleManager.hasBlePermissions()) {
            return CommandResult(
                isSuccess = false,
                action = "BLE_SCAN",
                target = "BLE_PERIPHERALS",
                errorCode = "BLE_PERMISSION_REQUIRED",
                userMessage = "BLE ရှာဖွေရန် Bluetooth Scan & Connect ခွင့်ပြုချက်များ လိုအပ်ပါသည်ခင်ဗျာ။"
            )
        }
        val started = bleManager.startScan(durationMs)
        return CommandResult(
            isSuccess = started,
            action = "BLE_SCAN",
            target = "BLE_PERIPHERALS",
            parameters = mapOf("durationMs" to durationMs.toString()),
            executionStarted = started,
            executionConfirmed = started,
            userMessage = if (started) {
                "အနီးအနားရှိ Bluetooth Low Energy (BLE) စမတ်ပစ္စည်းများကို စတင်ရှာဖွေနေပါသည်ခင်ဗျာ။"
            } else {
                "BLE ရှာဖွေမှု စတင်၍ မရပါ (Bluetooth ပိတ်ထားခြင်း ဖြစ်နိုင်ပါသည်)။"
            }
        )
    }

    fun connectBleDevice(deviceAddressOrName: String): CommandResult = runBlocking {
        val bleManager = com.example.ble.BlePeripheralManager.getInstance(context)
        if (!bleManager.hasBlePermissions()) {
            return@runBlocking CommandResult(
                isSuccess = false,
                action = "BLE_CONNECT",
                target = deviceAddressOrName,
                errorCode = "BLE_PERMISSION_REQUIRED",
                userMessage = "BLE ချိတ်ဆက်ရန် Bluetooth ခွင့်ပြုချက် လိုအပ်ပါသည်ခင်ဗျာ။"
            )
        }

        val targetAddress = if (deviceAddressOrName.contains(":")) {
            deviceAddressOrName
        } else {
            val found = bleManager.uiState.value.discoveredDevices.firstOrNull {
                it.name.contains(deviceAddressOrName, ignoreCase = true)
            }
            found?.address ?: deviceAddressOrName
        }

        val success = bleManager.connect(targetAddress)
        CommandResult(
            isSuccess = success,
            action = "BLE_CONNECT",
            target = targetAddress,
            executionStarted = true,
            executionConfirmed = success,
            userMessage = if (success) {
                "BLE စမတ်ပစ္စည်း ($targetAddress) သို့ ချိတ်ဆက်နေပါသည်ခင်ဗျာ။"
            } else {
                "BLE စမတ်ပစ္စည်း ($targetAddress) သို့ ချိတ်ဆက်၍ မရနိုင်ပါခင်ဗျာ။"
            }
        )
    }

    fun disconnectBleDevice(): CommandResult {
        val bleManager = com.example.ble.BlePeripheralManager.getInstance(context)
        bleManager.disconnect()
        return CommandResult(
            isSuccess = true,
            action = "BLE_DISCONNECT",
            target = "ACTIVE_GATT",
            executionStarted = true,
            executionConfirmed = true,
            userMessage = "BLE စမတ်ပစ္စည်း ချိတ်ဆက်မှုကို ဖြတ်တောက်လိုက်ပါပြီခင်ဗျာ။"
        )
    }

    fun getBleStatus(): CommandResult {
        val state = com.example.ble.BlePeripheralManager.getInstance(context).uiState.value
        val msg = "BLE အခြေအနေ: ${state.connectionState.name}, ချိတ်ဆက်ထားသော ပစ္စည်း: ${state.connectedDeviceName ?: "မရှိပါ"}, တွေ့ရှိသော ပစ္စည်းများ: ${state.discoveredDevices.size} ခု"
        return CommandResult(
            isSuccess = true,
            action = "BLE_STATUS",
            target = "BLE_SUBSYSTEM",
            parameters = mapOf(
                "connectionState" to state.connectionState.name,
                "deviceName" to (state.connectedDeviceName ?: "None"),
                "deviceCount" to state.discoveredDevices.size.toString()
            ),
            executionStarted = true,
            executionConfirmed = true,
            userMessage = msg
        )
    }

    companion object {
        @Volatile
        private var instance: KyawGyiActionExecutor? = null

        fun getInstance(context: Context): KyawGyiActionExecutor {
            return instance ?: synchronized(this) {
                instance ?: KyawGyiActionExecutor(context.applicationContext).also { instance = it }
            }
        }
    }
}
