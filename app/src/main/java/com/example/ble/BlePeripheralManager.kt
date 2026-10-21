package com.example.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connection states for BLE Peripheral management.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTING
}

data class BleDiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
    val timestampMs: Long = System.currentTimeMillis()
)

data class BleGattCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isNotifiable: Boolean
)

data class BleGattServiceInfo(
    val uuid: String,
    val characteristics: List<BleGattCharacteristicInfo>
)

data class BleNotificationData(
    val deviceAddress: String,
    val characteristicUuid: String,
    val value: ByteArray,
    val timestampMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean = other is BleNotificationData &&
            deviceAddress == other.deviceAddress &&
            characteristicUuid == other.characteristicUuid &&
            value.contentEquals(other.value)

    override fun hashCode(): Int = 31 * deviceAddress.hashCode() + characteristicUuid.hashCode()
}

data class BleManagerUiState(
    val isBleSupported: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val discoveredDevices: List<BleDiscoveredDevice> = emptyList(),
    val discoveredServices: List<BleGattServiceInfo> = emptyList(),
    val negotiatedMtu: Int = 23,
    val lastError: String? = null
)

/**
 * BlePeripheralManager
 *
 * Production-grade, Coroutine-backed Bluetooth Low Energy (BLE) Peripheral & GATT Client.
 * Governs:
 * 1. Safe permission checks across Android 8 through 14+ (BLUETOOTH_SCAN, BLUETOOTH_CONNECT).
 * 2. Asynchronous device scanning with configurable timeout.
 * 3. Thread-safe sequential GATT execution queue (Android BLE requires one operation at a time).
 * 4. MTU negotiation (up to 512 bytes).
 * 5. Characteristic reads, writes, and descriptor notification subscriptions.
 */
class BlePeripheralManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "BlePeripheralManager"
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val DEFAULT_OPERATION_TIMEOUT_MS = 5000L

        @Volatile
        private var instance: BlePeripheralManager? = null

        fun getInstance(context: Context): BlePeripheralManager {
            return instance ?: synchronized(this) {
                instance ?: BlePeripheralManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gattOperationMutex = Mutex()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val _uiState = MutableStateFlow(
        BleManagerUiState(
            isBleSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
        )
    )
    val uiState: StateFlow<BleManagerUiState> = _uiState.asStateFlow()

    private val _notifications = MutableSharedFlow<BleNotificationData>(extraBufferCapacity = 32)
    val notifications: SharedFlow<BleNotificationData> = _notifications.asSharedFlow()

    private var activeGatt: BluetoothGatt? = null
    private val isScanning = AtomicBoolean(false)
    private val discoveredDevicesMap = ConcurrentHashMap<String, BleDiscoveredDevice>()

    // Pending operation deferreds
    private var pendingReadDeferred: CompletableDeferred<ByteArray?>? = null
    private var pendingWriteDeferred: CompletableDeferred<Boolean>? = null
    private var pendingMtuDeferred: CompletableDeferred<Int>? = null

    // -------------------------------------------------------------
    // PERMISSIONS & CAPABILITY CHECKS
    // -------------------------------------------------------------

    fun hasBlePermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            return scan && connect
        }
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bt = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        return fineLocation || bt
    }

    // -------------------------------------------------------------
    // SCANNING
    // -------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address
                val name = try {
                    if (hasBlePermissions()) device.name ?: "Unknown BLE Device" else "Unknown BLE Device"
                } catch (e: SecurityException) {
                    "Unknown BLE Device"
                }
                val rssi = result.rssi
                val item = BleDiscoveredDevice(name = name, address = address, rssi = rssi, device = device)
                discoveredDevicesMap[address] = item
                _uiState.update { it.copy(discoveredDevices = discoveredDevicesMap.values.toList()) }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with errorCode=$errorCode")
            isScanning.set(false)
            _uiState.update {
                it.copy(
                    connectionState = BleConnectionState.DISCONNECTED,
                    lastError = "BLE Scan failed (code: $errorCode)"
                )
            }
        }
    }

    fun startScan(durationMs: Long = 10000L): Boolean {
        if (!hasBlePermissions()) {
            _uiState.update { it.copy(lastError = "Bluetooth Scan/Connect permissions missing") }
            return false
        }
        val scanner = bleScanner
        if (scanner == null || bluetoothAdapter?.isEnabled != true) {
            _uiState.update { it.copy(lastError = "Bluetooth adapter not active") }
            return false
        }
        if (isScanning.getAndSet(true)) {
            return true
        }

        discoveredDevicesMap.clear()
        _uiState.update {
            it.copy(
                connectionState = BleConnectionState.SCANNING,
                discoveredDevices = emptyList(),
                lastError = null
            )
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            scanner.startScan(null, settings, scanCallback)
            scope.launch {
                delay(durationMs)
                stopScan()
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan", e)
            isScanning.set(false)
            false
        }
    }

    fun stopScan() {
        if (isScanning.getAndSet(false)) {
            try {
                if (hasBlePermissions()) {
                    bleScanner?.stopScan(scanCallback)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping BLE scan: ${e.message}")
            }
            _uiState.update {
                if (it.connectionState == BleConnectionState.SCANNING) {
                    it.copy(connectionState = BleConnectionState.DISCONNECTED)
                } else it
            }
        }
    }

    // -------------------------------------------------------------
    // GATT CONNECTION & CALLBACK
    // -------------------------------------------------------------

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.i(TAG, "GATT onConnectionStateChange: status=$status, newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "GATT status failed: $status. Disconnecting.")
                disconnect()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _uiState.update {
                        it.copy(
                            connectionState = BleConnectionState.DISCOVERING_SERVICES,
                            connectedDeviceName = try { gatt?.device?.name } catch (_: SecurityException) { null } ?: "Connected Device",
                            connectedDeviceAddress = gatt?.device?.address
                        )
                    }
                    try {
                        if (hasBlePermissions()) {
                            gatt?.discoverServices()
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException discovering services", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val serviceList = gatt.services.map { s ->
                    BleGattServiceInfo(
                        uuid = s.uuid.toString(),
                        characteristics = s.characteristics.map { c ->
                            BleGattCharacteristicInfo(
                                uuid = c.uuid.toString(),
                                properties = c.properties,
                                isReadable = (c.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0,
                                isWritable = (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0,
                                isNotifiable = (c.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0
                            )
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        connectionState = BleConnectionState.READY,
                        discoveredServices = serviceList
                    )
                }
                Log.i(TAG, "GATT services discovered (${serviceList.size} services). Ready for IO.")
            } else {
                Log.w(TAG, "Failed discovering GATT services, status=$status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "GATT MTU changed: mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _uiState.update { it.copy(negotiatedMtu = mtu) }
                pendingMtuDeferred?.complete(mtu)
            } else {
                pendingMtuDeferred?.complete(23)
            }
            pendingMtuDeferred = null
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val data = if (status == BluetoothGatt.GATT_SUCCESS) characteristic?.value else null
            pendingReadDeferred?.complete(data)
            pendingReadDeferred = null
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            pendingWriteDeferred?.complete(success)
            pendingWriteDeferred = null
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let { c ->
                val bytes = c.value ?: ByteArray(0)
                val item = BleNotificationData(
                    deviceAddress = gatt?.device?.address ?: "",
                    characteristicUuid = c.uuid.toString(),
                    value = bytes
                )
                _notifications.tryEmit(item)
            }
        }
    }

    suspend fun connect(deviceAddress: String): Boolean = gattOperationMutex.withLock {
        if (!hasBlePermissions()) return false
        stopScan()

        val adapter = bluetoothAdapter ?: return false
        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Bluetooth address: $deviceAddress", e)
            return false
        }

        _uiState.update {
            it.copy(
                connectionState = BleConnectionState.CONNECTING,
                connectedDeviceAddress = deviceAddress,
                lastError = null
            )
        }

        return try {
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
            activeGatt = gatt
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to GATT", e)
            disconnect()
            false
        }
    }

    fun disconnect() {
        try {
            if (hasBlePermissions()) {
                activeGatt?.disconnect()
                activeGatt?.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during GATT disconnect: ${e.message}")
        } finally {
            activeGatt = null
            pendingReadDeferred?.complete(null)
            pendingWriteDeferred?.complete(false)
            pendingMtuDeferred?.complete(23)
            _uiState.update {
                it.copy(
                    connectionState = BleConnectionState.DISCONNECTED,
                    connectedDeviceName = null,
                    connectedDeviceAddress = null,
                    discoveredServices = emptyList()
                )
            }
        }
    }

    // -------------------------------------------------------------
    // GATT OPERATIONS (SEQUENTIAL / MUTEX-GUARDED)
    // -------------------------------------------------------------

    suspend fun requestMtu(mtu: Int): Int = gattOperationMutex.withLock {
        val gatt = activeGatt ?: return 23
        if (!hasBlePermissions()) return 23
        val deferred = CompletableDeferred<Int>()
        pendingMtuDeferred = deferred

        val started = try {
            gatt.requestMtu(mtu.coerceIn(23, 517))
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            pendingMtuDeferred = null
            return 23
        }
        return withTimeoutOrNull(DEFAULT_OPERATION_TIMEOUT_MS) { deferred.await() } ?: 23
    }

    suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): ByteArray? = gattOperationMutex.withLock {
        val gatt = activeGatt ?: return null
        if (!hasBlePermissions()) return null
        val service = gatt.getService(serviceUuid) ?: return null
        val characteristic = service.getCharacteristic(charUuid) ?: return null

        val deferred = CompletableDeferred<ByteArray?>()
        pendingReadDeferred = deferred

        val started = try {
            gatt.readCharacteristic(characteristic)
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            pendingReadDeferred = null
            return null
        }
        return withTimeoutOrNull(DEFAULT_OPERATION_TIMEOUT_MS) { deferred.await() }
    }

    suspend fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean = gattOperationMutex.withLock {
        val gatt = activeGatt ?: return false
        if (!hasBlePermissions()) return false
        val service = gatt.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(charUuid) ?: return false

        characteristic.writeType = writeType
        characteristic.value = data

        val deferred = CompletableDeferred<Boolean>()
        pendingWriteDeferred = deferred

        val started = try {
            gatt.writeCharacteristic(characteristic)
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            pendingWriteDeferred = null
            return false
        }
        return withTimeoutOrNull(DEFAULT_OPERATION_TIMEOUT_MS) { deferred.await() } ?: false
    }

    suspend fun setNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        enable: Boolean = true
    ): Boolean = gattOperationMutex.withLock {
        val gatt = activeGatt ?: return false
        if (!hasBlePermissions()) return false
        val service = gatt.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(charUuid) ?: return false

        val notifSet = try {
            gatt.setCharacteristicNotification(characteristic, enable)
        } catch (e: SecurityException) {
            false
        }
        if (!notifSet) return false

        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return true
        descriptor.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

        val deferred = CompletableDeferred<Boolean>()
        pendingWriteDeferred = deferred
        val written = try {
            gatt.writeDescriptor(descriptor)
        } catch (e: SecurityException) {
            false
        }
        if (!written) {
            pendingWriteDeferred = null
            return false
        }
        return withTimeoutOrNull(DEFAULT_OPERATION_TIMEOUT_MS) { deferred.await() } ?: false
    }
}
