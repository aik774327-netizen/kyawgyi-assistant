package com.example.cognitive

import com.example.data.MemoryDao
import com.example.data.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * LongTermMemory manages durable user preferences, frequently accessed targets,
 * persistent facts, and learned configurations using Room Database integration with in-memory caching.
 */
class LongTermMemory(
    private val memoryDao: MemoryDao? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val localCache = ConcurrentHashMap<String, MemoryRecord>()

    init {
        // Hydrate default knowledge
        setPreference("PREFERRED_MUSIC_APP", "YouTube")
        setPreference("PREFERRED_SEARCH_ENGINE", "Google")
    }

    fun setPreference(key: String, value: String, isPinned: Boolean = false) {
        val record = MemoryRecord(
            key = key,
            value = value,
            type = MemoryType.PREFERENCE,
            ttlMs = 0L, // Indefinite
            updatedAt = System.currentTimeMillis()
        )
        localCache[key] = record

        // Persist to Room if available
        if (memoryDao != null) {
            coroutineScope.launch {
                try {
                    memoryDao.insertMemory(
                        MemoryEntity(
                            key = key,
                            value = value,
                            category = "Preference",
                            isPinned = isPinned,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (ignored: Throwable) {}
            }
        }
    }

    fun getPreference(key: String, defaultValue: String? = null): String? {
        return localCache[key]?.value ?: defaultValue
    }

    fun storeFact(key: String, value: String, category: String = "User Fact") {
        val record = MemoryRecord(
            key = key,
            value = value,
            type = MemoryType.LONG_TERM,
            ttlMs = 0L,
            updatedAt = System.currentTimeMillis()
        )
        localCache[key] = record

        if (memoryDao != null) {
            coroutineScope.launch {
                try {
                    memoryDao.insertMemory(
                        MemoryEntity(
                            key = key,
                            value = value,
                            category = category,
                            isPinned = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (ignored: Throwable) {}
            }
        }
    }

    fun getFact(key: String): String? {
        return localCache[key]?.value
    }

    fun getAllRecords(): List<MemoryRecord> {
        return localCache.values.toList()
    }

    fun clearCache() {
        localCache.clear()
    }
}
