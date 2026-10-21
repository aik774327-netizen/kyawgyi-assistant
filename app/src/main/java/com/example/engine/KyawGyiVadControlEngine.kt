package com.example.engine

import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Human-Centric Voice Activity Detection (VAD) & AGC Control Engine
 *
 * Features:
 * - Adaptive ambient noise floor tracking.
 * - Hardware/Software AGC gain boost (+20dB max) without clipping.
 * - Artifact rejection (<150ms clicks/claps).
 * - Multi-pause Burmese conversational tolerance (configurable 1800ms default silence confirmation).
 */
class KyawGyiVadControlEngine(
    var silenceTimeoutMs: Long = SILENCE_TIMEOUT_MS,
    private val onAutoOpenMic: () -> Unit = {},
    private val onAutoCloseMicAndSendToStt: (ByteArray) -> Unit = {}
) {

    companion object {
        private const val TAG = "KyawGyiVadEngine"
        const val SILENCE_TIMEOUT_MS = 1800L
        const val MIN_SPEECH_DURATION_MS = 150L
        private const val MAX_GAIN_BOOST_DB = 20.0f
        private const val NOISE_FLOOR_ADAPT_FACTOR = 0.05f
    }

    enum class VadState {
        LOW_POWER_LISTENING,
        SPEECH_DETECTED_RECORDING,
        SILENCE_WAITING
    }

    @Volatile
    var currentState: VadState = VadState.LOW_POWER_LISTENING
        private set

    private var ambientNoiseFloorDb: Float = 35.0f
    private var speechStartTimestamp: Long = 0L
    private var lastVoiceTimestamp: Long = System.currentTimeMillis()

    private val pcmAccumulator = ArrayList<Byte>()

    /**
     * Process incoming 16-bit PCM audio frame chunk.
     */
    @Synchronized
    fun processPcmFrame(pcmChunk: ByteArray, readLength: Int) {
        try {
            if (readLength <= 0) return

            // 1. Calculate RMS Energy and dB of frame
            val rmsDb = calculateRmsDb(pcmChunk, readLength)

            // Adaptive noise floor tracking when silent
            if (rmsDb < ambientNoiseFloorDb + 2.0f) {
                ambientNoiseFloorDb = (1f - NOISE_FLOOR_ADAPT_FACTOR) * ambientNoiseFloorDb + NOISE_FLOOR_ADAPT_FACTOR * rmsDb
            }

            // Speech detection criteria: energy exceeds ambient threshold by +3dB
            val isSpeechDetected = rmsDb >= (ambientNoiseFloorDb + 3.0f)

            // 2. Apply Software AGC / Dynamic Range Compression (+20dB max gain)
            val amplifiedPcm = applySoftwareAgcBoost(pcmChunk, readLength, currentDb = rmsDb)

            // 3. VAD State Machine
            val now = System.currentTimeMillis()

            if (isSpeechDetected) {
                lastVoiceTimestamp = now

                when (currentState) {
                    VadState.LOW_POWER_LISTENING -> {
                        speechStartTimestamp = now
                        currentState = VadState.SPEECH_DETECTED_RECORDING
                        pcmAccumulator.clear()
                        pcmAccumulator.addAll(amplifiedPcm.toList())
                        onAutoOpenMic()
                        Log.d(TAG, "VAD Trigger: Human Voice Detected (+3dB over ambient). Auto-Open Mic.")
                    }
                    VadState.SPEECH_DETECTED_RECORDING, VadState.SILENCE_WAITING -> {
                        currentState = VadState.SPEECH_DETECTED_RECORDING
                        pcmAccumulator.addAll(amplifiedPcm.toList())
                    }
                }
            } else {
                // Silence detected in current frame
                when (currentState) {
                    VadState.SPEECH_DETECTED_RECORDING -> {
                        val speechDuration = now - speechStartTimestamp
                        if (speechDuration < MIN_SPEECH_DURATION_MS) {
                            Log.d(TAG, "VAD Artifact Rejection: Duration ${speechDuration}ms < 150ms. Dropping.")
                            currentState = VadState.LOW_POWER_LISTENING
                            pcmAccumulator.clear()
                        } else {
                            currentState = VadState.SILENCE_WAITING
                            pcmAccumulator.addAll(amplifiedPcm.toList())
                            checkSilenceTimeout(now)
                        }
                    }
                    VadState.SILENCE_WAITING -> {
                        pcmAccumulator.addAll(amplifiedPcm.toList())
                        checkSilenceTimeout(now)
                    }
                    VadState.LOW_POWER_LISTENING -> {
                        // Remain in low power state
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in processPcmFrame: ${e.localizedMessage}")
        }
    }

    private fun checkSilenceTimeout(now: Long) {
        val elapsedSilence = now - lastVoiceTimestamp
        if (elapsedSilence >= silenceTimeoutMs) {
            Log.d(TAG, "VAD Silence Timeout (${silenceTimeoutMs}ms reached). Auto-Close Mic & Pipe PCM to STT Engine.")
            stopAndProcessAudioBuffer()
        }
    }

    @Synchronized
    fun stopAndProcessAudioBuffer() {
        try {
            if (pcmAccumulator.isNotEmpty()) {
                val pcmData = pcmAccumulator.toByteArray()
                pcmAccumulator.clear()
                currentState = VadState.LOW_POWER_LISTENING
                onAutoCloseMicAndSendToStt(pcmData)
            } else {
                currentState = VadState.LOW_POWER_LISTENING
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAndProcessAudioBuffer: ${e.localizedMessage}")
            currentState = VadState.LOW_POWER_LISTENING
            pcmAccumulator.clear()
        }
    }

    /**
     * Compute Root Mean Square (RMS) decibels from 16-bit PCM buffer.
     */
    private fun calculateRmsDb(pcmChunk: ByteArray, readLength: Int): Float {
        var sumSquares = 0.0
        val sampleCount = readLength / 2
        if (sampleCount == 0) return 0.0f

        var i = 0
        while (i < readLength - 1) {
            val sample = (pcmChunk[i].toInt() and 0xFF) or (pcmChunk[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sumSquares += (shortSample * shortSample).toDouble()
            i += 2
        }

        val rms = Math.sqrt(sumSquares / sampleCount)
        if (rms <= 1e-6) return 0.0f
        return (20 * log10(rms)).toFloat()
    }

    /**
     * Apply Dynamic Range Compression and Software Gain Boost up to +20dB without clipping.
     */
    private fun applySoftwareAgcBoost(pcmChunk: ByteArray, readLength: Int, currentDb: Float): ByteArray {
        val output = ByteArray(readLength)
        if (readLength <= 0) return output

        val targetDbBoost = min(MAX_GAIN_BOOST_DB, max(0.0f, 50.0f - currentDb))
        val gainMultiplier = Math.pow(10.0, (targetDbBoost / 20.0)).toFloat()

        var i = 0
        while (i < readLength - 1) {
            val sample = (pcmChunk[i].toInt() and 0xFF) or (pcmChunk[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()

            val boosted = (shortSample * gainMultiplier).toInt()
            val clamped = max(-32768, min(32767, boosted)).toShort()

            output[i] = (clamped.toInt() and 0xFF).toByte()
            output[i + 1] = (clamped.toInt() shr 8 and 0xFF).toByte()

            i += 2
        }

        return output
    }

    fun resetState() {
        currentState = VadState.LOW_POWER_LISTENING
        pcmAccumulator.clear()
        lastVoiceTimestamp = System.currentTimeMillis()
    }
}
