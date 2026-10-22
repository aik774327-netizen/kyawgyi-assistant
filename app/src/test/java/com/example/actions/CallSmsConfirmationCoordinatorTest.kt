package com.example.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.router.KyawGyiIntentRouter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-029 regression tests (Phase 1.5 plan §5), IDs T15-029-*.
 *
 * Confirmation matching in CallSmsConfirmationCoordinator was
 * substring-based and KyawGyiIntentRouter.parseAndExecute evaluated
 * confirmation BEFORE cancellation, so a mixed reply such as
 * "ဟုတ်ကဲ့ မခေါ်ပါနဲ့" ("yes, but don't call") — which contains the
 * affirmative keyword "ဟုတ်ကဲ့" — resolved to CONFIRM and executed
 * the staged call. Cancellation/negation must take precedence, and a
 * reply matching a cancellation form must never count as a
 * confirmation.
 *
 * The coordinator is a singleton; every test clears pending state
 * before and after itself so no staged action can leak between tests
 * (a staged pending action is consumed by whichever router runs next).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallSmsConfirmationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var coordinator: CallSmsConfirmationCoordinator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = CallSmsConfirmationCoordinator.getInstance()
        coordinator.clearPending()
    }

    @After
    fun tearDown() {
        coordinator.clearPending()
    }

    private fun stageCall() {
        coordinator.stageCall("09123456789", "မောင်မောင်", "09123456789")
        assertTrue("staging must register a pending action", coordinator.hasPendingAction())
    }

    // T15-029-03 — predicate level: a mixed reply is a cancellation,
    // never a confirmation; pure forms keep their meaning.
    @Test
    fun t15_029_03_mixedReply_isCancellation_neverConfirmation() {
        assertFalse(coordinator.isConfirmation("ဟုတ်ကဲ့ မခေါ်ပါနဲ့"))
        assertTrue(coordinator.isCancellation("ဟုတ်ကဲ့ မခေါ်ပါနဲ့"))
        assertFalse(coordinator.isConfirmation("မခေါ်ပါနဲ့"))

        assertTrue(coordinator.isConfirmation("ဟုတ်ကဲ့"))
        assertFalse(coordinator.isCancellation("ဟုတ်ကဲ့"))
        assertTrue(coordinator.isConfirmation("ခေါ်ပါ"))
    }

    // T15-029-01 — staged call + mixed reply through the real router:
    // the call is CANCELLED (cancellation message), nothing executes,
    // and no pending action remains.
    @Test
    fun t15_029_01_stagedCall_mixedReply_cancelsAndDoesNotExecute() {
        stageCall()
        val executor = KyawGyiActionExecutor(context)
        val router = KyawGyiIntentRouter()

        val (handled, response) = router.parseAndExecute("ဟုတ်ကဲ့ မခေါ်ပါနဲ့", executor)

        assertTrue(handled)
        assertTrue(
            "mixed reply must cancel the staged call, got: $response",
            response.contains("ပယ်ဖျက်လိုက်ပါပြီ")
        )
        assertFalse("pending action must be consumed by the cancellation", coordinator.hasPendingAction())
    }

    // T15-029-02 — staged call + pure affirmative reply still executes
    // through the confirmation path (no over-blocking of real
    // confirmations): the reply is handled, is NOT a cancellation,
    // and the pending action is consumed.
    @Test
    fun t15_029_02_stagedCall_affirmativeReply_executesConfirmationPath() {
        stageCall()
        val executor = KyawGyiActionExecutor(context)
        val router = KyawGyiIntentRouter()

        val (handled, response) = router.parseAndExecute("ဟုတ်ကဲ့", executor)

        assertTrue(handled)
        assertFalse(
            "pure affirmative must not be treated as cancellation, got: $response",
            response.contains("ပယ်ဖျက်လိုက်ပါပြီ")
        )
        assertFalse("pending action must be consumed by the confirmation", coordinator.hasPendingAction())
    }
}
