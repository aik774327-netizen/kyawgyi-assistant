package com.example.router

import android.content.Context
import com.example.actions.KyawGyiActionExecutor
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KyawGyiDispatcher(private val context: Context) {

    private val db = KyawGyiDatabase.getDatabase(context)
    private val executor = KyawGyiActionExecutor(context)
    private val staticRouter = KyawGyiIntentRouter()

    suspend fun processVoiceCommand(spokenText: String): String = withContext(Dispatchers.IO) {
        val cleanText = spokenText.trim().lowercase()
        var responseText: String
        var executionMode = "OFFLINE"

        // 1. Check User Custom Shortcuts in Room Database First
        val customShortcut = db.shortcutDao().findShortcutByPhrase(cleanText)

        if (customShortcut != null) {
            responseText = when (customShortcut.actionType) {
                "OPEN_APP" -> executor.openApplication(customShortcut.payload, customShortcut.triggerPhrase).userMessage
                "TOGGLE_FLASH" -> executor.setFlashlight(customShortcut.payload.toBoolean()).userMessage
                "TOGGLE_WIFI" -> executor.setWifi(customShortcut.payload.toBoolean()).userMessage
                else -> "သတ်မှတ်ထားသော Action အမျိုးအစား မမှန်ကန်ပါခင်ဗျာ"
            }
        } else {
            // 2. Fallback to Static Intent Router
            val (isHandled, response) = staticRouter.parseAndExecute(cleanText, executor)
            if (isHandled) {
                responseText = response
            } else {
                // 3. Optional: Online Gemini AI Fallback (If Network Available)
                executionMode = "ONLINE_GEMINI"
                responseText = "ဤစကားလုံးအတွက် သီးသန့် အမိန့်မရှိသေးပါခင်ဗျာ"
            }
        }

        // 4. Save Execution History into Room Database
        val historyEntry = ConversationEntity(
            userQuery = spokenText,
            assistantResponse = responseText,
            executionMode = executionMode
        )
        db.conversationDao().insertConversation(historyEntry)

        return@withContext responseText
    }

    fun dispatchAndExecute(spokenText: String): Pair<Boolean, String> {
        val (isHandled, response) = staticRouter.parseAndExecute(spokenText, executor)
        return isHandled to response
    }
}
