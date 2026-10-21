package com.example.ingestion

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Universal Ingested Asset Entity
 * Represents any file, media, audio, document, or code file ingested into KYAW GYI AI.
 */
@Entity(tableName = "ingested_assets")
data class IngestedAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    val fileName: String,
    val mimeType: String,
    val category: String, // "IMAGE", "AUDIO", "VIDEO", "DOCUMENT", "CODE_DATA", "ARCHIVE", "OTHER"
    val sizeBytes: Long,
    val sha256Hash: String,
    val storedRelativePath: String,
    val originalSource: String = "USER_UPLOAD", // "USER_UPLOAD", "DOWNLOAD", "VOICE_RECORDING"
    val aliasName: String? = null,
    val metadataJson: String = "{}",
    val extractedSummary: String? = null,
    val indexedKeywords: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
