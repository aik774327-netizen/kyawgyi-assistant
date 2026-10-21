package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_aliases")
data class LearnedAliasEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedPhrase: String,  // Spoken Myanmar phrase normalized (e.g. "ဂိမ်းဖွင့်", "အလုပ်စာအုပ်")
    val rawPhrase: String,         // Original spoken text
    val actionType: String,        // "OPEN_APP", "SYSTEM_ACTION", "CUSTOM_INTENT"
    val targetPayload: String,     // e.g. "com.dts.freefireth", "VOLUME_SET:50"
    val displayLabel: String,      // User friendly label e.g. "Free Fire", "Volume 50%"
    val confidence: Float = 1.0f,
    val learnedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 1
)
