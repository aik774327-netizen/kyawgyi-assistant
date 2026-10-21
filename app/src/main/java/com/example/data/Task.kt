package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Database Entity for 'Task' with Category classification, Priority level, Due Date, and Tags
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val isCompleted: Boolean = false,
    val category: String = "General",
    val priority: String = "MEDIUM",
    val dueDateMillis: Long? = null,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
