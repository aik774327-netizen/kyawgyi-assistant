package com.example.router

import android.content.Context
import com.example.actions.KyawGyiActionExecutor
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of asking the command pipeline to handle one spoken segment.
 *
 * [Handled] means the pipeline really matched the segment — either a user
 * custom shortcut from Room or the static [KyawGyiIntentRouter] — and the
 * executor ran; [response] is the executor's own user-facing message.
 * [NotHandled] means neither source matched, so the caller keeps the text
 * (e.g. dictation files it as document text). This is NOT an error state.
 */
sealed interface CommandRouteResult {
    data class Handled(val response: String) : CommandRouteResult
    data object NotHandled : CommandRouteResult
}

class KyawGyiDispatcher(private val context: Context) {

    private val db = KyawGyiDatabase.getDatabase(context)
    private val executor = KyawGyiActionExecutor(context)
    private val staticRouter = KyawGyiIntentRouter()

    /** Internal outcome of the shared command-execution core. */
    private data class CoreOutcome(
        val handled: Boolean,
        val response: String,
        val executionMode: String
    )

    /**
     * Shared core behind [processVoiceCommand] and [tryExecuteCommand]:
     * user custom shortcuts from Room first, then the static intent
     * router. Behaviour (matching order, response strings, execution
     * modes) is exactly what [processVoiceCommand] has always done.
     */
    private suspend fun executeCommandCore(spokenText: String): CoreOutcome {
        val cleanText = spokenText.trim().lowercase()
        var executionMode = "OFFLINE"

        // 1. Check User Custom Shortcuts in Room Database First
        val customShortcut = db.shortcutDao().findShortcutByPhrase(cleanText)

        if (customShortcut != null) {
            val responseText = when (customShortcut.actionType) {
                "OPEN_APP" -> executor.openApplication(customShortcut.payload, customShortcut.triggerPhrase).userMessage
                "TOGGLE_FLASH" -> executor.setFlashlight(customShortcut.payload.toBoolean()).userMessage
                "TOGGLE_WIFI" -> executor.setWifi(customShortcut.payload.toBoolean()).userMessage
                else -> "သတ်မှတ်ထားသော Action အမျိုးအစား မမှန်ကန်ပါခင်ဗျာ"
            }
            return CoreOutcome(handled = true, response = responseText, executionMode = executionMode)
        }

        // 2. Fallback to Static Intent Router
        val (isHandled, response) = staticRouter.parseAndExecute(cleanText, executor)
        return if (isHandled) {
            CoreOutcome(handled = true, response = response, executionMode = executionMode)
        } else {
            // 3. Optional: Online Gemini AI Fallback (If Network Available)
            executionMode = "ONLINE_GEMINI"
            CoreOutcome(
                handled = false,
                response = "ဤစကားလုံးအတွက် သီးသန့် အမိန့်မရှိသေးပါခင်ဗျာ",
                executionMode = executionMode
            )
        }
    }

    private suspend fun logHistory(spokenText: String, outcome: CoreOutcome) {
        // Save Execution History into Room Database. The query stored is
        // the ORIGINAL spoken text, exactly as processVoiceCommand has
        // always logged it (not the trimmed/lowercased form).
        val historyEntry = ConversationEntity(
            userQuery = spokenText,
            assistantResponse = outcome.response,
            executionMode = outcome.executionMode
        )
        db.conversationDao().insertConversation(historyEntry)
    }

    suspend fun processVoiceCommand(spokenText: String): String = withContext(Dispatchers.IO) {
        val outcome = executeCommandCore(spokenText)
        logHistory(spokenText, outcome)
        return@withContext outcome.response
    }

    /**
     * Try to execute [spokenText] as a voice command through the exact
     * same pipeline as [processVoiceCommand] (custom shortcuts + static
     * router), reporting whether it was handled.
     *
     * History logging differs deliberately from [processVoiceCommand]:
     * an entry is written ONLY when the segment was handled. Callers like
     * dictation route every dictated sentence through here first; logging
     * each ordinary sentence as a failed command would pollute the
     * conversation history with non-commands. [processVoiceCommand]
     * keeps its legacy log-every-call behaviour unchanged.
     */
    suspend fun tryExecuteCommand(spokenText: String): CommandRouteResult =
        withContext(Dispatchers.IO) {
            val outcome = executeCommandCore(spokenText)
            if (outcome.handled) {
                logHistory(spokenText, outcome)
                CommandRouteResult.Handled(outcome.response)
            } else {
                CommandRouteResult.NotHandled
            }
        }

    fun dispatchAndExecute(spokenText: String): Pair<Boolean, String> {
        val (isHandled, response) = staticRouter.parseAndExecute(spokenText, executor)
        return isHandled to response
    }
}
