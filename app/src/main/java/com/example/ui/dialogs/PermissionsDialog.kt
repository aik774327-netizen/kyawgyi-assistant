package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.realworld.DeviceLocationInfo
import com.example.ui.permissions.RuntimePermissionHandlerState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PermissionsDialog(
  onDismiss: () -> Unit,
  onOpenAppDetails: () -> Unit = {},
  onOpenMicrophoneSettings: () -> Unit = {},
  onOpenTtsSettings: () -> Unit = {},
  onOpenBatteryOptimization: () -> Unit = {},
  onOpenNotificationSettings: () -> Unit = {},
  onOpenOverlaySettings: () -> Unit = {},
  onOpenLocationSettings: () -> Unit = {},
  onOpenAutoStartSettings: () -> Unit = {},
  onOpenSoundSettings: () -> Unit = {},
  onOpenWifiSettings: () -> Unit = {},
  onOpenBluetoothSettings: () -> Unit = {},
  isBatteryOptimizationIgnored: Boolean = false,
  isBackgroundListening: Boolean = false,
  onToggleBackgroundListening: (Boolean) -> Unit = {},
  ambientNoiseDb: Float = 0f,
  onTestVoice: () -> Unit = {},
  permissionHandlerState: RuntimePermissionHandlerState? = null,
  deviceLocation: DeviceLocationInfo? = null,
  onRefreshLocation: () -> Unit = {}
) {
  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.2.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .padding(20.dp)
        .testTag("permissions_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Top Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x3310B981)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(text = "စနစ်ခွင့်ပြုချက်နှင့် ဆက်တင် လမ်းကြောင်းများ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
              Text(text = "Kyaw Gyi Direct System Access Paths", fontSize = 10.sp, color = TextMuted)
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Runtime Permission Request Handler (Microphone & Location)
        if (permissionHandlerState != null) {
          val pState = permissionHandlerState.permissionState
          Text(
            text = "စနစ်တွင်း တိုက်ရိုက် ခွင့်ပြုချက်များ (Jetpack Compose Runtime Handler):",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
          )
          Spacer(modifier = Modifier.height(6.dp))

          // Microphone Permission Row
          ComposePermissionStatusRow(
            icon = Icons.Default.Mic,
            name = "မိုက်ခရိုဖုန်း (Microphone Access)",
            desc = "မြန်မာစကားသံ အမိန့်များနှင့် စကားပြန်ကြားမှုအတွက် (RECORD_AUDIO)",
            isGranted = pState.isMicrophoneGranted,
            onRequest = { permissionHandlerState.requestMicrophone() }
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Bluetooth Permission Row
          ComposePermissionStatusRow(
            icon = Icons.Default.Bluetooth,
            name = "ဘလူးတုသ် စကင်နှင့် ချိတ်ဆက်မှု (Bluetooth)",
            desc = "EcoFlow Hardware နှင့် BLE စက်များ ရှာဖွေချိတ်ဆက်ရန်",
            isGranted = pState.isBluetoothGranted,
            onRequest = { permissionHandlerState.requestBluetooth() }
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Push Notifications Permission Row
          ComposePermissionStatusRow(
            icon = Icons.Default.Notifications,
            name = "အသိပေးချက် (Push Notifications)",
            desc = "Foreground Service သတိပေးချက်များ ပြသရန် (Android 13+)",
            isGranted = pState.isNotificationGranted,
            onRequest = { permissionHandlerState.requestNotification() }
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Contacts Permission Row
          ComposePermissionStatusRow(
            icon = Icons.Default.Contacts,
            name = "ဖုန်းစာအုပ် (Device Contacts)",
            desc = "အမေ၊ အဖေ၊ မိတ်ဆွေများဆီသို့ ဖုန်းခေါ်ရန် (READ_CONTACTS)",
            isGranted = pState.isContactsGranted,
            onRequest = { permissionHandlerState.requestContacts() }
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Location Permission Row
          ComposePermissionStatusRow(
            icon = Icons.Default.LocationOn,
            name = "တည်နေရာ (Location Access)",
            desc = "မိုးလေဝသ၊ ဒေသဆိုင်ရာ အချက်အလက်နှင့် အနီးဆုံး စက်ပစ္စည်း",
            isGranted = pState.isLocationGranted,
            onRequest = { permissionHandlerState.requestLocation() }
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Location Info Box if granted
          if (pState.isLocationGranted) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x1510B981))
                .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "တည်နေရာ အချက်အလက် (Current GPS Location):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                  )
                  Text(
                    text = deviceLocation?.descriptionBurmese ?: "GPS အချက်အလက် ဖတ်ရှုနေပါသည်...",
                    fontSize = 9.sp,
                    color = TextPrimary
                  )
                }
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x3310B981))
                    .clickable { onRefreshLocation() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "စစ်ဆေးမည်", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
          }

          // Grant All Button if any missing
          if (!pState.areAllEssentialGranted) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0284C7))
                .clickable { permissionHandlerState.requestAll() }
                .padding(vertical = 9.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "မိုက်ခရိုဖုန်းနှင့် တည်နေရာ ခွင့်ပြုချက် အားလုံး တောင်းခံရန် (Grant Permissions)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
          }
        }

        // Background Listening Active Switch & 100% Boost Indicator
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.2.dp, if (isBackgroundListening) EmeraldPrimary else SurfaceCardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "နောက်ကွယ် မိုက်ခရိုဖုန်း အာရုံခံ နည်းစနစ်",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                  if (isBackgroundListening) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3310B981))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(text = "100% အာရုံခံ တည်မြဲ", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    }
                  }
                }
                Text(
                  text = "နောက်ကွယ်မှ စကားသံဖမ်းယူမှု 100% တိုးမြှင့်ပြီး အမြဲတမ်း တည်မြဲစေပါသည်",
                  fontSize = 10.sp,
                  color = TextMuted
                )
              }

              Switch(
                checked = isBackgroundListening,
                onCheckedChange = onToggleBackgroundListening,
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.White,
                  checkedTrackColor = EmeraldPrimary,
                  uncheckedThumbColor = Color.Gray,
                  uncheckedTrackColor = Color(0xFF1E293B)
                )
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 100% Boost & Perpetual Persistence details chip
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1810B981))
                .border(1.dp, Color(0x3310B981), RoundedCornerShape(8.dp))
                .padding(8.dp)
            ) {
              Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.GraphicEq, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(13.dp))
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "Audio Capture 100% Boosted: +6dB Digital Pre-Amp & Hardware AGC/NS Active",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                  )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Shield, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = "အမြဲတမ်း တည်မြဲခြင်း: Permanent WakeLock & Auto-Restart Watchdog Active",
                    fontSize = 9.sp,
                    color = Color(0xFFE2E8F0)
                  )
                }
                if (isBackgroundListening) {
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = "လက်ရှိ ပတ်ဝန်းကျင်အသံ အာရုံခံမှု: ${"%.1f".format(ambientNoiseDb)} dB (Real-time RMS)",
                    fontSize = 9.sp,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Direct System Permission Pathways
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Setting ခွင့်ပြုချက်များ တိုက်ရိုက်လမ်းကြောင်း (Direct Settings Pathways):",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AmberAccent
          )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Direct Microphone Permission
        DirectSettingActionItem(
          icon = Icons.Default.Mic,
          title = "မိုက်ခရိုဖုန်း အထူးခွင့်ပြုချက် (Microphone Permission)",
          desc = "မြန်မာအသံအမိန့်များ ၁၀၀% ဖမ်းယူရန် စနစ်ခွင့်ပြုချက် တိုက်ရိုက်ဖွင့်ရန်",
          onClick = onOpenMicrophoneSettings
        )

        // 2. Battery Optimization Exemption
        DirectSettingActionItem(
          icon = Icons.Default.BatteryChargingFull,
          title = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် (Battery Optimization Exemption)",
          desc = if (isBatteryOptimizationIgnored) "အသံဖမ်းယူမှု အမြဲတမ်း တည်မြဲခွင့် ရရှိထားပါသည် (Ignore Battery Optimizations)" else "နောက်ကွယ် အသံဖမ်းယူမှု အမြဲတမ်း တည်မြဲစေရန် နှိပ်၍ ခွင့်ပြုပါ",
          statusBadge = if (isBatteryOptimizationIgnored) "ကင်းလွတ်ခွင့် ရရှိပြီး" else "ခွင့်ပြုရန် လိုအပ်",
          isSuccessBadge = isBatteryOptimizationIgnored,
          onClick = onOpenBatteryOptimization
        )

        // 3. Notification Settings
        DirectSettingActionItem(
          icon = Icons.Default.Notifications,
          title = "အသိပေးချက် စနစ်ခွင့်ပြုချက် (Push Notification Settings)",
          desc = "Foreground Service သတိပေးချက်နှင့် စနစ်တုံ့ပြန်မှုများ ပြသရန်",
          onClick = onOpenNotificationSettings
        )

        // 4. Overlay Settings
        DirectSettingActionItem(
          icon = Icons.Default.Layers,
          title = "အခြားအက်ပ်များပေါ်တွင် ပြသခွင့် (Overlay Permission)",
          desc = "မည်သည့် App ထဲတွင်မဆို ကျော်ကြီး အသံလက်ခံခလုတ်ကို အပေါ်မှပြသရန်",
          onClick = onOpenOverlaySettings
        )

        // 5. Location / GPS Settings
        DirectSettingActionItem(
          icon = Icons.Default.LocationOn,
          title = "တည်နေရာနှင့် ဂျီပီအက်စ် ဆက်တင် (Location / GPS Settings)",
          desc = "ဒေသဆိုင်ရာ မိုးလေဝသနှင့် အနီးဆုံးစက်ပစ္စည်း ချိတ်ဆက်မှု ဆက်တင်",
          onClick = onOpenLocationSettings
        )

        // 6. OEM Auto-Start Settings
        val localContext = androidx.compose.ui.platform.LocalContext.current
        val oemManager = androidx.compose.runtime.remember { com.example.permissions.BackgroundExecutionManager(localContext) }

        DirectSettingActionItem(
          icon = Icons.Default.PowerSettingsNew,
          title = "နောက်ကွယ် အလိုအလျောက် စတင်ခွင့် (Auto-Start / Background Manager)",
          desc = "${oemManager.getOemName()} တွင် အက်ပ်အား နောက်ခံမှ ရပ်တန့်မသွားစေရန် တိုက်ရိုက်ဆက်တင် ဖွင့်ပါ",
          onClick = onOpenAutoStartSettings
        )

        // OEM Guidance Steps Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(10.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "စက်အလိုက် လမ်းညွှန်ချက်: ${oemManager.getOemName()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldLight
              )
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(0x3310B981))
                  .clickable { onOpenAutoStartSettings() }
                  .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(text = "ဆက်တင်ဖွင့်ရန်", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            oemManager.getOemGuidanceBurmese().forEach { step ->
              Text(
                text = "• $step",
                fontSize = 9.sp,
                color = TextSecondary,
                lineHeight = 13.sp
              )
            }
          }
        }

        // 7. Google Myanmar TTS Settings
        DirectSettingActionItem(
          icon = Icons.Default.RecordVoiceOver,
          title = "Google မြန်မာအသံထွက် (TTS) ဆက်တင်",
          desc = "မြန်မာစကား ပီပီသသ နွေးထွေးစွာ ထွက်ဆိုရန် အသံဒေါင်းလုဒ်/ရွေးချယ်ရန်",
          onClick = onOpenTtsSettings
        )

        // 8. Sound and Volume Settings
        DirectSettingActionItem(
          icon = Icons.Default.VolumeUp,
          title = "အသံနှင့် တုန်ခါမှု စနစ် ဆက်တင် (Sound & Volume Settings)",
          desc = "ဖုန်း၏ အသံအတိုးအကျယ်နှင့် တုံ့ပြန်သံ စနစ်များကို တိုက်ရိုက်ဖွင့်ရန်",
          onClick = onOpenSoundSettings
        )

        // 9. WiFi Settings
        DirectSettingActionItem(
          icon = Icons.Default.Wifi,
          title = "ဝိုင်ဖိုင် ကွန်ရက် ဆက်တင် (WiFi Network Settings)",
          desc = "ESP32 IR Controller နှင့် EcoFlow စက်ပစ္စည်းများ ချိတ်ဆက်ရန်",
          onClick = onOpenWifiSettings
        )

        // 10. Bluetooth Settings
        DirectSettingActionItem(
          icon = Icons.Default.Bluetooth,
          title = "ဘလူးတုသ် ဆက်တင် (Bluetooth Device Settings)",
          desc = "အနီးနားရှိ စမတ်စက်ပစ္စည်းများနှင့် အသံဖမ်းစက်များ တွဲဖက်ရန်",
          onClick = onOpenBluetoothSettings
        )

        // 11. Master App Details Settings
        DirectSettingActionItem(
          icon = Icons.Default.Settings,
          title = "အက်ပ် အချက်အလက် စနစ် ဆက်တင် (Application Details Settings)",
          desc = "Android စနစ်အတွင်းရှိ ပရောဂျက် ခွင့်ပြုချက်အားလုံးကို စီမံခန့်ခွဲရန်",
          onClick = onOpenAppDetails
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Test Authentic Burmese Voice Button
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF065F46))
            .clickable { onTestVoice() }
            .padding(vertical = 11.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "မြန်မာအသံထွက် နမူနာ နားထောင်စမ်းသပ်ရန် (Test Warm TTS)",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(EmeraldPrimary)
            .clickable { onDismiss() }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "ပြီးပါပြီ (Confirm & Close)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
      }
    }
  }
}

@Composable
private fun ComposePermissionStatusRow(
  icon: ImageVector,
  name: String,
  desc: String,
  isGranted: Boolean,
  onRequest: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceCard)
      .border(
        1.dp,
        if (isGranted) Color(0x3310B981) else SurfaceCardBorder,
        RoundedCornerShape(12.dp)
      )
      .padding(10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (isGranted) Color(0x2210B981) else Color(0x22F59E0B)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = if (isGranted) EmeraldPrimary else AmberAccent,
          modifier = Modifier.size(16.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = desc, fontSize = 9.sp, color = TextSecondary)
      }
    }

    if (isGranted) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0x3310B981))
          .padding(horizontal = 8.dp, vertical = 3.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(11.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "ခွင့်ပြုပြီး", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        }
      }
    } else {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0x330284C7))
          .clickable { onRequest() }
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Text(
          text = "တောင်းခံမည်",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF38BDF8)
        )
      }
    }
  }
}

@Composable
private fun DirectSettingActionItem(
  icon: ImageVector,
  title: String,
  desc: String,
  statusBadge: String? = null,
  isSuccessBadge: Boolean = false,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0x2238BDF8)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          if (statusBadge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSuccessBadge) Color(0x3310B981) else Color(0x33F59E0B))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = statusBadge,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSuccessBadge) EmeraldPrimary else AmberAccent
              )
            }
          }
        }
        Text(text = desc, fontSize = 9.sp, color = TextSecondary)
      }
    }

    Icon(
      Icons.AutoMirrored.Filled.ArrowForwardIos,
      contentDescription = "Navigate",
      tint = TextMuted,
      modifier = Modifier.size(13.dp)
    )
  }
}
