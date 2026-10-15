package com.example.realworld

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object SettingsNavigationHelper {

  /**
   * Open the detailed App Permission Settings page in Android OS.
   */
  fun openAppDetailsSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Open Android Text-to-Speech (TTS) Engine & Voice Pack Settings
   * allowing the user to select or install Google Speech Services Myanmar voice pack.
   */
  fun openTtsSettings(context: Context) {
    try {
      val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      try {
        val intent = Intent(Settings.ACTION_LOCALE_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (_: Exception) {
        openAppDetailsSettings(context)
      }
    }
  }

  /**
   * Request or navigate to Battery Optimization Exemption
   * to ensure background microphone audio sensing is not killed by Android Doze.
   */
  fun openBatteryOptimizationSettings(context: Context) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
          data = Uri.parse("package:${context.packageName}")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } else {
        openAppDetailsSettings(context)
      }
    } catch (_: Exception) {
      try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (_: Exception) {
        openAppDetailsSettings(context)
      }
    }
  }

  /**
   * Open Notification Settings for this app
   */
  fun openNotificationSettings(context: Context) {
    try {
      val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openAppDetailsSettings(context)
    }
  }

  /**
   * Open Manage Overlay / Draw Over Other Apps Settings
   */
  fun openOverlaySettings(context: Context) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        ).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } else {
        openAppDetailsSettings(context)
      }
    } catch (_: Exception) {
      openAppDetailsSettings(context)
    }
  }

  /**
   * Open Location / GPS Settings
   */
  fun openLocationSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Open Sound and Volume Settings
   */
  fun openSoundSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Open OEM specific Auto-Start / Background Execution Settings
   * (Crucial for Xiaomi, Huawei, Oppo, Vivo, Samsung to keep microphone listening perpetually active)
   */
  fun openAutoStartSettings(context: Context) {
    val autoStartIntents = listOf(
      // Xiaomi MIUI
      Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
      Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
      // Huawei EMUI
      Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
      Intent().setComponent(android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
      // Oppo ColorOS
      Intent().setComponent(android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
      Intent().setComponent(android.content.ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
      // Vivo FuntouchOS
      Intent().setComponent(android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
      Intent().setComponent(android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
      // Samsung Smart Manager / Device Care
      Intent().setComponent(android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
      Intent().setComponent(android.content.ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
    )

    for (intent in autoStartIntents) {
      try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
      } catch (_: Exception) {
        // Continue trying next OEM component
      }
    }

    // Fallback to Battery Optimization settings
    openBatteryOptimizationSettings(context)
  }

  /**
   * Direct pathway for Microphone Permission.
   * Routes directly to Application Details Settings where users can grant mic access.
   */
  fun openMicrophonePermissionSettings(context: Context) {
    openAppDetailsSettings(context)
  }

  /**
   * Open General Android System Settings
   */
  fun openGeneralSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {}
  }

  /**
   * Open WiFi Settings
   */
  fun openWifiSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Open Bluetooth Settings
   */
  fun openBluetoothSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (_: Exception) {
      openGeneralSettings(context)
    }
  }

  /**
   * Check if battery optimization is currently ignored
   */
  fun isBatteryOptimizationIgnored(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else {
      true
    }
  }

  /**
   * Check if overlay permission is granted
   */
  fun canDrawOverlays(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(context)
    } else {
      true
    }
  }
}
