package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SileroVadFilterModuleTest {

    private lateinit var vadFilter: SileroVadFilterModule

    @Before
    fun setup() {
        vadFilter = SileroVadFilterModule(
            sampleRate = 16000,
            speechProbabilityThreshold = 0.5f,
            minSpeechFrames = 2,
            hangoverFrames = 3
        )
    }

    @Test
    fun testSilenceChunkFiltering() {
        // Generate pure silence (all 0s)
        val silencePcm = ByteArray(1024)
        val decision = vadFilter.processPcmChunk(silencePcm)

        assertFalse("Silence chunk should not be marked active", decision.isVoiceActive)
        assertEquals(SileroVadFilterModule.VadState.SILENCE, decision.state)
        assertNull("Silence PCM should be filtered out (null)", decision.filteredPcm)
    }

    @Test
    fun testSyntheticSpeechDetection() {
        var speechStartedTriggered = false
        vadFilter.onSpeechStarted = {
            speechStartedTriggered = true
        }

        // Generate synthetic sine wave simulating voice activity (~300Hz, amplitude 8000)
        val voiceSamples = ShortArray(512)
        for (i in voiceSamples.indices) {
            voiceSamples[i] = (kotlin.math.sin(2.0 * Math.PI * 300.0 * i / 16000.0) * 8000.0).toInt().toShort()
        }
        val voicePcm = ByteArray(voiceSamples.size * 2)
        ByteBuffer.wrap(voicePcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(voiceSamples)

        // Process consecutive voice frames to pass minSpeechFrames
        vadFilter.processPcmChunk(voicePcm)
        val decision2 = vadFilter.processPcmChunk(voicePcm)

        assertTrue("Speech frames should trigger active voice", decision2.isVoiceActive)
        assertNotNull("Active voice PCM should be preserved", decision2.filteredPcm)
        assertTrue("Speech started callback should be invoked", speechStartedTriggered)
    }
}
