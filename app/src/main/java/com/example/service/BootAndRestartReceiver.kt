package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver responsible for perpetual background microphone sensing persistence.
 * Automatically respawns the VoiceListeningService after device boot, package update,
 * or when scheduled by the watchdog / task removal monitor.
 */
class BootAndRestartReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return

    when (action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      "android.intent.action.QUICKBOOT_POWERON",
      "com.htc.intent.action.QUICKBOOT_POWERON",
      VoiceListeningService.ACTION_RESTART_VOICE_SERVICE -> {
        // Verify audio recording permission before reviving foreground service
        val hasMicPermission = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
          val serviceIntent = Intent(context, VoiceListeningService::class.java).apply {
            this.action = VoiceListeningService.ACTION_START_BACKGROUND_LISTENING
          }
          try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              context.startForegroundService(serviceIntent)
            } else {
              context.startService(serviceIntent)
            }
          } catch (_: Exception) {
            // Handled gracefully if background start restrictions apply
          }
        }
      }
    }
  }
}
