package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.UrlControlEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlControlDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrlControl(urlControl: UrlControlEntity): Long

    @Update
    suspend fun updateUrlControl(urlControl: UrlControlEntity)

    @Query("SELECT * FROM url_controls ORDER BY id DESC")
    fun getAllUrlControls(): Flow<List<UrlControlEntity>>

    @Query("SELECT * FROM url_controls WHERE isEnabled = 1")
    fun getAllActiveUrlControls(): Flow<List<UrlControlEntity>>

    @Query("SELECT * FROM url_controls WHERE triggerPhrase = :phrase AND isEnabled = 1 LIMIT 1")
    suspend fun findByPhrase(phrase: String): UrlControlEntity?

    @Query("SELECT * FROM url_controls WHERE isEnabled = 1")
    suspend fun getAllActiveList(): List<UrlControlEntity>

    @Delete
    suspend fun deleteUrlControl(urlControl: UrlControlEntity)
}
