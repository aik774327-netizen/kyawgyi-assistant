package com.example.engine

import android.content.Context
import com.example.data.LocalMemoryStore
import com.example.discovery.DynamicAppResolver
import com.example.discovery.DynamicPackageDiscovery
import com.example.discovery.InstalledAppInfo
import com.example.discovery.ResolutionDecision
import com.example.discovery.UniversalAppLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class AppInfoCache(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean
)

/**
 * Dynamic App Package Launcher & Cache Manager.
 * Delegates to DynamicPackageDiscovery, DynamicAppResolver, and UniversalAppLauncher.
 * Completely eliminates static package name mappings.
 */
class KyawGyiAppLauncher(private val context: Context) {

    private val packageDiscovery = DynamicPackageDiscovery.getInstance(context)
    private val appResolver = DynamicAppResolver(context, packageDiscovery)
    private val universalLauncher = UniversalAppLauncher.getInstance(context)
    private val memoryStore = LocalMemoryStore.getInstance(context)

    private val _installedApps = MutableStateFlow<List<AppInfoCache>>(emptyList())
    val installedApps: StateFlow<List<AppInfoCache>> = _installedApps.asStateFlow()

    /**
     * Scan and cache installed app packages asynchronously on Dispatchers.IO.
     */
    suspend fun refreshInstalledAppsCache() = withContext(Dispatchers.IO) {
        val apps = packageDiscovery.refreshInstalledApps("APP_LAUNCHER_REFRESH")
        _installedApps.value = apps.map {
            AppInfoCache(
                appName = it.applicationLabel,
                packageName = it.packageName,
                isSystemApp = it.isSystemApp
            )
        }
    }

    /**
     * Resolve package name from Burmese speech input or package search query dynamically.
     */
    suspend fun resolvePackageName(queryText: String): String? = withContext(Dispatchers.Default) {
        // Check user-taught vocabulary from LocalMemoryStore first if custom alias exists
        val learnedAlias = memoryStore.findAlias(queryText)
        if (learnedAlias != null && (learnedAlias.actionType == "OPEN_APP" || learnedAlias.targetPayload.contains("."))) {
            val app = packageDiscovery.getAppByPackage(learnedAlias.targetPayload)
            if (app != null && app.isLaunchable) {
                return@withContext app.packageName
            }
            if (context.packageManager.getLaunchIntentForPackage(learnedAlias.targetPayload) != null) {
                return@withContext learnedAlias.targetPayload
            }
        }

        // Dynamic multi-signal semantic resolution
        val resolution = appResolver.resolveApp(queryText)
        if (resolution.decision == ResolutionDecision.LAUNCH || resolution.decision == ResolutionDecision.CONTEXT_RESOLVED) {
            return@withContext resolution.resolvedApp?.packageName
        }

        null
    }

    /**
     * Safely launch package dynamically with multi-layer fallback exceptions.
     */
    fun launchPackageSafely(packageName: String): Pair<Boolean, String> {
        val result = universalLauncher.launchPackageDirectly(packageName, packageName)
        return Pair(result.success, result.userMessageMM)
    }

    /**
     * Resolve and launch command directly
     */
    suspend fun resolveAndLaunch(commandOrAppName: String): Pair<Boolean, String> {
        val result = universalLauncher.resolveAndLaunch(commandOrAppName)
        return Pair(result.success, result.userMessageMM)
    }
}
