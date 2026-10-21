package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * BluetoothAccessibilityHelper
 *
 * Dedicated Accessibility Service Helper for Android 10+ (API 29+ - Android 14+) Bluetooth System Panel:
 * 1. Intercepts System Settings Panel / Bluetooth Settings when requested.
 * 2. Traverses `AccessibilityNodeInfo` hierarchy to identify the Bluetooth toggle switch.
 * 3. Inspects current checked state against requested target state.
 * 4. Dispatches `AccessibilityNodeInfo.ACTION_CLICK` to toggle Bluetooth automatically.
 * 5. Handles timeout expiration and callback listeners.
 */
class BluetoothAccessibilityHelper private constructor() {

    companion object {
        private const val TAG = "BluetoothAccessibilityHelper"
        const val PANEL_EXPIRATION_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: BluetoothAccessibilityHelper? = null

        fun getInstance(): BluetoothAccessibilityHelper {
            return instance ?: synchronized(this) {
                instance ?: BluetoothAccessibilityHelper().also { instance = it }
            }
        }

        private val KNOWN_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings.intelligence",
            "com.android.systemui",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.miui.personalassistant",
            "com.coloros.wirelesssettings",
            "com.oplus.wirelesssettings",
            "com.transsion.wirelesssettings",
            "com.huawei.systemmanager"
        )
    }

    interface OnBluetoothToggleListener {
        fun onBluetoothToggleSuccess(targetState: Boolean)
        fun onBluetoothToggleFailed(reason: String)
    }

    @Volatile
    private var pendingTargetState: Boolean? = null

    @Volatile
    private var pendingRequestTimestamp: Long = 0L

    private var toggleListener: OnBluetoothToggleListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fallbackTriggered = false

    fun queueBluetoothToggleRequest(targetEnable: Boolean, listener: OnBluetoothToggleListener? = null) {
        pendingTargetState = targetEnable
        pendingRequestTimestamp = System.currentTimeMillis()
        toggleListener = listener
        fallbackTriggered = false
        Log.i(TAG, "Queued automated Bluetooth toggle request: targetEnable=$targetEnable")

        mainHandler.postDelayed({
            if (hasPendingRequest()) {
                Log.w(TAG, "Automated Bluetooth toggle request timed out after ${PANEL_EXPIRATION_TIMEOUT_MS}ms")
                toggleListener?.onBluetoothToggleFailed("Settings panel interaction timed out")
                clearPendingRequest()
            }
        }, PANEL_EXPIRATION_TIMEOUT_MS)
    }

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
                packageName.contains("bluetooth", ignoreCase = true) ||
                packageName.contains("controlcenter", ignoreCase = true)

        if (!isSettingsWindow) {
            return false
        }

        val targetState = pendingTargetState ?: return false
        val rootNode = service.rootInActiveWindow ?: return false

        Log.i(TAG, "Settings panel detected in package [$packageName]. Searching for Bluetooth toggle node with OEM awareness...")
        val executed = executeNodeToggle(rootNode, targetState)
        if (!executed && !fallbackTriggered) {
            triggerDirectSettingsFallback(service)
        }
        return executed
    }

    /**
     * Secondary Fallback: Direct Intent to System Bluetooth Settings if panel node was not reachable
     */
    private fun triggerDirectSettingsFallback(service: AccessibilityService) {
        val elapsed = System.currentTimeMillis() - pendingRequestTimestamp
        if (elapsed < 2000L) {
            return
        }
        fallbackTriggered = true
        Log.w(TAG, "[SECONDARY FALLBACK] OEM Bluetooth node search exhausted. Launching Direct Settings Intent...")
        try {
            val directIntent = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(directIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch secondary Bluetooth settings fallback intent: ${e.message}")
        }
    }

    private fun executeNodeToggle(rootNode: AccessibilityNodeInfo, targetState: Boolean): Boolean {
        val switchNode = locateBluetoothSwitchNode(rootNode)
        if (switchNode == null) {
            Log.d(TAG, "Bluetooth switch node not found in current node hierarchy.")
            return false
        }

        val isChecked = switchNode.isChecked
        Log.i(TAG, "Found Bluetooth switch node: isChecked=$isChecked, targetState=$targetState, isClickable=${switchNode.isClickable}")

        if (isChecked == targetState) {
            Log.i(TAG, "Bluetooth switch is already in requested state ($isChecked). Completing operation.")
            toggleListener?.onBluetoothToggleSuccess(targetState)
            clearPendingRequest()
            return true
        }

        val clickSuccessful = performClickAction(switchNode)
        if (clickSuccessful) {
            Log.i(TAG, "ACTION_CLICK successfully executed on Bluetooth toggle.")
            toggleListener?.onBluetoothToggleSuccess(targetState)
            clearPendingRequest()
            return true
        } else {
            Log.w(TAG, "Failed to perform ACTION_CLICK on Bluetooth toggle node.")
            return false
        }
    }

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
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun locateBluetoothSwitchNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
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
            val matchesBtKeyword = text.contains("Bluetooth", ignoreCase = true) ||
                    text.contains("ဘလူးတု", ignoreCase = true) ||
                    text.contains("ဗလူးတု", ignoreCase = true) ||
                    contentDesc.contains("Bluetooth", ignoreCase = true) ||
                    contentDesc.contains("ဘလူးတု", ignoreCase = true) ||
                    viewId.contains("bluetooth") ||
                    viewId.contains("switch_widget") ||
                    // Xiaomi HyperOS / MIUI quick settings node IDs
                    viewId.contains("miui:id/switch") ||
                    viewId.contains("toggle_bluetooth") ||
                    // Samsung One UI node IDs
                    viewId.contains("sec_switch") ||
                    viewId.contains("switch_button") ||
                    // Oppo ColorOS / Realme UI node IDs
                    viewId.contains("coui_switch") ||
                    viewId.contains("color_switch")

            if (matchesBtKeyword) {
                return node
            }

            val parent = node.parent
            if (parent != null) {
                val parentText = parent.text?.toString() ?: ""
                val parentDesc = parent.contentDescription?.toString() ?: ""
                if (parentText.contains("Bluetooth", ignoreCase = true) || parentDesc.contains("Bluetooth", ignoreCase = true) ||
                    parentText.contains("ဘလူးတု", ignoreCase = true)) {
                    return node
                }

                for (i in 0 until parent.childCount) {
                    val sibling = parent.getChild(i)
                    if (sibling != null && sibling != node) {
                        val sText = sibling.text?.toString() ?: ""
                        val sDesc = sibling.contentDescription?.toString() ?: ""
                        if (sText.contains("Bluetooth", ignoreCase = true) || sDesc.contains("Bluetooth", ignoreCase = true) ||
                            sText.contains("ဘလူးတု", ignoreCase = true)) {
                            return node
                        }
                    }
                }
            }

            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = locateBluetoothSwitchNode(child)
            if (found != null) {
                return found
            }
        }

        return null
    }
}
