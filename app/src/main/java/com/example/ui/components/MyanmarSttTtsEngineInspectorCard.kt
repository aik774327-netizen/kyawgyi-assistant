package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nlu.BurmeseNluPreprocessor
import com.example.nlu.BurmeseTtsTextNormalizer
import com.example.nlu.LocalBurmeseIntentParser
import com.kyawgyi.voice.MyanmarPronunciationLexicon
import com.example.ui.theme.AngelCardDark
import com.example.ui.theme.AngelCyanAccent
import com.example.ui.theme.AngelDarkBackground
import com.example.ui.theme.AngelNeonGreen
import com.example.ui.theme.AngelTextSecondary
import com.example.utils.MyanmarTTSDiagnosticHelper
import com.example.utils.MyanmarTTSEngine
import com.example.utils.MyanmarWarmNaturalSpeechSystem
import com.kyawgyi.voice.AudioFocusAndAecManager
import com.kyawgyi.voice.F5MyanmarTtsClient
import com.kyawgyi.voice.SttEngineManager
import com.kyawgyi.voice.SttEngineType
import kotlinx.coroutines.launch

/**
 * MyanmarSttTtsEngineInspectorCard
 *
 * Interactive Diagnostic & Verification Card for KyawGyi Myanmar STT/TTS Engine:
 * 1. Inspects the Android System STT engine (the former Sherpa-ONNX/Vosk tiers were
 *    heuristic fakes and were removed on 2026-10-22; no offline STT model exists).
 * 2. Inspects TTS Engine (F5MyanmarTtsClient -> MyanmarTTSEngine -> Google TTS my-MM Voice Pack + Safety Gate).
 * 3. Inspects Hardware DSP (AEC / NS / AGC) & 150-600ms Echo Hangover Guard.
 * 4. Provides an interactive Burmese NLU (STT Normalization) & G2P (TTS Normalization) Live Verification Testbench.
 */
@Composable
fun MyanmarSttTtsEngineInspectorCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sttManager = remember { SttEngineManager.getInstance(context) }
    val aecManager = remember { AudioFocusAndAecManager.getInstance(context) }
    val ttsEngine = remember { MyanmarTTSEngine.getInstance(context) }
    val f5TtsClient = remember { F5MyanmarTtsClient(context, aecManager) }
    val ttsDiagHelper = remember { MyanmarTTSDiagnosticHelper.getInstance(context) }
    val warmSpeechSystem = remember { MyanmarWarmNaturalSpeechSystem.getInstance(context) }

    val ttsReport by ttsDiagHelper.report.collectAsState()
    val currentPersona by warmSpeechSystem.currentPersona.collectAsState()
    val liveEngineState by sttManager.engineState.collectAsState()
    val sttUiState by sttManager.uiState.collectAsState()
    val captureModuleState by sttManager.audioCaptureModule.captureState.collectAsState()
    var sttHealth by remember { mutableStateOf(sttManager.getHealthReport()) }

    // Sample Myanmar test sentence covering Code-Switching, Phone Number, Time, Currency, and Percentages
    var testInputText by remember {
        mutableStateOf("ဟယ်လို ကျော်ကြီး ၀၉၇၈၉၅၃၂၁၀ ကို နေ့လည် ၁၂:၃၀ မှာ 15,000 MMK နဲ့ Wi-Fi 85% ဖွင့်ပေးပါ")
    }

    LaunchedEffect(liveEngineState, sttUiState.streamedChunksCount, sttUiState.totalUtterancesProcessed) {
        sttHealth = sttManager.getHealthReport()
    }

    LaunchedEffect(Unit) {
        sttManager.transcriptionFlow.collect { (transcript, isFinal) ->
            if (transcript.isNotBlank()) {
                testInputText = transcript
                sttHealth = sttManager.getHealthReport()
                if (isFinal) {
                    sttManager.updateEngineState(com.kyawgyi.voice.SttEngineState.IDLE)
                }
            }
        }
    }

    val liveMicPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sttManager.startListening()
            sttHealth = sttManager.getHealthReport()
        } else {
            Toast.makeText(context, "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်", Toast.LENGTH_SHORT).show()
        }
    }

    val normalizedSttNlu = remember(testInputText) {
        BurmeseNluPreprocessor.normalize(testInputText)
    }
    val syllablesList = remember(testInputText) {
        BurmeseNluPreprocessor.segmentSyllables(testInputText)
    }
    val parsedIntent = remember(normalizedSttNlu) {
        LocalBurmeseIntentParser.getInstance().parse(normalizedSttNlu)
    }
    val normalizedTtsG2p = remember(testInputText) {
        BurmeseTtsTextNormalizer.normalizeForTts(testInputText)
    }

    var isSpeakingSample by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, AngelNeonGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("myanmar_stt_tts_inspector_card"),
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
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "STT/TTS Myanmar Engine Inspector",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "STT / TTS မြန်မာ အသံအင်ဂျင် စစ်ဆေးရေး စင်တာ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "System STT • Google my-MM TTS • NLU & G2P Verifier",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                IconButton(
                    onClick = {
                        sttManager.ensureHealthyEngineSelected()
                        sttHealth = sttManager.getHealthReport()
                        ttsDiagHelper.runDiagnostics()
                        Toast.makeText(context, "STT/TTS မြန်မာ အင်ဂျင် အခြေအနေကို ပြန်လည်စစ်ဆေးပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("refresh_stt_tts_inspector_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh STT/TTS Status",
                        tint = AngelNeonGreen
                    )
                }
            }

            // 1. MULTI-TIER STT ENGINE STATUS & SELECTOR
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelDarkBackground.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "၁။ Speech-to-Text (STT) System Engine",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelCyanAccent
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AngelNeonGreen.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "ACTIVE: ${sttHealth.activeEngineType.name}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AngelNeonGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 3 Tier Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tiers = listOf(
                            Triple(SttEngineType.SYSTEM_ON_DEVICE, "Android System STT", sttHealth.isSystemSttAvailable)
                        )
                        tiers.forEach { (engineType, label, isReady) ->
                            val isSelected = sttHealth.activeEngineType == engineType
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AngelCyanAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AngelCyanAccent else Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        sttManager.selectEngine(engineType)
                                        sttHealth = sttManager.getHealthReport()
                                    }
                                    .testTag("stt_tier_select_${engineType.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else AngelTextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isReady) "Ready ✓" else "Not Available",
                                        fontSize = 9.sp,
                                        color = if (isReady) AngelNeonGreen else Color(0xFFFFB74D)
                                    )
                                }
                            }
                        }
                    }

                    // AEC & Echo Hangover Guard Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "• HW AEC/NS/AGC + Echo Guard: ${aecManager.getEchoHangoverDurationMs()}ms",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "State: ${sttUiState.engineState.name} (${sttUiState.totalUtterancesProcessed} utterances)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AngelNeonGreen
                        )
                    }

                    // AudioCaptureModule ➔ System STT Live Streaming Buffer Telemetry
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("audio_capture_streaming_telemetry_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AudioCaptureModule ➔ ${sttUiState.activeEngineType.name} Stream",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelCyanAccent
                                )
                                Text(
                                    text = if (sttUiState.isStreamingFromCaptureModule || captureModuleState.isCapturing) "STREAMING ●" else "READY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (sttUiState.isStreamingFromCaptureModule || captureModuleState.isCapturing) AngelNeonGreen else AngelTextSecondary
                                )
                            }

                            Text(
                                text = "Chunks: ${sttUiState.streamedChunksCount} (${sttUiState.streamedBytesCount} B) • RMS: ${"%.1f".format(sttUiState.currentRmsDbFs)} dBFS • Latency: ${"%.2f".format(sttUiState.lastInferenceLatencyMs)} ms",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )

                            Text(
                                text = "Route: ${sttUiState.activeInputRouteLabel} • Pre-Roll: ${sttUiState.preRollFlushedBytesCount} B • Hotwords: ${sttUiState.registeredHotwordsCount} • Mic: ${if (sttUiState.isMicrophoneMuted) "MUTED ⚠" else "UNMUTED ✓"}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (sttUiState.isMicrophoneMuted) Color(0xFFFF8A80) else AngelTextSecondary
                            )

                            Text(
                                text = "Pool Hit: ${"%.0f".format(sttUiState.bufferPoolHitRatioPercent)}% • Failovers: ${sttUiState.failoverSwitchCount} • Call: ${if (sttUiState.isCallPreempted) "PREEMPTED" else "IDLE"} • TTS Data: ${if (sttUiState.ttsVoiceDataReady) "READY ✓" else "SYNTH ✓"}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AngelTextSecondary
                            )

                            Text(
                                text = "AGC Gain: ${"%.2f".format(sttUiState.currentAgcGain)}x • Barge-Ins: ${sttUiState.bargeInCount} • Power: ${sttUiState.powerProfileLabel}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AngelTextSecondary
                            )

                            Text(
                                text = "G2P Rules: ${sttUiState.g2pRulesCount} • Homophone Matrix: ${MyanmarPronunciationLexicon.SPOKEN_TO_ORTHOGRAPHY_STT.size} entries",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AngelNeonGreen
                            )

                            Text(
                                text = "Room Lexicons: ${sttUiState.persistedCustomLexiconsCount} • Loopback: ${sttUiState.lastLoopbackStatus} • ${sttUiState.nativeBinarySummary}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AngelCyanAccent
                            )

                            val normalizedLevel = ((sttUiState.currentRmsDbFs + 90f) / 90f).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { normalizedLevel },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (sttUiState.isSpeechActive) AngelNeonGreen else AngelCyanAccent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )

                            if (sttUiState.partialTranscript.isNotBlank()) {
                                Text(
                                    text = "▸ Partial Stream: \"${sttUiState.partialTranscript}\"",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                            if (sttUiState.finalTranscript.isNotBlank()) {
                                Text(
                                    text = "▸ Finalized Transcript: \"${sttUiState.finalTranscript}\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AngelNeonGreen
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            sttManager.triggerThreeSecondMicLoopbackCheck(durationMs = 3000)
                        },
                        border = BorderStroke(1.dp, AngelNeonGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelNeonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("btn_run_three_second_mic_loopback")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Run 3-Second Mic Loopback Check",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "3-Second Mic Check & Playback Loopback စမ်းမည်",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                }
            }

            // 2. MYANMAR TTS ENGINE & VOICE PACK STATUS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelDarkBackground.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "၂။ Text-to-Speech (TTS) မြန်မာ အသံထွက် အင်ဂျင်",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelCyanAccent
                        )
                        Text(
                            text = "Pitch: ${ttsEngine.pitch}x • Rate: ${ttsEngine.rate}x",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen
                        )
                    }

                    Text(
                        text = "• Status: ${ttsReport.messageMM}",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                    Text(
                        text = "• Persona: ${currentPersona.displayNameMM}",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                    Text(
                        text = "• Universal Phonetic G2P Fallback: မြန်မာ Voice Pack မရှိသေးသော ဖုန်းများတွင်ပါ Phonetic Syllables ဖြင့် အသံမပျက် တိုက်ရိုက်ထွက်ဆိုပေးသည်",
                        fontSize = 10.sp,
                        color = AngelTextSecondary
                    )
                }
            }

            // 3. LIVE MYANMAR NLU (STT -> INTENT) & G2P (TEXT -> TTS) VERIFICATION BENCH
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelDarkBackground.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "၃။ မြန်မာ STT/NLU နှင့် TTS G2P တိုက်ရိုက် စမ်းသပ်စစ်ဆေးမှု (Live Pipeline Verifier)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = testInputText,
                        onValueChange = { testInputText = it },
                        label = {
                            Text(
                                text = "စမ်းသပ်လိုသော မြန်မာ/English စာကြောင်း (STT/TTS Input)",
                                fontSize = 11.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stt_tts_live_test_input")
                    )

                    // Pipeline Stage Outputs
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "▸ [STT ➔ NLU Normalized]: $normalizedSttNlu",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AngelNeonGreen
                        )
                        Text(
                            text = "▸ [Syllable Segmentation (${syllablesList.size} syllables)]: ${syllablesList.take(12).joinToString(" | ")}${if (syllablesList.size > 12) " ..." else ""}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AngelCyanAccent
                        )
                        Text(
                            text = "▸ [Parsed Offline Intent]: ${parsedIntent.intentName} (conf=${parsedIntent.confidence}, slots=${parsedIntent.slots})",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "▸ [TTS G2P Spoken Burmese]: $normalizedTtsG2p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Live Microphone STT Button
                    val isLiveMicListening = liveEngineState == com.kyawgyi.voice.SttEngineState.LISTENING
                    OutlinedButton(
                        onClick = {
                            if (isLiveMicListening) {
                                sttManager.stopListening()
                                sttHealth = sttManager.getHealthReport()
                            } else {
                                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    sttManager.startListening()
                                    sttHealth = sttManager.getHealthReport()
                                } else {
                                    liveMicPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isLiveMicListening) Color(0xFFEF4444) else AngelNeonGreen
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isLiveMicListening) Color(0xFFEF4444) else AngelNeonGreen
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("live_mic_stt_test_button")
                    ) {
                        Icon(
                            imageVector = if (isLiveMicListening) Icons.Default.Stop else Icons.Default.MicExternalOn,
                            contentDescription = "Live Mic STT",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isLiveMicListening) "အသံနားထောင်နေသည်... (ရပ်ရန်နှိပ်ပါ)" else "မိုက်ဖြင့် တိုက်ရိုက်ပြော၍ STT စမ်းသပ်မည်",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Action Buttons for Live STT Emit & F5/Myanmar TTS Playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                sttManager.emitTranscript(testInputText, isFinal = true)
                                sttHealth = sttManager.getHealthReport()
                                Toast.makeText(
                                    context,
                                    "STT Finalized -> Intent: ${parsedIntent.intentName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelCyanAccent.copy(alpha = 0.2f),
                                contentColor = AngelCyanAccent
                            ),
                            border = BorderStroke(1.dp, AngelCyanAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_stt_utterance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Test STT Pipeline",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STT ပိုက်လိုင်း စမ်းမည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                isSpeakingSample = true
                                coroutineScope.launch {
                                    f5TtsClient.synthesizeAndPlay(testInputText) {
                                        isSpeakingSample = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelNeonGreen.copy(alpha = 0.2f),
                                contentColor = AngelNeonGreen
                            ),
                            border = BorderStroke(1.dp, AngelNeonGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("speak_normalized_tts_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak Myanmar TTS",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSpeakingSample) "အသံထွက်နေသည်..." else "TTS အသံထွက် စမ်းမည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
