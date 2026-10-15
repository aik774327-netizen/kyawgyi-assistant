package com.example.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

/**
 * State object holding runtime permission verification status for Microphone and Location.
 */
@Stable
data class PermissionStatus(
  val isMicrophoneGranted: Boolean = false,
  val isLocationGranted: Boolean = false,
  val shouldShowRationale: Boolean = false
) {
  val hasAllEssentialPermissions: Boolean
    get() = isMicrophoneGranted
}

/**
 * Controller class to request and verify permissions within Jetpack Compose UI flows.
 */
class PermissionManager(
  val status: PermissionStatus,
  private val requestAllLauncher: () -> Unit,
  private val requestMicrophoneLauncher: () -> Unit,
  private val requestLocationLauncher: () -> Unit,
  val openAppSettings: () -> Unit
) {
  fun requestAllPermissions() = requestAllLauncher()
  fun requestMicrophonePermission() = requestMicrophoneLauncher()
  fun requestLocationPermission() = requestLocationLauncher()
}

/**
 * Remembers and provisions a PermissionManager utilizing standard ActivityResultContracts
 * for verifying Microphone and Location runtime permissions in Compose.
 */
@Composable
fun rememberPermissionManager(
  onPermissionsUpdated: ((PermissionStatus) -> Unit)? = null
): PermissionManager {
  val context = LocalContext.current

  fun checkCurrentPermissions(): PermissionStatus {
    val micGranted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val locationGranted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return PermissionStatus(
      isMicrophoneGranted = micGranted,
      isLocationGranted = locationGranted
    )
  }

  var status by remember { mutableStateOf(checkCurrentPermissions()) }

  val allPermissionsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissionsMap ->
    val mic = permissionsMap[Manifest.permission.RECORD_AUDIO]
      ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)

    val fineLoc = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
    val coarseLoc = permissionsMap[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
    val location = fineLoc || coarseLoc || (
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    )

    status = PermissionStatus(
      isMicrophoneGranted = mic,
      isLocationGranted = location,
      shouldShowRationale = !mic || !location
    )
    onPermissionsUpdated?.invoke(status)
  }

  val micOnlyLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    status = status.copy(isMicrophoneGranted = isGranted)
    onPermissionsUpdated?.invoke(status)
  }

  val locationOnlyLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    status = status.copy(isLocationGranted = granted)
    onPermissionsUpdated?.invoke(status)
  }

  LaunchedEffect(Unit) {
    status = checkCurrentPermissions()
    onPermissionsUpdated?.invoke(status)
  }

  return remember(status) {
    PermissionManager(
      status = status,
      requestAllLauncher = {
        allPermissionsLauncher.launch(
          arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      },
      requestMicrophoneLauncher = {
        micOnlyLauncher.launch(Manifest.permission.RECORD_AUDIO)
      },
      requestLocationLauncher = {
        locationOnlyLauncher.launch(
          arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
          )
        )
      },
      openAppSettings = {
        try {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(intent)
        } catch (_: Exception) {}
      }
    )
  }
}

/**
 * UI Banner displaying permission status and direct action button to grant missing permissions.
 */
@Composable
fun PermissionVerificationBanner(
  permissionManager: PermissionManager,
  modifier: Modifier = Modifier
) {
  val status = permissionManager.status
  if (status.hasAllEssentialPermissions) return

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF1E293B))
      .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
      .padding(12.dp)
      .testTag("permission_verification_banner")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Warning",
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "ခွင့်ပြုချက်များ လိုအပ်နေပါသည် (Permissions Required)",
            color = Color(0xFFF59E0B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Button(
          onClick = { permissionManager.requestAllPermissions() },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.testTag("grant_permissions_btn")
        ) {
          Text("ခွင့်ပြုမည်", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        PermissionIndicatorChip(
          name = "မိုက်ခရိုဖုန်း (Microphone)",
          isGranted = status.isMicrophoneGranted
        )
        PermissionIndicatorChip(
          name = "တည်နေရာ (Location)",
          isGranted = status.isLocationGranted
        )
      }
    }
  }
}

@Composable
private fun PermissionIndicatorChip(name: String, isGranted: Boolean) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(if (isGranted) Color(0x2210B981) else Color(0x22EF4444))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444))
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = name,
      fontSize = 10.sp,
      color = if (isGranted) Color(0xFF10B981) else Color(0xFFFCA5A5),
      fontWeight = FontWeight.Medium
    )
  }
}
