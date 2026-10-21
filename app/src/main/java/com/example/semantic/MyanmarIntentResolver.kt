package com.example.semantic

import com.example.semantic.dictionary.MyanmarAppDictionary
import com.example.semantic.dictionary.MyanmarIntentDictionary
import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import java.util.Locale

/**
 * Myanmar Intent Resolver for MSIL
 * Resolves user intent based on semantic meaning, intention verbs, semantic goals,
 * and contextual hints, independently from superficial surface wording.
 */
class MyanmarIntentResolver {

    data class ResolvedIntent(
        val intent: SemanticIntent,
        val target: String? = null,
        val baseConfidence: Float = 0.90f,
        val matchedRule: String = "DEFAULT"
    )

    fun resolveIntent(
        cleanedText: String,
        strippedText: String,
        entities: List<SemanticEntity>
    ): ResolvedIntent {
        val lowerText = cleanedText.lowercase(Locale.ROOT)
        val lowerStripped = strippedText.lowercase(Locale.ROOT)

        // 1. Check Track Navigation (Next / Previous)
        if (matchesAny(lowerStripped, MyanmarSynonymDictionary.NEXT_TRACK_SYNONYMS) ||
            (lowerStripped.contains("နောက်") && (lowerStripped.contains("ပြောင်း") || lowerStripped.contains("ကျော်") || lowerStripped.contains("ဖွင့်")) && lowerStripped.contains("ပုဒ်"))
        ) {
            return ResolvedIntent(SemanticIntent.NEXT_TRACK, target = "NEXT", baseConfidence = 0.95f, matchedRule = "NEXT_TRACK")
        }

        if (matchesAny(lowerStripped, MyanmarSynonymDictionary.PREVIOUS_TRACK_SYNONYMS) ||
            (lowerStripped.contains("ရှေ့") && lowerStripped.contains("ပြောင်း") && lowerStripped.contains("ပုဒ်"))
        ) {
            return ResolvedIntent(SemanticIntent.PREVIOUS_TRACK, target = "PREV", baseConfidence = 0.95f, matchedRule = "PREV_TRACK")
        }

        // 2. Check Music Playback (Including contextual "အဲဒီသီချင်း ပြန်ဖွင့်", "သီချင်းဖွင့်")
        if (lowerText.contains("သီချင်း") || lowerText.contains("music") || lowerText.contains("song")) {
            val songEntity = entities.firstOrNull { it.type == EntityType.SONG_TITLE }
            return ResolvedIntent(SemanticIntent.PLAY_MUSIC, target = songEntity?.value ?: "MUSIC", baseConfidence = 0.93f, matchedRule = "PLAY_MUSIC")
        }

        // 3. Check Reopen App Reference
        if (lowerText.contains("ပြန်ဖွင့်") || lowerText.contains("ပြန်ဝင်") ||
            (matchesAny(lowerText, MyanmarSynonymDictionary.APP_REFERENCES) && lowerText.contains("ဖွင့်"))
        ) {
            return ResolvedIntent(SemanticIntent.REOPEN_APP, target = "LAST_APP", baseConfidence = 0.92f, matchedRule = "REOPEN_APP")
        }

        // 3. Check Wi-Fi / Bluetooth / Flashlight Settings
        if (matchesAny(lowerText, MyanmarSynonymDictionary.WIFI_KEYWORDS)) {
            return ResolvedIntent(SemanticIntent.TOGGLE_WIFI, target = "WIFI", baseConfidence = 0.96f, matchedRule = "WIFI_TOGGLE")
        }
        if (matchesAny(lowerText, MyanmarSynonymDictionary.BLUETOOTH_KEYWORDS)) {
            return ResolvedIntent(SemanticIntent.TOGGLE_BLUETOOTH, target = "BLUETOOTH", baseConfidence = 0.96f, matchedRule = "BLUETOOTH_TOGGLE")
        }
        if (matchesAny(lowerText, MyanmarSynonymDictionary.FLASHLIGHT_KEYWORDS)) {
            return ResolvedIntent(SemanticIntent.TOGGLE_FLASHLIGHT, target = "FLASHLIGHT", baseConfidence = 0.96f, matchedRule = "FLASHLIGHT_TOGGLE")
        }

        // Screen Reading ("မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြပါ", "မျက်နှာပြင် ဖတ်ပြပါ", "screen ပေါ်က စာဖတ်ပြပါ")
        val isScreenRead = (lowerText.contains("မျက်နှာပြင်") || lowerText.contains("screen")) &&
            (lowerText.contains("ဖတ်") || lowerText.contains("read"))
        if (isScreenRead) {
            return ResolvedIntent(SemanticIntent.READ_SCREEN, target = "SCREEN", baseConfidence = 0.98f, matchedRule = "READ_SCREEN")
        }

        // 4. Check Volume Controls
        if (lowerText.contains("အသံ") || lowerText.contains("volume")) {
            if (lowerText.contains("ပိတ်") || lowerText.contains("mute")) {
                return ResolvedIntent(SemanticIntent.MUTE, target = "VOLUME", baseConfidence = 0.94f, matchedRule = "VOLUME_MUTE")
            }
            if (lowerText.contains("မြှင့်") || lowerText.contains("ကျယ်") || lowerText.contains("တင်") || lowerText.contains("louder")) {
                return ResolvedIntent(SemanticIntent.VOLUME_UP, target = "VOLUME", baseConfidence = 0.95f, matchedRule = "VOLUME_UP")
            }
            if (lowerText.contains("တိုး") || lowerText.contains("လျှော့") || lowerText.contains("ချ") || lowerText.contains("softer")) {
                return ResolvedIntent(SemanticIntent.VOLUME_DOWN, target = "VOLUME", baseConfidence = 0.95f, matchedRule = "VOLUME_DOWN")
            }
        }

        // 5. Check Alarm / Clock
        if (matchesAny(lowerText, MyanmarSynonymDictionary.ALARM_KEYWORDS)) {
            return ResolvedIntent(SemanticIntent.SET_ALARM, target = "ALARM", baseConfidence = 0.95f, matchedRule = "SET_ALARM")
        }

        // 6. Check Call & SMS Communication
        if (matchesAny(lowerText, MyanmarSynonymDictionary.CALL_ACTION_SYNONYMS)) {
            val contactEntity = entities.firstOrNull { it.type == EntityType.CONTACT_NAME || it.type == EntityType.PHONE_NUMBER }
            return ResolvedIntent(SemanticIntent.CALL_CONTACT, target = contactEntity?.value, baseConfidence = 0.94f, matchedRule = "CALL_CONTACT")
        }
        if (matchesAny(lowerText, MyanmarSynonymDictionary.SMS_ACTION_SYNONYMS)) {
            return ResolvedIntent(SemanticIntent.SEND_SMS, target = entities.firstOrNull { it.type == EntityType.CONTACT_NAME }?.value, baseConfidence = 0.94f, matchedRule = "SEND_SMS")
        }

        // 7. Check Music Playback (Including contextual "အဲဒီမှာ သီချင်းတစ်ပုဒ်ဖွင့်")
        if (lowerText.contains("သီချင်း") || lowerText.contains("music") || lowerText.contains("song")) {
            val songEntity = entities.firstOrNull { it.type == EntityType.SONG_TITLE }
            return ResolvedIntent(SemanticIntent.PLAY_MUSIC, target = songEntity?.value ?: "MUSIC", baseConfidence = 0.93f, matchedRule = "PLAY_MUSIC")
        }

        // 8. Check Camera / Photography Goal ("ကင်မရာဖွင့်", "ကင်မရာလေး ဝင်ပေး", "ဓာတ်ပုံရိုက်ချင်လို့ ကင်မရာဖွင့်ပေး", "ကင်မရာထဲ ဝင်ပေးပါဦး", "ဓာတ်ပုံရိုက်ချင်လို့ ကင်မရာဝင်ပေး")
        val isCameraGoal = lowerText.contains("ကင်မရာ") || lowerText.contains("camera") ||
            (lowerText.contains("ဓာတ်ပုံ") && (lowerText.contains("ရိုက်") || lowerText.contains("ဝင်") || lowerText.contains("ဖွင့်"))) ||
            (lowerText.contains("ဓါတ်ပုံ") && (lowerText.contains("ရိုက်") || lowerText.contains("ဝင်") || lowerText.contains("ဖွင့်")))

        if (isCameraGoal) {
            return ResolvedIntent(
                intent = SemanticIntent.OPEN_APP,
                target = "CAMERA",
                baseConfidence = 0.96f,
                matchedRule = "OPEN_CAMERA_SEMANTIC_GOAL"
            )
        }

        // 9. Check Photos / Gallery Goal
        val isPhotosGoal = (lowerText.contains("ဓာတ်ပုံ") || lowerText.contains("ဓါတ်ပုံ") || lowerText.contains("gallery") || lowerText.contains("photos") || lowerText.contains("ဂယ်လာရီ")) &&
            (lowerText.contains("ကြည့်") || lowerText.contains("ဖွင့်") || lowerText.contains("ဝင်") || lowerText.contains("ပြ"))
        if (isPhotosGoal) {
            return ResolvedIntent(
                intent = SemanticIntent.OPEN_APP,
                target = "PHOTOS",
                baseConfidence = 0.94f,
                matchedRule = "OPEN_PHOTOS_SEMANTIC_GOAL"
            )
        }

        // 10. Check General App Launch by Name / Loanword
        val appEntity = entities.firstOrNull { it.type == EntityType.APP_NAME }
        if (appEntity != null) {
            val isOpenAction = matchesAny(lowerText, MyanmarSynonymDictionary.OPEN_ACTION_SYNONYMS) || lowerText.contains("ဝင်") || lowerText.contains("ဖွင့်") || lowerText.contains("သွား")
            if (isOpenAction || lowerStripped == appEntity.rawText.lowercase(Locale.ROOT)) {
                return ResolvedIntent(
                    intent = SemanticIntent.OPEN_APP,
                    target = appEntity.value,
                    baseConfidence = 0.95f,
                    matchedRule = "OPEN_APP_BY_NAME"
                )
            }
        }

        // 11. Check Delete Data / Items
        if (lowerText.contains("ဖျက်") || lowerText.contains("delete") || lowerText.contains("ဖျက်ပစ်") || lowerText.contains("ဖယ်ရှား")) {
            return ResolvedIntent(
                intent = SemanticIntent.DELETE_ITEM,
                target = "SELECTION",
                baseConfidence = 0.95f,
                matchedRule = "DELETE_ITEM"
            )
        }

        // 12. Match against Intent Dictionary Patterns
        for (pattern in MyanmarIntentDictionary.PATTERNS) {
            val hasKeyword = pattern.keywords.any { lowerText.contains(it) }
            val hasVerb = pattern.verbStems.any { lowerText.contains(it) }
            val hasAnti = pattern.antiKeywords.any { lowerText.contains(it) }

            if (hasKeyword && hasVerb && !hasAnti) {
                val finalIntent = if (pattern.intent == SemanticIntent.OPEN_CAMERA || pattern.intent == SemanticIntent.OPEN_PHOTOS || pattern.intent == SemanticIntent.OPEN_MAPS || pattern.intent == SemanticIntent.OPEN_SETTINGS) {
                    SemanticIntent.OPEN_APP
                } else {
                    pattern.intent
                }
                return ResolvedIntent(
                    intent = finalIntent,
                    target = pattern.defaultTarget,
                    baseConfidence = pattern.minConfidence,
                    matchedRule = "DICTIONARY_PATTERN_${pattern.intent.name}"
                )
            }
        }

        // Fallback for unclassified intent
        return ResolvedIntent(
            intent = SemanticIntent.UNKNOWN,
            target = null,
            baseConfidence = 0.40f,
            matchedRule = "UNKNOWN"
        )
    }

    private fun matchesAny(text: String, keywords: List<String>): Boolean {
        for (kw in keywords) {
            if (text.contains(kw)) return true
        }
        return false
    }
}
