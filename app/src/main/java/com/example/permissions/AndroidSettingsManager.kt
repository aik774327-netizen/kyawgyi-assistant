package com.example.permissions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Production-grade Android Settings Navigator.
 * Exposes direct pathways to system settings with validated Intent dispatching,
 * multi-tier fallback mechanisms, and full ActivityNotFoundException immunity.
 */
object AndroidSettingsManager {

  private const val TAG = "AndroidSettingsManager"

  /**
   * Universal safe intent launcher with automatic multi-tier fallback.
   */
  fun safeStartActivity(
    context: Context,
    primaryIntent: Intent,
    fallbackIntent: Intent? = null,
    settingName: String = "Unknown"
  ): Boolean {
    DiagnosticLogger.logSettingsOpen(settingName, primaryIntent.action ?: primaryIntent.component?.flattenToString() ?: "CUSTOM_INTENT")
    return try {
      primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      val pm = context.packageManager
      if (primaryIntent.resolveActivity(pm) != null) {
        context.startActivity(primaryIntent)
        true
      } else {
        throw ActivityNotFoundException("Primary intent not resolvable for $settingName")
      }
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Primary intent failed for $settingName: ${e.message}. Attempting fallback...")
      if (fallbackIntent != null) {
        try {
          fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          val pm = context.packageManager
          if (fallbackIntent.resolveActivity(pm) != null) {
            context.startActivity(fallbackIntent)
            DiagnosticLogger.logOperation(
              operation = "SETTINGS_FALLBACK",
              setting = settingName,
              previousState = "PRIMARY_FAILED",
              newState = "FALLBACK_LAUNCHED",
              result = "SUCCESS",
              error = e.message
            )
            true
          } else {
            openAppDetails(context)
            false
          }
        } catch (fallbackEx: Exception) {
          Log.e(TAG, "Fallback intent failed for $settingName", fallbackEx)
          openAppDetails(context)
          false
        }
      } else {
        openAppDetails(context)
        false
      }
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error launching $settingName", e)
      openAppDetails(context)
      false
    }
  }

  /**
   * Direct pathway: Application Details Settings page.
   */
  fun openAppDetails(context: Context): Boolean {
    return try {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      DiagnosticLogger.logSettingsOpen("AppDetails", Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      context.startActivity(intent)
      true
    } catch (e: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Direct pathway: Bluetooth Adapter & Pairing Settings.
   */
  fun openBluetoothSettings(context: Context): Boolean {
    val primary = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS)
    return safeStartActivity(context, primary, fallback, "Bluetooth")
  }

  /**
   * Direct pathway: Microphone / App Permission settings.
   */
  fun openMicrophoneSettings(context: Context): Boolean {
    return openAppDetails(context)
  }

  /**
   * Direct pathway: Notification Settings (App specific or Channel specific).
   */
  fun openNotificationSettings(context: Context): Boolean {
    val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
    }
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
    }
    return safeStartActivity(context, primary, fallback, "Notifications")
  }

  /**
   * Direct pathway: Android Accessibility Services Settings.
   */
  fun openAccessibilitySettings(context: Context): Boolean {
    val primary = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val fallback = Intent(Settings.ACTION_SETTINGS)
    return safeStartActivity(context, primary, fallback, "Accessibility")
  }

  /**
   * Direct pathway: Overlay / Draw Over Other Apps Settings.
   */
  fun openOverlaySettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val primary = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
      )
      val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
      return safeStartActivity(context, primary, fallback, "Overlay")
    }
    return openAppDetails(context)
  }

  /**
   * Direct pathway: Battery Optimization Settings.
   */
  fun openBatteryOptimizationSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val primary = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
      }
      val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      return safeStartActivity(context, primary, fallback, "BatteryOptimization")
    }
    return openAppDetails(context)
  }

  /**
   * Direct pathway: General Battery Usage & Saver Settings.
   */
  fun openBatterySettings(context: Context): Boolean {
    val primary = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    val fallback = Intent(Settings.ACTION_SETTINGS)
    return safeStartActivity(context, primary, fallback, "BatteryUsage")
  }

  /**
   * Direct pathway: Location / GPS Source Settings.
   */
  fun openLocationSettings(context: Context): Boolean {
    val primary = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    val fallback = Intent(Settings.ACTION_SETTINGS)
    return safeStartActivity(context, primary, fallback, "Location")
  }

  /**
   * Direct pathway: Wi-Fi & Wireless Settings.
   */
  fun openWirelessSettings(context: Context): Boolean {
    val primary = Intent(Settings.ACTION_WIFI_SETTINGS)
    val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS)
    return safeStartActivity(context, primary, fallback, "Wireless")
  }

  /**
   * Direct pathway: General Android System Settings.
   */
  fun openGeneralSettings(context: Context): Boolean {
    return try {
      val intent = Intent(Settings.ACTION_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      DiagnosticLogger.logSettingsOpen("GeneralSettings", Settings.ACTION_SETTINGS)
      context.startActivity(intent)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch general settings", e)
      false
    }
  }

  /**
   * OEM Background Execution & Auto-Start settings dispatcher.
   * Dynamically inspects device manufacturer and safely attempts vendor-specific activities.
   */
  fun openOemBackgroundSettings(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    DiagnosticLogger.logOperation(
      operation = "OEM_DETECT",
      setting = "AutoStart",
      previousState = "UNKNOWN",
      newState = manufacturer,
      result = "MATCHING_OEM_RULES"
    )

    val candidateIntents = mutableListOf<Intent>()

    when {
      manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
      }
      manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
      }
      manufacturer.contains("samsung") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
      }
      manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
      }
      manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
      }
      manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") || manufacturer.contains("itel") -> {
        candidateIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.autostart.AutoStartActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.autostart.AutoStartActivity")))
        candidateIntents.add(Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.transsion.phonemaster.home.HomeActivity")))
      }
    }

    // Try manufacturer-specific candidates
    for (intent in candidateIntents) {
      try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        DiagnosticLogger.logOperation(
          operation = "OEM_STARTUP_LAUNCH",
          setting = "AutoStart",
          previousState = manufacturer,
          newState = intent.component?.className ?: "UNKNOWN",
          result = "SUCCESS"
        )
        return true
      } catch (_: Exception) {
        // Continue to next OEM candidate
      }
    }

    // Fallback: Battery Optimization settings or App details
    return openBatteryOptimizationSettings(context)
  }
}
