package com.example.ingestion

import android.content.Context
import android.net.Uri
import com.example.database.AppDatabase
import com.example.database.AssetAliasEntity
import com.example.database.FileAssetEntity
import com.example.database.IngestionEventEntity
import com.example.database.MusicAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

class UniversalDataIngestionManager(private val context: Context) {

  private val database = AppDatabase.getDatabase(context)
  private val fileAssetDao = database.fileAssetDao()
  private val musicAssetDao = database.musicAssetDao()
  private val assetAliasDao = database.assetAliasDao()
  private val ingestionEventDao = database.ingestionEventDao()

  /**
   * Main entry point to ingest data from Uri or Stream.
   */
  suspend fun ingestFromUri(
    uri: Uri,
    originalFilename: String,
    mimeType: String,
    provenance: String = "USER_UPLOAD",
    onProgress: ((IngestionStep) -> Unit)? = null
  ): IngestionResult = withContext(Dispatchers.IO) {
    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        ingestFromStream(
          inputStream = stream,
          originalFilename = originalFilename,
          mimeType = mimeType,
          fileUri = uri.toString(),
          provenance = provenance,
          onProgress = onProgress
        )
      } ?: IngestionResult.Error("ဖိုင် input stream ကို ဖွင့်၍ မရပါ (Unable to open stream).")
    } catch (e: Exception) {
      IngestionResult.Error("Ingestion failure: ${e.localizedMessage ?: "Unknown error"}")
    }
  }

  /**
   * Universal ingestion pipeline for an InputStream.
   */
  suspend fun ingestFromStream(
    inputStream: InputStream,
    originalFilename: String,
    mimeType: String,
    fileUri: String = "",
    provenance: String = "USER_UPLOAD",
    onProgress: ((IngestionStep) -> Unit)? = null
  ): IngestionResult = withContext(Dispatchers.IO) {
    val operationId = UUID.randomUUID().toString()

    // 1. Stage in temp cache
    onProgress?.invoke(IngestionStep.UPLOADING)
    val tempFile = File(context.cacheDir, "temp_ingest_${System.currentTimeMillis()}.tmp")
    try {
      tempFile.outputStream().use { out ->
        inputStream.copyTo(out)
      }

      // 2. Analyzing & Validating Security
      onProgress?.invoke(IngestionStep.ANALYZING)
      val validation = SecurityValidator.validate(originalFilename, tempFile.length())
      if (validation is SecurityValidator.ValidationResult.Invalid) {
        logEvent(operationId, "", originalFilename, "SECURITY_VALIDATED", "FAILED", validation.reason)
        return@withContext IngestionResult.SecurityBlocked(validation.reason)
      }
      val validRes = validation as SecurityValidator.ValidationResult.Valid

      // 3. Compute SHA-256
      val sha256 = SecurityValidator.computeFileSha256(tempFile)

      // 4. Duplicate Check in Database
      onProgress?.invoke(IngestionStep.VALIDATING)
      val existingAsset = fileAssetDao.findBySha256(sha256)
      if (existingAsset != null && File(existingAsset.absolutePath).exists()) {
        logEvent(operationId, sha256, originalFilename, "DUPLICATE_CHECKED", "DUPLICATE", "File already exists in library")
        onProgress?.invoke(IngestionStep.COMPLETED)
        return@withContext IngestionResult.Duplicate(
          existingAsset = existingAsset,
          burmeseMessage = "\"${existingAsset.filename}\" သည် စနစ်အတွင်း သိမ်းဆည်းပြီးဖြစ်ပါသည် (Duplicate SHA-256).",
          englishMessage = "File already exists in library with identical SHA-256."
        )
      }

      // 5. Classify and Route
      onProgress?.invoke(IngestionStep.CLASSIFYING)
      val routeDecision = ProjectDataRouter.route(
        context = context,
        filename = validRes.sanitizedFilename,
        mimeType = mimeType,
        extension = validRes.extension
      )

      // 6. Conflict Management & Deterministic Destination Resolution
      onProgress?.invoke(IngestionStep.ROUTING)
      val conflictResolution = ConflictManager.resolve(
        destinationDir = routeDecision.targetDirectory,
        proposedFilename = validRes.sanitizedFilename,
        newFileSha256 = sha256
      )

      val targetFile = when (conflictResolution) {
        is ConflictManager.ConflictResult.ExactDuplicate -> {
          // File already existed on disk with same hash
          logEvent(operationId, sha256, originalFilename, "DUPLICATE_CHECKED", "DUPLICATE", "Existing disk file matched hash")
          onProgress?.invoke(IngestionStep.COMPLETED)
          val existing = fileAssetDao.findBySha256(sha256)
          if (existing != null) {
            return@withContext IngestionResult.Duplicate(
              existingAsset = existing,
              burmeseMessage = "\"${conflictResolution.existingFile.name}\" သည် သိမ်းဆည်းပြီးဖြစ်ပါသည်",
              englishMessage = "File exists on disk with identical hash."
            )
          }
          conflictResolution.existingFile
        }
        is ConflictManager.ConflictResult.UniqueTarget -> conflictResolution.targetFile
        is ConflictManager.ConflictResult.ResolvedWithRename -> conflictResolution.resolvedTargetFile
      }

      // 7. Save file deterministically
      onProgress?.invoke(IngestionStep.SAVING)
      if (targetFile != tempFile && !targetFile.exists()) {
        tempFile.copyTo(targetFile, overwrite = false)
      }

      // 8. Indexing Metadata
      onProgress?.invoke(IngestionStep.INDEXING)
      val categoryName = routeDecision.category.name
      var musicAsset: MusicAssetEntity? = null

      val fileAsset = FileAssetEntity(
        id = UUID.randomUUID().toString(),
        filename = targetFile.name,
        originalFilename = originalFilename,
        fileUri = if (fileUri.isNotEmpty()) fileUri else targetFile.toURI().toString(),
        absolutePath = targetFile.absolutePath,
        mimeType = mimeType,
        extension = validRes.extension,
        fileSizeBytes = targetFile.length(),
        sha256 = sha256,
        category = categoryName,
        destinationFolder = routeDecision.relativePath,
        provenance = provenance,
        searchableText = "${targetFile.name} $originalFilename $categoryName"
      )
      fileAssetDao.insert(fileAsset)

      // If category is MUSIC, create MusicAssetEntity and index aliases
      if (routeDecision.category == ProjectDataRouter.AssetCategory.MUSIC) {
        val meta = MetadataExtractor.extractAudioMetadata(targetFile, originalFilename)
        val searchableText = AssetIndexer.buildSearchableText(
          title = meta.title ?: targetFile.nameWithoutExtension,
          artist = meta.artist ?: "Unknown Artist",
          album = meta.album ?: "Single",
          filename = targetFile.name
        )

        val musicEntity = MusicAssetEntity(
          id = UUID.randomUUID().toString(),
          fileAssetId = fileAsset.id,
          title = meta.title ?: targetFile.nameWithoutExtension,
          artist = meta.artist ?: "Unknown Artist",
          album = meta.album ?: "Single",
          genre = meta.genre ?: "Music",
          durationMs = meta.durationMs,
          year = meta.year,
          trackNumber = meta.trackNumber,
          fileUri = fileAsset.fileUri,
          absolutePath = targetFile.absolutePath,
          mimeType = mimeType,
          sha256 = sha256,
          source = provenance,
          searchableText = searchableText
        )
        musicAssetDao.insert(musicEntity)
        musicAsset = musicEntity

        // Save artist and title aliases
        musicEntity.artist.let { artist ->
          assetAliasDao.insert(
            AssetAliasEntity(
              assetId = musicEntity.id,
              aliasText = artist,
              normalizedText = AssetIndexer.normalize(artist),
              sourceType = "FILE_METADATA"
            )
          )
        }
      }

      // 9. Verifying final integrity
      onProgress?.invoke(IngestionStep.VERIFYING)
      if (!targetFile.exists() || targetFile.length() != fileAsset.fileSizeBytes) {
        logEvent(operationId, sha256, targetFile.name, "VERIFICATION_COMPLETED", "FAILED", "Target file size verification failed")
        return@withContext IngestionResult.StorageFailed("ဖိုင်သိမ်းဆည်းမှု မပြည့်စုံပါ (File integrity verification failed).")
      }

      logEvent(operationId, sha256, targetFile.name, "VERIFICATION_COMPLETED", "SUCCESS", null)
      onProgress?.invoke(IngestionStep.COMPLETED)

      val burmeseMsg = when (routeDecision.category) {
        ProjectDataRouter.AssetCategory.MUSIC -> "တေးဂီတ ဖိုင် \"${targetFile.name}\" ကို Music Library ထဲသို့ စနစ်တကျ ထည့်သွင်းသိမ်းဆည်းပြီးပါပြီ။"
        ProjectDataRouter.AssetCategory.IMAGE -> "ရုပ်ပုံ ဖိုင် \"${targetFile.name}\" ကို Images ထဲသို့ သိမ်းဆည်းပြီးပါပြီ။"
        ProjectDataRouter.AssetCategory.PROJECT_CONFIG -> "ပရောဂျက် ဆက်တင် \"${targetFile.name}\" ကို Configurations ထဲသို့ သိမ်းဆည်းပြီးပါပြီ။"
        ProjectDataRouter.AssetCategory.PROJECT_SOURCE -> "ကုဒ်ဖိုင် \"${targetFile.name}\" ကို လုံခြုံစွာ စစ်ဆေးရန် Source Patches ထဲသို့ ထည့်သွင်းပြီးပါပြီ (Never auto-overwrites live code)."
        else -> "\"${targetFile.name}\" ကို ${routeDecision.category.displayNameBurmese} အဖြစ် အောင်မြင်စွာ သိမ်းဆည်းပြီးပါပြီ။"
      }

      val englishMsg = "Successfully ingested ${targetFile.name} as ${routeDecision.category.displayNameEnglish}."

      return@withContext IngestionResult.Success(
        fileAsset = fileAsset,
        musicAsset = musicAsset,
        burmeseMessage = burmeseMsg,
        englishMessage = englishMsg
      )
    } finally {
      if (tempFile.exists()) {
        tempFile.delete()
      }
    }
  }

  private suspend fun logEvent(
    opId: String,
    hash: String,
    filename: String,
    eventType: String,
    status: String,
    error: String?
  ) {
    try {
      ingestionEventDao.insert(
        IngestionEventEntity(
          operationId = opId,
          sha256 = hash,
          filename = filename,
          source = "USER_UPLOAD",
          destination = "kyaw_gyi_storage",
          eventType = eventType,
          status = status,
          error = error
        )
      )
    } catch (_: Exception) {}
  }
}
