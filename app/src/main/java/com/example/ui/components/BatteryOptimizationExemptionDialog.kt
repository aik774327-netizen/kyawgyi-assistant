package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager

/**
 * Battery Optimization Exemption Dialog (ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့် ဒိုင်ယာလော့ခ်)
 *
 * Specifically addresses Android 14 (API 34) Foreground Service restrictions:
 * - FOREGROUND_SERVICE_MICROPHONE & FOREGROUND_SERVICE_MEDIA_PROJECTION constraints
 * - Prevents Android OS from killing the background listening process when screen is off
 *   or when the user switches to other applications.
 */
@Composable
fun BatteryOptimizationExemptionDialog(
    onDismiss: () -> Unit,
    onExemptionRequested: () -> Unit = {}
) {
    val context = LocalContext.current
    var isExempt by remember {
        mutableStateOf(KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context))
    }

    // Periodically re-check status if user went to settings and returned
    LaunchedEffect(Unit) {
        while (true) {
            isExempt = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AngelSurfaceDark,
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .border(1.dp, AngelCyanAccent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .testTag("battery_optimization_exemption_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon with Glow Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (isExempt) AngelNeonGreen.copy(alpha = 0.18f) else AngelOrangeAccent.copy(alpha = 0.18f),
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (isExempt) AngelNeonGreen else AngelOrangeAccent,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExempt) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = "Battery Optimization",
                        tint = if (isExempt) AngelNeonGreen else AngelOrangeAccent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Battery Optimization & Android 14 FGS Protection",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AngelCyanAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Live Status Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExempt) Color(0xFF0F3826) else Color(0xFF382312),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isExempt) AngelNeonGreen.copy(alpha = 0.5f) else AngelOrangeAccent.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExempt) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isExempt) AngelNeonGreen else AngelOrangeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isExempt) "အကာအကွယ် ရရှိပြီး (Don't Optimize သတ်မှတ်ပြီး)" else "ကင်းလွတ်ခွင့် မရရှိသေးပါ (သတ်မှတ်ရန် လိုအပ်သည်)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExempt) AngelNeonGreen else AngelOrangeAccent
                            )
                            Text(
                                text = if (isExempt) "နောက်ခံအသံနားထောင်မှု စနစ် ချောမွေ့စွာ အလုပ်လုပ်ပါမည်" else "စခရင်ပိတ်ချိန် OS မှ Background Process အား Kill နိုင်သည်",
                                fontSize = 11.sp,
                                color = AngelTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Technical explanation card for Android 14 & Screen Off behavior
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AngelCardDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AngelCyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "အဘယ်ကြောင့် လိုအပ်သနည်း (Technical Rationale)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelCyanAccent
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "• Android 14 (API 34) Foreground Service Type: အသံဖမ်းယူမှုအတွက် FOREGROUND_SERVICE_MICROPHONE အမျိုးအစားကို အသုံးပြုထားသော်လည်း ဘက်ထရီချွေတာမှု မပိတ်ထားပါက Background မှ အသံဖမ်းခွင့်ကို စနစ်မှ ရပ်တန့်တတ်ပါသည်။",
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• စခရင်ပိတ်ထားချိန် (Screen Off): အခြားအက်ပ်သုံးနေချိန် သို့မဟုတ် ဖုန်း screen ပိတ်ထားချိန်တွင် Android OS မှ Background Process ကို Kill မပစ်စေရန် Battery Optimization မှ ကင်းလွတ်ခွင့် (Ignore Battery Optimizations) ပေးထားရန် မဖြစ်မနေ လိုအပ်ပါသည်။",
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "• နိုးကြားစကား (Wake Word): 'ကျော်ကြီး' ဟု ခေါ်ယူလိုက်ပါက အချိန်မရွေး ၂၄ နာရီ တုံ့ပြန်နိုင်စေရန် WakeLock နှင့်အတူ နောက်ခံတွင် အသင့်စောင့်ကြိုနားထောင်နိုင်ပါမည်။",
                            fontSize = 12.sp,
                            color = AngelTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Button(
                    onClick = {
                        onExemptionRequested()
                        KyawGyiPermissionManager.openBatteryOptimizationSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExempt) Color(0xFF2E7D32) else AngelCyanAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_battery_exemption_button")
                ) {
                    Icon(
                        imageVector = if (isExempt) Icons.Default.CheckCircle else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isExempt) Color.White else AngelDarkBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExempt) "ဆက်တင်များတွင် ပြန်လည်စစ်ဆေးမည်" else "ကင်းလွတ်ခွင့် ပေးမည် (Don't Optimize)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExempt) Color.White else AngelDarkBackground
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("dismiss_battery_dialog_button")
                ) {
                    Text(
                        text = if (isExempt) "ပြီးပါပြီ (ပိတ်မည်)" else "နောင်မှ ပြုလုပ်မည် (ပိတ်မည်)",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Reusable Composable helper to manage Battery Optimization state and dialog presentation.
 */
@Composable
fun rememberBatteryOptimizationState(): BatteryOptimizationState {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var isExempt by remember {
        mutableStateOf(KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context))
    }

    LaunchedEffect(Unit) {
        isExempt = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
    }

    return remember(showDialog, isExempt) {
        BatteryOptimizationState(
            context = context,
            isExempt = isExempt,
            showDialog = showDialog,
            onShowDialog = { showDialog = it },
            refresh = {
                isExempt = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            }
        )
    }
}

class BatteryOptimizationState(
    private val context: Context,
    val isExempt: Boolean,
    val showDialog: Boolean,
    private val onShowDialog: (Boolean) -> Unit,
    val refresh: () -> Unit
) {
    fun openDialog() {
        refresh()
        onShowDialog(true)
    }

    fun dismissDialog() {
        onShowDialog(false)
        refresh()
    }

    fun requestExemption() {
        KyawGyiPermissionManager.openBatteryOptimizationSettings(context)
        refresh()
    }
}
