package com.example.semantic

import androidx.test.core.app.ApplicationProvider
import com.example.actions.ActionCategory
import com.example.actions.ActionRegistry
import com.example.pipeline.contract.RiskLevel
import com.example.semantic.analyzer.MyanmarSemanticAnalyzer
import com.example.semantic.classifier.IntentClassifier
import com.example.semantic.memory.MemoryManager
import com.example.semantic.model.ConfidenceLevel
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.SemanticIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyanmarSemanticAnalyzerAndMemoryTest {

    private lateinit var analyzer: MyanmarSemanticAnalyzer
    private lateinit var classifier: IntentClassifier
    private lateinit var memoryManager: MemoryManager

    @Before
    fun setUp() {
        analyzer = MyanmarSemanticAnalyzer()
        classifier = IntentClassifier()
        memoryManager = MemoryManager(context = ApplicationProvider.getApplicationContext())
        memoryManager.clearVolatileMemory()
    }

    // =========================================================================
    // 1. INTENT CLASSIFIER TESTS
    // =========================================================================

    @Test
    fun testIntentClassifier_DirectAppLaunch() {
        val result = classifier.classify("Facebook ဖွင့်")
        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("FACEBOOK", result.target)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun testIntentClassifier_ColloquialVolumeDecrease() {
        val result = classifier.classify("အသံနည်းနည်းတိုးပေး")
        assertEquals(SemanticIntent.VOLUME_DOWN, result.intent)
        assertEquals("VOLUME", result.target)
        assertTrue(result.confidence >= 0.90f)
    }

    @Test
    fun testIntentClassifier_ColloquialFlashlight() {
        val result = classifier.classify("မီးလင်းပေး")
        assertEquals(SemanticIntent.TOGGLE_FLASHLIGHT, result.intent)
        assertTrue(result.confidence >= 0.90f)
    }

    @Test
    fun testIntentClassifier_MixedBurmeseEnglish() {
        val result = classifier.classify("Facebook open လုပ်")
        assertEquals(SemanticIntent.OPEN_APP, result.intent)
        assertEquals("FACEBOOK", result.target)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun testIntentClassifier_Navigation() {
        val homeResult = classifier.classify("Home ပြန်")
        assertEquals(SemanticIntent.NAVIGATE_HOME, homeResult.intent)

        val backResult = classifier.classify("နောက်သို့ ပြန်သွား")
        assertEquals(SemanticIntent.NAVIGATE_BACK, backResult.intent)
    }

    // =========================================================================
    // 2. ACTION REGISTRY TESTS
    // =========================================================================

    @Test
    fun testActionRegistry_DefinedActions() {
        val openApp = ActionRegistry.getAction("OPEN_APP")
        assertNotNull(openApp)
        assertEquals(RiskLevel.LOW, openApp?.riskLevel)
        assertEquals(ActionCategory.APP_NAVIGATION, openApp?.category)
        assertFalse(openApp?.defaultRequiresConfirmation ?: true)

        val callAction = ActionRegistry.getAction("CALL")
        assertNotNull(callAction)
        assertEquals(RiskLevel.HIGH, callAction?.riskLevel)
        assertEquals(ActionCategory.COMMUNICATION, callAction?.category)
        assertTrue(callAction?.defaultRequiresConfirmation ?: false)
        assertTrue(callAction?.requiredPermissions?.contains("android.permission.CALL_PHONE") == true)

        val volumeUp = ActionRegistry.getAction("VOLUME_UP")
        assertNotNull(volumeUp)
        assertEquals(RiskLevel.LOW, volumeUp?.riskLevel)
    }

    @Test
    fun testActionRegistry_ParameterValidation() {
        // OPEN_APP requires target
        assertTrue(ActionRegistry.validateParameters("OPEN_APP", mapOf("target" to "YOUTUBE")))
        assertFalse(ActionRegistry.validateParameters("OPEN_APP", emptyMap()))
        assertEquals(listOf("target"), ActionRegistry.getMissingParameters("OPEN_APP", emptyMap()))

        // CALL requires target
        assertTrue(ActionRegistry.validateParameters("CALL", mapOf("target" to "မေမေ")))
        assertFalse(ActionRegistry.validateParameters("CALL", emptyMap()))
    }

    @Test
    fun testActionRegistry_ConfirmationPromptGeneration() {
        val prompt = ActionRegistry.getConfirmationPrompt("CALL", target = "မေမေ")
        assertNotNull(prompt)
        assertTrue(prompt?.contains("မေမေ") == true)
        assertTrue(prompt?.contains("ဖုန်းခေါ်ဆို") == true)
    }

    // =========================================================================
    // 3. MEMORY MANAGER TESTS
    // =========================================================================

    @Test
    fun testMemoryManager_ShortTermAndContextTracking() {
        memoryManager.recordTurn(
            rawQuery = "YouTube ဖွင့်ပေးပါ",
            normalizedQuery = "youtube ဖွင့်ပေးပါ",
            assistantResponse = "YouTube ဖွင့်ပေးလိုက်ပါပြီ",
            intent = SemanticIntent.OPEN_APP,
            target = "YOUTUBE",
            targetPackage = "com.google.android.youtube"
        )

        // Verify Short-Term Turn History
        val history = memoryManager.getShortTermHistory()
        assertEquals(1, history.size)
        assertEquals("YouTube ဖွင့်ပေးပါ", history.first().userQuery)
        assertEquals("YOUTUBE", history.first().target)

        // Verify Context Tracking
        val activeContext = memoryManager.getActiveContext()
        assertEquals("YOUTUBE", activeContext.activeAppNameMM)
        assertEquals("com.google.android.youtube", activeContext.activeAppPackage)
        assertEquals(SemanticIntent.OPEN_APP, activeContext.lastIntent)
    }

    @Test
    fun testMemoryManager_MultiTurnContactContext() {
        memoryManager.recordTurn(
            rawQuery = "မေမေ့ဆီ ဖုန်းဆက်",
            normalizedQuery = "မေမေ့ဆီ ဖုန်းဆက်",
            assistantResponse = "မေမေ့ထံ ဖုန်းခေါ်ဆိုပေးရမလားခင်ဗျာ",
            intent = SemanticIntent.CALL_CONTACT,
            contact = "မေမေ",
            phone = "09123456789"
        )

        assertEquals("မေမေ", memoryManager.getLastContact())
        assertEquals("မေမေ", memoryManager.getActiveContext().activeContactName)
    }

    // =========================================================================
    // 4. MYANMAR SEMANTIC ANALYZER (MULTI-TURN E2E)
    // =========================================================================

    @Test
    fun testSemanticAnalyzer_DirectActionPlan() {
        val plan = analyzer.analyze("Facebook ဖွင့်")
        assertEquals(SemanticIntent.OPEN_APP, plan.intent)
        assertEquals("OPEN_APP", plan.actionType)
        assertEquals("FACEBOOK", plan.target)
        assertEquals(ConfidenceLevel.EXECUTE, plan.confidenceLevel)
        assertTrue(plan.isExecutable)
        assertFalse(plan.requiresConfirmation)
    }

    @Test
    fun testSemanticAnalyzer_MultiTurnAnaphoraResolution() {
        val context = ConversationContext()
        context.updateApp("YouTube", "com.google.android.youtube")

        // User says "အဲ့ဒီ app ထဲဝင်" referring to previous context
        val plan = analyzer.analyze("အဲ့ဒီ app ထဲဝင်", context = context)
        assertEquals(SemanticIntent.OPEN_APP, plan.intent)
        assertEquals("YOUTUBE", plan.target)
        assertEquals("OPEN_APP", plan.actionType)
        assertTrue(plan.confidence >= 0.85f)
    }

    @Test
    fun testSemanticAnalyzer_HighRiskCallRequiresConfirmation() {
        val plan = analyzer.analyze("မေမေ့ဆီ ဖုန်းဆက်")
        assertEquals(SemanticIntent.CALL_CONTACT, plan.intent)
        assertEquals("CALL", plan.actionType)
        assertTrue(plan.requiresConfirmation)
        assertNotNull(plan.confirmationPrompt)
        assertTrue(plan.confirmationPrompt?.contains("မေမေ") == true)
    }
}
