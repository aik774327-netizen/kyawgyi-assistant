package com.kyawgyi.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

interface TtsEngine {
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

/** Google/default engine -> Samsung engine -> chime fallback. */
class MultiTierSystemTts(
    context: Context,
    private val preferences: VoicePreferences,
    private val chime: suspend () -> Unit,
) : TtsEngine {
    private val primary = TextToSpeech(context) {}
    private val secondary = TextToSpeech(context, {}, "com.samsung.SMT")

    override suspend fun speak(text: String) {
        val result = trySpeak(primary, text)
        if (result) return
        if (trySpeak(secondary, text)) return
        chime()
    }

    private suspend fun trySpeak(engine: TextToSpeech, text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val id = "kyawgyi-${System.nanoTime()}"
            engine.setSpeechRate(preferences.speechRate.value)
            engine.setPitch(preferences.pitch.value)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(true) }
                override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(false) }
            })
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (result == TextToSpeech.ERROR && cont.isActive) cont.resume(false)
            cont.invokeOnCancellation { engine.stop() }
        }

    override fun stop() {
        primary.stop()
        secondary.stop()
    }

    override fun close() {
        primary.shutdown()
        secondary.shutdown()
    }
}

class AndroidFallbackTts(private val context: Context) : TtsEngine {
    private val tts = TextToSpeech(context) {}
    override suspend fun speak(text: String) = suspendCancellableCoroutine<Unit> { c ->
        val id = "kyawgyi-${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { if (c.isActive) c.resume(Unit) }
            override fun onError(id: String?) { if (c.isActive) c.resume(Unit) }
        })
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR && c.isActive) c.resume(Unit)
        c.invokeOnCancellation { tts.stop() }
    }

    override fun stop() {
        tts.stop()
    }

    override fun close() {
        tts.shutdown()
    }
}

data class WavPcm16(val rate: Int, val pcm: ShortArray) {
    companion object {
        fun parse(b: ByteArray): WavPcm16 {
            require(b.size >= 44 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WAVE")
            var p = 12
            var format = -1
            var channels = 0
            var rate = 0
            var bits = 0
            var start = -1
            var size = 0
            while (p + 8 <= b.size) {
                val id = String(b, p, 4)
                val n = i32(b, p + 4)
                require(n >= 0 && p + 8 + n <= b.size)
                if (id == "fmt ") {
                    format = u16(b, p + 8)
                    channels = u16(b, p + 10)
                    rate = i32(b, p + 12)
                    bits = u16(b, p + 22)
                }
                if (id == "data") {
                    start = p + 8
                    size = n
                    break
                }
                p += 8 + n + (n and 1)
            }
            require(format == 1 && channels == 1 && bits == 16 && start >= 0 && size % 2 == 0) {
                "Expected mono PCM16 WAV"
            }
            val out = ShortArray(size / 2)
            val bb = ByteBuffer.wrap(b, start, size).order(ByteOrder.LITTLE_ENDIAN)
            for (i in out.indices) out[i] = bb.short
            return WavPcm16(rate, out)
        }

        private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
        private fun i32(b: ByteArray, p: Int) = u16(b, p) or (u16(b, p + 2) shl 16)
    }
}
