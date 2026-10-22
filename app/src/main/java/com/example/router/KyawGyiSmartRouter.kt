package com.example.router

import android.content.Context
import com.example.BuildConfig
import com.example.actions.KyawGyiActionExecutor
import com.example.actions.UrlVoiceControlExecutor
import com.example.audit.AuditLogger
import com.example.audit.ResolutionPath
import com.example.data.LocalMemoryStore
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.remote.GeminiRepository
import com.example.engine.BurmesePhoneticNormalizer
import com.example.engine.CommandContextManager
import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.KyawGyiIntent
import com.example.security.RiskPolicy
import com.example.semantic.GenericSemanticIntentMatcher
import com.example.utils.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KyawGyiSmartRouter(
    private val context: Context,
    geminiApiKey: String = BuildConfig.GEMINI_API_KEY
) {

    private val db by lazy { runCatching { KyawGyiDatabase.getDatabase(context) }.getOrNull() }
    private val executor = KyawGyiActionExecutor(context)
    private val urlExecutor = UrlVoiceControlExecutor(context)
    private val offlineIntentRouter = KyawGyiIntentRouter()
    private val geminiRepo = GeminiRepository(geminiApiKey, context)
    private val googleHomeRouter = com.example.googlehome.router.GoogleHomeCommandRouter(context)
    private val memoryStore = LocalMemoryStore.getInstance(context)
    private val pipeline by lazy { com.example.pipeline.UnifiedIntelligencePipeline.getInstance(context, geminiApiKey) }

    suspend fun processVoiceInput(spokenText: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val preprocessedText = BurmeseNluPreprocessor.normalize(spokenText)
        val cleanQuery = preprocessedText.trim().lowercase()
        val normalizedQuery = BurmesePhoneticNormalizer.normalizeBurmeseText(cleanQuery)
        var finalResponse: String = "ကြားရတဲ့ စကားလုံးအတွက် လုပ်ဆောင်ချက် မတွေ့ရှိသေးပါခင်ဗျာ"
        var executionMode: String = "UNKNOWN"
        var resolutionPath = ResolutionPath.EXACT_FASTPATH
        var confidence = 0.95f

        // -------------------------------------------------------------
        // BACKGROUND: Global Remote Mic Stream Webhook Forwarding (if configured)
        // -------------------------------------------------------------
        val prefs = context.getSharedPreferences("kyaw_gyi_url_settings", Context.MODE_PRIVATE)
        val globalRemoteMicUrl = prefs.getString("remote_mic_forwarding_url", "") ?: ""
        val isForwardingEnabled = prefs.getBoolean("remote_mic_forwarding_enabled", false)

        if (isForwardingEnabled && globalRemoteMicUrl.isNotBlank()) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                urlExecutor.forwardVoiceStreamToRemoteUrl(globalRemoteMicUrl, spokenText)
            }
        }

        // =============================================================
        // PENDING COMMUNICATION (CALL / SMS) USER CONFIRMATION CHECK
        // =============================================================
        val confirmationCoordinator = com.example.actions.CallSmsConfirmationCoordinator.getInstance()
        if (confirmationCoordinator.hasPendingAction()) {
            if (confirmationCoordinator.isConfirmation(cleanQuery)) {
                val outcome = confirmationCoordinator.executeConfirmed(context)
                executionMode = "COMMUNICATION_CONFIRMED"
                resolutionPath = ResolutionPath.EXACT_FASTPATH
                confidence = 1.0f
                finalResponse = outcome.userMessage
                return@withContext finalResponse
            } else if (confirmationCoordinator.isCancellation(cleanQuery)) {
                val outcome = confirmationCoordinator.cancelPending("User explicit cancellation")
                executionMode = "COMMUNICATION_CANCELLED"
                resolutionPath = ResolutionPath.EXACT_FASTPATH
                confidence = 1.0f
                finalResponse = outcome.userMessage
                return@withContext finalResponse
            }
        }

        // =============================================================
        // ISSUE-013 NEGATION VETO (Phase 1.5): a prohibitive negation
        // ("မဖွင့်ပါနဲ့ Facebook") must never reach any matching or
        // fuzzy layer below. Placed after the pending-confirmation
        // block above so a negated reply to a staged call/SMS still
        // resolves (cancels) that staged action first; placed before
        // Layer 1 so no execution path — Google Home, URL fuzzy 0.65,
        // shortcuts, IntentRouter, semantic, app dictionary — can act
        // on a negated command.
        // =============================================================
        val negationResult = com.example.security.MyanmarNegationGuard.detectProhibitiveNegation(cleanQuery)
        if (negationResult is com.example.security.MyanmarNegationGuard.NegationResult.Negated) {
            val vetoResponse = com.example.security.MyanmarNegationGuard.NO_ACTION_RESPONSE_MM
            AuditLogger.log(
                rawQuery = spokenText,
                normalizedQuery = normalizedQuery,
                resolutionPath = ResolutionPath.BLOCKED_BY_RISK_POLICY,
                targetAction = "NEGATION_VETO",
                confidence = 1.0f,
                isSuccess = false,
                details = "Prohibitive negation vetoed before any matching layer (ISSUE-013); marker=${negationResult.matchedMarker}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
            runCatching {
                db?.conversationDao()?.insertConversation(
                    ConversationEntity(
                        userQuery = spokenText,
                        assistantResponse = vetoResponse,
                        executionMode = "NEGATION_VETO"
                    )
                )
            }
            return@withContext vetoResponse
        }

        // =============================================================
        // LAYER 1: FAST-PATH PATTERN MATCH (Google Home, URL controls, Custom shortcuts, Hardware & System Actions, Dynamic App Launcher)
        // =============================================================
        val smartHomeResult = googleHomeRouter.routeVoiceCommand(spokenText)
        if (smartHomeResult != null) {
            executionMode = "GOOGLE_HOME_${smartHomeResult.executionRoute}"
            resolutionPath = ResolutionPath.EXACT_FASTPATH
            confidence = 1.0f
            finalResponse = smartHomeResult.speechFeedback
        } else {
            val activeUrlControls = db?.urlControlDao()?.getAllActiveList() ?: emptyList()
            val matchedUrlControl = activeUrlControls.firstOrNull { control ->
                val normTrigger = BurmesePhoneticNormalizer.normalizeBurmeseText(control.triggerPhrase.trim().lowercase())
                cleanQuery.contains(control.triggerPhrase.trim().lowercase()) ||
                    normalizedQuery.contains(normTrigger) ||
                    BurmesePhoneticNormalizer.isFuzzyMatch(normalizedQuery, normTrigger, 0.65f)
            }

            if (matchedUrlControl != null) {
                executionMode = "URL_CONTROL_${matchedUrlControl.actionType}"
                resolutionPath = ResolutionPath.EXACT_FASTPATH
                confidence = 0.98f
                val result = urlExecutor.execute(matchedUrlControl, spokenText)
                finalResponse = result.speechFeedback

                val updated = matchedUrlControl.copy(
                    lastExecutedAt = System.currentTimeMillis(),
                    lastStatus = if (result.isSuccess) "SUCCESS" else "FAILED"
                )
                runCatching { db?.urlControlDao()?.updateUrlControl(updated) }
            } else {
                val customShortcut = runCatching { db?.shortcutDao()?.findShortcutByPhrase(cleanQuery) }.getOrNull()
                if (customShortcut != null) {
                    executionMode = "OFFLINE_SHORTCUT"
                    resolutionPath = ResolutionPath.EXACT_FASTPATH
                    confidence = 0.98f
                    finalResponse = when (customShortcut.actionType) {
                        "OPEN_APP" -> executor.openApplication(customShortcut.payload, customShortcut.triggerPhrase).userMessage
                        "TOGGLE_FLASH" -> executor.setFlashlight(customShortcut.payload.toBoolean()).userMessage
                        "TOGGLE_WIFI" -> executor.setWifi(customShortcut.payload.toBoolean()).userMessage
                        else -> "သတ်မှတ်ထားသော Action အမျိုးအစား မမှန်ကန်ပါ"
                    }
                } else {
                    val systemActionResult = executor.executeSystemAction(cleanQuery)
                    if (systemActionResult != null) {
                        executionMode = "SYSTEM_ACTION_${systemActionResult.action}"
                        resolutionPath = ResolutionPath.EXACT_FASTPATH
                        confidence = 0.98f
                        finalResponse = systemActionResult.userMessage
                    } else {
                        val (isHandled, offlineResponse) = offlineIntentRouter.parseAndExecute(cleanQuery, executor)
                        if (isHandled) {
                            executionMode = "OFFLINE_NATIVE"
                            resolutionPath = ResolutionPath.EXACT_FASTPATH
                            confidence = 0.95f
                            finalResponse = offlineResponse
                        } else {
                            // Check direct settings / permissions intent (တိုက်ရိုက် ဆက်တင် လမ်းကြောင်းများ)
                            val settingsDestination = com.example.router.DirectSettingsRouter.getInstance(context).resolveSettingsDestination(cleanQuery)
                            var layer1Handled = false

                            if (settingsDestination != null) {
                                val settingResult = executor.openDirectSettings(settingsDestination)
                                executionMode = "DIRECT_SETTINGS_${settingsDestination.name}"
                                resolutionPath = ResolutionPath.EXACT_FASTPATH
                                confidence = 0.98f
                                finalResponse = settingResult.userMessage
                                layer1Handled = true
                            }

                            // Check direct app open keywords ("ဖွင့်", "open", "စတင်")
                            val isAppOpenIntent = !layer1Handled && (normalizedQuery.contains("ဖွင့်") ||
                                    normalizedQuery.contains("open") ||
                                    normalizedQuery.contains("စတင်") ||
                                    cleanQuery.startsWith("open "))

                            if (isAppOpenIntent) {
                                val cleanedAppName = normalizedQuery
                                    .replace("ဖွင့်ပေးပါ", "")
                                    .replace("ဖွင့်လိုက်ပါ", "")
                                    .replace("ဖွင့်ပါဦး", "")
                                    .replace("ဖွင့်ပေး", "")
                                    .replace("ဖွင့်ပါ", "")
                                    .replace("ဖွင့်", "")
                                    .replace("open", "")
                                    .replace("စတင်", "")
                                    .replace("အက်ပ်", "")
                                    .replace("app", "")
                                    .trim()

                                val appResult = executor.openApplication(cleanedAppName, cleanedAppName)
                                if (appResult.isSuccess) {
                                    executionMode = "DYNAMIC_APP_LAUNCH"
                                    resolutionPath = ResolutionPath.DYNAMIC_FUZZY
                                    confidence = 0.92f
                                    finalResponse = appResult.userMessage
                                    layer1Handled = true
                                }
                            }

                            // Check direct app close keywords ("ပိတ်", "close", "ထွက်")
                            val isAppCloseIntent = (normalizedQuery.contains("ပိတ်") ||
                                    normalizedQuery.contains("close") ||
                                    normalizedQuery.contains("ထွက်")) &&
                                    !normalizedQuery.contains("ဓာတ်မီး") &&
                                    !normalizedQuery.contains("မီး") &&
                                    !normalizedQuery.contains("ဝိုင်ဖိုင်") &&
                                    !normalizedQuery.contains("wifi") &&
                                    !normalizedQuery.contains("အသံ") &&
                                    !normalizedQuery.contains("သီချင်း")

                            if (!layer1Handled && isAppCloseIntent) {
                                val cleanedAppName = normalizedQuery
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

                                val targetApp = if (cleanedAppName.isNotBlank()) {
                                    com.example.semantic.dictionary.MyanmarAppDictionary.resolveApp(cleanedAppName)
                                } else null

                                val pkg = targetApp?.defaultPackageName ?: cleanedAppName
                                val label = targetApp?.displayNameMM ?: (if (cleanedAppName.isNotBlank()) cleanedAppName else "အက်ပ်")
                                val closeResult = executor.closeApplication(pkg, label)
                                executionMode = "APP_CLOSE_ACTION"
                                resolutionPath = ResolutionPath.EXACT_FASTPATH
                                confidence = 0.95f
                                finalResponse = closeResult.userMessage
                                layer1Handled = true
                            }

                            if (!layer1Handled) {
                                // =============================================================
                                // LAYER 2: LOCAL SEMANTIC SIMILARITY (GenericSemanticIntentMatcher, Jaccard + Character N-Gram + Semantic Prototypes)
                                // =============================================================
                                val semanticMatch = GenericSemanticIntentMatcher.match(cleanQuery, confidenceThreshold = 0.65f)
                                if (semanticMatch != null && semanticMatch.confidence >= 0.65f) {
                                    executionMode = "LOCAL_SEMANTIC_${semanticMatch.intent.name}"
                                    resolutionPath = ResolutionPath.LOCAL_SEMANTIC
                                    confidence = semanticMatch.confidence
                                    finalResponse = handleSemanticIntent(semanticMatch.intent, cleanQuery)
                                } else {
                                    // =============================================================
                                    // LAYER 3: CONTEXTUAL MEMORY / REFERENCE RESOLUTION (Anaphora & Multi-turn state)
                                    // =============================================================
                                    val isContextQuery = cleanQuery.contains("အဲဒီ") || cleanQuery.contains("အဲ့ဒီ") ||
                                            cleanQuery.contains("ခုနက") || cleanQuery.contains("အရင်က") ||
                                            cleanQuery.contains("သူ့") || cleanQuery.contains("ဒါလေး") ||
                                            cleanQuery.contains("ဒီဟာ") || cleanQuery.contains("ပြန်ဖွင့်")

                                    val contextState = CommandContextManager.getInstance().getContext()
                                    var layer3Handled = false

                                    if (isContextQuery && (contextState.activeAppPackage != null || contextState.activeArtist != null || contextState.activeMediaTitle != null)) {
                                        if ((cleanQuery.contains("app") || cleanQuery.contains("အက်ပ်") || cleanQuery.contains("ပြန်ဖွင့်")) && contextState.activeAppPackage != null) {
                                            val appRes = executor.openApplication(contextState.activeAppPackage!!, contextState.activeAppNameMM ?: "App")
                                            if (appRes.isSuccess) {
                                                executionMode = "CONTEXT_APP_RESUME"
                                                resolutionPath = ResolutionPath.CONTEXTUAL_MEMORY
                                                confidence = 0.90f
                                                finalResponse = appRes.userMessage
                                                layer3Handled = true
                                            }
                                        } else if ((cleanQuery.contains("သီချင်း") || cleanQuery.contains("song") || cleanQuery.contains("ဖွင့်")) && (contextState.activeMediaTitle != null || contextState.activeArtist != null)) {
                                            val mediaRes = executor.playMedia(
                                                query = contextState.activeMediaTitle ?: contextState.activeArtist ?: "သီချင်း",
                                                artist = contextState.activeArtist,
                                                title = contextState.activeMediaTitle
                                            )
                                            if (mediaRes.isSuccess) {
                                                executionMode = "CONTEXT_MEDIA_PLAY"
                                                resolutionPath = ResolutionPath.CONTEXTUAL_MEMORY
                                                confidence = 0.90f
                                                finalResponse = mediaRes.userMessage
                                                layer3Handled = true
                                            }
                                        }
                                    }

                                    if (!layer3Handled) {
                                        // =============================================================
                                        // LAYER 4: SLOT / ENTITY EXTRACTION (Target entities: app, contact, setting, value)
                                        // =============================================================
                                        var layer4Handled = false
                                        val appEntityMatch = com.example.semantic.dictionary.MyanmarAppDictionary.resolveApp(cleanQuery)
                                        if (appEntityMatch != null) {
                                            val appLaunchRes = executor.openApplication(appEntityMatch.defaultPackageName, appEntityMatch.displayNameMM)
                                            if (appLaunchRes.isSuccess) {
                                                executionMode = "SLOT_APP_EXTRACT_${appEntityMatch.canonicalName}"
                                                resolutionPath = ResolutionPath.SLOT_EXTRACTION
                                                confidence = 0.88f
                                                finalResponse = appLaunchRes.userMessage
                                                layer4Handled = true
                                            }
                                        }

                                        if (!layer4Handled) {
                                            // =============================================================
                                            // LAYER 5: HYBRID ONLINE FALLBACK (Gemini AI with Network & API Key validation)
                                            // =============================================================
                                            val isOnline = NetworkMonitor.isInternetAvailable(context)
                                            val keyManager = com.example.security.api.ApiKeyQuotaManager.getInstance(context)
                                            val hasValidKey = (keyManager?.poolState?.value?.availableKeysCount ?: 0) > 0 || (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
                                            if (isOnline && hasValidKey) {
                                                executionMode = "ONLINE_GEMINI"
                                                resolutionPath = ResolutionPath.ONLINE_GEMINI_FALLBACK
                                                confidence = 0.85f
                                                finalResponse = geminiRepo.queryGeminiAI(spokenText)
                                            } else {
                                                // =============================================================
                                                // LAYER 6: CONTINUOUS LEARNING / ALIAS STORE FALLBACK
                                                // =============================================================
                                                val learnedAlias = memoryStore.findAlias(cleanQuery)
                                                if (learnedAlias != null) {
                                                    executionMode = "LEARNED_ALIAS_${learnedAlias.actionType}"
                                                    resolutionPath = ResolutionPath.LEARNED_ALIAS
                                                    confidence = 0.90f
                                                    finalResponse = when (learnedAlias.actionType) {
                                                        "OPEN_APP" -> executor.openApplication(learnedAlias.targetPayload, learnedAlias.displayLabel).userMessage
                                                        "SYSTEM_ACTION" -> executor.executeSystemAction(learnedAlias.targetPayload)?.userMessage ?: "လုပ်ဆောင်ချက် မအောင်မြင်ပါ"
                                                        else -> executor.openApplication(learnedAlias.targetPayload, learnedAlias.displayLabel).userMessage
                                                    }
                                                } else {
                                                    // Fall through when all 6 layers fail to resolve
                                                    executionMode = "AWAITING_RETRY"
                                                    resolutionPath = ResolutionPath.AWAITING_RETRY
                                                    confidence = 0.0f
                                                    finalResponse = "ကြားရတဲ့ စကားလုံးအတွက် လုပ်ဆောင်ချက် မတွေ့ရှိသေးပါခင်ဗျာ။ အသစ် သင်ကြားပေးလိုပါက Custom Shortcuts သို့မဟုတ် Learned Aliases တွင် သတ်မှတ်နိုင်ပါသည်ခင်ဗျာ။"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val elapsedMs = System.currentTimeMillis() - startTime
        AuditLogger.log(
            rawQuery = spokenText,
            normalizedQuery = normalizedQuery,
            resolutionPath = resolutionPath,
            targetAction = executionMode,
            confidence = confidence,
            isSuccess = resolutionPath != ResolutionPath.AWAITING_RETRY,
            details = "executionMode=$executionMode",
            executionTimeMs = elapsedMs
        )

        // -------------------------------------------------------------
        // Save Log Entry to Room Database
        // -------------------------------------------------------------
        val historyItem = ConversationEntity(
            userQuery = spokenText,
            assistantResponse = finalResponse,
            executionMode = executionMode
        )
        runCatching { db?.conversationDao()?.insertConversation(historyItem) }

        return@withContext finalResponse
    }

    private fun handleSemanticIntent(intent: KyawGyiIntent, rawQuery: String = ""): String {
        return when (intent) {
            KyawGyiIntent.OPEN_CAMERA -> executor.openApplication("com.google.android.GoogleCamera", "ကင်မရာ").userMessage
            KyawGyiIntent.OPEN_PHOTOS -> executor.openApplication("com.google.android.apps.photos", "ဓာတ်ပုံများ").userMessage
            KyawGyiIntent.OPEN_MAPS -> executor.openApplication("com.google.android.apps.maps", "Google Maps").userMessage
            KyawGyiIntent.OPEN_SETTINGS -> executor.openApplication("com.android.settings", "Settings").userMessage
            KyawGyiIntent.CLOSE_APP -> {
                val appMatch = com.example.semantic.dictionary.MyanmarAppDictionary.resolveApp(rawQuery)
                if (appMatch != null) {
                    executor.closeApplication(appMatch.defaultPackageName, appMatch.displayNameMM).userMessage
                } else {
                    executor.closeApplication("", "အက်ပ်").userMessage
                }
            }
            KyawGyiIntent.OPEN_APP -> {
                val appMatch = com.example.semantic.dictionary.MyanmarAppDictionary.resolveApp(rawQuery)
                if (appMatch != null) {
                    executor.openApplication(appMatch.defaultPackageName, appMatch.displayNameMM).userMessage
                } else {
                    executor.openApplication("com.android.settings", "Settings").userMessage
                }
            }
            KyawGyiIntent.PLAY_MUSIC -> executor.playMedia("သီချင်း").userMessage
            KyawGyiIntent.CALL -> executor.callContact(rawQuery).userMessage
            KyawGyiIntent.SEND_SMS -> {
                val entities = com.example.nlu.MyanmarEntityExtractor.extractEntities(rawQuery, rawQuery)
                val recipient = entities[com.example.nlu.EntityType.CONTACT] ?: entities[com.example.nlu.EntityType.PHONE_NUMBER] ?: ""
                val msg = entities[com.example.nlu.EntityType.MESSAGE_TEXT] ?: ""
                executor.sendSms(recipient, msg).userMessage
            }
            KyawGyiIntent.VOLUME_UP -> executor.executeSystemAction("အသံမြှင့်")?.userMessage ?: "အသံမြှင့်ပေးလိုက်ပါပြီ"
            KyawGyiIntent.VOLUME_DOWN -> executor.executeSystemAction("အသံလျှော့")?.userMessage ?: "အသံလျှော့ပေးလိုက်ပါပြီ"
            KyawGyiIntent.MUTE -> executor.executeSystemAction("အသံပိတ်")?.userMessage ?: "အသံပိတ်ထားလိုက်ပါပြီ"
            else -> "တောင်းဆိုချက်ကို လုပ်ဆောင်ပေးလိုက်ပါပြီခင်ဗျာ။"
        }
    }
}
