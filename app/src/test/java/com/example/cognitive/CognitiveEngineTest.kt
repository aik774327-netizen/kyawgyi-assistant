package com.example.cognitive

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.SemanticIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CognitiveEngineTest {

    private lateinit var cognitiveEngine: CognitiveEngine

    @Before
    fun setUp() {
        cognitiveEngine = CognitiveEngine()
        cognitiveEngine.reset()
    }

    // 1. “YouTube ဖွင့်”
    @Test
    fun testCase01_OpenYouTube() {
        val input = "YouTube ဖွင့်"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("YOUTUBE", result.target)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertTrue(result.actionPlan.isExecutable)
        assertEquals("YOUTUBE", cognitiveEngine.workingMemory.lastApp)
    }

    // 2. Multi-turn reference: “YouTube ဖွင့်” -> “အဲဒီမှာ သီချင်းဖွင့်”
    @Test
    fun testCase02_PlayMusicInReferencedContainer() {
        // Turn 1
        cognitiveEngine.process("YouTube ဖွင့်")

        // Turn 2
        val input = "အဲဒီမှာ သီချင်းဖွင့်"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.PLAY_MUSIC, result.intent)
        assertTrue(result.resolvedReferences.containsKey("အဲဒီမှာ"))
        assertEquals("YOUTUBE", result.resolvedReferences["အဲဒီမှာ"])
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
    }

    // 3. “အဲဒီသီချင်း ပြန်ဖွင့်”
    @Test
    fun testCase03_ReplayPreviousSongReference() {
        // Turn 1: Play specific track
        cognitiveEngine.workingMemory.lastSong = "အလွမ်းည"
        cognitiveEngine.workingMemory.lastMedia = "အလွမ်းည"

        // Turn 2
        val input = "အဲဒီသီချင်း ပြန်ဖွင့်"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.PLAY_MUSIC, result.intent)
        assertEquals("အလွမ်းည", result.resolvedReferences["MEDIA_REFERENCE"])
    }

    // 4. “အသံနည်းနည်းတိုး”
    @Test
    fun testCase04_DecreaseVolumeSmallStep() {
        val input = "အသံနည်းနည်းတိုး"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.VOLUME_DOWN, result.intent)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 5. “နောက်သီချင်းပြောင်း”
    @Test
    fun testCase05_NextSongChange() {
        val input = "နောက်သီချင်းပြောင်း"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.NEXT_TRACK, result.intent)
        assertEquals("NEXT", result.target)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
    }

    // 6. “မနက်ဖြန် Alarm ထား” -> Missing time -> ASK_FOR_CLARIFICATION
    @Test
    fun testCase06_AlarmMissingTime_Clarification() {
        val input = "မနက်ဖြန် Alarm ထား"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.SET_ALARM, result.intent)
        assertEquals(ConfidenceLevel.ASK_FOR_CLARIFICATION, result.confidenceLevel)
        assertTrue(result.missingInformation.contains("ALARM_TIME"))
        assertNotNull(result.actionPlan.clarificationPrompt)
        assertTrue(result.actionPlan.clarificationPrompt?.contains("ဘယ်အချိန်") == true)
    }

    // 7. “မနက်ဖြန် မနက် ၇ နာရီ Alarm ထား” -> SET_ALARM
    @Test
    fun testCase07_AlarmCompleteWithTime() {
        val input = "မနက်ဖြန် မနက် ၇ နာရီ Alarm ထား"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.SET_ALARM, result.intent)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertTrue(result.missingInformation.isEmpty())
    }

    // 8. “မေမေကို ဖုန်းဆက်” -> High confidence with contact
    @Test
    fun testCase08_CallNamedContact() {
        val input = "မေမေကို ဖုန်းဆက်"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.CALL_CONTACT, result.intent)
        assertEquals("မေမေ", result.target)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
    }

    // 9. Multi-step plan: “YouTube ဖွင့်ပြီး သီချင်းဖွင့်ပြီး အသံလျှော့”
    @Test
    fun testCase09_MultiStepPlan() {
        val input = "YouTube ဖွင့်ပြီး သီချင်းဖွင့်ပြီး အသံလျှော့"
        val result = cognitiveEngine.process(input)

        assertNotNull(result.multiStepPlan)
        val plan = result.multiStepPlan!!
        assertTrue(plan.totalSteps >= 2)
        assertEquals(SemanticIntent.OPEN_APP, plan.steps[0].intent)
        assertEquals(SemanticIntent.PLAY_MUSIC, plan.steps[1].intent)
        if (plan.steps.size > 2) {
            assertEquals(SemanticIntent.VOLUME_DOWN, plan.steps[2].intent)
        }
    }

    // 10. Pronoun / Reference tests: “သူ့ကို ဆက်”, “အဲဒါကို ပြန်ဖွင့်”, “အရင်ဟာ ပြန်လုပ်”
    @Test
    fun testCase10_PronounAndReferenceResolution() {
        // Set previous turn state
        cognitiveEngine.workingMemory.lastContact = "ကိုကျော်"
        cognitiveEngine.workingMemory.lastApp = "Facebook"

        // 10a: “သူ့ကို ဆက်”
        val callResult = cognitiveEngine.process("သူ့ကို ဆက်")
        assertEquals(SemanticIntent.CALL_CONTACT, callResult.intent)
        assertEquals("ကိုကျော်", callResult.resolvedReferences["သူ့ကို"])

        // 10b: “အဲဒါကို ပြန်ဖွင့်”
        val reopenResult = cognitiveEngine.process("အဲဒါကို ပြန်ဖွင့်")
        assertEquals(SemanticIntent.REOPEN_APP, reopenResult.intent)
        assertEquals("Facebook", reopenResult.resolvedReferences["အဲဒါ"])
    }

    // 11. Goal Detection: “မနက်ဖြန် မနက်စောစောထရမယ်”
    @Test
    fun testCase11_ImplicitWakeUpGoal() {
        val input = "မနက်ဖြန် မနက်စောစောထရမယ်"
        val result = cognitiveEngine.process(input)

        assertEquals(SemanticIntent.SET_ALARM, result.intent)
        assertEquals(ConfidenceLevel.ASK_FOR_CLARIFICATION, result.confidenceLevel)
        assertTrue(result.missingInformation.contains("ALARM_TIME"))
        assertTrue(result.actionPlan.speechFeedbackMM?.contains("ဘယ်အချိန် Alarm ထားပေးရမလဲ") == true)
    }

    // 12. Safe Learning Engine Validation
    @Test
    fun testCase12_SafeLearningPolicy() {
        // Non-sensitive command should learn
        cognitiveEngine.learningEngine.learnFromInteraction("YouTube ဖွင့်", SemanticIntent.OPEN_APP, "YOUTUBE", true)
        val pattern = cognitiveEngine.learningEngine.findLearnedPattern("YouTube ဖွင့်")
        assertNotNull(pattern)

        // Sensitive command containing password/PIN must NOT be learned
        cognitiveEngine.learningEngine.learnFromInteraction("ကျွန်တော့် password က 12345", SemanticIntent.UNKNOWN, null, true)
        val sensitivePattern = cognitiveEngine.learningEngine.findLearnedPattern("ကျွန်တော့် password က 12345")
        assertTrue(sensitivePattern == null)
    }
}
