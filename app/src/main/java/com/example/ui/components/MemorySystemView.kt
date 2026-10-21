package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyTaskEntity
import com.example.data.FocusSessionEntity
import com.example.data.HabitEntity
import com.example.data.MemoryEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemorySystemView(
    memories: List<MemoryEntity>,
    onAddMemory: (String, String, String) -> Unit,
    onDeleteMemory: (MemoryEntity) -> Unit,
    tasks: List<DailyTaskEntity>,
    onAddTask: (String, String) -> Unit,
    onToggleTask: (DailyTaskEntity) -> Unit,
    onDeleteTask: (DailyTaskEntity) -> Unit,
    habits: List<HabitEntity>,
    onAddHabit: (String, String) -> Unit,
    onToggleHabit: (HabitEntity) -> Unit,
    onDeleteHabit: (HabitEntity) -> Unit,
    focusSessions: List<FocusSessionEntity>,
    onAddFocusSession: (sessionName: String, durationMinutes: Int, notes: String, category: String, colorHex: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteFocusSession: (FocusSessionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Memory, 1: Tasks, 2: Habits, 3: Focus

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }

    // Memory form
    var memoryKey by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }

    // Task form
    var taskTitle by remember { mutableStateOf("") }
    var taskCategory by remember { mutableStateOf("AI & Work") }

    // Habit form
    var habitTitle by remember { mutableStateOf("") }
    var habitFreq by remember { mutableStateOf("Daily") }

    // Focus form
    var focusName by remember { mutableStateOf("") }
    var focusDuration by remember { mutableStateOf("25") }
    var focusNotes by remember { mutableStateOf("") }
    var focusCategory by remember { mutableStateOf(com.example.data.FocusCategory.WORK) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("memory_system_view")
    ) {
        // Banner Header Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AngelCyanAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            color = AngelSurfaceDark
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = "Room Persistence System",
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local Room Database Engine",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_item_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add",
                            tint = AngelDarkBackground,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (selectedSubTab) {
                                0 -> "Add Memory"
                                1 -> "Add Task"
                                2 -> "Add Habit"
                                else -> "Log Session"
                            },
                            color = AngelDarkBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Sub-tabs for Room Entities
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "Memories (${memories.size})",
                        "Tasks (${tasks.size})",
                        "Habits (${habits.size})",
                        "Focus (${focusSessions.size})"
                    )

                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedSubTab == index
                        Surface(
                            onClick = { selectedSubTab = index },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AngelNeonGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AngelNeonGreen else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sub_tab_$index")
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AngelNeonGreen else AngelTextSecondary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Content Sections based on selectedSubTab
        when (selectedSubTab) {
            0 -> {
                // Conversation Memory Tab
                if (memories.isEmpty()) {
                    EmptyStatePlaceholder(text = "No Memory entries stored yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(memories, key = { it.id }) { memory ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .testTag("memory_item_${memory.id}"),
                                color = AngelCardDark
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (memory.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = AngelNeonGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = memory.key,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AngelNeonGreen
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.White.copy(alpha = 0.1f)
                                            ) {
                                                Text(
                                                    text = memory.category,
                                                    fontSize = 9.sp,
                                                    color = AngelTextSecondary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = memory.value,
                                            fontSize = 13.sp,
                                            color = AngelTextPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteMemory(memory) },
                                        modifier = Modifier.testTag("delete_memory_${memory.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete Memory",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Daily Tasks Tab
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Visual Daily Task Progress Bar Card
                    DailyTaskProgressBarCard(tasks = tasks)

                    if (tasks.isEmpty()) {
                        EmptyStatePlaceholder(text = "No Daily Tasks recorded yet.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(tasks, key = { it.id }) { task ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (task.isCompleted) AngelNeonGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .testTag("task_item_${task.id}"),
                                    color = AngelCardDark
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { onToggleTask(task) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = AngelNeonGreen,
                                                uncheckedColor = AngelTextSecondary,
                                                checkmarkColor = AngelDarkBackground
                                            ),
                                            modifier = Modifier.testTag("task_checkbox_${task.id}")
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (task.isCompleted) AngelTextSecondary else Color.White,
                                                style = if (task.isCompleted) MaterialTheme.typography.bodyMedium.copy(
                                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                                ) else MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AngelCyanAccent.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = task.category,
                                                        fontSize = 10.sp,
                                                        color = AngelCyanAccent,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Due: ${task.dueDate}",
                                                    fontSize = 11.sp,
                                                    color = AngelTextSecondary
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDeleteTask(task) },
                                            modifier = Modifier.testTag("delete_task_${task.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Task",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Habits Tracker Tab
                if (habits.isEmpty()) {
                    EmptyStatePlaceholder(text = "No Habits being tracked yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(habits, key = { it.id }) { habit ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        if (habit.isCompletedToday) ModeCreativeColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .testTag("habit_item_${habit.id}"),
                                color = AngelCardDark
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = habit.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = ModeCreativeColor.copy(alpha = 0.2f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.LocalFireDepartment,
                                                        contentDescription = "Streak",
                                                        tint = ModeCreativeColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "${habit.streakCount} d streak",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ModeCreativeColor
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Frequency: ${habit.frequency}",
                                            fontSize = 11.sp,
                                            color = AngelTextSecondary
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { onToggleHabit(habit) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (habit.isCompletedToday) ModeCreativeColor else Color.White.copy(alpha = 0.1f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (habit.isCompletedToday) "Done Today ✓" else "Mark Done",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (habit.isCompletedToday) AngelDarkBackground else Color.White
                                            )
                                        }

                                        IconButton(onClick = { onDeleteHabit(habit) }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Habit",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // Focus Session Records Tab
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 25-Minute Pomodoro Focus Session Timer
                    FocusPomodoroTimerView(
                        onSaveFocusSession = onAddFocusSession
                    )

                    Text(
                        text = "Logged Focus History (${focusSessions.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (focusSessions.isEmpty()) {
                        EmptyStatePlaceholder(text = "No Focus Session records logged yet.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(focusSessions, key = { it.id }) { session ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, ModeTeachingColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .testTag("focus_session_${session.id}"),
                                    color = AngelCardDark
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(14.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val cat = com.example.data.FocusCategory.fromString(session.category)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.Timer,
                                                    contentDescription = "Focus Timer",
                                                    tint = cat.color,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = session.sessionName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = cat.color.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = session.category,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cat.color,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = cat.color.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "${session.durationMinutes} minutes",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cat.color,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                                    .format(Date(session.timestamp))
                                                Text(
                                                    text = dateStr,
                                                    fontSize = 11.sp,
                                                    color = AngelTextSecondary
                                                )
                                            }
                                            if (session.notes.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = session.notes,
                                                    fontSize = 12.sp,
                                                    color = AngelTextPrimary
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDeleteFocusSession(session) },
                                            modifier = Modifier.testTag("delete_focus_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Focus Log",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
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

    // Modal Add Item Dialog for selectedSubTab
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = when (selectedSubTab) {
                        0 -> "Add Memory Store"
                        1 -> "Add Daily Task"
                        2 -> "Track New Habit"
                        else -> "Log Focus Session"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (selectedSubTab) {
                        0 -> {
                            OutlinedTextField(
                                value = memoryKey,
                                onValueChange = { memoryKey = it },
                                label = { Text("Key (e.g. Favorite Language)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AngelNeonGreen)
                            )
                            OutlinedTextField(
                                value = memoryValue,
                                onValueChange = { memoryValue = it },
                                label = { Text("Value (e.g. Burmese)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AngelNeonGreen)
                            )
                        }

                        1 -> {
                            OutlinedTextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                label = { Text("Task Title") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AngelCyanAccent)
                            )
                            OutlinedTextField(
                                value = taskCategory,
                                onValueChange = { taskCategory = it },
                                label = { Text("Category (e.g. Work, Health)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AngelCyanAccent)
                            )
                        }

                        2 -> {
                            OutlinedTextField(
                                value = habitTitle,
                                onValueChange = { habitTitle = it },
                                label = { Text("Habit Title") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModeCreativeColor)
                            )
                            OutlinedTextField(
                                value = habitFreq,
                                onValueChange = { habitFreq = it },
                                label = { Text("Frequency (e.g. Daily)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModeCreativeColor)
                            )
                        }

                        3 -> {
                            OutlinedTextField(
                                value = focusName,
                                onValueChange = { focusName = it },
                                label = { Text("Session Name") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModeTeachingColor)
                            )
                            OutlinedTextField(
                                value = focusDuration,
                                onValueChange = { focusDuration = it },
                                label = { Text("Duration (Minutes)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModeTeachingColor)
                            )
                            OutlinedTextField(
                                value = focusNotes,
                                onValueChange = { focusNotes = it },
                                label = { Text("Notes / Goal achieved") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ModeTeachingColor)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Category Tag:", fontSize = 12.sp, color = AngelTextSecondary)
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(com.example.data.FocusCategory.entries.toTypedArray()) { cat ->
                                    val isSelected = focusCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) cat.color else AngelCardDark,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { focusCategory = cat }
                                    ) {
                                        Text(
                                            text = cat.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (selectedSubTab) {
                            0 -> {
                                if (memoryKey.isNotBlank() && memoryValue.isNotBlank()) {
                                    onAddMemory(memoryKey, memoryValue, "User Preference")
                                    memoryKey = ""
                                    memoryValue = ""
                                    showAddDialog = false
                                }
                            }

                            1 -> {
                                if (taskTitle.isNotBlank()) {
                                    onAddTask(taskTitle, taskCategory)
                                    taskTitle = ""
                                    showAddDialog = false
                                }
                            }

                            2 -> {
                                if (habitTitle.isNotBlank()) {
                                    onAddHabit(habitTitle, habitFreq)
                                    habitTitle = ""
                                    showAddDialog = false
                                }
                            }

                            3 -> {
                                val dur = focusDuration.toIntOrNull() ?: 25
                                if (focusName.isNotBlank()) {
                                    onAddFocusSession(focusName, dur, focusNotes, focusCategory.displayName, focusCategory.colorHex)
                                    focusName = ""
                                    focusNotes = ""
                                    showAddDialog = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent)
                ) {
                    Text("Save to Room DB", color = AngelDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = AngelTextSecondary)
                }
            },
            containerColor = AngelSurfaceDark
        )
    }
}

@Composable
private fun EmptyStatePlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AngelTextSecondary,
            fontSize = 14.sp
        )
    }
}
