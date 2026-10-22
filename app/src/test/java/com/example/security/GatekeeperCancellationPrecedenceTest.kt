package com.example.security

import com.example.actions.CommandResult
import com.kyawgyi.assistant.state.AssistantStateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * ISSUE-015 proof tests (Phase 1.5 plan §2.2), IDs T15-015-*.
 *
 * Question under proof: can a confirmation-worthy action execute on the
 * hybrid path WITHOUT the user actually confirming? The two
 * `bypassConfirmation = true` call sites in HybridPipelineController are
 * the ActionPermissionGatekeeper's `onConfirmed` lambdas, so execution
 * requires the gatekeeper's deferred to complete with `true`, which only
 * [ActionPermissionGatekeeper.submitVoiceInput] can do.
 *
 * Defect proven by T15-015-01a..01d (pre-fix): submitVoiceInput evaluates
 * AFFIRMATIVE keywords BEFORE cancellation keywords, both with substring
 * (`contains`) matching. Every Burmese prohibitive cancellation of the
 * form "မ<verb>ပါနဲ့" CONTAINS the affirmative keyword "<verb>ပါ"
 * ("ခေါ်ပါ" ⊂ "မခေါ်ပါနဲ့", "ပို့ပါ" ⊂ "မပို့ပါနဲ့", "လုပ်ပါ" ⊂ "မလုပ်ပါနဲ့"),
 * so an explicit cancellation resolves the gate as CONFIRMED and the
 * staged call/SMS executes — the same substring-precedence defect class
 * ISSUE-029 fixed in CallSmsConfirmationCoordinator, present here in the
 * gatekeeper. Cancellation must take precedence (the ISSUE-029 rule).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatekeeperCancellationPrecedenceTest {

    private lateinit var gatekeeper: ActionPermissionGatekeeper
    private val executionCount = AtomicInteger(0)

    @Before
    fun setup() {
        AssistantStateStore.getInstance().resetToIdle()
        gatekeeper = ActionPermissionGatekeeper(AssistantStateStore.getInstance())
        executionCount.set(0)
    }

    private fun countingOnConfirmed(actionType: String): suspend () -> CommandResult = {
        executionCount.incrementAndGet()
        CommandResult(
            isSuccess = true,
            action = actionType,
            target = "target",
            userMessage = "EXECUTED_MARKER"
        )
    }

    /**
 * Stages a gate for [actionType], waits until it is pending, submits
 * [reply], and returns the gate result.
 */
    private fun runGate(actionType: String, reply: String): CommandResult = runBlocking {
        val deferred = async {
            gatekeeper.executeWithSafetyGate(
                actionType = actionType,
                target = "မောင်မောင်",
                promptMM = "အတည်ပြုပါသလား ခင်ဗျာ။",
                onConfirmed = countingOnConfirmed(actionType)
            )
        }
        delay(150)
        assertTrue("gate must be awaiting confirmation", gatekeeper.isAwaitingConfirmation())
        val submitted = gatekeeper.submitVoiceInput(reply)
        assertTrue("reply must resolve the gate: $reply", submitted)
        deferred.await()
    }

    // T15-015-01a — pure cancellation of a staged CALL must NOT execute.
    // Pre-fix FAIL: "မခေါ်ပါနဲ့" contains affirmative "ခေါ်ပါ", checked first.
    @Test
    fun t15_015_01a_stagedCall_pureCancellation_doesNotExecute() {
        val result = runGate("CALL", "မခေါ်ပါနဲ့")
        assertEquals("cancellation must not execute the staged call", 0, executionCount.get())
        assertFalse(result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
    }

    // T15-015-01b — pure cancellation of a staged SMS must NOT execute.
    // Pre-fix FAIL: "မပို့ပါနဲ့" contains affirmative "ပို့ပါ".
    @Test
    fun t15_015_01b_stagedSms_pureCancellation_doesNotExecute() {
        val result = runGate("SMS", "မပို့ပါနဲ့")
        assertEquals("cancellation must not execute the staged SMS", 0, executionCount.get())
        assertFalse(result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
    }

    // T15-015-01c — mixed reply ("yes, but don't call") must NOT execute:
    // cancellation takes precedence over the affirmative substring,
    // mirroring the ISSUE-029 coordinator semantics.
    // Pre-fix FAIL: affirmative "ဟုတ်ကဲ့" is matched first.
    @Test
    fun t15_015_01c_stagedCall_mixedReply_doesNotExecute() {
        val result = runGate("CALL", "ဟုတ်ကဲ့ မခေါ်ပါနဲ့")
        assertEquals("mixed reply must not execute the staged call", 0, executionCount.get())
        assertFalse(result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
    }

    // T15-015-01d — generic prohibitive cancellation must NOT execute.
    // Pre-fix FAIL: "မလုပ်ပါနဲ့" contains affirmative "လုပ်ပါ".
    @Test
    fun t15_015_01d_stagedCall_genericProhibitive_doesNotExecute() {
        val result = runGate("CALL", "မလုပ်ပါနဲ့")
        assertEquals("prohibitive cancellation must not execute", 0, executionCount.get())
        assertFalse(result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
    }

    // T15-015-01e — control: the bare affirmative verb form "ခေါ်ပါ"
    // still confirms and executes exactly once (no over-blocking from
    // cancellation precedence). Must PASS before and after the fix.
    @Test
    fun t15_015_01e_stagedCall_bareAffirmative_executesOnce() {
        val result = runGate("CALL", "ခေါ်ပါ")
        assertEquals("real confirmation must execute exactly once", 1, executionCount.get())
        assertTrue(result.isSuccess)
        assertEquals("EXECUTED_MARKER", result.userMessage)
    }

    // T15-015-01f — control: the short cancellation "မလုပ်နဲ့" (no
    // affirmative substring) already cancelled correctly before the fix;
    // pinned so the precedence change cannot regress it.
    @Test
    fun t15_015_01f_stagedCall_shortCancellation_doesNotExecute() {
        val result = runGate("CALL", "မလုပ်နဲ့")
        assertEquals(0, executionCount.get())
        assertFalse(result.isSuccess)
        assertEquals("USER_REJECTED", result.errorCode)
    }

    // T15-015-02 — architecture pin: the literal `bypassConfirmation = true`
    // may occur ONLY at the two gatekeeper onConfirmed lambda sites in
    // HybridPipelineController. Any future third site fails this test.
    @Test
    fun t15_015_02_bypassTrue_onlyAtGatekeeperLambdaSites() {
        val mainRoot = locateMainSourceRoot()
        val ktFiles = mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "source scan must cover the main tree (found ${ktFiles.size} files)",
            ktFiles.size > 100
        )
        val hits = mutableListOf<String>()
        for (file in ktFiles) {
            val text = file.readText()
            var idx = text.indexOf("bypassConfirmation = true")
            while (idx >= 0) {
                hits.add(file.name)
                idx = text.indexOf("bypassConfirmation = true", idx + 1)
            }
        }
        assertEquals(
            "bypassConfirmation = true must exist at exactly the 2 HybridPipelineController gate-lambda sites, found: $hits",
            listOf("HybridPipelineController.kt", "HybridPipelineController.kt"),
            hits.sorted()
        )
    }

    private fun locateMainSourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val candidate = File(dir, "app/src/main/java")
            if (candidate.isDirectory) return candidate
            val moduleLocal = File(dir, "src/main/java")
            if (moduleLocal.isDirectory) return moduleLocal
            dir = dir?.parentFile
        }
        fail("could not locate app/src/main/java from ${System.getProperty("user.dir") ?: "?"}")
        throw AssertionError("unreachable")
    }
}
