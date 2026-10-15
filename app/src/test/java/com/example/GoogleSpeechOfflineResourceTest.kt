package com.example

import com.example.database.ErrorCategory
import com.example.database.ErrorLogDao
import com.example.database.ErrorLogEntity
import com.example.service.ErrorLearningEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies Requirement 2 (ဒုတိယအချက် (၂)):
 * Google Speech Recognizer အော့ဖ်လိုင်း အရင်းအမြစ် ပို့ချချက် (Resource Constraint).
 *
 * Adheres strictly to the 5 rules:
 * 1. Log failed action in ErrorLog under ErrorCategory.RESOURCE.
 * 2. Categorize as "အရင်းအမြစ် (Resource)" - Missing offline language pack / no internet.
 * 3. If duplicate/repeated request: Warn "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်", show retry count.
 * 4. Clear explanation in Burmese explaining why it failed and zero hallucination.
 * 5. Never suggest the failed approach again; provide working alternatives (Quick Action Chips + Settings advice).
 */
class GoogleSpeechOfflineResourceTest {

  private class InMemoryErrorLogDao : ErrorLogDao {
    private val logs = mutableListOf<ErrorLogEntity>()
    private val logsFlow = MutableStateFlow<List<ErrorLogEntity>>(emptyList())
    private var nextId = 1L

    override fun getAllLogs(): Flow<List<ErrorLogEntity>> = logsFlow.asStateFlow()

    override suspend fun findRecentFailure(actionKey: String, rawCommand: String): ErrorLogEntity? {
      return logs.filter { (it.actionKey == actionKey || it.rawCommand == rawCommand) && !it.isResolved }
        .maxByOrNull { it.timestamp }
    }

    override suspend fun getFailedMethodsForAction(actionKey: String): List<String> {
      return logs.filter { it.actionKey == actionKey && !it.isResolved }.map { it.failedMethod }
    }

    override suspend fun insertLog(log: ErrorLogEntity): Long {
      val id = nextId++
      val entity = log.copy(id = id)
      logs.add(entity)
      logsFlow.value = logs.toList()
      return id
    }

    override suspend fun incrementRetry(id: Long, newTime: Long) {
      val index = logs.indexOfFirst { it.id == id }
      if (index >= 0) {
        val existing = logs[index]
        logs[index] = existing.copy(retryCount = existing.retryCount + 1, timestamp = newTime)
        logsFlow.value = logs.toList()
      }
    }

    override suspend fun markResolved(actionKey: String) {
      for (i in logs.indices) {
        if (logs[i].actionKey == actionKey) {
          logs[i] = logs[i].copy(isResolved = true)
        }
      }
      logsFlow.value = logs.toList()
    }

    override suspend fun clearAll() {
      logs.clear()
      logsFlow.value = emptyList()
    }
  }

  private lateinit var errorLogDao: InMemoryErrorLogDao
  private lateinit var errorLearningEngine: ErrorLearningEngine

  @Before
  fun setUp() {
    errorLogDao = InMemoryErrorLogDao()
    errorLearningEngine = ErrorLearningEngine(errorLogDao)
  }

  @Test
  fun testOfflineGoogleSpeech_loggedUnderResourceConstraintCategory() = runTest {
    val actionKey = "SPEECH_RECOGNIZER_OFFLINE_RESOURCE"
    val rawCmd = "အသံဖမ်းယူခြင်း (Speech Recognition)"
    val reason = "Google Speech Services တွင် အော့ဖ်လိုင်း မြန်မာစကားပြော အထောက်အပံ့ (Offline Language Pack) မရှိသေးခြင်း သို့မဟုတ် အင်တာနက် လိုင်းပြတ်တောက်နေခြင်း"
    val failedMethod = "အင်တာနက်မရှိဘဲ Google Speech Recognizer ဖြင့် အသံဖမ်းယူရန် ကြိုးစားခြင်း"
    val advice = "အောက်ပါ Quick Action Chips များကို တိုက်ရိုက်နှိပ်၍ ခိုင်းစေပါ သို့မဟုတ် Phone Settings > System > Languages > Voice Typing > Offline Speech Recognition တွင် Myanmar Pack ကို ဒေါင်းလုဒ်ရယူပါ / Wi-Fi သို့မဟုတ် မိုဘိုင်းဒေတာ ဖွင့်ပါ"

    // 1. First failure occurrence
    val recorded = errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.RESOURCE,
      reasonDetail = reason,
      failedMethod = failedMethod,
      alternativeAdvice = advice
    )

    assertEquals("Category must be RESOURCE", "RESOURCE", recorded.category)
    assertEquals("အရင်းအမြစ် (Resource)", recorded.categoryBurmese)
    assertTrue(
      "Reason detail must mention offline pack or internet",
      recorded.reasonDetail.contains("Offline Language Pack") || recorded.reasonDetail.contains("အင်တာနက်")
    )
    assertTrue(
      "Alternative advice must mention Quick Action Chips",
      recorded.alternativeAdvice.contains("Quick Action Chips")
    )
    assertFalse("Must not be resolved initially", recorded.isResolved)
    assertEquals(1, recorded.retryCount)
  }

  @Test
  fun testRepeatedOfflineVoiceRequest_triggersDuplicateFailureWarningAndIncrementsRetry() = runTest {
    val actionKey = "SPEECH_RECOGNIZER_OFFLINE_RESOURCE"
    val rawCmd = "အသံဖမ်းယူခြင်း (Speech Recognition)"
    val reason = "Google Speech Services တွင် အော့ဖ်လိုင်း မြန်မာစကားပြော အထောက်အပံ့ (Offline Language Pack) မရှိသေးခြင်း"
    val failedMethod = "အင်တာနက်မရှိဘဲ Google Speech Recognizer ဖြင့် အသံဖမ်းယူရန် ထပ်မံကြိုးစားခြင်း"
    val advice = "အော့ဖ်လိုင်း Quick Action Chips များကို တိုက်ရိုက်နှိပ်၍ ခိုင်းစေပါ"

    // Initial failure
    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.RESOURCE,
      reasonDetail = reason,
      failedMethod = failedMethod,
      alternativeAdvice = advice
    )

    // Check previous failure on second attempt (Rule 3)
    val checkResult = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertTrue("Must detect previous failure", checkResult.hadPreviousFailure)
    assertEquals(ErrorCategory.RESOURCE, checkResult.failedCategory)
    assertNotNull(checkResult.warningMessage)
    assertTrue(
      "Must contain previous failure warning",
      checkResult.warningMessage!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်")
    )
    assertTrue("Must contain category title", checkResult.warningMessage!!.contains("အရင်းအမြစ် (Resource)"))
    assertTrue("Must specify working alternative", checkResult.workingAlternative!!.contains("Quick Action Chips"))

    // Record repeated failure (increment retry count)
    val updated = errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.RESOURCE,
      reasonDetail = reason,
      failedMethod = failedMethod,
      alternativeAdvice = advice
    )
    assertEquals("Retry count must be incremented to 2", 2, updated.retryCount)
  }

  @Test
  fun testZeroHallucination_offlineConstraintDoesNotSuggestFailedMethodAgain() = runTest {
    val actionKey = "SPEECH_RECOGNIZER_OFFLINE_RESOURCE"
    val rawCmd = "အသံဖမ်းယူခြင်း (Speech Recognition)"
    val failedMethod = "အင်တာနက်မရှိဘဲ Google Speech Recognizer ဖြင့် အသံဖမ်းယူရန် ကြိုးစားခြင်း"

    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.RESOURCE,
      reasonDetail = "အော့ဖ်လိုင်း အသံဖိုင် မရှိပါ",
      failedMethod = failedMethod,
      alternativeAdvice = "Quick Action Chips ကို အသုံးပြုပါ"
    )

    val checkResult = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertTrue(checkResult.allAvoidedMethods.contains(failedMethod))
    assertTrue(checkResult.warningMessage!!.contains("မအောင်မြင်ခဲ့သော နည်းလမ်း (ထပ်မံအကြံမပေးပါ)"))
  }

  @Test
  fun testOnlineRecovery_marksResourceConstraintResolved() = runTest {
    val actionKey = "SPEECH_RECOGNIZER_OFFLINE_RESOURCE"
    val rawCmd = "အသံဖမ်းယူခြင်း (Speech Recognition)"

    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.RESOURCE,
      reasonDetail = "အော့ဖ်လိုင်း ချို့ယွင်းချက်",
      failedMethod = "အော့ဖ်လိုင်း အသံဖမ်း",
      alternativeAdvice = "Quick Action Chips"
    )

    // Verify it was active
    val before = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertTrue(before.hadPreviousFailure)

    // Now internet is restored and speech succeeds
    errorLearningEngine.markResolved(actionKey)

    // Verify resolved
    val after = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertFalse("Must be marked resolved after recovery", after.hadPreviousFailure)
  }
}
