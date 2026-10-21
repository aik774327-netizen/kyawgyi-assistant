package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * TaskRepository handles local storage and CRUD operations for tasks using Room Database (TaskDao).
 */
class TaskRepository(
    private val taskDao: TaskDao,
    private val backupDataStore: TaskBackupDataStore? = null
) {
    /**
     * Observable stream of all tasks ordered by completion status and ID.
     */
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    /**
     * Observable stream of last backup metadata from DataStore.
     */
    val lastBackupMetadata: Flow<TaskBackupMetadata?> =
        backupDataStore?.lastBackupMetadata ?: kotlinx.coroutines.flow.flowOf(null)

    /**
     * Get a single task by its unique ID.
     */
    suspend fun getTaskById(id: Long): Task? {
        return taskDao.getTaskById(id)
    }

    /**
     * Filter tasks by specific category.
     */
    fun getTasksByCategory(category: String): Flow<List<Task>> {
        return taskDao.getTasksByCategory(category)
    }

    /**
     * Filter tasks by due date range.
     */
    fun getTasksByDueDateRange(startMillis: Long, endMillis: Long): Flow<List<Task>> {
        return taskDao.getTasksByDueDateRange(startMillis, endMillis)
    }

    /**
     * Filter tasks by a specific due date (calendar day).
     */
    fun getTasksBySpecificDate(dateMillis: Long): Flow<List<Task>> {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return taskDao.getTasksByDueDateRange(startOfDay, endOfDay)
    }

    /**
     * Insert a new task into the database.
     * Returns the generated row ID.
     */
    suspend fun insertTask(task: Task): Long {
        return taskDao.insertTask(task)
    }

    /**
     * Insert multiple tasks (useful for restore or batch operations).
     */
    suspend fun insertTasks(tasks: List<Task>): List<Long> {
        return taskDao.insertTasks(tasks)
    }

    /**
     * Update an existing task in the database.
     */
    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
    }

    /**
     * Update due date for a specific task.
     */
    suspend fun updateTaskDueDate(taskId: Long, dueDateMillis: Long?) {
        taskDao.updateTaskDueDate(taskId, dueDateMillis)
    }

    /**
     * Update category and tags for a specific task.
     */
    suspend fun updateTaskCategoryAndTags(taskId: Long, category: String, tags: String) {
        taskDao.updateTaskCategoryAndTags(taskId, category, tags)
    }

    /**
     * Delete a task from the database.
     */
    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
    }

    /**
     * Delete a task by its ID.
     */
    suspend fun deleteTaskById(id: Long) {
        taskDao.deleteTaskById(id)
    }

    /**
     * Clear all tasks from the database.
     */
    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    /**
     * Toggle the completion status of a task.
     */
    suspend fun toggleTaskCompletion(task: Task) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        taskDao.updateTask(updated)
    }

    /**
     * Get total count of tasks.
     */
    suspend fun getTaskCount(): Int {
        return taskDao.getTaskCount()
    }

    /**
     * Backup current tasks state into DataStore.
     */
    suspend fun backupTasksToDataStore(tasks: List<Task>): TaskBackupMetadata? {
        return backupDataStore?.backupTasks(tasks)
    }

    /**
     * Restore tasks from DataStore and re-insert into Room.
     */
    suspend fun restoreTasksFromDataStore(): List<Task> {
        val restored = backupDataStore?.restoreTasks() ?: emptyList()
        if (restored.isNotEmpty()) {
            taskDao.insertTasks(restored)
        }
        return restored
    }
}
