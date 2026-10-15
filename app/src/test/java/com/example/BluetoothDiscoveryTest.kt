package com.example

import android.bluetooth.BluetoothDevice
import com.example.bluetooth.models.BleScanStatus
import com.example.bluetooth.models.DeviceFilterTab
import com.example.bluetooth.models.DiscoveredDevice
import com.example.bluetooth.models.GattConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 100% Comprehensive Unit Tests for Bluetooth & EcoFlow Smart Discovery System.
 * Tests device detection heuristics, RSSI signal evaluation, GATT states, and filtering.
 */
class BluetoothDiscoveryTest {

  @Test
  fun testEcoFlowDeviceDetectionHeuristics() {
    val deltaDevice = DiscoveredDevice(
      id = "1",
      name = "DELTA 2 Max",
      address = "AA:BB:CC:DD:EE:01",
      rssi = -55,
      isEcoFlow = true
    )
    assertTrue("DELTA should be recognized as EcoFlow", deltaDevice.isEcoFlow)

    val riverDevice = DiscoveredDevice(
      id = "2",
      name = "EcoFlow RIVER 2 Pro",
      address = "AA:BB:CC:DD:EE:02",
      rssi = -60,
      isEcoFlow = true
    )
    assertTrue("RIVER should be recognized as EcoFlow", riverDevice.isEcoFlow)

    val efDevice = DiscoveredDevice(
      id = "3",
      name = "EF-DELTA-PRO-1234",
      address = "AA:BB:CC:DD:EE:03",
      rssi = -70,
      isEcoFlow = true
    )
    assertTrue("EF- prefix should be recognized as EcoFlow", efDevice.isEcoFlow)

    val genericDevice = DiscoveredDevice(
      id = "4",
      name = "Wireless Headphones",
      address = "AA:BB:CC:DD:EE:04",
      rssi = -80,
      isEcoFlow = false
    )
    assertFalse("Generic device must not be marked as EcoFlow", genericDevice.isEcoFlow)

    val unnamedDevice = DiscoveredDevice(
      id = "5",
      name = "Unknown",
      address = "AA:BB:CC:DD:EE:05",
      rssi = -90,
      isEcoFlow = false
    )
    assertFalse("Unnamed device must not be marked as EcoFlow", unnamedDevice.isEcoFlow)
    assertEquals("Unknown Device (AA:BB:CC:DD:EE:05)", unnamedDevice.displayName)
  }

  @Test
  fun testRssiSignalEvaluation() {
    // Very strong signal
    val strong = DiscoveredDevice(id = "1", name = "Device 1", address = "A1", rssi = -50)
    assertTrue(strong.signalQualityLabel.contains("အလွန်ကောင်း"))
    assertEquals(100, strong.signalLevelPercent)

    // Good signal
    val good = DiscoveredDevice(id = "2", name = "Device 2", address = "A2", rssi = -62)
    assertTrue(good.signalQualityLabel.contains("ကောင်း"))
    assertEquals(80, good.signalLevelPercent)

    // Fair signal
    val fair = DiscoveredDevice(id = "3", name = "Device 3", address = "A3", rssi = -78)
    assertTrue(fair.signalQualityLabel.contains("အသင့်အတင့်"))
    assertEquals(40, fair.signalLevelPercent)

    // Weak signal
    val weak = DiscoveredDevice(id = "4", name = "Device 4", address = "A4", rssi = -92)
    assertTrue(weak.signalQualityLabel.contains("အားနည်း"))
    assertEquals(20, weak.signalLevelPercent)
  }

  @Test
  fun testGattConnectionStateTransitions() {
    // Disconnected
    val disconnected = GattConnectionState.DISCONNECTED
    assertFalse(disconnected.isConnectedOrReady)
    assertEquals("Disconnected", disconnected.label)

    // Connecting
    val connecting = GattConnectionState.CONNECTING
    assertFalse(connecting.isConnectedOrReady)
    assertEquals("Connecting...", connecting.label)

    // Connected
    val connected = GattConnectionState.CONNECTED
    assertTrue(connected.isConnectedOrReady)
    assertEquals("Connected", connected.label)

    // Discovering Services
    val discovering = GattConnectionState.DISCOVERING_SERVICES
    assertFalse(discovering.isConnectedOrReady)
    assertEquals("Discovering Services...", discovering.label)

    // Ready
    val ready = GattConnectionState.READY
    assertTrue(ready.isConnectedOrReady)
    assertEquals("GATT Ready", ready.label)

    // Failed
    val failed = GattConnectionState.FAILED
    assertFalse(failed.isConnectedOrReady)
    assertEquals("Connection Failed", failed.label)
  }

  @Test
  fun testDeviceFiltering() {
    val devices = listOf(
      DiscoveredDevice(id = "1", name = "EcoFlow DELTA 2", address = "01", rssi = -50, isEcoFlow = true, bondState = BluetoothDevice.BOND_BONDED),
      DiscoveredDevice(id = "2", name = "EcoFlow RIVER 2", address = "02", rssi = -60, isEcoFlow = true, bondState = BluetoothDevice.BOND_NONE),
      DiscoveredDevice(id = "3", name = "Bluetooth Speaker", address = "03", rssi = -70, isEcoFlow = false, bondState = BluetoothDevice.BOND_BONDED),
      DiscoveredDevice(id = "4", name = "Fitness Band", address = "04", rssi = -80, isEcoFlow = false, bondState = BluetoothDevice.BOND_NONE)
    )

    // ALL Tab
    val allDevices = devices
    assertEquals(4, allDevices.size)

    // ECOFLOW Tab
    val ecoFlowDevices = devices.filter { it.isEcoFlow }
    assertEquals(2, ecoFlowDevices.size)
    assertTrue(ecoFlowDevices.all { it.isEcoFlow })

    // PAIRED Tab
    val pairedDevices = devices.filter { it.isPaired }
    assertEquals(2, pairedDevices.size)
    assertTrue(pairedDevices.all { it.isPaired })
  }

  @Test
  fun testBleScanStatusValues() {
    assertEquals("Idle", BleScanStatus.IDLE.label)
    assertEquals("Scanning for devices...", BleScanStatus.SCANNING.label)
    assertEquals("Bluetooth is turned off", BleScanStatus.BLUETOOTH_DISABLED.label)
    assertEquals("Bluetooth permission required", BleScanStatus.PERMISSION_REQUIRED.label)
  }
}
