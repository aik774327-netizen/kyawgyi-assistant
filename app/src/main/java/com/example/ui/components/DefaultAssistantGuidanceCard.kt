package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.core.content.ContextCompat
import com.example.service.KyawGyiAssistActivity
import com.example.service.KyawGyiFloatingService
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.OverlayPermissionHelper

/**
 * KYAW GYI AI — Default Digital Assistant & Quick Activation Guidance Card
 *
 * Provides real-time detection whether Kyaw Gyi is configured as Android's
 * Default Assist & Voice Input app, instructions on triggering via Home Button
 * Long Press or Gesture Swipe, and controls for the Floating Assist Overlay.
 */
@Composable
fun DefaultAssistantGuidanceCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAssistantSet by remember {
        mutableStateOf(KyawGyiPermissionManager.isDefaultAssistant(context))
    }
    var isOverlayRunning by remember {
        mutableStateOf(KyawGyiFloatingService.isServiceRunning)
    }
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isAssistantSet) AngelCyanAccent.copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.6f)
                ),
                RoundedCornerShape(16.dp)
            )
            .testTag("default_assistant_guidance_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
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
                        color = if (isAssistantSet) AngelCyanAccent.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Assistant,
                                contentDescription = null,
                                tint = if (isAssistantSet) AngelCyanAccent else Color(0xFFF59E0B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "ပုံသေ ဒစ်ဂျစ်တယ် အသံအကူ (Default Assistant)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Home ခလုတ် နှင့် လက်ဟန်ခြေဟန်ဖြင့် ခေါ်ယူနိုင်သော စနစ်",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp).testTag("toggle_assistant_guide_btn")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = AngelTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAssistantSet) AngelNeonGreen.copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isAssistantSet) AngelNeonGreen.copy(alpha = 0.3f) else Color(0xFFF59E0B).copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAssistantSet) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isAssistantSet) AngelNeonGreen else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAssistantSet) {
                            "ကျော်ကြီးအား ပုံသေ အသံအကူအက်ပ်အဖြစ် သတ်မှတ်ထားပြီးဖြစ်သည်"
                        } else {
                            "Android စနစ်၏ Default Assist App အဖြစ် မသတ်မှတ်ရသေးပါ"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isAssistantSet) AngelNeonGreen else Color(0xFFF59E0B)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "ကျော်ကြီး AI အား Default Digital Assistant အဖြစ် သတ်မှတ်ထားပါက မည်သည့်အက်ပ်ကိုမဆို အသုံးပြုနေစဉ် အောက်ပါနည်းလမ်းများဖြင့် ချက်ချင်း အသံခေါ်ယူနိုင်ပါသည်:",
                        fontSize = 11.sp,
                        color = AngelTextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4 Ways to Trigger Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AngelSurfaceDark,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TriggerMethodItem(
                                number = "၁",
                                title = "Home Button ကို ဖိထားခြင်း (Long Press Home)",
                                desc = "ဖုန်းအောက်ခြေရှိ Home ခလုတ် (အဝိုင်း သို့မဟုတ် အတုံး) ကို ၁ စက္ကန့် ဖိထားပါက ကျော်ကြီး AI အသင့် ပေါ်လာပါမည်။"
                            )
                            TriggerMethodItem(
                                number = "၂",
                                title = "မျက်နှာပြင် ထောင့်စွန်းမှ ပွတ်ဆွဲခြင်း (Gesture Swipe)",
                                desc = "Gesture Navigation သုံးထားပါက ဖုန်းအောက်ခြေ ဘယ် သို့မဟုတ် ညာထောင့်စွန်းမှ အပေါ်သို့ လက်ဖြင့် ပွတ်ဆွဲ၍ ခေါ်ယူနိုင်ပါသည်။"
                            )
                            TriggerMethodItem(
                                number = "၃",
                                title = "Power ခလုတ် ဖိထားခြင်း (Hold Power for Assistant)",
                                desc = "Android 12 နှင့် အထက် ဖုန်းများတွင် ပါဝါခလုတ်ကို ဖိထားပြီး အသံအကူ ခေါ်ယူနိုင်အောင် စနစ်တွင် သတ်မှတ်နိုင်ပါသည်။"
                            )
                            TriggerMethodItem(
                                number = "၄",
                                title = "Floating Assist Bubble (ပေါ်နေသော အဝိုင်းလေး)",
                                desc = "ဖုန်းမျက်နှာပြင်ပေါ်တွင် အမြဲရှိနေပြီး ၁-ချက်နှိပ်ရုံဖြင့် အသံအမိန့်ပေးနိုင်သော Floating Widget စနစ်။"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action 1: Open System Default Assistant Settings
                    Button(
                        onClick = {
                            KyawGyiPermissionManager.openDefaultAssistantSettings(context)
                            isAssistantSet = KyawGyiPermissionManager.isDefaultAssistant(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelCyanAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("set_default_assistant_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Default Digital Assistant သို့ သွားရောက်သတ်မှတ်မည်",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action 2 & 3: Floating Overlay Toggle & Test Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Toggle Floating Overlay
                        OutlinedButton(
                            onClick = {
                                if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                                    OverlayPermissionHelper.requestOverlayPermission(context)
                                    Toast.makeText(context, "Display over other apps ခွင့်ပြုချက် ပေးပါ", Toast.LENGTH_SHORT).show()
                                } else {
                                    val serviceIntent = Intent(context, KyawGyiFloatingService::class.java)
                                    if (KyawGyiFloatingService.isServiceRunning) {
                                        context.stopService(serviceIntent)
                                        isOverlayRunning = false
                                        Toast.makeText(context, "Floating Bubble ပိတ်လိုက်ပါပြီ", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            ContextCompat.startForegroundService(context, serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                        isOverlayRunning = true
                                        Toast.makeText(context, "Floating Bubble ဖွင့်လိုက်ပါပြီ", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isOverlayRunning) AngelNeonGreen else AngelCyanAccent
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isOverlayRunning) AngelNeonGreen.copy(alpha = 0.6f) else AngelCyanAccent.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("toggle_floating_bubble_btn")
                        ) {
                            Icon(
                                imageVector = if (isOverlayRunning) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isOverlayRunning) "Bubble ပိတ်မည်" else "Bubble ဖွင့်မည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Test Assistant Trigger
                        OutlinedButton(
                            onClick = {
                                try {
                                    val testIntent = Intent(context, KyawGyiAssistActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(testIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Assist စမ်းသပ်မှု: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("test_assist_trigger_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "စမ်းသပ်ခေါ်ယူမည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TriggerMethodItem(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = AngelCyanAccent.copy(alpha = 0.15f),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelCyanAccent
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                color = AngelTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
