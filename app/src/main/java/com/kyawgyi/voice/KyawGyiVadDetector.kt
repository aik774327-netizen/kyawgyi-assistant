package com.kyawgyi.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * KyawGyiVadDetector (Layer 1 Acoustic Sensing & Voice Activity Endpointing)
 *
 * Provides real-time speech boundary detection on raw 16kHz 16-bit Mono PCM audio:
 * 1. Adaptive Noise Floor Tracking: Computes dynamic RMS energy & Zero-Crossing Rate.
 * 2. Speech Onset Detection: Requires sustained voice frames (~180ms) to filter out room clicks/coughs.
 * 3. Dynamic Silence Endpointing: Declares SPEECH_ENDPOINT when silence persists for 600ms-800ms
 *    after speech has occurred, automatically committing the spoken utterance to Layer 2 ASR & Layer 6 NLU.
 * 4. Safety Cutoff: Auto-terminates listening if utterance exceeds max duration (15s).
 */
class KyawGyiVadDetector(
    private val sampleRate: Int = 16000,
    var speechSilenceThresholdMs: Long = 700L, // 700ms silence = speech endpoint
    var minSpeechDurationMs: Long = 180L,      // 180ms voice = confirmed speech start
    var maxUtteranceDurationMs: Long = 15000L   // 15 seconds max safety cutoff
) {

    companion object {
        private const val TAG = "KyawGyiVadDetector"
        private const val DEFAULT_NOISE_FLOOR = 300.0
        private const val ENERGY_MULTIPLIER = 1.6
    }

    enum class VadState {
        IDLE,
        LISTENING_FOR_SPEECH,
        SPEECH_DETECTED,
        SPEECH_ENDPOINT_REACHED
    }

    data class VadResult(
        val state: VadState,
        val isVoiceActive: Boolean,
        val energyRms: Double,
        val speechDurationMs: Long,
        val silenceDurationMs: Long
    )

    private val _vadState = MutableStateFlow(VadState.IDLE)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var adaptiveNoiseFloor = DEFAULT_NOISE_FLOOR
    private var speechStartTimestamp = 0L
    private var lastVoiceTimestamp = 0L
    private var sessionStartTimestamp = 0L

    val isUserSpeaking: Boolean get() = _vadState.value == VadState.SPEECH_DETECTED

    // Event Callbacks
    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechEndpoint: (() -> Unit)? = null

    /**
     * Resets the VAD state machine for a new listening session.
     */
    @Synchronized
    fun startSession() {
        sessionStartTimestamp = System.currentTimeMillis()
        speechStartTimestamp = 0L
        lastVoiceTimestamp = 0L
        _vadState.value = VadState.LISTENING_FOR_SPEECH
        Log.i(TAG, "VAD Session started. State -> LISTENING_FOR_SPEECH")
    }

    /**
     * Resets the VAD detector back to IDLE.
     */
    @Synchronized
    fun reset() {
        speechStartTimestamp = 0L
        lastVoiceTimestamp = 0L
        sessionStartTimestamp = 0L
        _vadState.value = VadState.IDLE
    }

    /**
     * Ingests a raw 16kHz 16-bit Mono PCM chunk and evaluates voice activity & endpoints.
     */
    @Synchronized
    fun processPcmChunk(pcmData: ByteArray): VadResult {
        if (pcmData.isEmpty()) {
            return VadResult(_vadState.value, false, 0.0, 0L, 0L)
        }

        val shorts = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val rms = calculateRms(shorts)
        val zcr = calculateZeroCrossingRate(shorts)

        // Dynamic background noise floor adaptation
        if (rms < adaptiveNoiseFloor * 1.5) {
            adaptiveNoiseFloor = (adaptiveNoiseFloor * 0.95) + (rms * 0.05)
        }

        val dynamicThreshold = adaptiveNoiseFloor * ENERGY_MULTIPLIER
        // Human speech typically has higher energy and balanced zero-crossing rates
        val isVoiceActive = rms > dynamicThreshold && zcr in 0.02..0.55

        val now = System.currentTimeMillis()
        var currentSpeechDuration = 0L
        var currentSilenceDuration = 0L

        when (_vadState.value) {
            VadState.IDLE -> {
                // Not actively listening
            }

            VadState.LISTENING_FOR_SPEECH -> {
                if (isVoiceActive) {
                    if (speechStartTimestamp == 0L) {
                        speechStartTimestamp = now
                    }
                    lastVoiceTimestamp = now
                    currentSpeechDuration = now - speechStartTimestamp

                    if (currentSpeechDuration >= minSpeechDurationMs) {
                        _vadState.value = VadState.SPEECH_DETECTED
                        Log.i(TAG, "Speech Onset detected (duration: ${currentSpeechDuration}ms). State -> SPEECH_DETECTED")
                        onSpeechStarted?.invoke()
                    }
                } else {
                    speechStartTimestamp = 0L
                }
            }

            VadState.SPEECH_DETECTED -> {
                currentSpeechDuration = now - speechStartTimestamp

                if (isVoiceActive) {
                    lastVoiceTimestamp = now
                } else {
                    currentSilenceDuration = now - lastVoiceTimestamp

                    // 1. Dynamic silence endpoint check (User stopped speaking for threshold ms)
                    if (currentSilenceDuration >= speechSilenceThresholdMs) {
                        _vadState.value = VadState.SPEECH_ENDPOINT_REACHED
                        Log.i(TAG, "Speech Endpoint reached! Silence duration: ${currentSilenceDuration}ms. Triggering endpoint callback...")
                        onSpeechEndpoint?.invoke()
                    }
                }

                // 2. Safety max duration cutoff
                if (currentSpeechDuration >= maxUtteranceDurationMs) {
                    _vadState.value = VadState.SPEECH_ENDPOINT_REACHED
                    Log.w(TAG, "Max utterance duration cutoff reached (${currentSpeechDuration}ms). Forcing endpoint.")
                    onSpeechEndpoint?.invoke()
                }
            }

            VadState.SPEECH_ENDPOINT_REACHED -> {
                // Waiting for session reset or pipeline processing
            }
        }

        return VadResult(
            state = _vadState.value,
            isVoiceActive = isVoiceActive,
            energyRms = rms,
            speechDurationMs = currentSpeechDuration,
            silenceDurationMs = currentSilenceDuration
        )
    }

    private fun calculateRms(shorts: ShortArray): Double {
        var sum = 0.0
        for (sample in shorts) {
            sum += sample * sample
        }
        return sqrt(sum / shorts.size)
    }

    private fun calculateZeroCrossingRate(shorts: ShortArray): Double {
        var crossings = 0
        for (i in 0 until shorts.size - 1) {
            if ((shorts[i] >= 0 && shorts[i + 1] < 0) || (shorts[i] < 0 && shorts[i + 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / shorts.size
    }
}
