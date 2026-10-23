package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GeminiApiService
import com.example.data.GeminiFailureClassifier
import com.example.data.GeminiFailureClass
import com.example.data.GeminiHttpException
import com.example.data.NoApiKeyConfiguredException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §7 Part C regression tests (t18_c_01..06) — the Gemini online TTS
 * engine with FAKE synthesis + a recording fake player (no network,
 * no key, no device). Pins: playback parameter selection (24 kHz +
 * exact PCM bytes reach the player), honest failure surfacing via
 * onError BEFORE any fallback, fallback receiving the utterance
 * without the failure being hidden, the no-fallback throw, playback
 * failures reported as playback failures, and VoiceSettings TTS
 * selection persistence/defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiTtsEngineTest {

    private class RecordingPlayer : PcmPlayer {
        var lastPcm: ByteArray? = null
        var lastRate: Int = -1
        var playCount = 0
        override suspend fun play(pcm: ByteArray, sampleRateHz: Int) {
            lastPcm = pcm
            lastRate = sampleRateHz
            playCount++
        }
        override fun stop() {}
    }

    private class ThrowingPlayer(private val failure: Exception) : PcmPlayer {
        override suspend fun play(pcm: ByteArray, sampleRateHz: Int) {
            throw failure
        }
        override fun stop() {}
    }

    private class RecordingFallback : TtsEngine {
        val spoken = mutableListOf<String>()
        override suspend fun speak(text: String) { spoken += text }
        override fun stop() {}
        override fun close() {}
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // t18_c_01: success — the player receives the exact PCM bytes at
    // the Gemini TTS rate (24 kHz); no error, no fallback.
    @Test
    fun t18_c_01_successPlaysPcmAt24kHz() = runBlocking {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        var synthesizedText: String? = null
        var synthesizedVoice: String? = null
        val player = RecordingPlayer()
        val fallback = RecordingFallback()
        var error: String? = null
        val engine = GeminiTtsEngine(
            synthesize = { text, voice ->
                synthesizedText = text
                synthesizedVoice = voice
                pcm
            },
            player = player,
            fallback = fallback,
            onError = { error = it }
        )
        engine.speak("မင်္ဂလာပါ")
        assertEquals("မင်္ဂလာပါ", synthesizedText)
        assertEquals(GeminiApiService.DEFAULT_TTS_VOICE_NAME, synthesizedVoice)
        assertEquals(24_000, player.lastRate)
        assertArrayEquals(pcm, player.lastPcm)
        assertEquals(1, player.playCount)
        assertTrue(fallback.spoken.isEmpty())
        assertEquals(null, error)
    }

    // t18_c_02: synthesis failure (403) — the AUTH message is
    // surfaced via onError and the fallback speaks the utterance;
    // the player is never touched.
    @Test
    fun t18_c_02_synthesisFailureSurfacesAndFallsBack() = runBlocking {
        val player = RecordingPlayer()
        val fallback = RecordingFallback()
        var error: String? = null
        val engine = GeminiTtsEngine(
            synthesize = { _, _ -> throw GeminiHttpException(403) },
            player = player,
            fallback = fallback,
            onError = { error = it }
        )
        engine.speak("မင်္ဂလာပါ")
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.AUTH, 403),
            error
        )
        assertEquals(listOf("မင်္ဂလာပါ"), fallback.spoken)
        assertEquals(0, player.playCount)
    }

    // t18_c_03: without a fallback, a synthesis failure throws
    // GeminiTtsException carrying the classified message — the
    // utterance is never reported as spoken.
    @Test
    fun t18_c_03_synthesisFailureWithoutFallbackThrows() = runBlocking {
        val engine = GeminiTtsEngine(
            synthesize = { _, _ -> throw GeminiHttpException(500) },
            player = RecordingPlayer(),
            fallback = null,
            onError = {}
        )
        try {
            engine.speak("မင်္ဂလာပါ")
            fail("Expected GeminiTtsException")
        } catch (e: GeminiTtsException) {
            assertEquals(
                GeminiFailureClassifier.userMessageFor(GeminiFailureClass.SERVER, 500),
                e.message
            )
        }
    }

    // t18_c_04: no key configured — the NO_KEY message is surfaced
    // (the user is told to add a key, not told "connection failed").
    @Test
    fun t18_c_04_noKeySurfacesNoKeyMessage() = runBlocking {
        var error: String? = null
        val fallback = RecordingFallback()
        val engine = GeminiTtsEngine(
            synthesize = { _, _ -> throw NoApiKeyConfiguredException() },
            player = RecordingPlayer(),
            fallback = fallback,
            onError = { error = it }
        )
        engine.speak("မင်္ဂလာပါ")
        assertEquals(GeminiApiService.NO_API_KEY_MESSAGE, error)
        assertEquals(1, fallback.spoken.size)
    }

    // t18_c_05: synthesis succeeds but playback fails — reported as
    // a PLAYBACK failure (not mislabeled a connection failure) and
    // the fallback speaks.
    @Test
    fun t18_c_05_playbackFailureReportedAsPlaybackAndFallsBack() = runBlocking {
        var error: String? = null
        val fallback = RecordingFallback()
        val engine = GeminiTtsEngine(
            synthesize = { _, _ -> byteArrayOf(9, 9, 9, 9) },
            player = ThrowingPlayer(IllegalStateException("track dead")),
            fallback = fallback,
            onError = { error = it }
        )
        engine.speak("မင်္ဂလာပါ")
        assertTrue(error!!.contains("ဖွင့်၍ မရပါ"))
        assertEquals(1, fallback.spoken.size)
    }

    // t18_c_06: TTS engine selection persists in VoiceSettings and
    // resetToDefaults restores SYSTEM.
    @Test
    fun t18_c_06_ttsSelectionPersistsAndResets() {
        val settings = VoiceSettings.getInstance(context)
        settings.preferredTtsEngine = TtsEngineType.GEMINI_ONLINE
        assertEquals(TtsEngineType.GEMINI_ONLINE, settings.preferredTtsEngine)
        assertEquals(TtsEngineType.GEMINI_ONLINE, settings.preferredTtsEngineFlow.value)

        settings.resetToDefaults()
        assertEquals(TtsEngineType.SYSTEM, settings.preferredTtsEngine)
    }
}
