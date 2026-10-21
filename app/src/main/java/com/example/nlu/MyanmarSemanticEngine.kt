package com.example.nlu

import com.example.engine.CommandContextManager

/**
 * Stage 1 - Myanmar Semantic Engine
 *
 * Deconstructs the natural Myanmar utterance into high-level semantic tokens:
 * - User Intention (Command, Query, Request, Cancellation)
 * - Semantic Action (OPEN, CLOSE, CALL, SEND, PLAY, PAUSE, RESUME, NEXT, PREVIOUS, ADJUST_VOLUME, MUTE, SEARCH, STOP, CANCEL)
 * - Semantic Target (APPLICATION, CONTACT, PHONE_NUMBER, MEDIA, CAMERA, PHOTOS, MAPS, MESSAGING, SETTINGS, DEVICE_AUDIO, SYSTEM, WEB_QUERY)
 * - Person / Contact / Destination
 * - Temporal information
 * - Politeness level
 * - Contextual inheritance from Multi-Turn conversation state
 */
object MyanmarSemanticEngine {

    fun analyzeSemantics(
        originalText: String,
        normalizedText: String,
        entities: Map<EntityType, String>,
        conversationContext: CommandContextManager.ConversationContext? = null
    ): SemanticRepresentation {
        val politeness = MyanmarTextNormalizer.extractPoliteness(originalText)
        val temporalInfo = entities[EntityType.TIME] ?: entities[EntityType.DATE]

        val action = determineAction(originalText, normalizedText)
        val target = determineTarget(originalText, normalizedText, entities, conversationContext)

        val parameters = mutableMapOf<String, Any?>()
        entities[EntityType.CONTACT]?.let { parameters["contact"] = it }
        entities[EntityType.PHONE_NUMBER]?.let { parameters["phoneNumber"] = it }
        entities[EntityType.APP_NAME]?.let { parameters["appName"] = it }
        entities[EntityType.SONG_NAME]?.let { parameters["songName"] = it }
        entities[EntityType.ARTIST_NAME]?.let { parameters["artistName"] = it }
        entities[EntityType.MESSAGE_TEXT]?.let { parameters["messageText"] = it }
        entities[EntityType.LOCATION]?.let { parameters["location"] = it }
        entities[EntityType.MEDIA_TYPE]?.let { parameters["mediaType"] = it }

        // Contextual inheritance
        if (conversationContext != null) {
            if (!parameters.containsKey("appName") && conversationContext.activeAppNameMM != null) {
                parameters["inheritedAppName"] = conversationContext.activeAppNameMM
            }
            if (!parameters.containsKey("artistName") && conversationContext.activeArtist != null) {
                parameters["inheritedArtist"] = conversationContext.activeArtist
            }
        }

        val intention = when (action) {
            SemanticAction.CANCEL, SemanticAction.STOP -> "CANCELLATION"
            SemanticAction.SEARCH -> "QUERY"
            SemanticAction.UNKNOWN -> "UNKNOWN"
            else -> "COMMAND"
        }

        val destination = entities[EntityType.LOCATION] ?: entities[EntityType.CONTACT]

        return SemanticRepresentation(
            action = action,
            target = target,
            politeness = politeness,
            userIntention = intention,
            destination = destination,
            parameters = parameters,
            temporalInfo = temporalInfo,
            confidence = calculateSemanticConfidence(action, target, entities)
        )
    }

    private fun determineAction(originalText: String, normalizedText: String): SemanticAction {
        val text = "$originalText $normalizedText"
        return when {
            text.contains("မလုပ်နဲ့") || text.contains("ဖျက်လိုက်") || text.contains("cancel") -> SemanticAction.CANCEL
            text.contains("တော်ပြီ") || text.contains("ရပ်လိုက်") || text.contains("stop") -> SemanticAction.STOP
            text.contains("ဆက်ဖွင့်") || text.contains("ပြန်ဖွင့်") || text.contains("resume") -> SemanticAction.RESUME
            text.contains("ခဏရပ်") || text.contains("ခဏနား") || text.contains("pause") -> SemanticAction.PAUSE
            text.contains("နောက်တစ်ပုဒ်") || text.contains("next") -> SemanticAction.NEXT
            text.contains("ရှေ့တစ်ပုဒ်") || text.contains("previous") || text.contains("prev") -> SemanticAction.PREVIOUS
            text.contains("အသံပိတ်") || text.contains("mute") || text.contains("တိတ်") -> SemanticAction.MUTE
            text.contains("အသံ") && (text.contains("ကျယ်") || text.contains("တိုး") || text.contains("လျှော့") || text.contains("မြှင့်") || text.contains("volume")) -> SemanticAction.ADJUST_VOLUME
            text.contains("ဖုန်းဆက်") || text.contains("ဖုန်းခေါ်") || text.contains("call") || text.contains("ခေါ်") || text.contains("ဆက်") -> SemanticAction.CALL
            text.contains("message") || text.contains("sms") || text.contains("စာပို့") || text.contains("ပို့ပေး") || text.contains("ပို့") -> SemanticAction.SEND
            text.contains("သီချင်း") || text.contains("music") || text.contains("play") || text.contains("ဖွင့်ပြ") -> SemanticAction.PLAY
            text.contains("ပိတ်") || text.contains("close") || text.contains("exit") -> SemanticAction.CLOSE
            text.contains("ဖွင့်") || text.contains("open") || text.contains("launch") || text.contains("start") -> SemanticAction.OPEN
            text.contains("ရှာ") || text.contains("search") || text.contains("google") -> SemanticAction.SEARCH
            else -> SemanticAction.UNKNOWN
        }
    }

    private fun determineTarget(
        originalText: String,
        normalizedText: String,
        entities: Map<EntityType, String>,
        conversationContext: CommandContextManager.ConversationContext?
    ): SemanticTarget {
        val text = "$originalText $normalizedText"
        return when {
            entities.containsKey(EntityType.PHONE_NUMBER) -> SemanticTarget.PHONE_NUMBER
            entities.containsKey(EntityType.CONTACT) -> SemanticTarget.CONTACT
            text.contains("ကင်မရာ") || text.contains("camera") -> SemanticTarget.CAMERA
            text.contains("ဓာတ်ပုံ") || text.contains("photos") || text.contains("gallery") -> SemanticTarget.PHOTOS
            text.contains("မြေပုံ") || text.contains("maps") -> SemanticTarget.MAPS
            text.contains("မက်ဆေ့") || text.contains("messages") || text.contains("sms") -> SemanticTarget.MESSAGING
            text.contains("ဆက်တင်") || text.contains("settings") -> SemanticTarget.SETTINGS
            text.contains("အသံ") || text.contains("volume") -> SemanticTarget.DEVICE_AUDIO
            entities.containsKey(EntityType.SONG_NAME) || entities.containsKey(EntityType.ARTIST_NAME) || text.contains("သီချင်း") || text.contains("music") -> SemanticTarget.MEDIA
            entities.containsKey(EntityType.APP_NAME) -> SemanticTarget.APPLICATION
            conversationContext?.activeAppNameMM != null -> SemanticTarget.APPLICATION
            text.contains("ရှာ") || text.contains("search") -> SemanticTarget.WEB_QUERY
            else -> SemanticTarget.UNKNOWN
        }
    }

    private fun calculateSemanticConfidence(
        action: SemanticAction,
        target: SemanticTarget,
        entities: Map<EntityType, String>
    ): Float {
        if (action == SemanticAction.UNKNOWN && target == SemanticTarget.UNKNOWN) return 0.25f
        if (action == SemanticAction.UNKNOWN || target == SemanticTarget.UNKNOWN) return 0.60f

        var score = 0.85f
        if (entities.isNotEmpty()) score += 0.10f
        return minOf(1.0f, score)
    }
}
