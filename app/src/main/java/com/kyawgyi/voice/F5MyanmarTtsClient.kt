package com.kyawgyi.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.utils.MyanmarTTSEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * F5MyanmarTtsClient (Layer 2 High-Fidelity Neural TTS Client)
 *
 * Provides streaming playback for 24kHz/16kHz 16-bit Mono WAV audio synthesized via F5-TTS
 * server (`WavPcm16.parse`) with automatic graceful fallback to `TtsEngine` / `MyanmarTTSEngine`.
 */
open class F5MyanmarTtsClient(
    private val endpoint: String,
    private val fallback: TtsEngine,
    private val audioFocus: AudioFocusAndAecManager? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
) : TtsEngine {

    private var appContext: Context? = null

    constructor(
        context: Context,
        audioFocusManager: AudioFocusAndAecManager = AudioFocusAndAecManager.getInstance(context),
        endpointUrl: String = ""
    ) : this(
        endpoint = endpointUrl,
        fallback = AndroidFallbackTts(context.applicationContext),
        audioFocus = audioFocusManager
    ) {
        this.appContext = context.applicationContext
    }

    companion object {
        private const val TAG = "F5MyanmarTtsClient"
    }

    private val lock = Any()
    private val isPlayingState = AtomicBoolean(false)
    val isPlaying: Boolean get() = isPlayingState.get()

    private var activeTrack: AudioTrack? = null
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun speak(text: String) {
        require(text.isNotBlank())
        stop()
        val normalizedSpeechText = com.example.nlu.BurmeseTtsTextNormalizer.normalizeForTts(text)
        if (endpoint.isBlank() || !endpoint.startsWith("http")) {
            speakLocalFallback(normalizedSpeechText)
            return
        }
        try {
            val audio = withContext(Dispatchers.IO) {
                ensureActive()
                val request = Request.Builder()
                    .url(endpoint)
                    .post(JSONObject().put("text", normalizedSpeechText).toString().toRequestBody(jsonType))
                    .header("Accept", "audio/wav")
                    .build()
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "F5 HTTP ${response.code}" }
                    WavPcm16.parse(response.body?.bytes() ?: error("Empty F5 response"))
                }
            }
            play(audio)
        } catch (e: SocketTimeoutException) {
            fallback.speak(normalizedSpeechText)
        } catch (e: IOException) {
            fallback.speak(normalizedSpeechText)
        } catch (e: kotlinx.coroutines.CancellationException) {
            stop()
            throw e
        } catch (e: Throwable) {
            fallback.speak(normalizedSpeechText)
        }
    }

    /**
     * Synthesizes and plays the Myanmar speech text (backward-compatible callback API).
     */
    open suspend fun synthesizeAndPlay(
        text: String,
        onPlaybackFinished: (() -> Unit)? = null
    ): Unit = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            onPlaybackFinished?.invoke()
            return@withContext
        }
        try {
            val normalizedSpeechText = com.example.nlu.BurmeseTtsTextNormalizer.normalizeForTts(text)
            Log.i(TAG, "Initiating F5 Myanmar TTS synthesis for: \"$normalizedSpeechText\" (raw: \"$text\")")
            audioFocus?.onTtsPlaybackStarted()
            isPlayingState.set(true)

            val ctx = appContext
            if (ctx != null && (endpoint.isBlank() || !endpoint.startsWith("http"))) {
                MyanmarTTSEngine.getInstance(ctx).speak(normalizedSpeechText) {
                    stopPlayback()
                    onPlaybackFinished?.invoke()
                }
            } else {
                speak(normalizedSpeechText)
                stopPlayback()
                onPlaybackFinished?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "F5 Myanmar TTS playback error: ${e.message}", e)
            stopPlayback()
            onPlaybackFinished?.invoke()
        }
    }

    private suspend fun speakLocalFallback(normalizedSpeechText: String) {
        val ctx = appContext
        if (ctx != null) {
            kotlinx.coroutines.withTimeoutOrNull(1500L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    MyanmarTTSEngine.getInstance(ctx).speak(normalizedSpeechText) {
                        if (cont.isActive) cont.resume(Unit) {}
                    }
                }
            }
        } else {
            fallback.speak(normalizedSpeechText)
        }
    }

    private suspend fun play(audio: WavPcm16) = withContext(Dispatchers.IO) {
        val min = AudioTrack.getMinBufferSize(audio.rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(min > 0) { "AudioTrack does not support ${audio.rate} Hz" }
        check(audioFocus?.beginTts() != false) { "Audio focus unavailable" }
        isPlayingState.set(true)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(audio.rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, audio.pcm.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        synchronized(lock) { activeTrack = track }
        try {
            track.play()
            var offset = 0
            while (offset < audio.pcm.size) {
                val written = track.write(audio.pcm, offset, audio.pcm.size - offset)
                check(written > 0) { "AudioTrack write failed: $written" }
                offset += written
            }
            track.stop()
        } finally {
            isPlayingState.set(false)
            synchronized(lock) { if (activeTrack === track) activeTrack = null }
            runCatching { track.release() }
            audioFocus?.endTts()
        }
    }

    fun stopPlayback() {
        stop()
        if (isPlayingState.getAndSet(false)) {
            audioFocus?.onTtsPlaybackStopped()
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

    override fun close() {
        stopPlayback()
        fallback.close()
        client.dispatcher.executorService.shutdown()
    }
}
