package com.example.utils

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPermissionConstants() {
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionManager.PERMISSION_RECORD_AUDIO)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, PermissionManager.PERMISSION_POST_NOTIFICATIONS)
    }

    @Test
    fun testRequiredPermissionsList() {
        val permissions = PermissionManager.getRequiredPermissionsList(context)
        assertNotNull(permissions)
        assertTrue(permissions.isNotEmpty())

        val ids = permissions.map { it.id }
        assertTrue(ids.contains("RECORD_AUDIO"))
        assertTrue(ids.contains("POST_NOTIFICATIONS"))
        assertTrue(ids.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(ids.contains("ACCESSIBILITY_SERVICE"))
        assertTrue(ids.contains("WRITE_SETTINGS"))
        assertTrue(ids.contains("SCHEDULE_EXACT_ALARM"))
        assertTrue(ids.contains("IGNORE_BATTERY_OPTIMIZATIONS"))
    }

    @Test
    fun testHandlePermissionResultAllGranted() {
        var allGrantedCalled = false
        var deniedCalled = false

        val results = mapOf(
            Manifest.permission.RECORD_AUDIO to true,
            Manifest.permission.POST_NOTIFICATIONS to true
        )

        PermissionManager.handlePermissionResult(
            permissions = results,
            onAllGranted = { allGrantedCalled = true },
            onDenied = { deniedCalled = true }
        )

        assertTrue(allGrantedCalled)
        assertFalse(deniedCalled)
    }

    @Test
    fun testHandlePermissionResultSomeDenied() {
        var allGrantedCalled = false
        var deniedPermissions = listOf<String>()

        val results = mapOf(
            Manifest.permission.RECORD_AUDIO to true,
            Manifest.permission.POST_NOTIFICATIONS to false
        )

        PermissionManager.handlePermissionResult(
            permissions = results,
            onAllGranted = { allGrantedCalled = true },
            onDenied = { deniedPermissions = it }
        )

        assertFalse(allGrantedCalled)
        assertEquals(1, deniedPermissions.size)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, deniedPermissions[0])
    }

    @Test
    fun testSpecialPermissionNavigationMethodsDoNotCrash() {
        try {
            PermissionManager.openAppSettings(context)
            PermissionManager.openNotificationSettings(context)
            PermissionManager.requestOverlayPermission(context)
            PermissionManager.requestAccessibilityPermission(context)
            PermissionManager.requestWriteSettingsPermission(context)
            PermissionManager.requestIgnoreBatteryOptimization(context)
            PermissionManager.requestExactAlarmPermission(context)
        } catch (e: Exception) {
            fail("Navigation method crashed: ${e.message}")
        }
    }
}
