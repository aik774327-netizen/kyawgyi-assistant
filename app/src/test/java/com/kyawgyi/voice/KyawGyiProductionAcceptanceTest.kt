package com.kyawgyi.voice

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.pipeline.contract.ExecutionStatus
import com.example.pipeline.verifier.ResultVerifier
import com.example.service.KyawGyiAccessibilityService
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import com.kyawgyi.diagnostics.DeviceCompatibilityEvaluator
import com.kyawgyi.diagnostics.KyawGyiResourceManager
import com.kyawgyi.pipeline.AndroidCapabilityMatrix
import com.kyawgyi.pipeline.CloudFallbackPolicy
import com.kyawgyi.pipeline.CommandIdempotencyManager
import com.kyawgyi.pipeline.ExecutionPolicy
import com.kyawgyi.pipeline.GeminiTrustBoundaryValidator
import com.kyawgyi.pipeline.SensitiveDataRedactor
import com.kyawgyi.pipeline.UniversalResultVerifier
import com.kyawgyi.pipeline.UniversalVerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * ITEM 28 — Production Acceptance Test (PAT) Suite
 *
 * Validates all 28 Production Repair & Hardening requirements across P0, P1, and P2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KyawGyiProductionAcceptanceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
        CommandIdempotencyManager.clearForTesting()
        KyawGyiResourceManager.getInstance(context).clearAllWorkloads()
        AssistantStateStore.getInstance().resetToIdle()
    }

    @Test
    fun pat01_capabilityMatrix_and_zeroFakeSuccess_universalVerifier() {
        // 1. AndroidCapabilityMatrix on API 34: Wi-Fi requires system panel or accessibility fallback
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        val wifiCap = AndroidCapabilityMatrix.evaluateCapability(context, "WIFI_TOGGLE", sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        assertFalse("Direct Wi-Fi toggle API is not available on Android 10+", wifiCap.nativeAndroidApiAvailable)
        assertTrue("System Settings UI panel is required on Android 10+", wifiCap.systemSettingsUiRequired)
        assertEquals(ExecutionPolicy.SYSTEM_UI_DELEGATION, wifiCap.recommendedExecutionPolicy)

        // 2. Unregistered action must be marked UNSUPPORTED
        val unknownCap = AndroidCapabilityMatrix.evaluateCapability(context, "HACK_KERNEL_ROOT")
        assertTrue(unknownCap.isUnsupported)
        assertEquals(ExecutionPolicy.SAFE_FAIL_UNSUPPORTED, unknownCap.recommendedExecutionPolicy)

        // 3. UniversalResultVerifier & ResultVerifier: Opening Wi-Fi panel when Wi-Fi is still OFF must return NOT_VERIFIABLE (NEVER fake SUCCESS)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        wifiManager.isWifiEnabled = false

        val universalVerifier = UniversalResultVerifier(context)
        val wifiReport = universalVerifier.verifyOutcome(
            actionType = "WIFI_TOGGLE",
            target = "ON",
            parameters = mapOf("enable" to true),
            initialDispatchSucceeded = true
        )
        assertEquals(UniversalVerificationOutcome.NOT_VERIFIABLE, wifiReport.outcome)
        assertFalse("Opening settings panel must NOT be reported as VERIFIED_SUCCESS", wifiReport.isVerifiedSuccess)

        val legacyResultVerifier = ResultVerifier(context).verify(
            actionType = "WIFI_TOGGLE",
            target = "ON",
            parameters = mapOf("enable" to true),
            initialSuccess = true
        )
        assertFalse("ResultVerifier must not convert NOT_VERIFIABLE Wi-Fi panel into verified=true", legacyResultVerifier.verified)
        assertEquals("NOT_VERIFIABLE", legacyResultVerifier.outputData["verificationOutcome"])

        // 4. IR_COMMAND one-way optical transmission must return NOT_VERIFIABLE
        val irReport = universalVerifier.verifyOutcome(
            actionType = "IR_COMMAND",
            target = "TV_POWER",
            parameters = emptyMap(),
            initialDispatchSucceeded = true
        )
        assertEquals(UniversalVerificationOutcome.NOT_VERIFIABLE, irReport.outcome)
        assertFalse(irReport.isVerifiedSuccess)
    }

    @Test
    fun pat02_geminiTrustBoundary_cloudFallbackSecurity_and_piiRedaction() {
        // 1. Sensitive PII in transcript (both ASCII and Myanmar digits) must block Cloud Fallback automatically
        val policyWithPhone = GeminiTrustBoundaryValidator.evaluateCloudFallbackEligibility(
            rawTranscript = "၀၉၇၈၉၅၃၂၁၀ ကို ငွေလွှဲပေးပါ",
            isOfflineModeRequested = false,
            userApprovedCloud = true
        )
        assertEquals(CloudFallbackPolicy.SENSITIVE_DATA_BLOCKED, policyWithPhone)

        // 2. SensitiveDataRedactor must SHA-256 hash/redact phone numbers, Myanmar digits, OTPs, and emails before logging
        val redacted = SensitiveDataRedactor.redactForAuditLog("Call 0978953210 or ၀၉၇၈၉၅၃၂၁၀ with OTP: 482910 email user@example.com")
        assertFalse("Raw phone number must not remain in audit log", redacted.contains("0978953210"))
        assertFalse("Raw Myanmar phone number must not remain in audit log", redacted.contains("၀၉၇၈၉၅၃၂၁၀"))
        assertFalse("Raw OTP must not remain in audit log", redacted.contains("482910"))
        assertFalse("Raw email must not remain in audit log", redacted.contains("user@example.com"))
        assertTrue(redacted.contains("[PHONE_SHA256:"))
        assertTrue(redacted.contains("[REDACTED_CREDENTIAL_SHA256:"))
        assertTrue(redacted.contains("[EMAIL_SHA256:"))

        // 3. GeminiTrustBoundaryValidator must reject non-allowlisted intents and shell injection parameters
        val maliciousOutput = """{"intent":"EXECUTE_SHELL","target":"su -c rm -rf /","confidence":0.99}"""
        val rejectedIntent = GeminiTrustBoundaryValidator.validateGeminiJsonOutput(maliciousOutput)
        assertFalse(rejectedIntent.isValid)

        val maliciousParamOutput = """{"intent":"OPEN_APP","target":"runtime.exec(sh -c id)","confidence":0.95}"""
        val rejectedParam = GeminiTrustBoundaryValidator.validateGeminiJsonOutput(maliciousParamOutput)
        assertFalse(rejectedParam.isValid)

        // 4. Valid allowlisted JSON proposal passes Trust Boundary
        val validJson = """{"intent":"OPEN_APP","target":"YouTube","confidence":0.92,"parameters":{"package":"com.google.android.youtube"}}"""
        val validProposal = GeminiTrustBoundaryValidator.validateGeminiJsonOutput(validJson)
        assertTrue(validProposal.isValid)
        assertEquals("OPEN_APP", validProposal.approvedIntent)
        assertEquals("YouTube", validProposal.sanitizedTarget)
    }

    @Test
    fun pat03_errorLearning_preservesHistoryOnResolution_supportsUnknownCause_and_fingerprint() {
        // 1. Insufficient evidence must classify as ErrorTaxonomy.UNKNOWN ("CAUSE = UNKNOWN") instead of inventing a cause
        val unknownEntry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Ambiguous subsystem failure",
            commandKey = "ambiguous_cmd",
            expectedResult = "Verify outcome",
            actualResult = "Unknown state",
            attemptedMethod = "Subsystem.invoke()",
            evidence = ""
        )
        assertEquals(ErrorLearningWorkflowEngine.ErrorTaxonomy.UNKNOWN, unknownEntry.errorType)
        assertEquals("CAUSE = UNKNOWN", unknownEntry.rootCause)
        assertEquals(ErrorLearningWorkflowEngine.ErrorRecoveryPolicy.SAFE_FAIL, unknownEntry.retryPolicy)

        // 2. Record a permission failure and verify FailureFingerprint & non-destructive resolution lifecycle (Item 13 & 14)
        val permEntry = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Call 0978953210",
            commandKey = "call_contact_test",
            expectedResult = "Place phone call",
            actualResult = "SecurityException: CALL_PHONE permission denied",
            attemptedMethod = "KyawGyiActionExecutor.callContact",
            evidence = "SecurityException: Permission denial: CALL_PHONE",
            intent = "CALL",
            targetPackage = "com.android.server.telecom"
        )
        assertEquals(ErrorLearningWorkflowEngine.ErrorLifecycleStatus.BLOCKED, permEntry.status)
        assertFalse("PII phone number must be redacted in stored task", permEntry.task.contains("0978953210"))
        assertTrue(permEntry.environmentFingerprint.toCompositeHash().contains("CALL"))

        // Verify it is currently blocked
        assertNotNull(ErrorLearningWorkflowEngine.evaluateRequestedAction("call_contact_test"))

        // ISSUE-034: resolution is possible ONLY through a typed VerificationResult
        // produced by a real check. Grant CALL_PHONE for real (Robolectric shadow),
        // then run the live verifier — its RUNTIME_PERMISSION branch re-checks
        // ContextCompat.checkSelfPermission and only then may the record resolve.
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.CALL_PHONE)
        val verifyResult = ErrorLearningWorkflowEngine.verifyBlockedMethod("call_contact_test", context)
        assertEquals(
            com.example.audit.VerificationOutcome.VERIFIED,
            verifyResult.outcome
        )
        assertNull("Method should no longer be blocked after resolution", ErrorLearningWorkflowEngine.evaluateRequestedAction("call_contact_test"))

        val allHistory = ErrorLearningWorkflowEngine.getAllErrorLogs()
        assertEquals("Historical error logs must NEVER be deleted when resolved", 2, allHistory.size)
        val resolvedEntry = allHistory.first { it.commandKey == "call_contact_test" }
        assertEquals(ErrorLearningWorkflowEngine.ErrorLifecycleStatus.RESOLVED, resolvedEntry.status)
        assertTrue(resolvedEntry.statusTransitionHistory.any { it.contains("CONDITION_CHANGED") })
        assertTrue(resolvedEntry.statusTransitionHistory.contains("RESOLVED"))
    }

    @Test
    fun pat04_idempotency_accessibilityBoundary_modelManifest_and_wakeWordHardening() {
        // 1. Item 18: Idempotency blocks rapid duplicate SMS/Call execution
        val key = CommandIdempotencyManager.computeIdempotencyKey("SEND_SMS", "091234567", "မင်္ဂလာပါ")
        assertTrue("First execution should be permitted", CommandIdempotencyManager.checkAndRegisterExecution(key, dedupWindowMs = 5000L, nowMs = 1000L))
        assertFalse("Duplicate execution within 5s window must be blocked", CommandIdempotencyManager.checkAndRegisterExecution(key, dedupWindowMs = 5000L, nowMs = 2500L))
        assertTrue("Execution after window expires is permitted", CommandIdempotencyManager.checkAndRegisterExecution(key, dedupWindowMs = 5000L, nowMs = 7000L))

        // 2. Item 11: Accessibility Service boundary blocks financial & permission-installer packages
        assertFalse(KyawGyiAccessibilityService.isPackageAllowedForAccessibilityAutomation("com.kbzbank.kpaycustomer"))
        assertFalse(KyawGyiAccessibilityService.isPackageAllowedForAccessibilityAutomation("com.wave.money"))
        assertFalse(KyawGyiAccessibilityService.isPackageAllowedForAccessibilityAutomation("com.google.android.permissioncontroller"))
        assertTrue(KyawGyiAccessibilityService.isPackageAllowedForAccessibilityAutomation("com.android.settings"))

        // 3. Item 21, 22, 26: ModelManager Manifest, Atomic Install & Burmese Linguistic Quality Validation
        val modelManager = ModelManager(context)
        val validationReport = modelManager.validateBurmeseLinguisticAndModelQuality()
        assertTrue(validationReport.pronunciationCoverageVerified)
        assertTrue(validationReport.codeSwitchingVerified)
        assertTrue(validationReport.numbersAndPhoneVerified)
        assertTrue(validationReport.currencyExpansionVerified)
        assertTrue(validationReport.dateTimeExpansionVerified)

        // Test atomic model install with SHA-256 verification
        val stagedFile = File(context.cacheDir, "staged_test_model.onnx")
        stagedFile.writeBytes("ONNX_VALID_MODEL_HEADER_BYTES_1234567890".toByteArray())
        val actualHash = modelManager.computeFileSha256(stagedFile)
        val testManifest = ModelManager.SHERPA_ONNX_BURMESE_MANIFEST.copy(sha256Checksum = actualHash)
        val installRes = modelManager.verifyAndInstallStagedModelAtomically(
            manifest = testManifest,
            stagedFile = stagedFile,
            targetFileName = "test-verified-model.onnx",
            enforceStrictHash = true
        )
        assertTrue("Atomic model install with matching SHA-256 and valid header should succeed", installRes.isSuccess)
        assertTrue(installRes.httpsVerified && installRes.checksumVerified && installRes.smokeTestPassed)

        // 4. Item 23, 24, 25, 27: Resource Manager, Configurable Echo Hangover, Wake Word TTS Self-Trigger Guard & Compatibility Matrix
        val resourceManager = KyawGyiResourceManager.getInstance(context)
        val ttsSlot = resourceManager.acquireWorkloadSlot(KyawGyiResourceManager.AiWorkloadType.TTS_SYNTHESIS)
        assertTrue(ttsSlot.granted)
        val sttDuringTts = resourceManager.acquireWorkloadSlot(KyawGyiResourceManager.AiWorkloadType.STT_RECOGNITION)
        assertFalse("STT must be blocked while TTS synthesis workload is active", sttDuringTts.granted)
        resourceManager.releaseWorkloadSlot(KyawGyiResourceManager.AiWorkloadType.TTS_SYNTHESIS)

        val aecManager = AudioFocusAndAecManager.getInstance(context)
        aecManager.setEchoHangoverDurationMs(400L)
        assertEquals(400L, aecManager.getEchoHangoverDurationMs())

        // Wake Word must block self-trigger when TTS is speaking
        val wakeEngine = KyawGyiWakeWordEngine(context = context, cooldownMs = 500L)
        aecManager.onTtsPlaybackStarted()
        val blockedWake = wakeEngine.processTranscriptAndTrigger("ဟယ်လို ကျော်ကြီး ဝိုင်ဖိုင် ဖွင့်ပေးပါ", playEarcon = false)
        assertFalse("Wake word must not self-trigger while TTS is speaking", blockedWake.isDetected)
        assertTrue(blockedWake.blockedByTtsEcho)
        aecManager.onTtsPlaybackStopped()

        val compatReport = DeviceCompatibilityEvaluator(context).evaluate()
        assertEquals(34, compatReport.androidSdkInt)
        assertTrue(compatReport.activeAudioOutputRoute.isNotBlank())
    }

    @Test
    fun pat05_directSettingsAndPermissionRouter_systematicVerification() {
        val router = com.example.router.DirectSettingsRouter.getInstance(context)
        val requiredPerms = com.example.utils.KyawGyiPermissionManager.getRequiredPermissions().toSet()

        // 1. Verify all core runtime permissions are registered in KyawGyiPermissionManager
        assertTrue(requiredPerms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(requiredPerms.contains(android.Manifest.permission.CALL_PHONE))
        assertTrue(requiredPerms.contains(android.Manifest.permission.SEND_SMS))
        assertTrue(requiredPerms.contains(android.Manifest.permission.READ_CONTACTS))
        assertTrue(requiredPerms.contains(android.Manifest.permission.CAMERA))
        assertTrue(requiredPerms.contains(android.Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(requiredPerms.contains(android.Manifest.permission.BLUETOOTH_CONNECT))

        // 2. Verify DirectSettingsRouter builds exact package-scoped deep-link Intents
        val expectedPackageUri = "package:${context.packageName}"
        val overlayIntent = router.buildDirectIntent(com.example.router.SettingsDestination.OVERLAY_PERMISSION)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, overlayIntent.action)
        assertEquals(expectedPackageUri, overlayIntent.dataString)

        val writeSettingsIntent = router.buildDirectIntent(com.example.router.SettingsDestination.WRITE_SETTINGS)
        assertEquals(Settings.ACTION_MANAGE_WRITE_SETTINGS, writeSettingsIntent.action)
        assertEquals(expectedPackageUri, writeSettingsIntent.dataString)

        val batteryIntent = router.buildDirectIntent(com.example.router.SettingsDestination.BATTERY_OPTIMIZATION)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, batteryIntent.action)
        assertEquals(expectedPackageUri, batteryIntent.dataString)

        val alarmIntent = router.buildDirectIntent(com.example.router.SettingsDestination.EXACT_ALARM)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, alarmIntent.action)
        assertEquals(expectedPackageUri, alarmIntent.dataString)

        val notifIntent = router.buildDirectIntent(com.example.router.SettingsDestination.APP_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifIntent.action)
        assertEquals(context.packageName, notifIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        // 3. Verify Burmese & English natural query resolution to exact SettingsDestination
        assertEquals(
            com.example.router.SettingsDestination.MICROPHONE_PERMISSION,
            router.resolveSettingsDestination("မိုက်ခရိုဖုန်း ခွင့်ပြုချက် ဖွင့်ပေးပါ")
        )
        assertEquals(
            com.example.router.SettingsDestination.CALL_PHONE_PERMISSION,
            router.resolveSettingsDestination("ဖုန်းခေါ်ဆိုခွင့် ခွင့်ပြုချက် စစ်မယ်")
        )
        assertEquals(
            com.example.router.SettingsDestination.SEND_SMS_PERMISSION,
            router.resolveSettingsDestination("SMS စာတို ခွင့်ပြုချက် ဖွင့်ပါ")
        )
        assertEquals(
            com.example.router.SettingsDestination.OVERLAY_PERMISSION,
            router.resolveSettingsDestination("မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် overlay setting ဖွင့်ပါ")
        )
        assertEquals(
            com.example.router.SettingsDestination.ACCESSIBILITY,
            router.resolveSettingsDestination("Accessibility အလိုအလျောက် ထိန်းချုပ်ခွင့် ဖွင့်မယ်")
        )
        assertEquals(
            com.example.router.SettingsDestination.OEM_AUTOSTART,
            router.resolveSettingsDestination("OEM Auto-Start ဆက်တင် ဖွင့်ပေးပါ")
        )
        assertEquals(
            com.example.router.SettingsDestination.TTS_SETTINGS,
            router.resolveSettingsDestination("TTS အသံထွက် ဆက်တင် သွားမယ်")
        )
        assertEquals(
            com.example.router.SettingsDestination.BLUETOOTH_PERMISSION,
            router.resolveSettingsDestination("ဘလူးတုသ် ခွင့်ပြုချက် ဖွင့်ပါ")
        )

        // 4. Verify action-to-required-destination mapping and execution report
        assertEquals(
            com.example.router.SettingsDestination.CALL_PHONE_PERMISSION,
            router.getRequiredDestinationForAction("CALL")
        )
        assertEquals(
            com.example.router.SettingsDestination.SEND_SMS_PERMISSION,
            router.getRequiredDestinationForAction("SEND_SMS")
        )
        val report = router.openSettingsWithReport(com.example.router.SettingsDestination.OVERLAY_PERMISSION)
        assertTrue(report.dispatchSucceeded)
        assertTrue(report.usedPackageUriDeepLink)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, report.resolvedIntentAction)
    }
}
