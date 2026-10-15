package com.example.permissions

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Dedicated Overlay (SYSTEM_ALERT_WINDOW) Verification Manager.
 * Checks whether the app can draw floating UI overlays over other applications.
 */
class OverlayPermissionManager(private val context: Context) {

  fun checkReadiness(): OverlayReadiness {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (Settings.canDrawOverlays(context)) {
        OverlayReadiness.OVERLAY_GRANTED
      } else {
        OverlayReadiness.OVERLAY_DENIED
      }
    } else {
      OverlayReadiness.OVERLAY_GRANTED
    }
  }

  fun canDrawOverlays(): Boolean {
    return checkReadiness() == OverlayReadiness.OVERLAY_GRANTED
  }
}
