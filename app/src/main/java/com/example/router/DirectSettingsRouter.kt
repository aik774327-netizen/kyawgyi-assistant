package com.example.router

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.OverlayPermissionHelper

/**
 * Direct Route Execution Report
 * Provides verified telemetry on which Android Settings / Permission route was launched,
 * whether a package-scoped URI deep-link was used, and the current grant status.
 */
data class DirectRouteExecutionReport(
    val destination: SettingsDestination,
    val resolvedIntentAction: String,
    val usedPackageUriDeepLink: Boolean,
    val usedFallbackRoute: Boolean,
    val dispatchSucceeded: Boolean,
    val isCurrentlyGranted: Boolean,
    val verificationOutcome: String, // VERIFIED_GRANTED, INTENT_DISPATCHED_AWAITING_USER, SAFE_FAIL
    val messageMM: String
)

/**
 * Settings Destination Enum with localized descriptions, categories, runtime permission bindings,
 * and direct Android Intent Actions.
 */
enum class SettingsDestination(
    val titleMM: String,
    val titleEN: String,
    val descriptionMM: String,
    val category: SettingsCategory,
    val isPermission: Boolean = false,
    val intentAction: String,
    val runtimePermissionName: String? = null,
    val requiresPackageUri: Boolean = false
) {
    APPLICATION_DETAILS(
        "အက်ပ် အသေးစိတ်နှင့် ခွင့်ပြုချက်များ",
        "App Info & Permissions",
        "မိုက်ခရိုဖုန်း၊ ဖုန်းခေါ်ဆိုမှုနှင့် ကင်မရာ ခွင့်ပြုချက်များ စီမံခန့်ခွဲရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = false,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.RECORD_AUDIO,
        requiresPackageUri = true
    ),
    MICROPHONE_PERMISSION(
        "မိုက်ခရိုဖုန်း အသံဖမ်းယူခွင့်",
        "Microphone (RECORD_AUDIO)",
        "မြန်မာ အသံအမိန့်များနှင့် 'ကျော်ကြီး' Wake Word ဖမ်းယူရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.RECORD_AUDIO,
        requiresPackageUri = true
    ),
    CALL_PHONE_PERMISSION(
        "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့်",
        "Direct Phone Call (CALL_PHONE)",
        "အသံအမိန့်ဖြင့် ဖုန်းနံပါတ် သို့မဟုတ် Contact ထံ တိုက်ရိုက် ဖုန်းခေါ်ဆိုရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.CALL_PHONE,
        requiresPackageUri = true
    ),
    SEND_SMS_PERMISSION(
        "SMS စာတို ပေးပို့ခွင့်",
        "Send SMS (SEND_SMS)",
        "အသံအမိန့်ဖြင့် မက်ဆေ့ခ်ျ (SMS) ရေးသားပေးပို့နိုင်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.SEND_SMS,
        requiresPackageUri = true
    ),
    CONTACTS_PERMISSION(
        "ဖုန်းအဆက်အသွယ်များ ဖတ်ရှုခွင့်",
        "Contacts Access (READ_CONTACTS)",
        "ဖုန်းထဲရှိ အဆက်အသွယ် (Contacts) အမည်များဖြင့် ဖုန်းခေါ်ဆိုနိုင်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.READ_CONTACTS,
        requiresPackageUri = true
    ),
    CAMERA_PERMISSION(
        "ကင်မရာနှင့် ဓာတ်မီး ထိန်းချုပ်ခွင့်",
        "Camera & Flashlight (CAMERA)",
        "အသံဖြင့် ဓာတ်မီး (Flashlight) အဖွင့်/အပိတ်နှင့် ကင်မရာ စီမံရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.CAMERA,
        requiresPackageUri = true
    ),
    AUDIO_STORAGE_PERMISSION(
        "သီချင်းနှင့် အသံဖိုင်များ ဖတ်ရှုခွင့်",
        "Audio & Music Storage",
        "ဖုန်းထဲရှိ MP3 သီချင်းများကို ရှာဖွေ၍ အသံဖြင့် တိုက်ရိုက်ဖွင့်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
        requiresPackageUri = true
    ),
    BLUETOOTH_PERMISSION(
        "ဘလူးတုသ် ချိတ်ဆက်မှု ခွင့်ပြုချက်",
        "Bluetooth Connect (BLUETOOTH_CONNECT)",
        "Android 12+ တွင် ဘလူးတုသ် နားကြပ်နှင့် ကိရိယာများ ထိန်းချုပ်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            null
        },
        requiresPackageUri = true
    ),
    LOCATION_PERMISSION(
        "တည်နေရာ ခွင့်ပြုချက်",
        "Location Access (ACCESS_FINE_LOCATION)",
        "တည်နေရာနှင့် မြေပုံ လမ်းညွှန်အမိန့်များ အသုံးပြုရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        runtimePermissionName = Manifest.permission.ACCESS_FINE_LOCATION,
        requiresPackageUri = true
    ),
    OVERLAY_PERMISSION(
        "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့်",
        "Display Over Other Apps (Overlay)",
        "အခြား App များ သုံးနေစဉ် Floating Mic Ball ပေါ်နေစေရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        requiresPackageUri = true
    ),
    ACCESSIBILITY(
        "စနစ်တွင်း အလိုအလျောက် ထိန်းချုပ်ခွင့်",
        "Accessibility Service",
        "အသံဖြင့် Back, Home, Recent Apps နှင့် အက်ပ်များ အလိုအလျောက် ဖွင့်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        requiresPackageUri = false
    ),
    WRITE_SETTINGS(
        "စနစ် ဆက်တင်များ ပြင်ဆင်ခွင့်",
        "Modify System Settings",
        "မျက်နှာပြင် အလင်းအမှောင်၊ အသံအတိုးအကျယ်နှင့် စနစ်အမိန့်များ ထိန်းချုပ်ရန်",
        SettingsCategory.CORE_PERMISSIONS,
        isPermission = true,
        intentAction = Settings.ACTION_MANAGE_WRITE_SETTINGS,
        requiresPackageUri = true
    ),
    BATTERY_OPTIMIZATION(
        "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
        "Ignore Battery Optimization",
        "Screen ပိတ်ထားစဉ် အသံအမိန့်များ အမြဲတမ်း နိုးကြားနေစေရန်",
        SettingsCategory.BACKGROUND_POWER,
        isPermission = true,
        intentAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        requiresPackageUri = true
    ),
    OEM_AUTOSTART(
        "OEM Auto-Start နှင့် Background စောင့်ရှောက်မှု",
        "OEM Auto-Start & Protection",
        "Xiaomi, Samsung, Oppo, Vivo, Huawei ဖုန်းများတွင် Background Voice Wake-up မပိတ်စေရန်",
        SettingsCategory.BACKGROUND_POWER,
        isPermission = false,
        intentAction = "oem.action.AUTOSTART",
        requiresPackageUri = false
    ),
    EXACT_ALARM(
        "တိကျသော နှိုးစက်နှင့် သတိပေးချက်များ",
        "Alarms & Reminders (Exact Alarm)",
        "Pomodoro Timer နှင့် သတိပေးချက်များ အချိန်မှန် တိကျစွာ အချက်ပေးရန်",
        SettingsCategory.BACKGROUND_POWER,
        isPermission = true,
        intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        },
        requiresPackageUri = true
    ),
    APP_NOTIFICATIONS(
        "အက်ပ် အသိပေးချက် ဆက်တင်များ",
        "App Notifications",
        "Foreground Service နှင့် အသံအမိန့် လုပ်ဆောင်မှု အသိပေးချက်များ",
        SettingsCategory.NOTIFICATIONS_DND,
        isPermission = true,
        intentAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        runtimePermissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        },
        requiresPackageUri = false
    ),
    NOTIFICATION_LISTENER(
        "အသိပေးချက်များ ဖတ်ရှုခွင့်",
        "Notification Listener Access",
        "ဝင်လာသော မက်ဆေ့ခ်ျနှင့် အသိပေးချက်များကို အသံဖြင့် ဖတ်ကြားစေရန်",
        SettingsCategory.NOTIFICATIONS_DND,
        isPermission = true,
        intentAction = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
        requiresPackageUri = false
    ),
    NOTIFICATION_POLICY(
        "အနှောင့်အယှက် မပေးရ စနစ် ထိန်းချုပ်ခွင့်",
        "Do Not Disturb (DND) Access",
        "အသံတိတ် စနစ် (Mute) နှင့် Volume လွှမ်းမိုး ထိန်းချုပ်နိုင်ရန်",
        SettingsCategory.NOTIFICATIONS_DND,
        isPermission = true,
        intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        } else {
            Settings.ACTION_SETTINGS
        },
        requiresPackageUri = false
    ),
    DEFAULT_ASSISTANT(
        "ပင်မ ဒစ်ဂျစ်တယ် အကူအက်ပ် သတ်မှတ်ခြင်း",
        "Default Digital Assistant",
        "Home ခလုတ် ဖိထားချိန်တွင် Kyaw Gyi AI ချက်ချင်း ပွင့်လာစေရန်",
        SettingsCategory.VOICE_ASSISTANT,
        isPermission = false,
        intentAction = Settings.ACTION_VOICE_INPUT_SETTINGS,
        requiresPackageUri = false
    ),
    TTS_SETTINGS(
        "အသံထွက် ဖတ်ကြားမှု ဆက်တင်များ",
        "Text-to-Speech (TTS) Settings",
        "မြန်မာဘာသာ အသံထွက် engine နှင့် စကားပြောနှုန်း သတ်မှတ်ရန်",
        SettingsCategory.VOICE_ASSISTANT,
        isPermission = false,
        intentAction = "com.android.settings.TTS_SETTINGS",
        requiresPackageUri = false
    ),
    VOICE_INPUT(
        "အသံသွင်းယူမှု ဆက်တင်များ",
        "Voice Input Settings",
        "Google Speech & Keyboard Voice Input စီမံခန့်ခွဲရန်",
        SettingsCategory.VOICE_ASSISTANT,
        isPermission = false,
        intentAction = Settings.ACTION_VOICE_INPUT_SETTINGS,
        requiresPackageUri = false
    ),
    WIFI_SETTINGS(
        "Wi-Fi ကွန်ရက် ဆက်တင်",
        "Wi-Fi Settings",
        "ကြိုးမဲ့အင်တာနက် ကွန်ရက်များ ချိတ်ဆက် ထိန်းချုပ်ရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_WIFI_SETTINGS,
        requiresPackageUri = false
    ),
    BLUETOOTH_SETTINGS(
        "ဘလူးတုသ် ဆက်တင်",
        "Bluetooth Settings",
        "နားကြပ်နှင့် ဘလူးတုသ် ကိရိယာများ ရှာဖွေ ချိတ်ဆက်ရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_BLUETOOTH_SETTINGS,
        requiresPackageUri = false
    ),
    SOUND_SETTINGS(
        "အသံနှင့် တုန်ခါမှု ဆက်တင်",
        "Sound & Vibration Settings",
        "ဖုန်းမြည်သံ၊ Media နှင့် အချက်ပေးသံများ အတိုးအကျယ် စီမံရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_SOUND_SETTINGS,
        requiresPackageUri = false
    ),
    DISPLAY_SETTINGS(
        "မျက်နှာပြင်နှင့် အလင်းအမှောင် ဆက်တင်",
        "Display & Brightness Settings",
        "မျက်နှာပြင် အလင်းနှင့် Screen Timeout သတ်မှတ်ရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_DISPLAY_SETTINGS,
        requiresPackageUri = false
    ),
    LOCATION_SETTINGS(
        "တည်နေရာ ဝန်ဆောင်မှု ဆက်တင်",
        "Location Settings",
        "GPS နှင့် တည်နေရာ အချက်အလက် ဖွင့်/ပိတ် စီမံရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        requiresPackageUri = false
    ),
    DATE_TIME_SETTINGS(
        "ရက်စွဲနှင့် အချိန် ဆက်တင်",
        "Date & Time Settings",
        "ဖုန်းနာရီ၊ အချိန်ဇုန်နှင့် Alarm ချိန်ညှိရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_DATE_SETTINGS,
        requiresPackageUri = false
    ),
    STORAGE_SETTINGS(
        "ဖုန်းတွင်း သိုလှောင်မှု ဆက်တင်",
        "Internal Storage Settings",
        "Storage နေရာလွတ်နှင့် Cache ဒေတာများ စစ်ဆေးရန်",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        requiresPackageUri = false
    ),
    DEVICE_INFO_SETTINGS(
        "ဖုန်းအချက်အလက် စာမျက်နှာ",
        "About Phone / Device Info",
        "ဖုန်း မော်ဒယ်၊ Android Version နှင့် စနစ် အချက်အလက်များ",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_DEVICE_INFO_SETTINGS,
        requiresPackageUri = false
    ),
    DEVELOPER_SETTINGS(
        "Developer Options ဆက်တင်",
        "Developer Options",
        "USB Debugging နှင့် စနစ် စမ်းသပ်မှု ဆက်တင်များ",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        requiresPackageUri = false
    ),
    GENERAL_SETTINGS(
        "Android ပင်မ ဆက်တင်",
        "Android System Settings",
        "ဖုန်း၏ အထွေထွေ ပင်မ ဆက်တင် စာမျက်နှာ",
        SettingsCategory.SYSTEM_CONTROLS,
        isPermission = false,
        intentAction = Settings.ACTION_SETTINGS,
        requiresPackageUri = false
    )
}

enum class SettingsCategory(val titleMM: String) {
    CORE_PERMISSIONS("ပင်မ ခွင့်ပြုချက်များ"),
    BACKGROUND_POWER("Background & ဘက်ထရီ စောင့်ရှောက်မှု"),
    NOTIFICATIONS_DND("အသိပေးချက် & DND"),
    VOICE_ASSISTANT("Voice & Assistant ဆက်တင်"),
    SYSTEM_CONTROLS("စနစ်ဆိုင်ရာ ဖြတ်လမ်းများ")
}

/**
 * DirectSettingsRouter - Systematic, deterministic navigation to Android system settings
 * for permission fulfillment, background operation, STT/TTS configuration,
 * and device system integration.
 */
class DirectSettingsRouter(private val context: Context) {

    companion object {
        private const val TAG = "DirectSettingsRouter"

        @Volatile
        private var INSTANCE: DirectSettingsRouter? = null

        fun getInstance(context: Context): DirectSettingsRouter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DirectSettingsRouter(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Deterministically constructs the primary Android Intent for a given SettingsDestination,
     * including package-scoped URI ("package:<packageName>") or EXTRA_APP_PACKAGE where supported.
     */
    fun buildDirectIntent(destination: SettingsDestination): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = when (destination) {
            SettingsDestination.APPLICATION_DETAILS,
            SettingsDestination.MICROPHONE_PERMISSION,
            SettingsDestination.CALL_PHONE_PERMISSION,
            SettingsDestination.SEND_SMS_PERMISSION,
            SettingsDestination.CONTACTS_PERMISSION,
            SettingsDestination.CAMERA_PERMISSION,
            SettingsDestination.AUDIO_STORAGE_PERMISSION,
            SettingsDestination.BLUETOOTH_PERMISSION,
            SettingsDestination.LOCATION_PERMISSION -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            }
            SettingsDestination.OVERLAY_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            SettingsDestination.BATTERY_OPTIMIZATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            SettingsDestination.WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            SettingsDestination.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            SettingsDestination.APP_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            SettingsDestination.DEFAULT_ASSISTANT -> {
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            }
            SettingsDestination.TTS_SETTINGS -> {
                Intent("com.android.settings.TTS_SETTINGS")
            }
            else -> {
                Intent(destination.intentAction)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Launch direct settings screen and return a structured DirectRouteExecutionReport
     * with 3-tier fallback protection (Primary Package Intent -> Category Intent -> App Details -> General Settings).
     */
    fun openSettingsWithReport(destination: SettingsDestination): DirectRouteExecutionReport {
        val preGranted = isCapabilityGranted(destination)
        if (destination == SettingsDestination.OEM_AUTOSTART) {
            val oemDispatched = openOemAutostartSettings()
            return DirectRouteExecutionReport(
                destination = destination,
                resolvedIntentAction = "oem.action.AUTOSTART",
                usedPackageUriDeepLink = false,
                usedFallbackRoute = !oemDispatched,
                dispatchSucceeded = oemDispatched,
                isCurrentlyGranted = preGranted,
                verificationOutcome = if (oemDispatched) "INTENT_DISPATCHED_AWAITING_USER" else "SAFE_FAIL",
                messageMM = if (oemDispatched) {
                    "${destination.titleMM} သို့ တိုက်ရိုက် ဖွင့်လှစ်ပေးထားပါသည်ခင်ဗျာ။"
                } else {
                    "${destination.titleMM} ကို ဖွင့်လှစ်နိုင်ခြင်း မရှိပါ။"
                }
            )
        }

        val primaryIntent = buildDirectIntent(destination)
        try {
            context.startActivity(primaryIntent)
            return DirectRouteExecutionReport(
                destination = destination,
                resolvedIntentAction = primaryIntent.action ?: destination.intentAction,
                usedPackageUriDeepLink = primaryIntent.data != null,
                usedFallbackRoute = false,
                dispatchSucceeded = true,
                isCurrentlyGranted = preGranted,
                verificationOutcome = if (preGranted && !destination.isPermission) {
                    "VERIFIED_GRANTED"
                } else {
                    "INTENT_DISPATCHED_AWAITING_USER"
                },
                messageMM = "${destination.titleMM} စာမျက်နှာသို့ တိုက်ရိုက် လမ်းညွှန်ဖွင့်လှစ်ပေးပြီးပါပြီခင်ဗျာ။"
            )
        } catch (primaryEx: Exception) {
            // Tier 2 Fallback: Category-level intent without package URI
            try {
                val categoryFallback = when (destination) {
                    SettingsDestination.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    SettingsDestination.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                    SettingsDestination.DEFAULT_ASSISTANT -> Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(categoryFallback)
                return DirectRouteExecutionReport(
                    destination = destination,
                    resolvedIntentAction = categoryFallback.action ?: Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    usedPackageUriDeepLink = categoryFallback.data != null,
                    usedFallbackRoute = true,
                    dispatchSucceeded = true,
                    isCurrentlyGranted = preGranted,
                    verificationOutcome = "INTENT_DISPATCHED_AWAITING_USER",
                    messageMM = "${destination.titleMM} အတွက် အရန်လမ်းကြောင်း (Fallback Route) မှတစ်ဆင့် ဖွင့်လှစ်ပေးထားပါသည်။"
                )
            } catch (fallbackEx: Exception) {
                // Tier 3 Fallback: General Android Settings
                return try {
                    val generalIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(generalIntent)
                    DirectRouteExecutionReport(
                        destination = destination,
                        resolvedIntentAction = Settings.ACTION_SETTINGS,
                        usedPackageUriDeepLink = false,
                        usedFallbackRoute = true,
                        dispatchSucceeded = true,
                        isCurrentlyGranted = preGranted,
                        verificationOutcome = "INTENT_DISPATCHED_AWAITING_USER",
                        messageMM = "ပင်မ Android Settings စာမျက်နှာသို့ ဖွင့်လှစ်ပေးထားပါသည်။"
                    )
                } catch (finalEx: Exception) {
                    DirectRouteExecutionReport(
                        destination = destination,
                        resolvedIntentAction = destination.intentAction,
                        usedPackageUriDeepLink = false,
                        usedFallbackRoute = true,
                        dispatchSucceeded = false,
                        isCurrentlyGranted = preGranted,
                        verificationOutcome = "SAFE_FAIL",
                        messageMM = "${destination.titleMM} ဆက်တင်ကို ဖွင့်လှစ်နိုင်ခြင်း မရှိပါ (SAFE_FAIL)။"
                    )
                }
            }
        }
    }

    /**
     * Launch direct settings screen for a specific destination with intelligent fallback
     */
    fun openSettings(destination: SettingsDestination): Boolean {
        return openSettingsWithReport(destination).dispatchSucceeded
    }

    /**
     * Direct OEM Auto-Start & Background Protection Launcher
     */
    fun openOemAutostartSettings(): Boolean {
        try {
            val persistenceHelper = com.example.utils.OemBackgroundPersistenceHelper(context)
            if (persistenceHelper.launchSettings()) {
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "OemBackgroundPersistenceHelper launch failed: ${e.message}")
        }

        val oemIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.AppPowerConsumeActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.autostart.AutoStartActivity")),
            Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
        )

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (ignored: Exception) {
            }
        }

        // Fallback to Battery Optimization
        return openBatteryOptimizationSettings()
    }

    /**
     * Direct one-touch shortcuts
     */
    fun openMicrophonePermissionSettings(): Boolean = openSettings(SettingsDestination.MICROPHONE_PERMISSION)
    fun openCallPhonePermissionSettings(): Boolean = openSettings(SettingsDestination.CALL_PHONE_PERMISSION)
    fun openSendSmsPermissionSettings(): Boolean = openSettings(SettingsDestination.SEND_SMS_PERMISSION)
    fun openContactsPermissionSettings(): Boolean = openSettings(SettingsDestination.CONTACTS_PERMISSION)
    fun openCameraPermissionSettings(): Boolean = openSettings(SettingsDestination.CAMERA_PERMISSION)
    fun openAudioStoragePermissionSettings(): Boolean = openSettings(SettingsDestination.AUDIO_STORAGE_PERMISSION)
    fun openAccessibilitySettings(): Boolean = openSettings(SettingsDestination.ACCESSIBILITY)
    fun openOverlaySettings(): Boolean = openSettings(SettingsDestination.OVERLAY_PERMISSION)
    fun openBatteryOptimizationSettings(): Boolean = openSettings(SettingsDestination.BATTERY_OPTIMIZATION)
    fun openWriteSettingsPermission(): Boolean = openSettings(SettingsDestination.WRITE_SETTINGS)
    fun openExactAlarmSettings(): Boolean = openSettings(SettingsDestination.EXACT_ALARM)
    fun openTtsSettings(): Boolean = openSettings(SettingsDestination.TTS_SETTINGS)
    fun openVoiceInputSettings(): Boolean = openSettings(SettingsDestination.VOICE_INPUT)
    fun openNotificationSettings(): Boolean = openSettings(SettingsDestination.APP_NOTIFICATIONS)
    fun openNotificationPolicySettings(): Boolean = openSettings(SettingsDestination.NOTIFICATION_POLICY)
    fun openDefaultAssistantSettings(): Boolean = openSettings(SettingsDestination.DEFAULT_ASSISTANT)
    fun openWifiSettings(): Boolean = openSettings(SettingsDestination.WIFI_SETTINGS)
    fun openBluetoothSettings(): Boolean = openSettings(SettingsDestination.BLUETOOTH_SETTINGS)
    fun openSoundSettings(): Boolean = openSettings(SettingsDestination.SOUND_SETTINGS)
    fun openDisplaySettings(): Boolean = openSettings(SettingsDestination.DISPLAY_SETTINGS)
    fun openLocationSettings(): Boolean = openSettings(SettingsDestination.LOCATION_SETTINGS)
    fun openDateTimeSettings(): Boolean = openSettings(SettingsDestination.DATE_TIME_SETTINGS)
    fun openStorageSettings(): Boolean = openSettings(SettingsDestination.STORAGE_SETTINGS)
    fun openDeviceInfoSettings(): Boolean = openSettings(SettingsDestination.DEVICE_INFO_SETTINGS)

    /**
     * Maps any pipeline actionType to the exact SettingsDestination required to fulfill its permission/capability.
     */
    fun getRequiredDestinationForAction(actionType: String): SettingsDestination? {
        return when (actionType.trim().uppercase()) {
            "CALL", "CALL_CONTACT", "CALL_PHONE" -> {
                if (!KyawGyiPermissionManager.hasCallPhonePermission(context)) {
                    SettingsDestination.CALL_PHONE_PERMISSION
                } else if (!KyawGyiPermissionManager.hasContactsPermission(context)) {
                    SettingsDestination.CONTACTS_PERMISSION
                } else null
            }
            "SEND_MESSAGE", "SEND_SMS" -> {
                if (!KyawGyiPermissionManager.hasSendSmsPermission(context)) {
                    SettingsDestination.SEND_SMS_PERMISSION
                } else if (!KyawGyiPermissionManager.hasContactsPermission(context)) {
                    SettingsDestination.CONTACTS_PERMISSION
                } else null
            }
            "FLASHLIGHT", "SET_FLASHLIGHT", "OPEN_CAMERA" -> {
                if (!KyawGyiPermissionManager.hasCameraPermission(context)) {
                    SettingsDestination.CAMERA_PERMISSION
                } else null
            }
            "PLAY_MUSIC" -> {
                if (!KyawGyiPermissionManager.hasAudioStoragePermission(context)) {
                    SettingsDestination.AUDIO_STORAGE_PERMISSION
                } else null
            }
            "BRIGHTNESS_UP", "BRIGHTNESS_DOWN" -> {
                if (!KyawGyiPermissionManager.canWriteSystemSettings(context)) {
                    SettingsDestination.WRITE_SETTINGS
                } else null
            }
            "MUTE", "UNMUTE" -> {
                if (!KyawGyiPermissionManager.hasNotificationPolicyAccess(context)) {
                    SettingsDestination.NOTIFICATION_POLICY
                } else null
            }
            "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "TAP", "SCROLL", "READ_SCREEN", "TAKE_SCREENSHOT" -> {
                if (!isCapabilityGranted(SettingsDestination.ACCESSIBILITY)) {
                    SettingsDestination.ACCESSIBILITY
                } else null
            }
            "BLUETOOTH_TOGGLE", "SET_BLUETOOTH" -> {
                if (!KyawGyiPermissionManager.hasBluetoothPermission(context)) {
                    SettingsDestination.BLUETOOTH_PERMISSION
                } else {
                    SettingsDestination.BLUETOOTH_SETTINGS
                }
            }
            "WIFI_TOGGLE", "SET_WIFI" -> SettingsDestination.WIFI_SETTINGS
            "SET_ALARM", "SET_TIMER" -> {
                if (!KyawGyiPermissionManager.canScheduleExactAlarms(context)) {
                    SettingsDestination.EXACT_ALARM
                } else null
            }
            else -> null
        }
    }

    /**
     * Resolve a voice or text query into a SettingsDestination
     */
    fun resolveSettingsDestination(query: String): SettingsDestination? {
        val q = query.lowercase().trim()
        // Check if user passed an exact SettingsDestination enum name
        SettingsDestination.entries.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let {
            return it
        }

        val isSettingsIntent = q.contains("ဆက်တင်") || q.contains("setting") ||
            q.contains("ခွင့်ပြုချက်") || q.contains("permission") ||
            q.contains("လမ်းကြောင်း") || q.contains("ဖွင့်") || q.contains("သွား")

        if (!isSettingsIntent && !q.contains("settings")) {
            return null
        }

        return when {
            q.contains("မိုက်ခရိုဖုန်း") || q.contains("အသံဖမ်း") || q.contains("mic") || q.contains("microphone") -> SettingsDestination.MICROPHONE_PERMISSION
            (q.contains("ဖုန်းခေါ်") || q.contains("call")) && (q.contains("ခွင့်ပြုချက်") || q.contains("permission") || q.contains("ဆက်တင်") || q.contains("setting")) -> SettingsDestination.CALL_PHONE_PERMISSION
            (q.contains("မက်ဆေ့") || q.contains("စာတို") || q.contains("sms")) && (q.contains("ခွင့်ပြုချက်") || q.contains("permission") || q.contains("ဆက်တင်") || q.contains("setting")) -> SettingsDestination.SEND_SMS_PERMISSION
            (q.contains("အဆက်အသွယ်") || q.contains("contact")) && (q.contains("ခွင့်ပြုချက်") || q.contains("permission") || q.contains("ဆက်တင်") || q.contains("setting")) -> SettingsDestination.CONTACTS_PERMISSION
            (q.contains("ကင်မရာ") || q.contains("camera") || q.contains("ဓာတ်မီး")) && (q.contains("ခွင့်ပြုချက်") || q.contains("permission") || q.contains("ဆက်တင်") || q.contains("setting")) -> SettingsDestination.CAMERA_PERMISSION
            (q.contains("သီချင်း") || q.contains("အသံဖိုင်") || q.contains("media") || q.contains("audio")) && (q.contains("ခွင့်ပြုချက်") || q.contains("permission")) -> SettingsDestination.AUDIO_STORAGE_PERMISSION
            q.contains("overlay") || q.contains("အပေါ်ပေါ်") || q.contains("floating") || q.contains("မျက်နှာပြင်ပေါ်") -> SettingsDestination.OVERLAY_PERMISSION
            q.contains("accessibility") || q.contains("အလိုအလျောက်") || q.contains("လက်လှမ်းမီ") -> SettingsDestination.ACCESSIBILITY
            q.contains("write setting") || q.contains("စနစ် ဆက်တင် ပြင်ဆင်") || q.contains("modify system") || q.contains("write_settings") -> SettingsDestination.WRITE_SETTINGS
            q.contains("dnd") || q.contains("အနှောင့်အယှက်") || q.contains("do not disturb") || q.contains("notification policy") -> SettingsDestination.NOTIFICATION_POLICY
            q.contains("listener") || q.contains("အသိပေးချက် ဖတ်") || q.contains("notification listener") -> SettingsDestination.NOTIFICATION_LISTENER
            q.contains("ဘက်ထရီ") || q.contains("battery") || q.contains("ချွေတာ") -> SettingsDestination.BATTERY_OPTIMIZATION
            q.contains("autostart") || q.contains("auto-start") || q.contains("စတင်ခွင့်") || q.contains("oem") -> SettingsDestination.OEM_AUTOSTART
            q.contains("နှိုးစက်") || q.contains("alarm") -> SettingsDestination.EXACT_ALARM
            q.contains("အသိပေးချက်") || q.contains("notification") -> SettingsDestination.APP_NOTIFICATIONS
            q.contains("assistant") || q.contains("အကူအက်ပ်") -> SettingsDestination.DEFAULT_ASSISTANT
            q.contains("tts") || q.contains("အသံထွက်") || q.contains("ဖတ်ကြား") -> SettingsDestination.TTS_SETTINGS
            q.contains("voice input") || q.contains("အသံသွင်း") -> SettingsDestination.VOICE_INPUT
            q.contains("wifi") || q.contains("ဝိုင်ဖိုင်") -> SettingsDestination.WIFI_SETTINGS
            q.contains("bluetooth") || q.contains("ဘလူးတု") -> {
                if (q.contains("ခွင့်ပြုချက်") || q.contains("permission")) {
                    SettingsDestination.BLUETOOTH_PERMISSION
                } else {
                    SettingsDestination.BLUETOOTH_SETTINGS
                }
            }
            q.contains("အသံ") || q.contains("sound") || q.contains("volume") -> SettingsDestination.SOUND_SETTINGS
            q.contains("အလင်း") || q.contains("display") || q.contains("screen") -> SettingsDestination.DISPLAY_SETTINGS
            q.contains("တည်နေရာ") || q.contains("location") || q.contains("gps") -> {
                if (q.contains("ခွင့်ပြုချက်") || q.contains("permission")) {
                    SettingsDestination.LOCATION_PERMISSION
                } else {
                    SettingsDestination.LOCATION_SETTINGS
                }
            }
            q.contains("နေ့စွဲ") || q.contains("အချိန်") || q.contains("date") || q.contains("time") -> SettingsDestination.DATE_TIME_SETTINGS
            q.contains("သိုလှောင်") || q.contains("storage") -> SettingsDestination.STORAGE_SETTINGS
            q.contains("ဖုန်းအချက်အလက်") || q.contains("about phone") || q.contains("စက်အချက်အလက်") || q.contains("device info") -> SettingsDestination.DEVICE_INFO_SETTINGS
            q.contains("developer") -> SettingsDestination.DEVELOPER_SETTINGS
            q.contains("အက်ပ် အသေးစိတ်") || q.contains("app info") || q.contains("app details") -> SettingsDestination.APPLICATION_DETAILS
            q.contains("ဆက်တင်") || q.contains("settings") || q.contains("setting") -> SettingsDestination.APPLICATION_DETAILS
            else -> null
        }
    }

    /**
     * Check if a required capability or permission is currently granted
     */
    fun isCapabilityGranted(destination: SettingsDestination): Boolean {
        return when (destination) {
            SettingsDestination.ACCESSIBILITY -> com.example.service.KyawGyiAccessibilityService.instance != null ||
                KyawGyiPermissionManager.isAccessibilityServiceEnabled(context)
            SettingsDestination.OVERLAY_PERMISSION -> OverlayPermissionHelper.canDrawOverlays(context)
            SettingsDestination.BATTERY_OPTIMIZATION, SettingsDestination.OEM_AUTOSTART -> {
                KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            }
            SettingsDestination.APPLICATION_DETAILS -> KyawGyiPermissionManager.hasAllPermissions(context)
            SettingsDestination.MICROPHONE_PERMISSION -> KyawGyiPermissionManager.hasRecordAudioPermission(context)
            SettingsDestination.CALL_PHONE_PERMISSION -> KyawGyiPermissionManager.hasCallPhonePermission(context)
            SettingsDestination.SEND_SMS_PERMISSION -> KyawGyiPermissionManager.hasSendSmsPermission(context)
            SettingsDestination.CONTACTS_PERMISSION -> KyawGyiPermissionManager.hasContactsPermission(context)
            SettingsDestination.CAMERA_PERMISSION -> KyawGyiPermissionManager.hasCameraPermission(context)
            SettingsDestination.AUDIO_STORAGE_PERMISSION -> KyawGyiPermissionManager.hasAudioStoragePermission(context)
            SettingsDestination.BLUETOOTH_PERMISSION -> KyawGyiPermissionManager.hasBluetoothPermission(context)
            SettingsDestination.LOCATION_PERMISSION -> KyawGyiPermissionManager.hasLocationPermission(context)
            SettingsDestination.WRITE_SETTINGS -> KyawGyiPermissionManager.canWriteSystemSettings(context)
            SettingsDestination.EXACT_ALARM -> KyawGyiPermissionManager.canScheduleExactAlarms(context)
            SettingsDestination.NOTIFICATION_POLICY -> KyawGyiPermissionManager.hasNotificationPolicyAccess(context)
            SettingsDestination.NOTIFICATION_LISTENER -> KyawGyiPermissionManager.hasNotificationListenerAccess(context)
            SettingsDestination.APP_NOTIFICATIONS -> KyawGyiPermissionManager.hasNotificationPermission(context)
            SettingsDestination.DEFAULT_ASSISTANT -> KyawGyiPermissionManager.isDefaultAssistant(context)
            SettingsDestination.VOICE_INPUT, SettingsDestination.TTS_SETTINGS -> true
            SettingsDestination.WIFI_SETTINGS, SettingsDestination.BLUETOOTH_SETTINGS,
            SettingsDestination.SOUND_SETTINGS, SettingsDestination.DISPLAY_SETTINGS,
            SettingsDestination.LOCATION_SETTINGS, SettingsDestination.DATE_TIME_SETTINGS,
            SettingsDestination.STORAGE_SETTINGS, SettingsDestination.DEVICE_INFO_SETTINGS,
            SettingsDestination.DEVELOPER_SETTINGS, SettingsDestination.GENERAL_SETTINGS -> true
        }
    }

    /**
     * Returns all permission-oriented SettingsDestinations in the project along with their live grant status.
     */
    fun getAllProjectPermissionRoutesStatus(): Map<SettingsDestination, Boolean> {
        return SettingsDestination.entries
            .filter { it.isPermission || it == SettingsDestination.OEM_AUTOSTART || it == SettingsDestination.DEFAULT_ASSISTANT }
            .associateWith { isCapabilityGranted(it) }
    }
}
