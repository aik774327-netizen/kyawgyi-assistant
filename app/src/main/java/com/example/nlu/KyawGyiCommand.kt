package com.example.nlu

/**
 * Stage 1 - Kyaw Gyi AI Command Data Models
 * Clean structured representation of user intent, entities, parameters, and confidence.
 */

enum class KyawGyiIntent {
    OPEN_APP,
    CLOSE_APP,
    CALL,
    SEND_SMS,
    PLAY_MUSIC,
    PAUSE_MEDIA,
    RESUME_MEDIA,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    OPEN_CAMERA,
    OPEN_PHOTOS,
    OPEN_MAPS,
    OPEN_MESSAGES,
    OPEN_PHONE,
    OPEN_SETTINGS,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    SEARCH,
    STOP,
    CANCEL,
    UNKNOWN
}

enum class EntityType {
    CONTACT,
    PHONE_NUMBER,
    APP_NAME,
    SONG_NAME,
    ARTIST_NAME,
    MESSAGE_TEXT,
    LOCATION,
    NUMBER,
    TIME,
    DATE,
    MEDIA_TYPE,
    RAW_QUERY
}

enum class CommandSource {
    VOICE,
    TEXT,
    SHORTCUT,
    WEBHOOK,
    ACCESSIBILITY
}

enum class ConfidenceLevel {
    HIGH,      // >= 0.75 -> safe to execute
    MEDIUM,    // 0.45..0.74 -> ask for clarification
    LOW        // < 0.45 -> do not execute, ask user what they mean
}

enum class PolitenessLevel {
    POLITE,    // e.g. ပါ, ပါဦး, ခင်ဗျာ, ရှင်, လေး
    CASUAL,    // e.g. လိုက်, ဦး, နော်
    DIRECT,    // e.g. ဖွင့်, ပိတ်, ဆက်
    URGENT     // e.g. အခုချက်ချင်း, အမြန်
}

enum class SemanticAction {
    OPEN,
    CLOSE,
    CALL,
    SEND,
    PLAY,
    PAUSE,
    RESUME,
    NEXT,
    PREVIOUS,
    ADJUST_VOLUME,
    MUTE,
    SEARCH,
    STOP,
    CANCEL,
    UNKNOWN
}

enum class SemanticTarget {
    APPLICATION,
    CONTACT,
    PHONE_NUMBER,
    MEDIA,
    CAMERA,
    PHOTOS,
    MAPS,
    MESSAGING,
    SETTINGS,
    DEVICE_AUDIO,
    SYSTEM,
    WEB_QUERY,
    UNKNOWN
}

data class KyawGyiEntity(
    val type: EntityType,
    val value: String,
    val rawText: String = value,
    val confidence: Float = 1.0f
)

data class SemanticRepresentation(
    val action: SemanticAction,
    val target: SemanticTarget,
    val politeness: PolitenessLevel = PolitenessLevel.CASUAL,
    val userIntention: String = "COMMAND",
    val destination: String? = null,
    val parameters: Map<String, Any?> = emptyMap(),
    val temporalInfo: String? = null,
    val confidence: Float = 0.9f
)

data class KyawGyiCommand(
    val intent: KyawGyiIntent,
    val entities: Map<EntityType, String> = emptyMap(),
    val entityList: List<KyawGyiEntity> = emptyList(),
    val parameters: Map<String, Any?> = emptyMap(),
    val semanticRepresentation: SemanticRepresentation? = null,
    val originalText: String,
    val normalizedText: String,
    val confidence: Float,
    val requiresConfirmation: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val source: CommandSource = CommandSource.VOICE
) {
    val confidenceLevel: ConfidenceLevel
        get() = when {
            confidence >= 0.75f -> ConfidenceLevel.HIGH
            confidence >= 0.45f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

    // Convenience property accessors
    val contact: String? get() = entities[EntityType.CONTACT]
    val phoneNumber: String? get() = entities[EntityType.PHONE_NUMBER]
    val appName: String? get() = entities[EntityType.APP_NAME]
    val songName: String? get() = entities[EntityType.SONG_NAME]
    val artistName: String? get() = entities[EntityType.ARTIST_NAME]
    val messageText: String? get() = entities[EntityType.MESSAGE_TEXT]
    val location: String? get() = entities[EntityType.LOCATION]
    val mediaType: String? get() = entities[EntityType.MEDIA_TYPE]
    val query: String? get() = entities[EntityType.RAW_QUERY] ?: songName ?: appName ?: messageText
}
