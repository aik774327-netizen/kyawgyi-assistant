package com.example.ingestion

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestedAssetDao {

    @Query("SELECT * FROM ingested_assets ORDER BY timestamp DESC")
    fun getAllAssetsFlow(): Flow<List<IngestedAssetEntity>>

    @Query("SELECT * FROM ingested_assets ORDER BY timestamp DESC")
    suspend fun getAllAssets(): List<IngestedAssetEntity>

    @Query("SELECT * FROM ingested_assets WHERE category = :category ORDER BY timestamp DESC")
    fun getAssetsByCategoryFlow(category: String): Flow<List<IngestedAssetEntity>>

    @Query("SELECT * FROM ingested_assets WHERE id = :id LIMIT 1")
    suspend fun getAssetById(id: Long): IngestedAssetEntity?

    @Query("SELECT * FROM ingested_assets WHERE sha256Hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): IngestedAssetEntity?

    @Query("""
        SELECT * FROM ingested_assets 
        WHERE fileName LIKE '%' || :query || '%' 
           OR aliasName LIKE '%' || :query || '%' 
           OR indexedKeywords LIKE '%' || :query || '%' 
           OR extractedSummary LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchAssets(query: String): List<IngestedAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: IngestedAssetEntity): Long

    @Update
    suspend fun updateAsset(asset: IngestedAssetEntity)

    @Delete
    suspend fun deleteAsset(asset: IngestedAssetEntity)

    @Query("DELETE FROM ingested_assets WHERE id = :id")
    suspend fun deleteAssetById(id: Long)

    @Query("SELECT COUNT(*) FROM ingested_assets")
    fun getAssetCountFlow(): Flow<Int>
}
