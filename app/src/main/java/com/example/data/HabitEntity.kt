package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val streakCount: Int = 0,
    val frequency: String = "Daily",
    val isCompletedToday: Boolean = false,
    val lastCompletedTimestamp: Long = 0L
)
