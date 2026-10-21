package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusSessionEntity
import com.example.data.HabitEntity
import com.example.data.Task
import com.example.ui.theme.*

/**
 * Data Model for Ring Metrics
 */
data class RingMetric(
    val title: String,
    val subtitle: String,
    val current: Int,
    val target: Int,
    val unit: String,
    val color: Color,
    val gradientColors: List<Color>,
    val icon: ImageVector
) {
    val progress: Float
        get() = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f

    val percentage: Int
        get() = (progress * 100).toInt()
}

/**
 * VISUAL PROGRESS RINGS CARD
 * Displays high-precision animated concentric progress rings for:
 * 1. Habit Completion Ring (Habits done today vs total habits)
 * 2. Focus Session Goal Ring (Today's focused minutes vs daily goal e.g. 60m)
 * 3. Daily Categorized Tasks Ring (Completed tasks vs total tasks)
 */
@Composable
fun VisualProgressRingsCard(
    tasks: List<Task>,
    habits: List<HabitEntity>,
    focusSessions: List<FocusSessionEntity>,
    dailyFocusGoalMinutes: Int = 60,
    modifier: Modifier = Modifier,
    onNavigateToTasks: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {}
) {
    // 1. Habits Progress Calculation
    val totalHabits = habits.size
    val completedHabits = habits.count { it.isCompletedToday }
    val habitRing = RingMetric(
        title = "အလေ့အကျင့် (Habits)",
        subtitle = "$completedHabits / $totalHabits ပြီးစီး",
        current = completedHabits,
        target = if (totalHabits > 0) totalHabits else 1,
        unit = "habits",
        color = Color(0xFF00E5FF),
        gradientColors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF)),
        icon = Icons.Filled.LocalFireDepartment
    )

    // 2. Focus Goal Progress Calculation (Today's sessions)
    val todayStartMs = remember {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }
    val todayFocusMinutes = remember(focusSessions) {
        focusSessions.filter { it.timestamp >= todayStartMs }.sumOf { it.durationMinutes }
    }
    val focusRing = RingMetric(
        title = "အာရုံစိုက်ချိန် (Focus)",
        subtitle = "$todayFocusMinutes / $dailyFocusGoalMinutes မိနစ်",
        current = todayFocusMinutes,
        target = dailyFocusGoalMinutes,
        unit = "mins",
        color = Color(0xFF8B5CF6),
        gradientColors = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)),
        icon = Icons.Filled.Timer
    )

    // 3. Daily Tasks Progress Calculation
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.isCompleted }
    val taskRing = RingMetric(
        title = "နေ့စဉ်တာဝန်များ (Tasks)",
        subtitle = "$completedTasks / $totalTasks ပြီးမြောက်",
        current = completedTasks,
        target = if (totalTasks > 0) totalTasks else 1,
        unit = "tasks",
        color = Color(0xFF10B981),
        gradientColors = listOf(Color(0xFF34D399), Color(0xFF059669)),
        icon = Icons.Filled.TaskAlt
    )

    // Overall Average Completion Score
    val totalWeights = 3
    val avgScore = ((habitRing.progress + focusRing.progress + taskRing.progress) / totalWeights * 100).toInt()

    // Smooth Entrance Animations for the 3 Rings
    val habitAnim = remember { Animatable(0f) }
    val focusAnim = remember { Animatable(0f) }
    val taskAnim = remember { Animatable(0f) }

    LaunchedEffect(habitRing.progress, focusRing.progress, taskRing.progress) {
        habitAnim.animateTo(
            targetValue = habitRing.progress,
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(focusRing.progress) {
        focusAnim.animateTo(
            targetValue = focusRing.progress,
            animationSpec = tween(durationMillis = 1100, delayMillis = 100, easing = FastOutSlowInEasing)
        )
    }
    LaunchedEffect(taskRing.progress) {
        taskAnim.animateTo(
            targetValue = taskRing.progress,
            animationSpec = tween(durationMillis = 1200, delayMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .testTag("visual_progress_rings_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with Overall Score Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Daily Goals & Progress Rings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "အလေ့အကျင့်၊ အာရုံစူးစိုက်မှုနှင့် နေ့စဉ်တာဝန်များ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("overall_progress_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ရလဒ် $avgScore%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Visual Layout: Concentric Canvas Ring on Left + Detailed Breakdown Legend on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Triple Concentric Animated Rings Canvas
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .testTag("concentric_progress_rings_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)

                        // Outer Ring: Habit (Radius: size.width / 2 - 8.dp)
                        val r1 = (size.minDimension / 2f) - (strokeWidth / 2f) - 2.dp.toPx()
                        // Middle Ring: Focus Session Goal (Radius: r1 - strokeWidth - 4.dp)
                        val r2 = r1 - strokeWidth - 5.dp.toPx()
                        // Inner Ring: Daily Tasks (Radius: r2 - strokeWidth - 4.dp)
                        val r3 = r2 - strokeWidth - 5.dp.toPx()

                        val rings = listOf(
                            Triple(r1, habitAnim.value, habitRing.gradientColors),
                            Triple(r2, focusAnim.value, focusRing.gradientColors),
                            Triple(r3, taskAnim.value, taskRing.gradientColors)
                        )

                        // Draw Background Tracks
                        rings.forEach { (radius, _, _) ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = radius,
                                center = centerOffset,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Draw Animated Progress Arcs
                        rings.forEach { (radius, progress, colors) ->
                            if (progress > 0f) {
                                val brush = Brush.sweepGradient(
                                    colors = colors,
                                    center = centerOffset
                                )
                                drawArc(
                                    brush = brush,
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                    size = Size(radius * 2f, radius * 2f),
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Center Metric Indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$avgScore%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Daily",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Legend and Individual Metric Cards
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RingLegendItem(
                        ring = habitRing,
                        onClick = onNavigateToTasks,
                        tag = "habit_ring_item"
                    )
                    RingLegendItem(
                        ring = focusRing,
                        onClick = onNavigateToFocus,
                        tag = "focus_ring_item"
                    )
                    RingLegendItem(
                        ring = taskRing,
                        onClick = onNavigateToTasks,
                        tag = "task_ring_item"
                    )
                }
            }
        }
    }
}

@Composable
private fun RingLegendItem(
    ring: RingMetric,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ring.color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = ring.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = ring.subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ring.color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${ring.percentage}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ring.color,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
