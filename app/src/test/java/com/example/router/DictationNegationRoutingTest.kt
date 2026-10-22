package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE-013 regression tests for the dictation funnel (entry point C),
 * IDs T15-013-02*.
 *
 * A dictated segment containing a prohibitive negation ("မဖွင့်ပါနဲ့ …")
 * must NEVER be executed as a command: the dispatcher must report
 * [CommandRouteResult.NotHandled] so the dictation layer files the
 * segment as document text instead. Pre-fix, the static intent router
 * matched the affirmative keyword inside the negated sentence and the
 * command executed (Phase 1 audit §4.2 rows 2/5/8, column D).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictationNegationRoutingTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dispatcher: KyawGyiDispatcher

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        dispatcher = KyawGyiDispatcher(context)
        db.conversationDao().clearHistory()
    }

    @After
    fun tearDown() = runBlocking {
        db.conversationDao().clearHistory()
    }

    private suspend fun history() = db.conversationDao().getAllHistory().first()

    // T15-013-02a — audit row 8 (dictation column): "မဖွင့်ပါနဲ့ Facebook"
    // executed OPEN_FACEBOOK pre-fix. It must not be handled as a command.
    @Test
    fun t15_013_02a_negatedFacebookOpen_notHandledAndNotLogged() = runBlocking {
        val result = dispatcher.tryExecuteCommand("မဖွင့်ပါနဲ့ Facebook")
        assertEquals(
            "negated segment must not execute; it is filed as dictation text",
            CommandRouteResult.NotHandled,
            result
        )
        assertTrue("vetoed segment must not be logged as an executed command", history().isEmpty())
    }

    // T15-013-02b — audit row 5 variant: "ဓာတ်မီး မဖွင့်ပါနဲ့" fired
    // TURN_ON_FLASH pre-fix (ဓာတ်မီး + ဖွင့် both present).
    @Test
    fun t15_013_02b_negatedFlashlight_notHandled() = runBlocking {
        val result = dispatcher.tryExecuteCommand("ဓာတ်မီး မဖွင့်ပါနဲ့")
        assertEquals(CommandRouteResult.NotHandled, result)
        assertTrue(history().isEmpty())
    }

    // T15-013-02c — affirmative control: a genuine open command must
    // still execute through the same funnel (no over-blocking).
    @Test
    fun t15_013_02c_affirmativeFacebookOpen_stillHandled() = runBlocking {
        val result = dispatcher.tryExecuteCommand("Facebook ဖွင့်ပေးပါ")
        assertTrue("expected Handled, got $result", result is CommandRouteResult.Handled)
    }
}
