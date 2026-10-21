package com.example.security

import com.example.actions.CommandResult
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionPermissionGatekeeperTest {

    private lateinit var stateStore: AssistantStateStore
    private lateinit var gatekeeper: ActionPermissionGatekeeper

    @Before
    fun setup() {
        stateStore = AssistantStateStore.getInstance()
        stateStore.resetToIdle()
        gatekeeper = ActionPermissionGatekeeper(stateStore)
    }

    @Test
    fun testHighRiskActionDetection() {
        assertTrue("Call action must be high-risk", gatekeeper.isHighRisk("CALL", "Aung Aung"))
        assertTrue("SMS action must be high-risk", gatekeeper.isHighRisk("SMS", "Daw Mya"))
        assertTrue("Factory reset must be high-risk", gatekeeper.isHighRisk("SYSTEM", "factory_reset"))
        assertTrue("Financial payment must be high-risk", gatekeeper.isHighRisk("PAYMENT", "ငွေလွှဲ"))

        assertFalse("Volume control is safe", gatekeeper.isHighRisk("SYSTEM_CONTROL", "VOLUME_UP"))
        assertFalse("App launch is safe", gatekeeper.isHighRisk("OPEN_APP", "YouTube"))
    }

    @Test
    fun testAffirmativeVoiceConfirmation_Within5s_SucceedsAndTransitionsToExecuting() = runBlocking {
        stateStore.resetToIdle()
        assertEquals(AssistantState.IDLE, stateStore.getCurrentState())

        val executionDeferred = async {
            gatekeeper.executeWithSafetyGate(
                actionType = "CALL",
                target = "ဦးဘ",
                promptMM = "ဖုန်းခေါ်မည် သေချာပါသလားခင်ဗျာ။"
            ) {
                CommandResult(
                    isSuccess = true,
                    action = "CALL",
                    target = "ဦးဘ",
                    userMessage = "ဖုန်းခေါ်ဆိုနေပါသည်ခင်ဗျာ။"
                )
            }
        }

        // Wait briefly for gatekeeper to engage
        delay(100)
        assertTrue(gatekeeper.isAwaitingConfirmation())
        assertEquals(AssistantState.AWAITING_CONFIRMATION, stateStore.getCurrentState())

        // User speaks affirmative confirmation ("အတည်ပြုပါတယ်")
        val submitted = gatekeeper.submitVoiceInput("အတည်ပြုပါတယ်")
        assertTrue("Voice input should be accepted", submitted)

        val result = executionDeferred.await()
        assertTrue("Action execution should succeed", result.isSuccess)
        assertEquals(AssistantState.EXECUTING, stateStore.getCurrentState())
    }

    @Test
    fun testAlternativeAffirmativeKeyword_Within5s_Succeeds() = runBlocking {
        val executionDeferred = async {
            gatekeeper.executeWithSafetyGate(
                actionType = "SMS",
                target = "မောင်မောင်",
                promptMM = "မက်ဆေ့ခ်ျ ပို့မည် သေချာပါသလားခင်ဗျာ။"
            ) {
                CommandResult(
                    isSuccess = true,
                    action = "SMS",
                    target = "မောင်မောင်",
                    userMessage = "မက်ဆေ့ခ်ျ ပို့ပြီးပါပြီ။"
                )
            }
        }

        delay(100)
        assertTrue(gatekeeper.isAwaitingConfirmation())

        // User speaks "လုပ်ပါ"
        val submitted = gatekeeper.submitVoiceInput("လုပ်ပါ")
        assertTrue(submitted)

        val result = executionDeferred.await()
        assertTrue(result.isSuccess)
        assertEquals(AssistantState.EXECUTING, stateStore.getCurrentState())
    }

    @Test
    fun testUserRejection_CancelsImmediatelyAndResetsToIdle() = runBlocking {
        stateStore.resetToIdle()

        val executionDeferred = async {
            gatekeeper.executeWithSafetyGate(
                actionType = "CALL",
                target = "ကိုစိုး",
                promptMM = "ဖုန်းခေါ်မည် သေချာပါသလားခင်ဗျာ။"
            ) {
                CommandResult(
                    isSuccess = true,
                    action = "CALL",
                    target = "ကိုစိုး",
                    userMessage = "ဖုန်းခေါ်ဆိုနေပါသည်ခင်ဗျာ။"
                )
            }
        }

        delay(100)
        assertTrue(gatekeeper.isAwaitingConfirmation())

        // User speaks rejection keyword ("မလုပ်နဲ့")
        val submitted = gatekeeper.submitVoiceInput("မလုပ်နဲ့")
        assertTrue("Rejection keyword should be accepted to resolve gate", submitted)

        val result = executionDeferred.await()
        assertFalse("Action should not succeed when rejected", result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
        assertEquals(AssistantState.IDLE, stateStore.getCurrentState())
    }

    @Test
    fun testStrict5sTimeout_AutoCancelsAndResetsToIdle() = runBlocking {
        stateStore.resetToIdle()

        val startTime = System.currentTimeMillis()
        val result = gatekeeper.executeWithSafetyGate(
            actionType = "CALL",
            target = "ဦးမြ",
            promptMM = "ဖုန်းခေါ်မည် သေချာပါသလားခင်ဗျာ။"
        ) {
            CommandResult(
                isSuccess = true,
                action = "CALL",
                target = "ဦးမြ",
                userMessage = "ဖုန်းခေါ်ဆိုနေပါသည်ခင်ဗျာ။"
            )
        }
        val elapsed = System.currentTimeMillis() - startTime

        // Should auto-cancel upon 5-second timeout
        assertFalse(result.isSuccess)
        assertEquals("CONFIRMATION_TIMEOUT", result.errorCode)
        assertTrue("Elapsed time should be at least ~5000ms", elapsed >= 4500L)
        assertEquals(AssistantState.IDLE, stateStore.getCurrentState())
    }
}
