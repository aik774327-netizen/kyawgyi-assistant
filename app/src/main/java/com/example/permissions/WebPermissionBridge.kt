package com.example.permissions

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * Normalized DTO for Web / WebView permission query response.
 */
data class WebPermissionStatusDto(
  val permission: String,
  val status: String,
  val canRequest: Boolean,
  val canOpenSettings: Boolean,
  val userMessageMyanmar: String,
  val userMessageEnglish: String
) {
  fun toJson(): String {
    return """{"permission":"$permission","status":"$status","canRequest":$canRequest,"canOpenSettings":$canOpenSettings,"userMessageMyanmar":"$userMessageMyanmar","userMessageEnglish":"$userMessageEnglish"}"""
  }
}

/**
 * Clean native Android bridge interface for WebView / Web Bluetooth contexts.
 * Ensures web layers never manipulate permissions directly or access privileged native APIs.
 */
class WebPermissionBridge(
  private val context: Context,
  private val permissionManager: PermissionManager
) {

  @JavascriptInterface
  fun queryPermission(permissionId: String): String {
    val items = permissionManager.state.value.items
    val matched = items.firstOrNull { it.id.equals(permissionId, ignoreCase = true) }

    val dto = if (matched != null) {
      WebPermissionStatusDto(
        permission = matched.id,
        status = matched.currentStatus.name,
        canRequest = matched.requestable && matched.currentStatus != PermissionStatus.GRANTED,
        canOpenSettings = true,
        userMessageMyanmar = matched.userMessageMyanmar,
        userMessageEnglish = matched.userMessageEnglish
      )
    } else {
      WebPermissionStatusDto(
        permission = permissionId,
        status = PermissionStatus.UNKNOWN.name,
        canRequest = false,
        canOpenSettings = false,
        userMessageMyanmar = "အချက်အလက် မသိရှိရပါ",
        userMessageEnglish = "Unknown permission requested"
      )
    }

    return dto.toJson()
  }

  @JavascriptInterface
  fun openSettings(route: String): Boolean {
    return when (route.lowercase()) {
      "bluetooth" -> AndroidSettingsManager.openBluetoothSettings(context)
      "notifications" -> AndroidSettingsManager.openNotificationSettings(context)
      "accessibility" -> AndroidSettingsManager.openAccessibilitySettings(context)
      "overlay" -> AndroidSettingsManager.openOverlaySettings(context)
      "battery" -> AndroidSettingsManager.openBatteryOptimizationSettings(context)
      "location" -> AndroidSettingsManager.openLocationSettings(context)
      else -> AndroidSettingsManager.openAppDetails(context)
    }
  }

  @JavascriptInterface
  fun getSystemReadiness(): String {
    val state = permissionManager.state.value
    return """{"overallReadiness":"${state.overallReadiness.name}","microphone":"${state.microphoneReadiness.name}","bluetooth":"${state.bluetoothReadiness.name}","notifications":"${state.notificationReadiness.name}","ecoFlowBle":"${state.ecoFlowBleReadiness.name}","battery":"${state.batteryReadiness.name}"}"""
  }
}
