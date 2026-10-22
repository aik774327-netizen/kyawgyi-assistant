package com.example.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX-03 regression tests — the pure navigation parser.
 *
 * Device evidence B1: the user's exact phrases "နောက်ဆုတ် /
 * အောက်ရွှေ့ / အပေါ်ရွှေ့" did not work anywhere on the main voice
 * path. These tests pin the phrases (and their prohibitive forms,
 * which must never execute) at parser level.
 */
class NavigationCommandsTest {

    // T16-03-P1: the user's exact phrases parse to the right actions.
    @Test
    fun t16_03_p1_userPhrasesParse() {
        assertEquals(NavigationAction.BACK, NavigationCommands.parse("နောက်ဆုတ်"))
        assertEquals(NavigationAction.SCROLL_DOWN, NavigationCommands.parse("အောက်ရွှေ့"))
        assertEquals(NavigationAction.SCROLL_UP, NavigationCommands.parse("အပေါ်ရွှေ့"))
        assertEquals(NavigationAction.BACK, NavigationCommands.parse("နောက်ပြန်"))
        assertEquals(NavigationAction.SCROLL_DOWN, NavigationCommands.parse("အောက်ဆင်း"))
        assertEquals(NavigationAction.SCROLL_UP, NavigationCommands.parse("အပေါ်တက်"))
    }

    // T16-03-P2: prohibitive forms parse to null (never execute) —
    // including nav verbs the shared ISSUE-013 guard's verb set was
    // not seeded with. Detection must still FIND the candidate under
    // the မ…ပါနဲ့ circumfix (the negated surface form does not
    // contain the affirmative marker as a substring), so the router
    // answers with the veto response rather than its dead-end
    // message; the veto itself is what parse() enforces.
    @Test
    fun t16_03_p2_negatedNavigationNeverParses() {
        assertTrue(NavigationCommands.isNegatedNavigation("နောက်မဆုတ်ပါနဲ့"))
        assertNull(NavigationCommands.parse("နောက်မဆုတ်ပါနဲ့"))
        assertNull(NavigationCommands.parse("အောက်မရွှေ့ပါနဲ့"))
        assertNull(NavigationCommands.parse("အပေါ်မရွှေ့ပါနဲ့"))
        assertTrue(NavigationCommands.isNegatedNavigation("နောက်မပြန်ပါနဲ့"))
        assertNull(NavigationCommands.parse("နောက်မပြန်ပါနဲ့"))
        // The candidate IS detected (veto path), not merely unmatched:
        assertEquals(NavigationAction.BACK, NavigationCommands.detect("နောက်မဆုတ်ပါနဲ့"))
        assertEquals(NavigationAction.SCROLL_DOWN, NavigationCommands.detect("အောက်မရွှေ့ပါနဲ့"))
        assertEquals(NavigationAction.SCROLL_UP, NavigationCommands.detect("အပေါ်မရွှေ့ပါနဲ့"))
    }

    // T16-03-P3: non-navigation text does not match (no false routes),
    // including words that merely resemble the markers.
    @Test
    fun t16_03_p3_nonNavigationTextDoesNotMatch() {
        assertNull(NavigationCommands.parse("မနက်ဖြန် ဘာလုပ်မလဲ"))
        assertNull(NavigationCommands.parse("ဝိုင်ဖိုင် ဖွင့်"))
        assertNull(NavigationCommands.parse("သီချင်း ဖွင့်"))
        assertNull(NavigationCommands.parse(""))
    }

    // T16-03-P4: affirmative phrases containing the nav verbs are NOT
    // flagged as negated (over-blocking check, same spirit as the
    // ISSUE-013 guard's affirmative controls).
    @Test
    fun t16_03_p4_affirmativeNotFlaggedNegated() {
        assertTrue(!NavigationCommands.isNegatedNavigation("အောက်ရွှေ့ပေး"))
        assertTrue(!NavigationCommands.isNegatedNavigation("နောက်ဆုတ်ပေး"))
        assertEquals(NavigationAction.SCROLL_DOWN, NavigationCommands.parse("အောက်ရွှေ့ပေး"))
    }
}
