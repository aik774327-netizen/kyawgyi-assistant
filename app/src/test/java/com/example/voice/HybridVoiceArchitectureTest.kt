package com.example.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HybridVoiceArchitectureTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        com.example.audio.CentralAudioRecorder.resetForTesting()
    }

    @Test
    fun testCentralAudioRecorderInitialization() {
        val recorder = com.example.audio.CentralAudioRecorder.getInstance(context)
        assertNotNull(recorder)
        assertEquals(com.example.audio.CentralAudioRecorder.RecorderState.IDLE, recorder.recorderState.value)
    }

    @Test
    fun testSherpaOnnxOfflineStrategyExecution() = runBlocking {
        val offlineStrategy = SherpaOnnxOfflineStrategy(context)
        assertEquals(EngineType.SHERPA_ONNX_OFFLINE, offlineStrategy.engineType)

        // Mock 16kHz PCM audio chunk (silence/audio samples)
        val sampleChunk = ByteArray(1280) { 10 }
        val fakeFlow = flowOf(sampleChunk, sampleChunk)

        val result = offlineStrategy.processAudioStream(fakeFlow)
        assertNotNull(result)
        assertTrue(result.isSuccess)
        assertEquals(EngineType.SHERPA_ONNX_OFFLINE, result.executionEngine)
        assertTrue(result.spokenResponse.isNotBlank())
    }

    @Test
    fun testHybridEngineRouterInstance() {
        val router = HybridEngineRouter.getInstance(context)
        assertNotNull(router)
    }
}
