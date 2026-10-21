package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diagnostics.*
import com.example.router.DirectSettingsRouter
import com.example.ui.theme.*
import com.example.utils.MyanmarTTSEngine
import kotlinx.coroutines.launch

/**
 * OneClickSystemDiagnosticsCard
 *
 * Prominent Dashboard Card allowing users to test the entire project architecture
 * with a single click, showing live inspection status, subsystem health, and error fixes.
 */
@Composable
fun OneClickSystemDiagnosticsCard(
    modifier: Modifier = Modifier,
    onOpenFullDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { OneClickSystemDiagnosticEngine.getInstance(context) }
    val report by engine.reportFlow.collectAsState()
    var showDetailsDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("one_click_system_diagnostics_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (report.isRunning) AngelCyanAccent.copy(alpha = pulseAlpha)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (report.isRunning) Icons.Default.Sync else Icons.Default.FactCheck,
                                contentDescription = "Diagnostics",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .then(
                                        if (report.isRunning) {
                                            val rotation by infiniteTransition.animateFloat(
                                                initialValue = 0f,
                                                targetValue = 360f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1200, easing = LinearEasing),
                                                    repeatMode = RepeatMode.Restart
                                                ),
                                                label = "rotate"
                                            )
                                            Modifier.rotate(rotation)
                                        } else Modifier
                                    )
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "စနစ်တစ်ခုလုံး Error စစ်ဆေးမှု",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "1-Click Full System Diagnostics",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (report.isComplete) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (report.healthScore >= 80) AngelNeonGreen.copy(alpha = 0.15f)
                        else if (report.healthScore >= 50) Color(0xFFFFB74D).copy(alpha = 0.15f)
                        else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${report.healthScore}% ကျန်းမာမှု",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (report.healthScore >= 80) AngelNeonGreen
                            else if (report.healthScore >= 50) Color(0xFFFFB74D)
                            else Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Running State Progress Bar & Step Text
            if (report.isRunning) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = report.currentStepMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(report.progressPercent * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { report.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (report.isComplete) {
                // Completed Quick Summary Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.summaryMM,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "အောင်မြင်: ${report.passedCount} ခု | သတိပေးချက်: ${report.warningCount} ခု | အမှား: ${report.failedCount} ခု",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { showDetailsDialog = true },
                            modifier = Modifier.testTag("view_diagnostic_details_btn")
                        ) {
                            Text("အသေးစိတ်", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Big 1-Click Action Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        engine.runFullDiagnostic()
                    }
                },
                enabled = !report.isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("run_one_click_diagnostic_btn")
            ) {
                Icon(
                    imageVector = if (report.isRunning) Icons.Default.HourglassTop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (report.isRunning) "စနစ်ကို စစ်ဆေးနေပါသည်..."
                    else if (report.isComplete) "စနစ်တစ်ခုလုံး ပြန်လည် စစ်ဆေးမည် (Re-check All)"
                    else "ခလုတ်တစ်ချက်နှိပ်ပြီး Error အားလုံး စစ်ဆေးမည် (Start Scan)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDetailsDialog) {
        OneClickDiagnosticsModal(
            report = report,
            onDismiss = { showDetailsDialog = false },
            onReRun = {
                coroutineScope.launch {
                    engine.runFullDiagnostic()
                }
            }
        )
    }
}

/**
 * OneClickDiagnosticsModal
 *
 * Full detailed dialog presenting all 10 checked subsystems, latency, error reasons,
 * direct 1-tap repair buttons, and full report copy capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickDiagnosticsModal(
    report: DiagnosticReport,
    onDismiss: () -> Unit,
    onReRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val engine = remember { OneClickSystemDiagnosticEngine.getInstance(context) }
    val settingsRouter = remember { DirectSettingsRouter.getInstance(context) }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0: All, 1: Errors, 2: Warnings, 3: Passed

    val filteredResults = remember(report.results, selectedFilter) {
        when (selectedFilter) {
            1 -> report.results.filter { it.status == DiagnosticStatus.FAIL }
            2 -> report.results.filter { it.status == DiagnosticStatus.WARNING }
            3 -> report.results.filter { it.status == DiagnosticStatus.PASS }
            else -> report.results
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    RoundedCornerShape(24.dp)
                )
                .testTag("one_click_diagnostics_modal"),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header Row
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
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "စနစ် အလုံးစုံ စစ်ဆေးမှု ရလဒ်",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Subsystem Health & Error Inspection",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diag_modal_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Score & Health Gauge Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ကျန်းမာရေး ရမှတ်: ${report.healthScore}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (report.healthScore >= 80) AngelNeonGreen
                                else if (report.healthScore >= 50) Color(0xFFFFB74D)
                                else Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = report.summaryMM,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalIconButton(
                                onClick = {
                                    val reportText = engine.generateDiagnosticReportText()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Kyaw Gyi Diagnostic Report", reportText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "အစီရင်ခံစာ ကော်ပီကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_diagnostic_report_btn")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", modifier = Modifier.size(18.dp))
                            }

                            FilledTonalIconButton(
                                onClick = onReRun,
                                modifier = Modifier.testTag("rerun_diagnostic_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-check", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == 0,
                            onClick = { selectedFilter = 0 },
                            label = { Text("အားလုံး (${report.results.size})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == 1,
                            onClick = { selectedFilter = 1 },
                            label = { Text("ချို့ယွင်းချက် (${report.failedCount})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == 2,
                            onClick = { selectedFilter = 2 },
                            label = { Text("သတိပေးချက် (${report.warningCount})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == 3,
                            onClick = { selectedFilter = 3 },
                            label = { Text("အောင်မြင် (${report.passedCount})", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Check Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredResults) { item ->
                        DiagnosticItemCard(
                            item = item,
                            onFixAction = {
                                when (item.remediationType) {
                                    RemediationType.OPEN_SETTINGS -> {
                                        item.settingsDestination?.let { dest ->
                                            settingsRouter.openSettings(dest)
                                        }
                                    }
                                    RemediationType.AUTO_FIX -> {
                                        if (item.id == "tts_disabled_warning") {
                                            MyanmarTTSEngine.masterTtsEnabled = true
                                            Toast.makeText(context, "TTS အသံထွက်စနစ် ဖွင့်လှစ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                                            onReRun()
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Dismiss Action
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("ပိတ်မည် (Close)")
                }
            }
        }
    }
}

/**
 * DiagnosticItemCard
 * Individual card displaying check status, latency, Myanmar description, and repair button.
 */
@Composable
private fun DiagnosticItemCard(
    item: DiagnosticCheckResult,
    onFixAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (item.status) {
        DiagnosticStatus.PASS -> AngelNeonGreen
        DiagnosticStatus.WARNING -> Color(0xFFFFB74D)
        DiagnosticStatus.FAIL -> Color(0xFFEF4444)
    }

    val statusIcon = when (item.status) {
        DiagnosticStatus.PASS -> Icons.Default.CheckCircle
        DiagnosticStatus.WARNING -> Icons.Default.Warning
        DiagnosticStatus.FAIL -> Icons.Default.Cancel
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diag_item_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )

                    Column {
                        Text(
                            text = item.titleMM,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.descriptionMM,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${item.latencyMs}ms",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.detailsMM,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )

            // Direct Fix / Resolve Action Button if applicable
            if (item.remediationType != RemediationType.NONE && item.status != DiagnosticStatus.PASS) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onFixAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusColor.copy(alpha = 0.2f),
                        contentColor = statusColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("fix_action_${item.id}")
                ) {
                    Icon(
                        imageVector = when (item.remediationType) {
                            RemediationType.AUTO_FIX -> Icons.Default.AutoFixHigh
                            else -> Icons.Default.Settings
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (item.remediationType) {
                            RemediationType.AUTO_FIX -> "အလိုအလျောက် ပြင်မည်"
                            else -> "ဆက်တင်တွင် ဖွင့်ရန်"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
