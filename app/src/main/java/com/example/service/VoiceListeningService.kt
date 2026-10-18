package com.example.service

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
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceListeningService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null
  private var audioSensor: AdaptiveAudioSensor? = null
  private var voiceActivityDetector: AudioRecordVoiceActivityDetector? = null
  private val serviceJob = Job()
  private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
  private var explicitlyStoppedByUser = false

  companion object {
    const val ACTION_START_BACKGROUND_LISTENING = "com.example.action.START_BACKGROUND_LISTENING"
    const val ACTION_STOP_BACKGROUND_LISTENING = "com.example.action.STOP_BACKGROUND_LISTENING"
    const val ACTION_RESTART_VOICE_SERVICE = "com.example.action.RESTART_VOICE_SERVICE"
    private const val NOTIFICATION_CHANNEL_ID = "kyawgyi_background_voice_channel"
    private const val NOTIFICATION_ID = 2026

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _backgroundSpeechDetected = MutableStateFlow<String?>(null)
    val backgroundSpeechDetected: StateFlow<String?> = _backgroundSpeechDetected.asStateFlow()

    private val _ambientNoiseLevel = MutableStateFlow(20f)
    val ambientNoiseLevel: StateFlow<Float> = _ambientNoiseLevel.asStateFlow()

    fun startService(context: Context) {
      val intent = Intent(context, VoiceListeningService::class.java).apply {
        action = ACTION_START_BACKGROUND_LISTENING
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, VoiceListeningService::class.java).apply {
        action = ACTION_STOP_BACKGROUND_LISTENING
      }
      context.startService(intent)
    }

    fun clearDetectionEvent() {
      _backgroundSpeechDetected.value = null
    }
  }

  override fun onCreate() {
    super.onCreate()
    explicitlyStoppedByUser = false
    acquireWakeLock()
    createNotificationChannel()
    setupAudioSensor()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_BACKGROUND_LISTENING -> {
        explicitlyStoppedByUser = true
        stopForegroundListening()
        stopSelf()
        return START_NOT_STICKY
      }
      else -> {
        explicitlyStoppedByUser = false
        startForegroundListening()
      }
    }
    return START_STICKY
  }

  private fun startForegroundListening() {
    val notification = buildForegroundNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
      } else {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
      }
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    audioSensor?.startSensing()
    voiceActivityDetector?.startContinuousListening()
    _isServiceRunning.value = true
  }

  private fun stopForegroundListening() {
    audioSensor?.stopSensing()
    voiceActivityDetector?.stopContinuousListening()
    _isServiceRunning.value = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  private fun setupAudioSensor() {
    audioSensor = AdaptiveAudioSensor(this, serviceScope).apply {
      onVoiceActivityDetected = { rms ->
        // When speech activity is captured in background, emit event
        _backgroundSpeechDetected.value = "VOICE_TRIGGERED"
      }
      onAmbientNoiseUpdated = { ambient, _ ->
        _ambientNoiseLevel.value = ambient
      }
    }

    voiceActivityDetector = AudioRecordVoiceActivityDetector(this, serviceScope).apply {
      onVoiceActivityDetected = { keyword, confidence ->
        // Continuous voice-activity / energy detection triggered hands-free interaction
        _backgroundSpeechDetected.value = "VOICE_ACTIVITY_DETECTED: $keyword (Confidence: ${"%.2f".format(confidence)})"
      }
    }
  }

  private fun buildForegroundNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
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
      Intent(this, VoiceListeningService::class.java).apply {
        action = ACTION_STOP_BACKGROUND_LISTENING
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle("ကျော်ကြီး စကားသံအာရုံခံ နည်းစနစ် (Active)")
      .setContentText("နောက်ကွယ်မှ လိုက်ရောညီထွေ အသံဖမ်းယူစောင့်ဆိုင်းနေပါသည်...")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setOngoing(true)
      .setContentIntent(pendingIntent)
      .addAction(0, "ပိတ်ပါ (Stop)", stopIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "Kyaw Gyi Voice Service",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Background Voice Activity Detection for Myanmar Assistant"
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun acquireWakeLock() {
    try {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      if (wakeLock == null) {
        wakeLock = powerManager.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "KyawGyi::PerpetualBackgroundVoiceWakeLock"
        )
      }
      if (wakeLock?.isHeld == false) {
        wakeLock?.acquire() // Perpetual wake lock to guarantee continuous audio sensing
      }
    } catch (_: Exception) {}
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    if (!explicitlyStoppedByUser) {
      scheduleAutoRestart()
    }
    super.onTaskRemoved(rootIntent)
  }

  private fun scheduleAutoRestart() {
    try {
      val restartIntent = Intent(applicationContext, BootAndRestartReceiver::class.java).apply {
        action = ACTION_RESTART_VOICE_SERVICE
      }
      val pendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        7711,
        restartIntent,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
      )
      val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
      alarmManager.setExactAndAllowWhileIdle(
        android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
        android.os.SystemClock.elapsedRealtime() + 1000L,
        pendingIntent
      )
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    if (!explicitlyStoppedByUser) {
      scheduleAutoRestart()
    }
    stopForegroundListening()
    wakeLock?.let {
      if (it.isHeld) it.release()
    }
    serviceJob.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
