package com.example.audit

/**
 * ISSUE-034 — Typed outcome of a live verification.
 *
 * A blocked failed-method record may transition to VERIFIED_SUCCESS / RESOLVED
 * ONLY from a [VerificationResult] whose [outcome] is [VerificationOutcome.VERIFIED].
 * The result is bound to a specific error record ([errorId] + [commandKey]),
 * carries the time the check actually ran ([checkedAtMs]) and the mechanism
 * that produced the evidence ([evidenceSource]). Caller-supplied free-text
 * "evidence" strings are never accepted as verification anywhere in the
 * error-learning system.
 */
enum class VerificationOutcome {
    /** The live check ran and the previously failing condition is now satisfied. */
    VERIFIED,

    /** No conclusive check could be performed (no verifier branch, timeout, ...). */
    UNVERIFIED,

    /** The live check ran and the condition is still failing. */
    FAILED,

    /** A required resource is still missing, so the method remains blocked. */
    BLOCKED
}

data class VerificationResult(
    val outcome: VerificationOutcome,
    val errorId: String,
    val commandKey: String,
    val checkedAtMs: Long,
    val evidenceSource: String,
    val detail: String,
    val stages: List<String> = emptyList()
) {
    fun summaryLine(): String =
        "[$evidenceSource] ${outcome.name} @ $checkedAtMs: $detail" +
            if (stages.isEmpty()) "" else " | stages: ${stages.joinToString(" -> ")}"
}
