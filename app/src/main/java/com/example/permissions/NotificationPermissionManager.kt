package com.example.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Dedicated Notification Permission & Channel Verification Manager.
 * Separates runtime permissions from channel-disabled states.
 */
class NotificationPermissionManager(private val context: Context) {

  companion object {
    const val MYANMAR_RATIONALE = "နောက်ခံအသံဖမ်းယူမှု အခြေအနေနှင့် အရေးပေါ် ဘက်ထရီ/စနစ် သတိပေးချက်များကို အချိန်နှင့်တပြေးညီ ပြသနိုင်ရန် Notification ခွင့်ပြုချက် လိုအပ်ပါသည်။"
    const val ENGLISH_RATIONALE = "Notification permission is required to display ongoing background voice status and urgent alerts."
    const val MYANMAR_DENIED_FALLBACK = "Notification ခွင့်ပြုချက် မရရှိပါက နောက်ခံအခြေအနေ သတိပေးချက်များကို ပြသနိုင်မည် မဟုတ်ပါ။ သို့သော် အက်ပ်အတွင်း တိုက်ရိုက်လုပ်ဆောင်ချက်များသည် ပုံမှန်အတိုင်း ဆက်လက်အလုပ်လုပ်ပါမည်။"
    const val ENGLISH_DENIED_FALLBACK = "Without notification permission, background status notifications cannot be shown. In-app features continue to function normally."
  }

  fun checkReadiness(channelId: String? = null): NotificationReadiness {
    val notifCompat = NotificationManagerCompat.from(context)
    if (!notifCompat.areNotificationsEnabled()) {
      return NotificationReadiness.NOTIFICATION_PERMISSION_DENIED
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val isGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED

      if (!isGranted) {
        return NotificationReadiness.NOTIFICATION_PERMISSION_DENIED
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && channelId != null) {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      val channel = nm?.getNotificationChannel(channelId)
      if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
        return NotificationReadiness.NOTIFICATION_CHANNEL_DISABLED
      }
    }

    return NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED
  }

  fun areNotificationsGranted(): Boolean {
    return checkReadiness() == NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED
  }
}
