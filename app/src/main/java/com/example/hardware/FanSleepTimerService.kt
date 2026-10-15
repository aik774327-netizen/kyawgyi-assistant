package com.example.hardware

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.notification.NotificationManagementHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Item 2: Android Foreground Service for KENNEDE Fan Sleep Timer.
 * Maintains an ongoing notification with live countdown on lock screen,
 * holds a partial WakeLock to keep the CPU awake during timing,
 * and ensures reliable auto-power-off via IR even when phone is locked.
 */
class FanSleepTimerService : Service() {

  companion object {
    private const val TAG = "FanSleepTimerService"
    const val ACTION_START = "com.example.action.START_FAN_SLEEP_TIMER_SERVICE"
    const val ACTION_STOP = "com.example.action.STOP_FAN_SLEEP_TIMER_SERVICE"
    const val EXTRA_MINUTES = "extra_minutes"

    fun start(context: Context, minutes: Int) {
      val intent = Intent(context, FanSleepTimerService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_MINUTES, minutes)
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start FanSleepTimerService", e)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, FanSleepTimerService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.startService(intent)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stop FanSleepTimerService", e)
      }
    }
  }

  private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
  private var countdownJob: Job? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private lateinit var notificationHelper: NotificationManagementHelper
  private lateinit var notificationManager: NotificationManager

  override fun onCreate() {
    super.onCreate()
    notificationHelper = NotificationManagementHelper.getInstance(this)
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    wakeLock = powerManager?.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      "aiangel:FanSleepTimerServiceWakeLock"
    )?.apply {
      setReferenceCounted(false)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: return START_NOT_STICKY

    when (action) {
      ACTION_STOP -> {
        stopCountdown()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START -> {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 30)
        startCountdown(minutes)
      }
    }

    return START_STICKY
  }

  private fun startCountdown(minutes: Int) {
    stopCountdown()

    val totalSeconds = minutes * 60
    val notification = notificationHelper.buildSleepTimerNotification(totalSeconds, minutes)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NotificationManagementHelper.NOTIF_ID_FAN_SLEEP_TIMER,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      )
    } else {
      startForeground(
        NotificationManagementHelper.NOTIF_ID_FAN_SLEEP_TIMER,
        notification
      )
    }

    try {
      wakeLock?.acquire(totalSeconds * 1000L + 15_000L)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to acquire wake lock", e)
    }

    countdownJob = serviceScope.launch {
      var remaining = totalSeconds
      while (isActive && remaining > 0) {
        delay(1000L)
        remaining--

        if (remaining % 5 == 0 || remaining <= 60) {
          val updated = notificationHelper.buildSleepTimerNotification(remaining, minutes)
          notificationManager.notify(NotificationManagementHelper.NOTIF_ID_FAN_SLEEP_TIMER, updated)
        }
      }

      if (isActive && remaining <= 0) {
        Log.i(TAG, "Timer expired in FanSleepTimerService. Triggering auto-off.")
        val expireIntent = Intent(this@FanSleepTimerService, FanSleepTimerReceiver::class.java).apply {
          action = FanSleepTimerReceiver.ACTION_TIMER_EXPIRED
        }
        sendBroadcast(expireIntent)
        stopSelf()
      }
    }
  }

  private fun stopCountdown() {
    countdownJob?.cancel()
    countdownJob = null
    try {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error releasing wake lock", e)
    }
  }

  override fun onDestroy() {
    stopCountdown()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
