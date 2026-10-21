package com.example.data

import androidx.compose.ui.graphics.Color

/**
 * Category labeling for Pomodoro Focus Sessions
 */
enum class FocusCategory(
    val id: String,
    val displayName: String,
    val colorHex: String,
    val color: Color
) {
    WORK("Work", "Work", "#3B82F6", Color(0xFF3B82F6)),
    STUDY("Study", "Study", "#8B5CF6", Color(0xFF8B5CF6)),
    PERSONAL("Personal", "Personal", "#10B981", Color(0xFF10B981)),
    HEALTH("Health", "Health", "#F59E0B", Color(0xFFF59E0B)),
    CREATIVE("Creative", "Creative", "#EC4899", Color(0xFFEC4899));

    companion object {
        fun fromString(value: String?): FocusCategory {
            if (value.isNullOrBlank()) return WORK
            return entries.firstOrNull {
                it.id.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: WORK
        }
    }
}
