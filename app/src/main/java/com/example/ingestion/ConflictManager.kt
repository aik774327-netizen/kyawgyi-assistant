package com.example.ingestion

import java.io.File

object ConflictManager {

  sealed class ConflictResult {
    data class ExactDuplicate(val existingFile: File, val sha256: String) : ConflictResult()
    data class UniqueTarget(val targetFile: File) : ConflictResult()
    data class ResolvedWithRename(val originalRequestedFile: File, val resolvedTargetFile: File, val sha256: String) : ConflictResult()
  }

  /**
   * Resolves conflict for destination file.
   * If identical hash exists: identifies as duplicate.
   * If different hash exists: provides deterministic incremented name (e.g. name (1).ext).
   */
  fun resolve(destinationDir: File, proposedFilename: String, newFileSha256: String): ConflictResult {
    val target = File(destinationDir, proposedFilename)
    if (!target.exists()) {
      return ConflictResult.UniqueTarget(target)
    }

    // Existing file exists: calculate its hash
    val existingSha = SecurityValidator.computeFileSha256(target)
    if (existingSha.equals(newFileSha256, ignoreCase = true)) {
      return ConflictResult.ExactDuplicate(target, existingSha)
    }

    // Different content: compute unique incremented filename
    val baseName = proposedFilename.substringBeforeLast('.')
    val ext = if (proposedFilename.contains('.')) ".${proposedFilename.substringAfterLast('.')}" else ""

    var counter = 1
    var resolvedFile: File
    do {
      val candidateName = "$baseName ($counter)$ext"
      resolvedFile = File(destinationDir, candidateName)
      if (resolvedFile.exists()) {
        val candSha = SecurityValidator.computeFileSha256(resolvedFile)
        if (candSha.equals(newFileSha256, ignoreCase = true)) {
          return ConflictResult.ExactDuplicate(resolvedFile, candSha)
        }
      }
      counter++
    } while (resolvedFile.exists())

    return ConflictResult.ResolvedWithRename(target, resolvedFile, newFileSha256)
  }
}
