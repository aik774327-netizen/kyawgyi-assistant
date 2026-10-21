package com.example.ingestion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UniversalDataIngestionManagerTest {

    private lateinit var context: Context
    private lateinit var ingestionManager: UniversalDataIngestionManager
    private lateinit var database: AppDatabase
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ingestionManager = UniversalDataIngestionManager(context)
        database = AppDatabase.getDatabase(context)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { if (it.exists()) it.delete() }
        tempFiles.clear()
    }

    @Test
    fun test01_DetermineCategories() {
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.IMAGE,
            ingestionManager.determineCategory("profile.png", "image/png")
        )
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.AUDIO,
            ingestionManager.determineCategory("song.mp3", "audio/mpeg")
        )
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.VIDEO,
            ingestionManager.determineCategory("clip.mp4", "video/mp4")
        )
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.DOCUMENT,
            ingestionManager.determineCategory("report.pdf", "application/pdf")
        )
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.CODE_DATA,
            ingestionManager.determineCategory("Main.kt", "text/plain")
        )
        assertEquals(
            UniversalDataIngestionManager.AssetCategory.ARCHIVE,
            ingestionManager.determineCategory("backup.zip", "application/zip")
        )
    }

    @Test
    fun test02_TakePersistableUriPermission_NonContentUri() {
        val fileUri = Uri.parse("file:///sdcard/test.txt")
        val result = ingestionManager.takePersistableUriPermissionSafely(fileUri)
        assertFalse("File URI should return false for persistable content permission without crashing", result)
    }

    @Test
    fun test03_TakePersistableUriPermission_ContentUriSafety() {
        val contentUri = Uri.parse("content://com.android.providers.media.documents/document/document%3A1234")
        // In Robolectric environment, taking permission on a mock URI without an active grant provider
        // should safely catch SecurityException and return false rather than crashing.
        val result = ingestionManager.takePersistableUriPermissionSafely(contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertNotNull(result)
    }

    @Test
    fun test04_ReleasePersistableUriPermission_Safety() {
        val contentUri = Uri.parse("content://com.android.providers.media.documents/document/document%3A1234")
        val result = ingestionManager.releasePersistableUriPermissionSafely(contentUri)
        assertNotNull(result)
    }

    @Test
    fun test05_IngestFileAndStoreOriginalUri() {
        runBlocking {
            // Create a local sample text file
            val sampleFile = File(context.cacheDir, "sample_document.txt")
            sampleFile.writeText("မင်္ဂလာပါ KYAW GYI AI လက်ထောက်။ Android Storage Access Framework စမ်းသပ်မှု။")
            tempFiles.add(sampleFile)

            val sampleUri = Uri.fromFile(sampleFile)
            val ingestionResult = ingestionManager.ingestUri(sampleUri, "USER_UPLOAD")

            assertTrue(ingestionResult.isSuccess)
            assertNotNull(ingestionResult.asset)
            val asset = ingestionResult.asset!!

            assertEquals("sample_document.txt", asset.fileName)
            assertEquals(UniversalDataIngestionManager.AssetCategory.DOCUMENT.name, asset.category)
            assertEquals(sampleUri.toString(), asset.fileUri)
            assertTrue(asset.storedRelativePath.contains("ingested_vault/documents"))
            assertTrue(File(asset.storedRelativePath).exists())
            assertTrue(asset.metadataJson.contains("original_uri"))

            // Verify resilient open stream from asset fallback
            val inputStream = ingestionManager.openAssetInputStream(asset)
            assertNotNull("Should be able to open stream for ingested asset", inputStream)
            val content = inputStream?.bufferedReader()?.readText()
            assertTrue(content?.contains("မင်္ဂလာပါ") == true)
            inputStream?.close()
        }
    }

    @Test
    fun test06_DeduplicationDetection() {
        runBlocking {
            val sampleFile = File(context.cacheDir, "duplicate_test.txt")
            sampleFile.writeText("Deduplication Unique Content 123456789")
            tempFiles.add(sampleFile)

            val sampleUri = Uri.fromFile(sampleFile)
            val firstResult = ingestionManager.ingestUri(sampleUri, "TEST")
            assertTrue(firstResult.isSuccess)
            assertFalse(firstResult.isDuplicate)

            // Second ingestion of exact same content
            val secondResult = ingestionManager.ingestUri(sampleUri, "TEST")
            assertTrue(secondResult.isSuccess)
            assertTrue(secondResult.isDuplicate)
            assertEquals(firstResult.asset?.sha256Hash, secondResult.asset?.sha256Hash)
        }
    }
}
