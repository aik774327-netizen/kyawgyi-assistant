package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.LearnedAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedAliasDao {

    @Query("SELECT * FROM learned_aliases ORDER BY usageCount DESC, learnedAt DESC")
    fun getAllAliasesFlow(): Flow<List<LearnedAliasEntity>>

    @Query("SELECT * FROM learned_aliases ORDER BY usageCount DESC, learnedAt DESC")
    suspend fun getAllAliases(): List<LearnedAliasEntity>

    @Query("SELECT * FROM learned_aliases WHERE normalizedPhrase = :phrase LIMIT 1")
    suspend fun findByPhrase(phrase: String): LearnedAliasEntity?

    @Query("SELECT * FROM learned_aliases WHERE normalizedPhrase LIKE '%' || :keyword || '%'")
    suspend fun searchAliases(keyword: String): List<LearnedAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: LearnedAliasEntity): Long

    @Update
    suspend fun updateAlias(alias: LearnedAliasEntity)

    @Delete
    suspend fun deleteAlias(alias: LearnedAliasEntity)

    @Query("DELETE FROM learned_aliases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE learned_aliases SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)
}
