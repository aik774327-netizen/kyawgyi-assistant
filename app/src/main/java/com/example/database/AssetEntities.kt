package com.example.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Universal File Asset entity stored in local Room database.
 */
@Entity(
  tableName = "file_assets",
  indices = [
    Index(value = ["sha256"], unique = false),
    Index(value = ["category"]),
    Index(value = ["filename"])
  ]
)
data class FileAssetEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val filename: String,
  val originalFilename: String,
  val fileUri: String,
  val absolutePath: String,
  val mimeType: String,
  val extension: String,
  val fileSizeBytes: Long,
  val sha256: String,
  val category: String, // MUSIC, AUDIO, IMAGE, VIDEO, DOCUMENT, PROJECT_CONFIG, PROJECT_SOURCE, ASSET, QUARANTINE
  val destinationFolder: String,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val provenance: String = "USER_UPLOAD", // USER_UPLOAD, AI_PROPOSAL, SYSTEM_GENERATED
  val searchableText: String = ""
)

/**
 * Music asset entity with detailed audio metadata for local offline library and playback.
 */
@Entity(
  tableName = "music_assets",
  indices = [
    Index(value = ["sha256"], unique = true),
    Index(value = ["title"]),
    Index(value = ["artist"]),
    Index(value = ["album"])
  ]
)
data class MusicAssetEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val fileAssetId: String,
  val title: String,
  val artist: String,
  val album: String,
  val genre: String = "General",
  val durationMs: Long = 0L,
  val year: Int? = null,
  val trackNumber: Int? = null,
  val artworkPath: String? = null,
  val fileUri: String,
  val absolutePath: String,
  val mimeType: String = "audio/mpeg",
  val sha256: String,
  val source: String = "USER_UPLOAD",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val searchableText: String = ""
)

/**
 * Multi-lingual aliases and search keywords for an asset.
 */
@Entity(
  tableName = "asset_aliases",
  indices = [
    Index(value = ["assetId"]),
    Index(value = ["aliasText"]),
    Index(value = ["normalizedText"])
  ]
)
data class AssetAliasEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val assetId: String,
  val aliasText: String,
  val normalizedText: String,
  val sourceType: String = "EXPLICIT_USER", // FILE_METADATA, AI_INFERRED, EXPLICIT_USER
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Ingestion event audit log.
 */
@Entity(
  tableName = "ingestion_events",
  indices = [
    Index(value = ["timestamp"]),
    Index(value = ["operationId"]),
    Index(value = ["sha256"])
  ]
)
data class IngestionEventEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val timestamp: Long = System.currentTimeMillis(),
  val operationId: String,
  val sha256: String,
  val filename: String,
  val source: String,
  val destination: String,
  val eventType: String, // INGEST_STARTED, TYPE_DETECTED, METADATA_EXTRACTED, DESTINATION_RESOLVED, SECURITY_VALIDATED, DUPLICATE_CHECKED, CONFLICT_RESOLVED, FILE_STORED, DATABASE_INDEXED, SEARCH_INDEX_UPDATED, VERIFICATION_COMPLETED, FAILED
  val status: String, // SUCCESS, FAILED, DUPLICATE, BLOCKED
  val error: String? = null,
  val actor: String = "USER", // USER, SYSTEM, AI_PROPOSAL
  val confirmationRequired: Boolean = false,
  val confirmed: Boolean = true
)

/**
 * Chat messages for AI Angel Chatbox.
 */
@Entity(
  tableName = "chat_messages",
  indices = [
    Index(value = ["timestamp"])
  ]
)
data class ChatMessageEntity(
  @PrimaryKey val id: String = UUID.randomUUID().toString(),
  val sender: String, // USER, AI_ANGEL, SYSTEM
  val messageText: String,
  val attachedFileAssetId: String? = null,
  val attachmentName: String? = null,
  val attachmentMimeType: String? = null,
  val attachmentCategory: String? = null,
  val attachmentUri: String? = null,
  val attachmentSize: Long = 0L,
  val ingestionStatus: String? = null, // SUCCESS, DUPLICATE, FAILED, etc.
  val destinationPath: String? = null,
  val metadataJson: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)
