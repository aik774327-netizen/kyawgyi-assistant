package com.example.ingestion

import android.content.Context
import com.example.database.AppDatabase
import com.example.database.MusicAssetEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

sealed class MusicSearchOutcome {
  data class SingleMatch(val song: MusicAssetEntity, val matchReason: String) : MusicSearchOutcome()
  data class MultipleCandidates(
    val candidates: List<MusicAssetEntity>,
    val burmeseExplanation: String
  ) : MusicSearchOutcome()
  data class NoMatch(val burmeseExplanation: String) : MusicSearchOutcome()
}

class MusicRepository(context: Context) {

  private val database = AppDatabase.getDatabase(context)
  private val musicAssetDao = database.musicAssetDao()
  private val fileAssetDao = database.fileAssetDao()

  fun getAllMusicFlow(): Flow<List<MusicAssetEntity>> = musicAssetDao.getAllMusicFlow()

  suspend fun getAllMusic(): List<MusicAssetEntity> = musicAssetDao.getAllMusic()

  suspend fun getLatestSong(): MusicAssetEntity? = musicAssetDao.getLatestMusic()

  /**
   * Searches music catalog with Burmese & English normalization, alias mapping, and ranking.
   */
  suspend fun search(query: String): MusicSearchOutcome {
    val allSongs = musicAssetDao.getAllMusic()
    if (allSongs.isEmpty()) {
      return MusicSearchOutcome.NoMatch("လက်ရှိ Music Library ထဲတွင် သီချင်းများ မရှိသေးပါ (No songs available).")
    }

    val ranked = AssetIndexer.rankMatches(query, allSongs)
    if (ranked.isEmpty()) {
      return MusicSearchOutcome.NoMatch("\"$query\" နှင့် ကိုက်ညီသော သီချင်း ရှာမတွေ့ပါ ခင်ဗျာ။")
    }

    // If single candidate or top candidate has distinct high confidence
    val top = ranked.first()
    if (ranked.size == 1 || (top.score >= 850 && (ranked.size > 1 && top.score - ranked[1].score > 200))) {
      return MusicSearchOutcome.SingleMatch(top.asset, top.matchReason)
    }

    // Multiple potential songs: disambiguate without guessing!
    val candidateCount = ranked.size
    val artistOrQuery = top.asset.artist
    val burmeseMsg = "$artistOrQuery ရဲ့ သီချင်း $candidateCount ပုဒ် တွေ့ရှိထားပါသည်။ ဘယ်သီချင်းကို ဖွင့်ပေးရမလဲ ခင်ဗျာ?\n" +
      ranked.take(4).mapIndexed { idx, it ->
        val burmeseNum = when (idx) {
          0 -> "၁။ ပထမတစ်ပုဒ်"
          1 -> "၂။ ဒုတိယတစ်ပုဒ်"
          2 -> "၃။ တတိယတစ်ပုဒ်"
          else -> "${idx + 1}။"
        }
        "$burmeseNum - \"${it.asset.title}\""
      }.joinToString("\n")

    return MusicSearchOutcome.MultipleCandidates(
      candidates = ranked.map { it.asset },
      burmeseExplanation = burmeseMsg
    )
  }

  suspend fun deleteSong(song: MusicAssetEntity): Boolean {
    return try {
      val file = File(song.absolutePath)
      if (file.exists()) {
        file.delete()
      }
      musicAssetDao.deleteById(song.id)
      fileAssetDao.deleteById(song.fileAssetId)
      true
    } catch (_: Exception) {
      false
    }
  }
}
