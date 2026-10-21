package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusCategory
import com.example.data.FocusSessionEntity
import com.example.service.PomodoroForegroundService
import com.example.ui.theme.*
import com.example.utils.PomodoroWorkScheduler
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pomodoro Focus Timer with:
 * 1. Category Tag Selector (Work, Study, Personal, Health, Creative) with color-coded tags
 * 2. Animated Radial Progress Countdown Circle & Dynamic Progress Indicators
 * 3. Background WorkManager Notification Alert integration
 * 4. Start, Pause, Resume, and Stop / Reset Controls
 * 5. Session logging into Room Database upon completion or manual stop
 * 6. Focus Session Logs History List with category color badges and timestamps
 */
@Composable
fun PomodoroFocusTimerView(
    focusSessions: List<FocusSessionEntity>,
    onSaveFocusSession: (sessionName: String, durationMinutes: Int, notes: String, category: String, colorHex: String) -> Unit,
    onDeleteFocusSession: (FocusSessionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Timer durations in seconds
    var selectedPresetMinutes by remember { mutableIntStateOf(25) }
    var totalTimeInSeconds by remember { mutableIntStateOf(25 * 60) }
    var timeLeftInSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(FocusCategory.WORK) }
    var sessionName by remember { mutableStateOf("အာရုံစိုက်ချိန် (Focus Session)") }
    var sessionStartTimeMs by remember { mutableLongStateOf(0L) }
    var elapsedSecondsInSession by remember { mutableIntStateOf(0) }

    var isBatteryExempt by remember { mutableStateOf(PomodoroForegroundService.isIgnoringBatteryOptimizations(context)) }

    // Countdown Coroutine Loop + WorkManager background alerts + Foreground Service for deep sleep
    // Keyed purely on isRunning so we only initiate Service/Alarm once per start/resume, avoiding 60 intents/min
    LaunchedEffect(isRunning) {
        if (isRunning) {
            PomodoroForegroundService.startTimer(
                context = context,
                sessionName = sessionName.ifBlank { "${selectedCategory.displayName} Focus" },
                totalSeconds = totalTimeInSeconds,
                categoryName = selectedCategory.displayName,
                colorHex = selectedCategory.colorHex,
                remainingSeconds = timeLeftInSeconds
            )

            PomodoroWorkScheduler.scheduleCompletionAlert(
                context = context,
                remainingSeconds = timeLeftInSeconds.toLong(),
                sessionName = sessionName.ifBlank { "${selectedCategory.displayName} Focus" },
                category = selectedCategory.displayName,
                durationMinutes = maxOf(1, totalTimeInSeconds / 60)
            )

            while (isRunning && timeLeftInSeconds > 0) {
                delay(1000L)
                timeLeftInSeconds -= 1
                elapsedSecondsInSession += 1
            }

            if (timeLeftInSeconds <= 0) {
                // Completed Session automatically
                isRunning = false
                PomodoroForegroundService.stopTimer(context)
                val completedMinutes = maxOf(1, totalTimeInSeconds / 60)
                onSaveFocusSession(
                    sessionName,
                    completedMinutes,
                    "အောင်မြင်စွာ ပြီးမြောက်ခဲ့သည် (Auto-completed)",
                    selectedCategory.displayName,
                    selectedCategory.colorHex
                )
                Toast.makeText(context, "$sessionName [${selectedCategory.displayName}] ပြီးဆုံးပါပြီ။ Room DB တွင် သိမ်းဆည်းပြီးပါပြီ။", Toast.LENGTH_LONG).show()
                // Reset to preset
                timeLeftInSeconds = selectedPresetMinutes * 60
                totalTimeInSeconds = selectedPresetMinutes * 60
                elapsedSecondsInSession = 0
                PomodoroWorkScheduler.cancelCompletionAlert(context)
            }
        } else {
            PomodoroForegroundService.pauseTimer(context)
            PomodoroWorkScheduler.cancelCompletionAlert(context)
        }
    }

    val progress = if (totalTimeInSeconds > 0) {
        (totalTimeInSeconds - timeLeftInSeconds).toFloat() / totalTimeInSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "pomodoro_progress"
    )

    val minutes = timeLeftInSeconds / 60
    val seconds = timeLeftInSeconds % 60
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pomodoro_focus_timer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = selectedCategory.color.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Pomodoro Timer",
                                tint = selectedCategory.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Pomodoro Focus Timer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "WorkManager Alert • Room DB",
                            fontSize = 11.sp,
                            color = selectedCategory.color
                        )
                    }
                }

                // Preset selector pills (25m / 15m / 5m)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AngelCardDark)
                        .padding(2.dp)
                ) {
                    listOf(25 to "25m", 15 to "15m", 5 to "5m").forEach { (mins, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPresetMinutes == mins) selectedCategory.color else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!isRunning) {
                                        selectedPresetMinutes = mins
                                        totalTimeInSeconds = mins * 60
                                        timeLeftInSeconds = mins * 60
                                        elapsedSecondsInSession = 0
                                    }
                                }
                                .testTag("preset_${mins}_btn")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPresetMinutes == mins) Color.Black else AngelTextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category tag system selector
            Text(
                text = "CATEGORY TAGS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FocusCategory.entries) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) category.color else AngelCardDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = category }
                            .testTag("tag_pill_${category.id.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else category.color)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = category.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reusable Circular Focus Progress Indicator
            CircularFocusProgressIndicator(
                remainingSeconds = timeLeftInSeconds.toLong(),
                totalSeconds = totalTimeInSeconds.toLong(),
                isRunning = isRunning,
                modifier = Modifier.testTag("pomodoro_radial_circle"),
                size = 175.dp,
                strokeWidth = 10.dp,
                primaryColor = selectedCategory.color,
                secondaryColor = AngelNeonGreen,
                trackColor = Color.White.copy(alpha = 0.08f),
                categoryName = if (isRunning) "အာရုံစိုက်နေသည် (${selectedCategory.displayName})" else if (progress > 0f) "ခေတ္တရပ်တန့်ထားသည်" else "အသင့်ဖြစ်ပါပြီ"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Start / Pause & Stop / Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop / Save Session Button
                if (isRunning || elapsedSecondsInSession > 0) {
                    OutlinedButton(
                        onClick = {
                            val elapsedMinutes = maxOf(1, (elapsedSecondsInSession + 30) / 60)
                            onSaveFocusSession(
                                sessionName,
                                elapsedMinutes,
                                "ရပ်တန့်ပြီး မှတ်တမ်းတင်ခဲ့သည်",
                                selectedCategory.displayName,
                                selectedCategory.colorHex
                            )
                            Toast.makeText(context, "$elapsedMinutes မိနစ် Session [${selectedCategory.displayName}] ကို မှတ်တမ်းတင်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            isRunning = false
                            PomodoroForegroundService.stopTimer(context)
                            timeLeftInSeconds = selectedPresetMinutes * 60
                            totalTimeInSeconds = selectedPresetMinutes * 60
                            elapsedSecondsInSession = 0
                            PomodoroWorkScheduler.cancelCompletionAlert(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("stop_focus_session_btn")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ရပ်တန့် / မှတ်တမ်းတင်", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Primary Start / Pause Button
                Button(
                    onClick = {
                        if (!isRunning) {
                            if (sessionStartTimeMs == 0L) {
                                sessionStartTimeMs = System.currentTimeMillis()
                            }
                            isRunning = true
                        } else {
                            isRunning = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFFF9800) else selectedCategory.color
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("toggle_focus_timer_btn")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start",
                        tint = if (isRunning) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "ခေတ္တရပ် (Pause)" else "စတင်မည် (Start Focus)",
                        color = if (isRunning) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Battery Optimization & Deep Sleep Status Banner
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isBatteryExempt) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBatteryExempt) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isBatteryExempt) {
                            PomodoroForegroundService.requestIgnoreBatteryOptimizations(context)
                            isBatteryExempt = PomodoroForegroundService.isIgnoringBatteryOptimizations(context)
                        }
                    }
                    .testTag("pomodoro_battery_status_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = if (isBatteryExempt) "⚡ Deep-Sleep Protected (Unrestricted Background)"
                                   else "⚠️ Battery Restricted: Tap to allow deep-sleep focus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isBatteryExempt) Color(0xFF34D399) else Color(0xFFFBBF24),
                            maxLines = 1
                        )
                    }
                    if (!isBatteryExempt) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "Allow",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Session History Section
            if (focusSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ပြီးဆုံးခဲ့သော Focus မှတ်တမ်းများ (${focusSessions.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val totalFocusMinutes = focusSessions.sumOf { it.durationMinutes }
                    Text(
                        text = "စုစုပေါင်း: $totalFocusMinutes မိနစ်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelCyanAccent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    focusSessions.take(4).forEach { session ->
                        val cat = FocusCategory.fromString(session.category)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AngelCardDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("focus_session_log_${session.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(cat.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = session.sessionName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(session.timestamp))} • ${session.category}",
                                            fontSize = 10.sp,
                                            color = AngelTextSecondary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = cat.color.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "${session.durationMinutes} min",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cat.color,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteFocusSession(session) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
