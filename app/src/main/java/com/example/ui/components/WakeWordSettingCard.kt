package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.wakeword.WakeWordSettingManager
import com.kyawgyi.ir.service.VoiceForegroundService

/**
 * WakeWordSettingCard
 *
 * Dedicated UI Card in Settings providing:
 * 1. Master ON/OFF Switch for On-Device Wake-word detection ("ကျော်ကြီး" နှိုးစကားလုံး).
 * 2. Real-time background sync with VoiceForegroundService.
 * 3. High/Normal Sensitivity toggle option.
 * 4. Technical specifications & privacy badge (100% On-Device DSP / Zero Cloud Audio).
 */
@Composable
fun WakeWordSettingCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wakeWordManager = remember { WakeWordSettingManager.getInstance(context) }
    val isEnabled by wakeWordManager.isWakeWordEnabled.collectAsState()
    val isHighSensitivity by wakeWordManager.sensitivityHigh.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = if (isEnabled) AngelNeonGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("wake_word_setting_card"),
        color = AngelCardDark
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Icon, Title, and Main Switch
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
                        color = (if (isEnabled) AngelNeonGreen else Color.White.copy(alpha = 0.2f)).copy(alpha = 0.15f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isEnabled) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                                contentDescription = "Wake Word Status",
                                tint = if (isEnabled) AngelNeonGreen else AngelTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "'ကျော်ကြီး' နှိုးစကားလုံး စနစ်",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isEnabled) AngelNeonGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f)
                            ) {
                                Text(
                                    text = if (isEnabled) "ACTIVE" else "DISABLED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isEnabled) AngelNeonGreen else AngelTextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "On-Device Wake-word Model (Kyaw Gyi)",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                // Main Toggle Switch
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { newState ->
                        wakeWordManager.setWakeWordEnabled(newState)
                        // Send broadcast / intent to update running VoiceForegroundService
                        try {
                            val intent = Intent(context, VoiceForegroundService::class.java).apply {
                                action = VoiceForegroundService.ACTION_TOGGLE_WAKE_WORD
                                putExtra(VoiceForegroundService.EXTRA_WAKE_WORD_ENABLED, newState)
                            }
                            context.startService(intent)
                        } catch (e: Exception) {
                            // In case service isn't started yet
                        }

                        val toastMsg = if (newState) {
                            "'ကျော်ကြီး' နှိုးစကားလုံး စနစ် ဖွင့်လှစ်လိုက်ပါပြီ"
                        } else {
                            "'ကျော်ကြီး' နှိုးစကားလုံး စနစ် ပိတ်ထားပါသည် (ခေါ်ဆို၍ မရပါ)"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AngelNeonGreen,
                        checkedTrackColor = AngelNeonGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("wake_word_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description of what the feature does
            Text(
                text = if (isEnabled) {
                    "နောက်ကွယ်မှ 'ကျော်ကြီး' ဟု ခေါ်ဆိုပါက ဖုန်းမျက်နှာပြင် မဖွင့်ရသေးသော်လည်း လက်ထောက်စနစ် အလိုအလျောက် နိုးကြားပြီး အမိန့်စကားများကို လက်ခံပါမည်။"
                } else {
                    "နှိုးစကားလုံးစနစ်ကို ပိတ်ထားပါသည်။ အသံအမိန့်များ ပေးလိုပါက စကားပြောခလုတ် (Mic Button) ကို ကိုယ်တိုင် နှိပ်၍သာ အသုံးပြုနိုင်ပါမည်။"
                },
                fontSize = 12.sp,
                color = AngelTextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Badges: 100% On-Device & Battery Efficient
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelCyanAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "100% On-Device DSP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelCyanAccent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelNeonGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Low Power / Eco Passive",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen
                        )
                    }
                }
            }

            // High Sensitivity Option (visible when enabled)
            AnimatedVisibility(visible = isEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "အဝေးမှ အာရုံခံမှု မြှင့်တင်ရန် (High Sensitivity)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "အဝေးမှ တိုးညင်းစွာ ခေါ်သံများကို ပိုမိုမြန်ဆန်စွာ ဖမ်းယူနိုင်ပါသည်",
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Switch(
                            checked = isHighSensitivity,
                            onCheckedChange = { high ->
                                wakeWordManager.setHighSensitivity(high)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AngelCyanAccent,
                                checkedTrackColor = AngelCyanAccent.copy(alpha = 0.35f),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.testTag("wake_word_sensitivity_switch")
                        )
                    }
                }
            }
        }
    }
}
