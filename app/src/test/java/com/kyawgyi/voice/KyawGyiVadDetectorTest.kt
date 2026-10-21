package com.kyawgyi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class KyawGyiVadDetectorTest {

    private lateinit var vadDetector: KyawGyiVadDetector

    @Before
    fun setup() {
        vadDetector = KyawGyiVadDetector(
            speechSilenceThresholdMs = 300L,
            minSpeechDurationMs = 100L,
            maxUtteranceDurationMs = 5000L
        )
    }

    @Test
    fun testVadInitialState() {
        assertEquals(KyawGyiVadDetector.VadState.IDLE, vadDetector.vadState.value)
        assertFalse(vadDetector.isUserSpeaking)
    }

    @Test
    fun testVadSessionAndSilence() {
        vadDetector.startSession()
        assertEquals(KyawGyiVadDetector.VadState.LISTENING_FOR_SPEECH, vadDetector.vadState.value)

        // Feed silence (all zeros)
        val silencePcm = ByteArray(1024)
        val res = vadDetector.processPcmChunk(silencePcm)

        assertFalse(res.isVoiceActive)
        assertEquals(KyawGyiVadDetector.VadState.LISTENING_FOR_SPEECH, vadDetector.vadState.value)
    }

    @Test
    fun testSpeechOnsetAndEndpointCallback() {
        var speechStartedFired = false
        var speechEndpointFired = false

        vadDetector.onSpeechStarted = { speechStartedFired = true }
        vadDetector.onSpeechEndpoint = { speechEndpointFired = true }

        vadDetector.startSession()

        // Generate synthetic voice PCM (approx 250 Hz sine wave typical of speech)
        val voicePcm = ByteArray(1024)
        val shorts = ShortArray(512) { i ->
            (8000.0 * kotlin.math.sin(2.0 * Math.PI * 250.0 * i / 16000.0)).toInt().toShort()
        }
        ByteBuffer.wrap(voicePcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)

        // Process first voice frame to establish onset start
        vadDetector.processPcmChunk(voicePcm)

        // Wait to exceed minSpeechDurationMs (100ms) and process second voice frame
        Thread.sleep(150)
        vadDetector.processPcmChunk(voicePcm)

        assertTrue("Speech onset callback should fire", speechStartedFired)
        assertEquals(KyawGyiVadDetector.VadState.SPEECH_DETECTED, vadDetector.vadState.value)
        assertTrue(vadDetector.isUserSpeaking)

        // Feed silence to trigger endpoint
        val silencePcm = ByteArray(1024)
        Thread.sleep(350) // exceed speechSilenceThresholdMs (300ms)
        vadDetector.processPcmChunk(silencePcm)

        assertTrue("Speech endpoint callback should fire", speechEndpointFired)
        assertEquals(KyawGyiVadDetector.VadState.SPEECH_ENDPOINT_REACHED, vadDetector.vadState.value)
    }
}
