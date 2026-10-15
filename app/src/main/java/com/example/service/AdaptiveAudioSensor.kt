package com.example.service

import android.content.Context
import com.example.voice.core.CentralAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

enum class SensitivityMode(val displayNameBurmese: String, val thresholdMargin: Float) {
  NORMAL("သာမန် အာရုံခံမှု (Standard)", 14.0f),
  HIGH("အာရုံခံမှု မြင့်မား (High Sensitivity)", 8.0f),
  NOISY_ENVIRONMENT("ဆူညံသံများသော ပတ်ဝန်းကျင် (Noisy Outdoor)", 22.0f)
}

/**
 * Adaptive Audio Sensor refactored to consume from [CentralAudioRecorder].
 * NO direct AudioRecord initialization: prevents microphone hardware locking.
 */
class AdaptiveAudioSensor(
  private val context: Context,
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

  private val centralRecorder = CentralAudioRecorder.getInstance(context)
  private var sensingJob: Job? = null
  private var isRunning = false

  private var ambientNoiseFloor = 20.0f
  private var currentSensitivity = SensitivityMode.NORMAL
  private var speechFrameCount = 0
  private var silenceFrameCount = 0
  private var isSpeechOngoing = false

  var onVoiceActivityDetected: ((rms: Float) -> Unit)? = null
  var onVoiceActivityEnded: (() -> Unit)? = null
  var onAmbientNoiseUpdated: ((ambientDb: Float, currentThreshold: Float) -> Unit)? = null
  var onSensorError: ((String) -> Unit)? = null

  fun setSensitivity(mode: SensitivityMode) {
    currentSensitivity = mode
  }

  fun startSensing(): Boolean {
    if (isRunning) return true
    isRunning = true

    sensingJob = coroutineScope.launch(Dispatchers.Default) {
      val started = centralRecorder.start(coroutineScope)
      if (!started) {
        isRunning = false
        onSensorError?.invoke("မိုက်ခရိုဖုန်း အသံဖမ်းယူမှု စတင်မရပါ")
        return@launch
      }

      centralRecorder.audioFlow.collect { chunk ->
        if (!isRunning) return@collect

        val sampleCount = chunk.size / 2
        val audioBuffer = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
          val low = chunk[i * 2].toInt() and 0xFF
          val high = chunk[i * 2 + 1].toInt() shl 8
          audioBuffer[i] = (low or high).toShort()
        }

        processAudioFrame(audioBuffer, sampleCount)
      }
    }

    return true
  }

  private fun processAudioFrame(buffer: ShortArray, samplesRead: Int) {
    var sumSquares = 0.0
    for (i in 0 until samplesRead) {
      val sample = buffer[i].toDouble()
      sumSquares += sample * sample
    }

    val meanSquare = sumSquares / samplesRead
    val rms = sqrt(meanSquare)
    val frameDb = if (rms > 1.0) (20.0 * kotlin.math.log10(rms / 32768.0) + 90.0).toFloat() else 0f

    ambientNoiseFloor = (0.95f * ambientNoiseFloor) + (0.05f * frameDb.coerceAtMost(ambientNoiseFloor + 6.0f))
    val dynamicThreshold = ambientNoiseFloor + currentSensitivity.thresholdMargin
    onAmbientNoiseUpdated?.invoke(ambientNoiseFloor, dynamicThreshold)

    if (frameDb > dynamicThreshold) {
      speechFrameCount++
      silenceFrameCount = 0

      if (speechFrameCount >= 3 && !isSpeechOngoing) {
        isSpeechOngoing = true
        onVoiceActivityDetected?.invoke(frameDb)
      }
    } else {
      silenceFrameCount++
      if (silenceFrameCount >= 10 && isSpeechOngoing) {
        isSpeechOngoing = false
        speechFrameCount = 0
        onVoiceActivityEnded?.invoke()
      }
    }
  }

  fun stopSensing() {
    isRunning = false
    sensingJob?.cancel()
    sensingJob = null

    coroutineScope.launch {
      centralRecorder.stop()
    }
    isSpeechOngoing = false
    speechFrameCount = 0
    silenceFrameCount = 0
  }
}
