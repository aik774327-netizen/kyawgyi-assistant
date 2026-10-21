package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * High-priority Exact Alarm Receiver for Task Reminders.
 * 
 * Triggered by AlarmManager (setExactAndAllowWhileIdle / setAlarmClock)
 * to guarantee instantaneous alerting even when the device is locked or in Doze mode.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Reminder"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "General"
        val priority = intent.getStringExtra(EXTRA_PRIORITY) ?: "MEDIUM"
        val notes = intent.getStringExtra(EXTRA_NOTES) ?: "သတ်မှတ်ချိန်အတွင်း ပြီးမြောက်အောင် လုပ်ဆောင်ပါ"

        Log.i(TAG, "TaskAlarmReceiver received exact alarm for task #$taskId: '$taskTitle'")

        // Acquire a short wake lock to guarantee CPU stays active during notification construction
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KyawGyi:TaskAlarmWakeLock"
        )
        wakeLock?.acquire(5000L) // 5 second safety limit

        try {
            showTaskNotification(context, taskId, taskTitle, category, priority, notes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show alarm notification: ${e.message}", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }

    private fun showTaskNotification(
        context: Context,
        taskId: Long,
        taskTitle: String,
        category: String,
        priority: String,
        notes: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_reminders_channel"

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Reminders (တာဝန်သတိပေးချက်များ)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends high-priority alerts for scheduled tasks and deadlines"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                setSound(
                    defaultSoundUri,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
            putExtra("target_task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val priorityBadge = when (priority.uppercase()) {
            "HIGH" -> "🔴 High"
            "LOW" -> "🟢 Low"
            else -> "🟡 Normal"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 တာဝန်သတိပေးချက်: $taskTitle")
            .setContentText("[$category • $priorityBadge] $notes")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("[$category • $priorityBadge]\n$taskTitle\n$notes")
                    .setBigContentTitle("🔔 Task Reminder: $taskTitle")
                    .setSummaryText(category)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = if (taskId > 0) (NOTIFICATION_BASE_ID + taskId).toInt() else (System.currentTimeMillis() % 100000).toInt()
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "TaskAlarmReceiver"
        const val ACTION_TASK_ALARM = "com.example.action.TASK_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_PRIORITY = "extra_priority"
        const val EXTRA_NOTES = "extra_notes"
        const val NOTIFICATION_BASE_ID = 88000
    }
}
