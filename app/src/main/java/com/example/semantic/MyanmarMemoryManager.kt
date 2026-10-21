package com.example.semantic

import com.example.engine.CommandContextManager
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.MemoryItem

/**
 * Myanmar Memory Manager for MSIL
 * Bridges short-term turn memory and multi-turn conversational context with CommandContextManager.
 */
class MyanmarMemoryManager(
    private val commandContextManager: CommandContextManager = CommandContextManager.getInstance()
) {

    private val conversationContext = ConversationContext()

    fun getActiveContext(): ConversationContext {
        // Sync from CommandContextManager if needed
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

    fun recordInteraction(
        query: String,
        normalizedQuery: String,
        targetApp: String? = null,
        targetPackage: String? = null,
        contact: String? = null,
        phone: String? = null,
        artist: String? = null,
        song: String? = null
    ) {
        conversationContext.lastUserQuery = query
        conversationContext.lastNormalizedQuery = normalizedQuery
        conversationContext.turnCount++
        conversationContext.lastUpdatedTimestamp = System.currentTimeMillis()

        if (targetApp != null) {
            conversationContext.updateApp(targetApp, targetPackage)
            conversationContext.addMemory(
                MemoryItem(
                    key = "LAST_APP",
                    value = targetApp,
                    entityType = EntityType.APP_NAME,
                    metadata = mapOf("packageName" to (targetPackage ?: ""))
                )
            )
            // Sync to legacy manager
            commandContextManager.updateAppTarget(targetPackage ?: targetApp, targetApp)
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
    }

    fun reset() {
        conversationContext.reset()
        commandContextManager.resetContext()
    }
}
