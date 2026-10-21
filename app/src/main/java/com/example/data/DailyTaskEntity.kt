package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_tasks")
data class DailyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String = "General",
    val isCompleted: Boolean = false,
    val dueDate: String = "Today",
    val timestamp: Long = System.currentTimeMillis()
)
