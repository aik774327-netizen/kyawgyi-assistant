package com.example.pipeline.memory

import com.example.pipeline.contract.UnifiedActionResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Structured Memory Architecture for Myanmar Intelligent Voice Assistant
 * Implements bounded memory with TTL, categorized into 6 functional memory tiers:
 * A. Short-Term Conversation Memory
 * B. Context Memory (with TTL)
 * C. Session Memory
 * D. User Preference Memory
 * E. Action History
 * F. Error / Recovery Memory
 */
class StructuredMemoryStore {

    companion object {
        private const val DEFAULT_CONTEXT_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_CONVERSATION_TURNS = 20
        private const val MAX_ACTION_HISTORY = 50
        private const val MAX_ERROR_HISTORY = 30
    }

    // -------------------------------------------------------------
    // Memory Models
    // -------------------------------------------------------------
    data class ConversationTurn(
        val turnId: String,
        val userQuery: String,
        val normalizedQuery: String,
        val assistantResponse: String,
        val intent: String,
        val target: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class ContextItem<T>(
        val value: T,
        val timestamp: Long = System.currentTimeMillis(),
        val ttlMs: Long = DEFAULT_CONTEXT_TTL_MS
    ) {
        val isExpired: Boolean
            get() = (System.currentTimeMillis() - timestamp) > ttlMs
    }

    data class ContextMemory(
        var lastApp: ContextItem<String>? = null,
        var lastPackage: ContextItem<String>? = null,
        var lastArtist: ContextItem<String>? = null,
        var lastMedia: ContextItem<String>? = null,
        var lastContact: ContextItem<String>? = null,
        var lastMessageTarget: ContextItem<String>? = null,
        var lastCommand: ContextItem<String>? = null,
        var lastIntent: ContextItem<String>? = null,
        var lastAction: ContextItem<String>? = null
    ) {
        fun getValidLastApp(): String? = lastApp?.takeIf { !it.isExpired }?.value
        fun getValidLastPackage(): String? = lastPackage?.takeIf { !it.isExpired }?.value
        fun getValidLastArtist(): String? = lastArtist?.takeIf { !it.isExpired }?.value
        fun getValidLastMedia(): String? = lastMedia?.takeIf { !it.isExpired }?.value
        fun getValidLastContact(): String? = lastContact?.takeIf { !it.isExpired }?.value
        fun getValidLastMessageTarget(): String? = lastMessageTarget?.takeIf { !it.isExpired }?.value
        fun getValidLastCommand(): String? = lastCommand?.takeIf { !it.isExpired }?.value
        fun getValidLastIntent(): String? = lastIntent?.takeIf { !it.isExpired }?.value
        fun getValidLastAction(): String? = lastAction?.takeIf { !it.isExpired }?.value
    }

    data class ActiveSessionState(
        val sessionId: String,
        var pendingIntent: String? = null,
        var pendingTarget: String? = null,
        var missingSlot: String? = null,
        var awaitingConfirmation: Boolean = false,
        var confirmationPayload: Map<String, Any?> = emptyMap(),
        val startTime: Long = System.currentTimeMillis()
    )

    data class ErrorRecoveryRecord(
        val errorId: String,
        val failedAction: String,
        val target: String?,
        val reason: String,
        val retryCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    // -------------------------------------------------------------
    // In-Memory Storage Tiers
    // -------------------------------------------------------------
    // Tier A: Short-Term Conversation Memory
    private val conversationTurns = CopyOnWriteArrayList<ConversationTurn>()

    // Tier B: Context Memory
    private val contextMemory = ContextMemory()

    // Tier C: Session Memory
    private val activeSessions = ConcurrentHashMap<String, ActiveSessionState>()

    // Tier D: User Preference Memory
    private val userPreferences = ConcurrentHashMap<String, String>()

    // Tier E: Action History
    private val actionHistory = CopyOnWriteArrayList<UnifiedActionResult>()

    // Tier F: Error / Recovery Memory
    private val errorHistory = CopyOnWriteArrayList<ErrorRecoveryRecord>()

    // -------------------------------------------------------------
    // A. Conversation Memory Management
    // -------------------------------------------------------------
    fun recordTurn(turn: ConversationTurn) {
        conversationTurns.add(turn)
        if (conversationTurns.size > MAX_CONVERSATION_TURNS) {
            conversationTurns.removeAt(0)
        }
    }

    fun getRecentTurns(limit: Int = 5): List<ConversationTurn> {
        return conversationTurns.takeLast(limit)
    }

    fun getLastTurn(): ConversationTurn? = conversationTurns.lastOrNull()

    // -------------------------------------------------------------
    // B. Context Memory Management (With TTL)
    // -------------------------------------------------------------
    fun updateContext(
        app: String? = null,
        packageName: String? = null,
        artist: String? = null,
        media: String? = null,
        contact: String? = null,
        messageTarget: String? = null,
        command: String? = null,
        intent: String? = null,
        action: String? = null,
        ttlMs: Long = DEFAULT_CONTEXT_TTL_MS
    ) {
        val now = System.currentTimeMillis()
        app?.let { contextMemory.lastApp = ContextItem(it, now, ttlMs) }
        packageName?.let { contextMemory.lastPackage = ContextItem(it, now, ttlMs) }
        artist?.let { contextMemory.lastArtist = ContextItem(it, now, ttlMs) }
        media?.let { contextMemory.lastMedia = ContextItem(it, now, ttlMs) }
        contact?.let { contextMemory.lastContact = ContextItem(it, now, ttlMs) }
        messageTarget?.let { contextMemory.lastMessageTarget = ContextItem(it, now, ttlMs) }
        command?.let { contextMemory.lastCommand = ContextItem(it, now, ttlMs) }
        intent?.let { contextMemory.lastIntent = ContextItem(it, now, ttlMs) }
        action?.let { contextMemory.lastAction = ContextItem(it, now, ttlMs) }
    }

    fun getActiveContext(): ContextMemory = contextMemory

    // -------------------------------------------------------------
    // C. Session Memory Management
    // -------------------------------------------------------------
    fun getOrCreateSession(sessionId: String): ActiveSessionState {
        return activeSessions.computeIfAbsent(sessionId) {
            ActiveSessionState(sessionId = it)
        }
    }

    fun clearSession(sessionId: String) {
        activeSessions.remove(sessionId)
    }

    // -------------------------------------------------------------
    // D. User Preferences
    // -------------------------------------------------------------
    fun setPreference(key: String, value: String) {
        userPreferences[key] = value
    }

    fun getPreference(key: String, defaultValue: String = ""): String {
        return userPreferences[key] ?: defaultValue
    }

    // -------------------------------------------------------------
    // E. Action History
    // -------------------------------------------------------------
    fun recordAction(result: UnifiedActionResult) {
        actionHistory.add(result)
        if (actionHistory.size > MAX_ACTION_HISTORY) {
            actionHistory.removeAt(0)
        }
    }

    fun getLastAction(): UnifiedActionResult? = actionHistory.lastOrNull()

    fun getActionHistory(limit: Int = 10): List<UnifiedActionResult> {
        return actionHistory.takeLast(limit)
    }

    // -------------------------------------------------------------
    // F. Error / Recovery Memory
    // -------------------------------------------------------------
    fun recordError(failedAction: String, target: String?, reason: String) {
        val record = ErrorRecoveryRecord(
            errorId = "err_${System.currentTimeMillis()}",
            failedAction = failedAction,
            target = target,
            reason = reason
        )
        errorHistory.add(record)
        if (errorHistory.size > MAX_ERROR_HISTORY) {
            errorHistory.removeAt(0)
        }
    }

    fun getLastFailure(): ErrorRecoveryRecord? = errorHistory.lastOrNull()

    fun clearAll() {
        conversationTurns.clear()
        activeSessions.clear()
        actionHistory.clear()
        errorHistory.clear()
    }
}
