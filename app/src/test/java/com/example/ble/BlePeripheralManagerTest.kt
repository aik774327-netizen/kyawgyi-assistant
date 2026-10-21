package com.example.ble

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
class BlePeripheralManagerTest {

    private lateinit var context: Context
    private lateinit var bleManager: BlePeripheralManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        bleManager = BlePeripheralManager.getInstance(context)
    }

    @Test
    fun testBleManagerInitializationAndInitialState() {
        assertNotNull(bleManager)
        val state = bleManager.uiState.value
        assertEquals(BleConnectionState.DISCONNECTED, state.connectionState)
        assertNull(state.connectedDeviceAddress)
        assertTrue(state.discoveredDevices.isEmpty())
        assertTrue(state.discoveredServices.isEmpty())
    }

    @Test
    fun testBleNotificationDataEquality() {
        val bytes1 = byteArrayOf(0x01, 0x02, 0x03)
        val bytes2 = byteArrayOf(0x01, 0x02, 0x03)
        val notif1 = BleNotificationData("00:11:22:33:44:55", "uuid-char-1", bytes1, 1000L)
        val notif2 = BleNotificationData("00:11:22:33:44:55", "uuid-char-1", bytes2, 2000L)

        assertEquals(notif1, notif2)
        assertEquals(notif1.hashCode(), notif2.hashCode())
    }

    @Test
    fun testBleDisconnectCleansState() {
        bleManager.disconnect()
        val state = bleManager.uiState.value
        assertEquals(BleConnectionState.DISCONNECTED, state.connectionState)
        assertNull(state.connectedDeviceName)
        assertNull(state.connectedDeviceAddress)
    }
}
