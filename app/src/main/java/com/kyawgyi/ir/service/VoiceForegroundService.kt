package com.kyawgyi.ir.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.voice.core.CentralAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * VoiceForegroundService in com.kyawgyi.ir.service.
 * REFACTORED TO CENTRAL AUDIO ARCHITECTURE:
 * Consumes from [CentralAudioRecorder] to prevent microphone hardware conflicts.
 */
class VoiceForegroundService : Service() {

  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
  private val centralRecorder by lazy { CentralAudioRecorder.getInstance(this) }

  private var wakeLock: PowerManager.WakeLock? = null
  private var isMicrophoneActive = false

  companion object {
    const val CHANNEL_ID = "kyawgyi_ir_voice_service_channel"
    const val CHANNEL_NAME = "Kyaw Gyi Voice Command Service"
    const val NOTIFICATION_ID = 9988

    const val ACTION_START_LISTENING = "com.kyawgyi.ir.service.action.START_LISTENING"
    const val ACTION_STOP_LISTENING = "com.kyawgyi.ir.service.action.STOP_LISTENING"
    const val ACTION_VOICE_ACTIVITY = "com.kyawgyi.ir.service.action.VOICE_ACTIVITY"
    const val EXTRA_AUDIO_LEVEL = "extra_audio_level"

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _liveAudioDb = MutableStateFlow(0f)
    val liveAudioDb: StateFlow<Float> = _liveAudioDb.asStateFlow()

    fun startService(context: Context): Boolean {
      return try {
        val intent = Intent(context, VoiceForegroundService::class.java).apply {
          action = ACTION_START_LISTENING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
        true
      } catch (_: Exception) {
        false
      }
    }

    fun stopService(context: Context): Boolean {
      return try {
        val intent = Intent(context, VoiceForegroundService::class.java).apply {
          action = ACTION_STOP_LISTENING
        }
        context.startService(intent)
        true
      } catch (_: Exception) {
        false
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    acquireWakeLock()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_LISTENING -> {
        stopMicrophoneLoop()
        stopSelf()
        return START_NOT_STICKY
      }
      else -> {
        startForegroundServiceInternal()
      }
    }
    return START_STICKY
  }

  private fun startForegroundServiceInternal() {
    val notification = buildPersistentNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    _isServiceRunning.value = true
    startMicrophoneLoop()
  }

  private fun stopMicrophoneLoop() {
    isMicrophoneActive = false
    _isServiceRunning.value = false
    serviceScope.launch {
      centralRecorder.stop()
    }

    wakeLock?.let {
      if (it.isHeld) it.release()
    }
    stopForeground(STOP_FOREGROUND_REMOVE)
  }

  private fun startMicrophoneLoop() {
    if (isMicrophoneActive) return
    isMicrophoneActive = true

    serviceScope.launch {
      val started = centralRecorder.start(serviceScope)
      if (!started) {
        isMicrophoneActive = false
        return@launch
      }

      var consecutiveLoudFrames = 0

      centralRecorder.audioFlow.collect { chunk ->
        if (!isMicrophoneActive) return@collect

        val sampleCount = chunk.size / 2
        var sumSquares = 0.0
        for (i in 0 until chunk.size - 1 step 2) {
          val s = ((chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)).toShort()
          val amplified = (s * 2.0f).coerceIn(-32768f, 32767f).toDouble()
          sumSquares += amplified * amplified
        }

        if (sampleCount > 0) {
          val rms = sqrt(sumSquares / sampleCount).toFloat()
          val db = if (rms > 1f) (20 * kotlin.math.log10(rms.toDouble())).toFloat() else 0f
          _liveAudioDb.value = db

          if (db > 48f) {
            consecutiveLoudFrames++
            if (consecutiveLoudFrames >= 3) {
              consecutiveLoudFrames = 0
              val broadcast = Intent(ACTION_VOICE_ACTIVITY).apply {
                putExtra(EXTRA_AUDIO_LEVEL, db)
                setPackage(packageName)
              }
              sendBroadcast(broadcast)
            }
          } else {
            consecutiveLoudFrames = 0
          }
        }
      }
    }
  }

  private fun buildPersistentNotification(): Notification {
    val openIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val stopIntent = PendingIntent.getService(
      this,
      1,
      Intent(this, VoiceForegroundService::class.java).apply {
        action = ACTION_STOP_LISTENING
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("ကျော်ကြီး အသံအာရုံခံ နည်းစနစ် (Active)")
      .setContentText("ဗဟို အသံစနစ်မှ မြန်မာအသံအမိန့်များကို အသင့်စောင့်ကြိုနေပါသည်")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(android.R.drawable.ic_delete, "ပိတ်ပါ", stopIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "ဗဟို မိုက်ခရိုဖုန်း အသံအမိန့်များ နောက်ကွယ်မှ နားထောင်စနစ်"
        setShowBadge(false)
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KyawGyiIr:VoiceForegroundLock").apply {
      setReferenceCounted(false)
      acquire(10 * 60 * 1000L)
    }
  }

  override fun onDestroy() {
    stopMicrophoneLoop()
    serviceJob.cancel()
    super.onDestroy()
  }
}
