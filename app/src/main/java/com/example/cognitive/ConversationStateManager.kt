package com.example.cognitive

import com.example.semantic.dictionary.MyanmarAppDictionary
import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.EntityType
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent
import java.util.Locale

/**
 * Multi-Turn Conversation State Manager for Cognitive Intelligence
 */
class ConversationStateManager(
    val workingMemory: WorkingMemory,
    val episodicMemory: EpisodicMemory,
    val semanticMemory: SemanticMemory,
    val longTermMemory: LongTermMemory
) {

    /**
     * Resolves references ("အဲဒီ App", "အဲဒီမှာ", "အဲဒီသီချင်း", "ဒီသီချင်း", "သူ့ကို", "အရင်ကဖွင့်ထားတာ", "ပြန်ဖွင့်")
     * across Working Memory, Episodic History, Semantic Memory, and Long-Term Memory.
     */
    fun resolveReference(utterance: String): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        val clean = utterance.trim().lowercase(Locale.ROOT)

        // 1. Container / App Pronoun ("အဲဒီမှာ", "အဲဒီထဲ")
        for (ref in MyanmarSynonymDictionary.CONTAINER_REFERENCES) {
            if (clean.contains(ref)) {
                val app = workingMemory.lastApp
                    ?: episodicMemory.findLastEventWithTarget("YOUTUBE")?.target
                    ?: episodicMemory.findLastEventMatching { it.intent == SemanticIntent.OPEN_APP }?.target
                    ?: longTermMemory.getPreference("PREFERRED_MUSIC_APP")
                if (app != null) {
                    resolved[ref] = app
                }
                break
            }
        }

        // 2. Direct App Pronoun ("အဲဒီ App", "အဲဒါ", "အဲဒီဟာ")
        for (ref in MyanmarSynonymDictionary.APP_REFERENCES) {
            if (clean.contains(ref)) {
                val app = workingMemory.lastApp
                    ?: episodicMemory.findLastEventMatching { it.intent == SemanticIntent.OPEN_APP }?.target
                if (app != null) {
                    resolved[ref] = app
                }
                break
            }
        }

        // 3. Media Pronoun ("အဲဒီသီချင်း", "ဒီသီချင်း", "အရင်ကဖွင့်ထားတာ", "အရင်သီချင်း")
        if (clean.contains("အဲဒီသီချင်း") || clean.contains("ဒီသီချင်း") || clean.contains("အရင်ကဖွင့်ထားတာ") || clean.contains("အရင်သီချင်း") || clean.contains("အရင်ဟာ")) {
            val song = workingMemory.lastSong
                ?: workingMemory.lastMedia
                ?: episodicMemory.findLastEventMatching { it.intent == SemanticIntent.PLAY_MUSIC }?.target
                ?: episodicMemory.getLastEvent()?.parameters?.get("songTitle")?.toString()
            if (song != null) {
                resolved["MEDIA_REFERENCE"] = song
            }
        }

        // 4. Contact Pronoun ("သူ့ကို", "သူ့ဆီ", "အဲဒီလူ")
        for (ref in MyanmarSynonymDictionary.CONTACT_REFERENCES) {
            if (clean.contains(ref)) {
                val contact = workingMemory.lastContact
                    ?: episodicMemory.findLastEventMatching { it.intent == SemanticIntent.CALL_CONTACT || it.intent == SemanticIntent.SEND_SMS }?.target
                if (contact != null) {
                    resolved[ref] = contact
                }
                break
            }
        }

        return resolved
    }

    fun isFollowUpCommand(utterance: String): Boolean {
        val clean = utterance.trim().lowercase(Locale.ROOT)
        // Check for deictic markers, ellipsis, or pronoun triggers
        return clean.startsWith("ပြီးရင်") ||
            clean.startsWith("ပြီးတော့") ||
            clean.startsWith("အဲဒီ") ||
            clean.startsWith("အဲ့ဒီ") ||
            clean.startsWith("သူ့") ||
            clean.startsWith("အဲဒါ") ||
            clean.startsWith("ပြန်") ||
            clean.contains("နောက်တစ်ပုဒ်") ||
            clean.contains("ရှေ့တစ်ပုဒ်") ||
            workingMemory.pendingQuestion != null
    }

    fun recordTurn(
        rawUtterance: String,
        normalizedUtterance: String,
        intent: SemanticIntent,
        target: String?,
        parameters: Map<String, Any?>,
        entities: List<SemanticEntity>,
        outcomeStatus: String = "SUCCESS"
    ) {
        val event = EpisodicEvent(
            rawUtterance = rawUtterance,
            normalizedUtterance = normalizedUtterance,
            intent = intent,
            target = target,
            parameters = parameters,
            entities = entities,
            outcomeStatus = outcomeStatus,
            turnNumber = workingMemory.conversationTurn
        )
        episodicMemory.recordEvent(event)

        val appName = parameters["appName"] as? String ?: (if (intent == SemanticIntent.OPEN_APP || intent == SemanticIntent.REOPEN_APP) target else null)
        val packageName = parameters["packageName"] as? String
        val contactName = parameters["contactName"] as? String ?: (if (intent == SemanticIntent.CALL_CONTACT) target else null)
        val phoneNumber = parameters["phoneNumber"] as? String
        val songTitle = parameters["songTitle"] as? String ?: (if (intent == SemanticIntent.PLAY_MUSIC) target else null)

        workingMemory.updateState(
            app = appName,
            packageName = packageName,
            contact = contactName,
            phone = phoneNumber,
            song = songTitle,
            intent = intent,
            action = intent.name,
            entities = entities
        )
    }

    fun reset() {
        workingMemory.reset()
        episodicMemory.clear()
    }
}
