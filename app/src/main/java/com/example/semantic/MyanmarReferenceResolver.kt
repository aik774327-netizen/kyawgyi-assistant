package com.example.semantic

import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.ConversationContext
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import java.util.Locale

/**
 * Myanmar Reference Resolver for MSIL
 * Resolves anaphoric expressions ("အဲဒီမှာ", "အဲဒီ App", "ဒီသီချင်း", "သူ့ဆီ")
 * against the active ConversationContext.
 */
class MyanmarReferenceResolver {

    data class ResolvedReference(
        val originalReference: String,
        val resolvedValue: String,
        val entityType: EntityType,
        val confidence: Float = 0.95f
    )

    fun resolveReferences(
        normalizedText: String,
        context: ConversationContext
    ): Map<String, ResolvedReference> {
        val resolved = mutableMapOf<String, ResolvedReference>()
        val textLower = normalizedText.lowercase(Locale.ROOT)

        // 1. Container / App Context Reference ("အဲဒီမှာ" -> active app / YouTube)
        for (ref in MyanmarSynonymDictionary.CONTAINER_REFERENCES) {
            if (textLower.contains(ref)) {
                val appName = context.activeAppNameMM ?: context.findMemory("LAST_APP")?.value
                if (appName != null) {
                    resolved[ref] = ResolvedReference(
                        originalReference = ref,
                        resolvedValue = appName,
                        entityType = EntityType.APP_NAME,
                        confidence = 0.95f
                    )
                }
                break
            }
        }

        // 2. Direct App Pronouns ("အဲဒီ App ကို ပြန်ဖွင့်")
        for (ref in MyanmarSynonymDictionary.APP_REFERENCES) {
            if (textLower.contains(ref)) {
                val appName = context.activeAppNameMM ?: context.findMemory("LAST_APP")?.value
                if (appName != null) {
                    resolved[ref] = ResolvedReference(
                        originalReference = ref,
                        resolvedValue = appName,
                        entityType = EntityType.APP_NAME,
                        confidence = 0.95f
                    )
                }
                break
            }
        }

        // 3. Media Pronouns ("ဒီသီချင်းကို နောက်တစ်ပုဒ်ပြောင်း")
        for (ref in MyanmarSynonymDictionary.MEDIA_REFERENCES) {
            if (textLower.contains(ref)) {
                val songTitle = context.activeSongTitle ?: context.findMemory("LAST_SONG")?.value ?: "CURRENT_TRACK"
                resolved[ref] = ResolvedReference(
                    originalReference = ref,
                    resolvedValue = songTitle,
                    entityType = EntityType.SONG_TITLE,
                    confidence = 0.95f
                )
                break
            }
        }

        // 4. Contact Pronouns ("သူ့ဆီ", "သူ့ကို")
        for (ref in MyanmarSynonymDictionary.CONTACT_REFERENCES) {
            if (textLower.contains(ref)) {
                val contact = context.activeContact ?: context.findMemory("LAST_CONTACT")?.value
                if (contact != null) {
                    resolved[ref] = ResolvedReference(
                        originalReference = ref,
                        resolvedValue = contact,
                        entityType = EntityType.CONTACT_NAME,
                        confidence = 0.95f
                    )
                }
                break
            }
        }

        return resolved
    }
}
