package com.example.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FIX-04 regression tests (tests-first) — ON/OFF result honesty.
 *
 * Device evidence B3 + defect D3: KyawGyiActionExecutor.setWifi /
 * setBluetooth on API 29+ can only open a Settings panel (direct
 * toggle is platform-forbidden for third-party apps), yet they
 * returned isSuccess=true / executionConfirmed=true — an
 * ISSUE-030-class false success at executor level. These tests pin
 * the honest contract: a panel-only outcome is NOT a success and NOT
 * a confirmed state change, while the panel action identity is kept
 * so callers can still tell the user what was opened.
 *
 * Pre-fix expectation: T16-04-01/02/03 FAIL (results claim success).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActionExecutorToggleHonestyTest {

    private lateinit var context: Context
    private lateinit var executor: KyawGyiActionExecutor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        executor = KyawGyiActionExecutor(context)
    }

    // T16-04-01: Wi-Fi ON via panel must not claim success/confirmation.
    @Test
    fun t16_04_01_wifiOnPanelIsNotSuccess() {
        val result = executor.setWifi(true)
        assertFalse("panel-only Wi-Fi result must not be isSuccess", result.isSuccess)
        assertFalse("panel-only Wi-Fi result must not be executionConfirmed", result.executionConfirmed)
        assertEquals("OPEN_WIFI_PANEL", result.action)
        assertTrue(result.executionStarted)
    }

    // T16-04-02: Wi-Fi OFF via panel — same honest contract.
    @Test
    fun t16_04_02_wifiOffPanelIsNotSuccess() {
        val result = executor.setWifi(false)
        assertFalse(result.isSuccess)
        assertFalse(result.executionConfirmed)
        assertEquals("OPEN_WIFI_PANEL", result.action)
    }

    // T16-04-03: Bluetooth via Settings page — same honest contract.
    @Test
    fun t16_04_03_bluetoothPanelIsNotSuccess() {
        val on = executor.setBluetooth(true)
        assertFalse(on.isSuccess)
        assertFalse(on.executionConfirmed)
        assertEquals("OPEN_BLUETOOTH_SETTINGS", on.action)
        val off = executor.setBluetooth(false)
        assertFalse(off.isSuccess)
        assertFalse(off.executionConfirmed)
    }

    // T16-04-07: flashlight camera selection (pure helper) — the
    // first camera WITH a flash unit wins (not blindly the first
    // camera); no flash anywhere -> null (the executor turns that
    // into the honest NO_FLASH_CAMERA failure); a throwing probe
    // counts as "no flash", never as a crash.
    @Test
    fun t16_04_07_flashCapableCameraSelection() {
        assertEquals(
            "1",
            firstFlashCapableCameraId(listOf("0", "1", "2")) { it == "1" }
        )
        assertEquals(
            null,
            firstFlashCapableCameraId(listOf("0", "1")) { false }
        )
        assertEquals(
            null,
            firstFlashCapableCameraId(emptyList()) { true }
        )
        assertEquals(
            "2",
            firstFlashCapableCameraId(listOf("0", "2")) { id ->
                if (id == "0") throw RuntimeException("probe failed") else true
            }
        )
    }
}
