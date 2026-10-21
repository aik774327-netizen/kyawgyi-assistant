package com.example.service

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiAccessibilityHelperTest {

    private lateinit var helper: WifiAccessibilityHelper

    @Before
    fun setUp() {
        helper = WifiAccessibilityHelper.getInstance()
        helper.clearPendingRequest()
    }

    @Test
    fun testPendingRequestLifecycle() {
        assertFalse(helper.hasPendingRequest())

        var callbackCalled = false
        var successState = false

        helper.queueWifiToggleRequest(targetEnable = true, listener = object : WifiAccessibilityHelper.OnWifiToggleListener {
            override fun onWifiToggleSuccess(targetState: Boolean) {
                callbackCalled = true
                successState = targetState
            }

            override fun onWifiToggleFailed(reason: String) {
                // Not expected
            }
        })

        assertTrue(helper.hasPendingRequest())

        // Clear request
        helper.clearPendingRequest()
        assertFalse(helper.hasPendingRequest())
    }

    @Test
    fun testLocateWifiSwitchNodeNull() {
        val node = helper.locateWifiSwitchNode(null)
        assertNull(node)
    }

    @Test
    fun testHelperSingleton() {
        val instance1 = WifiAccessibilityHelper.getInstance()
        val instance2 = WifiAccessibilityHelper.getInstance()
        assertEquals(instance1, instance2)
    }
}
