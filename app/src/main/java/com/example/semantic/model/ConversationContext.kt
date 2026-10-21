package com.example.semantic.model

/**
 * Conversation Context tracking multi-turn state, active targets, and conversational memory.
 */
data class ConversationContext(
    var activeAppPackage: String? = null,
    var activeAppNameMM: String? = null,
    var activeContact: String? = null,
    var activePhoneNumber: String? = null,
    var activeArtist: String? = null,
    var activeSongTitle: String? = null,
    var activeSetting: String? = null,
    var lastIntent: SemanticIntent = SemanticIntent.UNKNOWN,
    var lastActionPlan: ActionPlan? = null,
    var lastUserQuery: String? = null,
    var lastNormalizedQuery: String? = null,
    var lastEntities: List<SemanticEntity> = emptyList(),
    var memoryItems: MutableList<MemoryItem> = mutableListOf(),
    var turnCount: Int = 0,
    var lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    val activeContactName: String?
        get() = activeContact

    fun updateApp(appName: String, packageName: String? = null) {
        activeAppNameMM = appName
        if (packageName != null) {
            activeAppPackage = packageName
        }
        lastUpdatedTimestamp = System.currentTimeMillis()
    }

    fun updateContact(contact: String, phone: String? = null) {
        activeContact = contact
        if (phone != null) {
            activePhoneNumber = phone
        }
        lastUpdatedTimestamp = System.currentTimeMillis()
    }

    fun updateMedia(artist: String? = null, songTitle: String? = null) {
        if (!artist.isNullOrBlank()) activeArtist = artist
        if (!songTitle.isNullOrBlank()) activeSongTitle = songTitle
        lastUpdatedTimestamp = System.currentTimeMillis()
    }

    fun addMemory(item: MemoryItem) {
        memoryItems.removeAll { it.key == item.key }
        memoryItems.add(0, item)
        // Keep maximum 20 memory items
        if (memoryItems.size > 20) {
            memoryItems = memoryItems.take(20).toMutableList()
        }
        lastUpdatedTimestamp = System.currentTimeMillis()
    }

    fun findMemory(key: String): MemoryItem? {
        return memoryItems.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }

    fun findLastMemoryOfType(type: EntityType): MemoryItem? {
        return memoryItems.firstOrNull { it.entityType == type }
    }

    fun isExpired(maxAgeMs: Long = 5 * 60 * 1000L): Boolean {
        return (System.currentTimeMillis() - lastUpdatedTimestamp) > maxAgeMs
    }

    fun reset() {
        activeAppPackage = null
        activeAppNameMM = null
        activeContact = null
        activePhoneNumber = null
        activeArtist = null
        activeSongTitle = null
        activeSetting = null
        lastIntent = SemanticIntent.UNKNOWN
        lastActionPlan = null
        lastUserQuery = null
        lastNormalizedQuery = null
        lastEntities = emptyList()
        memoryItems.clear()
        turnCount = 0
        lastUpdatedTimestamp = System.currentTimeMillis()
    }
}
