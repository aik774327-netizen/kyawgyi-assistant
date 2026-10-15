package com.example.bluetooth.models

import android.bluetooth.BluetoothDevice
import androidx.compose.ui.graphics.Color

/**
 * Connection states for Bluetooth GATT.
 */
enum class GattConnectionState(val label: String, val color: Color) {
  DISCONNECTED("Disconnected", Color(0xFF94A3B8)),
  CONNECTING("Connecting...", Color(0xFFF59E0B)),
  CONNECTED("Connected", Color(0xFF10B981)),
  DISCOVERING_SERVICES("Discovering Services...", Color(0xFF38BDF8)),
  READY("GATT Ready", Color(0xFF10B981)),
  DISCONNECTING("Disconnecting...", Color(0xFFF59E0B)),
  FAILED("Connection Failed", Color(0xFFEF4444));

  val isConnectedOrReady: Boolean
    get() = this == CONNECTED || this == READY
}

/**
 * State for Bluetooth scanner.
 */
enum class BleScanStatus(val label: String) {
  IDLE("Idle"),
  SCANNING("Scanning for devices..."),
  STOPPED("Scan stopped"),
  PERMISSION_REQUIRED("Bluetooth permission required"),
  BLUETOOTH_DISABLED("Bluetooth is turned off"),
  UNSUPPORTED("Bluetooth LE unsupported on this device"),
  ERROR("Scan error")
}

/**
 * Filter mode for discovered devices.
 */
enum class DeviceFilterTab(val myanmar: String, val english: String) {
  ALL("စက်အားလုံး", "All Devices"),
  ECOFLOW("EcoFlow သီးသန့်", "EcoFlow Only"),
  PAIRED("တွဲချိတ်ပြီးစက်များ", "Paired / Bonded")
}

/**
 * Enhanced Bluetooth Device model for UI and GATT connection handling.
 */
data class DiscoveredDevice(
  val id: String,
  val name: String,
  val address: String,
  val rssi: Int,
  val isEcoFlow: Boolean = false,
  val bondState: Int = BluetoothDevice.BOND_NONE,
  val connectionState: GattConnectionState = GattConnectionState.DISCONNECTED,
  val rawDevice: BluetoothDevice? = null,
  val serviceUuids: List<String> = emptyList(),
  val servicesCount: Int = 0,
  val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
  val displayName: String
    get() = if (name.isNotBlank() && name != "Unknown") name else "Unknown Device ($address)"

  val isPaired: Boolean
    get() = bondState == BluetoothDevice.BOND_BONDED

  val signalLevelPercent: Int
    get() = when {
      rssi >= -50 -> 100
      rssi >= -65 -> 80
      rssi >= -75 -> 60
      rssi >= -85 -> 40
      rssi >= -95 -> 20
      else -> 10
    }

  val signalQualityLabel: String
    get() = when {
      rssi >= -60 -> "အလွန်ကောင်း (Excellent)"
      rssi >= -75 -> "ကောင်း (Good)"
      rssi >= -85 -> "အသင့်အတင့် (Fair)"
      else -> "အားနည်း (Weak)"
    }

  val signalColor: Color
    get() = when {
      rssi >= -65 -> Color(0xFF10B981)
      rssi >= -80 -> Color(0xFFF59E0B)
      else -> Color(0xFFEF4444)
    }
}
