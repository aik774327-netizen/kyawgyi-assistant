package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: ShortcutEntity)

    @Query("SELECT * FROM kyawgyi_shortcuts WHERE isEnabled = 1")
    fun getAllActiveShortcuts(): Flow<List<ShortcutEntity>>

    @Query("SELECT * FROM kyawgyi_shortcuts WHERE triggerPhrase = :phrase AND isEnabled = 1 LIMIT 1")
    suspend fun findShortcutByPhrase(phrase: String): ShortcutEntity?

    @Delete
    suspend fun deleteShortcut(shortcut: ShortcutEntity)
}
