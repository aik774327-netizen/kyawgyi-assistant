package com.example.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.engine.CommandContextManager
import com.example.service.KyawGyiAccessibilityService
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Structured App Launch Execution Result.
 */
data class AppLaunchResult(
    val success: Boolean,
    val packageName: String?,
    val applicationLabel: String?,
    val error: String? = null,
    val userMessageMM: String,
    val resolutionResult: AppResolutionResult? = null,
    val latencyMs: Long = 0L
)

/**
 * Universal App Launcher.
 * 
 * Safely resolves and launches applications dynamically using Android PackageManager
 * and DynamicAppResolver. Guarantees no privilege escalation, no shell execution,
 * and maintains context memory & telemetry auditing.
 */
class UniversalAppLauncher(
    private val context: Context,
    private val appResolver: DynamicAppResolver = DynamicAppResolver(context),
    private val contextManager: CommandContextManager = CommandContextManager.getInstance()
) {

    companion object {
        private const val TAG = "UniversalAppLauncher"

        @Volatile
        private var instance: UniversalAppLauncher? = null

        fun getInstance(context: Context): UniversalAppLauncher {
            return instance ?: synchronized(this) {
                instance ?: UniversalAppLauncher(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Resolve and launch an application from a natural language Myanmar command or app name.
     */
    suspend fun resolveAndLaunch(commandOrAppName: String): AppLaunchResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.APP_LAUNCH_STARTED,
            details = "query=$commandOrAppName"
        )

        // 1. Resolve Application dynamically
        val resolution = appResolver.resolveApp(commandOrAppName)

        when (resolution.decision) {
            ResolutionDecision.LAUNCH, ResolutionDecision.CONTEXT_RESOLVED -> {
                val app = resolution.resolvedApp
                if (app == null) {
                    val duration = System.currentTimeMillis() - startTime
                    TelemetryManager.recordEvent(
                        eventType = TelemetryEventType.APP_LAUNCH_FAILED,
                        details = "reason=NULL_RESOLVED_APP",
                        latencyMs = duration
                    )
                    return@withContext AppLaunchResult(
                        success = false,
                        packageName = null,
                        applicationLabel = null,
                        error = "APP_NOT_FOUND",
                        userMessageMM = "အပလီကေးရှင်း မတွေ့ရှိပါခင်ဗျာ။",
                        resolutionResult = resolution,
                        latencyMs = duration
                    )
                }

                return@withContext launchPackageDirectly(
                    packageName = app.packageName,
                    applicationLabel = app.applicationLabel,
                    resolutionResult = resolution,
                    startTime = startTime
                )
            }

            ResolutionDecision.ASK_CLARIFICATION -> {
                val duration = System.currentTimeMillis() - startTime
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_MATCH_AMBIGUOUS,
                    details = "query=$commandOrAppName candidates=${resolution.candidates.size}",
                    latencyMs = duration
                )
                return@withContext AppLaunchResult(
                    success = false,
                    packageName = null,
                    applicationLabel = null,
                    error = "AMBIGUOUS_APP_MATCH",
                    userMessageMM = resolution.clarificationMessageMM ?: "ဘယ် အပလီကေးရှင်းကို ဖွင့်ပေးရမလဲခင်ဗျာ?",
                    resolutionResult = resolution,
                    latencyMs = duration
                )
            }

            ResolutionDecision.REJECT_LOW_CONFIDENCE, ResolutionDecision.NOT_FOUND -> {
                val duration = System.currentTimeMillis() - startTime
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_LAUNCH_FAILED,
                    details = "query=$commandOrAppName confidence=${resolution.confidence}",
                    latencyMs = duration
                )
                return@withContext AppLaunchResult(
                    success = false,
                    packageName = null,
                    applicationLabel = null,
                    error = "APP_NOT_FOUND_OR_LOW_CONFIDENCE",
                    userMessageMM = resolution.clarificationMessageMM ?: "အပလီကေးရှင်းကို ဖွင့်လို့ မရသေးပါဘူးခင်ဗျာ။",
                    resolutionResult = resolution,
                    latencyMs = duration
                )
            }
        }
    }

    /**
     * Directly launch a verified package name with intent safety checks and contextual registration.
     */
    fun launchPackageDirectly(
        packageName: String,
        applicationLabel: String,
        resolutionResult: AppResolutionResult? = null,
        startTime: Long = System.currentTimeMillis()
    ): AppLaunchResult {
        return try {
            val pm = context.packageManager

            // 1. Verify package existence and enabled state
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            if (appInfo == null || !appInfo.enabled) {
                val duration = System.currentTimeMillis() - startTime
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_LAUNCH_FAILED,
                    details = "pkg=$packageName reason=PACKAGE_DISABLED_OR_NOT_FOUND",
                    latencyMs = duration
                )
                return AppLaunchResult(
                    success = false,
                    packageName = packageName,
                    applicationLabel = applicationLabel,
                    error = "PACKAGE_DISABLED_OR_NOT_FOUND",
                    userMessageMM = "$applicationLabel ကို ဖွင့်လို့ မရသေးပါဘူးခင်ဗျာ (ပိတ်ထားပါသည်)။",
                    resolutionResult = resolutionResult,
                    latencyMs = duration
                )
            }

            // 2. Obtain launch Intent
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                val duration = System.currentTimeMillis() - startTime
                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_LAUNCH_FAILED,
                    details = "pkg=$packageName reason=NO_LAUNCH_INTENT",
                    latencyMs = duration
                )
                return AppLaunchResult(
                    success = false,
                    packageName = packageName,
                    applicationLabel = applicationLabel,
                    error = "NO_LAUNCH_INTENT",
                    userMessageMM = "$applicationLabel ကို ဖွင့်လို့ မရသေးပါဘူးခင်ဗျာ။",
                    resolutionResult = resolutionResult,
                    latencyMs = duration
                )
            }

            // 3. Add intent flags
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

            // 4. Safe launch execution
            context.startActivity(launchIntent)

            // 5. Update multi-turn context
            contextManager.updateAppTarget(packageName, applicationLabel)

            // 6. Update accessibility service tracking
            try {
                KyawGyiAccessibilityService.recordAppControlIntent(
                    targetPackage = packageName,
                    appKey = applicationLabel,
                    source = "UNIVERSAL_APP_LAUNCHER"
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Accessibility intent record warning: ${e.message}")
            }

            val duration = System.currentTimeMillis() - startTime

            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_LAUNCH_SUCCESS,
                details = "pkg=$packageName label=$applicationLabel",
                latencyMs = duration
            )

            AppLaunchResult(
                success = true,
                packageName = packageName,
                applicationLabel = applicationLabel,
                error = null,
                userMessageMM = "ဟုတ်ကဲ့၊ $applicationLabel ဖွင့်ပေးလိုက်ပါပြီခင်ဗျာ။",
                resolutionResult = resolutionResult,
                latencyMs = duration
            )
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to launch package $packageName: ${e.message}", e)

            TelemetryManager.recordEvent(
                eventType = TelemetryEventType.APP_LAUNCH_FAILED,
                details = "pkg=$packageName error=${e.message}",
                latencyMs = duration
            )

            AppLaunchResult(
                success = false,
                packageName = packageName,
                applicationLabel = applicationLabel,
                error = e.message,
                userMessageMM = "ဒီ app ကို ဖွင့်လို့ မရသေးပါဘူးခင်ဗျာ။",
                resolutionResult = resolutionResult,
                latencyMs = duration
            )
        }
    }
}
