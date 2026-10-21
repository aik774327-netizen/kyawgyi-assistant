package com.example.utils

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

/**
 * OEM Background Permission State
 */
enum class OemPermissionState {
    SUPPORTED,
    GRANTED,
    NOT_GRANTED,
    UNKNOWN,
    UNAVAILABLE
}

/**
 * OEM Manufacturer Classification
 */
enum class OemType {
    XIAOMI_MIUI_HYPEROS,
    SAMSUNG_ONEUI,
    OPPO_COLOROS,
    VIVO_FUNTOUCH,
    TRANSSION_INFINIX_TECNO,
    HUAWEI_EMUI,
    ONEPLUS_OXYGENOS,
    STANDARD_AOSP,
    UNKNOWN
}

/**
 * Detailed OEM Diagnostics & Guidance
 */
data class OemPersistenceDiagnostics(
    val oemType: OemType,
    val manufacturer: String,
    val model: String,
    val androidVersion: Int,
    val isBatteryOptimizationIgnored: Boolean,
    val autoStartState: OemPermissionState,
    val guidanceTitleMM: String,
    val guidanceInstructionsMM: List<String>,
    val settingsIntent: Intent?
)

/**
 * OemBackgroundPersistenceHelper (Stage 17 Production Hardening)
 *
 * Discovers OEM background kill-policies, provides safe intent routing to vendor auto-start managers,
 * catches ActivityNotFoundException and SecurityException with robust fallback,
 * and generates clear Burmese instructions without false guarantees.
 */
class OemBackgroundPersistenceHelper(private val context: Context) {

    companion object {
        private const val TAG = "OemPersistenceHelper"
    }

    private val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    private val model = Build.MODEL

    /**
     * Identify device OEM manufacturer
     */
    fun detectOem(): OemType {
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemType.XIAOMI_MIUI_HYPEROS
            manufacturer.contains("samsung") -> OemType.SAMSUNG_ONEUI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> OemType.OPPO_COLOROS
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemType.VIVO_FUNTOUCH
            manufacturer.contains("infinix") || manufacturer.contains("tecno") || manufacturer.contains("itel") || manufacturer.contains("transsion") -> OemType.TRANSSION_INFINIX_TECNO
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemType.HUAWEI_EMUI
            manufacturer.contains("oneplus") -> OemType.ONEPLUS_OXYGENOS
            else -> OemType.STANDARD_AOSP
        }
    }

    /**
     * Get diagnostic info and settings intent for OEM background setup
     */
    fun getDiagnostics(): OemPersistenceDiagnostics {
        val oemType = detectOem()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else false

        val autoStartState = if (isIgnored) OemPermissionState.GRANTED else OemPermissionState.NOT_GRANTED
        val intent = getVendorAutoStartIntent(oemType)

        val (titleMM, instructionsMM) = when (oemType) {
            OemType.XIAOMI_MIUI_HYPEROS -> {
                "Xiaomi / HyperOS နောက်ခံအလုပ်လုပ်ခွင့်" to listOf(
                    "၁။ ‘Autostart’ (အလိုအလျောက်စတင်ခွင့်) ကို ဖွင့်ပေးပါ။",
                    "၂။ ‘Battery Saver’ ဆက်တင်တွင် ‘No Restrictions’ သို့ ရွေးချယ်ပေးပါ။",
                    "၃။ အက်ပ်ပြတင်းပေါက်ကို Lock ခတ်ထားပေးပါ။",
                    "၄။ ‘Display pop-up windows while running in background’ ကို ခွင့်ပြုပါ။"
                )
            }
            OemType.SAMSUNG_ONEUI -> {
                "Samsung / One UI ဘက်ထရီချွင်းချက်" to listOf(
                    "၁။ ‘Never sleeping apps’ (ဘယ်တော့မှမအိပ်သော အက်ပ်များ) စာရင်းထဲသို့ ထည့်သွင်းပေးပါ။",
                    "၂။ App Info > Battery တွင် ‘Unrestricted’ ကို ရွေးချယ်ပါ။",
                    "၃။ Battery > Background usage limits တွင် ချွင်းချက်ပေးပါ။"
                )
            }
            OemType.OPPO_COLOROS -> {
                "OPPO / ColorOS / Realme UI အလိုအလျောက်ဖွင့်ခွင့်" to listOf(
                    "၁။ ‘Auto-launch’ ခွင့်ပြုချက်ကို ဖွင့်ပေးပါ။",
                    "၂။ နောက်ခံစွမ်းအင်သုံးစွဲမှု ‘Allow background activity’ ကို ဖွင့်ပေးပါ။",
                    "၃။ Recent Apps စာရင်းတွင် အက်ပ်ကို အောက်သို့ဆွဲချ၍ Lock ခတ်ထားပါ။"
                )
            }
            OemType.VIVO_FUNTOUCH -> {
                "Vivo / FuntouchOS / OriginOS နောက်ခံစွမ်းအင်စီမံမှု" to listOf(
                    "၁။ ‘Autostart’ ခွင့်ပြုချက်ကို ဖွင့်ပေးပါ။",
                    "၂။ ‘High background power consumption’ ကို ခွင့်ပြုပေးပါ။",
                    "၃။ Recent Apps စာရင်းတွင် Kyaw Gyi AI ကို Lock ခတ်ထားပေးပါ။"
                )
            }
            OemType.TRANSSION_INFINIX_TECNO -> {
                "Infinix / Tecno / itel (XOS / HiOS) စွမ်းအင်စီမံမှု" to listOf(
                    "၁။ Phone Master အက်ပ် > ‘Auto-start management’ တွင် ဖွင့်ပေးပါ။",
                    "၂။ ‘Power Marathon’ / ‘Battery Lab’ နောက်ခံကန့်သတ်ချက်များတွင် ချွင်းချက်ပေးပါ။",
                    "၃။ Recent Apps တွင် အက်ပ်အား Lock ခတ်ထားပေးပါ။"
                )
            }
            OemType.HUAWEI_EMUI -> {
                "Huawei / EMUI အက်ပ်စတင်မှုစီမံခန့်ခွဲခြင်း" to listOf(
                    "၁။ ‘App Launch’ ဆက်တင်တွင် ‘Manage manually’ ရွေးချယ်ပါ။",
                    "၂။ ‘Auto-launch’ နှင့် ‘Run in background’ ကို ဖွင့်ထားပါ။"
                )
            }
            OemType.ONEPLUS_OXYGENOS -> {
                "OnePlus / OxygenOS ဘက်ထရီစီမံခန့်ခွဲမှု" to listOf(
                    "၁။ ‘Battery Optimization’ တွင် ‘Don't optimize’ ရွေးချယ်ပါ။",
                    "၂။ ‘Auto-launch’ ခွင့်ပြုချက်ကို ဖွင့်ထားပေးပါ။"
                )
            }
            else -> {
                "စနစ်နောက်ခံ လုပ်ဆောင်ခွင့် (Battery Optimization)" to listOf(
                    "၁။ ဘက်ထရီချွေတာရေး ဆက်တင်တွင် ချွင်းချက် (Don't optimize) ရွေးချယ်ပေးပါ။",
                    "၂။ အသိပေးချက်များနှင့် အသံဖမ်းခွင့်များကို အမြဲဖွင့်ထားပေးပါ။"
                )
            }
        }

        return OemPersistenceDiagnostics(
            oemType = oemType,
            manufacturer = Build.MANUFACTURER,
            model = model,
            androidVersion = Build.VERSION.SDK_INT,
            isBatteryOptimizationIgnored = isIgnored,
            autoStartState = autoStartState,
            guidanceTitleMM = titleMM,
            guidanceInstructionsMM = instructionsMM,
            settingsIntent = intent
        )
    }

    /**
     * Resolve vendor-specific autostart or battery setting intent safely
     */
    fun getVendorAutoStartIntent(oemType: OemType): Intent? {
        val intents = when (oemType) {
            OemType.XIAOMI_MIUI_HYPEROS -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))
            )
            OemType.OPPO_COLOROS -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oplus.battery", "com.oplus.battery.AppPowerConsumeActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
            )
            OemType.VIVO_FUNTOUCH -> listOf(
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            )
            OemType.TRANSSION_INFINIX_TECNO -> listOf(
                Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.autostart.AutoStartActivity")),
                Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.MainActivity")),
                Intent().setComponent(ComponentName("com.transsion.powerchecker", "com.transsion.powerchecker.ui.MainActivity"))
            )
            OemType.HUAWEI_EMUI -> listOf(
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"))
            )
            OemType.SAMSUNG_ONEUI -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )
            OemType.ONEPLUS_OXYGENOS -> listOf(
                Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
            )
            else -> emptyList()
        }

        // Check if any intent can be resolved
        val pm = context.packageManager
        for (intent in intents) {
            try {
                if (intent.resolveActivity(pm) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return intent
                }
            } catch (e: Exception) {
                Log.w(TAG, "Intent resolution error for OEM intent", e)
            }
        }

        // Fallback to standard Battery Optimization settings
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Safely attempts to open OEM settings with ActivityNotFound and SecurityException handling
     */
    fun launchSettings(intent: Intent? = null): Boolean {
        val targetIntent = intent ?: getDiagnostics().settingsIntent ?: return false
        return try {
            context.startActivity(targetIntent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "OEM intent not found on device, falling back to app settings", e)
            launchFallbackAppSettings()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException launching OEM intent, falling back", e)
            launchFallbackAppSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching settings intent", e)
            false
        }
    }

    private fun launchFallbackAppSettings(): Boolean {
        return try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed fallback to application details settings", ex)
            false
        }
    }
}
