package com.kyawgyi.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kyawgyi.assistant.state.AssistantState
import com.kyawgyi.assistant.state.AssistantStateStore
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
class KyawGyiWakeWordEngineTest {

    private lateinit var context: Context
    private lateinit var stateStore: AssistantStateStore
    private lateinit var wakeWordEngine: KyawGyiWakeWordEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        stateStore = AssistantStateStore.getInstance()
        stateStore.resetToIdle()
        wakeWordEngine = KyawGyiWakeWordEngine(context, stateStore = stateStore, cooldownMs = 500L)
        wakeWordEngine.resetCooldown()
    }

    @Test
    fun testStandaloneWakePhrasesAndStateTransition() {
        val res1 = wakeWordEngine.processTranscriptAndTrigger("ကျော်ကြီး", playEarcon = false)
        assertTrue(res1.isDetected)
        assertEquals("", res1.trailingCommand)
        assertEquals(AssistantState.LISTENING, stateStore.getCurrentState())

        wakeWordEngine.resetCooldown()
        stateStore.resetToIdle()

        val res2 = wakeWordEngine.processTranscriptAndTrigger("ဟယ်လို ကျော်ကြီး", playEarcon = false)
        assertTrue(res2.isDetected)
        assertEquals("", res2.trailingCommand)
        assertEquals(AssistantState.LISTENING, stateStore.getCurrentState())
    }

    @Test
    fun testOneShotWakeWordWithInlineCommand() {
        val res = wakeWordEngine.evaluateUtterance("ကျော်ကြီး ဝိုင်ဖာ ဖွင့်ပေးပါ")
        assertTrue(res.isDetected)
        assertEquals("ကျော်ကြီး", res.matchedWakePhrase)
        assertEquals("ဝိုင်ဖိုင် ဖွင့်ပေးပါ", res.trailingCommand)

        val res2 = wakeWordEngine.evaluateUtterance("ဟယ်လို ကျော်ကြီး ဘလူးတု ပိတ်ပါ")
        assertTrue(res2.isDetected)
        assertEquals("ဟယ်လို ကျော်ကြီး", res2.matchedWakePhrase)
        assertEquals("ဘလူးတုသ် ပိတ်ပါ", res2.trailingCommand)
    }

    @Test
    fun testFuzzyPhoneticWakeWordRecognition() {
        val fuzzy1 = wakeWordEngine.evaluateUtterance("ကျောကြီး ဓာတ်မီး ဖွင့်ပါ")
        assertTrue(fuzzy1.isDetected)
        assertEquals("ဓာတ်မီး ဖွင့်ပါ", fuzzy1.trailingCommand)

        val englishWake = wakeWordEngine.evaluateUtterance("Hello Kyaw Gyi YouTube ဖွင့်ပေးပါ")
        assertTrue(englishWake.isDetected)
        assertEquals("ယူကျု ဖွင့်ပေးပါ", englishWake.trailingCommand)
    }

    @Test
    fun testNonWakeUtteranceRejected() {
        val nonWake = wakeWordEngine.evaluateUtterance("မနက်ဖြန် ရာသီဥတု ဘယ်လိုရှိလဲ")
        assertFalse(nonWake.isDetected)
    }
}
