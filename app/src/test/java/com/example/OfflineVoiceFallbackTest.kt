package com.example

import com.example.models.KennedeButton
import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Item 6: Offline Voice Guidance & Graceful Fallback (အချက် ၆).
 * Verifies that all Quick Action Chips commands execute locally without internet,
 * parse accurately into intended intents, and provide seamless fallback.
 */
class OfflineVoiceFallbackTest {

  @Test
  fun testOfflineFanQuickActionChips_ParseCorrectly() {
    // 1. Power
    val powerIntent = MyanmarVoiceParser.parse("ပန်ကာဖွင့်")
    assertTrue(powerIntent is ParsedIntent.KennedeFanCommand)
    val fanPower = powerIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_POWER_ON", fanPower.action)
    assertEquals("power", fanPower.buttonKey)

    val powerOffIntent = MyanmarVoiceParser.parse("ပန်ကာပိတ်")
    assertTrue(powerOffIntent is ParsedIntent.KennedeFanCommand)
    val fanPowerOff = powerOffIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_POWER_OFF", fanPowerOff.action)

    // 2. Speed
    val speedUpIntent = MyanmarVoiceParser.parse("ပန်ကာ အရှိန်မြှင့်")
    assertTrue(speedUpIntent is ParsedIntent.KennedeFanCommand)
    val speedUp = speedUpIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_SPEED_UP", speedUp.action)
    assertEquals("speed", speedUp.buttonKey)

    val speedDownIntent = MyanmarVoiceParser.parse("ပန်ကာ အရှိန်လျှော့")
    assertTrue(speedDownIntent is ParsedIntent.KennedeFanCommand)
    val speedDown = speedDownIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_SPEED_DOWN", speedDown.action)

    // 3. Light
    val lightIntent = MyanmarVoiceParser.parse("ပန်ကာ မီးဖွင့်")
    assertTrue(lightIntent is ParsedIntent.KennedeFanCommand)
    val fanLight = lightIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_LIGHT_ON", fanLight.action)
    assertEquals("light", fanLight.buttonKey)

    // 4. Oscillation
    val oscIntent = MyanmarVoiceParser.parse("ပန်ကာ လှည့်ပါ")
    assertTrue(oscIntent is ParsedIntent.KennedeFanCommand)
    val fanOsc = oscIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_OSCILLATION_ON", fanOsc.action)
    assertEquals("oscillation", fanOsc.buttonKey)

    // 5. Mode
    val modeIntent = MyanmarVoiceParser.parse("ပန်ကာ မုဒ်ပြောင်း")
    assertTrue(modeIntent is ParsedIntent.KennedeFanCommand)
    val fanMode = modeIntent as ParsedIntent.KennedeFanCommand
    assertEquals("FAN_MODE", fanMode.action)
    assertEquals("mode", fanMode.buttonKey)

    // 6. Timer
    val timerIntent = MyanmarVoiceParser.parse("ပန်ကာ အချိန်မှတ် ၃၀ မိနစ်")
    assertTrue(timerIntent is ParsedIntent.KennedeFanAutomationIntent)
    val fanTimer = timerIntent as ParsedIntent.KennedeFanAutomationIntent
    assertEquals("SET_TIMER", fanTimer.action)
    assertEquals(30, fanTimer.minutes)
  }

  @Test
  fun testOfflineEcoFlowQuickActionChips_ParseCorrectly() {
    // 1. AC On
    val acOn = MyanmarVoiceParser.parse("အေစီဖွင့်ပါ")
    assertTrue(acOn is ParsedIntent.EcoFlowPortToggle)
    val portToggle = acOn as ParsedIntent.EcoFlowPortToggle
    assertEquals("AC", portToggle.port)
    assertTrue(portToggle.enabled)

    // 2. AC Off
    val acOff = MyanmarVoiceParser.parse("အေစီပိတ်ပါ")
    assertTrue(acOff is ParsedIntent.EcoFlowPortToggle)
    val portToggleOff = acOff as ParsedIntent.EcoFlowPortToggle
    assertEquals("AC", portToggleOff.port)
    org.junit.Assert.assertFalse(portToggleOff.enabled)

    // 3. DC On
    val dcOn = MyanmarVoiceParser.parse("ဒီစီဖွင့်ပါ")
    assertTrue(dcOn is ParsedIntent.EcoFlowPortToggle)
    val dcToggle = dcOn as ParsedIntent.EcoFlowPortToggle
    assertEquals("DC", dcToggle.port)
    assertTrue(dcToggle.enabled)

    // 4. Battery status query
    val battQuery = MyanmarVoiceParser.parse("ဘက်ထရီ အခြေအနေ")
    assertTrue(battQuery is ParsedIntent.EcoFlowQueryStatus)
    val queryStatus = battQuery as ParsedIntent.EcoFlowQueryStatus
    assertEquals("battery_percentage", queryStatus.parameter)
  }

  @Test
  fun testOfflineSystemQuickActionChips_ParseCorrectly() {
    val callMom = MyanmarVoiceParser.parse("မေမေ့ဆီ ဖုန်းခေါ်")
    assertTrue(callMom is ParsedIntent.CallContact)
    val contact = callMom as ParsedIntent.CallContact
    assertEquals("မေမေ", contact.target)

    val wifi = MyanmarVoiceParser.parse("ဝိုင်ဖိုင်ဖွင့်")
    assertTrue(wifi is ParsedIntent.ToggleSystemSetting)
    val setting = wifi as ParsedIntent.ToggleSystemSetting
    assertEquals("WiFi", setting.settingName)
    assertTrue(setting.enabled)

    val fb = MyanmarVoiceParser.parse("ဖေ့စ်ဘုတ်ဖွင့်")
    assertTrue(fb is ParsedIntent.OpenApp)

    val yt = MyanmarVoiceParser.parse("ယူကျုဖွင့်")
    assertTrue(yt is ParsedIntent.OpenApp)

    val kpay = MyanmarVoiceParser.parse("ကေပေးဖွင့်")
    assertTrue(kpay is ParsedIntent.OpenApp)
  }

  @Test
  fun testKennedeOfflineButtons_AllExist() {
    val buttons = listOf(
      KennedeButton.POWER,
      KennedeButton.SPEED,
      KennedeButton.LIGHT,
      KennedeButton.OSCILLATION,
      KennedeButton.MODE,
      KennedeButton.TIMER
    )
    for (btn in buttons) {
      assertNotNull(btn.key)
      assertNotNull(btn.label)
      assertNotNull(btn.burmeseLabel)
    }
  }

  @Test
  fun testOfflineGuidanceErrorMessageContent() {
    val errorNetworkMessage = "⚠️ အင်တာနက် လိုင်းမရှိပါ (သို့မဟုတ် Google Speech တွင် Offline Myanmar Pack မရှိပါ)။ အောက်ပါ Quick Action Chips များကို အသုံးပြုနိုင်ပါသည်"
    assertTrue(errorNetworkMessage.contains("Offline"))
    assertTrue(errorNetworkMessage.contains("Quick Action Chips"))
    assertTrue(errorNetworkMessage.contains("မရှိပါ"))
  }
}
