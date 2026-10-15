package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.voice.core.CentralAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Microphone Audio Recorder that consumes from [CentralAudioRecorder].
 * Writes standard RIFF/WAVE PCM audio (16000 Hz, 16-bit Mono).
 *
 * REFACTORED TO CENTRAL AUDIO ARCHITECTURE:
 * Never initializes [android.media.AudioRecord] directly to prevent microphone hardware lock.
 */
class WavAudioRecorder(
  private val context: Context,
  private val coroutineScope: CoroutineScope
) {
  companion object {
    private const val TAG = "WavAudioRecorder"
    const val SAMPLE_RATE = CentralAudioRecorder.SAMPLE_RATE_HZ
    const val CHUNK_SIZE = CentralAudioRecorder.CHUNK_SIZE_BYTES
  }

  private val centralRecorder = CentralAudioRecorder.getInstance(context)
  private var recordingJob: Job? = null
  private var pcmOutputStream: ByteArrayOutputStream? = null

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _rmsLevel = MutableStateFlow(0f)
  val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

  private val _decibels = MutableStateFlow(0f)
  val decibels: StateFlow<Float> = _decibels.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  var onRecordingFinished: ((ByteArray, File?) -> Unit)? = null
  var onError: ((String) -> Unit)? = null

  @SuppressLint("MissingPermission")
  fun startRecording(): Boolean {
    if (_isRecording.value) return true

    _isRecording.value = true
    _durationMs.value = 0L
    pcmOutputStream = ByteArrayOutputStream()
    val startTime = System.currentTimeMillis()

    recordingJob = coroutineScope.launch(Dispatchers.IO) {
      val started = centralRecorder.start(coroutineScope)
      if (!started) {
        _isRecording.value = false
        onError?.invoke("မိုက်ခရိုဖုန်း အသံဖမ်းယူမှု စတင်မရပါ")
        return@launch
      }

      centralRecorder.audioFlow.collect { chunk ->
        if (!_isRecording.value) return@collect

        pcmOutputStream?.write(chunk)

        // Calculate RMS & Decibels
        val sampleCount = chunk.size / 2
        var sumSquare = 0.0
        for (i in 0 until chunk.size - 1 step 2) {
          val s = ((chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)).toShort()
          sumSquare += s * s
        }

        if (sampleCount > 0) {
          val rms = sqrt(sumSquare / sampleCount)
          val normalizedRms = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
          val db = if (rms > 1.0) (20 * log10(rms / 32768.0) + 90).toFloat().coerceAtLeast(0f) else 0f

          _rmsLevel.value = normalizedRms
          _decibels.value = db
          _durationMs.value = System.currentTimeMillis() - startTime
        }
      }
    }

    return true
  }

  fun stopRecording(): ByteArray? {
    if (!_isRecording.value) return null

    _isRecording.value = false
    _rmsLevel.value = 0f
    _decibels.value = 0f
    recordingJob?.cancel()
    recordingJob = null

    coroutineScope.launch {
      centralRecorder.stop()
    }

    val rawPcmData = pcmOutputStream?.toByteArray() ?: ByteArray(0)
    pcmOutputStream = null

    if (rawPcmData.isEmpty()) {
      onError?.invoke("ဖမ်းယူရရှိသော အသံဖိုင် ဗလာဖြစ်နေပါသည်")
      return null
    }

    val wavData = createWavData(rawPcmData, SAMPLE_RATE, 1, 16)
    val cacheFile = try {
      val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
      FileOutputStream(file).use { it.write(wavData) }
      file
    } catch (e: Exception) {
      Log.w(TAG, "Failed to cache wav: ${e.message}")
      null
    }

    onRecordingFinished?.invoke(wavData, cacheFile)
    return wavData
  }

  fun cleanup() {
    stopRecording()
  }

  fun cancelRecording() {
    _isRecording.value = false
    _rmsLevel.value = 0f
    _decibels.value = 0f
    recordingJob?.cancel()
    recordingJob = null
    pcmOutputStream = null
    coroutineScope.launch {
      centralRecorder.stop()
    }
  }

  private fun createWavData(
    pcmData: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int
  ): ByteArray {
    val totalAudioLen = pcmData.size.toLong()
    val totalDataLen = totalAudioLen + 36
    val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xffL).toByte()
    header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
    header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
    header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1
    header[21] = 0
    header[22] = channels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xffL).toByte()
    header[29] = ((byteRate shr 8) and 0xffL).toByte()
    header[30] = ((byteRate shr 16) and 0xffL).toByte()
    header[31] = ((byteRate shr 24) and 0xffL).toByte()
    header[32] = (channels * bitsPerSample / 8).toByte()
    header[33] = 0
    header[34] = bitsPerSample.toByte()
    header[35] = 0
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (totalAudioLen and 0xffL).toByte()
    header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
    header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
    header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

    val out = ByteArray(header.size + pcmData.size)
    System.arraycopy(header, 0, out, 0, header.size)
    System.arraycopy(pcmData, 0, out, header.size, pcmData.size)
    return out
  }
}
