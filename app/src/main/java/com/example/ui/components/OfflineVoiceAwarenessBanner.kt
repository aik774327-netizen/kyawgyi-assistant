package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
 * KYAW GYI AI — Offline Voice Recognition Awareness & Connectivity Banner
 *
 * Automatically monitors device internet connection in real-time.
 * When internet is lost, displays proactive guidance for offline speech recognition,
 * detailing which native commands still execute 100% offline (Flashlight, Volume,
 * Alarms, Bluetooth, etc.) and offering a 1-tap shortcut to download offline voice models.
 */
@Composable
fun OfflineVoiceAwarenessBanner(
    modifier: Modifier = Modifier,
    alwaysShowForTesting: Boolean = false
) {
    val context = LocalContext.current
    val isOnlineState by produceState(initialValue = NetworkMonitor.isInternetAvailable(context)) {
        NetworkMonitor.observeNetwork(context).collect { value = it }
    }

    val isOffline = !isOnlineState || alwaysShowForTesting
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isOffline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    RoundedCornerShape(16.dp)
                )
                .testTag("offline_voice_awareness_banner"),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Main Banner Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "အင်တာနက် မရှိပါ (Offline Mode)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                        Text(
                            text = "အော့ဖ်လိုင်းအသံဖြင့် အမိန့်ပေးရန် အဆင်သင့်ဖြစ်စေရန် ပြင်ဆင်ပါ",
                            fontSize = 11.sp,
                            color = AngelTextSecondary,
                            lineHeight = 15.sp
                        )
                    }

                    // Details expand/collapse toggle button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp).testTag("toggle_offline_details_btn")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand details",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Expanded Breakdown & Setup Guidance
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        HorizontalDivider(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Offline Capability List
                        Text(
                            text = "⚡ အင်တာနက်မလိုဘဲ အလုပ်လုပ်သောစနစ်များ:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• ဓါတ်မီး (Flashlight) ဖွင့်/ပိတ်\n• အသံအတိုး/အကျယ် (Volume Control)\n• မျက်နှာပြင် အလင်းအမှောင် (Brightness)\n• နှိုးစက် (Alarm) နှင့် Timer သတ်မှတ်ခြင်း\n• အက်ပ်များ ဖွင့်လှစ်ခြင်း (Launch Apps)\n• ရေးဆွဲထားသော Local Shortcuts များ",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // What needs internet
                        Text(
                            text = "🌐 အင်တာနက်လိုအပ်သော စနစ်များ:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Gemini AI စကားပြောဆွေးနွေးမှု\n• Webhook URL ခေါ်ဆိုမှုများ နှင့် Smart Home Cloud",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action: Open Android Voice Input Settings to download offline models
                OutlinedButton(
                    onClick = {
                        NetworkMonitor.openVoiceInputSettings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("open_offline_voice_settings_btn"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF59E0B)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "အော့ဖ်လိုင်း စကားသံဒေါင်းလုဒ် ဆက်တင်သို့ သွားမည်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
