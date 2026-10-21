package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) to store, query, and manage daily tasks and habit tracking
 * for the Daily Planner in Room Database.
 */
@Dao
interface DailyPlannerDao {

    @Query("SELECT * FROM daily_planner_items ORDER BY isCompleted ASC, id DESC")
    fun getAllPlannerItems(): Flow<List<DailyPlannerItemEntity>>

    @Query("SELECT * FROM daily_planner_items WHERE isHabit = 0 ORDER BY isCompleted ASC, id DESC")
    fun getDailyTasks(): Flow<List<DailyPlannerItemEntity>>

    @Query("SELECT * FROM daily_planner_items WHERE isHabit = 1 ORDER BY streakCount DESC, id DESC")
    fun getHabits(): Flow<List<DailyPlannerItemEntity>>

    @Query("SELECT * FROM daily_planner_items WHERE category = :category ORDER BY isCompleted ASC, id DESC")
    fun getItemsByCategory(category: String): Flow<List<DailyPlannerItemEntity>>

    @Query("SELECT * FROM daily_planner_items WHERE scheduledDate = :date ORDER BY isCompleted ASC, id DESC")
    fun getTasksByDate(date: String): Flow<List<DailyPlannerItemEntity>>

    @Query("SELECT * FROM daily_planner_items WHERE id = :id")
    suspend fun getItemById(id: Long): DailyPlannerItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: DailyPlannerItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<DailyPlannerItemEntity>)

    @Update
    suspend fun updateItem(item: DailyPlannerItemEntity)

    @Delete
    suspend fun deleteItem(item: DailyPlannerItemEntity)

    @Query("DELETE FROM daily_planner_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM daily_planner_items WHERE isCompleted = 1 AND isHabit = 0")
    suspend fun clearCompletedTasks()

    @Query("SELECT COUNT(*) FROM daily_planner_items")
    suspend fun getTotalItemCount(): Int

    @Query("SELECT COUNT(*) FROM daily_planner_items WHERE isCompleted = 1")
    suspend fun getCompletedItemCount(): Int

    @Query("SELECT COUNT(*) FROM daily_planner_items WHERE isHabit = 1")
    suspend fun getHabitCount(): Int
}
