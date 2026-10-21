package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Unified Gateway Service for KyawGyi Voice Operations.
 * Directs all service operations to [com.kyawgyi.ir.service.VoiceForegroundService] to prevent
 * duplicate background services, conflicting audio capture, and foreground service crashes.
 */
class KyawGyiForegroundService : Service() {

    private val TAG = "KYAW_GYI_SERVICE"

    companion object {
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_START_LISTENING = "ACTION_START_LISTENING"
        const val ACTION_STOP_LISTENING = "ACTION_STOP_LISTENING"
        const val ACTION_SPEAK = "ACTION_SPEAK"
        const val EXTRA_SPEAK_TEXT = "EXTRA_SPEAK_TEXT"

        const val BROADCAST_ENGINE_EVENT = "com.example.ENGINE_EVENT"
        const val EXTRA_STATE = "EXTRA_STATE"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_SPEECH_RESULT = "EXTRA_SPEECH_RESULT"

        fun startService(context: Context) {
            com.kyawgyi.ir.service.VoiceForegroundService.startService(context)
        }

        fun stopService(context: Context) {
            com.kyawgyi.ir.service.VoiceForegroundService.stopService(context)
        }

        fun startListening(context: Context) {
            com.kyawgyi.ir.service.VoiceForegroundService.startListening(context)
        }

        fun stopListening(context: Context) {
            com.kyawgyi.ir.service.VoiceForegroundService.stopListening(context)
        }

        fun speak(context: Context, text: String) {
            com.kyawgyi.ir.service.VoiceForegroundService.speak(context, text)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KyawGyi Gateway Service created -> Routing to VoiceForegroundService.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        Log.i(TAG, "KyawGyiForegroundService action: $action -> forwarding to VoiceForegroundService")

        ensureForegroundIfNeeded()

        when (action) {
            ACTION_START_SERVICE -> {
                com.kyawgyi.ir.service.VoiceForegroundService.startService(this)
            }
            ACTION_START_LISTENING -> {
                com.kyawgyi.ir.service.VoiceForegroundService.startListening(this)
            }
            ACTION_STOP_LISTENING -> {
                com.kyawgyi.ir.service.VoiceForegroundService.stopListening(this)
            }
            ACTION_SPEAK -> {
                val textToSpeak = intent?.getStringExtra(EXTRA_SPEAK_TEXT) ?: ""
                if (textToSpeak.isNotEmpty()) {
                    com.kyawgyi.ir.service.VoiceForegroundService.speak(this, textToSpeak)
                }
            }
            ACTION_STOP_SERVICE -> {
                com.kyawgyi.ir.service.VoiceForegroundService.stopService(this)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channelId = "kyaw_gyi_gateway_channel"
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && nm.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Kyaw Gyi Service Gateway",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle("Kyaw Gyi Voice Service")
                    .setContentText("စနစ်စတင်နေပါသည်...")
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build()

                // Android 14 (API 34+) specifically requires FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(this, 9991, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(9991, notification)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ensureForegroundIfNeeded failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
