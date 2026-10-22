package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX-03 regression tests — in-app Back policy.
 *
 * Device defect D2: no BackHandler existed anywhere, so system Back
 * exited the app from every screen. The policy pinned here: close an
 * open full-screen route first, then fall back to the home tab, and
 * only at the root let Back exit.
 */
class MainBackNavigationTest {

    // T16-03-B1: dictation open -> Back closes dictation (even from a
    // non-home tab and even if the chat flag is somehow also set).
    @Test
    fun t16_03_b1_dictationClosesFirst() {
        assertEquals(
            MainBackAction.CLOSE_DICTATION,
            resolveBackAction(isDictationOpen = true, isChatboxOpen = false, selectedTab = 0)
        )
        assertEquals(
            MainBackAction.CLOSE_DICTATION,
            resolveBackAction(isDictationOpen = true, isChatboxOpen = true, selectedTab = 3)
        )
    }

    // T16-03-B2: chatbox open -> Back closes the chatbox.
    @Test
    fun t16_03_b2_chatCloses() {
        assertEquals(
            MainBackAction.CLOSE_CHAT,
            resolveBackAction(isDictationOpen = false, isChatboxOpen = true, selectedTab = 2)
        )
    }

    // T16-03-B3: any non-home tab -> Back goes to the home tab.
    @Test
    fun t16_03_b3_nonHomeTabGoesHome() {
        assertEquals(
            MainBackAction.GO_HOME_TAB,
            resolveBackAction(isDictationOpen = false, isChatboxOpen = false, selectedTab = 4)
        )
        assertEquals(
            MainBackAction.GO_HOME_TAB,
            resolveBackAction(isDictationOpen = false, isChatboxOpen = false, selectedTab = 1)
        )
    }

    // T16-03-B4: at the root (home tab, nothing open) -> EXIT, i.e.
    // the BackHandler stays disabled and the system default applies.
    @Test
    fun t16_03_b4_rootExits() {
        assertEquals(
            MainBackAction.EXIT,
            resolveBackAction(isDictationOpen = false, isChatboxOpen = false, selectedTab = 0)
        )
    }
}
