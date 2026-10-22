package com.example.router

import com.example.actions.CommandResult
import com.example.actions.KyawGyiActionExecutor
import com.example.engine.BurmesePhoneticNormalizer
import com.example.engine.CommandContextManager
import com.example.utils.MyanmarSTEngine

enum class CommandIntent {
    TURN_ON_FLASH,
    TURN_OFF_FLASH,
    TURN_ON_WIFI,
    TURN_OFF_WIFI,
    OPEN_FACEBOOK,
    OPEN_YOUTUBE,
    OPEN_TELEGRAM,
    CLOSE_APP,
    SEARCH_MEDIA,
    PLAY_MEDIA,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    PAUSE_MEDIA,
    RESUME_MEDIA,
    CALL_PHONE,
    SEND_SMS,
    UNKNOWN
}

data class ParsedCommand(
    val intent: CommandIntent,
    val rawText: String,
    val slot: String? = null,
    val artist: String? = null,
    val mediaTitle: String? = null,
    val query: String? = null
)

class KyawGyiIntentRouter {

    private val stEngine = MyanmarSTEngine()
    private val contextManager = CommandContextManager.getInstance()

    private val registeredCommands = listOf(
        MyanmarSTEngine.RegisteredCommand("TURN_ON_FLASH", "ဓာတ်မီးဖွင့်", CommandIntent.TURN_ON_FLASH),
        MyanmarSTEngine.RegisteredCommand("TURN_OFF_FLASH", "ဓာတ်မီးပိတ်", CommandIntent.TURN_OFF_FLASH),
        MyanmarSTEngine.RegisteredCommand("TURN_ON_WIFI", "ဝိုင်ဖိုင်ဖွင့်", CommandIntent.TURN_ON_WIFI),
        MyanmarSTEngine.RegisteredCommand("TURN_OFF_WIFI", "ဝိုင်ဖိုင်ပိတ်", CommandIntent.TURN_OFF_WIFI),
        MyanmarSTEngine.RegisteredCommand("OPEN_FACEBOOK", "ဖေ့ဘုတ်ဖွင့်", CommandIntent.OPEN_FACEBOOK),
        MyanmarSTEngine.RegisteredCommand("OPEN_YOUTUBE", "ယူကျုဖွင့်", CommandIntent.OPEN_YOUTUBE),
        MyanmarSTEngine.RegisteredCommand("OPEN_TELEGRAM", "တယ်လီဂရမ်ဖွင့်", CommandIntent.OPEN_TELEGRAM)
    )

    fun parseCommand(rawText: String): ParsedCommand {
        val transcript = stEngine.processTranscript(rawText, isFinal = true)
        val text = transcript.clean.lowercase()
        val normalized = BurmesePhoneticNormalizer.normalizeBurmeseText(text)
        val conversationContext = contextManager.getContext()

        // -----------------------------------------------------------------
        // 1. SEMANTIC MEDIA SEARCH & PLAY PARSING (with Multi-Turn Context)
        // -----------------------------------------------------------------
        val isPlayCommand = normalized.contains("ဖွင့်") || normalized.contains("ဖွင့်ပေး") ||
            normalized.contains("ဖွင့်ပါ") || normalized.contains("ဖွင့်လိုက်") ||
            normalized.contains("play") || normalized.contains("နားထောင်") ||
            normalized.contains("နားထောင်ချင်") || normalized.contains("ပြပေး") ||
            normalized.contains("ပြပါ") || normalized.contains("လွှင့်ပေး")

        val isSearchCommand = normalized.contains("ရှာ") || normalized.contains("ရှာပေး") ||
            normalized.contains("ရှာပါ") || normalized.contains("ရှာဖွေ") ||
            normalized.contains("search") || normalized.contains("ရှာကြည့်")

        // Check for known Myanmar artists
        val knownArtists = listOf("လေးဖြူ", "အငဲ", "မျိုးကြီး", "ဝိုင်းစုခိုင်သိန်း", "ပိုပို", "စိုင်းစိုင်းခမ်းလှိုင်", "ရဲရင့်အောင်", "စည်သူလွင်")
        var detectedArtist: String? = null
        for (artist in knownArtists) {
            if (normalized.contains(artist)) {
                detectedArtist = artist
                break
            }
        }
        val effectiveArtist = detectedArtist ?: conversationContext.activeArtist

        // Check for specific song title extraction (e.g. "အလွမ်းည", "အလွမ်းညကို", "အလွမ်းည ဖွင့်")
        var detectedSong: String? = null
        if (normalized.contains("အလွမ်းည")) {
            detectedSong = "အလွမ်းည"
        } else if (normalized.contains("သီချင်း")) {
            // Extract title phrase if present
            val stripped = normalized
                .replace("သီချင်း", "")
                .replace("ထဲက", "")
                .replace("ရဲ့", "")
                .replace("ရှာပြီး", "")
                .replace("ရှာပေး", "")
                .replace("ဖွင့်ပေး", "")
                .replace("ဖွင့်ပါ", "")
                .replace("ဖွင့်", "")
                .replace("youtube", "")
                .replace("ယူကျု", "")
                .trim()
            if (stripped.isNotBlank() && stripped != effectiveArtist) {
                detectedSong = stripped
            }
        }

        // Relative context references: "အဲ့ဒီသီချင်း ဖွင့်ပေး", "အဲဒါလေး ဖွင့်ပေး"
        val isContextReference = normalized.contains("အဲ့ဒီသီချင်း") || normalized.contains("အဲဒါလေး") ||
            normalized.contains("အဲ့ဒါဖွင့်") || normalized.contains("ဒီသီချင်း")

        if (isPlayCommand && (detectedSong != null || (isContextReference && conversationContext.activeMediaTitle != null) || (effectiveArtist != null && isSearchCommand))) {
            val finalSong = detectedSong ?: conversationContext.activeMediaTitle
            val finalArtist = effectiveArtist
            val queryBuilder = StringBuilder()
            if (!finalArtist.isNullOrBlank()) queryBuilder.append("$finalArtist ")
            if (!finalSong.isNullOrBlank()) queryBuilder.append(finalSong)
            val fullQuery = queryBuilder.toString().trim().ifBlank { rawText }

            return ParsedCommand(
                intent = CommandIntent.PLAY_MEDIA,
                rawText = rawText,
                slot = "com.google.android.youtube",
                artist = finalArtist,
                mediaTitle = finalSong,
                query = fullQuery
            )
        }

        if (isSearchCommand && (effectiveArtist != null || normalized.contains("သီချင်း"))) {
            val query = detectedArtist ?: effectiveArtist ?: text
            return ParsedCommand(
                intent = CommandIntent.SEARCH_MEDIA,
                rawText = rawText,
                slot = "com.google.android.youtube",
                artist = detectedArtist ?: effectiveArtist,
                query = query
            )
        }

        // -----------------------------------------------------------------
        // 2. APP LAUNCHING WITH SYNONYMS, PARTICLES & HONORIFICS
        // -----------------------------------------------------------------
        // YouTube variants: "YouTube ဖွင့်ပေး", "YouTube လေးဖွင့်ပါ", "YouTube ထဲဝင်ပေး", "YouTube သွားမယ်", "YouTube ဖွင့်"
        if (text.contains("ယူကျု") || text.contains("youtube") || normalized.contains("ယူကျူ")) {
            if ((isPlayCommand && (detectedSong != null || detectedArtist != null)) || isSearchCommand) {
                // Media command inside YouTube with specific entity or search intent
                val q = detectedSong ?: detectedArtist ?: "သီချင်းများ"
                return ParsedCommand(CommandIntent.SEARCH_MEDIA, rawText, slot = "com.google.android.youtube", query = q)
            }
            return ParsedCommand(CommandIntent.OPEN_YOUTUBE, rawText, slot = "com.google.android.youtube")
        }

        if (text.contains("ဖေ့ဘုတ်") || text.contains("facebook") || normalized.contains("ဖေ့ဘွတ်")) {
            return ParsedCommand(CommandIntent.OPEN_FACEBOOK, rawText, slot = "com.facebook.katana")
        }

        if (text.contains("တယ်လီဂရမ်") || text.contains("telegram")) {
            return ParsedCommand(CommandIntent.OPEN_TELEGRAM, rawText, slot = "org.telegram.messenger")
        }

        // -----------------------------------------------------------------
        // 3. HARDWARE TOGGLES
        // -----------------------------------------------------------------
        if (text.contains("ဓာတ်မီးဖွင့်") || text.contains("မီးလင်းဖွင့်") || text.contains("မီးဖွင့်") || (text.contains("ဓာတ်မီး") && text.contains("ဖွင့်"))) {
            return ParsedCommand(CommandIntent.TURN_ON_FLASH, rawText)
        }
        if (text.contains("ဓာတ်မီးပိတ်") || text.contains("မီးလင်းပိတ်") || text.contains("မီးပိတ်") || (text.contains("ဓာတ်မီး") && text.contains("ပိတ်"))) {
            return ParsedCommand(CommandIntent.TURN_OFF_FLASH, rawText)
        }
        if (text.contains("ဝိုင်ဖိုင်ဖွင့်") || text.contains("wifiဖွင့်") || text.contains("wifi ဖွင့်")) {
            return ParsedCommand(CommandIntent.TURN_ON_WIFI, rawText)
        }
        if (text.contains("ဝိုင်ဖိုင်ပိတ်") || text.contains("wifiပိတ်") || text.contains("wifi ပိတ်")) {
            return ParsedCommand(CommandIntent.TURN_OFF_WIFI, rawText)
        }

        // -----------------------------------------------------------------
        // 3B. SMS & MESSAGES (မက်ဆေ့ခ်ျ / စာပို့)
        // -----------------------------------------------------------------
        val isSmsCommand = normalized.contains("မက်ဆေ့") || normalized.contains("စာပို့") ||
                normalized.contains("sms") || normalized.contains("message")
        if (isSmsCommand) {
            val entities = com.example.nlu.MyanmarEntityExtractor.extractEntities(rawText, normalized)
            val recipient = entities[com.example.nlu.EntityType.CONTACT] ?: entities[com.example.nlu.EntityType.PHONE_NUMBER] ?: ""
            val msg = entities[com.example.nlu.EntityType.MESSAGE_TEXT] ?: ""
            return ParsedCommand(
                intent = CommandIntent.SEND_SMS,
                rawText = rawText,
                query = recipient,
                slot = msg
            )
        }

        // -----------------------------------------------------------------
        // 4. PHONE CALL & CONTACT CALL
        // -----------------------------------------------------------------
        val isCallCommand = normalized.contains("ဖုန်းခေါ်") || normalized.contains("ဖုန်းဆက်") ||
            normalized.contains("ခေါ်ပေး") || normalized.contains("ဆက်ပေး") ||
            normalized.contains("ဖုန်းတိုက်ရိုက်") || normalized.contains("call ") ||
            normalized.contains("dial ") || normalized.startsWith("call") ||
            (normalized.contains("ဖုန်း") && (normalized.contains("ခေါ်") || normalized.contains("ဆက်")))

        if (isCallCommand) {
            val contactName = normalized
                .replace("ဆီကို", "")
                .replace("ဆီ", "")
                .replace("ထံသို့", "")
                .replace("ထံ", "")
                .replace("ကို", "")
                .replace("အား", "")
                .replace("ဖုန်းတိုက်ရိုက်", "")
                .replace("တိုက်ရိုက်", "")
                .replace("ဖုန်းခေါ်ဆိုပေးပါ", "")
                .replace("ဖုန်းခေါ်ပေးပါ", "")
                .replace("ဖုန်းဆက်ပေးပါ", "")
                .replace("ဖုန်းခေါ်ပါဦး", "")
                .replace("ဖုန်းဆက်ပါဦး", "")
                .replace("ဖုန်းခေါ်ပေး", "")
                .replace("ဖုန်းဆက်ပေး", "")
                .replace("ဖုန်းခေါ်ပါ", "")
                .replace("ဖုန်းဆက်ပါ", "")
                .replace("ဖုန်းခေါ်မယ်", "")
                .replace("ဖုန်းဆက်မယ်", "")
                .replace("ဖုန်းခေါ်", "")
                .replace("ဖုန်းဆက်", "")
                .replace("ခေါ်ပေးပါ", "")
                .replace("ဆက်ပေးပါ", "")
                .replace("ခေါ်ပေး", "")
                .replace("ဆက်ပေး", "")
                .replace("ခေါ်မယ်", "")
                .replace("ဆက်မယ်", "")
                .replace("ခေါ်ပါ", "")
                .replace("ဆက်ပါ", "")
                .replace("call to", "")
                .replace("call", "")
                .replace("dial", "")
                .replace("phone", "")
                .trim()
                .removeSuffix("့")

            return ParsedCommand(
                intent = CommandIntent.CALL_PHONE,
                rawText = rawText,
                query = if (contactName.isNotBlank()) contactName else rawText
            )
        }

        // -----------------------------------------------------------------
        // 5. APP CLOSE INTENT PARSING (အက်ပ် ပိတ်ခြင်း / ထွက်ခြင်း)
        // -----------------------------------------------------------------
        val isCloseApp = (normalized.contains("ပိတ်") || normalized.contains("close") || normalized.contains("ထွက်")) &&
                !normalized.contains("ဓာတ်မီး") && !normalized.contains("မီး") &&
                !normalized.contains("ဝိုင်ဖိုင်") && !normalized.contains("wifi") &&
                !normalized.contains("အသံ") && !normalized.contains("သီချင်း")

        if (isCloseApp) {
            val stripped = normalized
                .replace("ပိတ်ပေးပါ", "")
                .replace("ပိတ်လိုက်ပါ", "")
                .replace("ပိတ်ပါဦး", "")
                .replace("ပိတ်ပေး", "")
                .replace("ပိတ်ပါ", "")
                .replace("ပိတ်", "")
                .replace("close", "")
                .replace("ထွက်ပါ", "")
                .replace("ထွက်", "")
                .replace("အက်ပ်", "")
                .replace("app", "")
                .trim()

            return ParsedCommand(
                intent = CommandIntent.CLOSE_APP,
                rawText = rawText,
                query = stripped.ifBlank { conversationContext.activeAppNameMM }
            )
        }

        // Fallback to Fuzzy Levenshtein Intent Matcher (>80% accuracy)
        val fuzzyMatch = stEngine.matchIntent(rawText, registeredCommands, thresholdPercent = 80)
        if (fuzzyMatch != null) {
            val intent = fuzzyMatch.command.payload as? CommandIntent ?: CommandIntent.UNKNOWN
            val slot = when (intent) {
                CommandIntent.OPEN_FACEBOOK -> "com.facebook.katana"
                CommandIntent.OPEN_YOUTUBE -> "com.google.android.youtube"
                CommandIntent.OPEN_TELEGRAM -> "org.telegram.messenger"
                else -> null
            }
            return ParsedCommand(intent, rawText, slot)
        }

        return ParsedCommand(CommandIntent.UNKNOWN, rawText)
    }

    fun parseAndExecute(spokenText: String, executor: KyawGyiActionExecutor): Pair<Boolean, String> {
        val confirmationCoordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
        if (confirmationCoordinator.hasPendingAction()) {
            // ISSUE-029 (Phase 1.5): cancellation is evaluated BEFORE
            // confirmation, so a mixed reply ("ဟုတ်ကဲ့ မခေါ်ပါနဲ့")
            // cancels the staged action instead of executing it.
            // (The coordinator's isConfirmation also refuses any reply
            // that matches a cancellation form — defence in depth at
            // both sites named by the finding.)
            if (confirmationCoordinator.isCancellation(spokenText)) {
                val outcome = confirmationCoordinator.cancelPending("User explicit cancellation")
                contextManager.recordTurn(spokenText, "CANCEL_COMMUNICATION", outcome.userMessage)
                return true to outcome.userMessage
            } else if (confirmationCoordinator.isConfirmation(spokenText)) {
                val outcome = confirmationCoordinator.executeConfirmed(executor.context)
                contextManager.recordTurn(spokenText, "CONFIRM_COMMUNICATION", outcome.userMessage)
                return true to outcome.userMessage
            }
        }

        val parsed = parseCommand(spokenText)
        val result: CommandResult = when (parsed.intent) {
            CommandIntent.TURN_ON_FLASH -> executor.setFlashlight(true)
            CommandIntent.TURN_OFF_FLASH -> executor.setFlashlight(false)
            CommandIntent.TURN_ON_WIFI -> executor.setWifi(true)
            CommandIntent.TURN_OFF_WIFI -> executor.setWifi(false)
            CommandIntent.OPEN_FACEBOOK -> {
                contextManager.updateAppTarget("com.facebook.katana", "Facebook")
                executor.openApplication(parsed.slot!!, "Facebook")
            }
            CommandIntent.OPEN_YOUTUBE -> {
                contextManager.updateAppTarget("com.google.android.youtube", "YouTube")
                executor.openApplication(parsed.slot!!, "YouTube")
            }
            CommandIntent.OPEN_TELEGRAM -> {
                contextManager.updateAppTarget("org.telegram.messenger", "Telegram")
                executor.openApplication(parsed.slot!!, "Telegram")
            }
            CommandIntent.CLOSE_APP -> {
                val appTarget = parsed.query ?: ""
                val targetApp = if (appTarget.isNotBlank()) {
                    com.example.semantic.dictionary.MyanmarAppDictionary.resolveApp(appTarget)
                } else null
                val pkg = targetApp?.defaultPackageName ?: appTarget
                val label = targetApp?.displayNameMM ?: (if (appTarget.isNotBlank()) appTarget else "အက်ပ်")
                executor.closeApplication(pkg, label)
            }
            CommandIntent.SEARCH_MEDIA -> {
                contextManager.updateAppTarget("com.google.android.youtube", "YouTube")
                if (parsed.artist != null) {
                    contextManager.updateMediaEntities(artist = parsed.artist)
                }
                executor.searchMedia(parsed.query ?: "သီချင်း", parsed.slot ?: "com.google.android.youtube", "YouTube")
            }
            CommandIntent.PLAY_MEDIA -> {
                contextManager.updateAppTarget("com.google.android.youtube", "YouTube")
                contextManager.updateMediaEntities(artist = parsed.artist, mediaTitle = parsed.mediaTitle)
                executor.playMedia(
                    query = parsed.query ?: "သီချင်း",
                    artist = parsed.artist,
                    title = parsed.mediaTitle,
                    targetAppPackage = parsed.slot ?: "com.google.android.youtube"
                )
            }
            CommandIntent.CALL_PHONE -> {
                executor.callContact(parsed.query ?: spokenText)
            }
            CommandIntent.SEND_SMS -> {
                executor.sendSms(parsed.query ?: "", parsed.slot ?: "")
            }
            else -> {
                return false to "ကြားရတဲ့ စကားလုံးအတွက် Local Action မရှိသေးပါ"
            }
        }

        // Record turn into context manager
        contextManager.recordTurn(
            userQuery = spokenText,
            intentType = parsed.intent.name,
            responseText = result.userMessage
        )

        return true to result.userMessage
    }
}

