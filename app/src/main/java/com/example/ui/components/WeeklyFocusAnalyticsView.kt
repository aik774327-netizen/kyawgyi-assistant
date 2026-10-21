package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusCategory
import com.example.data.FocusSessionEntity
import com.example.ui.CategoryFocusSummary
import com.example.ui.DayFocusSummary
import java.text.SimpleDateFormat
import java.util.*

/**
 * Component to visualize the user's completed focus sessions over the last week,
 * featuring interactive daily bar charts, category breakdowns, and color-coded tag analytics.
 */
@Composable
fun WeeklyFocusAnalyticsView(
    dailySummaries: List<DayFocusSummary>,
    categoryBreakdowns: List<CategoryFocusSummary>,
    weeklyHistory: List<FocusSessionEntity>,
    totalWeeklyMinutes: Int,
    onDeleteSession: (FocusSessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDaySummary by remember { mutableStateOf<DayFocusSummary?>(null) }
    val maxDayMinutes = (dailySummaries.maxOfOrNull { it.totalMinutes } ?: 60).coerceAtLeast(60)

    val averageDailyMinutes = if (dailySummaries.isNotEmpty()) totalWeeklyMinutes / dailySummaries.size else 0
    val totalSessions = weeklyHistory.size

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_focus_analytics_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.5f), Color(0xFF1E293B))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
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
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Analytics Icon",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "7-Day Focus Analytics",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Weekly completion & tag distribution",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "${totalWeeklyMinutes}m Total",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metric summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryMetricBadge(
                    title = "Total Time",
                    value = "${totalWeeklyMinutes / 60}h ${totalWeeklyMinutes % 60}m",
                    icon = Icons.Default.Timer,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricBadge(
                    title = "Daily Avg",
                    value = "${averageDailyMinutes}m",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricBadge(
                    title = "Sessions",
                    value = "$totalSessions",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 7-Day Interactive Bar Visualizer
            Text(
                text = "DAILY FOCUS DURATION (MINUTES)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF0B1120), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailySummaries.forEach { day ->
                    val isSelected = selectedDaySummary?.timestamp == day.timestamp
                    val heightRatio = if (maxDayMinutes > 0) {
                        (day.totalMinutes.toFloat() / maxDayMinutes.toFloat()).coerceIn(0.05f, 1.0f)
                    } else 0.05f

                    val animatedHeight by animateFloatAsState(
                        targetValue = heightRatio,
                        animationSpec = tween(durationMillis = 600),
                        label = "bar_height_${day.dayLabel}"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedDaySummary = if (isSelected) null else day }
                            .padding(horizontal = 2.dp)
                    ) {
                        // Duration label on top
                        Text(
                            text = if (day.totalMinutes > 0) "${day.totalMinutes}m" else "-",
                            fontSize = 10.sp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bar container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(animatedHeight)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (isSelected) {
                                                listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                                            } else if (day.totalMinutes > 0) {
                                                listOf(Color(0xFF60A5FA), Color(0xFF1D4ED8))
                                            } else {
                                                listOf(Color(0xFF334155), Color(0xFF1E293B))
                                            }
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Day label
                        Text(
                            text = day.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Selected Day Detail Box
            AnimatedVisibility(visible = selectedDaySummary != null) {
                selectedDaySummary?.let { day ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${day.dateString} (${day.dayLabel})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "${day.totalMinutes} mins in ${day.sessionCount} sessions",
                                fontSize = 12.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Category breakdown tag system
            Text(
                text = "CATEGORY BREAKDOWN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryBreakdowns.forEach { catSummary ->
                    CategoryBreakdownRow(summary = catSummary)
                }
            }

            if (weeklyHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "RECENT COMPLETED SESSIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weeklyHistory.take(5).forEach { session ->
                        val cat = FocusCategory.fromString(session.category)
                        val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        val formattedDate = timeFormat.format(Date(session.timestamp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(cat.color.copy(alpha = 0.4f), Color(0xFF1E293B))))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(cat.color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = session.sessionName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$formattedDate • ${session.durationMinutes}m • ${session.category}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onDeleteSession(session) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Session",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
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

@Composable
private fun SummaryMetricBadge(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun CategoryBreakdownRow(summary: CategoryFocusSummary) {
    val animatedProgress by animateFloatAsState(
        targetValue = (summary.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "category_bar_${summary.category.name}"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(summary.category.color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = summary.category.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E8F0)
                )
            }

            Text(
                text = "${summary.totalMinutes}m (${summary.percentage.toInt()}%)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar for category
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1E293B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(summary.category.color)
            )
        }
    }
}
