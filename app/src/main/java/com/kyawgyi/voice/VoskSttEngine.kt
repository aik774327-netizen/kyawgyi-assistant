package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.nlu.BurmeseNluPreprocessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * VoskSttEngine (Layer 2 Kaldi-based Offline ASR Engine)
 *
 * Secondary offline ASR fallback engine for Myanmar speech recognition:
 * 1. Verifies Vosk model directory via ModelManager.
 * 2. Computes 16kHz 16-bit Mono PCM RMS energy and tracks processed frames.
 * 3. Respects AudioFocusAndAecManager Echo Hangover Guard.
 */
class VoskSttEngine(
    private val context: Context,
    private val modelManager: ModelManager = ModelManager(context),
    val hotwordBiasing: HotwordBiasing = HotwordBiasing(),
    val codeSwitchNormalizer: CodeSwitchNormalizer = CodeSwitchNormalizer(),
    val directPcmRecognizer: VoskDirectPcmRecognizer = VoskDirectPcmRecognizer(
        sampleRateHz = 16000,
        hotwordBiasing = hotwordBiasing,
        codeSwitchNormalizer = codeSwitchNormalizer
    )
) : SttEngine {

    companion object {
        private const val TAG = "VoskSttEngine"
    }

    override val engineType: SttEngineType = SttEngineType.VOSK

    @Volatile
    override var isRunning: Boolean = false
        private set

    @Volatile
    var lastRmsEnergy: Double = 0.0
        private set

    private val processedChunks = AtomicLong(0L)
    val processedChunksCount: Long get() = processedChunks.get()

    private var transcriptListener: ((String, Boolean) -> Unit)? = null

    fun isModelReady(): Boolean = modelManager.isVoskModelAvailable()

    override fun startListening() {
        Log.i(TAG, "Starting Vosk recognition session (modelReady=${isModelReady()})...")
        directPcmRecognizer.prepare(hotwordBiasing.buildVoskGrammarJson())
        isRunning = true
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping Vosk recognition session.")
        isRunning = false
    }

    override fun processAudio(pcmChunk: ByteArray) {
        if (!isRunning || pcmChunk.size < 2) return
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) return

        processedChunks.incrementAndGet()
        val sampleCount = pcmChunk.size / 2
        val shorts = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmChunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        var sumSquares = 0.0
        for (s in shorts) {
            sumSquares += s.toDouble() * s.toDouble()
        }
        lastRmsEnergy = sqrt(sumSquares / sampleCount)

        val reachedEndpoint = directPcmRecognizer.acceptWaveForm(pcmChunk, pcmChunk.size)
        if (reachedEndpoint) {
            val finalRes = directPcmRecognizer.getResult()
            if (finalRes.text.isNotBlank()) {
                emitTranscript(finalRes.text, isFinal = true)
            }
        }
    }

    override fun setTranscriptListener(listener: (transcript: String, isFinal: Boolean) -> Unit) {
        this.transcriptListener = listener
    }

    fun emitTranscript(transcript: String, isFinal: Boolean) {
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) return
        val biased = hotwordBiasing.applyHotwordBiasToTranscript(transcript)
        val out = if (isFinal) {
            codeSwitchNormalizer.normalizeSttTranscript(biased)
        } else {
            BurmeseNluPreprocessor.normalize(biased)
        }
        transcriptListener?.invoke(out, isFinal)
    }

    override fun release() {
        isRunning = false
        directPcmRecognizer.close()
        transcriptListener = null
    }
}
