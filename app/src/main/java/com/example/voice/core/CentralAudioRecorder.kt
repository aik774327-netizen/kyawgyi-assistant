package com.example.voice.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Thread-safe Singleton Central Audio Producer.
 *
 * CRITICAL CONSTRAINT:
 * Only THIS class is permitted to initialize and hold the Android [AudioRecord] hardware instance.
 * All downstream engines (Online Gemini, Offline Sherpa-ONNX, Wake Word Detector, UI Waveforms)
 * must consume from this centralized 16kHz PCM 16-bit Mono stream via [audioFlow].
 *
 * Features:
 * - 16000 Hz, CHANNEL_IN_MONO, ENCODING_PCM_16BIT.
 * - Backpressure-buffered SharedFlow (extraBufferCapacity = 64 chunks).
 * - Thread-safe lifecycle using Kotlin Coroutine Mutex.
 * - Hardware lock prevention and graceful audio recorder resource cleanup.
 */
class CentralAudioRecorder private constructor(private val appContext: Context) {

  companion object {
    private const val TAG = "CentralAudioRecorder"

    const val SAMPLE_RATE_HZ = 16000
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    // 512 shorts = 1024 bytes = 32ms frame chunk at 16kHz (optimal for neural STT & ONNX)
    const val CHUNK_SIZE_BYTES = 1024

    @Volatile
    private var instance: CentralAudioRecorder? = null

    fun getInstance(context: Context): CentralAudioRecorder {
      return instance ?: synchronized(this) {
        instance ?: CentralAudioRecorder(context.applicationContext).also { instance = it }
      }
    }
  }

  private val mutex = Mutex()
  private var audioRecord: AudioRecord? = null
  private var recordingJob: Job? = null

  // Hot SharedFlow broadcasting audio chunks to all subscribers
  private val _audioFlow = MutableSharedFlow<ByteArray>(
    replay = 0,
    extraBufferCapacity = 64
  )
  val audioFlow: SharedFlow<ByteArray> = _audioFlow.asSharedFlow()

  // State indicators
  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _currentRmsDb = MutableStateFlow(0f)
  val currentRmsDb: StateFlow<Float> = _currentRmsDb.asStateFlow()

  private val _activeSubscribers = MutableStateFlow(0)
  val activeSubscribers: StateFlow<Int> = _activeSubscribers.asStateFlow()

  /**
   * Starts capturing microphone audio into the centralized stream.
   * Safe to call multiple times; if already running, increments subscriber count.
   */
  @SuppressLint("MissingPermission")
  suspend fun start(scope: CoroutineScope): Boolean = mutex.withLock {
    _activeSubscribers.value++
    if (audioRecord != null && _isRecording.value) {
      Log.d(TAG, "AudioRecord already active. Subscriber count: ${_activeSubscribers.value}")
      return true
    }

    val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
    if (minBufferSize <= 0) {
      Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
      return false
    }

    val bufferSize = maxOf(minBufferSize * 2, CHUNK_SIZE_BYTES * 4)

    try {
      val record = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE_HZ,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        bufferSize
      )

      if (record.state != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord failed to initialize (State != STATE_INITIALIZED)")
        record.release()
        return false
      }

      record.startRecording()
      audioRecord = record
      _isRecording.value = true

      recordingJob = scope.launch(Dispatchers.IO) {
        readAudioLoop(record)
      }

      Log.i(TAG, "CentralAudioRecorder successfully started at ${SAMPLE_RATE_HZ}Hz Mono.")
      return true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start CentralAudioRecorder", e)
      cleanupLocked()
      return false
    }
  }

  /**
   * Continuous reading loop executed in Dispatchers.IO.
   */
  private suspend fun readAudioLoop(record: AudioRecord) {
    val buffer = ByteArray(CHUNK_SIZE_BYTES)

    try {
      while (recordingJob?.isActive == true && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val bytesRead = record.read(buffer, 0, buffer.size)

        if (bytesRead > 0) {
          val copy = buffer.copyOf(bytesRead)
          _audioFlow.emit(copy)

          // Calculate RMS level for UI audio waveforms
          calculateRms(copy, bytesRead)
        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
          Log.w(TAG, "AudioRecord read error code: $bytesRead")
          break
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Exception inside readAudioLoop", e)
    } finally {
      Log.d(TAG, "readAudioLoop exited.")
    }
  }

  private fun calculateRms(bytes: ByteArray, length: Int) {
    var sum = 0.0
    val sampleCount = length / 2
    if (sampleCount <= 0) return

    for (i in 0 until length - 1 step 2) {
      val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
      val sampleShort = sample.toShort()
      sum += sampleShort * sampleShort
    }

    val mean = sum / sampleCount
    val rms = sqrt(mean)
    val db = if (rms > 0) 20 * log10(rms / 32767.0).toFloat() + 90f else 0f
    _currentRmsDb.value = db.coerceIn(0f, 100f)
  }

  /**
   * Stops recording and releases microphone hardware if subscriber count drops to zero
   * or if [forceRelease] is true.
   */
  suspend fun stop(forceRelease: Boolean = false): Unit = mutex.withLock {
    if (_activeSubscribers.value > 0 && !forceRelease) {
      _activeSubscribers.value--
    }

    if (_activeSubscribers.value <= 0 || forceRelease) {
      _activeSubscribers.value = 0
      cleanupLocked()
      Log.i(TAG, "CentralAudioRecorder stopped and AudioRecord hardware released.")
    }
  }

  private fun cleanupLocked() {
    recordingJob?.cancel()
    recordingJob = null

    try {
      audioRecord?.let {
        if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          it.stop()
        }
        it.release()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error releasing AudioRecord", e)
    } finally {
      audioRecord = null
      _isRecording.value = false
      _currentRmsDb.value = 0f
    }
  }
}
