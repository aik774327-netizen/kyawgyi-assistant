package com.example.fallback

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.audit.CommandFailureAnalyzer
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
class AlternativeFlowManagerTest {

    @Test
    fun testAccessibilityPermissionFallbackFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flow = AlternativeFlowManager.resolveFlow(
            context = context,
            commandText = "စာမျက်နှာကို အောက်သို့ဆွဲပါ",
            commandType = "SCROLL",
            target = "SCREEN",
            failureCategory = FailureCategory.PRAGMATIC_CONTEXTUAL,
            failureDetail = "AccessibilityService not running or disabled"
        )

        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL, flow.category)
        assertEquals(RemediationType.SETTINGS_PERMISSION, flow.remediationType)
        assertTrue(flow.titleMm.contains("Accessibility"))
        assertTrue(flow.avoidedMethodMm.contains("ခွင့်ပြုချက် မရှိဘဲ"))
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, flow.fallbackIntentAction)
        assertNotNull(flow.createIntent())
    }

    @Test
    fun testAppNotInstalledWebFallbackFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flow = AlternativeFlowManager.resolveFlow(
            context = context,
            commandText = "ဖွင့် YouTube",
            commandType = "OPEN_APP",
            target = "YouTube",
            failureCategory = FailureCategory.PRAGMATIC_CONTEXTUAL,
            failureDetail = "App not installed: ActivityNotFoundException"
        )

        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL, flow.category)
        assertEquals(RemediationType.WEB_FALLBACK, flow.remediationType)
        assertTrue(flow.titleMm.contains("ဝဘ်ဆိုက်"))
        assertTrue(flow.avoidedMethodMm.contains("ထည့်သွင်းမထားသော") || flow.avoidedMethodMm.contains("မရှိသော အက်ပ်"))
        assertEquals(Intent.ACTION_VIEW, flow.fallbackIntentAction)
        assertEquals("https://www.youtube.com", flow.fallbackIntentUri)
        val intent = flow.createIntent()
        assertNotNull(intent)
        assertEquals("https://www.youtube.com", intent?.dataString)
    }

    @Test
    fun testOfflineNetworkResourceFallbackFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flow = AlternativeFlowManager.resolveFlow(
            context = context,
            commandText = "မင်္ဂလာပါ",
            commandType = "CONVERSATION",
            target = null,
            failureCategory = FailureCategory.RESOURCE,
            failureDetail = "Network timeout - speech server offline"
        )

        assertEquals(FailureCategory.RESOURCE, flow.category)
        assertEquals(RemediationType.OFFLINE_FASTPATH, flow.remediationType)
        assertTrue(flow.titleMm.contains("အော့ဖ်လိုင်း"))
        assertTrue(flow.avoidedMethodMm.contains("Cloud") || flow.avoidedMethodMm.contains("အင်တာနက်"))
        assertEquals(Settings.ACTION_WIFI_SETTINGS, flow.fallbackIntentAction)
    }

    @Test
    fun testTechnicalFailureDirectIntentFallbackFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flow = AlternativeFlowManager.resolveFlow(
            context = context,
            commandText = "ဆက်တင်ဖွင့်",
            commandType = "SYSTEM_SETTINGS",
            target = "SETTINGS",
            failureCategory = FailureCategory.TECHNICAL,
            failureDetail = "Internal Action Execution exception in UI node traversal"
        )

        assertEquals(FailureCategory.TECHNICAL, flow.category)
        assertEquals(RemediationType.DIRECT_INTENT, flow.remediationType)
        assertTrue(flow.titleMm.contains("တိုက်ရိုက်"))
        assertTrue(flow.avoidedMethodMm.contains("UI"))
    }

    @Test
    fun testRecordFailureWithRemediationAndRepeatAvoidance() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        db.commandLogDao().clearCommandLogs()

        val cmd = "ဖွင့် YouTube"

        // Record failure
        val logged = CommandFailureAnalyzer.recordFailure(
            context = context,
            commandText = cmd,
            commandType = "OPEN_APP",
            rawDetail = "ActivityNotFoundException: app not installed"
        )

        assertNotNull(logged)
        assertEquals("FAILED", logged.status)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL.code, logged.failureCategory)
        assertEquals(Intent.ACTION_VIEW, logged.remediationAction)
        assertEquals("https://www.youtube.com", logged.remediationUri)

        // Repeat check
        val repeat = CommandFailureAnalyzer.checkPreviousFailure(context, cmd)
        assertNotNull(repeat)
        assertTrue(repeat!!.isRepeated)
        assertNotNull(repeat.alternativeFlow)
        assertTrue(repeat.warningSpeechMm.contains("မအောင်မြင်ခဲ့သော နည်းလမ်း"))
        assertTrue(repeat.explanationMm.contains("ရှောင်လွှဲခဲ့သော နည်းလမ်း"))
    }
}
