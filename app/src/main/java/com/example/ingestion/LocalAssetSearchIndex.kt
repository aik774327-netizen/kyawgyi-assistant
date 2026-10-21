package com.example.ingestion

import android.content.Context
import android.util.Log

/**
 * Intelligent Local Search Index for ingested assets and memories
 * Supports offline-first instant retrieval by keyword, filename, or category.
 */
class LocalAssetSearchIndex(private val context: Context) {

    private val db = com.example.data.AppDatabase.getDatabase(context)

    suspend fun search(query: String): List<IngestedAssetEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return db.ingestedAssetDao().getAllAssets()
        }
        return try {
            db.ingestedAssetDao().searchAssets(trimmed)
        } catch (e: Exception) {
            Log.e("LocalAssetSearchIndex", "Error querying search index", e)
            emptyList()
        }
    }

    suspend fun getAssetsByCategory(category: String): List<IngestedAssetEntity> {
        return try {
            val all = db.ingestedAssetDao().getAllAssets()
            all.filter { it.category.equals(category, ignoreCase = true) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
