package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AngelCyanAccent
import com.example.ui.theme.AngelSurfaceDark
import com.example.ui.theme.AngelTextSecondary

/**
 * A friendly, accessible rationale dialog shown when microphone permission is denied or needed.
 */
@Composable
fun VoicePermissionRationaleDialog(
    onConfirmGrant: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AngelSurfaceDark,
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("voice_permission_rationale_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Friendly Microphone Icon Badge
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(AngelCyanAccent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "မိုက်ခရိုဖုန်း အသုံးပြုခွင့်",
                        tint = AngelCyanAccent,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Accessible Burmese Title & Subtitle
                Text(
                    text = "မိုက်ခရိုဖုန်း အသုံးပြုခွင့် လိုအပ်ပါသည်",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Microphone Access Rationale",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = AngelCyanAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed Friendly Explanation in Myanmar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2638),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "• \"ကျော်ကြီး\" အစပျိုးစကားလုံး (Wake Word) ကို ကြားသိနားထောင်နိုင်ရန်",
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• မြန်မာစကား အသံအမိန့်များကို တိကျပြတ်သားစွာ ဖမ်းယူလုပ်ဆောင်ပေးရန်",
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• လုံခြုံရေး: မည်သည့် အသံဒေတာကိုမျှ ပြင်ပသို့ ပေးပို့ခြင်းမရှိဘဲ စက်တွင်းသာ လုံခြုံစွာ လုပ်ဆောင်ပါသည်",
                            fontSize = 12.sp,
                            color = AngelTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("voice_permission_cancel_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelTextSecondary)
                    ) {
                        Text("ခဏနေမှ", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onConfirmGrant,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("voice_permission_grant_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelCyanAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ခွင့်ပြုမည်", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (onOpenSettings != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_permission_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ဆက်တင်တွင် တိုက်ရိုက်ဖွင့်ရန်",
                            fontSize = 12.sp,
                            color = AngelCyanAccent
                        )
                    }
                }
            }
        }
    }
}
