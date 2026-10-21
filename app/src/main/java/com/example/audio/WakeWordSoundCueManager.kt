package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * WakeWordSoundCueManager
 *
 * Provides subtle, non-intrusive audio earcon cues using direct in-memory PCM AudioTrack
 * synthesis to notify the user whenever the 'ကျော်ကြီး' wake word is detected.
 *
 * Architectural Note (Error-Learning Fix):
 * - Avoids Android's `SoundPool.load(wavFile)` which invokes in-process `MediaCodec` / `CCodec`
 *   (`c2.android.raw.decoder`) and triggers native Codec2 errors on emulators/devices:
 *   `E/io.aiangel.core: Failed to query component interface for required system resources: 6`.
 * - Uses pre-synthesized 16-bit PCM harmonic chime played directly through `AudioTrack`
 *   (`MODE_STATIC`, `USAGE_ASSISTANCE_SONIFICATION`), bypassing `CCodec` completely with zero latency.
 */
class WakeWordSoundCueManager private constructor(
    @Suppress("UNUSED_PARAMETER") context: Context
) {

    companion object {
        private const val TAG = "WakeWordSoundCue"
        private const val SAMPLE_RATE = 44100
        private const val TOTAL_DURATION_MS = 180

        @Volatile
        private var instance: WakeWordSoundCueManager? = null

        fun getInstance(context: Context): WakeWordSoundCueManager {
            return instance ?: synchronized(this) {
                instance ?: WakeWordSoundCueManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pre-computed unit-amplitude harmonic 2-tone waveform (D5 587.33Hz -> A5 880.00Hz)
    private val baseWaveform: FloatArray by lazy {
        buildBaseHarmonicWaveform()
    }

    /**
     * Plays a subtle, non-intrusive harmonic earcon chime via direct PCM AudioTrack
     * to indicate that 'ကျော်ကြီး' has been heard.
     */
    fun playWakeWordDetectedCue(volume: Float = 0.40f) {
        val clampedVolume = volume.coerceIn(0.1f, 0.7f)
        playDirectPcmChime(clampedVolume)
    }

    private fun playDirectPcmChime(volume: Float) {
        scope.launch {
            var audioTrack: AudioTrack? = null
            try {
                val samples = baseWaveform
                val pcmBuffer = ShortArray(samples.size)
                for (i in samples.indices) {
                    val scaled = (samples[i] * volume * Short.MAX_VALUE).toInt()
                    pcmBuffer[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val byteSize = pcmBuffer.size * 2
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(byteSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                audioTrack.play()
                Log.i(TAG, "🔔 Direct PCM AudioTrack cue played for 'ကျော်ကြီး' wake word (vol: $volume).")

                delay(TOTAL_DURATION_MS.toLong() + 50L)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack wake word cue playback skipped: ${e.message}")
            } finally {
                try {
                    audioTrack?.stop()
                } catch (_: Throwable) {}
                try {
                    audioTrack?.release()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Generates a normalized [-1.0f, 1.0f] 2-tone harmonic chime buffer in memory:
     * - Tone 1: 587.33 Hz (D5) for first 45%
     * - Tone 2: 880.00 Hz (A5) for remaining 55%
     * Smooth sine window envelope eliminates clicks/pops without needing WAV decoding.
     */
    private fun buildBaseHarmonicWaveform(): FloatArray {
        val totalSamples = (SAMPLE_RATE * TOTAL_DURATION_MS) / 1000
        val out = FloatArray(totalSamples)
        val part1Samples = (totalSamples * 0.45).toInt()
        val part2Samples = totalSamples - part1Samples

        for (i in 0 until part1Samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = sin((i.toDouble() / part1Samples) * PI)
            out[i] = (sin(2.0 * PI * 587.33 * t) * env).toFloat()
        }

        for (i in 0 until part2Samples) {
            val idx = part1Samples + i
            val t = i.toDouble() / SAMPLE_RATE
            val env = sin((i.toDouble() / part2Samples) * PI)
            out[idx] = (sin(2.0 * PI * 880.00 * t) * 0.9 * env).toFloat()
        }

        return out
    }

    fun release() {
        // No persistent native SoundPool / MediaCodec resources held
    }
}
