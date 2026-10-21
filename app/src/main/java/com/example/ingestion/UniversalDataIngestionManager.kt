package com.example.ingestion

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Universal Data Ingestion & Intelligent File Router Engine
 *
 * Adheres strictly to security boundaries:
 * 1. Sandboxed target directories in app private internal/external storage.
 * 2. Deterministic hashing (SHA-256) for deduplication.
 * 3. Never overwrites or accesses raw OS code/manifest/gradle files.
 * 4. Extracts metadata (EXIF/Audio tags/Doc dimensions/lines) offline.
 * 5. Generates local full-text search tokens and Myanmar summaries.
 * 6. Grants and maintains persistent URI permission via Android Storage Access Framework (SAF).
 */
class UniversalDataIngestionManager(private val context: Context) {

    companion object {
        private const val TAG = "DataIngestionManager"
        private const val MAX_READABLE_TEXT_BYTES = 256 * 1024 // 256 KB max for text indexing
    }

    enum class AssetCategory(val folderName: String, val labelMM: String) {
        IMAGE("images", "ဓာတ်ပုံ/ရုပ်ပုံ"),
        AUDIO("audio", "အသံ/တေးဂီတ"),
        VIDEO("videos", "ဗီဒီယို"),
        DOCUMENT("documents", "စာရွက်စာတမ်း"),
        CODE_DATA("data_code", "ဒေတာနှင့် ကုဒ်"),
        ARCHIVE("archives", "ဖိုင်တွဲ သိမ်းဆည်းမှု"),
        OTHER("others", "အခြားဖိုင်များ")
    }

    data class IngestionResult(
        val isSuccess: Boolean,
        val asset: IngestedAssetEntity? = null,
        val isDuplicate: Boolean = false,
        val messageMM: String,
        val originalFileName: String
    )

    /**
     * Take persistent URI permission from Android Storage Access Framework (SAF).
     * Ensures that document and file access persists across device reboots and process restarts.
     */
    fun takePersistableUriPermissionSafely(
        uri: Uri,
        modeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
    ): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.takePersistableUriPermission(uri, modeFlags)
            Log.d(TAG, "Successfully took persistable URI permission for: $uri with flags: $modeFlags")
            true
        } catch (e: SecurityException) {
            // Some URI providers or non-SAF intents do not provide persistable flags
            Log.w(TAG, "SecurityException taking persistable URI permission for $uri: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed taking persistable URI permission for $uri", e)
            false
        }
    }

    /**
     * Release persistent URI permission from Android Storage Access Framework (SAF)
     * when asset is permanently deleted from Kyaw Gyi AI vault.
     */
    fun releasePersistableUriPermissionSafely(
        uri: Uri,
        modeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
    ): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.releasePersistableUriPermission(uri, modeFlags)
            Log.d(TAG, "Successfully released persistable URI permission for: $uri")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not release persistable URI permission for $uri: ${e.message}")
            false
        }
    }

    /**
     * Check if a content URI currently has an active persistent permission grant.
     */
    fun hasPersistedUriPermission(uri: Uri): Boolean {
        return try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the count of currently persisted URI permissions.
     */
    fun getPersistedUriCount(): Int {
        return try {
            context.contentResolver.persistedUriPermissions.size
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Resilient stream reader:
     * 1. Attempts to open stream directly from the original persistent SAF URI.
     * 2. If the URI is unavailable, smoothly falls back to the sandboxed vault file copy.
     */
    fun openAssetInputStream(asset: IngestedAssetEntity): InputStream? {
        // 1. Try reading directly from original persistent URI
        try {
            val parsed = Uri.parse(asset.fileUri)
            val stream = context.contentResolver.openInputStream(parsed)
            if (stream != null) return stream
        } catch (e: Exception) {
            Log.w(TAG, "Could not open original URI ${asset.fileUri}, falling back to sandboxed vault: ${e.message}")
        }

        // 2. Fallback to sandboxed local file
        return try {
            val localFile = File(asset.storedRelativePath)
            if (localFile.exists()) {
                localFile.inputStream()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Could not open local sandboxed file ${asset.storedRelativePath}", e)
            null
        }
    }

    /**
     * Categorize MIME and file extensions into safe target categories
     */
    fun determineCategory(fileName: String, mimeType: String?): AssetCategory {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType?.lowercase(Locale.ROOT) ?: ""

        return when {
            lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") || lowerName.endsWith(".svg") -> {
                AssetCategory.IMAGE
            }
            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                    lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") || lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") -> {
                AssetCategory.AUDIO
            }
            lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                    lowerName.endsWith(".webm") || lowerName.endsWith(".3gp") || lowerName.endsWith(".mov") -> {
                AssetCategory.VIDEO
            }
            lowerMime.contains("pdf") || lowerName.endsWith(".pdf") ||
                    lowerName.endsWith(".doc") || lowerName.endsWith(".docx") ||
                    lowerName.endsWith(".txt") || lowerName.endsWith(".md") ||
                    lowerName.endsWith(".rtf") -> {
                AssetCategory.DOCUMENT
            }
            lowerMime.contains("json") || lowerMime.contains("xml") || lowerMime.contains("yaml") ||
                    lowerName.endsWith(".json") || lowerName.endsWith(".xml") ||
                    lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") ||
                    lowerName.endsWith(".csv") || lowerName.endsWith(".kt") ||
                    lowerName.endsWith(".java") || lowerName.endsWith(".py") ||
                    lowerName.endsWith(".html") || lowerName.endsWith(".css") || lowerName.endsWith(".js") -> {
                AssetCategory.CODE_DATA
            }
            lowerName.endsWith(".zip") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz") || lowerName.endsWith(".rar") -> {
                AssetCategory.ARCHIVE
            }
            else -> AssetCategory.OTHER
        }
    }

    /**
     * Ingest Uri into sandboxed repository with deduplication and metadata extraction
     */
    suspend fun ingestUri(uri: Uri, source: String = "USER_UPLOAD"): IngestionResult {
        return try {
            val contentResolver = context.contentResolver

            // 0. Storage Access Framework - Take Persistent URI Grant across device reboots
            val isPermissionPersisted = takePersistableUriPermissionSafely(uri)

            val resolvedName = queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            val category = determineCategory(resolvedName, mimeType)

            // 1. Calculate SHA-256 hash streamingly
            val sha256 = calculateStreamSha256(uri)
            if (sha256.isNullOrBlank()) {
                return IngestionResult(
                    isSuccess = false,
                    messageMM = "ဖိုင်ဖတ်ရှုရန် မအောင်မြင်ပါ (SHA256 တွက်ချက်မရပါ)",
                    originalFileName = resolvedName
                )
            }

            // 2. Check for existing duplicate in DB
            val db = com.example.data.AppDatabase.getDatabase(context)
            val existing = db.ingestedAssetDao().findByHash(sha256)
            if (existing != null) {
                return IngestionResult(
                    isSuccess = true,
                    asset = existing,
                    isDuplicate = true,
                    messageMM = "ဖိုင် '${existing.fileName}' သည် စနစ်ထဲတွင် သိမ်းဆည်းပြီးသား ဖြစ်ပါသည် (ထပ်တူ ဖိုင်တွေ့ရှိပါသည်)",
                    originalFileName = resolvedName
                )
            }

            // 3. Prepare target destination directory
            val targetFolder = File(context.filesDir, "ingested_vault/${category.folderName}")
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }

            val sanitizedBase = resolvedName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(targetFolder, "${System.currentTimeMillis()}_$sanitizedBase")

            // 4. Copy stream safely into sandbox
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return IngestionResult(
                isSuccess = false,
                messageMM = "ဖိုင်အချက်အလက် ကူးယူခွင့် မရရှိပါခင်ဗျာ",
                originalFileName = resolvedName
            )

            val fileSize = targetFile.length()

            // 5. Extract metadata & generate local keyword index
            val (metadataObj, summaryText, keywords) = extractOfflineMetadata(targetFile, category, mimeType)
            metadataObj.put("original_uri", uri.toString())
            metadataObj.put("is_permission_persisted", isPermissionPersisted)
            metadataObj.put("sandboxed_vault_path", targetFile.absolutePath)

            val asset = IngestedAssetEntity(
                fileUri = uri.toString(),
                fileName = resolvedName,
                mimeType = mimeType,
                category = category.name,
                sizeBytes = fileSize,
                sha256Hash = sha256,
                storedRelativePath = targetFile.absolutePath,
                originalSource = source,
                aliasName = resolvedName.substringBeforeLast("."),
                metadataJson = metadataObj.toString(),
                extractedSummary = summaryText,
                indexedKeywords = keywords,
                timestamp = System.currentTimeMillis()
            )

            val id = db.ingestedAssetDao().insertAsset(asset)
            val savedAsset = asset.copy(id = id)

            IngestionResult(
                isSuccess = true,
                asset = savedAsset,
                isDuplicate = false,
                messageMM = "'$resolvedName' ကို ${category.labelMM} အဖြစ် စနစ်တကျ လက်ခံသိမ်းဆည်းပြီး အညွှန်းပြုစုပြီးပါပြီ",
                originalFileName = resolvedName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ingest URI: $uri", e)
            IngestionResult(
                isSuccess = false,
                messageMM = "ဖိုင်သိမ်းဆည်းရာတွင် အမှားဖြစ်ပွားပါသည်: ${e.localizedMessage}",
                originalFileName = uri.lastPathSegment ?: "unknown"
            )
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment

        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateStreamSha256(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Offline local metadata extraction without network dependencies
     */
    private fun extractOfflineMetadata(
        file: File,
        category: AssetCategory,
        mimeType: String
    ): Triple<JSONObject, String, String> {
        val meta = JSONObject()
        meta.put("file_size_bytes", file.length())
        meta.put("mime_type", mimeType)
        meta.put("absolute_path", file.absolutePath)

        val keywordsList = mutableListOf(file.name, category.name, category.labelMM)
        var summary = "${category.labelMM}: ${file.name} (${file.length() / 1024} KB)"

        when (category) {
            AssetCategory.IMAGE -> {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    meta.put("width", options.outWidth)
                    meta.put("height", options.outHeight)
                    summary = "ဓာတ်ပုံ ဖိုင်အရွယ်အစား: ${options.outWidth}x${options.outHeight} pixels"
                    keywordsList.add("${options.outWidth}x${options.outHeight}")
                    keywordsList.add("photo")
                    keywordsList.add("image")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract image bounds", e)
                }
            }

            AssetCategory.AUDIO, AssetCategory.VIDEO -> {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

                    val durationMs = durationStr?.toLongOrNull() ?: 0L
                    meta.put("duration_ms", durationMs)
                    if (artist != null) {
                        meta.put("artist", artist)
                        keywordsList.add(artist)
                    }
                    if (title != null) {
                        meta.put("title", title)
                        keywordsList.add(title)
                    }
                    if (album != null) {
                        meta.put("album", album)
                        keywordsList.add(album)
                    }
                    retriever.release()

                    val seconds = (durationMs / 1000) % 60
                    val minutes = (durationMs / (1000 * 60))
                    val durationFmt = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    summary = "မီဒီယာ ဖိုင်ကြာချိန်: $durationFmt, အနုပညာရှင်: ${artist ?: "မသိရှိ"}, အမည်: ${title ?: file.name}"
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract media metadata", e)
                }
            }

            AssetCategory.DOCUMENT, AssetCategory.CODE_DATA -> {
                try {
                    if (file.length() <= MAX_READABLE_TEXT_BYTES) {
                        val textContent = file.readText(Charsets.UTF_8)
                        val lines = textContent.lines()
                        meta.put("line_count", lines.size)
                        meta.put("char_count", textContent.length)

                        // Sample the first 400 chars as summary
                        val snippet = textContent.take(400).replace("\n", " ").trim()
                        summary = "စာကြောင်းရေ: ${lines.size} ကြောင်း။ အစမ်းအကျဉ်း: $snippet"

                        // Extract distinct words for indexing
                        val words = textContent.split("\\s+".toRegex())
                            .map { it.trim().lowercase(Locale.ROOT) }
                            .filter { it.length in 3..25 }
                            .distinct()
                            .take(50)
                        keywordsList.addAll(words)
                    } else {
                        meta.put("line_count", "LARGE_FILE")
                        summary = "ဖိုင်ဆိုဒ်ကြီးမားသော စာရွက်စာတမ်း/ဒေတာဖိုင် (${file.length() / 1024} KB)"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract document text snippet", e)
                }
            }

            else -> {
                summary = "အထွေထွေ ဖိုင်: ${file.name} (${file.length() / 1024} KB)"
            }
        }

        val allKeywords = keywordsList.joinToString(" ")
        return Triple(meta, summary, allKeywords)
    }
}
