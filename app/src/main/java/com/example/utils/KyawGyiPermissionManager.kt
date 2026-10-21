package com.example.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

data class PermissionItemInfo(
    val id: String,
    val titleMM: String,
    val titleEN: String,
    val description: String,
    val isGranted: Boolean,
    val isCritical: Boolean,
    val category: String = "CORE", // CORE, SYSTEM, OEM, QUICK
    val openIntentAction: (Context) -> Unit
)

data class DirectSettingShortcut(
    val id: String,
    val titleMM: String,
    val titleEN: String,
    val descriptionMM: String,
    val category: String, // CORE_PERM, POWER_BG, NOTIFY_DND, QUICK_SYS
    val iconName: String,
    val isPermission: Boolean = false,
    val isGranted: Boolean = true,
    val runtimePermissionName: String? = null,
    val routeActionLabel: String = "",
    val directAction: (Context) -> Unit
)

object KyawGyiPermissionManager {

    private const val TAG = "PermissionManager"

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions.toTypedArray()
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun isDefaultAssistant(context: Context): Boolean {
        return try {
            val assistant = Settings.Secure.getString(context.contentResolver, "assistant")
            val voiceInteraction = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
            (assistant != null && assistant.contains(context.packageName, ignoreCase = true)) ||
            (voiceInteraction != null && voiceInteraction.contains(context.packageName, ignoreCase = true))
        } catch (e: Exception) {
            false
        }
    }

    fun hasAudioStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasForegroundServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSendSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
        return try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            flat?.contains(context.packageName, ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
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
     * Specific helper function to request POST_NOTIFICATIONS permission for Android 13+ (API 33+) devices,
     * ensuring Pomodoro timer notifications function correctly.
     */
    fun requestPostNotificationsPermission(
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        onAlreadyGranted: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onAlreadyGranted?.invoke()
        }
    }

    /**
     * Specific helper function to request RECORD_AUDIO permission using ActivityResultLauncher
     */
    fun requestRecordAudioPermission(
        launcher: androidx.activity.result.ActivityResultLauncher<String>
    ) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return OverlayPermissionHelper.canDrawOverlays(context)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.contains(context.packageName, ignoreCase = true) ||
                    componentNameString.contains("KyawGyiAccessibilityService", ignoreCase = true)
                ) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility status: ${e.message}")
            false
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    fun canWriteSystemSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun hasNotificationPolicyAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.isNotificationPolicyAccessGranted ?: false
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun hasAllPermissions(context: Context): Boolean {
        return hasRecordAudioPermission(context) &&
                hasOverlayPermission(context) &&
                isAccessibilityServiceEnabled(context) &&
                canWriteSystemSettings(context)
    }

    // -----------------------------------------------------------------
    // DIRECT NAVIGATION INTENTS (တိုက်ရိုက် လမ်းကြောင်းများ)
    // -----------------------------------------------------------------

    /**
     * 1. Direct App Details Settings (Permissions, Storage, Battery, Defaults)
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings: ${e.message}")
            openGeneralSettings(context)
        }
    }

    /**
     * 2. Direct Overlay Permission (Display over other apps)
     */
    fun openOverlaySettings(context: Context) {
        OverlayPermissionHelper.requestOverlayPermission(context)
    }

    /**
     * 3. Direct Accessibility Service Settings
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "စာရင်းထဲမှ 'Kyaw Gyi AI' (Accessibility Service) ကို ရှာဖွေ၍ ဖွင့်ပေးပါ", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            openGeneralSettings(context)
        }
    }

    /**
     * 4. Direct Battery Optimization Exemption Settings (Ignore Battery Optimization)
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    Toast.makeText(context, "Kyaw Gyi AI ကို ရွေးချယ်ပြီး 'Don't Optimize' (မကန့်သတ်ရန်) သတ်မှတ်ပေးပါ", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် မလိုအပ်ပါ", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 5. Direct App Notification Settings
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
                openAppDetailsSettings(context)
            }
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 5b. Direct Notification Channel Settings (e.g. Pomodoro focus alert channel)
     */
    fun openNotificationChannelSettings(context: Context, channelId: String = "pomodoro_focus_channel") {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openNotificationSettings(context)
            }
        } catch (e: Exception) {
            openNotificationSettings(context)
        }
    }

    /**
     * 6. Direct Modify System Settings (WRITE_SETTINGS)
     */
    fun openWriteSystemSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                var launched = false
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "စနစ် ဆက်တင် ပြင်ဆင်ခွင့် (Modify System Settings) Toggle ကို ဖွင့်ပေးပါ", Toast.LENGTH_LONG).show()
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "Direct package WRITE_SETTINGS failed: ${e.message}")
                }

                if (!launched) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                        Toast.makeText(context, "စာရင်းထဲမှ Kyaw Gyi AI ကို ရွေးချယ်၍ Modify System Settings ဖွင့်ပေးပါ", Toast.LENGTH_LONG).show()
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "General ACTION_MANAGE_WRITE_SETTINGS failed: ${e.message}")
                    }
                }

                if (!launched) {
                    openAppDetailsSettings(context)
                }
            } else {
                Toast.makeText(context, "စနစ် ဆက်တင် ပြင်ဆင်ခွင့် အလိုအလျောက် ရရှိပြီးဖြစ်ပါသည်", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 7. Direct Do Not Disturb (DND) / Notification Policy Access Settings
     */
    fun openNotificationPolicySettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Kyaw Gyi AI ကို ရွေးချယ်၍ Do Not Disturb ဝင်ရောက်ခွင့် ပေးပါ", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 8. Direct Default Digital Assistant App Settings
     */
    fun openDefaultAssistantSettings(context: Context) {
        try {
            var launched = false
            try {
                val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Default Digital Assistant အဖြစ် Kyaw Gyi AI ကို ရွေးချယ်ပေးပါ", Toast.LENGTH_LONG).show()
                launched = true
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_VOICE_INPUT_SETTINGS failed: ${e.message}")
            }

            if (!launched) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Default Apps မှ Digital Assistant ကို Kyaw Gyi သို့ ပြောင်းလဲပါ", Toast.LENGTH_LONG).show()
                    launched = true
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_MANAGE_DEFAULT_APPS_SETTINGS failed: ${e.message}")
                }
            }

            if (!launched) {
                openAppDetailsSettings(context)
            }
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 9. Direct OEM AutoStart & Background Execution Protection (Xiaomi, Samsung, Oppo, Vivo, Huawei)
     */
    fun openOemAutostartSettings(context: Context) {
        val oemIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
        )

        var launched = false
        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Kyaw Gyi AI အတွက် Auto-start နှင့် Background ဖွင့်ခွင့် ပေးပါ", Toast.LENGTH_LONG).show()
                launched = true
                break
            } catch (ignored: Exception) {
            }
        }

        if (!launched) {
            openBatteryOptimizationSettings(context)
        }
    }

    /**
     * 10. Direct Exact Alarm & Reminders Permission (Android 12+)
     */
    fun openExactAlarmSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Alarms & Reminders ခွင့်ပြုချက်ကို ဖွင့်ပေးပါ", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Exact Alarm ခွင့်ပြုချက် အလိုအလျောက် ရရှိထားပါသည်", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    /**
     * 11. Direct Wi-Fi Settings
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 12. Direct Bluetooth Settings
     */
    fun openBluetoothSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 13. Direct Sound & Volume Settings
     */
    fun openSoundSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 14. Direct Display & Brightness Settings
     */
    fun openDisplaySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 15. Direct Location Settings
     */
    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 16. Direct Date & Time Settings
     */
    fun openDateTimeSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DATE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 17. Direct Storage Settings
     */
    fun openStorageSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 18. Direct About Phone / Device Info Settings
     */
    fun openAboutPhoneSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 19. Direct Text-to-Speech (TTS) Settings
     */
    fun openTtsSettings(context: Context) {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 20. Direct Voice Input Settings
     */
    fun openVoiceInputSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 21. Direct Notification Listener Settings
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * 22. Direct General System Settings
     */
    fun openGeneralSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open general settings: ${e.message}")
        }
    }

    // -----------------------------------------------------------------
    // ALL PERMISSION STATUS SUMMARY LIST & SHORTCUT CATALOG
    // -----------------------------------------------------------------

    fun getAllPermissionsStatus(context: Context): List<PermissionItemInfo> {
        return listOf(
            PermissionItemInfo(
                id = "RECORD_AUDIO",
                titleMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် (Microphone)",
                titleEN = "RECORD_AUDIO",
                description = "မြန်မာစကား အသံအမိန့်များ ဖမ်းယူရန်နှင့် 'ကျော်ကြီး' Wake Word အမြဲနိုးကြားစေရန် လိုအပ်ပါသည်။",
                isGranted = hasRecordAudioPermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "READ_CONTACTS",
                titleMM = "ဖုန်းအဆက်အသွယ်များ ဖတ်ရှုခွင့် (Contacts Access)",
                titleEN = "READ_CONTACTS",
                description = "ဖုန်းထဲရှိ အဆက်အသွယ် (Contacts) များကို Scan ဖတ်ပြီး အသံဖြင့် အမည်ခေါ်၍ ဖုန်းဆက်နိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = hasContactsPermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "CALL_PHONE",
                titleMM = "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့် (Direct Call Phone)",
                titleEN = "CALL_PHONE",
                description = "စကားသံ အမိန့်ဖြင့် ဖုန်းနံပါတ် သို့မဟုတ် Contact ဆီသို့ တိုက်ရိုက် ဖုန်းခေါ်ဆိုနိုင်စေရန် လိုအပ်ပါသည်။",
                isGranted = hasCallPhonePermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "SEND_SMS",
                titleMM = "SMS စာတို တိုက်ရိုက်ပေးပို့ခွင့် (Send SMS)",
                titleEN = "SEND_SMS",
                description = "အသံအမိန့်ဖြင့် မက်ဆေ့ခ်ျ (SMS) ရေးသားပေးပို့နိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = hasSendSmsPermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "READ_MEDIA_AUDIO",
                titleMM = "သီချင်း MP3 ဖိုင်များ ဖတ်ရှုခွင့် (Music & Audio Storage)",
                titleEN = "READ_MEDIA_AUDIO / STORAGE",
                description = "ဖုန်းထဲရှိ MP3 သီချင်းဖိုင်များကို Scan ဖတ်ပြီး အသံဖြင့် နာမည်ခေါ်ကာ တိုက်ရိုက် ဖွင့်ပေးနိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = hasAudioStoragePermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "CAMERA",
                titleMM = "ကင်မရာနှင့် ဓာတ်မီး ထိန်းချုပ်ခွင့် (Camera & Flashlight)",
                titleEN = "CAMERA",
                description = "အသံဖြင့် ဓာတ်မီး အဖွင့်/အပိတ်နှင့် ကင်မရာ လုပ်ဆောင်ချက်များ စီမံနိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = hasCameraPermission(context),
                isCritical = false,
                category = "CORE",
                openIntentAction = { openAppDetailsSettings(it) }
            ),
            PermissionItemInfo(
                id = "SYSTEM_ALERT_WINDOW",
                titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Display Over Other Apps)",
                titleEN = "SYSTEM_ALERT_WINDOW",
                description = "အခြား Application များ သုံးနေချိန်နှင့် Lock Screen တွင် Assistive Touch မိုက်ခရိုဖုန်း အိုင်ကွန် အမြဲပေါ်နေစေရန် လိုအပ်ပါသည်။",
                isGranted = hasOverlayPermission(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openOverlaySettings(it) }
            ),
            PermissionItemInfo(
                id = "ACCESSIBILITY_SERVICE",
                titleMM = "စနစ်တွင်း အလိုအလျောက် ထိန်းချုပ်ခွင့် (Accessibility Service)",
                titleEN = "KyawGyiAccessibilityService",
                description = "အသံဖြင့် အက်ပ်များ အလိုအလျောက်ဖွင့်ခြင်း၊ ခလုတ်နှိပ်ခြင်း၊ Back, Home, Recent Apps များ လုပ်ဆောင်နိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = isAccessibilityServiceEnabled(context),
                isCritical = true,
                category = "CORE",
                openIntentAction = { openAccessibilitySettings(it) }
            ),
            PermissionItemInfo(
                id = "WRITE_SETTINGS",
                titleMM = "စနစ် ဆက်တင် ပြင်ဆင်ခွင့် (Modify System Settings)",
                titleEN = "WRITE_SETTINGS",
                description = "မျက်နှာပြင် အလင်းအမှောင်၊ အသံအတိုးအကျယ်နှင့် စနစ်ဆိုင်ရာ အမိန့်များကို တိုက်ရိုက် ထိန်းချုပ်နိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = canWriteSystemSettings(context),
                isCritical = true,
                category = "SYSTEM",
                openIntentAction = { openWriteSystemSettings(it) }
            ),
            PermissionItemInfo(
                id = "EXACT_ALARM",
                titleMM = "တိကျသော နှိုးစက်နှင့် သတိပေးချက် (Exact Alarms & Schedule)",
                titleEN = "SCHEDULE_EXACT_ALARM",
                description = "Pomodoro Timer နှင့် သတိပေးချက်များ Deep Sleep တွင် တိကျစွာ အချိန်မှန် အချက်ပေးစေရန် လိုအပ်ပါသည်။",
                isGranted = canScheduleExactAlarms(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openExactAlarmSettings(it) }
            ),
            PermissionItemInfo(
                id = "BATTERY_OPTIMIZATION",
                titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် (Ignore Battery Optimization)",
                titleEN = "IGNORE_BATTERY_OPTIMIZATIONS",
                description = "ဖုန်းမျက်နှာပြင် ပိတ်ထားချိန်တွင် အက်ပ်ရပ်တန့်မသွားဘဲ Background မှ အသံအမိန့်များကို အမြဲ အဆင်သင့် နားထောင်နိုင်စေရန် လိုအပ်ပါသည်။",
                isGranted = isIgnoringBatteryOptimizations(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openBatteryOptimizationSettings(it) }
            ),
            PermissionItemInfo(
                id = "OEM_AUTOSTART",
                titleMM = "ဖုန်းမျိုးစုံ Auto-Start & Background စောင့်ရှောက်ခွင့်",
                titleEN = "AUTOSTART_PROTECTION",
                description = "Xiaomi (MIUI), Samsung, Oppo, Vivo, Huawei ဖုန်းများတွင် Background Voice Wake-up မပိတ်သွားစေရန် ဖွင့်ပေးပါ။",
                isGranted = isIgnoringBatteryOptimizations(context),
                isCritical = false,
                category = "OEM",
                openIntentAction = { openOemAutostartSettings(it) }
            ),
            PermissionItemInfo(
                id = "DEFAULT_ASSISTANT",
                titleMM = "ပင်မ ဒစ်ဂျစ်တယ် အကူအက်ပ် သတ်မှတ်ခြင်း (Default Assistant)",
                titleEN = "DEFAULT_DIGITAL_ASSISTANT",
                description = "Home ခလုတ်ကို ဖိထားချိန် သို့မဟုတ် Gesture ဆွဲတင်ချိန်တွင် Kyaw Gyi AI ချက်ချင်း ပွင့်လာစေရန် သတ်မှတ်ပါ။",
                isGranted = isDefaultAssistant(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openDefaultAssistantSettings(it) }
            ),
            PermissionItemInfo(
                id = "BLUETOOTH_CONNECT",
                titleMM = "Bluetooth ချိတ်ဆက်ခွင့် (Bluetooth Permission)",
                titleEN = "BLUETOOTH_CONNECT",
                description = "Bluetooth နားကြပ်နှင့် အသံစက်ပစ္စည်းများကို အသံဖြင့် ချိတ်ဆက် ထိန်းချုပ်ရန် လိုအပ်ပါသည်။",
                isGranted = hasBluetoothPermission(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openBluetoothSettings(it) }
            ),
            PermissionItemInfo(
                id = "POST_NOTIFICATIONS",
                titleMM = "အသိပေးချက်များ ပေးပို့ခွင့် (Notification Permission)",
                titleEN = "POST_NOTIFICATIONS",
                description = "Foreground Service အခြေအနေနှင့် အသံအမိန့် လုပ်ဆောင်မှု သတိပေးချက်များ ပြသရန် လိုအပ်ပါသည်။",
                isGranted = hasNotificationPermission(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openNotificationSettings(it) }
            ),
            PermissionItemInfo(
                id = "NOTIFICATION_POLICY",
                titleMM = "အနှောင့်အယှက် မပေးရ စနစ် ထိန်းချုပ်ခွင့် (Do Not Disturb Access)",
                titleEN = "NOTIFICATION_POLICY_ACCESS",
                description = "အသံတိတ် စနစ် (Mute) နှင့် အသံအမြင့်ဆုံး အမိန့်များ တိကျစွာ လွှမ်းမိုး ထိန်းချုပ်နိုင်ရန် လိုအပ်ပါသည်။",
                isGranted = hasNotificationPolicyAccess(context),
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openNotificationPolicySettings(it) }
            ),
            PermissionItemInfo(
                id = "APP_DETAILS",
                titleMM = "အက်ပ် အသေးစိတ် ဆက်တင် (App Info Settings)",
                titleEN = "APPLICATION_DETAILS_SETTINGS",
                description = "အက်ပ်၏ ခွင့်ပြုချက်အားလုံး၊ Storage နှင့် ဒေတာများကို တိုက်ရိုက် စီမံခန့်ခွဲနိုင်သော ပင်မစာမျက်နှာ ဖြစ်ပါသည်။",
                isGranted = true,
                isCritical = false,
                category = "SYSTEM",
                openIntentAction = { openAppDetailsSettings(it) }
            )
        )
    }

    /**
     * Complete list of direct setting shortcuts for one-tap system routing
     */
    fun getAllDirectShortcuts(context: Context): List<DirectSettingShortcut> {
        return listOf(
            // Core
            DirectSettingShortcut(
                id = "SHORTCUT_APP_DETAILS",
                titleMM = "အက်ပ် အသေးစိတ် စာမျက်နှာ",
                titleEN = "App Info & Permissions",
                descriptionMM = "ခွင့်ပြုချက်အားလုံး၊ ဒေတာနှင့် Storage စီမံခန့်ခွဲမှု တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Info",
                isPermission = false,
                isGranted = true,
                routeActionLabel = "ACTION_APPLICATION_DETAILS_SETTINGS (package:${context.packageName})",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_MIC",
                titleMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက်",
                titleEN = "Microphone Permission (RECORD_AUDIO)",
                descriptionMM = "မြန်မာစကား အသံအမိန့်များ ဖမ်းယူရန် တိုက်ရိုက်ခွင့်ပြုချက် လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Mic",
                isPermission = true,
                isGranted = hasRecordAudioPermission(context),
                runtimePermissionName = Manifest.permission.RECORD_AUDIO,
                routeActionLabel = "Runtime: RECORD_AUDIO -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_CONTACTS",
                titleMM = "ဖုန်းအဆက်အသွယ်များ ရှာဖွေခွင့်",
                titleEN = "Read Contacts Permission (READ_CONTACTS)",
                descriptionMM = "ဖုန်းထဲရှိ Contacts များကို အသံဖြင့်ခေါ်ဆိုရန် ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Contacts",
                isPermission = true,
                isGranted = hasContactsPermission(context),
                runtimePermissionName = Manifest.permission.READ_CONTACTS,
                routeActionLabel = "Runtime: READ_CONTACTS -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_CALL_PHONE",
                titleMM = "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့်",
                titleEN = "Call Phone Permission (CALL_PHONE)",
                descriptionMM = "အသံဖြင့် ဖုန်းတိုက်ရိုက်ခေါ်ဆိုရန် ခွင့်ပြုချက် လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Phone",
                isPermission = true,
                isGranted = hasCallPhonePermission(context),
                runtimePermissionName = Manifest.permission.CALL_PHONE,
                routeActionLabel = "Runtime: CALL_PHONE -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_SEND_SMS",
                titleMM = "SMS စာတို ပေးပို့ခွင့်",
                titleEN = "Send SMS Permission (SEND_SMS)",
                descriptionMM = "အသံဖြင့် SMS စာတိုပေးပို့ရန် ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Sms",
                isPermission = true,
                isGranted = hasSendSmsPermission(context),
                runtimePermissionName = Manifest.permission.SEND_SMS,
                routeActionLabel = "Runtime: SEND_SMS -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_CAMERA",
                titleMM = "ကင်မရာနှင့် ဓာတ်မီး ခွင့်ပြုချက်",
                titleEN = "Camera Permission (CAMERA)",
                descriptionMM = "Flashlight ဓာတ်မီး အဖွင့်/အပိတ်နှင့် ကင်မရာ ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "CameraAlt",
                isPermission = true,
                isGranted = hasCameraPermission(context),
                runtimePermissionName = Manifest.permission.CAMERA,
                routeActionLabel = "Runtime: CAMERA -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_AUDIO_STORAGE",
                titleMM = "သီချင်းနှင့် အသံဖိုင်များ ဖတ်ရှုခွင့်",
                titleEN = "Audio Storage (READ_MEDIA_AUDIO)",
                descriptionMM = "ဖုန်းထဲရှိ MP3 သီချင်းများကို အသံဖြင့် ရှာဖွေဖွင့်လှစ်ရန် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "LibraryMusic",
                isPermission = true,
                isGranted = hasAudioStoragePermission(context),
                runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
                routeActionLabel = "Runtime: READ_MEDIA_AUDIO -> App Details Deep-Link",
                directAction = { openAppDetailsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_OVERLAY",
                titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့်",
                titleEN = "Display Over Other Apps",
                descriptionMM = "Floating Assistant နှင့် Overlay ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Layers",
                isPermission = true,
                isGranted = hasOverlayPermission(context),
                routeActionLabel = "ACTION_MANAGE_OVERLAY_PERMISSION (package:${context.packageName})",
                directAction = { openOverlaySettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_ACCESSIBILITY",
                titleMM = "Accessibility အလိုအလျောက် ထိန်းချုပ်မှု",
                titleEN = "Accessibility Service",
                descriptionMM = "Kyaw Gyi AI Accessibility ဝန်ဆောင်မှု ဖွင့်/ပိတ် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "AccessibilityNew",
                isPermission = true,
                isGranted = isAccessibilityServiceEnabled(context),
                routeActionLabel = "ACTION_ACCESSIBILITY_SETTINGS",
                directAction = { openAccessibilitySettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_WRITE_SETTINGS",
                titleMM = "စနစ် ဆက်တင် ပြင်ဆင်ခွင့်",
                titleEN = "Modify System Settings",
                descriptionMM = "မျက်နှာပြင် အလင်းနှင့် စနစ် ပြင်ဆင်ခွင့် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "SettingsSuggest",
                isPermission = true,
                isGranted = canWriteSystemSettings(context),
                routeActionLabel = "ACTION_MANAGE_WRITE_SETTINGS (package:${context.packageName})",
                directAction = { openWriteSystemSettings(it) }
            ),
            // Power & Background
            DirectSettingShortcut(
                id = "SHORTCUT_BATTERY_OPT",
                titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
                titleEN = "Ignore Battery Optimization",
                descriptionMM = "Deep Sleep အိပ်မောကျချိန် Foreground အဆက်မပြတ် နိုးကြားခွင့်",
                category = "POWER_BG",
                iconName = "BatteryChargingFull",
                isPermission = true,
                isGranted = isIgnoringBatteryOptimizations(context),
                directAction = { openBatteryOptimizationSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_OEM_AUTOSTART",
                titleMM = "OEM Auto-Start စောင့်ရှောက်မှု",
                titleEN = "Auto-Start & Background",
                descriptionMM = "Xiaomi / Samsung / Oppo / Vivo ဖုန်းများ၏ Background Process ကာကွယ်မှု",
                category = "POWER_BG",
                iconName = "Shield",
                isPermission = false,
                isGranted = isIgnoringBatteryOptimizations(context),
                directAction = { openOemAutostartSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_EXACT_ALARM",
                titleMM = "တိကျသော Alarm & Schedule",
                titleEN = "Exact Alarms & Reminders",
                descriptionMM = "Pomodoro နှင့် အချိန်တိကျ သတိပေးချက်များ ခွင့်ပြုချက်",
                category = "POWER_BG",
                iconName = "Alarm",
                isPermission = true,
                isGranted = canScheduleExactAlarms(context),
                directAction = { openExactAlarmSettings(it) }
            ),
            // Notification & DND
            DirectSettingShortcut(
                id = "SHORTCUT_NOTIF_SETTINGS",
                titleMM = "အသိပေးချက် ဆက်တင်",
                titleEN = "App Notifications",
                descriptionMM = "အသံနှင့် တုန်ခါမှု အသိပေးချက် အသေးစိတ် ဆက်တင် လမ်းကြောင်း",
                category = "NOTIFY_DND",
                iconName = "Notifications",
                isPermission = true,
                isGranted = hasNotificationPermission(context),
                directAction = { openNotificationSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_POMODORO_CHANNEL",
                titleMM = "Pomodoro Focus Channel ဆက်တင်",
                titleEN = "Pomodoro Channel",
                descriptionMM = "Pomodoro အချိန်ကိုက် Notification Channel အသံနှင့် ဦးစားပေးသတ်မှတ်ချက်",
                category = "NOTIFY_DND",
                iconName = "Timer",
                isPermission = false,
                isGranted = true,
                directAction = { openNotificationChannelSettings(it, "pomodoro_focus_channel") }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_DND_ACCESS",
                titleMM = "Do Not Disturb (DND) ထိန်းချုပ်ခွင့်",
                titleEN = "Do Not Disturb Policy",
                descriptionMM = "အသံတိတ် စနစ်နှင့် အနှောင့်အယှက်ကင်းမဲ့ စနစ် ဝင်ရောက်ခွင့် လမ်းကြောင်း",
                category = "NOTIFY_DND",
                iconName = "DoNotDisturbOn",
                isPermission = true,
                isGranted = hasNotificationPolicyAccess(context),
                directAction = { openNotificationPolicySettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_TTS",
                titleMM = "အသံထွက်ဖတ်ကြားမှု (TTS Engine) ဆက်တင်",
                titleEN = "Text-to-Speech Settings",
                descriptionMM = "မြန်မာဘာသာ အသံထွက် engine နှင့် စကားပြောနှုန်း တိုက်ရိုက်သတ်မှတ်ရန်",
                category = "CORE_PERM",
                iconName = "VolumeUp",
                isPermission = false,
                isGranted = true,
                directAction = { openTtsSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_VOICE_INPUT",
                titleMM = "အသံသွင်းယူမှု (Voice Input) ဆက်တင်",
                titleEN = "Voice Input Settings",
                descriptionMM = "Google Speech Recognition & Voice Typing စီမံခန့်ခွဲရန်",
                category = "CORE_PERM",
                iconName = "Mic",
                isPermission = false,
                isGranted = true,
                directAction = { openVoiceInputSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_NOTIF_LISTENER",
                titleMM = "အသိပေးချက်များ ဖတ်ရှုခွင့်",
                titleEN = "Notification Listener Access",
                descriptionMM = "ဝင်လာသော သတိပေးချက်များကို အသံဖြင့် ဖတ်ကြားစေရန် ဝင်ရောက်ခွင့် လမ်းကြောင်း",
                category = "NOTIFY_DND",
                iconName = "Notifications",
                isPermission = true,
                isGranted = hasNotificationListenerAccess(context),
                directAction = { openNotificationListenerSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_DEFAULT_ASSISTANT",
                titleMM = "Default Digital Assistant",
                titleEN = "Voice & Assist App",
                descriptionMM = "ပင်မ ဒစ်ဂျစ်တယ် အကူအက်ပ်အဖြစ် သတ်မှတ်ရန် တိုက်ရိုက်လမ်းကြောင်း",
                category = "CORE_PERM",
                iconName = "Assistant",
                isPermission = false,
                isGranted = isDefaultAssistant(context),
                directAction = { openDefaultAssistantSettings(it) }
            ),
            // Quick System Settings
            DirectSettingShortcut(
                id = "SHORTCUT_WIFI",
                titleMM = "Wi-Fi ဆက်တင်",
                titleEN = "Wi-Fi Settings",
                descriptionMM = "စနစ် Wi-Fi ချိတ်ဆက်မှု စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "Wifi",
                isPermission = false,
                isGranted = true,
                directAction = { openWifiSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_BLUETOOTH",
                titleMM = "Bluetooth ဆက်တင်",
                titleEN = "Bluetooth Settings",
                descriptionMM = "Bluetooth ချိတ်ဆက်မှု စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "Bluetooth",
                isPermission = true,
                isGranted = hasBluetoothPermission(context),
                directAction = { openBluetoothSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_SOUND",
                titleMM = "အသံနှင့် အတိုးအကျယ် ဆက်တင်",
                titleEN = "Sound & Vibration",
                descriptionMM = "စနစ် အသံအတိုးအကျယ်နှင့် တုန်ခါမှု စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "VolumeUp",
                isPermission = false,
                isGranted = true,
                directAction = { openSoundSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_DISPLAY",
                titleMM = "မျက်နှာပြင် အလင်းနှင့် Display",
                titleEN = "Display & Brightness",
                descriptionMM = "မျက်နှာပြင် အလင်းအမှောင်နှင့် Display ဆက်တင် တိုက်ရိုက်လမ်းကြောင်း",
                category = "QUICK_SYS",
                iconName = "BrightnessMedium",
                isPermission = false,
                isGranted = true,
                directAction = { openDisplaySettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_LOCATION",
                titleMM = "တည်နေရာ (Location) ဆက်တင်",
                titleEN = "Location Settings",
                descriptionMM = "GPS တည်နေရာ ဝန်ဆောင်မှု စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "LocationOn",
                isPermission = false,
                isGranted = hasLocationPermission(context),
                directAction = { openLocationSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_DATETIME",
                titleMM = "ရက်စွဲနှင့် အချိန် ဆက်တင်",
                titleEN = "Date & Time Settings",
                descriptionMM = "ဖုန်း၏ နေ့စွဲနှင့် အချိန် စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "AccessTime",
                isPermission = false,
                isGranted = true,
                directAction = { openDateTimeSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_STORAGE",
                titleMM = "Storage ဒေတာ ဆက်တင်",
                titleEN = "Internal Storage",
                descriptionMM = "ဖုန်း Storage နှင့် မန်မိုရီ စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "Storage",
                isPermission = false,
                isGranted = true,
                directAction = { openStorageSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_ABOUT_PHONE",
                titleMM = "ဖုန်း အချက်အလက် (About Phone)",
                titleEN = "Device Info Settings",
                descriptionMM = "စနစ်နှင့် ဖုန်းအချက်အလက် စာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "PermDeviceInformation",
                isPermission = false,
                isGranted = true,
                directAction = { openAboutPhoneSettings(it) }
            ),
            DirectSettingShortcut(
                id = "SHORTCUT_GENERAL",
                titleMM = "Android ပင်မ ဆက်တင်",
                titleEN = "System Main Settings",
                descriptionMM = "ဖုန်း၏ အထွေထွေ ဆက်တင် ပင်မစာမျက်နှာသို့ တိုက်ရိုက်သွားရောက်ရန်",
                category = "QUICK_SYS",
                iconName = "Settings",
                isPermission = false,
                isGranted = true,
                directAction = { openGeneralSettings(it) }
            )
        )
    }
}
