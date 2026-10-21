package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "angel", "system"
    val content: String,
    val responseMode: String = "QUICK", // QUICK, EXPERT, CREATIVE, TEACHING
    val engineType: String = "Response Engine", // Response Engine, Brain Core, Memory System
    val thoughtChain: String? = null,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
