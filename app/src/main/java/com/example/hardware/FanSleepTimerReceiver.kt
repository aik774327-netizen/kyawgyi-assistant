package com.example.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.models.KennedeButton
import com.example.notification.NotificationManagementHelper
import com.example.realworld.PhoneIrBlaster

/**
 * Item 2: BroadcastReceiver triggered by AlarmManager (setExactAndAllowWhileIdle)
 * or by user clicking 'Cancel Timer' action in the ongoing notification.
 * Ensures the KENNEDE fan is turned off on time even if the phone screen is locked or in deep Doze mode.
 */
class FanSleepTimerReceiver : BroadcastReceiver() {

  companion object {
    const val TAG = "FanSleepTimerReceiver"
    const val ACTION_TIMER_EXPIRED = "com.example.action.FAN_SLEEP_TIMER_EXPIRED"
    const val ACTION_CANCEL_TIMER = "com.example.action.CANCEL_FAN_SLEEP_TIMER"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    Log.i(TAG, "onReceive action: $action")

    when (action) {
      ACTION_CANCEL_TIMER -> {
        KennedeAutomationEngine.activeInstance?.cancelSleepTimer()
        FanSleepTimerService.stop(context)
      }
      ACTION_TIMER_EXPIRED -> {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "aiangel:FanSleepTimerReceiverWakeLock"
        )
        wakeLock?.acquire(15_000L) // 15 seconds max to guarantee IR transmission & TTS

        try {
          val engine = KennedeAutomationEngine.activeInstance
          if (engine != null) {
            engine.onSleepTimerExpired()
          } else {
            val phoneIr = PhoneIrBlaster(context)
            if (phoneIr.hasIrEmitter) {
              val preset = phoneIr.getKennedePresetProfiles()[KennedeButton.POWER]
              if (preset != null && preset.rawTiming.isNotEmpty()) {
                phoneIr.transmitPattern(38000, preset.rawTiming.toIntArray())
              }
            }
            NotificationManagementHelper.getInstance(context).notifyIrCommandExecuted(
              deviceName = "KENNEDE KN-5996HRB",
              buttonLabel = "POWER OFF (SLEEP TIMER)",
              burmeseLabel = "အချိန်ကိုက် အလိုအလျောက် ပိတ်ခြင်း"
            )
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error executing sleep timer expired logic", e)
        } finally {
          FanSleepTimerService.stop(context)
          if (wakeLock?.isHeld == true) {
            wakeLock.release()
          }
        }
      }
    }
  }
}
