package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
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
import com.example.utils.NetworkMonitor

/**
 * Settings Card for Offline Voice Recognition & Network Readiness.
 * Explains how Android offline voice dictation works and provides direct
 * access to download offline speech models.
 */
@Composable
fun OfflineVoiceSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOnline by produceState(initialValue = NetworkMonitor.isInternetAvailable(context)) {
        NetworkMonitor.observeNetwork(context).collect { value = it }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, if (isOnline) AngelCyanAccent.copy(alpha = 0.3f) else Color(0xFFF59E0B).copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp)
            )
            .testTag("offline_voice_settings_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isOnline) AngelCyanAccent.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isOnline) AngelCyanAccent else Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "အော့ဖ်လိုင်း အသံဖမ်းစနစ် (Offline Speech Recognition)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isOnline) "ကွန်ရက်ချိတ်ဆက်ထားသည် (Online)" else "အင်တာနက် မရှိပါ (Offline)",
                        fontSize = 11.sp,
                        color = if (isOnline) AngelNeonGreen else Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ဖုန်းတွင် Google Voice Typing ၏ အော့ဖ်လိုင်း စကားသံဒေါင်းလုဒ် (Offline Recognition) ထည့်သွင်းထားပါက အင်တာနက် မရှိချိန်တွင်လည်း ကျော်ကြီးသည် အခြေခံ အမိန့်များကို သွက်လက်စွာ နားထောင် အလုပ်လုပ်ပေးနိုင်မည် ဖြစ်ပါသည်။",
                fontSize = 12.sp,
                color = AngelTextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    NetworkMonitor.openVoiceInputSettings(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AngelCyanAccent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("manage_offline_voice_pack_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Google အော့ဖ်လိုင်း စကားသံဒေါင်းလုဒ် ဆက်တင်ဖွင့်မည်",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
