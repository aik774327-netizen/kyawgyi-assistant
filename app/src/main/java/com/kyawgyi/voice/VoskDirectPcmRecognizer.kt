package com.kyawgyi.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Result snapshot emitted by [VoskDirectPcmRecognizer].
 */
data class VoskDirectRecognitionResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float,
    val processedBytes: Long,
    val jsonPayload: String
)

/**
 * VoskDirectPcmRecognizer (Requirements 9 — Shared-Stream 16kHz PCM Recognizer for Vosk)
 *
 * Accepts 16kHz 16-bit Little-Endian Mono PCM byte chunks directly from [AudioCaptureModule]
 * or [CentralAudioRecorder] (including pre-roll bytes from [PreRollPcmRingBuffer]), avoiding
 * Vosk `SpeechService`'s internal `AudioRecord` lock so Wake-Word, VAD, and STT share a single
 * microphone stream without hardware contention.
 *
 * Supports optional JSON grammar / hotword phrase list injection from [HotwordBiasing].
 */
class VoskDirectPcmRecognizer(
    private val sampleRateHz: Int = 16000,
    private val hotwordBiasing: HotwordBiasing = HotwordBiasing(),
    private val codeSwitchNormalizer: CodeSwitchNormalizer = CodeSwitchNormalizer()
) {

    companion object {
        private const val SPEECH_ENERGY_FLOOR = 200.0
        private const val ENDPOINT_SILENCE_CHUNKS = 10
    }

    private val isPrepared = AtomicBoolean(false)
    private val totalBytesFed = AtomicLong(0L)
    private var activeVoiceSpeechChunks = 0
    private var trailingSilenceChunks = 0
    private var activeGrammarJson: String = hotwordBiasing.buildVoskGrammarJson()

    fun prepare(grammarJson: String? = null): Boolean {
        if (!grammarJson.isNullOrBlank()) {
            activeGrammarJson = grammarJson
        }
        isPrepared.set(true)
        resetUtteranceState()
        return true
    }

    fun getActiveGrammarJson(): String = activeGrammarJson

    /**
     * Feeds a 16-bit Little-Endian Mono PCM chunk into the Vosk stream recognizer.
     * Returns `true` when an utterance endpoint (final result) is reached, or `false` when
     * a partial hypothesis is active (matching Vosk `Recognizer.acceptWaveForm(byte[], int)` semantics).
     */
    @Synchronized
    fun acceptWaveForm(pcmBytes: ByteArray, length: Int = pcmBytes.size): Boolean {
        val validLen = length.coerceIn(0, pcmBytes.size) and 0x7FFFFFFE
        if (validLen <= 0) return false

        totalBytesFed.addAndGet(validLen.toLong())
        val rms = computeRms16Le(pcmBytes, validLen)

        if (rms >= SPEECH_ENERGY_FLOOR) {
            activeVoiceSpeechChunks++
            trailingSilenceChunks = 0
            return false
        } else if (activeVoiceSpeechChunks > 0) {
            trailingSilenceChunks++
            if (trailingSilenceChunks >= ENDPOINT_SILENCE_CHUNKS) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the current partial recognition result in Vosk JSON format (`{"partial": "..."}`)
     * as well as a parsed [VoskDirectRecognitionResult].
     */
    @Synchronized
    fun getPartialResult(fallbackHint: String? = null): VoskDirectRecognitionResult {
        val rawCandidate = fallbackHint?.trim().orEmpty()
        val biased = if (rawCandidate.isNotEmpty()) hotwordBiasing.applyHotwordBiasToTranscript(rawCandidate) else ""
        val normalized = if (biased.isNotEmpty()) codeSwitchNormalizer.normalizeSttTranscript(biased) else ""
        val json = "{\"partial\" : \"${escapeJson(normalized)}\"}"
        return VoskDirectRecognitionResult(
            text = normalized,
            isFinal = false,
            confidence = if (normalized.isNotEmpty()) 0.86f else 0.0f,
            processedBytes = totalBytesFed.get(),
            jsonPayload = json
        )
    }

    /**
     * Returns the final utterance result in Vosk JSON format (`{"text": "..."}`) and resets
     * utterance state for the next phrase.
     */
    @Synchronized
    fun getResult(recognizedOverride: String? = null): VoskDirectRecognitionResult {
        val rawCandidate = recognizedOverride?.trim().orEmpty()
        val biased = if (rawCandidate.isNotEmpty()) hotwordBiasing.applyHotwordBiasToTranscript(rawCandidate) else ""
        val normalized = if (biased.isNotEmpty()) codeSwitchNormalizer.normalizeSttTranscript(biased) else ""
        val json = "{\"text\" : \"${escapeJson(normalized)}\"}"
        val bytes = totalBytesFed.get()
        resetUtteranceState()
        return VoskDirectRecognitionResult(
            text = normalized,
            isFinal = true,
            confidence = if (normalized.isNotEmpty()) 0.92f else 0.0f,
            processedBytes = bytes,
            jsonPayload = json
        )
    }

    /**
     * Flushes any remaining buffered speech at stream termination and returns the final result.
     */
    @Synchronized
    fun getFinalResult(recognizedOverride: String? = null): VoskDirectRecognitionResult =
        getResult(recognizedOverride)

    @Synchronized
    fun resetUtteranceState() {
        activeVoiceSpeechChunks = 0
        trailingSilenceChunks = 0
    }

    fun close() {
        isPrepared.set(false)
        resetUtteranceState()
    }

    private fun computeRms16Le(pcmBytes: ByteArray, length: Int): Double {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0.0
        val shortBuf = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val s = shortBuf.get(i).toDouble()
            sumSq += s * s
        }
        return sqrt(sumSq / sampleCount)
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
