package com.example.ui.dictation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LanguagePreferencesRepository
import com.example.router.CommandRouteResult
import com.example.router.KyawGyiDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Status flavour for the dictation screen's status chip. */
enum class DictationStatusKind { IDLE, LISTENING, RESTARTING, ERROR, UNAVAILABLE }

data class DictationUiState(
    val isRecognitionAvailable: Boolean = true,
    val isListening: Boolean = false,
    val wantsToListen: Boolean = false,
    val hasMicPermission: Boolean = false,
    val interimText: String = "",
    val statusMessage: String = "အသင့်ရှိသည်",
    val statusKind: DictationStatusKind = DictationStatusKind.IDLE,
    val guidanceMessage: String? = null,
    val languageCode: String = DICTATION_DEFAULT_LANGUAGE,
    val autoRestart: Boolean = true,
    val autoSave: Boolean = true,
    /** When on, final segments are first offered to the command pipeline. */
    val commandCheckEnabled: Boolean = true,
    val saveState: DictationSaveState = DictationSaveState.IDLE
)

/** One final transcript segment, with the recognizer's real confidence (0.0f = unknown). */
data class DictationFinalTranscript(
    val text: String,
    val confidence: Float
)

/**
 * ViewModel for the native Voice Typing screen.
 *
 * Owns: the recognizer controller, the bounded auto-restart policy
 * ([RestartGuard]), the persisted [DictationDocument] (via [DictationStore],
 * debounced ~250 ms like the HTML v2.1 auto-save), and the shared voice
 * language (via the app's [LanguagePreferencesRepository], whose supported
 * list already contains my-MM / en-US / th-TH).
 *
 * Command integration: every FINAL segment is routed here first. When the
 * "အမိန့်အဖြစ် စစ်ရန်" toggle is on (default), the segment is offered to the
 * app's existing command pipeline via [KyawGyiDispatcher.tryExecuteCommand]
 * — the same custom-shortcut + static-router path as
 * [KyawGyiDispatcher.processVoiceCommand]. A handled segment is executed by
 * that pipeline and never reaches the document; an unhandled segment is
 * emitted on [finalTranscripts] for caret insertion, exactly as before.
 * Interim transcripts are never routed. Executions are serialized through
 * a single-consumer channel so two finals can never run concurrently or
 * out of order, and routing never blocks the recognizer session.
 */
class DictationViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DictationStore(application)
    private val languageRepository = LanguagePreferencesRepository.getInstance(application)
    private val restartGuard = RestartGuard()

    /** The app's existing command pipeline; built lazily on first use. */
    private val commandDispatcher by lazy { KyawGyiDispatcher(application) }

    /**
     * Final segments awaiting routing, consumed by ONE coroutine so
     * command executions are strictly serialized in arrival order.
     */
    private val finalRoutingChannel = Channel<DictationFinalTranscript>(Channel.UNLIMITED)

    private val _uiState = MutableStateFlow(DictationUiState())
    val uiState: StateFlow<DictationUiState> = _uiState.asStateFlow()

    /** The restored document; null until the first load completes. */
    private val _loadedDocument = MutableStateFlow<DictationDocument?>(null)
    val loadedDocument: StateFlow<DictationDocument?> = _loadedDocument.asStateFlow()

    private val _finalTranscripts = MutableSharedFlow<DictationFinalTranscript>(extraBufferCapacity = 16)
    val finalTranscripts: SharedFlow<DictationFinalTranscript> = _finalTranscripts.asSharedFlow()

    /** One-shot user-facing messages (toasts) for actions like copy/export. */
    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userMessages: SharedFlow<String> = _userMessages.asSharedFlow()

    private var currentText: String = ""
    private var saveJob: Job? = null
    private var restartJob: Job? = null

    private val recognizer = DictationRecognizerController(
        application,
        object : DictationRecognizerController.Listener {
            override fun onListeningStarted() {
                _uiState.update {
                    it.copy(
                        isListening = true,
                        statusMessage = "🎤 နားထောင်နေသည်",
                        statusKind = DictationStatusKind.LISTENING,
                        guidanceMessage = null
                    )
                }
            }

            override fun onPartialTranscript(text: String) {
                // Interim text is display-only; it is NEVER inserted into
                // the document (HTML v2.1 parity).
                _uiState.update { it.copy(interimText = text) }
            }

            override fun onFinalTranscript(text: String, confidence: Float) {
                _uiState.update { it.copy(interimText = "") }
                // Blank finals are ignored, as before (the insertion path
                // dropped them); real segments queue for routing.
                if (text.isBlank()) return
                finalRoutingChannel.trySend(DictationFinalTranscript(text, confidence))
            }

            override fun onRecognizerError(kind: DictationErrorKind) {
                handleRecognizerError(kind)
            }

            override fun onSessionEnded() {
                handleSessionEnded()
            }
        }
    )

    init {
        _uiState.update { it.copy(isRecognitionAvailable = recognizer.isRecognitionAvailable()) }
        if (!_uiState.value.isRecognitionAvailable) {
            _uiState.update {
                it.copy(
                    statusMessage = "Speech Recognition မရပါ",
                    statusKind = DictationStatusKind.UNAVAILABLE,
                    guidanceMessage = "ဤဖုန်းတွင် Speech Recognition ဝန်ဆောင်မှု မရှိပါ။ Google app / Speech Services ကို ထည့်သွင်းထားခြင်း ရှိမရှိ စစ်ပါ။"
                )
            }
        }
        viewModelScope.launch {
            val stored = try {
                store.load()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load dictation document: ${e.message}")
                DictationDocument()
            }
            currentText = stored.text
            _uiState.update {
                it.copy(
                    // The dictation language itself comes from the shared
                    // app-wide voice language preference (collected below);
                    // the document only restores this screen's toggles.
                    autoRestart = stored.autoRestart,
                    autoSave = stored.autoSave,
                    commandCheckEnabled = stored.commandCheckEnabled,
                    saveState = if (stored.autoSave) DictationSaveState.SAVED else DictationSaveState.DISABLED
                )
            }
            _loadedDocument.value = stored
        }
        // Single consumer: routes final segments one at a time, in order.
        viewModelScope.launch {
            for (segment in finalRoutingChannel) {
                routeFinalTranscript(segment)
            }
        }
        viewModelScope.launch {
            languageRepository.selectedLanguageState.collect { code ->
                _uiState.update { it.copy(languageCode = validateDictationLanguage(code)) }
            }
        }
    }

    // ---------------------------------------------------------------
    // Document editing / auto-save
    // ---------------------------------------------------------------

    /** Called by the screen on every edit (typing or transcript insertion). */
    fun onDocumentEdited(text: String) {
        currentText = text
        if (!_uiState.value.autoSave) {
            _uiState.update { it.copy(saveState = DictationSaveState.DISABLED) }
            return
        }
        _uiState.update { it.copy(saveState = DictationSaveState.SAVING) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistCurrentDocument()
        }
    }

    fun setAutoSave(enabled: Boolean) {
        _uiState.update {
            it.copy(
                autoSave = enabled,
                saveState = if (enabled) DictationSaveState.SAVING else DictationSaveState.DISABLED
            )
        }
        // Persist the toggle itself (and, when enabling, the document too).
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persistCurrentDocument() }
    }

    fun setAutoRestart(enabled: Boolean) {
        _uiState.update { it.copy(autoRestart = enabled) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persistCurrentDocument() }
    }

    fun setCommandCheckEnabled(enabled: Boolean) {
        _uiState.update { it.copy(commandCheckEnabled = enabled) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { persistCurrentDocument() }
    }

    // ---------------------------------------------------------------
    // Final-segment routing: command pipeline first, document second
    // ---------------------------------------------------------------

    /**
     * Route one final segment. Runs on the single routing consumer, so
     * executions are serialized; the recognizer session is untouched.
     */
    private suspend fun routeFinalTranscript(segment: DictationFinalTranscript) {
        if (!_uiState.value.commandCheckEnabled) {
            _finalTranscripts.emit(segment)
            return
        }
        var handledResponse: String? = null
        val checkResult = try {
            when (val result = commandDispatcher.tryExecuteCommand(segment.text)) {
                is CommandRouteResult.Handled -> {
                    handledResponse = result.response
                    DictationCommandCheckResult.HANDLED
                }
                CommandRouteResult.NotHandled -> DictationCommandCheckResult.NOT_HANDLED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Command routing failed for a dictation segment: ${e.message}")
            DictationCommandCheckResult.FAILED
        }
        when (decideDictationSegmentRoute(commandCheckEnabled = true, checkResult = checkResult)) {
            DictationSegmentDecision.EXECUTED_AS_COMMAND -> {
                val response = handledResponse ?: return
                // Surface the pipeline's own confirmation both in the
                // status chip (stays until the next recognizer event)
                // and as a toast; the segment is NOT inserted as text.
                _uiState.update {
                    it.copy(
                        statusMessage = response,
                        statusKind = DictationStatusKind.IDLE,
                        guidanceMessage = null
                    )
                }
                _userMessages.tryEmit(response)
            }
            DictationSegmentDecision.SURFACE_ERROR -> {
                // The segment was offered as a command and the pipeline
                // failed: report honestly and drop the segment rather
                // than silently filing it as document text.
                _uiState.update {
                    it.copy(
                        statusMessage = "အမိန့် လုပ်ဆောင်ရာတွင် အဆင်မပြေပါ",
                        statusKind = DictationStatusKind.ERROR,
                        guidanceMessage = "အမိန့်ကို လုပ်ဆောင်ရာတွင် ပြဿနာ ဖြစ်ပွားခဲ့သည်။ ထိုစာသားကို စာရွက်ထဲ မထည့်ခဲ့ပါ။"
                    )
                }
                _userMessages.tryEmit("အမိန့် လုပ်ဆောင်ရာတွင် အဆင်မပြေပါ")
            }
            DictationSegmentDecision.INSERT_AS_TEXT -> _finalTranscripts.emit(segment)
        }
    }

    private suspend fun persistCurrentDocument() {
        val state = _uiState.value
        val document = DictationDocument(
            text = currentText,
            language = state.languageCode,
            autoRestart = state.autoRestart,
            autoSave = state.autoSave,
            commandCheckEnabled = state.commandCheckEnabled,
            updatedAtMs = System.currentTimeMillis()
        )
        try {
            store.save(document)
            if (state.autoSave) {
                _uiState.update { it.copy(saveState = DictationSaveState.SAVED) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save dictation document: ${e.message}")
            _uiState.update { it.copy(saveState = DictationSaveState.FAILED) }
        }
    }

    // ---------------------------------------------------------------
    // Language (shared app-wide voice language — see class doc)
    // ---------------------------------------------------------------

    fun setLanguage(code: String) {
        val validated = validateDictationLanguage(code)
        if (validated == _uiState.value.languageCode) return
        // HTML parity: changing language stops the current session;
        // the user taps the mic again to continue in the new language.
        if (_uiState.value.wantsToListen) stopListening()
        viewModelScope.launch { languageRepository.savePreferredLanguage(validated) }
        _userMessages.tryEmit("ဘာသာစကားပြောင်းပြီးပါပြီ။ Mic ကို ပြန်နှိပ်ပါ။")
    }

    // ---------------------------------------------------------------
    // Microphone session control
    // ---------------------------------------------------------------

    fun onMicPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = granted) }
        if (!granted && _uiState.value.wantsToListen) {
            _uiState.update { it.copy(wantsToListen = false) }
        }
    }

    /** User tapped the mic to start. Caller must have secured permission first. */
    fun startListening() {
        val state = _uiState.value
        if (!state.isRecognitionAvailable) {
            _userMessages.tryEmit("ဤဖုန်းတွင် Speech Recognition မရပါ။")
            return
        }
        if (!state.hasMicPermission) {
            handleRecognizerError(DictationErrorKind.PERMISSION_DENIED)
            return
        }
        restartGuard.reset()
        restartJob?.cancel()
        _uiState.update {
            it.copy(
                wantsToListen = true,
                statusMessage = "ပြန်လည်စတင်နေသည်…",
                statusKind = DictationStatusKind.RESTARTING,
                guidanceMessage = null
            )
        }
        recognizer.start(state.languageCode)
    }

    /** User tapped the mic (or left the screen) to stop — intentional stop. */
    fun stopListening() {
        restartJob?.cancel()
        _uiState.update {
            it.copy(
                wantsToListen = false,
                isListening = false,
                interimText = "",
                statusMessage = "ရပ်ထားသည်",
                statusKind = DictationStatusKind.IDLE
            )
        }
        recognizer.stop()
    }

    private fun handleRecognizerError(kind: DictationErrorKind) {
        val fatal = isFatalDictationError(kind)
        _uiState.update {
            it.copy(
                isListening = false,
                wantsToListen = if (fatal) false else it.wantsToListen,
                statusMessage = dictationErrorStatus(kind),
                statusKind = DictationStatusKind.ERROR,
                guidanceMessage = dictationErrorGuidance(kind),
                interimText = if (kind == DictationErrorKind.SPEECH_TIMEOUT || kind == DictationErrorKind.NO_MATCH) {
                    it.interimText
                } else {
                    ""
                }
            )
        }
        if (fatal) {
            restartJob?.cancel()
            recognizer.stop()
        }
    }

    private fun handleSessionEnded() {
        val state = _uiState.value
        _uiState.update { it.copy(isListening = false) }
        if (!state.wantsToListen) return
        if (!state.autoRestart) {
            _uiState.update {
                it.copy(
                    wantsToListen = false,
                    statusMessage = "အသင့်ရှိသည်",
                    statusKind = DictationStatusKind.IDLE
                )
            }
            return
        }
        // Bounded auto-restart (HTML v2.1 parity): at most 5 restarts
        // inside 15 seconds, counted by RestartGuard.
        if (!restartGuard.recordRestart(System.currentTimeMillis())) {
            _uiState.update {
                it.copy(
                    wantsToListen = false,
                    statusMessage = "ခဏခဏရပ်နေသဖြင့် ခေတ္တရပ်ထားသည်",
                    statusKind = DictationStatusKind.ERROR,
                    guidanceMessage = "Auto-restart ကို လုံခြုံရေးအတွက် ခေတ္တရပ်ထားသည်။ Mic ကို နှိပ်၍ ပြန်စပါ။"
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                statusMessage = "ပြန်လည်စတင်နေသည်…",
                statusKind = DictationStatusKind.RESTARTING
            )
        }
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            delay(RESTART_DELAY_MS)
            if (_uiState.value.wantsToListen) {
                recognizer.start(_uiState.value.languageCode)
            }
        }
    }

    fun notifyUser(message: String) {
        _userMessages.tryEmit(message)
    }

    override fun onCleared() {
        restartJob?.cancel()
        saveJob?.cancel()
        recognizer.destroy()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DictationViewModel"
        private const val SAVE_DEBOUNCE_MS = 250L
        private const val RESTART_DELAY_MS = 700L
    }
}
