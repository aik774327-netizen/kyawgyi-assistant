package com.example.ingestion

import com.example.database.FileAssetEntity
import com.example.database.MusicAssetEntity

/**
 * Step progress for ingestion state machine.
 */
enum class IngestionStep(val burmeseLabel: String, val englishLabel: String) {
  UPLOADING("ဖိုင်တင်နေသည်...", "Uploading..."),
  ANALYZING("စစ်ဆေးနေပါသည်...", "Analyzing..."),
  CLASSIFYING("အမျိုးအစားခွဲခြားနေသည်...", "Classifying..."),
  ROUTING("တည်နေရာသတ်မှတ်နေသည်...", "Routing..."),
  VALIDATING("လုံခြုံရေးစစ်ဆေးနေသည်...", "Validating security..."),
  SAVING("သိမ်းဆည်းနေပါသည်...", "Saving file..."),
  INDEXING("အချက်အလက် index ပြုလုပ်နေသည်...", "Indexing metadata..."),
  VERIFYING("အတည်ပြုစစ်ဆေးနေသည်...", "Verifying integrity..."),
  COMPLETED("အောင်မြင်စွာ သိမ်းဆည်းပြီးပါပြီ", "Completed successfully"),
  FAILED("မအောင်မြင်ပါ", "Failed")
}

/**
 * Strongly typed outcome of universal ingestion.
 */
sealed class IngestionResult {
  data class Success(
    val fileAsset: FileAssetEntity,
    val musicAsset: MusicAssetEntity? = null,
    val burmeseMessage: String,
    val englishMessage: String
  ) : IngestionResult()

  data class Duplicate(
    val existingAsset: FileAssetEntity,
    val burmeseMessage: String,
    val englishMessage: String
  ) : IngestionResult()

  data class ConflictRequiresDecision(
    val proposedPath: String,
    val existingPath: String,
    val sha256: String,
    val burmeseMessage: String
  ) : IngestionResult()

  data class Unsupported(val reason: String) : IngestionResult()
  data class SecurityBlocked(val reason: String) : IngestionResult()
  data class ValidationFailed(val reason: String) : IngestionResult()
  data class StorageFailed(val error: String) : IngestionResult()
  data class IndexFailed(val error: String) : IngestionResult()
  data class PartialSuccess(val fileAsset: FileAssetEntity, val warning: String) : IngestionResult()
  data class Error(val error: String) : IngestionResult()

  val isSuccess: Boolean
    get() = this is Success || this is Duplicate
}

/**
 * Extracted file metadata holder.
 */
data class ExtractedMetadata(
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  val genre: String? = null,
  val durationMs: Long = 0L,
  val year: Int? = null,
  val trackNumber: Int? = null,
  val width: Int? = null,
  val height: Int? = null,
  val lineCount: Int? = null,
  val extraSummary: String? = null
)
