package com.example.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.contract.UnifiedCommandResponse
import com.example.pipeline.memory.StructuredMemoryStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-031 regression tests (Phase 1.5) — T15-031-02 (Wi-Fi) and
 * T15-031-03 (Bluetooth + Flashlight), end-to-end through the chat
 * pipeline (`UnifiedIntelligencePipeline.processCommand`), following the
 * existing [UnifiedIntelligencePipelineTest] harness pattern.
 *
 * Why assert on `response.parameters`: the pipeline returns the exact
 * parameter map that flows from `MyanmarActionPlanner` into
 * `executeAction` (UnifiedIntelligencePipeline :178 -> :461 -> :608).
 * `executeAction` derives the executor call from that same map —
 * `actionExecutor.setWifi(parameters["enable"] as? Boolean ?: true)` —
 * so `parameters["enable"]` IS the value the executor receives.
 * Before the ISSUE-031 fix the key was absent (null), which the
 * `?: true` default silently turned into ON for every OFF command.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatToggleEnableParameterTest {

    private lateinit var pipeline: UnifiedIntelligencePipeline

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        pipeline = UnifiedIntelligencePipeline(context, StructuredMemoryStore())
    }

    private fun process(transcript: String, requestId: String): UnifiedCommandResponse = runBlocking {
        pipeline.processCommand(
            UnifiedCommandRequest(
                requestId = requestId,
                sessionId = "sess_031",
                transcript = transcript
            ),
            speakResponse = false
        )
    }

    // ---------------------------------------------------------------
    // T15-031-02: Wi-Fi ON and OFF through the chat path
    // ---------------------------------------------------------------

    @Test
    fun t15_031_02_wifiOff_chatInput_enableFalse() {
        val response = process("ဝိုင်ဖိုင် ပိတ်ပါ", "req_031_wifi_off")
        assertEquals("WIFI_TOGGLE", response.intent)
        assertEquals("OFF", response.parameters["state"])
        assertEquals(false, response.parameters["enable"])
    }

    @Test
    fun t15_031_02_wifiOn_chatInput_enableTrue() {
        val response = process("ဝိုင်ဖိုင် ဖွင့်ပါ", "req_031_wifi_on")
        assertEquals("WIFI_TOGGLE", response.intent)
        assertEquals("ON", response.parameters["state"])
        assertEquals(true, response.parameters["enable"])
    }

    // ---------------------------------------------------------------
    // T15-031-03: the same OFF/ON contract for the other two toggle
    // branches that read parameters["enable"] (Bluetooth, Flashlight)
    // ---------------------------------------------------------------

    @Test
    fun t15_031_03_bluetoothOff_chatInput_enableFalse() {
        val response = process("ဘလူးတုသ် ပိတ်ပါ", "req_031_bt_off")
        assertEquals("BLUETOOTH_TOGGLE", response.intent)
        assertEquals("OFF", response.parameters["state"])
        assertEquals(false, response.parameters["enable"])
    }

    @Test
    fun t15_031_03_bluetoothOn_chatInput_enableTrue() {
        val response = process("ဘလူးတုသ် ဖွင့်ပါ", "req_031_bt_on")
        assertEquals("BLUETOOTH_TOGGLE", response.intent)
        assertEquals("ON", response.parameters["state"])
        assertEquals(true, response.parameters["enable"])
    }

    @Test
    fun t15_031_03_flashlightOff_chatInput_enableFalse() {
        val response = process("ဓာတ်မီး ပိတ်ပါ", "req_031_fl_off")
        assertEquals("FLASHLIGHT", response.intent)
        assertEquals("OFF", response.parameters["state"])
        assertEquals(false, response.parameters["enable"])
    }

    @Test
    fun t15_031_03_flashlightOn_chatInput_enableTrue() {
        val response = process("ဓာတ်မီး ဖွင့်ပါ", "req_031_fl_on")
        assertEquals("FLASHLIGHT", response.intent)
        assertEquals("ON", response.parameters["state"])
        assertEquals(true, response.parameters["enable"])
    }
}
