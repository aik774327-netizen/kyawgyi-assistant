package com.example.permissions

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Production ViewModel managing permission states, settings navigation,
 * and diagnostic telemetry for the Kyaw Gyi AI system.
 */
class PermissionViewModel(
  private val permissionManager: PermissionManager
) : ViewModel() {

  val state: StateFlow<SystemPermissionsState> = permissionManager.state
  val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticLogger.logsFlow

  fun refresh(
    gattConnected: Boolean = false,
    protocolVerified: Boolean = false,
    authenticated: Boolean = false
  ) {
    permissionManager.refreshAll(
      gattConnected = gattConnected,
      protocolVerified = protocolVerified,
      authenticated = authenticated
    )
  }

  fun openSettings(context: Context, route: String) {
    if (route.startsWith("kyawgyi://") || route.startsWith("ကောင်ဂျီး://")) {
      DirectRouteManager.executeRoute(route, context)
      return
    }
    when (route.lowercase().trim()) {
      "bluetooth", "ဘလူးတုသ်" -> AndroidSettingsManager.openBluetoothSettings(context)
      "notifications", "notification", "အသိပေးချက်" -> AndroidSettingsManager.openNotificationSettings(context)
      "accessibility", "အထူးပြုသုံးစွဲနိုင်မှု" -> AndroidSettingsManager.openAccessibilitySettings(context)
      "overlay", "မျက်နှာပြင်ပေါ်တင်ခွင့်" -> AndroidSettingsManager.openOverlaySettings(context)
      "batteryoptimization", "ဘက်ထရီသက်သာစေမှု" -> AndroidSettingsManager.openBatteryOptimizationSettings(context)
      "battery", "ဘက်ထရီ" -> AndroidSettingsManager.openBatterySettings(context)
      "location", "တည်နေရာ" -> AndroidSettingsManager.openLocationSettings(context)
      "wireless", "ကြိုးမဲ့ကွန်ရက်" -> AndroidSettingsManager.openWirelessSettings(context)
      "oem", "စနစ်နောက်ခံ" -> AndroidSettingsManager.openOemBackgroundSettings(context)
      "microphone", "mic", "အသံဖမ်းယူခွင့်" -> AndroidSettingsManager.openAppDetails(context)
      else -> AndroidSettingsManager.openAppDetails(context)
    }
  }

  fun openOemSettings(context: Context) {
    AndroidSettingsManager.openOemBackgroundSettings(context)
  }

  fun clearDiagnosticLogs() {
    DiagnosticLogger.clearLogs()
  }
}
