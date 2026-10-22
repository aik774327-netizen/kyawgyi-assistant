package com.example.security.api

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/**
 * ApiKeyQuotaManager
 *
 * Enterprise API Key Quota Management System:
 * - Detects quota exhaustion (HTTP 429 / RESOURCE_EXHAUSTED) and approaching limits (80%).
 * - Automatic seamless failover between multiple keys in the pool.
 * - Cost optimization strategy: automatically reverts to Free Tier key upon cooldown/reset.
 * - Hardware/Keystore-backed secure persistence via SecureKeyVault.
 * - Proactive monitoring, state emission, alerts, and rotation history.
 * - Client-side sliding-window rate limiting (RPM guard).
 * - Automatic retry with exponential backoff during key failover.
 */
class ApiKeyQuotaManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ApiKeyQuotaManager"
        private const val VAULT_KEY_POOL = "encrypted_api_key_pool"
        private const val VAULT_ACTIVE_KEY_ID = "active_api_key_id"
        private const val VAULT_AUTO_REVERT = "auto_revert_to_free"

        const val DEFAULT_PRIMARY_ID = "key_primary_default"

        @Volatile
        private var INSTANCE: ApiKeyQuotaManager? = null

        fun getInstance(context: Context? = null): ApiKeyQuotaManager? {
            if (INSTANCE != null) return INSTANCE
            if (context != null) {
                return synchronized(this) {
                    INSTANCE ?: ApiKeyQuotaManager(context.applicationContext).also { INSTANCE = it }
                }
            }
            return null
        }

        fun getRequiredInstance(context: Context): ApiKeyQuotaManager {
            return getInstance(context) ?: synchronized(this) {
                INSTANCE ?: ApiKeyQuotaManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val vault = SecureKeyVault(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // In-memory key pool
    private val keysMap = mutableMapOf<String, ManagedApiKey>()
    private var activeKeyId: String = DEFAULT_PRIMARY_ID
    private var autoRevertToFree: Boolean = true

    // Rotation & Alert history (bounded to last 50 entries)
    private val alertsList = mutableListOf<ApiQuotaAlert>()
    private val rotationList = mutableListOf<KeyRotationRecord>()

    // Exposed Reactive State
    private val _poolState = MutableStateFlow(ApiKeyPoolOverview())
    val poolState: StateFlow<ApiKeyPoolOverview> = _poolState.asStateFlow()

    init {
        loadKeysFromVault()
        // Periodically verify cooldowns and cost-saving auto-reversion
        startPeriodicMaintenance()
    }

    /**
     * Load stored keys from SecureKeyVault or initialize default key from BuildConfig.
     */
    private fun loadKeysFromVault() {
        try {
            val serialized = vault.getDecrypted(VAULT_KEY_POOL, "")
            autoRevertToFree = vault.getString(VAULT_AUTO_REVERT, "true").toBoolean()

            if (serialized.isNotBlank()) {
                val jsonArr = JSONArray(serialized)
                for (i in 0 until jsonArr.length()) {
                    val keyObj = jsonArr.getJSONObject(i)
                    val key = ManagedApiKey.fromJson(keyObj)
                    keysMap[key.id] = key
                }
            }

            // If empty, auto-seed with BuildConfig.GEMINI_API_KEY as primary Free tier key
            val defaultKey = BuildConfig.GEMINI_API_KEY
            if (keysMap.isEmpty() && defaultKey.isNotBlank() && defaultKey != "MY_GEMINI_API_KEY") {
                val primaryKey = ManagedApiKey(
                    id = DEFAULT_PRIMARY_ID,
                    rawKey = defaultKey,
                    label = "Primary Key (Free Tier)",
                    tier = ApiKeyTier.FREE_TIER,
                    status = ApiKeyHealthStatus.HEALTHY,
                    dailyLimit = 1500,
                    minuteLimit = 15
                )
                keysMap[DEFAULT_PRIMARY_ID] = primaryKey
                saveKeysToVault()
            }

            // Restore active key or pick best available
            val savedActiveId = vault.getString(VAULT_ACTIVE_KEY_ID, "")
            activeKeyId = if (savedActiveId.isNotBlank() && keysMap.containsKey(savedActiveId)) {
                savedActiveId
            } else {
                keysMap.keys.firstOrNull() ?: DEFAULT_PRIMARY_ID
            }

            updateOverviewState()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading keys from vault: ${e.message}", e)
        }
    }

    /**
     * Persists the key pool. Returns false when secure storage is unavailable
     * so callers never report a save that did not happen (FAKE-12).
     */
    private fun saveKeysToVault(): Boolean {
        return try {
            val jsonArr = JSONArray()
            keysMap.values.forEach { key ->
                jsonArr.put(key.toJson())
            }
            vault.saveEncrypted(VAULT_KEY_POOL, jsonArr.toString())
            vault.saveString(VAULT_ACTIVE_KEY_ID, activeKeyId)
            vault.saveString(VAULT_AUTO_REVERT, autoRevertToFree.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving keys to vault: ${e.message}", e)
            false
        }
    }

    /**
     * Periodic background loop checking cooldown expiration and daily reset.
     */
    private fun startPeriodicMaintenance() {
        scope.launch {
            while (isActive) {
                delay(15000) // Check every 15 seconds
                mutex.withLock {
                    checkAndResetDailyQuotas()
                    checkCooldownsAndRevertToFree()
                }
            }
        }
    }

    /**
     * Check if a new day has started in UTC and reset daily quota counters.
     */
    private fun checkAndResetDailyQuotas() {
        val lastReset = vault.getLong("last_daily_reset_timestamp", 0L)
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        val calLast = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = lastReset }

        if (calNow.get(Calendar.DAY_OF_YEAR) != calLast.get(Calendar.DAY_OF_YEAR) || calNow.get(Calendar.YEAR) != calLast.get(Calendar.YEAR)) {
            Log.i(TAG, "New UTC day detected. Resetting daily quota counters for all keys.")
            keysMap.forEach { (id, key) ->
                keysMap[id] = key.copy(
                    requestsToday = 0,
                    status = if (key.status == ApiKeyHealthStatus.EXHAUSTED_429 || key.status == ApiKeyHealthStatus.NEAR_LIMIT_80) {
                        ApiKeyHealthStatus.HEALTHY
                    } else key.status,
                    cooldownUntilTimestamp = 0L,
                    consecutive429Count = 0
                )
            }
            vault.saveLong("last_daily_reset_timestamp", now)
            recordAlert("All Keys", "Daily quota reset completed. All keys restored to Healthy status.", AlertSeverity.INFO)
            saveKeysToVault()
            updateOverviewState()
        }
    }

    /**
     * Cost optimization: If currently using a paid or fallback key,
     * check if free tier key is out of cooldown and automatically revert to it.
     */
    private fun checkCooldownsAndRevertToFree() {
        val now = System.currentTimeMillis()
        var changed = false

        // Un-cooldown keys whose time has elapsed
        keysMap.forEach { (id, key) ->
            if (key.cooldownUntilTimestamp > 0L && now >= key.cooldownUntilTimestamp) {
                val newStatus = if (key.dailyUsagePercent >= 80) ApiKeyHealthStatus.NEAR_LIMIT_80 else ApiKeyHealthStatus.HEALTHY
                keysMap[id] = key.copy(
                    status = newStatus,
                    cooldownUntilTimestamp = 0L
                )
                changed = true
                recordAlert(key.label, "Cooldown period ended. Key restored to $newStatus.", AlertSeverity.INFO)
            }
        }

        // Cost optimization strategy: revert to free tier key
        if (autoRevertToFree) {
            val activeKey = keysMap[activeKeyId]
            if (activeKey != null && activeKey.tier != ApiKeyTier.FREE_TIER) {
                // Find healthy available free-tier key
                val freeKey = keysMap.values.firstOrNull { it.tier == ApiKeyTier.FREE_TIER && it.isAvailable }
                if (freeKey != null && freeKey.id != activeKeyId) {
                    val prevLabel = activeKey.label
                    activeKeyId = freeKey.id
                    changed = true
                    recordRotation(
                        fromKeyLabel = prevLabel,
                        toKeyLabel = freeKey.label,
                        reason = "Cost Optimization: Free Tier key became available again."
                    )
                    recordAlert(
                        freeKey.label,
                        "💡 Reverted to Free Quota Key for maximum cost efficiency.",
                        AlertSeverity.INFO
                    )
                }
            }
        }

        if (changed) {
            saveKeysToVault()
            updateOverviewState()
        }
    }

    /**
     * Resolves the current active API key, rotating if the current one is rate-limited or exhausted.
     */
    suspend fun getActiveApiKey(): String? = mutex.withLock {
        ensureOptimalActiveKeyLocked()
        val current = keysMap[activeKeyId]
        return@withLock if (current != null && current.isAvailable) current.rawKey else null
    }

    /**
     * Ensures active key is available and optimal (prioritizing Free tier if available).
     */
    private fun ensureOptimalActiveKeyLocked() {
        val current = keysMap[activeKeyId]
        if (current != null && current.isAvailable) {
            // Cost Optimization: If autoRevertToFree is enabled and current is not free tier, check if a healthy free tier key is available
            if (autoRevertToFree && current.tier != ApiKeyTier.FREE_TIER) {
                val freeCandidate = keysMap.values.firstOrNull { it.tier == ApiKeyTier.FREE_TIER && it.isAvailable }
                if (freeCandidate != null && freeCandidate.id != activeKeyId) {
                    val prevLabel = current.label
                    activeKeyId = freeCandidate.id
                    recordRotation(prevLabel, freeCandidate.label, "Cost Optimization: Reverted to Free Tier key.")
                    saveKeysToVault()
                    updateOverviewState()
                    return
                }
            }

            // Check proactive sliding window rate limit
            val currentMinute = System.currentTimeMillis() / 60000
            val requestsThisMin = if (current.lastMinuteWindow == currentMinute) current.requestsThisMinute else 0
            if (requestsThisMin < current.minuteLimit) {
                return // Current key is fine
            } else {
                Log.w(TAG, "Key '${current.label}' reached RPM limit ($requestsThisMin/${current.minuteLimit}). Rotating proactively.")
                keysMap[activeKeyId] = current.copy(
                    status = ApiKeyHealthStatus.RATE_LIMITED_RPM,
                    cooldownUntilTimestamp = System.currentTimeMillis() + 60000
                )
                recordAlert(current.label, "Preemptive RPM limit reached ($requestsThisMin/${current.minuteLimit}). Switching key.", AlertSeverity.WARNING)
            }
        }

        // Find best replacement key
        val nextKey = selectBestKeyLocked()
        if (nextKey != null && nextKey.id != activeKeyId) {
            val prevLabel = current?.label ?: "None"
            activeKeyId = nextKey.id
            recordRotation(prevLabel, nextKey.label, "Automatic failover / load balance")
            saveKeysToVault()
            updateOverviewState()
        }
    }

    private fun selectBestKeyLocked(): ManagedApiKey? {
        val availableKeys = keysMap.values.filter { it.isAvailable }
        if (availableKeys.isEmpty()) return null

        // Priority 1: Free tier keys with healthy status
        val freeHealthy = availableKeys.firstOrNull { it.tier == ApiKeyTier.FREE_TIER && it.status == ApiKeyHealthStatus.HEALTHY }
        if (freeHealthy != null) return freeHealthy

        // Priority 2: Any available Free tier key
        val anyFree = availableKeys.firstOrNull { it.tier == ApiKeyTier.FREE_TIER }
        if (anyFree != null) return anyFree

        // Priority 3: Lowest daily usage percent among remaining keys
        return availableKeys.minByOrNull { it.dailyUsagePercent }
    }

    /**
     * Executes an API request with automatic retry, exponential backoff, and seamless key rotation on 429.
     */
    suspend fun <T> executeWithRetry(
        operationName: String = "Gemini API Call",
        maxRetries: Int = 3,
        block: suspend (apiKey: String) -> T
    ): T {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            attempt++
            val keyToUse = mutex.withLock {
                ensureOptimalActiveKeyLocked()
                keysMap[activeKeyId]
            }

            if (keyToUse == null || !keyToUse.isAvailable) {
                if (mutex.withLock { keysMap.isEmpty() }) {
                    // No key has ever been configured. Claiming "HTTP 429 Quota
                    // Exhausted" here (the pre-2026-10-22 behavior) was false:
                    // no request was ever sent. Report the real problem.
                    val err = com.example.data.GeminiApiService.NO_API_KEY_MESSAGE
                    Log.e(TAG, "No API keys configured in pool: $err")
                    throw com.example.data.NoApiKeyConfiguredException(err)
                }
                // Keys exist but every one is in cooldown after real quota
                // errors. Say exactly that - do not claim an HTTP 429 response
                // was received for this attempt.
                val err = "All configured API keys are in cooldown (all in cooldown after quota errors). Please wait for cooldown to expire or add another key."
                Log.e(TAG, err)
                throw com.example.data.GeminiQuotaExceededException(err)
            }

            try {
                // Track pre-request call
                recordRequestStarted(keyToUse.id)

                val result = block(keyToUse.rawKey)

                // Track successful call
                recordRequestSuccess(keyToUse.id)
                return result
            } catch (e: Exception) {
                lastException = e
                val isQuotaExhausted = isQuotaExhaustedException(e)

                if (isQuotaExhausted) {
                    Log.w(TAG, "Quota exhausted (429) on attempt $attempt with key '${keyToUse.label}': ${e.message}")
                    mutex.withLock {
                        handleQuotaExhaustionLocked(keyToUse.id, e.message ?: "HTTP 429 Too Many Requests")
                    }

                    // Exponential backoff before next attempt with new key
                    val backoffMs = (500L * (1L shl (attempt - 1))) + (100L..300L).random()
                    delay(backoffMs)
                } else {
                    Log.w(TAG, "Transient error on attempt $attempt: ${e.message}")
                    val backoffMs = 400L * attempt
                    delay(backoffMs)
                }
            }
        }

        throw lastException ?: RuntimeException("API execution failed after $maxRetries attempts")
    }

    private fun isQuotaExhaustedException(e: Exception): Boolean {
        if (e is com.example.data.GeminiQuotaExceededException) return true
        val msg = (e.message ?: "").lowercase()
        return msg.contains("429") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota_exceeded") ||
                msg.contains("quota exceeded") ||
                msg.contains("rate limit") ||
                msg.contains("too many requests") ||
                msg.contains("all in cooldown")
    }

    private fun recordRequestStarted(keyId: String) {
        val key = keysMap[keyId] ?: return
        val currentMinute = System.currentTimeMillis() / 60000
        val requestsThisMin = if (key.lastMinuteWindow == currentMinute) key.requestsThisMinute + 1 else 1
        val newRequestsToday = key.requestsToday + 1

        // Proactive warning: Check if >= 80% daily quota
        val newStatus = if (key.dailyLimit > 0 && newRequestsToday >= (key.dailyLimit * 0.8)) {
            if (key.status != ApiKeyHealthStatus.NEAR_LIMIT_80) {
                recordAlert(key.label, "⚠️ Quota warning: Key has reached ${((newRequestsToday.toFloat() / key.dailyLimit) * 100).toInt()}% of daily quota.", AlertSeverity.WARNING)
            }
            ApiKeyHealthStatus.NEAR_LIMIT_80
        } else {
            key.status
        }

        keysMap[keyId] = key.copy(
            requestsToday = newRequestsToday,
            requestsThisMinute = requestsThisMin,
            lastMinuteWindow = currentMinute,
            lastUsedTimestamp = System.currentTimeMillis(),
            totalCalls = key.totalCalls + 1,
            status = newStatus
        )
        updateOverviewState()
    }

    private fun recordRequestSuccess(keyId: String) {
        val key = keysMap[keyId] ?: return
        keysMap[keyId] = key.copy(
            consecutive429Count = 0,
            lastError = null
        )
    }

    private fun handleQuotaExhaustionLocked(keyId: String, reason: String) {
        val key = keysMap[keyId] ?: return
        val newConsecutive = key.consecutive429Count + 1

        // Exponential cooldown: 60s, 120s, 240s... up to 30 minutes
        val cooldownSeconds = (60L * (1L shl (newConsecutive - 1).coerceAtMost(5))).coerceAtMost(1800L)
        val cooldownUntil = System.currentTimeMillis() + (cooldownSeconds * 1000L)

        keysMap[keyId] = key.copy(
            status = ApiKeyHealthStatus.EXHAUSTED_429,
            cooldownUntilTimestamp = cooldownUntil,
            consecutive429Count = newConsecutive,
            totalFailures = key.totalFailures + 1,
            lastError = reason
        )

        recordAlert(
            keyLabel = key.label,
            message = "❌ Quota limit exceeded (429). Cooldown: ${cooldownSeconds}s.",
            severity = AlertSeverity.CRITICAL
        )

        // Select next healthy key
        val nextKey = selectBestKeyLocked()
        if (nextKey != null) {
            recordRotation(key.label, nextKey.label, "Auto-failover on 429 Quota Exhaustion")
            activeKeyId = nextKey.id
        } else {
            recordAlert("Key Pool", "🚨 All API keys in pool are currently exhausted or cooling down!", AlertSeverity.CRITICAL)
        }

        saveKeysToVault()
        updateOverviewState()
    }

    private fun recordAlert(keyLabel: String, message: String, severity: AlertSeverity) {
        val alert = ApiQuotaAlert(
            keyLabel = keyLabel,
            message = message,
            severity = severity
        )
        alertsList.add(0, alert)
        if (alertsList.size > 50) alertsList.removeLast()
        updateOverviewState()
    }

    private fun recordRotation(fromKeyLabel: String, toKeyLabel: String, reason: String) {
        val record = KeyRotationRecord(
            fromKeyLabel = fromKeyLabel,
            toKeyLabel = toKeyLabel,
            reason = reason
        )
        rotationList.add(0, record)
        if (rotationList.size > 50) rotationList.removeLast()
        updateOverviewState()
    }

    private fun updateOverviewState() {
        val activeKey = keysMap[activeKeyId]
        val total = keysMap.size
        val available = keysMap.values.count { it.isAvailable }

        _poolState.value = ApiKeyPoolOverview(
            activeKeyLabel = activeKey?.label ?: "None",
            activeKeyMasked = activeKey?.maskedKey ?: "Not Configured",
            activeKeyTier = activeKey?.tier ?: ApiKeyTier.FREE_TIER,
            activeKeyStatus = activeKey?.status ?: ApiKeyHealthStatus.HEALTHY,
            activeKeyUsagePercent = activeKey?.dailyUsagePercent ?: 0,
            totalKeys = total,
            availableKeysCount = available,
            isAutoRevertToFreeEnabled = autoRevertToFree,
            totalRotationsCount = rotationList.size,
            alerts = alertsList.toList(),
            recentRotations = rotationList.take(10).toList(),
            keysList = keysMap.values.toList()
        )
    }

    // =========================================================================
    // Public Management API for Settings UI & Testing
    // =========================================================================

    /**
     * FIX-05: returns whether the key was actually PERSISTED to the
     * secure vault. The pre-fix Unit return let the Settings UI show
     * an unconditional success Toast even when the vault write failed
     * and the key existed only in memory (lost on restart). Callers
     * must surface this result honestly.
     */
    suspend fun addOrUpdateKey(
        rawKey: String,
        label: String,
        tier: ApiKeyTier = ApiKeyTier.CUSTOM,
        dailyLimit: Int = 1500
    ): Boolean = mutex.withLock {
        val cleanKey = rawKey.trim()
        if (cleanKey.isBlank()) return@withLock false

        val existingId = keysMap.entries.firstOrNull { it.value.rawKey == cleanKey }?.key
        // UUID, not currentTimeMillis: two keys added within the same
        // millisecond previously collided and silently overwrote each other.
        val id = existingId ?: "key_${java.util.UUID.randomUUID()}"

        keysMap[id] = ManagedApiKey(
            id = id,
            rawKey = cleanKey,
            label = label.ifBlank { "Backup Key #${keysMap.size + 1}" },
            tier = tier,
            status = ApiKeyHealthStatus.HEALTHY,
            dailyLimit = dailyLimit,
            minuteLimit = if (tier == ApiKeyTier.FREE_TIER) 15 else 60
        )

        val saved = saveKeysToVault()
        if (saved) {
            recordAlert(label, "Added/Updated API key successfully in secure vault.", AlertSeverity.INFO)
        } else {
            recordAlert(label, "API key was NOT saved: secure storage unavailable. The key is only in memory for this session.", AlertSeverity.CRITICAL)
        }
        updateOverviewState()
        saved
    }

    suspend fun removeKey(keyId: String) = mutex.withLock {
        if (keyId == DEFAULT_PRIMARY_ID && keysMap.size == 1) {
            Log.w(TAG, "Cannot remove the only primary key.")
            return@withLock
        }
        keysMap.remove(keyId)
        if (activeKeyId == keyId) {
            activeKeyId = keysMap.keys.firstOrNull() ?: DEFAULT_PRIMARY_ID
        }
        saveKeysToVault()
        updateOverviewState()
    }

    suspend fun manuallySetActiveKey(keyId: String) = mutex.withLock {
        if (keysMap.containsKey(keyId)) {
            val prev = keysMap[activeKeyId]?.label ?: "None"
            activeKeyId = keyId
            val next = keysMap[keyId]?.label ?: "Unknown"
            recordRotation(prev, next, "Manual user selection")
            saveKeysToVault()
            updateOverviewState()
        }
    }

    suspend fun setAutoRevertToFree(enabled: Boolean) = mutex.withLock {
        autoRevertToFree = enabled
        vault.saveString(VAULT_AUTO_REVERT, enabled.toString())
        updateOverviewState()
    }

    suspend fun resetAllQuotasAndCooldowns() = mutex.withLock {
        keysMap.forEach { (id, key) ->
            keysMap[id] = key.copy(
                requestsToday = 0,
                requestsThisMinute = 0,
                status = ApiKeyHealthStatus.HEALTHY,
                cooldownUntilTimestamp = 0L,
                consecutive429Count = 0,
                lastError = null
            )
        }
        alertsList.clear()
        recordAlert("All Keys", "Manual reset of all quotas and cooldowns completed.", AlertSeverity.INFO)
        saveKeysToVault()
        updateOverviewState()
    }
}
