package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.permissions.AndroidSettingsManager
import com.example.permissions.BackgroundExecutionManager
import com.example.permissions.BatteryOptimizationManager
import com.example.permissions.BatteryReadiness
import com.example.ui.permissions.RuntimePermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Test for:
 * (၂) (Runtime Permissions, Battery Optimization Exemption & OEM Autostart Guidance)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuntimePermissionsAndOemTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun testRuntimePermissionState_calculation() {
    // 1. None granted
    val emptyState = RuntimePermissionState()
    assertFalse("Default should not be essential granted", emptyState.areAllEssentialGranted)
    assertFalse("Default microphone should be false", emptyState.isMicrophoneGranted)
    assertFalse("Default contacts should be false", emptyState.isContactsGranted)

    // 2. Only microphone granted
    val micOnly = RuntimePermissionState(isMicrophoneGranted = true)
    assertFalse("Mic only is not all essential on S+ without Bluetooth", micOnly.areAllEssentialGranted)

    // 3. Microphone + Bluetooth scan & connect granted
    val essentialGranted = RuntimePermissionState(
      isMicrophoneGranted = true,
      isBluetoothScanGranted = true,
      isBluetoothConnectGranted = true,
      isContactsGranted = true,
      isNotificationGranted = true,
      isLocationGranted = true
    )
    assertTrue("All essential should be granted", essentialGranted.areAllEssentialGranted)
    assertTrue("Bluetooth should be granted", essentialGranted.isBluetoothGranted)
    assertTrue("Contacts should be granted", essentialGranted.isContactsGranted)
    assertTrue("Notification should be granted", essentialGranted.isNotificationGranted)
  }

  @Test
  fun testBackgroundExecutionManager_oemGuidanceBurmese() {
    val bgManager = BackgroundExecutionManager(context)
    val oemName = bgManager.getOemName()
    assertNotNull("OEM Name should not be null", oemName)
    assertTrue("OEM Name should have content", oemName.isNotBlank())

    val guidance = bgManager.getOemGuidanceBurmese()
    assertNotNull("Guidance list should not be null", guidance)
    assertTrue("Guidance should contain at least 2 steps", guidance.size >= 2)
    guidance.forEach { step ->
      assertTrue("Each guidance step must be in Burmese and non-empty", step.isNotBlank())
    }
  }

  @Test
  fun testBatteryOptimizationManager_status() {
    val batteryManager = BatteryOptimizationManager(context)
    val readiness = batteryManager.checkReadiness()
    assertTrue(
      "Readiness must be either EXEMPTED or OPTIMIZED",
      readiness == BatteryReadiness.EXEMPTED || readiness == BatteryReadiness.OPTIMIZED
    )
    assertNotNull("Message should not be null", BatteryOptimizationManager.MYANMAR_MESSAGE)
  }

  @Test
  fun testAndroidSettingsManager_safeDispatch() {
    // Verify launching settings does not crash and handles simulated environment safely
    try {
      AndroidSettingsManager.openBatteryOptimizationSettings(context)
      AndroidSettingsManager.openAppDetails(context)
      AndroidSettingsManager.openOemBackgroundSettings(context)
      assertTrue("Settings dispatch completed without uncaught exception", true)
    } catch (e: Exception) {
      org.junit.Assert.fail("Settings dispatch threw exception: ${e.message}")
    }
  }
}
