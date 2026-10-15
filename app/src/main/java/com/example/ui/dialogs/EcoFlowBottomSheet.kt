package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.BluetoothDeviceItem
import com.example.models.EcoFlowConnectionType
import com.example.models.EcoFlowDeviceState
import com.example.models.EcoFlowModel
import com.example.models.EcoFlowSeries
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberLight
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoFlowBottomSheet(
  state: EcoFlowDeviceState,
  bleDevices: List<BluetoothDeviceItem>,
  isBleScanning: Boolean,
  onDismiss: () -> Unit,
  onSetAcPort: (Boolean) -> Unit,
  onSetDcPort: (Boolean) -> Unit,
  onSetUsbPort: (Boolean) -> Unit,
  onSetMasterPower: (Boolean) -> Unit,
  onSetChargingSpeed: (Int) -> Unit,
  onSetXBoost: (Boolean) -> Unit,
  onSetBeep: (Boolean) -> Unit,
  onSetTimeout: (Int) -> Unit,
  onTriggerSafetyHalt: () -> Unit,
  onScanBluetooth: () -> Unit,
  onVoiceIntentTest: (String) -> Unit,
  onConnectBle: (String) -> Unit = {},
  onDisconnectBle: () -> Unit = {},
  onOpenFullScanner: () -> Unit = {},
  onSelectModel: (EcoFlowModel) -> Unit = {},
  onToggleLowBatteryCutoff: (Boolean) -> Unit = {},
  onSetLowBatteryThreshold: (Int) -> Unit = {},
  onToggleOverheatProtection: (Boolean) -> Unit = {},
  onToggleOverloadProtection: (Boolean) -> Unit = {},
  onToggleGridOutageUps: (Boolean) -> Unit = {},
  onTestEmergencyAutomation: () -> Unit = {},
  onSyncCloudTelemetry: () -> Unit = {},
  onToggleDiagnosticsMode: (Boolean) -> Unit = {},
  currentConfig: com.example.database.EcoFlowConfigEntity = com.example.database.EcoFlowConfigEntity(),
  onSaveEcoFlowConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf(
    "Controls\n(ထိန်းချုပ်စနစ်)",
    "Analytics\n& Energy",
    "Protection\n(ကာကွယ်ရေး)",
    "Diagnostic\nLog (မှတ်တမ်း)",
    "Scheduler\n(အချိန်ဇယား)"
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = BackgroundDark,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.94f)
        .verticalScroll(rememberScrollState())
        .padding(bottom = 32.dp)
    ) {
      // 1. Header (Model, tags, actions)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(46.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0x33F59E0B))
              .border(1.2.dp, AmberAccent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Bolt,
              contentDescription = "EcoFlow",
              tint = AmberAccent,
              modifier = Modifier.size(26.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Text(
              text = "EcoFlow Power Station",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = "${state.model} · အသံနှင့် ထိတွေ့ခလုတ်ဖြင့် ထိန်းချုပ်စနစ်",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        }

        // Action icons (Close)
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SurfaceCard)
        ) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
        }
      }

      // Status tags row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BadgeChip(text = "REST Cloud Ready", color = Color(0xFF0284C7))
        BadgeChip(text = "BLE Scanner", color = IndigoAccent)
        BadgeChip(text = "API Connected", color = EmeraldPrimary)
        BadgeChip(text = "</>", color = Color(0xFF64748B))
      }

      Spacer(modifier = Modifier.height(8.dp))

      // RIVER vs DELTA Model Picker Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        EcoFlowModel.values().forEach { model ->
          val isSel = model == state.selectedModel
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSel) Color(0xFF1E3A5F) else SurfaceCard)
              .border(1.dp, if (isSel) AmberAccent else SurfaceCardBorder, RoundedCornerShape(10.dp))
              .clickable { onSelectModel(model) }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "${model.modelName} (${model.capacityWh}Wh)",
              fontSize = 11.sp,
              fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
              color = if (isSel) Color.White else TextSecondary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Navigation Tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        tabs.forEachIndexed { index, tabTitle ->
          val isSelected = selectedTab == index
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
              .border(
                1.dp,
                if (isSelected) AmberAccent else SurfaceCardBorder,
                RoundedCornerShape(12.dp)
              )
              .clickable { selectedTab = index }
              .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = tabTitle,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) AmberLight else TextMuted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3. Tab Content - Controls View
      if (selectedTab == 0) {
        ControlsTabContent(
          state = state,
          bleDevices = bleDevices,
          isBleScanning = isBleScanning,
          onSetAcPort = onSetAcPort,
          onSetDcPort = onSetDcPort,
          onSetUsbPort = onSetUsbPort,
          onSetMasterPower = onSetMasterPower,
          onSetChargingSpeed = onSetChargingSpeed,
          onSetXBoost = onSetXBoost,
          onSetBeep = onSetBeep,
          onSetTimeout = onSetTimeout,
          onTriggerSafetyHalt = onTriggerSafetyHalt,
          onScanBluetooth = onScanBluetooth,
          onVoiceIntentTest = onVoiceIntentTest,
          onConnectBle = onConnectBle,
          onDisconnectBle = onDisconnectBle,
          onOpenFullScanner = onOpenFullScanner
        )
      } else {
        // Secondary Tabs (Analytics, Protection, Diagnostic, Scheduler)
        SecondaryTabContent(
          tabIndex = selectedTab,
          state = state,
          onToggleLowBattery = onToggleLowBatteryCutoff,
          onSetThreshold = onSetLowBatteryThreshold,
          onToggleOverheat = onToggleOverheatProtection,
          onToggleOverload = onToggleOverloadProtection,
          onToggleGridOutage = onToggleGridOutageUps,
          onTestEmergency = onTestEmergencyAutomation,
          onSyncCloud = onSyncCloudTelemetry,
          onToggleDiagnostics = onToggleDiagnosticsMode,
          currentConfig = currentConfig,
          onSaveEcoFlowConfig = onSaveEcoFlowConfig
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // 4. Footer info bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Thermostat, contentDescription = "Temperature", tint = TextMuted, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Temp: ${state.temperatureCelsius}°C", fontSize = 12.sp, color = TextMuted)
        }
        Text(
          text = "စကားပြောခလုတ်ဖြင့်လည်း EcoFlow အား တိုက်ရိုက် ခိုင်းစေနိုင်ပါသည်",
          fontSize = 10.sp,
          color = TextMuted
        )
      }
    }
  }
}

@Composable
fun ControlsTabContent(
  state: EcoFlowDeviceState,
  bleDevices: List<BluetoothDeviceItem>,
  isBleScanning: Boolean,
  onSetAcPort: (Boolean) -> Unit,
  onSetDcPort: (Boolean) -> Unit,
  onSetUsbPort: (Boolean) -> Unit,
  onSetMasterPower: (Boolean) -> Unit,
  onSetChargingSpeed: (Int) -> Unit,
  onSetXBoost: (Boolean) -> Unit,
  onSetBeep: (Boolean) -> Unit,
  onSetTimeout: (Int) -> Unit,
  onTriggerSafetyHalt: () -> Unit,
  onScanBluetooth: () -> Unit,
  onVoiceIntentTest: (String) -> Unit,
  onConnectBle: (String) -> Unit = {},
  onDisconnectBle: () -> Unit = {},
  onOpenFullScanner: () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
  ) {
    // Bluetooth Scanner Collapsible Section
    BluetoothScannerCard(
      bleDevices = bleDevices,
      isScanning = isBleScanning,
      onScan = onScanBluetooth,
      onConnect = onConnectBle,
      onDisconnect = onDisconnectBle,
      onOpenFullScanner = onOpenFullScanner
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Telemetry Gauges Grid (Battery % with circular progress, Input Watts, Output Watts)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Battery Card (with circular indicator)
      Box(
        modifier = Modifier
          .weight(1.3f)
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(text = "ဘက်ထရီ ပမာဏ", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = "${state.batteryPercentage}",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
              )
              Text(text = " %", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "ကျန်ရှိချိန်: ~${state.remainingMinutes / 60} နာရီ ${state.remainingMinutes % 60} မိနစ်",
              fontSize = 10.sp,
              color = EmeraldLight
            )
          }

          // Circular progress ring
          Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
              progress = { state.batteryPercentage / 100f },
              modifier = Modifier.size(46.dp),
              color = EmeraldPrimary,
              trackColor = Color(0xFF1E293B),
              strokeWidth = 4.dp
            )
            Text(
              text = "${state.batteryPercentage}%",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
          }
        }
      }

      // Input Power Card
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = "Input Power", fontSize = 10.sp, color = Color(0xFF38BDF8))
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${state.inputWatts}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = " W", fontSize = 11.sp, color = TextMuted)
          }
          Text(text = "AC/Solar In", fontSize = 9.sp, color = TextMuted)
        }
      }

      // Output Total Card
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
          .padding(14.dp)
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = "Output Total", fontSize = 10.sp, color = AmberAccent)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${state.outputWatts}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = " W", fontSize = 11.sp, color = TextMuted)
          }
          Text(text = "Total Load", fontSize = 9.sp, color = TextMuted)
        }
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 4. OUTPUT PORTS SWITCH (အထွက်ပေါက်များ အဖွင့်/အပိတ်)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "OUTPUT PORTS SWITCH (အထွက်ပေါက်များ အဖွင့်/အပိတ်)",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted
      )

      // Master All Ports On / Off
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PortSwitchPill(label = "အားလုံးဖွင့်", color = EmeraldPrimary, onClick = { onSetMasterPower(true) })
        PortSwitchPill(label = "အားလုံးပိတ်", color = RoseError, onClick = { onSetMasterPower(false) })
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Port Rows (AC 220V Outlets, 12V DC, USB-A & Type-C)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // AC Port
      PortCard(
        title = "AC 220V Outlets",
        sub = "အိမ်သုံး လျှပ်စစ်ပလပ်ပေါက်",
        loadText = if (state.acPortEnabled) "~220W Load" else "OFF",
        isEnabled = state.acPortEnabled,
        activeColor = AmberAccent,
        onToggle = { onSetAcPort(!state.acPortEnabled) },
        modifier = Modifier.weight(1f)
      )

      // DC Port
      PortCard(
        title = "12V DC / Car Port",
        sub = "ကားသီးသန့် / 12V ပလပ်ပေါက်",
        loadText = if (state.dcPortEnabled) "~60W Load" else "OFF",
        isEnabled = state.dcPortEnabled,
        activeColor = Color(0xFF38BDF8),
        onToggle = { onSetDcPort(!state.dcPortEnabled) },
        modifier = Modifier.weight(1f)
      )

      // USB Port
      PortCard(
        title = "USB-A & Type-C",
        sub = "ဖုန်း/Laptop အားသွင်းပေါက်များ",
        loadText = if (state.usbPortEnabled) "~35W Load" else "OFF",
        isEnabled = state.usbPortEnabled,
        activeColor = EmeraldPrimary,
        onToggle = { onSetUsbPort(!state.usbPortEnabled) },
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 5. AC အားသွင်းနှုန်း ချိန်ညှိမှု (Charging Speed: 200W - 1800W)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(SurfaceCard)
        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
        .padding(16.dp)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(text = "AC အားသွင်းနှုန်း ချိန်ညှိမှု (Charging Speed)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Text(text = "${state.chargingSpeedWatts} W", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
          value = state.chargingSpeedWatts.toFloat(),
          onValueChange = { onSetChargingSpeed(it.toInt()) },
          valueRange = 200f..1800f,
          steps = 15,
          colors = SliderDefaults.colors(
            thumbColor = AmberAccent,
            activeTrackColor = AmberAccent,
            inactiveTrackColor = Color(0xFF1E293B)
          )
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(text = "200W", fontSize = 10.sp, color = TextMuted)
          Text(text = "1800W", fontSize = 10.sp, color = TextMuted)
        }

        if (state.chargingSpeedWatts > 1200) {
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0x33F59E0B))
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "သတိပေးချက်: အားသွင်းနှုန်း ၁၂၀၀W ကျော်ပါက မီးကြိုးလိုင်း ခံနိုင်ရည်ကို သတိပြုပါ။",
              fontSize = 10.sp,
              color = AmberLight
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 6. DEVICE SETTINGS & FEATURES (စနစ်အထူးလုပ်ဆောင်ချက်များ)
    Text(text = "DEVICE SETTINGS & FEATURES (စနစ်အထူးလုပ်ဆောင်ချက်များ)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // X-Boost
      FeatureSettingCard(
        title = "X-Boost System",
        sub = "ဝပ်အားမြင့်ပစ္စည်းများ မောင်းနှင်ခြင်း",
        statusText = if (state.xboostEnabled) "ENABLED" else "DISABLED",
        isEnabled = state.xboostEnabled,
        buttonText = if (state.xboostEnabled) "ပိတ်ရန်" else "ဖွင့်ရန်",
        activeColor = AmberAccent,
        onToggle = { onSetXBoost(!state.xboostEnabled) },
        modifier = Modifier.weight(1f)
      )

      // Beep Sound
      FeatureSettingCard(
        title = "Beep Sound",
        sub = "ခလုတ်နှိပ်/အချက်ပေးသံ",
        statusText = if (state.beepSoundEnabled) "ON (Sound)" else "OFF (Muted)",
        isEnabled = state.beepSoundEnabled,
        buttonText = if (state.beepSoundEnabled) "အသံပိတ်" else "အသံဖွင့်",
        activeColor = Color(0xFF38BDF8),
        onToggle = { onSetBeep(!state.beepSoundEnabled) },
        modifier = Modifier.weight(1f)
      )

      // AC Auto-Timeout
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(16.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
          .padding(12.dp)
      ) {
        Column {
          Text(text = "AC Auto-Timeout", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Text(text = "AC ပလပ် အလိုအလျောက် ပိတ်ချိန်", fontSize = 9.sp, color = TextMuted)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            listOf(0 to "Off", 2 to "2h", 4 to "4h", 12 to "12h").forEach { (h, label) ->
              val isSel = state.acTimeoutHours == h
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (isSel) IndigoAccent else Color(0xFF1E293B))
                  .clickable { onSetTimeout(h) }
                  .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 7. VOICE INTENT TESTING (အသံဖြင့် အမိန့်ပေးချက်များ စမ်းသပ်ရန်)
    Text(text = "VOICE INTENT TESTING (အသံဖြင့် အမိန့်ပေးချက်များ စမ်းသပ်ရန်)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
    Spacer(modifier = Modifier.height(10.dp))

    val voiceTests = listOf(
      "\"EcoFlow ဖွင့်ပေးပါ\"" to "set_power_state(true)",
      "\"EcoFlow ပိတ်ပေးပါ\"" to "set_power_state(false)",
      "\"AC ပလပ်ကို ဖွင့်ပေးပါ\"" to "set_ac_port_state(true)",
      "\"ဘက်ထရီ ဘယ်လောက်ကျန်လဲ\"" to "get_device_status('battery')",
      "\"အားသွင်းနှုန်း ၈၀၀W ထားပါ\"" to "set_charging_speed(800)",
      "\"USB ပေါက် ဖွင့်လိုက်ပါ\"" to "set_usb_port_state(true)",
      "\"EcoFlow အခြေအနေ ပြပါ\"" to "get_device_status('all')",
      "\"အရေးပေါ် ရပ်တန့်ပါ\"" to "trigger_safety_halt()"
    )

    val rows = voiceTests.chunked(2)
    rows.forEach { pair ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        pair.forEach { (phrase, action) ->
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(if (action.contains("halt")) Color(0x33EF4444) else SurfaceCard)
              .border(
                1.dp,
                if (action.contains("halt")) RoseError else SurfaceCardBorder,
                RoundedCornerShape(12.dp)
              )
              .clickable { onVoiceIntentTest(phrase) }
              .padding(10.dp)
          ) {
            Column {
              Text(
                text = phrase,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (action.contains("halt")) RoseError else TextPrimary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(text = action, fontSize = 9.sp, color = TextMuted)
            }
          }
        }
      }
    }
  }
}

@Composable
fun SecondaryTabContent(
  tabIndex: Int,
  state: EcoFlowDeviceState,
  onToggleLowBattery: (Boolean) -> Unit = {},
  onSetThreshold: (Int) -> Unit = {},
  onToggleOverheat: (Boolean) -> Unit = {},
  onToggleOverload: (Boolean) -> Unit = {},
  onToggleGridOutage: (Boolean) -> Unit = {},
  onTestEmergency: () -> Unit = {},
  onSyncCloud: () -> Unit = {},
  onToggleDiagnostics: (Boolean) -> Unit = {},
  currentConfig: com.example.database.EcoFlowConfigEntity = com.example.database.EcoFlowConfigEntity(),
  onSaveEcoFlowConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> }
) {
  var showApiConfigEditor by remember { mutableStateOf(false) }
  var editAccessKey by remember(currentConfig.accessKey) { mutableStateOf(currentConfig.accessKey) }
  var editSecretKey by remember(currentConfig.secretKey) { mutableStateOf(currentConfig.secretKey) }
  var editDeviceSn by remember(currentConfig.deviceSn) { mutableStateOf(currentConfig.deviceSn) }
  var editHostUrl by remember(currentConfig.hostUrl) { mutableStateOf(if (currentConfig.hostUrl.isNotBlank()) currentConfig.hostUrl else "https://api-a.ecoflow.com") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 12.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(SurfaceCard)
        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
        .padding(16.dp)
    ) {
      when (tabIndex) {
        1 -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = "Analytics & Energy 24-Hour Tracking (${state.selectedModel.modelName})",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = "• Capacity: ${state.selectedModel.capacityWh}Wh (${state.selectedModel.batteryChemistry})\n• Solar / Grid Input: ${state.inputWatts}W\n• AC Loads: ${if (state.acPortEnabled) "Active" else "0W"}\n• DC 12V Loads: ${if (state.dcPortEnabled) "Active" else "0W"}\n• USB Fast Charge: ${if (state.usbPortEnabled) "Active" else "0W"}\n• Net Power Flow: ${state.inputWatts - state.outputWatts}W",
              fontSize = 11.sp,
              color = TextSecondary,
              lineHeight = 18.sp
            )
            com.example.ui.components.EcoFlowHistoryLineChart(modifier = Modifier.fillMaxWidth())
          }
        }
        2 -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Automatic Emergency Safeguards (အရေးပေါ် ကာကွယ်ရေး)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldLight
              )
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

            // Low battery cutoff rule
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "ဘက်ထရီအားနည်း အလိုအလျောက် AC ဖြတ်တောက်မှု", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(text = "လက်ကျန် ${state.automation.lowBatteryCutoffThreshold}% အောက်ရောက်လျှင် AC ပိတ်မည်", fontSize = 10.sp, color = TextMuted)
              }
              androidx.compose.material3.Switch(
                checked = state.automation.autoLowBatteryCutoffEnabled,
                onCheckedChange = onToggleLowBattery
              )
            }

            // Threshold pills
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = "သတ်မှတ်ချက်:", fontSize = 10.sp, color = TextMuted)
              listOf(10, 15, 20, 25).forEach { th ->
                val isSel = state.automation.lowBatteryCutoffThreshold == th
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSel) Color(0xFFEF4444) else Color(0xFF1E293B))
                    .clickable { onSetThreshold(th) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(text = "$th%", fontSize = 10.sp, color = if (isSel) Color.White else TextMuted)
                }
              }
            }

            // Overheat halt rule
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "အပူချိန်လွန်ကဲမှု အလိုအလျောက် ရပ်တန့်ခြင်း", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(text = "> ${state.automation.overheatTempThreshold}°C ရောက်လျှင် အန္တရာယ်ကင်းစေရန် အကုန်ပိတ်မည်", fontSize = 10.sp, color = TextMuted)
              }
              androidx.compose.material3.Switch(
                checked = state.automation.autoOverheatHaltEnabled,
                onCheckedChange = onToggleOverheat
              )
            }

            // Grid outage UPS rule
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "မီးပျက်မှု အလိုအလျောက် အရန်စွမ်းအင်သို့ ကူးပြောင်းမှု (EPS/UPS)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(text = "လိုင်းမီးပျက်သည်နှင့် အသံဖြင့်သတိပေး၍ အရန်မီးသို့ ကူးပြောင်းပေးခြင်း", fontSize = 10.sp, color = TextMuted)
              }
              androidx.compose.material3.Switch(
                checked = state.automation.autoGridOutageUpsEnabled,
                onCheckedChange = onToggleGridOutage
              )
            }
          }
        }
        3 -> {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "EcoFlow အခြေအနေ ခွဲခြမ်းစိတ်ဖြာချက် (၃ ရပ်)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .clickable { onSyncCloud() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Cloud ရယူမည်", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            // 3-Pillars Card
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
            ) {
              Text(
                text = "• ၁။ နည်းပညာပိုင်း (Technical): ${if (state.connectionType == EcoFlowConnectionType.BLE) "BLE Connected (${state.bleRssi} dBm)" else if (state.connectionType == EcoFlowConnectionType.WIFI_CLOUD) "Cloud REST API Synced" else "Standby / Offline"}\n• ၂။ အရင်းအမြစ် (Resource): ${if (currentConfig.accessKey.isNotBlank()) "API Keys သတ်မှတ်ပြီးပါပြီ (Ready)" else "API Keys မထည့်ရသေးပါ (Missing)"}\n• ၃။ လက်တွေ့မကိုက်ညီမှု (Real-World Mismatch): ${if (state.isConnected) "ပြင်ပစက်နှင့် ချိတ်ဆက်မိပါသည် (${state.connectionStatus})" else "ပြင်ပ စက် မတွေ့ရှိသေးပါ (အတုအယောင် ဟန်ပြဒေတာ မသုံးပါ)"}",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 18.sp
              )
            }

            // Diagnostic Mode Toggle
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .padding(horizontal = 12.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = "စမ်းသပ်မှုစနစ် (Diagnostic Mode)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AmberLight)
                Text(text = "ပြင်ပ ဟာ့ဒ်ဝဲမပါဘဲ UI logic နှင့် Safe State စမ်းသပ်ရန်", fontSize = 9.sp, color = TextMuted)
              }
              androidx.compose.material3.Switch(
                checked = state.isDiagnosticsMode,
                onCheckedChange = onToggleDiagnostics
              )
            }

            // EcoFlow Cloud Open API Credentials Card
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A))
                .border(1.2.dp, if (currentConfig.accessKey.isNotBlank()) Color(0xFF10B981) else AmberAccent, RoundedCornerShape(14.dp))
                .padding(14.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "EcoFlow Open API Cloud ချိတ်ဆက်မှု",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = Color.White
                    )
                  }
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(if (currentConfig.accessKey.isNotBlank()) Color(0x3310B981) else Color(0x33EF4444))
                      .clickable { showApiConfigEditor = !showApiConfigEditor }
                      .padding(horizontal = 8.dp, vertical = 3.dp)
                  ) {
                    Text(
                      text = if (showApiConfigEditor) "ပိတ်မည်" else "ပြင်ဆင်မည်",
                      fontSize = 10.sp,
                      color = if (currentConfig.accessKey.isNotBlank()) EmeraldPrimary else Color(0xFFF87171),
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                if (!showApiConfigEditor) {
                  // Summary view
                  Text(
                    text = "• Server Host: ${if (currentConfig.hostUrl.isNotBlank()) currentConfig.hostUrl else "https://api-a.ecoflow.com"}\n• Device SN: ${if (currentConfig.deviceSn.isNotBlank()) currentConfig.deviceSn else "မသတ်မှတ်ရသေးပါ"}\n• Access Key: ${if (currentConfig.accessKey.isNotBlank()) currentConfig.accessKey.take(8) + "********" else "မသတ်မှတ်ရသေးပါ (Missing)"}",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                  )
                } else {
                  // Editor Form
                  Text(
                    text = "developer.ecoflow.com မှ developer keys ထည့်သွင်းပါ",
                    fontSize = 10.sp,
                    color = TextMuted
                  )

                  // Region Buttons
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    val isAsia = editHostUrl.contains("api-a.ecoflow.com")
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAsia) Color(0x3310B981) else SurfaceCard)
                        .border(1.dp, if (isAsia) EmeraldPrimary else SurfaceCardBorder, RoundedCornerShape(6.dp))
                        .clickable { editHostUrl = "https://api-a.ecoflow.com" }
                        .padding(vertical = 6.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(text = "Asia / Americas", fontSize = 10.sp, color = if (isAsia) EmeraldPrimary else TextMuted)
                    }

                    val isEu = editHostUrl.contains("api-e.ecoflow.com")
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isEu) Color(0x3310B981) else SurfaceCard)
                        .border(1.dp, if (isEu) EmeraldPrimary else SurfaceCardBorder, RoundedCornerShape(6.dp))
                        .clickable { editHostUrl = "https://api-e.ecoflow.com" }
                        .padding(vertical = 6.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(text = "Europe", fontSize = 10.sp, color = if (isEu) EmeraldPrimary else TextMuted)
                    }
                  }

                  OutlinedTextField(
                    value = editAccessKey,
                    onValueChange = { editAccessKey = it },
                    label = { Text("Access Key", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = TextPrimary,
                      unfocusedTextColor = TextPrimary,
                      focusedBorderColor = AmberAccent,
                      unfocusedBorderColor = SurfaceCardBorder
                    )
                  )

                  OutlinedTextField(
                    value = editSecretKey,
                    onValueChange = { editSecretKey = it },
                    label = { Text("Secret Key", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = TextPrimary,
                      unfocusedTextColor = TextPrimary,
                      focusedBorderColor = AmberAccent,
                      unfocusedBorderColor = SurfaceCardBorder
                    )
                  )

                  OutlinedTextField(
                    value = editDeviceSn,
                    onValueChange = { editDeviceSn = it },
                    label = { Text("Device SN (Serial Number)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedTextColor = TextPrimary,
                      unfocusedTextColor = TextPrimary,
                      focusedBorderColor = AmberAccent,
                      unfocusedBorderColor = SurfaceCardBorder
                    )
                  )

                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(AmberAccent)
                      .clickable {
                        onSaveEcoFlowConfig(editAccessKey, editSecretKey, editDeviceSn, editHostUrl)
                        showApiConfigEditor = false
                      }
                      .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(text = "Save & Verify Cloud Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                  }
                }
              }
            }
          }
        }
        4 -> {
          Column {
            Text(text = "Smart Power Scheduler (${state.selectedModel.modelName})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberLight)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "• 08:00 - 12:00: Solar / Grid Fast Charging Mode (${state.selectedModel.maxAcWatts}W Max)\n• 13:00 - 17:00: Power Saving Mode (Inverter Optimization)\n• 18:00 - 22:00: High Peak Inverter Support (${state.selectedModel.capacityWh}Wh Reserve)",
              fontSize = 11.sp,
              color = TextSecondary,
              lineHeight = 20.sp
            )
          }
        }
      }
    }
  }
}

@Composable
fun BluetoothScannerCard(
  bleDevices: List<BluetoothDeviceItem>,
  isScanning: Boolean,
  onScan: () -> Unit,
  onConnect: (String) -> Unit = {},
  onDisconnect: () -> Unit = {},
  onOpenFullScanner: () -> Unit = {}
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color(0xFF0F1829))
      .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(16.dp))
      .padding(14.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Bluetooth, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = "Bluetooth Device Scanner (Direct Connect)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Text(text = "Next-Gen Web Bluetooth (BLE) v2.5 GATT", fontSize = 9.sp, color = IndigoAccent)
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(IndigoAccent)
            .clickable { onScan() }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = if (isScanning) "ရှာဖွေနေပါသည်..." else "စက်အသစ်ရှာမည် (Scan)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
            .clickable { onOpenFullScanner() }
            .padding(vertical = 8.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "အသေးစိတ်ရှာရန် (Full UI)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Text(text = "တွေ့ရှိထားသော Bluetooth စက်များ: (${bleDevices.size})", fontSize = 10.sp, color = TextMuted)

      if (bleDevices.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          bleDevices.take(4).forEach { device ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF131F37))
                .padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .background(if (device.isEcoFlowDevice) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = if (device.isEcoFlowDevice) Icons.Default.Bolt else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (device.isEcoFlowDevice) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = device.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (device.isEcoFlowDevice) {
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(text = "EcoFlow", fontSize = 8.sp, color = Color(0xFF38BDF8))
                    }
                  }
                  Text(text = "${device.address} • ${device.rssi} dBm", fontSize = 9.sp, color = TextMuted)
                }
              }

              if (device.isConnected) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF7F1D1D))
                    .clickable { onDisconnect() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text("Disconnect", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              } else {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (device.isEcoFlowDevice) Color(0xFF0284C7) else IndigoAccent)
                    .clickable { onConnect(device.address) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Text("Connect", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PortCard(
  title: String,
  sub: String,
  loadText: String,
  isEnabled: Boolean,
  activeColor: Color,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(14.dp))
      .background(if (isEnabled) activeColor.copy(alpha = 0.12f) else SurfaceCard)
      .border(
        1.dp,
        if (isEnabled) activeColor.copy(alpha = 0.5f) else SurfaceCardBorder,
        RoundedCornerShape(14.dp)
      )
      .padding(10.dp)
  ) {
    Column {
      Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Text(text = sub, fontSize = 8.5.sp, color = TextMuted, maxLines = 1)
      Spacer(modifier = Modifier.height(10.dp))
      Text(text = loadText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isEnabled) activeColor else TextMuted)
      Spacer(modifier = Modifier.height(6.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(if (isEnabled) activeColor else Color(0xFF1E293B))
          .clickable { onToggle() }
          .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (isEnabled) "ပိတ်ရန်" else "ဖွင့်ရန်",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (isEnabled) Color.Black else TextPrimary
        )
      }
    }
  }
}

@Composable
fun FeatureSettingCard(
  title: String,
  sub: String,
  statusText: String,
  isEnabled: Boolean,
  buttonText: String,
  activeColor: Color,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
      .padding(12.dp)
  ) {
    Column {
      Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Text(text = sub, fontSize = 8.5.sp, color = TextMuted, maxLines = 1)
      Spacer(modifier = Modifier.height(8.dp))
      Text(text = statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isEnabled) activeColor else TextMuted)
      Spacer(modifier = Modifier.height(6.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(if (isEnabled) activeColor.copy(alpha = 0.2f) else Color(0xFF1E293B))
          .border(1.dp, if (isEnabled) activeColor else SurfaceCardBorder, RoundedCornerShape(8.dp))
          .clickable { onToggle() }
          .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(text = buttonText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isEnabled) activeColor else TextPrimary)
      }
    }
  }
}

@Composable
fun PortSwitchPill(label: String, color: Color, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(color.copy(alpha = 0.2f))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
  }
}

@Composable
fun BadgeChip(text: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(50))
      .background(color.copy(alpha = 0.2f))
      .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
      .padding(horizontal = 10.dp, vertical = 3.dp)
  ) {
    Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
  }
}
