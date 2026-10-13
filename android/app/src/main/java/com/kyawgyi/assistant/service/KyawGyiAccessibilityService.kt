package com.kyawgyi.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * KyawGyiAccessibilityService — system event listening, screen reading,
 * and touch / navigation shortcuts driven by voice commands.
 *
 * SECURITY & PRIVACY (D5 — High Risk):
 *  - This service CAN read screen contents and perform gestures.
 *  - It must NEVER log, persist, or transmit screen text. All text stays
 *    in memory and is handed only to a locally registered listener.
 *  - The user must explicitly enable the service in system Settings;
 *    getInstance() returns null until they do.
 *
 * Compile status: NOT VERIFIED (no JDK/Android SDK in this environment, ER-006).
 */
class KyawGyiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: KyawGyiAccessibilityService? = null

        /** Null until the user enables the service in system Settings. */
        fun getInstance(): KyawGyiAccessibilityService? = instance

        /** FIX #9: upper bound for readVisibleText results. */
        private const val MAX_TEXTS = 200
    }

    /**
     * Local-only consumer for accessibility events. Implementations must not
     * persist or exfiltrate event data (D5).
     *
     * NOTE: no bridge is attached yet — the Flutter layer can register one
     * via a future EventChannel. Kept minimal on purpose.
     */
    interface EventListener {
        fun onEvent(eventType: Int, packageName: String?)
    }

    @Volatile
    var eventListener: EventListener? = null

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        // D5: dispatch metadata only — never the text content, never logged.
        try {
            eventListener?.onEvent(e.eventType, e.packageName?.toString())
        } catch (ignored: Exception) {
        }
    }

    override fun onInterrupt() {
        // FIX #16: onInterrupt is a "pause feedback" signal, NOT a disable —
        // nulling the instance here falsely reported the service as not
        // running. Only onDestroy clears it.
    }

    override fun onDestroy() {
        instance = null
        eventListener = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ screen reading

    /**
     * Collects visible text from the active window for local TTS readout.
     * Returns the strings via [callback]; nothing is logged or stored (D5).
     * Capped at [MAX_TEXTS] entries — a dense screen must not flood the caller.
     * Returns false when no window content is available.
     */
    fun readVisibleText(callback: (List<String>) -> Unit) {
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        }
        if (root == null) {
            callback(emptyList())
            return
        }
        val out = ArrayList<String>()
        try {
            collectText(root, out)
        } catch (e: Exception) {
            // ignore — return whatever was collected
        } finally {
            try {
                root.recycle()
            } catch (ignored: Exception) {
            }
        }
        callback(out)
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (out.size >= MAX_TEXTS) return // FIX #9: stop traversal at the cap
        try {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && node.isVisibleToUser) {
                out.add(text)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    collectText(child, out)
                } finally {
                    try {
                        child.recycle()
                    } catch (ignored: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            // ignore — best effort traversal
        }
    }

    // ------------------------------------------------------------------ navigation shortcuts

    /** System Back — returns false on old platforms or failure. */
    fun goBack(): Boolean = performSystemAction(GLOBAL_ACTION_BACK)

    /** System Home. */
    fun goHome(): Boolean = performSystemAction(GLOBAL_ACTION_HOME)

    /** Recent apps overview. */
    fun openRecents(): Boolean = performSystemAction(GLOBAL_ACTION_RECENTS)

    private fun performSystemAction(action: Int): Boolean {
        return try {
            performGlobalAction(action)
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ touch automation

    /**
     * Performs a tap gesture at the given coordinates.
     * Requires Android 7.0 (API 24)+. Callback receives true on completion.
     * FIX #10: coordinates are clamped to the display bounds — negative or
     * off-screen values can no longer produce undefined gesture behavior.
     */
    fun performTap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback(false)
            return
        }
        try {
            val metrics = resources.displayMetrics
            val cx = x.coerceIn(0f, metrics.widthPixels.toFloat())
            val cy = y.coerceIn(0f, metrics.heightPixels.toFloat())
            val path = Path().apply { moveTo(cx, cy) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    callback(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    callback(false)
                }
            }, null)
            if (!dispatched) callback(false)
        } catch (e: Exception) {
            callback(false)
        }
    }
}
