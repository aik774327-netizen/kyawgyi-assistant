package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.OneClickSystemDiagnosticEngine
import com.example.router.DirectSettingsRouter
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.OverlayPermissionHelper
import kotlinx.coroutines.launch

/**
 * QuickActionCenterCard
 *
 * Provides a dedicated, high-visibility 1-Tap Action Center for:
 * 1. "Permission တောင်းဆိုမည့် ခလုတ်" (Request & Grant All Permissions)
 * 2. "ခလုတ်တစ်ချက်နှိပ်ပြီး Error အားလုံး စစ်ဆေးမည့် စနစ်" (1-Click Full System Diagnostics)
 */
@Composable
fun QuickActionCenterCard(
    onOpenFullDiagnosticsModal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val diagnosticEngine = remember { OneClickSystemDiagnosticEngine.getInstance(context) }
    val diagnosticReport by diagnosticEngine.reportFlow.collectAsState()

    var permissionsList by remember {
        mutableStateOf(KyawGyiPermissionManager.getAllPermissionsStatus(context))
    }
    val grantedCount = permissionsList.count { it.isGranted }
    val totalCount = permissionsList.size
    val isAllGranted = grantedCount == totalCount

    // Refresh permission statuses on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Direct Runtime Permission Launcher for all needed permissions
    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
        val allOk = results.values.all { it }
        if (allOk) {
            Toast.makeText(context, "ခွင့်ပြုချက်များ အားလုံး အောင်မြင်စွာ ရရှိပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ခွင့်ပြုချက် အချို့ လိုအပ်နေဆဲဖြစ်ပါသည်", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_action_center_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.5.dp,
            if (diagnosticReport.isRunning) AngelCyanAccent.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = com.example.R.drawable.ic_kyaw_gyi_logo),
                        contentDescription = "Kyaw Gyi AI Voice Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "Kyaw Gyi AI Voice (ကျော်ကြီး)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "စနစ် ထိန်းချုပ်ရေးနှင့် စစ်ဆေးရေး ဗဟို (Control Hub)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================
            // ACTION 1: PERMISSION REQUEST BUTTON SECTION
            // =========================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isAllGranted) AngelNeonGreen.copy(alpha = 0.5f)
                    else Color(0xFFFFB74D).copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllGranted) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isAllGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ခွင့်ပြုချက် (Permissions) အခြေအနေ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAllGranted) AngelNeonGreen.copy(alpha = 0.2f) else Color(0xFFFFB74D).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (isAllGranted) "$grantedCount/$totalCount ရရှိပြီး ✓" else "$grantedCount/$totalCount ရရှိထား",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAllGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAllGranted) {
                            "မိုက်ခရိုဖုန်း၊ ဖုန်းခေါ်ဆိုမှု၊ အသိပေးချက်များနှင့် စနစ်ခွင့်ပြုချက်များ အပြည့်အစုံ ရရှိထားပါသည်။"
                        } else {
                            "မိုက်ခရိုဖုန်း၊ ဖုန်းခေါ်၊ မက်ဆေ့ခ်ျ၊ ဘလူးတုသ်နှင့် Floating Window ခွင့်ပြုချက်များ လိုအပ်နေပါသည်။"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Direct Permission Request Button
                        Button(
                            onClick = {
                                val required = KyawGyiPermissionManager.getRequiredPermissions()
                                multiPermissionLauncher.launch(required)

                                // Also handle overlay if not granted
                                if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                                    OverlayPermissionHelper.requestOverlayPermission(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAllGranted) AngelNeonGreen else MaterialTheme.colorScheme.primary,
                                contentColor = if (isAllGranted) Color.Black else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("grant_all_permissions_hub_btn")
                        ) {
                            Icon(
                                imageVector = if (isAllGranted) Icons.Default.DoneAll else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAllGranted) "ခွင့်ပြုချက်များ စစ်ဆေးပြီး ✓" else "Permission တောင်းဆိုမည်",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // App Settings Direct Shortcut Button
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("open_system_settings_hub_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ဆက်တင်", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================
            // ACTION 2: ONE-CLICK FULL ERROR DIAGNOSTICS BUTTON SECTION
            // =========================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (diagnosticReport.isComplete && diagnosticReport.failedCount == 0) AngelNeonGreen.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "စနစ်တစ်ခုလုံး Error စစ်ဆေးမှု (1-Click)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (diagnosticReport.isComplete) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (diagnosticReport.failedCount == 0) AngelNeonGreen.copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Score: ${diagnosticReport.healthScore}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diagnosticReport.failedCount == 0) AngelNeonGreen else Color(0xFFFF5252),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (diagnosticReport.isRunning) {
                            "စနစ် အစိတ်အပိုင်း (၁၀) ခုကို အမှန်တကယ် စစ်ဆေးနေပါသည်..."
                        } else if (diagnosticReport.isComplete) {
                            "အောင်မြင်: ${diagnosticReport.passedCount} ခု | သတိပေးချက်: ${diagnosticReport.warningCount} ခု | အမှား: ${diagnosticReport.failedCount} ခု"
                        } else {
                            "မိုက်ခရိုဖုန်း၊ မြန်မာ NLU၊ TTS၊ Database၊ ဟာ့ဒ်ဝဲ၊ BLE နှင့် Cloud AI စနစ် ၁၀ ခုလုံးကို စစ်ဆေးပေးမည်။"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Progress indicator if running
                    if (diagnosticReport.isRunning) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { diagnosticReport.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Big One-Click Diagnostic Scan Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    diagnosticEngine.runFullDiagnostic()
                                }
                            },
                            enabled = !diagnosticReport.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("run_full_system_diagnostic_hub_btn")
                        ) {
                            Icon(
                                imageVector = if (diagnosticReport.isRunning) Icons.Default.HourglassTop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (diagnosticReport.isRunning) "စစ်ဆေးနေပါသည်..."
                                else if (diagnosticReport.isComplete) "ပြန်လည် စစ်ဆေးမည် (Scan Again)"
                                else "ခလုတ်တစ်ချက်နှိပ်ပြီး Error အားလုံး စစ်ဆေးမည်",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Full Report Modal Button
                        OutlinedButton(
                            onClick = onOpenFullDiagnosticsModal,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("open_diagnostic_report_modal_btn")
                        ) {
                            Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("အစီရင်ခံစာ", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
