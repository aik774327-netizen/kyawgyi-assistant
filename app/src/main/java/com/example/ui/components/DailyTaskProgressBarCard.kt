package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyTaskEntity
import com.example.ui.theme.*

/**
 * VISUAL DAILY TASK PROGRESS BAR CARD
 * Displays the percentage of daily tasks completed on screen with an interactive M3 progress bar.
 */
@Composable
fun DailyTaskProgressBarCard(
    tasks: List<DailyTaskEntity>,
    modifier: Modifier = Modifier
) {
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.isCompleted }
    val progressRatio = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 0f
    val percentageInt = (progressRatio * 100).toInt()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, AngelNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag("daily_task_progress_card"),
        color = AngelCardDark
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.TaskAlt,
                        contentDescription = "Task Progress",
                        tint = AngelNeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Task Completion",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AngelNeonGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "$percentageInt%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AngelNeonGreen,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("task_progress_percentage_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual M3 Progress Bar
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .testTag("daily_task_progress_bar"),
                color = AngelNeonGreen,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = AngelNeonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$completedTasks of $totalTasks finished",
                        fontSize = 12.sp,
                        color = AngelTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = if (totalTasks == 0) "No tasks for today" else if (completedTasks == totalTasks) "All Done! 🎉" else "${totalTasks - completedTasks} remaining",
                    fontSize = 12.sp,
                    color = AngelTextSecondary
                )
            }
        }
    }
}
