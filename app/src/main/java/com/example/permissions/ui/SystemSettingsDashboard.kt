package com.example.permissions.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.permissions.AccessibilityReadiness
import com.example.permissions.BatteryReadiness
import com.example.permissions.BluetoothReadiness
import com.example.permissions.MicrophoneReadiness
import com.example.permissions.NotificationReadiness
import com.example.permissions.OverlayReadiness
import com.example.permissions.PermissionViewModel
import com.example.permissions.SystemPermissionsState
import com.example.permissions.SystemReadiness

/**
 * Production System Settings & Permission Dashboard for Kyaw Gyi AI.
 * Implements Sections A-F with granular state verification, dual-language explanations,
 * direct Android settings pathways, and interactive runtime permission launchers.
 */
@Composable
fun SystemSettingsDashboard(
  viewModel: PermissionViewModel,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val state by viewModel.state.collectAsState()
  val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()

  var showWizard by remember { mutableStateOf(false) }
  var showLogs by remember { mutableStateOf(false) }
  var showAccessibilityDisclosure by remember { mutableStateOf(false) }

  // Runtime Permission Launchers
  val micLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.refresh()
  }

  val btLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    viewModel.refresh()
  }

  val notifLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.refresh()
  }

  val locLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    viewModel.refresh()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF090D16))
      .padding(horizontal = 16.dp)
      .verticalScroll(rememberScrollState())
      .testTag("system_settings_dashboard")
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    // Top App Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "System Permissions & Settings",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
        Text(
          text = "ကျော်ကြီး စနစ်ခွင့်ပြုချက်များနှင့် ဆက်တင်များ",
          fontSize = 12.sp,
          color = Color(0xFF94A3B8)
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { showLogs = true },
          modifier = Modifier.testTag("btn_diagnostic_logs")
        ) {
          Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = "Logs",
            tint = Color(0xFF38BDF8)
          )
        }
        IconButton(
          onClick = { viewModel.refresh() },
          modifier = Modifier.testTag("btn_refresh_permissions")
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh",
            tint = Color.White
          )
        }
        if (onClose != null) {
          IconButton(
            onClick = onClose,
            modifier = Modifier.testTag("btn_close_settings")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = Color.White
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Overall System Readiness Card
    ReadinessBanner(
      state = state,
      onOpenWizard = { showWizard = true }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION A: VOICE
    SettingsSectionHeader(
      title = "A. VOICE CONTROL ENGINE",
      myanmarTitle = "အသံဖမ်းယူမှုနှင့် နားထောင်မှု စနစ်",
      icon = Icons.Default.Mic
    )

    SettingRowCard(
      title = "Microphone Access",
      myanmarDescription = "ကျော်ကြီး အသံအမိန့်များကို နားထောင်ရန် Microphone ခွင့်ပြုချက် လိုအပ်ပါသည်။",
      englishDescription = "Microphone permission is required for Kyaw Gyi voice commands.",
      statusText = when (state.microphoneReadiness) {
        MicrophoneReadiness.MIC_PERMISSION_GRANTED -> "ALLOWED"
        MicrophoneReadiness.MIC_HARDWARE_UNAVAILABLE -> "NO HARDWARE"
        else -> "DENIED"
      },
      statusColor = when (state.microphoneReadiness) {
        MicrophoneReadiness.MIC_PERMISSION_GRANTED -> Color(0xFF10B981)
        MicrophoneReadiness.MIC_HARDWARE_UNAVAILABLE -> Color(0xFF6B7280)
        else -> Color(0xFFEF4444)
      },
      icon = Icons.Default.Mic,
      actionLabel = if (state.microphoneReadiness == MicrophoneReadiness.MIC_PERMISSION_GRANTED) null else "ALLOW",
      onAction = {
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
      },
      onOpenSettings = {
        viewModel.openSettings(context, "appdetails")
      }
    )

    SettingRowCard(
      title = "Voice Activity Detection",
      myanmarDescription = "အသံစွမ်းအင်နှင့် လှုပ်ရှားမှုကို အာရုံခံသည့် စနစ် (Voice Activity / Energy Sensing)",
      englishDescription = "Passive voice-activity audio sensing pipeline status.",
      statusText = if (state.microphoneReadiness.isGranted) "READY" else "STANDBY",
      statusColor = if (state.microphoneReadiness.isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
      icon = Icons.Default.Hearing,
      actionLabel = null,
      onAction = {},
      onOpenSettings = { viewModel.openSettings(context, "appdetails") }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION B: BLUETOOTH / ECOFLOW
    SettingsSectionHeader(
      title = "B. BLUETOOTH & ECOFLOW",
      myanmarTitle = "Bluetooth နှင့် EcoFlow ချိတ်ဆက်မှု အခြေအနေ",
      icon = Icons.Default.Bluetooth
    )

    SettingRowCard(
      title = "Nearby Devices (Bluetooth)",
      myanmarDescription = "EcoFlow Power Station ကို ရှာဖွေချိတ်ဆက်ရန် Bluetooth ခွင့်ပြုချက် လိုအပ်ပါသည်။",
      englishDescription = "Nearby devices permission is required for EcoFlow BLE discovery.",
      statusText = when (state.bluetoothReadiness) {
        BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED -> "DENIED"
        BluetoothReadiness.BLUETOOTH_UNSUPPORTED -> "NOT SUPPORTED"
        else -> "GRANTED"
      },
      statusColor = when (state.bluetoothReadiness) {
        BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED -> Color(0xFFEF4444)
        BluetoothReadiness.BLUETOOTH_UNSUPPORTED -> Color(0xFF6B7280)
        else -> Color(0xFF10B981)
      },
      icon = Icons.Default.Security,
      actionLabel = if (state.bluetoothReadiness == BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED) "ALLOW" else null,
      onAction = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          btLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
          btLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN))
        }
      },
      onOpenSettings = { viewModel.openSettings(context, "bluetooth") }
    )

    SettingRowCard(
      title = "Bluetooth Adapter",
      myanmarDescription = "ဖုန်း၏ Bluetooth ခလုတ် ဖွင့်/ပိတ် အခြေအနေ",
      englishDescription = "Physical Bluetooth hardware power state.",
      statusText = when (state.bluetoothReadiness) {
        BluetoothReadiness.BLUETOOTH_DISABLED -> "DISABLED"
        BluetoothReadiness.BLUETOOTH_UNSUPPORTED -> "UNAVAILABLE"
        else -> "ENABLED"
      },
      statusColor = when (state.bluetoothReadiness) {
        BluetoothReadiness.BLUETOOTH_DISABLED -> Color(0xFFF59E0B)
        BluetoothReadiness.BLUETOOTH_UNSUPPORTED -> Color(0xFF6B7280)
        else -> Color(0xFF10B981)
      },
      icon = Icons.Default.Bluetooth,
      actionLabel = if (state.bluetoothReadiness == BluetoothReadiness.BLUETOOTH_DISABLED) "TURN ON" else null,
      onAction = { viewModel.openSettings(context, "bluetooth") },
      onOpenSettings = { viewModel.openSettings(context, "bluetooth") }
    )

    SettingRowCard(
      title = "EcoFlow BLE Pipeline",
      myanmarDescription = "GATT Protocol၊ Authentication နှင့် စက်ချိတ်ဆက်မှု အဆင့်ဆင့် အခြေအနေ",
      englishDescription = "Live EcoFlow communication readiness: ${state.ecoFlowBleReadiness.name}",
      statusText = state.ecoFlowBleReadiness.name,
      statusColor = if (state.ecoFlowBleReadiness.isReady) Color(0xFF10B981) else Color(0xFFF59E0B),
      icon = Icons.Default.Power,
      actionLabel = null,
      onAction = {},
      onOpenSettings = { viewModel.openSettings(context, "bluetooth") }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION C: BACKGROUND & BATTERY
    SettingsSectionHeader(
      title = "C. BACKGROUND RELIABILITY",
      myanmarTitle = "နောက်ခံအလုပ်လုပ်ဆောင်မှုနှင့် ဘက်ထရီ ချွင်းချက်",
      icon = Icons.Default.BatteryChargingFull
    )

    SettingRowCard(
      title = "Battery Optimization",
      myanmarDescription = "နောက်ခံတွင် အသံနှင့် Bluetooth လုပ်ဆောင်မှုများကို တည်ငြိမ်စေရန် Battery Optimization setting ကို စစ်ဆေးနိုင်ပါသည်။",
      englishDescription = "Battery optimization may affect background voice and Bluetooth reliability.",
      statusText = when (state.batteryReadiness) {
        BatteryReadiness.EXEMPTED -> "EXEMPTED"
        BatteryReadiness.OPTIMIZED -> "OPTIMIZED"
      },
      statusColor = when (state.batteryReadiness) {
        BatteryReadiness.EXEMPTED -> Color(0xFF10B981)
        BatteryReadiness.OPTIMIZED -> Color(0xFFF59E0B)
      },
      icon = Icons.Default.BatteryChargingFull,
      actionLabel = if (state.batteryReadiness == BatteryReadiness.OPTIMIZED) "OPTIMIZE" else null,
      onAction = { viewModel.openSettings(context, "batteryoptimization") },
      onOpenSettings = { viewModel.openSettings(context, "batteryoptimization") }
    )

    SettingRowCard(
      title = "OEM Auto-Start / Background Activity",
      myanmarDescription = "စက်ပစ္စည်း ထုတ်လုပ်သူ (${state.manufacturer.uppercase()}) ၏ နောက်ခံ ပိတ်ဆို့မှု ကာကွယ်ရန် ဆက်တင်",
      englishDescription = "Vendor-specific background killer protection for ${state.manufacturer.uppercase()}.",
      statusText = "MANUAL CHECK",
      statusColor = Color(0xFFF59E0B),
      icon = Icons.Default.Settings,
      actionLabel = "OPEN OEM",
      onAction = { viewModel.openOemSettings(context) },
      onOpenSettings = { viewModel.openOemSettings(context) }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION D: SYSTEM CONTROL
    SettingsSectionHeader(
      title = "D. SYSTEM CONTROL",
      myanmarTitle = "စနစ်ထိန်းချုပ်မှုနှင့် အထူးခွင့်ပြုချက်များ",
      icon = Icons.Default.Layers
    )

    SettingRowCard(
      title = "Accessibility Service",
      myanmarDescription = "စနစ်လမ်းညွှန်ချက်များ (Home, Back, Recents) အလိုအလျောက် ဆောင်ရွက်ရန် (Optional)",
      englishDescription = "App control and supported device action automation.",
      statusText = when (state.accessibilityReadiness) {
        AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED -> "ENABLED"
        AccessibilityReadiness.ACCESSIBILITY_SERVICE_DISABLED -> "DISABLED"
      },
      statusColor = when (state.accessibilityReadiness) {
        AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED -> Color(0xFF10B981)
        AccessibilityReadiness.ACCESSIBILITY_SERVICE_DISABLED -> Color(0xFF6B7280)
      },
      icon = Icons.Default.SettingsAccessibility,
      actionLabel = "DISCLOSURE",
      onAction = { showAccessibilityDisclosure = true },
      onOpenSettings = { showAccessibilityDisclosure = true }
    )

    SettingRowCard(
      title = "Floating Assistant Overlay",
      myanmarDescription = "အခြားအက်ပ်များအပေါ်တွင် ကျော်ကြီး အသံခလုတ် အလွယ်တကူ တင်ထားရန် (Optional)",
      englishDescription = "Display Kyaw Gyi voice indicator over other apps.",
      statusText = when (state.overlayReadiness) {
        OverlayReadiness.OVERLAY_GRANTED -> "GRANTED"
        OverlayReadiness.OVERLAY_DENIED -> "DENIED"
      },
      statusColor = when (state.overlayReadiness) {
        OverlayReadiness.OVERLAY_GRANTED -> Color(0xFF10B981)
        OverlayReadiness.OVERLAY_DENIED -> Color(0xFF6B7280)
      },
      icon = Icons.Default.Layers,
      actionLabel = if (state.overlayReadiness == OverlayReadiness.OVERLAY_DENIED) "ENABLE" else null,
      onAction = { viewModel.openSettings(context, "overlay") },
      onOpenSettings = { viewModel.openSettings(context, "overlay") }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION E: NOTIFICATIONS
    SettingsSectionHeader(
      title = "E. NOTIFICATIONS",
      myanmarTitle = "နောက်ခံအသိပေးချက်များနှင့် အချက်ပြမှုများ",
      icon = Icons.Default.Notifications
    )

    SettingRowCard(
      title = "Notification Permission",
      myanmarDescription = "နောက်ခံအသံဖမ်းယူမှု အခြေအနေနှင့် ဘက်ထရီအချက်ပြမှုများ ပြသရန် ခွင့်ပြုချက်",
      englishDescription = "Ongoing status for voice listening service & battery alerts.",
      statusText = when (state.notificationReadiness) {
        NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED -> "GRANTED"
        NotificationReadiness.NOTIFICATION_CHANNEL_DISABLED -> "CHANNEL OFF"
        else -> "DENIED"
      },
      statusColor = when (state.notificationReadiness) {
        NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED -> Color(0xFF10B981)
        else -> Color(0xFFF59E0B)
      },
      icon = Icons.Default.Notifications,
      actionLabel = if (state.notificationReadiness != NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "ALLOW" else null,
      onAction = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          viewModel.openSettings(context, "notifications")
        }
      },
      onOpenSettings = { viewModel.openSettings(context, "notifications") }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // SECTION F: NETWORK & CLOUD
    SettingsSectionHeader(
      title = "F. NETWORK & CLOUD BACKEND",
      myanmarTitle = "အင်တာနက်နှင့် Cloud API ဆက်သွယ်မှု",
      icon = Icons.Default.Cloud
    )

    SettingRowCard(
      title = "Internet Connectivity",
      myanmarDescription = "EcoFlow Cloud API နှင့် မိုးလေဝသ အချက်အလက်များ ချိတ်ဆက်ရန်",
      englishDescription = "Active network transport capability verification.",
      statusText = if (state.isNetworkConnected) "ONLINE" else "OFFLINE",
      statusColor = if (state.isNetworkConnected) Color(0xFF10B981) else Color(0xFFEF4444),
      icon = Icons.Default.Wifi,
      actionLabel = if (!state.isNetworkConnected) "WIFI" else null,
      onAction = { viewModel.openSettings(context, "wireless") },
      onOpenSettings = { viewModel.openSettings(context, "wireless") }
    )

    SettingRowCard(
      title = "Firebase Realtime Service",
      myanmarDescription = "Firebase Application Configuration နှင့် Cloud Sync အခြေအနေ",
      englishDescription = "Firebase app initialization state.",
      statusText = if (state.isFirebaseConfigured) "INITIALIZED" else "STANDALONE",
      statusColor = if (state.isFirebaseConfigured) Color(0xFF10B981) else Color(0xFF6B7280),
      icon = Icons.Default.Cloud,
      actionLabel = null,
      onAction = {},
      onOpenSettings = { viewModel.openSettings(context, "appdetails") }
    )

    Spacer(modifier = Modifier.height(32.dp))
  }

  // Dialogs
  if (showWizard) {
    SetupWizardDialog(
      state = state,
      onDismiss = { showWizard = false },
      onRefresh = { viewModel.refresh() },
      onRequestMicrophone = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
      onRequestBluetooth = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          btLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT))
        } else {
          btLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN))
        }
      },
      onRequestNotification = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          viewModel.openSettings(context, "notifications")
        }
      },
      onOpenSettings = { route -> viewModel.openSettings(context, route) }
    )
  }

  if (showLogs) {
    DiagnosticLogsDialog(
      logs = diagnosticLogs,
      onDismiss = { showLogs = false },
      onClear = { viewModel.clearDiagnosticLogs() }
    )
  }

  // D5: Burmese Accessibility Service Privacy Disclosure
  if (showAccessibilityDisclosure) {
    AlertDialog(
      onDismissRequest = { showAccessibilityDisclosure = false },
      title = {
        Text(
          text = "Accessibility ဝန်ဆောင်မှုဆိုင်ရာ အသိပေးချက်နှင့် ကိုယ်ရေးလုံခြုံမှု",
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Text(
            text = "၁။ အသုံးပြုရသည့် ရည်ရွယ်ချက် (Why it is used):\nအသံအမိန့်ပေးချက်များအရ စနစ်ပင်မစာမျက်နှာ (Home)၊ နောက်သို့ (Back)၊ မကြာသေးမီက အက်ပ်များ (Recent Apps) စသည့် စနစ်လမ်းညွှန်မှုများကို အလိုအလျောက် ဆောင်ရွက်ပေးနိုင်ရန် ဖြစ်ပါသည်။\n\n၂။ မျက်နှာပြင်ပေါ်တွင် စောင့်ကြည့်လေ့လာနိုင်မှု (What it can observe):\nAndroid စနစ်၏ Accessibility API သဘောသဘာဝအရ မျက်နှာပြင် အပြောင်းအလဲများကို သိရှိနိုင်သော်လည်း ကျော်ကြီး သည် သုံးစွဲသူ၏ မျက်နှာပြင်ပါ အချက်အလက်များ၊ စာသားများ၊ စကားဝှက်များကို လုံးဝ (လုံးဝ) မှတ်တမ်းတင်ခြင်း (Log)၊ သိမ်းဆည်းခြင်း သို့မဟုတ် ပြင်ပသို့ ပေးပို့ခြင်း (Transmit) မပြုလုပ်ပါ။ Data Minimization မူဝါဒအတိုင်း လိုအပ်သော စနစ်လုပ်ဆောင်ချက်အတွက်သာ အနည်းဆုံး အသုံးပြုပါသည်။\n\n၃။ အချိန်မရွေး ပိတ်နိုင်ခြင်း (Can disable anytime):\nအဆိုပါ ဝန်ဆောင်မှုကို အသုံးမပြုလိုပါက ဖုန်း၏ Settings > Accessibility (အထူးပြုသုံးစွဲနိုင်မှု) သို့ သွားရောက်၍ အချိန်မရွေး ပြန်လည် ပိတ်ထားနိုင်ပါသည်။",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = Color(0xFFCBD5E1)
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          showAccessibilityDisclosure = false
          viewModel.openSettings(context, "accessibility")
        }) {
          Text("ဆက်တင်သို့ သွားမည်", color = Color(0xFF38BDF8))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAccessibilityDisclosure = false }) {
          Text("နားလည်ပါပြီ", color = Color(0xFF94A3B8))
        }
      },
      containerColor = Color(0xFF1E293B)
    )
  }
}

@Composable
private fun ReadinessBanner(
  state: SystemPermissionsState,
  onOpenWizard: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("system_readiness_banner"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
    border = androidx.compose.foundation.BorderStroke(1.dp, state.overallReadiness.color.copy(alpha = 0.4f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(12.dp)
              .background(state.overallReadiness.color, CircleShape)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "SYSTEM READINESS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = state.overallReadiness.color
          )
        }

        Button(
          onClick = onOpenWizard,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.testTag("btn_open_setup_wizard")
        ) {
          Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Setup Wizard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = state.overallReadiness.label,
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White
      )

      Text(
        text = when (state.overallReadiness) {
          SystemReadiness.GREEN -> "စနစ်အပြည့်အစုံ ကောင်းမွန်စွာ အဆင်သင့်ဖြစ်နေပါပြီ (All essential services ready)."
          SystemReadiness.YELLOW -> "အချို့သော လုပ်ဆောင်ချက်များအတွက် အပိုဆောင်း ခွင့်ပြုချက်များ လိုအပ်ပါသည် (Action recommended)."
          SystemReadiness.RED -> "အသံဖမ်းယူမှု မဖြစ်မနေ လိုအပ်ချက် ပျောက်ဆုံးနေပါသည် (Microphone permission missing)."
          SystemReadiness.GRAY -> "စနစ်ကို မထောက်ပံ့ပါ (System unsupported)."
        },
        fontSize = 12.sp,
        color = Color(0xFF94A3B8)
      )
    }
  }
}

@Composable
private fun SettingsSectionHeader(
  title: String,
  myanmarTitle: String,
  icon: ImageVector
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = Color(0xFF38BDF8),
      modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF38BDF8)
      )
      Text(
        text = myanmarTitle,
        fontSize = 11.sp,
        color = Color(0xFF64748B)
      )
    }
  }
}

@Composable
private fun SettingRowCard(
  title: String,
  myanmarDescription: String,
  englishDescription: String,
  statusText: String,
  statusColor: Color,
  icon: ImageVector,
  actionLabel: String?,
  onAction: () -> Unit,
  onOpenSettings: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpenSettings)
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .background(Color(0xFF1E293B), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
          )
          Spacer(modifier = Modifier.width(8.dp))
          // Semantic Status Chip
          Box(
            modifier = Modifier
              .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
              .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = statusText,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = statusColor
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = myanmarDescription,
          fontSize = 11.sp,
          color = Color(0xFFCBD5E1)
        )
        Text(
          text = englishDescription,
          fontSize = 10.sp,
          color = Color(0xFF64748B)
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      if (actionLabel != null) {
        Button(
          onClick = onAction,
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_action_$title")
        ) {
          Text(text = actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      } else {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
          contentDescription = "Open Settings",
          tint = Color(0xFF64748B),
          modifier = Modifier.size(14.dp)
        )
      }
    }
  }
}
