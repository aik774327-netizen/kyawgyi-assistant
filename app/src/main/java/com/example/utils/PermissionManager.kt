package com.example.utils

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Data representation of an app permission item for UI display & runtime requests.
 */
data class AppPermission(
    val id: String,
    val permissionManifest: String?,
    val titleMM: String,
    val titleEN: String,
    val descriptionMM: String,
    val isGranted: Boolean,
    val isCritical: Boolean = true,
    val isSpecialPermission: Boolean = false,
    val category: PermissionCategory = PermissionCategory.CORE
)

enum class PermissionCategory {
    CORE,
    NOTIFICATION,
    BACKGROUND_POWER,
    SYSTEM_CONTROL
}

/**
 * Comprehensive PermissionManager utility class encapsulating logic for checking,
 * requesting, and handling runtime and special permissions via ActivityResultLauncher.
 */
object PermissionManager {

    private const val TAG = "PermissionManager"

    // Standard Permissions
    const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    // -------------------------------------------------------------------------
    // 1. Permission Check Methods
    // -------------------------------------------------------------------------

    /**
     * Checks if a standard runtime permission is granted.
     */
    fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return checkPermission(context, permission)
    }

    /**
     * Checks multiple runtime permissions.
     */
    fun checkPermissions(context: Context, permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { checkPermission(context, it) }
    }

    /**
     * Specific check for Microphone (RECORD_AUDIO)
     */
    fun isRecordAudioGranted(context: Context): Boolean {
        return checkPermission(context, Manifest.permission.RECORD_AUDIO)
    }

    fun isContactsGranted(context: Context): Boolean {
        return checkPermission(context, Manifest.permission.READ_CONTACTS)
    }

    fun isCallPhoneGranted(context: Context): Boolean {
        return checkPermission(context, Manifest.permission.CALL_PHONE)
    }

    fun isCameraGranted(context: Context): Boolean {
        return checkPermission(context, Manifest.permission.CAMERA)
    }

    fun isAudioStorageGranted(context: Context): Boolean {
        return KyawGyiPermissionManager.hasAudioStoragePermission(context)
    }

    fun isBluetoothGranted(context: Context): Boolean {
        return KyawGyiPermissionManager.hasBluetoothPermission(context)
    }

    fun isDefaultAssistant(context: Context): Boolean {
        return KyawGyiPermissionManager.isDefaultAssistant(context)
    }

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        return KyawGyiPermissionManager.hasNotificationPolicyAccess(context)
    }

    /**
     * Specific check for Notification (POST_NOTIFICATIONS on Android 13+ / API 33+)
     * Returns true on Android 12 and below as runtime permission is not required.
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.areNotificationsEnabled() ?: true
        }
    }

    /**
     * Specific check for System Overlay (SYSTEM_ALERT_WINDOW)
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Check if Kyaw Gyi Accessibility Service is running
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return KyawGyiPermissionManager.isAccessibilityServiceEnabled(context)
    }

    /**
     * Check if System Settings (WRITE_SETTINGS) can be modified
     */
    fun canWriteSystemSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    /**
     * Check if App is exempt from Battery Optimization (Unrestricted background)
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } else {
            true
        }
    }

    /**
     * Check if exact alarms and timer notifications can be scheduled (Android 12+)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    /**
     * Check if all critical permissions required by the assistant and Pomodoro timer are granted.
     */
    fun hasAllCriticalPermissions(context: Context): Boolean {
        return isRecordAudioGranted(context) &&
                isOverlayPermissionGranted(context) &&
                isNotificationPermissionGranted(context)
    }

    // -------------------------------------------------------------------------
    // 2. Request Helpers via ActivityResultLauncher & Direct System Routing
    // -------------------------------------------------------------------------

    /**
     * Specific helper function to request POST_NOTIFICATIONS permission for Android 13+ (API 33+) devices,
     * ensuring Pomodoro timer notifications and background alerts function correctly.
     */
    fun requestPostNotificationsPermission(
        launcher: ActivityResultLauncher<String>,
        onAlreadyGranted: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onAlreadyGranted?.invoke()
        }
    }

    /**
     * Helper to request POST_NOTIFICATIONS or route to Notification Settings if on older OS.
     */
    fun requestPostNotifications(
        context: Context,
        launcher: ActivityResultLauncher<String>? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && launcher != null) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }

    /**
     * Helper to request Record Audio (Microphone) permission via ActivityResultLauncher.
     */
    fun requestRecordAudioPermission(launcher: ActivityResultLauncher<String>) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Helper to request multiple runtime permissions via ActivityResultLauncher.
     */
    fun requestMultiplePermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>
    ) {
        launcher.launch(permissions)
    }

    /**
     * Helper to handle the results of multiple runtime permission requests cleanly.
     */
    fun handlePermissionResult(
        permissions: Map<String, Boolean>,
        onAllGranted: () -> Unit,
        onDenied: (deniedPermissions: List<String>) -> Unit
    ) {
        val deniedList = permissions.filter { !it.value }.keys.toList()
        if (deniedList.isEmpty()) {
            onAllGranted()
        } else {
            onDenied(deniedList)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Special Permission Direct Navigation Handlers
    // -------------------------------------------------------------------------

    /**
     * Launch System Overlay Settings
     */
    fun requestOverlayPermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Overlay Settings: ${e.message}")
            openAppSettings(context)
        }
    }

    /**
     * Launch Modify System Settings (WRITE_SETTINGS)
     */
    fun requestWriteSettingsPermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Write Settings: ${e.message}")
            openAppSettings(context)
        }
    }

    /**
     * Launch Accessibility Settings
     */
    fun requestAccessibilityPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Launch Battery Optimization Exemption dialog/settings
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Launch Schedule Exact Alarm Settings (Android 12+)
     */
    fun requestExactAlarmPermission(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Open App Details / Info Settings
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open App Settings: ${e.message}")
        }
    }

    /**
     * Open App Notification Settings
     */
    fun openNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppSettings(context)
            }
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    // -------------------------------------------------------------------------
    // 4. Comprehensive Permission List Generator for UI
    // -------------------------------------------------------------------------

    fun getRequiredPermissionsList(context: Context): List<AppPermission> {
        return listOf(
            AppPermission(
                id = "RECORD_AUDIO",
                permissionManifest = Manifest.permission.RECORD_AUDIO,
                titleMM = "မိုက်ခရိုဖုန်း အသံဖမ်းခွင့်",
                titleEN = "Microphone (RECORD_AUDIO)",
                descriptionMM = "မြန်မာစကားသံ နားထောင်ခြင်း၊ Wake Word (ကျော်ကြီး) နှင့် AI စကားပြောရန် လိုအပ်ပါသည်။",
                isGranted = isRecordAudioGranted(context),
                isCritical = true,
                isSpecialPermission = false,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "POST_NOTIFICATIONS",
                permissionManifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                titleMM = "အသိပေးချက်များ ပေးပို့ခွင့်",
                titleEN = "Notifications (POST_NOTIFICATIONS)",
                descriptionMM = "Pomodoro Focus Timer နှင့် အရေးကြီး Background အချက်ပေးသံများ ပြသရန် လိုအပ်ပါသည်။",
                isGranted = isNotificationPermissionGranted(context),
                isCritical = true,
                isSpecialPermission = false,
                category = PermissionCategory.NOTIFICATION
            ),
            AppPermission(
                id = "SYSTEM_ALERT_WINDOW",
                permissionManifest = null,
                titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့်",
                titleEN = "Display Over Other Apps (Overlay)",
                descriptionMM = "အခြား App များပေါ်တွင် Kyaw Gyi Floating Assistant Avatar ပေါ်စေရန် လိုအပ်ပါသည်။",
                isGranted = isOverlayPermissionGranted(context),
                isCritical = true,
                isSpecialPermission = true,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "ACCESSIBILITY_SERVICE",
                permissionManifest = null,
                titleMM = "Accessibility အလိုအလျောက် ထိန်းချုပ်ခွင့်",
                titleEN = "Accessibility Automation Service",
                descriptionMM = "ဖုန်းမျက်နှာပြင်ကို အသံဖြင့် အလိုအလျောက် ထိန်းချုပ်/နှိပ်/ရှာဖွေနိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = isAccessibilityServiceEnabled(context),
                isCritical = false,
                isSpecialPermission = true,
                category = PermissionCategory.SYSTEM_CONTROL
            ),
            AppPermission(
                id = "WRITE_SETTINGS",
                permissionManifest = null,
                titleMM = "စနစ် ဆက်တင် ပြင်ဆင်ခွင့်",
                titleEN = "Modify System Settings (WRITE_SETTINGS)",
                descriptionMM = "မျက်နှာပြင် အလင်းအမှောင် (Brightness) နှင့် စနစ် အသံများကို တိုက်ရိုက် ချိန်ညှိနိုင်ရန်။",
                isGranted = canWriteSystemSettings(context),
                isCritical = false,
                isSpecialPermission = true,
                category = PermissionCategory.SYSTEM_CONTROL
            ),
            AppPermission(
                id = "SCHEDULE_EXACT_ALARM",
                permissionManifest = null,
                titleMM = "တိကျသော နှိုးစက်နှင့် Timer သတိပေးချက်",
                titleEN = "Exact Alarms & Schedule",
                descriptionMM = "Deep Sleep အခြေအနေတွင် Pomodoro Timer အချိန်တိကျစွာ အချက်ပေးနိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = canScheduleExactAlarms(context),
                isCritical = false,
                isSpecialPermission = true,
                category = PermissionCategory.NOTIFICATION
            ),
            AppPermission(
                id = "READ_CONTACTS",
                permissionManifest = Manifest.permission.READ_CONTACTS,
                titleMM = "ဖုန်းအဆက်အသွယ်များ ဖတ်ရှုခွင့်",
                titleEN = "Read Contacts (READ_CONTACTS)",
                descriptionMM = "အသံဖြင့် ဖုန်းအဆက်အသွယ်များ ရှာဖွေခေါ်ဆိုရန် လိုအပ်ပါသည်။",
                isGranted = isContactsGranted(context),
                isCritical = false,
                isSpecialPermission = false,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "CALL_PHONE",
                permissionManifest = Manifest.permission.CALL_PHONE,
                titleMM = "ဖုန်းခေါ်ဆိုခွင့်",
                titleEN = "Direct Phone Call (CALL_PHONE)",
                descriptionMM = "မြန်မာအသံအမိန့်ဖြင့် ဖုန်းတိုက်ရိုက်ခေါ်ဆိုရန် လိုအပ်ပါသည်။",
                isGranted = isCallPhoneGranted(context),
                isCritical = false,
                isSpecialPermission = false,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "CAMERA",
                permissionManifest = Manifest.permission.CAMERA,
                titleMM = "ကင်မရာနှင့် ဓာတ်မီး ထိန်းချုပ်ခွင့်",
                titleEN = "Camera & Flashlight (CAMERA)",
                descriptionMM = "Flashlight ဓာတ်မီး အဖွင့်/အပိတ်နှင့် ကင်မရာ ဖွင့်လှစ်ရန် လိုအပ်ပါသည်။",
                isGranted = isCameraGranted(context),
                isCritical = false,
                isSpecialPermission = false,
                category = PermissionCategory.SYSTEM_CONTROL
            ),
            AppPermission(
                id = "READ_MEDIA_AUDIO",
                permissionManifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
                titleMM = "စက်တွင်း အသံဖိုင်များ ဖတ်ရှုခွင့်",
                titleEN = "Audio Media Storage",
                descriptionMM = "စက်တွင်းရှိ သီချင်းများနှင့် အသံဖိုင်များကို ရှာဖွေဖွင့်လှစ်ရန် လိုအပ်ပါသည်။",
                isGranted = isAudioStorageGranted(context),
                isCritical = false,
                isSpecialPermission = false,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "BLUETOOTH_CONNECT",
                permissionManifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null,
                titleMM = "Bluetooth ချိတ်ဆက်ခွင့်",
                titleEN = "Bluetooth Connect",
                descriptionMM = "Bluetooth နားကြပ်နှင့် စမတ်ပစ္စည်းများ ချိတ်ဆက်ထိန်းချုပ်ရန် လိုအပ်ပါသည်။",
                isGranted = isBluetoothGranted(context),
                isCritical = false,
                isSpecialPermission = false,
                category = PermissionCategory.SYSTEM_CONTROL
            ),
            AppPermission(
                id = "DEFAULT_ASSISTANT",
                permissionManifest = null,
                titleMM = "ပင်မ ဒစ်ဂျစ်တယ် အကူအက်ပ် သတ်မှတ်ခြင်း",
                titleEN = "Default Digital Assistant",
                descriptionMM = "Home ခလုတ်ကို ဖိထားချိန် Kyaw Gyi AI ချက်ချင်း ပွင့်လာစေရန် သတ်မှတ်ပါ။",
                isGranted = isDefaultAssistant(context),
                isCritical = false,
                isSpecialPermission = true,
                category = PermissionCategory.CORE
            ),
            AppPermission(
                id = "ACCESS_NOTIFICATION_POLICY",
                permissionManifest = null,
                titleMM = "အနှောင့်အယှက် မပေးရ စနစ် (DND) ထိန်းချုပ်ခွင့်",
                titleEN = "Do Not Disturb Policy Access",
                descriptionMM = "အသံတိတ် စနစ် (Mute) နှင့် အသံအတိုးအကျယ် စနစ်များ တိကျစွာ ထိန်းချုပ်ရန် လိုအပ်ပါသည်။",
                isGranted = hasNotificationPolicyAccess(context),
                isCritical = false,
                isSpecialPermission = true,
                category = PermissionCategory.SYSTEM_CONTROL
            ),
            AppPermission(
                id = "IGNORE_BATTERY_OPTIMIZATIONS",
                permissionManifest = null,
                titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
                titleEN = "Ignore Battery Optimization (Unrestricted)",
                descriptionMM = "ဖုန်း Screen ပိတ်ထားချိန်နှင့် အိပ်မောကျချိန်တွင် Voice Assistant မရပ်တန့်စေရန် လိုအပ်ပါသည်။",
                isGranted = isIgnoringBatteryOptimizations(context),
                isCritical = true,
                isSpecialPermission = true,
                category = PermissionCategory.BACKGROUND_POWER
            )
        )
    }
}
