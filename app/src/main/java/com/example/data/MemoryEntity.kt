package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_store")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "Memory Store", // Preference, User Fact, System Memory
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
