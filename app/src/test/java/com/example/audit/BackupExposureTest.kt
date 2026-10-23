package com.example.audit

import com.example.ocr.FailureMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * ISSUE-043 — with allowBackup=true and template backup rules, the
 * error-learning memory, the Keystore-wrapped vault prefs and the Room
 * command-log database were all eligible for cloud backup / device
 * transfer. These tests pin the exclusions in both rule files and the
 * honest database-encryption status.
 */
@RunWith(RobolectricTestRunner::class)
class BackupExposureTest {

    private fun readRepoFile(name: String): String {
        val candidates = listOf(
            "src/main/res/xml/$name",
            "app/src/main/res/xml/$name",
            "../app/src/main/res/xml/$name"
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f.readText()
        }
        fail("res/xml/$name not found from user.dir=${System.getProperty("user.dir")}")
        return ""
    }

    private val sensitivePaths = listOf(
        "kyawgyi_error_learning_memory.xml",
        "kyaw_gyi_secure_vault_prefs.xml",
        "kyawgyi_keystore_vault.xml",
        "ai_angel_v25_db"
    )

    @Test
    fun t17_043_01_backupRules_excludeSensitiveStores() {
        val xml = readRepoFile("backup_rules.xml")
        for (path in sensitivePaths) {
            assertTrue("backup_rules.xml must exclude $path", xml.contains(path))
        }
        assertTrue(xml.contains("domain=\"sharedpref\""))
        assertTrue(xml.contains("domain=\"database\""))
        assertTrue(xml.contains("<full-backup-content>"))
    }

    @Test
    fun t17_043_02_dataExtractionRules_excludeInCloudBackupAndDeviceTransfer() {
        val xml = readRepoFile("data_extraction_rules.xml")
        assertTrue(xml.contains("<cloud-backup>"))
        assertTrue(xml.contains("<device-transfer>"))
        for (path in sensitivePaths) {
            val occurrences = xml.split(path).size - 1
            assertTrue(
                "$path must be excluded in BOTH sections (found $occurrences)",
                occurrences >= 2
            )
        }
    }

    @Test
    fun t17_043_03_securityStatus_reportsDatabaseUnencrypted_truthfully() {
        val status = FailureMemoryEngine.getKeystoreSecurityStatus(
            RuntimeEnvironment.getApplication()
        )
        // The wrapped Keystore key exists, but AppDatabase has no SQLCipher
        // factory: the status API must say so instead of implying encryption.
        assertEquals(false, status["isDatabaseEncrypted"])
        assertEquals("AES-256-GCM", status["algorithm"])
    }
}
