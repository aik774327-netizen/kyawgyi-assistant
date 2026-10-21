package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kyawgyi_shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val triggerPhrase: String, // ခေါ်ဆိုမည့် အသံစကားလုံး (ဥပမာ- "မေမေ့ဆီ ဖုန်းဆက်")
    val actionType: String,    // "CALL", "OPEN_APP", "TOGGLE_WIFI", "TOGGLE_FLASH"
    val payload: String,       // Phone number "09xxxxxxxxx" သို့မဟုတ် Package name "com.whatsapp"
    val isEnabled: Boolean = true
)
