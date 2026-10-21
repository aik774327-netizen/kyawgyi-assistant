package com.example.data

import android.content.Context
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.LearnedAliasEntity
import com.example.engine.BurmesePhoneticNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * LocalMemoryStore manages user-taught vocabulary, personalized aliases,
 * and custom voice shortcuts with in-memory caching and Room persistence.
 */
class LocalMemoryStore private constructor(context: Context) {

    private val database: KyawGyiDatabase? by lazy {
        runCatching { KyawGyiDatabase.getDatabase(context) }.getOrNull()
    }
    private val aliasDao get() = database?.learnedAliasDao()
    private val memoryCache = ConcurrentHashMap<String, LearnedAliasEntity>()

    @Volatile
    private var isLoaded = false

    companion object {
        @Volatile
        private var INSTANCE: LocalMemoryStore? = null

        fun getInstance(context: Context): LocalMemoryStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalMemoryStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Preload active aliases into memory cache for instant sub-millisecond lookups.
     */
    suspend fun preload() = withContext(Dispatchers.IO) {
        val all = runCatching { aliasDao?.getAllAliases() ?: emptyList() }.getOrDefault(emptyList())
        memoryCache.clear()
        for (item in all) {
            memoryCache[item.normalizedPhrase] = item
        }
        isLoaded = true
    }

    /**
     * Fast-path lookup for user-taught alias by spoken Myanmar phrase.
     */
    suspend fun findAlias(rawPhrase: String): LearnedAliasEntity? = withContext(Dispatchers.Default) {
        if (!isLoaded) {
            preload()
        }
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(rawPhrase)
        
        // 1. Direct normalized memory match
        memoryCache[normalized]?.let { return@withContext it }

        // 2. Database lookup
        val fromDb = runCatching { aliasDao?.findByPhrase(normalized) }.getOrNull()
        if (fromDb != null) {
            memoryCache[normalized] = fromDb
            runCatching { aliasDao?.incrementUsage(fromDb.id) }
            return@withContext fromDb
        }

        // 3. Substring key match in cache
        for ((key, entity) in memoryCache) {
            if (normalized.contains(key) || key.contains(normalized)) {
                return@withContext entity
            }
        }

        null
    }

    /**
     * Learn a new alias taught by the user.
     */
    suspend fun learnAlias(
        rawPhrase: String,
        actionType: String,
        targetPayload: String,
        displayLabel: String
    ): LearnedAliasEntity = withContext(Dispatchers.IO) {
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(rawPhrase)
        val entity = LearnedAliasEntity(
            normalizedPhrase = normalized,
            rawPhrase = rawPhrase,
            actionType = actionType,
            targetPayload = targetPayload,
            displayLabel = displayLabel,
            confidence = 1.0f,
            learnedAt = System.currentTimeMillis(),
            usageCount = 1
        )
        val id = runCatching { aliasDao?.insertAlias(entity) ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())
        val saved = entity.copy(id = id)
        memoryCache[normalized] = saved
        saved
    }

    /**
     * Stream all learned aliases for UI display / management.
     */
    fun getAllAliasesFlow(): Flow<List<LearnedAliasEntity>> {
        return aliasDao?.getAllAliasesFlow() ?: kotlinx.coroutines.flow.flowOf(memoryCache.values.toList())
    }

    suspend fun deleteAlias(id: Long) = withContext(Dispatchers.IO) {
        runCatching { aliasDao?.deleteById(id) }
        memoryCache.values.removeIf { it.id == id }
    }
}
