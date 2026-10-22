package com.example.security

/**
 * ISSUE-013 — shared prohibitive-negation detector for every live
 * command path (Phase 1.5 remediation plan §2.1).
 *
 * Before this guard existed, no live matcher checked for negation at
 * all: matching was `contains()`-only, so "မဖွင့်ပါနဲ့ Facebook"
 * ("don't open Facebook") still contained the affirmative keyword
 * "ဖွင့်" and executed OPEN_FACEBOOK on every entry point
 * (Phase 1 audit §4.2 row 8).
 *
 * Design (per the approved plan):
 *  - Pure Kotlin, no Android dependencies, so the detector itself is
 *    unit-testable without a device or Robolectric.
 *  - Detection = (a) an explicit marker list seeded from the two live
 *    confirmation-flow lists that already exist in the codebase
 *    ([com.example.actions.CallSmsConfirmationCoordinator]'s
 *    cancellation keywords and [ActionPermissionGatekeeper]'s
 *    CANCELLATION_KEYWORDS), PLUS the generic prohibitive forms those
 *    lists lack (မဖွင့်ပါနဲ့ / မပိတ်ပါနဲ့ / …), and (b) a conservative
 *    circumfix check: မ + known action verb + a prohibitive suffix
 *    (ပါနဲ့ / နဲ့ / တော့ဘူး / တော့ပါ / ဘူး / ရဘူး).
 *  - Conservative bias: on uncertainty the result is NOT_NEGATED
 *    rather than vetoing a legitimate command. The marker and verb
 *    lists stay explicit and test-pinned (T15-013-01) so behaviour
 *    cannot drift silently.
 *  - The dead MyanmarIntentEngine stack is deliberately NOT reused:
 *    its negativeTriggers are per-intent anti-keywords (competing-verb
 *    disqualifiers), not prohibitive negation (plan §2.1).
 *
 * Known, accepted limitation (documented in the plan): reported or
 * narrative negation ("သူက မဖွင့်ဘူးလို့ ပြောတယ်") can false-positive
 * the veto on voice paths. On the dictation path the consequence is
 * nil — the segment is still inserted as document text.
 */
object MyanmarNegationGuard {

    /**
     * The honest Burmese no-action response every funnel returns when
     * a command is vetoed: the user is told plainly that the action
     * was NOT carried out.
     */
    const val NO_ACTION_RESPONSE_MM: String =
        "နားလည်ပါပြီခင်ဗျာ။ အဲဒီ လုပ်ဆောင်ချက်ကို မလုပ်ဆောင်ပါဘူးခင်ဗျာ။"

    sealed interface NegationResult {
        /** Prohibitive negation detected; [matchedMarker] is the exact marker or circumfix pattern that matched. */
        data class Negated(val matchedMarker: String) : NegationResult

        /** No prohibitive negation detected; the command may proceed. */
        data object NotNegated : NegationResult
    }

    /**
     * Explicit prohibitive markers. The first block is seeded from the
     * two live confirmation lists; the second block adds the generic
     * prohibitive forms those lists lack (plan §2.1 evidence).
     */
    private val EXPLICIT_MARKERS: List<String> = listOf(
        // Seeded from CallSmsConfirmationCoordinator.cancellationKeywords
        // and ActionPermissionGatekeeper.CANCELLATION_KEYWORDS.
        "မခေါ်ပါနဲ့", "မခေါ်နဲ့", "မခေါ်တော့ဘူး", "မခေါ်တော့ပါဘူး", "မခေါ်ဘူး",
        "မဆက်ပါနဲ့", "မဆက်နဲ့", "မဆက်တော့ဘူး",
        "မပို့ပါနဲ့", "မပို့နဲ့", "မပို့တော့ဘူး",
        "မလုပ်နဲ့", "မလုပ်ပါနဲ့", "မလုပ်တော့ဘူး", "မလုပ်တော့ပါ",
        // Generic prohibitive forms the confirmation lists lack.
        "မဖွင့်ပါနဲ့", "မဖွင့်နဲ့", "မဖွင့်တော့ဘူး", "မဖွင့်တော့ပါ",
        "မပိတ်ပါနဲ့", "မပိတ်နဲ့", "မပိတ်တော့ဘူး", "မပိတ်တော့ပါ",
        "မသွားပါနဲ့", "မသွားနဲ့",
        "မဖျက်ပါနဲ့", "မဖျက်နဲ့",
        "မထွက်ပါနဲ့", "မထွက်နဲ့",
        // English prohibitives (the confirmation lists carry "don't").
        "don't", "do not"
    )

    /**
     * Action verbs from the existing live command vocabularies
     * (IntentRouter / MyanmarCommands / GoogleHome / VoiceEngine), used
     * by the circumfix check. Kept deliberately narrow: a match needs
     * မ immediately before the verb AND a prohibitive suffix
     * immediately after it, so affirmative commands whose words merely
     * contain မ (မီး "light", မနက်ဖြန် "tomorrow", names like
     * မောင်မောင်) can never match.
     */
    private val ACTION_VERBS: List<String> = listOf(
        "ဖွင့်", "ပိတ်", "ခေါ်", "ဆက်", "ပို့", "လုပ်", "ဖျက်",
        "သွား", "ထွက်", "တင်", "ချ", "ရပ်", "ဖတ်"
    )

    /** Prohibitive suffixes that complete the မ … circumfix. */
    private val CIRCUMFIX_SUFFIXES: List<String> = listOf(
        "ပါနဲ့", "နဲ့", "တော့ဘူး", "တော့ပါ", "ရဘူး", "ဘူး"
    )

    /**
     * Detect prohibitive negation in [text]. The funnels pass their
     * own normalized text; the guard additionally trims/lowercases and
     * retries with whitespace removed, because ASR output sometimes
     * splits a prohibitive form across spaces ("မ ဖွင့် ပါနဲ့").
     */
    fun detectProhibitiveNegation(text: String): NegationResult {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return NegationResult.NotNegated

        val candidates = listOf(clean, clean.replace(" ", ""))
        for (candidate in candidates) {
            for (marker in EXPLICIT_MARKERS) {
                if (candidate.contains(marker)) {
                    return NegationResult.Negated(marker)
                }
            }
            for (verb in ACTION_VERBS) {
                for (suffix in CIRCUMFIX_SUFFIXES) {
                    val pattern = "မ" + verb + suffix
                    if (candidate.contains(pattern)) {
                        return NegationResult.Negated(pattern)
                    }
                }
            }
        }
        return NegationResult.NotNegated
    }

    /** Convenience for call sites that only need the boolean. */
    fun isNegated(text: String): Boolean =
        detectProhibitiveNegation(text) is NegationResult.Negated
}
