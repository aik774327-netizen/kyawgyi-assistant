package com.example.service

import com.example.ocr.MyanmarScriptDetector
import com.example.security.MyanmarNegationGuard

/**
 * FIX-02 (Phase 1.5 device remediation): what the floating bubble may
 * SAY about a voice command — derived from the command's ACTUAL
 * execution result, never from keywords in the user's sentence.
 *
 * The previous implementation announced "အက်ပ် ဖွင့်ပေးလိုက်ပါပြီ"
 * whenever the spoken text contained "ဖွင့်" and "အက်ပ်ကို
 * ပိတ်လိုက်ပါပြီ" whenever it contained "ပိတ်"/"ထွက်" — without
 * looking at the execution result at all. It claimed success when the
 * accessibility service was inactive, when the app was not found, and
 * even for vetoed negations ("မဖွင့်ပါနဲ့" contains "ဖွင့်").
 *
 * Mapping rules (result strings come from
 * [KyawGyiAccessibilityService.executeVoiceCommand]):
 * - a prohibitive negation always gets the guard's no-action response;
 * - results that are already user-facing Burmese (executor / call-SMS
 *   coordinator outcomes) are spoken verbatim;
 * - known English success statuses map to a neutral Burmese completion;
 * - known failures map to honest Burmese failure lines;
 * - anything unrecognized maps to null — silence is honest, an
 *   invented claim is not.
 */
object FloatingFeedbackSpeech {

    const val SERVICE_INACTIVE_MM: String =
        "Accessibility Service မဖွင့်ရသေးပါခင်ဗျာ။ Settings ထဲက Accessibility မှာ Kyaw Gyi ကို ဖွင့်ပေးပါ"
    const val FAILED_MM: String = "မလုပ်နိုင်ခဲ့ပါခင်ဗျာ"
    const val NOT_FOUND_MM: String = "ရှာမတွေ့ပါခင်ဗျာ"
    const val NOT_UNDERSTOOD_MM: String = "နားမလည်ပါခင်ဗျာ"
    const val COMPLETED_MM: String = "လုပ်ဆောင်ပြီးပါပြီခင်ဗျာ"

    fun speechFor(spokenText: String, executionResult: String): String? {
        if (MyanmarNegationGuard.isNegated(spokenText)) {
            return MyanmarNegationGuard.NO_ACTION_RESPONSE_MM
        }
        val result = executionResult.trim()
        return when {
            result == "Accessibility service not active" -> SERVICE_INACTIVE_MM
            result.startsWith("Failed") -> FAILED_MM
            result.contains("not found") -> NOT_FOUND_MM
            result.startsWith("Command received:") -> NOT_UNDERSTOOD_MM
            result.startsWith("Wake Word") -> null
            MyanmarScriptDetector.containsMyanmar(result) -> result
            result.startsWith("Performed") ||
                result.startsWith("Opening App") ||
                result.startsWith("Closed App") ||
                result.startsWith("Executing Shortcut") ||
                result.endsWith("executed") -> COMPLETED_MM
            else -> null
        }
    }
}
