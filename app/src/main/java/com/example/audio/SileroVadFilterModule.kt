package com.example.audio

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SileroVadFilterModule (Layer 1 - Voice Activity Detection Pipeline)
 *
 * Implements high-resilience on-device Voice Activity Detection on raw 16kHz 16-bit Mono PCM:
 * 1. Adaptive Noise Floor Tracking: Computes dynamic RMS energy & zero-crossing rate.
 * 2. Speech Onset / Offset State Machine:
 *    - Detects start of human voice (Speech Onset) after sustained voice frames.
 *    - Preserves trailing hangover frames (Speech Offset) to prevent clipping Myanmar tone endings.
 * 3. Silence Filtering: Discards continuous dead silence to save CPU, battery, and network tokens.
 */
class SileroVadFilterModule(
    private val sampleRate: Int = 16000,
    private val frameSizeSamples: Int = 512, // 32ms window at 16kHz
    private val speechProbabilityThreshold: Float = 0.5f,
    private val minSpeechFrames: Int = 3,    // ~100ms consecutive voice to trigger SPEECH
    private val hangoverFrames: Int = 15     // ~500ms trailing speech buffer before declaring SILENCE
) {

    companion object {
        private const val TAG = "SileroVadFilter"
        private const val DEFAULT_ENERGY_THRESHOLD = 300.0 // Baseline acoustic energy
    }

    enum class VadState {
        SILENCE,
        SPEECH_ONSET,
        SPEECH,
        SPEECH_HANGOVER
    }

    data class VadDecision(
        val isVoiceActive: Boolean,
        val probability: Float,
        val energyRms: Double,
        val state: VadState,
        val filteredPcm: ByteArray?
    )

    private val _vadState = MutableStateFlow(VadState.SILENCE)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var consecutiveVoiceFrames = 0
    private var hangoverCounter = 0
    private var adaptiveNoiseFloor = DEFAULT_ENERGY_THRESHOLD

    // Observers for speech boundary transitions
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechEnded: (() -> Unit)? = null

    /**
     * Ingests a raw 16-bit Mono PCM byte array and evaluates voice activity.
     */
    @Synchronized
    fun processPcmChunk(pcmData: ByteArray): VadDecision {
        if (pcmData.isEmpty()) {
            return VadDecision(
                isVoiceActive = false,
                probability = 0f,
                energyRms = 0.0,
                state = _vadState.value,
                filteredPcm = null
            )
        }

        // Convert byte array to shorts for acoustic analysis
        val shorts = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val rms = calculateRms(shorts)
        val zcr = calculateZeroCrossingRate(shorts)

        // Dynamic threshold adjustment
        if (rms < adaptiveNoiseFloor * 1.5) {
            adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.95) + (rms * 0.05)
        }

        val speechProbability = computeSpeechProbability(rms, zcr, adaptiveNoiseFloor)
        val isVoiceFrame = speechProbability >= speechProbabilityThreshold

        val previousState = _vadState.value
        val newState = when (previousState) {
            VadState.SILENCE -> {
                if (isVoiceFrame) {
                    consecutiveVoiceFrames++
                    if (consecutiveVoiceFrames >= minSpeechFrames) {
                        hangoverCounter = hangoverFrames
                        VadState.SPEECH
                    } else {
                        VadState.SPEECH_ONSET
                    }
                } else {
                    consecutiveVoiceFrames = 0
                    VadState.SILENCE
                }
            }
            VadState.SPEECH_ONSET -> {
                if (isVoiceFrame) {
                    consecutiveVoiceFrames++
                    if (consecutiveVoiceFrames >= minSpeechFrames) {
                        hangoverCounter = hangoverFrames
                        VadState.SPEECH
                    } else {
                        VadState.SPEECH_ONSET
                    }
                } else {
                    consecutiveVoiceFrames = 0
                    VadState.SILENCE
                }
            }
            VadState.SPEECH -> {
                if (isVoiceFrame) {
                    hangoverCounter = hangoverFrames
                    VadState.SPEECH
                } else {
                    hangoverCounter--
                    if (hangoverCounter > 0) {
                        VadState.SPEECH_HANGOVER
                    } else {
                        VadState.SILENCE
                    }
                }
            }
            VadState.SPEECH_HANGOVER -> {
                if (isVoiceFrame) {
                    hangoverCounter = hangoverFrames
                    VadState.SPEECH
                } else {
                    hangoverCounter--
                    if (hangoverCounter > 0) {
                        VadState.SPEECH_HANGOVER
                    } else {
                        VadState.SILENCE
                    }
                }
            }
        }

        _vadState.value = newState

        // Notify transition events
        if (previousState == VadState.SILENCE && (newState == VadState.SPEECH || newState == VadState.SPEECH_ONSET)) {
            Log.d(TAG, "VAD: Speech started (rms=$rms, prob=$speechProbability)")
            onSpeechStarted?.invoke()
        } else if ((previousState == VadState.SPEECH || previousState == VadState.SPEECH_HANGOVER) && newState == VadState.SILENCE) {
            Log.d(TAG, "VAD: Speech ended (noiseFloor=$adaptiveNoiseFloor)")
            onSpeechEnded?.invoke()
        }

        val isSpeechActive = newState != VadState.SILENCE
        return VadDecision(
            isVoiceActive = isSpeechActive,
            probability = speechProbability,
            energyRms = rms,
            state = newState,
            filteredPcm = if (isSpeechActive) pcmData else null
        )
    }

    private fun calculateRms(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val d = sample.toDouble()
            sum += d * d
        }
        return sqrt(sum / samples.size)
    }

    private fun calculateZeroCrossingRate(samples: ShortArray): Double {
        if (samples.size < 2) return 0.0
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / (samples.size - 1)
    }

    private fun computeSpeechProbability(rms: Double, zcr: Double, noiseFloor: Double): Float {
        val snr = rms / (noiseFloor + 1e-6)
        var score = 0.0

        if (snr > 2.5) score += 0.5
        if (snr > 4.0) score += 0.3
        if (zcr in 0.02..0.35) score += 0.2 // Typical human speech zero crossing range

        return score.coerceIn(0.0, 1.0).toFloat()
    }

    fun reset() {
        consecutiveVoiceFrames = 0
        hangoverCounter = 0
        _vadState.value = VadState.SILENCE
    }
}
