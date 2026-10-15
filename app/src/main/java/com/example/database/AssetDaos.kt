package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileAssetDao {
  @Query("SELECT * FROM file_assets ORDER BY createdAt DESC")
  fun getAllAssetsFlow(): Flow<List<FileAssetEntity>>

  @Query("SELECT * FROM file_assets ORDER BY createdAt DESC")
  suspend fun getAllAssets(): List<FileAssetEntity>

  @Query("SELECT * FROM file_assets WHERE category = :category ORDER BY createdAt DESC")
  suspend fun getAssetsByCategory(category: String): List<FileAssetEntity>

  @Query("SELECT * FROM file_assets WHERE sha256 = :hash LIMIT 1")
  suspend fun findBySha256(hash: String): FileAssetEntity?

  @Query("SELECT * FROM file_assets WHERE id = :id LIMIT 1")
  suspend fun findById(id: String): FileAssetEntity?

  @Query("SELECT COUNT(*) FROM file_assets WHERE category = :category")
  suspend fun countByCategory(category: String): Int

  @Query("SELECT COUNT(*) FROM file_assets")
  suspend fun countTotal(): Int

  @Query("SELECT SUM(fileSizeBytes) FROM file_assets")
  suspend fun totalSizeBytes(): Long?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(asset: FileAssetEntity)

  @Update
  suspend fun update(asset: FileAssetEntity)

  @Delete
  suspend fun delete(asset: FileAssetEntity)

  @Query("DELETE FROM file_assets WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface MusicAssetDao {
  @Query("SELECT * FROM music_assets ORDER BY createdAt DESC")
  fun getAllMusicFlow(): Flow<List<MusicAssetEntity>>

  @Query("SELECT * FROM music_assets ORDER BY createdAt DESC")
  suspend fun getAllMusic(): List<MusicAssetEntity>

  @Query("SELECT * FROM music_assets WHERE sha256 = :hash LIMIT 1")
  suspend fun findBySha256(hash: String): MusicAssetEntity?

  @Query("SELECT * FROM music_assets WHERE id = :id LIMIT 1")
  suspend fun findById(id: String): MusicAssetEntity?

  @Query("SELECT * FROM music_assets WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR searchableText LIKE '%' || :query || '%'")
  suspend fun searchMusic(query: String): List<MusicAssetEntity>

  @Query("SELECT * FROM music_assets ORDER BY createdAt DESC LIMIT 1")
  suspend fun getLatestMusic(): MusicAssetEntity?

  @Query("SELECT COUNT(*) FROM music_assets")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(music: MusicAssetEntity)

  @Update
  suspend fun update(music: MusicAssetEntity)

  @Delete
  suspend fun delete(music: MusicAssetEntity)

  @Query("DELETE FROM music_assets WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface AssetAliasDao {
  @Query("SELECT * FROM asset_aliases WHERE assetId = :assetId")
  suspend fun getAliasesForAsset(assetId: String): List<AssetAliasEntity>

  @Query("SELECT * FROM asset_aliases WHERE normalizedText = :normalizedText")
  suspend fun findByNormalized(normalizedText: String): List<AssetAliasEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(alias: AssetAliasEntity)

  @Query("DELETE FROM asset_aliases WHERE assetId = :assetId")
  suspend fun deleteByAssetId(assetId: String)
}

@Dao
interface IngestionEventDao {
  @Query("SELECT * FROM ingestion_events ORDER BY timestamp DESC LIMIT :limit")
  fun getRecentEventsFlow(limit: Int = 50): Flow<List<IngestionEventEntity>>

  @Query("SELECT * FROM ingestion_events ORDER BY timestamp DESC LIMIT :limit")
  suspend fun getRecentEvents(limit: Int = 50): List<IngestionEventEntity>

  @Query("SELECT COUNT(*) FROM ingestion_events WHERE status = 'DUPLICATE'")
  suspend fun countDuplicates(): Int

  @Query("SELECT COUNT(*) FROM ingestion_events WHERE status = 'FAILED'")
  suspend fun countFailed(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(event: IngestionEventEntity)
}

@Dao
interface ChatMessageDao {
  @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
  fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
  suspend fun getAllMessages(): List<ChatMessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(message: ChatMessageEntity)

  @Query("DELETE FROM chat_messages")
  suspend fun clearAll()
}
