package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskBackupDataStore
import com.example.data.TaskBackupMetadata
import com.example.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * TaskViewModel manages the UI state of tasks, exposing a StateFlow of task lists to the UI layer,
 * local DataStore backups, and due date / category filtering.
 */
class TaskViewModel(
    application: Application,
    private val repository: TaskRepository = TaskRepository(
        taskDao = AppDatabase.getDatabase(application).taskDao(),
        backupDataStore = TaskBackupDataStore(application)
    )
) : AndroidViewModel(application) {

    /**
     * Observable StateFlow of tasks exposed to the UI layer.
     */
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _selectedFilterCategory = MutableStateFlow("All")
    val selectedFilterCategory: StateFlow<String> = _selectedFilterCategory.asStateFlow()

    private val _selectedDueDateFilter = MutableStateFlow<Long?>(null)
    val selectedDueDateFilter: StateFlow<Long?> = _selectedDueDateFilter.asStateFlow()

    private val _lastCompletedTaskTitle = MutableStateFlow<String?>(null)
    val lastCompletedTaskTitle: StateFlow<String?> = _lastCompletedTaskTitle.asStateFlow()

    val lastBackupMetadata: StateFlow<TaskBackupMetadata?> = repository.lastBackupMetadata
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    /**
     * Add a new task with title, category, priority, due date, and tags.
     */
    fun addTask(
        title: String,
        category: String = "General",
        priority: String = "MEDIUM",
        dueDateMillis: Long? = null,
        tags: String = ""
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title.trim(),
                    category = category.trim(),
                    priority = priority.trim(),
                    dueDateMillis = dueDateMillis,
                    tags = tags.trim(),
                    isCompleted = false
                )
            )
        }
    }

    /**
     * Toggle completion state of a task.
     */
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val willBeCompleted = !task.isCompleted
            repository.toggleTaskCompletion(task)
            if (willBeCompleted) {
                _lastCompletedTaskTitle.value = task.title
            }
        }
    }

    /**
     * Update an existing task.
     */
    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    /**
     * Update due date for an existing task.
     */
    fun updateTaskDueDate(taskId: Long, dueDateMillis: Long?) {
        viewModelScope.launch {
            repository.updateTaskDueDate(taskId, dueDateMillis)
        }
    }

    /**
     * Update category and tags for an existing task.
     */
    fun updateTaskCategory(taskId: Long, category: String, tags: String = "") {
        viewModelScope.launch {
            repository.updateTaskCategoryAndTags(taskId, category, tags)
        }
    }

    /**
     * Delete a task.
     */
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    /**
     * Delete a task by ID.
     */
    fun deleteTaskById(id: Long) {
        viewModelScope.launch {
            repository.deleteTaskById(id)
        }
    }

    /**
     * Clear all tasks.
     */
    fun clearAllTasks() {
        viewModelScope.launch {
            repository.clearAllTasks()
        }
    }

    /**
     * Set category filter.
     */
    fun setFilterCategory(category: String) {
        _selectedFilterCategory.value = category
    }

    /**
     * Set due date filter.
     */
    fun setDueDateFilter(dateMillis: Long?) {
        _selectedDueDateFilter.value = dateMillis
    }

    /**
     * Backup current task state into DataStore.
     */
    fun backupCurrentTasks(onComplete: (TaskBackupMetadata?) -> Unit = {}) {
        viewModelScope.launch {
            val currentTasks = tasks.value
            val metadata = repository.backupTasksToDataStore(currentTasks)
            _backupStatusMessage.value = if (metadata != null) {
                "သိမ်းဆည်းပြီးပါပြီ: ${metadata.count} ခု (${metadata.formattedDate})"
            } else {
                "သိမ်းဆည်းမှု မအောင်မြင်ပါ"
            }
            onComplete(metadata)
        }
    }

    /**
     * Restore tasks from DataStore backup.
     */
    fun restoreTasksFromBackup(onComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val restored = repository.restoreTasksFromDataStore()
            _backupStatusMessage.value = if (restored.isNotEmpty()) {
                "ပြန်လည်ရယူပြီးပါပြီ: ${restored.size} ခု"
            } else {
                "သိမ်းဆည်းထားသော အချက်အလက် မရှိပါ"
            }
            onComplete(restored.size)
        }
    }

    /**
     * Clear status notification.
     */
    fun clearBackupStatusMessage() {
        _backupStatusMessage.value = null
    }

    /**
     * Clear last completed task notification flag.
     */
    fun clearLastCompletedNotification() {
        _lastCompletedTaskTitle.value = null
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskViewModel(application) as T
                }
            }
    }
}
