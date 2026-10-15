package com.example.permissions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.permissions.AccessibilityReadiness
import com.example.permissions.BatteryReadiness
import com.example.permissions.BluetoothReadiness
import com.example.permissions.MicrophoneReadiness
import com.example.permissions.NotificationReadiness
import com.example.permissions.OverlayReadiness
import com.example.permissions.SystemPermissionsState
import com.example.permissions.SystemReadiness

@Composable
fun SetupWizardDialog(
  state: SystemPermissionsState,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit,
  onRequestMicrophone: () -> Unit,
  onRequestBluetooth: () -> Unit,
  onRequestNotification: () -> Unit,
  onOpenSettings: (String) -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .height(640.dp)
        .testTag("setup_wizard_dialog"),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Wizard Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF38BDF8).copy(alpha = 0.15f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Kyaw Gyi Setup Wizard",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "စနစ်အပြည့်အစုံ စစ်ဆေးခြင်း လမ်းညွှန်",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_wizard_button")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = Color(0xFF94A3B8)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Steps list
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // STEP 1: Microphone
          WizardStepItem(
            stepNumber = 1,
            title = "Microphone Permission",
            myanmarTitle = "အသံဖမ်းယူခွင့် (မဖြစ်မနေလိုအပ်)",
            icon = Icons.Default.Mic,
            isSuccess = state.microphoneReadiness == MicrophoneReadiness.MIC_PERMISSION_GRANTED,
            statusLabel = if (state.microphoneReadiness == MicrophoneReadiness.MIC_PERMISSION_GRANTED) "Granted" else "Required",
            actionLabel = if (state.microphoneReadiness == MicrophoneReadiness.MIC_PERMISSION_GRANTED) null else "Grant",
            onAction = onRequestMicrophone
          )

          // STEP 2: Nearby Devices
          val isBtPerm = state.bluetoothReadiness != BluetoothReadiness.BLUETOOTH_PERMISSION_DENIED &&
            state.bluetoothReadiness != BluetoothReadiness.BLUETOOTH_UNSUPPORTED
          WizardStepItem(
            stepNumber = 2,
            title = "Nearby Devices (Bluetooth)",
            myanmarTitle = "အနီးရှိ စက်ပစ္စည်း ခွင့်ပြုချက်",
            icon = Icons.Default.Bluetooth,
            isSuccess = isBtPerm,
            statusLabel = if (isBtPerm) "Granted" else "Required",
            actionLabel = if (isBtPerm) null else "Grant",
            onAction = onRequestBluetooth
          )

          // STEP 3: Bluetooth Hardware
          val isBtEnabled = state.bluetoothReadiness == BluetoothReadiness.BLUETOOTH_READY ||
            state.bluetoothReadiness == BluetoothReadiness.GATT_CONNECTED
          WizardStepItem(
            stepNumber = 3,
            title = "Bluetooth Adapter",
            myanmarTitle = "Bluetooth ခလုတ် ဖွင့်ထားမှု",
            icon = Icons.Default.Bluetooth,
            isSuccess = isBtEnabled,
            statusLabel = if (isBtEnabled) "Enabled" else "Disabled",
            actionLabel = if (isBtEnabled) null else "Turn On",
            onAction = { onOpenSettings("bluetooth") }
          )

          // STEP 4: Notifications
          val isNotif = state.notificationReadiness == NotificationReadiness.NOTIFICATION_PERMISSION_GRANTED
          WizardStepItem(
            stepNumber = 4,
            title = "Notification Alerts",
            myanmarTitle = "နောက်ခံအသိပေးချက်များ",
            icon = Icons.Default.Notifications,
            isSuccess = isNotif,
            statusLabel = if (isNotif) "Granted" else "Recommended",
            actionLabel = if (isNotif) null else "Enable",
            onAction = onRequestNotification
          )

          // STEP 5: Accessibility Service
          val isAccess = state.accessibilityReadiness == AccessibilityReadiness.ACCESSIBILITY_SERVICE_ENABLED
          WizardStepItem(
            stepNumber = 5,
            title = "Accessibility Automation",
            myanmarTitle = "စနစ်အလိုအလျောက် ခိုင်းစေမှု (Optional)",
            icon = Icons.Default.SettingsAccessibility,
            isSuccess = isAccess,
            statusLabel = if (isAccess) "Enabled" else "Optional",
            actionLabel = if (isAccess) null else "Settings",
            onAction = { onOpenSettings("accessibility") }
          )

          // STEP 6: Overlay
          val isOverlay = state.overlayReadiness == OverlayReadiness.OVERLAY_GRANTED
          WizardStepItem(
            stepNumber = 6,
            title = "Floating Voice Overlay",
            myanmarTitle = "မျက်နှာပြင်ပေါ် အသံခလုတ်တင်ရန်",
            icon = Icons.Default.Layers,
            isSuccess = isOverlay,
            statusLabel = if (isOverlay) "Granted" else "Optional",
            actionLabel = if (isOverlay) null else "Allow",
            onAction = { onOpenSettings("overlay") }
          )

          // STEP 7: Battery Optimization
          val isBatteryExempt = state.batteryReadiness == BatteryReadiness.EXEMPTED
          WizardStepItem(
            stepNumber = 7,
            title = "Battery Optimization",
            myanmarTitle = "နောက်ခံ အသံမရပ်တန့်ရေး ခြွင်းချက်",
            icon = Icons.Default.BatteryChargingFull,
            isSuccess = isBatteryExempt,
            statusLabel = if (isBatteryExempt) "Exempted" else "Action Recommended",
            actionLabel = if (isBatteryExempt) null else "Optimize",
            onAction = { onOpenSettings("battery") }
          )

          // STEP 8: EcoFlow BLE Readiness
          val isEcoFlowReady = state.ecoFlowBleReadiness.isReady
          WizardStepItem(
            stepNumber = 8,
            title = "EcoFlow BLE Pipeline",
            myanmarTitle = "EcoFlow ဘက်ထရီ ချိတ်ဆက်မှု အဆင်သင့်ဖြစ်မှု",
            icon = Icons.Default.Power,
            isSuccess = isEcoFlowReady,
            statusLabel = state.ecoFlowBleReadiness.name,
            actionLabel = if (isEcoFlowReady) null else "Settings",
            onAction = { onOpenSettings("bluetooth") }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // System Readiness Badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(state.overallReadiness.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, state.overallReadiness.color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "SYSTEM READINESS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = state.overallReadiness.color
              )
              Text(
                text = state.overallReadiness.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
            OutlinedButton(
              onClick = onRefresh,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              modifier = Modifier.testTag("wizard_refresh_button")
            ) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Re-Check", fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WizardStepItem(
  stepNumber: Int,
  title: String,
  myanmarTitle: String,
  icon: ImageVector,
  isSuccess: Boolean,
  statusLabel: String,
  actionLabel: String?,
  onAction: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
      .border(1.dp, if (isSuccess) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF334155), RoundedCornerShape(12.dp))
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Step indicator
    Box(
      modifier = Modifier
        .size(32.dp)
        .background(if (isSuccess) Color(0xFF10B981) else Color(0xFF475569), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      if (isSuccess) {
        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
      } else {
        Text(text = "$stepNumber", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Step Info
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      }
      Text(text = myanmarTitle, fontSize = 11.sp, color = Color(0xFF94A3B8))
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = statusLabel,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (isSuccess) Color(0xFF10B981) else Color(0xFFF59E0B)
      )
    }

    // Action button if not success
    if (actionLabel != null) {
      Button(
        onClick = onAction,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
