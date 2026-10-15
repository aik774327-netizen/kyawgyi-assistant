package com.example.permissions

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

enum class RouteCategory(val displayNameMy: String, val displayNameEn: String) {
  PERMISSION("ခွင့်ပြုချက်များ", "Permissions"),
  SYSTEM_SETTING("စနစ်ဆက်တင်များ", "System Settings"),
  APP_FEATURE("အက်ပ်လုပ်ဆောင်ချက်များ", "App Features")
}

data class DirectRouteItem(
  val id: String,
  val nameMy: String,
  val nameEn: String,
  val descriptionMy: String,
  val descriptionEn: String,
  val uri: String,
  val systemAction: String,
  val category: RouteCategory,
  val isGrantedCheck: (Context) -> Boolean,
  val onDirectExecute: (Context, onNavigateApp: (String) -> Unit) -> Boolean,
  val aliasUri: String? = null
)

/**
 * Direct Route & Permission Navigation Engine for Kyaw Gyi AI.
 *
 * Provides direct URL schemes (kyawgyi://... and ကောင်ဂျီး://...), standard Android intent actions
 * (e.g. android.intent.action.APPLICATION_PREFERENCES), direct OS settings dispatching,
 * and runtime permission status inspection.
 */
object DirectRouteManager {

  const val SCHEME = "kyawgyi"
  const val SCHEME_PRIMARY = "kyawgyi"
  const val SCHEME_BURMESE = "ကောင်ဂျီး"

  // Standard Direct Route URIs
  const val ROUTE_PERMISSIONS = "kyawgyi://permissions"
  const val ROUTE_SETTINGS = "kyawgyi://settings"
  const val ROUTE_MICROPHONE = "kyawgyi://permissions/microphone"
  const val ROUTE_BLUETOOTH = "kyawgyi://permissions/bluetooth"
  const val ROUTE_BATTERY = "kyawgyi://permissions/battery"
  const val ROUTE_OEM_AUTOSTART = "kyawgyi://permissions/oem"
  const val ROUTE_NOTIFICATIONS = "kyawgyi://permissions/notifications"
  const val ROUTE_LOCATION = "kyawgyi://permissions/location"
  const val ROUTE_OVERLAY = "kyawgyi://permissions/overlay"
  const val ROUTE_ACCESSIBILITY = "kyawgyi://permissions/accessibility"
  const val ROUTE_WIRELESS = "kyawgyi://permissions/wireless"
  const val ROUTE_API_KEYS = "kyawgyi://apikeys"
  const val ROUTE_VOICE_STUDIO = "kyawgyi://voice-studio"
  const val ROUTE_ECOFLOW = "kyawgyi://ecoflow"
  const val ROUTE_KENNEDE_IR = "kyawgyi://ir-controller"
  const val ROUTE_ESP32_MONITOR = "kyawgyi://esp32-monitor"
  const val ROUTE_DIRECT_ROUTES = "kyawgyi://direct-routes"

  // Burmese Direct Route URI Aliases (ကောင်ဂျီး://...)
  const val ROUTE_PERMISSIONS_MY = "ကောင်ဂျီး://ခွင့်ပြုချက်များ"
  const val ROUTE_SETTINGS_MY = "ကောင်ဂျီး://ဆက်တင်များ"
  const val ROUTE_MICROPHONE_MY = "ကောင်ဂျီး://အသံဖမ်းယူခွင့်"
  const val ROUTE_BLUETOOTH_MY = "ကောင်ဂျီး://ဘလူးတုသ်"
  const val ROUTE_BATTERY_MY = "ကောင်ဂျီး://ဘက်ထရီ"
  const val ROUTE_OEM_AUTOSTART_MY = "ကောင်ဂျီး://နောက်ခံအော်တို"
  const val ROUTE_NOTIFICATIONS_MY = "ကောင်ဂျီး://အသိပေးချက်များ"
  const val ROUTE_LOCATION_MY = "ကောင်ဂျီး://တည်နေရာ"
  const val ROUTE_DIRECT_ROUTES_MY = "ကောင်ဂျီး://တိုက်ရိုက်လမ်းကြောင်းများ"
  const val ROUTE_ECOFLOW_MY = "ကောင်ဂျီး://အီကိုဖလိုး"
  const val ROUTE_KENNEDE_IR_MY = "ကောင်ဂျီး://အဝေးထိန်း"
  const val ROUTE_ESP32_MONITOR_MY = "ကောင်ဂျီး://စောင့်ကြည့်စနစ်"

  /**
   * Complete catalog of all Direct Routes and Permissions.
   */
  fun getAllRoutes(context: Context? = null): List<DirectRouteItem> {
    return listOf(
      // 1. Microphone Direct Route
      DirectRouteItem(
        id = "microphone",
        nameMy = "အသံဖမ်းယူခွင့် (Microphone)",
        nameEn = "Microphone Access",
        descriptionMy = "ကျော်ကြီး စကားသံအမိန့်များ နားထောင်ရန် တိုက်ရိုက်ခွင့်ပြုချက် လမ်းကြောင်း",
        descriptionEn = "Direct route to microphone permission & app details settings",
        uri = ROUTE_MICROPHONE,
        systemAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
          ) == PackageManager.PERMISSION_GRANTED
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openAppDetails(ctx)
        },
        aliasUri = ROUTE_MICROPHONE_MY
      ),

      // 2. Bluetooth & Nearby Devices Direct Route
      DirectRouteItem(
        id = "bluetooth",
        nameMy = "Bluetooth နှင့် အနီးနားစက်ပစ္စည်း (BLE)",
        nameEn = "Bluetooth & Nearby Devices",
        descriptionMy = "EcoFlow Power Station ချိတ်ဆက်ရှာဖွေရန် တိုက်ရိုက် Bluetooth လမ်းကြောင်း",
        descriptionEn = "Direct route to Bluetooth settings and device discovery",
        uri = ROUTE_BLUETOOTH,
        systemAction = Settings.ACTION_BLUETOOTH_SETTINGS,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
              ctx,
              Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
              ctx,
              Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
          } else {
            ContextCompat.checkSelfPermission(
              ctx,
              Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
          }
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openBluetoothSettings(ctx)
        },
        aliasUri = ROUTE_BLUETOOTH_MY
      ),

      // 3. Battery Optimization Direct Route
      DirectRouteItem(
        id = "battery_optimization",
        nameMy = "ဘက်ထရီ ချွင်းချက် (Battery Exemption)",
        nameEn = "Ignore Battery Optimization",
        descriptionMy = "နောက်ခံအသံဖမ်းယူမှု စနစ်မရပ်တန့်စေရန် တိုက်ရိုက် ဘက်ထရီချွင်းချက် လမ်းကြောင်း",
        descriptionEn = "Direct route to battery optimization exemption whitelist",
        uri = ROUTE_BATTERY,
        systemAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
          pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openBatteryOptimizationSettings(ctx)
        }
      ),

      // 4. OEM Auto-Start & Background Activity Direct Route
      DirectRouteItem(
        id = "oem_autostart",
        nameMy = "OEM Auto-Start (Xiaomi / Samsung / Oppo)",
        nameEn = "OEM Background & Auto-Start",
        descriptionMy = "ဖုန်းထုတ်လုပ်သူ၏ Background Killer မှ အလိုအလျောက်လွတ်မြောက်စေရန် လမ်းကြောင်း",
        descriptionEn = "Direct route to manufacturer auto-start manager (MIUI, Samsung, ColorOS, etc.)",
        uri = ROUTE_OEM_AUTOSTART,
        systemAction = "com.miui.permcenter.autostart / vendor-specific",
        category = RouteCategory.SYSTEM_SETTING,
        isGrantedCheck = { _ -> false }, // Requires user inspection in OEM UI
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openOemBackgroundSettings(ctx)
        }
      ),

      // 5. Notifications Direct Route
      DirectRouteItem(
        id = "notifications",
        nameMy = "အသိပေးချက်များ (Notifications)",
        nameEn = "Push Notifications & Channels",
        descriptionMy = "နောက်ခံ အသံနားထောင်မှုနှင့် ဘက်ထရီအချက်ပြမှုများ ပြသရန် လမ်းကြောင်း",
        descriptionEn = "Direct route to application notification settings & channels",
        uri = ROUTE_NOTIFICATIONS,
        systemAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
              ctx,
              Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
          } else {
            androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
          }
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openNotificationSettings(ctx)
        }
      ),

      // 6. Floating Overlay Window Direct Route
      DirectRouteItem(
        id = "overlay",
        nameMy = "အပေါ်ယံဝင်းဒိုးတင်ခြင်း (Draw Over Other Apps)",
        nameEn = "Display Over Other Apps (Overlay)",
        descriptionMy = "အခြားအက်ပ်များအပေါ်တွင် ကျော်ကြီး Floating ခလုတ် တင်ထားရန် လမ်းကြောင်း",
        descriptionEn = "Direct route to system alert window / overlay permission",
        uri = ROUTE_OVERLAY,
        systemAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(ctx)
          } else {
            true
          }
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openOverlaySettings(ctx)
        }
      ),

      // 7. Accessibility Service Direct Route
      DirectRouteItem(
        id = "accessibility",
        nameMy = "အလိုအလျောက် ထိန်းချုပ်မှု (Accessibility)",
        nameEn = "Accessibility Service",
        descriptionMy = "ဖုန်းအတွင်း ခိုင်းစေချက်များကို အလိုအလျောက် နှိပ်ပေးနိုင်ရန် လမ်းကြောင်း",
        descriptionEn = "Direct route to Android accessibility settings",
        uri = ROUTE_ACCESSIBILITY,
        systemAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        category = RouteCategory.SYSTEM_SETTING,
        isGrantedCheck = { ctx ->
          DeviceAccessibilityManager(ctx).isAccessibilityEnabled()
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openAccessibilitySettings(ctx)
        }
      ),

      // 8. Location & GPS Direct Route
      DirectRouteItem(
        id = "location",
        nameMy = "တည်နေရာနှင့် GPS (Location)",
        nameEn = "Location / GPS Settings",
        descriptionMy = "မိုးလေဝသနှင့် ဒေသဆိုင်ရာ သတင်းအချက်အလက်များအတွက် လမ်းကြောင်း",
        descriptionEn = "Direct route to device location sources & GPS settings",
        uri = ROUTE_LOCATION,
        systemAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        category = RouteCategory.PERMISSION,
        isGrantedCheck = { ctx ->
          ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
          ) == PackageManager.PERMISSION_GRANTED
        },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openLocationSettings(ctx)
        }
      ),

      // 9. Wi-Fi & Wireless Settings Direct Route
      DirectRouteItem(
        id = "wireless",
        nameMy = "Wi-Fi နှင့် ကွန်ရက် (Wireless)",
        nameEn = "Wi-Fi & Network Settings",
        descriptionMy = "EcoFlow Wi-Fi ချိတ်ဆက်မှုနှင့် အင်တာနက် ဆက်တင်များ လမ်းကြောင်း",
        descriptionEn = "Direct route to Wi-Fi and network configuration",
        uri = ROUTE_WIRELESS,
        systemAction = Settings.ACTION_WIFI_SETTINGS,
        category = RouteCategory.SYSTEM_SETTING,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { ctx, _ ->
          AndroidSettingsManager.openWirelessSettings(ctx)
        }
      ),

      // 10. In-App: Full Permissions Dashboard
      DirectRouteItem(
        id = "app_permissions",
        nameMy = "ခွင့်ပြုချက်များ ဒက်ရှ်ဘုတ် (All Permissions)",
        nameEn = "Full Permissions Dashboard",
        descriptionMy = "ကျော်ကြီး စနစ်ခွင့်ပြုချက်များနှင့် စစ်ဆေးချက်များ အပြည့်အစုံ",
        descriptionEn = "Internal app route to comprehensive System Settings Dashboard",
        uri = ROUTE_PERMISSIONS,
        systemAction = "internal://screen/permissions",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("permissions")
          true
        }
      ),

      // 11. In-App: General App Settings
      DirectRouteItem(
        id = "app_settings",
        nameMy = "အက်ပ်အထွေထွေ ဆက်တင်များ (App Settings)",
        nameEn = "Application Settings & Shortcuts",
        descriptionMy = "Shortcuts၊ အသံနှင့် EcoFlow API သတ်မှတ်ချက်များ",
        descriptionEn = "Internal app route to General Settings Dialog",
        uri = ROUTE_SETTINGS,
        systemAction = Intent.ACTION_APPLICATION_PREFERENCES,
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("settings")
          true
        }
      ),

      // 12. In-App: API Key Manager
      DirectRouteItem(
        id = "api_keys",
        nameMy = "API Key စီမံမှု (API Key Manager)",
        nameEn = "API Key Management & Failover",
        descriptionMy = "Gemini၊ OpenAI၊ ElevenLabs နှင့် EcoFlow API Keys စီမံခန့်ခွဲမှု",
        descriptionEn = "Internal app route to secure API Key Manager dialog",
        uri = ROUTE_API_KEYS,
        systemAction = "internal://screen/apikeys",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("apikeys")
          true
        }
      ),

      // 13. In-App: AI Voice Studio
      DirectRouteItem(
        id = "voice_studio",
        nameMy = "AI Voice Studio (သဒ္ဒါနှင့် အသံ)",
        nameEn = "AI Voice Studio & Grammar",
        descriptionMy = "WAV Recording၊ မြန်မာသဒ္ဒါစနစ်နှင့် Multi-model TTS Studio",
        descriptionEn = "Internal app route to AI Voice Studio & Grammar Correction",
        uri = ROUTE_VOICE_STUDIO,
        systemAction = "internal://screen/voice_studio",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("voice_studio")
          true
        }
      ),

      // 14. In-App: EcoFlow Real-time Dashboard
      DirectRouteItem(
        id = "ecoflow",
        nameMy = "EcoFlow Real-time ဒက်ရှ်ဘုတ်",
        nameEn = "EcoFlow Telemetry Dashboard",
        descriptionMy = "ဘက်ထရီရာခိုင်နှုန်း၊ ဝပ်အားနှင့် အဝင်/အထွက် တိုက်ရိုက်ဒက်ရှ်ဘုတ်",
        descriptionEn = "Internal app route to full EcoFlow hardware dashboard",
        uri = ROUTE_ECOFLOW,
        systemAction = "internal://screen/ecoflow",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("ecoflow")
          true
        }
      ),

      // 15. In-App: Kennede IR Remote
      DirectRouteItem(
        id = "ir_controller",
        nameMy = "Kennede IR Smart Remote Controller",
        nameEn = "Kennede IR Remote Controller",
        descriptionMy = "ESP32 IR Hardware ဖြင့် အဝေးထိန်းခလုတ်များ တိုက်ရိုက်ခိုင်းစေခြင်း",
        descriptionEn = "Internal app route to Kennede IR Smart Controller dialog",
        uri = ROUTE_KENNEDE_IR,
        systemAction = "internal://screen/ir_controller",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("ir_controller")
          true
        }
      ),

      // 16. In-App: ESP32 & IR Verification Real-Time Monitor
      DirectRouteItem(
        id = "esp32_monitor",
        nameMy = "ESP32 & IR ထိန်းချုပ်မှု စောင့်ကြည့်စနစ် (Monitor)",
        nameEn = "ESP32 & IR Real-Time Monitor",
        descriptionMy = "ESP32 ချိတ်ဆက်မှု အခြေအနေနှင့် KENNEDE IR (၅) ခု၏ သက်သေအထောက်အထား စောင့်ကြည့်စနစ်",
        descriptionEn = "Real-time connection status and 5-control verification state tracking",
        uri = ROUTE_ESP32_MONITOR,
        systemAction = "internal://screen/esp32_monitor",
        category = RouteCategory.APP_FEATURE,
        isGrantedCheck = { _ -> true },
        onDirectExecute = { _, onNavigate ->
          onNavigate("esp32_monitor")
          true
        }
      )
    )
  }

  /**
   * Safe intent handler for incoming intents (Deep links, preferences action, or notification shortcuts).
   * Supports both standard `kyawgyi://...` and Burmese `ကောင်ဂျီး://...` URI schemes.
   */
  fun handleIntent(
    intent: Intent?,
    context: Context,
    onNavigate: (String) -> Unit
  ): Boolean {
    if (intent == null) return false

    // 1. Check standard APPLICATION_PREFERENCES
    if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) {
      DiagnosticLogger.logOperation(
        operation = "INTENT_ROUTE",
        setting = "APPLICATION_PREFERENCES",
        previousState = "IDLE",
        newState = "SETTINGS_OPENED",
        result = "SUCCESS"
      )
      onNavigate("settings")
      return true
    }

    // 2. Check Deep Link URI (kyawgyi://... or ကောင်ဂျီး://...)
    val data: Uri? = intent.data
    if (data != null) {
      val rawUri = data.toString()
      val decodedUri = try {
        java.net.URLDecoder.decode(rawUri, "UTF-8")
      } catch (_: Exception) {
        rawUri
      }
      val scheme = data.scheme?.lowercase() ?: ""
      val decodedScheme = try {
        java.net.URLDecoder.decode(scheme, "UTF-8")
      } catch (_: Exception) {
        scheme
      }

      if (decodedScheme == SCHEME || 
          decodedScheme == SCHEME_BURMESE || 
          decodedUri.startsWith("$SCHEME://", ignoreCase = true) || 
          decodedUri.startsWith("$SCHEME_BURMESE://", ignoreCase = true)) {
        return executeRoute(decodedUri, context, onNavigate)
      }
    }

    // 3. Check extra "navigate_to" or "route"
    val extraRoute = intent.getStringExtra("navigate_to") ?: intent.getStringExtra("route")
    if (!extraRoute.isNullOrBlank()) {
      return executeRoute(extraRoute, context, onNavigate)
    }

    return false
  }

  /**
   * Dispatches a route string (URI or keyword) directly.
   * Recognizes standard URIs, Burmese deep link schemes, and natural language keywords.
   */
  fun executeRoute(
    routeString: String,
    context: Context,
    onNavigate: (String) -> Unit = {}
  ): Boolean {
    val decoded = try {
      java.net.URLDecoder.decode(routeString.trim(), "UTF-8")
    } catch (_: Exception) {
      routeString.trim()
    }
    val normalized = decoded.lowercase()

    DiagnosticLogger.logOperation(
      operation = "EXECUTE_DIRECT_ROUTE",
      setting = normalized,
      previousState = "DISPATCHING",
      newState = "EXECUTING",
      result = "DISPATCHED"
    )

    return when {
      // 1. Microphone Access Routes
      normalized.contains("permissions/microphone") || 
      normalized.contains("အသံဖမ်းယူခွင့်") || 
      normalized.contains("microphone") ||
      (normalized.contains("အသံ") && normalized.contains("ခွင့်ပြုချက်")) -> {
        AndroidSettingsManager.openAppDetails(context)
      }

      // 2. Bluetooth & Nearby Devices Routes
      normalized.contains("permissions/bluetooth") || 
      normalized.contains("ဘလူးတုသ်") || 
      normalized.contains("bluetooth") -> {
        AndroidSettingsManager.openBluetoothSettings(context)
      }

      // 3. Battery Optimization Routes
      normalized.contains("permissions/battery") || 
      normalized.contains("ဘက်ထရီ") || 
      normalized.contains("battery") -> {
        AndroidSettingsManager.openBatteryOptimizationSettings(context)
      }

      // 4. OEM Background Autostart Routes
      normalized.contains("permissions/oem") || 
      normalized.contains("နောက်ခံအော်တို") || 
      normalized.contains("oem") -> {
        AndroidSettingsManager.openOemBackgroundSettings(context)
      }

      // 5. Notifications Routes
      normalized.contains("permissions/notifications") || 
      normalized.contains("အသိပေးချက်") || 
      normalized.contains("notifications") -> {
        AndroidSettingsManager.openNotificationSettings(context)
      }

      // 6. Overlay Window Routes
      normalized.contains("permissions/overlay") || normalized.contains("overlay") -> {
        AndroidSettingsManager.openOverlaySettings(context)
      }

      // 7. Accessibility Service Routes
      normalized.contains("permissions/accessibility") || normalized.contains("accessibility") -> {
        AndroidSettingsManager.openAccessibilitySettings(context)
      }

      // 8. Location Routes
      normalized.contains("permissions/location") || 
      normalized.contains("တည်နေရာ") || 
      normalized.contains("location") -> {
        AndroidSettingsManager.openLocationSettings(context)
      }

      // 9. Wi-Fi & Wireless Routes
      normalized.contains("permissions/wireless") || normalized.contains("wireless") -> {
        AndroidSettingsManager.openWirelessSettings(context)
      }

      // 10. In-App: API Keys Management
      normalized.contains("apikeys") || normalized == "api_keys" -> {
        onNavigate("apikeys")
        true
      }

      // 11. In-App: AI Voice Studio
      normalized.contains("voice-studio") || normalized == "voice_studio" -> {
        onNavigate("voice_studio")
        true
      }

      // 12. In-App: EcoFlow Real-Time Dashboard
      normalized.contains("ecoflow") || normalized.contains("အီကိုဖလိုး") -> {
        onNavigate("ecoflow")
        true
      }

      // 13. In-App: Kennede IR Remote Controller
      normalized.contains("ir-controller") || normalized.contains("အဝေးထိန်း") || normalized == "ir" -> {
        onNavigate("ir_controller")
        true
      }

      // 14. In-App: ESP32 & IR Real-Time Monitor
      normalized.contains("esp32-monitor") || normalized.contains("စောင့်ကြည့်စနစ်") || normalized == "esp32" -> {
        onNavigate("esp32_monitor")
        true
      }

      // 15. In-App: General Settings Dialog
      normalized.contains("settings") || normalized.contains("ဆက်တင်များ") || normalized == "ဆက်တင်" -> {
        onNavigate("settings")
        true
      }

      // 16. In-App: All Permissions & System Settings Dashboard
      normalized.contains("permissions") || normalized.contains("ခွင့်ပြုချက်များ") || normalized == "ခွင့်ပြုချက်" -> {
        onNavigate("permissions")
        true
      }

      // 17. In-App: Direct Routes Explorer Dialog
      normalized.contains("direct-routes") || normalized.contains("တိုက်ရိုက်လမ်းကြောင်း") || normalized == "routes" -> {
        onNavigate("direct_routes")
        true
      }

      else -> {
        // Default: Open System Settings Dashboard
        onNavigate("permissions")
        true
      }
    }
  }

  /**
   * Helper to copy route URI to device clipboard.
   */
  fun copyToClipboard(context: Context, text: String, label: String = "Route URI") {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText(label, text)
      clipboard.setPrimaryClip(clip)
      Toast.makeText(context, "$label ကို Clipboard ထဲ ကူးယူပြီးပါပြီ ($text)", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
  }
}
