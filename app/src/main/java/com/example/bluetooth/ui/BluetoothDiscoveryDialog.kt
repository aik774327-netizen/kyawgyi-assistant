package com.example.bluetooth.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bluetooth.RealBluetoothManager
import com.example.bluetooth.models.BleScanStatus
import com.example.bluetooth.models.DeviceFilterTab
import com.example.bluetooth.models.DiscoveredDevice
import com.example.bluetooth.models.GattConnectionState
import com.example.permissions.AndroidSettingsManager

/**
 * 100% Complete Production Bluetooth Device Discovery & Connection Interface.
 * Enables scanning, filtering, signal monitoring, and GATT connection for EcoFlow and generic BLE devices.
 */
@Composable
fun BluetoothDiscoveryDialog(
  bluetoothManager: RealBluetoothManager,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scanStatus by bluetoothManager.scanStatus.collectAsState()
  val devices by bluetoothManager.discoveredDevices.collectAsState()
  val connectionState by bluetoothManager.connectionState.collectAsState()
  val connectedDevice by bluetoothManager.connectedDevice.collectAsState()
  val discoveredServices by bluetoothManager.discoveredServices.collectAsState()
  val lastGattEvent by bluetoothManager.lastGattEvent.collectAsState()

  var selectedTab by remember { mutableStateOf(DeviceFilterTab.ALL) }
  var showDiagnostics by remember { mutableStateOf(true) }

  // Permission Launcher for Bluetooth
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    if (results.values.all { it }) {
      bluetoothManager.startScan()
    }
  }

  val filteredDevices = remember(devices, selectedTab) {
    when (selectedTab) {
      DeviceFilterTab.ALL -> devices
      DeviceFilterTab.ECOFLOW -> devices.filter { it.isEcoFlow }
      DeviceFilterTab.PAIRED -> devices.filter { it.isPaired }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .testTag("bluetooth_discovery_dialog"),
      color = Color(0xFF090D16)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // 1. Header Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF1E293B), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Bluetooth & EcoFlow Scanner",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Bluetooth စက်ပစ္စည်း ရှာဖွေချိတ်ဆက်မှု စနစ် (GATT v2.5)",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("btn_close_bluetooth_dialog")
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Hardware Warnings (if any)
        if (!bluetoothManager.isBluetoothEnabled) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text("Bluetooth ပိတ်ထားပါသည်", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  Text("စက်ပစ္စည်းများ ရှာဖွေရန် Bluetooth ဖွင့်ပေးပါ", fontSize = 11.sp, color = Color(0xFFFDE68A))
                }
              }
              Button(
                onClick = { AndroidSettingsManager.openBluetoothSettings(context) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("ဖွင့်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        if (scanStatus == BleScanStatus.PERMISSION_REQUIRED) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFFF87171))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text("ခွင့်ပြုချက် လိုအပ်ပါသည်", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  Text("အနီးရှိ စက်ပစ္စည်း ခွင့်ပြုချက် (Nearby Devices) ပေးရန်", fontSize = 11.sp, color = Color(0xFFFECACA))
                }
              }
              Button(
                onClick = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
                  } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN))
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("ခွင့်ပြုမည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // 3. Scan Controls Bar
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanStatus == BleScanStatus.SCANNING) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.dp
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .size(10.dp)
                      .background(
                        if (connectionState.isConnectedOrReady) Color(0xFF10B981) else Color(0xFF64748B),
                        CircleShape
                      )
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = scanStatus.label,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (scanStatus == BleScanStatus.SCANNING) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (scanStatus == BleScanStatus.SCANNING) {
                  OutlinedButton(
                    onClick = { bluetoothManager.stopScan() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                  ) {
                    Text("ရပ်တန့်မည်", fontSize = 11.sp, color = Color(0xFFEF4444))
                  }
                } else {
                  Button(
                    onClick = { bluetoothManager.startScan() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_start_ble_scan")
                  ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("စကင်ဖတ်မည် (Scan)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            if (scanStatus == BleScanStatus.SCANNING) {
              Spacer(modifier = Modifier.height(8.dp))
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFF38BDF8),
                trackColor = Color(0xFF1E293B)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Connected Device Card (if active)
        if (connectedDevice != null || connectionState != GattConnectionState.DISCONNECTED) {
          ActiveDeviceCard(
            device = connectedDevice,
            connectionState = connectionState,
            servicesCount = discoveredServices.size,
            onDisconnect = { bluetoothManager.disconnectDevice() }
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        // 5. Filter Tabs
        TabRow(
          selectedTabIndex = selectedTab.ordinal,
          containerColor = Color(0xFF131C2E),
          contentColor = Color.White,
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
              color = Color(0xFF38BDF8)
            )
          },
          modifier = Modifier.clip(RoundedCornerShape(10.dp))
        ) {
          DeviceFilterTab.values().forEach { tab ->
            Tab(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              text = {
                Text(
                  text = "${tab.myanmar} (${when (tab) {
                    DeviceFilterTab.ALL -> devices.size
                    DeviceFilterTab.ECOFLOW -> devices.count { it.isEcoFlow }
                    DeviceFilterTab.PAIRED -> devices.count { it.isPaired }
                  }})",
                  fontSize = 11.sp,
                  fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                )
              }
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Discovered Devices List
        Text(
          text = "တွေ့ရှိထားသော စက်များ (${filteredDevices.size})",
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredDevices.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF101827))
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = if (scanStatus == BleScanStatus.SCANNING) "စက်ပစ္စည်းများကို ရှာဖွေနေပါသည်..." else "စက်ပစ္စည်း မတွေ့ရှိသေးပါ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
              )
              Text(
                text = "စက်အသစ်များ ရှာဖွေရန် [စကင်ဖတ်မည်] ခလုတ်ကို နှိပ်ပါ",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(filteredDevices, key = { it.address }) { device ->
              DeviceItemRow(
                device = device,
                isConnecting = connectionState == GattConnectionState.CONNECTING && connectedDevice?.address == device.address,
                isConnected = connectionState.isConnectedOrReady && connectedDevice?.address == device.address,
                onConnect = { bluetoothManager.connectToDevice(device.address) },
                onDisconnect = { bluetoothManager.disconnectDevice() }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Live GATT Telemetry Strip
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = lastGattEvent,
                fontSize = 10.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ActiveDeviceCard(
  device: DiscoveredDevice?,
  connectionState: GattConnectionState,
  servicesCount: Int,
  onDisconnect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF132238)),
    border = androidx.compose.foundation.BorderStroke(1.dp, connectionState.color.copy(alpha = 0.5f))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .background(connectionState.color.copy(alpha = 0.15f), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (device?.isEcoFlow == true) Icons.Default.Bolt else Icons.Default.BluetoothConnected,
            contentDescription = null,
            tint = connectionState.color,
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = device?.displayName ?: "Bluetooth Device",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .background(connectionState.color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = connectionState.label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = connectionState.color
              )
            }
          }

          Text(
            text = "MAC: ${device?.address ?: "Unknown"} • GATT Services: $servicesCount",
            fontSize = 10.sp,
            color = Color(0xFF94A3B8)
          )
        }
      }

      Button(
        onClick = onDisconnect,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun DeviceItemRow(
  device: DiscoveredDevice,
  isConnecting: Boolean,
  isConnected: Boolean,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isConnected) Color(0xFF132238) else Color(0xFF111827)
    ),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isConnected) Color(0xFF10B981) else if (device.isEcoFlow) Color(0xFF0284C7).copy(alpha = 0.5f) else Color(0xFF1F2937)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Device Icon / Badge
      Box(
        modifier = Modifier
          .size(38.dp)
          .background(
            if (device.isEcoFlow) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF1E293B),
            CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (device.isEcoFlow) Icons.Default.Bolt else Icons.Default.Bluetooth,
          contentDescription = null,
          tint = if (device.isEcoFlow) Color(0xFF38BDF8) else Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      // Device Details
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = device.displayName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          if (device.isEcoFlow) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .background(Color(0xFF0284C7).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text("EcoFlow", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
          }

          if (device.isPaired) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
              modifier = Modifier
                .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text("Paired", fontSize = 8.sp, color = Color(0xFF94A3B8))
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = device.address,
          fontSize = 10.sp,
          color = Color(0xFF64748B)
        )

        // RSSI Signal Strength
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.SignalCellularAlt,
            contentDescription = null,
            tint = device.signalColor,
            modifier = Modifier.size(11.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${device.rssi} dBm • ${device.signalQualityLabel}",
            fontSize = 9.sp,
            color = device.signalColor
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Action Button
      if (isConnected) {
        Button(
          onClick = onDisconnect,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_disconnect_${device.address}")
        ) {
          Text("ဖြတ်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      } else if (isConnecting) {
        CircularProgressIndicator(
          modifier = Modifier.size(24.dp),
          color = Color(0xFF38BDF8),
          strokeWidth = 2.dp
        )
      } else {
        Button(
          onClick = onConnect,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (device.isEcoFlow) Color(0xFF0284C7) else Color(0xFF1E293B)
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_connect_${device.address}")
        ) {
          Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (device.isEcoFlow) "ချိတ်ဆက်မည်" else "Connect",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (device.isEcoFlow) Color.White else Color(0xFFE2E8F0)
          )
        }
      }
    }
  }
}
