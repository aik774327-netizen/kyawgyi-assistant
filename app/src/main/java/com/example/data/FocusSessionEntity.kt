package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionName: String,
    val durationMinutes: Int,
    val category: String = "Work",
    val colorHex: String = "#3B82F6",
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)
