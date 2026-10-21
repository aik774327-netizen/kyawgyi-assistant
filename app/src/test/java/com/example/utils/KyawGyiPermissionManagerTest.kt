package com.example.utils

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
class KyawGyiPermissionManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRequiredPermissions() {
        val permissions = KyawGyiPermissionManager.getRequiredPermissions()
        assertTrue(permissions.isNotEmpty())
        assertTrue(permissions.contains(android.Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun testAllPermissionsStatusList() {
        val statusList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
        assertNotNull(statusList)
        assertTrue(statusList.size >= 8)

        val ids = statusList.map { it.id }
        assertTrue(ids.contains("RECORD_AUDIO"))
        assertTrue(ids.contains("SYSTEM_ALERT_WINDOW"))
        assertTrue(ids.contains("ACCESSIBILITY_SERVICE"))
        assertTrue(ids.contains("WRITE_SETTINGS"))
        assertTrue(ids.contains("BATTERY_OPTIMIZATION"))
        assertTrue(ids.contains("OEM_AUTOSTART"))
        assertTrue(ids.contains("DEFAULT_ASSISTANT"))
        assertTrue(ids.contains("POST_NOTIFICATIONS"))
    }

    @Test
    fun testDirectIntentActionsDoNotCrash() {
        val statusList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
        for (item in statusList) {
            try {
                item.openIntentAction(context)
            } catch (e: Exception) {
                fail("openIntentAction for ${item.id} crashed: ${e.message}")
            }
        }
    }
}
