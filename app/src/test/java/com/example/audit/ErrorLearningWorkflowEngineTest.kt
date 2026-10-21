package com.example.audit

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorLearningWorkflowEngineTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ErrorLearningWorkflowEngine.initialize(context)
        ErrorLearningWorkflowEngine.clearAllForTesting()
    }

    @Test
    fun testRecordErrorLogAndThreeFactorAnalysis() {
        val log = ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Enable Wi-Fi via Accessibility toggle",
            commandKey = "ဝိုင်ဖိုင် ဖွင့်ပေးပါ",
            expectedResult = "Wi-Fi toggles to ON",
            actualResult = "SecurityException: Accessibility service permission denied",
            attemptedMethod = "WifiAccessibilityHelper.toggleWifi(true)",
            evidence = "SecurityException: Accessibility permission is disabled in Settings"
        )

        assertEquals("ER-001", log.errorId)
        assertEquals(ErrorLearningWorkflowEngine.ExecutionOutcome.FAIL, log.result)
        assertEquals(
            ErrorLearningWorkflowEngine.ErrorTaxonomy.ACCESS_PERMISSION_ERROR,
            log.errorType
        )
        assertTrue(log.threeFactorAnalysis.technicalCause.contains("SecurityManager"))
        assertTrue(log.threeFactorAnalysis.resourceLimitation.contains("permission"))
        assertTrue(log.threeFactorAnalysis.realWorldMismatch.contains("Settings"))
        assertEquals("WifiAccessibilityHelper.toggleWifi(true)", log.doNotReuseMethod)

        // Verify JSON Persistence Serialization Round-Trip
        val json = log.toJsonObject()
        val restored = ErrorLearningWorkflowEngine.ErrorLogEntry.fromJsonObject(json)
        assertEquals(log.errorId, restored.errorId)
        assertEquals(log.commandKey, restored.commandKey)
        assertEquals(log.rootCause, restored.rootCause)

        val report = log.toFormattedReport()
        assertTrue(report.contains("Error ID: ER-001"))
        assertTrue(report.contains("Technical Cause:"))
        assertTrue(report.contains("Resource / Source Limitation:"))
        assertTrue(report.contains("Real-World / Practical Mismatch:"))
        assertTrue(report.contains("Result: FAIL"))
    }

    @Test
    fun testFailedMethodMemoryAndLiveConditionVerifier() {
        // 1. Ensure Accessibility is initially disabled in Settings.Secure
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ""
        )

        ErrorLearningWorkflowEngine.recordErrorLog(
            task = "Toggle Bluetooth via Accessibility",
            commandKey = "ဘလူးတုသ် ဖွင့်ပါ",
            expectedResult = "Bluetooth toggles ON",
            actualResult = "Accessibility service disabled",
            attemptedMethod = "BluetoothAccessibilityHelper",
            evidence = "Accessibility service not enabled"
        )

        // 2. Requesting the same failed action while Accessibility is still off triggers mandatory warning
        val warning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = "ဘလူးတုသ် ဖွင့်ပါ",
            context = context
        )
        assertNotNull(warning)
        assertTrue(warning!!.warningBanner.startsWith("This is a previously unsuccessful action."))
        assertEquals("BluetoothAccessibilityHelper", warning.blockedMethod)

        // 3. Simulate user enabling the Accessibility Service in Android Settings
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "${context.packageName}/.service.KyawGyiAccessibilityService"
        )

        // 4. Live Condition Verifier detects the real environment change and automatically unblocks!
        val afterEnableWarning = ErrorLearningWorkflowEngine.evaluateRequestedAction(
            commandKey = "ဘလူးတုသ် ဖွင့်ပါ",
            context = context
        )
        assertNull("Live Condition Verifier should automatically unblock when Accessibility is enabled", afterEnableWarning)
    }
}
