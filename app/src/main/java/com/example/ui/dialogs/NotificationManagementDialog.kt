package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.notification.AppNotificationRecord
import com.example.notification.NotificationManagementHelper
import com.example.notification.NotificationType

@Composable
fun NotificationManagementDialog(
  notificationHelper: NotificationManagementHelper,
  onDismiss: () -> Unit
) {
  val settings by notificationHelper.settings.collectAsState()
  val history by notificationHelper.notificationHistory.collectAsState()

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.96f)
        .height(680.dp)
        .testTag("notification_management_dialog"),
      shape = RoundedCornerShape(24.dp),
      color = Color(0xFF0F172A),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp)
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
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x2238BDF8)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = "Notification Settings",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "အသိပေးချက် စီမံခန့်ခွဲမှု",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Notification Management & Alerts",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color(0xFF1E293B))
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Threshold Configuration & Toggle Controls
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Low Battery Alert Switch
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "EcoFlow ဘက်ထရီအားနည်း သတိပေးချက်",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White
                )
                Text(
                  text = "သတ်မှတ်ချက်အောက် ရောက်ရှိပါက အသံ/တုန်ခါမှုဖြင့် အသိပေးမည်",
                  fontSize = 10.sp,
                  color = Color(0xFF94A3B8)
                )
              }
              Switch(
                checked = settings.batteryAlertsEnabled,
                onCheckedChange = { notificationHelper.toggleBatteryAlerts(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFEF4444))
              )
            }

            // Slider for Threshold
            if (settings.batteryAlertsEnabled) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(text = "အနိမ့်ဆုံး ဘက်ထရီ သတ်မှတ်ချက်:", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                Text(
                  text = "${settings.lowBatteryThreshold}%",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFEF4444)
                )
              }

              Slider(
                value = settings.lowBatteryThreshold.toFloat(),
                onValueChange = { notificationHelper.setLowBatteryThreshold(it.toInt()) },
                valueRange = 5f..50f,
                steps = 8,
                colors = SliderDefaults.colors(
                  thumbColor = Color(0xFFEF4444),
                  activeTrackColor = Color(0xFFEF4444),
                  inactiveTrackColor = Color(0xFF334155)
                )
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // IR Remote Execution Switch
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "IR အဝေးထိန်း အမိန့်ပေး အောင်မြင်မှု",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White
                )
                Text(
                  text = "KENNEDE ပန်ကာသို့ အချက်ပြရောက်ရှိချိန် အသိပေးမည်",
                  fontSize = 10.sp,
                  color = Color(0xFF94A3B8)
                )
              }
              Switch(
                checked = settings.irSuccessAlertsEnabled,
                onCheckedChange = { notificationHelper.toggleIrSuccessAlerts(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF38BDF8))
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Test Notification Buttons Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = { notificationHelper.sendTestAlert(NotificationType.LOW_BATTERY) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
          ) {
            Icon(Icons.Default.BatteryAlert, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "ဘက်ထရီ စမ်းသပ်", fontSize = 10.sp)
          }

          OutlinedButton(
            onClick = { notificationHelper.sendTestAlert(NotificationType.IR_COMMAND_SUCCESS) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
          ) {
            Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "IR စမ်းသပ်", fontSize = 10.sp)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Notification History Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "လက်ခံရရှိခဲ့သော သတိပေးချက်များ (${history.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8)
          )

          if (history.isNotEmpty()) {
            Text(
              text = "ရှင်းလင်းမည်",
              fontSize = 11.sp,
              color = Color(0xFFEF4444),
              modifier = Modifier.clickable { notificationHelper.clearNotificationHistory() }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // History list
        if (history.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .background(Color(0xFF131F33)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "မှတ်တမ်းတင်ထားသော သတိပေးချက် မရှိသေးပါ",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(history, key = { it.id }) { record ->
              NotificationHistoryCard(record)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationHistoryCard(record: AppNotificationRecord) {
  val accentColor = when (record.type) {
    NotificationType.LOW_BATTERY -> Color(0xFFEF4444)
    NotificationType.IR_COMMAND_SUCCESS -> Color(0xFF38BDF8)
    NotificationType.SYSTEM_INFO -> Color(0xFF10B981)
  }

  val icon = when (record.type) {
    NotificationType.LOW_BATTERY -> Icons.Default.BatteryAlert
    NotificationType.IR_COMMAND_SUCCESS -> Icons.Default.Sensors
    NotificationType.SYSTEM_INFO -> Icons.Default.CheckCircle
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF1E293B))
      .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
      .padding(12.dp)
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = record.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = record.timestamp.takeLast(8),
            fontSize = 9.sp,
            color = Color(0xFF64748B)
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = record.message,
          fontSize = 11.sp,
          color = Color(0xFFCBD5E1),
          lineHeight = 16.sp
        )
      }
    }
  }
}
