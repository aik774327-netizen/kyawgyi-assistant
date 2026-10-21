package com.kyawgyi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Earcon tone types used by [AcousticToneGenerator] for non-verbal voice assistant cues.
 */
enum class EarconToneType(
    val startFreqHz: Double,
    val endFreqHz: Double,
    val durationMs: Int
) {
    WAKE_WORD_DETECTED(startFreqHz = 587.33, endFreqHz = 880.00, durationMs = 110), // D5 -> A5 rising chime
    START_LISTENING(startFreqHz = 523.25, endFreqHz = 783.99, durationMs = 95),     // C5 -> G5 crisp cue
    STOP_LISTENING(startFreqHz = 659.25, endFreqHz = 440.00, durationMs = 95),      // E5 -> A4 falling cue
    ERROR_ALERT(startFreqHz = 330.00, endFreqHz = 220.00, durationMs = 140)         // E4 -> A3 soft alert
}

/**
 * AcousticToneGenerator (Requirements 12 — Echo-Safe Earcon & Chime Synthesizer)
 *
 * Synthesizes clean, click-free sine-sweep earcon chimes (with Hanning attack/decay envelopes)
 * so the user receives instant auditory confirmation on Wake-Word, Start Listening, Stop Listening,
 * or Error events without relying on external audio files.
 *
 * Coordinates with [AudioFocusAndAecManager] so chime playback never triggers self-transcription.
 */
class AcousticToneGenerator(
    private val context: Context,
    private val sampleRateHz: Int = 16000
) {

    companion object {
        private const val TAG = "AcousticToneGenerator"
    }

    /**
     * Synthesizes a 16-bit Little-Endian Mono PCM earcon waveform for [toneType].
     * Exposed for deterministic unit testing and static `AudioTrack` playback.
     */
    fun synthesizeTonePcm16Le(
        toneType: EarconToneType,
        amplitudeScale: Float = 0.28f
    ): ByteArray {
        val totalSamples = ((sampleRateHz * toneType.durationMs) / 1000).coerceAtLeast(16)
        val buffer = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        val rampSamples = (totalSamples / 6).coerceAtLeast(8)

        var phase = 0.0
        for (i in 0 until totalSamples) {
            val progress = i.toDouble() / totalSamples.toDouble()
            val instFreq = toneType.startFreqHz + (toneType.endFreqHz - toneType.startFreqHz) * progress
            phase += (2.0 * PI * instFreq) / sampleRateHz.toDouble()

            val envelope = when {
                i < rampSamples -> i.toDouble() / rampSamples.toDouble()
                i > totalSamples - rampSamples -> (totalSamples - i).toDouble() / rampSamples.toDouble()
                else -> 1.0
            }

            // Fundamental + soft 2nd harmonic for warm marimba-like chime
            val rawWave = sin(phase) + 0.22 * sin(2.0 * phase)
            val sampleVal = (rawWave * 0.8f * envelope * amplitudeScale * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            buffer.putShort(sampleVal)
        }
        return buffer.array()
    }

    /**
     * Plays the earcon chime asynchronously if non-verbal chimes are enabled.
     */
    fun playEarcon(toneType: EarconToneType, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        val pcmBytes = synthesizeTonePcm16Le(toneType)
        return runCatching {
            val aecManager = AudioFocusAndAecManager.getInstance(context)
            aecManager.onTtsPlaybackStarted()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcmBytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmBytes, 0, pcmBytes.size)
            track.play()
            track.release()
            aecManager.onTtsPlaybackStopped()
            true
        }.onFailure { e ->
            Log.d(TAG, "Earcon playback skipped in headless/test audio environment: ${e.message}")
            AudioFocusAndAecManager.getInstance(context).onTtsPlaybackStopped()
        }.getOrDefault(false)
    }
}
