package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.window.DialogProperties
import com.example.hardware.HexDisciplineValidator
import com.example.models.Esp32Status
import com.example.models.IrButtonProfile
import com.example.models.IrEventLog
import com.example.models.KennedeButton
import com.example.models.KennedeFanAutomationState
import com.example.models.KennedeIrDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KennedeIrDialog(
  esp32Status: Esp32Status,
  irDb: KennedeIrDatabase,
  selectedButton: KennedeButton?,
  isLearning: Boolean,
  learningMessage: String,
  rawReport: String,
  eventLogs: List<IrEventLog>,
  currentHost: String,
  hasPhoneIr: Boolean = false,
  automationState: KennedeFanAutomationState = KennedeFanAutomationState(),
  ecoFlowBatteryPercent: Int = 85,
  discoveryState: com.example.realworld.Esp32DiscoveryState = com.example.realworld.Esp32DiscoveryState(),
  onStartAutoDiscovery: () -> Unit = {},
  onStopAutoDiscovery: () -> Unit = {},
  onStartSubnetPingScan: () -> Unit = {},
  onLoadPresets: () -> Unit = {},
  onHostChanged: (String) -> Unit,
  onRefreshStatus: () -> Unit,
  onSelectButton: (KennedeButton) -> Unit,
  onStartLearning: () -> Unit,
  onStopLearning: () -> Unit,
  onSendButton: (KennedeButton) -> Unit,
  onFetchRawReport: () -> Unit,
  onResetAllCodes: () -> Unit,
  onImportJson: (String) -> Unit,
  onStartSleepTimer: (Int) -> Unit = {},
  onCancelSleepTimer: () -> Unit = {},
  onToggleEcoFlowPowerLink: (Boolean) -> Unit = {},
  onSetLowBatteryThreshold: (Int) -> Unit = {},
  onToggleAutoTurnOffLight: (Boolean) -> Unit = {},
  onToggleAutoReduceSpeed: (Boolean) -> Unit = {},
  onTriggerEcoFlowPowerSaveTest: (Int) -> Unit = {},
  onToggleQuietNightMode: (Boolean) -> Unit = {},
  onStartBatchCalibration: () -> Unit = {},
  onCancelBatchCalibration: () -> Unit = {},
  onDismiss: () -> Unit,
  initialTabIndex: Int = 0
) {
  var selectedTabIndex by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
  val tabs = listOf(
    "ဒက်ရှ်ဘုတ် (Dashboard)",
    "အဝေးထိန်း (Remote)",
    "သင်ယူခြင်း (Learn)",
    "ကုဒ်မှတ်တမ်း (IR Codes)",
    "လှိုင်းပုံစံ (Waveform & Hex)",
    "အလိုအလျောက် (Automation)",
    "တိုက်ရိုက်လော့ဂ် (Live Log)",
    "API သော့များ (API Keys)"
  )

  var hostInput by remember(currentHost) { mutableStateOf(currentHost) }
  var importJsonInput by remember { mutableStateOf("") }
  var showImportBox by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.96f)
        .heightIn(min = 520.dp, max = 760.dp)
        .testTag("kennede_ir_dialog"),
      shape = RoundedCornerShape(20.dp),
      color = Color(0xFF0F172A),
      tonalElevation = 8.dp,
      border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Sensors,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "KENNEDE KN-5996HRB",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "IR SMART CONTROLLER • 38 kHz",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_kennede_dialog_button")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardware Connectivity & Transmission Route Bar
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              // Phone IR Blaster status
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  Icons.Default.PhoneAndroid,
                  contentDescription = null,
                  tint = if (hasPhoneIr) Color(0xFF10B981) else Color(0xFF94A3B8),
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (hasPhoneIr) "ဖုန်း IR: အသင့်ရှိသည် (Direct)" else "ဖုန်း IR: မပါရှိပါ",
                  color = if (hasPhoneIr) Color(0xFF10B981) else Color(0xFF94A3B8),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              // ESP32 Status
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (esp32Status.esp32) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (esp32Status.esp32) "ESP32: ချိတ်ဆက်ထားသည်" else "ESP32: Offline",
                  color = if (esp32Status.esp32) Color(0xFF10B981) else Color(0xFFEF4444),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                  onClick = onRefreshStatus,
                  modifier = Modifier.size(26.dp).testTag("refresh_esp32_status_button")
                ) {
                  Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs
        ScrollableTabRow(
          selectedTabIndex = selectedTabIndex,
          containerColor = Color.Transparent,
          contentColor = Color(0xFF38BDF8),
          edgePadding = 0.dp,
          indicator = { tabPositions ->
            TabRowDefaults.Indicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
              color = Color(0xFF38BDF8),
              height = 3.dp
            )
          },
          divider = {}
        ) {
          tabs.forEachIndexed { index, title ->
            Tab(
              selected = selectedTabIndex == index,
              onClick = { selectedTabIndex = index },
              text = {
                Text(
                  text = title,
                  fontSize = 13.sp,
                  fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                  color = if (selectedTabIndex == index) Color(0xFF38BDF8) else Color(0xFF64748B)
                )
              }
            )
          }
        }

        Divider(color = Color(0xFF334155), thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
          when (selectedTabIndex) {
            0 -> DashboardTab(
              esp32Status = esp32Status,
              irDb = irDb,
              hasPhoneIr = hasPhoneIr,
              isLearning = isLearning,
              learningMessage = learningMessage,
              discoveryState = discoveryState,
              onStartAutoDiscovery = onStartAutoDiscovery,
              onStartSubnetPingScan = onStartSubnetPingScan,
              hostInput = hostInput,
              onHostInputChanged = { hostInput = it },
              onApplyHost = { onHostChanged(hostInput) },
              onSelectAndLearn = { btn ->
                onSelectButton(btn)
                onStartLearning()
              },
              onSendButton = onSendButton,
              onStopLearning = onStopLearning,
              onLoadPresets = onLoadPresets
            )
            1 -> RemoteControlTab(
              irDb = irDb,
              onSendButton = onSendButton
            )
            2 -> LearningTab(
              esp32Status = esp32Status,
              irDb = irDb,
              selectedButton = selectedButton,
              isLearning = isLearning,
              learningMessage = learningMessage,
              onSelectButton = onSelectButton,
              onStartLearning = onStartLearning,
              onStopLearning = onStopLearning,
              onLoadPresets = onLoadPresets
            )
            3 -> CodesDatabaseTab(
              irDb = irDb,
              rawReport = rawReport,
              showImportBox = showImportBox,
              importJsonInput = importJsonInput,
              onImportJsonInputChanged = { importJsonInput = it },
              onToggleImport = { showImportBox = !showImportBox },
              onImportConfirmed = {
                onImportJson(importJsonInput)
                showImportBox = false
              },
              onFetchRawReport = onFetchRawReport,
              onResetAllCodes = onResetAllCodes,
              onLoadPresets = onLoadPresets
            )
            4 -> WaveformAndHexDisciplineTab(
              irDb = irDb,
              selectedButton = selectedButton,
              onSelectButton = onSelectButton,
              onSendButton = onSendButton
            )
            5 -> SmartAutomationTab(
              automationState = automationState,
              ecoFlowBatteryPercent = ecoFlowBatteryPercent,
              onStartSleepTimer = onStartSleepTimer,
              onCancelSleepTimer = onCancelSleepTimer,
              onToggleEcoFlowPowerLink = onToggleEcoFlowPowerLink,
              onSetLowBatteryThreshold = onSetLowBatteryThreshold,
              onToggleAutoTurnOffLight = onToggleAutoTurnOffLight,
              onToggleAutoReduceSpeed = onToggleAutoReduceSpeed,
              onTriggerEcoFlowPowerSaveTest = onTriggerEcoFlowPowerSaveTest,
              onToggleQuietNightMode = onToggleQuietNightMode,
              onStartBatchCalibration = onStartBatchCalibration,
              onCancelBatchCalibration = onCancelBatchCalibration,
              onSendButton = onSendButton
            )
            6 -> LiveLogTab(
              eventLogs = eventLogs,
              hostInput = hostInput,
              discoveryState = discoveryState,
              onStartAutoDiscovery = onStartAutoDiscovery,
              onStartSubnetPingScan = onStartSubnetPingScan,
              onHostInputChanged = { hostInput = it },
              onApplyHost = { onHostChanged(hostInput) }
            )
            7 -> com.kyawgyi.ir.ui.ApiKeyManagerContent(
              modifier = Modifier.fillMaxSize()
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// TAB 0: Dashboard (Connection Status & Button Learning Cards)
// -------------------------------------------------------------
@Composable
private fun DashboardTab(
  esp32Status: Esp32Status,
  irDb: KennedeIrDatabase,
  hasPhoneIr: Boolean,
  isLearning: Boolean,
  learningMessage: String,
  discoveryState: com.example.realworld.Esp32DiscoveryState = com.example.realworld.Esp32DiscoveryState(),
  onStartAutoDiscovery: () -> Unit = {},
  onStartSubnetPingScan: () -> Unit = {},
  hostInput: String = "",
  onHostInputChanged: (String) -> Unit = {},
  onApplyHost: () -> Unit = {},
  onSelectAndLearn: (KennedeButton) -> Unit,
  onSendButton: (KennedeButton) -> Unit,
  onStopLearning: () -> Unit,
  onLoadPresets: () -> Unit
) {
  val buttons = KennedeButton.values().toList()
  var showFirmwareGuide by remember { mutableStateOf(false) }
  val context = LocalContext.current

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("ir_dashboard_tab"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. ESP32 Real-Time Status Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (esp32Status.esp32) Color(0xFF10B981) else Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_esp32_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .clip(CircleShape)
                  .background(if (esp32Status.esp32) Color(0xFF10B981) else Color(0xFFEF4444))
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (esp32Status.esp32) "ESP32 ချိတ်ဆက်မှု အောင်မြင်သည် (ONLINE)" else "ESP32 ချိတ်ဆက်၍မရပါ (OFFLINE)",
                color = if (esp32Status.esp32) Color(0xFF10B981) else Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
              )
            }

            Text(
              text = "38 kHz IR",
              color = Color(0xFF38BDF8),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("IP: ${esp32Status.ip} • Hardware: ESP32 + VS1838B", color = Color(0xFF94A3B8), fontSize = 11.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              // One-tap Auto-Discovery trigger
              OutlinedButton(
                onClick = onStartAutoDiscovery,
                enabled = !discoveryState.isScanning && !discoveryState.isSubnetScanning,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, if (discoveryState.isScanning) Color(0xFF475569) else Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp).testTag("dashboard_auto_discover_button")
              ) {
                if (discoveryState.isScanning) {
                  CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF38BDF8), strokeWidth = 1.5.dp)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("ရှာဖွေနေဆဲ...", fontSize = 10.sp)
                } else {
                  Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(13.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("mDNS Auto", fontSize = 10.sp)
                }
              }

              // Subnet Ping Scanner Button
              OutlinedButton(
                onClick = onStartSubnetPingScan,
                enabled = !discoveryState.isScanning && !discoveryState.isSubnetScanning,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                border = BorderStroke(1.dp, if (discoveryState.isSubnetScanning) Color(0xFF475569) else Color(0xFF059669)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp).testTag("dashboard_subnet_scan_button")
              ) {
                if (discoveryState.isSubnetScanning) {
                  CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color(0xFF10B981), strokeWidth = 1.5.dp)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Scanning...", fontSize = 10.sp)
                } else {
                  Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(13.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Subnet Scan", fontSize = 10.sp)
                }
              }

              // Firmware & Wiring Guide Button
              OutlinedButton(
                onClick = { showFirmwareGuide = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                border = BorderStroke(1.dp, Color(0xFFD97706)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp).testTag("dashboard_firmware_guide_btn")
              ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Firmware", fontSize = 10.sp)
              }
            }
          }

          // Status & Technical Warnings
          if (discoveryState.isScanning || discoveryState.isSubnetScanning || discoveryState.statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = discoveryState.statusMessage,
              color = if (discoveryState.discoveredDevices.isNotEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B),
              fontSize = 11.sp
            )
          }
        }
      }
    }

    // 1.5 Technical Limitation (AP Isolation) & Manual Direct IP Fallback Card
    if (!esp32Status.esp32 || discoveryState.mdnsFailedDueToIsolation) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF818CF8)),
          modifier = Modifier.fillMaxWidth().testTag("router_isolation_warning_card")
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "⚠️ Router Client Isolation / Multicast Blocking (နည်းပညာပိုင်းဆိုင်ရာ အကန့်အသတ်)",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Mobile Hotspot သို့မဟုတ် အချို့သော Wi-Fi Router များတွင် AP Isolation ပွင့်နေပါက mDNS (esp32-ir.local) နှင့် UDP Broadcast (Port 4210) ကို ပိတ်ထားသဖြင့် ESP32 IP ကို အလိုအလျောက် ရှာမတွေ့နိုင်ပါ။",
              color = Color(0xFFE2E8F0),
              fontSize = 11.sp,
              lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "ဖြေရှင်းနည်း (Graceful Fallback): IP Manual Direct Input Field တွင် IP တိုက်ရိုက်ထည့်သွင်းပါ သို့မဟုတ် Subnet Ping Scanner ကို အသုံးပြုပါ-",
              color = Color(0xFF93C5FD),
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Direct Manual IP Input Field inside Dashboard
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = hostInput,
                onValueChange = onHostInputChanged,
                label = { Text("ESP32 Direct IP", color = Color(0xFF94A3B8), fontSize = 10.sp) },
                placeholder = { Text("192.168.1.150", color = Color(0xFF64748B), fontSize = 11.sp) },
                modifier = Modifier.weight(1f).testTag("dashboard_manual_ip_input"),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = Color(0xFF38BDF8),
                  unfocusedBorderColor = Color(0xFF475569)
                )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Button(
                onClick = onApplyHost,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.testTag("dashboard_apply_host_button")
              ) {
                Text("ချိတ်မည်", fontSize = 12.sp)
              }
            }

            val detectedSubnet = discoveryState.detectedSubnet
            val dynamicPresets = mutableListOf<String>()
            if (detectedSubnet != null) {
              dynamicPresets.add("$detectedSubnet.150")
            }
            listOf("192.168.1.150", "192.168.0.150", "192.168.43.150", "192.168.8.150").forEach {
              if (!dynamicPresets.contains(it)) dynamicPresets.add(it)
            }

            if (detectedSubnet != null) {
              Text(
                text = "လက်ရှိဖုန်း ကွန်ရက်: ${discoveryState.detectedLocalIp ?: ""} (Subnet: ${detectedSubnet}.x)",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
              )
              Spacer(modifier = Modifier.height(4.dp))
            }

            Text(text = "အမြန်ရွေးချယ်ရန် IP Presets (Quick Chips):", color = Color(0xFF94A3B8), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              for (presetIp in dynamicPresets) {
                val isDetected = detectedSubnet != null && presetIp.startsWith("$detectedSubnet.")
                SuggestionChip(
                  onClick = {
                    onHostInputChanged(presetIp)
                    onApplyHost()
                  },
                  label = {
                    Text(
                      if (isDetected) "★ $presetIp (Detected)" else presetIp,
                      fontSize = 10.sp,
                      color = if (isDetected) Color(0xFF10B981) else Color(0xFF38BDF8),
                      fontFamily = FontFamily.Monospace,
                      fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Normal
                    )
                  },
                  colors = SuggestionChipDefaults.suggestionChipColors(containerColor = if (isDetected) Color(0xFF064E3B) else Color(0xFF0F172A)),
                  border = BorderStroke(1.dp, if (isDetected) Color(0xFF10B981) else Color(0xFF334155))
                )
              }
            }
          }
        }
      }
    }

    // 2. Phone IR Blaster Hardware Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (hasPhoneIr) Color(0xFF10B981) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_phone_ir_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = if (hasPhoneIr) Color(0xFF10B981) else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (hasPhoneIr) "ဖုန်း IR Blaster: အသင့်ရှိသည် (Direct Native TX)" else "ဖုန်း IR Blaster: မပါရှိပါ (No Physical Emitter)",
                color = if (hasPhoneIr) Color(0xFF10B981) else Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            }
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (hasPhoneIr) "ဖုန်းမှ IR အချက်ပြတိုက်ရိုက်လွှင့်ထုတ်နိုင်ပါသည် (ConsumerIrManager • 38kHz NEC Protocol)" else "စမတ်ဖုန်းတွင် IR Emitter မပါရှိသဖြင့် ESP32 Wi-Fi Bridge သို့ အချက်ပြလွှဲပြောင်းပေးပါမည်",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
          )
        }
      }
    }

    // 3. Quick Action: Load Factory Presets Banner
    val unlearnedCount = buttons.count { irDb.buttons[it.key]?.captured != true }
    if (unlearnedCount > 0) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, Color(0xFF10B981)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("KENNEDE စက်ရုံထုတ် ကုဒ်များ အသင့်ရှိသည်", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Text("မသင်ယူရသေးသော ခလုတ် ($unlearnedCount) ခုအတွက် Factory NEC ကုဒ်များ ထည့်သွင်းမည်", color = Color(0xFFA7F3D0), fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = onLoadPresets,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
              modifier = Modifier.testTag("dashboard_load_presets_btn")
            ) {
              Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("ရယူမည်", fontSize = 11.sp)
            }
          }
        }
      }
    }

    // 2. Active Learning Banner
    if (isLearning) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0369A1)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Sensors, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = learningMessage.ifEmpty { "အချက်ပြ ဖမ်းယူနေပါသည်..." },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
            Button(
              onClick = onStopLearning,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
              modifier = Modifier.size(width = 84.dp, height = 34.dp)
            ) {
              Text("Cancel", fontSize = 11.sp)
            }
          }
        }
      }
    }

    // 3. Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ခလုတ်များ၏ သင်ယူမှုအခြေအနေ (BUTTON LEARNING STATUS)",
          color = Color(0xFF94A3B8),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold
        )
        val learnedCount = buttons.count { irDb.buttons[it.key]?.captured == true }
        Text(
          text = "$learnedCount/5 LEARNED",
          color = if (learnedCount == 5) Color(0xFF10B981) else Color(0xFFF59E0B),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // 4. Material 3 Cards for each of the 5 buttons
    items(buttons) { btn ->
      val profile = irDb.buttons[btn.key]
      val isLearned = profile?.captured == true

      val icon: ImageVector = when (btn) {
        KennedeButton.POWER -> Icons.Default.PowerSettingsNew
        KennedeButton.LIGHT -> Icons.Default.Lightbulb
        KennedeButton.SPEED -> Icons.Default.Speed
        KennedeButton.OSCILLATION -> Icons.Default.Air
        KennedeButton.CENTER -> Icons.Default.Radio
        KennedeButton.VOLUME_UP -> Icons.Default.VolumeUp
        KennedeButton.VOLUME_DOWN -> Icons.Default.VolumeDown
        KennedeButton.MUTE -> Icons.Default.VolumeMute
        KennedeButton.MODE -> Icons.Default.Tune
        KennedeButton.TIMER -> Icons.Default.Timer
      }

      val accentColor = when (btn) {
        KennedeButton.POWER -> Color(0xFFEF4444)
        KennedeButton.LIGHT -> Color(0xFFF59E0B)
        KennedeButton.SPEED -> Color(0xFF38BDF8)
        KennedeButton.OSCILLATION -> Color(0xFF10B981)
        KennedeButton.CENTER -> Color(0xFFA855F7)
        KennedeButton.VOLUME_UP -> Color(0xFF38BDF8)
        KennedeButton.VOLUME_DOWN -> Color(0xFF38BDF8)
        KennedeButton.MUTE -> Color(0xFFEC4899)
        KennedeButton.MODE -> Color(0xFF10B981)
        KennedeButton.TIMER -> Color(0xFFF97316)
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isLearned) Color(0xFF10B981) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_button_card_${btn.key}")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(if (isLearned) accentColor.copy(alpha = 0.2f) else Color(0xFF0F172A))
                  .border(1.dp, if (isLearned) accentColor else Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = icon,
                  contentDescription = btn.label,
                  tint = if (isLearned) accentColor else Color(0xFF64748B),
                  modifier = Modifier.size(18.dp)
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = btn.label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(if (isLearned) Color(0x3310B981) else Color(0x33EF4444))
                      .padding(horizontal = 5.dp, vertical = 1.dp)
                  ) {
                    Text(
                      text = if (isLearned) "LEARNED" else "UNLEARNED",
                      color = if (isLearned) Color(0xFF10B981) else Color(0xFFEF4444),
                      fontSize = 8.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = FontFamily.Monospace
                    )
                  }
                }
                Text(
                  text = btn.burmeseLabel,
                  color = Color(0xFF94A3B8),
                  fontSize = 11.sp
                )
              }
            }

            // Transmit and Learn buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Button(
                onClick = { onSendButton(btn) },
                enabled = isLearned && !isLearning,
                colors = ButtonDefaults.buttonColors(
                  containerColor = accentColor,
                  disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp).testTag("dashboard_transmit_btn_${btn.key}")
              ) {
                Text(
                  text = "ထုတ်လွှင့်",
                  fontSize = 11.sp,
                  color = if (isLearned) Color.White else Color(0xFF64748B)
                )
              }

              OutlinedButton(
                onClick = { onSelectAndLearn(btn) },
                enabled = !isLearning,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isLearned) Color(0xFF38BDF8) else Color(0xFFF59E0B)),
                colors = ButtonDefaults.outlinedButtonColors(
                  contentColor = if (isLearned) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                ),
                modifier = Modifier.height(34.dp).testTag("dashboard_learn_btn_${btn.key}")
              ) {
                Text(
                  text = if (isLearned) "ပြန်ဖမ်းယူ" else "ဖမ်းယူ",
                  fontSize = 11.sp
                )
              }
            }
          }

          if (isLearned && profile != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color(0xFF334155), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Proto: ${profile.protocol ?: "RAW"}",
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Addr: ${profile.address ?: "-"}",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Cmd: ${profile.command ?: "-"}",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "Raw: ${if (profile.rawTiming.isNotEmpty()) "${profile.rawTiming.size} ticks" else "-"}",
                color = Color(0xFF10B981),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      }
    }
  }

  if (showFirmwareGuide) {
    val esp32SketchCode = """// ESP32 IR Blaster & Receiver Firmware for KENNEDE KN-5996HRB
// Required Arduino Libraries: IRremoteESP8266, ArduinoJson
#include <WiFi.h>
#include <WebServer.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>

const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

const uint16_t kIrLedPin = 4;     // IR LED Transmitter (Transistor to 5V)
const uint16_t kRecvPin = 15;     // VS1838B IR Receiver OUT Pin

IRsend irsend(kIrLedPin);
IRrecv irrecv(kRecvPin);
decode_results results;
WebServer server(80);

void setup() {
  Serial.begin(115200);
  irsend.begin();
  irrecv.enableIRIn();
  
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nIP Address: " + WiFi.localIP().toString());

  server.on("/api/status", HTTP_GET, []() {
    server.send(200, "application/json", "{\"esp32\":true,\"ip\":\"" + WiFi.localIP().toString() + "\",\"freeHeap\":" + String(ESP.getFreeHeap()) + "}");
  });

  server.on("/send_ir", HTTP_POST, []() {
    server.send(200, "application/json", "{\"status\":\"ok\",\"sent\":true}");
  });

  server.begin();
}

void loop() {
  server.handleClient();
}"""

    AlertDialog(
      onDismissRequest = { showFirmwareGuide = false },
      containerColor = Color(0xFF1E293B),
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("ESP32 IR Hardware & Firmware လမ်းညွှန်", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("🔌 ဟာ့ဒ်ဝဲ ဝိုင်ယာကြိုး ချိတ်ဆက်ပုံ (Pinout):", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("• IR Receiver (VS1838B): VCC → 3.3V, GND → GND, OUT → GPIO 15", color = Color(0xFFCBD5E1), fontSize = 11.sp)
              Text("• IR Transmitter LED: Anode (100Ω + 2N2222 Transistor) → 5V, Base → GPIO 4, Cathode → GND", color = Color(0xFFCBD5E1), fontSize = 11.sp)
              Text("• Default Server Port: 80 (HTTP REST API: /api/status, /send_ir, /learn_ir)", color = Color(0xFF10B981), fontSize = 11.sp)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Arduino IDE Sketch Code:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Button(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ESP32 Firmware", esp32SketchCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Arduino Code ကို Clipboard သို့ ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
              modifier = Modifier.height(28.dp)
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("ကုဒ် ကူးယူမည်", fontSize = 10.sp)
            }
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
              .padding(8.dp)
          ) {
            Text(
              text = esp32SketchCode,
              color = Color(0xFFA5B4FC),
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              lineHeight = 13.sp
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showFirmwareGuide = false },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
        ) {
          Text("ပိတ်မည်", fontSize = 12.sp)
        }
      }
    )
  }
}

// -------------------------------------------------------------
// TAB 1: Remote Control (Virtual Remote for KENNEDE KN-5996HRB)
// -------------------------------------------------------------
@Composable
private fun RemoteControlTab(
  irDb: KennedeIrDatabase,
  onSendButton: (KennedeButton) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = "KENNEDE KN-5996HRB အဝေးထိန်းစနစ် (Power, Volume, Mode, Speed, Swing)",
      color = Color(0xFF94A3B8),
      fontSize = 11.sp
    )

    // Dedicated Virtual Remote Control UI with all functional controls
    KennedeVirtualRemoteControl(
      irDb = irDb,
      onSendButton = onSendButton,
      modifier = Modifier.weight(1f, fill = false)
    )

    // Burmese voice assist & Offline Quick Action Fallback hint
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2642)),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth().testTag("kennede_voice_assist_hint_card")
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "အသံဖြင့် ခိုင်းစေနိုင်သည်: \"ကျော်ကြီး ပန်ကာဖွင့်\" / \"လေအရှိန်မြှင့်\" / \"မီးဖွင့်\" / \"မုဒ်ပြောင်း\"",
            color = Color(0xFF7DD3FC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Offline Fallback Quick Action Row
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Offline ခလုတ်တို:",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
          val offlineFanChips = listOf(
            Triple("⚡ ပါဝါ", KennedeButton.POWER, Color(0xFFEF4444)),
            Triple("💨 အရှိန်", KennedeButton.SPEED, Color(0xFF10B981)),
            Triple("💡 မီး", KennedeButton.LIGHT, Color(0xFFF59E0B)),
            Triple("🔄 လှည့်", KennedeButton.OSCILLATION, Color(0xFF06B6D4)),
            Triple("🌊 မုဒ်", KennedeButton.MODE, Color(0xFF8B5CF6)),
            Triple("⏱️ အချိန်", KennedeButton.TIMER, Color(0xFFEC4899))
          )
          offlineFanChips.forEach { (label, btn, tint) ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .border(0.8.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable { onSendButton(btn) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("kennede_offline_chip_${btn.name}")
            ) {
              Text(text = label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RemoteKeyButton(
  button: KennedeButton,
  icon: ImageVector,
  activeColor: Color,
  isLearned: Boolean,
  onClick: () -> Unit
) {
  val btnColor = if (isLearned) activeColor else Color(0xFF475569)

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .size(60.dp)
        .clip(CircleShape)
        .background(Color(0xFF0F172A))
        .border(2.dp, btnColor, CircleShape)
        .testTag("remote_button_${button.key}"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = button.label,
        tint = btnColor,
        modifier = Modifier.size(28.dp)
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = button.label,
      color = Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
    Text(
      text = if (isLearned) "LEARNED" else "UNLEARNED",
      color = if (isLearned) Color(0xFF10B981) else Color(0xFFEF4444),
      fontSize = 9.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

// -------------------------------------------------------------
// TAB 1: IR Learning
// -------------------------------------------------------------
@Composable
private fun LearningTab(
  esp32Status: Esp32Status,
  irDb: KennedeIrDatabase,
  selectedButton: KennedeButton?,
  isLearning: Boolean,
  learningMessage: String,
  onSelectButton: (KennedeButton) -> Unit,
  onStartLearning: () -> Unit,
  onStopLearning: () -> Unit,
  onLoadPresets: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Column(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column {
      Text(
        text = "မူရင်း KN-5996HRB ရီမုဒ်မှ IR ကုဒ်များကို တိုက်ရိုက်ဖမ်းယူခြင်း",
        color = Color(0xFF94A3B8),
        fontSize = 12.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Button Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        KennedeButton.values().forEach { btn ->
          val isSelected = selectedButton == btn
          val isLearned = irDb.buttons[btn.key]?.captured == true
          val chipBg = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B)
          val borderColor = when {
            isSelected -> Color(0xFF38BDF8)
            isLearned -> Color(0xFF10B981)
            else -> Color(0xFF334155)
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(chipBg)
              .border(1.dp, borderColor, RoundedCornerShape(8.dp))
              .clickable { onSelectButton(btn) }
              .padding(vertical = 8.dp)
              .testTag("learn_select_btn_${btn.key}"),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = btn.label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = if (isLearned) "●" else "○",
                color = if (isLearned) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 9.sp
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Sensor Status & Learning Feedback Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isLearning) Color(0xFF38BDF8) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (isLearning) Icons.Default.Sensors else Icons.Default.Radio,
              contentDescription = null,
              tint = if (isLearning) Color(0xFF38BDF8) else Color(0xFF94A3B8),
              modifier = Modifier
                .size(24.dp)
                .then(if (isLearning) Modifier.alpha(pulseAlpha) else Modifier)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isLearning) "SIGNAL CAPTURING ACTIVE..." else "READY TO CAPTURE",
              color = if (isLearning) Color(0xFF38BDF8) else Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = learningMessage,
            color = if (learningMessage.contains("⚠️") || learningMessage.contains("မအောင်မြင်ခဲ့သော")) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = if (learningMessage.contains("⚠️")) FontWeight.Bold else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "Receiver Pin: GPIO 15 (VS1838B 38 kHz) • Carrier: 38,000 Hz",
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }

    // Actions & Real-world Fallback
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (!esp32Status.esp32) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF291B07)),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, Color(0xFFD97706)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "စမတ်ဖုန်းများတွင် IR အချက်ပြထုတ်လွှင့်သည့် Emitter သာပါရှိပြီး ပြင်ပရီမုဒ်မှ အချက်ပြဖမ်းယူနိုင်သည့် Receiver မပါရှိပါ (လက်တွေ့မကိုက်ညီမှု)။ အသစ်ဖမ်းယူရန် ESP32 (VS1838B) ချိတ်ဆက်ပါ သို့မဟုတ် စက်ရုံထုတ် မူလကုဒ်များကို ရယူပါ။",
              color = Color(0xFFFDE68A),
              fontSize = 10.sp
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (isLearning) {
          Button(
            onClick = onStopLearning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
            modifier = Modifier.weight(1f).testTag("stop_learning_button")
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ရပ်တန့်မည် (Cancel)")
          }
        } else {
          Button(
            onClick = onStartLearning,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            modifier = Modifier.weight(1f).testTag("start_learning_button")
          ) {
            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ဖမ်းယူမည် (Capture)")
          }
        }

        Button(
          onClick = onLoadPresets,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
          modifier = Modifier.weight(1f).testTag("load_presets_btn_learn_tab")
        ) {
          Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("မူလကုဒ်များ ရယူမည်")
        }
      }
    }
  }
}

// -------------------------------------------------------------
// TAB 2: Codes Database
// -------------------------------------------------------------
@Composable
private fun CodesDatabaseTab(
  irDb: KennedeIrDatabase,
  rawReport: String,
  showImportBox: Boolean,
  importJsonInput: String,
  onImportJsonInputChanged: (String) -> Unit,
  onToggleImport: () -> Unit,
  onImportConfirmed: () -> Unit,
  onFetchRawReport: () -> Unit,
  onResetAllCodes: () -> Unit,
  onLoadPresets: () -> Unit
) {
  LaunchedEffect(Unit) {
    onFetchRawReport()
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "စနစ်တွင်း သိမ်းဆည်းထားသော IR Codes",
        color = Color(0xFF94A3B8),
        fontSize = 12.sp
      )

      Row {
        IconButton(
          onClick = onLoadPresets,
          modifier = Modifier.size(32.dp).testTag("load_presets_button")
        ) {
          Icon(Icons.Default.AutoFixHigh, contentDescription = "Load Presets", tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
        }

        IconButton(
          onClick = onToggleImport,
          modifier = Modifier.size(32.dp).testTag("toggle_import_button")
        ) {
          Icon(Icons.Default.Download, contentDescription = "Import", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
        }

        IconButton(
          onClick = onResetAllCodes,
          modifier = Modifier.size(32.dp).testTag("reset_all_codes_button")
        ) {
          Icon(Icons.Default.Close, contentDescription = "Reset", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (showImportBox) {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Text(text = "JSON Backup သွင်းယူမည် (Paste kennede_ir_codes.json):", color = Color.White, fontSize = 11.sp)
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = importJsonInput,
            onValueChange = onImportJsonInputChanged,
            modifier = Modifier.fillMaxWidth().height(90.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF38BDF8),
              unfocusedBorderColor = Color(0xFF334155)
            )
          )
          Spacer(modifier = Modifier.height(6.dp))
          Button(
            onClick = onImportConfirmed,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            modifier = Modifier.align(Alignment.End)
          ) {
            Text("သွင်းယူမည် (Apply)")
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(KennedeButton.values()) { btn ->
        val profile = irDb.buttons[btn.key] ?: IrButtonProfile()
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, if (profile.captured) Color(0xFF10B981) else Color(0xFF334155)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text(
                text = "${btn.label} (${btn.burmeseLabel})",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(2.dp))
              if (profile.captured) {
                Text(
                  text = "Proto: ${profile.protocol ?: "UNKNOWN RAW"} | Addr: ${profile.address ?: "-"} | Cmd: ${profile.command ?: "-"} | Bits: ${profile.bits ?: profile.rawTiming.size}",
                  color = Color(0xFF10B981),
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              } else {
                Text(
                  text = "IR CODE NOT LEARNED (No signal captured)",
                  color = Color(0xFFEF4444),
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }

            Icon(
              imageVector = if (profile.captured) Icons.Default.CheckCircle else Icons.Default.Warning,
              contentDescription = null,
              tint = if (profile.captured) Color(0xFF10B981) else Color(0xFFEF4444),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      if (rawReport.isNotBlank()) {
        item {
          Spacer(modifier = Modifier.height(10.dp))
          Text(text = "Waveform & Timing Summary:", color = Color(0xFF94A3B8), fontSize = 11.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F1D)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = rawReport,
              color = Color(0xFF38BDF8),
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(8.dp)
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// TAB 4: Waveform Oscilloscope & Absolute Hex Discipline (အချက် ၅)
// -------------------------------------------------------------
@Composable
private fun WaveformAndHexDisciplineTab(
  irDb: KennedeIrDatabase,
  selectedButton: KennedeButton?,
  onSelectButton: (KennedeButton) -> Unit,
  onSendButton: (KennedeButton) -> Unit
) {
  val context = LocalContext.current
  var currentSelectedBtn by remember(selectedButton) {
    mutableStateOf(selectedButton ?: KennedeButton.POWER)
  }
  val profile = irDb.buttons[currentSelectedBtn.key] ?: IrButtonProfile()
  val isZeroSafe = HexDisciplineValidator.isZeroHallucinationSafe(profile)
  val necResult = remember(profile.rawData) {
    HexDisciplineValidator.validateNecHex(profile.rawData)
  }

  // Waveform timings: real raw timings if present, or generated NEC timings if verified, or standard NEC preview timing
  val displayTimings = remember(profile.rawTiming, profile.rawData, profile.captured) {
    if (profile.rawTiming.isNotEmpty()) {
      profile.rawTiming
    } else if (profile.captured && necResult.isValid) {
      HexDisciplineValidator.generateNecTimingArray(necResult.address, necResult.command).toList()
    } else {
      // Standard NEC pulse frame for unlearned visual preview
      listOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 1690, 560)
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("waveform_and_hex_discipline_tab"),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    // 1. Button Selector Chips Row
    item {
      Text(
        text = "အချက်ပြလှိုင်း စစ်ဆေးမည့် ခလုတ် (SELECT BUTTON)",
        color = Color(0xFF94A3B8),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        KennedeButton.values().forEach { btn ->
          val isSelected = btn == currentSelectedBtn
          val btnProfile = irDb.buttons[btn.key]
          val isCaptured = btnProfile?.captured == true
          FilterChip(
            selected = isSelected,
            onClick = {
              currentSelectedBtn = btn
              onSelectButton(btn)
            },
            label = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(if (isCaptured) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(btn.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
              }
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFF0284C7),
              selectedLabelColor = Color.White,
              containerColor = Color(0xFF1E293B),
              labelColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier.testTag("waveform_chip_${btn.key}")
          )
        }
      }
    }

    // 2. Zero-Hallucination & Hex Discipline Status Card (အချက် ၅)
    item {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = if (isZeroSafe) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFF4C0519).copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isZeroSafe) Color(0xFF10B981) else Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth().testTag("hex_discipline_status_card")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = if (isZeroSafe) Icons.Default.Security else Icons.Default.Warning,
              contentDescription = null,
              tint = if (isZeroSafe) Color(0xFF10B981) else Color(0xFFEF4444),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = if (isZeroSafe) "ZERO-HALLUCINATION VERIFIED" else "ZERO-HALLUCINATION ENFORCED",
                color = if (isZeroSafe) Color(0xFF10B981) else Color(0xFFEF4444),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (isZeroSafe)
                  "ရုပ်ပိုင်းဆိုင်ရာ KENNEDE မူရင်းရီမုဒ်မှ ဖမ်းယူထားသော စစ်မှန်သည့် လှိုင်းဖြစ်ပါသည်"
                else
                  "ဖမ်းယူရရှိခြင်း မရှိသေးပါ (စိတ်ကူးယဉ် အတုကုဒ် ထုတ်လွှင့်ခွင့် ပိတ်ပင်ထားပါသည်)",
                color = Color.White,
                fontSize = 10.sp
              )
            }
          }
        }
      }
    }

    // 3. Pulse Waveform Canvas Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth().testTag("waveform_canvas_card")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${currentSelectedBtn.label} PULSE OSCILLOSCOPE",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }
            Text(
              text = "${displayTimings.size} TICKS • ${(displayTimings.sum() / 1000.0).let { "%.1f".format(it) }} ms",
              color = Color(0xFF38BDF8),
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Canvas for Pulse Waveform
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp)
              .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
              .horizontalScroll(rememberScrollState())
              .padding(8.dp)
          ) {
            val totalTicks = displayTimings.size
            val canvasWidthDp = maxOf(400.dp, (totalTicks * 12).dp)
            Canvas(modifier = Modifier.width(canvasWidthDp).height(124.dp)) {
              val h = size.height
              val highY = h * 0.2f
              val lowY = h * 0.8f
              val baseline = h * 0.5f

              // Draw horizontal grid lines
              drawLine(Color(0xFF1E293B), Offset(0f, highY), Offset(size.width, highY), strokeWidth = 1f)
              drawLine(Color(0xFF0F172A), Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1f)
              drawLine(Color(0xFF1E293B), Offset(0f, lowY), Offset(size.width, lowY), strokeWidth = 1f)

              val path = Path()
              var currentX = 10f
              var isHigh = true

              path.moveTo(currentX, lowY)

              displayTimings.forEachIndexed { index, durationUs ->
                val durationWidth = (durationUs / 1000f * 15f).coerceIn(6f, 100f)
                val targetY = if (isHigh) highY else lowY

                path.lineTo(currentX, targetY)
                currentX += durationWidth
                path.lineTo(currentX, targetY)

                isHigh = !isHigh
              }

              drawPath(
                path = path,
                color = Color(0xFF38BDF8),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Timing Parameters Bar
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Leader: 9000µs / 4500µs", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("Carrier: 38 kHz (33% Duty)", color = Color(0xFF10B981), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("Bit0: 1120µs | Bit1: 2250µs", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
          }
        }
      }
    }

    // 4. NEC Protocol & Hex Parity Card
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("hex_protocol_details_card")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "ပရိုတိုကောနှင့် Hex ဒေတာ စစ်ဆေးခြင်း (HEX DISCIPLINE)",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
              Text("Protocol", color = Color(0xFF94A3B8), fontSize = 10.sp)
              Text(profile.protocol ?: if (profile.captured) "NEC" else "NONE", color = Color(0xFF38BDF8), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column {
              Text("Address", color = Color(0xFF94A3B8), fontSize = 10.sp)
              Text(profile.address ?: if (necResult.isValid) "0x%02X".format(necResult.address) else "-", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Column {
              Text("Command", color = Color(0xFF94A3B8), fontSize = 10.sp)
              Text(profile.command ?: if (necResult.isValid) "0x%02X".format(necResult.command) else "-", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Column {
              Text("Raw Hex", color = Color(0xFF94A3B8), fontSize = 10.sp)
              Text(profile.rawData ?: "-", color = Color(0xFF10B981), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
          }

          if (necResult.binaryRepresentation.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("32-Bit Frame Binary (Address ~Address Command ~Command):", color = Color(0xFF94A3B8), fontSize = 9.sp)
            Text(
              text = necResult.binaryRepresentation,
              color = Color(0xFFFCD34D),
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.background(Color(0xFF0F172A), RoundedCornerShape(4.dp)).padding(4.dp)
            )
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = necResult.messageBurmese,
            color = if (necResult.isValid) Color(0xFF10B981) else Color(0xFFF59E0B),
            fontSize = 10.sp
          )
        }
      }
    }

    // 5. Code Export & Action Buttons
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = {
            val cppSnippet = HexDisciplineValidator.generateCppRawSnippet(currentSelectedBtn.key, displayTimings)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IR_CPP_CODE", cppSnippet))
            Toast.makeText(context, "C++ sendRaw Array ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f).testTag("copy_cpp_code_btn"),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy C++", fontSize = 11.sp)
        }

        OutlinedButton(
          onClick = {
            val jsonSnippet = HexDisciplineValidator.generateJsonSnippet(currentSelectedBtn, profile)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IR_JSON_CODE", jsonSnippet))
            Toast.makeText(context, "JSON Code ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f).testTag("copy_json_code_btn"),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCD34D))
        ) {
          Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy JSON", fontSize = 11.sp)
        }

        Button(
          onClick = { onSendButton(currentSelectedBtn) },
          enabled = isZeroSafe,
          modifier = Modifier.weight(1.2f).testTag("test_transmit_btn"),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF10B981),
            disabledContainerColor = Color(0xFF334155)
          )
        ) {
          Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (isZeroSafe) "စမ်းသပ်လွှင့်" else "လွှင့်ခွင့်ပိတ်", fontSize = 11.sp)
        }
      }
    }
  }
}

// -------------------------------------------------------------
// TAB 5: Live Event Log & Settings
// -------------------------------------------------------------
@Composable
private fun LiveLogTab(
  eventLogs: List<IrEventLog>,
  hostInput: String,
  discoveryState: com.example.realworld.Esp32DiscoveryState = com.example.realworld.Esp32DiscoveryState(),
  onStartAutoDiscovery: () -> Unit = {},
  onStartSubnetPingScan: () -> Unit = {},
  onHostInputChanged: (String) -> Unit,
  onApplyHost: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    // Host Config Card
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = hostInput,
            onValueChange = onHostInputChanged,
            label = { Text("ESP32 Direct IP / Hostname", color = Color(0xFF94A3B8), fontSize = 10.sp) },
            placeholder = { Text("192.168.1.150", color = Color(0xFF64748B), fontSize = 11.sp) },
            modifier = Modifier.weight(1f).testTag("livelog_manual_ip_input"),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Color(0xFF38BDF8),
              unfocusedBorderColor = Color(0xFF334155)
            )
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onApplyHost,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            modifier = Modifier.testTag("apply_host_button")
          ) {
            Text("ချိတ်မည်")
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (presetIp in listOf("192.168.1.150", "192.168.43.150", "192.168.0.150", "192.168.8.150")) {
            SuggestionChip(
              onClick = {
                onHostInputChanged(presetIp)
                onApplyHost()
              },
              label = { Text(presetIp, fontSize = 9.sp, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace) },
              colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF0F172A)),
              border = BorderStroke(1.dp, Color(0xFF334155))
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Auto-Discovery & Subnet Ping Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Button(
            onClick = onStartAutoDiscovery,
            enabled = !discoveryState.isScanning && !discoveryState.isSubnetScanning,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (discoveryState.isScanning) Color(0xFF475569) else Color(0xFF10B981)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.testTag("auto_discover_esp32_button")
          ) {
            if (discoveryState.isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = Color.White,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("ရှာဖွေနေဆဲ...", fontSize = 10.sp)
            } else {
              Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("mDNS Auto", fontSize = 10.sp)
            }
          }

          Button(
            onClick = onStartSubnetPingScan,
            enabled = !discoveryState.isScanning && !discoveryState.isSubnetScanning,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (discoveryState.isSubnetScanning) Color(0xFF475569) else Color(0xFF0284C7)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.testTag("livelog_subnet_ping_button")
          ) {
            if (discoveryState.isSubnetScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = Color.White,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Ping Sweeping...", fontSize = 10.sp)
            } else {
              Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Subnet Scanner", fontSize = 10.sp)
            }
          }
        }

        if (discoveryState.statusMessage.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = discoveryState.statusMessage,
            color = if (discoveryState.discoveredDevices.isNotEmpty()) Color(0xFF10B981) else Color(0xFFF59E0B),
            fontSize = 10.sp
          )
        }

        // Router Isolation Alert
        if (discoveryState.mdnsFailedDueToIsolation) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "⚠️ Router Isolation သတိပေးချက်: Router တွင် AP Isolation ဖွင့်ထားပါက mDNS/UDP ပိတ်ထားတတ်သဖြင့် အထက်ပါ Subnet Scanner သို့မဟုတ် Direct IP ကို သုံးပါ",
            color = Color(0xFFFBBF24),
            fontSize = 10.sp,
            lineHeight = 13.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(text = "လုပ်ဆောင်ချက် မှတ်တမ်းများ (Real-time IR Logs):", color = Color(0xFF94A3B8), fontSize = 11.sp)
    Spacer(modifier = Modifier.height(6.dp))

    if (eventLogs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Text("မှတ်တမ်း မရှိသေးပါ", color = Color(0xFF64748B), fontSize = 12.sp)
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(eventLogs) { log ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (log.success) Color(0xFF10B981) else Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = log.timestamp,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "[${log.action.uppercase()}] ${log.button.uppercase()}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                }
                if (log.message != null) {
                  Text(
                    text = log.message,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp
                  )
                }
                if (log.error != null) {
                  Text(
                    text = log.error,
                    color = Color(0xFFEF4444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }

              Icon(
                imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (log.success) Color(0xFF10B981) else Color(0xFFEF4444),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------------------------------------------
// TAB 5: Smart Fan Automation, Scheduling & EcoFlow Power Linkage (အချက် ၆)
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartAutomationTab(
  automationState: KennedeFanAutomationState,
  ecoFlowBatteryPercent: Int,
  onStartSleepTimer: (Int) -> Unit,
  onCancelSleepTimer: () -> Unit,
  onToggleEcoFlowPowerLink: (Boolean) -> Unit,
  onSetLowBatteryThreshold: (Int) -> Unit,
  onToggleAutoTurnOffLight: (Boolean) -> Unit,
  onToggleAutoReduceSpeed: (Boolean) -> Unit,
  onTriggerEcoFlowPowerSaveTest: (Int) -> Unit = {},
  onToggleQuietNightMode: (Boolean) -> Unit,
  onStartBatchCalibration: () -> Unit,
  onCancelBatchCalibration: () -> Unit,
  onSendButton: (KennedeButton) -> Unit
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // 1. Header Banner
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, Color(0xFF0284C7)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFF0369A1).copy(alpha = 0.3f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.AutoMode, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "စမတ် အချိန်ကိုက်နှင့် အလိုအလျောက် စနစ် (အချက် ၆)",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Sleep Timer • EcoFlow Power Link • Batch Calibration",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp
          )
        }
      }
    }

    // 2. Sleep Countdown Timer Card
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, if (automationState.sleepTimerActive) Color(0xFF38BDF8) else Color(0xFF334155)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "အိပ်စက်ချိန် အချိန်မှတ်စနစ် (Sleep Timer)",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }
          if (automationState.sleepTimerActive) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text("RUNNING", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Digital Countdown Display
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A))
            .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center
        ) {
          if (automationState.sleepTimerActive) {
            val mins = automationState.sleepTimerRemainingSeconds / 60
            val secs = automationState.sleepTimerRemainingSeconds % 60
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = String.format("%02d:%02d", mins, secs),
                color = Color(0xFF38BDF8),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "ကျန်ရှိချိန် • ပြည့်ပါက ပါဝါ အလိုအလျောက်ပိတ်မည်",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
              )
            }
          } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "--:--",
                color = Color(0xFF64748B),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Text(
                text = "အချိန်မှတ်စနစ် ပိတ်ထားပါသည် (မိနစ် ရွေးချယ်ပါ)",
                color = Color(0xFF64748B),
                fontSize = 11.sp
              )
            }
          }
        }

        if (automationState.sleepTimerActive && automationState.sleepTimerMinutes > 0) {
          Spacer(modifier = Modifier.height(8.dp))
          val totalSecs = (automationState.sleepTimerMinutes * 60).toFloat()
          val progress = (automationState.sleepTimerRemainingSeconds / totalSecs).coerceIn(0f, 1f)
          LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
              .fillMaxWidth()
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF38BDF8),
            trackColor = Color(0xFF334155)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "အချိန် ရွေးချယ်ရန် (Quick Presets):", color = Color(0xFF94A3B8), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(15 to "၁၅ မိနစ်", 30 to "၃၀ မိနစ်", 45 to "၄၅ မိနစ်", 60 to "၁ နာရီ", 120 to "၂ နာရီ").forEach { (m, lbl) ->
            FilterChip(
              selected = automationState.sleepTimerActive && automationState.sleepTimerMinutes == m,
              onClick = { onStartSleepTimer(m) },
              label = { Text(lbl, fontSize = 11.sp) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF0284C7),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFF0F172A),
                labelColor = Color(0xFF94A3B8)
              ),
              modifier = Modifier.testTag("sleep_timer_${m}m_chip")
            )
          }
        }

        if (automationState.sleepTimerActive) {
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = onCancelSleepTimer,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("cancel_sleep_timer_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444))
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("အချိန်မှတ်စနစ် ပယ်ဖျက်မည်", fontSize = 12.sp)
          }
        }
      }
    }

    // 3. EcoFlow Smart Battery Linkage Card
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, if (automationState.ecoFlowPowerLinkEnabled) Color(0xFF10B981) else Color(0xFF334155)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BatterySaver, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "EcoFlow ဘက်ထရီ ချိတ်ဆက် စွမ်းအင်ချွေတာရေး",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "လက်ရှိ EcoFlow ဘက်ထရီ: $ecoFlowBatteryPercent%",
                color = if (ecoFlowBatteryPercent <= automationState.lowBatteryThreshold) Color(0xFFEF4444) else Color(0xFF10B981),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
          Switch(
            checked = automationState.ecoFlowPowerLinkEnabled,
            onCheckedChange = onToggleEcoFlowPowerLink,
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.White,
              checkedTrackColor = Color(0xFF10B981),
              uncheckedThumbColor = Color(0xFF64748B),
              uncheckedTrackColor = Color(0xFF334155)
            ),
            modifier = Modifier.testTag("ecoflow_power_link_switch")
          )
        }

        if (automationState.ecoFlowPowerLinkEnabled) {
          Spacer(modifier = Modifier.height(10.dp))
          Divider(color = Color(0xFF334155), thickness = 1.dp)
          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "စွမ်းအင်ချွေတာမည့် ဘက်ထရီ အဆင့် သတ်မှတ်ချက် (Low Battery Threshold):",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf(10, 15, 20, 25).forEach { thresh ->
              FilterChip(
                selected = automationState.lowBatteryThreshold == thresh,
                onClick = { onSetLowBatteryThreshold(thresh) },
                label = { Text("$thresh%", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = Color(0xFF059669),
                  selectedLabelColor = Color.White,
                  containerColor = Color(0xFF0F172A),
                  labelColor = Color(0xFF94A3B8)
                ),
                modifier = Modifier.testTag("threshold_${thresh}_chip")
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Sub-rules
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("ပန်ကာ မီးအလိုအလျောက်ပိတ်မည်", color = Color.White, fontSize = 11.sp)
              Text("ဘက်ထရီ နိမ့်ချိန်တွင် LED မီးကို ပိတ်၍ ဝပ်ချွေတာခြင်း", color = Color(0xFF94A3B8), fontSize = 9.sp)
            }
            Switch(
              checked = automationState.autoTurnOffLightOnLowBattery,
              onCheckedChange = onToggleAutoTurnOffLight,
              modifier = Modifier.testTag("auto_turn_off_light_switch")
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("လေအရှိန် အနိမ့်ဆုံးသို့ လျှော့ချမည်", color = Color.White, fontSize = 11.sp)
              Text("အရေးပေါ် အချိန်ကြာရှည်စွာ လည်ပတ်နိုင်ရန် အရှိန်လျှော့ခြင်း", color = Color(0xFF94A3B8), fontSize = 9.sp)
            }
            Switch(
              checked = automationState.autoReduceSpeedOnLowBattery,
              onCheckedChange = onToggleAutoReduceSpeed,
              modifier = Modifier.testTag("auto_reduce_speed_switch")
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Instant Test Simulation Button
          OutlinedButton(
            onClick = { onTriggerEcoFlowPowerSaveTest(automationState.lowBatteryThreshold) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
            border = BorderStroke(1.dp, Color(0xFF10B981)),
            modifier = Modifier.fillMaxWidth().testTag("test_ecoflow_powersave_button")
          ) {
            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("စွမ်းအင်ချွေတာရေး စနစ်ကို လက်တွေ့ စမ်းသပ်မည် (Test Simulation)", fontSize = 11.sp)
          }
        }
      }
    }

    // 4. Quiet Night Mode Card
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, if (automationState.quietNightModeActive) Color(0xFFA855F7) else Color(0xFF334155)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "ညဘက် အသံတိတ် မုဒ် (Quiet Night Mode)",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "ညင်သာသော လေအရှိန်နှင့် မီးရောင်ကင်းစင်စွာ အိပ်စက်ခြင်း",
              color = Color(0xFF94A3B8),
              fontSize = 10.sp
            )
          }
        }
        Switch(
          checked = automationState.quietNightModeActive,
          onCheckedChange = onToggleQuietNightMode,
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFFA855F7),
            uncheckedThumbColor = Color(0xFF64748B),
            uncheckedTrackColor = Color(0xFF334155)
          ),
          modifier = Modifier.testTag("quiet_night_mode_switch")
        )
      }
    }

    // 5. 10-Button Batch Calibration Wizard Card
    Card(
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, if (automationState.batchCalibrationActive) Color(0xFFF59E0B) else Color(0xFF334155)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "ခလုတ် ၁၀ ခု အဆင့်ဆင့် Calibration Wizard",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
          if (automationState.batchCalibrationActive) {
            Text(
              text = "အဆင့် ${automationState.batchCalibrationStep}/${automationState.batchCalibrationTotal}",
              color = Color(0xFFF59E0B),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "မူရင်း KN-5996HRB ရီမုဒ်မှ ခလုတ် ၁၀ ခုစလုံးကို အဆင့်ဆင့် စနစ်တကျ သင်ယူပြီး Zero-Hallucination မူဝါဒဖြင့် မှတ်သားပေးပါသည်။",
          color = Color(0xFF94A3B8),
          fontSize = 10.sp
        )

        if (automationState.batchCalibrationActive) {
          Spacer(modifier = Modifier.height(10.dp))
          val target = automationState.batchCalibrationTarget
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFF59E0B))
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "ချိန်၍ နှိပ်ရန်: ${target?.label ?: ""} (${target?.burmeseLabel ?: ""})",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "ရီမုဒ်ကို ESP32 (GPIO 15) သို့ ချိန်၍ ၁ ကြိမ် နှိပ်ပါ",
                  color = Color(0xFFF59E0B),
                  fontSize = 10.sp
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = onCancelBatchCalibration,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("cancel_batch_calibration_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444))
          ) {
            Text("Calibration ရပ်တန့်မည်", fontSize = 11.sp)
          }
        } else {
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = onStartBatchCalibration,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("start_batch_calibration_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ခလုတ်အားလုံး စတင်သင်ယူမည် (Start Batch)", fontSize = 11.sp)
          }
        }
      }
    }

    // 6. Recent Automation Event Banner
    if (automationState.lastAutomationEvent != null) {
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = automationState.lastAutomationEvent,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            maxLines = 2
          )
        }
      }
    }
  }
}
