package com.kyawgyi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.data.GeminiApiService
import com.example.data.GeminiEmptyResponseException
import com.example.data.GeminiFailureClassifier
import com.example.nlu.BurmeseTtsTextNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A Gemini online TTS attempt failed; [message] is the already-classified, user-safe Burmese reason. */
class GeminiTtsException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * PCM playback seam for [GeminiTtsEngine]. Production uses
 * [AudioTrackPcmPlayer]; unit tests substitute a recording fake so
 * playback parameters (sample rate, exact bytes) are verifiable
 * without a device.
 */
interface PcmPlayer {
    suspend fun play(pcm: ByteArray, sampleRateHz: Int)
    fun stop()
}

/**
 * Plays raw PCM16 through AudioTrack — the playback pattern proven
 * in [F5MyanmarTtsClient.play] (AudioFocus/AEC begin/end around the
 * write loop, USAGE_ASSISTANCE_ACCESSIBILITY, MODE_STREAM), minus
 * the WAV parsing: Gemini returns headerless L16 PCM at a fixed rate.
 */
class AudioTrackPcmPlayer(
    context: Context,
    private val audioFocus: AudioFocusAndAecManager? = AudioFocusAndAecManager.getInstance(context)
) : PcmPlayer {

    private val lock = Any()
    private var activeTrack: AudioTrack? = null

    override suspend fun play(pcm: ByteArray, sampleRateHz: Int) = withContext(Dispatchers.IO) {
        val min = AudioTrack.getMinBufferSize(
            sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(min > 0) { "AudioTrack does not support $sampleRateHz Hz" }
        check(audioFocus?.beginTts() != false) { "Audio focus unavailable" }

        // Raw L16 bytes -> samples (a trailing odd byte is not a sample).
        val shorts = ShortArray(pcm.size / 2)
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, shorts.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        synchronized(lock) { activeTrack = track }
        try {
            track.play()
            var offset = 0
            while (offset < shorts.size) {
                val written = track.write(shorts, offset, shorts.size - offset)
                check(written > 0) { "AudioTrack write failed: $written" }
                offset += written
            }
            track.stop()
        } finally {
            synchronized(lock) { if (activeTrack === track) activeTrack = null }
            runCatching { track.release() }
            audioFocus?.endTts()
        }
    }

    override fun stop() {
        synchronized(lock) {
            activeTrack?.let {
                runCatching {
                    it.pause()
                    it.flush()
                    it.release()
                }
                activeTrack = null
            }
        }
    }
}

/**
 * GeminiTtsEngine (2026-10-23, §7 plan) — online TTS implementing
 * the existing [TtsEngine] contract beside [AndroidFallbackTts] /
 * [F5MyanmarTtsClient].
 *
 * Flow: normalize text (same [BurmeseTtsTextNormalizer] prep the F5
 * client applies, §7.2.4 — injectable because MyanmarTTSEngine's
 * speak path passes already-preprocessed text) → synthesize via the
 * injected function (production: `GeminiApiService.synthesizeSpeech`,
 * shared key pool + classifier) → play the returned raw PCM at
 * [GeminiApiService.TTS_OUTPUT_SAMPLE_RATE_HZ] (24 kHz).
 *
 * Honesty rules: a synthesis failure is classified with
 * [GeminiFailureClassifier] and ALWAYS surfaced via [onError] before
 * any fallback runs; with a [fallback] the utterance is then spoken
 * by the fallback, otherwise [GeminiTtsException] is thrown. A
 * playback failure after successful synthesis is reported as a
 * playback failure (not mislabeled a connection failure) and follows
 * the same fallback rule. This engine never reports success for an
 * utterance it did not hand to the player or the fallback.
 */
class GeminiTtsEngine(
    private val synthesize: suspend (text: String, voiceName: String) -> ByteArray,
    private val player: PcmPlayer,
    private val fallback: TtsEngine? = null,
    private val onError: (message: String) -> Unit = {},
    private val voiceName: String = GeminiApiService.DEFAULT_TTS_VOICE_NAME,
    private val sampleRateHz: Int = GeminiApiService.TTS_OUTPUT_SAMPLE_RATE_HZ,
    private val textNormalizer: (String) -> String = { BurmeseTtsTextNormalizer.normalizeForTts(it) }
) : TtsEngine {

    companion object {
        private const val TAG = "GeminiTtsEngine"
    }

    override suspend fun speak(text: String) {
        require(text.isNotBlank()) { "GeminiTtsEngine.speak requires non-blank text" }
        val prepared = textNormalizer(text)

        val pcm = try {
            synthesize(prepared, voiceName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failHonestly(prepared, GeminiFailureClassifier.userMessageForThrowable(e), e)
            return
        }
        if (pcm.isEmpty()) {
            val empty = GeminiEmptyResponseException("Gemini speech synthesis returned zero audio bytes")
            failHonestly(prepared, GeminiFailureClassifier.userMessageForThrowable(empty), empty)
            return
        }

        try {
            player.play(pcm, sampleRateHz)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Gemini မှ အသံရရှိခဲ့သော်လည်း ဤစက်တွင် ဖွင့်၍ မရပါ (${e.javaClass.simpleName})"
            Log.w(TAG, "Gemini TTS playback failed: $message")
            onError(message)
            if (fallback != null) {
                fallback.speak(prepared)
                return
            }
            throw GeminiTtsException(message, e)
        }
    }

    private suspend fun failHonestly(preparedText: String, message: String, cause: Exception) {
        Log.w(TAG, "Gemini online TTS failed: $message")
        onError(message)
        if (fallback != null) {
            fallback.speak(preparedText)
            return
        }
        throw GeminiTtsException(message, cause)
    }

    override fun stop() {
        player.stop()
    }

    override fun close() {
        stop()
        fallback?.close()
    }
}
