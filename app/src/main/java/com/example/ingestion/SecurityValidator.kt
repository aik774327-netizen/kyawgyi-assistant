package com.example.ingestion

import java.io.File
import java.security.MessageDigest

object SecurityValidator {

  private const val MAX_FILE_SIZE_BYTES = 150L * 1024L * 1024L // 150 MB

  private val FORBIDDEN_EXTENSIONS = setOf(
    "exe", "bat", "cmd", "sh", "bin", "apk", "dex", "so", "vbs", "ps1", "scr", "pif"
  )

  /**
   * Sanitizes filename: removes path traversal tokens, replaces unsafe characters with underscore.
   */
  fun sanitizeFilename(rawFilename: String): String {
    var clean = rawFilename.trim()
      .replace("\\", "/")
      .substringAfterLast("/")
      .replace("..", "")
      .replace(Regex("[^a-zA-Z0-9._\\-\\u1000-\\u109F\\s]"), "_")
      .trim()

    if (clean.isEmpty() || clean == "." || clean == "..") {
      clean = "asset_${System.currentTimeMillis()}"
    }
    return clean
  }

  /**
   * Validates file size, extension, path safety.
   */
  fun validate(filename: String, sizeBytes: Long): ValidationResult {
    val sanitized = sanitizeFilename(filename)
    val ext = sanitized.substringAfterLast('.', "").lowercase()

    if (FORBIDDEN_EXTENSIONS.contains(ext)) {
      return ValidationResult.Invalid("Executable and script files (.${ext}) are strictly blocked for security.")
    }

    if (sizeBytes > MAX_FILE_SIZE_BYTES) {
      return ValidationResult.Invalid("File size exceeds maximum permitted limit (${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB).")
    }

    if (filename.contains("../") || filename.contains("..\\") || filename.startsWith("/")) {
      return ValidationResult.Invalid("Path traversal attempts are prohibited.")
    }

    return ValidationResult.Valid(sanitized, ext)
  }

  /**
   * Computes deterministic SHA-256 hash from byte array.
   */
  fun computeSha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  /**
   * Computes deterministic SHA-256 hash from file.
   */
  fun computeFileSha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        md.update(buffer, 0, bytesRead)
      }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  sealed class ValidationResult {
    data class Valid(val sanitizedFilename: String, val extension: String) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
  }
}
