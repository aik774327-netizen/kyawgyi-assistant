package com.example

import com.example.hardware.HexDisciplineValidator
import com.example.models.IrButtonProfile
import com.example.models.KennedeButton
import com.example.nlu.MyanmarVoiceParser
import com.example.nlu.ParsedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Item 5: Absolute Hex Discipline & Zero-Hallucination Protocol Analysis.
 */
class HexDisciplineTest {

  @Test
  fun testZeroHallucinationSafe_blocksUncapturedSignals() {
    val unlearned = IrButtonProfile(
      captured = false,
      protocol = null,
      command = null,
      rawTiming = emptyList()
    )
    assertFalse("Uncaptured button must NOT pass zero-hallucination check", HexDisciplineValidator.isZeroHallucinationSafe(unlearned))

    val capturedWithCommand = IrButtonProfile(
      captured = true,
      protocol = "NEC",
      command = "0x12",
      address = "0x00",
      rawTiming = listOf(9000, 4500, 560, 560)
    )
    assertTrue("Captured button with valid command must pass zero-hallucination check", HexDisciplineValidator.isZeroHallucinationSafe(capturedWithCommand))
  }

  @Test
  fun testNecHexParityValidation() {
    // 0x00FF10EF:
    // Address: 0x00, Inverted Address: 0xFF (0x00 xor 0xFF == 0xFF -> Pass)
    // Command: 0x10, Inverted Command: 0xEF (0x10 xor 0xEF == 0xFF -> Pass)
    val result = HexDisciplineValidator.validateNecHex("0x00FF10EF")
    assertTrue("Valid NEC 32-bit hex should be valid", result.isValid)
    assertTrue("Zero-Hallucination safe", result.isZeroHallucinationSafe)
    assertEquals(0x00, result.address)
    assertEquals(0xFF, result.invertedAddress)
    assertEquals(0x10, result.command)
    assertEquals(0xEF, result.invertedCommand)
    assertTrue("Address parity must pass", result.addressParityValid)
    assertTrue("Command parity must pass", result.commandParityValid)
    assertNotNull(result.binaryRepresentation)
  }

  @Test
  fun testNecTimingArrayGeneration() {
    // Standard NEC timing array length: 1 leader mark + 1 leader space + 32 bits * 2 + 1 stop mark = 67
    val timings = HexDisciplineValidator.generateNecTimingArray(address = 0x00, command = 0x10)
    assertEquals(67, timings.size)
    assertEquals(9000, timings[0]) // Leader mark
    assertEquals(4500, timings[1]) // Leader space
    assertEquals(560, timings[66]) // Stop bit
  }

  @Test
  fun testCppAndJsonExportSnippets() {
    val profile = IrButtonProfile(
      captured = true,
      protocol = "NEC",
      address = "0x00",
      command = "0x10",
      rawData = "0x00FF10EF",
      rawTiming = listOf(9000, 4500, 560, 560)
    )

    val cppCode = HexDisciplineValidator.generateCppRawSnippet("power", profile.rawTiming)
    assertTrue("C++ code must contain PROGMEM and timing array", cppCode.contains("PROGMEM") && cppCode.contains("rawData_power"))

    val jsonCode = HexDisciplineValidator.generateJsonSnippet(KennedeButton.POWER, profile)
    assertTrue("JSON snippet must contain captured true and protocol NEC", jsonCode.contains("\"captured\": true") && jsonCode.contains("\"protocol\": \"NEC\""))
  }

  @Test
  fun testVoiceParserInspectIrWaveformIntent() {
    val intent1 = MyanmarVoiceParser.parse("ကျော်ကြီး လှိုင်းပုံစံ စစ်ဆေးပါ")
    assertTrue("Must parse waveform command", intent1 is ParsedIntent.InspectIrWaveform)

    val intent2 = MyanmarVoiceParser.parse("ကျော်ကြီး အချက်ပြလှိုင်း ပြပါ")
    assertTrue("Must parse signal pulse command", intent2 is ParsedIntent.InspectIrWaveform)

    val intent3 = MyanmarVoiceParser.parse("ကျော်ကြီး ပါဝါ လှိုင်းပုံစံ စစ်ဆေးပါ")
    assertTrue("Must parse power waveform command", intent3 is ParsedIntent.InspectIrWaveform)
    assertEquals("power", (intent3 as ParsedIntent.InspectIrWaveform).buttonKey)
  }
}
