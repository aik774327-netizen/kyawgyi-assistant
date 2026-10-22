package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.MyanmarNegationGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIX-03 regression tests — Back/Scroll on the SmartRouter voice path.
 *
 * Device evidence B1: "နောက်ဆုတ် / အောက်ရွှေ့ / အပေါ်ရွှေ့" did
 * nothing, because navigation intents existed only in the
 * bubble/accessibility command table. These tests drive the real
 * KyawGyiSmartRouter.processVoiceInput with a fake
 * AccessibilityNavigationBridge and pin: correct dispatch, the
 * honest unavailable-service answer, dispatch-failure honesty, and
 * the negation veto interplay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartRouterNavigationTest {

    private class FakeBridge(
        var available: Boolean = true,
        var performResult: Boolean = true
    ) : AccessibilityNavigationBridge {
        val performed = mutableListOf<NavigationAction>()
        override fun isAvailable(): Boolean = available
        override fun perform(action: NavigationAction): Boolean {
            performed.add(action)
            return performResult
        }
    }

    private lateinit var context: Context
    private lateinit var bridge: FakeBridge
    private lateinit var router: KyawGyiSmartRouter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        bridge = FakeBridge()
        router = KyawGyiSmartRouter(context, navigationBridge = bridge)
    }

    // T16-03-01: scroll down (the user's exact phrase).
    @Test
    fun t16_03_01_scrollDownDispatches() = runBlocking {
        val response = router.processVoiceInput("အောက်ရွှေ့")
        assertEquals(listOf(NavigationAction.SCROLL_DOWN), bridge.performed)
        assertEquals(NavigationResponses.dispatchedMessage(NavigationAction.SCROLL_DOWN), response)
    }

    // T16-03-02: scroll up.
    @Test
    fun t16_03_02_scrollUpDispatches() = runBlocking {
        val response = router.processVoiceInput("အပေါ်ရွှေ့")
        assertEquals(listOf(NavigationAction.SCROLL_UP), bridge.performed)
        assertEquals(NavigationResponses.dispatchedMessage(NavigationAction.SCROLL_UP), response)
    }

    // T16-03-03: back — "နောက်ဆုတ်", absent from every pre-fix table.
    @Test
    fun t16_03_03_backDispatches() = runBlocking {
        val response = router.processVoiceInput("နောက်ဆုတ်")
        assertEquals(listOf(NavigationAction.BACK), bridge.performed)
        assertEquals(NavigationResponses.dispatchedMessage(NavigationAction.BACK), response)
    }

    // T16-03-04: accessibility service OFF -> enablement guidance,
    // and perform() is never called (no fake success).
    @Test
    fun t16_03_04_unavailableServiceGuides() = runBlocking {
        bridge.available = false
        val response = router.processVoiceInput("အောက်ရွှေ့")
        assertEquals(NavigationResponses.SERVICE_UNAVAILABLE_MM, response)
        assertTrue(bridge.performed.isEmpty())
    }

    // T16-03-05: dispatch failure is reported as failure.
    @Test
    fun t16_03_05_dispatchFailureHonest() = runBlocking {
        bridge.performResult = false
        val response = router.processVoiceInput("နောက်ဆုတ်")
        assertEquals(NavigationResponses.DISPATCH_FAILED_MM, response)
    }

    // T16-03-06: negated navigation is vetoed (guard or nav layer)
    // and never reaches the bridge.
    @Test
    fun t16_03_06_negatedNavigationVetoed() = runBlocking {
        val response = router.processVoiceInput("နောက်မဆုတ်ပါနဲ့")
        assertEquals(MyanmarNegationGuard.NO_ACTION_RESPONSE_MM, response)
        assertTrue(bridge.performed.isEmpty())
    }
}
