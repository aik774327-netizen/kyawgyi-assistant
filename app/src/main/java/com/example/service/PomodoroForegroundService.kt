package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.FocusCategory
import com.example.data.FocusSessionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Foreground Service for Pomodoro Focus Timer with Advanced Screen State Awareness
 * & Battery Optimization Protection.
 * 
 * Ensures that the focus countdown remains 100% accurate, uninterrupted,
 * and active even when the device enters deep sleep mode (Doze mode) or the screen turns off.
 *
 * Optimizations:
 * - Dynamic PowerManager PARTIAL_WAKE_LOCK (scoped to session duration with safety buffer)
 * - Wall-clock elapsedRealtime timestamp calculation for drift-free accuracy
 * - Screen State Receiver: Throttles notification updates during screen-off to save battery
 *   and prevent notification flood/system UI wakeups.
 * - AlarmManager setExactAndAllowWhileIdle backup to guarantee finish alarm even under deep Doze mode
 * - Persistent Foreground Notification with real-time controls (+5 min, Pause/Resume, Stop & Save)
 * - Automatic Room Database logging upon session completion
 * - Battery optimization whitelist helpers
 */
class PomodoroForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var countdownJob: Job? = null

    private var sessionName: String = "Focus Session"
    private var categoryName: String = "Work"
    private var categoryColorHex: String = "#3B82F6"
    private var totalSeconds: Int = 25 * 60
    private var remainingSeconds: Int = 25 * 60
    private var isPaused: Boolean = false
    private var targetEndTimeMs: Long = 0L

    // Screen State Awareness for Battery Preservation
    private var isScreenOn: Boolean = true
    private var isScreenReceiverRegistered: Boolean = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d(TAG, "Screen OFF: Throttling notification updates to prevent CPU wakeups during deep sleep.")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen ON: Immediately refreshing notification with current elapsed countdown.")
                    // Immediately re-synchronize remaining seconds using wall-clock elapsedRealtime
                    if (targetEndTimeMs > 0 && !isPaused) {
                        val now = SystemClock.elapsedRealtime()
                        val millisRemaining = targetEndTimeMs - now
                        remainingSeconds = (millisRemaining / 1000L).toInt().coerceAtLeast(0)
                    }
                    updateNotification()
                    updateServiceState(isRunning = _serviceState.value.isRunning, isPaused = isPaused)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PomodoroForeground"
        const val CHANNEL_ID = "pomodoro_foreground_service_channel"
        const val NOTIFICATION_ID = 9022
        private const val ALARM_REQ_CODE = 9025

        const val ACTION_START = "com.example.service.ACTION_START_POMODORO"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE_POMODORO"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME_POMODORO"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_POMODORO"
        const val ACTION_ADD_TIME = "com.example.service.ACTION_ADD_TIME_POMODORO"
        const val ACTION_ALARM_FINISH = "com.example.service.ACTION_POMODORO_ALARM_FINISH"

        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_CATEGORY_NAME = "extra_category_name"
        const val EXTRA_COLOR_HEX = "extra_color_hex"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
        const val EXTRA_ADD_SECONDS = "extra_add_seconds"

        // Reactive Service State for UI Observation
        data class ServiceState(
            val isRunning: Boolean = false,
            val isPaused: Boolean = false,
            val remainingSeconds: Int = 0,
            val totalSeconds: Int = 0,
            val sessionName: String = "",
            val category: String = "Work",
            val colorHex: String = "#3B82F6"
        ) {
            val progress: Float
                get() = if (totalSeconds > 0) (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat() else 0f
        }

        private val _serviceState = MutableStateFlow(ServiceState())
        val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

        fun startTimer(
            context: Context,
            sessionName: String,
            totalSeconds: Int,
            categoryName: String,
            colorHex: String,
            remainingSeconds: Int = totalSeconds
        ) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_NAME, sessionName)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                putExtra(EXTRA_REMAINING_SECONDS, remainingSeconds)
                putExtra(EXTRA_CATEGORY_NAME, categoryName)
                putExtra(EXTRA_COLOR_HEX, colorHex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeTimer(context: Context) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun addTime(context: Context, secondsToAdd: Int = 300) {
            val intent = Intent(context, PomodoroForegroundService::class.java).apply {
                action = ACTION_ADD_TIME
                putExtra(EXTRA_ADD_SECONDS, secondsToAdd)
            }
            context.startService(intent)
        }

        /**
         * Checks whether the application is exempt from battery optimizations.
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        }

        /**
         * Requests exemption from battery optimizations to guarantee uninterrupted background execution.
         */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: ${e.message}")
                    try {
                        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(fallback)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Fallback battery settings error: ${ex.message}")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
        Log.i(TAG, "PomodoroForegroundService created with Screen State Awareness.")
    }

    private fun registerScreenStateReceiver() {
        if (!isScreenReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                registerReceiver(screenStateReceiver, filter)
                isScreenReceiverRegistered = true
                Log.d(TAG, "ScreenStateReceiver registered.")
            } catch (e: Exception) {
                Log.w(TAG, "Error registering screen state receiver: ${e.message}")
            }
        }
    }

    private fun unregisterScreenStateReceiver() {
        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
                isScreenReceiverRegistered = false
                Log.d(TAG, "ScreenStateReceiver unregistered.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen state receiver: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                sessionName = intent?.getStringExtra(EXTRA_SESSION_NAME) ?: "Focus Session"
                categoryName = intent?.getStringExtra(EXTRA_CATEGORY_NAME) ?: "Work"
                categoryColorHex = intent?.getStringExtra(EXTRA_COLOR_HEX) ?: "#3B82F6"
                totalSeconds = intent?.getIntExtra(EXTRA_TOTAL_SECONDS, 25 * 60) ?: (25 * 60)
                remainingSeconds = intent?.getIntExtra(EXTRA_REMAINING_SECONDS, totalSeconds) ?: totalSeconds
                isPaused = false

                startForegroundWithNotification()
                startCountdown()
            }
            ACTION_PAUSE -> {
                pauseCountdown()
            }
            ACTION_RESUME -> {
                resumeCountdown()
            }
            ACTION_ADD_TIME -> {
                val addSecs = intent?.getIntExtra(EXTRA_ADD_SECONDS, 300) ?: 300
                addTimeToCountdown(addSecs)
            }
            ACTION_STOP -> {
                stopAndSaveSession(isCompleted = false)
            }
            ACTION_ALARM_FINISH -> {
                Log.i(TAG, "Received ACTION_ALARM_FINISH from AlarmManager (Doze mode wakeup).")
                if (_serviceState.value.isRunning && !_serviceState.value.isPaused) {
                    onTimerFinished()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock(durationSeconds: Int = remainingSeconds) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "KyawGyi:PomodoroDeepSleepWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Dynamically scale timeout to remaining seconds + 60s safety buffer, min 60s, max 4 hours
                val timeoutMillis = ((durationSeconds + 60L) * 1000L).coerceIn(60_000L, 4 * 3600_000L)
                wakeLock?.acquire(timeoutMillis)
                Log.d(TAG, "Partial WakeLock acquired (timeout: ${timeoutMillis / 1000}s) for deep sleep protection.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Partial WakeLock released.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock: ${e.message}")
        }
    }

    private fun scheduleExactAlarmBackup(triggerAtMillis: Long) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, PomodoroForegroundService::class.java).apply {
                action = ACTION_ALARM_FINISH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getService(this, ALARM_REQ_CODE, intent, flags)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Exact AlarmManager backup scheduled for Doze mode at $triggerAtMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm backup: ${e.message}")
        }
    }

    private fun cancelExactAlarmBackup() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, PomodoroForegroundService::class.java).apply {
                action = ACTION_ALARM_FINISH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getService(this, ALARM_REQ_CODE, intent, flags)
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Exact AlarmManager backup cancelled.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel exact alarm backup: ${e.message}")
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        val nowElapsed = SystemClock.elapsedRealtime()
        targetEndTimeMs = nowElapsed + (remainingSeconds * 1000L)
        
        // Schedule exact hardware alarm backup for Doze mode
        val finishWallClockTime = System.currentTimeMillis() + (remainingSeconds * 1000L)
        scheduleExactAlarmBackup(finishWallClockTime)

        acquireWakeLock(remainingSeconds)
        updateServiceState(isRunning = true, isPaused = false)

        countdownJob = serviceScope.launch {
            var lastNotificationSecs = remainingSeconds
            while (isActive && remainingSeconds > 0 && !isPaused) {
                val now = SystemClock.elapsedRealtime()
                val millisRemaining = targetEndTimeMs - now
                val currentSecs = (millisRemaining / 1000L).toInt().coerceAtLeast(0)

                remainingSeconds = currentSecs

                // Screen & Battery Optimization:
                // If screen is ON, update notification and UI state every second.
                // If screen is OFF, only refresh notification every 30 seconds to prevent
                // excessive CPU wakeups and Android OS notification throttling.
                if (isScreenOn) {
                    updateNotification()
                    lastNotificationSecs = currentSecs
                } else if (lastNotificationSecs - currentSecs >= 30) {
                    lastNotificationSecs = currentSecs
                    updateNotification()
                }

                updateServiceState(isRunning = true, isPaused = false)

                if (remainingSeconds <= 0) {
                    onTimerFinished()
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun pauseCountdown() {
        isPaused = true
        countdownJob?.cancel()
        cancelExactAlarmBackup()
        releaseWakeLock()
        updateNotification()
        updateServiceState(isRunning = true, isPaused = true)
    }

    private fun resumeCountdown() {
        if (remainingSeconds > 0) {
            isPaused = false
            startCountdown()
        }
    }

    private fun addTimeToCountdown(additionalSeconds: Int) {
        remainingSeconds += additionalSeconds
        totalSeconds += additionalSeconds
        targetEndTimeMs += (additionalSeconds * 1000L)

        // Reschedule exact alarm backup
        val finishWallClockTime = System.currentTimeMillis() + (remainingSeconds * 1000L)
        scheduleExactAlarmBackup(finishWallClockTime)

        // Reacquire wakelock with expanded duration
        acquireWakeLock(remainingSeconds)

        updateNotification()
        updateServiceState(isRunning = true, isPaused = isPaused)
    }

    private fun onTimerFinished() {
        countdownJob?.cancel()
        cancelExactAlarmBackup()
        stopAndSaveSession(isCompleted = true)
    }

    private fun stopAndSaveSession(isCompleted: Boolean) {
        countdownJob?.cancel()
        cancelExactAlarmBackup()
        releaseWakeLock()

        val elapsedSecs = (totalSeconds - remainingSeconds).coerceAtLeast(0)
        val elapsedMins = maxOf(1, if (isCompleted) totalSeconds / 60 else (elapsedSecs + 30) / 60)

        // Save session record to Room DB
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val entity = FocusSessionEntity(
                    sessionName = sessionName.ifBlank { "$categoryName Focus Session" },
                    durationMinutes = elapsedMins,
                    category = categoryName,
                    colorHex = categoryColorHex,
                    timestamp = System.currentTimeMillis(),
                    notes = if (isCompleted) "Completed $categoryName Focus ($elapsedMins min) [Foreground Service]"
                            else "Stopped $categoryName Focus ($elapsedMins min) [Foreground Service]"
                )
                db.focusSessionDao().insertFocusSession(entity)
                Log.i(TAG, "Saved session record to Room DB: $entity")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving focus session to Room DB", e)
            }

            if (isCompleted) {
                showFinishedNotification(elapsedMins)
            }

            updateServiceState(isRunning = false, isPaused = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateServiceState(isRunning: Boolean, isPaused: Boolean) {
        _serviceState.value = ServiceState(
            isRunning = isRunning,
            isPaused = isPaused,
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            sessionName = sessionName,
            category = categoryName,
            colorHex = categoryColorHex
        )
    }

    // -------------------------------------------------------------------------
    // NOTIFICATION MANAGEMENT
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pomodoro Deep-Sleep Active Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Pomodoro focus timer accurate during deep sleep with real-time controls."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildTimerNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        try {
            val notification = buildTimerNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed: ${e.message}")
        }
    }

    private fun buildTimerNotification(): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        val elapsedSecs = (totalSeconds - remainingSeconds).coerceAtLeast(0)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "focus_timer")
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            101,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Pause / Resume Intent
        val toggleActionIntent = Intent(this, PomodoroForegroundService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            102,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Add 5 Minutes Intent
        val addTimeActionIntent = Intent(this, PomodoroForegroundService::class.java).apply {
            action = ACTION_ADD_TIME
            putExtra(EXTRA_ADD_SECONDS, 300)
        }
        val addTimePendingIntent = PendingIntent.getService(
            this,
            105,
            addTimeActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Stop & Save Intent
        val stopActionIntent = Intent(this, PomodoroForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            103,
            stopActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val statusText = if (isPaused) "⏸️ Paused" else "⚡ Active (Deep Sleep & Battery Protected)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ $formattedTime remaining • $categoryName")
            .setContentText("$sessionName ($statusText)")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalSeconds, elapsedSecs, false)
            .setContentIntent(mainPendingIntent)
            .setColor(try { AndroidColor.parseColor(categoryColorHex) } catch (_: Exception) { AndroidColor.parseColor("#3B82F6") })
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_input_add,
                "+5 Mins",
                addTimePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_save,
                "Stop & Save",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showFinishedNotification(durationMins: Int) {
        val finishChannelId = "pomodoro_timer_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                finishChannelId,
                "Pomodoro Focus Timer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a Pomodoro focus or break session finishes"
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "focus_timer")
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            104,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, finishChannelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🎉 Focus Session Completed! [$categoryName]")
            .setContentText("Great job! \"$sessionName\" ($durationMins min) has completed and saved to Room DB.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .build()

        manager.notify(9023, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        cancelExactAlarmBackup()
        releaseWakeLock()
        unregisterScreenStateReceiver()
        serviceScope.cancel()
        updateServiceState(isRunning = false, isPaused = false)
        Log.i(TAG, "PomodoroForegroundService destroyed")
    }
}
