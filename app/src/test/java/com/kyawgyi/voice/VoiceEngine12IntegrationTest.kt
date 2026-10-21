package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceEngine12IntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testVoiceEngine12InitializationAndSubsystems() {
        val engine = VoiceEngine12.getInstance(context)
        assertNotNull(engine)

        val status = engine.status.value
        assertTrue(status.isInitialized)
        assertTrue(status.activeHotwordsCount > 0)
        assertNotNull(engine.audioBufferPool)
        assertNotNull(engine.hotwordBiasing)
        assertNotNull(engine.clippingLimiter)
        assertNotNull(engine.dynamicAgc)
        assertNotNull(engine.twoStageWakeWord)
        assertNotNull(engine.screenContextHotwordManager)
    }

    @Test
    fun testProcessIncomingPcmFrameConditionsAudio() {
        val engine = VoiceEngine12.getInstance(context)

        // Synthesize 40ms of 16kHz audio with high amplitude
        val pcm = ByteArray(1280)
        for (i in 0 until 640) {
            val offset = i * 2
            val sample = if (i % 2 == 0) 31000 else -31000
            pcm[offset] = (sample and 0xFF).toByte()
            pcm[offset + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val processed = engine.processIncomingPcmFrame(pcm)
        assertEquals(1280, processed.size)
    }

    @Test
    fun testPrepareTextForTtsVerbalizesSymbols() {
        val engine = VoiceEngine12.getInstance(context)
        val verbalized = engine.prepareTextForTts("32°C မှာ 5000 ကျပ် လွှဲပါ")
        assertTrue(verbalized.contains("ဒီဂရီ စင်တီဂရိတ်"))
    }
}
