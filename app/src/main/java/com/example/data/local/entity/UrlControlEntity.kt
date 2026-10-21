package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * အခြား Application များနှင့် Webhook/URL များအား မြန်မာဘာသာစကား အသံဖြင့် ထိန်းချုပ်ရန် သိမ်းဆည်းသည့် Entity
 */
@Entity(tableName = "url_controls")
data class UrlControlEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                  // e.g. "Home Assistant မီးဖွင့်", "Telegram DeepLink", "Spotify Music"
    val triggerPhrase: String,         // e.g. "မီးခလုတ်ဖွင့်", "တယ်လီဂရမ်သို့ပို့", "သီချင်းဖွင့်ပါ"
    val url: String,                   // URL link: https://..., http://..., tg://..., spotify://..., intent://...
    val actionType: String = "OPEN_URL", // "OPEN_URL", "WEBHOOK_GET", "WEBHOOK_POST", "REMOTE_MIC_STREAM", "DEEP_LINK"
    val requestBody: String = "",      // JSON payload template for POST / Webhook (ဥပမာ- {"command":"{voice_text}"})
    val headers: String = "",          // Optional custom headers e.g. "Authorization: Bearer xxx"
    val successSpeech: String = "လင့်ခ်သို့ ချိတ်ဆက်ဆောင်ရွက်ပြီးပါပြီ",
    val failureSpeech: String = "လင့်ခ်သို့ ချိတ်ဆက်၍ မရပါခင်ဗျာ",
    val isEnabled: Boolean = true,
    val lastExecutedAt: Long = 0,
    val lastStatus: String = "IDLE"    // "SUCCESS", "FAILED", "IDLE"
)
