package com.kyawgyi.voice

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ISSUE-017 regression tests (T15-017-*).
 *
 * A failed model download must be reported honestly as
 * [ModelDownloader.DownloadState.Failed] and must NOT fall back to a
 * fabricated, locally generated "offline model" archive.
 *
 * The failure is forced deterministically by pointing the downloader at a
 * loopback address with nothing listening (connection refused), so no real
 * network or DNS is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelDownloaderHonestFailureTest {

    private lateinit var context: Context
    private lateinit var downloader: ModelDownloader

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Deliberately a fresh instance, NOT getInstance(): the singleton would
        // keep the first test's Context (and its filesDir) and its terminal
        // state would leak into other test classes (e.g. the initial-state
        // assertions in VoiceSettingsAndDownloaderTest).
        downloader = ModelDownloader(context)
        // Start each case from a clean models directory so artifacts left by a
        // previous case (or a previous fabricated run) cannot mask the result.
        File(context.filesDir, "models").deleteRecursively()
    }

    /**
     * Runs one download against an unreachable loopback URL and waits for the
     * completion callback. Returns the Boolean delivered to onComplete.
     */
    private fun runFailingDownload(): Boolean {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Boolean?>(null)
        downloader.startDownload("https://127.0.0.1:1/model.zip") { success ->
            result.set(success)
            latch.countDown()
        }
        val deadlineMs = System.currentTimeMillis() + 90_000L
        while (System.currentTimeMillis() < deadlineMs) {
            // The callback is dispatched on Dispatchers.Main; keep the main
            // looper moving while the IO work proceeds on its own threads.
            shadowOf(Looper.getMainLooper()).idle()
            if (latch.await(100, TimeUnit.MILLISECONDS)) break
        }
        if (latch.count > 0L) {
            fail("Download did not complete within 90s (state=${downloader.downloadState.value})")
        }
        return result.get() ?: throw AssertionError("onComplete invoked without a result")
    }

    @Test(timeout = 120_000)
    fun t15_017_01_downloadFailureReportsFailedNotCompleted() {
        val success = runFailingDownload()

        assertFalse("A failed download must not report success", success)
        val state = downloader.downloadState.value
        assertTrue(
            "Expected DownloadState.Failed but was $state",
            state is ModelDownloader.DownloadState.Failed
        )
        assertTrue(
            "Failure must carry a real error message",
            (state as ModelDownloader.DownloadState.Failed).errorReason.isNotBlank()
        )
    }

    @Test(timeout = 120_000)
    fun t15_017_02_downloadFailureCreatesNoFabricatedModelArtifacts() {
        runFailingDownload()

        val modelsDir = File(context.filesDir, "models")
        val fabricatedNames = listOf(
            "sherpa-onnx-burmese.onnx",
            "myanmar/encoder.onnx",
            "myanmar/decoder.onnx",
            "myanmar/joiner.onnx",
            "vosk-model-my/am/final.mdl",
            "tokens.txt"
        )
        for (name in fabricatedNames) {
            assertFalse(
                "Fabricated artifact must not exist after a failed download: $name",
                File(modelsDir, name).exists()
            )
        }
        val modelFiles = if (modelsDir.exists()) {
            modelsDir.walkTopDown().filter { it.isFile && (it.extension == "onnx" || it.extension == "mdl") }.toList()
        } else {
            emptyList()
        }
        assertEquals(
            "No .onnx/.mdl model files may be provisioned on the failure path, found: $modelFiles",
            emptyList<File>(),
            modelFiles
        )
    }

    @Test
    fun t15_017_03_fabricationEntryPointIsGone() {
        val fabricationMethods = ModelDownloader::class.java.declaredMethods
            .map { it.name }
            .filter { it.contains("provisionBundled", ignoreCase = true) }
        assertTrue(
            "Fabricated bundled-model provisioning must not exist, found: $fabricationMethods",
            fabricationMethods.isEmpty()
        )
    }
}
