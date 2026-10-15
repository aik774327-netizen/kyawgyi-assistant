package com.example.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.permissions.BackgroundExecutionManager
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Snapshot of current runtime permission states.
 * Supports Android 12/13+ granular permission tracking for Microphone and Bluetooth.
 */
data class RuntimePermissionState(
  val isMicrophoneGranted: Boolean = false,
  val isBluetoothScanGranted: Boolean = false,
  val isBluetoothConnectGranted: Boolean = false,
  val isLocationGranted: Boolean = false,
  val isNotificationGranted: Boolean = false,
  val isContactsGranted: Boolean = false
) {
  val isBluetoothGranted: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      isBluetoothScanGranted && isBluetoothConnectGranted
    } else {
      true
    }

  val areAllEssentialGranted: Boolean
    get() = isMicrophoneGranted && isBluetoothGranted
}

/**
 * Controller handle for Jetpack Compose permission management.
 */
@Stable
class RuntimePermissionHandlerState(
  val permissionState: RuntimePermissionState,
  val requestMicrophone: () -> Unit,
  val requestBluetooth: () -> Unit,
  val requestLocation: () -> Unit,
  val requestNotification: () -> Unit,
  val requestContacts: () -> Unit,
  val requestAll: () -> Unit,
  val refreshPermissions: () -> Unit
)

/**
 * Jetpack Compose runtime permission request handler to explicitly manage and request
 * microphone, Bluetooth (Android 12+), location, and notification access.
 */
@Composable
fun rememberRuntimePermissionHandler(
  onPermissionsUpdated: ((RuntimePermissionState) -> Unit)? = null
): RuntimePermissionHandlerState {
  val context = LocalContext.current

  fun checkCurrentPermissions(): RuntimePermissionState {
    val mic = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val btScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH
      ) == PackageManager.PERMISSION_GRANTED
    }

    val btConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED
    }

    val fineLocation = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseLocation = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

    val contacts = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    return RuntimePermissionState(
      isMicrophoneGranted = mic,
      isBluetoothScanGranted = btScan,
      isBluetoothConnectGranted = btConnect,
      isLocationGranted = fineLocation || coarseLocation,
      isNotificationGranted = notif,
      isContactsGranted = contacts
    )
  }

  var currentPermissions by remember { mutableStateOf(checkCurrentPermissions()) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { _ ->
    val updated = checkCurrentPermissions()
    currentPermissions = updated
    onPermissionsUpdated?.invoke(updated)
  }

  LaunchedEffect(Unit) {
    val initial = checkCurrentPermissions()
    currentPermissions = initial
    onPermissionsUpdated?.invoke(initial)
  }

  return remember(currentPermissions) {
    RuntimePermissionHandlerState(
      permissionState = currentPermissions,
      requestMicrophone = {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
      },
      requestBluetooth = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          permissionLauncher.launch(
            arrayOf(
              Manifest.permission.BLUETOOTH_SCAN,
              Manifest.permission.BLUETOOTH_CONNECT
            )
          )
        } else {
          permissionLauncher.launch(
            arrayOf(
              Manifest.permission.BLUETOOTH,
              Manifest.permission.BLUETOOTH_ADMIN
            )
          )
        }
      },
      requestLocation = {
        permissionLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      },
      requestNotification = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
      },
      requestContacts = {
        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
      },
      requestAll = {
        val permissions = mutableListOf(
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          permissions.add(Manifest.permission.BLUETOOTH_SCAN)
          permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
          permissions.add(Manifest.permission.BLUETOOTH)
          permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
      },
      refreshPermissions = {
        currentPermissions = checkCurrentPermissions()
      }
    )
  }
}

/**
 * Dedicated Jetpack Compose Dialog for explicit Android 12/13+ permission management and requesting.
 * Interlinks directly with DirectRouteManager (ကောင်ဂျီး://) and SystemSettingsDashboard.
 */
@Composable
fun RuntimePermissionModal(
  permissionHandlerState: RuntimePermissionHandlerState,
  onDismiss: () -> Unit,
  onOpenSystemSettings: () -> Unit = {},
  onOpenDirectRoutes: () -> Unit = {}
) {
  val context = LocalContext.current
  val state = permissionHandlerState.permissionState
  val scrollState = rememberScrollState()

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.2.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .padding(18.dp)
        .testTag("runtime_permission_modal")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState)
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
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x3310B981)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "စနစ်ခွင့်ပြုချက်များ စီမံမှု",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
              Text(
                text = "Android 12/13+ Runtime Permissions",
                fontSize = 10.sp,
                color = EmeraldLight
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp).testTag("btn_close_permission_modal")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Finding & Policy Notice Box
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AmberAccent,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = "နည်းပညာပိုင်းဆိုင်ရာ လိုအပ်ချက် (ခွင့်ပြုချက်/စနစ်)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberAccent
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Android 12/13+ မူဝါဒအရ အသံသွင်းချက် (RECORD_AUDIO)၊ ဘလူးတုသ်_စကင် (BLUETOOTH_SCAN) နှင့် ချိတ်ဆက်မှု (BLUETOOTH_CONNECT) များကို စက်ပေါ်တွင် အသုံးပြုသူကိုယ်တိုင် 'Allow' ကို နှိပ်၍ ခွင့်ပြုပေးရန် လိုအပ်ပါသည်။",
              fontSize = 11.sp,
              color = TextSecondary,
              lineHeight = 16.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Microphone Item
        PermissionItemRow(
          icon = Icons.Default.Mic,
          title = "အသံဖမ်းယူခွင့် (Microphone Access)",
          subtitle = "မိုက်ခရိုဖုန်း အသံအမိန့်များနှင့် နောက်ကွယ် အာရုံခံစနစ်အတွက် (RECORD_AUDIO)",
          isGranted = state.isMicrophoneGranted,
          onRequest = { permissionHandlerState.requestMicrophone() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Bluetooth Scan & Connect Item (Android 12+)
        PermissionItemRow(
          icon = Icons.Default.Bluetooth,
          title = "ဘလူးတုသ် စကင်နှင့် ချိတ်ဆက်မှု (Bluetooth)",
          subtitle = "EcoFlow Power Station နှင့် BLE စက်များ ရှာဖွေချိတ်ဆက်ရန် (SCAN/CONNECT)",
          isGranted = state.isBluetoothGranted,
          onRequest = { permissionHandlerState.requestBluetooth() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Location Item
        PermissionItemRow(
          icon = Icons.Default.LocationOn,
          title = "တည်နေရာ (Location for BLE)",
          subtitle = "BLE စက်ပစ္စည်း ရှာဖွေမှုနှင့် ဒေသန္တရ မိုးလေဝသ အချက်အလက်အတွက်",
          isGranted = state.isLocationGranted,
          onRequest = { permissionHandlerState.requestLocation() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Notification Item
        PermissionItemRow(
          icon = Icons.Default.Notifications,
          title = "အသိပေးချက် (Ongoing Notifications)",
          subtitle = "Foreground Service နှင့် နောက်ကွယ် အသံဖမ်းယူမှု အခြေအနေပြသရန် (Android 13+)",
          isGranted = state.isNotificationGranted,
          onRequest = { permissionHandlerState.requestNotification() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Contacts Item
        PermissionItemRow(
          icon = Icons.Default.Contacts,
          title = "ဖုန်းစာအုပ် (Device Contacts)",
          subtitle = "အမေ၊ အဖေ၊ မိတ်ဆွေများဆီသို့ အသံဖြင့် အမှန်တကယ် ဖုန်းခေါ်ဆိုနိုင်ရန် (READ_CONTACTS)",
          isGranted = state.isContactsGranted,
          onRequest = { permissionHandlerState.requestContacts() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Battery Optimization & Background Screen-Off Reliability
        val bgManager = remember { BackgroundExecutionManager(context) }
        var isBatteryExempted by remember { mutableStateOf(bgManager.isBatteryOptimizationExempted()) }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = if (isBatteryExempted) EmeraldPrimary else AmberAccent,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "စခရင်ပိတ်ထားချိန် အသံကြားနိုင်ရေး ဆက်တင်များ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "အက်ပ်အား ပိတ်ထားချိန် / စခရင်ကို ပိတ်ထားချိန်တွင် \"Hey Kyaw Gyi\" အသံကြားနိုင်ရန် Battery Optimization Exemption နှင့် OEM Background Autostart ဆက်တင်များ လိုအပ်ပါသည်",
              fontSize = 10.sp,
              color = TextMuted,
              lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Battery Optimization Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Battery Optimization",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Text(
                  text = if (isBatteryExempted) "ဘက်ထရီကန့်သတ်မှု ကင်းလွတ်ခွင့် ရရှိပြီး" else "ဘက်ထရီ ချွေတာမှုဖြင့် နောက်ခံကို ပိတ်ဆို့နိုင်သည်",
                  fontSize = 9.sp,
                  color = if (isBatteryExempted) EmeraldLight else AmberAccent
                )
              }
              OutlinedButton(
                onClick = {
                  bgManager.openBatteryOptimizationSettings()
                  isBatteryExempted = bgManager.isBatteryOptimizationExempted()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = if (isBatteryExempted) "စစ်ဆေးပြီး" else "ချွင်းချက်ပေးရန်",
                  fontSize = 9.sp,
                  color = if (isBatteryExempted) EmeraldLight else AmberAccent
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OEM Autostart Guidance Card
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .padding(8.dp)
            ) {
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "OEM Autostart: ${bgManager.getOemName()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLight
                  )
                  OutlinedButton(
                    onClick = { bgManager.openOemAutoStartSettings() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text("စက်ဆက်တင်ဖွင့်ရန်", fontSize = 9.sp, color = EmeraldLight)
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                bgManager.getOemGuidanceBurmese().forEach { step ->
                  Text(
                    text = "• $step",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    lineHeight = 13.sp
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main One-Tap Grant All Button (ALLOW)
        Button(
          onClick = {
            if (state.areAllEssentialGranted) {
              onDismiss()
            } else {
              permissionHandlerState.requestAll()
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("grant_all_permissions_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (state.areAllEssentialGranted) EmeraldPrimary else Color(0xFF0284C7)
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (state.areAllEssentialGranted) Icons.Default.CheckCircle else Icons.Default.Security,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = if (state.areAllEssentialGranted) {
                "အားလုံး ခွင့်ပြုပြီးပါပြီ (All Granted)"
              } else {
                "Allow - အားလုံး ခွင့်ပြုမည် (Grant All)"
              },
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interlink Row: Direct Routes & System Settings Dashboard
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Direct Routes Deep Link Button (ကောင်ဂျီး://)
          OutlinedButton(
            onClick = onOpenDirectRoutes,
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .testTag("btn_modal_direct_routes"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.AltRoute, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(14.dp))
              Text("ကောင်ဂျီး:// လမ်းကြောင်း", fontSize = 10.sp, color = EmeraldLight)
            }
          }

          // System Settings Dashboard Button
          OutlinedButton(
            onClick = onOpenSystemSettings,
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .testTag("btn_modal_system_settings"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
              Text("စနစ်ဆက်တင်များ ဒိုင်ခွက်", fontSize = 10.sp, color = Color(0xFF38BDF8))
            }
          }
        }
      }
    }
  }
}

@Composable
fun PermissionItemRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
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
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (isGranted) Color(0x2210B981) else Color(0x22F59E0B)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          icon,
          contentDescription = null,
          tint = if (isGranted) EmeraldPrimary else AmberAccent,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Text(
          text = title,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
        Text(
          text = subtitle,
          fontSize = 9.sp,
          color = TextMuted
        )
      }
    }

    if (isGranted) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0x3310B981))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "GRANTED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        }
      }
    } else {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0x330284C7))
          .clickable { onRequest() }
          .padding(horizontal = 8.dp, vertical = 5.dp)
      ) {
        Text(
          text = "ခွင့်ပြုရန် (Grant)",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF38BDF8)
        )
      }
    }
  }
}
