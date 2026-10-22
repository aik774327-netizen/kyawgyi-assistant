package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * FIX-06 regression tests — the microphone arbiter's semantics.
 *
 * Defect D4: 4+ independent recognizer owners with no coordination.
 * These tests pin the arbiter contract from the FIX-06 design doc:
 * grant when free, preempt (with stopper) on contention, idempotent
 * re-acquire, release only by the owner, and containment of throwing
 * stoppers.
 */
class VoiceSessionArbiterTest {

    @Before
    fun setUp() {
        VoiceSessionArbiter.resetForTesting()
    }

    // T16-06-A1: free mic is granted without invoking any stopper.
    @Test
    fun t16_06_a1_grantWhenFree() {
        val stops = AtomicInteger(0)
        VoiceSessionArbiter.registerStopper(VoiceSessionOwner.BUBBLE) { stops.incrementAndGet() }
        val previous = VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.DICTATION)
        assertEquals(VoiceSessionOwner.NONE, previous)
        assertEquals(VoiceSessionOwner.DICTATION, VoiceSessionArbiter.currentOwner())
        assertEquals(0, stops.get())
    }

    // T16-06-A2: a second owner preempts — the first owner's stopper
    // runs exactly once and ownership transfers.
    @Test
    fun t16_06_a2_preemptionStopsPrevious() {
        val bubbleStops = AtomicInteger(0)
        VoiceSessionArbiter.registerStopper(VoiceSessionOwner.BUBBLE) { bubbleStops.incrementAndGet() }
        VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.BUBBLE)

        val previous = VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.CHAT_MIC)
        assertEquals(VoiceSessionOwner.BUBBLE, previous)
        assertEquals(1, bubbleStops.get())
        assertEquals(VoiceSessionOwner.CHAT_MIC, VoiceSessionArbiter.currentOwner())
    }

    // T16-06-A3: same-owner re-acquire is idempotent (no self-stop).
    @Test
    fun t16_06_a3_sameOwnerIdempotent() {
        val stops = AtomicInteger(0)
        VoiceSessionArbiter.registerStopper(VoiceSessionOwner.IN_APP_SESSION) { stops.incrementAndGet() }
        VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.IN_APP_SESSION)
        val previous = VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.IN_APP_SESSION)
        assertEquals(VoiceSessionOwner.IN_APP_SESSION, previous)
        assertEquals(0, stops.get())
        assertEquals(VoiceSessionOwner.IN_APP_SESSION, VoiceSessionArbiter.currentOwner())
    }

    // T16-06-A4: release by a NON-owner is ignored; release by the
    // owner frees the mic.
    @Test
    fun t16_06_a4_releaseOnlyByOwner() {
        VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.DICTATION)
        VoiceSessionArbiter.releaseOwnership(VoiceSessionOwner.BUBBLE)
        assertEquals(VoiceSessionOwner.DICTATION, VoiceSessionArbiter.currentOwner())
        VoiceSessionArbiter.releaseOwnership(VoiceSessionOwner.DICTATION)
        assertEquals(VoiceSessionOwner.NONE, VoiceSessionArbiter.currentOwner())
    }

    // T16-06-A5: a throwing stopper cannot break arbitration — the
    // transfer still completes.
    @Test
    fun t16_06_a5_throwingStopperContained() {
        VoiceSessionArbiter.registerStopper(VoiceSessionOwner.BUBBLE) {
            throw RuntimeException("stopper blew up")
        }
        VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.BUBBLE)
        val previous = VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.IR_SERVICE)
        assertEquals(VoiceSessionOwner.BUBBLE, previous)
        assertEquals(VoiceSessionOwner.IR_SERVICE, VoiceSessionArbiter.currentOwner())
    }

    // T16-06-A6: unregistering the current owner clears ownership
    // (service-destroy path: no stale owner left holding the mic).
    @Test
    fun t16_06_a6_unregisterCurrentOwnerClears() {
        VoiceSessionArbiter.registerStopper(VoiceSessionOwner.BUBBLE) {}
        VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.BUBBLE)
        VoiceSessionArbiter.unregisterStopper(VoiceSessionOwner.BUBBLE)
        assertEquals(VoiceSessionOwner.NONE, VoiceSessionArbiter.currentOwner())
        // And a fresh request is a clean grant again:
        assertEquals(
            VoiceSessionOwner.NONE,
            VoiceSessionArbiter.requestOwnership(VoiceSessionOwner.DICTATION)
        )
        assertTrue(VoiceSessionArbiter.currentOwner() == VoiceSessionOwner.DICTATION)
    }
}
