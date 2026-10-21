package com.example.ui.urlcontrol

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.UrlExecutionResult
import com.example.actions.UrlVoiceControlExecutor
import com.example.data.local.KyawGyiDatabase
import com.example.data.local.entity.UrlControlEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UrlControlViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = KyawGyiDatabase.getDatabase(context).urlControlDao()
    private val executor = UrlVoiceControlExecutor(context)
    private val prefs = context.getSharedPreferences("kyaw_gyi_url_settings", Context.MODE_PRIVATE)

    val urlControls: StateFlow<List<UrlControlEntity>> = dao.getAllUrlControls()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _globalRemoteMicUrl = MutableStateFlow(prefs.getString("remote_mic_forwarding_url", "") ?: "")
    val globalRemoteMicUrl: StateFlow<String> = _globalRemoteMicUrl.asStateFlow()

    private val _isGlobalForwardingEnabled = MutableStateFlow(prefs.getBoolean("remote_mic_forwarding_enabled", false))
    val isGlobalForwardingEnabled: StateFlow<Boolean> = _isGlobalForwardingEnabled.asStateFlow()

    private val _testResult = MutableStateFlow<UrlExecutionResult?>(null)
    val testResult: StateFlow<UrlExecutionResult?> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    init {
        // If first launch and empty, seed preset templates
        viewModelScope.launch {
            val currentList = dao.getAllActiveList()
            if (currentList.isEmpty()) {
                loadPresetTemplates()
            }
        }
    }

    fun addUrlControl(
        name: String,
        triggerPhrase: String,
        url: String,
        actionType: String,
        requestBody: String = "",
        headers: String = "",
        successSpeech: String = "လင့်ခ်သို့ ချိတ်ဆက်ဆောင်ရွက်ပြီးပါပြီ",
        failureSpeech: String = "လင့်ခ်သို့ ချိတ်ဆက်၍ မရပါခင်ဗျာ"
    ) {
        viewModelScope.launch {
            val entity = UrlControlEntity(
                name = name.trim(),
                triggerPhrase = triggerPhrase.trim().lowercase(),
                url = url.trim(),
                actionType = actionType,
                requestBody = requestBody.trim(),
                headers = headers.trim(),
                successSpeech = successSpeech.trim().ifBlank { "${name.trim()} ကို ချိတ်ဆက်ဆောင်ရွက်ပြီးပါပြီ" },
                failureSpeech = failureSpeech.trim().ifBlank { "${name.trim()} ချိတ်ဆက်မှု မအောင်မြင်ပါ" }
            )
            dao.insertUrlControl(entity)
        }
    }

    fun updateUrlControl(entity: UrlControlEntity) {
        viewModelScope.launch {
            dao.updateUrlControl(entity)
        }
    }

    fun deleteUrlControl(entity: UrlControlEntity) {
        viewModelScope.launch {
            dao.deleteUrlControl(entity)
        }
    }

    fun toggleStatus(entity: UrlControlEntity) {
        viewModelScope.launch {
            dao.updateUrlControl(entity.copy(isEnabled = !entity.isEnabled))
        }
    }

    fun testUrlControl(entity: UrlControlEntity, sampleVoiceText: String = "") {
        viewModelScope.launch {
            _isTesting.value = true
            val testVoice = sampleVoiceText.ifBlank { entity.triggerPhrase }
            val result = executor.execute(entity, testVoice)
            _testResult.value = result

            // Update status in DB
            dao.updateUrlControl(
                entity.copy(
                    lastExecutedAt = System.currentTimeMillis(),
                    lastStatus = if (result.isSuccess) "SUCCESS" else "FAILED"
                )
            )
            _isTesting.value = false
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun saveGlobalForwarding(url: String, enabled: Boolean) {
        prefs.edit()
            .putString("remote_mic_forwarding_url", url.trim())
            .putBoolean("remote_mic_forwarding_enabled", enabled)
            .apply()
        _globalRemoteMicUrl.value = url.trim()
        _isGlobalForwardingEnabled.value = enabled
    }

    fun testRemoteMicStreamUrl(url: String) {
        viewModelScope.launch {
            _isTesting.value = true
            val success = executor.forwardVoiceStreamToRemoteUrl(url, "မင်္ဂလာပါ ကျော်ကြီး အသံစမ်းသပ်မှု ဖြစ်ပါသည်")
            _testResult.value = UrlExecutionResult(
                isSuccess = success,
                responseBody = if (success) "Microphone stream connected successfully!" else "Failed to connect to stream endpoint",
                speechFeedback = if (success) "မိုက်ခရိုဖုန်း URL သို့ အသံစမ်းသပ်ချက် အောင်မြင်စွာ ပို့ပြီးပါပြီ" else "မိုက်ခရိုဖုန်း URL သို့ ချိတ်ဆက်၍ မရပါ",
                httpStatusCode = if (success) 200 else 500
            )
            _isTesting.value = false
        }
    }

    fun loadPresetTemplates() {
        viewModelScope.launch {
            val presets = listOf(
                UrlControlEntity(
                    name = "Telegram App DeepLink",
                    triggerPhrase = "တယ်လီဂရမ် ဖွင့်",
                    url = "tg://resolve?domain=telegram",
                    actionType = "DEEP_LINK",
                    successSpeech = "Telegram Application ကို ဖွင့်ပေးလိုက်ပါပြီ"
                ),
                UrlControlEntity(
                    name = "YouTube Music Search",
                    triggerPhrase = "သီချင်း ရှာဖွင့်",
                    url = "https://www.youtube.com/results?search_query={encoded_voice}",
                    actionType = "OPEN_URL",
                    successSpeech = "YouTube တွင် သီချင်း ရှာဖွင့်ပေးနေပါပြီ"
                ),
                UrlControlEntity(
                    name = "Home Assistant IoT မီးဖွင့်",
                    triggerPhrase = "မီးခလုတ် ဖွင့်",
                    url = "http://homeassistant.local:8123/api/webhook/kyaw_gyi_light_on",
                    actionType = "WEBHOOK_POST",
                    requestBody = "{\"state\": \"on\", \"command\": \"{voice_text}\", \"timestamp\": {timestamp}}",
                    successSpeech = "စမတ်မီးခလုတ်ကို အသံဖြင့် ဖွင့်ပေးလိုက်ပါပြီ"
                ),
                UrlControlEntity(
                    name = "Spotify Music DeepLink",
                    triggerPhrase = "စပေါတီဖိုင်း ဖွင့်",
                    url = "spotify://search/{encoded_voice}",
                    actionType = "DEEP_LINK",
                    successSpeech = "Spotify တွင် အသံဖြင့် ရှာဖွေဖွင့်ပေးနေပါပြီ"
                ),
                UrlControlEntity(
                    name = "Custom App Remote Voice Webhook",
                    triggerPhrase = "အသံအချက်အလက် ပို့",
                    url = "https://httpbin.org/post",
                    actionType = "WEBHOOK_POST",
                    requestBody = "{\"action\": \"remote_voice_control\", \"voice\": \"{voice_text}\"}",
                    successSpeech = "တခြား Application ဆာဗာသို့ အသံဒေတာ ပေးပို့ပြီးပါပြီ"
                )
            )
            for (p in presets) {
                dao.insertUrlControl(p)
            }
        }
    }
}
