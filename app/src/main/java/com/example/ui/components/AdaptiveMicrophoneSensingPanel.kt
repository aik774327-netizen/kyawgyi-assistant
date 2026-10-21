package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AdaptiveAcousticSensingEngine
import com.example.audio.MicrophoneLifecycleController
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * KYAW GYI AI — 100% Updated Adaptive Acoustic Sensing & Power-Saving Panel
 * (နောက်ကွယ်က လိုက်ရောညီထွေ အသံဖမ်းယူခြင်း နှင့် အထူးစွမ်းအင်းနည်းစနစ် အာရုံခံ နည်းပညာ)
 */
@Composable
fun AdaptiveMicrophoneSensingPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val adaptiveEngine = remember { AdaptiveAcousticSensingEngine.getInstance(context) }
    val micController = remember { MicrophoneLifecycleController.getInstance(context) }

    val metrics by adaptiveEngine.metrics.collectAsState()
    var isLiveTesting by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isLiveTesting) {
                coroutineScope.launch {
                    micController.stopCapture("PANEL_DISPOSE")
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(listOf(AngelNeonGreen.copy(alpha = 0.6f), AngelCyanAccent.copy(alpha = 0.6f)))
                ),
                RoundedCornerShape(18.dp)
            )
            .testTag("adaptive_mic_sensing_panel"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = AngelNeonGreen.copy(alpha = 0.18f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Acoustic Sensing",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "လိုက်ရောညီထွေ အသံဖမ်းယူမှု အာရုံခံစနစ်",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AngelNeonGreen.copy(alpha = 0.2f),
                                border = BorderStroke(0.8.dp, AngelNeonGreen)
                            ) {
                                Text(
                                    text = "100% UPDATED",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AngelNeonGreen,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = "Adaptive Acoustic Sensing • Eco Power-Saving • Voice VAD",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Non-interruptive audio badge (Spotify/Music protection)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AngelDarkBackground.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AngelCyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "အသုံးပြုသူ အဆင်ပြေစေမည့် Passive Mic Capture (Zero Music Ducking)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "နောက်ကွယ်တွင် သီချင်းနားထောင်ခြင်း (Spotify / YouTube) ကို အနှောင့်အယှက် မဖြစ်စေပါ",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 1: Special Energy-Efficient Power Modes (အထူးစွမ်းအင်းနည်းစနစ်)
            Text(
                text = "အထူးစွမ်းအင်းနည်းစနစ် (Energy-Efficient Power Profiles)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "ဘက်ထရီ ချွေတာမှုနှင့် အသံဖမ်းယူမှု အာရုံခံစွမ်းအားကို မိမိစိတ်ကြိုက် ရွေးချယ်နိုင်ပါသည်",
                fontSize = 11.sp,
                color = AngelTextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveAcousticSensingEngine.PowerProfile.values().forEach { profile ->
                    val isSelected = metrics.powerProfile == profile
                    val profileTag = profile.name.lowercase()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                adaptiveEngine.setPowerProfile(profile)
                                Toast.makeText(
                                    context,
                                    "${profile.displayNameEN} သို့ ပြောင်းလဲပြီးပါပြီ",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .testTag("power_profile_option_$profileTag"),
                        color = if (isSelected) AngelDarkBackground.copy(alpha = 0.85f) else AngelDarkBackground.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { adaptiveEngine.setPowerProfile(profile) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AngelNeonGreen,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.displayNameMM,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                    Text(
                                        text = profile.descriptionMM,
                                        fontSize = 10.sp,
                                        color = AngelTextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) AngelNeonGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Text(
                                        text = "${profile.energySavings}% ချွေတာ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AngelNeonGreen else AngelTextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.cpuOverheadEstimate,
                                    fontSize = 9.sp,
                                    color = AngelCyanAccent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 2: Real-time Acoustic Environment Meter (အာရုံခံ နည်းပညာ)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelSurfaceDark,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("acoustic_environment_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = metrics.acousticEnvironment.icon, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "လက်ရှိ ပတ်ဝန်းကျင် အာရုံခံမှု:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (metrics.acousticEnvironment) {
                                AdaptiveAcousticSensingEngine.AcousticEnvironment.SPEECH_ACTIVE -> AngelNeonGreen.copy(alpha = 0.25f)
                                AdaptiveAcousticSensingEngine.AcousticEnvironment.QUIET_ROOM -> AngelCyanAccent.copy(alpha = 0.2f)
                                else -> Color.White.copy(alpha = 0.1f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (metrics.acousticEnvironment) {
                                    AdaptiveAcousticSensingEngine.AcousticEnvironment.SPEECH_ACTIVE -> AngelNeonGreen
                                    AdaptiveAcousticSensingEngine.AcousticEnvironment.QUIET_ROOM -> AngelCyanAccent
                                    else -> Color.White.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Text(
                                text = metrics.acousticEnvironment.displayNameMM,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (metrics.acousticEnvironment) {
                                    AdaptiveAcousticSensingEngine.AcousticEnvironment.SPEECH_ACTIVE -> AngelNeonGreen
                                    AdaptiveAcousticSensingEngine.AcousticEnvironment.QUIET_ROOM -> AngelCyanAccent
                                    else -> Color.White
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Decibel Level Bar
                    val normalizedDb = ((metrics.decibels + 80f) / 80f).coerceIn(0f, 1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Live Acoustic Energy: ${String.format("%.1f", metrics.decibels)} dB",
                            fontSize = 11.sp,
                            color = if (metrics.isSpeechDetected) AngelNeonGreen else AngelTextSecondary
                        )
                        Text(
                            text = "Noise Floor: ${String.format("%.1f", metrics.noiseFloorDb)} dB",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { normalizedDb },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (metrics.isSpeechDetected) AngelNeonGreen else AngelCyanAccent,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Myanmar Vocal Resonance & SNR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "မြန်မာစကား အသံကြိမ်နှုန်း (Vocal Band): ${(metrics.vocalEnergyRatio * 100).toInt()}%",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "SNR: +${String.format("%.1f", metrics.snrDb)} dB",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metrics.snrDb > 6f) AngelNeonGreen else AngelTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section 3: Fine-Tuning Switches (Adaptive AGC & Myanmar Formant Enhancer)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelDarkBackground.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Switch 1: Burmese Vocal Resonance Enhancer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "မြန်မာစကား အသံကြိမ်နှုန်း အထူးစစ်ထုတ်မှု",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Myanmar Speech Formant Filter (200Hz - 3.4kHz Vocal Band)",
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Switch(
                            checked = metrics.isBurmeseSpeechEnhanced,
                            onCheckedChange = { adaptiveEngine.setBurmeseSpeechEnhanced(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AngelDarkBackground,
                                checkedTrackColor = AngelNeonGreen
                            ),
                            modifier = Modifier.testTag("burmese_vocal_filter_switch")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    )

                    // Switch 2: Adaptive AGC Pre-Amp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "အလိုအလျောက် အသံအနိမ့်အမြင့် ချိန်ညှိမှု (Adaptive AGC)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Soft-Saturation Limiter (အနီးကပ်အော်ပြောသံများကို အသံကွဲမသွားအောင် ကာကွယ်ပေးပါသည်)",
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Switch(
                            checked = metrics.isAgcActive,
                            onCheckedChange = { adaptiveEngine.setAdaptiveAgc(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AngelDarkBackground,
                                checkedTrackColor = AngelCyanAccent
                            ),
                            modifier = Modifier.testTag("adaptive_agc_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Test Button
            Button(
                onClick = {
                    if (isLiveTesting) {
                        coroutineScope.launch {
                            micController.stopCapture("PANEL_USER_STOP")
                            isLiveTesting = false
                        }
                    } else {
                        coroutineScope.launch {
                            val started = micController.startCapture("AdaptiveAcousticPanelTest") { buffer, size ->
                                adaptiveEngine.processBuffer(buffer, size)
                            }
                            isLiveTesting = started
                            if (started) {
                                Toast.makeText(context, "အာရုံခံစနစ် အသံဖမ်းယူမှု စတင်နေပါသည်", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLiveTesting) Color(0xFFEF4444) else AngelNeonGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_adaptive_mic_sensing_btn")
            ) {
                Icon(
                    imageVector = if (isLiveTesting) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLiveTesting) "အာရုံခံ စမ်းသပ်မှုကို ရပ်တန့်မည် (Stop Test)" else "လိုက်ရောညီထွေ အာရုံခံစနစ် စမ်းသပ်စစ်ဆေးမည် (Test Adaptive Sensing)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
