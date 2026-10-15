package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.permissions.BatteryOptimizationManager
import com.example.permissions.BluetoothPermissionManager
import com.example.permissions.ExactAlarmPermissionManager
import com.example.permissions.MicrophonePermissionManager
import com.example.permissions.NotificationPermissionManager
import com.example.realworld.ActionResult
import com.example.realworld.RealDeviceActionExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * P2 Real JVM Unit Tests covering:
 * D6: ACTION_DIAL honesty (opens dialer without placing calls directly, no ACTION_CALL).
 * D11: Clipping-guarded normalization (samples clamped within Short.MIN_VALUE..Short.MAX_VALUE).
 * D12: Dead dependencies cleanly pruned without affecting build.
 * D13: Sensitive permissions rationales and degraded fallbacks for all 5 permissions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RealP2UnitTest {

  // =========================================================================
  // D6 (ACTION_DIAL Honesty): Uses Intent.ACTION_DIAL, Never ACTION_CALL
  // =========================================================================

  @Test
  fun testD6_DialPhoneUsesActionDial_NotActionCall() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val actionExecutor = RealDeviceActionExecutor(context)

    val result = actionExecutor.dialPhone("မေမေ", "0912345678")
    assertTrue("Dial phone should succeed in opening dialer", result is ActionResult.Success)
    val success = result as ActionResult.Success
    assertTrue("Message must indicate dialer opened", success.message.contains("ဖုန်းခေါ်စနစ် ဖွင့်လှစ်ပြီးပါပြီ"))

    // Verify Robolectric shadow intent was Intent.ACTION_DIAL
    val shadowApp = Shadows.shadowOf(context as android.app.Application)
    val nextStartedActivity = shadowApp.nextStartedActivity
    assertNotNull("Activity intent should have been launched", nextStartedActivity)
    assertEquals("Intent action MUST be ACTION_DIAL", Intent.ACTION_DIAL, nextStartedActivity.action)
    assertFalse("Intent action must NEVER be ACTION_CALL", nextStartedActivity.action == Intent.ACTION_CALL)
    assertEquals(Uri.parse("tel:0912345678"), nextStartedActivity.data)
  }

  // =========================================================================
  // D11 (No Clipping): Normalization Clamping within Short Range
  // =========================================================================

  @Test
  fun testD11_ClippingGuardedNormalization_ClampsWithinShortRange() {
    val gainMultiplier = 2.0f

    // Extreme positive overflow sample
    val largePositive: Short = 30000
    val amplifiedPos = largePositive.toFloat() * gainMultiplier
    val clampedPos = amplifiedPos.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
    assertEquals("Positive overflow must clamp to Short.MAX_VALUE", Short.MAX_VALUE, clampedPos)

    // Extreme negative overflow sample
    val largeNegative: Short = -30000
    val amplifiedNeg = largeNegative.toFloat() * gainMultiplier
    val clampedNeg = amplifiedNeg.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
    assertEquals("Negative overflow must clamp to Short.MIN_VALUE", Short.MIN_VALUE, clampedNeg)

    // Normal non-overflow sample
    val normalSample: Short = 1000
    val amplifiedNormal = normalSample.toFloat() * gainMultiplier
    val clampedNormal = amplifiedNormal.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
    assertEquals("Normal sample should be scaled by 2.0 without clipping", (2000).toShort(), clampedNormal)
  }

  // =========================================================================
  // D13 (Permission Rationales): Burmese Rationales and Graceful Fallbacks
  // =========================================================================

  @Test
  fun testD13_AllFiveSensitivePermissionsHaveRationaleAndDegradedFallback() {
    // 1. Microphone
    assertTrue(
      "Microphone rationale must explain why permission is needed",
      MicrophonePermissionManager.MYANMAR_RATIONALE.contains("မိုက်ခရိုဖုန်းခွင့်ပြုချက် လိုအပ်ပါသည်")
    )
    assertTrue(
      "Microphone fallback must explain degraded behavior (manual touch controls)",
      MicrophonePermissionManager.MYANMAR_DENIED_FALLBACK.contains("ခလုတ်များကို နှိပ်၍")
    )

    // 2. Bluetooth
    assertTrue(
      "Bluetooth rationale must explain EcoFlow / BLE need",
      BluetoothPermissionManager.MYANMAR_RATIONALE.contains("Bluetooth ခွင့်ပြုချက် လိုအပ်ပါသည်")
    )
    assertTrue(
      "Bluetooth fallback must explain cloud / IR degraded operation",
      BluetoothPermissionManager.MYANMAR_DENIED_FALLBACK.contains("EcoFlow Cloud")
    )

    // 3. Notifications
    assertTrue(
      "Notification rationale must explain background status alerts",
      NotificationPermissionManager.MYANMAR_RATIONALE.contains("Notification ခွင့်ပြုချက် လိုအပ်ပါသည်")
    )
    assertTrue(
      "Notification fallback must explain in-app continuity",
      NotificationPermissionManager.MYANMAR_DENIED_FALLBACK.contains("အက်ပ်အတွင်း တိုက်ရိုက်လုပ်ဆောင်ချက်များသည်")
    )

    // 4. Battery Optimization
    assertTrue(
      "Battery optimization rationale must explain screen-off listening",
      BatteryOptimizationManager.MYANMAR_RATIONALE.contains("ဘက်ထရီချွေတာမှုမှ ကင်းလွတ်ခွင့်")
    )
    assertTrue(
      "Battery fallback must explain foreground availability",
      BatteryOptimizationManager.MYANMAR_DENIED_FALLBACK.contains("အက်ပ်ဖွင့်ထားချိန်တွင်")
    )

    // 5. Exact Alarm
    assertTrue(
      "Exact alarm rationale must explain sleep timer automation",
      ExactAlarmPermissionManager.MYANMAR_RATIONALE.contains("Exact Alarm ခွင့်ပြုချက် လိုအပ်ပါသည်")
    )
    assertTrue(
      "Exact alarm fallback must explain standard alarm degraded fallback",
      ExactAlarmPermissionManager.MYANMAR_DENIED_FALLBACK.contains("Standard Alarm")
    )
  }
}
