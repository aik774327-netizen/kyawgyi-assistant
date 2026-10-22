package com.example.router

import com.example.security.MyanmarNegationGuard

/**
 * FIX-03 (Phase 1.5 device remediation): voice Back / Scroll commands
 * for the SmartRouter/chat voice path.
 *
 * Device evidence B1: "နောက်ဆုတ် / အောက်ရွှေ့ / အပေါ်ရွှေ့" did NOT
 * work. Root cause: navigation intents existed ONLY inside
 * KyawGyiAccessibilityService's MyanmarCommands table, reachable solely
 * through the floating bubble. The main voice path (KyawGyiSmartRouter)
 * had no Back/Scroll intent at all, so these phrases fell through
 * every layer to the dead-end message. This file is the pure parser
 * for the new SmartRouter navigation layer; execution goes through
 * [AccessibilityNavigationBridge] to the accessibility service, and
 * the router answers honestly when that service is unavailable.
 */
enum class NavigationAction { BACK, SCROLL_UP, SCROLL_DOWN }

object NavigationCommands {

    private val scrollDownMarkers = listOf(
        "အောက်ရွှေ့", "အောက်သို့ရွှေ့", "အောက်သို့ ရွှေ့", "အောက်ဆင်း",
        "အောက်သို့ဆင်း", "အောက်သို့ ဆင်း", "scroll down"
    )
    private val scrollUpMarkers = listOf(
        "အပေါ်ရွှေ့", "အပေါ်သို့ရွှေ့", "အပေါ်သို့ ရွှေ့", "အပေါ်တက်",
        "အပေါ်သို့တက်", "အပေါ်သို့ တက်", "scroll up"
    )
    private val backMarkers = listOf(
        // "နောက်ဆုတ်" is the user's exact phrase (evidence B1); it was
        // absent from the accessibility parser's BACK table too.
        "နောက်ဆုတ်", "နောက်ပြန်", "နောက်သို့ပြန်", "နောက်သို့ ပြန်",
        "ပြန်သွား", "go back"
    )

    /**
     * Prohibitive forms of the navigation verbs. The shared
     * [MyanmarNegationGuard] verb set is seeded from open/close/call
     * commands and may not cover ဆုတ်/ရွှေ့/ဆင်း/တက်, so the
     * navigation layer checks its own verbs as defense-in-depth: a
     * negated navigation phrase must NEVER execute.
     */
    private val negatedNavMarkers = listOf("မဆုတ်", "မရွှေ့", "မဆင်း", "မတက်", "မပြန်")

    /**
     * Negated surface forms. Burmese prohibitives wrap the verb in
     * the မ…ပါနဲ့ circumfix, so "နောက်မဆုတ်ပါနဲ့" does NOT contain
     * the affirmative marker "နောက်ဆုတ်" as a substring. Detection
     * must still recognize these as navigation candidates so the
     * router can answer with the veto response instead of falling
     * through to its dead-end message (caught by
     * SmartRouterNavigationTest T16-03-06 in CI).
     */
    private val scrollDownNegatedMarkers = listOf("အောက်မရွှေ့", "အောက်မဆင်း")
    private val scrollUpNegatedMarkers = listOf("အပေါ်မရွှေ့", "အပေါ်မတက်")
    private val backNegatedMarkers = listOf("နောက်မဆုတ်", "နောက်မပြန်")

    /** Pattern-only detection (affirmative AND negated surface forms). */
    fun detect(text: String): NavigationAction? {
        val lowered = text.lowercase()
        return when {
            (scrollDownMarkers + scrollDownNegatedMarkers).any { text.contains(it) || lowered.contains(it) } ->
                NavigationAction.SCROLL_DOWN
            (scrollUpMarkers + scrollUpNegatedMarkers).any { text.contains(it) || lowered.contains(it) } ->
                NavigationAction.SCROLL_UP
            (backMarkers + backNegatedMarkers).any { text.contains(it) || lowered.contains(it) } ->
                NavigationAction.BACK
            else -> null
        }
    }

    /** True when the phrase prohibits the navigation action. */
    fun isNegatedNavigation(text: String): Boolean =
        MyanmarNegationGuard.isNegated(text) || negatedNavMarkers.any { text.contains(it) }

    /** Detection + negation veto: the action to execute, or null. */
    fun parse(text: String): NavigationAction? {
        val action = detect(text) ?: return null
        return if (isNegatedNavigation(text)) null else action
    }
}

/** User-facing lines for the navigation layer. */
object NavigationResponses {
    const val SERVICE_UNAVAILABLE_MM: String =
        "ဒီ လုပ်ဆောင်ချက်အတွက် Accessibility Service လိုအပ်ပါတယ်ခင်ဗျာ။ Settings ထဲက Accessibility မှာ Kyaw Gyi ကို ဖွင့်ပေးပါ"
    const val DISPATCH_FAILED_MM: String = "မလုပ်နိုင်ခဲ့ပါခင်ဗျာ"

    fun dispatchedMessage(action: NavigationAction): String = when (action) {
        NavigationAction.BACK -> "နောက် ပြန်သွားပါပြီခင်ဗျာ"
        NavigationAction.SCROLL_UP -> "အပေါ်သို့ ရွှေ့ပေးလိုက်ပါပြီခင်ဗျာ"
        NavigationAction.SCROLL_DOWN -> "အောက်သို့ ရွှေ့ပေးလိုက်ပါပြီခင်ဗျာ"
    }
}

/**
 * Seam between the router and the accessibility service, so the
 * navigation layer is unit-testable with a fake. `perform` returns
 * true when the action was DISPATCHED to the accessibility service;
 * scroll gestures complete asynchronously inside the service, so
 * dispatch — not visual completion — is what true means here.
 */
interface AccessibilityNavigationBridge {
    fun isAvailable(): Boolean
    fun perform(action: NavigationAction): Boolean
}

/** Production bridge backed by the live accessibility service. */
class AndroidAccessibilityNavigationBridge : AccessibilityNavigationBridge {
    override fun isAvailable(): Boolean =
        com.example.service.KyawGyiAccessibilityService.instance != null

    override fun perform(action: NavigationAction): Boolean =
        com.example.service.KyawGyiAccessibilityService.instance
            ?.performNavigationAction(action) ?: false
}
