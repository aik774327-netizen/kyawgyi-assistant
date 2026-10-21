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

    // The former testSherpaOnnxOfflineStrategyExecution was deleted with the
    // SherpaOnnxOfflineStrategy itself (2026-10-22): it asserted that an
    // energy-level heuristic "recognized" canned Burmese transcripts from
    // silent PCM - i.e. it tested a fake. There is no offline STT engine.

    @Test
    fun testHybridEngineRouterInstance() {
        val router = HybridEngineRouter.getInstance(context)
        assertNotNull(router)
    }
}
