package com.example.actions

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.example.audit.AuditLogger
import com.example.audit.AuditLogEntry
import com.example.ocr.FailureMemoryEngine
import com.example.router.KyawGyiDispatcher
import com.example.service.KyawGyiAccessibilityService
import kotlinx.coroutines.runBlocking
import org.junit.After
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

/**
 * ISSUE-027 regression tests (T15-027-*).
 *
 * [KyawGyiActionExecutor.closeApplication] cannot actually close another app
 * through Android public APIs — it navigates the device to the Home screen
 * (accessibility GLOBAL_ACTION_HOME and/or a Home intent) and may ask the
 * system to kill the target's background processes. These tests pin the
 * honest reporting contract:
 *  - isSuccess reflects ONLY whether the Home navigation was dispatched.
 *  - executionConfirmed stays false: the named app being closed is never
 *    verified, so success and verification must remain separate facts.
 *  - The user message must say the device went Home and must NOT claim the
 *    app "ပိတ်ပြီး" (was closed).
 *  - The AuditLogger entry must carry the same honest outcome (no
 *    unconditional isSuccess=true / confidence=1.0f).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloseApplicationHonestyTest {

    private lateinit var context: Context
    private lateinit var executor: KyawGyiActionExecutor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        executor = KyawGyiActionExecutor(context)
        runBlocking {
            FailureMemoryEngine.clearAllPersistently(context)
        }
        FailureMemoryEngine.initialize(context)
        // Precondition for the "no accessibility service" scenarios below:
        // the companion setter is private and no service connects under
        // Robolectric, so the instance must be null here. Asserted loudly
        // rather than assumed — if this ever fails, the scenarios' meaning
        // changed and the failures must not be read as ISSUE-027 evidence.
        assertNull(
            "precondition: no accessibility service instance in unit tests",
            KyawGyiAccessibilityService.instance
        )
    }

    @After
    fun tearDown() {
        assertNull(KyawGyiAccessibilityService.instance)
    }

    private fun newCloseAppAuditEntries(since: Int): List<AuditLogEntry> =
        AuditLogger.auditLogs.value.drop(since).filter { it.targetAction == "CLOSE_APP" }

    private fun consumeStartedActivity(): Intent? =
        Shadows.shadowOf(context as Application).nextStartedActivity

    /**
     * T15-027-01 (cases 1 + 5): only Home navigation occurs (no accessibility
     * service in tests). Dispatch success and closure verification are
     * asserted as SEPARATE facts — this test fails if they are conflated.
     */
    @Test
    fun t15_027_01_homeNavigationOnly_successIsDispatchOnly_closeNeverVerified() {
        val auditBefore = AuditLogger.auditLogs.value.size

        val result = executor.closeApplication("com.facebook.katana", "Facebook")

        assertEquals("CLOSE_APP", result.action)
        assertTrue(
            "Home intent was dispatched, so dispatch-level success is the honest outcome",
            result.isSuccess
        )
        assertFalse(
            "the named app being closed is never verified, so executionConfirmed must stay false",
            result.executionConfirmed
        )
        assertFalse(
            "user message must not claim the app was closed, was: ${result.userMessage}",
            result.userMessage.contains("ပိတ်ပြီး")
        )
        assertTrue(
            "user message must state the device returned to the Home screen",
            result.userMessage.contains("ပင်မစာမျက်နှာ")
        )

        // The Home dispatch itself is observable and must have happened.
        val homeIntent = consumeStartedActivity()
        assertNotNull("a Home intent must have been dispatched", homeIntent)
        assertEquals(Intent.ACTION_MAIN, homeIntent!!.action)
        assertTrue(homeIntent.categories.contains(Intent.CATEGORY_HOME))

        val entries = newCloseAppAuditEntries(auditBefore)
        assertEquals("exactly one CLOSE_APP audit entry expected", 1, entries.size)
        assertTrue(entries[0].isSuccess)
        assertTrue(
            "audit must not record certainty (1.0f) for an unverified close, was ${entries[0].confidence}",
            entries[0].confidence < 1.0f
        )
    }

    /**
     * T15-027-02 (case 2): even when every mechanism runs (Home intent +
     * killBackgroundProcesses attempt for a dotted package), the outcome
     * "the app closed" remains unverifiable and must be recorded as such.
     */
    @Test
    fun t15_027_02_unverifiableOutcome_auditAndResultNeverAssertVerifiedClosure() {
        val auditBefore = AuditLogger.auditLogs.value.size

        val result = executor.closeApplication("com.facebook.katana", "Facebook")

        assertEquals(mapOf("appName" to "Facebook"), result.parameters)
        assertFalse(
            "closure is unverifiable via public APIs; executionConfirmed must stay false",
            result.executionConfirmed
        )
        val entries = newCloseAppAuditEntries(auditBefore)
        assertEquals(1, entries.size)
        assertTrue(
            "audit details must record that closure was not verified, was: ${entries[0].details}",
            entries[0].details.contains("appClosureVerified=false")
        )
    }

    /**
     * T15-027-03 (case 3): execution failure — no accessibility service and
     * the Home intent cannot be dispatched at all. The outcome must be an
     * honest, audited failure, not a silent catch or a claimed success.
     */
    @Test
    fun t15_027_03_dispatchFailure_reportsHonestAuditedFailure() {
        val throwingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException("test: no launcher available")
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                throw ActivityNotFoundException("test: no launcher available")
            }
        }
        val failingExecutor = KyawGyiActionExecutor(throwingContext)
        val auditBefore = AuditLogger.auditLogs.value.size

        val result = failingExecutor.closeApplication("Facebook", "Facebook")

        assertFalse("nothing was dispatched, so success must be false", result.isSuccess)
        assertFalse(result.executionConfirmed)
        assertFalse("nothing was dispatched, so execution must not be marked started", result.executionStarted)
        assertEquals("HOME_NAVIGATION_FAILED", result.errorCode)
        assertTrue(
            "failure message must speak about the Home navigation, was: ${result.userMessage}",
            result.userMessage.contains("ပင်မစာမျက်နှာ")
        )

        val entries = newCloseAppAuditEntries(auditBefore)
        assertEquals("the failed dispatch must still be audited", 1, entries.size)
        assertFalse(entries[0].isSuccess)
        assertEquals(0.0f, entries[0].confidence)
    }

    /**
     * T15-027-04 (case 4): an unsupported utterance routed through the real
     * dispatcher must NOT reach closeApplication's semantics at all — no
     * handling, and no Home intent is dispatched as a side effect.
     */
    @Test
    fun t15_027_04_unsupportedInput_neverReachesCloseApplication() {
        val dispatcher = KyawGyiDispatcher(context)

        val (handled, _) = dispatcher.dispatchAndExecute("မနက်ဖြန် နေ့လယ်မှာ အစည်းအဝေး ရှိပါတယ်")

        assertFalse("a plain sentence is not a command", handled)
        assertNull(
            "no Home intent may be dispatched for an unsupported input",
            consumeStartedActivity()
        )
    }

    /**
     * T15-027-05 (case 6): the existing caller path (dispatcher → intent
     * router → executor) still handles a close command end-to-end, but the
     * caller-visible message is now the honest Home-navigation wording.
     */
    @Test
    fun t15_027_05_callerPath_closeCommand_handledWithHonestMessage() {
        val dispatcher = KyawGyiDispatcher(context)

        val (handled, message) = dispatcher.dispatchAndExecute("ကင်မရာ ပိတ်ပါ")

        assertTrue("a close command must still be handled by the existing caller path", handled)
        assertFalse(
            "caller-visible message must not claim the app was closed, was: $message",
            message.contains("ပိတ်ပြီး")
        )
        assertTrue(message.contains("ပင်မစာမျက်နှာ"))
        assertNotNull("the close flow must still dispatch the Home intent", consumeStartedActivity())
    }
}
