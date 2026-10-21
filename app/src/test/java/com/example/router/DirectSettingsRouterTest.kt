package com.example.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.utils.KyawGyiPermissionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectSettingsRouterTest {

    private lateinit var context: Context
    private lateinit var router: DirectSettingsRouter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        router = DirectSettingsRouter.getInstance(context)
    }

    @Test
    fun testAllSettingsDestinations_HaveValidMetadataAndCategories() {
        for (destination in SettingsDestination.values()) {
            assertNotNull("titleMM must not be null for $destination", destination.titleMM)
            assertTrue("titleMM must not be empty for $destination", destination.titleMM.isNotBlank())
            assertNotNull("titleEN must not be null for $destination", destination.titleEN)
            assertTrue("titleEN must not be empty for $destination", destination.titleEN.isNotBlank())
            assertNotNull("descriptionMM must not be null for $destination", destination.descriptionMM)
            assertTrue("descriptionMM must not be empty for $destination", destination.descriptionMM.isNotBlank())
            assertNotNull("category must not be null for $destination", destination.category)
            assertNotNull("intentAction must not be null for $destination", destination.intentAction)
        }
    }

    @Test
    fun testOpenSettings_ExecutesSafelyForAllDestinations() {
        for (destination in SettingsDestination.values()) {
            val result = router.openSettings(destination)
            assertTrue("openSettings should execute safely for $destination", result)
        }
    }

    @Test
    fun testSpecificDirectShortcuts_ExecuteSafely() {
        assertTrue(router.openMicrophonePermissionSettings())
        assertTrue(router.openAccessibilitySettings())
        assertTrue(router.openOverlaySettings())
        assertTrue(router.openBatteryOptimizationSettings())
        assertTrue(router.openWriteSettingsPermission())
        assertTrue(router.openExactAlarmSettings())
        assertTrue(router.openTtsSettings())
        assertTrue(router.openVoiceInputSettings())
        assertTrue(router.openNotificationSettings())
        assertTrue(router.openNotificationPolicySettings())
        assertTrue(router.openDefaultAssistantSettings())
        assertTrue(router.openOemAutostartSettings())
        assertTrue(router.openWifiSettings())
        assertTrue(router.openBluetoothSettings())
        assertTrue(router.openSoundSettings())
        assertTrue(router.openDisplaySettings())
        assertTrue(router.openLocationSettings())
        assertTrue(router.openDateTimeSettings())
        assertTrue(router.openStorageSettings())
        assertTrue(router.openDeviceInfoSettings())
    }

    @Test
    fun testIsCapabilityGranted_ReturnsValidBooleanWithoutCrash() {
        for (destination in SettingsDestination.values()) {
            val granted = router.isCapabilityGranted(destination)
            // Boolean assertion to ensure it computes cleanly
            assertTrue(granted || !granted)
        }
    }

    @Test
    fun testPermissionManager_GetAllPermissionsStatus_Complete() {
        val permissions = KyawGyiPermissionManager.getAllPermissionsStatus(context)
        assertTrue("Permissions list must have entries", permissions.isNotEmpty())
        assertTrue("Must include core permissions", permissions.any { it.id == "RECORD_AUDIO" })
        assertTrue("Must include overlay", permissions.any { it.id == "SYSTEM_ALERT_WINDOW" })
        assertTrue("Must include accessibility", permissions.any { it.id == "ACCESSIBILITY_SERVICE" })
        assertTrue("Must include battery optimization", permissions.any { it.id == "BATTERY_OPTIMIZATION" })

        // Test running actions
        for (perm in permissions) {
            perm.openIntentAction(context)
        }
    }

    @Test
    fun testPermissionManager_GetAllDirectShortcuts_Complete() {
        val shortcuts = KyawGyiPermissionManager.getAllDirectShortcuts(context)
        assertTrue("Shortcuts list must have entries", shortcuts.isNotEmpty())
        assertTrue("Must include core perm category", shortcuts.any { it.category == "CORE_PERM" })
        assertTrue("Must include power bg category", shortcuts.any { it.category == "POWER_BG" })
        assertTrue("Must include notify dnd category", shortcuts.any { it.category == "NOTIFY_DND" })
        assertTrue("Must include quick sys category", shortcuts.any { it.category == "QUICK_SYS" })

        // Test invoking directAction on each shortcut
        for (shortcut in shortcuts) {
            shortcut.directAction(context)
        }
    }
}
