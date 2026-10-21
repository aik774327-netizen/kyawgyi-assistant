package com.example.security.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SecureKeyVault
 *
 * Provides enterprise-grade hardware/Keystore-backed encryption (AES-256 GCM)
 * for securely persisting sensitive API keys in the app sandbox.
 * Keys are never stored in plaintext and are masked in UI and diagnostic logs.
 */
/**
 * Thrown when the AndroidKeyStore master key cannot be created or retrieved.
 * The vault fails CLOSED in that case: before 2026-10-22 it silently fell
 * back to a deterministic key derived from a hardcoded seed string plus the
 * package name, which anyone could reproduce to decrypt stored API keys
 * (FAKE-12).
 */
class SecureStorageUnavailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class SecureKeyVault(
    private val context: Context,
    /**
     * Test seam only: when non-null, supplies the AES key directly instead of
     * the AndroidKeyStore (e.g. JVM/Robolectric environments without a working
     * AndroidKeyStore provider). Production code never passes this.
     */
    private val secretKeyProvider: (() -> SecretKey)? = null
) {

    companion object {
        private const val TAG = "SecureKeyVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "KyawGyiApiKeyMasterKey"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PREFS_NAME = "kyaw_gyi_secure_vault_prefs"

        /**
         * Safely masks an API key for logs and UI display (e.g., "AIzaSy...4xQ9").
         */
        fun maskKey(rawKey: String): String {
            if (rawKey.isBlank()) return "Not Configured"
            if (rawKey.length <= 8) return "••••••••"
            val prefix = rawKey.take(6)
            val suffix = rawKey.takeLast(4)
            return "$prefix••••••••$suffix"
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
                Log.d(TAG, "Hardware master key generated successfully in AndroidKeyStore")
            }
        } catch (e: Exception) {
            // No fallback key exists anymore (FAKE-12): getSecretKey() will
            // throw SecureStorageUnavailableException when the key is needed.
            Log.w(TAG, "AndroidKeyStore init failed; secure storage unavailable: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey {
        secretKeyProvider?.let { return it() }
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry?.secretKey != null) return entry.secretKey
        } catch (e: Exception) {
            throw SecureStorageUnavailableException(
                "AndroidKeyStore unavailable; secure storage cannot be used", e
            )
        }
        // Key missing (e.g. first run raced with init failure): try once to create it.
        ensureMasterKey()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry?.secretKey != null) return entry.secretKey
        } catch (e: Exception) {
            throw SecureStorageUnavailableException(
                "AndroidKeyStore unavailable; secure storage cannot be used", e
            )
        }
        throw SecureStorageUnavailableException(
            "AndroidKeyStore master key could not be created or retrieved; secure storage unavailable"
        )
    }

    /**
     * Encrypts plaintext string using AES-256 GCM.
     *
     * Throws [SecureStorageUnavailableException] when encryption cannot be
     * performed: silently returning "" (the pre-2026-10-22 behavior) made
     * callers believe a key had been stored when nothing was persisted.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            // Combined IV + CipherText encoded in Base64
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: SecureStorageUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw SecureStorageUnavailableException("Encryption failed: ${e.message}", e)
        }
    }

    /**
     * Decrypts AES-256 GCM encrypted ciphertext string.
     */
    fun decrypt(cipherPayload: String): String {
        if (cipherPayload.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherPayload, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: SecureStorageUnavailableException) {
            // Keystore itself is broken: surface it honestly instead of
            // pretending the stored value is simply absent.
            throw e
        } catch (e: Exception) {
            // Ciphertext cannot be authenticated/decrypted - e.g. data written
            // under the removed deterministic fallback key (FAKE-12). Treat as
            // unreadable/absent; the user must re-enter the API key.
            Log.e(TAG, "Decryption failed (stored data unreadable): ${e.message}")
            ""
        }
    }

    fun saveEncrypted(key: String, rawValue: String) {
        val encrypted = encrypt(rawValue)
        prefs.edit().putString(key, encrypted).apply()
    }

    fun getDecrypted(key: String, defaultValue: String = ""): String {
        val encrypted = prefs.getString(key, null) ?: return defaultValue
        val decrypted = decrypt(encrypted)
        return if (decrypted.isNotBlank()) decrypted else defaultValue
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun saveLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
