package com.example.permissions

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Dedicated Accessibility Service Verification Manager.
 * Checks whether the application's accessibility service is actively enabled in OS settings.
 * Never claims enabled without verification from Android system provider.
 */
class DeviceAccessibilityManager(private val context: Context) {

  fun checkReadiness(): AccessibilityReadiness {
    val enabledServices = Settings.Secure.getString(
      context.contentResolver,
      Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return AccessibilityReadiness.ACCESSIBILITY_SERVICE_DISABLED

    val isEnabled = !TextUtils.isEmpty(enabledServices) && enabledServices.contains(context.packageName)
    return if (isEnabled) {
      AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED
    } else {
      AccessibilityReadiness.ACCESSIBILITY_SERVICE_DISABLED
    }
  }

  fun isAccessibilityEnabled(): Boolean {
    return checkReadiness() == AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED
  }
}
