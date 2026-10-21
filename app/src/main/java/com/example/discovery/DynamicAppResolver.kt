package com.example.discovery

import android.content.Context
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.engine.CommandContextManager
import com.example.semantic.MyanmarTextNormalizer
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Resolution decision outcome.
 */
enum class ResolutionDecision {
    LAUNCH,
    ASK_CLARIFICATION,
    REJECT_LOW_CONFIDENCE,
    NOT_FOUND,
    CONTEXT_RESOLVED
}

/**
 * Structured app resolution result.
 */
data class AppResolutionResult(
    val decision: ResolutionDecision,
    val resolvedApp: InstalledAppInfo?,
    val confidence: Float,
    val matchType: MatchType,
    val candidates: List<ScoredAppCandidate> = emptyList(),
    val clarificationMessageMM: String? = null,
    val resolutionTimeMs: Long = 0L,
    val originalQuery: String = ""
)

/**
 * Dynamic App Resolution Pipeline.
 * 
 * Orchestrates spoken input normalization, context checking, dynamic candidate retrieval,
 * multi-signal scoring, ambiguity gating, and structured resolution decisions.
 */
class DynamicAppResolver(
    private val context: Context,
    private val packageDiscovery: DynamicPackageDiscovery = DynamicPackageDiscovery.getInstance(context),
    private val appMatcher: AppNameMatcher = AppNameMatcher(),
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer(),
    private val contextManager: CommandContextManager = CommandContextManager.getInstance()
) {

    companion object {
        private const val TAG = "DynamicAppResolver"

        const val HIGH_CONFIDENCE_THRESHOLD = 0.90f
        const val MINIMUM_CONFIDENCE_THRESHOLD = 0.70f
        const val AMBIGUITY_DELTA_THRESHOLD = 0.07f

        // Action verbs and suffixes to strip when extracting target app name
        private val ACTION_STRIPPERS = listOf(
            "ကို ဖွင့်ပေးပါ", "ကို ဖွင့်ပါ", "ကို ဖွင့်", "ကို ဖွင့်", "ကိုဖွင့်",
            "ဖွင့်ပေးပါဦး", "ဖွင့်ပေးပါ", "ဖွင့်ပေး", "ဖွင့်ပါ", "ဖွင့်လိုက်", "ဖွင့်မယ်", "ဖွင့်",
            "စတင်ပါ", "စတင်", "run", "open", "launch", "start",
            "အက်ပ်", "app", "application", "လေး", "ထဲက", "ဆီသို့", "ကို", "အား"
        )

        // Pronouns and indirect contextual references in Burmese
        private val CONTEXT_PRONOUNS = listOf(
            "အဲဒါ", "အဲဒါကို", "အဲ့ဒါ", "အဲ့ဒါကို", "အဲဒီ app", "အဲ့ဒီ app",
            "အဲဒီအက်ပ်", "အဲ့ဒီအက်ပ်", "အခုနောက်ဆုံးဖွင့်ထားတာ", "နောက်ဆုံးဖွင့်တာ",
            "ပြန်ဖွင့်", "ပြန်ဖွင့်ပေး", "ပြန်ဖွင့်ပါ", "ဒါကို ဖွင့်", "အဲ့ဒါလေး"
        )
    }

    /**
     * Resolve user spoken or typed command to an installed application dynamically.
     */
    suspend fun resolveApp(rawCommand: String): AppResolutionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.APP_RESOLUTION_STARTED,
            details = "query=$rawCommand"
        )

        // 1. Text Normalization
        val normOutput = textNormalizer.normalize(rawCommand)
        val cleanedText = normOutput.strippedText.ifBlank { normOutput.cleanedText }
        val phoneticClean = BurmesePhoneticNormalizer.normalizeBurmeseText(cleanedText).trim()

        // 2. Check for Contextual / Pronoun References
        val isContextual = isContextualReference(phoneticClean)
        if (isContextual) {
            val convContext = contextManager.getContext()
            val activePackage = convContext.activeAppPackage
            val activeLabel = convContext.activeAppNameMM

            if (!activePackage.isNullOrBlank()) {
                val appInfo = packageDiscovery.getAppByPackage(activePackage)
                if (appInfo != null && appInfo.isLaunchable && appInfo.isEnabled) {
                    val duration = System.currentTimeMillis() - startTime
                    TelemetryManager.recordEvent(
                        eventType = TelemetryEventType.APP_MATCH_SELECTED,
                        details = "contextPkg=$activePackage label=$activeLabel matchType=CONTEXT",
                        latencyMs = duration
                    )
                    return@withContext AppResolutionResult(
                        decision = ResolutionDecision.CONTEXT_RESOLVED,
                        resolvedApp = appInfo,
                        confidence = 0.95f,
                        matchType = MatchType.CONTEXT,
                        candidates = listOf(
                            ScoredAppCandidate(
                                app = appInfo,
                                score = MatchScore(0.95f, MatchType.CONTEXT, rawCommand, appInfo.applicationLabel)
                            )
                        ),
                        resolutionTimeMs = duration,
                        originalQuery = rawCommand
                    )
                }
            }
        }

        // 3. Extract Probable Application Target from Command
        val extractedTarget = extractAppTarget(cleanedText)
        if (extractedTarget.isBlank()) {
            val duration = System.currentTimeMillis() - startTime
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_MATCH_REJECTED,
                details = "reason=EMPTY_TARGET",
                latencyMs = duration
            )
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.NOT_FOUND,
                resolvedApp = null,
                confidence = 0.0f,
                matchType = MatchType.NONE,
                clarificationMessageMM = "ဘယ် အပလီကေးရှင်းကို ဖွင့်ပေးရမလဲ မသိပါခင်ဗျာ။",
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        }

        // 4. Retrieve Launchable Installed Applications
        val launchableApps = packageDiscovery.getAllLaunchableApps()
        if (launchableApps.isEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.NOT_FOUND,
                resolvedApp = null,
                confidence = 0.0f,
                matchType = MatchType.NONE,
                clarificationMessageMM = "ဖုန်းထဲတွင် ဖွင့်နိုင်သော အပလီကေးရှင်း မတွေ့ရှိပါခင်ဗျာ။",
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        }

        // 5. Score Candidates
        val candidates = appMatcher.scoreCandidates(extractedTarget, launchableApps)

        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.APP_CANDIDATES_FOUND,
            details = "target=$extractedTarget count=${candidates.size}"
        )

        val duration = System.currentTimeMillis() - startTime

        if (candidates.isEmpty()) {
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_MATCH_REJECTED,
                details = "target=$extractedTarget reason=NO_CANDIDATES",
                latencyMs = duration
            )
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.NOT_FOUND,
                resolvedApp = null,
                confidence = 0.0f,
                matchType = MatchType.NONE,
                clarificationMessageMM = "\"$extractedTarget\" အပလီကေးရှင်း ဖုန်းထဲတွင် မတွေ့ရှိပါခင်ဗျာ။",
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        }

        val topCandidate = candidates[0]
        val topScore = topCandidate.score.confidence

        // 6. Ambiguity Check between top candidates
        if (candidates.size > 1) {
            val secondCandidate = candidates[1]
            val scoreDelta = topScore - secondCandidate.score.confidence

            // If both candidates have high/moderate scores and are very close (e.g. "Facebook" and "Facebook Lite")
            if (topScore >= MINIMUM_CONFIDENCE_THRESHOLD && scoreDelta < AMBIGUITY_DELTA_THRESHOLD) {
                val label1 = topCandidate.app.applicationLabel
                val label2 = secondCandidate.app.applicationLabel

                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_MATCH_AMBIGUOUS,
                    details = "c1=$label1 (${topScore}) c2=$label2 (${secondCandidate.score.confidence})",
                    latencyMs = duration
                )

                val messageMM = "$label1 နဲ့ $label2 နှစ်ခုတွေ့ပါတယ်။ ဘယ်ဟာကို ဖွင့်ပေးရမလဲခင်ဗျာ?"
                return@withContext AppResolutionResult(
                    decision = ResolutionDecision.ASK_CLARIFICATION,
                    resolvedApp = null,
                    confidence = topScore,
                    matchType = topCandidate.score.matchType,
                    candidates = candidates.take(3),
                    clarificationMessageMM = messageMM,
                    resolutionTimeMs = duration,
                    originalQuery = rawCommand
                )
            }
        }

        // 7. Confidence Gating
        if (topScore >= HIGH_CONFIDENCE_THRESHOLD) {
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_MATCH_SELECTED,
                details = "pkg=${topCandidate.app.packageName} label=${topCandidate.app.applicationLabel} confidence=$topScore",
                latencyMs = duration
            )
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.LAUNCH,
                resolvedApp = topCandidate.app,
                confidence = topScore,
                matchType = topCandidate.score.matchType,
                candidates = candidates,
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        } else if (topScore >= MINIMUM_CONFIDENCE_THRESHOLD) {
            // Single candidate with moderate score (e.g. substring match)
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_MATCH_SELECTED,
                details = "pkg=${topCandidate.app.packageName} label=${topCandidate.app.applicationLabel} confidence=$topScore moderate=true",
                latencyMs = duration
            )
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.LAUNCH,
                resolvedApp = topCandidate.app,
                confidence = topScore,
                matchType = topCandidate.score.matchType,
                candidates = candidates,
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        } else {
            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_MATCH_REJECTED,
                details = "pkg=${topCandidate.app.packageName} confidence=$topScore reason=LOW_CONFIDENCE",
                latencyMs = duration
            )
            return@withContext AppResolutionResult(
                decision = ResolutionDecision.REJECT_LOW_CONFIDENCE,
                resolvedApp = null,
                confidence = topScore,
                matchType = topCandidate.score.matchType,
                candidates = candidates,
                clarificationMessageMM = "\"$extractedTarget\" နှင့် ကိုက်ညီသော အပလီကေးရှင်းကို သေချာစွာ ရှာမတွေ့ပါခင်ဗျာ။",
                resolutionTimeMs = duration,
                originalQuery = rawCommand
            )
        }
    }

    /**
     * Check if the phrase is a contextual pronoun/anaphoric reference
     */
    private fun isContextualReference(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return CONTEXT_PRONOUNS.any { pronoun ->
            lower == pronoun || lower.startsWith(pronoun) || lower.endsWith(pronoun)
        }
    }

    /**
     * Extract target application query string by removing Myanmar action verbs and fillers.
     */
    fun extractAppTarget(rawInput: String): String {
        var target = rawInput.trim()

        for (stripper in ACTION_STRIPPERS) {
            if (target.endsWith(stripper)) {
                target = target.substring(0, target.length - stripper.length).trim()
            }
            if (target.startsWith(stripper)) {
                target = target.substring(stripper.length).trim()
            }
        }

        // Remove trailing "ကို" or "အား"
        if (target.endsWith(" ကို") || target.endsWith("ကို")) {
            target = target.removeSuffix("ကို").removeSuffix(" ").trim()
        }

        return target
    }
}
