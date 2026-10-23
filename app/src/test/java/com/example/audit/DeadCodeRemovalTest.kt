package com.example.audit

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ISSUE-045 — the dead phonetic transliterator (zero callers since FIX-02
 * removed the G2P fallback path) must stay deleted: its presence invited
 * exactly the fabricated "verification" that ISSUE-033 removed (the old
 * verifier cited this removed feature as if it were active).
 */
@RunWith(RobolectricTestRunner::class)
class DeadCodeRemovalTest {

    @Test
    fun t17_045_01_deadTransliterator_isGone() {
        val outerMethods = Class.forName("com.example.utils.MyanmarTTSEngine")
            .declaredMethods.map { it.name }
        val companionMethods = Class.forName("com.example.utils.MyanmarTTSEngine\$Companion")
            .declaredMethods.map { it.name }
        assertFalse(
            "transliterateBurmeseToSpeakablePhonetics must not exist",
            (outerMethods + companionMethods).contains("transliterateBurmeseToSpeakablePhonetics")
        )
    }
}
