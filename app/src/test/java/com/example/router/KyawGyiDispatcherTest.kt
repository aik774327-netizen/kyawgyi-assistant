package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.local.entity.ShortcutEntity
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
 * Robolectric tests for the [KyawGyiDispatcher] refactor that powers
 * dictation command routing:
 *  - [KyawGyiDispatcher.processVoiceCommand] keeps its legacy behaviour
 *    byte-for-byte (same response strings, history logged for handled
 *    AND unhandled inputs, original untrimmed query stored).
 *  - [KyawGyiDispatcher.tryExecuteCommand] covers BOTH required sources
 *    (Room custom shortcuts and the static intent router) and logs
 *    history ONLY when the segment was handled, so ordinary dictated
 *    sentences never pollute the conversation history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KyawGyiDispatcherTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var dispatcher: KyawGyiDispatcher
    private val insertedShortcuts = mutableListOf<ShortcutEntity>()

    /** A plain dictated sentence: no command keywords of any intent. */
    private val plainSentence = "မနက်ဖြန် နေ့လယ်မှာ အစည်းအဝေး ရှိပါတယ်"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(context)
        dispatcher = KyawGyiDispatcher(context)
        db.conversationDao().clearHistory()
    }

    @After
    fun tearDown() = runBlocking {
        insertedShortcuts.forEach { db.shortcutDao().deleteShortcut(it) }
        insertedShortcuts.clear()
        db.conversationDao().clearHistory()
    }

    private suspend fun history() = db.conversationDao().getAllHistory().first()

    private suspend fun insertShortcut(trigger: String, actionType: String, payload: String) {
        val shortcut = ShortcutEntity(triggerPhrase = trigger, actionType = actionType, payload = payload)
        db.shortcutDao().insertShortcut(shortcut)
        insertedShortcuts.add(shortcut)
    }

    // ---------------------------------------------------------------
    // Unhandled path (ordinary dictation text)
    // ---------------------------------------------------------------

    @Test
    fun tryExecuteCommand_plainSentence_notHandledAndNotLogged() = runBlocking {
        val result = dispatcher.tryExecuteCommand(plainSentence)
        assertEquals(CommandRouteResult.NotHandled, result)
        assertTrue(
            "unhandled dictation text must not pollute conversation history",
            history().isEmpty()
        )
    }

    @Test
    fun processVoiceCommand_plainSentence_legacyFallbackStringAndLogged() = runBlocking {
        val response = dispatcher.processVoiceCommand(plainSentence)
        assertEquals("ဤစကားလုံးအတွက် သီးသန့် အမိန့်မရှိသေးပါခင်ဗျာ", response)
        val entries = history()
        assertEquals(1, entries.size)
        assertEquals(plainSentence, entries[0].userQuery)
        assertEquals("ဤစကားလုံးအတွက် သီးသန့် အမိန့်မရှိသေးပါခင်ဗျာ", entries[0].assistantResponse)
        assertEquals("ONLINE_GEMINI", entries[0].executionMode)
    }

    // ---------------------------------------------------------------
    // Custom shortcut path (Room) — required coverage (a)
    // ---------------------------------------------------------------

    @Test
    fun tryExecuteCommand_customShortcutOpenApp_handledAndLogged() = runBlocking {
        insertShortcut(
            trigger = "test open app shortcut",
            actionType = "OPEN_APP",
            payload = "com.facebook.katana"
        )
        // The dispatcher matches on trim().lowercase() of the spoken text.
        val result = dispatcher.tryExecuteCommand("  Test Open App Shortcut  ")
        assertTrue("expected Handled, got $result", result is CommandRouteResult.Handled)
        assertTrue((result as CommandRouteResult.Handled).response.isNotBlank())
        val entries = history()
        assertEquals(1, entries.size)
        assertEquals("OFFLINE", entries[0].executionMode)
    }

    @Test
    fun tryExecuteCommand_customShortcutUnknownAction_handledWithLegacyMessage() = runBlocking {
        insertShortcut(
            trigger = "test bogus shortcut",
            actionType = "NO_SUCH_ACTION",
            payload = "x"
        )
        val result = dispatcher.tryExecuteCommand("test bogus shortcut")
        assertEquals(
            CommandRouteResult.Handled("သတ်မှတ်ထားသော Action အမျိုးအစား မမှန်ကန်ပါခင်ဗျာ"),
            result
        )
        // processVoiceCommand agrees byte-for-byte on the same input.
        assertEquals(
            "သတ်မှတ်ထားသော Action အမျိုးအစား မမှန်ကန်ပါခင်ဗျာ",
            dispatcher.processVoiceCommand("test bogus shortcut")
        )
    }

    // ---------------------------------------------------------------
    // Static router path — required coverage (b): the user's example
    // ---------------------------------------------------------------

    @Test
    fun tryExecuteCommand_facebookOpenViaStaticRouter_handledAndLogged() = runBlocking {
        val result = dispatcher.tryExecuteCommand("Facebook ဖွင့်ပေးပါ")
        assertTrue("expected Handled, got $result", result is CommandRouteResult.Handled)
        assertTrue((result as CommandRouteResult.Handled).response.isNotBlank())
        val entries = history()
        assertEquals(1, entries.size)
        assertEquals("Facebook ဖွင့်ပေးပါ", entries[0].userQuery)
        assertEquals("OFFLINE", entries[0].executionMode)
    }
}
