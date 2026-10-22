package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-013 / ISSUE-028 regression tests for the accessibility funnel
 * (entry point D, MyanmarCommands), IDs T15-013-03 / T15-028-01.
 *
 * Pre-fix (Phase 1 audit §4.2 row 2, column A): "မပိတ်ပါနဲ့" ("don't
 * close") contains the STOP word "ပိတ်ပါ", so parse() flagged it as a
 * stop command and parseCommand() mapped it to LOCK_SCREEN — a
 * "don't close" utterance locked the device screen. A prohibitive
 * negation must produce no action at all on this path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyanmarCommandsNegationTest {

    // T15-028-01 — the traced ISSUE-028 repro: "မပိတ်ပါနဲ့" must NOT
    // resolve to LOCK_SCREEN (and must not be flagged as a stop word).
    @Test
    fun t15_028_01_negatedClose_doesNotLockScreen() {
        val parsed = MyanmarCommands.parse("မပိတ်ပါနဲ့")
        assertFalse(
            "negated close must not be treated as a STOP word",
            parsed?.isStop ?: false
        )
        val (type, _) = MyanmarCommands.parseCommand("မပိတ်ပါနဲ့")
        assertNotEquals(MyanmarCommandType.LOCK_SCREEN, type)
        assertEquals(MyanmarCommandType.UNKNOWN, type)
    }

    // T15-013-03 — negated open on the accessibility path: audit row 8
    // resolved OPEN_APP + facebook pre-fix; row 1 resolved an
    // empty-target OPEN_APP. Neither may happen now.
    @Test
    fun t15_013_03_negatedOpen_noOpenAppOutcome() {
        val (type, _) = MyanmarCommands.parseCommand("မဖွင့်ပါနဲ့ Facebook")
        assertNotEquals(MyanmarCommandType.OPEN_APP, type)
        assertEquals(MyanmarCommandType.UNKNOWN, type)

        val bareParsed = MyanmarCommands.parse("မဖွင့်ပါနဲ့")
        assertNotEquals(MyanmarCommandType.OPEN_APP, bareParsed?.type)
    }

    // Controls — affirmative behaviour on this path is unchanged:
    // a genuine open still opens, a genuine close still closes, and a
    // genuine stop word still stops (ISSUE-028 fix removes only the
    // negated collision, not the STOP mechanism itself).
    @Test
    fun controls_affirmativeCommands_unchanged() {
        val (openType, _) = MyanmarCommands.parseCommand("ကျော်ကြီး Facebook ဖွင့်ပါ")
        assertEquals(MyanmarCommandType.OPEN_APP, openType)

        // Note: the app-name mapping keys are lowercase ("facebook"),
        // and parse() matches case-sensitively — the lowercase form is
        // the genuine-close input this path actually recognises.
        val (closeType, _) = MyanmarCommands.parseCommand("facebook ပိတ်ပါ")
        assertEquals(MyanmarCommandType.CLOSE_APP, closeType)

        val stopParsed = MyanmarCommands.parse("ရပ်ပါ")
        assertTrue("genuine stop word must still flag isStop", stopParsed?.isStop == true)
    }
}
