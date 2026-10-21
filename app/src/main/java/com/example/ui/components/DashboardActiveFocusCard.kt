package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusCategory
import com.example.service.PomodoroForegroundService
import com.example.ui.theme.*
import java.util.Locale

/**
 * DASHBOARD ACTIVE FOCUS SESSION CARD
 * Real-time integration with PomodoroForegroundService:
 * 1. Live Countdown with CircularProgressIndicator
 * 2. Play / Pause / Resume / Stop / +5 Mins Notification & Service Sync
 * 3. Quick Presets: 25m Focus, 50m Deep Work, 5m Break, 15m Long Break
 * 4. Category Selector (Work, Study, Personal, Health, Creative)
 * 5. Foreground Service & Partial WakeLock indicator
 */
@Composable
fun DashboardActiveFocusCard(
    onNavigateToFullFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceState by PomodoroForegroundService.serviceState.collectAsState()

    var selectedCategory by remember { mutableStateOf(FocusCategory.WORK) }
    var selectedPresetMinutes by remember { mutableIntStateOf(25) }

    var isBatteryExempt by remember { mutableStateOf(PomodoroForegroundService.isIgnoringBatteryOptimizations(context)) }

    val isRunning = serviceState.isRunning
    val isPaused = serviceState.isPaused
    val remainingSecs = if (isRunning) serviceState.remainingSeconds else selectedPresetMinutes * 60
    val totalSecs = if (isRunning) serviceState.totalSeconds else selectedPresetMinutes * 60

    val minutes = remainingSecs / 60
    val seconds = remainingSecs % 60
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    val rawProgress = if (totalSecs > 0) (totalSecs - remainingSecs).toFloat() / totalSecs.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = rawProgress, label = "timerProgress")

    val activeCategoryColor = remember(serviceState.colorHex, selectedCategory) {
        if (isRunning) {
            try {
                Color(android.graphics.Color.parseColor(serviceState.colorHex))
            } catch (_: Exception) {
                Color(0xFF8B5CF6)
            }
        } else {
            try {
                Color(android.graphics.Color.parseColor(selectedCategory.colorHex))
            } catch (_: Exception) {
                Color(0xFF8B5CF6)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (isRunning && !isPaused) activeCategoryColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp)
            )
            .testTag("dashboard_active_focus_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with Live Status Tag & Navigation Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = activeCategoryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Focus Session & Timer",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isRunning && !isPaused) Color(0xFF10B981).copy(alpha = 0.2f)
                                else if (isRunning && isPaused) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning && !isPaused) Color(0xFF10B981)
                                        else if (isRunning && isPaused) Color(0xFFF59E0B)
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRunning && !isPaused) "RUNNING"
                                       else if (isRunning && isPaused) "PAUSED"
                                       else "READY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning && !isPaused) Color(0xFF10B981)
                                        else if (isRunning && isPaused) Color(0xFFF59E0B)
                                        else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    TextButton(
                        onClick = onNavigateToFullFocus,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("အသေးစိတ် >", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Timer Center Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Circular Timer Indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        strokeWidth = 7.dp
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = activeCategoryColor,
                        strokeWidth = 7.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formattedTime,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRunning) serviceState.category else selectedCategory.displayName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeCategoryColor
                        )
                    }
                }

                // Controls & Actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isRunning) {
                        // Presets Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(25 to "25m", 50 to "50m", 5 to "5m", 15 to "15m").forEach { (mins, label) ->
                                val isSelected = selectedPresetMinutes == mins
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedPresetMinutes = mins }
                                        .testTag("preset_chip_$mins")
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Start Button
                        Button(
                            onClick = {
                                PomodoroForegroundService.startTimer(
                                    context = context,
                                    sessionName = "${selectedCategory.displayName} Focus (${selectedPresetMinutes}m)",
                                    totalSeconds = selectedPresetMinutes * 60,
                                    categoryName = selectedCategory.displayName,
                                    colorHex = selectedCategory.colorHex,
                                    remainingSeconds = selectedPresetMinutes * 60
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("start_focus_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = activeCategoryColor)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("စတင်အာရုံစိုက်မည် ($selectedPresetMinutes မိနစ်)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // In-Progress Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pause / Resume Button
                            FilledTonalButton(
                                onClick = {
                                    if (isPaused) {
                                        PomodoroForegroundService.resumeTimer(context)
                                    } else {
                                        PomodoroForegroundService.pauseTimer(context)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("toggle_focus_timer_button")
                            ) {
                                Icon(
                                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp)
                            }

                            // +5 Mins Extension
                            OutlinedButton(
                                onClick = {
                                    PomodoroForegroundService.addTime(context, 300)
                                },
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("add_5min_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+5m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Stop Button
                            Button(
                                onClick = {
                                    PomodoroForegroundService.stopTimer(context)
                                },
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("stop_focus_timer_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isBatteryExempt) {
                                        PomodoroForegroundService.requestIgnoreBatteryOptimizations(context)
                                        isBatteryExempt = PomodoroForegroundService.isIgnoringBatteryOptimizations(context)
                                    }
                                }
                        ) {
                            Text(
                                text = if (isBatteryExempt) "⚡ Deep Sleep Protected (Unrestricted)"
                                       else "⚠️ Battery Saver: Tap to allow deep sleep",
                                fontSize = 9.sp,
                                color = if (isBatteryExempt) Color(0xFF10B981) else Color(0xFFF59E0B),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            // Category Selector Chips (if not running)
            if (!isRunning) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(FocusCategory.entries) { cat ->
                        val isSelected = selectedCategory == cat
                        val catColor = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) catColor else Color.Transparent),
                            modifier = Modifier
                                .clickable { selectedCategory = cat }
                                .testTag("focus_cat_chip_${cat.name}")
                        ) {
                            Text(
                                text = cat.displayName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
