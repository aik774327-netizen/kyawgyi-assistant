package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.ui.theme.*
import com.kyawgyi.voice.ModelDownloader
import com.kyawgyi.voice.ModelManager
import com.kyawgyi.voice.SttEngineManager
import com.kyawgyi.voice.SttEngineType
import com.kyawgyi.voice.VoiceSettings
import java.util.Locale

/**
 * SttTtsModelAndVoiceSettingsCard
 *
 * Connects the Layer 2 & Layer 3 STT/TTS Offline Model Downloader (`ModelDownloader`, `ModelManager`),
 * Pluggable STT Engine Selector (`SttEngineManager`), and persistent `VoiceSettings`
 * directly into the user-facing Jetpack Compose UI.
 */
@Composable
fun SttTtsModelAndVoiceSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceSettings = remember { VoiceSettings.getInstance(context) }
    val modelDownloader = remember { ModelDownloader.getInstance(context) }
    val modelManager = remember { ModelManager(context) }
    val sttEngineManager = remember { SttEngineManager.getInstance(context) }

    val preferredEngine by voiceSettings.preferredSttEngineFlow.collectAsState()
    val ttsSpeechRate by voiceSettings.ttsSpeechRateFlow.collectAsState()
    val ttsPitch by voiceSettings.ttsPitchFlow.collectAsState()
    val autoDownloadModels by voiceSettings.autoDownloadModelsFlow.collectAsState()
    val isAecHangoverEnabled by voiceSettings.isAecHangoverEnabledFlow.collectAsState()
    val useLocalTtsFallback by voiceSettings.useLocalTtsFallbackFlow.collectAsState()

    val downloadState by modelDownloader.downloadState.collectAsState()

    var isSherpaAvailable by remember { mutableStateOf(modelManager.isSherpaOnnxModelAvailable()) }
    var isVoskAvailable by remember { mutableStateOf(modelManager.isVoskModelAvailable()) }

    fun refreshModelAvailability() {
        isSherpaAvailable = modelManager.isSherpaOnnxModelAvailable()
        isVoskAvailable = modelManager.isVoskModelAvailable()
    }

    LaunchedEffect(downloadState) {
        refreshModelAvailability()
        if (downloadState is ModelDownloader.DownloadState.Completed) {
            // Re-evaluate any blocked RESOURCE_UNAVAILABLE_ERROR actions now that models exist on disk
            ErrorLearningWorkflowEngine.blockedMethodsFlow.value.keys.toList().forEach { key ->
                ErrorLearningWorkflowEngine.evaluateRequestedAction(key, context = context)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.4f)),
                RoundedCornerShape(16.dp)
            )
            .testTag("stt_tts_model_and_voice_settings_card"),
        color = AngelCardDark
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AngelNeonGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Offline STT/TTS Models",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "STT/TTS အော့ဖ်လိုင်းမော်ဒယ် & အသံဆက်တင်",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Sherpa-ONNX • Vosk • AEC Echo Guard • F5/Local TTS",
                            fontSize = 11.sp,
                            color = AngelNeonGreen
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelCyanAccent.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = preferredEngine.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelCyanAccent,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("active_stt_engine_badge")
                    )
                }
            }

            // 1. On-Device Model Availability Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelSurfaceDark,
                    border = BorderStroke(
                        1.dp,
                        if (isSherpaAvailable) Color(0xFF10B981).copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sherpa_model_status_box")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSherpaAvailable) Icons.Default.CheckCircle else Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                tint = if (isSherpaAvailable) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sherpa-ONNX",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSherpaAvailable) "စက်တွင်း အဆင်သင့်ရှိသည်" else "ဒေါင်းလုဒ် ရယူရန်လိုသည်",
                            fontSize = 10.sp,
                            color = if (isSherpaAvailable) Color(0xFF10B981) else AngelTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelSurfaceDark,
                    border = BorderStroke(
                        1.dp,
                        if (isVoskAvailable) Color(0xFF10B981).copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vosk_model_status_box")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isVoskAvailable) Icons.Default.CheckCircle else Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                tint = if (isVoskAvailable) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Vosk Myanmar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isVoskAvailable) "စက်တွင်း အဆင်သင့်ရှိသည်" else "ဒေါင်းလုဒ် ရယူရန်လိုသည်",
                            fontSize = 10.sp,
                            color = if (isVoskAvailable) Color(0xFF10B981) else AngelTextSecondary
                        )
                    }
                }
            }

            // 2. Offline Model Downloader Progress & Action Controls
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelSurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "အော့ဖ်လိုင်း မြန်မာအသံမော်ဒယ် ဒေါင်းလုဒ်စနစ် (ModelDownloader)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    when (val state = downloadState) {
                        is ModelDownloader.DownloadState.Idle -> {
                            Text(
                                text = "Sherpa-ONNX Zipformer မြန်မာမော်ဒယ်ဖိုင်အား စက်တွင်းသို့ ဒေါင်းလုဒ်ရယူနိုင်ပါသည်။",
                                fontSize = 11.sp,
                                color = AngelTextSecondary
                            )
                        }

                        is ModelDownloader.DownloadState.Downloading -> {
                            val progressFraction = if (state.progressPercent in 0..100) {
                                state.progressPercent / 100f
                            } else 0f
                            Text(
                                text = "ဒေါင်းလုဒ်ဆွဲနေသည်: ${state.progressPercent.coerceAtLeast(0)}% (${state.bytesDownloaded / 1024} KB)",
                                fontSize = 11.sp,
                                color = AngelCyanAccent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("model_download_progress_text")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = AngelNeonGreen
                            )
                        }

                        is ModelDownloader.DownloadState.Extracting -> {
                            Text(
                                text = "Zip ဖိုင်ဖြည်နေသည် (ZipSlip Guard): ${state.currentEntry}",
                                fontSize = 11.sp,
                                color = AngelNeonGreen
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = AngelNeonGreen
                            )
                        }

                        is ModelDownloader.DownloadState.Completed -> {
                            Text(
                                text = "✓ ${state.message}",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("model_download_completed_text")
                            )
                        }

                        is ModelDownloader.DownloadState.Failed -> {
                            Text(
                                text = "⚠ ${state.errorReason}",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("model_download_failed_text")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isBusy = downloadState is ModelDownloader.DownloadState.Downloading ||
                            downloadState is ModelDownloader.DownloadState.Extracting

                        Button(
                            onClick = {
                                modelDownloader.startDownload { success ->
                                    refreshModelAvailability()
                                    if (!success) {
                                        val failState = modelDownloader.downloadState.value
                                        val errReason = (failState as? ModelDownloader.DownloadState.Failed)?.errorReason
                                            ?: "Model download failed"
                                        ErrorLearningWorkflowEngine.recordErrorLog(
                                            task = "Download Offline Myanmar Speech Model",
                                            commandKey = "download_sherpa_model",
                                            expectedResult = "Download and extract Sherpa-ONNX model archive to internal storage",
                                            actualResult = "Download failed: $errReason",
                                            attemptedMethod = "ModelDownloader.startDownload",
                                            evidence = errReason
                                        )
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("start_model_download_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "မော်ဒယ် ဒေါင်းလုဒ်ဆွဲမည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isBusy) {
                            OutlinedButton(
                                onClick = { modelDownloader.cancelDownload() },
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("cancel_model_download_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ရပ်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { refreshModelAvailability() },
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("refresh_model_status_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Model Status",
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Preferred STT Engine Selection (Sherpa-ONNX / Vosk / System On-Device)
            Text(
                text = "ဦးစားပေး အသံဖမ်း အင်ဂျင် ရွေးချယ်ရန် (Preferred STT Engine):",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SttEngineType.entries.forEach { engineType ->
                    val isSelected = preferredEngine == engineType
                    val label = when (engineType) {
                        SttEngineType.SHERPA_ONNX -> "Sherpa-ONNX"
                        SttEngineType.VOSK -> "Vosk Offline"
                        SttEngineType.SYSTEM_ON_DEVICE -> "System ASR"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AngelNeonGreen else AngelSurfaceDark,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                voiceSettings.preferredSttEngine = engineType
                                sttEngineManager.selectEngine(engineType)
                            }
                            .testTag("select_stt_engine_${engineType.name.lowercase(Locale.ROOT)}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }
            }

            // 4. VoiceSettings Sliders (TTS Speech Rate & Pitch)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TTS အသံထွက် အမြန်နှုန်း (Speech Rate):",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", ttsSpeechRate)}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelCyanAccent,
                        modifier = Modifier.testTag("voice_settings_rate_value")
                    )
                }
                Slider(
                    value = ttsSpeechRate,
                    onValueChange = { voiceSettings.ttsSpeechRate = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = AngelCyanAccent,
                        activeTrackColor = AngelCyanAccent
                    ),
                    modifier = Modifier.testTag("voice_settings_rate_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TTS အသံအနိမ့်အမြင့် (Voice Pitch):",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", ttsPitch)}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelNeonGreen,
                        modifier = Modifier.testTag("voice_settings_pitch_value")
                    )
                }
                Slider(
                    value = ttsPitch,
                    onValueChange = { voiceSettings.ttsPitch = it },
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = AngelNeonGreen,
                        activeTrackColor = AngelNeonGreen
                    ),
                    modifier = Modifier.testTag("voice_settings_pitch_slider")
                )
            }

            // 5. Toggles: Auto-Download, AEC 250ms Echo Guard, Local TTS Fallback
            VoiceSettingToggleRow(
                title = "အော့ဖ်လိုင်း မော်ဒယ် အလိုအလျောက် ဒေါင်းလုဒ် (Auto-Download)",
                subtitle = "Wi-Fi ချိတ်ဆက်ချိန်တွင် လိုအပ်သော မော်ဒယ်ဖိုင်အား အလိုအလျောက် ရယူမည်",
                checked = autoDownloadModels,
                onCheckedChange = { voiceSettings.autoDownloadModels = it },
                testTag = "toggle_auto_download_models"
            )

            VoiceSettingToggleRow(
                title = "AEC 250ms Echo Hangover Guard",
                subtitle = "TTS စကားပြောအပြီး 250ms အတွင်း ပဲ့တင်သံ ပြန်မဖမ်းမိစေရန် ကာကွယ်မည်",
                checked = isAecHangoverEnabled,
                onCheckedChange = { voiceSettings.isAecHangoverEnabled = it },
                testTag = "toggle_aec_hangover_guard"
            )

            VoiceSettingToggleRow(
                title = "Local MyanmarTTSEngine Graceful Fallback",
                subtitle = "F5-TTS ဆာဗာ ချိတ်ဆက်မရချိန်တွင် စက်တွင်း မြန်မာအသံစနစ်သို့ အလိုအလျောက် ပြောင်းမည်",
                checked = useLocalTtsFallback,
                onCheckedChange = { voiceSettings.useLocalTtsFallback = it },
                testTag = "toggle_local_tts_fallback"
            )

            // Reset to Defaults button
            OutlinedButton(
                onClick = {
                    voiceSettings.resetToDefaults()
                    sttEngineManager.selectEngine(voiceSettings.preferredSttEngine)
                    Toast.makeText(context, "အသံဆက်တင်များကို မူလအတိုင်း ပြန်လည်သတ်မှတ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("reset_voice_settings_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelTextSecondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("မူလအသံဆက်တင်အတိုင်း ပြန်ထားမည် (Reset Defaults)", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun VoiceSettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = AngelTextSecondary,
                lineHeight = 14.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AngelDarkBackground,
                checkedTrackColor = AngelNeonGreen
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
