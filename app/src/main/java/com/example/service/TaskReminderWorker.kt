package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity

/**
 * WorkManager CoroutineWorker that triggers rich local push notifications
 * for scheduled task reminders.
 */
class TaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, 0L)
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Task Reminder"
        val category = inputData.getString(KEY_CATEGORY) ?: "General"
        val notes = inputData.getString(KEY_NOTES) ?: ""

        sendTaskReminderNotification(taskId, taskTitle, category, notes)
        return Result.success()
    }

    private fun sendTaskReminderNotification(
        taskId: Long,
        taskTitle: String,
        category: String,
        notes: String
    ) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "task_reminders_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Reminders (တာဝန်သတိပေးချက်များ)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends local push notification alerts for scheduled tasks and deadlines"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "tasks")
            putExtra("target_task_id", taskId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val subText = if (notes.isNotBlank()) notes else "သတ်မှတ်ချိန်အတွင်း ပြီးမြောက်အောင် လုပ်ဆောင်ပါ"

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("🔔 တာဝန်သတိပေးချက်: $taskTitle")
            .setContentText("[$category] $subText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("[$category] $taskTitle\n$subText")
                    .setBigContentTitle("🔔 Task Reminder: $taskTitle")
                    .setSummaryText(category)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = if (taskId > 0) (NOTIFICATION_BASE_ID + taskId).toInt() else System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }

    companion object {
        const val KEY_TASK_ID = "key_task_id"
        const val KEY_TASK_TITLE = "key_task_title"
        const val KEY_CATEGORY = "key_category"
        const val KEY_NOTES = "key_notes"
        const val NOTIFICATION_BASE_ID = 88000
    }
}
