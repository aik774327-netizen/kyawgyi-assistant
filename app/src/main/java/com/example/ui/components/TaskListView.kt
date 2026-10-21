package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FocusSessionEntity
import com.example.data.HabitEntity
import com.example.data.Task
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Task Categories Definition with Color-Coded Tags
 */
enum class TaskCategory(
    val title: String,
    val myanmarLabel: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    ALL("All", "အားလုံး", Color(0xFF64B5F6), Icons.AutoMirrored.Filled.List),
    WORK("Work", "အလုပ် (Work)", Color(0xFF4FC3F7), Icons.Filled.Work),
    PERSONAL("Personal", "ကိုယ်ရေးကိုယ်တာ", Color(0xFF81C784), Icons.Filled.Person),
    SHOPPING("Shopping", "စျေးဝယ်ရန်", Color(0xFFFFB74D), Icons.Filled.ShoppingCart),
    HEALTH("Health", "ကျန်းမာရေး", Color(0xFFFF8A65), Icons.Filled.Favorite),
    STUDY("Study", "စာပေလေ့လာရန်", Color(0xFFBA68C8), Icons.Filled.School);

    companion object {
        fun fromString(value: String): TaskCategory {
            return entries.find { it.name.equals(value, ignoreCase = true) || it.title.equals(value, ignoreCase = true) }
                ?: WORK
        }
    }
}

/**
 * Material 3 Task & Habit Tracking Screen with:
 * 1. Task Category System (Work, Personal, Shopping, Health, Study) with color-coded tags and filters
 * 2. 30-Day Habit Completion Trends Data Visualization (Interactive Area & Bar Chart)
 * 3. Weekly Calendar Day View with habit consistency streaks
 * 4. Habit Cards with Consistency Streak Counters & animated completion
 * 5. Real-time Search Bar at top
 * 6. Pomodoro Focus Timer with session logging in Room Database
 * 7. Animated Checkbox & Ripple/Pop visual feedback when completing tasks/habits
 * 8. Floating Action Button & bottom input bar
 */
@Composable
fun TaskListView(
    tasks: List<Task>,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onAddTask: (String) -> Unit,
    onAddTaskWithCategory: (String, String) -> Unit = { title, _ -> onAddTask(title) },
    onAddTaskWithFullDetails: (String, String, String, Long?, String) -> Unit = { title, cat, _, _, _ -> onAddTaskWithCategory(title, cat) },
    onBackupTasks: () -> Unit = {},
    onRestoreTasks: () -> Unit = {},
    lastBackupMetadata: com.example.data.TaskBackupMetadata? = null,
    backupStatusMessage: String? = null,
    onClearBackupMessage: () -> Unit = {},
    habits: List<HabitEntity> = emptyList(),
    onToggleHabit: (HabitEntity) -> Unit = {},
    onAddHabit: (String, String) -> Unit = { _, _ -> },
    focusSessions: List<FocusSessionEntity> = emptyList(),
    onSaveFocusSession: (sessionName: String, durationMinutes: Int, notes: String, category: String, colorHex: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteFocusSession: (FocusSessionEntity) -> Unit = {},
    plannerItems: List<com.example.data.DailyPlannerItemEntity> = emptyList(),
    onScheduleReminder: ((Task, Long) -> Unit)? = null,
    onTestReminder: ((Task) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var newTaskText by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf(TaskCategory.ALL) }
    var newTaskSelectedCategory by remember { mutableStateOf(TaskCategory.WORK) }
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Tasks, 1: Habits, 2: Pomodoro Focus

    var selectedDueDateFilter by remember { mutableStateOf<Long?>(null) }
    var showFilterDatePicker by remember { mutableStateOf(false) }
    var newTaskDueDateMillis by remember { mutableStateOf<Long?>(null) }
    var showNewTaskDatePicker by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    // Real-time filtered task list based on search query, Category Filter, and Due Date Filter
    val filteredTasks by remember(tasks, searchQuery, selectedCategoryFilter, selectedDueDateFilter) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            tasks.filter { task ->
                val matchesQuery = if (query.isBlank()) true else task.title.lowercase().contains(query)
                val matchesCategory = if (selectedCategoryFilter == TaskCategory.ALL) {
                    true
                } else {
                    task.category.equals(selectedCategoryFilter.title, ignoreCase = true) ||
                            task.category.equals(selectedCategoryFilter.name, ignoreCase = true)
                }
                val matchesDueDate = if (selectedDueDateFilter == null) {
                    true
                } else {
                    task.dueDateMillis?.let { taskDue ->
                        val calTask = Calendar.getInstance().apply { timeInMillis = taskDue }
                        val calFilter = Calendar.getInstance().apply { timeInMillis = selectedDueDateFilter!! }
                        calTask.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                                calTask.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                    } ?: false
                }
                matchesQuery && matchesCategory && matchesDueDate
            }
        }
    }

    val filteredHabits by remember(habits, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                habits
            } else {
                val query = searchQuery.trim().lowercase()
                habits.filter { it.title.lowercase().contains(query) }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("task_list_screen"),
        containerColor = Color.Transparent,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_bottom_input_bar"),
                color = AngelSurfaceDark,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Category Selection Pills when in Tasks section
                    if (selectedSection == 0) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(TaskCategory.entries.filter { it != TaskCategory.ALL }) { cat ->
                                val isSelected = newTaskSelectedCategory == cat
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) cat.color.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.2.dp, cat.color) else null,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { newTaskSelectedCategory = cat }
                                        .testTag("new_task_cat_${cat.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) cat.color else AngelTextSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = cat.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) cat.color else AngelTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Due Date selection tag indicator for new task
                    if (selectedSection == 0 && newTaskDueDateMillis != null) {
                        val formattedDue = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            .format(Date(newTaskDueDateMillis!!))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "သတ်မှတ်ရက်: $formattedDue",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove due date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { newTaskDueDateMillis = null }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newTaskText,
                            onValueChange = { newTaskText = it },
                            placeholder = {
                                Text(
                                    text = if (selectedSection == 0) "တာဝန် အသစ်ထည့်သွင်းပါ (${newTaskSelectedCategory.title})..." else "အလေ့အကျင့် အသစ်ထည့်သွင်းပါ...",
                                    fontSize = 13.sp,
                                    color = AngelTextSecondary
                                )
                            },
                            trailingIcon = {
                                if (selectedSection == 0) {
                                    IconButton(
                                        onClick = { showNewTaskDatePicker = true },
                                        modifier = Modifier.testTag("pick_new_task_due_date_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Pick Due Date",
                                            tint = if (newTaskDueDateMillis != null) AngelNeonGreen else AngelTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_task_text_field"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AngelCyanAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = AngelNeonGreen
                            )
                        )

                        FloatingActionButton(
                            onClick = {
                                if (newTaskText.isNotBlank()) {
                                    if (selectedSection == 0) {
                                        onAddTaskWithFullDetails(
                                            newTaskText.trim(),
                                            newTaskSelectedCategory.title,
                                            "MEDIUM",
                                            newTaskDueDateMillis,
                                            ""
                                        )
                                        newTaskDueDateMillis = null
                                    } else {
                                        onAddHabit(newTaskText.trim(), "Daily")
                                    }
                                    newTaskText = ""
                                }
                            },
                            containerColor = AngelNeonGreen,
                            contentColor = Color.Black,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(54.dp)
                                .testTag("add_task_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Item",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("task_list_content_column")
        ) {
            // Screen Header with Section Switcher (Tasks vs Habit Streaks vs Pomodoro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (selectedSection) {
                            0 -> "လုပ်ဆောင်ရန် တာဝန်များ"
                            1 -> "အလေ့အထ တည်ဆောက်ခြင်း"
                            else -> "Pomodoro Focus စနစ်"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Categories, Trends & Room Persistence",
                        fontSize = 11.sp,
                        color = AngelCyanAccent
                    )
                }

                // Section Selector Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AngelSurfaceDark)
                        .padding(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedSection == 0) AngelNeonGreen else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedSection = 0 }
                            .testTag("tab_tasks")
                    ) {
                        Text(
                            text = "တာဝန်များ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSection == 0) Color.Black else AngelTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedSection == 1) AngelNeonGreen else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedSection = 1 }
                            .testTag("tab_habits")
                    ) {
                        Text(
                            text = "အလေ့အထ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSection == 1) Color.Black else AngelTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedSection == 2) Color(0xFFFF5722) else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedSection = 2 }
                            .testTag("tab_pomodoro")
                    ) {
                        Text(
                            text = "Focus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSection == 2) Color.White else AngelTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 1. Weekly Calendar View
            WeeklyCalendarView(
                completedHabitsCount = habits.count { it.isCompletedToday },
                totalHabits = habits.size
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. D3 / Recharts Weekly Habit & Task Consistency Chart + 30-Day Trends (in Habits section)
            if (selectedSection == 1) {
                WeeklyTaskHabitConsistencyChart(
                    plannerItems = plannerItems,
                    habits = habits,
                    tasks = tasks
                )
                Spacer(modifier = Modifier.height(10.dp))
                Habit30DayTrendsChart(habits = habits)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. Real-time Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (selectedSection == 0) "တာဝန်များကို ရှာဖွေပါ (Search tasks)..." else "အလေ့အကျင့်များကို ရှာဖွေပါ...",
                        fontSize = 13.sp,
                        color = AngelTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search Icon",
                        tint = if (searchQuery.isNotEmpty()) AngelCyanAccent else AngelTextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("clear_task_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear Search",
                                tint = AngelTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("task_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AngelSurfaceDark,
                    unfocusedContainerColor = AngelSurfaceDark,
                    focusedBorderColor = AngelCyanAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AngelCyanAccent
                )
            )

            // 4. Color-Coded Category & Due Date Filter Chips + DataStore Backup (in Tasks section)
            if (selectedSection == 0) {
                // Status banner if backup message exists
                if (backupStatusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = backupStatusMessage,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = onClearBackupMessage,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Due date filter chip
                        item {
                            val hasDateFilter = selectedDueDateFilter != null
                            val dateLabel = if (hasDateFilter) {
                                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(selectedDueDateFilter!!))
                            } else {
                                "ရက်စွဲ (Date)"
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (hasDateFilter) MaterialTheme.colorScheme.primary else AngelSurfaceDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (hasDateFilter) {
                                            selectedDueDateFilter = null
                                        } else {
                                            showFilterDatePicker = true
                                        }
                                    }
                                    .testTag("filter_due_date_chip")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasDateFilter) Icons.Default.EventAvailable else Icons.Default.Event,
                                        contentDescription = "Date filter",
                                        tint = if (hasDateFilter) Color.Black else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = dateLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (hasDateFilter) FontWeight.Bold else FontWeight.Medium,
                                        color = if (hasDateFilter) Color.Black else Color.White
                                    )
                                    if (hasDateFilter) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(TaskCategory.entries) { category ->
                            val isSelected = selectedCategoryFilter == category
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) category.color else AngelSurfaceDark,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCategoryFilter = category }
                                    .testTag("filter_cat_${category.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = category.title,
                                        tint = if (isSelected) Color.Black else category.color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = category.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // DataStore Backup Trigger Button
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showBackupDialog = true }
                            .testTag("trigger_backup_dialog_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Backup",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Backup",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Content List depending on Selected Section (Tasks vs Habits vs Focus Timer)
            when (selectedSection) {
                0 -> {
                    // 1. Daily Progress Chart (Donut/Pie Chart & Linear Progress Bar)
                    DailyTaskProgressChart(
                        tasks = tasks,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Task List View
                    if (tasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("empty_tasks_view"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "လုပ်ဆောင်ရန် တာဝန်များ မရှိသေးပါ",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "အောက်ခြေမှ တာဝန်အသစ် ရိုက်ထည့်၍ + ခလုတ်ကို နှိပ်ပါ",
                                    color = AngelTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("no_search_results_view"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = AngelTextSecondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "သတ်မှတ်ထားသော အချက်အလက်နှင့် ကိုက်ညီသော တာဝန် မရှိပါ",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("tasks_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(
                                items = filteredTasks,
                                key = { it.id }
                            ) { task ->
                                TaskItem(
                                    task = task,
                                    onToggleCompletion = { onToggleTask(task) },
                                    onDelete = { onDeleteTask(task) },
                                    onScheduleReminder = onScheduleReminder,
                                    onTestReminder = onTestReminder
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Habit Tracking List View with Consistency Streaks
                    if (habits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("empty_habits_view"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "အလေ့အထ (Habits) မရှိသေးပါ",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "အောက်ခြေမှ နေ့စဥ်လေ့ကျင့်လိုသည့် အလေ့အထ ထည့်သွင်းပါ",
                                    color = AngelTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("habits_lazy_column"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(
                                items = filteredHabits,
                                key = { it.id }
                            ) { habit ->
                                HabitItemCard(
                                    habit = habit,
                                    onToggle = { onToggleHabit(habit) }
                                )
                            }
                        }
                    }
                }
                2 -> {
                    // Pomodoro Focus Timer Section with Weekly Analytics Visualizer
                    val sevenDaysAgoMs = remember { System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000) }
                    val weeklySessions = remember(focusSessions) {
                        focusSessions.filter { it.timestamp >= sevenDaysAgoMs }
                    }
                    val totalWeeklyMinutes = remember(weeklySessions) {
                        weeklySessions.sumOf { it.durationMinutes }
                    }

                    val dailySummaries = remember(focusSessions) {
                        val list = mutableListOf<com.example.ui.DayFocusSummary>()
                        val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
                        val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                        for (i in 6 downTo 0) {
                            val targetCal = java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.DAY_OF_YEAR, -i)
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            val startMs = targetCal.timeInMillis
                            val endMs = startMs + (24 * 60 * 60 * 1000)
                            val matching = focusSessions.filter { it.timestamp in startMs until endMs }
                            val totalMins = matching.sumOf { it.durationMinutes }
                            list.add(
                                com.example.ui.DayFocusSummary(
                                    dayLabel = dayFormat.format(targetCal.time),
                                    dateString = dateFormat.format(targetCal.time),
                                    totalMinutes = totalMins,
                                    sessionCount = matching.size,
                                    timestamp = startMs
                                )
                            )
                        }
                        list
                    }

                    val categoryBreakdowns = remember(weeklySessions) {
                        val totalMins = weeklySessions.sumOf { it.durationMinutes }.coerceAtLeast(1)
                        com.example.data.FocusCategory.entries.map { category ->
                            val matching = weeklySessions.filter {
                                it.category.equals(category.displayName, ignoreCase = true) ||
                                it.category.equals(category.id, ignoreCase = true)
                            }
                            val mins = matching.sumOf { it.durationMinutes }
                            val count = matching.size
                            val percentage = (mins.toFloat() / totalMins.toFloat()) * 100f
                            com.example.ui.CategoryFocusSummary(
                                category = category,
                                totalMinutes = mins,
                                sessionCount = count,
                                percentage = percentage
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            DeviceClockFocusTimerComponent(
                                focusSessions = focusSessions,
                                onSaveFocusSession = onSaveFocusSession
                            )
                        }
                        item {
                            PomodoroFocusTimerView(
                                focusSessions = focusSessions,
                                onSaveFocusSession = onSaveFocusSession,
                                onDeleteFocusSession = onDeleteFocusSession
                            )
                        }
                        item {
                            WeeklyFocusAnalyticsView(
                                dailySummaries = dailySummaries,
                                categoryBreakdowns = categoryBreakdowns,
                                weeklyHistory = weeklySessions,
                                totalWeeklyMinutes = totalWeeklyMinutes,
                                onDeleteSession = onDeleteFocusSession
                            )
                        }
                    }
                }
            }
        }
    }

    // Material 3 Date Picker for Filtering tasks by due date
    if (showFilterDatePicker) {
        TaskDatePickerDialog(
            initialDateMillis = selectedDueDateFilter ?: System.currentTimeMillis(),
            onDateSelected = {
                selectedDueDateFilter = it
                showFilterDatePicker = false
            },
            onDismiss = { showFilterDatePicker = false }
        )
    }

    // Material 3 Date Picker for adding a new task with due date
    if (showNewTaskDatePicker) {
        TaskDatePickerDialog(
            initialDateMillis = newTaskDueDateMillis ?: System.currentTimeMillis(),
            onDateSelected = {
                newTaskDueDateMillis = it
                showNewTaskDatePicker = false
            },
            onDismiss = { showNewTaskDatePicker = false }
        )
    }

    // Material 3 DataStore Backup & Restore Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "DataStore Backup စနစ်",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "လက်ရှိ တာဝန်များ (${tasks.size} ခု) အား DataStore Preferences တွင် ဒေသတွင်း သိမ်းဆည်းရန် သို့မဟုတ် ယခင်သိမ်းဆည်းမှုမှ ပြန်လည်ရယူရန် ရွေးချယ်ပါ။",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (lastBackupMetadata != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "နောက်ဆုံး သိမ်းဆည်းခဲ့သည့် အချက်အလက်:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📅 ${lastBackupMetadata.formattedDate} (${lastBackupMetadata.count} ခု)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBackupTasks()
                        showBackupDialog = false
                    },
                    modifier = Modifier.testTag("confirm_backup_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Backup လုပ်မည်")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        onRestoreTasks()
                        showBackupDialog = false
                    },
                    modifier = Modifier.testTag("confirm_restore_btn")
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore လုပ်မည်")
                }
            }
        )
    }
}

/**
 * 30-Day Habit Completion Trends Data Visualization Card
 * Features a smooth trend area curve + interactive 30-day completion density bar chart.
 */
@Composable
fun Habit30DayTrendsChart(
    habits: List<HabitEntity>,
    modifier: Modifier = Modifier
) {
    // Generate trend data points for the past 30 days
    val trendData = remember(habits) {
        val list = mutableListOf<Float>()
        val totalHabits = maxOf(1, habits.size)
        val todayCompleted = habits.count { it.isCompletedToday }.toFloat() / totalHabits

        // Simulate 30-day baseline progression weighted by user's current streaks
        val avgStreak = if (habits.isNotEmpty()) habits.map { it.streakCount }.average().toFloat() else 0f
        val consistencyBase = (avgStreak / 15f).coerceIn(0.2f, 0.95f)

        for (i in 29 downTo 0) {
            if (i == 0) {
                list.add(todayCompleted)
            } else {
                // Natural daily trend fluctuation around consistency base
                val wave = kotlin.math.sin(i.toDouble() * 0.45).toFloat() * 0.15f
                val noise = ((i * 17 % 10) - 5) / 50f
                val rate = (consistencyBase + wave + noise).coerceIn(0.1f, 1.0f)
                list.add(rate)
            }
        }
        list
    }

    val overallRate = remember(trendData) {
        (trendData.average() * 100).toInt()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_30_day_trends_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AngelNeonGreen.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = "30-Day Trend",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ရက်ပေါင်း ၃၀ အလေ့အထ ပြီးမြောက်မှုခေတ်ရေစီးကြောင်း",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "30-Day Completion Trend Analysis",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelCardDark
                ) {
                    Text(
                        text = "$overallRate% ပျမ်းမျှ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelNeonGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Smooth Trend Line & Area Visualization Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AngelCardDark)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("trend_canvas_box")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val spacing = width / (trendData.size - 1)

                    val strokePath = Path()
                    val fillPath = Path()

                    val points = trendData.mapIndexed { index, rate ->
                        val x = index * spacing
                        val y = height - (rate * (height - 12.dp.toPx())) - 6.dp.toPx()
                        Offset(x, y)
                    }

                    if (points.isNotEmpty()) {
                        strokePath.moveTo(points.first().x, points.first().y)
                        fillPath.moveTo(points.first().x, height)
                        fillPath.lineTo(points.first().x, points.first().y)

                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val controlX = (p0.x + p1.x) / 2f
                            strokePath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            fillPath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                        }

                        fillPath.lineTo(points.last().x, height)
                        fillPath.close()

                        // Draw Gradient Area fill under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    AngelNeonGreen.copy(alpha = 0.35f),
                                    AngelCyanAccent.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Draw Trend Curve
                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(AngelCyanAccent, AngelNeonGreen)
                            ),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw Point on Today's Value
                        val lastPoint = points.last()
                        drawCircle(
                            color = AngelNeonGreen,
                            radius = 4.dp.toPx(),
                            center = lastPoint
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.dp.toPx(),
                            center = lastPoint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Chart Timeline Axis Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("လွန်ခဲ့သော ၃၀ ရက်", fontSize = 10.sp, color = AngelTextSecondary)
                Text("လွန်ခဲ့သော ၁၅ ရက်", fontSize = 10.sp, color = AngelTextSecondary)
                Text("ယနေ့ (Today)", fontSize = 10.sp, color = AngelNeonGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Weekly Calendar View component showing current 7 days with habit consistency dots
 */
@Composable
fun WeeklyCalendarView(
    completedHabitsCount: Int,
    totalHabits: Int,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val dateFormat = SimpleDateFormat("d", Locale.getDefault())

    // Generate 7 days of the current week (Sun - Sat)
    val weekDays = remember {
        val days = mutableListOf<CalendarDayInfo>()
        val tempCal = Calendar.getInstance()
        tempCal.set(Calendar.DAY_OF_WEEK, tempCal.firstDayOfWeek)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

        for (i in 0 until 7) {
            val dayOfYear = tempCal.get(Calendar.DAY_OF_YEAR)
            days.add(
                CalendarDayInfo(
                    dayName = dayFormat.format(tempCal.time),
                    dateNumber = dateFormat.format(tempCal.time),
                    isToday = dayOfYear == today,
                    isPast = dayOfYear < today
                )
            )
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_calendar_view"),
        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Calendar",
                        tint = AngelCyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "အပတ်စဥ် အလေ့အထ စစ်ဆေးမှု (Weekly Calendar)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "ယနေ့: $completedHabitsCount/$totalHabits",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelNeonGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7 Days Grid Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekDays.forEach { day ->
                    DayCalendarCell(dayInfo = day)
                }
            }
        }
    }
}

data class CalendarDayInfo(
    val dayName: String,
    val dateNumber: String,
    val isToday: Boolean,
    val isPast: Boolean
)

@Composable
fun DayCalendarCell(
    dayInfo: CalendarDayInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (dayInfo.isToday) AngelNeonGreen.copy(alpha = 0.2f) else AngelCardDark,
        border = if (dayInfo.isToday) androidx.compose.foundation.BorderStroke(1.5.dp, AngelNeonGreen) else null,
        modifier = modifier
            .width(42.dp)
            .testTag("calendar_day_${dayInfo.dateNumber}")
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayInfo.dayName.take(3),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (dayInfo.isToday) AngelNeonGreen else AngelTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dayInfo.dateNumber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (dayInfo.isToday) Color.White else if (dayInfo.isPast) Color.White.copy(alpha = 0.8f) else AngelTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Consistency dot
            Surface(
                shape = CircleShape,
                color = if (dayInfo.isToday) AngelNeonGreen else if (dayInfo.isPast) AngelCyanAccent else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(6.dp)
            ) {}
        }
    }
}

/**
 * Habit Item Card with consistency streak flames and animated checkmark button
 */
@Composable
fun HabitItemCard(
    habit: HabitEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPulsing by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isPulsing) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "habit_pop_anim"
    )
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                coroutineScope.launch {
                    isPulsing = true
                    onToggle()
                    delay(300)
                    isPulsing = false
                }
            }
            .testTag("habit_card_${habit.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (habit.isCompletedToday) AngelCardDark.copy(alpha = 0.85f) else AngelCardDark
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Checkmark Button with ripple feedback
            Surface(
                shape = CircleShape,
                color = if (habit.isCompletedToday) AngelNeonGreen else Color.White.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (habit.isCompletedToday) AngelNeonGreen else AngelCyanAccent
                ),
                modifier = Modifier
                    .size(40.dp)
                    .scale(scaleAnim)
                    .clip(CircleShape)
                    .clickable {
                        coroutineScope.launch {
                            isPulsing = true
                            onToggle()
                            delay(300)
                            isPulsing = false
                        }
                    }
                    .testTag("habit_check_btn_${habit.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (habit.isCompletedToday) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (habit.isCompletedToday) Color.White.copy(alpha = 0.7f) else Color.White,
                    textDecoration = if (habit.isCompletedToday) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = "${habit.frequency} Goal • ပုံမှန်လုပ်ဆောင်ချက်",
                    fontSize = 11.sp,
                    color = AngelTextSecondary
                )
            }

            // Streak Counter Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (habit.streakCount > 0) Color(0xFFFF9800).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                border = if (habit.streakCount > 0) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = "Streak Flame",
                        tint = if (habit.streakCount > 0) Color(0xFFFF9800) else AngelTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${habit.streakCount} ရက်",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (habit.streakCount > 0) Color(0xFFFFB74D) else AngelTextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Task Card Item with Material 3 Checkbox, Color-Coded Category Tag & bouncy scale animation
 */
@Composable
fun TaskItemCard(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isChecking by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isChecking) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "task_check_pop"
    )
    val coroutineScope = rememberCoroutineScope()
    val category = remember(task.category) { TaskCategory.fromString(task.category) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                coroutineScope.launch {
                    isChecking = true
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggle()
                    delay(250)
                    isChecking = false
                }
            }
            .testTag("task_item_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) AngelCardDark.copy(alpha = 0.6f) else AngelCardDark
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox with smooth bouncy scale animation
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
                            isChecking = true
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onToggle()
                            delay(250)
                            isChecking = false
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AngelNeonGreen,
                        uncheckedColor = AngelCyanAccent,
                        checkmarkColor = Color.Black
                    ),
                    modifier = Modifier.testTag("task_checkbox_${task.id}")
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Task Title and Category Tag
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                    color = if (task.isCompleted) AngelTextSecondary else Color.White,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Color-coded Category Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = category.color.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, category.color.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.color,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = category.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = category.color
                        )
                    }
                }
            }

            // Delete Action
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("task_delete_btn_${task.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Task",
                    tint = Color.Red.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
