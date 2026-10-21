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
 * Background WorkManager Worker to trigger high-priority alerts
 * when a Pomodoro focus timer or break session completes in the background.
 */
class PomodoroNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionName = inputData.getString(KEY_SESSION_NAME) ?: "Focus Session"
        val category = inputData.getString(KEY_CATEGORY) ?: "Work"
        val durationMins = inputData.getInt(KEY_DURATION_MINS, 25)

        showSessionCompleteNotification(sessionName, category, durationMins)
        return Result.success()
    }

    private fun showSessionCompleteNotification(sessionName: String, category: String, durationMins: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "pomodoro_timer_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pomodoro Focus Timer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a Pomodoro focus or break session finishes"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "focus_timer")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Focus Session Complete! [$category]")
            .setContentText("Awesome job! \"$sessionName\" ($durationMins min) has completed.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Awesome job! \"$sessionName\" ($durationMins min) in category '$category' has completed. Take a break or start your next sprint!")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_SESSION_NAME = "key_session_name"
        const val KEY_CATEGORY = "key_category"
        const val KEY_DURATION_MINS = "key_duration_mins"
        const val NOTIFICATION_ID = 9021
    }
}
