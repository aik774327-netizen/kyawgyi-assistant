package com.example.ui.components

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.GlobalAudioStateManager
import com.example.data.FocusCategory
import com.example.data.FocusSessionEntity
import com.example.service.PomodoroForegroundService
import com.example.ui.theme.*
import com.example.utils.PomodoroWorkScheduler
import kotlinx.coroutines.delay

/**
 * DeviceClockFocusTimerComponent
 *
 * Precision focus session timer that:
 * 1. Uses the device clock (SystemClock.elapsedRealtime) to guarantee zero timing drift.
 * 2. Features an animated circular radial progress indicator with dynamic gradient arcs.
 * 3. Automatically PAUSES when the microphone detects the 'ကျော်ကြီး' wake word,
 *    displaying a visual pause notification banner with quick resume affordance.
 * 4. Logs completed focus sessions into the Room database.
 */
@Composable
fun DeviceClockFocusTimerComponent(
    focusSessions: List<FocusSessionEntity>,
    onSaveFocusSession: (sessionName: String, durationMinutes: Int, notes: String, category: String, colorHex: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Presets in minutes
    val presets = listOf(15, 25, 45, 60)
    var selectedPresetMinutes by remember { mutableIntStateOf(25) }

    var totalDurationSeconds by remember { mutableIntStateOf(25 * 60) }
    var remainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isRunning by remember { mutableStateOf(false) }
    var pausedByWakeWord by remember { mutableStateOf(false) }

    // Device Clock anchor (drift-free timestamp tracking)
    var targetEndTimeRealtimeMs by remember { mutableLongStateOf(0L) }
    var sessionStartTimeEpochMs by remember { mutableLongStateOf(0L) }

    var selectedCategory by remember { mutableStateOf(FocusCategory.WORK) }
    var sessionName by remember { mutableStateOf("အာရုံစိုက်ချိန် (Focus Session)") }

    // Observe GlobalAudioStateManager for the 'ကျော်ကြီး' wake word detection
    val globalAudioStateManager = remember { GlobalAudioStateManager.getInstance(context) }
    val audioState by globalAudioStateManager.audioState.collectAsState()

    var lastHandledInterruptionTimestamp by remember { mutableLongStateOf(0L) }

    // React immediately when the microphone detects the 'ကျော်ကြီး' wake word
    LaunchedEffect(audioState.lastInterruptionTimestamp, audioState.isAudioPausedByWakeWord) {
        if (audioState.lastInterruptionTimestamp > lastHandledInterruptionTimestamp) {
            lastHandledInterruptionTimestamp = audioState.lastInterruptionTimestamp
            if (isRunning) {
                // Read current device clock to snapshot exact remaining time
                val now = SystemClock.elapsedRealtime()
                remainingSeconds = maxOf(0, ((targetEndTimeRealtimeMs - now + 999) / 1000).toInt())
                isRunning = false
                pausedByWakeWord = true

                // Update notification / foreground service
                PomodoroForegroundService.stopTimer(context)
                PomodoroWorkScheduler.cancelCompletionAlert(context)

                Toast.makeText(
                    context,
                    "🎤 'ကျော်ကြီး' အသံလှိုင်းကြောင့် Focus Timer ခေတ္တရပ်တန့်ထားပါသည်",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // High-precision countdown loop anchored against device clock
    LaunchedEffect(isRunning, targetEndTimeRealtimeMs) {
        if (isRunning && remainingSeconds > 0) {
            PomodoroForegroundService.startTimer(
                context = context,
                sessionName = sessionName.ifBlank { "${selectedCategory.displayName} Focus" },
                totalSeconds = totalDurationSeconds,
                categoryName = selectedCategory.displayName,
                colorHex = selectedCategory.colorHex,
                remainingSeconds = remainingSeconds
            )

            PomodoroWorkScheduler.scheduleCompletionAlert(
                context = context,
                remainingSeconds = remainingSeconds.toLong(),
                sessionName = sessionName.ifBlank { "${selectedCategory.displayName} Focus" },
                category = selectedCategory.displayName,
                durationMinutes = maxOf(1, totalDurationSeconds / 60)
            )

            while (isRunning && remainingSeconds > 0) {
                delay(200L) // frequent tick for smooth UI, actual time computed from device clock
                val now = SystemClock.elapsedRealtime()
                val diffSeconds = ((targetEndTimeRealtimeMs - now + 999) / 1000).toInt()
                if (diffSeconds <= 0) {
                    remainingSeconds = 0
                    isRunning = false
                    pausedByWakeWord = false

                    // Auto-save completed session to Room Database
                    val completedMinutes = maxOf(1, totalDurationSeconds / 60)
                    onSaveFocusSession(
                        sessionName.ifBlank { "${selectedCategory.displayName} Focus Session" },
                        completedMinutes,
                        "Device-clock focus session completed successfully.",
                        selectedCategory.displayName,
                        selectedCategory.colorHex
                    )

                    PomodoroForegroundService.stopTimer(context)
                    PomodoroWorkScheduler.cancelCompletionAlert(context)
                    Toast.makeText(context, "🎉 Focus Session ပြီးမြောက်ပါပြီ!", Toast.LENGTH_LONG).show()
                    break
                } else {
                    remainingSeconds = diffSeconds
                }
            }
        }
    }

    // Circular progress calculations
    val progressFraction = if (totalDurationSeconds > 0) {
        ((totalDurationSeconds - remainingSeconds).toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "circularProgress"
    )

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val categoryColor = remember(selectedCategory) {
        try {
            Color(android.graphics.Color.parseColor(selectedCategory.colorHex))
        } catch (e: Exception) {
            Color(0xFF38BDF8)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_clock_focus_timer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                listOf(categoryColor.copy(alpha = 0.6f), Color(0xFF1E293B).copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Focus Timer",
                            tint = categoryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Precision Focus Timer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Device Clock Synced • 'ကျော်ကြီး' Auto-Pause",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                val badgeBgColor: Color = if (isRunning) AngelNeonGreen.copy(alpha = 0.15f)
                else if (pausedByWakeWord) AngelOrangeAccent.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.08f)

                val badgeBorderStartColor: Color = if (isRunning) AngelNeonGreen
                else if (pausedByWakeWord) AngelOrangeAccent
                else Color.White.copy(alpha = 0.3f)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeBgColor,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(
                                badgeBorderStartColor,
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    Text(
                        text = if (isRunning) "RUNNING" else if (pausedByWakeWord) "PAUSED BY WAKE WORD" else "READY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isRunning) AngelNeonGreen else if (pausedByWakeWord) AngelOrangeAccent else AngelTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Wake-word Auto-Pause Alert Banner
            AnimatedVisibility(visible = pausedByWakeWord) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("wake_word_pause_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D1B1B),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(AngelOrangeAccent.copy(alpha = 0.8f), AngelOrangeAccent.copy(alpha = 0.3f))
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic Triggered",
                                tint = AngelOrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "မိုက်ခရိုဖုန်းမှ 'ကျော်ကြီး' ကို ကြားသိရသဖြင့် ခေတ္တရပ်တန့်ထားပါသည်",
                                fontSize = 11.sp,
                                color = Color.White,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                pausedByWakeWord = false
                                targetEndTimeRealtimeMs = SystemClock.elapsedRealtime() + (remainingSeconds * 1000L)
                                isRunning = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("resume_wake_timer_btn")
                        ) {
                            Text("ဆက်လုပ်ရန်", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            // Circular Progress Indicator using Jetpack Compose Canvas
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .testTag("circular_focus_progress_indicator"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension
                    val topLeft = Offset(
                        (size.width - diameter) / 2f + strokeWidth / 2f,
                        (size.height - diameter) / 2f + strokeWidth / 2f
                    )
                    val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)

                    // Background Track
                    drawArc(
                        color = Color(0xFF1F2937),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Circular Countdown Progress Arc
                    val sweepAngle = animatedProgress * 360f
                    if (sweepAngle > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(categoryColor.copy(alpha = 0.7f), categoryColor, categoryColor)
                            ),
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center Digital Time & Status
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeFormatted,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.testTag("device_timer_countdown_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = selectedCategory.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = categoryColor
                    )
                    Text(
                        text = if (isRunning) "Device Clock Active" else if (pausedByWakeWord) "Paused" else "Tap Start to Begin",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration Preset Chips (15m, 25m, 45m, 60m)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.forEach { presetMins ->
                    val isSelected = selectedPresetMinutes == presetMins
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) categoryColor else Color(0xFF1F2937),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRunning) {
                                selectedPresetMinutes = presetMins
                                totalDurationSeconds = presetMins * 60
                                remainingSeconds = presetMins * 60
                                pausedByWakeWord = false
                            }
                            .testTag("preset_${presetMins}m_btn")
                    ) {
                        Text(
                            text = "${presetMins}m",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FocusCategory.entries) { cat ->
                    val isSelected = selectedCategory == cat
                    val catCol = try {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    } catch (e: Exception) {
                        Color.Gray
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) catCol.copy(alpha = 0.25f) else Color(0xFF1F2937),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) catCol else Color.Transparent
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isRunning) {
                                selectedCategory = cat
                            }
                    ) {
                        Text(
                            text = cat.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) catCol else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controls: Start / Pause / Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action Button (Start / Pause / Resume)
                Button(
                    onClick = {
                        if (isRunning) {
                            // Pause
                            val now = SystemClock.elapsedRealtime()
                            remainingSeconds = maxOf(0, ((targetEndTimeRealtimeMs - now + 999) / 1000).toInt())
                            isRunning = false
                            PomodoroForegroundService.stopTimer(context)
                            PomodoroWorkScheduler.cancelCompletionAlert(context)
                        } else {
                            // Start or Resume using device clock
                            pausedByWakeWord = false
                            sessionStartTimeEpochMs = System.currentTimeMillis()
                            targetEndTimeRealtimeMs = SystemClock.elapsedRealtime() + (remainingSeconds * 1000L)
                            isRunning = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) AngelOrangeAccent else categoryColor
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("focus_timer_primary_btn")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "ခေတ္တရပ်မည် (Pause)" else if (remainingSeconds < totalDurationSeconds) "ဆက်လုပ်မည် (Resume)" else "စတင်မည် (Start)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                // Reset Button
                OutlinedButton(
                    onClick = {
                        isRunning = false
                        pausedByWakeWord = false
                        targetEndTimeRealtimeMs = 0L
                        remainingSeconds = totalDurationSeconds
                        PomodoroForegroundService.stopTimer(context)
                        PomodoroWorkScheduler.cancelCompletionAlert(context)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("focus_timer_reset_btn")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ပြန်စမည်", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
