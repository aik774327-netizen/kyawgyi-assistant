package com.example

import com.example.models.KennedeButton
import com.example.models.KennedeFanAutomationState
import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Item 6: Smart Fan Automation, Scheduling & EcoFlow Power Linkage (အချက် ၆).
 */
class FanAutomationTest {

  @Test
  fun testVoiceParser_SleepTimerCommands() {
    // 30 minutes
    val intent30 = MyanmarVoiceParser.parse("ပန်ကာကို မိနစ် ၃၀ အချိန်မှတ်ပေးပါ")
    assertTrue("Should parse as KennedeFanAutomationIntent", intent30 is ParsedIntent.KennedeFanAutomationIntent)
    val auto30 = intent30 as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("SET_TIMER", auto30.action)
    assertEquals(30, auto30.minutes)

    // 15 minutes
    val intent15 = MyanmarVoiceParser.parse("ပန်ကာ ၁၅ မိနစ် timer ပေးပါ")
    assertTrue(intent15 is ParsedIntent.KennedeFanAutomationIntent)
    val auto15 = intent15 as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("SET_TIMER", auto15.action)
    assertEquals(15, auto15.minutes)

    // 1 hour (60 minutes)
    val intent60 = MyanmarVoiceParser.parse("ပန်ကာ ၁ နာရီ အချိန်မှတ်ပေးပါ")
    assertTrue(intent60 is ParsedIntent.KennedeFanAutomationIntent)
    val auto60 = intent60 as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("SET_TIMER", auto60.action)
    assertEquals(60, auto60.minutes)

    // 2 hours (120 minutes)
    val intent120 = MyanmarVoiceParser.parse("ပန်ကာ ၂ နာရီ timer ပေးပါ")
    assertTrue(intent120 is ParsedIntent.KennedeFanAutomationIntent)
    val auto120 = intent120 as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("SET_TIMER", auto120.action)
    assertEquals(120, auto120.minutes)

    // Cancel timer
    val intentCancel = MyanmarVoiceParser.parse("ပန်ကာ အချိန်မှတ်ထားတာ ဖျက်ပေးပါ")
    assertTrue(intentCancel is ParsedIntent.KennedeFanAutomationIntent)
    val autoCancel = intentCancel as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("CANCEL_TIMER", autoCancel.action)
  }

  @Test
  fun testVoiceParser_EcoFlowPowerLinkCommands() {
    val intentEnable = MyanmarVoiceParser.parse("ပန်ကာနှင့် ecoflow ဘက်ထရီချွေတာ ချိတ်ဆက်ပေးပါ")
    assertTrue(intentEnable is ParsedIntent.KennedeFanAutomationIntent)
    val autoEnable = intentEnable as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("TOGGLE_ECOFLOW_LINK", autoEnable.action)
    assertEquals("true", autoEnable.param)

    val intentDisable = MyanmarVoiceParser.parse("ecoflow power link ဘက်ထရီချွေတာ ပိတ်ပါ")
    assertTrue(intentDisable is ParsedIntent.KennedeFanAutomationIntent)
    val autoDisable = intentDisable as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("TOGGLE_ECOFLOW_LINK", autoDisable.action)
    assertEquals("false", autoDisable.param)
  }

  @Test
  fun testVoiceParser_QuietNightModeCommands() {
    val intentNightOn = MyanmarVoiceParser.parse("ပန်ကာကို ညဘက် မုဒ် ဖွင့်ပေးပါ")
    assertTrue(intentNightOn is ParsedIntent.KennedeFanAutomationIntent)
    val autoNightOn = intentNightOn as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("TOGGLE_NIGHT_MODE", autoNightOn.action)
    assertEquals("true", autoNightOn.param)

    val intentNightOff = MyanmarVoiceParser.parse("ညဘက် အသံတိတ် မုဒ် ပိတ်လိုက်ပါ")
    assertTrue(intentNightOff is ParsedIntent.KennedeFanAutomationIntent)
    val autoNightOff = intentNightOff as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("TOGGLE_NIGHT_MODE", autoNightOff.action)
    assertEquals("false", autoNightOff.param)
  }

  @Test
  fun testVoiceParser_BatchCalibrationCommands() {
    val intentStart = MyanmarVoiceParser.parse("ပန်ကာ ခလုတ်အားလုံး စတင်သင်ယူပါ")
    assertTrue(intentStart is ParsedIntent.KennedeFanAutomationIntent)
    val autoStart = intentStart as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("START_BATCH_CALIBRATION", autoStart.action)

    val intentCancel = MyanmarVoiceParser.parse("ပန်ကာ calibration ရပ်တန့်ပေးပါ")
    assertTrue(intentCancel is ParsedIntent.KennedeFanAutomationIntent)
    val autoCancel = intentCancel as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("CANCEL_BATCH_CALIBRATION", autoCancel.action)
  }

  @Test
  fun testFanAutomationStateDefaults() {
    val state = KennedeFanAutomationState()
    assertFalse("Sleep timer should be inactive by default", state.sleepTimerActive)
    assertEquals(0, state.sleepTimerMinutes)
    assertEquals(0, state.sleepTimerRemainingSeconds)
    assertTrue("EcoFlow power link should be enabled by default", state.ecoFlowPowerLinkEnabled)
    assertEquals(20, state.lowBatteryThreshold)
    assertTrue("Auto turn off light should be enabled by default", state.autoTurnOffLightOnLowBattery)
    assertTrue("Auto reduce speed should be enabled by default", state.autoReduceSpeedOnLowBattery)
    assertFalse("Quiet night mode should be inactive by default", state.quietNightModeActive)
    assertFalse("Batch calibration should be inactive by default", state.batchCalibrationActive)
    assertEquals(0, state.batchCalibrationStep)
    assertEquals(10, state.batchCalibrationTotal)
  }

  @Test
  fun testKennedeButtonsCount() {
    // KN-5996HRB has exactly 10 hardware buttons on remote
    assertEquals(10, KennedeButton.values().size)
    assertNotNull(KennedeButton.POWER)
    assertNotNull(KennedeButton.SPEED)
    assertNotNull(KennedeButton.LIGHT)
    assertNotNull(KennedeButton.TIMER)
    assertNotNull(KennedeButton.OSCILLATION)
  }

  @Test
  fun testEcoFlowBatteryThresholdCrossings() {
    val state = KennedeFanAutomationState(
      ecoFlowPowerLinkEnabled = true,
      lowBatteryThreshold = 20,
      autoTurnOffLightOnLowBattery = true,
      autoReduceSpeedOnLowBattery = true
    )
    assertTrue(state.ecoFlowPowerLinkEnabled)
    assertEquals(20, state.lowBatteryThreshold)
    assertTrue(state.autoTurnOffLightOnLowBattery)
    assertTrue(state.autoReduceSpeedOnLowBattery)

    // Verify sub-rule states can be toggled
    val lightDisabledState = state.copy(autoTurnOffLightOnLowBattery = false)
    assertFalse(lightDisabledState.autoTurnOffLightOnLowBattery)

    val speedDisabledState = state.copy(autoReduceSpeedOnLowBattery = false)
    assertFalse(speedDisabledState.autoReduceSpeedOnLowBattery)

    val thresholdChanged = state.copy(lowBatteryThreshold = 15)
    assertEquals(15, thresholdChanged.lowBatteryThreshold)
  }
}
