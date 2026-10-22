package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class AccessibilityDiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val eventTypeCode: Int,
    val packageName: String,
    val className: String,
    val matchedAppKey: String?,
    val matchedBurmeseAliases: List<String>,
    val isAppPackagesMatch: Boolean,
    val intendedAppPackage: String?,
    val isIntentMatchConfirmed: Boolean,
    val confirmationStatusMessage: String,
    val eventTextSnippet: String
)

class KyawGyiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KyawGyiAccessibility"
        private const val INTENT_TIMEOUT_MS = 15_000L // 15 seconds window for app control intent matching

        @Volatile
        var instance: KyawGyiAccessibilityService? = null
            private set

        @Volatile
        private var lastIntendedPackage: String? = null

        @Volatile
        private var lastIntendedAppKey: String? = null

        @Volatile
        private var lastIntentTimestamp: Long = 0L

        @Volatile
        private var lastIntentSource: String? = null

        // Automated Wi-Fi & Bluetooth panel toggle delegation
        fun requestAutomatedWifiToggle(targetEnable: Boolean) {
            WifiAccessibilityHelper.getInstance().queueWifiToggleRequest(targetEnable)
            Log.i(TAG, "[AUTOMATED WIFI] Requested automated toggle: targetEnable=$targetEnable")
        }

        fun hasPendingWifiToggle(): Boolean {
            return WifiAccessibilityHelper.getInstance().hasPendingRequest()
        }

        fun requestAutomatedBluetoothToggle(targetEnable: Boolean) {
            BluetoothAccessibilityHelper.getInstance().queueBluetoothToggleRequest(targetEnable)
            Log.i(TAG, "[AUTOMATED BLUETOOTH] Requested automated toggle: targetEnable=$targetEnable")
        }

        fun hasPendingBluetoothToggle(): Boolean {
            return BluetoothAccessibilityHelper.getInstance().hasPendingRequest()
        }

        private val diagnosticBuffer = CopyOnWriteArrayList<AccessibilityDiagnosticEntry>()
        private val _diagnosticLogs = MutableStateFlow<List<AccessibilityDiagnosticEntry>>(emptyList())
        val diagnosticLogs: StateFlow<List<AccessibilityDiagnosticEntry>> = _diagnosticLogs.asStateFlow()

        /**
         * Record an app control intent targeted by voice command or UI action.
         * Used to cross-reference with subsequent AccessibilityEvents.
         */
        fun recordAppControlIntent(targetPackage: String, appKey: String? = null, source: String = "VOICE_COMMAND") {
            lastIntendedPackage = targetPackage.trim()
            lastIntendedAppKey = appKey?.trim() ?: MyanmarCommands.APP_PACKAGES.entries.firstOrNull {
                it.value.equals(targetPackage.trim(), ignoreCase = true)
            }?.key
            lastIntentTimestamp = System.currentTimeMillis()
            lastIntentSource = source
            Log.d(TAG, "[DIAGNOSTIC INTENT RECORDED] Target: $lastIntendedPackage (Key: $lastIntendedAppKey, Source: $source)")
        }

        fun getRecentDiagnosticLogs(): List<AccessibilityDiagnosticEntry> = _diagnosticLogs.value

        @Synchronized
        fun clearDiagnosticLogs() {
            diagnosticBuffer.clear()
            _diagnosticLogs.value = emptyList()
        }

        // ITEM 11 — Narrowly Scoped Accessibility Boundary Policy
        // Prohibits automated UI clicking on sensitive financial, banking, or credential dialogs.
        private val RESTRICTED_AUTOMATION_PACKAGES = setOf(
            "com.kbzbank.kpaycustomer",
            "com.wave.money",
            "cb.ibank",
            "com.ayabank.ayapay",
            "com.google.android.apps.nbu.paisa.user",
            "com.android.vpndialogs",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        fun isPackageAllowedForAccessibilityAutomation(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return false
            val norm = packageName.trim().lowercase(Locale.ROOT)
            return norm !in RESTRICTED_AUTOMATION_PACKAGES &&
                !norm.contains("bank") &&
                !norm.contains("wallet") &&
                !norm.contains("keychain")
        }
    }

    private var appLauncher: com.example.engine.KyawGyiAppLauncher? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        appLauncher = com.example.engine.KyawGyiAppLauncher(this)
        Log.i(TAG, "Kyaw Gyi Accessibility Service Connected - Diagnostic Event Tracking Enabled")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventTypeInt = event.eventType
        val eventTypeName = formatAccessibilityEventType(eventTypeInt)
        val capturedPackageName = event.packageName?.toString() ?: "unknown.package"
        val capturedClassName = event.className?.toString() ?: "unknown.class"
        val eventText = try {
            event.text?.filterNotNull()?.joinToString(" ") ?: ""
        } catch (_: Exception) {
            ""
        }
        val contentDesc = event.contentDescription?.toString() ?: ""
        val textSnippet = when {
            eventText.isNotBlank() -> eventText
            contentDesc.isNotBlank() -> "[Desc: $contentDesc]"
            else -> "[None]"
        }

        // ---------------------------------------------------------------------------------
        // Cross-reference captured packageName with MyanmarCommands.APP_PACKAGES
        // ---------------------------------------------------------------------------------
        val matchedAppEntries = MyanmarCommands.APP_PACKAGES.filter { (_, pkg) ->
            pkg.equals(capturedPackageName, ignoreCase = true)
        }
        val matchedAppKey = matchedAppEntries.keys.firstOrNull()
        val isAppPackagesMatch = matchedAppKey != null

        // Look up registered Burmese aliases for this app
        val matchedBurmeseAliases = if (matchedAppKey != null) {
            MyanmarCommands.APP_NAME_MAPPINGS.filter { (_, key) ->
                key.equals(matchedAppKey, ignoreCase = true)
            }.keys.toList()
        } else {
            emptyList()
        }

        // ---------------------------------------------------------------------------------
        // Cross-reference with the most recent App Control Intent
        // ---------------------------------------------------------------------------------
        val now = System.currentTimeMillis()
        val hasRecentIntent = lastIntendedPackage != null && (now - lastIntentTimestamp) <= INTENT_TIMEOUT_MS
        val isIntentMatchConfirmed = hasRecentIntent && capturedPackageName.equals(lastIntendedPackage, ignoreCase = true)

        val confirmationStatus = when {
            isIntentMatchConfirmed -> {
                "CONFIRMED: Captured package '$capturedPackageName' matches intended app control target '$lastIntendedPackage' (App Key: '$matchedAppKey')"
            }
            hasRecentIntent -> {
                "MISMATCH/INTERMEDIATE: Captured package '$capturedPackageName' differs from expected target '$lastIntendedPackage'"
            }
            isAppPackagesMatch -> {
                "KNOWN APP DETECTED: '$capturedPackageName' registered in APP_PACKAGES as '$matchedAppKey' (Idle/System Trigger)"
            }
            else -> {
                "EXTERNAL/SYSTEM EVENT: Package '$capturedPackageName' not present in APP_PACKAGES"
            }
        }

        // ---------------------------------------------------------------------------------
        // Output formatted Diagnostic Log to Logcat
        // ---------------------------------------------------------------------------------
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timestampStr = dateFormat.format(Date(now))

        val diagnosticLogText = buildString {
            appendLine("╔════════════════════════════════════════════════════════════════════════════════")
            appendLine("║ [ACCESSIBILITY EVENT DIAGNOSTIC] $timestampStr")
            appendLine("╠════════════════════════════════════════════════════════════════════════════════")
            appendLine("║ • Event Type: $eventTypeName (Code: 0x${Integer.toHexString(eventTypeInt)})")
            appendLine("║ • Captured Package: $capturedPackageName")
            appendLine("║ • Captured Class: $capturedClassName")
            appendLine("║ • Cross-Reference APP_PACKAGES: ${if (isAppPackagesMatch) "[MATCHED: key='$matchedAppKey']" else "[NOT REGISTERED]"}")
            if (matchedBurmeseAliases.isNotEmpty()) {
                appendLine("║     └─ Burmese Trigger Phrases: ${matchedBurmeseAliases.joinToString(", ")}")
            }
            appendLine("║ • App Control Intent Verification: $confirmationStatus")
            if (hasRecentIntent) {
                appendLine("║     └─ Expected Package: $lastIntendedPackage (Source: $lastIntentSource, Age: ${now - lastIntentTimestamp}ms)")
                appendLine("║     └─ Match Status: ${if (isIntentMatchConfirmed) "✅ MATCH SUCCESS" else "⚠️ DIFFERENT PACKAGE"}")
            }
            if (textSnippet.isNotBlank() && textSnippet != "[None]") {
                appendLine("║ • Event Text Snippet: $textSnippet")
            }
            append("╚════════════════════════════════════════════════════════════════════════════════")
        }

        if (isIntentMatchConfirmed || eventTypeInt == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.i(TAG, diagnosticLogText)
        } else {
            Log.d(TAG, diagnosticLogText)
        }

        // Store into in-memory ring buffer (up to 100 entries)
        val entry = AccessibilityDiagnosticEntry(
            timestamp = now,
            eventType = eventTypeName,
            eventTypeCode = eventTypeInt,
            packageName = capturedPackageName,
            className = capturedClassName,
            matchedAppKey = matchedAppKey,
            matchedBurmeseAliases = matchedBurmeseAliases,
            isAppPackagesMatch = isAppPackagesMatch,
            intendedAppPackage = if (hasRecentIntent) lastIntendedPackage else null,
            isIntentMatchConfirmed = isIntentMatchConfirmed,
            confirmationStatusMessage = confirmationStatus,
            eventTextSnippet = textSnippet
        )

        synchronized(diagnosticBuffer) {
            if (diagnosticBuffer.size >= 100) {
                diagnosticBuffer.removeAt(0)
            }
            diagnosticBuffer.add(entry)
            _diagnosticLogs.value = diagnosticBuffer.toList()
        }

        // Automated Wi-Fi & Bluetooth Fallback: Check if Settings Panel is open and delegate to Helpers
        if (WifiAccessibilityHelper.getInstance().hasPendingRequest()) {
            WifiAccessibilityHelper.getInstance().handleAccessibilityEvent(this, event)
        }
        if (BluetoothAccessibilityHelper.getInstance().hasPendingRequest()) {
            BluetoothAccessibilityHelper.getInstance().handleAccessibilityEvent(this, event)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Kyaw Gyi Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
        Log.i(TAG, "Kyaw Gyi Accessibility Service Destroyed")
    }

    fun executeVoiceCommand(spokenText: String): String {
        val coordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
        if (coordinator.hasPendingAction()) {
            if (coordinator.isConfirmation(spokenText)) {
                val outcome = coordinator.executeConfirmed(this)
                return outcome.userMessage
            } else if (coordinator.isCancellation(spokenText)) {
                val outcome = coordinator.cancelPending("Accessibility voice cancellation")
                return outcome.userMessage
            }
        }

        val (commandType, payload) = MyanmarCommands.parseCommand(spokenText)
        Log.d(TAG, "Executing Parsed Command: Type=$commandType, Payload=$payload")

        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager

        return when (commandType) {
            MyanmarCommandType.HOME -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                if (success) "Performed Action: HOME" else "Failed to perform HOME action"
            }

            MyanmarCommandType.BACK -> {
                val success = performGlobalAction(GLOBAL_ACTION_BACK)
                if (success) "Performed Action: BACK" else "Failed to perform BACK action"
            }

            MyanmarCommandType.RECENTS -> {
                val success = performGlobalAction(GLOBAL_ACTION_RECENTS)
                if (success) "Performed Action: RECENTS" else "Failed to perform RECENTS action"
            }

            MyanmarCommandType.NOTIFICATIONS -> {
                val success = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                if (success) "Performed Action: NOTIFICATIONS" else "Failed to open Notifications"
            }

            MyanmarCommandType.QUICK_SETTINGS -> {
                val success = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                if (success) "Performed Action: QUICK SETTINGS" else "Failed to open Quick Settings"
            }

            MyanmarCommandType.LOCK_SCREEN -> {
                val success = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
                if (success) "Performed Action: LOCK SCREEN" else "Lock screen action trigger complete"
            }

            MyanmarCommandType.VOLUME_UP -> {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                "Performed Action: VOLUME UP"
            }

            MyanmarCommandType.VOLUME_DOWN -> {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                "Performed Action: VOLUME DOWN"
            }

            MyanmarCommandType.SCROLL -> {
                if (payload == "UP") {
                    performScroll(ScrollDirection.UP)
                    "Performed Scroll: UP"
                } else {
                    performScroll(ScrollDirection.DOWN)
                    "Performed Scroll: DOWN"
                }
            }

            MyanmarCommandType.TAP, MyanmarCommandType.LONG_PRESS -> {
                performTapAtScreenCenter(isLongPress = (commandType == MyanmarCommandType.LONG_PRESS))
                "Performed Gesture: ${commandType.name}"
            }

            MyanmarCommandType.OPEN_APP -> {
                val launched = openAppByPackageOrQuery(payload)
                if (launched) "Opening App: $payload" else "App matching '$payload' not found"
            }

            MyanmarCommandType.CLOSE_APP -> {
                val success = performGlobalAction(GLOBAL_ACTION_HOME)
                if (success) "Closed App: $payload (Returned to Home)" else "Failed to close app $payload"
            }

            MyanmarCommandType.PLAY_MUSIC -> {
                val executor = com.example.actions.KyawGyiActionExecutor(this)
                val res = executor.playLocalAudioTrack(spokenText)
                res.userMessage
            }

            MyanmarCommandType.PAUSE_MUSIC -> {
                val player = com.example.audio.KyawGyiMusicPlayer.getInstance(this)
                player.pause()
            }

            MyanmarCommandType.STOP_MUSIC -> {
                val player = com.example.audio.KyawGyiMusicPlayer.getInstance(this)
                player.stop()
            }

            MyanmarCommandType.CALL_CONTACT -> {
                val executor = com.example.actions.KyawGyiActionExecutor(this)
                val res = executor.callContact(spokenText)
                res.userMessage
            }

            MyanmarCommandType.READ_SCREEN -> {
                val speech = KyawGyiVoiceSession.readScreenContentAloud(this)
                speech
            }

            MyanmarCommandType.CUSTOM -> {
                val launched = openAppByPackageOrQuery(payload)
                if (launched) "Executing Shortcut Target: $payload" else "Shortcut target '$payload' executed"
            }

            MyanmarCommandType.WAKE_WORD -> {
                "Wake Word 'ကျော်ကြီး' Recognized! Listening for command..."
            }

            // FIX-04: hardware toggles delegate to the shared action
            // executor; its (now honest) userMessage is the result —
            // a panel-only outcome reads as panel-only here too.
            MyanmarCommandType.WIFI_TOGGLE -> {
                com.example.actions.KyawGyiActionExecutor(this).setWifi(payload == "ON").userMessage
            }

            MyanmarCommandType.BLUETOOTH_TOGGLE -> {
                com.example.actions.KyawGyiActionExecutor(this).setBluetooth(payload == "ON").userMessage
            }

            MyanmarCommandType.FLASHLIGHT_TOGGLE -> {
                com.example.actions.KyawGyiActionExecutor(this).setFlashlight(payload == "ON").userMessage
            }

            else -> {
                "Command received: $spokenText"
            }
        }
    }

    private fun performTapAtScreenCenter(isLongPress: Boolean = false) {
        val displayMetrics = resources.displayMetrics
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        val path = Path().apply {
            moveTo(centerX, centerY)
        }

        val duration = if (isLongPress) 800L else 100L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun performScroll(direction: ScrollDirection) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val middleX = screenWidth / 2f
        val middleY = screenHeight / 2f
        val distance = screenHeight * 0.35f

        val path = Path()
        when (direction) {
            ScrollDirection.DOWN -> {
                path.moveTo(middleX, middleY + distance)
                path.lineTo(middleX, middleY - distance)
            }

            ScrollDirection.UP -> {
                path.moveTo(middleX, middleY - distance)
                path.lineTo(middleX, middleY + distance)
            }

            ScrollDirection.LEFT -> {
                path.moveTo(middleX + (screenWidth * 0.3f), middleY)
                path.lineTo(middleX - (screenWidth * 0.3f), middleY)
            }

            ScrollDirection.RIGHT -> {
                path.moveTo(middleX - (screenWidth * 0.3f), middleY)
                path.lineTo(middleX + (screenWidth * 0.3f), middleY)
            }
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Scroll Gesture completed successfully")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Scroll Gesture cancelled")
            }
        }, null)
    }

    /**
     * FIX-03: navigation entry point for the SmartRouter voice path
     * (previously Back/Scroll existed only via this service's own
     * bubble-driven command table). Returns true when the action was
     * DISPATCHED; scroll gestures complete asynchronously, so true is
     * a dispatch result, not visual confirmation.
     */
    fun performNavigationAction(action: com.example.router.NavigationAction): Boolean {
        return when (action) {
            com.example.router.NavigationAction.BACK ->
                performGlobalAction(GLOBAL_ACTION_BACK)
            com.example.router.NavigationAction.SCROLL_UP -> {
                performScroll(ScrollDirection.UP)
                true
            }
            com.example.router.NavigationAction.SCROLL_DOWN -> {
                performScroll(ScrollDirection.DOWN)
                true
            }
        }
    }

    private fun openAppByPackageOrQuery(queryOrPackage: String): Boolean {
        // 1. If it's a direct package name (e.g. contains dots)
        if (queryOrPackage.contains(".")) {
            recordAppControlIntent(queryOrPackage, source = "ACCESSIBILITY_DIRECT_PACKAGE")
            val launcher = appLauncher ?: com.example.engine.KyawGyiAppLauncher(this)
            val (success, _) = launcher.launchPackageSafely(queryOrPackage)
            if (success) return true
        }

        // 2. Non-blocking Universal Dynamic App Resolution & Launch via serviceScope
        if (findAndLaunchAppByLabel(queryOrPackage)) {
            return true
        }
        serviceScope.launch {
            try {
                val universalLauncher = com.example.discovery.UniversalAppLauncher.getInstance(this@KyawGyiAccessibilityService)
                val launchResult = universalLauncher.resolveAndLaunch(queryOrPackage)
                if (launchResult.success && launchResult.packageName != null) {
                    recordAppControlIntent(
                        targetPackage = launchResult.packageName,
                        appKey = launchResult.applicationLabel ?: queryOrPackage,
                        source = "ACCESSIBILITY_DYNAMIC_RESOLVER"
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in async universal app launch: ${e.message}")
            }
        }
        return true
    }

    private fun openAppByPackageName(packageName: String): Boolean {
        return try {
            recordAppControlIntent(packageName, source = "ACCESSIBILITY_PACKAGE_NAME")
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package $packageName: ${e.message}")
            false
        }
    }

    private fun findAndLaunchAppByLabel(appLabelQuery: String): Boolean {
        return try {
            val lowerQuery = appLabelQuery.lowercase(Locale.ROOT)
            val installedApps = packageManager.getInstalledApplications(0)

            for (appInfo in installedApps) {
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue // Skip core system apps
                val label = packageManager.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT)
                if (label.contains(lowerQuery) || lowerQuery.contains(label)) {
                    recordAppControlIntent(appInfo.packageName, appKey = lowerQuery, source = "ACCESSIBILITY_LABEL_SEARCH")
                    if (openAppByPackageName(appInfo.packageName)) return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error finding app by label: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun formatAccessibilityEventType(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "TYPE_VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "TYPE_NOTIFICATION_STATE_CHANGED"
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> "TYPE_VIEW_HOVER_ENTER"
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> "TYPE_VIEW_HOVER_EXIT"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> "TYPE_TOUCH_EXPLORATION_GESTURE_START"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> "TYPE_TOUCH_EXPLORATION_GESTURE_END"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TYPE_VIEW_TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> "TYPE_ANNOUNCEMENT"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> "TYPE_GESTURE_DETECTION_START"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> "TYPE_GESTURE_DETECTION_END"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TYPE_TOUCH_INTERACTION_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TYPE_TOUCH_INTERACTION_END"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            else -> "TYPE_EVENT_0x${Integer.toHexString(eventType)}"
        }
    }

    /**
     * Extracts visible screen text by traversing the active accessibility window node hierarchy.
     * Enforces Financial App Blocklist and bounded node traversal to prevent PII leaks and binder OOMs.
     */
    fun extractCurrentScreenText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        return try {
            val activePkg = rootNode.packageName?.toString()
            if (!isPackageAllowedForAccessibilityAutomation(activePkg)) {
                Log.w(TAG, "Blocked screen text extraction on restricted financial/system package: $activePkg")
                return ""
            }
            val collectedText = mutableListOf<String>()
            traverseAccessibilityNode(rootNode, collectedText, depth = 0, maxDepth = 25, maxNodes = 500)
            collectedText.distinct().joinToString("\n")
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                runCatching { rootNode.recycle() }
            }
        }
    }

    private fun traverseAccessibilityNode(
        node: android.view.accessibility.AccessibilityNodeInfo?,
        outList: MutableList<String>,
        depth: Int = 0,
        maxDepth: Int = 25,
        maxNodes: Int = 500
    ) {
        if (node == null || depth > maxDepth || outList.size >= maxNodes || !node.isVisibleToUser) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { outList.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { outList.add(it) }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (outList.size >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try {
                traverseAccessibilityNode(child, outList, depth + 1, maxDepth, maxNodes)
            } finally {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    runCatching { child.recycle() }
                }
            }
        }
    }

    /**
     * Captures an on-screen screenshot using AccessibilityService.takeScreenshot (Android 11 / API 30+).
     * Strictly blocked when a restricted financial/banking package is in the foreground.
     */
    fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        val rootNode = rootInActiveWindow
        val activePkg = try {
            rootNode?.packageName?.toString()
        } finally {
            if (rootNode != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                runCatching { rootNode.recycle() }
            }
        }
        if (activePkg != null && !isPackageAllowedForAccessibilityAutomation(activePkg)) {
            Log.w(TAG, "Blocked Accessibility screenshot capture on restricted package: $activePkg")
            callback(null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            callback(softwareBitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Accessibility takeScreenshot failed with code $errorCode")
                            callback(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating Accessibility takeScreenshot: ${e.message}", e)
                callback(null)
            }
        } else {
            callback(null)
        }
    }

    /**
     * Extracts screen text with a secondary fallback to On-Device Google ML Kit Vision OCR
     * when Accessibility Nodes are empty (such as in Flutter, Unity, Canvas, or WebView).
     */
    fun extractScreenTextWithOcrFallback(onComplete: (String) -> Unit) {
        val nodeText = extractCurrentScreenText()
        if (nodeText.isNotBlank()) {
            onComplete(nodeText)
            return
        }

        Log.i(TAG, "Standard Accessibility nodes are empty. Attempting Hybrid Screen OCR fallback...")
        captureScreenshot { bitmap ->
            if (bitmap != null) {
                com.example.ocr.ScreenOcrEngine.extractTextAsync(bitmap, this) { ocrResult ->
                    if (ocrResult.isSuccess && ocrResult.text.isNotBlank()) {
                        Log.i(TAG, "Hybrid OCR Fallback succeeded: extracted ${ocrResult.text.length} chars (${ocrResult.scriptType}) using ${ocrResult.engineUsed}")
                        onComplete(ocrResult.text)
                    } else {
                        Log.w(TAG, "Hybrid OCR found no text or failed: ${ocrResult.technicalDiagnostic}")
                        onComplete(ocrResult.userMessageMm)
                    }
                }
            } else {
                Log.w(TAG, "Screenshot not available for OCR fallback")
                onComplete("")
            }
        }
    }
}
