package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiKeyQuotaManagerTest {

    private lateinit var context: Context
    private lateinit var quotaManager: ApiKeyQuotaManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        quotaManager = ApiKeyQuotaManager.getRequiredInstance(context)
        runBlocking {
            quotaManager.resetAllQuotasAndCooldowns()
            // Remove any leftover keys from previous runs except DEFAULT_PRIMARY_ID
            val toRemove = quotaManager.poolState.value.keysList.filter { it.id != ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
            toRemove.forEach { quotaManager.removeKey(it.id) }
        }
    }

    @Test
    fun testSecureKeyVaultEncryptionAndMasking() {
        // Robolectric provides no "AndroidKeyStore" JCA provider, and the
        // vault now (correctly) fails closed without one - the deterministic
        // fallback key was removed as FAKE-12. Inject a real platform AES key
        // through the vault's test-only seam; production code never does this.
        val testKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val vault = SecureKeyVault(context) { testKey }
        val testSecret = "AIzaSySecretApiKey123456789"

        val encrypted = vault.encrypt(testSecret)
        assertNotEquals(testSecret, encrypted)

        val decrypted = vault.decrypt(encrypted)
        assertEquals(testSecret, decrypted)

        val masked = SecureKeyVault.maskKey(testSecret)
        assertTrue(masked.startsWith("AIzaSy"))
        assertTrue(masked.endsWith("6789"))
        assertFalse(masked.contains("SecretApiKey"))
    }

    @Test
    fun testMultiKeyRotationOn429Exhaustion() = runBlocking {
        // Disable default key by adding and targeting our explicit test keys
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyPrimaryFreeKey_AAA111",
            label = "Targeted Free Key",
            tier = ApiKeyTier.FREE_TIER,
            dailyLimit = 100
        )
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyBackupPaidKey_BBB222",
            label = "Targeted Paid Backup Key",
            tier = ApiKeyTier.PAID_TIER,
            dailyLimit = 5000
        )

        // Make default key exhausted so rotation selects our backup
        val defaultKey = quotaManager.poolState.value.keysList.firstOrNull { it.id == ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
        if (defaultKey != null) {
            quotaManager.removeKey(defaultKey.id)
        }

        // Make Targeted Free Key active
        val primaryId = quotaManager.poolState.value.keysList.first { it.label == "Targeted Free Key" }.id
        quotaManager.manuallySetActiveKey(primaryId)

        assertEquals("Targeted Free Key", quotaManager.poolState.value.activeKeyLabel)

        // Simulate an operation that encounters 429 on free key and rotates to paid backup key
        var callCount = 0
        val result = quotaManager.executeWithRetry<String>(
            operationName = "Test Simulated Rotation",
            maxRetries = 2
        ) { keyUsed ->
            callCount++
            if (keyUsed.contains("PrimaryFreeKey")) {
                throw IOException("HTTP 429: RESOURCE_EXHAUSTED Quota exceeded")
            }
            "Success with $keyUsed"
        }

        assertTrue(result.contains("Success with AIzaSyBackupPaidKey_BBB222"))
        assertEquals(2, callCount)

        // Verify active key has automatically switched to the healthy backup key
        val currentActive = quotaManager.poolState.value.activeKeyLabel
        assertEquals("Targeted Paid Backup Key", currentActive)

        // Verify free key is marked as EXHAUSTED_429
        val primaryKeyObj = quotaManager.poolState.value.keysList.first { it.id == primaryId }
        assertEquals(ApiKeyHealthStatus.EXHAUSTED_429, primaryKeyObj.status)
        assertTrue(primaryKeyObj.cooldownUntilTimestamp > System.currentTimeMillis())
    }

    @Test
    fun testQuotaApproachingWarningDetection() = runBlocking {
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyQuotaThresholdKey_999",
            label = "Threshold Test Key",
            tier = ApiKeyTier.CUSTOM,
            dailyLimit = 10
        )
        val keyObj = quotaManager.poolState.value.keysList.first { it.label == "Threshold Test Key" }
        quotaManager.manuallySetActiveKey(keyObj.id)

        // Simulate 8 requests to hit 80%
        repeat(8) {
            quotaManager.executeWithRetry { "ok" }
        }

        val updatedKey = quotaManager.poolState.value.keysList.first { it.id == keyObj.id }
        assertEquals(80, updatedKey.dailyUsagePercent)
        assertEquals(ApiKeyHealthStatus.NEAR_LIMIT_80, updatedKey.status)

        // Verify an alert was generated
        val alert = quotaManager.poolState.value.alerts.firstOrNull { it.keyLabel == "Threshold Test Key" }
        assertNotNull(alert)
        assertEquals(AlertSeverity.WARNING, alert?.severity)
    }

    @Test
    fun testCostOptimizationAutoRevertToFreeTier() = runBlocking {
        quotaManager.setAutoRevertToFree(true)

        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyFreeTierCostSaving_001",
            label = "Free Primary Key",
            tier = ApiKeyTier.FREE_TIER,
            dailyLimit = 1000
        )
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyPaidTierExpensive_002",
            label = "Paid Fallback Key",
            tier = ApiKeyTier.PAID_TIER,
            dailyLimit = 50000
        )

        val defaultKey = quotaManager.poolState.value.keysList.firstOrNull { it.id == ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
        if (defaultKey != null) {
            quotaManager.removeKey(defaultKey.id)
        }

        val paidKeyId = quotaManager.poolState.value.keysList.first { it.label == "Paid Fallback Key" }.id
        // Manually switch to paid key
        quotaManager.manuallySetActiveKey(paidKeyId)
        assertEquals("Paid Fallback Key", quotaManager.poolState.value.activeKeyLabel)

        // When executeWithRetry is called, since Free Tier key is available and auto-revert is enabled,
        // it ensures the optimal free key is used to save costs!
        quotaManager.executeWithRetry { key ->
            assertTrue(key.contains("FreeTierCostSaving"))
            "executed"
        }

        // Active key should now be Free Primary Key
        assertEquals("Free Primary Key", quotaManager.poolState.value.activeKeyLabel)
    }

    @Test
    fun testQuotaExhaustedErrorMessageReturnedToUser() = runBlocking {
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyTestOnlyKey_429",
            label = "Exhausted 429 Key",
            tier = ApiKeyTier.FREE_TIER,
            dailyLimit = 100
        )
        val defaultKey = quotaManager.poolState.value.keysList.firstOrNull { it.id == ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
        if (defaultKey != null) {
            quotaManager.removeKey(defaultKey.id)
        }
        val testKeyId = quotaManager.poolState.value.keysList.first { it.label == "Exhausted 429 Key" }.id
        quotaManager.manuallySetActiveKey(testKeyId)

        var caughtException: Exception? = null
        try {
            quotaManager.executeWithRetry<String>(
                operationName = "Simulate 429 Quota Exhausted",
                maxRetries = 1
            ) { _ ->
                throw com.example.data.GeminiQuotaExceededException("HTTP 429 RESOURCE_EXHAUSTED")
            }
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertTrue(com.example.data.GeminiApiService.isQuotaOrRateLimitException(caughtException!!))
        assertEquals(
            "Gemini API Quota ပြည့်သွားပါသဖြင့် ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် ကိုယ်ပိုင် API Key ထည့်ပါ",
            com.example.data.GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE
        )
    }

    @Test
    fun testEmptyPoolThrowsNoApiKeyConfiguredNotFake429() = runBlocking {
        // Empty the pool completely (add a temp key so the default key can be
        // removed, then remove the temp key as well).
        quotaManager.addOrUpdateKey(
            rawKey = "AIzaSyTempKeyForPoolEmptying00000000",
            label = "Temp Pool Key",
            tier = ApiKeyTier.FREE_TIER,
            dailyLimit = 100
        )
        val defaultKey = quotaManager.poolState.value.keysList.firstOrNull { it.id == ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
        if (defaultKey != null) {
            quotaManager.removeKey(defaultKey.id)
        }
        quotaManager.poolState.value.keysList.map { it.id }.forEach { quotaManager.removeKey(it) }
        assertTrue(quotaManager.poolState.value.keysList.isEmpty())

        var caughtException: Exception? = null
        try {
            quotaManager.executeWithRetry<String>(
                operationName = "No Key Configured",
                maxRetries = 1
            ) { _ -> "should never run" }
        } catch (e: Exception) {
            caughtException = e
        }

        // FAKE-07 regression: an empty pool must NOT fabricate an HTTP 429.
        assertNotNull(caughtException)
        assertTrue(caughtException is com.example.data.NoApiKeyConfiguredException)
        assertTrue(com.example.data.GeminiApiService.isNoApiKeyException(caughtException!!))
        assertFalse(com.example.data.GeminiApiService.isQuotaOrRateLimitException(caughtException!!))
    }
}
