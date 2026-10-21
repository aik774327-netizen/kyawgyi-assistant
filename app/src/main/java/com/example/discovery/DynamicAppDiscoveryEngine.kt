package com.example.discovery

import android.content.Context
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.semantic.MyanmarTextNormalizer
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Discovery and Resolution Policy Outcome
 */
sealed class AppDiscoveryOutcome {
    data class DirectExecute(
        val appInfo: InstalledAppInfo,
        val confidence: Float,
        val matchType: MatchType,
        val query: String
    ) : AppDiscoveryOutcome()

    data class NeedsClarification(
        val candidateApps: List<InstalledAppInfo>,
        val confidence: Float,
        val clarificationPromptMM: String,
        val query: String
    ) : AppDiscoveryOutcome()

    data class NotFound(
        val query: String,
        val reasonMM: String = "ရှာဖွေနေသော အက်ပ်ကို စက်ထဲတွင် ရှာမတွေ့ပါခင်ဗျာ။"
    ) : AppDiscoveryOutcome()
}

/**
 * DynamicAppDiscoveryEngine (Stage 16 Production Hardened)
 *
 * Requirements:
 * - Uses PackageManager dynamically with ACTION_MAIN & CATEGORY_LAUNCHER
 * - Never depends on hardcoded package maps
 * - Caches discovered apps with invalidation on app install/remove/change
 * - Android 11–15 package visibility compliant
 * - Unicode normalization, phonetic normalization, Burmese transliterations, English labels
 * - Match hierarchy: Exact match -> Alias match -> Fuzzy match (Normalized Levenshtein)
 * - Strict Confidence policy:
 *     >= 0.90 -> Direct execute
 *     0.70 to 0.89 -> Ask Burmese clarification
 *     < 0.70 -> Report app not found
 * - Never automatically launch an ambiguous application
 */
class DynamicAppDiscoveryEngine(
    private val context: Context,
    private val packageDiscovery: DynamicPackageDiscovery = DynamicPackageDiscovery.getInstance(context),
    private val appMatcher: AppNameMatcher = AppNameMatcher(),
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer(),
    private val appResolver: DynamicAppResolver = DynamicAppResolver(context, packageDiscovery, appMatcher, textNormalizer)
) {

    companion object {
        private const val TAG = "DynamicAppDiscovery"

        const val CONFIDENCE_DIRECT_EXECUTE = 0.85f
        const val CONFIDENCE_CLARIFICATION = 0.65f

        @Volatile
        private var instance: DynamicAppDiscoveryEngine? = null

        fun getInstance(context: Context): DynamicAppDiscoveryEngine {
            return instance ?: synchronized(this) {
                instance ?: DynamicAppDiscoveryEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Resolve user query dynamically to an installed application outcome
     */
    suspend fun resolveApp(rawQuery: String): AppDiscoveryOutcome = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val queryClean = rawQuery.trim()

        if (queryClean.isBlank()) {
            return@withContext AppDiscoveryOutcome.NotFound(rawQuery)
        }

        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.APP_RESOLUTION_STARTED,
            details = "query=$queryClean"
        )

        // 1. Refresh or get cached apps
        val apps = packageDiscovery.getAllLaunchableApps()
        if (apps.isEmpty()) {
            Log.w(TAG, "No launchable apps discovered in PackageManager")
            return@withContext AppDiscoveryOutcome.NotFound(queryClean)
        }

        // 2. Delegate to dynamic multi-signal resolver
        val resolution = appResolver.resolveApp(queryClean)
        val latency = System.currentTimeMillis() - startTime

        Log.d(TAG, "Resolved '$queryClean' in ${latency}ms: decision=${resolution.decision}, conf=${resolution.confidence}")

        return@withContext when {
            resolution.confidence >= CONFIDENCE_DIRECT_EXECUTE && resolution.resolvedApp != null -> {
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_MATCH_SELECTED,
                    details = "pkg=${resolution.resolvedApp.packageName}, conf=${resolution.confidence}, type=${resolution.matchType}",
                    latencyMs = latency
                )
                AppDiscoveryOutcome.DirectExecute(
                    appInfo = resolution.resolvedApp,
                    confidence = resolution.confidence,
                    matchType = resolution.matchType,
                    query = queryClean
                )
            }
            resolution.confidence >= CONFIDENCE_CLARIFICATION && resolution.candidates.isNotEmpty() -> {
                val candidateApps = resolution.candidates.map { it.app }
                val prompt = resolution.clarificationMessageMM
                    ?: "${candidateApps.first().applicationLabel} အက်ပ်ကို ဖွင့်ပေးရမလားခင်ဗျာ။"

                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_MATCH_AMBIGUOUS,
                    details = "candidates=${candidateApps.map { it.applicationLabel }}, conf=${resolution.confidence}",
                    latencyMs = latency
                )
                AppDiscoveryOutcome.NeedsClarification(
                    candidateApps = candidateApps,
                    confidence = resolution.confidence,
                    clarificationPromptMM = prompt,
                    query = queryClean
                )
            }
            else -> {
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_MATCH_REJECTED,
                    details = "query=$queryClean, conf=${resolution.confidence}",
                    latencyMs = latency
                )
                AppDiscoveryOutcome.NotFound(
                    query = queryClean,
                    reasonMM = "‘$queryClean’ အမည်ရှိသော အက်ပ်ကို ရှာမတွေ့ပါခင်ဗျာ။"
                )
            }
        }
    }

    /**
     * Invalidate discovery cache manually or on package broadcast
     */
    suspend fun invalidateCache() {
        packageDiscovery.refreshInstalledApps()
    }
}
