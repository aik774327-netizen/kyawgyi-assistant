package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent

@Composable
fun StatusPillsBar(
  isOfflineMode: Boolean,
  batteryPercentage: Int,
  bleConnected: Boolean,
  irReady: Boolean = false,
  onPermissionsClick: () -> Unit,
  onWifiClick: () -> Unit,
  onBluetoothClick: () -> Unit,
  onEcoFlowClick: () -> Unit,
  onEcoFlowDashboardClick: () -> Unit = {},
  onIrControllerClick: () -> Unit = {},
  onEsp32MonitorClick: () -> Unit = {},
  onNotificationsClick: () -> Unit = {},
  onApiKeyClick: () -> Unit = {},
  onDirectRoutesClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // 0. Direct Routes & Quick Pathway Pill ("တိုက်ရိုက်လမ်းကြောင်း")
    StatusPill(
      icon = Icons.Default.AltRoute,
      text = "တိုက်ရိုက်လမ်းကြောင်း",
      iconColor = Color(0xFF38BDF8),
      bgColor = Color(0x220284C7),
      borderColor = Color(0x550284C7),
      textColor = Color(0xFF7DD3FC),
      testTag = "pill_direct_routes",
      onClick = onDirectRoutesClick
    )

    // 0a. EcoFlow Power Station Dashboard Pill
    StatusPill(
      icon = Icons.Default.Bolt,
      text = "EcoFlow ဒက်ရှ်ဘုတ် ($batteryPercentage%)",
      iconColor = if (batteryPercentage <= 20) Color(0xFFEF4444) else AmberAccent,
      bgColor = if (batteryPercentage <= 20) Color(0x33EF4444) else Color(0x22F59E0B),
      borderColor = if (batteryPercentage <= 20) Color(0xFFEF4444) else Color(0x44F59E0B),
      textColor = if (batteryPercentage <= 20) Color(0xFFFCA5A5) else Color(0xFFFCD34D),
      testTag = "pill_ecoflow_dashboard",
      onClick = onEcoFlowDashboardClick
    )

    // 0b. Notification Management Pill ("အသိပေးချက်")
    StatusPill(
      icon = Icons.Default.Notifications,
      text = "အသိပေးချက် စီမံမှု",
      iconColor = Color(0xFF38BDF8),
      bgColor = Color(0x220284C7),
      borderColor = Color(0x550284C7),
      textColor = Color(0xFF7DD3FC),
      testTag = "pill_notifications",
      onClick = onNotificationsClick
    )

    // 1. Permissions Pill ("ခွင့်ပြုချက်များ")
    StatusPill(
      icon = Icons.Default.Shield,
      text = "ခွင့်ပြုချက်များ",
      iconColor = EmeraldPrimary,
      bgColor = Color(0x2210B981),
      borderColor = Color(0x4410B981),
      textColor = EmeraldPrimary,
      testTag = "pill_permissions",
      onClick = onPermissionsClick
    )

    // 2. WiFi / Offline Mode Pill
    if (isOfflineMode) {
      StatusPill(
        icon = Icons.Default.WifiOff,
        text = "Offline Mode",
        iconColor = AmberAccent,
        bgColor = Color(0x33F59E0B),
        borderColor = Color(0x66F59E0B),
        textColor = AmberAccent,
        testTag = "pill_wifi",
        onClick = onWifiClick
      )
    } else {
      StatusPill(
        icon = Icons.Default.Wifi,
        text = "WiFi Online",
        iconColor = Color(0xFF60A5FA),
        bgColor = Color(0x223B82F6),
        borderColor = Color(0x443B82F6),
        textColor = Color(0xFF93C5FD),
        testTag = "pill_wifi",
        onClick = onWifiClick
      )
    }

    // 3. Bluetooth Pill
    StatusPill(
      icon = Icons.Default.Bluetooth,
      text = if (bleConnected) "Bluetooth စစ်ဆေး" else "Bluetooth Off",
      iconColor = IndigoAccent,
      bgColor = Color(0x226366F1),
      borderColor = Color(0x446366F1),
      textColor = Color(0xFFA5B4FC),
      testTag = "pill_bluetooth",
      onClick = onBluetoothClick
    )

    // 4. EcoFlow Battery Pill
    StatusPill(
      icon = Icons.Default.Bolt,
      text = "EcoFlow $batteryPercentage%",
      iconColor = AmberAccent,
      bgColor = Color(0x22F59E0B),
      borderColor = Color(0x44F59E0B),
      textColor = Color(0xFFFCD34D),
      testTag = "pill_ecoflow",
      onClick = onEcoFlowClick
    )

    // 5. KENNEDE KN-5996HRB IR Controller Pill
    StatusPill(
      icon = Icons.Default.Sensors,
      text = if (irReady) "KENNEDE IR အဆင်သင့်" else "KENNEDE IR စစ်ဆေးရန်",
      iconColor = if (irReady) Color(0xFF38BDF8) else Color(0xFF94A3B8),
      bgColor = if (irReady) Color(0x220284C7) else Color(0x22334155),
      borderColor = if (irReady) Color(0x550284C7) else Color(0x44334155),
      textColor = if (irReady) Color(0xFF7DD3FC) else Color(0xFF94A3B8),
      testTag = "pill_kennede_ir",
      onClick = onIrControllerClick
    )

    // 6. ESP32 & IR Verification Monitor Pill
    StatusPill(
      icon = Icons.Default.Router,
      text = "ESP32 စောင့်ကြည့်မှု",
      iconColor = Color(0xFF38BDF8),
      bgColor = Color(0x220284C7),
      borderColor = Color(0x440284C7),
      textColor = Color(0xFFBAE6FD),
      testTag = "pill_esp32_monitor",
      onClick = onEsp32MonitorClick
    )

    // 7. API Key Manager Pill ("API သော့ချက်")
    StatusPill(
      icon = Icons.Default.Key,
      text = "API သော့ချက် စီမံမှု",
      iconColor = Color(0xFFA855F7),
      bgColor = Color(0x22A855F7),
      borderColor = Color(0x55A855F7),
      textColor = Color(0xFFD8B4FE),
      testTag = "pill_api_key_manager",
      onClick = onApiKeyClick
    )
  }
}

@Composable
fun StatusPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  iconColor: Color,
  bgColor: Color,
  borderColor: Color,
  textColor: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(50))
      .background(bgColor)
      .border(1.dp, borderColor, RoundedCornerShape(50))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 7.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = text,
      tint = iconColor,
      modifier = Modifier.size(13.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = text,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = textColor
    )
  }
}
