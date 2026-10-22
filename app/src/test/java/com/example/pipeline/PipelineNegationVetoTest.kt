package com.example.pipeline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.pipeline.contract.UnifiedCommandRequest
import com.example.pipeline.memory.StructuredMemoryStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-013 regression tests for the chat funnel (entry point B,
 * UnifiedIntelligencePipeline), ID T15-013-04.
 *
 * Pre-fix (Phase 1 audit §4.2 row 8, column C): "YouTube မဖွင့်ပါနဲ့"
 * resolved to OPEN_APP and executed — the semantic resolver matched
 * the app entity plus the bare "ဖွင့်" OPEN synonym and never saw the
 * negation. A prohibitive negation must return a non-executing result
 * carrying the honest no-action response.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PipelineNegationVetoTest {

    private lateinit var context: Context
    private lateinit var pipeline: UnifiedIntelligencePipeline

    /** The honest no-action response a vetoed command must produce. */
    private val vetoResponse = "နားလည်ပါပြီခင်ဗျာ။ အဲဒီ လုပ်ဆောင်ချက်ကို မလုပ်ဆောင်ပါဘူးခင်ဗျာ။"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pipeline = UnifiedIntelligencePipeline(context, StructuredMemoryStore())
    }

    private fun request(id: String, transcript: String) = UnifiedCommandRequest(
        requestId = id,
        sessionId = "sess_t13",
        transcript = transcript
    )

    // T15-013-04a — negated open must not resolve or execute.
    @Test
    fun t15_013_04a_negatedYouTubeOpen_notExecuted() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t13_1", "YouTube မဖွင့်ပါနဲ့"),
            speakResponse = false
        )

        assertEquals("UNKNOWN", response.intent)
        assertNull("vetoed command must not produce an execution result", response.executionResult)
        assertFalse(response.verificationResult)
        assertEquals(vetoResponse, response.responseMyanmar)
    }

    // T15-013-04b — affirmative control: a genuine open command is not
    // vetoed and still resolves to OPEN_APP on this path.
    @Test
    fun t15_013_04b_affirmativeFacebookOpen_notVetoed() = runBlocking {
        val response = pipeline.processCommand(
            request("req_t13_2", "ကျော်ကြီး Facebook ဖွင့်ပါ"),
            speakResponse = false
        )

        assertEquals("OPEN_APP", response.intent)
        assertNotEquals(vetoResponse, response.responseMyanmar)
    }
}
