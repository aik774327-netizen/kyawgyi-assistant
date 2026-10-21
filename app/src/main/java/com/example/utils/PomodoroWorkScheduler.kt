package com.example.utils

import android.content.Context
import androidx.work.*
import com.example.service.PomodoroNotificationWorker
import java.util.concurrent.TimeUnit

/**
 * Helper to schedule and cancel background alert notifications via WorkManager
 */
object PomodoroWorkScheduler {
    private const val UNIQUE_WORK_NAME = "kyaw_gyi_pomodoro_session_worker"

    fun scheduleCompletionAlert(
        context: Context,
        remainingSeconds: Long,
        sessionName: String,
        category: String,
        durationMinutes: Int
    ) {
        if (remainingSeconds <= 0) return

        val inputData = Data.Builder()
            .putString(PomodoroNotificationWorker.KEY_SESSION_NAME, sessionName)
            .putString(PomodoroNotificationWorker.KEY_CATEGORY, category)
            .putInt(PomodoroNotificationWorker.KEY_DURATION_MINS, durationMinutes)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PomodoroNotificationWorker>()
            .setInitialDelay(remainingSeconds, TimeUnit.SECONDS)
            .setInputData(inputData)
            .addTag(UNIQUE_WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelCompletionAlert(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        } catch (_: Exception) {
            // Ignore if WorkManager is not initialized
        }
    }
}
