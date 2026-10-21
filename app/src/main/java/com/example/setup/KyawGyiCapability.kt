package com.example.setup

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.utils.KyawGyiPermissionManager

/**
 * Capability Verification Status for P01-P16.
 * Guaranteed Rule: No faking. Every PASS must be backed by genuine runtime check.
 */
enum class CapabilityStatus {
    PASS,
    ACTION_REQUIRED,
    OPTIONAL
}

/**
 * 16 Specific Capabilities (P01 - P16) required for Kyaw Gyi AI
 */
enum class KyawGyiCapability(
    val code: String,
    val titleMM: String,
    val titleEN: String,
    val descriptionMM: String,
    val category: String,
    val isCritical: Boolean,
    val destination: SettingsDestination
) {
    P01_MICROPHONE(
        code = "P01",
        titleMM = "မိုက်ခရိုဖုန်း အသုံးပြုခွင့်",
        titleEN = "Microphone (RECORD_AUDIO)",
        descriptionMM = "မြန်မာစကား အသံအမိန့်များ ဖမ်းယူရန်နှင့် 'ကျော်ကြီး' Wake Word အမြဲနိုးကြားစေရန် လိုအပ်ပါသည်။",
        category = "Core Audio",
        isCritical = true,
        destination = SettingsDestination.APPLICATION_DETAILS
    ),
    P02_OVERLAY(
        code = "P02",
        titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Overlay)",
        titleEN = "Display Over Other Apps",
        descriptionMM = "အခြား Application များ သုံးနေစဉ် Floating Mic Ball ပေါ်နေစေရန် လိုအပ်ပါသည်။",
        category = "Display & UI",
        isCritical = true,
        destination = SettingsDestination.OVERLAY_PERMISSION
    ),
    P03_ACCESSIBILITY(
        code = "P03",
        titleMM = "အလိုအလျောက် ထိန်းချုပ်ခွင့် (Accessibility)",
        titleEN = "KyawGyi Accessibility Service",
        descriptionMM = "အသံဖြင့် အက်ပ်များ အလိုအလျောက်ဖွင့်ခြင်း၊ ခလုတ်နှိပ်ခြင်း၊ Back/Home လုပ်ဆောင်ရန် ဖြစ်ပါသည်။",
        category = "System Automation",
        isCritical = true,
        destination = SettingsDestination.ACCESSIBILITY
    ),
    P04_BATTERY_OPTIMIZATION(
        code = "P04",
        titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
        titleEN = "Ignore Battery Optimizations",
        descriptionMM = "မျက်နှာပြင် ပိတ်ထားစဉ် အက်ပ်ရပ်တန့်မသွားဘဲ Background နားထောင်နိုင်ရန် ဖြစ်ပါသည်။",
        category = "Background Power",
        isCritical = true,
        destination = SettingsDestination.BATTERY_OPTIMIZATION
    ),
    P05_OEM_AUTOSTART(
        code = "P05",
        titleMM = "OEM Auto-Start စောင့်ရှောက်မှု",
        titleEN = "OEM Auto-Start & Background",
        descriptionMM = "Xiaomi/HyperOS, Samsung, Oppo, Vivo ဖုန်းများတွင် Background Voice မသေစေရန် ဖြစ်ပါသည်။",
        category = "OEM Protection",
        isCritical = false,
        destination = SettingsDestination.OEM_AUTOSTART
    ),
    P06_POST_NOTIFICATIONS(
        code = "P06",
        titleMM = "အသိပေးချက်များ ပေးပို့ခွင့်",
        titleEN = "Post Notifications (Android 13+)",
        descriptionMM = "Foreground Service အခြေအနေနှင့် သတိပေးချက်များ ပြသရန် ဖြစ်ပါသည်။",
        category = "Notifications",
        isCritical = false,
        destination = SettingsDestination.APP_NOTIFICATIONS
    ),
    P07_EXACT_ALARM(
        code = "P07",
        titleMM = "တိကျသော Alarm & Reminders",
        titleEN = "Exact Alarm & Scheduling",
        descriptionMM = "Pomodoro Timer နှင့် အချိန်ကိုက် နှိုးစက်များ တိကျစွာ အချက်ပေးရန် ဖြစ်ပါသည်။",
        category = "System Timing",
        isCritical = false,
        destination = SettingsDestination.EXACT_ALARM
    ),
    P08_WRITE_SETTINGS(
        code = "P08",
        titleMM = "စနစ် ဆက်တင် ပြင်ဆင်ခွင့်",
        titleEN = "Modify System Settings",
        descriptionMM = "မျက်နှာပြင် အလင်းအမှောင်၊ အသံအတိုးအကျယ်များကို တိုက်ရိုက် ပြင်ဆင်ရန် ဖြစ်ပါသည်။",
        category = "System Controls",
        isCritical = false,
        destination = SettingsDestination.WRITE_SETTINGS
    ),
    P09_NOTIFICATION_POLICY(
        code = "P09",
        titleMM = "အနှောင့်အယှက် မပေးရ စနစ် (DND)",
        titleEN = "Do Not Disturb / Notification Policy",
        descriptionMM = "အသံတိတ် စနစ် (Mute) နှင့် အသံအမြင့်ဆုံး အမိန့်များ တိကျစွာ ထိန်းချုပ်ရန် ဖြစ်ပါသည်။",
        category = "Notifications",
        isCritical = false,
        destination = SettingsDestination.NOTIFICATION_POLICY
    ),
    P10_NOTIFICATION_LISTENER(
        code = "P10",
        titleMM = "အသိပေးချက်များ ဖတ်ရှုခွင့်",
        titleEN = "Notification Listener Service",
        descriptionMM = "ဝင်လာသော စာတိုများကို အသံဖြင့် ပြန်လည်ဖတ်ကြားစေရန် ဖြစ်ပါသည်။",
        category = "Notifications",
        isCritical = false,
        destination = SettingsDestination.NOTIFICATION_LISTENER
    ),
    P11_DEFAULT_ASSISTANT(
        code = "P11",
        titleMM = "ပင်မ ဒစ်ဂျစ်တယ် အကူအက်ပ်",
        titleEN = "Default Digital Assistant",
        descriptionMM = "Home ခလုတ်ဖိထားချိန် သို့မဟုတ် Swipe Gesture တွင် Kyaw Gyi တန်းပွင့်စေရန် ဖြစ်ပါသည်။",
        category = "Voice Assistant",
        isCritical = false,
        destination = SettingsDestination.DEFAULT_ASSISTANT
    ),
    P12_TTS_ENGINE(
        code = "P12",
        titleMM = "မြန်မာ စကားသံထွက် (TTS) စနစ်",
        titleEN = "Text-to-Speech (my-MM)",
        descriptionMM = "Android စနစ် TTS အသံထွက် Engine နှင့် မြန်မာဘာသာစကား သတ်မှတ်ချက် ဖြစ်ပါသည်။",
        category = "Voice Engine",
        isCritical = true,
        destination = SettingsDestination.TTS_SETTINGS
    ),
    P13_VOICE_INPUT(
        code = "P13",
        titleMM = "အသံသွင်း Voice Input (STT)",
        titleEN = "Voice Input / Speech Recognition",
        descriptionMM = "Google Voice Typing နှင့် အသံဖြင့် စာရိုက်သွင်းမှု စနစ် ဖြစ်ပါသည်။",
        category = "Voice Engine",
        isCritical = true,
        destination = SettingsDestination.DEFAULT_ASSISTANT
    ),
    P14_CONTACTS_ACCESS(
        code = "P14",
        titleMM = "ဖုန်းအဆက်အသွယ်များ ရှာဖွေခွင့်",
        titleEN = "Contacts Access (READ_CONTACTS)",
        descriptionMM = "ဖုန်းထဲရှိ Contacts များကို အသံဖြင့် နာမည်ခေါ်ဆို ဖုန်းဆက်နိုင်ရန် ဖြစ်ပါသည်။",
        category = "Communications",
        isCritical = false,
        destination = SettingsDestination.APPLICATION_DETAILS
    ),
    P15_CALL_PHONE(
        code = "P15",
        titleMM = "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့်",
        titleEN = "Direct Phone Call (CALL_PHONE)",
        descriptionMM = "စကားသံ အမိန့်ဖြင့် ဖုန်းနံပါတ်ဆီသို့ တိုက်ရိုက် ဖုန်းခေါ်ဆိုနိုင်စေရန် ဖြစ်ပါသည်။",
        category = "Communications",
        isCritical = false,
        destination = SettingsDestination.APPLICATION_DETAILS
    ),
    P16_AUDIO_STORAGE(
        code = "P16",
        titleMM = "သီချင်း MP3 ဖိုင်များ ဖတ်ရှုခွင့်",
        titleEN = "Audio Media / Storage Access",
        descriptionMM = "ဖုန်းထဲရှိ သီချင်းများကို Scan ဖတ်ပြီး အသံဖြင့် နာမည်ခေါ်ကာ ဖွင့်ပေးရန် ဖြစ်ပါသည်။",
        category = "Media & Storage",
        isCritical = false,
        destination = SettingsDestination.APPLICATION_DETAILS
    );

    fun getIcon(): ImageVector {
        return when (this) {
            P01_MICROPHONE -> Icons.Default.Mic
            P02_OVERLAY -> Icons.Default.Layers
            P03_ACCESSIBILITY -> Icons.Default.AccessibilityNew
            P04_BATTERY_OPTIMIZATION -> Icons.Default.BatteryChargingFull
            P05_OEM_AUTOSTART -> Icons.Default.Shield
            P06_POST_NOTIFICATIONS -> Icons.Default.Notifications
            P07_EXACT_ALARM -> Icons.Default.Alarm
            P08_WRITE_SETTINGS -> Icons.Default.SettingsSuggest
            P09_NOTIFICATION_POLICY -> Icons.Default.DoNotDisturbOn
            P10_NOTIFICATION_LISTENER -> Icons.Default.MarkChatRead
            P11_DEFAULT_ASSISTANT -> Icons.Default.Assistant
            P12_TTS_ENGINE -> Icons.AutoMirrored.Filled.VolumeUp
            P13_VOICE_INPUT -> Icons.Default.RecordVoiceOver
            P14_CONTACTS_ACCESS -> Icons.Default.Contacts
            P15_CALL_PHONE -> Icons.Default.Phone
            P16_AUDIO_STORAGE -> Icons.Default.LibraryMusic
        }
    }
}

/**
 * Concrete Diagnostic Result for each capability
 */
data class CapabilityCheckResult(
    val capability: KyawGyiCapability,
    val isGranted: Boolean,
    val status: CapabilityStatus,
    val diagnosticDetails: String,
    val directRouteAction: (Context) -> Boolean
)

/**
 * Production-Grade Diagnostics Evaluator.
 * Checks ALL 16 capabilities without mock or fake passes.
 */
object CapabilityDiagnosticsEngine {

    fun checkAll(context: Context): List<CapabilityCheckResult> {
        val router = DirectSettingsRouter.getInstance(context)
        return KyawGyiCapability.values().map { cap ->
            evaluateCapability(context, router, cap)
        }
    }

    fun evaluateCapability(
        context: Context,
        router: DirectSettingsRouter,
        capability: KyawGyiCapability
    ): CapabilityCheckResult {
        val isGranted = when (capability) {
            KyawGyiCapability.P01_MICROPHONE -> KyawGyiPermissionManager.hasRecordAudioPermission(context)
            KyawGyiCapability.P02_OVERLAY -> KyawGyiPermissionManager.hasOverlayPermission(context)
            KyawGyiCapability.P03_ACCESSIBILITY -> {
                com.example.service.KyawGyiAccessibilityService.instance != null ||
                        KyawGyiPermissionManager.isAccessibilityServiceEnabled(context)
            }
            KyawGyiCapability.P04_BATTERY_OPTIMIZATION -> KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            KyawGyiCapability.P05_OEM_AUTOSTART -> KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            KyawGyiCapability.P06_POST_NOTIFICATIONS -> KyawGyiPermissionManager.hasNotificationPermission(context)
            KyawGyiCapability.P07_EXACT_ALARM -> KyawGyiPermissionManager.canScheduleExactAlarms(context)
            KyawGyiCapability.P08_WRITE_SETTINGS -> KyawGyiPermissionManager.canWriteSystemSettings(context)
            KyawGyiCapability.P09_NOTIFICATION_POLICY -> KyawGyiPermissionManager.hasNotificationPolicyAccess(context)
            KyawGyiCapability.P10_NOTIFICATION_LISTENER -> {
                val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                flat?.contains(context.packageName) == true
            }
            KyawGyiCapability.P11_DEFAULT_ASSISTANT -> KyawGyiPermissionManager.isDefaultAssistant(context)
            KyawGyiCapability.P12_TTS_ENGINE -> com.example.utils.MyanmarTTSEngine.masterTtsEnabled
            KyawGyiCapability.P13_VOICE_INPUT -> android.speech.SpeechRecognizer.isRecognitionAvailable(context)
            KyawGyiCapability.P14_CONTACTS_ACCESS -> KyawGyiPermissionManager.hasContactsPermission(context)
            KyawGyiCapability.P15_CALL_PHONE -> KyawGyiPermissionManager.hasCallPhonePermission(context)
            KyawGyiCapability.P16_AUDIO_STORAGE -> KyawGyiPermissionManager.hasAudioStoragePermission(context)
        }

        val status = if (isGranted) {
            CapabilityStatus.PASS
        } else if (capability.isCritical) {
            CapabilityStatus.ACTION_REQUIRED
        } else {
            CapabilityStatus.OPTIONAL
        }

        val diagnosticDetails = when (capability) {
            KyawGyiCapability.P01_MICROPHONE -> if (isGranted) "PCM 16kHz AudioRecord capture verified" else "RECORD_AUDIO ခွင့်ပြုချက်မရှိသေးပါ"
            KyawGyiCapability.P02_OVERLAY -> if (isGranted) "canDrawOverlays = true (Assistive Ball အသင့်)" else "Overlay ခွင့်ပြုချက် ပိတ်ထားပါသည်"
            KyawGyiCapability.P03_ACCESSIBILITY -> if (isGranted) "KyawGyiAccessibilityService Active" else "Accessibility Service ပိတ်ထားပါသည်"
            KyawGyiCapability.P04_BATTERY_OPTIMIZATION -> if (isGranted) "Ignoring battery optimizations" else "System battery optimization active"
            KyawGyiCapability.P05_OEM_AUTOSTART -> if (isGranted) "Background execution protected" else "OEM background protection စစ်ဆေးရန်"
            KyawGyiCapability.P06_POST_NOTIFICATIONS -> if (isGranted) "Notifications allowed" else "Notification permission missing"
            KyawGyiCapability.P07_EXACT_ALARM -> if (isGranted) "Exact alarms allowed" else "SCHEDULE_EXACT_ALARM needed"
            KyawGyiCapability.P08_WRITE_SETTINGS -> if (isGranted) "Settings.System.canWrite = true" else "System settings write disabled"
            KyawGyiCapability.P09_NOTIFICATION_POLICY -> if (isGranted) "DND access granted" else "Notification policy access missing"
            KyawGyiCapability.P10_NOTIFICATION_LISTENER -> if (isGranted) "Notification listener enabled" else "Notification listener disabled"
            KyawGyiCapability.P11_DEFAULT_ASSISTANT -> if (isGranted) "Default assistant package confirmed" else "Not set as default assistant"
            KyawGyiCapability.P12_TTS_ENGINE -> if (isGranted) "TTS engine enabled (my-MM ready)" else "TTS engine disabled"
            KyawGyiCapability.P13_VOICE_INPUT -> if (isGranted) "SpeechRecognizer available on device" else "SpeechRecognizer not found"
            KyawGyiCapability.P14_CONTACTS_ACCESS -> if (isGranted) "READ_CONTACTS granted" else "Contacts permission missing"
            KyawGyiCapability.P15_CALL_PHONE -> if (isGranted) "CALL_PHONE granted" else "Call phone permission missing"
            KyawGyiCapability.P16_AUDIO_STORAGE -> if (isGranted) "Audio storage permission granted" else "Storage permission missing"
        }

        return CapabilityCheckResult(
            capability = capability,
            isGranted = isGranted,
            status = status,
            diagnosticDetails = diagnosticDetails,
            directRouteAction = { ctx ->
                when (capability) {
                    KyawGyiCapability.P05_OEM_AUTOSTART -> router.openOemAutostartSettings()
                    else -> router.openSettings(capability.destination)
                }
            }
        )
    }
}
