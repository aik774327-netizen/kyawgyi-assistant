package com.example.cognitive

import com.example.semantic.model.SemanticIntent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-Level Goal Types
 */
enum class GoalType {
    IMMEDIATE_ACTION,
    WAKE_UP,
    COMMUNICATION,
    MEDIA_CONSUMPTION,
    TASK_MANAGEMENT,
    INFORMATION_SEEKING,
    SYSTEM_CONFIG,
    AMBIGUOUS_GOAL
}

enum class GoalStatus {
    ACTIVE,
    PENDING_CLARIFICATION,
    COMPLETED,
    CANCELLED
}

/**
 * Goal Representation with Missing Slots tracking
 */
data class Goal(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: GoalType,
    val descriptionMM: String,
    val targetIntent: SemanticIntent,
    val extractedSlots: Map<String, Any?> = emptyMap(),
    val missingSlots: List<String> = emptyList(),
    val clarificationQuestionMM: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * GoalManager identifies user intentions from conversational cues,
 * classifies discourse type (COMMAND, QUESTION, REQUEST, GOAL, STATEMENT, FOLLOW_UP),
 * and tracks goals requiring missing information.
 */
class GoalManager {

    enum class DiscourseType {
        COMMAND,
        QUESTION,
        REQUEST,
        GOAL,
        STATEMENT,
        FOLLOW_UP
    }

    private val activeGoals = CopyOnWriteArrayList<Goal>()

    fun classifyDiscourse(utterance: String, isFollowUp: Boolean): DiscourseType {
        val clean = utterance.trim()
        if (isFollowUp) return DiscourseType.FOLLOW_UP

        if (clean.endsWith("လား") || clean.endsWith("လဲ") || clean.contains("ဘယ်") || clean.contains("ဘာလဲ")) {
            return DiscourseType.QUESTION
        }

        // Implicit Goal / Future necessity (e.g. "မနက်ဖြန် မနက်စောစောထရမယ်", "မနက်ဖြန် Alarm ထား")
        if ((clean.contains("ရမယ်") || clean.contains("ထရမယ်") || clean.contains("သွားရမယ်") || clean.contains("လုပ်ရမယ်")) ||
            (clean.contains("မနက်ဖြန်") && clean.contains("Alarm") && !clean.contains("နာရီ") && !clean.contains(":"))
        ) {
            return DiscourseType.GOAL
        }

        if (clean.contains("ပေးပါ") || clean.contains("ပါဦး") || clean.contains("ပါဦး") || clean.contains("ပေး")) {
            return DiscourseType.REQUEST
        }

        return DiscourseType.COMMAND
    }

    fun detectGoal(
        utterance: String,
        extractedSlots: Map<String, Any?>,
        workingMemory: WorkingMemory
    ): Goal? {
        val clean = utterance.trim()

        // 1. Wake Up / Alarm Goal with Missing Time
        // e.g. "မနက်ဖြန် မနက်စောစောထရမယ်", "မနက်ဖြန် Alarm ထား", "Alarm ပေး"
        val hasTime = extractedSlots.containsKey("TIME_EXPRESSION") || extractedSlots.containsKey("time")
        val isAlarmWakeUpGoal = clean.contains("ထရမယ်") ||
            clean.contains("စောစောထ") ||
            ((clean.contains("alarm") || clean.contains("နှိုးစက်")) && !hasTime)

        if (isAlarmWakeUpGoal && !hasTime) {
            val goal = Goal(
                type = GoalType.WAKE_UP,
                descriptionMM = "နှိုးစက်သတ်မှတ်ခြင်း",
                targetIntent = SemanticIntent.SET_ALARM,
                extractedSlots = extractedSlots,
                missingSlots = listOf("ALARM_TIME"),
                clarificationQuestionMM = "ဘယ်အချိန် Alarm ထားပေးရမလဲခင်ဗျာ။",
                status = GoalStatus.PENDING_CLARIFICATION
            )
            activeGoals.add(0, goal)
            return goal
        }

        // 2. Communication Goal with Missing Contact
        // e.g. "ဖုန်းခေါ်ပေး", "မက်ဆေ့ပို့"
        val hasContact = extractedSlots.containsKey("CONTACT_NAME") || extractedSlots.containsKey("PHONE_NUMBER") ||
            extractedSlots.containsKey("contactName") || extractedSlots.containsKey("phoneNumber")
        if ((clean.contains("ဖုန်းဆက်") || clean.contains("ဖုန်းခေါ်")) && !hasContact) {
            val goal = Goal(
                type = GoalType.COMMUNICATION,
                descriptionMM = "ဖုန်းခေါ်ဆိုခြင်း",
                targetIntent = SemanticIntent.CALL_CONTACT,
                extractedSlots = extractedSlots,
                missingSlots = listOf("CONTACT_NAME"),
                clarificationQuestionMM = "မည်သူ့ထံ ဖုန်းခေါ်ဆိုပေးရမလဲခင်ဗျာ။",
                status = GoalStatus.PENDING_CLARIFICATION
            )
            activeGoals.add(0, goal)
            return goal
        }

        // 3. Media Consumption Goal with Ambiguous Target
        val hasSong = extractedSlots.containsKey("SONG_TITLE") || extractedSlots.containsKey("songTitle")
        if (clean == "သီချင်းဖွင့်" && !hasSong && workingMemory.lastSong == null && workingMemory.lastApp == null) {
            val goal = Goal(
                type = GoalType.MEDIA_CONSUMPTION,
                descriptionMM = "သီချင်းဖွင့်ခြင်း",
                targetIntent = SemanticIntent.PLAY_MUSIC,
                extractedSlots = extractedSlots,
                missingSlots = listOf("MEDIA_TITLE"),
                clarificationQuestionMM = "မည်သည့်သီချင်း (သို့မဟုတ် အဆိုတော်) ကို ဖွင့်ပေးရမလဲခင်ဗျာ။",
                status = GoalStatus.PENDING_CLARIFICATION
            )
            activeGoals.add(0, goal)
            return goal
        }

        return null
    }

    fun getPendingGoal(): Goal? {
        return activeGoals.firstOrNull { it.status == GoalStatus.PENDING_CLARIFICATION }
    }

    fun resolvePendingGoal(goalId: String, newSlots: Map<String, Any?>): Goal? {
        val goal = activeGoals.firstOrNull { it.id == goalId } ?: return null
        val updatedSlots = goal.extractedSlots + newSlots
        val remainingMissing = goal.missingSlots.filter { !updatedSlots.containsKey(it) }
        val updated = goal.copy(
            extractedSlots = updatedSlots,
            missingSlots = remainingMissing,
            status = if (remainingMissing.isEmpty()) GoalStatus.COMPLETED else GoalStatus.PENDING_CLARIFICATION
        )
        activeGoals.remove(goal)
        activeGoals.add(0, updated)
        return updated
    }

    fun clear() {
        activeGoals.clear()
    }
}
