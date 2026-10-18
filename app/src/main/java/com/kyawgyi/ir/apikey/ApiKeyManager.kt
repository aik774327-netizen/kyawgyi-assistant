package com.kyawgyi.ir.apikey

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Robust Central API Key Management System.
 *
 * Features:
 * 1. Approaching Quota Exhaustion Detection (80% warning, 95% critical auto-switch).
 * 2. Multi-tier Automatic Switching / Failover (Free Tier -> Backup Tier -> Pro Tier).
 * 3. Strategy to Revert to Reset Free Quota Key (checks cooldown & periodic reset to save costs).
 * 4. AES-256-GCM Secure KeyStore Persistence.
 * 5. Rate-limiting (HTTP 429) & Quota Exceeded (HTTP 403) handling with Exponential Backoff.
 * 6. Pluggable into existing OkHttp / Retrofit clients.
 */
class ApiKeyManager private constructor(context: Context) {

  private val appContext = context.applicationContext
  private val secureStorage = ApiKeySecureStorage(appContext)
  private val prefs: SharedPreferences = appContext.getSharedPreferences(METADATA_PREFS, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

  private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  private val listType = Types.newParameterizedType(List::class.java, ManagedApiKey::class.java)
  private val jsonAdapter = moshi.adapter<List<ManagedApiKey>>(listType)

  private val _keys = MutableStateFlow<List<ManagedApiKey>>(emptyList())
  val keys: StateFlow<List<ManagedApiKey>> = _keys.asStateFlow()

  private val _activeKeyId = MutableStateFlow<String?>(null)
  val activeKeyId: StateFlow<String?> = _activeKeyId.asStateFlow()

  private val _activeKey = MutableStateFlow<ManagedApiKey?>(null)
  val activeKey: StateFlow<ManagedApiKey?> = _activeKey.asStateFlow()

  private fun syncActiveKey() {
    val activeId = _activeKeyId.value
    val keyList = _keys.value
    _activeKey.value = keyList.firstOrNull { it.id == activeId } ?: keyList.firstOrNull { it.canBeUsed }
  }

  private val _quotaAlerts = MutableSharedFlow<ApiKeyQuotaAlert>(extraBufferCapacity = 32)
  val quotaAlerts: SharedFlow<ApiKeyQuotaAlert> = _quotaAlerts.asSharedFlow()

  private val _rotationLogs = MutableStateFlow<List<ApiKeyRotationLog>>(emptyList())
  val rotationLogs: StateFlow<List<ApiKeyRotationLog>> = _rotationLogs.asStateFlow()

  private val _autoFailoverEnabled = MutableStateFlow(true)
  val autoFailoverEnabled: StateFlow<Boolean> = _autoFailoverEnabled.asStateFlow()

  private val _autoRevertToFreeTier = MutableStateFlow(true)
  val autoRevertToFreeTier: StateFlow<Boolean> = _autoRevertToFreeTier.asStateFlow()

  companion object {
    private const val METADATA_PREFS = "kyawgyi_api_key_metadata_prefs"
    private const val KEY_METADATA_JSON = "key_metadata_list_v1"
    private const val KEY_ACTIVE_ID = "active_key_id"
    private const val KEY_AUTO_FAILOVER = "auto_failover_enabled"
    private const val KEY_AUTO_REVERT = "auto_revert_free_tier"

    fun isSeedPlaceholderId(id: String): Boolean {
      return id == "free_tier_primary" || id == "free_tier_backup" || id == "pro_emergency_tier"
    }

    fun isPlaceholderKey(secret: String): Boolean {
      val trimmed = secret.trim()
      return trimmed.isBlank() ||
             trimmed == "MY_GEMINI_API_KEY" ||
             trimmed == "YOUR_API_KEY" ||
             trimmed.startsWith("AIzaSyDefaultFreeKey") ||
             trimmed.startsWith("AIzaSyBackupFreeTierKey") ||
             trimmed.startsWith("AIzaSyEnterpriseProKey")
    }

    @Volatile
    private var instance: ApiKeyManager? = null

    fun getInstance(context: Context): ApiKeyManager {
      return instance ?: synchronized(this) {
        instance ?: ApiKeyManager(context).also { instance = it }
      }
    }
  }

  init {
    loadPreferences()
    checkAndRevertToFreeTierIfReset()
    syncActiveKey()
  }

  fun setAutoFailoverEnabled(enabled: Boolean) {
    _autoFailoverEnabled.value = enabled
    prefs.edit().putBoolean(KEY_AUTO_FAILOVER, enabled).apply()
  }

  fun setAutoRevertToFreeTier(enabled: Boolean) {
    _autoRevertToFreeTier.value = enabled
    prefs.edit().putBoolean(KEY_AUTO_REVERT, enabled).apply()
  }

  /**
   * Loads persisted keys metadata.
   */
  private fun loadPreferences() {
    _autoFailoverEnabled.value = prefs.getBoolean(KEY_AUTO_FAILOVER, true)
    _autoRevertToFreeTier.value = prefs.getBoolean(KEY_AUTO_REVERT, true)
    val json = prefs.getString(KEY_METADATA_JSON, null)
    if (!json.isNullOrBlank()) {
      try {
        val loaded = jsonAdapter.fromJson(json)
        if (!loaded.isNullOrEmpty()) {
          // Filter out legacy dummy seed keys so they never persist or masquerade as real user keys
          _keys.value = loaded.filterNot { isSeedPlaceholderId(it.id) }
        }
      } catch (_: Exception) { }
    }
    _activeKeyId.value = prefs.getString(KEY_ACTIVE_ID, null)
  }

  private fun savePreferences() {
    syncActiveKey()
    try {
      val json = jsonAdapter.toJson(_keys.value)
      prefs.edit()
        .putString(KEY_METADATA_JSON, json)
        .putString(KEY_ACTIVE_ID, _activeKeyId.value)
        .apply()
    } catch (_: Exception) { }
  }

  /**
   * Pre-populates sample multi-tier keys for local testing environments.
   */
  fun seedDefaultKeysForTesting() {
    val key1Id = "free_tier_primary"
    val key2Id = "free_tier_backup"
    val key3Id = "pro_emergency_tier"

    secureStorage.saveEncryptedKey(key1Id, "AIzaSyDefaultFreeKeyPrimary9981")
    secureStorage.saveEncryptedKey(key2Id, "AIzaSyBackupFreeTierKey4412")
    secureStorage.saveEncryptedKey(key3Id, "AIzaSyEnterpriseProKey7723")

    val defaultList = listOf(
      ManagedApiKey(
        id = key1Id,
        label = "Primary Free Tier Key (မူလ အခမဲ့သော့)",
        maskedKey = "AIzaSy...9981",
        tier = ApiKeyTier.FREE_TIER,
        maxQuota = 1000,
        usedQuota = 120,
        warningThreshold = 0.80f,
        criticalThreshold = 0.95f,
        status = ApiKeyStatus.HEALTHY_ACTIVE,
        isDefaultFreeTier = true,
        quotaResetTimestamp = System.currentTimeMillis() + (12 * 3600 * 1000L)
      ),
      ManagedApiKey(
        id = key2Id,
        label = "Secondary Backup Free Key (ဒုတိယ အခမဲ့အရန်သော့)",
        maskedKey = "AIzaSy...4412",
        tier = ApiKeyTier.FREE_TIER,
        maxQuota = 500,
        usedQuota = 50,
        warningThreshold = 0.80f,
        criticalThreshold = 0.95f,
        status = ApiKeyStatus.HEALTHY_ACTIVE,
        isDefaultFreeTier = false,
        quotaResetTimestamp = System.currentTimeMillis() + (18 * 3600 * 1000L)
      ),
      ManagedApiKey(
        id = key3Id,
        label = "Emergency Pro Tier Key (အရေးပေါ် ပရိုသော့)",
        maskedKey = "AIzaSy...7723",
        tier = ApiKeyTier.PRO_TIER,
        maxQuota = 5000,
        usedQuota = 210,
        warningThreshold = 0.85f,
        criticalThreshold = 0.98f,
        status = ApiKeyStatus.HEALTHY_ACTIVE,
        isDefaultFreeTier = false,
        quotaResetTimestamp = System.currentTimeMillis() + (24 * 3600 * 1000L)
      )
    )

    _keys.value = defaultList
    _activeKeyId.value = key1Id
    savePreferences()
  }

  /**
   * Retrieves the decrypted secret key string for the currently active key.
   */
  fun getActiveSecretKey(): String {
    val key = activeKey.value ?: return ""
    val secret = secureStorage.getDecryptedKey(key.id)
    return if (isPlaceholderKey(secret)) "" else secret
  }

  /**
   * Increments usage count and detects approaching quota exhaustion.
   * Emits pre-warning alerts and triggers proactive failover if critical threshold is reached.
   */
  @Synchronized
  fun recordUsage(amount: Int = 1) {
    val currentKey = activeKey.value ?: return
    val newUsed = currentKey.usedQuota + amount
    val newPercentage = if (currentKey.maxQuota > 0) newUsed.toFloat() / currentKey.maxQuota.toFloat() else 0f

    var updatedStatus = currentKey.status
    if (newPercentage >= 1f) {
      updatedStatus = ApiKeyStatus.QUOTA_EXHAUSTED
    } else if (newPercentage >= currentKey.warningThreshold) {
      updatedStatus = ApiKeyStatus.APPROACHING_QUOTA
    }

    val updatedKey = currentKey.copy(
      usedQuota = newUsed,
      lastUsedTimestamp = System.currentTimeMillis(),
      status = updatedStatus
    )

    updateKey(updatedKey)

    // Pre-warning Alert at Warning Threshold (e.g. >= 80%)
    if (newPercentage >= currentKey.warningThreshold && newPercentage < currentKey.criticalThreshold) {
      val alert = ApiKeyQuotaAlert(
        keyId = currentKey.id,
        keyLabel = currentKey.label,
        currentUsage = newUsed,
        maxQuota = currentKey.maxQuota,
        usagePercentage = newPercentage,
        messageBurmese = "သတိပေးချက်: '${currentKey.label}' သော့၏ ခွဲတမ်းအသုံးပြုမှု ${(newPercentage * 100).toInt()}% သို့ ရောက်ရှိနေပါပြီ။ အရန်သော့သို့ ပြောင်းရန် အဆင်သင့်ဖြစ်နေပါသည်။",
        messageEnglish = "Warning: API key '${currentKey.label}' has reached ${(newPercentage * 100).toInt()}% of its quota. Backup keys are ready.",
        severity = AlertSeverity.WARNING
      )
      scope.launch { _quotaAlerts.emit(alert) }
    }

    // Critical Threshold Proactive Failover (e.g. >= 95%)
    if (newPercentage >= currentKey.criticalThreshold) {
      val alert = ApiKeyQuotaAlert(
        keyId = currentKey.id,
        keyLabel = currentKey.label,
        currentUsage = newUsed,
        maxQuota = currentKey.maxQuota,
        usagePercentage = newPercentage,
        messageBurmese = "အရေးပေါ်သတိပေးချက်: '${currentKey.label}' ခွဲတမ်း ${(newPercentage * 100).toInt()}% ကုန်လုနီးပါးဖြစ်သဖြင့် ဝန်ဆောင်မှုမပြတ်တောက်စေရန် အလိုအလျောက် သော့ပြောင်းလဲပေးနေပါသည်။",
        messageEnglish = "Critical Warning: API key '${currentKey.label}' reached ${(newPercentage * 100).toInt()}%. Proactively switching to prevent outage.",
        severity = AlertSeverity.CRITICAL
      )
      scope.launch { _quotaAlerts.emit(alert) }

      if (_autoFailoverEnabled.value) {
        switchToNextAvailableKey("Reached Critical Quota (${(newPercentage * 100).toInt()}%)")
      }
    }
  }

  /**
   * Cost-optimization strategy: Reverts back to the reset free-tier key
   * once cooldown expires or the quota reset timestamp is reached.
   */
  @Synchronized
  fun checkAndRevertToFreeTierIfReset(): Boolean {
    if (!_autoRevertToFreeTier.value) return false

    val now = System.currentTimeMillis()
    var reverted = false

    val updatedList = _keys.value.map { key ->
      var modified = key
      // Check if periodic quota reset time has arrived
      if (key.quotaResetTimestamp > 0 && now >= key.quotaResetTimestamp) {
        val nextReset = now + (24 * 3600 * 1000L) // reset every 24 hours
        modified = modified.copy(
          usedQuota = 0,
          status = ApiKeyStatus.HEALTHY_ACTIVE,
          consecutiveErrors = 0,
          cooldownUntilTimestamp = 0L,
          quotaResetTimestamp = nextReset
        )
      } else if (key.cooldownUntilTimestamp > 0 && now >= key.cooldownUntilTimestamp) {
        // Cooldown period expired
        if (modified.status == ApiKeyStatus.RATE_LIMITED_429 || modified.status == ApiKeyStatus.COOLING_DOWN) {
          modified = modified.copy(
            status = ApiKeyStatus.HEALTHY_ACTIVE,
            consecutiveErrors = 0,
            cooldownUntilTimestamp = 0L
          )
        }
      }
      modified
    }

    _keys.value = updatedList
    savePreferences()

    // If current active key is not the primary free tier key, and primary free tier is now healthy & reset,
    // revert back to primary free tier key!
    val currentActive = activeKey.value
    val primaryFreeKey = _keys.value.firstOrNull { it.isDefaultFreeTier && it.canBeUsed && it.usedQuota == 0 }

    if (primaryFreeKey != null && currentActive?.id != primaryFreeKey.id) {
      logRotation(
        fromId = currentActive?.id,
        toId = primaryFreeKey.id,
        reason = "သက်သာမှုဗျူဟာ: မူလအခမဲ့သော့ ခွဲတမ်းပြန်လည်သတ်မှတ်ပြီးဖြစ်၍ အလိုအလျောက် ပြန်လည်အသုံးပြုသည် (Reverted to reset free quota key)"
      )
      _activeKeyId.value = primaryFreeKey.id
      savePreferences()
      reverted = true
    }

    return reverted
  }

  /**
   * Handles HTTP Rate Limiting (429) or Quota Exceeded (403) from API clients.
   * Applies Exponential Backoff, updates key health, and performs seamless failover.
   *
   * @return true if failover succeeded and request can be retried with the new key.
   */
  @Synchronized
  fun handleHttpError(responseCode: Int, responseMessage: String? = null): Boolean {
    val currentKey = activeKey.value ?: return false
    val now = System.currentTimeMillis()
    val consecutive = currentKey.consecutiveErrors + 1

    val is429 = responseCode == 429
    val isQuota403 = responseCode == 403 && (
      responseMessage?.contains("quota", ignoreCase = true) == true ||
      responseMessage?.contains("exhausted", ignoreCase = true) == true ||
      responseMessage?.contains("resource", ignoreCase = true) == true
    )

    if (!is429 && !isQuota403) {
      return false
    }

    // Exponential backoff: 30s * 2^(errors-1), max 1 hour
    val backoffDuration = if (is429) {
      (30_000L * (1L shl (consecutive - 1).coerceIn(0, 6))).coerceAtMost(3600_000L)
    } else {
      // For quota exhausted, cooldown for 4 hours or until reset
      4 * 3600_000L
    }

    val updatedKey = currentKey.copy(
      status = if (is429) ApiKeyStatus.RATE_LIMITED_429 else ApiKeyStatus.QUOTA_EXHAUSTED,
      consecutiveErrors = consecutive,
      lastErrorTimestamp = now,
      cooldownUntilTimestamp = now + backoffDuration
    )
    updateKey(updatedKey)

    // Trigger alert
    val reasonText = if (is429) "HTTP 429 Rate Limit Exceeded" else "HTTP 403 Quota Exhausted"
    val alert = ApiKeyQuotaAlert(
      keyId = currentKey.id,
      keyLabel = currentKey.label,
      currentUsage = currentKey.usedQuota,
      maxQuota = currentKey.maxQuota,
      usagePercentage = currentKey.usagePercentage,
      messageBurmese = "သတိပေးချက်: '${currentKey.label}' သည် $reasonText ဖြစ်သွားပါသည်။ အခြားအရန်သော့သို့ အလိုအလျောက် ပြောင်းလဲပေးနေပါသည်။",
      messageEnglish = "Alert: Key '${currentKey.label}' failed with $reasonText. Switching to alternate key.",
      severity = AlertSeverity.CRITICAL
    )
    scope.launch { _quotaAlerts.emit(alert) }

    // Automatic Failover to next key
    if (_autoFailoverEnabled.value) {
      val nextKey = switchToNextAvailableKey("Failover due to $reasonText (Cooldown: ${backoffDuration / 1000}s)")
      return nextKey != null
    }

    return false
  }

  /**
   * Automatic rotation to the best available backup key.
   * Priority:
   * 1. Healthy Free Tier keys (not exhausted, not in cooldown)
   * 2. Pro Tier keys
   * 3. Emergency Backup keys
   */
  @Synchronized
  fun switchToNextAvailableKey(reason: String): ManagedApiKey? {
    checkAndRevertToFreeTierIfReset()

    val currentId = _activeKeyId.value
    val candidates = _keys.value.filter { it.id != currentId && it.canBeUsed }

    // Sort by tier preference: Free -> Pro -> Backup, then by lowest usage percentage
    val bestCandidate = candidates.sortedWith(
      compareBy<ManagedApiKey> { key ->
        when (key.tier) {
          ApiKeyTier.FREE_TIER -> 0
          ApiKeyTier.PRO_TIER -> 1
          ApiKeyTier.BACKUP_TIER -> 2
        }
      }.thenBy { it.usagePercentage }
    ).firstOrNull()

    if (bestCandidate != null) {
      logRotation(fromId = currentId, toId = bestCandidate.id, reason = reason)
      _activeKeyId.value = bestCandidate.id
      savePreferences()
      return bestCandidate
    }

    return null
  }

  /**
   * Manually select an active key from UI.
   */
  fun setActiveKey(keyId: String) {
    val target = _keys.value.firstOrNull { it.id == keyId } ?: return
    logRotation(fromId = _activeKeyId.value, toId = keyId, reason = "Manual selection by user", isAutomatic = false)
    _activeKeyId.value = keyId
    savePreferences()
  }

  /**
   * Adds a new API key securely.
   */
  @Synchronized
  fun addApiKey(
    label: String,
    rawKey: String,
    tier: ApiKeyTier = ApiKeyTier.FREE_TIER,
    maxQuota: Int = 1000,
    isDefaultFreeTier: Boolean = false
  ): ManagedApiKey {
    val keyId = "key_" + UUID.randomUUID().toString().take(8)
    val masked = if (rawKey.length > 8) {
      rawKey.take(6) + "..." + rawKey.takeLast(4)
    } else {
      "••••••••"
    }

    secureStorage.saveEncryptedKey(keyId, rawKey)

    val newKey = ManagedApiKey(
      id = keyId,
      label = label,
      maskedKey = masked,
      tier = tier,
      maxQuota = maxQuota,
      usedQuota = 0,
      status = ApiKeyStatus.HEALTHY_ACTIVE,
      isDefaultFreeTier = isDefaultFreeTier,
      quotaResetTimestamp = System.currentTimeMillis() + (24 * 3600 * 1000L)
    )

    _keys.value = _keys.value + newKey
    _activeKeyId.value = keyId
    savePreferences()
    return newKey
  }

  /**
   * Removes an API key.
   */
  @Synchronized
  fun removeApiKey(keyId: String) {
    secureStorage.removeKey(keyId)
    val remaining = _keys.value.filter { it.id != keyId }
    _keys.value = remaining
    if (_activeKeyId.value == keyId) {
      _activeKeyId.value = remaining.firstOrNull { it.canBeUsed }?.id
    }
    savePreferences()
  }

  /**
   * Resets quota usage for a key (e.g. manual reset or testing).
   */
  @Synchronized
  fun resetKeyQuota(keyId: String) {
    val updated = _keys.value.map {
      if (it.id == keyId) {
        it.copy(
          usedQuota = 0,
          status = ApiKeyStatus.HEALTHY_ACTIVE,
          consecutiveErrors = 0,
          cooldownUntilTimestamp = 0L
        )
      } else it
    }
    _keys.value = updated
    savePreferences()
  }

  @Synchronized
  fun resetAllKeysForTesting() {
    _keys.value = _keys.value.map {
      it.copy(
        usedQuota = 0,
        status = ApiKeyStatus.HEALTHY_ACTIVE,
        consecutiveErrors = 0,
        cooldownUntilTimestamp = 0L
      )
    }
    _activeKeyId.value = _keys.value.firstOrNull()?.id ?: ""
    savePreferences()
  }

  /**
   * Updates an existing key in memory and storage.
   */
  private fun updateKey(updated: ManagedApiKey) {
    _keys.value = _keys.value.map { if (it.id == updated.id) updated else it }
    savePreferences()
  }

  private fun logRotation(fromId: String?, toId: String, reason: String, isAutomatic: Boolean = true) {
    val log = ApiKeyRotationLog(
      fromKeyId = fromId,
      toKeyId = toId,
      reason = reason,
      isAutomatic = isAutomatic
    )
    _rotationLogs.value = listOf(log) + _rotationLogs.value.take(49)
  }
}
