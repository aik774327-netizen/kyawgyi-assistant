package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-013 regression tests for the in-app voice funnel (entry point
 * A2, KyawGyiSmartRouter.processVoiceInput), ID T15-013-05.
 *
 * The plan (§2.1, T15-013-05) notes SmartRouter has no existing test
 * harness; this file constructs it under Robolectric. A prohibitive
 * negation must be vetoed before every matching/fuzzy layer of the
 * router (Google Home, URL fuzzy 0.65, shortcuts, IntentRouter,
 * semantic, app dictionary) and answered with the honest no-action
 * response. Pre-fix, "မဖွင့်ပါနဲ့ Facebook" executed OPEN_FACEBOOK
 * (Phase 1 audit §4.2 row 8, column V).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartRouterNegationVetoTest {

    private lateinit var context: Context
    private lateinit var router: KyawGyiSmartRouter

    /** The honest no-action response a vetoed command must produce. */
    private val vetoResponse = "နားလည်ပါပြီခင်ဗျာ။ အဲဒီ လုပ်ဆောင်ချက်ကို မလုပ်ဆောင်ပါဘူးခင်ဗျာ။"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        router = KyawGyiSmartRouter(context)
    }

    // T15-013-05a — negated open is vetoed, not executed.
    @Test
    fun t15_013_05a_negatedFacebookOpen_vetoed() = runBlocking {
        val response = router.processVoiceInput("မဖွင့်ပါနဲ့ Facebook")
        assertEquals(vetoResponse, response)
    }

    // T15-013-05b — Wi-Fi negation (audit §4.2 closing example): on the
    // voice path this previously reached the settings/app layers; it
    // must now be vetoed before any of them.
    @Test
    fun t15_013_05b_negatedWifiOpen_vetoed() = runBlocking {
        val response = router.processVoiceInput("ဝိုင်ဖိုင် မဖွင့်ပါနဲ့")
        assertEquals(vetoResponse, response)
    }

    // T15-013-05c — affirmative control: a genuine open command still
    // flows through the router and returns a real (non-veto) response.
    @Test
    fun t15_013_05c_affirmativeFacebookOpen_notVetoed() = runBlocking {
        val response = router.processVoiceInput("Facebook ဖွင့်ပေးပါ")
        assertTrue("response must not be blank", response.isNotBlank())
        assertNotEquals(vetoResponse, response)
    }
}
