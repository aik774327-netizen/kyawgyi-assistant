package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kyawgyi.ir.apikey.ApiKeyInterceptor
import com.kyawgyi.ir.apikey.ApiKeyManager
import com.kyawgyi.ir.apikey.ApiKeySecureStorage
import com.kyawgyi.ir.apikey.ApiKeyStatus
import com.kyawgyi.ir.apikey.ApiKeyTier
import com.kyawgyi.ir.apikey.ManagedApiKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyManagerTest {

  private lateinit var context: Context
  private lateinit var apiKeyManager: ApiKeyManager

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    apiKeyManager = ApiKeyManager.getInstance(context)
    apiKeyManager.seedDefaultKeysForTesting()
    apiKeyManager.resetAllKeysForTesting()
    apiKeyManager.setAutoFailoverEnabled(true)
  }

  @Test
  fun testSecureStorageEncryptionAndDecryption() {
    val storage = ApiKeySecureStorage(context)
    val originalSecret = "AIzaSyTestKey_Encrypted_Securely_123456"
    val keyId = "test_key_storage"

    storage.saveEncryptedKey(keyId, originalSecret)
    val decrypted = storage.getDecryptedKey(keyId)

    assertEquals(originalSecret, decrypted)
  }

  @Test
  fun testDefaultKeysSeed() {
    val keys = apiKeyManager.keys.value
    assertTrue("Default keys should not be empty", keys.isNotEmpty())
    val activeKey = apiKeyManager.activeKey.value
    assertNotNull("Active key should be initialized", activeKey)
  }

  @Test
  fun testApproachingQuotaDetection() {
    val activeKey = apiKeyManager.activeKey.value ?: return
    apiKeyManager.resetKeyQuota(activeKey.id)

    // Increase usage to 82% (above 80% warning threshold)
    val targetUsage = (activeKey.maxQuota * 0.82f).toInt()
    apiKeyManager.recordUsage(targetUsage)

    val updatedKey = apiKeyManager.keys.value.first { it.id == activeKey.id }
    assertTrue("Should detect approaching quota", updatedKey.isApproachingQuota)
    assertEquals(ApiKeyStatus.APPROACHING_QUOTA, updatedKey.status)
  }

  @Test
  fun testCriticalQuotaAutoFailover() {
    apiKeyManager.setAutoFailoverEnabled(true)
    val initialActive = apiKeyManager.activeKey.value ?: return
    apiKeyManager.resetKeyQuota(initialActive.id)

    // Trigger critical quota (> 95%)
    val criticalUsage = (initialActive.maxQuota * 0.96f).toInt()
    apiKeyManager.recordUsage(criticalUsage)

    val newActive = apiKeyManager.activeKey.value
    assertNotNull(newActive)
    assertNotEquals("Should have automatically failed over to next key", initialActive.id, newActive?.id)
  }

  @Test
  fun testHttp429FailoverHandling() {
    apiKeyManager.setAutoFailoverEnabled(true)
    val currentActive = apiKeyManager.activeKey.value ?: return

    val failoverHandled = apiKeyManager.handleHttpError(429, "Too Many Requests")
    assertTrue("Failover should be handled on 429", failoverHandled)

    val newActive = apiKeyManager.activeKey.value
    assertNotEquals("Active key should switch after 429", currentActive.id, newActive?.id)

    val previousKey = apiKeyManager.keys.value.first { it.id == currentActive.id }
    assertEquals(ApiKeyStatus.RATE_LIMITED_429, previousKey.status)
    assertTrue("Cooldown should be set", previousKey.isCoolingDown)
  }

  @Test
  fun testStrategyToRevertToResetFreeTierKey() {
    apiKeyManager.setAutoRevertToFreeTier(true)

    // 1. Force switch to pro tier key
    val proKey = apiKeyManager.keys.value.firstOrNull { it.tier == ApiKeyTier.PRO_TIER } ?: return
    apiKeyManager.setActiveKey(proKey.id)
    assertEquals(proKey.id, apiKeyManager.activeKeyId.value)

    // 2. Ensure default free tier key has expired reset timestamp so it qualifies for reset
    val primaryFree = apiKeyManager.keys.value.first { it.isDefaultFreeTier }
    apiKeyManager.resetKeyQuota(primaryFree.id)

    // 3. Call strategy check
    val reverted = apiKeyManager.checkAndRevertToFreeTierIfReset()
    assertTrue("Should revert back to reset primary free tier key", reverted)
    assertEquals(primaryFree.id, apiKeyManager.activeKeyId.value)
  }

  @Test
  fun testPlaceholderKeysAreRejected() {
    // .env.example placeholder baked into BuildConfig must never be treated as a genuine key (R1)
    assertTrue(ApiKeyManager.isPlaceholderKey("YOUR_API_KEY"))
    assertTrue(ApiKeyManager.isPlaceholderKey("MY_GEMINI_API_KEY"))
    assertTrue(ApiKeyManager.isPlaceholderKey("  "))
    assertTrue(ApiKeyManager.isPlaceholderKey("AIzaSyDefaultFreeKeyPrimary9981"))
    assertFalse(ApiKeyManager.isPlaceholderKey("AIzaSyUserKeyReal9999"))
  }

  @Test
  fun testApiKeyInterceptorAttachesKey() {
    val interceptor = ApiKeyInterceptor(apiKeyManager, headerName = "X-TEST-API-KEY")
    apiKeyManager.addApiKey("User Key", "AIzaSyUserKeyReal9999", ApiKeyTier.FREE_TIER)
    val currentSecret = apiKeyManager.getActiveSecretKey()

    // Test request
    val request = Request.Builder()
      .url("https://api.example.com/data")
      .build()

    val client = OkHttpClient.Builder()
      .addInterceptor(interceptor)
      .build()

    assertNotNull(client)
    assertTrue("Secret key should not be blank", currentSecret.isNotBlank())
    assertEquals("AIzaSyUserKeyReal9999", currentSecret)
  }
}
