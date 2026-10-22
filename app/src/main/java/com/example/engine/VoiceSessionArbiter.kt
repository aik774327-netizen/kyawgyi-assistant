package com.example.engine

/**
 * FIX-06 (Phase 1.5 device remediation): single-owner microphone
 * arbitration. Design: ~/workspace/your_files/
 * phase1.5-fix06-mic-arbitration-design-2026-10-23.md
 *
 * Before this, the app had 4+ independent SpeechRecognizer owners
 * (in-app VoiceSessionManager, floating-bubble SpeechHandler, IR
 * VoiceEngine/SttEngineManager, chat inline mic, dictation), none of
 * which knew about the others; the codebase relied on pairwise
 * stopListening patches, and concurrent starts produced
 * ERROR_RECOGNIZER_BUSY loops and the user's "two microphone UIs"
 * confusion (defect D4).
 *
 * The rule is now single and central: whoever starts listening
 * first stops whoever was listening. Each owner registers a stopper
 * (its own stop function) once; a new owner's request invokes the
 * previous owner's stopper and takes ownership. Ownership is
 * transferred BEFORE the stopper runs, so a stopper that releases or
 * re-requests for the old owner cannot resurrect it.
 */
enum class VoiceSessionOwner {
    NONE, IN_APP_SESSION, BUBBLE, IR_SERVICE, CHAT_MIC, DICTATION, WAKE_WORD
}

object VoiceSessionArbiter {

    private val lock = Any()
    private val stoppers = mutableMapOf<VoiceSessionOwner, () -> Unit>()

    @Volatile
    private var currentOwner: VoiceSessionOwner = VoiceSessionOwner.NONE

    /** Registers (or replaces) the stop function for [owner]. */
    fun registerStopper(owner: VoiceSessionOwner, stopper: () -> Unit) {
        if (owner == VoiceSessionOwner.NONE) return
        synchronized(lock) { stoppers[owner] = stopper }
    }

    /** Removes the stopper; if [owner] currently owns the mic, ownership clears. */
    fun unregisterStopper(owner: VoiceSessionOwner) {
        synchronized(lock) {
            stoppers.remove(owner)
            if (currentOwner == owner) currentOwner = VoiceSessionOwner.NONE
        }
    }

    /**
     * Requests the microphone for [newOwner]. Returns the previous
     * owner (NONE if the mic was free, or [newOwner] itself if it
     * already owned it — re-acquiring is idempotent and invokes no
     * stopper). On a transfer, the previous owner's stopper is
     * invoked; a throwing stopper is contained and never breaks
     * arbitration.
     */
    fun requestOwnership(newOwner: VoiceSessionOwner): VoiceSessionOwner {
        if (newOwner == VoiceSessionOwner.NONE) return currentOwner
        val previous: VoiceSessionOwner
        val stopperToInvoke: (() -> Unit)?
        synchronized(lock) {
            previous = currentOwner
            if (previous == newOwner) return newOwner
            currentOwner = newOwner
            stopperToInvoke = if (previous != VoiceSessionOwner.NONE) stoppers[previous] else null
        }
        if (previous != VoiceSessionOwner.NONE) {
            try {
                stopperToInvoke?.invoke()
            } catch (e: Exception) {
                // Contained by design: a broken stopper must not wedge
                // the microphone for every other owner.
            }
        }
        return previous
    }

    /** Releases the microphone if [owner] currently owns it; otherwise a no-op. */
    fun releaseOwnership(owner: VoiceSessionOwner) {
        synchronized(lock) {
            if (currentOwner == owner) currentOwner = VoiceSessionOwner.NONE
        }
    }

    fun currentOwner(): VoiceSessionOwner = currentOwner

    /** Test-only: clears all stoppers and ownership between tests. */
    fun resetForTesting() {
        synchronized(lock) {
            stoppers.clear()
            currentOwner = VoiceSessionOwner.NONE
        }
    }
}
