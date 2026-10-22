package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-04 regression tests — bubble-parser hardware toggles.
 *
 * Defect (B3 trace): MyanmarCommands had NO Wi-Fi/Bluetooth/
 * flashlight patterns at all, so on the bubble path these phrases
 * resolved to UNKNOWN and nothing happened. These tests pin the new
 * toggle parsing, the legacy Pair payload contract ("ON"/"OFF"),
 * and that the ISSUE-013 negation veto still fires first.
 */
class MyanmarCommandsToggleTest {

    // T16-04-08: toggle phrases parse with the right type + direction.
    @Test
    fun t16_04_08_toggleParsing() {
        val wifiOn = MyanmarCommands.parse("ဝိုင်ဖိုင် ဖွင့်")
        assertEquals(MyanmarCommandType.WIFI_TOGGLE, wifiOn?.type)
        assertTrue(wifiOn?.isEnable == true)

        val btOff = MyanmarCommands.parse("ဘလူးတူးသ် ပိတ်")
        assertEquals(MyanmarCommandType.BLUETOOTH_TOGGLE, btOff?.type)
        assertFalse(btOff?.isEnable == true)

        val flashOn = MyanmarCommands.parse("ဓာတ်မီး ဖွင့်")
        assertEquals(MyanmarCommandType.FLASHLIGHT_TOGGLE, flashOn?.type)
        assertTrue(flashOn?.isEnable == true)
    }

    // T16-04-09: the legacy Pair API carries the toggle + ON/OFF.
    @Test
    fun t16_04_09_legacyPairCarriesOnOff() {
        assertEquals(
            MyanmarCommandType.WIFI_TOGGLE to "OFF",
            MyanmarCommands.parseCommand("ဝိုင်ဖိုင် ပိတ်")
        )
        assertEquals(
            MyanmarCommandType.BLUETOOTH_TOGGLE to "ON",
            MyanmarCommands.parseCommand("ဘလူးတူးသ် ဖွင့်")
        )
    }

    // T16-04-10: a negated toggle is vetoed (isNegated), never an ON.
    @Test
    fun t16_04_10_negatedToggleVetoed() {
        val parsed = MyanmarCommands.parse("ဝိုင်ဖိုင် မဖွင့်ပါနဲ့")
        assertTrue(parsed?.isNegated == true)
        assertEquals(
            MyanmarCommandType.UNKNOWN,
            MyanmarCommands.parseCommand("ဝိုင်ဖိုင် မဖွင့်ပါနဲ့").first
        )
    }
}
