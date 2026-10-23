package com.kyawgyi.voice

import com.example.data.GeminiFailureClassifier
import com.example.data.GeminiFailureClass
import com.example.data.GeminiHttpException
import com.example.engine.VoiceSessionArbiter
import com.example.engine.VoiceSessionOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * §7 Part B regression tests (t18_b_01..06) — the Gemini online STT
 * engine in isolation, with a FAKE transcription function (no network,
 * no key). Pins: WAV packing correctness, transcript delivery through
 * the SttEngine listener seam, honest failure surfacing with buffer
 * clearing, blank-transcript = no-match (no emission, no error), and
 * FIX-06 arbiter acquire/release on BOTH success and error paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiOnlineSttEngineTest {

    private class CountingGate : MicSessionGate {
        var acquires = 0
        var releases = 0
        override fun acquire() { acquires++ }
        override fun release() { releases++ }
    }

    @Before
    fun setUp() {
        VoiceSessionArbiter.resetForTesting()
    }

    private fun awaitTrue(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    // t18_b_01: WAV packing — canonical 44-byte header, payload verbatim.
    @Test
    fun t18_b_01_wavPackingIsCanonical() {
        val pcm = ByteArray(320) { (it % 251).toByte() }
        val wav = WavPcm16Encoder.wrap(pcm, sampleRateHz = 16_000)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + pcm.size, le32(wav, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals(16, le32(wav, 16))
        assertEquals(1, le16(wav, 20)) // PCM format
        assertEquals(1, le16(wav, 22)) // mono
        assertEquals(16_000, le32(wav, 24))
        assertEquals(32_000, le32(wav, 28)) // byte rate
        assertEquals(16, le16(wav, 34)) // bits per sample
        assertEquals("data", String(wav, 36, 4))
        assertEquals(pcm.size, le32(wav, 40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    // t18_b_02: buffered PCM is WAV-wrapped and the transcript is
    // delivered through the engine listener as a final transcript.
    @Test
    fun t18_b_02_transcriptDeliveredThroughListener() {
        val capturedWav = AtomicReference<ByteArray>()
        val engine = GeminiOnlineSttEngine(
            transcribe = { wav, lang ->
                capturedWav.set(wav)
                assertEquals("my-MM", lang)
                "မင်္ဂလာပါ"
            },
            scope = CoroutineScope(Dispatchers.Default),
            micGate = CountingGate()
        )
        val received = AtomicReference<Pair<String, Boolean>>()
        val latch = CountDownLatch(1)
        engine.setTranscriptListener { text, isFinal ->
            received.set(text to isFinal)
            latch.countDown()
        }
        val chunk1 = ByteArray(640) { 1 }
        val chunk2 = ByteArray(640) { 2 }
        engine.startListening()
        engine.processAudio(chunk1)
        engine.processAudio(chunk2)
        assertTrue(engine.finishUtterance())
        assertTrue("transcript must arrive", latch.await(5, TimeUnit.SECONDS))
        assertEquals("မင်္ဂလာပါ" to true, received.get())
        val wav = capturedWav.get()
        assertEquals("RIFF", String(wav, 0, 4))
        assertArrayEquals(chunk1 + chunk2, wav.copyOfRange(44, wav.size))
    }

    // t18_b_03: a classified failure surfaces via onError with the
    // classifier's message; NOTHING is emitted as a transcript and
    // the buffer is cleared (no silent re-send).
    @Test
    fun t18_b_03_failureSurfacesAndBufferClears() {
        val error = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val engine = GeminiOnlineSttEngine(
            transcribe = { _, _ -> throw GeminiHttpException(403) },
            scope = CoroutineScope(Dispatchers.Default),
            onError = { msg -> error.set(msg); latch.countDown() },
            micGate = CountingGate()
        )
        var transcriptEmitted = false
        engine.setTranscriptListener { _, _ -> transcriptEmitted = true }
        engine.startListening()
        engine.processAudio(ByteArray(640) { 3 })
        assertTrue(engine.finishUtterance())
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.AUTH, 403),
            error.get()
        )
        assertFalse(transcriptEmitted)
        assertFalse("buffer must be cleared after a failed utterance", engine.finishUtterance())
    }

    // t18_b_04: FIX-06 arbiter — the production gate acquires on
    // start and releases on stop, on the success path AND the error
    // path (a failed transcription never wedges the microphone).
    @Test
    fun t18_b_04_arbiterAcquireReleaseOnSuccessAndError() {
        // Success path
        val okEngine = GeminiOnlineSttEngine(
            transcribe = { _, _ -> "ဟုတ်ကဲ့" },
            scope = CoroutineScope(Dispatchers.Default)
        )
        val okLatch = CountDownLatch(1)
        okEngine.setTranscriptListener { _, _ -> okLatch.countDown() }
        okEngine.startListening()
        assertEquals(VoiceSessionOwner.IR_SERVICE, VoiceSessionArbiter.currentOwner())
        okEngine.processAudio(ByteArray(320) { 1 })
        okEngine.stopListening()
        assertEquals(VoiceSessionOwner.NONE, VoiceSessionArbiter.currentOwner())
        assertTrue(okLatch.await(5, TimeUnit.SECONDS))

        VoiceSessionArbiter.resetForTesting()

        // Error path
        val errLatch = CountDownLatch(1)
        val errEngine = GeminiOnlineSttEngine(
            transcribe = { _, _ -> throw GeminiHttpException(500) },
            scope = CoroutineScope(Dispatchers.Default),
            onError = { errLatch.countDown() }
        )
        errEngine.startListening()
        assertEquals(VoiceSessionOwner.IR_SERVICE, VoiceSessionArbiter.currentOwner())
        errEngine.processAudio(ByteArray(320) { 1 })
        errEngine.stopListening()
        assertEquals(
            "mic must be released even though transcription will fail",
            VoiceSessionOwner.NONE, VoiceSessionArbiter.currentOwner()
        )
        assertTrue(errLatch.await(5, TimeUnit.SECONDS))
    }

    // t18_b_05: blank transcript = no speech recognized — no emission,
    // no error (an honest no-match, not a failure).
    @Test
    fun t18_b_05_blankTranscriptIsNoMatch() {
        var transcriptEmitted = false
        var errorRaised = false
        val done = CountDownLatch(1)
        val engine = GeminiOnlineSttEngine(
            transcribe = { _, _ -> "   " },
            scope = CoroutineScope(Dispatchers.Default),
            onError = { errorRaised = true },
            micGate = CountingGate()
        )
        engine.setTranscriptListener { _, _ -> transcriptEmitted = true }
        engine.startListening()
        engine.processAudio(ByteArray(320) { 0 })
        assertTrue(engine.finishUtterance())
        // Give the async transcription a bounded window to (not) emit.
        Thread.sleep(600)
        assertFalse(transcriptEmitted)
        assertFalse(errorRaised)
        done.countDown()
    }

    // t18_b_06: audio fed while not listening is ignored, and an
    // utterance with no buffered audio is a no-op.
    @Test
    fun t18_b_06_audioIgnoredWhenNotListening() {
        val gate = CountingGate()
        val engine = GeminiOnlineSttEngine(
            transcribe = { _, _ -> throw AssertionError("must not be called") },
            scope = CoroutineScope(Dispatchers.Default),
            micGate = gate
        )
        engine.processAudio(ByteArray(320) { 9 })
        assertFalse(engine.finishUtterance())
        assertEquals(0, gate.acquires)
    }

    private fun le16(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)

    private fun le32(b: ByteArray, p: Int): Int =
        le16(b, p) or (le16(b, p + 2) shl 16)
}
