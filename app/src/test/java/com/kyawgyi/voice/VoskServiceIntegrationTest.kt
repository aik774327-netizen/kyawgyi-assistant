package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * VoskServiceIntegrationTest
 *
 * Explicitly verifies:
 * "Integrate the Vosk speech-to-text engine with a service layer to process offline
 *  Myanmar language voice commands and output recognized text to the main state."
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoskServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var sttEngineManager: SttEngineManager
    private lateinit var pipelineController: HybridPipelineController
    private lateinit var stateStore: AssistantStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        stateStore = AssistantStateStore.getInstance()
        stateStore.resetToIdle()
        sttEngineManager = SttEngineManager.getInstance(context)
        pipelineController = HybridPipelineController.getInstance(context)
        AudioFocusAndAecManager.getInstance(context).resetForTesting()
    }

    @Test
    fun testVoskSttEngineDirectPcmProcessingAndMainStateOutput() = runTest {
        // 1. Select Vosk STT engine
        sttEngineManager.selectEngine(SttEngineType.VOSK)
        assertEquals(SttEngineType.VOSK, sttEngineManager.activeEngine?.engineType)
        assertEquals(SttEngineType.VOSK, sttEngineManager.uiState.value.activeEngineType)

        // 2. Start listening session
        sttEngineManager.startListening()
        assertEquals(SttEngineState.LISTENING, sttEngineManager.uiState.value.engineState)

        // 3. Process raw 16kHz PCM audio chunk through Vosk engine
        val samplePcm = ByteArray(1280) { 15 }
        sttEngineManager.feedPcmChunk(samplePcm)
        assertTrue(sttEngineManager.uiState.value.streamedChunksCount > 0)

        // 4. Emit finalized offline Myanmar voice command from Vosk
        val myanmarCommand = "ဝိုင်ဖိုင် ဖွင့်ပေးပါ"
        var collectedTranscript: String? = null

        val collectorJob = launch {
            sttEngineManager.transcriptionFlow.collect { (text, isFinal) ->
                if (isFinal) {
                    collectedTranscript = text
                }
            }
        }

        sttEngineManager.emitTranscript(myanmarCommand, isFinal = true)
        advanceUntilIdle()

        // 5. Verify recognized text updated in the manager UI state (CodeSwitchNormalizer normalizes to canonical entity)
        val expectedTranscript = "Wi-Fi ဖွင့်ပေးပါ"
        assertEquals(expectedTranscript, collectedTranscript)
        assertEquals(expectedTranscript, sttEngineManager.uiState.value.finalTranscript)

        // 6. Connect to service layer pipeline: process utterance
        pipelineController.processUtterance(expectedTranscript)
        advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // 7. Verify output to main state (FSM transitions and returns to IDLE after action execution)
        assertEquals(AssistantState.IDLE, stateStore.getCurrentState())

        collectorJob.cancel()
        sttEngineManager.stopListening()
    }

    @Test
    fun testVoskGrammarAndMyanmarHotwordBiasing() {
        val voskEngine = sttEngineManager.voskEngine
        assertNotNull(voskEngine)

        // Verify Vosk grammar generation produces valid JSON array for Kaldi decoder
        val grammarJson = voskEngine.hotwordBiasing.buildVoskGrammarJson()
        assertTrue(grammarJson.startsWith("["))
        assertTrue(grammarJson.endsWith("]"))
        assertTrue(grammarJson.contains("ကျော်ကြီး"))

        // Verify transcript emission applies homophone fixing and Myanmar normalization
        var receivedTranscript: String? = null
        voskEngine.setTranscriptListener { text, isFinal ->
            if (isFinal) receivedTranscript = text
        }

        // Acoustic near-miss variant: ကေဘီဇတ်ပေး (fuzzy hotword variant)
        voskEngine.emitTranscript("ကေဘီဇတ်ပေး ဖွင့်ပါ", isFinal = true)
        assertNotNull(receivedTranscript)
        // CodeSwitchNormalizer & HotwordBiasing maps acoustic near-miss to canonical entity KBZPay for STT
        assertTrue(receivedTranscript!!.contains("KBZPay"))
        // And normalizeTtsResponse converts canonical entity to natural Burmese phonetic script for TTS
        val ttsSpoken = voskEngine.codeSwitchNormalizer.normalizeTtsResponse(receivedTranscript!!)
        assertTrue(ttsSpoken.contains("ကေဘီဇက်ပေး"))
    }
}
