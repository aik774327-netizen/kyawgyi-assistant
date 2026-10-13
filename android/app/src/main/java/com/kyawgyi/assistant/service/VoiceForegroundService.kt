package com.kyawgyi.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VoiceForegroundService — persistent foreground service for the voice assistant.
 *
 * Responsibilities:
 *  - Foreground service with microphone type (declared in manifest).
 *  - PARTIAL_WAKE_LOCK so recording survives screen-off / Doze.
 *  - Safe AudioRecord lifecycle: init -> start -> drain -> stop -> release.
 *
 * NOTE (honesty): RECORD_AUDIO is a runtime permission. A Service cannot show
 * the permission dialog, so startListening() fails closed when the permission
 * is not granted — the Flutter layer must request it first.
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class VoiceForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "kyawgyi_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.kyawgyi.assistant.action.START"
        const val ACTION_STOP = "com.kyawgyi.assistant.action.STOP"
        const val ACTION_START_LISTENING = "com.kyawgyi.assistant.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.kyawgyi.assistant.action.STOP_LISTENING"

        private const val WAKELOCK_TAG = "KyawGyi:VoiceWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 min, re-acquired per session
        private const val SAMPLE_RATE = 16000

        /** Set while the service process is alive — read by the MethodChannel. */
        @Volatile
        var isServiceRunning = false
            private set

        /**
         * FIX #11: listening state lives in the companion so the MethodChannel
         * can honestly report it (was instance-private — startStt claimed
         * success fire-and-forget). A Service is effectively single-instance.
         */
        @Volatile
        var isListening = false
            private set

        /**
         * FIX (mic/stt/tts audit #3): static PCM tap for the Flutter EventChannel
         * ("com.kyawgyi.assistant/audio_stream"). 16-bit mono PCM @ 16 kHz.
         * Null = no streaming. Set by AudioStreamHandler while a Dart listener
         * is attached; the drain loop invokes it per chunk.
         */
        @Volatile
        var pcmTap: ((bytes: ByteArray, length: Int) -> Unit)? = null
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioRecord: AudioRecord? = null
    private var drainJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Optional consumer for PCM chunks; null = drain-and-discard (keeps pipeline warm). */
    var audioChunkListener: ((ByteArray, Int) -> Unit)? = null

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // FIX #12: no wake lock while idle — acquired per listening session in
        // startListening(). Holding it for a foreground-only service wastes battery.
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground quickly after startForegroundService() (API 26+).
        val notification = buildNotification("Kyaw Gyi Voice Assistant is active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }

        when (intent?.action) {
            ACTION_STOP -> stopServiceSafely()
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            // ACTION_START / null -> foreground only, no mic yet
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the service alive when the task is swiped away (START_STICKY re-creates).
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopListening()
        releaseWakeLock()
        serviceScope.cancel()
        isServiceRunning = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ wake lock

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also { wl ->
            try {
                // Bounded acquire: safer than indefinite if release is ever missed.
                wl.acquire(WAKELOCK_TIMEOUT_MS)
            } catch (e: Exception) {
                // ignore — service still runs, just without wake guarantees
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            // ignore — already released
        } finally {
            wakeLock = null
        }
    }

    // ------------------------------------------------------------------ audio lifecycle

    /**
     * Starts microphone capture. Fails closed (returns false) when:
     *  - RECORD_AUDIO not granted, or
     *  - the device cannot provide a recorder.
     */
    fun startListening(): Boolean {
        if (isListening) return true
        val recorder = createAudioRecord() ?: return false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try {
                recorder.release()
            } catch (e: Exception) {
                // ignore
            }
            return false
        }
        return try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
                recorder.release()
                return false
            }
            audioRecord = recorder
            isListening = true
            startDrainLoop(recorder)
            // Refresh the wake lock for this listening session.
            releaseWakeLock()
            acquireWakeLock()
            true
        } catch (e: Exception) {
            try {
                recorder.release()
            } catch (ignored: Exception) {
            }
            false
        }
    }

    fun stopListening() {
        if (!isListening) {
            releaseRecorder()
            return
        }
        isListening = false
        drainJob?.cancel()
        drainJob = null
        releaseRecorder()
    }

    private fun createAudioRecord(): AudioRecord? {
        // A Service cannot request runtime permissions — fail closed instead.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
        if (minBuf <= 0) return null
        return try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                channelConfig,
                audioFormat,
                minBuf * 2
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun startDrainLoop(recorder: AudioRecord) {
        drainJob?.cancel()
        drainJob = serviceScope.launch {
            val buffer = ByteArray(4096)
            // FIX #11: a read error must fail closed — previously the loop
            // exited but isListening stayed true and the recorder leaked.
            var readError = false
            try {
                while (isActive && isListening) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // FIX #8: read the listener per-iteration (was captured once at
                        // drain start, so a listener set later was silently ignored).
                        // Copy before handing off — the buffer is reused.
                        val copy = buffer.copyOf(read)
                        audioChunkListener?.invoke(copy, read)
                        // FIX (mic/stt/tts audit #3): static tap for the Flutter EventChannel.
                        pcmTap?.invoke(copy, read)
                    } else if (read < 0) {
                        readError = true
                        break // ERROR_INVALID_OPERATION etc. — fail closed below
                    }
                }
            } catch (e: Exception) {
                readError = true
            } finally {
                if (readError) {
                    isListening = false
                }
                if (!isListening) releaseRecorder()
            }
        }
    }

    private fun releaseRecorder() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder == null) return
        try {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (e: Exception) {
            // ignore — may already be stopped
        }
        try {
            recorder.release()
        } catch (e: Exception) {
            // ignore
        }
    }

    // ------------------------------------------------------------------ shutdown

    private fun stopServiceSafely() {
        stopListening()
        releaseWakeLock()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            // ignore
        }
        stopSelf()
    }

    // ------------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kyaw Gyi Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Kyaw Gyi voice assistant running in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kyaw Gyi Assistant")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
