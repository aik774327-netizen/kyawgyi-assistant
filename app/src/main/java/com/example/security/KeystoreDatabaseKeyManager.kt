package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * KeystoreDatabaseKeyManager (Layer 5 - Hardware Security & SQLCipher Passphrase Encryption)
 *
 * Implements Android Keystore hardware-backed cryptographic protection:
 * 1. Generates & retrieves an AES-256 Master Key from "AndroidKeyStore" using
 *    KeyGenParameterSpec with PURPOSE_ENCRYPT and PURPOSE_DECRYPT.
 * 2. Dynamically generates high-entropy 256-bit passphrases for SQLCipher / FailureMemoryEngine.
 * 3. Wraps (encrypts) the database passphrase using AES-256-GCM authenticated encryption.
 * 4. Unwraps (decrypts) the passphrase on-demand in volatile memory only.
 * 5. Guarantees that the raw encryption key is NEVER written to disk in plaintext.
 */
class KeystoreDatabaseKeyManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreKeyManager"
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "KyawGyi_FailureMemory_SQLCipher_Key"
        private const val PREFS_NAME = "kyawgyi_keystore_vault"
        private const val PREF_WRAPPED_PASSPHRASE = "wrapped_db_passphrase"
        private const val PREF_GCM_IV = "gcm_iv"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_SIZE_BITS = 256
        private const val PASSPHRASE_BYTE_LENGTH = 32 // 256 bits

        @Volatile
        private var instance: KeystoreDatabaseKeyManager? = null

        fun getInstance(context: Context): KeystoreDatabaseKeyManager {
            return instance ?: synchronized(this) {
                instance ?: KeystoreDatabaseKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    data class WrappedKeyResult(
        val wrappedPassphrase: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as WrappedKeyResult
            return wrappedPassphrase.contentEquals(other.wrappedPassphrase) && iv.contentEquals(other.iv)
        }

        override fun hashCode(): Int {
            var result = wrappedPassphrase.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private var softwareFallbackKey: SecretKey? = null

    /**
     * Gets an existing AES-256 Master Key from AndroidKeyStore, or generates a new one.
     */
    @Synchronized
    fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry?.secretKey != null) {
                    return entry.secretKey
                }
            }

            // Key does not exist, generate new AES-256 hardware-backed key
            generateKeystoreMasterKey()
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore hardware generation failed (${e.message}). Initializing secure fallback key.")
            getOrCreateSoftwareFallbackKey()
        }
    }

    /**
     * Generates a 256-bit AES Master Key inside Android Keystore.
     */
    private fun generateKeystoreMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        val secretKey = keyGenerator.generateKey()
        Log.i(TAG, "Generated new 256-bit AES Master Key in Android Keystore with PURPOSE_ENCRYPT/DECRYPT.")
        return secretKey
    }

    /**
     * Software fallback key for testing or environments lacking Android KeyStore hardware provider.
     */
    @Synchronized
    private fun getOrCreateSoftwareFallbackKey(): SecretKey {
        softwareFallbackKey?.let { return it }
        val raw = ByteArray(32)
        secureRandom.nextBytes(raw)
        val key = SecretKeySpec(raw, "AES")
        softwareFallbackKey = key
        return key
    }

    /**
     * Retrieves the decrypted database passphrase, generating and wrapping a new one if necessary.
     */
    @Synchronized
    fun getOrGenerateDatabasePassphrase(): ByteArray {
        val wrappedBase64 = prefs.getString(PREF_WRAPPED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(PREF_GCM_IV, null)

        if (!wrappedBase64.isNullOrEmpty() && !ivBase64.isNullOrEmpty()) {
            try {
                val wrappedBytes = Base64.decode(wrappedBase64, Base64.NO_WRAP)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                return unwrapCustomPassphrase(wrappedBytes, iv)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unwrap existing database passphrase: ${e.message}. Re-generating...", e)
            }
        }

        // Generate high-entropy 256-bit database passphrase
        val freshPassphrase = ByteArray(PASSPHRASE_BYTE_LENGTH)
        secureRandom.nextBytes(freshPassphrase)

        val wrappedResult = wrapCustomPassphrase(freshPassphrase)
        prefs.edit()
            .putString(PREF_WRAPPED_PASSPHRASE, Base64.encodeToString(wrappedResult.wrappedPassphrase, Base64.NO_WRAP))
            .putString(PREF_GCM_IV, Base64.encodeToString(wrappedResult.iv, Base64.NO_WRAP))
            .apply()

        Log.i(TAG, "Generated fresh 256-bit database passphrase and securely wrapped it via Android Keystore.")
        return freshPassphrase
    }

    /**
     * Returns the decrypted database passphrase encoded as a string for SQLCipher open helper.
     */
    fun getDatabasePassphraseString(): String {
        val bytes = getOrGenerateDatabasePassphrase()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Wraps (encrypts) arbitrary passphrase data with the Keystore Master Key using AES-GCM.
     */
    fun wrapCustomPassphrase(passphrase: ByteArray): WrappedKeyResult {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val iv = cipher.iv
        val wrappedBytes = cipher.doFinal(passphrase)
        return WrappedKeyResult(wrappedBytes, iv)
    }

    /**
     * Unwraps (decrypts) wrapped passphrase data with the Keystore Master Key.
     */
    fun unwrapCustomPassphrase(wrappedBytes: ByteArray, iv: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

        return cipher.doFinal(wrappedBytes)
    }

    /**
     * Checks if the active Master Key is backed by hardware (StrongBox or TEE).
     */
    fun isHardwareKeyBacked(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) return false

            val key = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey ?: return false
            val factory = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE_PROVIDER)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX ||
                        keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Securely clears all stored wrapped key material from disk (e.g. on full security wipe).
     */
    @Synchronized
    fun clearKeyMaterial() {
        prefs.edit().clear().apply()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice clearing Keystore alias: ${e.message}")
        }
        softwareFallbackKey = null
        Log.i(TAG, "All Keystore wrapped database encryption material cleared.")
    }
}
