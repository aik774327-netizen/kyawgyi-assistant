package com.example.discovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.engine.BurmesePhoneticNormalizer
import com.example.semantic.MyanmarTextNormalizer
import com.example.telemetry.TelemetryEventType
import com.example.telemetry.TelemetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Data model for dynamically discovered installed applications.
 */
data class InstalledAppInfo(
    val packageName: String,
    val applicationLabel: String,
    val normalizedLabel: String,
    val isLaunchable: Boolean,
    val isEnabled: Boolean,
    val isSystemApp: Boolean,
    val flags: Int = 0
)

/**
 * Performance metrics for dynamic package discovery and indexing.
 */
data class DiscoveryMetrics(
    val discoveryTimeMs: Long = 0L,
    val indexedAppCount: Int = 0,
    val launchableAppCount: Int = 0,
    val lastRefreshTimestamp: Long = 0L
)

/**
 * DynamicPackageDiscovery
 * 
 * Uses Android PackageManager at runtime to discover, filter, and index installed applications.
 * Replaces all hardcoded package name maps with real-time on-device registry discovery.
 */
class DynamicPackageDiscovery(
    private val context: Context,
    private val textNormalizer: MyanmarTextNormalizer = MyanmarTextNormalizer()
) {

    companion object {
        private const val TAG = "DynamicPackageDiscovery"

        @Volatile
        private var instance: DynamicPackageDiscovery? = null

        fun getInstance(context: Context): DynamicPackageDiscovery {
            return instance ?: synchronized(this) {
                instance ?: DynamicPackageDiscovery(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _discoveryMetrics = MutableStateFlow(DiscoveryMetrics())
    val discoveryMetrics: StateFlow<DiscoveryMetrics> = _discoveryMetrics.asStateFlow()

    private val packageMap = ConcurrentHashMap<String, InstalledAppInfo>()
    private val normalizedIndex = ConcurrentHashMap<String, MutableList<InstalledAppInfo>>()

    @Volatile
    private var isInitialized = false

    private var packageChangeReceiver: BroadcastReceiver? = null

    init {
        registerPackageReceiver()
    }

    /**
     * Register broadcast receiver for package installs, removals, and changes
     */
    private fun registerPackageReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }

            packageChangeReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    val uri = intent.data
                    val pkgName = uri?.schemeSpecificPart ?: return
                    Log.d(TAG, "Package state changed: $pkgName, action=$action")

                    scope.launch {
                        refreshInstalledApps(reason = "BROADCAST_$action")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(packageChangeReceiver, filter)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to register package change receiver: ${e.message}")
        }
    }

    /**
     * Unregister package receiver on shutdown
     */
    fun unregister() {
        packageChangeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (ignored: Throwable) {}
            packageChangeReceiver = null
        }
    }

    /**
     * Discover installed applications dynamically and populate the in-memory registry.
     */
    suspend fun refreshInstalledApps(reason: String = "MANUAL"): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        TelemetryManager.recordEvent(
            eventType = TelemetryEventType.APP_DISCOVERY_STARTED,
            details = "reason=$reason"
        )

        mutex.withLock {
            val pm = context.packageManager
            val discoveredList = mutableListOf<InstalledAppInfo>()
            val newPackageMap = mutableMapOf<String, InstalledAppInfo>()
            val newNormalizedIndex = mutableMapOf<String, MutableList<InstalledAppInfo>>()

            try {
                // 1. Query launcher activities
                val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                val launchablePackages = mutableSetOf<String>()

                for (ri in resolveInfos) {
                    val pkgName = ri.activityInfo.packageName
                    launchablePackages.add(pkgName)
                    val rawLabel = ri.loadLabel(pm).toString().trim()
                    val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isEnabled = ri.activityInfo.applicationInfo.enabled

                    val norm1 = textNormalizer.normalize(rawLabel).cleanedText
                    val norm2 = BurmesePhoneticNormalizer.normalizeBurmeseText(norm1).lowercase(Locale.ROOT)
                    val normalizedLabel = norm2.ifBlank { rawLabel.lowercase(Locale.ROOT) }

                    val appInfo = InstalledAppInfo(
                        packageName = pkgName,
                        applicationLabel = rawLabel,
                        normalizedLabel = normalizedLabel,
                        isLaunchable = true,
                        isEnabled = isEnabled,
                        isSystemApp = isSystem,
                        flags = ri.activityInfo.applicationInfo.flags
                    )

                    newPackageMap[pkgName] = appInfo
                    newNormalizedIndex.getOrPut(normalizedLabel) { mutableListOf() }.add(appInfo)
                }

                // 2. Query all installed applications to capture other apps
                val installedApps = pm.getInstalledApplications(0)
                for (app in installedApps) {
                    val pkgName = app.packageName
                    if (!newPackageMap.containsKey(pkgName)) {
                        val hasLaunchIntent = pm.getLaunchIntentForPackage(pkgName) != null
                        val rawLabel = pm.getApplicationLabel(app).toString().trim()
                        val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                        val norm1 = textNormalizer.normalize(rawLabel).cleanedText
                        val norm2 = BurmesePhoneticNormalizer.normalizeBurmeseText(norm1).lowercase(Locale.ROOT)
                        val normalizedLabel = norm2.ifBlank { rawLabel.lowercase(Locale.ROOT) }

                        val appInfo = InstalledAppInfo(
                            packageName = pkgName,
                            applicationLabel = rawLabel,
                            normalizedLabel = normalizedLabel,
                            isLaunchable = hasLaunchIntent,
                            isEnabled = app.enabled,
                            isSystemApp = isSystem,
                            flags = app.flags
                        )

                        newPackageMap[pkgName] = appInfo
                        if (hasLaunchIntent) {
                            newNormalizedIndex.getOrPut(normalizedLabel) { mutableListOf() }.add(appInfo)
                        }
                    }
                }

                discoveredList.addAll(newPackageMap.values)

                packageMap.clear()
                packageMap.putAll(newPackageMap)

                normalizedIndex.clear()
                normalizedIndex.putAll(newNormalizedIndex)

                _installedApps.value = discoveredList
                isInitialized = true

                val duration = System.currentTimeMillis() - startTime
                val launchableCount = discoveredList.count { it.isLaunchable && it.isEnabled }

                _discoveryMetrics.value = DiscoveryMetrics(
                    discoveryTimeMs = duration,
                    indexedAppCount = discoveredList.size,
                    launchableAppCount = launchableCount,
                    lastRefreshTimestamp = System.currentTimeMillis()
                )

                TelemetryManager.recordEvent(
                    eventType = TelemetryEventType.APP_DISCOVERY_COMPLETED,
                    details = "total=${discoveredList.size} launchable=$launchableCount",
                    latencyMs = duration
                )

                Log.i(TAG, "Discovered ${discoveredList.size} apps ($launchableCount launchable) in ${duration}ms")
            } catch (e: Throwable) {
                Log.e(TAG, "Error discovering packages: ${e.message}", e)
            }

            discoveredList
        }
    }

    /**
     * Get all launchable and enabled applications.
     */
    suspend fun getAllLaunchableApps(): List<InstalledAppInfo> {
        if (!isInitialized || _installedApps.value.isEmpty()) {
            refreshInstalledApps("INITIAL_QUERY")
        }
        return _installedApps.value.filter { it.isLaunchable && it.isEnabled }
    }

    /**
     * Lookup an installed app by exact package name.
     */
    suspend fun getAppByPackage(packageName: String): InstalledAppInfo? {
        if (!isInitialized) {
            refreshInstalledApps("PACKAGE_LOOKUP")
        }
        return packageMap[packageName]
    }

    /**
     * Check if registry has been initialized.
     */
    fun isReady(): Boolean = isInitialized
}
