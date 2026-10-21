package com.kyawgyi.voice.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.kyawgyi.voice.HotwordBiasing
import kotlinx.coroutines.flow.Flow

/**
 * Persistent Room entity storing a user-defined Burmese word, its spoken G2P pronunciation,
 * and whether it should be boosted as an ASR hotword.
 */
@Entity(tableName = "custom_lexicons")
data class CustomLexiconEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val pronunciation: String,
    val isHotword: Boolean = true,
    val boostScore: Float = 2.5f,
    val category: String = "USER_CUSTOM",
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface CustomLexiconDao {
    @Query("SELECT * FROM custom_lexicons ORDER BY createdAtMs DESC")
    fun getAllLexicons(): Flow<List<CustomLexiconEntity>>

    @Query("SELECT * FROM custom_lexicons ORDER BY createdAtMs DESC")
    suspend fun getAllLexiconsSnapshot(): List<CustomLexiconEntity>

    @Query("SELECT COUNT(*) FROM custom_lexicons")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLexicon(entity: CustomLexiconEntity)

    @Delete
    suspend fun deleteLexicon(entity: CustomLexiconEntity)

    @Query("DELETE FROM custom_lexicons WHERE word = :word")
    suspend fun deleteByWord(word: String)
}

@Database(entities = [CustomLexiconEntity::class], version = 1, exportSchema = false)
abstract class VoiceLexiconDatabase : RoomDatabase() {
    abstract fun customLexiconDao(): CustomLexiconDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceLexiconDatabase? = null

        fun getDatabase(context: Context): VoiceLexiconDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceLexiconDatabase::class.java,
                    "voice_ai_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

typealias AppDatabase = VoiceLexiconDatabase

/**
 * Repository abstracting Room access for [CustomLexiconEntity] and synchronizing saved user
 * hotwords with [HotwordBiasing].
 */
class CustomLexiconRepository(
    private val dao: CustomLexiconDao
) {
    val allLexicons: Flow<List<CustomLexiconEntity>> = dao.getAllLexicons()

    suspend fun addCustomWord(
        word: String,
        pronunciation: String = word,
        isHotword: Boolean = true,
        boostScore: Float = 2.5f,
        hotwordBiasing: HotwordBiasing? = null
    ) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return
        val cleanPron = pronunciation.trim().ifEmpty { cleanWord }
        val entity = CustomLexiconEntity(
            word = cleanWord,
            pronunciation = cleanPron,
            isHotword = isHotword,
            boostScore = boostScore
        )
        dao.insertLexicon(entity)
        if (isHotword) {
            hotwordBiasing?.registerHotword(
                phrase = cleanPron,
                canonicalReplacement = cleanWord,
                boostScore = boostScore,
                category = "USER_CUSTOM"
            )
        }
    }

    suspend fun removeCustomWord(entity: CustomLexiconEntity) {
        dao.deleteLexicon(entity)
    }

    /**
     * Loads all persisted custom words from Room SQLite storage into [hotwordBiasing].
     * Seeds default custom lexicon examples if the table is empty.
     */
    suspend fun syncIntoHotwordBiasing(hotwordBiasing: HotwordBiasing): Int {
        var existing = dao.getAllLexiconsSnapshot()
        if (existing.isEmpty()) {
            val defaults = listOf(
                CustomLexiconEntity(word = "ကျော်ကြီး", pronunciation = "ကျော် ကြီး", isHotword = true, boostScore = 3.0f),
                CustomLexiconEntity(word = "KBZPay", pronunciation = "ကေဘီဇက်ပေး", isHotword = true, boostScore = 2.8f),
                CustomLexiconEntity(word = "WavePay", pronunciation = "ဝေ့ဗ်ပေး", isHotword = true, boostScore = 2.8f),
                CustomLexiconEntity(word = "လက်ဖက်ရည်", pronunciation = "လဖက်ရည်", isHotword = true, boostScore = 2.2f)
            )
            for (item in defaults) {
                dao.insertLexicon(item)
            }
            existing = dao.getAllLexiconsSnapshot()
        }

        var synced = 0
        for (item in existing) {
            if (item.isHotword) {
                hotwordBiasing.registerHotword(
                    phrase = item.pronunciation,
                    canonicalReplacement = item.word,
                    boostScore = item.boostScore,
                    category = item.category
                )
                synced++
            }
        }
        return synced
    }
}
