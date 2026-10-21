package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.CentralAudioRecorder
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceBridgesIntegrationTest {

    private lateinit var context: Context
    private lateinit var sttEngineManager: SttEngineManager
    private lateinit var audioFocusManager: AudioFocusAndAecManager
    private lateinit var assistantStateStore: AssistantStateStore
    private lateinit var audioBridge: CentralAudioRecorderBridge
    private lateinit var fsmSynchronizer: FsmStateSynchronizer
    private lateinit var pipelineController: HybridPipelineController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        sttEngineManager = SttEngineManager.getInstance(context)
        audioFocusManager = AudioFocusAndAecManager.getInstance(context)
        assistantStateStore = AssistantStateStore.getInstance()

        audioBridge = CentralAudioRecorderBridge.getInstance(context)
        fsmSynchronizer = FsmStateSynchronizer(sttEngineManager, assistantStateStore)
        pipelineController = HybridPipelineController.getInstance(context)
        audioFocusManager.resetForTesting()
    }

    @Test
    fun testCentralAudioRecorderBridgeLifecycleAndAecGuard() = runTest {
        // Initially bridge not active
        assertFalse("Bridge should initially be inactive", audioBridge.isBridging())

        // Start bridge
        val started = audioBridge.startBridge()
        assertTrue("Bridge should start successfully", started)
        assertTrue(audioBridge.isBridging())

        // Verify AEC Guard: When TTS starts speaking, mic should be marked as speaking/paused
        audioFocusManager.onTtsPlaybackStarted()
        assertTrue("Audio focus manager should report speaking", audioFocusManager.isSpeaking)
        assertTrue("Audio focus manager should report mic paused", audioFocusManager.isMicPaused)

        // When TTS stops, guard should deactivate
        audioFocusManager.onTtsPlaybackStopped()
        assertFalse(audioFocusManager.isSpeaking)
        assertFalse(audioFocusManager.isMicPaused)

        // Stop bridge
        audioBridge.stopBridge()
        assertFalse(audioBridge.isBridging())
    }

    @Test
    fun testFsmStateSynchronizerMappings() {
        // 1. STT -> LISTENING maps to FSM -> LISTENING
        fsmSynchronizer.syncSttState(SttEngineState.LISTENING)
        assertEquals(AssistantState.LISTENING, assistantStateStore.getCurrentState())

        // 2. STT -> PROCESSING maps to FSM -> THINKING
        fsmSynchronizer.syncSttState(SttEngineState.PROCESSING)
        assertEquals(AssistantState.THINKING, assistantStateStore.getCurrentState())

        // 3. STT -> SPEAKING maps to FSM -> SPEAKING
        fsmSynchronizer.syncSttState(SttEngineState.SPEAKING)
        assertEquals(AssistantState.SPEAKING, assistantStateStore.getCurrentState())

        // 4. Guard test: When FSM is in AWAITING_CONFIRMATION, STT state changes should not override it
        assistantStateStore.transitionTo(AssistantState.AWAITING_CONFIRMATION)
        fsmSynchronizer.syncSttState(SttEngineState.IDLE)
        assertEquals("Protected AWAITING_CONFIRMATION should be preserved",
            AssistantState.AWAITING_CONFIRMATION, assistantStateStore.getCurrentState())

        // Clean up
        assistantStateStore.resetToIdle()
    }

    @Test
    fun testHybridPipelineControllerProcessing() = runTest {
        // Test processing an utterance directly through the pipeline
        pipelineController.processUtterance("ဝိုင်ဖိုင် ဖွင့်ပေးပါ")
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify state returned to IDLE after action and response
        assertEquals(AssistantState.IDLE, assistantStateStore.getCurrentState())
    }
}
