package com.kyawgyi.voice

import android.util.Log
import com.example.audio.CentralAudioRecorder
import com.example.data.GeminiFailureClassifier
import com.example.engine.VoiceSessionArbiter
import com.example.engine.VoiceSessionOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps raw 16-bit PCM samples in a canonical 44-byte WAV (RIFF)
 * header. Pure function — the same header layout the dormant
 * GeminiOnlineStrategy built privately (its `addWavHeader`), factored
 * out so the format is unit-testable and shared.
 */
object WavPcm16Encoder {

    fun wrap(pcm: ByteArray, sampleRateHz: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalAudioLen = pcm.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRateHz * channels * bitsPerSample / 8).toLong()
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRateHz and 0xff).toByte()
        header[25] = (sampleRateHz shr 8 and 0xff).toByte()
        header[26] = (sampleRateHz shr 16 and 0xff).toByte()
        header[27] = (sampleRateHz shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val output = ByteArray(44 + pcm.size)
        System.arraycopy(header, 0, output, 0, 44)
        System.arraycopy(pcm, 0, output, 44, pcm.size)
        return output
    }
}

/**
 * Single-owner microphone gate seam for the online engine (FIX-06).
 * The production implementation participates in [VoiceSessionArbiter];
 * tests substitute a counting fake.
 */
interface MicSessionGate {
    fun acquire()
    fun release()
}

/**
 * Production [MicSessionGate]: claims/releases the FIX-06 arbiter
 * under [VoiceSessionOwner.IR_SERVICE] — the same owner
 * [SttEngineManager] claims for its session, so inside a manager
 * session the engine's acquire/release is idempotent, and used
 * standalone the engine still preempts/releases correctly instead of
 * running an unarbitrated capture.
 */
class ArbiterMicSessionGate(
    private val owner: VoiceSessionOwner = VoiceSessionOwner.IR_SERVICE
) : MicSessionGate {
    override fun acquire() {
        VoiceSessionArbiter.requestOwnership(owner)
    }

    override fun release() {
        VoiceSessionArbiter.releaseOwnership(owner)
    }
}

/**
 * GeminiOnlineSttEngine (2026-10-23, §7 plan) — online STT engine
 * implementing the existing [SttEngine] contract (Seam A).
 *
 * Audio path: PCM arrives ONLY via [processAudio] — fed by
 * [SttEngineManager.feedPcmChunk]/`streamAudioBufferPacket`, which are
 * fed by CentralAudioRecorderBridge from CentralAudioRecorder's
 * 16 kHz stream. This engine creates NO AudioRecord of its own.
 * Microphone ownership is held through [MicSessionGate]
 * (FIX-06 [VoiceSessionArbiter]) for the listening session and is
 * released in [stopListening] on BOTH the success and the error path
 * (release happens before/regardless of the async transcription
 * result, so a failed or hung request can never wedge the mic).
 *
 * Utterance boundary: [finishUtterance] (called by the manager on a
 * VAD endpoint, and by [stopListening]) snapshots the buffered PCM,
 * wraps it as WAV, and calls the injected transcription function
 * (production: `GeminiApiService.transcribeAudio`, which runs through
 * the shared key pool + GeminiFailureClassifier). A non-blank
 * transcript is emitted through the SAME transcript listener the
 * system engine uses, so it re-enters the pipeline at
 * `SttEngineManager.onEngineTranscriptReceived` — SmartRouter and the
 * ISSUE-013 negation guard apply unchanged. A blank transcript means
 * "no speech recognized": nothing is emitted and no error is raised.
 * Any failure is reported via [onError] with the classified Burmese
 * message — never swallowed, never replaced by a fake transcript.
 */
class GeminiOnlineSttEngine(
    private val transcribe: suspend (wavBytes: ByteArray, languageTag: String) -> String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val languageTagProvider: () -> String = { DEFAULT_LANGUAGE_TAG },
    private val onError: (message: String) -> Unit = {},
    private val micGate: MicSessionGate = ArbiterMicSessionGate(),
    private val sampleRateHz: Int = CentralAudioRecorder.SAMPLE_RATE
) : SttEngine {

    companion object {
        private const val TAG = "GeminiOnlineSttEngine"
        const val DEFAULT_LANGUAGE_TAG = "my-MM"

        /** ≈150 s of 16 kHz mono PCM16 — one utterance per request stays
         * far below the ~20 MB inline-payload ceiling (§7 risk note). */
        const val MAX_BUFFER_BYTES = 16_000 * 2 * 150
    }

    override val engineType: SttEngineType = SttEngineType.GEMINI_ONLINE

    @Volatile
    override var isRunning: Boolean = false
        private set

    private val bufferLock = Any()
    private val buffer = ByteArrayOutputStream()
    private val bufferTruncated = AtomicBoolean(false)

    @Volatile
    private var transcriptListener: ((String, Boolean) -> Unit)? = null

    private val transcribeMutex = Mutex()

    override fun startListening() {
        micGate.acquire()
        synchronized(bufferLock) { buffer.reset() }
        bufferTruncated.set(false)
        isRunning = true
        Log.i(TAG, "GeminiOnlineSttEngine listening (buffered PCM -> Gemini transcription).")
    }

    override fun stopListening() {
        val wasRunning = isRunning
        isRunning = false
        // Release the microphone FIRST: transcription is network work
        // and must never hold the mic hostage, on success OR failure.
        micGate.release()
        if (wasRunning) {
            finishUtterance()
        }
    }

    override fun processAudio(pcmChunk: ByteArray) {
        if (!isRunning || pcmChunk.isEmpty()) return
        synchronized(bufferLock) {
            if (buffer.size() + pcmChunk.size <= MAX_BUFFER_BYTES) {
                buffer.write(pcmChunk)
            } else if (!bufferTruncated.getAndSet(true)) {
                Log.w(TAG, "Utterance exceeded ${MAX_BUFFER_BYTES} bytes; excess audio dropped.")
            }
        }
    }

    /**
     * Ends the current utterance: snapshots and clears the buffer and
     * transcribes it asynchronously. Returns false when nothing was
     * buffered (silence) — the caller keeps its normal idle handling.
     * The buffer is cleared on BOTH success and failure paths, so a
     * failed utterance is never silently re-sent.
     */
    fun finishUtterance(): Boolean {
        val pcm = synchronized(bufferLock) {
            if (buffer.size() == 0) return false
            val bytes = buffer.toByteArray()
            buffer.reset()
            bytes
        }
        scope.launch {
            transcribeMutex.withLock {
                try {
                    val wav = WavPcm16Encoder.wrap(pcm, sampleRateHz)
                    val transcript = transcribe(wav, languageTagProvider()).trim()
                    if (transcript.isNotBlank()) {
                        transcriptListener?.invoke(transcript, true)
                    } else {
                        Log.d(TAG, "Gemini transcription returned blank (no speech recognized).")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = GeminiFailureClassifier.userMessageForThrowable(e)
                    Log.w(TAG, "Gemini online STT failed: $message")
                    onError(message)
                }
            }
        }
        return true
    }

    override fun setTranscriptListener(listener: (transcript: String, isFinal: Boolean) -> Unit) {
        this.transcriptListener = listener
    }

    override fun release() {
        isRunning = false
        micGate.release()
        transcriptListener = null
        synchronized(bufferLock) { buffer.reset() }
    }
}
