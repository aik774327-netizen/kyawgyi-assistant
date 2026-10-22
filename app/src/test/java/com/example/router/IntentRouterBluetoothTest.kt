package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.MyanmarNegationGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIX-04 regression tests (tests-first) — Bluetooth reachability.
 *
 * Defect (B3 trace): KyawGyiIntentRouter had NO Bluetooth intent —
 * CommandIntent covered flashlight and Wi-Fi only — so "ဘလူးတူးသ်
 * ဖွင့်" parsed to UNKNOWN on the main voice path and nothing
 * happened on the user's phone. These tests pin Bluetooth parsing
 * and the negation-guard interplay through the real SmartRouter
 * entry ("မဖွင့်ပါနဲ့" must veto, never become ON).
 *
 * Pre-fix expectation: T16-04-05 FAILS (UNKNOWN). T16-04-06 already
 * passes pre-fix via the ISSUE-013 guard and must keep passing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IntentRouterBluetoothTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // T16-04-05: Bluetooth ON/OFF parse to real intents (spaced and
    // unspaced Burmese forms, matching how STT output arrives).
    @Test
    fun t16_04_05_bluetoothParses() {
        val intentRouter = KyawGyiIntentRouter()
        assertEquals(
            CommandIntent.TURN_ON_BLUETOOTH,
            intentRouter.parseCommand("ဘလူးတူးသ် ဖွင့်").intent
        )
        assertEquals(
            CommandIntent.TURN_OFF_BLUETOOTH,
            intentRouter.parseCommand("ဘလူးတူးသ် ပိတ်").intent
        )
        assertEquals(
            CommandIntent.TURN_ON_BLUETOOTH,
            intentRouter.parseCommand("ဘလူးတူးသ်ဖွင့်").intent
        )
    }

    // T16-04-06: negated Bluetooth ON is vetoed at the SmartRouter
    // entry and never becomes an ON intent.
    @Test
    fun t16_04_06_negatedBluetoothVetoed() = runBlocking {
        val router = KyawGyiSmartRouter(context)
        val response = router.processVoiceInput("ဘလူးတူးသ် မဖွင့်ပါနဲ့")
        assertEquals(MyanmarNegationGuard.NO_ACTION_RESPONSE_MM, response)
    }
}
