package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity to store and manage daily tasks and habit tracking for the Daily Planner.
 * Supports:
 * - Daily tasks with scheduled dates, priority levels, categories, and completion status.
 * - Habit tracking with streak counts, target frequencies, and 7-day weekly completion history.
 */
@Entity(tableName = "daily_planner_items")
data class DailyPlannerItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val category: String = "General", // Work, Study, Health, Personal, Shopping
    val isHabit: Boolean = false, // false = daily task, true = recurring habit
    val isCompleted: Boolean = false,
    val streakCount: Int = 0,
    val targetDaysPerWeek: Int = 7,
    val priority: String = "Medium", // High, Medium, Low
    val scheduledDate: String = "Today",
    val dayOfWeek: Int = 1, // 1 = Monday ... 7 = Sunday
    val weeklyCompletionHistory: String = "0,0,0,0,0,0,0", // Mon to Sun completion (0 or 1)
    val lastCompletedTimestamp: Long = 0L,
    val reminderTime: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Parses the 7-day weekly completion history (Mon-Sun) as a List of Booleans.
     */
    fun getWeeklyCompletionList(): List<Boolean> {
        val parts = weeklyCompletionHistory.split(",").map { it.trim() }
        val result = mutableListOf<Boolean>()
        for (i in 0 until 7) {
            val bit = parts.getOrNull(i)
            result.add(bit == "1" || bit.equals("true", ignoreCase = true))
        }
        return result
    }

    /**
     * Calculates weekly completion percentage (0 - 100%).
     */
    fun getWeeklyCompletionPercentage(): Float {
        val list = getWeeklyCompletionList()
        val completedCount = list.count { it }
        val target = if (targetDaysPerWeek in 1..7) targetDaysPerWeek else 7
        return (completedCount.toFloat() / target.toFloat()).coerceIn(0f, 1f) * 100f
    }
}
