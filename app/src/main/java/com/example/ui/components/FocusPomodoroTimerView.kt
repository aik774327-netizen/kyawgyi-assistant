package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import com.example.utils.PomodoroWorkScheduler
import kotlinx.coroutines.delay

/**
 * 25-MINUTE POMODORO FOCUS SESSION TIMER COMPONENT
 * Features:
 * 1. Category Tag Selector (Work, Study, Personal, Health, Creative) with color-coded tag system
 * 2. Background WorkManager Notification Alert integration
 * 3. Dynamic CircularProgressIndicator with smooth animations
 * 4. Room Database Session Persistence
 */
@Composable
fun FocusPomodoroTimerView(
    onSaveFocusSession: (sessionName: String, durationMinutes: Int, notes: String, category: String, colorHex: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val totalSeconds = 25 * 60 // 25 Minutes = 1500 Seconds
    var timeRemainingSeconds by remember { mutableIntStateOf(totalSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    var sessionTitle by remember { mutableStateOf("Deep Work Focus Session") }
    var selectedCategory by remember { mutableStateOf(FocusCategory.WORK) }
    var sessionSavedMessage by remember { mutableStateOf(false) }

    // Coroutine-driven 1-second countdown timer + Foreground Service (Deep Sleep) + WorkManager Alert
    LaunchedEffect(isRunning) {
        if (isRunning) {
            PomodoroForegroundService.startTimer(
                context = context,
                sessionName = sessionTitle.ifBlank { "${selectedCategory.displayName} Focus" },
                totalSeconds = totalSeconds,
                categoryName = selectedCategory.displayName,
                colorHex = selectedCategory.colorHex,
                remainingSeconds = timeRemainingSeconds
            )

            PomodoroWorkScheduler.scheduleCompletionAlert(
                context = context,
                remainingSeconds = timeRemainingSeconds.toLong(),
                sessionName = sessionTitle.ifBlank { "${selectedCategory.displayName} Focus" },
                category = selectedCategory.displayName,
                durationMinutes = 25
            )

            while (isRunning && timeRemainingSeconds > 0) {
                delay(1000L)
                timeRemainingSeconds--
            }
            if (timeRemainingSeconds <= 0) {
                isRunning = false
                PomodoroForegroundService.stopTimer(context)
                // Auto-save completed 25-minute Pomodoro session with category
                onSaveFocusSession(
                    sessionTitle.ifBlank { "${selectedCategory.displayName} 25m Focus" },
                    25,
                    "Completed 25-minute ${selectedCategory.displayName} Focus Session",
                    selectedCategory.displayName,
                    selectedCategory.colorHex
                )
                sessionSavedMessage = true
            }
        } else {
            PomodoroForegroundService.pauseTimer(context)
            PomodoroWorkScheduler.cancelCompletionAlert(context)
        }
    }

    val minutes = timeRemainingSeconds / 60
    val seconds = timeRemainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)
    val progress = (totalSeconds - timeRemainingSeconds).toFloat() / totalSeconds.toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "pomodoro_progress_animation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, selectedCategory.color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag("focus_pomodoro_timer_card"),
        color = AngelCardDark
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Category Tag Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Pomodoro Timer",
                        tint = selectedCategory.color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "25-Min Focus Session",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = selectedCategory.color.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, selectedCategory.color)
                ) {
                    Text(
                        text = selectedCategory.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedCategory.color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color-Coded Category Tag Selector
            Text(
                text = "SELECT CATEGORY TAG",
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
                        color = if (isSelected) category.color else Color(0xFF1E293B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = category }
                            .testTag("tag_category_${category.id.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else category.color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Circular Progress Indicator & Countdown Display
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .size(150.dp)
                        .testTag("pomodoro_circular_progress_indicator"),
                    color = selectedCategory.color,
                    strokeWidth = 10.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeFormatted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.testTag("timer_countdown_text")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${(progress * 100).toInt()}% • ${selectedCategory.displayName}",
                        fontSize = 12.sp,
                        color = AngelTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Linear Progress Indicator
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = selectedCategory.color,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons: Start / Pause / Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start / Pause Toggle
                Button(
                    onClick = { isRunning = !isRunning },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFE53935) else selectedCategory.color
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("timer_start_pause_btn")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pause Timer" else "Start Timer",
                        tint = if (isRunning) Color.White else Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "Pause" else "Start 25m",
                        color = if (isRunning) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                // Reset Button
                OutlinedButton(
                    onClick = {
                        isRunning = false
                        timeRemainingSeconds = totalSeconds
                        sessionSavedMessage = false
                        PomodoroForegroundService.stopTimer(context)
                        PomodoroWorkScheduler.cancelCompletionAlert(context)
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("timer_reset_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset Timer",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }

                // Manual Save Session Log to Room DB
                IconButton(
                    onClick = {
                        val elapsedMins = maxOf(1, (totalSeconds - timeRemainingSeconds) / 60)
                        onSaveFocusSession(
                            sessionTitle.ifBlank { "${selectedCategory.displayName} Focus" },
                            elapsedMins,
                            "Manually logged $elapsedMins mins in ${selectedCategory.displayName}",
                            selectedCategory.displayName,
                            selectedCategory.colorHex
                        )
                        sessionSavedMessage = true
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .testTag("timer_save_log_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Log Session to Room",
                        tint = AngelNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = sessionSavedMessage) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelNeonGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AngelNeonGreen)
                ) {
                    Text(
                        text = "Saved to Room DB under ${selectedCategory.displayName}!",
                        fontSize = 12.sp,
                        color = AngelNeonGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
