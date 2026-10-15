package com.example

import com.kyawgyi.ir.models.Esp32StatusResponse
import com.kyawgyi.ir.models.GenericApiResponse
import com.kyawgyi.ir.models.KennedeIrSchema
import com.kyawgyi.ir.repository.IrRepository
import com.kyawgyi.ir.repository.IrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies Requirement 1:
 * Real-World Mismatch detection when smartphone has no IR receiver.
 * - Zero hallucination / simulation
 * - Error logged under Real-World Mismatch
 * - Duplicate request triggers "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"
 * - Never repeats failed approach
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealWorldMismatchTest {

  private val testDispatcher = StandardTestDispatcher()

  private class MockOfflineIrRepository : IrRepository {
    override fun getBaseUrl(): String = "http://192.168.1.150"
    override fun updateBaseUrl(hostOrIp: String) {}
    override suspend fun fetchStatus(): Result<Esp32StatusResponse> =
      Result.failure(Exception("Offline - No ESP32 connected"))
    override suspend fun fetchCodes(): Result<KennedeIrSchema> =
      Result.failure(Exception("Offline"))
    override suspend fun startLearning(buttonKey: String): Result<GenericApiResponse> =
      Result.failure(Exception("Cannot start learning without ESP32 receiver"))
    override suspend fun stopLearning(): Result<GenericApiResponse> =
      Result.success(GenericApiResponse(success = true, message = "Stopped"))
    override suspend fun transmitButton(buttonKey: String): Result<GenericApiResponse> =
      Result.failure(Exception("Offline"))
    override suspend fun fetchRawReport(): Result<String> =
      Result.failure(Exception("Offline"))
    override suspend fun resetAllCodes(): Result<GenericApiResponse> =
      Result.success(GenericApiResponse(success = true, message = "Reset"))
    override suspend fun importCodesJson(json: String): Result<GenericApiResponse> =
      Result.success(GenericApiResponse(success = true, message = "Imported"))
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testPhoneHasNoIrReceiver_triggersRealWorldMismatch_andNoHallucination() = runTest(testDispatcher) {
    val repo = MockOfflineIrRepository()
    val viewModel = IrViewModel(repository = repo)
    advanceUntilIdle()

    // 1. First attempt to learn "power" while offline
    viewModel.startLearning("power")
    advanceUntilIdle()

    val state1 = viewModel.uiState.value
    assertFalse("Must NOT claim to be learning without hardware", state1.isLearning)
    assertNotNull(state1.failureWarning)
    assertEquals("လက်တွေ့မကိုက်ညီမှု (Real-World Mismatch)", state1.errorCategory)
    assertTrue("Warning must mention IR Receiver limitation", state1.failureWarning!!.contains("IR Receiver"))
    assertTrue("Must mention alternative hardware solution", state1.failureWarning!!.contains("ESP32") || state1.failureWarning!!.contains("Preset"))

    // 2. Second attempt for the exact same failed action (Duplicate action request)
    viewModel.startLearning("power")
    advanceUntilIdle()

    val state2 = viewModel.uiState.value
    assertFalse("Must remain false", state2.isLearning)
    assertNotNull(state2.failureWarning)
    assertTrue(
      "Duplicate failure must explicitly state 'ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်'",
      state2.failureWarning!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်")
    )
    assertTrue("Must show failure repetition count", state2.failureWarning!!.contains("ကြိမ်ရေ: 2 ကြိမ်"))
  }
}
