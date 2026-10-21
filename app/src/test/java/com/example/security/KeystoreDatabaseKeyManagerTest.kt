package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.FailureMemoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Arrays

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreDatabaseKeyManagerTest {

    private lateinit var context: Context
    private lateinit var keyManager: KeystoreDatabaseKeyManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        keyManager = KeystoreDatabaseKeyManager.getInstance(context)
        keyManager.clearKeyMaterial()
    }

    @Test
    fun testKeystoreMasterKeyGeneration() {
        val masterKey = keyManager.getOrCreateMasterKey()
        assertNotNull("Master key should not be null", masterKey)
        assertEquals("AES", masterKey.algorithm)
    }

    @Test
    fun testCustomPassphraseWrappingAndUnwrapping() {
        val originalPassphrase = "KyawGyiSecurePassphrase_2026_BurmeseAI_MasterKey!".toByteArray(Charsets.UTF_8)

        // Wrap using Keystore Master Key (AES-256-GCM)
        val wrappedResult = keyManager.wrapCustomPassphrase(originalPassphrase)
        assertNotNull(wrappedResult.wrappedPassphrase)
        assertNotNull(wrappedResult.iv)
        assertTrue("Ciphertext should differ from plaintext", !originalPassphrase.contentEquals(wrappedResult.wrappedPassphrase))

        // Unwrap using Keystore Master Key
        val unwrapped = keyManager.unwrapCustomPassphrase(wrappedResult.wrappedPassphrase, wrappedResult.iv)
        assertTrue("Unwrapped bytes must strictly match original passphrase", Arrays.equals(originalPassphrase, unwrapped))
        assertEquals(String(originalPassphrase, Charsets.UTF_8), String(unwrapped, Charsets.UTF_8))
    }

    @Test
    fun testDatabasePassphraseGenerationAndPersistence() {
        // First retrieval generates and persists wrapped passphrase
        val passphrase1 = keyManager.getOrGenerateDatabasePassphrase()
        assertNotNull(passphrase1)
        assertEquals(32, passphrase1.size) // 256 bits

        // Second retrieval should unwrap the exact same persisted passphrase
        val passphrase2 = keyManager.getOrGenerateDatabasePassphrase()
        assertNotNull(passphrase2)
        assertTrue("Subsequent retrieval must return identical decrypted passphrase", Arrays.equals(passphrase1, passphrase2))

        val passphraseStr = keyManager.getDatabasePassphraseString()
        assertTrue("Base64 string of passphrase must be non-empty", passphraseStr.isNotEmpty())
    }

    @Test
    fun testFailureMemoryEngineKeystoreIntegration() {
        FailureMemoryEngine.initialize(context)

        val status = FailureMemoryEngine.getKeystoreSecurityStatus(context)
        assertEquals("AES-256-GCM", status["algorithm"])
        assertEquals("AndroidKeyStore", status["provider"])
        assertEquals(true, status["isWrappedKeyConfigured"])
        assertEquals(256, status["passphraseBitLength"])

        val dbPassphrase = FailureMemoryEngine.getDatabasePassphrase(context)
        assertNotNull(dbPassphrase)
        assertEquals(32, dbPassphrase.size)
    }
}
