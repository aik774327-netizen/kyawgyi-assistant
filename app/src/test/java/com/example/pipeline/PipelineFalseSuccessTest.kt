package com.example.pipeline

import android.content.Context
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.memory.StructuredMemoryStore
import com.example.pipeline.verifier.ResultVerifier
import kotlinx.coroutines.runBlocking
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

/**
 * ISSUE-030 regression tests (Phase 1.5, IDs T15-030-*).
 *
 * UnifiedIntelligencePipeline.executeAction used to fabricate
 * success = true / verified = true ("Generic action completed") for every
 * action type without an executor branch — including unmapped "CUSTOM"
 * intents and mapped-but-unimplemented types (SET_ALARM, NEXT_TRACK, ...).
 * These tests pin the honest contract:
 *  - unsupported / unexecuted actions never report success,
 *  - verified = true only where a real verification step ran,
 *  - supported actions keep reporting their real outcomes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PipelineFalseSuccessTest {

    private lateinit var context: Context
    private lateinit var memoryStore: StructuredMemoryStore
    private lateinit var pipeline: UnifiedIntelligencePipeline
    private lateinit var resultVerifier: ResultVerifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        memoryStore = StructuredMemoryStore()
        pipeline = UnifiedIntelligencePipeline(context, memoryStore)
        resultVerifier = ResultVerifier(context)
    }

    private fun request(id: String, transcript: String) = UnifiedCommandRequest(
        requestId = id,
        sessionId = "sess_t30",
        transcript = transcript
    )

    // -----------------------------------------------------------------
    // T15-030-01 — user case 2: an unmapped DELETE intent must never
    // report success. Honest note: DELETE is CRITICAL-risk, so today the
    // confirmation gate stops it before executeAction; this test pins
    // that no success can ever surface for it at the pipeline boundary.
    // The fabrication itself is exercised directly by T15-030-02a/02b/02c
    // (action types that DO reach executeAction's fallback branch).
    // -----------------------------------------------------------------
    @Test
    fun t15_030_01_deleteIntent_neverReportsSuccess() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_1", "ဖိုင်တွေကို ဖျက်ပစ်ပါ"),
            speakResponse = false
        )

        assertEquals("DELETE", response.intent)
        assertTrue(response.requiresConfirmation)
        assertNull(response.executionResult) // never executed -> no result, no success
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-02a — user case 1 / plan T15-030-02: SET_ALARM is mapped by
    // mapIntentToActionType but has NO branch in executeAction, so it
    // lands in the fallback branch. Pre-fix that branch fabricated
    // success = true; the honest outcome is success = false,
    // verified = false (unsupported, not executed).
    // -----------------------------------------------------------------
    @Test
    fun t15_030_02a_setAlarm_unimplementedAction_doesNotReportSuccess() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_2a", "မနက် ၇ နာရီ နှိုးစက်ထားပေးပါ"),
            speakResponse = false
        )

        assertEquals("SET_ALARM", response.intent)
        assertNotNull(response.executionResult)
        assertFalse(response.executionResult!!.success)
        assertFalse(response.executionResult!!.verified)
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-02b — NEXT_TRACK: same defect class (mapped, no branch).
    // -----------------------------------------------------------------
    @Test
    fun t15_030_02b_nextTrack_unimplementedAction_doesNotReportSuccess() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_2b", "နောက်တစ်ပုဒ် ပြောင်းပေး"),
            speakResponse = false
        )

        assertEquals("NEXT_TRACK", response.intent)
        assertNotNull(response.executionResult)
        assertFalse(response.executionResult!!.success)
        assertFalse(response.executionResult!!.verified)
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-02c — an intent the intent-mapper itself cannot map falls
    // to "CUSTOM" (SemanticIntent.MUTE has no mapping entry) and lands
    // in the same fallback branch.
    // -----------------------------------------------------------------
    @Test
    fun t15_030_02c_muteIntent_mapsToCustom_doesNotReportSuccess() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_2c", "အသံတိတ်ထားပေး"),
            speakResponse = false
        )

        assertEquals("CUSTOM", response.intent)
        assertNotNull(response.executionResult)
        assertFalse(response.executionResult!!.success)
        assertFalse(response.executionResult!!.verified)
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-03 — user case 3: a supported action whose execution
    // FAILS (OPEN_APP for an app that is not installed) must report
    // failure. No-regression control: this already held pre-fix and
    // must keep holding after the ISSUE-030 fix.
    //
    // Input choice (evidence-based): Messenger is NOT used as a
    // transcript by any other pipeline test. The first pre-fix CI run
    // (39706844440) used "Facebook ဖွင့်" here and the recorded
    // error-learning failure for that exact transcript polluted
    // UnifiedIntelligencePipelineTest.test01 in the shared Robolectric
    // sandbox (its response-text assertion at :57 failed). A unique
    // transcript keeps the classes independent.
    // -----------------------------------------------------------------
    @Test
    fun t15_030_03_openAppExecutionFailure_reportsFailure() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_3", "မက်ဆင်ဂျာ ဖွင့်"),
            speakResponse = false
        )

        assertEquals("OPEN_APP", response.intent)
        assertNotNull(response.executionResult)
        assertFalse(response.executionResult!!.success)
        assertFalse(response.executionResult!!.verified)
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-04a — user case 4 (pipeline level): a dispatched action
    // whose outcome cannot be verified must keep verified = false.
    // On Android 10+ the Wi-Fi toggle is delegated to the Settings
    // panel, so the platform never confirms the final state.
    // -----------------------------------------------------------------
    @Config(sdk = [33])
    @Test
    fun t15_030_04a_wifiOff_dispatchedButUnverifiable_verifiedStaysFalse() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_4a", "WiFi ပိတ်"),
            speakResponse = false
        )

        assertEquals("WIFI_TOGGLE", response.intent)
        assertNotNull(response.executionResult)
        assertFalse(response.executionResult!!.verified)
        assertFalse(response.verificationResult)
    }

    // -----------------------------------------------------------------
    // T15-030-04b — user case 4 (verifier level, deterministic):
    // device Wi-Fi is ON while the executed action requested OFF, so
    // the state read-back cannot confirm the outcome: the dispatch
    // result stands, but verified MUST be false (NOT_VERIFIABLE).
    // -----------------------------------------------------------------
    @Test
    fun t15_030_04b_verifier_wifiStateMismatch_notVerified() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiManager.setWifiEnabled(true)

        val result = resultVerifier.verify(
            actionType = "WIFI_TOGGLE",
            target = null,
            parameters = mapOf("enable" to false),
            initialSuccess = true
        )

        assertTrue(result.success)
        assertFalse(result.verified)
        assertEquals("NOT_VERIFIABLE", result.outputData["verificationOutcome"])
    }

    // -----------------------------------------------------------------
    // T15-030-05 — user case 5 / plan T15-030-03 (no-regression
    // control): a supported action that executes successfully still
    // reports success after the fix.
    // -----------------------------------------------------------------
    @Test
    fun t15_030_05_volumeDown_supportedAction_reportsSuccess() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t30_5", "အသံတိုး"),
            speakResponse = false
        )

        assertEquals("VOLUME_DOWN", response.intent)
        assertNotNull(response.executionResult)
        assertTrue(response.executionResult!!.success)
    }

    // -----------------------------------------------------------------
    // T15-030-06 — user case 6: the architecture DOES support genuine
    // verification (platform state read-back). Device Wi-Fi is already
    // OFF and the executed action requested OFF, so the read-back
    // confirms it: verified = true with a VERIFIED_SUCCESS outcome.
    // -----------------------------------------------------------------
    @Test
    fun t15_030_06_verifier_wifiStateMatch_verifiedSuccess() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiManager.setWifiEnabled(false)

        val result = resultVerifier.verify(
            actionType = "WIFI_TOGGLE",
            target = null,
            parameters = mapOf("enable" to false),
            initialSuccess = true
        )

        assertTrue(result.success)
        assertTrue(result.verified)
        assertEquals("VERIFIED_SUCCESS", result.outputData["verificationOutcome"])
    }
}
