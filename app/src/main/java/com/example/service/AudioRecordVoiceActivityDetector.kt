package com.example.service

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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VoiceActivityEvent(
  val keyword: String = "ကျော်ကြီး",
  val confidence: Float,
  val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Voice-Activity & Energy Trigger (AudioRecordVoiceActivityDetector).
 *
 * HONEST RELABEL & ARCHITECTURE DOCUMENTATION:
 * This class is a voice-activity/energy detector (VAD) and heuristic acoustic-envelope trigger,
 * NOT a true keyword spotter. It continuously senses audio stream energy and zero-crossing rates
 * from [CentralAudioRecorder] without microphone hardware locking.
 *
 * True keyword recognition / spotting cannot be done with heuristic code alone.
 * True keyword recognition requires bundling a trained ML keyword-spotting (KWS) model asset
 * (such as a TFLite, Sherpa-ONNX KWS, or Porcupine model file in assets/).
 *
 * STATUS: NOT DONE — True keyword recognition requires bundling an ML model asset.
 *
 * Note: Phoneme matching is NOT performed; thresholds are tuned conservatively to avoid over-triggering.
 */
class AudioRecordVoiceActivityDetector(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

  private val centralRecorder = CentralAudioRecorder.getInstance(context)
  private var streamJob: Job? = null
  private var isListening = false

  // Observable states
  private val _isStreaming = MutableStateFlow(false)
  val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

  private val _lastWakeWordEvent = MutableStateFlow<VoiceActivityEvent?>(null)
  val lastWakeWordEvent: StateFlow<VoiceActivityEvent?> = _lastWakeWordEvent.asStateFlow()

  private val _currentRmsDb = MutableStateFlow(0f)
  val currentRmsDb: StateFlow<Float> = _currentRmsDb.asStateFlow()

  private val _detectionConfidence = MutableStateFlow(0f)
  val detectionConfidence: StateFlow<Float> = _detectionConfidence.asStateFlow()

  var onVoiceActivityDetected: ((keyword: String, confidence: Float) -> Unit)? = null

  companion object {
    private const val TAG = "AudioVoiceActivityDetector"
    private const val FRAME_SIZE = 512 // ~32ms window at 16kHz
    // Relabeled to honestly describe voice-activity trigger
    private const val VOICE_ACTIVITY_KEYWORD = "ကျော်ကြီး (Kyaw Gyi - Voice Activity Trigger)"
    // Conservatively tuned confidence threshold to avoid over-triggering
    private const val CONFIDENCE_THRESHOLD = 0.70f
    private const val DETECTION_COOLDOWN_MS = 2500L
  }

  enum class AcousticState {
    IDLE,
    SYLLABLE_KYAW,
    INTER_SYLLABLE_DIP,
    SYLLABLE_GYI
  }

  /**
   * Pure state machine logic for energy-envelope transition detection.
   * Completely decoupled from Android AudioRecord/Context for JVM unit testability.
   * Note: This measures heuristic acoustic energy levels, NOT machine learning phonemes.
   * True keyword spotting requires bundling an ML model asset (NOT DONE — needs model asset).
   */
  class WakeWordStateMachine(
    var currentState: AcousticState = AcousticState.IDLE,
    var stateEnterTimeMs: Long = 0L,
    var lastTriggerTimeMs: Long = 0L,
    val detectionCooldownMs: Long = DETECTION_COOLDOWN_MS
  ) {
    var detectedEvent: Pair<String, Float>? = null
      private set

    fun processFrame(
      now: Long,
      isSpeechBand: Boolean,
      spectralTilt: Float,
      snr: Float
    ): AcousticState {
      detectedEvent = null
      when (currentState) {
        AcousticState.IDLE -> {
          if (isSpeechBand && spectralTilt in 0.35f..1.65f && (now - lastTriggerTimeMs > detectionCooldownMs)) {
            currentState = AcousticState.SYLLABLE_KYAW
            stateEnterTimeMs = now
          }
        }
        AcousticState.SYLLABLE_KYAW -> {
          val duration = now - stateEnterTimeMs
          if (duration in 140..450) {
            if (snr < 4.0f || spectralTilt > 1.8f) {
              currentState = AcousticState.INTER_SYLLABLE_DIP
              stateEnterTimeMs = now
            }
          } else if (duration > 500) {
            currentState = AcousticState.IDLE
          }
        }
        AcousticState.INTER_SYLLABLE_DIP -> {
          val dipDuration = now - stateEnterTimeMs
          if (dipDuration in 40..250 && isSpeechBand && spectralTilt in 0.5f..2.2f) {
            currentState = AcousticState.SYLLABLE_GYI
            stateEnterTimeMs = now
          } else if (dipDuration > 300) {
            currentState = AcousticState.IDLE
          }
        }
        AcousticState.SYLLABLE_GYI -> {
          val secondDuration = now - stateEnterTimeMs
          if (secondDuration in 180..600) {
            val snrScore = (snr / 25.0f).coerceIn(0.6f, 1.0f)
            val timingScore = if (secondDuration in 180..380) 0.95f else 0.78f
            val confidence = (snrScore * 0.5f + timingScore * 0.5f).coerceIn(0.5f, 0.98f)

            if (confidence >= CONFIDENCE_THRESHOLD) {
              lastTriggerTimeMs = now
              detectedEvent = VOICE_ACTIVITY_KEYWORD to confidence
            }
            currentState = AcousticState.IDLE
          } else if (secondDuration > 650) {
            currentState = AcousticState.IDLE
          }
        }
      }
      return currentState
    }

    fun reset() {
      currentState = AcousticState.IDLE
      stateEnterTimeMs = 0L
      detectedEvent = null
    }
  }

  private val stateMachine = WakeWordStateMachine()
  private var ambientNoiseFloorDb = 25.0f

  fun startContinuousListening(): Boolean {
    if (isListening) return true
    isListening = true
    _isStreaming.value = true

    streamJob = scope.launch(Dispatchers.Default) {
      val started = centralRecorder.start(scope)
      if (!started) {
        Log.e(TAG, "Failed to start CentralAudioRecorder")
        isListening = false
        _isStreaming.value = false
        return@launch
      }

      centralRecorder.audioFlow.collect { chunk ->
        if (!isListening) return@collect

        // Convert byte chunk to ShortArray frame
        val sampleCount = chunk.size / 2
        val shortBuffer = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
          val low = chunk[i * 2].toInt() and 0xFF
          val high = chunk[i * 2 + 1].toInt() shl 8
          shortBuffer[i] = (low or high).toShort()
        }

        processAudioFrame(shortBuffer, sampleCount)
      }
    }

    return true
  }

  private fun processAudioFrame(buffer: ShortArray, length: Int) {
    val now = System.currentTimeMillis()

    // Clipping-guarded normalization: apply gain (+6dB), then clamp each sample to Short.MIN_VALUE..Short.MAX_VALUE
    val gainMultiplier = 2.0f
    for (i in 0 until length) {
      val amplified = buffer[i].toFloat() * gainMultiplier
      val clamped = amplified.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
      buffer[i] = clamped.toInt().toShort()
    }

    // 1. Calculate RMS Energy
    var sumSquares = 0.0
    var zeroCrossings = 0
    var lowBandSum = 0.0
    var highBandSum = 0.0

    for (i in 0 until length) {
      val sample = buffer[i].toDouble()
      sumSquares += sample * sample

      if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
        zeroCrossings++
      }

      if (i > 1) {
        val highPass = buffer[i] - buffer[i - 1]
        val lowPass = buffer[i] + buffer[i - 1]
        highBandSum += abs(highPass)
        lowBandSum += abs(lowPass)
      }
    }

    val meanSquares = sumSquares / length
    val rms = sqrt(meanSquares)
    val frameDb = if (rms > 1.0) (20.0 * log10(rms / 32768.0) + 90.0).toFloat() else 0f
    _currentRmsDb.value = frameDb

    ambientNoiseFloorDb = 0.98f * ambientNoiseFloorDb + 0.02f * min(ambientNoiseFloorDb + 5f, max(15.0f, frameDb))

    val snr = frameDb - ambientNoiseFloorDb
    // Conservative speech energy threshold (snr > 9.0f) to avoid over-triggering on ambient noise
    val isSpeechBand = snr > 9.0f && zeroCrossings in 15..120
    val spectralTilt = if (lowBandSum > 0) (highBandSum / lowBandSum).toFloat() else 1.0f

    val nextState = stateMachine.processFrame(
      now = now,
      isSpeechBand = isSpeechBand,
      spectralTilt = spectralTilt,
      snr = snr
    )

    stateMachine.detectedEvent?.let { (kw, conf) ->
      _detectionConfidence.value = conf
      val event = VoiceActivityEvent(
        keyword = kw,
        confidence = conf,
        timestampMs = now
      )
      _lastWakeWordEvent.value = event
      onVoiceActivityDetected?.invoke(kw, conf)
    }
  }

  private fun calculateConfidence(snr: Float, secondSyllableDurationMs: Long): Float {
    val snrScore = (snr / 25.0f).coerceIn(0.6f, 1.0f)
    val timingScore = if (secondSyllableDurationMs in 180..380) 0.95f else 0.78f
    return (snrScore * 0.5f + timingScore * 0.5f).coerceIn(0.5f, 0.98f)
  }

  fun stopContinuousListening() {
    isListening = false
    _isStreaming.value = false
    streamJob?.cancel()
    streamJob = null

    scope.launch {
      centralRecorder.stop()
    }
    stateMachine.reset()
  }

  // Audit C3: a simulateWakeWordDetected() helper used to live here — it
  // fabricated a 0.96-confidence wake event on demand. It had zero callers
  // and was removed so no future wiring can fake a wake detection.
}
