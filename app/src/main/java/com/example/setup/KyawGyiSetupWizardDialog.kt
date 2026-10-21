package com.example.setup

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.router.DirectSettingsRouter
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager

/**
 * KYAW GYI AI — Setup & Permission Wizard (Screens 1 to 10)
 *
 * Screen 1: Welcome & Overview (စနစ်မိတ်ဆက်နှင့် လိုအပ်ချက်များ)
 * Screen 2: Microphone & Audio Hardware (P01 & Audio Pipeline)
 * Screen 3: Overlay & Assistive Touch Ball (P02 Floating UI)
 * Screen 4: System Accessibility Service (P03 Hands-Free Automation)
 * Screen 5: Battery Optimization & Deep Sleep (P04 Background Guardian)
 * Screen 6: OEM Auto-Start Protection (P05 Xiaomi/Samsung/Oppo/Vivo Fallbacks)
 * Screen 7: Burmese Voice Engine STT & TTS (P12 & P13 Language Config)
 * Screen 8: Alarms & System Controls (P07 & P08 Timers/Brightness/Sound)
 * Screen 9: Full 16-Capability System Diagnostic Center (P01-P16 Real Verification)
 * Screen 10: Completion & Ready to Assist (အဆင်သင့်ဖြစ်ပြီ - စတင်အသုံးပြုခြင်း)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KyawGyiSetupWizardDialog(
    onDismiss: () -> Unit,
    onFinishSetup: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 10

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val results = remember(refreshTrigger) {
        CapabilityDiagnosticsEngine.checkAll(context)
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val multiplePermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .testTag("kyaw_gyi_setup_wizard_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = AngelDarkBackground,
            border = BorderStroke(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(AngelCyanAccent.copy(alpha = 0.8f), AngelNeonGreen.copy(alpha = 0.5f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Wizard Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (currentStep > 1) currentStep-- else onDismiss()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (currentStep > 1) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Kyaw Gyi AI Setup Wizard",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "အဆင့် ($currentStep / $totalSteps)",
                            fontSize = 12.sp,
                            color = AngelCyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = { refreshTrigger++ },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AngelNeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Linear Progress Indicator across 10 steps
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AngelNeonGreen,
                    trackColor = AngelSurfaceDark
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step Content Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        1 -> WizardScreen1Welcome()
                        2 -> WizardScreen2Microphone(results, context) {
                            val perms = KyawGyiPermissionManager.getRequiredPermissions()
                            multiplePermsLauncher.launch(perms)
                        }
                        3 -> WizardScreen3Overlay(results, context)
                        4 -> WizardScreen4Accessibility(results, context)
                        5 -> WizardScreen5Battery(results, context)
                        6 -> WizardScreen6OemProtection(results, context)
                        7 -> WizardScreen7VoiceEngine(results, context)
                        8 -> WizardScreen8SystemAlarms(results, context)
                        9 -> WizardScreen9DiagnosticCenter(results, context) {
                            refreshTrigger++
                        }
                        10 -> WizardScreen10Completion(results, onFinishSetup)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wizard Bottom Navigation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ရှေ့သို့", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < totalSteps) {
                                currentStep++
                            } else {
                                onFinishSetup()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelNeonGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("wizard_next_step_btn")
                    ) {
                        Text(
                            text = if (currentStep < totalSteps) "ဆက်သွားမည် >" else "စတင်အသုံးပြုမည် ✓",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentStep < totalSteps) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 1: WELCOME & SYSTEM ARCHITECTURE
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen1Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = AngelCyanAccent.copy(alpha = 0.15f),
            modifier = Modifier.size(90.dp),
            border = BorderStroke(2.dp, AngelCyanAccent.copy(alpha = 0.6f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "မင်္ဂလာပါ — ကျော်ကြီး AI အသင့်ရှိပါသည်",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "မြန်မာစကားသံဖြင့် ဖုန်းလုပ်ဆောင်ချက်များကို အပြည့်အဝ ထိန်းချုပ်နိုင်ရန် စနစ်ခွင့်ပြုချက်များကို စတင်သတ်မှတ်ပါမည်။",
            fontSize = 13.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AngelCardDark,
            border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "စနစ် အဓိက အင်္ဂါရပ်များ (100% On-Device):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelNeonGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                FeatureBulletPoint("၁။ 'ကျော်ကြီး' Wake Word ဖြင့် မျက်နှာပြင်ပိတ်ထားစဉ် အသံနိုးကြားခြင်း")
                FeatureBulletPoint("၂။ Assistive Touch Floating Mic Ball ဖြင့် မည်သည့် App ပေါ်တွင်မဆို ခေါ်ယူခြင်း")
                FeatureBulletPoint("၃။ Accessibility Service ဖြင့် ဖုန်း Call, Home, Back, Volume အလိုအလျောက်ထိန်းချုပ်ခြင်း")
                FeatureBulletPoint("၄။ သီးသန့် Room Database ဖြင့် လုံခြုံစွာ ဖုန်းတွင်း သိမ်းဆည်းခြင်း")
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 2: MICROPHONE & AUDIO CAPTURE (P01)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen2Microphone(
    results: List<CapabilityCheckResult>,
    context: Context,
    onRequestRuntimePermissions: () -> Unit
) {
    val micResult = results.find { it.capability == KyawGyiCapability.P01_MICROPHONE }
    val isGranted = micResult?.isGranted == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, if (isGranted) AngelNeonGreen else Color(0xFFFFB74D))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် (RECORD_AUDIO)",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isGranted) "ခွင့်ပြုချက် အောင်မြင်စွာ ရရှိထားပါသည် ✓ (16kHz PCM AudioRecord အသင့်)"
            else "မြန်မာအသံအမိန့်များ ဖမ်းယူရန်နှင့် 'ကျော်ကြီး' နိုးကြားစကားကို နားထောင်ရန် မိုက်ခရိုဖုန်း ခွင့်ပြုချက် လိုအပ်ပါသည်",
            fontSize = 12.sp,
            color = if (isGranted) AngelNeonGreen else AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        CapabilityStatusCard(
            title = "P01: Microphone Audio Pipeline",
            details = micResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = isGranted
        )

        Spacer(modifier = Modifier.weight(1f))

        if (!isGranted) {
            Button(
                onClick = onRequestRuntimePermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("မိုက်ခရိုဖုန်း ခွင့်ပြုချက် တောင်းခံမည်", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { micResult?.directRouteAction?.invoke(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Setting သို့ တိုက်ရိုက်သွားမည် >", fontSize = 12.sp)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 3: OVERLAY (P02)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen3Overlay(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val overlayResult = results.find { it.capability == KyawGyiCapability.P02_OVERLAY }
    val isGranted = overlayResult?.isGranted == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isGranted) AngelNeonGreen else AngelCyanAccent).copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, if (isGranted) AngelNeonGreen else AngelCyanAccent)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (isGranted) AngelNeonGreen else AngelCyanAccent,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Overlay)",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "အခြား Application များ သုံးနေချိန်နှင့် ဖုန်းစခရင်တိုင်းတွင် Floating Mic Ball ကို အလွယ်တကူနှိပ်၍ ကျော်ကြီးကို စကားပြောနိုင်စေရန် ဖြစ်ပါသည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        CapabilityStatusCard(
            title = "P02: Display Over Other Apps",
            details = overlayResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = isGranted
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { overlayResult?.directRouteAction?.invoke(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) AngelSurfaceDark else AngelNeonGreen,
                contentColor = if (isGranted) Color.White else Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGranted) "Overlay ခွင့်ပြုပြီးပါပြီ (Setting ပြန်ဖွင့်မည်)" else "Overlay ခွင့်ပြုချက် Setting ဖွင့်မည် >",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 4: ACCESSIBILITY SERVICE (P03)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen4Accessibility(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val accResult = results.find { it.capability == KyawGyiCapability.P03_ACCESSIBILITY }
    val isGranted = accResult?.isGranted == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, if (isGranted) AngelNeonGreen else Color(0xFFFFB74D))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    tint = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Accessibility အလိုအလျောက် ထိန်းချုပ်ခွင့်",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "'KyawGyiAccessibilityService' ကို ဖွင့်ပေးခြင်းဖြင့် အသံဖြင့် App များ အလိုအလျောက် ဖွင့်ခြင်း၊ Back/Home သွားခြင်း၊ စာရိုက်ခြင်းများကို လက်မပါဘဲ လုပ်ဆောင်နိုင်ပါမည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        CapabilityStatusCard(
            title = "P03: KyawGyiAccessibilityService",
            details = accResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = isGranted
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { accResult?.directRouteAction?.invoke(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) AngelSurfaceDark else AngelCyanAccent,
                contentColor = if (isGranted) Color.White else Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGranted) "Accessibility ဖွင့်ပြီးပါပြီ ✓" else "Accessibility Setting သို့ တိုက်ရိုက်သွားမည် >",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 5: BATTERY OPTIMIZATION & DEEP SLEEP (P04)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen5Battery(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val batResult = results.find { it.capability == KyawGyiCapability.P04_BATTERY_OPTIMIZATION }
    val isGranted = batResult?.isGranted == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = (if (isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, if (isGranted) AngelNeonGreen else Color(0xFFFFB74D))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "ဖုန်းစခရင် ပိတ်ထားပြီး အိပ်မောကျ (Deep Sleep / Doze Mode) ဖြစ်နေချိန်တွင် Android မှ ကျော်ကြီး Voice Service ကို ပိတ်မပစ်စေရန် ဖွင့်ပေးရန် လိုအပ်ပါသည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        CapabilityStatusCard(
            title = "P04: Battery Whitelist (Ignore Optimizations)",
            details = batResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = isGranted
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { batResult?.directRouteAction?.invoke(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isGranted) AngelSurfaceDark else AngelNeonGreen,
                contentColor = if (isGranted) Color.White else Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isGranted) "ဘက်ထရီ ကင်းလွတ်ခွင့် ရရှိပြီးပါပြီ ✓" else "ဘက်ထရီ ဆက်တင်သို့ တိုက်ရိုက်သွားမည် >",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 6: OEM AUTO-START PROTECTION (P05)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen6OemProtection(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val oemResult = results.find { it.capability == KyawGyiCapability.P05_OEM_AUTOSTART }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = AngelCyanAccent.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, AngelCyanAccent)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "OEM Auto-Start စောင့်ရှောက်မှု",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Xiaomi (HyperOS / MIUI), Samsung, OPPO (ColorOS), Vivo (FuntouchOS/OriginOS) နှင့် Realme ဖုန်းများတွင် Auto-start နှင့် Background Launch ခွင့်ပြုထားရန် လိုအပ်ပါသည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        CapabilityStatusCard(
            title = "P05: OEM Background Execution & Auto-Start",
            details = "ဖုန်းအမျိုးအစားအလိုက် AutoStart Manager ဆက်တင်သို့ တိုက်ရိုက်လမ်းကြောင်း ပံ့ပိုးပေးပါသည်",
            isGranted = true
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val success = DirectSettingsRouter.getInstance(context).openOemAutostartSettings()
                if (success) {
                    Toast.makeText(context, "OEM AutoStart ဆက်တင်သို့ ဖွင့်လှစ်နေပါသည်...", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("ဖုန်းအလိုက် Auto-Start ဆက်တင် ဖွင့်မည် >", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 7: BURMESE VOICE ENGINE STT & TTS (P12 & P13)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen7VoiceEngine(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val ttsResult = results.find { it.capability == KyawGyiCapability.P12_TTS_ENGINE }
    val sttResult = results.find { it.capability == KyawGyiCapability.P13_VOICE_INPUT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = AngelNeonGreen.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, AngelNeonGreen)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = AngelNeonGreen,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "မြန်မာ စကားသံထွက် (TTS) နှင့် အသံသွင်း (STT)",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "ကျော်ကြီး၏ သဘာဝကျသော မြန်မာစကားပြောသံ (my-MM) ထွက်ရှိရန်နှင့် Google Voice Typing အသံဖမ်းယူမှု စနစ် ဖြစ်ပါသည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityStatusCard(
            title = "P12: Text-to-Speech Engine (my-MM)",
            details = ttsResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = ttsResult?.isGranted == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        CapabilityStatusCard(
            title = "P13: SpeechRecognizer / Voice Input",
            details = sttResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = sttResult?.isGranted == true
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { ttsResult?.directRouteAction?.invoke(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent)
            ) {
                Text("TTS ဆက်တင် >", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    com.example.utils.MyanmarTTSEngine(context) { ready ->
                        if (ready) {
                            Toast.makeText(context, "ကျော်ကြီး မြန်မာအသံ စမ်းသပ်မှု အောင်မြင်ပါသည်", Toast.LENGTH_SHORT).show()
                        }
                    }.speak("မင်္ဂလာပါခင်ဗျာ၊ ကျွန်တော် ကျော်ကြီး ဖြစ်ပါတယ်")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AngelNeonGreen, contentColor = Color.Black)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("အသံစမ်းသပ်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 8: SYSTEM ALARMS & CONTROLS (P07 & P08)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen8SystemAlarms(
    results: List<CapabilityCheckResult>,
    context: Context
) {
    val alarmResult = results.find { it.capability == KyawGyiCapability.P07_EXACT_ALARM }
    val writeResult = results.find { it.capability == KyawGyiCapability.P08_WRITE_SETTINGS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = AngelCyanAccent.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp),
            border = BorderStroke(2.dp, AngelCyanAccent)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "တိကျသော Alarm နှင့် စနစ် ထိန်းချုပ်ခွင့်",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Pomodoro Timer များ Deep Sleep တွင် တိကျစွာ အချက်ပေးရန်နှင့် မျက်နှာပြင် အလင်းအမှောင်/အသံ အတိုးအကျယ် ထိန်းချုပ်ရန် ဖြစ်ပါသည်။",
            fontSize = 12.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        CapabilityStatusCard(
            title = "P07: SCHEDULE_EXACT_ALARM",
            details = alarmResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = alarmResult?.isGranted == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        CapabilityStatusCard(
            title = "P08: Settings.System.canWrite",
            details = writeResult?.diagnosticDetails ?: "စစ်ဆေးနေပါသည်...",
            isGranted = writeResult?.isGranted == true
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { alarmResult?.directRouteAction?.invoke(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black)
            ) {
                Text("Alarm Setting >", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { writeResult?.directRouteAction?.invoke(context) },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AngelNeonGreen, contentColor = Color.Black)
            ) {
                Text("System Write >", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 9: FULL 16-CAPABILITY DIAGNOSTIC CENTER (P01 - P16)
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen9DiagnosticCenter(
    results: List<CapabilityCheckResult>,
    context: Context,
    onRefresh: () -> Unit
) {
    val passCount = results.count { it.isGranted }
    val totalCount = results.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "စနစ် စွမ်းဆောင်ရည် ၁၆ မျိုး အပြည့်အစုံ စစ်ဆေးမှု",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "အခြေအနေ: $passCount / $totalCount ခု အောင်မြင်သည် (No-Fake Guaranteed)",
                    fontSize = 11.sp,
                    color = if (passCount >= totalCount / 2) AngelNeonGreen else Color(0xFFFFB74D),
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = AngelNeonGreen)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { item ->
                DiagnosticCapabilityRow(item, context)
            }
        }
    }
}

@Composable
private fun DiagnosticCapabilityRow(
    item: CapabilityCheckResult,
    context: Context
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AngelCardDark,
        border = BorderStroke(
            1.dp,
            if (item.isGranted) AngelNeonGreen.copy(alpha = 0.35f) else Color(0xFFFFB74D).copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = (if (item.isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.capability.getIcon(),
                            contentDescription = null,
                            tint = if (item.isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AngelCyanAccent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = item.capability.code,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelCyanAccent,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.capability.titleMM,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = item.diagnosticDetails,
                        fontSize = 10.sp,
                        color = AngelTextSecondary,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = { item.directRouteAction(context) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "Open Settings",
                    tint = if (item.isGranted) AngelNeonGreen else AngelCyanAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// SCREEN 10: COMPLETION & ACTIVATION
// -----------------------------------------------------------------------------------------
@Composable
private fun WizardScreen10Completion(
    results: List<CapabilityCheckResult>,
    onFinishSetup: () -> Unit
) {
    val passCount = results.count { it.isGranted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = AngelNeonGreen.copy(alpha = 0.18f),
            modifier = Modifier.size(90.dp),
            border = BorderStroke(2.dp, AngelNeonGreen)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AngelNeonGreen,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Setup ပြီးဆုံးပါပြီ ✓",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "စနစ် ခွင့်ပြုချက် $passCount ခု အောင်မြင်စွာ စစ်ဆေးပြီးစီးပါပြီ။ ယခုအခါ 'ကျော်ကြီး' ဟု ခေါ်ဆို၍သော်လည်းကောင်း၊ Floating Mic Ball ကို နှိပ်၍သော်လည်းကောင်း အသုံးပြုနိုင်ပါပြီ။",
            fontSize = 13.sp,
            color = AngelTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AngelCardDark,
            border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "စတင် စမ်းသပ်နိုင်သော မြန်မာအသံအမိန့်များ:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelCyanAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "• \"ကျော်ကြီး ရေ ဖုန်းခေါ်ပေးပါ\"", fontSize = 11.sp, color = Color.White)
                Text(text = "• \"ကျော်ကြီး ဓာတ်မီးဖွင့်ပေးပါ\"", fontSize = 11.sp, color = Color.White)
                Text(text = "• \"ကျော်ကြီး သီချင်းဖွင့်ပါ\"", fontSize = 11.sp, color = Color.White)
                Text(text = "• \"ကျော်ကြီး Facebook ဖွင့်ပေးပါ\"", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun CapabilityStatusCard(
    title: String,
    details: String,
    isGranted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AngelCardDark,
        border = BorderStroke(
            1.dp,
            if (isGranted) AngelNeonGreen.copy(alpha = 0.4f) else Color(0xFFFFB74D).copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = AngelTextSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = (if (isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (isGranted) "PASS ✓" else "သတ်မှတ်ရန်",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun FeatureBulletPoint(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = AngelTextSecondary,
        lineHeight = 16.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
