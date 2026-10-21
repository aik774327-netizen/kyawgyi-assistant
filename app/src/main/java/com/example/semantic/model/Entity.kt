package com.example.semantic.model

/**
 * Myanmar Semantic Intelligence Layer (MSIL) - Semantic Entity Types & Representations
 */
enum class EntityType {
    APP_NAME,
    APP_PACKAGE,
    CONTACT_NAME,
    PHONE_NUMBER,
    MESSAGE_BODY,
    SONG_TITLE,
    ARTIST_NAME,
    TIME_EXPRESSION,
    DATE_EXPRESSION,
    LOCATION,
    VOLUME_LEVEL,
    SETTING_NAME,
    SETTING_STATE,
    TARGET_REFERENCE,
    RAW_QUERY
}

data class SemanticEntity(
    val type: EntityType,
    val value: String,
    val rawText: String = value,
    val confidence: Float = 1.0f,
    val normalizedValue: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
