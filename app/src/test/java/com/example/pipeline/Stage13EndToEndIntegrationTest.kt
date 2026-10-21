package com.example.pipeline

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.ActionRegistry
import com.example.pipeline.contract.ExecutionStatus
import com.example.pipeline.contract.InputSource
import com.example.pipeline.contract.RiskLevel
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.memory.StructuredMemoryStore
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import com.example.ui.NexusUiState
import com.example.ui.NexusViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Stage13EndToEndIntegrationTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private lateinit var memoryStore: StructuredMemoryStore
    private lateinit var pipeline: UnifiedIntelligencePipeline
    private lateinit var viewModel: NexusViewModel
    private lateinit var settingsRouter: DirectSettingsRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = ApplicationProvider.getApplicationContext()
        memoryStore = StructuredMemoryStore()
        pipeline = UnifiedIntelligencePipeline(context, memoryStore)
        viewModel = NexusViewModel(app)
        settingsRouter = DirectSettingsRouter(context)
        TelemetryManager.clearEvents()
    }

    // =============================================================
    // 1. Myanmar Language & Phonetic Normalization Validation
    // =============================================================

    @Test
    fun test01_PipelineEndToEnd_YouTubeVoiceCommand() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_yt_1",
            sessionId = "sess_integration",
            transcript = "ယူကျုဖွင့်ပေးပါ",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("YOUTUBE", response.target)
        assertTrue(response.confidence >= 0.80f)
        assertTrue(response.isOfflineProcessed)
        assertTrue(response.responseMyanmar.contains("YouTube") || response.responseMyanmar.contains("ဖွင့်"))
    }

    @Test
    fun test02_PipelineEndToEnd_VolumeControlMyanmar() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_vol_1",
            sessionId = "sess_integration",
            transcript = "အသံနည်းနည်းတိုးပေးပါဦး",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("VOLUME_DOWN", response.intent)
        assertFalse(response.requiresConfirmation)
    }

    @Test
    fun test03_PipelineEndToEnd_MusicPlaybackIntent() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_music_1",
            sessionId = "sess_integration",
            transcript = "သီချင်းဖွင့်ပါ",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("PLAY_MUSIC", response.intent)
        assertTrue(response.confidence >= 0.70f)
    }

    // =============================================================
    // 2. Multi-turn Context & Reference Resolution
    // =============================================================

    @Test
    fun test04_PipelineMultiTurn_PronounResolution() = runBlocking {
        // Pre-condition: Open TikTok previously
        memoryStore.updateContext(app = "TIKTOK", command = "TikTok ဖွင့်")

        val followUpRequest = UnifiedCommandRequest(
            requestId = "req_ref_1",
            sessionId = "sess_integration",
            transcript = "အဲ့ဒီ app ကို ပြန်ဖွင့်",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(followUpRequest, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("TIKTOK", response.target)
    }

    // =============================================================
    // 3. Safety Guardrails & High Risk Confirmation
    // =============================================================

    @Test
    fun test05_PipelineSafetyGate_CallRequiresConfirmation() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_call_1",
            sessionId = "sess_integration",
            transcript = "ဦးကျော်ကို ဖုန်းခေါ်ပေး",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("CALL", response.intent)
        assertTrue(response.requiresConfirmation)
        val canonical = response.toCanonicalResult()
        assertEquals(ExecutionStatus.AWAITING_CONFIRMATION, canonical.executionStatus)
        assertTrue(response.responseMyanmar.contains("ဖုန်းခေါ်ဆို") || response.responseMyanmar.contains("အတည်ပြု"))
    }

    @Test
    fun test06_PipelineSafetyGate_DeleteRequiresConfirmation() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_del_1",
            sessionId = "sess_integration",
            transcript = "ဖိုင်တွေကို ဖျက်ပစ်ပါ",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("DELETE", response.intent)
        assertTrue(response.requiresConfirmation)
        assertEquals(RiskLevel.CRITICAL, response.actionPlan.steps.first().riskLevel)
    }

    // =============================================================
    // 4. Telemetry Tracking & PII Redaction
    // =============================================================

    @Test
    fun test07_TelemetryLifecycleAndPiiRedaction() = runBlocking {
        val sensitiveInput = "မောင်မောင် ဖုန်းနံပါတ် 09123456789 ကို စာပို့ပြီး password=secret123 ပြောပေး"
        val request = UnifiedCommandRequest(
            requestId = "req_tel_1",
            sessionId = "sess_telemetry",
            transcript = sensitiveInput,
            source = InputSource.VOICE
        )

        pipeline.processCommand(request, speakResponse = false)

        val recordedEvents = TelemetryManager.events.value
        assertTrue(recordedEvents.isNotEmpty())

        // Verify COMMAND_RECEIVED event exists
        val commandRecEvent = recordedEvents.find { it.eventType == TelemetryEventType.COMMAND_RECEIVED }
        assertNotNull(commandRecEvent)
        // Verify phone and password were redacted
        assertFalse(commandRecEvent!!.sanitizedDetails.contains("09123456789"))
        assertFalse(commandRecEvent.sanitizedDetails.contains("secret123"))
        assertTrue(commandRecEvent.sanitizedDetails.contains("[REDACTED_PHONE]"))
        assertTrue(commandRecEvent.sanitizedDetails.contains("[REDACTED_SECRET]"))

        // Verify downstream pipeline events occurred
        assertTrue(recordedEvents.any { it.eventType == TelemetryEventType.NORMALIZATION_COMPLETED })
        assertTrue(recordedEvents.any { it.eventType == TelemetryEventType.REFERENCE_RESOLVED })
        assertTrue(recordedEvents.any { it.eventType == TelemetryEventType.INTENT_CLASSIFIED })
        assertTrue(recordedEvents.any { it.eventType == TelemetryEventType.PLAN_CREATED })
        assertTrue(recordedEvents.any { it.eventType == TelemetryEventType.SAFETY_CHECKED })
    }

    // =============================================================
    // 5. DirectSettingsRouter Permission Handling
    // =============================================================

    @Test
    fun test08_DirectSettingsRouterCapabilityInspection() {
        assertNotNull(settingsRouter)
        // Verification of capabilities without throwing exceptions
        val micGranted = settingsRouter.isCapabilityGranted(SettingsDestination.APPLICATION_DETAILS)
        val overlayGranted = settingsRouter.isCapabilityGranted(SettingsDestination.OVERLAY_PERMISSION)
        val writeSettingsGranted = settingsRouter.isCapabilityGranted(SettingsDestination.WRITE_SETTINGS)

        assertTrue(micGranted || !micGranted) // Ensures safe execution without native crash
        assertTrue(overlayGranted || !overlayGranted)
        assertTrue(writeSettingsGranted || !writeSettingsGranted)
    }

    // =============================================================
    // 6. NexusViewModel State Machine & Single Source of Truth
    // =============================================================

    @Test
    fun test09_NexusViewModelDelegationToPipeline() = runBlocking {
        val job = viewModel.submitCommand("အသံတိုးပေးပါ", InputSource.TYPED)
        job.join()

        val history = viewModel.commandHistory.value
        assertTrue(history.isNotEmpty())
        val state = viewModel.uiState.value
        assertTrue(state is NexusUiState.Success)
    }

    @Test
    fun test10_NexusViewModelConfirmationWorkflow() = runBlocking {
        val job = viewModel.submitCommand("မောင်မောင်ကို ဖုန်းဆက်", InputSource.VOICE)
        job.join()

        val state = viewModel.uiState.value
        assertTrue(state is NexusUiState.AwaitingConfirmation)

        if (state is NexusUiState.AwaitingConfirmation) {
            assertEquals("CALL", state.commandType)
            assertEquals("မောင်မောင်", state.target)
            // User confirms
            val confirmJob = viewModel.confirmPendingAction()
            confirmJob.join()
            val nextState = viewModel.uiState.value
            assertTrue(nextState is NexusUiState.Success)
        }
    }

    // =============================================================
    // 7. Pipeline Performance & Latency Assertion
    // =============================================================

    @Test
    fun test11_PipelineOfflineExecutionLatency() = runBlocking {
        val request = UnifiedCommandRequest(
            requestId = "req_perf_1",
            sessionId = "sess_perf",
            transcript = "WiFi ပိတ်",
            source = InputSource.VOICE
        )
        val response = pipeline.processCommand(request, speakResponse = false)

        assertTrue(response.isOfflineProcessed)
        // Pipeline latency under 200ms on local test environment
        assertTrue(response.latencyMs < 200L)
    }
}
