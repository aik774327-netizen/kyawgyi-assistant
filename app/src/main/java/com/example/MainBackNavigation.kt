package com.example

/**
 * FIX-03 (Phase 1.5 device remediation): in-app Back semantics for
 * MainAppDashboardScreen's manual tab navigation.
 *
 * Device defect D2: the app had NO BackHandler anywhere (0 hits
 * repo-wide). Navigation was manual `selectedTab` state, so the
 * Android system Back button exited the app from every screen —
 * there was no in-app "previous". The resolver is pure so the policy
 * is unit-testable:
 *
 * - a full-screen route is open (dictation, then chatbox) → close it;
 * - on a non-home tab → go to the home tab (index 0);
 * - at the root → EXIT (the BackHandler stays disabled and the
 *   system default — leaving the app — applies).
 */
enum class MainBackAction { CLOSE_DICTATION, CLOSE_CHAT, GO_HOME_TAB, EXIT }

fun resolveBackAction(
    isDictationOpen: Boolean,
    isChatboxOpen: Boolean,
    selectedTab: Int
): MainBackAction = when {
    isDictationOpen -> MainBackAction.CLOSE_DICTATION
    isChatboxOpen -> MainBackAction.CLOSE_CHAT
    selectedTab != 0 -> MainBackAction.GO_HOME_TAB
    else -> MainBackAction.EXIT
}
