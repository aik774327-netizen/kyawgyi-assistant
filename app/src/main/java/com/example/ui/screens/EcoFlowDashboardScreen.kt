package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.EcoFlowConnectionType
import com.example.models.EcoFlowDeviceState
import com.example.models.EcoFlowModel
import com.example.models.EcoFlowSeries
import com.example.ui.components.EcoFlowHistoryLineChart

/**
 * Full-featured EcoFlow Power Station (RIVER & DELTA Series) Smart Energy Dashboard.
 * Real-time BLE & Wi-Fi Cloud Telemetry, Automatic Emergency Automation Engine,
 * and 3-Pillar Diagnostic Analysis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoFlowDashboardScreen(
  state: EcoFlowDeviceState,
  lowBatteryThreshold: Int = 20,
  onBack: () -> Unit,
  onOpenNotificationSettings: () -> Unit = {},
  onToggleAcPort: (Boolean) -> Unit = {},
  onToggleDcPort: (Boolean) -> Unit = {},
  onToggleUsbPort: (Boolean) -> Unit = {},
  onToggleMasterPower: (Boolean) -> Unit = {},
  onSelectModel: (EcoFlowModel) -> Unit = {},
  onToggleDiagnosticsMode: (Boolean) -> Unit = {},
  onSyncCloudTelemetry: () -> Unit = {},
  onScanBle: () -> Unit = {},
  onToggleLowBatteryCutoff: (Boolean) -> Unit = {},
  onSetLowBatteryThreshold: (Int) -> Unit = {},
  onToggleOverheatProtection: (Boolean) -> Unit = {},
  onToggleOverloadProtection: (Boolean) -> Unit = {},
  onToggleGridOutageUps: (Boolean) -> Unit = {},
  onTestEmergencyAutomation: () -> Unit = {},
  config: com.example.database.EcoFlowConfigEntity = com.example.database.EcoFlowConfigEntity(),
  onSaveConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
  var showDiagnosticDialog by remember { mutableStateOf(false) }
  val selectedSeries = state.selectedModel.series

  val remainingHours = state.remainingMinutes / 60
  val remainingMins = state.remainingMinutes % 60
  val remainingText = if (state.isCharging) {
    "အားသွင်းနေသည်: ပြည့်ရန် ~1h 15m"
  } else {
    "ကျန်ရှိချိန်: ~$remainingHours နာရီ $remainingMins မိနစ်"
  }

  val batteryColor = when {
    state.batteryPercentage <= lowBatteryThreshold -> Color(0xFFEF4444)
    state.batteryPercentage <= 50 -> Color(0xFFF59E0B)
    else -> Color(0xFF10B981)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = "EcoFlow Smart Energy Dashboard",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "${state.model} · စမတ်စွမ်းအင်နှင့် အရေးပေါ် အလိုအလျောက်ထိန်းချုပ်စနစ်",
              fontSize = 11.sp,
              color = Color(0xFF94A3B8)
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
        },
        actions = {
          IconButton(onClick = { onSyncCloudTelemetry() }) {
            Icon(Icons.Default.CloudSync, contentDescription = "Cloud Sync", tint = Color(0xFF38BDF8))
          }
          IconButton(onClick = onOpenNotificationSettings) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color(0xFFF59E0B))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
      )
    },
    containerColor = Color(0xFF090D16)
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

      // 1. Hardware Connection & Diagnostic Banner
      ConnectionStatusBanner(
        connectionType = state.connectionType,
        connectionStatus = state.connectionStatus,
        isDiagnosticsMode = state.isDiagnosticsMode,
        bleRssi = state.bleRssi,
        onToggleDiagnostics = { onToggleDiagnosticsMode(!state.isDiagnosticsMode) },
        onOpenDiagnostics = { showDiagnosticDialog = true },
        onScanBle = onScanBle,
        onSyncCloud = onSyncCloudTelemetry
      )

      // 2. Series & Model Selector (RIVER vs DELTA Series)
      ModelSelectorCard(
        currentModel = state.selectedModel,
        onSelectModel = onSelectModel
      )

      // 3. Low Battery Alert Warning Banner if below threshold
      AnimatedVisibility(
        visible = state.batteryPercentage <= lowBatteryThreshold,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x33EF4444))
            .border(1.2.dp, Color(0xFFEF4444), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .clickable { onOpenNotificationSettings() }
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "သတိပေးချက်: ဘက်ထရီ ${state.batteryPercentage}% သို့ လျော့ကျနေပါသည်",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFCA5A5)
              )
              Text(
                text = "သတ်မှတ်ခွဲတမ်း (${lowBatteryThreshold}%) အောက်သို့ ရောက်ရှိနေပါသည်။ အလိုအလျောက် ကာကွယ်ရေး စနစ် အသက်ဝင်နေပါသည်။",
                fontSize = 10.sp,
                color = Color(0xFFE2E8F0)
              )
            }
          }
        }
      }

      // 4. Real-Time Battery & Runtime Hero Card
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ecoflow_hero_battery_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111E33)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "လက်ကျန် ဘက်ထရီ စွမ်းအင်",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = "${state.batteryPercentage}",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
              )
              Text(
                text = "%",
                fontSize = 20.sp,
                color = batteryColor,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                fontWeight = FontWeight.Bold
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = remainingText,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (state.isCharging) Color(0xFF38BDF8) else Color(0xFF34D399)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Temperature & Tech Specs
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(3.dp))
              Text(text = "${state.temperatureCelsius}°C", fontSize = 11.sp, color = Color(0xFF94A3B8))
              Spacer(modifier = Modifier.width(10.dp))
              Text(text = state.selectedModel.batteryChemistry, fontSize = 11.sp, color = Color(0xFF64748B))
            }
          }

          // Large Circular Progress Ring
          Box(
            modifier = Modifier.size(88.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(
              progress = { state.batteryPercentage / 100f },
              modifier = Modifier.size(88.dp),
              color = batteryColor,
              trackColor = Color(0xFF1E293B),
              strokeWidth = 8.dp
            )
            Icon(
              imageVector = if (state.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.Bolt,
              contentDescription = null,
              tint = batteryColor,
              modifier = Modifier.size(34.dp)
            )
          }
        }
      }

      // 5. Real-Time Power Flow Metrics (Input Watts vs Output Watts)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Input Power Card
        PowerMetricCard(
          title = "INPUT POWER",
          burmeseTitle = "ဆိုလာ / လိုင်းအားသွင်း",
          watts = state.inputWatts,
          icon = Icons.Default.ArrowDownward,
          accentColor = Color(0xFF38BDF8),
          subText = if (state.inputWatts > 0) "Charging active" else "Standby (0W)",
          modifier = Modifier.weight(1f)
        )

        // Output Power Card
        PowerMetricCard(
          title = "OUTPUT LOAD",
          burmeseTitle = "သုံးစွဲနေသော ဝပ်အား",
          watts = state.outputWatts,
          icon = Icons.Default.ArrowUpward,
          accentColor = Color(0xFFF59E0B),
          subText = "Max ${state.selectedModel.maxAcWatts}W",
          modifier = Modifier.weight(1f)
        )
      }

      // 6. Automatic Emergency Automation Card (အရေးပေါ် အလိုအလျောက် ကာကွယ်ရေး စနစ်)
      EmergencyAutomationCard(
        automation = state.automation,
        onToggleLowBattery = onToggleLowBatteryCutoff,
        onSetThreshold = onSetLowBatteryThreshold,
        onToggleOverheat = onToggleOverheatProtection,
        onToggleOverload = onToggleOverloadProtection,
        onToggleGridOutage = onToggleGridOutageUps,
        onTestEmergency = onTestEmergencyAutomation
      )

      // 7. Historical Power Consumption Line Chart (24 Hours)
      EcoFlowHistoryLineChart(
        modifier = Modifier.fillMaxWidth()
      )

      // 8. Output Ports Quick Switches
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "OUTPUT PORTS (အထွက်ပေါက်များ ထိန်းချုပ်မှု)",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF94A3B8)
            )

            // Master power quick switch
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .clickable { onToggleMasterPower(!state.acPortEnabled) }
                .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "အကုန်ဖွင့်/ပိတ်", fontSize = 10.sp, color = Color.White)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Port Switch Rows
          PortSwitchRow(
            name = "AC 220V Outlets (မီးပလပ်ပေါက်များ)",
            watts = if (state.acPortEnabled) "${if (selectedSeries == EcoFlowSeries.RIVER) 120 else 220}W Load" else "OFF",
            isEnabled = state.acPortEnabled,
            color = Color(0xFFF59E0B),
            onToggle = { onToggleAcPort(!state.acPortEnabled) }
          )

          Spacer(modifier = Modifier.height(10.dp))

          PortSwitchRow(
            name = "DC 12V Car Port (ကားသုံးပေါက်)",
            watts = if (state.dcPortEnabled) "${if (selectedSeries == EcoFlowSeries.RIVER) 35 else 60}W Load" else "OFF",
            isEnabled = state.dcPortEnabled,
            color = Color(0xFF38BDF8),
            onToggle = { onToggleDcPort(!state.dcPortEnabled) }
          )

          Spacer(modifier = Modifier.height(10.dp))

          PortSwitchRow(
            name = "USB-A & Type-C Ports (ဖုန်း/လက်ပ်တော့ပ်)",
            watts = if (state.usbPortEnabled) "25W Fast Charge" else "OFF",
            isEnabled = state.usbPortEnabled,
            color = Color(0xFF10B981),
            onToggle = { onToggleUsbPort(!state.usbPortEnabled) }
          )
        }
      }
    }
  }

  // 3-Pillar Diagnostics Dialog
  if (showDiagnosticDialog) {
    androidx.compose.ui.window.Dialog(onDismissRequest = { showDiagnosticDialog = false }) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF38BDF8))
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "EcoFlow အခြေအနေ ခွဲခြမ်းစိတ်ဖြာချက် (၃ ရပ်)",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          DiagnosticPillarItem(
            title = "၁။ နည်းပညာပိုင်း (Technical)",
            detail = "Bluetooth BLE GATT သို့မဟုတ် Wi-Fi Cloud REST API Socket ချိတ်ဆက်မှု အခြေအနေ။ စက်အနီးတွင်ရှိပြီး Bluetooth ဖွင့်ထားခြင်း သို့မဟုတ် Open API မှတစ်ဆင့် ချိတ်ဆက်နိုင်ပါသည်။",
            status = when (state.connectionType) {
              EcoFlowConnectionType.BLE -> "BLE ချိတ်ဆက်ထားသည် (${state.bleRssi} dBm)"
              EcoFlowConnectionType.WIFI_CLOUD -> "EcoFlow Cloud Open API ဖြင့် ချိတ်ဆက်ပြီး (REST Synced)"
              EcoFlowConnectionType.DIAGNOSTIC_MODE -> "စမ်းသပ်မှု စနစ် (Safe Simulation)"
              EcoFlowConnectionType.NONE -> "Offline / Standby"
            }
          )

          Spacer(modifier = Modifier.height(10.dp))

          DiagnosticPillarItem(
            title = "၂။ အရင်းအမြစ် (Resource)",
            detail = "Bluetooth Permissions နှင့် EcoFlow Open API Key များ (${if (config.hostUrl.isNotBlank()) config.hostUrl else "api-a.ecoflow.com"}) ပြည့်စုံမှု။",
            status = if (config.accessKey.isNotBlank() && config.secretKey.isNotBlank()) {
              "Cloud API Keys ပြည့်စုံပါသည် (AccessKey: ${config.accessKey.take(6)}...)"
            } else {
              "Open API Keys မသတ်မှတ်ရသေးပါ (Settings တွင် ထည့်သွင်းပါ)"
            }
          )

          Spacer(modifier = Modifier.height(10.dp))

          DiagnosticPillarItem(
            title = "၃။ လက်တွေ့မကိုက်ညီမှု (Real-World Mismatch)",
            detail = "ပြင်ပလောကရှိ EcoFlow စက် (RIVER/DELTA) အမှန်တကယ် ပါဝါဖွင့်ထားပြီး SN: ${if (config.deviceSn.isNotBlank()) config.deviceSn else "သတ်မှတ်ဆဲ"} ဖြင့် Cloud သို့မဟုတ် BLE တွင် တည်ရှိခြင်း။ အတုအယောင် ဟန်ပြဒေတာ မသုံးပါ။",
            status = if (state.isConnected) "ပြင်ပစက်နှင့် ချိတ်ဆက်မိပါသည် (${state.connectionStatus})" else "ပြင်ပစက် မတွေ့ရှိသေးပါ (Offline)"
          )

          Spacer(modifier = Modifier.height(20.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF38BDF8))
                .clickable { showDiagnosticDialog = false }
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Text(text = "နားလည်ပါပြီ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConnectionStatusBanner(
  connectionType: EcoFlowConnectionType,
  connectionStatus: String,
  isDiagnosticsMode: Boolean,
  bleRssi: Int,
  onToggleDiagnostics: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  onScanBle: () -> Unit,
  onSyncCloud: () -> Unit
) {
  val (bgColor, borderColor, icon, statusTitle) = when (connectionType) {
    EcoFlowConnectionType.BLE -> Quadruple(
      Color(0x2210B981),
      Color(0xFF10B981),
      Icons.Default.BluetoothConnected,
      "BLE ချိတ်ဆက်ထားသည် ($bleRssi dBm)"
    )
    EcoFlowConnectionType.WIFI_CLOUD -> Quadruple(
      Color(0x2238BDF8),
      Color(0xFF38BDF8),
      Icons.Default.Cloud,
      "EcoFlow Cloud Open API ဖြင့် ချိတ်ဆက်ထားသည်"
    )
    EcoFlowConnectionType.DIAGNOSTIC_MODE -> Quadruple(
      Color(0x22F59E0B),
      Color(0xFFF59E0B),
      Icons.Default.Shield,
      "စမ်းသပ်မှု စနစ် (Safe Diagnostic Mode) ဖွင့်ထားပါသည်"
    )
    EcoFlowConnectionType.NONE -> Quadruple(
      Color(0x22EF4444),
      Color(0xFFEF4444),
      Icons.Default.Bluetooth,
      "စက်နှင့် ချိတ်ဆက်ထားခြင်း မရှိပါ (Offline)"
    )
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = statusTitle,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = connectionStatus,
              fontSize = 10.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onOpenDiagnostics() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(text = "စစ်ဆေးချက် ၃ ရပ်", fontSize = 10.sp, color = Color(0xFF38BDF8))
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action row: Scan BLE, Sync Cloud, Diagnostic Mode Toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onScanBle() }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "BLE ရှာမည်", fontSize = 11.sp, color = Color.White)
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable { onSyncCloud() }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Cloud ရယူမည်", fontSize = 11.sp, color = Color.White)
          }
        }

        Box(
          modifier = Modifier
            .weight(1.2f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDiagnosticsMode) Color(0x33F59E0B) else Color(0xFF1E293B))
            .border(1.dp, if (isDiagnosticsMode) Color(0xFFF59E0B) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onToggleDiagnostics() }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (isDiagnosticsMode) "စမ်းသပ်မှု ပိတ်မည်" else "စမ်းသပ်မှု ဖွင့်မည်",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDiagnosticsMode) Color(0xFFF59E0B) else Color(0xFF94A3B8)
          )
        }
      }
    }
  }
}

@Composable
private fun ModelSelectorCard(
  currentModel: EcoFlowModel,
  onSelectModel: (EcoFlowModel) -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "POWER STATION MODEL (စီးရီးနှင့် မော်ဒယ်)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8)
          )
          Text(
            text = "${currentModel.modelName} · ${currentModel.capacityWh}Wh · Max ${currentModel.maxAcWatts}W",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8)
          )
        }

        Text(
          text = currentModel.series.burmeseName,
          fontSize = 10.sp,
          color = Color(0xFFF59E0B)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Model horizontal scrollable selector
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        EcoFlowModel.values().forEach { model ->
          val isSelected = model == currentModel
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) Color(0xFF1E3A5F) else Color(0xFF131F33))
              .border(
                1.2.dp,
                if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                RoundedCornerShape(12.dp)
              )
              .clickable { onSelectModel(model) }
              .padding(horizontal = 12.dp, vertical = 8.dp)
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = model.modelName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
              )
              Text(
                text = "${model.capacityWh}Wh",
                fontSize = 10.sp,
                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "• ${currentModel.description} (အလေးချိန်: ${currentModel.weightKg}kg)",
        fontSize = 11.sp,
        color = Color(0xFF94A3B8)
      )
    }
  }
}

@Composable
private fun EmergencyAutomationCard(
  automation: com.example.models.EcoFlowEmergencyAutomation,
  onToggleLowBattery: (Boolean) -> Unit,
  onSetThreshold: (Int) -> Unit,
  onToggleOverheat: (Boolean) -> Unit,
  onToggleOverload: (Boolean) -> Unit,
  onToggleGridOutage: (Boolean) -> Unit,
  onTestEmergency: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "AUTOMATIC EMERGENCY AUTOMATION",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEF4444)
            )
            Text(
              text = "အရေးပေါ် အလိုအလျောက် ဘေးကင်းရေးနှင့် ဖြတ်တောက်မှုစနစ်",
              fontSize = 11.sp,
              color = Color.White
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.2f))
            .clickable { onTestEmergency() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(text = "စမ်းသပ်ပြသမည်", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCA5A5))
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 1. Low battery auto AC cutoff rule
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "ဘက်ထရီအားနည်း အလိုအလျောက် AC ဖြတ်တောက်မှု", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
          Text(text = "ဘက်ထရီ ${automation.lowBatteryCutoffThreshold}% အောက်ရောက်လျှင် AC မီးပလပ်များ အလိုအလျောက် ပိတ်မည်", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        Switch(
          checked = automation.autoLowBatteryCutoffEnabled,
          onCheckedChange = onToggleLowBattery,
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFFEF4444),
            uncheckedTrackColor = Color(0xFF1E293B)
          )
        )
      }

      // Threshold pills (10%, 15%, 20%, 25%)
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = "သတ်မှတ်ခွဲတမ်း:", fontSize = 10.sp, color = Color(0xFF94A3B8))
        listOf(10, 15, 20, 25).forEach { th ->
          val isSel = automation.lowBatteryCutoffThreshold == th
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSel) Color(0xFFEF4444) else Color(0xFF1E293B))
              .clickable { onSetThreshold(th) }
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(text = "$th%", fontSize = 10.sp, color = if (isSel) Color.White else Color(0xFF94A3B8))
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 2. Overheat safety protection rule
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "အပူလွန်ကဲမှု အလိုအလျောက် ရပ်တန့်ခြင်း", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
          Text(text = "အပူချိန် ${automation.overheatTempThreshold}°C အထက်ရောက်လျှင် အန္တရာယ်ကင်းစေရန် အကုန်ပိတ်မည်", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        Switch(
          checked = automation.autoOverheatHaltEnabled,
          onCheckedChange = onToggleOverheat,
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFFF59E0B),
            uncheckedTrackColor = Color(0xFF1E293B)
          )
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 3. Grid Outage EPS/UPS switch
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = "မီးပျက်မှု အလိုအလျောက် အရန်စွမ်းအင်သို့ ကူးပြောင်းမှု", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
          Text(text = "ဓာတ်အားလိုင်း မီးပြတ်တောက်သည်နှင့် အသံဖြင့် သတိပေး၍ အရန်စွမ်းအင် ဖွင့်လှစ်ပေးခြင်း", fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
        Switch(
          checked = automation.autoGridOutageUpsEnabled,
          onCheckedChange = onToggleGridOutage,
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF10B981),
            uncheckedTrackColor = Color(0xFF1E293B)
          )
        )
      }
    }
  }
}

@Composable
private fun DiagnosticPillarItem(
  title: String,
  detail: String,
  status: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF131F33))
      .padding(12.dp)
  ) {
    Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
    Spacer(modifier = Modifier.height(3.dp))
    Text(text = detail, fontSize = 11.sp, color = Color(0xFFE2E8F0))
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "• လက်ရှိအခြေအနေ: $status", fontSize = 10.sp, color = Color(0xFFF59E0B))
  }
}

@Composable
private fun PowerMetricCard(
  title: String,
  burmeseTitle: String,
  watts: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accentColor: Color,
  subText: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(Color(0xFF111E33))
      .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
      .padding(14.dp)
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
      }
      Spacer(modifier = Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(text = "$watts", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = " W", fontSize = 14.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(bottom = 3.dp))
      }
      Text(text = burmeseTitle, fontSize = 10.sp, color = Color(0xFF94A3B8))
      Spacer(modifier = Modifier.height(2.dp))
      Text(text = subText, fontSize = 9.sp, color = Color(0xFF64748B))
    }
  }
}

@Composable
private fun PortSwitchRow(
  name: String,
  watts: String,
  isEnabled: Boolean,
  color: Color,
  onToggle: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF131F33))
      .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column {
      Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      Text(text = watts, fontSize = 10.sp, color = if (isEnabled) color else Color(0xFF64748B))
    }

    Switch(
      checked = isEnabled,
      onCheckedChange = { onToggle() },
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = color,
        uncheckedThumbColor = Color(0xFF64748B),
        uncheckedTrackColor = Color(0xFF1E293B)
      )
    )
  }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
