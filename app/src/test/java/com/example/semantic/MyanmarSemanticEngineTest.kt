package com.example.semantic

import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MyanmarSemanticEngineTest {

    private lateinit var semanticEngine: MyanmarSemanticEngine

    @Before
    fun setUp() {
        semanticEngine = MyanmarSemanticEngine()
        semanticEngine.memoryManager.reset()
    }

    // 1. “ကင်မရာဖွင့်”
    @Test
    fun testCase01_OpenCameraDirect() {
        val input = "ကင်မရာဖွင့်"
        val result = semanticEngine.process(input)

        assertEquals("ကင်မရာဖွင့်", result.normalizedText)
        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("CAMERA", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
        assertTrue(result.actionPlan.isExecutable)
        assertEquals("OPEN_APP", result.actionPlan.actionType)
        assertNotNull(result.actionPlan.speechFeedbackMM)
    }

    // 2. “ကင်မရာလေးဖွင့်ပေး”
    @Test
    fun testCase02_OpenCameraPoliteVariation() {
        val input = "ကင်မရာလေးဖွင့်ပေး"
        val result = semanticEngine.process(input)

        assertEquals("ကင်မရာလေးဖွင့်ပေး", result.normalizedText)
        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("CAMERA", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
        assertTrue(result.actionPlan.isExecutable)
        assertNotNull(result.actionPlan.speechFeedbackMM)
    }

    // 3. “ဓာတ်ပုံရိုက်ချင်လို့ ကင်မရာဝင်ပေး”
    @Test
    fun testCase03_OpenCameraGoalBasedColloquial() {
        val input = "ဓာတ်ပုံရိုက်ချင်လို့ ကင်မရာဝင်ပေး"
        val result = semanticEngine.process(input)

        assertTrue(result.normalizedText.contains("ဓာတ်ပုံရိုက်ချင်လို့") || result.normalizedText.contains("ကင်မရာ"))
        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("CAMERA", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
        assertTrue(result.reasoning.contains("CAMERA") || result.reasoning.contains("photo"))
    }

    // 4. “ဖုန်းဆက်ပေး”
    @Test
    fun testCase04_CallGeneral() {
        val input = "ဖုန်းဆက်ပေး"
        val result = semanticEngine.process(input)

        assertEquals("ဖုန်းဆက်ပေး", result.normalizedText)
        assertEquals(SemanticIntent.CALL_CONTACT, result.intent)
        assertTrue(result.actionPlan.requiresConfirmation) // No target contact specified -> safety validator asks for confirmation
        assertEquals("CALL_CONTACT", result.actionPlan.actionType)
        assertNotNull(result.actionPlan.confirmationPrompt)
    }

    // 5. “မေမေကို ဖုန်းဆက်ပေး”
    @Test
    fun testCase05_CallNamedContact() {
        val input = "မေမေကို ဖုန်းဆက်ပေး"
        val result = semanticEngine.process(input)

        assertEquals("မေမေကို ဖုန်းဆက်ပေး", result.normalizedText)
        assertEquals(SemanticIntent.CALL_CONTACT, result.intent)
        val contactEntity = result.entities.find { it.type == EntityType.CONTACT_NAME }
        assertNotNull(contactEntity)
        assertEquals("မေမေ", contactEntity?.value)
        assertEquals("မေမေ", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
    }

    // 6. “အသံနည်းနည်းတိုးပေး”
    @Test
    fun testCase06_VolumeDownSmallStep() {
        val input = "အသံနည်းနည်းတိုးပေး"
        val result = semanticEngine.process(input)

        assertEquals("အသံနည်းနည်းတိုးပေး", result.normalizedText)
        assertEquals(SemanticIntent.VOLUME_DOWN, result.intent)
        val settingEntity = result.entities.find { it.type == EntityType.SETTING_NAME }
        assertNotNull(settingEntity)
        assertEquals("VOLUME", settingEntity?.value)
        val levelEntity = result.entities.find { it.type == EntityType.VOLUME_LEVEL }
        assertNotNull(levelEntity)
        assertEquals("SMALL", levelEntity?.value)
        assertTrue(result.confidence >= 0.85f)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 7. “အသံတိုး”
    @Test
    fun testCase07_VolumeDownDirect() {
        val input = "အသံတိုး"
        val result = semanticEngine.process(input)

        assertEquals("အသံတိုး", result.normalizedText)
        assertEquals(SemanticIntent.VOLUME_DOWN, result.intent)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 8. “Wi-Fi ဖွင့်ပေး”
    @Test
    fun testCase08_WifiOn() {
        val input = "Wi-Fi ဖွင့်ပေး"
        val result = semanticEngine.process(input)

        assertEquals(SemanticIntent.TOGGLE_WIFI, result.intent)
        assertEquals("WIFI", result.actionPlan.target)
        val stateEntity = result.entities.find { it.type == EntityType.SETTING_STATE }
        assertEquals("ON", stateEntity?.value)
        assertTrue(result.confidence >= 0.85f)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 9. “Wi-Fi ပိတ်လိုက်”
    @Test
    fun testCase09_WifiOff() {
        val input = "Wi-Fi ပိတ်လိုက်"
        val result = semanticEngine.process(input)

        assertEquals(SemanticIntent.TOGGLE_WIFI, result.intent)
        assertEquals("WIFI", result.actionPlan.target)
        val stateEntity = result.entities.find { it.type == EntityType.SETTING_STATE }
        assertEquals("OFF", stateEntity?.value)
        assertTrue(result.confidence >= 0.85f)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 10. “အဲဒီ App ကို ပြန်ဖွင့်”
    @Test
    fun testCase10_ReopenAppWithContext() {
        // Turn 1: Open YouTube
        val ctx = ConversationContext()
        ctx.updateApp("YouTube", "com.google.android.youtube")

        // Turn 2: "အဲဒီ App ကို ပြန်ဖွင့်"
        val input = "အဲဒီ App ကို ပြန်ဖွင့်"
        val result = semanticEngine.process(input, overrideContext = ctx)

        assertEquals(SemanticIntent.REOPEN_APP, result.intent)
        assertEquals("YouTube", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
        assertTrue(result.reasoning.contains("YouTube"))
    }

    // 11. “အဲဒီမှာ သီချင်းတစ်ပုဒ်ဖွင့်”
    @Test
    fun testCase11_PlayMusicInReferencedApp() {
        // Turn 1: Open YouTube
        val ctx = ConversationContext()
        ctx.updateApp("YouTube", "com.google.android.youtube")

        // Turn 2: "အဲဒီမှာ သီချင်းတစ်ပုဒ်ဖွင့်"
        val input = "အဲဒီမှာ သီချင်းတစ်ပုဒ်ဖွင့်"
        val result = semanticEngine.process(input, overrideContext = ctx)

        assertEquals(SemanticIntent.PLAY_MUSIC, result.intent)
        assertTrue(result.resolvedReferences.containsKey("အဲဒီမှာ"))
        assertEquals("YouTube", result.resolvedReferences["အဲဒီမှာ"])
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 12. “မနက် ၇ နာရီ Alarm ထားပေး”
    @Test
    fun testCase12_SetAlarmWithMyanmarDigits() {
        val input = "မနက် ၇ နာရီ Alarm ထားပေး"
        val result = semanticEngine.process(input)

        assertTrue(result.normalizedText.contains("7") || result.normalizedText.contains("၇"))
        assertEquals(SemanticIntent.SET_ALARM, result.intent)
        val timeEntity = result.entities.find { it.type == EntityType.TIME_EXPRESSION }
        assertNotNull(timeEntity)
        assertEquals("07:00", timeEntity?.value)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    // 13. “ဒီသီချင်းကို နောက်တစ်ပုဒ်ပြောင်း”
    @Test
    fun testCase13_NextTrackWithMediaReference() {
        // Turn 1: Active playing song
        val ctx = ConversationContext()
        ctx.updateMedia("လေးဖြူ", "အလွမ်းည")

        // Turn 2: "ဒီသီချင်းကို နောက်တစ်ပုဒ်ပြောင်း"
        val input = "ဒီသီချင်းကို နောက်တစ်ပုဒ်ပြောင်း"
        val result = semanticEngine.process(input, overrideContext = ctx)

        assertEquals(SemanticIntent.NEXT_TRACK, result.intent)
        assertEquals("NEXT", result.actionPlan.target)
        assertTrue(result.confidence >= 0.85f)
        assertEquals(ConfidenceLevel.EXECUTE, result.confidenceLevel)
        assertFalse(result.actionPlan.requiresConfirmation)
    }

    @Test
    fun testPoliteParticlesAndColloquialVariations() {
        val variations = listOf(
            "ကင်မရာဖွင့်",
            "ကင်မရာလေး ဝင်ပေး",
            "ဓာတ်ပုံရိုက်ချင်လို့ ကင်မရာဖွင့်ပေး",
            "ကင်မရာထဲ ဝင်ပေးပါဦး",
            "ကင်မရာဖွင့်ပေးပါဦးနော်"
        )

        for (v in variations) {
            val res = semanticEngine.process(v)
            assertEquals("Failed on: $v", SemanticIntent.OPEN_APP, res.intent)
            assertEquals("Failed target on: $v", "CAMERA", res.actionPlan.target)
            assertEquals("Failed confidence on: $v", ConfidenceLevel.EXECUTE, res.confidenceLevel)
        }
    }

    @Test
    fun testDangerousActionsSafetyValidation() {
        val payResult = semanticEngine.process("ငွေချေပေးပါ")
        // Payment or uninstall requires safety prompt
        val uninstallResult = semanticEngine.safetyValidator.validate(
            intent = SemanticIntent.UNINSTALL_APP,
            target = "Facebook",
            confidenceLevel = ConfidenceLevel.EXECUTE,
            parameters = emptyMap()
        )
        assertTrue(uninstallResult.requiresConfirmation)
        assertEquals("HIGH", uninstallResult.safetyRiskLevel)
    }
}
