package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * KYAW GYI AI — Microphone Lifecycle Controller (Stage 19 Production Hardened)
 *
 * Enforces the Single Source of Truth AudioRecord capture rules:
 * 1. Exactly ONE AudioRecord instance throughout the active lifecycle.
 * 2. Exactly ONE active coroutine capture Job.
 * 3. Thread-safe Mutex-guarded state transitions.
 * 4. Deterministic cleanup: Stop -> Release -> Nullify reference.
 * 5. Uses standard MediaRecorder.AudioSource.MIC to avoid OEM voice-call rerouting or media ducking.
 * 6. Debounced rapid ON/OFF handling: Resilient against 50+ rapid toggle cycles.
 */
class MicrophoneLifecycleController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MicrophoneController"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val DEBOUNCE_INTERVAL_MS = 200L

        @Volatile
        private var instance: MicrophoneLifecycleController? = null

        fun getInstance(context: Context): MicrophoneLifecycleController {
            return instance ?: synchronized(this) {
                instance ?: MicrophoneLifecycleController(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class CaptureState {
        IDLE,
        INITIALIZING,
        RECORDING,
        STOPPING,
        ERROR
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private var activeAudioRecord: AudioRecord? = null
    private var activeJob: Job? = null

    private val isCapturing = AtomicBoolean(false)
    private val activeInstanceCount = AtomicInteger(0)
    private var lastToggleTimestamp = 0L

    private val audioPolicyManager = KyawGyiAudioPolicyManager.getInstance(context)

    /**
     * Start deterministic PCM capture loop.
     * @param onPcmChunk Callback receiving raw 16-bit PCM short array chunks for DSP / Wake-word / VAD.
     */
    @SuppressLint("MissingPermission")
    suspend fun startCapture(
        consumerTag: String = "WakeWordEngine",
        onPcmChunk: suspend (ShortArray, Int) -> Unit
    ): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now - lastToggleTimestamp < DEBOUNCE_INTERVAL_MS && isCapturing.get()) {
            Log.d(TAG, "Capture start request debounced ($consumerTag). Already active.")
            return true
        }
        lastToggleTimestamp = now

        if (isCapturing.get() && activeAudioRecord != null && _captureState.value == CaptureState.RECORDING) {
            Log.d(TAG, "AudioRecord already recording. Reusing active stream for $consumerTag.")
            return true
        }

        // Clean up previous stale capture if any
        stopAndReleaseInternal("PRE_START_CLEANUP")

        _captureState.value = CaptureState.INITIALIZING

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing. Cannot initialize AudioRecord.")
            _captureState.value = CaptureState.ERROR
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

        var record: AudioRecord? = null
        // Primary source: standard MIC (avoids triggering OEM call routing or music pausing)
        val candidateSources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )

        for (source in candidateSources) {
            try {
                val candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    Log.i(TAG, "AudioRecord initialized successfully with source: $source")
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed source $source initialization: ${e.message}")
            }
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord with any candidate source.")
            _captureState.value = CaptureState.ERROR
            return false
        }

        activeAudioRecord = record
        activeInstanceCount.set(1)
        isCapturing.set(true)
        _captureState.value = CaptureState.RECORDING

        // Notify Audio Policy of passive capture
        audioPolicyManager.onMicrophoneCaptureStart(consumerTag)
        AudioDiagnostics.logMicState("STARTED", activeInstanceCount.get(), bufferSize)

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startRecording: ${e.message}")
            stopAndReleaseInternal("START_RECORDING_FAILURE")
            return false
        }

        activeJob = scope.launch {
            val pcmBuffer = ShortArray(bufferSize / 2)
            try {
                while (isActive && isCapturing.get() && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount > 0) {
                        onPcmChunk(pcmBuffer, readCount)
                    } else if (readCount < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $readCount")
                        break
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Capture coroutine cancelled normally.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in capture loop: ${e.message}")
            } finally {
                Log.d(TAG, "Capture read loop exited.")
            }
        }

        return true
    }

    /**
     * Stop and release the active AudioRecord deterministically.
     */
    suspend fun stopCapture(reason: String = "USER_REQUEST") = mutex.withLock {
        stopAndReleaseInternal(reason)
    }

    private fun stopAndReleaseInternal(reason: String) {
        _captureState.value = CaptureState.STOPPING
        isCapturing.set(false)

        activeJob?.cancel()
        activeJob = null

        activeAudioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping/releasing AudioRecord: ${e.message}")
            }
        }
        activeAudioRecord = null
        activeInstanceCount.set(0)
        _captureState.value = CaptureState.IDLE

        audioPolicyManager.onMicrophoneCaptureStop(reason)
        AudioDiagnostics.logMicState("STOPPED ($reason)", activeInstanceCount.get())
    }

    fun isRecordingActive(): Boolean = isCapturing.get()

    fun getActiveInstanceCount(): Int = activeInstanceCount.get()
}
