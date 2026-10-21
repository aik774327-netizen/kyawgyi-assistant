package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.utils.TaskReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot Receiver to restore and re-schedule all active task reminders
 * upon device boot, update, or quick boot power on.
 */
class TaskBootReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "TaskBootReminderReceiver triggered by action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val now = System.currentTimeMillis()
                    val pendingTasks = db.taskDao().getUpcomingPendingTasks(now)

                    Log.i(TAG, "Found ${pendingTasks.size} pending tasks to restore alarms for.")
                    for (task in pendingTasks) {
                        task.dueDateMillis?.let { dueMillis ->
                            if (dueMillis > now) {
                                TaskReminderScheduler.scheduleReminderAtTimestamp(
                                    context.applicationContext,
                                    task,
                                    dueMillis
                                )
                                Log.d(TAG, "Restored alarm for task '${task.title}' at $dueMillis")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring task reminders after reboot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "TaskBootReceiver"
    }
}
