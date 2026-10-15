package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppNotificationRecord(
  val id: Long = System.currentTimeMillis(),
  val title: String,
  val message: String,
  val type: NotificationType,
  val timestamp: String,
  val extraData: String = ""
)

enum class NotificationType {
  LOW_BATTERY,
  IR_COMMAND_SUCCESS,
  SYSTEM_INFO
}

data class NotificationSettings(
  val lowBatteryThreshold: Int = 20,
  val batteryAlertsEnabled: Boolean = true,
  val irSuccessAlertsEnabled: Boolean = true
)

/**
 * Manages notification channels, user notification preferences, and delivers notifications
 * when the EcoFlow power station reaches a low battery threshold or an IR command executes successfully.
 */
class NotificationManagementHelper private constructor(context: Context) {

  private val appContext = context.applicationContext
  private val prefs: SharedPreferences = appContext.getSharedPreferences("kyawgyi_notifications_prefs", Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

  private val _notificationHistory = MutableStateFlow<List<AppNotificationRecord>>(emptyList())
  val notificationHistory: StateFlow<List<AppNotificationRecord>> = _notificationHistory.asStateFlow()

  // Cooldown tracker to avoid spamming low battery alerts continuously
  private var lastBatteryNotificationTimeMs: Long = 0L
  private var lastNotifiedBatteryLevel: Int = -1

  companion object {
    const val CHANNEL_LOW_BATTERY = "channel_ecoflow_low_battery"
    const val CHANNEL_IR_SUCCESS = "channel_ir_remote_success"
    const val CHANNEL_FAN_SLEEP_TIMER = "channel_fan_sleep_timer"

    private const val NOTIF_ID_BATTERY = 4001
    private const val NOTIF_ID_IR_BASE = 5000
    const val NOTIF_ID_FAN_SLEEP_TIMER = 6001

    private const val PREF_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
    private const val PREF_BATTERY_ALERTS_ENABLED = "battery_alerts_enabled"
    private const val PREF_IR_ALERTS_ENABLED = "ir_alerts_enabled"

    private const val BATTERY_NOTIFICATION_COOLDOWN_MS = 120_000L // 2 minutes cooldown between same-level notifications

    @Volatile
    private var INSTANCE: NotificationManagementHelper? = null

    fun getInstance(context: Context): NotificationManagementHelper {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: NotificationManagementHelper(context).also { INSTANCE = it }
      }
    }
  }

  init {
    createNotificationChannels()
  }

  private fun loadSettings(): NotificationSettings {
    return NotificationSettings(
      lowBatteryThreshold = prefs.getInt(PREF_LOW_BATTERY_THRESHOLD, 20),
      batteryAlertsEnabled = prefs.getBoolean(PREF_BATTERY_ALERTS_ENABLED, true),
      irSuccessAlertsEnabled = prefs.getBoolean(PREF_IR_ALERTS_ENABLED, true)
    )
  }

  fun updateSettings(newSettings: NotificationSettings) {
    prefs.edit()
      .putInt(PREF_LOW_BATTERY_THRESHOLD, newSettings.lowBatteryThreshold)
      .putBoolean(PREF_BATTERY_ALERTS_ENABLED, newSettings.batteryAlertsEnabled)
      .putBoolean(PREF_IR_ALERTS_ENABLED, newSettings.irSuccessAlertsEnabled)
      .apply()
    _settings.value = newSettings
  }

  fun setLowBatteryThreshold(threshold: Int) {
    val updated = _settings.value.copy(lowBatteryThreshold = threshold.coerceIn(5, 50))
    updateSettings(updated)
  }

  fun toggleBatteryAlerts(enabled: Boolean) {
    val updated = _settings.value.copy(batteryAlertsEnabled = enabled)
    updateSettings(updated)
  }

  fun toggleIrSuccessAlerts(enabled: Boolean) {
    val updated = _settings.value.copy(irSuccessAlertsEnabled = enabled)
    updateSettings(updated)
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      // Low Battery Channel (High Importance, Vibration)
      val batteryChannel = NotificationChannel(
        CHANNEL_LOW_BATTERY,
        "EcoFlow ဘက်ထရီအားနည်း သတိပေးချက်များ (Low Battery Alerts)",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "EcoFlow သတ်မှတ်ထားသော ဘက်ထရီအဆင့်ရောက်ရှိပါက သတိပေးချက်ပေးပို့ရန်"
        enableVibration(true)
        enableLights(true)
        lightColor = Color.RED
        vibrationPattern = longArrayOf(0, 400, 200, 400)
      }

      // IR Command Success Channel (Default Importance)
      val irChannel = NotificationChannel(
        CHANNEL_IR_SUCCESS,
        "အနီအောက်ရောင်ခြည် အမိန့်ပေး အောင်မြင်မှု (IR Remote Execution)",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "KENNEDE KN-5996HRB အဝေးထိန်းမှ အချက်ပြပို့ဆောင်မှု အောင်မြင်သည့်အခါ သတိပေးရန်"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 100, 100, 100)
      }

      // Fan Sleep Timer Channel (Low Importance for persistent countdown, no sound spam)
      val sleepTimerChannel = NotificationChannel(
        CHANNEL_FAN_SLEEP_TIMER,
        "စမတ်ပန်ကာ အချိန်မှတ်စနစ် (Fan Sleep Timer)",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "KENNEDE KN-5996HRB စမတ်ပန်ကာ အိပ်စက်ချိန် လက်ကျန်အချိန် ပြသရန်"
        setShowBadge(false)
        enableVibration(false)
      }

      notificationManager.createNotificationChannel(batteryChannel)
      notificationManager.createNotificationChannel(irChannel)
      notificationManager.createNotificationChannel(sleepTimerChannel)
    }
  }

  /**
   * Item 2: Builds an ongoing notification for the Fan Sleep Timer with live countdown and cancel action.
   */
  fun buildSleepTimerNotification(remainingSeconds: Int, totalMinutes: Int): android.app.Notification {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openPendingIntent = PendingIntent.getActivity(
      appContext,
      1001,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val cancelIntent = Intent("com.example.action.CANCEL_FAN_SLEEP_TIMER").apply {
      setPackage(appContext.packageName)
    }
    val cancelPendingIntent = PendingIntent.getBroadcast(
      appContext,
      1002,
      cancelIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(appContext, CHANNEL_FAN_SLEEP_TIMER)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("KENNEDE စမတ်ပန်ကာ အချိန်မှတ်စနစ်")
      .setContentText("လက်ကျန်အချိန်: $timeFormatted ($totalMinutes မိနစ်ပြီးလျှင် အလိုအလျောက် ပိတ်ပါမည်)")
      .setContentIntent(openPendingIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .addAction(android.R.drawable.ic_menu_close_clear_cancel, "အချိန်မှတ် ဖျက်မည်", cancelPendingIntent)
      .build()
  }

  /**
   * Evaluates EcoFlow battery state and alerts user if it drops to or below lowBatteryThreshold.
   */
  fun checkAndNotifyLowBattery(batteryPercentage: Int, remainingMinutes: Int, isCharging: Boolean) {
    val currentSettings = _settings.value
    if (!currentSettings.batteryAlertsEnabled) return
    if (isCharging) return // Do not alert when battery is being actively charged

    if (batteryPercentage <= currentSettings.lowBatteryThreshold) {
      val currentTime = System.currentTimeMillis()
      val timeSinceLastAlert = currentTime - lastBatteryNotificationTimeMs
      val isDifferentLevel = batteryPercentage != lastNotifiedBatteryLevel

      // Alert if different level or cooldown expired
      if (isDifferentLevel || timeSinceLastAlert > BATTERY_NOTIFICATION_COOLDOWN_MS) {
        lastBatteryNotificationTimeMs = currentTime
        lastNotifiedBatteryLevel = batteryPercentage

        val remainingHours = remainingMinutes / 60
        val remainingMins = remainingMinutes % 60
        val remainingStr = if (remainingHours > 0) "~$remainingHours နာရီ $remainingMins မိနစ်" else "~$remainingMins မိနစ်"

        val title = "⚠️ EcoFlow ဘက်ထရီအားနည်း သတိပေးချက် ($batteryPercentage%)"
        val message = "EcoFlow ဓာတ်အားပေးစက် ဘက်ထရီ ${batteryPercentage}% သို့ ရောက်ရှိနေပါပြီ။ ကျန်ရှိချိန် $remainingStr ဖြစ်ပါသည်။ လိုအပ်ပါက အားသွင်းပါ သို့မဟုတ် AC ပေါက်ကို ပိတ်ထားပါ။"

        sendNotification(
          notificationId = NOTIF_ID_BATTERY,
          channelId = CHANNEL_LOW_BATTERY,
          title = title,
          message = message,
          priority = NotificationCompat.PRIORITY_HIGH,
          type = NotificationType.LOW_BATTERY,
          extra = "BATTERY_$batteryPercentage%"
        )
      }
    } else {
      // Reset tracker when battery recovers above threshold
      if (batteryPercentage > currentSettings.lowBatteryThreshold + 5) {
        lastNotifiedBatteryLevel = -1
      }
    }
  }

  /**
   * Alerts user when an IR command is successfully executed by the virtual remote.
   */
  fun notifyIrCommandExecuted(deviceName: String, buttonLabel: String, burmeseLabel: String) {
    val currentSettings = _settings.value
    if (!currentSettings.irSuccessAlertsEnabled) return

    val title = "📡 IR Remote: $burmeseLabel အမိန့်ပေး အောင်မြင်ပါသည်"
    val message = "$deviceName စက်သို့ '$buttonLabel' ($burmeseLabel) အနီအောက်ရောင်ခြည် အချက်ပြကုဒ် အောင်မြင်စွာ ပို့ဆောင်ပြီးပါပြီ။"

    val uniqueId = NOTIF_ID_IR_BASE + (System.currentTimeMillis() % 1000).toInt()

    sendNotification(
      notificationId = uniqueId,
      channelId = CHANNEL_IR_SUCCESS,
      title = title,
      message = message,
      priority = NotificationCompat.PRIORITY_DEFAULT,
      type = NotificationType.IR_COMMAND_SUCCESS,
      extra = "BUTTON_$buttonLabel"
    )
  }

  /**
   * Sends test notification to verify setup and permissions.
   */
  fun sendTestAlert(type: NotificationType) {
    when (type) {
      NotificationType.LOW_BATTERY -> {
        sendNotification(
          notificationId = NOTIF_ID_BATTERY,
          channelId = CHANNEL_LOW_BATTERY,
          title = "🔔 [စမ်းသပ်မှု] EcoFlow ဘက်ထရီ သတိပေးချက် (18%)",
          message = "သတ်မှတ်ထားသော သတိပေးချက်ဘောင် (18%) အောက်သို့ ရောက်ရှိနေပါသဖြင့် သတိပေးခြင်း ဖြစ်ပါသည်။",
          priority = NotificationCompat.PRIORITY_HIGH,
          type = NotificationType.LOW_BATTERY,
          extra = "TEST_BATTERY"
        )
      }
      NotificationType.IR_COMMAND_SUCCESS -> {
        sendNotification(
          notificationId = NOTIF_ID_IR_BASE + 99,
          channelId = CHANNEL_IR_SUCCESS,
          title = "🔔 [စမ်းသပ်မှု] KENNEDE IR အမိန့်ပေး အောင်မြင်ခြင်း",
          message = "KENNEDE KN-5996HRB ပန်ကာပါဝါ အချက်ပြကုဒ် အောင်မြင်စွာ ပို့လွှတ်နိုင်ခဲ့ပါသည်။",
          priority = NotificationCompat.PRIORITY_DEFAULT,
          type = NotificationType.IR_COMMAND_SUCCESS,
          extra = "TEST_IR"
        )
      }
      NotificationType.SYSTEM_INFO -> {
        sendNotification(
          notificationId = 3001,
          channelId = CHANNEL_IR_SUCCESS,
          title = "✨ ကျော်ကြီး အသိပေးချက် စနစ် အသင့်ရှိပါသည်",
          message = "အသိပေးချက် ဝန်ဆောင်မှု စနစ်များ အားလုံး ကောင်းမွန်စွာ အလုပ်လုပ်နေပါသည်။",
          priority = NotificationCompat.PRIORITY_DEFAULT,
          type = NotificationType.SYSTEM_INFO,
          extra = "TEST_SYSTEM"
        )
      }
    }
  }

  fun clearNotificationHistory() {
    _notificationHistory.value = emptyList()
  }

  private fun sendNotification(
    notificationId: Int,
    channelId: String,
    title: String,
    message: String,
    priority: Int,
    type: NotificationType,
    extra: String
  ) {
    // Record in local history
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val record = AppNotificationRecord(
      title = title,
      message = message,
      type = type,
      timestamp = timestamp,
      extraData = extra
    )
    _notificationHistory.value = listOf(record) + _notificationHistory.value.take(29)

    // Check runtime permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
      }
    }

    try {
      val intent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("OPEN_NOTIFICATION_TYPE", type.name)
      }
      val pendingIntent = PendingIntent.getActivity(
        appContext,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val builder = NotificationCompat.Builder(appContext, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(priority)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

      if (type == NotificationType.LOW_BATTERY) {
        builder.setColor(android.graphics.Color.RED)
      } else {
        builder.setColor(0xFF38BDF8.toInt())
      }

      val notificationManager = NotificationManagerCompat.from(appContext)
      notificationManager.notify(notificationId, builder.build())
    } catch (_: SecurityException) {
      // Permission not granted
    } catch (_: Exception) {
      // Notification failed gracefully
    }
  }
}
