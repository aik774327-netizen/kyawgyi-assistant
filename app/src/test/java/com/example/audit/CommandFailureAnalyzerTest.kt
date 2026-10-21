package com.example.audit

import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FailureCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandFailureAnalyzerTest {

    @Test
    fun testClassifyTechnicalFailure() {
        val npe = NullPointerException("View hierarchy root node was null")
        val analysis = CommandFailureAnalyzer.classifyFailure(
            commandText = "scroll down",
            commandType = "SCROLL",
            exception = npe,
            rawDetail = "NullPointerException inside AccessibilityNodeInfo"
        )

        assertEquals(FailureCategory.TECHNICAL, analysis.category)
        assertTrue(analysis.categoryLabelMm.contains("နည်းပညာပိုင်း"))
        assertTrue(analysis.reasonMm.isNotEmpty())
        assertTrue(analysis.suggestedAlternativeMm.isNotEmpty())
    }

    @Test
    fun testClassifyResourceFailure() {
        val timeoutException = java.net.SocketTimeoutException("Failed to connect to speech server")
        val analysis = CommandFailureAnalyzer.classifyFailure(
            commandText = "သီချင်းဖွင့်ပါ",
            commandType = "PLAY_MUSIC",
            exception = timeoutException,
            rawDetail = "Network timeout - device offline"
        )

        assertEquals(FailureCategory.RESOURCE, analysis.category)
        assertTrue(analysis.categoryLabelMm.contains("အရင်းအမြစ်"))
        assertTrue(analysis.reasonMm.contains("အင်တာနက်လိုင်း"))
        assertTrue(analysis.suggestedAlternativeMm.contains("Wi-Fi") || analysis.suggestedAlternativeMm.contains("Mobile Data"))
    }

    @Test
    fun testClassifyPragmaticContextualFailure() {
        val secException = SecurityException("Permission Denial: requires android.permission.CALL_PHONE")
        val analysis = CommandFailureAnalyzer.classifyFailure(
            commandText = "ဖုန်းခေါ်ပါ",
            commandType = "CALL",
            exception = secException,
            rawDetail = "CALL_PHONE permission denied by user"
        )

        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL, analysis.category)
        assertTrue(analysis.categoryLabelMm.contains("လက်တွေ့မကိုက်ညီမှု"))
        assertTrue(analysis.reasonMm.contains("ခွင့်ပြုချက်"))
        assertTrue(analysis.suggestedAlternativeMm.contains("Settings") || analysis.suggestedAlternativeMm.contains("ခွင့်ပြုချက်"))
    }

    @Test
    fun testRecordAndDetectRepeatedFailure() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = AppDatabase.getDatabase(context)

        // Clear existing command logs
        db.commandLogDao().clearCommandLogs()

        val commandQuery = "မီးဖွင့်ပါ"

        // 1. Initial check - no previous failure should exist
        val initialCheck = CommandFailureAnalyzer.checkPreviousFailure(context, commandQuery)
        assertNull(initialCheck)

        // 2. Record a failure
        val recordedEntity = CommandFailureAnalyzer.recordFailure(
            context = context,
            commandText = commandQuery,
            commandType = "SMART_HOME",
            rawDetail = "endpoint not configured for living room light",
            isRepeated = false
        )

        assertNotNull(recordedEntity)
        assertEquals("FAILED", recordedEntity.status)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL.code, recordedEntity.failureCategory)
        assertTrue(recordedEntity.failureReason.isNotEmpty())

        // Verify in Room Database
        val recentFailure = db.commandLogDao().findRecentFailure(commandQuery)
        assertNotNull(recentFailure)
        assertEquals(commandQuery, recentFailure?.commandText)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL.code, recentFailure?.failureCategory)

        // 3. User requests the exact same failed action again -> must trigger Repeat Warning
        val repeatWarning = CommandFailureAnalyzer.checkPreviousFailure(context, commandQuery)
        assertNotNull(repeatWarning)
        assertTrue(repeatWarning!!.isRepeated)
        assertTrue(repeatWarning.warningSpeechMm.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(repeatWarning.explanationMm.contains(FailureCategory.PRAGMATIC_CONTEXTUAL.burmeseLabel))
        assertTrue(repeatWarning.alternativeAdviceMm.isNotEmpty())
    }
}
