package com.example.engine

import android.util.Log

/**
 * Multi-Turn Conversational Command Context Manager
 * Handles entity tracking across multi-step user voice interactions.
 * E.g.,
 * Turn 1: "ကျော်ကြီးရေ YouTube ဖွင့်ပေး" -> targetApp = "com.google.android.youtube", appName = "YouTube"
 * Turn 2: "YouTube ထဲက လေးဖြူရဲ့ သီချင်းတွေရှာပေး" -> artist = "လေးဖြူ", mediaType = "song"
 * Turn 3: "အလွမ်းညကို ရှာပြီး ဖွင့်ပေး" -> mediaTitle = "အလွမ်းည", inheritedApp = "YouTube", inheritedArtist = "လေးဖြူ"
 */
class CommandContextManager {

    companion object {
        private const val TAG = "CommandContextManager"
        private const val CONTEXT_EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes context retention

        @Volatile
        private var instance: CommandContextManager? = null

        fun getInstance(): CommandContextManager {
            return instance ?: synchronized(this) {
                instance ?: CommandContextManager().also { instance = it }
            }
        }
    }

    data class ConversationContext(
        var activeAppPackage: String? = null,
        var activeAppNameMM: String? = null,
        var activeArtist: String? = null,
        var activeMediaTitle: String? = null,
        var lastIntentType: String? = null,
        var lastUserQuery: String? = null,
        var lastResponseText: String? = null,
        var lastUpdatedTimestamp: Long = 0L,
        var turnCount: Int = 0
    )

    private var currentContext = ConversationContext()

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (ignored: Throwable) {
            // Safe fallback in unit tests
        }
    }

    @Synchronized
    fun getContext(): ConversationContext {
        // Automatically invalidate expired context
        val now = System.currentTimeMillis()
        if (currentContext.lastUpdatedTimestamp > 0 && (now - currentContext.lastUpdatedTimestamp) > CONTEXT_EXPIRATION_MS) {
            logDebug("Context expired after 5 minutes of inactivity. Resetting context.")
            resetContext()
        }
        return currentContext.copy()
    }

    @Synchronized
    fun updateAppTarget(packageName: String, appNameMM: String) {
        currentContext.activeAppPackage = packageName
        currentContext.activeAppNameMM = appNameMM
        currentContext.lastUpdatedTimestamp = System.currentTimeMillis()
        currentContext.turnCount++
        logDebug("Updated context app: $appNameMM ($packageName), turn=${currentContext.turnCount}")
    }

    @Synchronized
    fun updateMediaEntities(artist: String? = null, mediaTitle: String? = null) {
        if (!artist.isNullOrBlank()) {
            currentContext.activeArtist = artist.trim()
        }
        if (!mediaTitle.isNullOrBlank()) {
            currentContext.activeMediaTitle = mediaTitle.trim()
        }
        currentContext.lastUpdatedTimestamp = System.currentTimeMillis()
        currentContext.turnCount++
        logDebug("Updated media entities: artist=${currentContext.activeArtist}, title=${currentContext.activeMediaTitle}")
    }

    @Synchronized
    fun updateIntent(intentType: String) {
        currentContext.lastIntentType = intentType
        currentContext.lastUpdatedTimestamp = System.currentTimeMillis()
    }

    @Synchronized
    fun recordTurn(userQuery: String, intentType: String, responseText: String) {
        currentContext.lastUserQuery = userQuery
        currentContext.lastIntentType = intentType
        currentContext.lastResponseText = responseText
        currentContext.lastUpdatedTimestamp = System.currentTimeMillis()
        currentContext.turnCount++

        // Check if query mentions known apps or artists to augment context
        extractContextEntities(userQuery)
    }

    private fun extractContextEntities(query: String) {
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(query)

        // Detect app names and fallback context metadata
        if (normalized.contains("ယူကျူ") || normalized.contains("youtube") || normalized.contains("ယူးကျု")) {
            currentContext.activeAppPackage = "com.google.android.youtube"
            currentContext.activeAppNameMM = "YouTube"
        } else if (normalized.contains("ဖေ့ဘွတ်") || normalized.contains("ဖေ့စ်ဘုတ်") || normalized.contains("facebook")) {
            currentContext.activeAppPackage = "com.facebook.katana"
            currentContext.activeAppNameMM = "Facebook"
        } else if (normalized.contains("တယ်လီဂရမ်") || normalized.contains("telegram")) {
            currentContext.activeAppPackage = "org.telegram.messenger"
            currentContext.activeAppNameMM = "Telegram"
        } else if (normalized.contains("တစ်တော့") || normalized.contains("tiktok")) {
            currentContext.activeAppPackage = "com.zhiliaoapp.musically"
            currentContext.activeAppNameMM = "TikTok"
        } else if (normalized.contains("ဗိုင်ဘာ") || normalized.contains("viber")) {
            currentContext.activeAppPackage = "com.viber.voip"
            currentContext.activeAppNameMM = "Viber"
        } else if (normalized.contains("မက်ဆင်ဂျာ") || normalized.contains("messenger")) {
            currentContext.activeAppPackage = "com.facebook.orca"
            currentContext.activeAppNameMM = "Messenger"
        }

        // Detect popular Myanmar Artists in speech
        val knownArtists = listOf("လေးဖြူ", "အငဲ", "မျိုးကြီး", "ဝိုင်းစုခိုင်သိန်း", "ပိုပို", "စိုင်းစိုင်းခမ်းလှိုင်", "ရဲရင့်အောင်", "စည်သူလွင်")
        for (artist in knownArtists) {
            if (normalized.contains(artist)) {
                currentContext.activeArtist = artist
                break
            }
        }
    }

    @Synchronized
    fun resetContext() {
        currentContext = ConversationContext()
        logDebug("Context reset to clean state.")
    }
}
