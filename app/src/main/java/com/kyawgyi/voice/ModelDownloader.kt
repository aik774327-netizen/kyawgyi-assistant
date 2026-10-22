package com.kyawgyi.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Observable state for a model installation operation.
 * progress is in the inclusive range 0..100.
 */
sealed interface ModelDownloadStatus {
    data object Idle : ModelDownloadStatus
    data object Checking : ModelDownloadStatus
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long?,
    ) : ModelDownloadStatus
    data object Verifying : ModelDownloadStatus
    data object Extracting : ModelDownloadStatus
    data object Installing : ModelDownloadStatus
    data class Ready(val directory: String) : ModelDownloadStatus
    data class Failed(val message: String) : ModelDownloadStatus
}

/**
 * Immutable configuration for one Sherpa-ONNX model package.
 */
data class SherpaModelPackage(
    val httpsZipUrl: String,
    val sha256: String,
    val assetDirectory: String = "models/myanmar",
    val modelDirectoryName: String = "myanmar",
)

/**
 * ModelDownloader (Layer 2 On-Demand Speech Model Delivery)
 *
 * Downloads, verifies, and extracts on-device Myanmar speech models:
 * - Sherpa-ONNX Zipformer Burmese Acoustic Models (.onnx)
 * - Vosk Small Myanmar Offline ASR Models
 *
 * Provides real-time download progress tracking via StateFlow,
 * thread-safe ZipSlip-resistant extraction, and cleanup.
 */
class ModelDownloader(
    private val context: Context,
    val modelManager: ModelManager = ModelManager(context)
) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000

        // Default public repository URL for Myanmar speech acoustic assets
        const val DEFAULT_SHERPA_MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-myanmar-2024.zip"

        @Volatile
        private var instance: ModelDownloader? = null

        fun getInstance(context: Context): ModelDownloader {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloader(context.applicationContext).also { instance = it }
            }
        }
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
        data class Extracting(val currentEntry: String) : DownloadState()
        data class Completed(val targetDirectory: File, val message: String) : DownloadState()
        data class Failed(val errorReason: String) : DownloadState()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _status = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.Idle)
    val status: StateFlow<ModelDownloadStatus> = _status.asStateFlow()

    val modelsRoot: File
        get() = File(context.filesDir, "models")

    private val requiredSherpaFiles = listOf("encoder.onnx", "decoder.onnx", "joiner.onnx", "tokens.txt")

    fun modelDirectory(packageConfig: SherpaModelPackage): File =
        File(modelsRoot, packageConfig.modelDirectoryName)

    fun isReady(modelDirectoryName: String = "myanmar"): Boolean {
        val dir = File(modelsRoot, modelDirectoryName)
        return dir.isDirectory && requiredSherpaFiles.all { File(dir, it).let { f -> f.isFile && f.length() > 0L } }
    }

    suspend fun ensureInstalled(
        packageConfig: SherpaModelPackage,
        useAssets: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        val target = modelDirectory(packageConfig)
        _status.value = ModelDownloadStatus.Checking
        if (isReady(packageConfig.modelDirectoryName)) {
            _status.value = ModelDownloadStatus.Ready(target.absolutePath)
            return@withContext target
        }
        try {
            if (useAssets) {
                val installed = modelManager.ensureSherpaFromAssets(packageConfig.assetDirectory)
                _status.value = ModelDownloadStatus.Ready(installed.absolutePath)
                installed
            } else {
                require(packageConfig.httpsZipUrl.startsWith("https://")) { "Only HTTPS model URLs are permitted" }
                require(packageConfig.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
                    "Expected a 64-character hexadecimal SHA-256"
                }
                val installed = modelManager.ensureSherpaModel(
                    remoteZipUrl = packageConfig.httpsZipUrl,
                    expectedSha256 = packageConfig.sha256,
                    assetRoot = packageConfig.assetDirectory
                )
                _status.value = ModelDownloadStatus.Ready(installed.absolutePath)
                installed
            }
        } catch (cancelled: CancellationException) {
            _status.value = ModelDownloadStatus.Failed("Model installation cancelled")
            throw cancelled
        } catch (t: Throwable) {
            _status.value = ModelDownloadStatus.Failed(t.message ?: "Sherpa model installation failed")
            throw t
        }
    }

    suspend fun ensureFromAssets(
        assetDirectory: String = "models/myanmar",
        modelDirectoryName: String = "myanmar",
    ): File = ensureInstalled(
        packageConfig = SherpaModelPackage(
            httpsZipUrl = "",
            sha256 = "",
            assetDirectory = assetDirectory,
            modelDirectoryName = modelDirectoryName,
        ),
        useAssets = true,
    )

    suspend fun ensureFromUrl(
        httpsZipUrl: String,
        expectedSha256: String,
        modelDirectoryName: String = "myanmar",
    ): File = ensureInstalled(
        packageConfig = SherpaModelPackage(
            httpsZipUrl = httpsZipUrl,
            sha256 = expectedSha256,
            modelDirectoryName = modelDirectoryName,
        ),
        useAssets = false,
    )

    private val isDownloading = AtomicBoolean(false)
    private var downloadJob: Job? = null

    /**
     * Downloads and extracts speech model package from remote URL to local internal storage.
     */
    fun startDownload(
        sourceUrl: String = DEFAULT_SHERPA_MODEL_URL,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (isDownloading.get()) {
            Log.w(TAG, "Download is already in progress.")
            return
        }

        downloadJob = scope.launch {
            val success = downloadAndExtractInternal(sourceUrl)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }

    private suspend fun downloadAndExtractInternal(sourceUrl: String): Boolean = mutex.withLock {
        isDownloading.set(true)
        val targetDir = File(context.filesDir, "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val tempZipFile = File(context.cacheDir, "model_temp_${System.currentTimeMillis()}.zip")
        var connection: HttpURLConnection? = null

        try {
            Log.i(TAG, "Connecting to model download source: $sourceUrl")
            var downloadedFromRemote = false
            var remoteFailureReason: String? = null

            try {
                val url = URL(sourceUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }

                connection.connect()
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val totalBytes = connection.contentLengthLong
                    Log.i(TAG, "Starting remote model download, total content size: $totalBytes bytes")

                    connection.inputStream.use { input ->
                        FileOutputStream(tempZipFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastPercentReported = -1

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isDownloading.get()) {
                                    throw CancellationException("Download cancelled by user.")
                                }

                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead

                                val percent = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else -1
                                if (percent != lastPercentReported) {
                                    lastPercentReported = percent
                                    _downloadState.value = DownloadState.Downloading(percent, totalRead, totalBytes)
                                }
                            }
                            output.flush()
                        }
                    }
                    downloadedFromRemote = tempZipFile.exists() && tempZipFile.length() > 0L
                } else {
                    remoteFailureReason = "HTTP $responseCode"
                    Log.i(
                        TAG,
                        "Remote model mirror returned HTTP $responseCode; download failed (no bundled fallback model exists)."
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (netEx: Exception) {
                remoteFailureReason = netEx.message ?: netEx.javaClass.simpleName
                Log.i(
                    TAG,
                    "Remote model mirror unreachable (${netEx.message}); download failed (no bundled fallback model exists)."
                )
            }

            if (!downloadedFromRemote) {
                // ISSUE-017: report the failure honestly. There is no bundled
                // offline model in this build, and a model archive must never
                // be fabricated locally to disguise a failed download.
                val reason = remoteFailureReason ?: "unknown error"
                val failMsg = "မော်ဒယ် ရယူရာတွင် အမှားဖြစ်ပေါ်ပါသည်: $reason"
                Log.w(TAG, "Model download failed: $reason")
                _downloadState.value = DownloadState.Failed(failMsg)
                return false
            }

            Log.i(TAG, "Model archive ready. Extracting archive to ${targetDir.absolutePath}...")

            // 2. Extract ZIP archive safely (ZipSlip Mitigation)
            extractZipSafely(tempZipFile, targetDir)

            // 3. Auto-select best installed offline engine in SttEngineManager
            SttEngineManager.getInstance(context).ensureHealthyEngineSelected()

            val successMsg = "မော်ဒယ်ဖိုင်များကို စက်တွင်းသို့ အောင်မြင်စွာ ဒေါင်းလုဒ်ရယူပြီးစီးပါပြီခင်ဗျာ။"
            Log.i(TAG, "Model extraction completed successfully.")
            _downloadState.value = DownloadState.Completed(targetDir, successMsg)
            return true

        } catch (ce: CancellationException) {
            Log.w(TAG, "Download was cancelled.")
            _downloadState.value = DownloadState.Failed("ဒေါင်းလုဒ် ရယူခြင်းကို ပယ်ဖျက်လိုက်ပါပြီ။")
            return false
        } catch (e: Exception) {
            val errorMsg = "မော်ဒယ် ရယူရာတွင် အမှားဖြစ်ပေါ်ပါသည်: ${e.message}"
            Log.w(TAG, errorMsg)
            _downloadState.value = DownloadState.Failed(errorMsg)
            return false
        } finally {
            isDownloading.set(false)
            connection?.disconnect()
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    /**
     * Extracts zip file ensuring paths do not escape target directory (ZipSlip vulnerability prevention).
     */
    private fun extractZipSafely(zipFile: File, destDir: File) {
        val destCanonicalPath = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalDestinationPath = newFile.canonicalPath

                // ZipSlip Security Guard
                if (!canonicalDestinationPath.startsWith(destCanonicalPath + File.separator) &&
                    canonicalDestinationPath != destCanonicalPath
                ) {
                    throw SecurityException("Zip traversal path detected in zip entry: ${entry.name}")
                }

                _downloadState.value = DownloadState.Extracting(entry.name)

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) {
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun cancelDownload() {
        if (isDownloading.getAndSet(false)) {
            Log.i(TAG, "Cancelling active model download job...")
            downloadJob?.cancel()
            downloadJob = null
            _downloadState.value = DownloadState.Idle
        }
    }

    fun isBusy(): Boolean = isDownloading.get()
}
