package com.kyawgyi.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * VoiceForegroundService (Additional 12 — Item #12 Android 14+ Foreground Microphone Service Helper)
 *
 * Ensures compliance with Android 14+ (API 34+) background microphone capture restrictions by:
 * 1. Promoting the voice service with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`.
 * 2. Managing a time-bounded partial `WakeLock` (`maxTimeoutMs = 10 minutes`) so the CPU never
 *    stays awake indefinitely if an utterance session is abandoned.
 */
class VoiceForegroundService : Service() {

    companion object {
        private const val TAG = "VoiceForegroundService"
        const val CHANNEL_ID = "kyawgyi_voice_mic_channel"
        const val NOTIFICATION_ID = 12014
        const val DEFAULT_WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes safety cap

        /**
         * Promotes any active [Service] to a foreground microphone service safely on Android 10–14+.
         */
        fun promoteToForegroundMicService(service: Service, statusText: String = "ကျော်ကြီး အသံစနစ် နားထောင်နေပါသည်"): Boolean {
            val compatReport = runCatching {
                com.kyawgyi.diagnostics.DeviceCompatibilityEvaluator(service).evaluate()
            }.getOrNull()

            if (compatReport != null && !compatReport.foregroundMicAllowedInCurrentState) {
                Log.w(
                    TAG,
                    "Skipping foreground microphone promotion: not allowed in current state " +
                        "(hasMic=${compatReport.hasRecordAudioPermission}, interactive=${compatReport.isScreenInteractive}, " +
                        "accessibility=${compatReport.isAccessibilityEnabled})"
                )
                return false
            }

            return try {
                ensureNotificationChannel(service)
                val notification = buildVoiceNotification(service, statusText)
                val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
                ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, fgType)
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException promoting foreground microphone service: ${e.message}")
                false
            } catch (e: Throwable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"
                ) {
                    Log.w(TAG, "ForegroundServiceStartNotAllowedException on Android 12+/14+: ${e.message}")
                } else {
                    Log.w(TAG, "Unable to promote foreground microphone service: ${e.message}")
                }
                false
            }
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "KyawGyi Voice Microphone Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps KyawGyi Myanmar Voice Assistant active during hands-free listening"
                }
                nm.createNotificationChannel(channel)
            }
        }

        private fun buildVoiceNotification(context: Context, statusText: String): Notification {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("KyawGyi Voice AI")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundMicService(this)
        acquireBoundedWakeLock()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    fun acquireBoundedWakeLock(timeoutMs: Long = DEFAULT_WAKELOCK_TIMEOUT_MS) {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        runCatching {
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KyawGyiVoice::BoundedMicWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        }.onFailure { e ->
            Log.w(TAG, "WakeLock acquire skipped: ${e.message}")
        }
    }

    fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }
}
