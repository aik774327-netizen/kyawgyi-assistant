package com.example.semantic.memory

import android.content.Context
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.engine.CommandContextManager
import com.example.semantic.model.ActionPlan
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.MemoryItem
import com.example.semantic.model.SemanticIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Data structure representing a single conversational interaction turn in short-term memory.
 */
data class InteractionTurn(
    val turnId: Long = System.currentTimeMillis(),
    val userQuery: String,
    val normalizedQuery: String,
    val assistantResponse: String,
    val intent: SemanticIntent = SemanticIntent.UNKNOWN,
    val target: String? = null,
    val executionMode: String = "OFFLINE",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Intelligent Multi-Tier Memory Manager for Myanmar Voice Assistant.
 * Manages Short-Term (in-memory turns), Contextual (TTL-based target resolution),
 * and Persistent storage (Room SQLite Database).
 */
class MemoryManager(
    private val context: Context? = null,
    private val maxShortTermCapacity: Int = 30,
    private val contextTtlMs: Long = 5 * 60 * 1000L, // 5 minutes TTL
    private val commandContextManager: CommandContextManager = CommandContextManager.getInstance(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // 1. Short-Term In-Memory Turn Storage
    private val shortTermMemory = ConcurrentLinkedDeque<InteractionTurn>()

    // 2. Contextual Memory (TTL tracking for last app, last contact, last media, etc.)
    private val conversationContext = ConversationContext()

    // 3. Room Database instance (lazy initialized when Android Context is present)
    private val database: KyawGyiDatabase? by lazy {
        context?.let { KyawGyiDatabase.getDatabase(it) }
    }

    /**
     * Get the active conversational context. Refreshes/resets if TTL has expired.
     */
    fun getActiveContext(): ConversationContext {
        if (conversationContext.isExpired(contextTtlMs)) {
            conversationContext.reset()
        }

        // Bridge with legacy engine context if present
        val legacyCtx = commandContextManager.getContext()
        if (legacyCtx.activeAppNameMM != null && conversationContext.activeAppNameMM == null) {
            conversationContext.activeAppNameMM = legacyCtx.activeAppNameMM
            conversationContext.activeAppPackage = legacyCtx.activeAppPackage
        }
        if (legacyCtx.activeArtist != null && conversationContext.activeArtist == null) {
            conversationContext.activeArtist = legacyCtx.activeArtist
            conversationContext.activeSongTitle = legacyCtx.activeMediaTitle
        }

        return conversationContext
    }

    /**
     * Record a conversational turn across Short-Term, Contextual, and Room Database storage.
     */
    fun recordTurn(
        rawQuery: String,
        normalizedQuery: String,
        assistantResponse: String,
        intent: SemanticIntent = SemanticIntent.UNKNOWN,
        target: String? = null,
        executionMode: String = "OFFLINE",
        targetPackage: String? = null,
        contact: String? = null,
        phone: String? = null,
        artist: String? = null,
        song: String? = null
    ) {
        val timestamp = System.currentTimeMillis()

        // 1. Update Short-Term In-Memory Deque
        val turn = InteractionTurn(
            turnId = timestamp,
            userQuery = rawQuery,
            normalizedQuery = normalizedQuery,
            assistantResponse = assistantResponse,
            intent = intent,
            target = target,
            executionMode = executionMode,
            timestamp = timestamp
        )

        shortTermMemory.addFirst(turn)
        while (shortTermMemory.size > maxShortTermCapacity) {
            shortTermMemory.removeLast()
        }

        // 2. Update Contextual Memory
        conversationContext.lastUserQuery = rawQuery
        conversationContext.lastNormalizedQuery = normalizedQuery
        conversationContext.lastIntent = intent
        conversationContext.turnCount++
        conversationContext.lastUpdatedTimestamp = timestamp

        if (target != null || targetPackage != null) {
            val appName = target ?: targetPackage ?: ""
            conversationContext.updateApp(appName, targetPackage)
            conversationContext.addMemory(
                MemoryItem(
                    key = "LAST_APP",
                    value = appName,
                    entityType = EntityType.APP_NAME,
                    metadata = mapOf("package" to (targetPackage ?: ""))
                )
            )
            commandContextManager.updateAppTarget(targetPackage ?: appName, appName)
        }

        if (contact != null) {
            conversationContext.updateContact(contact, phone)
            conversationContext.addMemory(
                MemoryItem(
                    key = "LAST_CONTACT",
                    value = contact,
                    entityType = EntityType.CONTACT_NAME,
                    metadata = mapOf("phone" to (phone ?: ""))
                )
            )
        }

        if (artist != null || song != null) {
            conversationContext.updateMedia(artist, song)
            if (song != null) {
                conversationContext.addMemory(
                    MemoryItem(
                        key = "LAST_SONG",
                        value = song,
                        entityType = EntityType.SONG_TITLE
                    )
                )
            }
            commandContextManager.updateMediaEntities(artist, song)
        }

        // 3. Persist to Room Database Asynchronously
        database?.let { db ->
            coroutineScope.launch {
                try {
                    db.conversationDao().insertConversation(
                        ConversationEntity(
                            userQuery = rawQuery,
                            assistantResponse = assistantResponse,
                            executionMode = executionMode,
                            timestamp = timestamp
                        )
                    )
                } catch (ignored: Throwable) {
                    // Safe guard against database IO failure
                }
            }
        }
    }

    /**
     * Retrieve all in-memory short-term turns (most recent first).
     */
    fun getShortTermHistory(): List<InteractionTurn> {
        return shortTermMemory.toList()
    }

    /**
     * Retrieve last used application name or package.
     */
    fun getLastUsedApp(): String? {
        val ctx = getActiveContext()
        return ctx.activeAppNameMM ?: ctx.activeAppPackage
    }

    /**
     * Retrieve last active contact name.
     */
    fun getLastContact(): String? {
        val ctx = getActiveContext()
        return ctx.activeContact
    }

    /**
     * Retrieve last classified intent.
     */
    fun getLastIntent(): SemanticIntent {
        val ctx = getActiveContext()
        return ctx.lastIntent
    }

    /**
     * Update active app in contextual memory.
     */
    fun updateActiveApp(appName: String, packageName: String? = null) {
        conversationContext.updateApp(appName, packageName)
        commandContextManager.updateAppTarget(packageName ?: appName, appName)
    }

    /**
     * Update active contact in contextual memory.
     */
    fun updateActiveContact(contactName: String, phone: String? = null) {
        conversationContext.updateContact(contactName, phone)
    }

    /**
     * Update active media in contextual memory.
     */
    fun updateActiveMedia(artist: String?, songTitle: String?) {
        conversationContext.updateMedia(artist, songTitle)
        commandContextManager.updateMediaEntities(artist, songTitle)
    }

    /**
     * Retrieve Flow of persisted history from Room SQLite database.
     */
    fun getPersistentHistory(): Flow<List<ConversationEntity>> {
        return database?.conversationDao()?.getAllHistory() ?: emptyFlow()
    }

    /**
     * Clear in-memory short term and contextual memory.
     */
    fun clearVolatileMemory() {
        shortTermMemory.clear()
        conversationContext.reset()
        commandContextManager.resetContext()
    }

    /**
     * Clear all persistent Room records and in-memory caches.
     */
    fun clearAll(onCleared: (() -> Unit)? = null) {
        clearVolatileMemory()
        database?.let { db ->
            coroutineScope.launch {
                try {
                    db.conversationDao().clearHistory()
                } finally {
                    onCleared?.invoke()
                }
            }
        }
    }
}
