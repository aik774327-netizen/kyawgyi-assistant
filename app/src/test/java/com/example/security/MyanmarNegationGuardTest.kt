package com.example.security

import com.example.security.MyanmarNegationGuard.NegationResult
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE-013 regression tests for the shared prohibitive-negation
 * detector, ID T15-013-01 (Phase 1.5 plan §2.1).
 *
 * Pure JUnit — the guard has no Android dependencies by design.
 * NEGATED cases: the 8 mandated Phase 1 inputs (§4.2 of the audit)
 * plus the plan's variants. NOT_NEGATED cases: the affirmative
 * controls, including the over-blocking traps — words that merely
 * contain မ (မီး "light", မနက်ဖြန် "tomorrow", the name မောင်မောင်)
 * or a declarative မဟုတ်-style negation that is not a prohibitive
 * command negation (မသိဘူး, မရှိဘူး) must never veto.
 */
class MyanmarNegationGuardTest {

    private fun assertNegated(input: String) {
        val result = MyanmarNegationGuard.detectProhibitiveNegation(input)
        assertTrue(
            "expected NEGATED for '$input', got $result",
            result is NegationResult.Negated
        )
    }

    private fun assertNotNegated(input: String) {
        val result = MyanmarNegationGuard.detectProhibitiveNegation(input)
        assertTrue(
            "expected NOT_NEGATED for '$input', got $result",
            result is NegationResult.NotNegated
        )
    }

    // ---------------------------------------------------------------
    // Mandated inputs (audit §4.2) + plan variants → NEGATED
    // ---------------------------------------------------------------

    @Test
    fun t15_013_01_mandatedNegations_detected() {
        // Row 1: bare "don't open"
        assertNegated("မဖွင့်ပါနဲ့")
        // Row 2: "don't close" (also the ISSUE-028 screen-lock input)
        assertNegated("မပိတ်ပါနဲ့")
        // Row 3: "don't call"
        assertNegated("မခေါ်ပါနဲ့")
        // Row 4: "don't send a message"
        assertNegated("စာမပို့ပါနဲ့")
        // Row 5: "don't turn on the light"
        assertNegated("မီးမဖွင့်နဲ့")
        // Row 8: "don't open Facebook"
        assertNegated("မဖွင့်ပါနဲ့ Facebook")
    }

    @Test
    fun t15_013_01_planVariants_detected() {
        assertNegated("ဓာတ်မီး မဖွင့်ပါနဲ့")
        assertNegated("ဝိုင်ဖိုင် မဖွင့်ပါနဲ့")
        assertNegated("YouTube မဖွင့်ပါနဲ့")
        assertNegated("ဖုန်းမခေါ်ပါနဲ့")
        assertNegated("မဖွင့်နဲ့")
        assertNegated("မလုပ်ပါနဲ့")
        assertNegated("ဖိုင်တွေ မဖျက်ပါနဲ့")
        // Circumfix-only forms (no explicit marker covers these):
        assertNegated("အသံမတင်ပါနဲ့")
        assertNegated("မသွားတော့ဘူး")
        // ASR-style spaced prohibitive:
        assertNegated("မဖွင့်ပါ နဲ့")
    }

    // ---------------------------------------------------------------
    // Affirmative controls + over-blocking traps → NOT_NEGATED
    // ---------------------------------------------------------------

    @Test
    fun t15_013_01_affirmativeControls_notNegated() {
        // Row 6: "open [it]" — bare affirmative, no target.
        assertNotNegated("ဖွင့်ပါ")
        // Row 7: "Kyaw Gyi, open Facebook"
        assertNotNegated("ကျော်ကြီး Facebook ဖွင့်ပါ")
        assertNotNegated("Facebook ဖွင့်ပေးပါ")
        assertNotNegated("ဓာတ်မီးဖွင့်")
        assertNotNegated("Facebook ပိတ်ပါ")
        assertNotNegated("စာပို့ပါ")
        assertNotNegated("ဝိုင်ဖိုင် ဖွင့်ပါ")
        assertNotNegated("ရပ်ပါ")
        assertNotNegated("သီချင်းဖွင့်ပေးပါ")
        assertNotNegated("")
    }

    @Test
    fun t15_013_01_overBlockingTraps_notNegated() {
        // မီး ("light") starts with မ but is not a negation:
        assertNotNegated("မီးဖွင့်ပေး")
        assertNotNegated("မီးဖွင့်ပါ")
        // မနက်ဖြန် ("tomorrow") + an ordinary dictated sentence:
        assertNotNegated("မနက်ဖြန် နေ့လယ်မှာ အစည်းအဝေး ရှိပါတယ်")
        // A person's name containing မ syllables + a genuine command:
        assertNotNegated("မောင်မောင်ကို ဖုန်းခေါ်ပေးပါ")
        // Declarative negations that are not prohibitive commands:
        assertNotNegated("မသိဘူး")
        assertNotNegated("မရှိဘူး")
    }
}
