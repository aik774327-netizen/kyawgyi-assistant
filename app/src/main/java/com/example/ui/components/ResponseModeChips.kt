package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ResponseMode
import com.example.ui.theme.*

@Composable
fun ResponseModeChips(
    selectedMode: ResponseMode,
    onModeSelected: (ResponseMode) -> Unit,
    selectedModelPreset: String,
    onModelPresetSelected: (String) -> Unit,
    onSendMessage: (String, String?) -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var attachedUri by remember { mutableStateOf<String?>(null) }
    var attachedName by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Handled gracefully if provider does not support persistable grants
            }
            attachedUri = uri.toString()
            attachedName = uri.lastPathSegment ?: "file"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("response_mode_bottom_bar"),
        color = AngelCardHeader
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            // Row 1: Model Preset & Response Mode Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Model Presets: Console | Flash | Pro
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AngelSurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Console", "Flash", "Pro").forEach { preset ->
                            val isSelected = preset == selectedModelPreset
                            Surface(
                                onClick = { onModelPresetSelected(preset) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) AngelNeonGreen.copy(alpha = 0.25f) else Color.Transparent,
                                modifier = Modifier.testTag("model_preset_$preset")
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) AngelNeonGreen else AngelTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Response Mode Chips (QUICK | EXPERT | CREATIVE | TEACHING)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(ResponseMode.values()) { mode ->
                        val isSelected = mode == selectedMode
                        val color = when (mode) {
                            ResponseMode.QUICK -> ModeQuickColor
                            ResponseMode.EXPERT -> ModeExpertColor
                            ResponseMode.CREATIVE -> ModeCreativeColor
                            ResponseMode.TEACHING -> ModeTeachingColor
                        }

                        Surface(
                            onClick = { onModeSelected(mode) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) color.copy(alpha = 0.25f) else AngelSurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) color else Color.White.copy(alpha = 0.08f)
                            ),
                            modifier = Modifier.testTag("response_mode_chip_${mode.name}")
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) color else AngelTextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Attached file chip indicator if any
            if (attachedUri != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AngelCyanAccent.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = attachedName ?: "Attached File",
                            fontSize = 11.sp,
                            color = AngelCyanAccent,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✕",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelTextSecondary,
                            modifier = Modifier.clickable {
                                attachedUri = null
                                attachedName = null
                            }
                        )
                    }
                }
            }

            // Row 2: Text Input with Attachment, Send, and Floating Mic
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Paperclip Attachment Button (Universal: Images, Audio, Video, PDF, Docs, Code)
                IconButton(
                    onClick = {
                        try {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            attachedUri = "sample_document.pdf"
                            attachedName = "sample_document.pdf"
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AngelSurfaceDark)
                        .testTag("attach_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Attach File",
                        tint = if (attachedUri != null) AngelCyanAccent else AngelTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Main Input Text Box
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "AI Angel ကို စကားပြောရန် သို့မဟုတ် စာရိုက်ပါ...",
                            fontSize = 12.sp,
                            color = AngelTextSecondary
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AngelSurfaceDark,
                        unfocusedContainerColor = AngelSurfaceDark,
                        focusedBorderColor = AngelNeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = AngelTextPrimary,
                        unfocusedTextColor = AngelTextPrimary
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send / Generating Button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() || attachedUri != null) {
                            onSendMessage(inputText, attachedUri)
                            inputText = ""
                            attachedUri = null
                        }
                    },
                    enabled = !isGenerating,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isGenerating) AngelTextSecondary else AngelNeonGreen)
                        .testTag("send_btn")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AngelDarkBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = AngelDarkBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Floating Mic Button with Android SpeechRecognizer STT & Live Partial Transcription
                var isListening by remember { mutableStateOf(false) }
                val activeRecognizerRef = remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }

                val voicePromptLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    isListening = false
                    val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        val normalized = com.example.nlu.BurmeseNluPreprocessor.normalize(matches[0])
                        inputText = normalized
                        try {
                            com.kyawgyi.voice.SttEngineManager.getInstance(context)
                                .recordLiveExternalTranscript(normalized, isFinal = true)
                        } catch (_: Exception) {}
                    }
                }

                fun launchSttSession() {
                    val selectedLang = try {
                        com.example.data.LanguagePreferencesRepository.getInstance(context).getSelectedLanguageSync()
                    } catch (e: Exception) {
                        "my-MM"
                    }
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLang)
                        putExtra(android.speech.RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("my-MM", "my", "en-US"))
                        putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "မြန်မာ / English အသံဖြင့် ပြောဆိုပါ ($selectedLang)...")
                    }

                    // Release any background raw AudioRecord loop so SpeechRecognizer has exclusive mic access
                    try {
                        com.example.wakeword.KyawGyiWakeWordEngine.getInstance(context).stopListening()
                    } catch (_: Exception) {}

                    if (!android.speech.SpeechRecognizer.isRecognitionAvailable(context)) {
                        try {
                            voicePromptLauncher.launch(intent)
                        } catch (e: Exception) {
                            isListening = false
                            android.widget.Toast.makeText(
                                context,
                                "ဤစက်တွင် Speech Recognition Service မရှိသေးပါ",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    try {
                        activeRecognizerRef.value?.destroy()
                    } catch (_: Exception) {}

                    val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                    activeRecognizerRef.value = recognizer
                    isListening = true

                    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() { isListening = false }
                        override fun onError(error: Int) {
                            isListening = false
                            // FIX-06: hand the microphone back.
                            com.example.engine.VoiceSessionArbiter.releaseOwnership(
                                com.example.engine.VoiceSessionOwner.CHAT_MIC
                            )
                            // If service is busy or language pack unavailable on direct binder, try system voice dialog once
                            if (error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                error == android.speech.SpeechRecognizer.ERROR_CLIENT ||
                                error == 12 || error == 13
                            ) {
                                try {
                                    voicePromptLauncher.launch(intent)
                                    return
                                } catch (_: Exception) {}
                            }
                            if (inputText.isBlank()) {
                                inputText = "မင်္ဂလာပါ ကျော်ကြီး၊ ဒီနေ့ လုပ်စရာရှိတာလေးတွေ ကူညီပေးပါဦး"
                            }
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            isListening = false
                            // FIX-06: hand the microphone back.
                            com.example.engine.VoiceSessionArbiter.releaseOwnership(
                                com.example.engine.VoiceSessionOwner.CHAT_MIC
                            )
                            val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val normalized = com.example.nlu.BurmeseNluPreprocessor.normalize(matches[0])
                                inputText = normalized
                                try {
                                    com.kyawgyi.voice.SttEngineManager.getInstance(context)
                                        .recordLiveExternalTranscript(normalized, isFinal = true)
                                } catch (_: Exception) {}
                            }
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                                inputText = matches[0]
                                try {
                                    com.kyawgyi.voice.SttEngineManager.getInstance(context)
                                        .recordLiveExternalTranscript(matches[0], isFinal = false)
                                } catch (_: Exception) {}
                            }
                        }
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                    // FIX-06: claim the microphone (preempts any
                    // current owner — bubble, dictation, in-app session).
                    com.example.engine.VoiceSessionArbiter.requestOwnership(
                        com.example.engine.VoiceSessionOwner.CHAT_MIC
                    )
                    recognizer.startListening(intent)
                }

                val micPermLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        launchSttSession()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "အသံဖြင့် စာရိုက်ရန် မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                DisposableEffect(Unit) {
                    // FIX-06: the chat mic is a first-class arbitration
                    // owner — other owners can stop it via this stopper,
                    // and dispose leaves arbitration cleanly.
                    com.example.engine.VoiceSessionArbiter.registerStopper(
                        com.example.engine.VoiceSessionOwner.CHAT_MIC
                    ) {
                        try {
                            activeRecognizerRef.value?.stopListening()
                            activeRecognizerRef.value?.destroy()
                            activeRecognizerRef.value = null
                        } catch (_: Exception) {}
                        isListening = false
                    }
                    onDispose {
                        com.example.engine.VoiceSessionArbiter.unregisterStopper(
                            com.example.engine.VoiceSessionOwner.CHAT_MIC
                        )
                        try {
                            activeRecognizerRef.value?.stopListening()
                            activeRecognizerRef.value?.destroy()
                            activeRecognizerRef.value = null
                        } catch (_: Exception) {}
                    }
                }

                IconButton(
                    onClick = {
                        if (!isListening) {
                            val hasMicPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasMicPerm) {
                                launchSttSession()
                            } else {
                                micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            isListening = false
                            try {
                                activeRecognizerRef.value?.stopListening()
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (isListening) AngelNeonGreen.copy(alpha = 0.3f) else AngelCardDark)
                        .border(1.dp, if (isListening) AngelNeonGreen else AngelCyanAccent.copy(alpha = 0.5f), CircleShape)
                        .testTag("mic_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice Input",
                        tint = if (isListening) AngelNeonGreen else AngelCyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
