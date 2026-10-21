package com.example.semantic

import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Myanmar Context Resolver for MSIL
 * Merges extracted entities with active conversational context and resolved references.
 */
class MyanmarContextResolver {

    data class ContextualizedSlots(
        val targetApp: String? = null,
        val targetPackage: String? = null,
        val targetContact: String? = null,
        val targetPhoneNumber: String? = null,
        val targetSetting: String? = null,
        val targetMedia: String? = null,
        val targetTime: String? = null,
        val isContextInherited: Boolean = false
    )

    fun resolveContextualSlots(
        intent: SemanticIntent,
        entities: List<SemanticEntity>,
        resolvedReferences: Map<String, MyanmarReferenceResolver.ResolvedReference>,
        context: ConversationContext
    ): ContextualizedSlots {
        var targetApp = entities.firstOrNull { it.type == EntityType.APP_NAME }?.value
        var targetPackage = entities.firstOrNull { it.type == EntityType.APP_PACKAGE }?.value
        var targetContact = entities.firstOrNull { it.type == EntityType.CONTACT_NAME }?.value
        var targetPhoneNumber = entities.firstOrNull { it.type == EntityType.PHONE_NUMBER }?.value
        var targetSetting = entities.firstOrNull { it.type == EntityType.SETTING_NAME }?.value
        var targetMedia = entities.firstOrNull { it.type == EntityType.SONG_TITLE }?.value
        val targetTime = entities.firstOrNull { it.type == EntityType.TIME_EXPRESSION }?.value
        var isInherited = false

        // Check resolved references
        for ((_, ref) in resolvedReferences) {
            when (ref.entityType) {
                EntityType.APP_NAME -> {
                    if (targetApp == null) {
                        targetApp = ref.resolvedValue
                        isInherited = true
                    }
                }
                EntityType.CONTACT_NAME -> {
                    if (targetContact == null) {
                        targetContact = ref.resolvedValue
                        isInherited = true
                    }
                }
                EntityType.SONG_TITLE -> {
                    if (targetMedia == null) {
                        targetMedia = ref.resolvedValue
                        isInherited = true
                    }
                }
                else -> {}
            }
        }

        // Fallback to active context if intent requires it
        if (targetApp == null && (intent == SemanticIntent.REOPEN_APP || (intent == SemanticIntent.PLAY_MUSIC && context.activeAppNameMM != null))) {
            targetApp = context.activeAppNameMM
            targetPackage = context.activeAppPackage
            isInherited = true
        }

        if (targetContact == null && intent == SemanticIntent.CALL_CONTACT && context.activeContact != null) {
            targetContact = context.activeContact
            targetPhoneNumber = context.activePhoneNumber
            isInherited = true
        }

        return ContextualizedSlots(
            targetApp = targetApp,
            targetPackage = targetPackage,
            targetContact = targetContact,
            targetPhoneNumber = targetPhoneNumber,
            targetSetting = targetSetting,
            targetMedia = targetMedia,
            targetTime = targetTime,
            isContextInherited = isInherited
        )
    }
}
