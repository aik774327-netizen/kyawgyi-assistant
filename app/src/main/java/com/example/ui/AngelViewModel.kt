package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

enum class CoreEngine(val title: String) {
    RESPONSE_ENGINE("Response Engine"),
    BRAIN_CORE("Brain Core"),
    KYAW_GYI_VOICE("Kyaw Gyi Voice"),
    MEMORY_SYSTEM("Memory System"),
    SETTINGS("Settings")
}

enum class ResponseMode(val label: String) {
    QUICK("QUICK"),
    EXPERT("EXPERT"),
    CREATIVE("CREATIVE"),
    TEACHING("TEACHING")
}

class AngelViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: AngelRepository
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _selectedEngine = MutableStateFlow(CoreEngine.RESPONSE_ENGINE)
    val selectedEngine: StateFlow<CoreEngine> = _selectedEngine.asStateFlow()

    private val _selectedMode = MutableStateFlow(ResponseMode.QUICK)
    val selectedMode: StateFlow<ResponseMode> = _selectedMode.asStateFlow()

    private val _modelPreset = MutableStateFlow("Flash")
    val modelPreset: StateFlow<String> = _modelPreset.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _systemPersona = MutableStateFlow(com.example.cognitive.KyawGyiSystemPersona.PERSONAL_AI_COMPANION_SYSTEM_PROMPT)
    val systemPersona: StateFlow<String> = _systemPersona.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _autoSpeakEnabled = MutableStateFlow(true)
    val autoSpeakEnabled: StateFlow<Boolean> = _autoSpeakEnabled.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(0.92f)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    private val _voicePitch = MutableStateFlow(1.0f)
    val voicePitch: StateFlow<Float> = _voicePitch.asStateFlow()

    private val _wakeWord = MutableStateFlow("ကျော်ကြီး")
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val languageRepo = com.example.data.LanguagePreferencesRepository.getInstance(application)
    val selectedLanguageCode: StateFlow<String> = languageRepo.selectedLanguageState

    private val _systemLanguage = MutableStateFlow("Burmese (Myanmar - my)")
    val systemLanguage: StateFlow<String> = _systemLanguage.asStateFlow()

    val messages: StateFlow<List<ChatMessageEntity>>
    val last50Messages: StateFlow<List<ChatMessageEntity>>
    val memories: StateFlow<List<MemoryEntity>>
    val roomTasks: StateFlow<List<Task>>
    val tasks: StateFlow<List<DailyTaskEntity>>
    val habits: StateFlow<List<HabitEntity>>
    val focusSessions: StateFlow<List<FocusSessionEntity>>
    val commandLogs: StateFlow<List<CommandLogEntity>>
    val customShortcuts: StateFlow<List<CustomShortcutEntity>>
    val plannerItems: StateFlow<List<DailyPlannerItemEntity>>
    val plannerTasks: StateFlow<List<DailyPlannerItemEntity>>
    val plannerHabits: StateFlow<List<DailyPlannerItemEntity>>
    val ingestedAssets: StateFlow<List<com.example.ingestion.IngestedAssetEntity>>

    private val taskBackupDataStore = TaskBackupDataStore(application)
    val lastBackupMetadata: StateFlow<TaskBackupMetadata?> = taskBackupDataStore.lastBackupMetadata
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val themePreferencesRepository = ThemePreferencesRepository.getInstance(application)
    val themeMode: StateFlow<ThemeMode> = themePreferencesRepository.themeMode

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferencesRepository.setThemeMode(mode)
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            themePreferencesRepository.toggleLightDark()
        }
    }

    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    fun clearBackupMessage() {
        _backupStatusMessage.value = null
    }

    fun backupTasksToDataStore() {
        viewModelScope.launch {
            try {
                val currentTasks = roomTasks.value
                val count = taskBackupDataStore.backupTasks(currentTasks)
                _backupStatusMessage.value = "DataStore Backup အောင်မြင်သည် - တာဝန် $count ခု သိမ်းဆည်းပြီးပါပြီ"
            } catch (e: Exception) {
                _backupStatusMessage.value = "Backup ပြုလုပ်ရာတွင် အမှားဖြစ်ပွားသည်: ${e.localizedMessage}"
            }
        }
    }

    fun restoreTasksFromDataStore() {
        viewModelScope.launch {
            try {
                val restoredTasks = taskBackupDataStore.restoreTasks()
                if (restoredTasks.isNotEmpty()) {
                    for (task in restoredTasks) {
                        repository.insertTask(task)
                    }
                    _backupStatusMessage.value = "DataStore မှ တာဝန် ${restoredTasks.size} ခု အောင်မြင်စွာ ပြန်လည်ရယူပြီးပါပြီ"
                } else {
                    _backupStatusMessage.value = "DataStore တွင် သိမ်းဆည်းထားသော တာဝန် မရှိသေးပါ"
                }
            } catch (e: Exception) {
                _backupStatusMessage.value = "Restore ပြုလုပ်ရာတွင် အမှားဖြစ်ပွားသည်: ${e.localizedMessage}"
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        val apiService = GeminiApiService(application)
        repository = AngelRepository(
            chatDao = db.chatDao(),
            memoryDao = db.memoryDao(),
            dailyTaskDao = db.dailyTaskDao(),
            taskDao = db.taskDao(),
            habitDao = db.habitDao(),
            focusSessionDao = db.focusSessionDao(),
            commandLogDao = db.commandLogDao(),
            customShortcutDao = db.customShortcutDao(),
            apiService = apiService,
            dailyPlannerDao = db.dailyPlannerDao()
        )

        val defaultLocale = Locale.getDefault()
        _systemLanguage.value = "${defaultLocale.displayLanguage} (${defaultLocale.language})"

        messages = repository.allMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        last50Messages = repository.last50Messages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        memories = repository.allMemories.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        roomTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        tasks = repository.allDailyTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        habits = repository.allHabits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        focusSessions = repository.allFocusSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        commandLogs = repository.allCommandLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        customShortcuts = repository.allCustomShortcuts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        plannerItems = repository.allPlannerItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        plannerTasks = repository.plannerTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        plannerHabits = repository.plannerHabits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        ingestedAssets = db.ingestedAssetDao().getAllAssetsFlow().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Listen to DataStore voice language changes
        viewModelScope.launch {
            languageRepo.selectedLanguageFlow.collect { langCode ->
                val loc = languageRepo.createLocale(langCode)
                _systemLanguage.value = "${loc.displayLanguage} (${loc.language})"
                if (isTtsReady) {
                    try {
                        tts?.setLanguage(loc)
                    } catch (ignored: Exception) {}
                }
            }
        }

        // Initialize Android TextToSpeech engine
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val targetLocale = languageRepo.getSelectedLocaleSync()
            val result = tts?.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (targetLocale.language == "my") {
                    val altResult = tts?.setLanguage(Locale.forLanguageTag("my"))
                    if (altResult == TextToSpeech.LANG_MISSING_DATA || altResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.US)
                    }
                } else {
                    tts?.setLanguage(Locale.US)
                }
            }
            isTtsReady = true
        }
    }

    fun setSelectedLanguage(languageCode: String) {
        viewModelScope.launch {
            languageRepo.savePreferredLanguage(languageCode)
            val loc = languageRepo.createLocale(languageCode)
            _systemLanguage.value = "${loc.displayLanguage} (${loc.language})"
            if (isTtsReady) {
                try {
                    tts?.setLanguage(loc)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun setVoiceSpeed(speed: Float) {
        _voiceSpeed.value = speed
        tts?.setSpeechRate(speed)
    }

    fun setVoicePitch(pitch: Float) {
        _voicePitch.value = pitch
        tts?.setPitch(pitch)
    }

    fun setWakeWord(word: String) {
        if (word.isNotBlank()) {
            _wakeWord.value = word.trim()
        }
    }

    fun speakText(text: String) {
        if (!_ttsEnabled.value || !com.example.utils.MyanmarTTSEngine.masterTtsEnabled) {
            android.util.Log.d("AngelViewModel", "TTS is disabled. Skipping speech output: $text")
            return
        }

        if (isTtsReady && tts != null) {
            val cleanText = com.example.utils.MyanmarTTSEngine.preprocessText(text)
            if (cleanText.isEmpty()) return

            // Check if text contains Myanmar unicode range (U+1000 to U+109F / U+AA60 to U+AA7F)
            val containsMyanmar = cleanText.any { it.code in 0x1000..0x109F || it.code in 0xAA60..0xAA7F }

            if (containsMyanmar) {
                val myLocale = Locale.forLanguageTag("my-MM")
                val res = tts?.setLanguage(myLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val altLocale = Locale.forLanguageTag("my")
                    val altRes = tts?.setLanguage(altLocale)
                    if (altRes == TextToSpeech.LANG_MISSING_DATA || altRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                }
            } else {
                tts?.setLanguage(Locale.US)
            }

            // Apply real-time voice speed and pitch state variables
            tts?.setSpeechRate(_voiceSpeed.value)
            tts?.setPitch(_voicePitch.value)

            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "AngelTTS")
        }
    }

    fun deleteMessageById(id: Long) {
        viewModelScope.launch {
            repository.deleteMessageById(id)
        }
    }

    fun logCommand(
        commandText: String,
        commandType: String,
        status: String,
        actionDetail: String,
        failureCategory: String = "NONE",
        failureReason: String = "",
        suggestedAlternative: String = "",
        isRepeatedFailure: Boolean = false,
        remediationAction: String = "",
        remediationUri: String = ""
    ) {
        viewModelScope.launch {
            repository.insertCommandLog(
                CommandLogEntity(
                    commandText = commandText,
                    commandType = commandType,
                    status = status,
                    actionDetail = actionDetail,
                    failureCategory = failureCategory,
                    failureReason = failureReason,
                    suggestedAlternative = suggestedAlternative,
                    isRepeatedFailure = isRepeatedFailure,
                    remediationAction = remediationAction,
                    remediationUri = remediationUri
                )
            )
        }
    }

    fun logCommandFailure(
        commandText: String,
        commandType: String,
        exception: Throwable? = null,
        detail: String? = null
    ) {
        viewModelScope.launch {
            val analysis = com.example.audit.CommandFailureAnalyzer.classifyFailure(commandText, commandType, exception, detail)
            val previous = repository.findRecentFailure(commandText)
            val flow = com.example.fallback.AlternativeFlowManager.resolveFlow(
                context = getApplication(),
                commandText = commandText,
                commandType = commandType,
                target = null,
                failureCategory = analysis.category,
                failureDetail = (detail ?: "") + " " + (exception?.message ?: "")
            )
            repository.insertCommandLog(
                CommandLogEntity(
                    commandText = commandText,
                    commandType = commandType,
                    status = "FAILED",
                    actionDetail = detail ?: analysis.technicalDetails,
                    failureCategory = analysis.category.code,
                    failureReason = analysis.reasonMm,
                    suggestedAlternative = analysis.suggestedAlternativeMm,
                    isRepeatedFailure = previous != null,
                    remediationAction = flow.fallbackIntentAction ?: "",
                    remediationUri = flow.fallbackIntentUri ?: ""
                )
            )
        }
    }

    fun deleteCommandLogById(id: Long) {
        viewModelScope.launch {
            repository.deleteCommandLogById(id)
        }
    }

    fun clearAllCommandLogs() {
        viewModelScope.launch {
            repository.clearCommandLogs()
        }
    }

    fun addCustomShortcut(phrase: String, target: String, desc: String) {
        if (phrase.isBlank() || target.isBlank()) return
        viewModelScope.launch {
            repository.insertCustomShortcut(
                CustomShortcutEntity(
                    phrase = phrase.trim(),
                    targetPackageOrAction = target.trim(),
                    description = desc.ifBlank { "Custom User Shortcut" }
                )
            )
        }
    }

    fun toggleCustomShortcut(shortcut: CustomShortcutEntity) {
        viewModelScope.launch {
            repository.updateCustomShortcut(shortcut.copy(isEnabled = !shortcut.isEnabled))
        }
    }

    fun deleteCustomShortcut(shortcut: CustomShortcutEntity) {
        viewModelScope.launch {
            repository.deleteCustomShortcut(shortcut)
        }
    }

    fun exportHistoryAsFormattedText(context: android.content.Context): java.io.File? {
        val sb = java.lang.StringBuilder()
        sb.append("=== KYAW GYI CONVERSATION HISTORY EXPORT ===\n")
        sb.append("Export Date: ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n\n")

        for (msg in messages.value) {
            val role = if (msg.role == "user") "USER" else "KYAW GYI AI"
            val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
            sb.append("[$time] $role:\n").append(msg.content).append("\n\n")
        }

        return try {
            val file = java.io.File(context.getExternalFilesDir(null), "kyaw_gyi_history_${System.currentTimeMillis()}.txt")
            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            null
        }
    }

    fun exportHistoryAsJson(context: android.content.Context): java.io.File? {
        val array = org.json.JSONArray()
        for (msg in messages.value) {
            val obj = org.json.JSONObject()
            obj.put("id", msg.id)
            obj.put("role", msg.role)
            obj.put("content", msg.content)
            obj.put("responseMode", msg.responseMode)
            obj.put("engineType", msg.engineType)
            obj.put("timestamp", msg.timestamp)
            array.put(obj)
        }

        return try {
            val file = java.io.File(context.getExternalFilesDir(null), "kyaw_gyi_history_${System.currentTimeMillis()}.json")
            file.writeText(array.toString(2))
            file
        } catch (e: Exception) {
            null
        }
    }

    fun exportChatHistoryJson(context: android.content.Context) {
        val file = exportHistoryAsJson(context)
        if (file != null) {
            android.widget.Toast.makeText(context, "JSON History exported to: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun exportChatHistoryText(context: android.content.Context) {
        val file = exportHistoryAsFormattedText(context)
        if (file != null) {
            android.widget.Toast.makeText(context, "Text History exported to: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun setEngine(engine: CoreEngine) {
        _selectedEngine.value = engine
    }

    fun setResponseMode(mode: ResponseMode) {
        _selectedMode.value = mode
    }

    fun setModelPreset(preset: String) {
        _modelPreset.value = preset
    }

    fun setSystemPersona(persona: String) {
        _systemPersona.value = persona
    }

    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        _autoSpeakEnabled.value = enabled
        com.example.utils.MyanmarTTSEngine.masterTtsEnabled = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun setAutoSpeakEnabled(enabled: Boolean) {
        _autoSpeakEnabled.value = enabled
        _ttsEnabled.value = enabled
        com.example.utils.MyanmarTTSEngine.masterTtsEnabled = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    fun sendMessage(prompt: String, imageUri: String? = null) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() && imageUri == null) return

        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                role = "user",
                content = if (trimmed.isNotEmpty()) trimmed else "[Attachment: $imageUri]",
                responseMode = _selectedMode.value.name,
                engineType = _selectedEngine.value.title,
                imageUri = imageUri
            )
            repository.insertMessage(userMsg)

            _isGenerating.value = true

            val pipeline = com.example.pipeline.UnifiedIntelligencePipeline.getInstance(getApplication())
            var replyText: String = ""
            var thoughtChain: String? = null

            if (imageUri != null) {
                // Ingest and route attachment using UniversalDataIngestionManager
                val ingestionMgr = com.example.ingestion.UniversalDataIngestionManager(getApplication())
                val parsedUri = android.net.Uri.parse(imageUri)
                val ingestResult = ingestionMgr.ingestUri(parsedUri)
                
                val req = com.example.pipeline.contract.UnifiedCommandRequest(
                    requestId = "msg_${java.util.UUID.randomUUID()}",
                    sessionId = "angel_chat_session",
                    transcript = trimmed.ifEmpty { "Ingest file: ${ingestResult.originalFileName}" },
                    rawInput = trimmed,
                    source = com.example.pipeline.contract.InputSource.TYPED,
                    attachedUri = imageUri,
                    attachedAssetId = ingestResult.asset?.id,
                    attachedAssetCategory = ingestResult.asset?.category,
                    metadata = mapOf("fileName" to ingestResult.originalFileName)
                )
                val pipeResult = pipeline.processCommand(req, speakResponse = false)
                
                replyText = if (ingestResult.isSuccess) {
                    "${ingestResult.messageMM}\n\nအကျဉ်းချုပ်: ${ingestResult.asset?.extractedSummary ?: "အညွှန်းပြုစုပြီးပါပြီ"}"
                } else {
                    pipeResult.responseMyanmar
                }
                thoughtChain = "Ingestion: ${if (ingestResult.isDuplicate) "DUPLICATE_FOUND" else "NEW_ASSET_INGESTED"}, Hash: ${ingestResult.asset?.sha256Hash?.take(8)}..."
            } else if (_selectedEngine.value == CoreEngine.KYAW_GYI_VOICE || _selectedEngine.value == CoreEngine.BRAIN_CORE) {
                val request = com.example.pipeline.contract.UnifiedCommandRequest(
                    requestId = "msg_${java.util.UUID.randomUUID()}",
                    sessionId = "angel_chat_session",
                    transcript = trimmed,
                    rawInput = trimmed,
                    source = com.example.pipeline.contract.InputSource.TYPED
                )
                val pipelineRes = pipeline.processCommand(request, speakResponse = false)
                replyText = pipelineRes.responseMyanmar
                thoughtChain = "Intent: ${pipelineRes.intent}, Confidence: ${pipelineRes.confidence}, Offline: ${pipelineRes.isOfflineProcessed}"
            } else {
                try {
                    val (aiReply, chain) = repository.generateAiResponse(
                        prompt = trimmed.ifEmpty { "Describe this request." },
                        mode = _selectedMode.value.name,
                        engine = _selectedEngine.value.title,
                        systemPrompt = _systemPersona.value,
                        history = messages.value
                    )
                    replyText = aiReply
                    thoughtChain = chain
                } catch (e: Exception) {
                    if (com.example.data.GeminiApiService.isNoApiKeyException(e)) {
                        replyText = com.example.data.GeminiApiService.NO_API_KEY_MESSAGE
                        thoughtChain = "Error: NO_API_KEY_CONFIGURED"
                    } else if (com.example.data.GeminiApiService.isQuotaOrRateLimitException(e)) {
                        replyText = com.example.data.GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE
                        thoughtChain = "HTTP 429 RESOURCE_EXHAUSTED - Quota limit reached"
                    } else {
                        replyText = "မေးမြန်းမှုကို ဖြေကြားရာတွင် ချို့ယွင်းချက် ဖြစ်ပေါ်နေပါသည်: ${e.localizedMessage ?: "မသိရှိသော အမှား"}"
                        thoughtChain = "Error: ${e.javaClass.simpleName}"
                    }
                }
            }

            val angelMsg = ChatMessageEntity(
                role = "angel",
                content = replyText,
                responseMode = _selectedMode.value.name,
                engineType = _selectedEngine.value.title,
                thoughtChain = thoughtChain
            )
            repository.insertMessage(angelMsg)

            _isGenerating.value = false

            if (_autoSpeakEnabled.value) {
                speakText(replyText)
            }
        }
    }

    fun ingestUserFile(uri: android.net.Uri, source: String = "USER_UPLOAD") {
        viewModelScope.launch {
            val ingestionMgr = com.example.ingestion.UniversalDataIngestionManager(getApplication())
            val result = ingestionMgr.ingestUri(uri, source)
            val angelMsg = ChatMessageEntity(
                role = "angel",
                content = "📁 ${result.messageMM}\n\n" + (result.asset?.extractedSummary?.let { "အကျဉ်းချုပ်: $it" } ?: ""),
                responseMode = "QUICK",
                engineType = "Ingestion Engine",
                thoughtChain = "Ingested: ${result.asset?.fileName}, SHA256: ${result.asset?.sha256Hash?.take(10)}..."
            )
            repository.insertMessage(angelMsg)
        }
    }

    fun deleteIngestedAsset(asset: com.example.ingestion.IngestedAssetEntity) {
        viewModelScope.launch {
            val db = AppDatabase.getDatabase(getApplication())
            val ingestionMgr = com.example.ingestion.UniversalDataIngestionManager(getApplication())
            try {
                // Safely release persistable URI permission if applicable
                try {
                    ingestionMgr.releasePersistableUriPermissionSafely(android.net.Uri.parse(asset.fileUri))
                } catch (e: Exception) {
                    android.util.Log.w("AngelViewModel", "Could not release persistable URI for ${asset.fileUri}", e)
                }

                val file = java.io.File(asset.storedRelativePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.w("AngelViewModel", "Could not delete vault file", e)
            }
            db.ingestedAssetDao().deleteAsset(asset)
        }
    }

    fun addMemory(key: String, value: String, category: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            repository.insertMemory(
                MemoryEntity(
                    key = key.trim(),
                    value = value.trim(),
                    category = category.ifBlank { "Memory Store" }
                )
            )
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    // Task management
    fun addTask(
        title: String,
        category: String = "General",
        priority: String = "MEDIUM",
        dueDateMillis: Long? = null,
        tags: String = ""
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title.trim(),
                    category = category.ifBlank { "General" },
                    priority = priority.ifBlank { "MEDIUM" },
                    dueDateMillis = dueDateMillis,
                    tags = tags.ifBlank { category }
                )
            )
            repository.insertDailyTask(
                DailyTaskEntity(
                    title = title.trim(),
                    category = category.ifBlank { "General" }
                )
            )
        }
    }

    fun toggleTaskCompleted(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            com.example.utils.TaskReminderScheduler.cancelReminder(getApplication(), task.id)
            repository.deleteTask(task)
        }
    }

    // WorkManager Task Reminder Notifications
    fun scheduleTaskReminder(task: Task, delayMillis: Long) {
        com.example.utils.TaskReminderScheduler.scheduleReminderWithDelay(
            context = getApplication(),
            task = task,
            delayMillis = delayMillis
        )
        val minutes = delayMillis / 60000
        _backupStatusMessage.value = "⏰ '${task.title}' အတွက် မိနစ် $minutes အကြာတွင် သတိပေးချက် သတ်မှတ်လိုက်ပါပြီ"
    }

    fun scheduleTaskReminderAtTimestamp(task: Task, targetEpochMillis: Long) {
        com.example.utils.TaskReminderScheduler.scheduleReminderAtTimestamp(
            context = getApplication(),
            task = task,
            targetEpochMillis = targetEpochMillis
        )
        _backupStatusMessage.value = "⏰ '${task.title}' သတ်မှတ်ချိန်အတွက် သတိပေးချက် သတ်မှတ်လိုက်ပါပြီ"
    }

    fun triggerTestTaskReminder(task: Task) {
        com.example.utils.TaskReminderScheduler.triggerImmediateTestReminder(
            context = getApplication(),
            task = task
        )
        _backupStatusMessage.value = "🔔 '${task.title}' အတွက် ၃ စက္ကန့်အကြာတွင် Push Notification ပေးပို့ပါမည်"
    }

    fun cancelTaskReminder(taskId: Long) {
        com.example.utils.TaskReminderScheduler.cancelReminder(
            context = getApplication(),
            taskId = taskId
        )
        _backupStatusMessage.value = "သတိပေးချက် ပယ်ဖျက်ပြီးပါပြီ"
    }

    fun toggleDailyTaskCompleted(task: DailyTaskEntity) {
        viewModelScope.launch {
            repository.updateDailyTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteDailyTask(task: DailyTaskEntity) {
        viewModelScope.launch {
            repository.deleteDailyTask(task)
        }
    }

    // Habit management
    fun addHabit(title: String, frequency: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertHabit(
                HabitEntity(
                    title = title.trim(),
                    frequency = frequency.ifBlank { "Daily" }
                )
            )
        }
    }

    fun toggleHabitCompleted(habit: HabitEntity) {
        viewModelScope.launch {
            val newCompleted = !habit.isCompletedToday
            val newStreak = if (newCompleted) habit.streakCount + 1 else maxOf(0, habit.streakCount - 1)
            repository.updateHabit(
                habit.copy(
                    isCompletedToday = newCompleted,
                    streakCount = newStreak,
                    lastCompletedTimestamp = if (newCompleted) System.currentTimeMillis() else habit.lastCompletedTimestamp
                )
            )
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    // Focus Session management
    fun addFocusSession(
        sessionName: String,
        durationMinutes: Int,
        notes: String,
        category: String = "Work",
        colorHex: String = "#3B82F6"
    ) {
        if (sessionName.isBlank() || durationMinutes <= 0) return
        viewModelScope.launch {
            repository.insertFocusSession(
                FocusSessionEntity(
                    sessionName = sessionName.trim(),
                    durationMinutes = durationMinutes,
                    notes = notes.trim(),
                    category = category,
                    colorHex = colorHex
                )
            )
        }
    }

    fun deleteFocusSession(session: FocusSessionEntity) {
        viewModelScope.launch {
            repository.deleteFocusSession(session)
        }
    }

    // Daily Planner operations
    fun addPlannerTask(title: String, category: String = "Work", priority: String = "Medium") {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertPlannerItem(
                DailyPlannerItemEntity(
                    title = title.trim(),
                    category = category,
                    priority = priority,
                    isHabit = false,
                    isCompleted = false,
                    scheduledDate = "Today"
                )
            )
        }
    }

    fun addPlannerHabit(title: String, category: String = "Health", targetDays: Int = 7) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertPlannerItem(
                DailyPlannerItemEntity(
                    title = title.trim(),
                    category = category,
                    isHabit = true,
                    isCompleted = false,
                    streakCount = 0,
                    targetDaysPerWeek = targetDays.coerceIn(1, 7),
                    weeklyCompletionHistory = "0,0,0,0,0,0,0"
                )
            )
        }
    }

    fun togglePlannerItem(item: DailyPlannerItemEntity) {
        viewModelScope.launch {
            val newCompleted = !item.isCompleted
            val newStreak = if (item.isHabit) {
                if (newCompleted) item.streakCount + 1 else maxOf(0, item.streakCount - 1)
            } else 0
            repository.updatePlannerItem(
                item.copy(
                    isCompleted = newCompleted,
                    streakCount = newStreak,
                    lastCompletedTimestamp = if (newCompleted) System.currentTimeMillis() else item.lastCompletedTimestamp
                )
            )
        }
    }

    fun deletePlannerItem(item: DailyPlannerItemEntity) {
        viewModelScope.launch {
            repository.deletePlannerItem(item)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            repository.seedInitialDataIfEmpty()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
