package com.example.ocr

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.CommandLogEntity
import com.example.data.FailureCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import com.example.security.KeystoreDatabaseKeyManager

/**
 * FAILURE MEMORY ENGINE
 *
 * အမှားမှ သင်ယူသော Memory စနစ် (Room Database ချိတ်ဆက်ထားသည်):
 * ၁။ မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက်တိုင်းကို Error Log အဖြစ် စနစ်တကျ မှတ်သားသည်။
 * ၂။ အမှားဖြစ်ရသော အကြောင်းရင်းကို (နည်းပညာပိုင်း / အရင်းအမြစ် / လက်တွေ့မကိုက်ညီမှု) အဖြစ် ခွဲခြမ်းစိတ်ဖြာသည်။
 * ၃။ အမှားတစ်ခုတူ ထပ်မံတောင်းဆိုလာပါက "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်" ဟု သတိပေးသည်။
 * ၄။ မအောင်မြင်ခဲ့သော နည်းလမ်းကို ထပ်မံ အကြံမပေးဘဲ အစားထိုးနည်းလမ်း ပံ့ပိုးသည်။
 * ၅။ လုံခြုံရေးအမှန်အခြေအနေ (ISSUE-043 ပြင်ဆင်ချက်): [KeystoreDatabaseKeyManager] သည် Android Keystore
 *    ဖြင့် AES-256-GCM wrap လုပ်ထားသော passphrase တစ်ခု ထုတ်ပေးနိုင်သော်လည်း Room Database
 *    (`ai_angel_v25_db`) ကို ထို passphrase ဖြင့် မ encrypt ထားပါ — AppDatabase တွင် SQLCipher
 *    SupportFactory မရှိပါ။ ထို့ကြောင့် command-log database သည် လက်ရှိတွင် UNENCRYPTED ဖြစ်သည်။
 *    SQLCipher အမှန်တကယ် ချိတ်ဆက်ခြင်းမှာ data migration လိုအပ်သောကြောင့် သီးခြား approved change
 *    အဖြစ် ကျန်ရှိသည် (BLOCKED remainder, ISSUE-043)။
 * ၆။ Process Death သို့မဟုတ် စက်ပိတ်ဖွင့်ပြီးနောက်တွင်လည်း Room Database (`command_logs`) မှ အမှားမှတ်တမ်းများကို ပြန်လည်ဆွဲတင်ပေးသည်။
 */
object FailureMemoryEngine {

    private const val TAG = "FailureMemoryEngine"

    data class FailureRecord(
        val id: String,
        val component: String,
        val operation: String,
        val failureCategory: FailureCategory,
        val rootCauseMm: String,
        val technicalDetails: String,
        val suggestedRemediationMm: String,
        val timestamp: Long = System.currentTimeMillis(),
        val occurrences: Int = 1,
        // ISSUE-037: the ErrorLearningWorkflowEngine ER entry this record was
        // synced to — the failure-event id the pipeline uses to avoid
        // minting a duplicate ER entry for the same failure.
        val errorLearningEntryId: String? = null
    )

    // Component -> List of FailureRecords
    private val failureHistory = ConcurrentHashMap<String, CopyOnWriteArrayList<FailureRecord>>()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    @Volatile
    private var isInitialized = false

    /**
     * Initializes the FailureMemoryEngine with Application Context and restores previous failures from Room DB.
     * ISSUE-043 correction: this also generates the Keystore-wrapped passphrase via
     * [KeystoreDatabaseKeyManager], but that passphrase is NOT applied to the Room
     * database — the database is currently UNENCRYPTED (no SQLCipher factory in
     * AppDatabase). The wrapped key existing does not mean the data is encrypted.
     */
    fun initialize(context: Context) {
        if (isInitialized && appContext != null) return
        val appCtx = context.applicationContext
        appContext = appCtx
        isInitialized = true

        // Generate/verify the Keystore-wrapped passphrase. NOTE (ISSUE-043):
        // the passphrase is not wired into Room - the DB stays unencrypted
        // until a separately approved SQLCipher migration lands.
        try {
            val keyManager = KeystoreDatabaseKeyManager.getInstance(appCtx)
            val passphrase = keyManager.getOrGenerateDatabasePassphrase()
            Log.i(TAG, "KeystoreDatabaseKeyManager initialized for FailureMemoryEngine. Passphrase length: ${passphrase.size} bytes (HardwareBacked=${keyManager.isHardwareKeyBacked()})")
        } catch (e: Exception) {
            Log.w(TAG, "Keystore database key verification note: ${e.message}")
        }

        engineScope.launch {
            try {
                restoreFailuresFromDatabase(appCtx)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring failure history from database: ${e.message}", e)
            }
        }
    }

    /**
     * Restores previous failure logs from Room database into memory.
     */
    suspend fun restoreFailuresFromDatabase(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val storedFailures = db.commandLogDao().getAllFailuresForMemoryRestore()
            var restoredCount = 0

            for (log in storedFailures) {
                val comp = if (log.commandType.isNotBlank()) log.commandType else "SYSTEM"
                val op = if (log.commandText.isNotBlank()) log.commandText else log.actionDetail
                val category = FailureCategory.fromCode(log.failureCategory)

                val list = failureHistory.computeIfAbsent(comp) { CopyOnWriteArrayList() }
                val existing = list.find { it.operation == op && it.failureCategory == category }

                if (existing != null) {
                    val updated = existing.copy(
                        occurrences = existing.occurrences + 1,
                        timestamp = maxOf(existing.timestamp, log.timestamp)
                    )
                    list.remove(existing)
                    list.add(0, updated)
                } else {
                    val restored = FailureRecord(
                        id = "${comp}_${log.timestamp}_${log.id}",
                        component = comp,
                        operation = op,
                        failureCategory = category,
                        rootCauseMm = log.failureReason,
                        technicalDetails = log.actionDetail,
                        suggestedRemediationMm = log.suggestedAlternative,
                        timestamp = log.timestamp,
                        occurrences = 1
                    )
                    list.add(0, restored)
                    restoredCount++
                }
            }
            Log.i(TAG, "Successfully restored $restoredCount persistent failures from Room DB.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore failure records from DB: ${e.message}")
        }
    }

    /**
     * Records a new failure in memory and immediately persists it into Room Database.
     */
    fun recordFailure(
        component: String,
        operation: String,
        category: FailureCategory,
        rootCauseMm: String,
        technicalDetails: String,
        suggestedRemediationMm: String,
        exception: Throwable? = null
    ): FailureRecord {
        // Also synchronize with ErrorLearningWorkflowEngine so ER-00X 3-Factor logs appear in UI.
        // ISSUE-037: the sync runs FIRST so the minted ER entry id can travel
        // with the returned FailureRecord as the failure-event id.
        val syncedErrorId = try {
            com.example.audit.ErrorLearningWorkflowEngine.recordErrorLog(
                task = "$component -> $operation",
                // ISSUE-036: canonical key from the ONE factory, not a local
                // "${component}_${operation}" namespace.
                commandKey = com.example.audit.FailedMethodKeys.canonical(component, operation),
                expectedResult = "$operation အောင်မြင်စွာ လုပ်ဆောင်နိုင်ရန်",
                actualResult = rootCauseMm,
                attemptedMethod = "$component.$operation",
                evidence = technicalDetails,
                recoveryAttempt = suggestedRemediationMm,
                recoveryResult = "NOT_EXECUTED",
                exception = exception,
                // ISSUE-039: tag the recorded resource kind for verifier dispatch
                resourceKind = when (component) {
                    "TTS_SYNTHESIS" -> com.example.audit.ErrorLearningWorkflowEngine.ResourceKind.TTS_VOICE
                    "APP_LAUNCH" -> com.example.audit.ErrorLearningWorkflowEngine.ResourceKind.PACKAGE
                    else -> null
                }
            ).errorId
        } catch (e: Exception) {
            Log.w(TAG, "ErrorLearningWorkflowEngine sync note: ${e.message}")
            null
        }

        val list = failureHistory.computeIfAbsent(component) { CopyOnWriteArrayList() }
        val existing = list.find { it.operation == operation && it.failureCategory == category }

        val newRecord = if (existing != null) {
            val updated = existing.copy(
                occurrences = existing.occurrences + 1,
                timestamp = System.currentTimeMillis(),
                rootCauseMm = rootCauseMm,
                technicalDetails = technicalDetails,
                suggestedRemediationMm = suggestedRemediationMm,
                errorLearningEntryId = syncedErrorId ?: existing.errorLearningEntryId
            )
            list.remove(existing)
            list.add(0, updated)
            updated
        } else {
            val created = FailureRecord(
                id = "${component}_${System.currentTimeMillis()}",
                component = component,
                operation = operation,
                failureCategory = category,
                rootCauseMm = rootCauseMm,
                technicalDetails = technicalDetails,
                suggestedRemediationMm = suggestedRemediationMm,
                occurrences = 1,
                errorLearningEntryId = syncedErrorId
            )
            list.add(0, created)
            created
        }

        Log.w(
            TAG,
            "⚠️ FAILURE RECORDED: component=$component, op=$operation, cat=${category.code}, times=${newRecord.occurrences}, rootCause=$rootCauseMm"
        )

        // Asynchronously persist to Room Database so it survives process recreation / device reboots
        val ctx = appContext
        if (ctx != null) {
            engineScope.launch {
                try {
                    val db = AppDatabase.getDatabase(ctx)
                    val logEntity = CommandLogEntity(
                        commandText = operation,
                        commandType = component,
                        status = "FAILED",
                        actionDetail = technicalDetails,
                        timestamp = newRecord.timestamp,
                        failureCategory = category.code,
                        failureReason = rootCauseMm,
                        suggestedAlternative = suggestedRemediationMm,
                        isRepeatedFailure = newRecord.occurrences > 1
                    )
                    db.commandLogDao().insertCommandLog(logEntity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist failure to Room database: ${e.message}", e)
                }
            }
        }

        return newRecord
    }

    /**
     * Checks if this exact operation failed previously.
     */
    fun hasPreviousFailure(component: String, operation: String): Boolean {
        val list = failureHistory[component] ?: return false
        return list.any { it.operation == operation }
    }

    /**
     * Retrieves the latest failure for a component and operation.
     */
    fun getLatestFailure(component: String, operation: String): FailureRecord? {
        val list = failureHistory[component] ?: return null
        return list.find { it.operation == operation }
    }

    /**
     * Retrieves all recorded failures across components.
     */
    fun getAllFailures(): List<FailureRecord> {
        return failureHistory.values.flatten()
    }

    /**
     * Formats an alert message if this action failed before.
     */
    fun getRepeatWarningIfFailed(component: String, operation: String): String? {
        val failure = getLatestFailure(component, operation) ?: return null
        return "⚠️ ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည် (ယခင်ဖြစ်ပွားမှု: ${failure.occurrences} ကြိမ်)။\n" +
                "• အမှားအမျိုးအစား: ${failure.failureCategory.burmeseLabel}\n" +
                "• အကြောင်းရင်း: ${failure.rootCauseMm}\n" +
                "• အကြံပြုချက်: ${failure.suggestedRemediationMm}"
    }

    /**
     * Clears in-memory history (used for unit testing or user reset).
     */
    fun clearAll() {
        failureHistory.clear()
    }

    /**
     * Clears both in-memory cache and Room database persistent failure records.
     */
    suspend fun clearAllPersistently(context: Context) = withContext(Dispatchers.IO) {
        failureHistory.clear()
        try {
            val db = AppDatabase.getDatabase(context)
            db.commandLogDao().clearAllFailures()
            Log.i(TAG, "All persistent failures cleared from Room DB.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear persistent failures from Room DB: ${e.message}", e)
        }
    }

    /**
     * Returns security status metadata for the Keystore wrapped key.
     * ISSUE-043: the map now states the database's REAL encryption state —
     * [isDatabaseEncrypted] is false because AppDatabase is built without a
     * SQLCipher SupportFactory. A wrapped key existing must never be read
     * as "the database is encrypted".
     */
    fun getKeystoreSecurityStatus(context: Context): Map<String, Any> {
        val keyManager = KeystoreDatabaseKeyManager.getInstance(context)
        return mapOf(
            "algorithm" to "AES-256-GCM",
            "provider" to "AndroidKeyStore",
            "isHardwareBacked" to keyManager.isHardwareKeyBacked(),
            "isWrappedKeyConfigured" to true,
            "passphraseBitLength" to 256,
            "isDatabaseEncrypted" to false
        )
    }

    /**
     * Retrieves the Keystore-wrapped database passphrase. ISSUE-043 note:
     * no production caller passes this to Room today — the command-log
     * database is unencrypted until the blocked SQLCipher migration lands.
     */
    fun getDatabasePassphrase(context: Context): ByteArray {
        return KeystoreDatabaseKeyManager.getInstance(context).getOrGenerateDatabasePassphrase()
    }
}
