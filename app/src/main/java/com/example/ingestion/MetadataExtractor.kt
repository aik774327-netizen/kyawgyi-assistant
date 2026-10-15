package com.example.ingestion

import android.media.MediaMetadataRetriever
import java.io.File

object MetadataExtractor {

  /**
   * Extracts metadata from audio/media file or falls back cleanly to derived filename info.
   */
  fun extractAudioMetadata(file: File, originalFilename: String): ExtractedMetadata {
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var genre: String? = null
    var durationMs: Long = 0L
    var year: Int? = null
    var trackNumber: Int? = null

    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(file.absolutePath)
      title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
      artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
      album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
      genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
      val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
      if (!durStr.isNullOrEmpty()) {
        durationMs = durStr.toLongOrNull() ?: 0L
      }
      val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
      if (!yearStr.isNullOrEmpty()) {
        year = yearStr.toIntOrNull()
      }
      val trackStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
      if (!trackStr.isNullOrEmpty()) {
        trackNumber = trackStr.substringBefore('/').toIntOrNull()
      }
    } catch (_: Exception) {
      // Graceful fallback on unreadable or partial tags
    } finally {
      try {
        retriever.release()
      } catch (_: Exception) {}
    }

    // If ID3 metadata is missing or incomplete, derive from filename (e.g., "Lay Phyu - Song Name.mp3")
    val cleanBase = originalFilename.substringBeforeLast('.')
    if (artist.isNullOrBlank() && cleanBase.contains(" - ")) {
      val parts = cleanBase.split(" - ", limit = 2)
      if (parts.size == 2) {
        artist = parts[0].trim()
        if (title.isNullOrBlank()) {
          title = parts[1].trim()
        }
      }
    }

    if (title.isNullOrBlank()) {
      title = cleanBase.replace("_", " ").trim()
    }
    if (artist.isNullOrBlank()) {
      artist = "Unknown Artist"
    }
    if (album.isNullOrBlank()) {
      album = "Single"
    }
    if (genre.isNullOrBlank()) {
      genre = "Music"
    }

    return ExtractedMetadata(
      title = title,
      artist = artist,
      album = album,
      genre = genre,
      durationMs = durationMs,
      year = year,
      trackNumber = trackNumber
    )
  }

  /**
   * Extracts text preview/metadata from document or config files.
   */
  fun extractDocumentSummary(file: File, extension: String): ExtractedMetadata {
    return try {
      val lines = file.readLines()
      val lineCount = lines.size
      val summary = if (lines.isNotEmpty()) lines.take(3).joinToString("\n") else "Empty file"
      ExtractedMetadata(
        title = file.nameWithoutExtension,
        lineCount = lineCount,
        extraSummary = summary
      )
    } catch (_: Exception) {
      ExtractedMetadata(
        title = file.nameWithoutExtension,
        extraSummary = "Binary or non-UTF8 document"
      )
    }
  }
}
