package com.example.semantic

import com.example.semantic.model.ConversationContext
import com.example.semantic.model.SemanticEntity
import com.example.semantic.model.SemanticIntent

/**
 * Myanmar Reasoning Engine for MSIL
 * Provides transparent semantic deductive reasoning explaining intent & slot resolutions.
 */
class MyanmarReasoningEngine {

    fun reason(
        originalText: String,
        normalizedText: String,
        intent: SemanticIntent,
        target: String?,
        entities: List<SemanticEntity>,
        resolvedReferences: Map<String, String>,
        context: ConversationContext
    ): String {
        val sb = StringBuilder()
        sb.append("Input: \"$originalText\" -> Normalized: \"$normalizedText\". ")

        when (intent) {
            SemanticIntent.OPEN_APP -> {
                if (target == "CAMERA") {
                    sb.append("Reasoning: User expressed intent to capture photos or launch camera tool. Target resolved to CAMERA.")
                } else if (target == "PHOTOS") {
                    sb.append("Reasoning: User expressed intent to view gallery or albums. Target resolved to PHOTOS.")
                } else if (target != null) {
                    sb.append("Reasoning: Direct or phonetic application launch request for target: $target.")
                } else {
                    sb.append("Reasoning: Generic application opening request without specific target.")
                }
            }
            SemanticIntent.REOPEN_APP -> {
                sb.append("Reasoning: User referenced previous app via anaphora. Context resolved to: ${target ?: context.activeAppNameMM ?: "unknown"}.")
            }
            SemanticIntent.CALL_CONTACT -> {
                sb.append("Reasoning: Communication request detected. Contact target: ${target ?: "Unspecified contact"}.")
            }
            SemanticIntent.SEND_SMS -> {
                sb.append("Reasoning: Text messaging request detected. Recipient target: ${target ?: "Unspecified contact"}.")
            }
            SemanticIntent.PLAY_MUSIC -> {
                if (resolvedReferences.isNotEmpty()) {
                    sb.append("Reasoning: User requested playing music inside active reference: ${resolvedReferences.values.firstOrNull()}.")
                } else {
                    sb.append("Reasoning: Media playback request. Target track/artist: ${target ?: "default music"}.")
                }
            }
            SemanticIntent.NEXT_TRACK -> {
                sb.append("Reasoning: User requested skipping or changing to next media track.")
            }
            SemanticIntent.PREVIOUS_TRACK -> {
                sb.append("Reasoning: User requested returning to previous media track.")
            }
            SemanticIntent.VOLUME_DOWN -> {
                val hasSmall = entities.any { it.value == "SMALL" }
                val stepDesc = if (hasSmall) "small step" else "standard step"
                sb.append("Reasoning: Audio decrease command detected ($stepDesc).")
            }
            SemanticIntent.VOLUME_UP -> {
                sb.append("Reasoning: Audio increase command detected.")
            }
            SemanticIntent.TOGGLE_WIFI -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                sb.append("Reasoning: Wi-Fi connectivity toggle requested with target state: $state.")
            }
            SemanticIntent.TOGGLE_BLUETOOTH -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                sb.append("Reasoning: Bluetooth connectivity toggle requested with target state: $state.")
            }
            SemanticIntent.TOGGLE_FLASHLIGHT -> {
                val state = entities.firstOrNull { it.metadata["state"] != null }?.metadata?.get("state") ?: "TOGGLE"
                sb.append("Reasoning: Device flashlight toggle requested with target state: $state.")
            }
            SemanticIntent.SET_ALARM -> {
                val timeVal = entities.firstOrNull { it.type == com.example.semantic.model.EntityType.TIME_EXPRESSION }?.value ?: "unspecified time"
                sb.append("Reasoning: Alarm scheduled for $timeVal.")
            }
            else -> {
                sb.append("Reasoning: Intent mapped to $intent.")
            }
        }

        return sb.toString()
    }
}
