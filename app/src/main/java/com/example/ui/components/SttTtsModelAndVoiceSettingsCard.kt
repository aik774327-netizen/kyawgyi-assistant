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
import com.example.ui.theme.*
import com.kyawgyi.voice.SttEngineManager
import com.kyawgyi.voice.SttEngineType
import com.kyawgyi.voice.VoiceSettings
import java.util.Locale

/**
 * SttTtsModelAndVoiceSettingsCard
 *
 * Shows the honest STT engine status (Android System ASR only - the former
 * Sherpa-ONNX/Vosk offline tiers were heuristic fakes and their placeholder
 * model files were removed on 2026-10-22) and the persistent `VoiceSettings`.
 */
@Composable
fun SttTtsModelAndVoiceSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceSettings = remember { VoiceSettings.getInstance(context) }
    val sttEngineManager = remember { SttEngineManager.getInstance(context) }

    val preferredEngine by voiceSettings.preferredSttEngineFlow.collectAsState()
    val ttsSpeechRate by voiceSettings.ttsSpeechRateFlow.collectAsState()
    val ttsPitch by voiceSettings.ttsPitchFlow.collectAsState()
    val isAecHangoverEnabled by voiceSettings.isAecHangoverEnabledFlow.collectAsState()
    val useLocalTtsFallback by voiceSettings.useLocalTtsFallbackFlow.collectAsState()

    var isSystemSttAvailable by remember {
        mutableStateOf(sttEngineManager.systemEngine.isRecognitionAvailable())
    }

    fun refreshModelAvailability() {
        isSystemSttAvailable = sttEngineManager.systemEngine.isRecognitionAvailable()
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
                                imageVector = Icons.Default.SettingsVoice,
                                contentDescription = "STT/TTS Settings",
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
                            text = "System ASR • AEC Echo Guard • Local TTS",
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

            // 1. STT Engine Availability Status (honest: system recognizer only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelSurfaceDark,
                    border = BorderStroke(
                        1.dp,
                        if (isSystemSttAvailable) Color(0xFF10B981).copy(alpha = 0.5f)
                        else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("system_stt_status_box")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSystemSttAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSystemSttAvailable) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Android System STT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isSystemSttAvailable) "စက်တွင်း အဆင်သင့်ရှိသည်" else "SpeechRecognizer မရနိုင်ပါ",
                            fontSize = 10.sp,
                            color = if (isSystemSttAvailable) Color(0xFF10B981) else AngelTextSecondary
                        )
                    }
                }
            }

            // 2. Offline models: none exist - state that plainly instead of
            // offering downloads of models that were never real.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelSurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_models_removed_note")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "အော့ဖ်လိုင်း STT မော်ဒယ် မရှိပါ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sherpa-ONNX / Vosk မြန်မာ အော့ဖ်လိုင်း မော်ဒယ် အမှန်တကယ် မရှိပါ — ယခင် placeholder ဖိုင်များကို ဖယ်ရှားထားပြီးဖြစ်၍ Android System SpeechRecognizer ကိုသာ အသုံးပြုပါသည်။",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { refreshModelAvailability() },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("refresh_model_status_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh STT Status",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("အခြေအနေ ပြန်စစ်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. Preferred STT Engine Selection (System On-Device only)
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
                        SttEngineType.SYSTEM_ON_DEVICE -> "System ASR"
                        SttEngineType.GEMINI_ONLINE -> "Gemini Online"
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

            // 3b. Gemini Online STT disclosure — shown only when the
            // online engine is selected, so the user always knows when
            // their voice audio leaves the device (§7 privacy rule).
            if (preferredEngine == SttEngineType.GEMINI_ONLINE) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AngelSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_online_stt_disclosure")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Gemini Online STT အသုံးပြုနေသည်",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "သင့်အသံကို စာသားပြောင်းရန် Google ဆာဗာသို့ ပို့ပေးပါမည်။ အင်တာနက်နှင့် Settings တွင် ထည့်ထားသော Gemini API key လိုအပ်ပါသည်။ API key မရှိပါက သို့မဟုတ် ချိတ်ဆက်၍ မရပါက အမှားအကြောင်းရင်းကို အတိအကျပြပြီး System ASR သို့ အလိုအလျောက် ပြောင်းပေးပါမည်။",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
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

            // 5. Toggles: AEC 250ms Echo Guard, Local TTS Fallback
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
