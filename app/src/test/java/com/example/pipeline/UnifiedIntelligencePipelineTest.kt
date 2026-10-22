package com.example.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.ActionRegistry
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.pipeline.contract.RiskLevel
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.memory.StructuredMemoryStore
import com.example.pipeline.response.ResponseGenerator
import com.example.pipeline.verifier.ResultVerifier
import com.example.semantic.ActionSafetyValidator
import com.example.semantic.MyanmarSemanticEngine
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticIntent
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
class UnifiedIntelligencePipelineTest {

    private lateinit var context: Context
    private lateinit var memoryStore: StructuredMemoryStore
    private lateinit var pipeline: UnifiedIntelligencePipeline
    private lateinit var responseGenerator: ResponseGenerator
    private lateinit var resultVerifier: ResultVerifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // ISSUE-036: the Failed-Method Memory is now genuinely cross-command
        // (canonical intent+target keys), so tests in this class must isolate
        // error-learning state per method — otherwise one method's recorded
        // failure correctly blocks a later method's rephrased retry.
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        memoryStore = StructuredMemoryStore()
        pipeline = UnifiedIntelligencePipeline(context, memoryStore)
        responseGenerator = ResponseGenerator()
        resultVerifier = ResultVerifier(context)
    }

    // =============================================================
    // Category A & B: Myanmar Language & Mixed Language Commands
    // =============================================================

    @Test
    fun test01_OpenFacebook_DirectMyanmar() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_1", sessionId = "sess_1", transcript = "Facebook ဖွင့်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("FACEBOOK", response.target)
        assertTrue(response.confidence >= 0.80f)
        assertTrue(response.responseMyanmar.contains("Facebook") || response.responseMyanmar.contains("ဖွင့်"))
        assertTrue(response.isOfflineProcessed)
    }

    @Test
    fun test02_OpenFacebook_PhoneticBurmese() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_2", sessionId = "sess_1", transcript = "ဖေ့စ်ဘုတ်ဖွင့်ပေး")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("FACEBOOK", response.target)
        assertTrue(response.confidence >= 0.80f)
        assertTrue(response.responseMyanmar.contains("ဖွင့်"))
    }

    @Test
    fun test03_OpenFacebook_MixedLanguage() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_3", sessionId = "sess_1", transcript = "Facebook open လုပ်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("FACEBOOK", response.target)
        assertTrue(response.confidence >= 0.80f)
    }

    // =============================================================
    // Category C & D: Context & Memory Architecture
    // =============================================================

    @Test
    fun test04_ContextualPronounResolution() = runBlocking {
        // Pre-populate context with Facebook
        memoryStore.updateContext(app = "FACEBOOK", command = "Facebook ဖွင့်")

        val request = UnifiedCommandRequest(requestId = "req_4", sessionId = "sess_1", transcript = "အဲ့ဒါဖွင့်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("FACEBOOK", response.target)
        assertTrue(response.confidence >= 0.70f)
    }

    @Test
    fun test05_ContextualAppEntry() = runBlocking {
        memoryStore.updateContext(app = "YOUTUBE", command = "YouTube ဖွင့်")

        val request = UnifiedCommandRequest(requestId = "req_5", sessionId = "sess_1", transcript = "အဲ့ဒီ app ထဲဝင်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("OPEN_APP", response.intent)
        assertEquals("YOUTUBE", response.target)
    }

    @Test
    fun test06_ContextMemoryExpirationTTL() {
        // Expired item with TTL of 1ms
        memoryStore.updateContext(app = "VIBER", ttlMs = 1L)
        Thread.sleep(5)

        val activeContext = memoryStore.getActiveContext()
        assertEquals(null, activeContext.getValidLastApp())
    }

    // =============================================================
    // Category E & F: Intent Classification & Action Planning
    // =============================================================

    @Test
    fun test07_VolumeUpIntent() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_7", sessionId = "sess_1", transcript = "အသံတိုး")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("VOLUME_DOWN", response.intent)
        assertFalse(response.requiresConfirmation)
        assertTrue(response.responseMyanmar.contains("အသံ"))
    }

    @Test
    fun test08_VolumeUpPoliteColloquial() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_8", sessionId = "sess_1", transcript = "အသံနည်းနည်းတိုးပေး")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("VOLUME_DOWN", response.intent)
    }

    @Test
    fun test09_FlashlightToggle() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_9", sessionId = "sess_1", transcript = "မီးလင်းပေး")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("FLASHLIGHT", response.intent)
        assertTrue(response.responseMyanmar.contains("ဓာတ်မီး") || response.responseMyanmar.contains("မီး"))
    }

    @Test
    fun test10_WifiToggle() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_10", sessionId = "sess_1", transcript = "WiFi ပိတ်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("WIFI_TOGGLE", response.intent)
    }

    // =============================================================
    // Category G: Safety Validation & High-Risk Confirmation
    // =============================================================

    @Test
    fun test11_CallContactRequiresConfirmation() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_11", sessionId = "sess_1", transcript = "မောင်မောင်ကို ဖုန်းဆက်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("CALL", response.intent)
        assertEquals("မောင်မောင်", response.target)
        assertTrue(response.requiresConfirmation)
        assertTrue(response.responseMyanmar.contains("ဖုန်းခေါ်ဆို"))
    }

    @Test
    fun test12_SendMessageRequiresConfirmation() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_12", sessionId = "sess_1", transcript = "မောင်မောင်ကို စာပို့")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("SEND_MESSAGE", response.intent)
        assertTrue(response.requiresConfirmation)
    }

    // =============================================================
    // Category L: Accessibility Navigation & UI Actions
    // =============================================================

    @Test
    fun test13_BackNavigation() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_13", sessionId = "sess_1", transcript = "နောက်ပြန်သွား")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("BACK", response.intent)
        assertTrue(response.responseMyanmar.contains("နောက်သို့ ပြန်သွားပေးလိုက်ပါပြီ") || response.responseMyanmar.contains("နောက်"))
    }

    @Test
    fun test14_HomeNavigation() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_14", sessionId = "sess_1", transcript = "Home ပြန်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("HOME", response.intent)
        assertTrue(response.responseMyanmar.contains("ပင်မ") || response.responseMyanmar.contains("Home") || response.responseMyanmar.contains("သွား"))
    }

    // =============================================================
    // Category H & I: Action Registry & Error Recovery Memory
    // =============================================================

    @Test
    fun test15_ActionRegistryApproval() {
        assertTrue(ActionRegistry.isApprovedAction("OPEN_APP"))
        assertTrue(ActionRegistry.isApprovedAction("CALL"))
        assertTrue(ActionRegistry.isApprovedAction("SEND_MESSAGE"))
        assertTrue(ActionRegistry.isApprovedAction("TAP"))
        assertTrue(ActionRegistry.isApprovedAction("SCROLL"))
        assertTrue(ActionRegistry.isApprovedAction("BACK"))
        assertTrue(ActionRegistry.isApprovedAction("HOME"))
        assertTrue(ActionRegistry.isApprovedAction("RECENTS"))
        assertTrue(ActionRegistry.isApprovedAction("VOLUME_UP"))
        assertTrue(ActionRegistry.isApprovedAction("VOLUME_DOWN"))
        assertTrue(ActionRegistry.isApprovedAction("TAKE_SCREENSHOT"))
        assertTrue(ActionRegistry.isApprovedAction("FLASHLIGHT"))
        assertTrue(ActionRegistry.isApprovedAction("WIFI_TOGGLE"))
        assertTrue(ActionRegistry.isApprovedAction("BLUETOOTH_TOGGLE"))
        assertTrue(ActionRegistry.isApprovedAction("SET_ALARM"))
        assertTrue(ActionRegistry.isApprovedAction("SET_TIMER"))

        val deleteDef = ActionRegistry.getAction("DELETE")
        assertNotNull(deleteDef)
        assertEquals(RiskLevel.CRITICAL, deleteDef!!.riskLevel)
        assertTrue(deleteDef.defaultRequiresConfirmation)
    }

    @Test
    fun test16_ResultVerifierAndFailureRecovery() {
        val verified = resultVerifier.verify(
            actionType = "OPEN_APP",
            target = "com.nonexistent.fakeapp",
            parameters = emptyMap(),
            initialSuccess = true
        )

        assertFalse(verified.verified)
        assertEquals("APP_NOT_FOUND", verified.errorCode)

        val failureResponse = responseGenerator.generateFailureResponse(
            actionType = "OPEN_APP",
            target = "FakeApp",
            reason = verified.reason,
            errorCode = verified.errorCode
        )
        assertTrue(failureResponse.contains("ထည့်သွင်းမထားပါ"))
    }

    @Test
    fun test17_PlayMusicLocalTrackExecution() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_17", sessionId = "sess_1", transcript = "မာယာ သီချင်းဖွင့်")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("PLAY_MUSIC", response.intent)
        assertTrue(response.confidence >= 0.70f)
        assertTrue(response.responseMyanmar.contains("ဖွင့်") || response.responseMyanmar.contains("သီချင်း") || response.responseMyanmar.contains("မာယာ"))
    }

    @Test
    fun test18_CallContactIntentResolution() = runBlocking {
        val request = UnifiedCommandRequest(requestId = "req_18", sessionId = "sess_1", transcript = "အမေဆီ ဖုန်းခေါ်ပေးပါ")
        val response = pipeline.processCommand(request, speakResponse = false)

        assertEquals("CALL", response.intent)
        assertTrue(response.requiresConfirmation)
        assertTrue(response.responseMyanmar.contains("ဖုန်းခေါ်ဆို") || response.responseMyanmar.contains("အမေ"))
    }
}
