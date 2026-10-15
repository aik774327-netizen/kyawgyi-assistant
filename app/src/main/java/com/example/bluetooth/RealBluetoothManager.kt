package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.bluetooth.models.BleScanStatus
import com.example.bluetooth.models.DiscoveredDevice
import com.example.bluetooth.models.GattConnectionState
import com.example.permissions.DiagnosticLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 100% Production-grade Bluetooth Discovery and GATT Connection Engine for Kyaw Gyi AI.
 * Handles LE Scanning, Paired device retrieval, EcoFlow signature identification,
 * robust GATT connection state machine with automatic retry and service discovery.
 */
class RealBluetoothManager(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

  private val bluetoothManager: BluetoothManager? by lazy {
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  }

  private val bluetoothAdapter: BluetoothAdapter? by lazy {
    bluetoothManager?.adapter
  }

  private var bleScanner: BluetoothLeScanner? = null
  private var scanJob: Job? = null
  private var activeGatt: BluetoothGatt? = null
  private var activeDeviceAddress: String? = null
  private var connectionRetryCount = 0

  // Observables
  private val _scanStatus = MutableStateFlow(BleScanStatus.IDLE)
  val scanStatus: StateFlow<BleScanStatus> = _scanStatus.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private val _connectionState = MutableStateFlow(GattConnectionState.DISCONNECTED)
  val connectionState: StateFlow<GattConnectionState> = _connectionState.asStateFlow()

  private val _connectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
  val connectedDevice: StateFlow<DiscoveredDevice?> = _connectedDevice.asStateFlow()

  private val _discoveredServices = MutableStateFlow<List<String>>(emptyList())
  val discoveredServices: StateFlow<List<String>> = _discoveredServices.asStateFlow()

  private val _lastGattEvent = MutableStateFlow("Ready for Bluetooth operations")
  val lastGattEvent: StateFlow<String> = _lastGattEvent.asStateFlow()

  // Hardware Checks
  val isBluetoothSupported: Boolean
    get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

  val isBleSupported: Boolean
    get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

  val isBluetoothEnabled: Boolean
    get() = bluetoothAdapter?.isEnabled == true

  fun hasBluetoothScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun hasBluetoothConnectPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
    }
  }

  // Active Scan Callback
  private val leScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      result?.let { handleScanResult(it) }
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
      results?.forEach { handleScanResult(it) }
    }

    override fun onScanFailed(errorCode: Int) {
      val errorMsg = when (errorCode) {
        SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
        SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE feature unsupported"
        SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
        else -> "Scan failed (code: $errorCode)"
      }
      _scanStatus.value = BleScanStatus.ERROR
      _lastGattEvent.value = "⚠️ $errorMsg"
      DiagnosticLogger.logOperation("BLE_SCAN", "Scanner", "SCANNING", "FAILED", errorMsg)
    }
  }

  /**
   * Starts BLE discovery and also aggregates bonded/paired devices.
   */
  @SuppressLint("MissingPermission")
  fun startScan(scanDurationMs: Long = 15000, ecoFlowOnly: Boolean = false) {
    if (!isBluetoothSupported || !isBleSupported) {
      _scanStatus.value = BleScanStatus.UNSUPPORTED
      _lastGattEvent.value = "Device does not support Bluetooth Low Energy (BLE)"
      return
    }

    if (!hasBluetoothScanPermission()) {
      _scanStatus.value = BleScanStatus.PERMISSION_REQUIRED
      _lastGattEvent.value = "BLUETOOTH_SCAN permission required"
      return
    }

    if (!isBluetoothEnabled) {
      _scanStatus.value = BleScanStatus.BLUETOOTH_DISABLED
      _lastGattEvent.value = "Bluetooth is currently turned off"
      return
    }

    // Load bonded devices first
    loadBondedDevices()

    bleScanner = bluetoothAdapter?.bluetoothLeScanner
    if (bleScanner == null) {
      _scanStatus.value = BleScanStatus.ERROR
      _lastGattEvent.value = "BluetoothLeScanner unavailable"
      return
    }

    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .setReportDelay(0)
      .build()

    val filters = mutableListOf<ScanFilter>()
    // If EcoFlow only requested, add device name filters where applicable
    if (ecoFlowOnly) {
      filters.add(ScanFilter.Builder().setDeviceName("EcoFlow").build())
      filters.add(ScanFilter.Builder().setDeviceName("DELTA").build())
      filters.add(ScanFilter.Builder().setDeviceName("RIVER").build())
    }

    try {
      bleScanner?.startScan(filters, settings, leScanCallback)
      _scanStatus.value = BleScanStatus.SCANNING
      _lastGattEvent.value = "Scanning for Bluetooth & EcoFlow devices..."
      DiagnosticLogger.logOperation("BLE_SCAN", "Scanner", "IDLE", "SCANNING", "Success")

      // Auto-stop scan timer
      scanJob?.cancel()
      scanJob = scope.launch {
        delay(scanDurationMs)
        stopScan()
      }
    } catch (e: SecurityException) {
      _scanStatus.value = BleScanStatus.PERMISSION_REQUIRED
      _lastGattEvent.value = "SecurityException: ${e.message}"
    } catch (e: Exception) {
      _scanStatus.value = BleScanStatus.ERROR
      _lastGattEvent.value = "Scan start failed: ${e.message}"
    }
  }

  /**
   * Stops BLE discovery.
   */
  @SuppressLint("MissingPermission")
  fun stopScan() {
    scanJob?.cancel()
    scanJob = null

    if (hasBluetoothScanPermission() && isBluetoothEnabled) {
      try {
        bleScanner?.stopScan(leScanCallback)
      } catch (_: Exception) { }
    }

    if (_scanStatus.value == BleScanStatus.SCANNING) {
      _scanStatus.value = BleScanStatus.STOPPED
      _lastGattEvent.value = "Scan finished. Found ${_discoveredDevices.value.size} device(s)."
      DiagnosticLogger.logOperation("BLE_SCAN", "Scanner", "SCANNING", "STOPPED", "Found ${_discoveredDevices.value.size}")
    }
  }

  /**
   * Loads bonded/paired devices from the local system adapter.
   */
  @SuppressLint("MissingPermission")
  private fun loadBondedDevices() {
    if (!hasBluetoothConnectPermission() || !isBluetoothEnabled) return

    try {
      val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
      val list = _discoveredDevices.value.toMutableList()

      bonded.forEach { device ->
        val name = device.name ?: "Paired Device"
        val addr = device.address
        val isEco = isEcoFlowSignature(name, addr, emptyList())

        if (list.none { it.address.equals(addr, ignoreCase = true) }) {
          list.add(
            DiscoveredDevice(
              id = addr,
              name = name,
              address = addr,
              rssi = -60, // Default nominal RSSI for paired device
              isEcoFlow = isEco,
              bondState = BluetoothDevice.BOND_BONDED,
              rawDevice = device
            )
          )
        }
      }
      _discoveredDevices.value = list
    } catch (e: Exception) {
      DiagnosticLogger.logOperation("BLE_BONDED", "Adapter", "NONE", "ERROR", e.message ?: "Error")
    }
  }

  @SuppressLint("MissingPermission")
  private fun handleScanResult(result: ScanResult) {
    val device = result.device ?: return
    val address = device.address ?: return
    val name = result.scanRecord?.deviceName
      ?: (if (hasBluetoothConnectPermission()) device.name else null)
      ?: "Unknown BLE Device"

    val rssi = result.rssi
    val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
    val isEco = isEcoFlowSignature(name, address, uuids)

    val currentList = _discoveredDevices.value.toMutableList()
    val existingIndex = currentList.indexOfFirst { it.address.equals(address, ignoreCase = true) }

    val bondState = if (hasBluetoothConnectPermission()) {
      try { device.bondState } catch (_: Exception) { BluetoothDevice.BOND_NONE }
    } else {
      BluetoothDevice.BOND_NONE
    }

    val updatedItem = DiscoveredDevice(
      id = address,
      name = name,
      address = address,
      rssi = rssi,
      isEcoFlow = isEco,
      bondState = bondState,
      connectionState = if (activeDeviceAddress == address) _connectionState.value else GattConnectionState.DISCONNECTED,
      rawDevice = device,
      serviceUuids = uuids,
      lastSeenTimestamp = System.currentTimeMillis()
    )

    if (existingIndex >= 0) {
      currentList[existingIndex] = updatedItem
    } else {
      currentList.add(updatedItem)
    }

    // Sort: EcoFlow first, then by signal strength (RSSI)
    currentList.sortWith(compareByDescending<DiscoveredDevice> { it.isEcoFlow }
      .thenByDescending { it.rssi })

    _discoveredDevices.value = currentList
  }

  /**
   * Determines if device matches known EcoFlow naming conventions or service UUIDs.
   */
  private fun isEcoFlowSignature(name: String, address: String, uuids: List<String>): Boolean {
    val upperName = name.uppercase()
    val knownPrefixes = listOf("EF-", "DELTA", "RIVER", "ECOFLOW", "EF_", "SMARTGEN", "WAVE", "GLACIER", "RAPID")
    if (knownPrefixes.any { upperName.contains(it) }) return true

    val knownUuids = listOf("00000001", "00000002", "00000003", "0000FF01")
    if (uuids.any { uuid -> knownUuids.any { uuid.contains(it, ignoreCase = true) } }) return true

    return false
  }

  /**
   * Connects to device GATT server.
   */
  @SuppressLint("MissingPermission")
  fun connectToDevice(address: String) {
    if (!hasBluetoothConnectPermission()) {
      _connectionState.value = GattConnectionState.FAILED
      _lastGattEvent.value = "BLUETOOTH_CONNECT permission missing"
      return
    }

    disconnectDevice()

    val device = try {
      bluetoothAdapter?.getRemoteDevice(address)
    } catch (e: Exception) {
      _connectionState.value = GattConnectionState.FAILED
      _lastGattEvent.value = "Invalid Bluetooth address: $address"
      return
    }

    if (device == null) {
      _connectionState.value = GattConnectionState.FAILED
      _lastGattEvent.value = "Unable to resolve Bluetooth device"
      return
    }

    activeDeviceAddress = address
    _connectionState.value = GattConnectionState.CONNECTING
    _lastGattEvent.value = "Connecting to ${device.name ?: address}..."
    DiagnosticLogger.logOperation("GATT_CONNECT", address, "DISCONNECTED", "CONNECTING", "Initiated")

    try {
      activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
      } else {
        device.connectGatt(context, false, gattCallback)
      }
    } catch (e: Exception) {
      _connectionState.value = GattConnectionState.FAILED
      _lastGattEvent.value = "connectGatt failed: ${e.message}"
    }
  }

  /**
   * Disconnects and cleans up GATT connection resources.
   */
  @SuppressLint("MissingPermission")
  fun disconnectDevice() {
    try {
      activeGatt?.disconnect()
      activeGatt?.close()
    } catch (_: Exception) { }

    activeGatt = null
    activeDeviceAddress = null
    _connectionState.value = GattConnectionState.DISCONNECTED
    _connectedDevice.value = null
    _discoveredServices.value = emptyList()
    _lastGattEvent.value = "Disconnected"
  }

  private val gattCallback = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      val addr = gatt?.device?.address ?: activeDeviceAddress ?: "Unknown"

      if (status != BluetoothGatt.GATT_SUCCESS) {
        _lastGattEvent.value = "GATT error status: $status on $addr"
        DiagnosticLogger.logOperation("GATT_STATUS", addr, "CONNECTING", "ERROR", "Status: $status")

        if (status == 133 && connectionRetryCount < 2) {
          connectionRetryCount++
          _lastGattEvent.value = "Retrying connection after GATT 133 ($connectionRetryCount/2)..."
          scope.launch {
            delay(1000)
            connectToDevice(addr)
          }
          return
        }

        disconnectDevice()
        _connectionState.value = GattConnectionState.FAILED
        return
      }

      connectionRetryCount = 0

      when (newState) {
        BluetoothProfile.STATE_CONNECTED -> {
          _connectionState.value = GattConnectionState.CONNECTED
          _lastGattEvent.value = "Connected to ${gatt?.device?.name ?: addr}. Discovering services..."
          DiagnosticLogger.logOperation("GATT_STATE", addr, "CONNECTING", "CONNECTED", "Success")

          // Find device item in list and mark connected
          val deviceItem = _discoveredDevices.value.find { it.address.equals(addr, ignoreCase = true) }
            ?: DiscoveredDevice(
              id = addr,
              name = gatt?.device?.name ?: "Connected Device",
              address = addr,
              rssi = -55,
              connectionState = GattConnectionState.CONNECTED,
              rawDevice = gatt?.device
            )
          _connectedDevice.value = deviceItem.copy(connectionState = GattConnectionState.CONNECTED)

          scope.launch {
            // Request high MTU for telemetry
            try {
              gatt?.requestMtu(512)
            } catch (_: Exception) { }

            delay(300)
            // Discover services
            _connectionState.value = GattConnectionState.DISCOVERING_SERVICES
            val success = gatt?.discoverServices() ?: false
            if (!success) {
              _lastGattEvent.value = "Service discovery start failed"
            }
          }
        }

        BluetoothProfile.STATE_DISCONNECTED -> {
          _lastGattEvent.value = "Device disconnected: $addr"
          DiagnosticLogger.logOperation("GATT_STATE", addr, "CONNECTED", "DISCONNECTED", "Clean disconnect")
          disconnectDevice()
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
        val servicesList = gatt.services.map { service ->
          val sUuid = service.uuid.toString()
          val charCount = service.characteristics.size
          "Service: ${sUuid.substring(0, 8)}... ($charCount chars)"
        }
        _discoveredServices.value = servicesList
        _connectionState.value = GattConnectionState.READY
        _lastGattEvent.value = "Discovered ${servicesList.size} GATT services. Ready for data exchange."
        DiagnosticLogger.logOperation("GATT_SERVICES", gatt.device.address, "DISCOVERING", "READY", "Services: ${servicesList.size}")

        // Check if any characteristic supports notification (e.g. EcoFlow status or Battery)
        gatt.services.forEach { service ->
          service.characteristics.forEach { char ->
            if ((char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
              enableNotifications(gatt, char)
            }
          }
        }
      } else {
        _lastGattEvent.value = "Service discovery failed with status $status"
      }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        _lastGattEvent.value = "GATT MTU updated to $mtu bytes"
      }
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray
    ) {
      handleIncomingData(characteristic.uuid, value)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
      if (characteristic != null) {
        @Suppress("DEPRECATION")
        val data = characteristic.value ?: ByteArray(0)
        handleIncomingData(characteristic.uuid, data)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
    try {
      gatt.setCharacteristicNotification(characteristic, true)
      val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
      if (cccd != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
          @Suppress("DEPRECATION")
          cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
          @Suppress("DEPRECATION")
          gatt.writeDescriptor(cccd)
        }
      }
    } catch (_: Exception) { }
  }

  private fun handleIncomingData(uuid: UUID, bytes: ByteArray) {
    val hexString = bytes.joinToString("") { "%02X".format(it) }
    _lastGattEvent.value = "Received ${bytes.size} bytes on ${uuid.toString().substring(0, 8)}..."
    DiagnosticLogger.logOperation("GATT_DATA", uuid.toString().substring(0, 8), "DATA", "OK", "Bytes: ${bytes.size} ($hexString)")
  }
}
