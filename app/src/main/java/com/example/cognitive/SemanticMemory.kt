package com.example.cognitive

import com.example.semantic.dictionary.MyanmarAppDictionary
import com.example.semantic.dictionary.MyanmarIntentDictionary
import com.example.semantic.dictionary.MyanmarSynonymDictionary
import com.example.semantic.model.SemanticIntent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Semantic Knowledge Node representing concept mappings, ontological relations, and synonyms.
 */
data class SemanticConcept(
    val canonicalKey: String,
    val synonyms: Set<String>,
    val category: String,
    val targetIntent: SemanticIntent? = null,
    val defaultTarget: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * SemanticMemory holds normalized semantic domain knowledge,
 * ontological concepts, Myanmar linguistic variations, and learned semantic generalizations.
 */
class SemanticMemory {

    private val concepts = ConcurrentHashMap<String, SemanticConcept>()

    init {
        loadBuiltInOntology()
    }

    private fun loadBuiltInOntology() {
        // 1. Camera concept
        registerConcept(
            SemanticConcept(
                canonicalKey = "CAMERA",
                synonyms = setOf(
                    "ကင်မရာ", "ကင်မရာထဲ", "ကင်မရာလေး", "ဓာတ်ပုံရိုက်ဖို့ ကင်မရာဖွင့်",
                    "ဓါတ်ပုံရိုက်ဖို့ ကင်မရာဖွင့်", "ကင်မရာထဲဝင်", "photo ရိုက်", "cam"
                ),
                category = "APPLICATION",
                targetIntent = SemanticIntent.OPEN_APP,
                defaultTarget = "CAMERA"
            )
        )

        // 2. Photos & Gallery
        registerConcept(
            SemanticConcept(
                canonicalKey = "PHOTOS",
                synonyms = setOf(
                    "ဓာတ်ပုံ", "ဓာတ်ပုံများ", "ဂယ်လာရီ", "gallery", "photos", "ပုံတွေ"
                ),
                category = "APPLICATION",
                targetIntent = SemanticIntent.OPEN_APP,
                defaultTarget = "PHOTOS"
            )
        )

        // 3. Volume concepts
        registerConcept(
            SemanticConcept(
                canonicalKey = "INCREASE_VOLUME",
                synonyms = setOf(
                    "အသံတိုး", "အသံနည်းနည်းကျယ်", "အသံတက်ပေး", "အသံမြှင့်", "အသံကျယ်ပေး", "volume up"
                ),
                category = "DEVICE_CONTROL",
                targetIntent = SemanticIntent.VOLUME_UP,
                defaultTarget = "VOLUME"
            )
        )

        registerConcept(
            SemanticConcept(
                canonicalKey = "DECREASE_VOLUME",
                synonyms = setOf(
                    "အသံလျှော့", "အသံချ", "အသံနည်းနည်းတိုး", "အသံတိုးပေး", "volume down"
                ),
                category = "DEVICE_CONTROL",
                targetIntent = SemanticIntent.VOLUME_DOWN,
                defaultTarget = "VOLUME"
            )
        )

        // 4. Media navigation
        registerConcept(
            SemanticConcept(
                canonicalKey = "NEXT_TRACK",
                synonyms = setOf(
                    "နောက်သီချင်းပြောင်း", "နောက်တစ်ပုဒ်", "နောက်သီချင်း", "next song", "skip"
                ),
                category = "MEDIA",
                targetIntent = SemanticIntent.NEXT_TRACK,
                defaultTarget = "NEXT"
            )
        )

        registerConcept(
            SemanticConcept(
                canonicalKey = "PREVIOUS_TRACK",
                synonyms = setOf(
                    "ရှေ့သီချင်းပြောင်း", "ရှေ့တစ်ပုဒ်", "အရင်တစ်ပုဒ်", "previous song", "prev"
                ),
                category = "MEDIA",
                targetIntent = SemanticIntent.PREVIOUS_TRACK,
                defaultTarget = "PREV"
            )
        )
    }

    fun registerConcept(concept: SemanticConcept) {
        concepts[concept.canonicalKey.uppercase(Locale.ROOT)] = concept
    }

    fun findConceptForUtterance(utterance: String): SemanticConcept? {
        val clean = utterance.trim().lowercase(Locale.ROOT)
        for (concept in concepts.values) {
            for (synonym in concept.synonyms) {
                if (clean.contains(synonym.lowercase(Locale.ROOT))) {
                    return concept
                }
            }
        }
        return null
    }

    fun getConcept(canonicalKey: String): SemanticConcept? {
        return concepts[canonicalKey.uppercase(Locale.ROOT)]
    }

    fun learnSynonym(canonicalKey: String, newSynonym: String) {
        val existing = concepts[canonicalKey.uppercase(Locale.ROOT)]
        if (existing != null) {
            val updated = existing.copy(synonyms = existing.synonyms + newSynonym.trim())
            concepts[canonicalKey.uppercase(Locale.ROOT)] = updated
        }
    }
}
