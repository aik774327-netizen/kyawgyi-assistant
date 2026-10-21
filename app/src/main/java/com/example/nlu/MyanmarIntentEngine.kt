package com.example.nlu

import java.util.Locale

/**
 * Stage 1 - Myanmar Intent Engine
 *
 * Classifies user voice and text queries into structured KyawGyiIntent definitions.
 * Evaluates semantic patterns, action keywords, target slots, and confidence scores.
 * Uses structured intent rules rather than fragile if/else spaghetti logic.
 */
object MyanmarIntentEngine {

    data class IntentScoreResult(
        val intent: KyawGyiIntent,
        val score: Float,
        val matchedPattern: String? = null
    )

    private data class IntentRule(
        val intent: KyawGyiIntent,
        val primaryTriggers: List<String>,
        val patterns: List<Regex>,
        val negativeTriggers: List<String> = emptyList(),
        val baseScore: Float = 0.85f,
        val requiresAppSlot: Boolean = false
    )

    private val INTENT_RULES = listOf(
        // OPEN_CAMERA
        IntentRule(
            intent = KyawGyiIntent.OPEN_CAMERA,
            primaryTriggers = listOf("ကင်မရာ", "camera", "ကင်မရာဖွင့်", "ဓာတ်ပုံရိုက်"),
            patterns = listOf(
                Regex("(?:ကင်မရာ|camera)(?:လေး)?\\s*(?:ကို)?\\s*(?:ဖွင့်|ဖွင့်ပေး|ဖွင့်လိုက်|စမ်း|run|open)", RegexOption.IGNORE_CASE),
                Regex("(?:open|start|launch)\\s*(?:the\\s*)?camera", RegexOption.IGNORE_CASE),
                Regex("ဓာတ်ပုံ(?:လေး)?\\s*(?:တွေ)?\\s*ရိုက်(?:ပေး|ပါ|မယ်|ရအောင်|ချင်တယ်)?", RegexOption.IGNORE_CASE),
                Regex("take\\s*(?:a\\s*)?photo", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.95f
        ),

        // OPEN_PHOTOS
        IntentRule(
            intent = KyawGyiIntent.OPEN_PHOTOS,
            primaryTriggers = listOf("ဓာတ်ပုံ", "photos", "gallery", "album", "ဂယ်လာရီ"),
            patterns = listOf(
                Regex("(?:ဓာတ်ပုံ|photos|gallery|album|ဂယ်လာရီ)(?:တွေ)?(?:လေး)?\\s*(?:ကို)?\\s*(?:ဖွင့်|ကြည့်|ပြ|open|view)", RegexOption.IGNORE_CASE),
                Regex("(?:open|view)\\s*(?:the\\s*)?(?:photos|gallery|album)", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("ရိုက်"),
            baseScore = 0.92f
        ),

        // OPEN_MAPS
        IntentRule(
            intent = KyawGyiIntent.OPEN_MAPS,
            primaryTriggers = listOf("မြေပုံ", "maps", "google maps", "လမ်းညွှန်", "ဘယ်လိုသွားရမလဲ"),
            patterns = listOf(
                Regex("(?:မြေပုံ|maps|google maps)(?:လေး)?\\s*(?:ကို)?\\s*(?:ဖွင့်|ကြည့်|ပြ|open)", RegexOption.IGNORE_CASE),
                Regex("(?:open|show)\\s*(?:the\\s*)?maps?", RegexOption.IGNORE_CASE),
                Regex("([\\p{L}0-9\\s]+?)(?:ကို|ဆီ)\\s*ဘယ်လိုသွားရမလဲ", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.92f
        ),

        // OPEN_MESSAGES
        IntentRule(
            intent = KyawGyiIntent.OPEN_MESSAGES,
            primaryTriggers = listOf("မက်ဆေ့", "messages", "မက်ဆေ့ခ်ျ", "sms app", "စာတို", "inbox"),
            patterns = listOf(
                Regex("(?:မက်ဆေ့|messages|sms|စာတို|inbox)(?:app|အက်ပ်)?(?:လေး)?\\s*(?:ကို)?\\s*(?:ဖွင့်|open|ပြ)", RegexOption.IGNORE_CASE),
                Regex("(?:open|launch)\\s*(?:messages|sms|inbox)", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("ပို့", "ပို့ပေး", "ပို့လိုက်", "send"),
            baseScore = 0.90f
        ),

        // OPEN_PHONE (Dialer)
        IntentRule(
            intent = KyawGyiIntent.OPEN_PHONE,
            primaryTriggers = listOf("ဖုန်းခေါ်ရန်", "dialer", "phone app", "ဖုန်းစာအုပ်", "ကီးပတ်"),
            patterns = listOf(
                Regex("(?:phone app|dialer|ဖုန်းစာအုပ်|ကီးပတ်)(?:လေး)?\\s*(?:ဖွင့်|open)", RegexOption.IGNORE_CASE),
                Regex("open\\s*(?:phone|dialer)", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.88f
        ),

        // OPEN_SETTINGS
        IntentRule(
            intent = KyawGyiIntent.OPEN_SETTINGS,
            primaryTriggers = listOf("ဆက်တင်", "settings", "setting", "စနစ်ဆက်တင်"),
            patterns = listOf(
                Regex("(?:ဆက်တင်|settings|setting)(?:လေး)?\\s*(?:ကို)?\\s*(?:ဖွင့်|open)", RegexOption.IGNORE_CASE),
                Regex("open\\s*settings", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.92f
        ),

        // CALL
        IntentRule(
            intent = KyawGyiIntent.CALL,
            primaryTriggers = listOf("ဖုန်းဆက်", "ဖုန်းခေါ်", "call", "ဖုန်းခေါ်ပေး", "ဖုန်းဆက်ပေး", "ခေါ်ပေး", "ဆက်ပေး"),
            patterns = listOf(
                Regex("(?:ဖုန်း|call)?\\s*(?:ဆက်|ခေါ်|ဆက်ပေး|ခေါ်ပေး|ဆက်လိုက်|ခေါ်လိုက်|call လုပ်)", RegexOption.IGNORE_CASE),
                Regex("(?:call|phone|ring)\\s+([\\p{L}0-9_\\s]+)", RegexOption.IGNORE_CASE),
                Regex("([\\p{L}0-9_\\s]+?)(?:ကို|ဆီ|ထံ)\\s*(?:ဖုန်း)?(?:ဆက်|ခေါ်)", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.95f
        ),

        // SEND_SMS
        IntentRule(
            intent = KyawGyiIntent.SEND_SMS,
            primaryTriggers = listOf("message ပို့", "sms ပို့", "စာပို့", "မက်ဆေ့ပို့", "မက်ဆေ့ခ်ျပို့", "send message", "send sms"),
            patterns = listOf(
                Regex("(?:message|sms|စာ|မက်ဆေ့)(?:လေး)?\\s*(?:ပို့|ပို့ပေး|ပို့လိုက်|ပေးပို့|send)", RegexOption.IGNORE_CASE),
                Regex("send\\s*(?:a\\s*)?(?:message|sms|text)", RegexOption.IGNORE_CASE),
                Regex("([\\p{L}0-9_\\s]+?)(?:ကို|ဆီ)\\s*.*(?:လို့|ဆိုပြီး)?\\s*(?:message|sms|စာ)?\\s*(?:ပို့|send)", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.95f
        ),

        // PLAY_MUSIC
        IntentRule(
            intent = KyawGyiIntent.PLAY_MUSIC,
            primaryTriggers = listOf("သီချင်းဖွင့်", "သီချင်း", "music", "play music", "play song", "ဖွင့်ပြပါ", "play"),
            patterns = listOf(
                Regex("(?:သီချင်း|music|song)(?:လေး)?\\s*(?:ဖွင့်|ဖွင့်ပေး|ဖွင့်ပြ|ဖွင့်လိုက်|play|play လုပ်)", RegexOption.IGNORE_CASE),
                Regex("(?:play|start)\\s*(?:the\\s*)?(?:music|song|track)", RegexOption.IGNORE_CASE),
                Regex("([\\p{L}0-9_\\s]+?)(?:ရဲ့\\s*)?(?:သီချင်း)?(?:ကို)?\\s*(?:ဖွင့်|play|ဖွင့်ပြ)", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("ခဏရပ်", "ရပ်", "ပိတ်", "နောက်တစ်ပုဒ်"),
            baseScore = 0.93f
        ),

        // PAUSE_MEDIA
        IntentRule(
            intent = KyawGyiIntent.PAUSE_MEDIA,
            primaryTriggers = listOf("သီချင်းခဏရပ်", "ခဏရပ်", "ခဏရပ်ထား", "pause", "pause music", "ခဏနား"),
            patterns = listOf(
                Regex("(?:သီချင်း|music|video|မီဒီယာ)?\\s*(?:ခဏရပ်|ရပ်ထား|pause|ခဏနား)", RegexOption.IGNORE_CASE),
                Regex("pause\\s*(?:the\\s*)?(?:music|playback|audio)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.94f
        ),

        // RESUME_MEDIA
        IntentRule(
            intent = KyawGyiIntent.RESUME_MEDIA,
            primaryTriggers = listOf("ပြန်ဖွင့်", "ဆက်ဖွင့်", "resume", "continue playing", "ပြန်ဖွင့်ပေး"),
            patterns = listOf(
                Regex("(?:သီချင်း|music)?\\s*(?:ပြန်ဖွင့်|ဆက်ဖွင့်|ဆက်တီး|resume|continue)", RegexOption.IGNORE_CASE),
                Regex("resume\\s*(?:the\\s*)?(?:music|playback)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.93f
        ),

        // NEXT_TRACK
        IntentRule(
            intent = KyawGyiIntent.NEXT_TRACK,
            primaryTriggers = listOf("နောက်တစ်ပုဒ်", "နောက်သီချင်း", "next", "next song", "ကျော်လိုက်", "နောက်တစ်ခု"),
            patterns = listOf(
                Regex("(?:နောက်|next)(?:တစ်ပုဒ်|တပုဒ်|သီချင်း|တစ်ခု)?\\s*(?:ပြောင်း|ဖွင့်|သွား|skip|next)?", RegexOption.IGNORE_CASE),
                Regex("(?:skip|next)\\s*(?:track|song)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.94f
        ),

        // PREVIOUS_TRACK
        IntentRule(
            intent = KyawGyiIntent.PREVIOUS_TRACK,
            primaryTriggers = listOf("ရှေ့တစ်ပုဒ်", "အရင်တစ်ပုဒ်", "previous", "prev song", "ရှေ့သီချင်း"),
            patterns = listOf(
                Regex("(?:ရှေ့|အရင်|previous)(?:တစ်ပုဒ်|တပုဒ်|သီချင်း|တစ်ခု)?\\s*(?:ပြန်ဖွင့်|သွား|prev)?", RegexOption.IGNORE_CASE),
                Regex("(?:previous|prev)\\s*(?:track|song)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.94f
        ),

        // VOLUME_UP
        IntentRule(
            intent = KyawGyiIntent.VOLUME_UP,
            primaryTriggers = listOf("အသံကျယ်", "အသံမြှင့်", "အသံတိုးပေးပါဦး", "အသံချဲ့", "volume up", "louder"),
            patterns = listOf(
                Regex("(?:အသံ|volume)(?:လေး)?\\s*(?:ကျယ်|မြှင့်|ချဲ့|တိုးပေး|တိုးပါဦး|up|increase)", RegexOption.IGNORE_CASE),
                Regex("(?:turn\\s*up|increase|raise)\\s*(?:the\\s*)?volume", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("အသံတိုး", "အသံလျှော့", "down"),
            baseScore = 0.93f
        ),

        // VOLUME_DOWN
        IntentRule(
            intent = KyawGyiIntent.VOLUME_DOWN,
            primaryTriggers = listOf("အသံတိုး", "အသံလျှော့", "အသံဖြည်းဖြည်း", "volume down", "quieter", "lower volume"),
            patterns = listOf(
                Regex("(?:အသံ|volume)(?:လေး)?\\s*(?:တိုး|လျှော့|ချ|ဖြည်းဖြည်း|down|decrease|lower)", RegexOption.IGNORE_CASE),
                Regex("(?:turn\\s*down|decrease|lower)\\s*(?:the\\s*)?volume", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("အသံကျယ်", "အသံမြှင့်", "အသံချဲ့", "up"),
            baseScore = 0.93f
        ),

        // MUTE
        IntentRule(
            intent = KyawGyiIntent.MUTE,
            primaryTriggers = listOf("အသံပိတ်", "အသံတိတ်", "mute", "silence", "ငြိမ်နေ"),
            patterns = listOf(
                Regex("(?:အသံ|volume)?\\s*(?:ပိတ်|တိတ်|ငြိမ်|mute|silence)", RegexOption.IGNORE_CASE),
                Regex("mute\\s*(?:the\\s*)?(?:audio|device|phone)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.92f
        ),

        // STOP
        IntentRule(
            intent = KyawGyiIntent.STOP,
            primaryTriggers = listOf("ရပ်", "ရပ်လိုက်", "ရပ်ပါ", "တော်ပြီ", "stop", "ခဏနား"),
            patterns = listOf(
                Regex("^(?:ရပ်|ရပ်လိုက်|ရပ်ပါ|တော်ပြီ|တော်ပါပြီ|stop|halt)$", RegexOption.IGNORE_CASE),
                Regex("^(?:ခဏ\\s*)?ရပ်လိုက်(?:ပါ)?$", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.96f
        ),

        // CANCEL
        IntentRule(
            intent = KyawGyiIntent.CANCEL,
            primaryTriggers = listOf("မလုပ်နဲ့တော့", "ဖျက်လိုက်", "cancel", "မလိုတော့ဘူး", "dismiss"),
            patterns = listOf(
                Regex("(?:မလုပ်နဲ့တော့|ဖျက်လိုက်|cancel|မလိုတော့ဘူး|dismiss|ပယ်ဖျက်)", RegexOption.IGNORE_CASE),
                Regex("cancel\\s*(?:that|this|operation)?", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.95f
        ),

        // SEARCH
        IntentRule(
            intent = KyawGyiIntent.SEARCH,
            primaryTriggers = listOf("ရှာပေး", "ရှာဖွေ", "search", "google မှာရှာ", "ရှာကြည့်"),
            patterns = listOf(
                Regex("([\\p{L}0-9_\\s]+?)(?:ကို)?\\s*(?:ရှာ|ရှာပေး|ရှာဖွေ|search|google)", RegexOption.IGNORE_CASE),
                Regex("(?:search|google|find)\\s+for\\s+(.+)", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.88f
        ),

        // CLOSE_APP
        IntentRule(
            intent = KyawGyiIntent.CLOSE_APP,
            primaryTriggers = listOf("ပိတ်", "ပိတ်လိုက်", "ပိတ်ပေး", "close", "exit"),
            patterns = listOf(
                Regex("([\\p{L}0-9_\\s]+?)(?:app|အက်ပ်)?(?:ကို)?\\s*(?:ပိတ်|ပိတ်ပေး|ပိတ်လိုက်|close|exit)", RegexOption.IGNORE_CASE),
                Regex("close\\s+(.+)", RegexOption.IGNORE_CASE)
            ),
            negativeTriggers = listOf("မီးပိတ်", "အသံပိတ်"),
            baseScore = 0.89f,
            requiresAppSlot = true
        ),

        // OPEN_APP (General application opener)
        IntentRule(
            intent = KyawGyiIntent.OPEN_APP,
            primaryTriggers = listOf("ဖွင့်", "ဖွင့်ပေး", "open", "launch", "start"),
            patterns = listOf(
                Regex("([\\p{L}0-9_\\s]+?)(?:app|အက်ပ်)?(?:လေး)?(?:ကို)?\\s*(?:ဖွင့်|ဖွင့်ပေး|ဖွင့်လိုက်|open|launch|run|စတင်)", RegexOption.IGNORE_CASE),
                Regex("(?:open|launch|start)\\s+([\\p{L}0-9_\\s]+)", RegexOption.IGNORE_CASE)
            ),
            baseScore = 0.88f,
            requiresAppSlot = false
        )
    )

    /**
     * Determines the most accurate KyawGyiIntent from original text, normalized text, and extracted entities.
     */
    fun detectIntent(
        originalText: String,
        normalizedText: String,
        entities: Map<EntityType, String>
    ): IntentScoreResult {
        val cleanNorm = normalizedText.lowercase(Locale.ROOT)
        val cleanOrig = originalText.lowercase(Locale.ROOT)

        // 1. Direct App Specific Specializations
        if (entities.containsKey(EntityType.APP_NAME)) {
            val app = entities[EntityType.APP_NAME]?.lowercase(Locale.ROOT)
            when (app) {
                "camera" -> {
                    if (cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("camera") || cleanOrig.contains("ရိုက်")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_CAMERA, 0.98f, "direct_camera_match")
                    }
                }
                "photos", "gallery" -> {
                    if ((cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("photos") || cleanNorm.contains("ကြည့်")) && !cleanOrig.contains("ရိုက်")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_PHOTOS, 0.96f, "direct_photos_match")
                    }
                }
                "settings" -> {
                    if (cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("settings") || cleanOrig.contains("ဆက်တင်")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_SETTINGS, 0.96f, "direct_settings_match")
                    }
                }
                "google maps", "maps" -> {
                    if (cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("maps") || cleanOrig.contains("မြေပုံ")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_MAPS, 0.96f, "direct_maps_match")
                    }
                }
                "messages" -> {
                    if ((cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("app")) && !cleanNorm.contains("ပို့") && !cleanOrig.contains("ပို့")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_MESSAGES, 0.95f, "direct_messages_match")
                    }
                }
                "phone" -> {
                    if (cleanNorm.contains("app") || cleanNorm.contains("dialer") || cleanNorm.contains("စာအုပ်") || cleanNorm.contains("open")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_PHONE, 0.94f, "direct_phone_app_match")
                    }
                }
                else -> {
                    // Other general apps (Facebook, TikTok, Telegram, Viber, KBZPay, WavePay, etc.)
                    if (cleanNorm.contains("ဖွင့်") || cleanNorm.contains("open") || cleanNorm.contains("launch") || cleanNorm.contains("စတင်")) {
                        return IntentScoreResult(KyawGyiIntent.OPEN_APP, 0.95f, "direct_open_app_match")
                    }
                }
            }
        }

        var bestIntent = KyawGyiIntent.UNKNOWN
        var maxScore = 0.0f
        var matchedPatternStr: String? = null

        // 2. Evaluate Intent Rules
        for (rule in INTENT_RULES) {
            var score = 0.0f
            var patternMatched: String? = null

            // Check negative triggers
            val hasNegative = rule.negativeTriggers.any { cleanNorm.contains(it) || cleanOrig.contains(it) }
            if (hasNegative) continue

            // Check regex patterns
            for (pattern in rule.patterns) {
                if (pattern.containsMatchIn(originalText) || pattern.containsMatchIn(normalizedText)) {
                    score = rule.baseScore
                    patternMatched = pattern.pattern
                    break
                }
            }

            // Check primary keyword triggers
            if (score == 0.0f) {
                for (trigger in rule.primaryTriggers) {
                    if (cleanNorm.contains(trigger) || cleanOrig.contains(trigger)) {
                        score = rule.baseScore * 0.88f
                        patternMatched = "trigger:$trigger"
                        break
                    }
                }
            }

            // Contextual Slot Boosting
            if (score > 0.0f) {
                when (rule.intent) {
                    KyawGyiIntent.CALL -> {
                        if (entities.containsKey(EntityType.CONTACT) || entities.containsKey(EntityType.PHONE_NUMBER)) {
                            score = minOf(1.0f, score + 0.10f)
                        }
                    }
                    KyawGyiIntent.SEND_SMS -> {
                        if (entities.containsKey(EntityType.MESSAGE_TEXT) || entities.containsKey(EntityType.CONTACT)) {
                            score = minOf(1.0f, score + 0.10f)
                        }
                    }
                    KyawGyiIntent.PLAY_MUSIC -> {
                        if (entities.containsKey(EntityType.SONG_NAME) || entities.containsKey(EntityType.ARTIST_NAME) || entities.containsKey(EntityType.MEDIA_TYPE)) {
                            score = minOf(1.0f, score + 0.10f)
                        }
                    }
                    KyawGyiIntent.OPEN_APP -> {
                        if (entities.containsKey(EntityType.APP_NAME)) {
                            score = minOf(1.0f, score + 0.12f)
                        } else if (rule.requiresAppSlot) {
                            score *= 0.6f
                        }
                    }
                    else -> {}
                }

                if (score > maxScore) {
                    maxScore = score
                    bestIntent = rule.intent
                    matchedPatternStr = patternMatched
                }
            }
        }

        // 3. Fallback for standalone commands
        if (maxScore < 0.45f) {
            if (cleanNorm.contains("ခေါ်") || cleanNorm.contains("ဆက်") || cleanOrig.contains("ခေါ်") || cleanOrig.contains("ဆက်")) {
                bestIntent = KyawGyiIntent.CALL
                maxScore = 0.60f
            } else if (cleanNorm.contains("ဖွင့်") || cleanOrig.contains("ဖွင့်")) {
                bestIntent = KyawGyiIntent.OPEN_APP
                maxScore = 0.50f
            } else {
                bestIntent = KyawGyiIntent.UNKNOWN
                maxScore = 0.20f
            }
        }

        return IntentScoreResult(
            intent = bestIntent,
            score = maxScore,
            matchedPattern = matchedPatternStr
        )
    }
}
