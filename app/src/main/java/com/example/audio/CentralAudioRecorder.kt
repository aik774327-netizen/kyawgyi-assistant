package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * KYAW GYI AI — Centralized Audio Producer
 *
 * Enforces the Single Source of Truth AudioRecord capture rules:
 * 1. Thread-safe Singleton managing the SOLE AudioRecord hardware instance on the device.
 * 2. Captures 16kHz, 16-bit Mono PCM raw stream.
 * 3. Dispatches audio packets across coroutines via a shared hot `SharedFlow<ByteArray>`.
 * 4. Multi-consumer support: Both Online (Gemini) and Offline (Sherpa-ONNX) can safely consume simultaneously or sequentially.
 * 5. Deterministic hardware lifecycle management: Clean Stop -> Release -> Nullify to avoid device microphone locks.
 */
class CentralAudioRecorder private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CentralAudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SIZE_BYTES = 1280 // 40ms of 16kHz 16-bit Mono (640 samples * 2 bytes)

        @Volatile
        private var instance: CentralAudioRecorder? = null

        fun getInstance(context: Context): CentralAudioRecorder {
            return instance ?: synchronized(this) {
                instance ?: CentralAudioRecorder(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting() {
            instance?.stopAndReleaseInternal("RESET_FOR_TESTING")
            instance = null
        }
    }

    enum class RecorderState {
        IDLE,
        INITIALIZING,
        RECORDING,
        PAUSED,
        STOPPING,
        ERROR
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _recorderState = MutableStateFlow(RecorderState.IDLE)
    val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

    // Hot SharedFlow with buffer capacity for multiple subscribers without backpressure blocking
    private val _audioSharedFlow = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioSharedFlow: SharedFlow<ByteArray> = _audioSharedFlow.asSharedFlow()

    private var activeAudioRecord: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isTransientlyPaused = AtomicBoolean(false)
    private val activeConsumerCount = AtomicInteger(0)

    /**
     * Registers a consumer and starts audio recording if not already active.
     */
    suspend fun startRecording(consumerId: String = "GenericConsumer"): Boolean = mutex.withLock {
        val currentCount = activeConsumerCount.incrementAndGet()
        Log.i(TAG, "startRecording requested by [$consumerId]. Total active consumers: $currentCount")

        if (isRecording.get() && activeAudioRecord != null && _recorderState.value == RecorderState.RECORDING) {
            Log.d(TAG, "AudioRecord already capturing. Reusing central PCM stream for $consumerId.")
            return true
        }

        // Clean up any stale state before spinning up new hardware instance
        stopAndReleaseInternal("PRE_START_CLEANUP")

        return startRecordingInternal(consumerId)
    }

    private fun startRecordingInternal(consumerId: String): Boolean {
        _recorderState.value = RecorderState.INITIALIZING

        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing. Cannot initialize CentralAudioRecorder.")
            _recorderState.value = RecorderState.ERROR
            activeConsumerCount.decrementAndGet()
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = if (minBufferSize > 0) maxOf(minBufferSize * 2, CHUNK_SIZE_BYTES * 4) else 4096

        var record: AudioRecord? = null
        // Fallback candidate sources: MediaRecorder.AudioSource.MIC is preferred to prevent call routing
        val candidateSources = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )

        for (source in candidateSources) {
            try {
                @SuppressLint("MissingPermission")
                val candidate = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    Log.i(TAG, "AudioRecord initialized successfully with audio source: $source")
                    break
                } else {
                    candidate.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Candidate source $source failed initialization: ${e.message}")
            }
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord with candidate sources.")
            _recorderState.value = RecorderState.ERROR
            activeConsumerCount.decrementAndGet()
            return false
        }

        activeAudioRecord = record

        // Attach Hardware Acoustic Echo Cancellation (AEC) and Noise Suppression (NS)
        attachAudioEffects(record.audioSessionId)

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord startRecording failed (recordingState != RECORDING).")
                stopAndReleaseInternal("START_RECORDING_FAILED")
                _recorderState.value = RecorderState.ERROR
                activeConsumerCount.decrementAndGet()
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord: ${e.message}", e)
            stopAndReleaseInternal("START_EXCEPTION")
            _recorderState.value = RecorderState.ERROR
            activeConsumerCount.decrementAndGet()
            return false
        }

        isRecording.set(true)
        isTransientlyPaused.set(false)
        _recorderState.value = RecorderState.RECORDING

        // Continuous streaming coroutine loop
        captureJob = scope.launch {
            val audioBuffer = ByteArray(CHUNK_SIZE_BYTES)
            Log.i(TAG, "Central PCM 16kHz capture loop started for [$consumerId].")

            try {
                while (isActive && isRecording.get() && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    if (isTransientlyPaused.get()) {
                        // In transient pause state (e.g. during incoming phone call or temporary audio loss)
                        kotlinx.coroutines.delay(50)
                        continue
                    }

                    val bytesRead = record.read(audioBuffer, 0, audioBuffer.size)
                    if (bytesRead > 0) {
                        if (!isTransientlyPaused.get()) {
                            val chunkCopy = audioBuffer.copyOf(bytesRead)
                            _audioSharedFlow.emit(chunkCopy)
                        }
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $bytesRead")
                        if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in central audio capture loop: ${e.message}", e)
            } finally {
                Log.i(TAG, "Central PCM capture loop exited.")
            }
        }

        return true
    }

    /**
     * Pauses the PCM stream transiently upon AUDIOFOCUS_LOSS_TRANSIENT or telephony interruption.
     * Prevents corrupt PCM packets from polluting downstream VAD and WakeWord detectors.
     */
    suspend fun pauseRecordingTransiently(reason: String = "AUDIOFOCUS_LOSS_TRANSIENT") = mutex.withLock {
        if (!isRecording.get()) {
            Log.d(TAG, "pauseRecordingTransiently requested ($reason) but recording is not active.")
            return@withLock
        }
        if (isTransientlyPaused.compareAndSet(false, true)) {
            Log.i(TAG, "CentralAudioRecorder transiently PAUSED ($reason). PCM stream paused.")
            _recorderState.value = RecorderState.PAUSED
        }
    }

    /**
     * Resumes the PCM stream and re-synchronizes the audio capture pipeline when AUDIOFOCUS_GAIN is received.
     */
    suspend fun resumeRecordingTransiently(reason: String = "AUDIOFOCUS_GAIN"): Boolean = mutex.withLock {
        if (!isRecording.get()) {
            Log.d(TAG, "resumeRecordingTransiently requested ($reason) but recording is not active.")
            return@withLock false
        }

        Log.i(TAG, "CentralAudioRecorder resuming and re-syncing pipeline: reason=$reason")
        val record = activeAudioRecord

        // Validate hardware AudioRecord state:
        if (record == null || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.w(TAG, "Underlying AudioRecord disrupted during transient pause. Rebuilding hardware pipeline...")
            stopAndReleaseInternal("TRANSIENT_RESYNC_RESTART")
            return@withLock startRecordingInternal("PipelineResync-$reason")
        }

        isTransientlyPaused.set(false)
        _recorderState.value = RecorderState.RECORDING
        Log.i(TAG, "CentralAudioRecorder pipeline successfully re-synced and resumed.")
        return@withLock true
    }

    fun isPaused(): Boolean = isTransientlyPaused.get()

    /**
     * Decrements consumer count. Releases hardware microphone when all consumers finish or if forceRelease is true.
     */
    suspend fun stopRecording(consumerId: String = "GenericConsumer", forceRelease: Boolean = false): Unit = mutex.withLock {
        val remaining = if (forceRelease) 0 else maxOf(0, activeConsumerCount.decrementAndGet())
        if (forceRelease) {
            activeConsumerCount.set(0)
        }
        Log.i(TAG, "stopRecording by [$consumerId]. Remaining consumers: $remaining (force=$forceRelease)")

        if (remaining == 0 && isRecording.get()) {
            stopAndReleaseInternal("ALL_CONSUMERS_RELEASED")
        }
    }

    private fun stopAndReleaseInternal(reason: String) {
        Log.i(TAG, "stopAndReleaseInternal invoked: reason=$reason")
        isRecording.set(false)
        _recorderState.value = RecorderState.STOPPING

        captureJob?.cancel()
        captureJob = null

        activeAudioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
                Log.i(TAG, "AudioRecord successfully stopped and released ($reason).")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping/releasing AudioRecord: ${e.message}", e)
            }
        }
        releaseAudioEffects()
        activeAudioRecord = null
        _recorderState.value = RecorderState.IDLE
    }

    /**
     * Attaches Hardware Acoustic Echo Canceler (AEC) and Noise Suppressor (NS) to the AudioRecord session.
     * Prevents TTS self-feedback loop and cancels background ambient acoustic noise at the hardware HAL level.
     */
    private fun attachAudioEffects(audioSessionId: Int) {
        // 1. Acoustic Echo Cancellation (AEC)
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(audioSessionId)
                if (aec != null) {
                    val status = aec.setEnabled(true)
                    if (status == AudioEffect.SUCCESS) {
                        acousticEchoCanceler = aec
                        Log.i(TAG, "Hardware AcousticEchoCanceler (AEC) enabled successfully on session $audioSessionId.")
                    } else {
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler (status=$status). Releasing instance.")
                        aec.release()
                    }
                } else {
                    Log.w(TAG, "AcousticEchoCanceler.create returned null for session $audioSessionId.")
                }
            } else {
                Log.d(TAG, "Hardware AcousticEchoCanceler (AEC) is not supported by device HAL.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception initializing AcousticEchoCanceler: ${e.message}")
        }

        // 2. Noise Suppression (NS)
        try {
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(audioSessionId)
                if (ns != null) {
                    val status = ns.setEnabled(true)
                    if (status == AudioEffect.SUCCESS) {
                        noiseSuppressor = ns
                        Log.i(TAG, "Hardware NoiseSuppressor (NS) enabled successfully on session $audioSessionId.")
                    } else {
                        Log.w(TAG, "Failed to enable NoiseSuppressor (status=$status). Releasing instance.")
                        ns.release()
                    }
                } else {
                    Log.w(TAG, "NoiseSuppressor.create returned null for session $audioSessionId.")
                }
            } else {
                Log.d(TAG, "Hardware NoiseSuppressor (NS) is not supported by device HAL.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception initializing NoiseSuppressor: ${e.message}")
        }
    }

    /**
     * Safely disables and releases attached audio effects to prevent hardware/native memory leaks.
     */
    private fun releaseAudioEffects() {
        try {
            acousticEchoCanceler?.let { aec ->
                try {
                    aec.enabled = false
                } catch (_: Exception) {}
                aec.release()
                Log.d(TAG, "AcousticEchoCanceler released.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AcousticEchoCanceler: ${e.message}")
        } finally {
            acousticEchoCanceler = null
        }

        try {
            noiseSuppressor?.let { ns ->
                try {
                    ns.enabled = false
                } catch (_: Exception) {}
                ns.release()
                Log.d(TAG, "NoiseSuppressor released.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing NoiseSuppressor: ${e.message}")
        } finally {
            noiseSuppressor = null
        }
    }

    fun isAecActive(): Boolean = acousticEchoCanceler?.enabled == true
    fun isNoiseSuppressorActive(): Boolean = noiseSuppressor?.enabled == true

    fun getAudioEffectsStatus(): Map<String, Boolean> = mapOf(
        "aecSupported" to AcousticEchoCanceler.isAvailable(),
        "aecActive" to (acousticEchoCanceler?.enabled == true),
        "nsSupported" to NoiseSuppressor.isAvailable(),
        "nsActive" to (noiseSuppressor?.enabled == true)
    )

    fun isCapturing(): Boolean = isRecording.get()
}
