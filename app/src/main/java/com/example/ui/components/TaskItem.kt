package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Task Priority levels with distinctive visual indicators and badges
 */
enum class TaskPriority(
    val level: String,
    val myanmarLabel: String,
    val indicatorColor: Color,
    val badgeBackground: Color
) {
    HIGH(
        level = "HIGH",
        myanmarLabel = "အရေးကြီး (High)",
        indicatorColor = Color(0xFFFF5252),
        badgeBackground = Color(0x33FF5252)
    ),
    MEDIUM(
        level = "MEDIUM",
        myanmarLabel = "အလတ် (Medium)",
        indicatorColor = Color(0xFFFFB74D),
        badgeBackground = Color(0x33FFB74D)
    ),
    LOW(
        level = "LOW",
        myanmarLabel = "သာမန် (Low)",
        indicatorColor = Color(0xFF64B5F6),
        badgeBackground = Color(0x3364B5F6)
    );

    companion object {
        fun fromString(value: String?): TaskPriority {
            return when (value?.uppercase()?.trim()) {
                "HIGH", "URGENT", "အရေးကြီး" -> HIGH
                "LOW", "MINOR", "သာမန်" -> LOW
                else -> MEDIUM
            }
        }
    }
}

/**
 * Categorical tagging metadata with distinct colors and icons
 */
data class CategoryTagMeta(
    val title: String,
    val color: Color,
    val icon: ImageVector
)

fun getCategoryTagMeta(category: String?): CategoryTagMeta {
    return when (category?.lowercase()?.trim()) {
        "work", "အလုပ်" -> CategoryTagMeta(
            title = "Work",
            color = Color(0xFF448AFF),
            icon = Icons.Default.Work
        )
        "personal", "ကိုယ်ရေးကိုယ်တာ" -> CategoryTagMeta(
            title = "Personal",
            color = Color(0xFFAB47BC),
            icon = Icons.Default.Person
        )
        "health", "ကျန်းမာရေး" -> CategoryTagMeta(
            title = "Health",
            color = Color(0xFF00E676),
            icon = Icons.Default.Favorite
        )
        "study", "ပညာရေး" -> CategoryTagMeta(
            title = "Study",
            color = Color(0xFF00E5FF),
            icon = Icons.Default.School
        )
        "shopping", "စျေးဝယ်" -> CategoryTagMeta(
            title = "Shopping",
            color = Color(0xFFFF9100),
            icon = Icons.Default.ShoppingCart
        )
        else -> CategoryTagMeta(
            title = category?.ifBlank { "General" } ?: "General",
            color = Color(0xFF78909C),
            icon = Icons.AutoMirrored.Filled.Label
        )
    }
}

/**
 * Composable function 'TaskItem' that displays a task with:
 * - Checkbox for completion with integrated HapticFeedback
 * - Visual indicator for priority levels
 * - Swipe-to-delete gesture handler (SwipeToDismissBox)
 * - Categorical tagging badges (Work, Personal, Health, etc.)
 * - Due date display with overdue detection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskItem(
    task: Task,
    onToggleCompletion: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onEditDueDate: (() -> Unit)? = null,
    onScheduleReminder: ((Task, Long) -> Unit)? = null,
    onTestReminder: ((Task) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPressingCheck by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressingCheck) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "task_check_pop"
    )

    val priority = remember(task.priority) { TaskPriority.fromString(task.priority) }
    val categoryMeta = remember(task.category) { getCategoryTagMeta(task.category) }

    val cardColor by animateColorAsState(
        targetValue = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "task_card_color"
    )

    // Swipe-to-delete gesture state
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else {
                false
            }
        }
    )

    // Formatted due date string & overdue checking
    val dueDateInfo = remember(task.dueDateMillis, task.isCompleted) {
        task.dueDateMillis?.let { millis ->
            val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            val formattedDate = formatter.format(Date(millis))
            val isOverdue = !task.isCompleted && millis < (System.currentTimeMillis() - 86400000L)
            Pair(formattedDate, isOverdue)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val bgColor by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                },
                label = "swipe_delete_bg"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ဖျက်မည် (Swipe to Delete)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("task_swipe_box_${task.id}")
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    coroutineScope.launch {
                        isPressingCheck = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleCompletion()
                        delay(200)
                        isPressingCheck = false
                    }
                }
                .testTag("task_item_${task.id}"),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (task.isCompleted) {
                    Color.Transparent
                } else {
                    priority.indicatorColor.copy(alpha = 0.35f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Indicator Pillar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (task.isCompleted) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            } else {
                                priority.indicatorColor
                            }
                        )
                        .testTag("task_priority_bar_${task.id}")
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Checkbox with pop animation & haptic feedback
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(scaleAnim),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = {
                            coroutineScope.launch {
                                isPressingCheck = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleCompletion()
                                delay(200)
                                isPressingCheck = false
                            }
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("task_checkbox_${task.id}")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Task Details & Metadata Pills
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (task.isCompleted) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tags, Priority & Due Date row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Priority Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priority.badgeBackground,
                            border = BorderStroke(0.8.dp, priority.indicatorColor.copy(alpha = 0.8f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Priority: ${priority.level}",
                                    tint = priority.indicatorColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = priority.level,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = priority.indicatorColor
                                )
                            }
                        }

                        // Categorical Tagging Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = categoryMeta.color.copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, categoryMeta.color.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = categoryMeta.icon,
                                    contentDescription = categoryMeta.title,
                                    tint = categoryMeta.color,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = categoryMeta.title,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryMeta.color
                                )
                            }
                        }

                        // Custom tags if present
                        if (task.tags.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "#${task.tags.trim()}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Due Date Badge
                        if (dueDateInfo != null) {
                            val (formattedDate, isOverdue) = dueDateInfo
                            val dueBadgeColor = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = dueBadgeColor.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, dueBadgeColor.copy(alpha = 0.6f)),
                                modifier = if (onEditDueDate != null) Modifier.clickable { onEditDueDate() } else Modifier
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Default.EventBusy else Icons.Default.Event,
                                        contentDescription = "Due Date",
                                        tint = dueBadgeColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isOverdue) "ရက်လွန်: $formattedDate" else formattedDate,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = dueBadgeColor
                                    )
                                }
                            }
                        }
                    }
                }

                // WorkManager Push Reminder Bell Button
                Box {
                    IconButton(
                        onClick = { showReminderMenu = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("task_reminder_btn_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Set Task Reminder Alert",
                            tint = if (task.dueDateMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showReminderMenu,
                        onDismissRequest = { showReminderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("စမ်းသပ် သတိပေးချက် (၃ စက္ကန့်)", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showReminderMenu = false
                                if (onTestReminder != null) {
                                    onTestReminder(task)
                                } else {
                                    com.example.utils.TaskReminderScheduler.triggerImmediateTestReminder(context, task)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("၁၅ မိနစ်အကြာတွင် သတိပေးရန်", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showReminderMenu = false
                                val delayMillis = 15 * 60 * 1000L
                                if (onScheduleReminder != null) {
                                    onScheduleReminder(task, delayMillis)
                                } else {
                                    com.example.utils.TaskReminderScheduler.scheduleReminderWithDelay(context, task, delayMillis)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("၁ နာရီအကြာတွင် သတိပေးရန်", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showReminderMenu = false
                                val delayMillis = 60 * 60 * 1000L
                                if (onScheduleReminder != null) {
                                    onScheduleReminder(task, delayMillis)
                                } else {
                                    com.example.utils.TaskReminderScheduler.scheduleReminderWithDelay(context, task, delayMillis)
                                }
                            }
                        )
                        if (task.dueDateMillis != null) {
                            DropdownMenuItem(
                                text = { Text("သတ်မှတ်ချိန် (Due Date) တွင် သတိပေးရန်", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showReminderMenu = false
                                    com.example.utils.TaskReminderScheduler.scheduleReminderAtTimestamp(context, task, task.dueDateMillis)
                                }
                            )
                        }
                    }
                }

                // Delete Icon Button (or swipe indicator)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("task_delete_btn_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
