package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.ui.theme.*

/**
 * Diagnostic Health Item representing a hardware/software capability check.
 */
data class SystemHealthCheckItem(
    val id: String,
    val nameMM: String,
    val descriptionMM: String,
    val destination: SettingsDestination,
    val isCrucial: Boolean = false
)

/**
 * Unified Diagnostics Overlay - Comprehensive hardware, permissions, and network
 * diagnostic tool that runs full A-to-Z checks and offers instant 1-tap direct repairs.
 */
@Composable
fun UnifiedDiagnosticsOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val router = remember { DirectSettingsRouter.getInstance(context) }

    val checks = remember {
        listOf(
            SystemHealthCheckItem(
                id = "audio_record",
                nameMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် (RECORD_AUDIO)",
                descriptionMM = "အသံဖမ်းယူမှုနှင့် စကားသံအမိန့်များ နားထောင်ရန် မဖြစ်မနေ လိုအပ်ပါသည်",
                destination = SettingsDestination.APPLICATION_DETAILS,
                isCrucial = true
            ),
            SystemHealthCheckItem(
                id = "overlay_window",
                nameMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (SYSTEM_ALERT_WINDOW)",
                descriptionMM = "အခြားအက်ပ်များ သုံးစွဲနေစဉ် Floating Voice Ball ပြသနိုင်ရန်",
                destination = SettingsDestination.OVERLAY_PERMISSION,
                isCrucial = true
            ),
            SystemHealthCheckItem(
                id = "accessibility_service",
                nameMM = "စနစ်တွင်း အလိုအလျောက် ထိန်းချုပ်ခွင့် (Accessibility)",
                descriptionMM = "Wi-Fi / Bluetooth ခလုတ်များ အလိုအလျောက် နှိပ်ခြင်းနှင့် Back/Home ထိန်းချုပ်ရန်",
                destination = SettingsDestination.ACCESSIBILITY,
                isCrucial = true
            ),
            SystemHealthCheckItem(
                id = "battery_doze",
                nameMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် (Battery Optimization)",
                descriptionMM = "Screen ပိတ်ထားစဉ် Doze Mode ကြောင့် Voice Service အသတ်မခံရစေရန်",
                destination = SettingsDestination.BATTERY_OPTIMIZATION,
                isCrucial = true
            ),
            SystemHealthCheckItem(
                id = "oem_protection",
                nameMM = "OEM Auto-Start နှင့် Background စောင့်ရှောက်မှု",
                descriptionMM = "Xiaomi/Samsung/Oppo/Vivo များတွင် Voice Service အမြဲနိုးကြားနေစေရန်",
                destination = SettingsDestination.OEM_AUTOSTART
            ),
            SystemHealthCheckItem(
                id = "write_settings",
                nameMM = "စနစ် ဆက်တင်များ ပြင်ဆင်ခွင့် (WRITE_SETTINGS)",
                descriptionMM = "မျက်နှာပြင် အလင်းအမှောင်နှင့် စနစ်အသံ အတိုးအကျယ်များ ထိန်းချုပ်ရန်",
                destination = SettingsDestination.WRITE_SETTINGS
            ),
            SystemHealthCheckItem(
                id = "dnd_policy",
                nameMM = "အနှောင့်အယှက် မပေးရ စနစ် (DND Access)",
                descriptionMM = "အသံတိတ် Mute စနစ်နှင့် စနစ်အသံ စည်းမျဉ်းများ ထိန်းချုပ်ရန်",
                destination = SettingsDestination.NOTIFICATION_POLICY
            ),
            SystemHealthCheckItem(
                id = "exact_alarms",
                nameMM = "တိကျသော Alarm & Reminders (SCHEDULE_EXACT_ALARM)",
                descriptionMM = "Pomodoro Timer နှင့် သတိပေးချက်များ အချိန်မှန် တိကျစွာ အလုပ်လုပ်ရန်",
                destination = SettingsDestination.EXACT_ALARM
            ),
            SystemHealthCheckItem(
                id = "notification_listener",
                nameMM = "အသိပေးချက်များ ဖတ်ရှုခွင့် (Notification Listener)",
                descriptionMM = "ဝင်လာသော မက်ဆေ့ခ်ျများနှင့် စာတိုများကို အသံဖြင့် အလိုအလျောက် ဖတ်ကြားရန်",
                destination = SettingsDestination.NOTIFICATION_LISTENER
            ),
            SystemHealthCheckItem(
                id = "myanmar_tts",
                nameMM = "မြန်မာစကား အသံထွက်စနစ် (Text-To-Speech)",
                descriptionMM = "Google TTS တွင် မြန်မာဘာသာ အသံဖိုင်ဒေါင်းလုဒ် ပြုလုပ်ထားရန်",
                destination = SettingsDestination.TTS_SETTINGS
            )
        )
    }

    // Dynamic state evaluation
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val results by produceState(initialValue = emptyMap<String, Boolean>(), key1 = refreshTrigger) {
        val map = mutableMapOf<String, Boolean>()
        checks.forEach { item ->
            map[item.id] = router.isCapabilityGranted(item.destination)
        }
        value = map
    }

    val passedCount = checks.count { results[it.id] == true }
    val totalCount = checks.size
    val healthPercent = if (totalCount > 0) (passedCount * 100 / totalCount) else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(1.dp, Brush.linearGradient(listOf(AngelCyanAccent, AngelNeonGreen))),
                    RoundedCornerShape(24.dp)
                )
                .testTag("unified_diagnostics_overlay"),
            color = AngelSurfaceDark
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AngelCyanAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = AngelCyanAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "စနစ် အလုံးစုံ စစ်ဆေးမှု (Diagnostics)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Hardware, OS Permissions & Settings",
                                fontSize = 11.sp,
                                color = AngelTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diagnostics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overall Score Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AngelCardDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "စနစ် အဆင်သင့်ဖြစ်မှု ရလဒ်",
                                fontSize = 13.sp,
                                color = AngelTextSecondary
                            )
                            Text(
                                text = "$passedCount / $totalCount ခု ပြီးပြည့်စုံပါသည် ($healthPercent%)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (healthPercent >= 80) AngelNeonGreen else Color(0xFFFFB74D)
                            )
                        }

                        Button(
                            onClick = { refreshTrigger++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelCyanAccent.copy(alpha = 0.2f),
                                contentColor = AngelCyanAccent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("refresh_diagnostics_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("စစ်ဆေးရန်", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Checklist Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(checks) { check ->
                        val isGranted = results[check.id] == true
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AngelCardDark,
                            border = BorderStroke(
                                1.dp,
                                if (isGranted) AngelNeonGreen.copy(alpha = 0.25f) else Color(0xFFEF4444).copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    router.openSettings(check.destination)
                                }
                                .testTag("diag_item_${check.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isGranted) AngelNeonGreen.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (isGranted) AngelNeonGreen else Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = check.nameMM,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = check.descriptionMM,
                                            fontSize = 11.sp,
                                            color = AngelTextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isGranted) AngelNeonGreen.copy(alpha = 0.15f) else AngelCyanAccent.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = if (isGranted) "အဆင်ပြေ ✓" else "ဖွင့်ရန်",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGranted) AngelNeonGreen else AngelCyanAccent
                                        )
                                        if (!isGranted) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = AngelCyanAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("finish_diagnostics_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelCyanAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "အတည်ပြုပြီး ထွက်ရန်",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
