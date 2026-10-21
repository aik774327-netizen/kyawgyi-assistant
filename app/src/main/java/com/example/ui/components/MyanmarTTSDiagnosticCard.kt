package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.MyanmarTTSDiagnosticHelper
import com.example.utils.TtsSupportStatus

/**
 * KYAW GYI AI — Myanmar TTS Diagnostic & Voice Guidance Card
 * Displays real-time status of device Text-to-Speech engine and gives one-tap
 * access to install or configure Burmese speech synthesis voice pack.
 */
@Composable
fun MyanmarTTSDiagnosticCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val diagnosticHelper = remember { MyanmarTTSDiagnosticHelper.getInstance(context) }
    val report by diagnosticHelper.report.collectAsState()

    val statusColor = when (report.status) {
        TtsSupportStatus.READY -> Color(0xFF10B981) // Green
        TtsSupportStatus.MISSING_DATA -> Color(0xFFF59E0B) // Amber
        TtsSupportStatus.NOT_SUPPORTED -> Color(0xFFEF4444) // Red
        TtsSupportStatus.INITIALIZATION_FAILED -> Color(0xFFEF4444)
        TtsSupportStatus.CHECKING -> AngelCyanAccent
    }

    val statusIcon = when (report.status) {
        TtsSupportStatus.READY -> Icons.Default.CheckCircle
        TtsSupportStatus.MISSING_DATA -> Icons.Default.Download
        TtsSupportStatus.NOT_SUPPORTED -> Icons.Default.WarningAmber
        TtsSupportStatus.INITIALIZATION_FAILED -> Icons.Default.ErrorOutline
        TtsSupportStatus.CHECKING -> Icons.Default.Sync
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp)
            )
            .testTag("tts_diagnostic_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "မြန်မာအသံထွက် စစ်ဆေးမှု (TTS Diagnostic)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "စနစ် အသံထွက် အင်ဂျင် အဆင်သင့်ဖြစ်မှု အခြေအနေ",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                // Re-check button
                IconButton(
                    onClick = { diagnosticHelper.runDiagnostics() },
                    modifier = Modifier.size(36.dp).testTag("refresh_tts_diagnostic_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AngelCyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Badge & Message
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (report.status) {
                                TtsSupportStatus.READY -> "အဆင်သင့်ဖြစ်သည် (Ready)"
                                TtsSupportStatus.MISSING_DATA -> "အသံဒေတာ မရှိသေးပါ (Voice Data Missing)"
                                TtsSupportStatus.NOT_SUPPORTED -> "မြန်မာဘာသာ မပါဝင်ပါ (Unsupported)"
                                TtsSupportStatus.INITIALIZATION_FAILED -> "စတင်၍ မရပါ (Init Error)"
                                TtsSupportStatus.CHECKING -> "စစ်ဆေးနေပါသည်..."
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = report.messageMM,
                        fontSize = 12.sp,
                        color = Color.White,
                        lineHeight = 17.sp
                    )

                    if (report.recommendationMM.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "အကြံပြုချက်: ${report.recommendationMM}",
                            fontSize = 11.sp,
                            color = AngelTextSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    if (report.currentEnginePackage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "လက်ရှိ Engine: ${report.currentEnginePackage}",
                            fontSize = 10.sp,
                            color = AngelCyanAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Test Speech Button
                Button(
                    onClick = {
                        diagnosticHelper.testSpeakBurmese(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelNeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("test_burmese_speech_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "အသံ စမ်းသပ်မည်",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Open System TTS Settings Button
                OutlinedButton(
                    onClick = {
                        diagnosticHelper.openTtsSettings(context)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AngelCyanAccent
                    ),
                    border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("open_tts_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TTS ဆက်တင်ဖွင့်မည်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Direct Voice Pack Download Button (For Missing Data)
            if (report.status == TtsSupportStatus.MISSING_DATA) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        diagnosticHelper.downloadVoicePack(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981), // Green
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("download_myanmar_voice_pack_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "မြန်မာအသံဒေတာ (Voice Pack) တိုက်ရိုက် ဒေါင်းလုဒ်ဆွဲမည်",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Xiaomi, Redmi, Vivo, Oppo နှင့် Samsung ဖုန်းများတွင် 'Install Voice Data' နှိပ်ပြီး 'Burmese' ကို ရွေးချယ်ဒေါင်းလုဒ်ဆွဲပါ",
                    fontSize = 10.sp,
                    color = AngelTextSecondary,
                    lineHeight = 14.sp
                )
            }

            // If Google TTS missing or not supported, offer direct Google Play Store action
            if (report.status == TtsSupportStatus.NOT_SUPPORTED || (!report.isGoogleTtsInstalled && report.status == TtsSupportStatus.MISSING_DATA)) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        diagnosticHelper.openGoogleTtsOnPlayStore(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("open_playstore_tts_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Speech Services by Google ကို Play Store မှ ရယူမည်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
