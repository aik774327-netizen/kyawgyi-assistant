package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.*
import com.example.data.Task
import com.example.receiver.TaskAlarmReceiver
import com.example.service.TaskReminderWorker
import java.util.concurrent.TimeUnit

/**
 * Robust Dual-Engine Task Reminder Scheduler.
 *
 * Integrates:
 * 1. Hardware AlarmManager (setExactAndAllowWhileIdle / setAlarmClock):
 *    Guarantees exact-to-the-second delivery even in deep Doze mode / screen-off.
 * 2. Android WorkManager (OneTimeWorkRequest):
 *    Ensures background execution backup and reliability across OS power-save policies.
 * 3. Exact Alarm permission checks and graceful fallbacks for Android 12+ (API 31+).
 */
object TaskReminderScheduler {

    private const val TAG = "TaskReminderScheduler"
    private const val UNIQUE_WORK_PREFIX = "task_reminder_work_"

    /**
     * Checks if the app can schedule exact alarms on Android 12+ (API 31+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
    }

    /**
     * Open system settings for exact alarm permission on Android 12+.
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open exact alarm settings: ${e.message}")
            }
        }
    }

    /**
     * Schedule a task reminder notification with a specific delay in milliseconds.
     */
    fun scheduleReminderWithDelay(
        context: Context,
        task: Task,
        delayMillis: Long
    ) {
        val triggerAtMillis = System.currentTimeMillis() + maxOf(1000L, delayMillis)
        scheduleExactAlarm(context, task, triggerAtMillis)
        scheduleWorkManagerFallback(context, task, maxOf(1000L, delayMillis))
    }

    /**
     * Schedule a task reminder at an exact due timestamp (epoch milliseconds).
     */
    fun scheduleReminderAtTimestamp(
        context: Context,
        task: Task,
        targetEpochMillis: Long
    ) {
        val currentMillis = System.currentTimeMillis()
        val delayMillis = targetEpochMillis - currentMillis
        if (delayMillis <= 0) {
            // Target is past due, trigger immediate alert in 2 seconds
            scheduleReminderWithDelay(context, task, 2000L)
        } else {
            scheduleExactAlarm(context, task, targetEpochMillis)
            scheduleWorkManagerFallback(context, task, delayMillis)
        }
    }

    /**
     * Instantly trigger a test reminder notification with a minimal delay (3 seconds).
     */
    fun triggerImmediateTestReminder(
        context: Context,
        task: Task
    ) {
        scheduleReminderWithDelay(context, task, 3000L)
    }

    /**
     * Schedules exact hardware alarm via AlarmManager.
     */
    private fun scheduleExactAlarm(
        context: Context,
        task: Task,
        triggerAtMillis: Long
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = TaskAlarmReceiver.ACTION_TASK_ALARM
                putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
                putExtra(TaskAlarmReceiver.EXTRA_TASK_TITLE, task.title)
                putExtra(TaskAlarmReceiver.EXTRA_CATEGORY, task.category)
                putExtra(TaskAlarmReceiver.EXTRA_PRIORITY, task.priority)
                putExtra(TaskAlarmReceiver.EXTRA_NOTES, "သတ်မှတ်ချိန်အတွင်း ပြီးမြောက်ရန် သတိပေးချက်")
            }

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                flags
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        Log.i(TAG, "Scheduled exact alarm for task #${task.id} at $triggerAtMillis")
                    } else {
                        // Fallback if exact alarm permission was revoked by user
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        Log.w(TAG, "Exact alarm permission missing. Used setAndAllowWhileIdle fallback for task #${task.id}")
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    Log.i(TAG, "Scheduled exact alarm for task #${task.id} at $triggerAtMillis")
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                Log.i(TAG, "Scheduled exact alarm for task #${task.id} at $triggerAtMillis (Pre-M)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule AlarmManager exact alarm for task #${task.id}: ${e.message}", e)
        }
    }

    /**
     * Schedules a WorkManager job as secondary fallback.
     */
    private fun scheduleWorkManagerFallback(
        context: Context,
        task: Task,
        delayMillis: Long
    ) {
        val inputData = Data.Builder()
            .putLong(TaskReminderWorker.KEY_TASK_ID, task.id)
            .putString(TaskReminderWorker.KEY_TASK_TITLE, task.title)
            .putString(TaskReminderWorker.KEY_CATEGORY, task.category)
            .putString(TaskReminderWorker.KEY_NOTES, "သတ်မှတ်ချိန်အတွင်း ပြီးမြောက်ရန် သတိပေးချက်")
            .build()

        val reminderRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(maxOf(0L, delayMillis), TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("task_reminder")
            .addTag("task_${task.id}")
            .build()

        val uniqueWorkName = "$UNIQUE_WORK_PREFIX${task.id}"

        try {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                reminderRequest
            )
            Log.d(TAG, "Enqueued WorkManager fallback '$uniqueWorkName' with delay ${delayMillis}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager fallback: ${e.message}", e)
        }
    }

    /**
     * Cancel any scheduled reminder for a specific task across both AlarmManager and WorkManager.
     */
    fun cancelReminder(context: Context, taskId: Long) {
        // 1. Cancel AlarmManager pending intent
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = TaskAlarmReceiver.ACTION_TASK_ALARM
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                flags
            )
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled AlarmManager alarm for task #$taskId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel AlarmManager alarm for task #$taskId: ${e.message}")
        }

        // 2. Cancel WorkManager job
        try {
            val uniqueWorkName = "$UNIQUE_WORK_PREFIX$taskId"
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName)
            Log.i(TAG, "Cancelled WorkManager unique work '$uniqueWorkName'")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel WorkManager work for task $taskId: ${e.message}")
        }
    }
}
