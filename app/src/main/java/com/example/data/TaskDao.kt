package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) to persist task data locally in Room.
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE category = :category ORDER BY isCompleted ASC, id DESC")
    fun getTasksByCategory(category: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE dueDateMillis IS NOT NULL AND dueDateMillis >= :startMillis AND dueDateMillis <= :endMillis ORDER BY isCompleted ASC, dueDateMillis ASC")
    fun getTasksByDueDateRange(startMillis: Long, endMillis: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE dueDateMillis IS NOT NULL ORDER BY isCompleted ASC, dueDateMillis ASC")
    fun getTasksWithDueDates(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDateMillis IS NOT NULL AND dueDateMillis > :currentMillis ORDER BY dueDateMillis ASC")
    suspend fun getUpcomingPendingTasks(currentMillis: Long): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>): List<Long>

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE tasks SET dueDateMillis = :dueDateMillis WHERE id = :id")
    suspend fun updateTaskDueDate(id: Long, dueDateMillis: Long?)

    @Query("UPDATE tasks SET category = :category, tags = :tags WHERE id = :id")
    suspend fun updateTaskCategoryAndTags(id: Long, category: String, tags: String)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAllTasks()

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int
}
