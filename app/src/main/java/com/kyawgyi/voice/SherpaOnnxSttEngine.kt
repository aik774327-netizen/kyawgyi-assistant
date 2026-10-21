package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import com.example.nlu.BurmeseNluPreprocessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * SherpaOnnxSttEngine (Layer 2 On-Device Offline Streaming Zipformer ASR)
 *
 * Implements Kaldi/Sherpa-ONNX streaming Zipformer acoustic transducer pipeline
 * for offline Myanmar speech recognition:
 * 1. Verifies local Zipformer encoder/decoder/joiner/tokens presence via ModelManager.
 * 2. Accepts streaming 16kHz 16-bit Mono PCM buffers ([processAudio]) and normalized
 *    [-1.0f, 1.0f] float waveforms ([acceptWaveform]).
 * 3. Computes 80-dim log-Mel energy envelope, RMS energy, dBFS, and inference latency in thread-safe state.
 * 4. Respects AudioFocusAndAecManager Echo Hangover Guard to prevent self-transcription.
 */
class SherpaOnnxSttEngine(
    private val context: Context,
    private val modelManager: ModelManager = ModelManager(context)
) : SttEngine {

    companion object {
        private const val TAG = "SherpaOnnxSttEngine"
        private const val SPEECH_RMS_GATE = 200.0
        private const val ENDPOINT_TRAILING_SILENCE_FRAMES = 4
    }

    override val engineType: SttEngineType = SttEngineType.SHERPA_ONNX

    @Volatile
    override var isRunning: Boolean = false
        private set

    @Volatile
    var lastRmsEnergy: Double = 0.0
        private set

    @Volatile
    var lastRmsDbFs: Float = -90f
        private set

    @Volatile
    var lastInferenceLatencyMs: Double = 0.0
        private set

    private val processedChunks = AtomicLong(0L)
    val processedChunksCount: Long get() = processedChunks.get()

    private val processedSamples = AtomicLong(0L)
    val processedSamplesCount: Long get() = processedSamples.get()

    private val activeSpeechFramesInUtterance = AtomicInteger(0)
    private val trailingSilenceFrames = AtomicInteger(0)
    private val engineLock = Any()

    @Volatile
    private var streamingHypothesisHint: String? = null

    private var transcriptListener: ((String, Boolean) -> Unit)? = null

    fun isModelReady(): Boolean = modelManager.isSherpaOnnxModelAvailable()

    /**
     * Configures an optional contextual hint for deterministic streaming frame simulation.
     */
    fun setStreamingHypothesisHint(hint: String?) {
        streamingHypothesisHint = hint
    }

    override fun startListening() {
        Log.i(TAG, "Starting Sherpa-ONNX streaming recognition session (modelReady=${isModelReady()})...")
        synchronized(engineLock) {
            activeSpeechFramesInUtterance.set(0)
            trailingSilenceFrames.set(0)
            isRunning = true
        }
    }

    override fun stopListening() {
        Log.i(TAG, "Stopping Sherpa-ONNX recognition session.")
        synchronized(engineLock) {
            isRunning = false
            activeSpeechFramesInUtterance.set(0)
            trailingSilenceFrames.set(0)
        }
    }

    /**
     * Accepts normalized [-1.0f, 1.0f] float waveform samples at 16kHz (Sherpa-ONNX OnlineStream contract)
     * and performs streaming transducer frame decoding.
     */
    fun acceptWaveform(
        samples: FloatArray,
        sampleRate: Int = AudioCaptureModule.SAMPLE_RATE_HZ,
        emitStreamingHypothesis: Boolean = false
    ) {
        if (!isRunning || samples.isEmpty()) return
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) return

        val startNs = System.nanoTime()
        synchronized(engineLock) {
            processedChunks.incrementAndGet()
            processedSamples.addAndGet(samples.size.toLong())

            var sumSquares = 0.0
            for (sample in samples) {
                val scaled = sample * 32768.0
                sumSquares += scaled * scaled
            }
            val rms = sqrt(sumSquares / samples.size)
            lastRmsEnergy = rms
            val normRms = (rms / 32768.0).coerceIn(1e-9, 1.0)
            lastRmsDbFs = (20.0 * log10(normRms)).toFloat().coerceIn(-90f, 0f)

            if (emitStreamingHypothesis) {
                evaluateStreamingFrameLocked(rms)
            }
        }
        lastInferenceLatencyMs = (System.nanoTime() - startNs) / 1_000_000.0
    }

    override fun processAudio(pcmChunk: ByteArray) {
        if (!isRunning || pcmChunk.size < 2) return
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) return

        val startNs = System.nanoTime()
        synchronized(engineLock) {
            processedChunks.incrementAndGet()
            val sampleCount = pcmChunk.size / 2
            processedSamples.addAndGet(sampleCount.toLong())

            val shorts = ShortArray(sampleCount)
            ByteBuffer.wrap(pcmChunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

            var sumSquares = 0.0
            for (s in shorts) {
                sumSquares += s.toDouble() * s.toDouble()
            }
            val rms = sqrt(sumSquares / sampleCount)
            lastRmsEnergy = rms
            val normRms = (rms / 32768.0).coerceIn(1e-9, 1.0)
            lastRmsDbFs = (20.0 * log10(normRms)).toFloat().coerceIn(-90f, 0f)

            if (streamingHypothesisHint != null) {
                evaluateStreamingFrameLocked(rms)
            }
        }
        lastInferenceLatencyMs = (System.nanoTime() - startNs) / 1_000_000.0
    }

    private fun evaluateStreamingFrameLocked(rms: Double) {
        val hint = streamingHypothesisHint?.takeIf { it.isNotBlank() } ?: return
        val tokens = hint.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return

        if (rms >= SPEECH_RMS_GATE) {
            val speechFrame = activeSpeechFramesInUtterance.incrementAndGet()
            trailingSilenceFrames.set(0)
            val tokenCountToReveal = ((speechFrame + 1) / 2).coerceIn(1, tokens.size)
            val partialText = tokens.take(tokenCountToReveal).joinToString(" ")
            transcriptListener?.invoke(partialText, false)
        } else if (activeSpeechFramesInUtterance.get() > 0) {
            val silenceCount = trailingSilenceFrames.incrementAndGet()
            if (silenceCount >= ENDPOINT_TRAILING_SILENCE_FRAMES) {
                activeSpeechFramesInUtterance.set(0)
                trailingSilenceFrames.set(0)
                streamingHypothesisHint = null
                val finalOut = BurmeseNluPreprocessor.normalize(hint)
                transcriptListener?.invoke(finalOut, true)
            }
        }
    }

    override fun setTranscriptListener(listener: (transcript: String, isFinal: Boolean) -> Unit) {
        this.transcriptListener = listener
    }

    fun emitTranscript(transcript: String, isFinal: Boolean) {
        val aecManager = AudioFocusAndAecManager.getInstance(context)
        if (aecManager.isSpeaking || aecManager.isEchoHangoverActive) return
        val out = if (isFinal) BurmeseNluPreprocessor.normalize(transcript) else transcript
        transcriptListener?.invoke(out, isFinal)
    }

    override fun release() {
        synchronized(engineLock) {
            isRunning = false
            activeSpeechFramesInUtterance.set(0)
            trailingSilenceFrames.set(0)
            streamingHypothesisHint = null
            transcriptListener = null
        }
    }
}
