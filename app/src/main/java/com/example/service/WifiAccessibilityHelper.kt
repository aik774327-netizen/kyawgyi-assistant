package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * WifiAccessibilityHelper
 *
 * Dedicated Accessibility Service Helper for Android 10+ (API 29+) System Settings Wi-Fi Panel:
 * 1. Detects when the Android System Settings Wi-Fi Panel (`Settings.Panel.ACTION_WIFI`) or Quick Settings
 *    is displayed on screen.
 * 2. Scans the active window's `AccessibilityNodeInfo` hierarchy to identify the primary Wi-Fi switch / toggle.
 * 3. Inspects current checked state (`isChecked`) against target requested state.
 * 4. Dispatches `AccessibilityNodeInfo.ACTION_CLICK` automatically to toggle the switch.
 * 5. Includes automated timeout expiration and callback listeners for completion or retry.
 */
class WifiAccessibilityHelper private constructor() {

    companion object {
        private const val TAG = "WifiAccessibilityHelper"
        const val PANEL_EXPIRATION_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: WifiAccessibilityHelper? = null

        fun getInstance(): WifiAccessibilityHelper {
            return instance ?: synchronized(this) {
                instance ?: WifiAccessibilityHelper().also { instance = it }
            }
        }

        // Known package names that host the Android Wi-Fi panel or quick settings across standard and OEM devices
        private val KNOWN_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings.intelligence",
            "com.android.systemui",
            "com.samsung.android.settings",
            "com.samsung.android.app.telephonyui",
            "com.miui.securitycenter",
            "com.miui.personalassistant",
            "com.coloros.wirelesssettings",
            "com.oplus.wirelesssettings",
            "com.transsion.wirelesssettings",
            "com.huawei.systemmanager"
        )
    }

    interface OnWifiToggleListener {
        fun onWifiToggleSuccess(targetState: Boolean)
        fun onWifiToggleFailed(reason: String)
    }

    @Volatile
    private var pendingTargetState: Boolean? = null

    @Volatile
    private var pendingRequestTimestamp: Long = 0L

    private var toggleListener: OnWifiToggleListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fallbackTriggered = false

    /**
     * Registers a request to automatically click the Wi-Fi toggle when the system panel appears.
     *
     * @param targetEnable True to turn Wi-Fi ON, False to turn Wi-Fi OFF.
     * @param listener Optional callback to observe success or failure.
     */
    fun queueWifiToggleRequest(targetEnable: Boolean, listener: OnWifiToggleListener? = null) {
        pendingTargetState = targetEnable
        pendingRequestTimestamp = System.currentTimeMillis()
        toggleListener = listener
        fallbackTriggered = false
        Log.i(TAG, "Queued automated Wi-Fi toggle request: targetEnable=$targetEnable")

        // Schedule timeout expiration & secondary fallback if OEM panel node is unreachable
        mainHandler.postDelayed({
            if (hasPendingRequest()) {
                Log.w(TAG, "Automated Wi-Fi toggle request timed out after ${PANEL_EXPIRATION_TIMEOUT_MS}ms")
                toggleListener?.onWifiToggleFailed("Settings panel interaction timed out")
                clearPendingRequest()
            }
        }, PANEL_EXPIRATION_TIMEOUT_MS)
    }

    /**
     * Checks if there is an active, unexpired Wi-Fi toggle request pending.
     */
    fun hasPendingRequest(): Boolean {
        val state = pendingTargetState ?: return false
        val isFresh = (System.currentTimeMillis() - pendingRequestTimestamp) < PANEL_EXPIRATION_TIMEOUT_MS
        return isFresh
    }

    fun clearPendingRequest() {
        pendingTargetState = null
        pendingRequestTimestamp = 0L
        toggleListener = null
        fallbackTriggered = false
    }

    /**
     * Invoked from `AccessibilityService.onAccessibilityEvent`.
     * Inspects the event and triggers automatic node searching and clicking.
     */
    fun handleAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent): Boolean {
        if (!hasPendingRequest()) return false

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return false
        }

        val packageName = event.packageName?.toString() ?: ""
        val isSettingsWindow = KNOWN_SETTINGS_PACKAGES.any { packageName.contains(it, ignoreCase = true) } ||
                packageName.contains("settings", ignoreCase = true) ||
                packageName.contains("systemui", ignoreCase = true) ||
                packageName.contains("wireless", ignoreCase = true) ||
                packageName.contains("controlcenter", ignoreCase = true)

        if (!isSettingsWindow) {
            return false
        }

        val targetState = pendingTargetState ?: return false
        val rootNode = service.rootInActiveWindow ?: return false

        Log.i(TAG, "Settings panel detected in package [$packageName]. Searching for Wi-Fi toggle node with OEM awareness...")
        val executed = executeNodeToggle(rootNode, targetState)
        if (!executed && !fallbackTriggered) {
            // Secondary Fallback: If OEM Quick Settings or Panel (e.g. HyperOS / One UI) node search fails,
            // trigger direct full System Settings Intent so the user or accessibility engine can directly toggle
            triggerDirectSettingsFallback(service)
        }
        return executed
    }

    /**
     * Secondary Fallback: Direct Intent to System Wi-Fi Settings if panel node was not reachable
     */
    private fun triggerDirectSettingsFallback(service: AccessibilityService) {
        val elapsed = System.currentTimeMillis() - pendingRequestTimestamp
        if (elapsed < 2000L) {
            // Allow up to 2 seconds for panel animations before declaring node missing
            return
        }
        fallbackTriggered = true
        Log.w(TAG, "[SECONDARY FALLBACK] OEM Panel node search exhausted. Launching Direct Settings Intent...")
        try {
            val directIntent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(directIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch secondary Wi-Fi settings fallback intent: ${e.message}")
        }
    }

    /**
     * Recursively traverses and triggers click on the Wi-Fi toggle node.
     */
    private fun executeNodeToggle(rootNode: AccessibilityNodeInfo, targetState: Boolean): Boolean {
        val switchNode = locateWifiSwitchNode(rootNode)
        if (switchNode == null) {
            Log.d(TAG, "Wi-Fi switch node not found in current node hierarchy.")
            return false
        }

        val isChecked = switchNode.isChecked
        Log.i(TAG, "Found Wi-Fi switch node: isChecked=$isChecked, targetState=$targetState, isClickable=${switchNode.isClickable}")

        if (isChecked == targetState) {
            Log.i(TAG, "Wi-Fi switch is already in the requested state ($isChecked). Completing operation.")
            toggleListener?.onWifiToggleSuccess(targetState)
            clearPendingRequest()
            return true
        }

        // Perform click on switch itself or on clickable parent container
        val clickSuccessful = performClickAction(switchNode)
        if (clickSuccessful) {
            Log.i(TAG, "ACTION_CLICK successfully executed on Wi-Fi toggle.")
            toggleListener?.onWifiToggleSuccess(targetState)
            clearPendingRequest()
            return true
        } else {
            Log.w(TAG, "Failed to perform ACTION_CLICK on Wi-Fi toggle node.")
            return false
        }
    }

    /**
     * Performs click action on the node, traversing up to clickable parents if node is not directly clickable.
     */
    private fun performClickAction(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        // Fallback: try click anyway
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Recursively locates the Wi-Fi toggle switch node within the active window hierarchy.
     */
    fun locateWifiSwitchNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isCheckableOrSwitch = node.isCheckable ||
                className.contains("Switch", ignoreCase = true) ||
                className.contains("ToggleButton", ignoreCase = true) ||
                className.contains("CompoundButton", ignoreCase = true)

        if (isCheckableOrSwitch) {
            // Check direct attributes including OEM specific identifiers
            val matchesWifiKeyword = text.contains("Wi-Fi", ignoreCase = true) ||
                    text.contains("Wifi", ignoreCase = true) ||
                    text.contains("ဝိုင်ဖိုင်", ignoreCase = true) ||
                    text.contains("WLAN", ignoreCase = true) ||
                    contentDesc.contains("Wi-Fi", ignoreCase = true) ||
                    contentDesc.contains("Wifi", ignoreCase = true) ||
                    contentDesc.contains("WLAN", ignoreCase = true) ||
                    viewId.contains("wifi") ||
                    viewId.contains("wlan") ||
                    viewId.contains("switch_widget") ||
                    // Xiaomi HyperOS / MIUI quick settings node IDs
                    viewId.contains("miui:id/switch") ||
                    viewId.contains("toggle_wifi") ||
                    // Samsung One UI node IDs
                    viewId.contains("sec_switch") ||
                    viewId.contains("switch_button") ||
                    // Oppo ColorOS / Realme UI node IDs
                    viewId.contains("coui_switch") ||
                    viewId.contains("color_switch")

            if (matchesWifiKeyword) {
                return node
            }

            // Check parent and siblings for Wi-Fi label context
            val parent = node.parent
            if (parent != null) {
                val parentText = parent.text?.toString() ?: ""
                val parentDesc = parent.contentDescription?.toString() ?: ""
                if (parentText.contains("Wi-Fi", ignoreCase = true) || parentDesc.contains("Wi-Fi", ignoreCase = true) ||
                    parentText.contains("WLAN", ignoreCase = true) || parentDesc.contains("WLAN", ignoreCase = true)) {
                    return node
                }

                // Check siblings
                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != null && sibling != node) {
                        val sText = sibling.text?.toString() ?: ""
                        val sDesc = sibling.contentDescription?.toString() ?: ""
                        if (sText.contains("Wi-Fi", ignoreCase = true) || sDesc.contains("Wi-Fi", ignoreCase = true) ||
                            sText.contains("WLAN", ignoreCase = true) || sDesc.contains("WLAN", ignoreCase = true)) {
                            return node
                        }
                    }
                }
            }

            // In Android 10-14 Settings Panel (ACTION_WIFI / ACTION_INTERNET_CONNECTIVITY),
            // if the window title or container is Wi-Fi/Internet, the first checkable switch is the primary toggle
            return node
        }

        // Recursively search child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = locateWifiSwitchNode(child)
            if (found != null) {
                return found
            }
        }

        return null
    }
}
