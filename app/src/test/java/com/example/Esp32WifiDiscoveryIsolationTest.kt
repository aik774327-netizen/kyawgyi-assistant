package com.example

import com.example.database.ErrorCategory
import com.example.database.ErrorLogDao
import com.example.database.ErrorLogEntity
import com.example.realworld.Esp32DiscoveryState
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
 * Verifies Requirement 3 (အချက် ၃):
 * ESP32 Wi-Fi Discovery သို့ Cold-Start ဖြစ်ချိန် Static IP မရှိပါက ပျက်ကွက်နိုင်ခြေ (Technical & Resource Constraint)
 *
 * Adheres strictly to the 5 rules:
 * 1. Log failed action in ErrorLog under ErrorCategory.TECHNICAL.
 * 2. Categorize as "နည်းပညာ (Technical Limitation)" - Router Client Isolation / AP Isolation / Multicast Blocking.
 * 3. If duplicate/repeated request: Warn "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်", show retry count.
 * 4. Clear explanation in Burmese explaining why it failed and zero hallucination.
 * 5. Never suggest the failed approach again; provide working alternatives (Subnet Ping Scanner + Manual Direct IP Input).
 */
class Esp32WifiDiscoveryIsolationTest {

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
  fun `Rule 1 and 2 - mDNS Discovery timeout logs under ErrorCategory TECHNICAL with isolation details`() = runTest {
    val actionKey = "ESP32_DISCOVERY_MDNS_TIMEOUT"
    val rawCmd = "ESP32 Wi-Fi အလိုအလျောက် ရှာဖွေခြင်း (Auto-Discovery)"
    val reason = "Wi-Fi Router သို့မဟုတ် Mobile Hotspot တွင် Client Isolation (AP Isolation) / Multicast Blocking ဖွင့်ထားသဖြင့် mDNS (esp32-ir.local) နှင့် UDP Broadcast (Port 4210) ကို Router မှ ပိတ်ပင်ထားပါသည်"
    val failedMethod = "mDNS (esp32-ir.local) နှင့် UDP Broadcast Multicast (Port 4210) ဖြင့်သာ အလိုအလျောက် ရှာဖွေခြင်း"
    val alternative = "IP Manual Direct Input Field (လက်ဖြင့် IP ထည့်သွင်းခြင်း) သို့မဟုတ် Subnet Ping Scanner ဖြင့် တိုက်ရိုက်ချိတ်ဆက်ပါ"

    val logged = errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.TECHNICAL,
      reasonDetail = reason,
      failedMethod = failedMethod,
      alternativeAdvice = alternative
    )

    assertEquals("TECHNICAL", logged.category)
    assertEquals("နည်းပညာပိုင်း (Technical)", logged.categoryBurmese)
    assertTrue(logged.reasonDetail.contains("Client Isolation (AP Isolation)"))
    assertTrue(logged.alternativeAdvice.contains("Subnet Ping Scanner"))
    assertEquals(1, logged.retryCount)
    assertFalse(logged.isResolved)
  }

  @Test
  fun `Rule 3 - Repeated discovery attempt warns user and increments retry count`() = runTest {
    val actionKey = "ESP32_DISCOVERY_MDNS_TIMEOUT"
    val rawCmd = "ESP32 Wi-Fi အလိုအလျောက် ရှာဖွေခြင်း (Auto-Discovery)"

    // First attempt fails
    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.TECHNICAL,
      reasonDetail = "Router AP Isolation / Multicast Blocking",
      failedMethod = "mDNS Multicast",
      alternativeAdvice = "Subnet Ping Scanner"
    )

    // Second attempt checked before repeating
    val checkResult = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertTrue("Should detect previous failure", checkResult.hadPreviousFailure)
    assertEquals(ErrorCategory.TECHNICAL, checkResult.failedCategory)
    assertNotNull(checkResult.warningMessage)
    assertTrue(checkResult.warningMessage!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
    assertTrue(checkResult.warningMessage!!.contains("နည်းပညာပိုင်း (Technical)"))
    assertTrue(checkResult.workingAlternative!!.contains("Subnet Ping Scanner"))

    // Record repeated attempt
    val updated = errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.TECHNICAL,
      reasonDetail = "Repeated failure due to AP Isolation",
      failedMethod = "mDNS Multicast",
      alternativeAdvice = "Subnet Ping Scanner"
    )

    assertEquals(2, updated.retryCount)
  }

  @Test
  fun `Rule 4 and 5 - Engine provides Subnet Ping Scanner and Manual IP fallback never repeats failed mDNS`() = runTest {
    val actionKey = "ESP32_DISCOVERY_MDNS_TIMEOUT"
    val rawCmd = "ESP32 Wi-Fi အလိုအလျောက် ရှာဖွေခြင်း (Auto-Discovery)"
    val failedMethod = "mDNS Multicast Query"

    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.TECHNICAL,
      reasonDetail = "Router AP Isolation",
      failedMethod = failedMethod,
      alternativeAdvice = "Subnet Ping Scanner"
    )

    val checkResult = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertTrue(checkResult.allAvoidedMethods.contains(failedMethod))
    assertTrue(checkResult.warningMessage!!.contains("မအောင်မြင်ခဲ့သော နည်းလမ်း (ထပ်မံအကြံမပေးပါ)"))
    assertTrue(checkResult.workingAlternative!!.contains("Subnet Ping Scanner"))
  }

  @Test
  fun `Rule 6 - Discovery State accurately reflects AP Isolation warning without hallucination`() {
    val state = Esp32DiscoveryState(
      isScanning = false,
      isSubnetScanning = false,
      mdnsFailedDueToIsolation = true,
      technicalWarning = "Router AP Isolation / Multicast Blocking ကြောင့် mDNS မအောင်မြင်ပါ",
      statusMessage = "Router Isolation ကြောင့် mDNS မအောင်မြင်ပါ (Subnet Scanner သုံးပါ)"
    )

    assertTrue(state.mdnsFailedDueToIsolation)
    assertFalse(state.isScanning)
    assertFalse(state.isSubnetScanning)
    assertEquals("Router AP Isolation / Multicast Blocking ကြောင့် mDNS မအောင်မြင်ပါ", state.technicalWarning)
  }

  @Test
  fun `Resolution - Successful connection resolves AP Isolation discovery failure`() = runTest {
    val actionKey = "ESP32_DISCOVERY_MDNS_TIMEOUT"
    val rawCmd = "ESP32 Wi-Fi အလိုအလျောက် ရှာဖွေခြင်း (Auto-Discovery)"

    errorLearningEngine.recordFailure(
      actionKey = actionKey,
      rawCommand = rawCmd,
      category = ErrorCategory.TECHNICAL,
      reasonDetail = "Router AP Isolation",
      failedMethod = "mDNS",
      alternativeAdvice = "Subnet Ping Scanner"
    )

    // When connection succeeds via Subnet Ping Scanner or Manual IP:
    errorLearningEngine.markResolved(actionKey)

    val checkAfter = errorLearningEngine.checkPreviousFailure(actionKey, rawCmd)
    assertFalse(checkAfter.hadPreviousFailure)
  }
}
