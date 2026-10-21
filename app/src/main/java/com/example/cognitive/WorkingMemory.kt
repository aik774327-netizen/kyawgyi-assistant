package com.example.cognitive

import com.example.engine.CommandContextManager
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import java.util.concurrent.ConcurrentHashMap

/**
 * WorkingMemory for short-lived conversational state and turn tracking.
 * Integrates directly with CommandContextManager to maintain full bidirectional consistency.
 */
class WorkingMemory(
    private val commandContextManager: CommandContextManager = CommandContextManager.getInstance(),
    private val defaultTtlMs: Long = 5 * 60 * 1000L // 5 minutes
) {

    private val dynamicSlots = ConcurrentHashMap<String, MemoryRecord>()

    var lastApp: String? = null
    var lastPackage: String? = null
    var lastArtist: String? = null
    var lastSong: String? = null
    var lastMedia: String? = null
    var lastContact: String? = null
    var lastPhoneNumber: String? = null
    var lastIntent: SemanticIntent = SemanticIntent.UNKNOWN
    var lastAction: String? = null
    var lastEntities: List<SemanticEntity> = emptyList()
    var lastTopic: String? = null
    var pendingGoal: Goal? = null
    var pendingQuestion: String? = null
    var conversationTurn: Int = 0
    var lastUpdatedTimestamp: Long = System.currentTimeMillis()

    init {
        syncFromCommandContext()
    }

    @Synchronized
    fun syncFromCommandContext() {
        val legacy = commandContextManager.getContext()
        if (legacy.activeAppNameMM != null) lastApp = legacy.activeAppNameMM
        if (legacy.activeAppPackage != null) lastPackage = legacy.activeAppPackage
        if (legacy.activeArtist != null) lastArtist = legacy.activeArtist
        if (legacy.activeMediaTitle != null) {
            lastSong = legacy.activeMediaTitle
            lastMedia = legacy.activeMediaTitle
        }
    }

    @Synchronized
    fun updateState(
        app: String? = null,
        packageName: String? = null,
        artist: String? = null,
        song: String? = null,
        media: String? = null,
        contact: String? = null,
        phone: String? = null,
        intent: SemanticIntent? = null,
        action: String? = null,
        entities: List<SemanticEntity>? = null,
        topic: String? = null,
        goal: Goal? = null,
        question: String? = null
    ) {
        checkTtlExpiration()

        if (app != null) {
            lastApp = app
            if (packageName != null) lastPackage = packageName
            commandContextManager.updateAppTarget(packageName ?: app, app)
            setSlot("LAST_APP", app, EntityType.APP_NAME)
        }
        if (packageName != null && app == null) {
            lastPackage = packageName
            setSlot("LAST_PACKAGE", packageName, EntityType.APP_PACKAGE)
        }
        if (artist != null || song != null) {
            if (artist != null) {
                lastArtist = artist
                setSlot("LAST_ARTIST", artist, EntityType.ARTIST_NAME)
            }
            if (song != null) {
                lastSong = song
                lastMedia = song
                setSlot("LAST_SONG", song, EntityType.SONG_TITLE)
                setSlot("LAST_MEDIA", song, EntityType.SONG_TITLE)
            }
            commandContextManager.updateMediaEntities(artist, song)
        }
        if (media != null && song == null) {
            lastMedia = media
            setSlot("LAST_MEDIA", media, EntityType.SONG_TITLE)
        }
        if (contact != null) {
            lastContact = contact
            setSlot("LAST_CONTACT", contact, EntityType.CONTACT_NAME)
        }
        if (phone != null) {
            lastPhoneNumber = phone
            setSlot("LAST_PHONE", phone, EntityType.PHONE_NUMBER)
        }
        if (intent != null) {
            lastIntent = intent
            commandContextManager.updateIntent(intent.name)
        }
        if (action != null) lastAction = action
        if (entities != null) lastEntities = entities
        if (topic != null) lastTopic = topic
        if (goal != null) pendingGoal = goal
        if (question != null) pendingQuestion = question

        conversationTurn++
        lastUpdatedTimestamp = System.currentTimeMillis()
    }

    @Synchronized
    fun setSlot(key: String, value: String, entityType: EntityType? = null, ttlMs: Long = defaultTtlMs) {
        val record = MemoryRecord(
            key = key,
            value = value,
            type = MemoryType.WORKING,
            entityType = entityType,
            ttlMs = ttlMs,
            updatedAt = System.currentTimeMillis()
        )
        dynamicSlots[key] = record
    }

    @Synchronized
    fun getSlot(key: String): String? {
        checkTtlExpiration()
        val record = dynamicSlots[key] ?: return null
        if (record.isExpired()) {
            dynamicSlots.remove(key)
            return null
        }
        return record.value
    }

    @Synchronized
    fun checkTtlExpiration() {
        val now = System.currentTimeMillis()
        if (lastUpdatedTimestamp > 0 && (now - lastUpdatedTimestamp) > defaultTtlMs) {
            reset(keepTopic = false)
        }
        // Remove individual expired dynamic slots
        val it = dynamicSlots.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value.isExpired()) {
                it.remove()
            }
        }
    }

    @Synchronized
    fun reset(keepTopic: Boolean = false) {
        lastApp = null
        lastPackage = null
        lastArtist = null
        lastSong = null
        lastMedia = null
        lastContact = null
        lastPhoneNumber = null
        lastIntent = SemanticIntent.UNKNOWN
        lastAction = null
        lastEntities = emptyList()
        if (!keepTopic) lastTopic = null
        pendingGoal = null
        pendingQuestion = null
        dynamicSlots.clear()
        conversationTurn = 0
        lastUpdatedTimestamp = System.currentTimeMillis()
        commandContextManager.resetContext()
    }
}
