package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_history")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userQuery: String,          // အသုံးပြုသူ ပြောလိုက်သော စကားလုံး (ဥပမာ- "ဓာတ်မီးဖွင့်")
    val assistantResponse: String,  // ကျော်ကြီး၏ ပြန်လည် တုံ့ပြန်မှု (ဥပမာ- "ဓာတ်မီး ဖွင့်ပေးလိုက်ပါပြီ")
    val executionMode: String,      // "OFFLINE" သို့မဟုတ် "ONLINE_GEMINI"
    val timestamp: Long = System.currentTimeMillis() // ပြောကြားခဲ့သည့် အချိန်
)
