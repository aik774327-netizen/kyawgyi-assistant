package com.example.voice.hardware

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * KyawGyiAccessibilityService enables system-level actions (Home, Back, Recents, Notifications)
 * requested by Gemini function calls or local offline commands.
 */
class KyawGyiAccessibilityService : AccessibilityService() {

  companion object {
    private const val TAG = "KyawGyiAccessibility"
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.i(TAG, "KyawGyi Accessibility Service Connected")
    SystemActionManager.activeAccessibilityService = this
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // DATA MINIMIZATION PRINCIPLE (D5):
    // Process only the absolute minimum information needed for system action execution.
    // Do NOT log, store, inspect, or transmit user screen content, keystrokes, or text.
    // Kyaw Gyi operates strictly on zero-telemetry and zero screen data exfiltration.
    // Service exists purely for performGlobalAction (Home, Back, Recents, Notifications) invocations.
  }

  override fun onInterrupt() {
    Log.w(TAG, "KyawGyi Accessibility Service Interrupted")
  }

  override fun onUnbind(intent: Intent?): Boolean {
    Log.i(TAG, "KyawGyi Accessibility Service Unbound")
    SystemActionManager.activeAccessibilityService = null
    return super.onUnbind(intent)
  }
}
