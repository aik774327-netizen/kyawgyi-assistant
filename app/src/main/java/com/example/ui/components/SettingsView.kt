package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.BatteryOptimizationMode
import com.example.utils.KyawGyiBatteryOptimizer
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.KyawGyiServiceController
import com.example.utils.MyanmarAudioDSP
import com.example.utils.PermissionItemInfo

@Composable
fun SettingsView(
    systemPersona: String,
    onPersonaChanged: (String) -> Unit,
    autoSpeakEnabled: Boolean,
    onAutoSpeakChanged: (Boolean) -> Unit,
    selectedLanguage: String = "my-MM",
    onLanguageSelected: (String) -> Unit = {},
    voiceSpeed: Float = 1.0f,
    onVoiceSpeedChanged: (Float) -> Unit = {},
    voicePitch: Float = 1.0f,
    onVoicePitchChanged: (Float) -> Unit = {},
    wakeWord: String = "ကျော်ကြီး",
    onWakeWordChanged: (String) -> Unit = {},
    onExportHistoryJson: (context: Context) -> Unit = {},
    onExportHistoryText: (context: Context) -> Unit = {},
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val languageRepo = remember { com.example.data.LanguagePreferencesRepository.getInstance(context) }
    val themeRepo = remember { com.example.data.ThemePreferencesRepository.getInstance(context) }
    val currentThemeMode by themeRepo.themeMode.collectAsState()
    val dataStoreLang by languageRepo.selectedLanguageState.collectAsState()
    val activeLanguage = if (selectedLanguage.isNotBlank() && selectedLanguage != "my-MM") selectedLanguage else dataStoreLang
    var personaInput by remember(systemPersona) { mutableStateOf(systemPersona) }
    var wakeWordInput by remember(wakeWord) { mutableStateOf(wakeWord) }
    var permissionsList by remember { mutableStateOf(KyawGyiPermissionManager.getAllPermissionsStatus(context)) }
    var showDynamicPermissionsScreen by remember { mutableStateOf(false) }
    var showDirectSettingsDialog by remember { mutableStateOf(false) }
    var showSetupWizardDialog by remember { mutableStateOf(false) }
    var showDiagnosticsOverlay by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        permissionsList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissions()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "ခွင့်ပြုချက်များ အားလုံး အောင်မြင်စွာ ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ခွင့်ပြုချက် အချို့ မရရှိသေးပါ (ဆက်တင်တွင် ထပ်မံဖွင့်နိုင်ပါသည်)", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-refresh permission statuses whenever the user returns from Android Settings screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 100% Microphone Power Boost Local State
    var isMicBoostEnabled by remember { mutableStateOf(MyanmarAudioDSP.isMaxPowerCaptureEnabled) }
    var micGainMultiplier by remember { mutableFloatStateOf(MyanmarAudioDSP.micGainMultiplier) }
    var isFarFieldEnabled by remember { mutableStateOf(MyanmarAudioDSP.isFarFieldModeEnabled) }
    var isAgcEnabled by remember { mutableStateOf(MyanmarAudioDSP.isAgcHardwareEnabled) }

    // Live Mic Test DSP
    val testAudioDSP = remember { MyanmarAudioDSP(context) }
    var isTestingMic by remember { mutableStateOf(false) }
    val dspAnalysisState by testAudioDSP.analysisState.collectAsState()
    val frequencyData by testAudioDSP.frequencyData.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (testAudioDSP.isListening()) {
                testAudioDSP.stopMicrophone()
            }
        }
    }

    val grantedCount = permissionsList.count { it.isGranted }
    val totalCount = permissionsList.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("dedicated_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AngelNeonGreen.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
            color = AngelSurfaceDark
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = AngelNeonGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Dedicated Settings",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ဆက်တင်နှင့် ခွင့်ပြုချက်များ (Settings & Hub)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Microphone 100% Boost, System Deep Links & Foreground Services",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                }
            }
        }

        // =========================================================================
        // SECTION 0: ROBUST API KEY & QUOTA MANAGEMENT SYSTEM
        // =========================================================================
        ApiKeyManagementCard(
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // =========================================================================
        // SECTION 0B: DYNAMIC THEME SYSTEM (LIGHT / DARK / SYSTEM)
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AngelNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .testTag("theme_settings_card"),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (currentThemeMode) {
                                com.example.data.ThemeMode.DARK -> Icons.Default.DarkMode
                                com.example.data.ThemeMode.LIGHT -> Icons.Default.LightMode
                                com.example.data.ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = "Theme",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "အပြင်အဆင် သတ်မှတ်ချက် (Theme Mode)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Dynamic Light & Dark Theme System",
                                fontSize = 10.sp,
                                color = AngelNeonGreen
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AngelNeonGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = currentThemeMode.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "နေ့ဘက် အလင်းရောင်နှင့် ညဘက် မျက်စိအေးချမ်းစေမည့် အပြင်အဆင်များကို စိတ်ကြိုက်ပြောင်းလဲအသုံးပြုနိုင်ပါသည်။",
                    fontSize = 11.sp,
                    color = AngelTextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.data.ThemeMode.entries.forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        themeRepo.setThemeMode(mode)
                                    }
                                }
                                .testTag("theme_btn_${mode.name.lowercase()}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AngelNeonGreen else AngelSurfaceDark,
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        com.example.data.ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        com.example.data.ThemeMode.LIGHT -> Icons.Default.LightMode
                                        com.example.data.ThemeMode.DARK -> Icons.Default.DarkMode
                                    },
                                    contentDescription = mode.title,
                                    tint = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.myanmarLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =========================================================================
        // SECTION 1: DEFAULT DIGITAL ASSISTANT & SCREEN ASSIST INTEGRATION
        // =========================================================================
        DefaultAssistantGuidanceCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // =========================================================================
        // SECTION 1B: REQUIRED PERMISSIONS STATUS & SYSTEM DEEP LINKS
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Permissions Status",
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "စနစ် ခွင့်ပြုချက် အခြေအနေများ (Permissions)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (grantedCount == totalCount) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFFB74D).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "$grantedCount / $totalCount ခု အဆင်သင့်",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grantedCount == totalCount) Color(0xFF10B981) else Color(0xFFFFB74D),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "အောက်ပါ ခွင့်ပြုချက်တစ်ခုချင်းစီအတွက် Deep Link ကို နှိပ်၍ Android System Settings တွင် တိုက်ရိုက် ပြင်ဆင်နိုင်ပါသည်:",
                    fontSize = 11.sp,
                    color = AngelTextSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Permissions List with Deep Links
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    permissionsList.forEach { perm ->
                        DedicatedPermissionItemCard(
                            item = perm,
                            onOpenDeepLink = {
                                perm.openIntentAction(context)
                                refreshPermissions()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1-Tap Request All Permissions Button
                Button(
                    onClick = {
                        val requiredPerms = KyawGyiPermissionManager.getRequiredPermissions()
                        multiplePermissionsLauncher.launch(requiredPerms)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("request_all_runtime_permissions_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelNeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚡ ခွင့်ပြုချက်များ အားလုံး အလိုအလျောက် တောင်းခံမည်",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { refreshPermissions() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("refresh_permissions_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ပြန်စစ်ဆေးမည်", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showDynamicPermissionsScreen = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_dynamic_permissions_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelCardDark,
                            contentColor = AngelCyanAccent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ToggleOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ခွင့်ပြုချက် စီမံမည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Setup Wizard Master Button
                Button(
                    onClick = { showSetupWizardDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("open_setup_wizard_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelNeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✨ Kyaw Gyi AI — Setup & Permission Wizard (10 Steps)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Direct System Settings & Permissions Routes Master Button
                Button(
                    onClick = { showDirectSettingsDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("open_all_direct_settings_routes_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelCyanAccent.copy(alpha = 0.15f),
                        contentColor = AngelCyanAccent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "စနစ် ဆက်တင်နှင့် ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်းများ အားလုံး",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Dedicated Direct Settings & Permissions Routing Hub Card
        DirectSettingsPermissionHubCard(
            onOpenFullDialog = { showDirectSettingsDialog = true },
            onOpenWizard = { showSetupWizardDialog = true },
            onOpenDiagnostics = { showDiagnosticsOverlay = true },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // =========================================================================
        // SECTION 2: ADAPTIVE ACOUSTIC SENSING & 100% MICROPHONE AUDIO CAPTURE
        // =========================================================================
        AdaptiveMicrophoneSensingPanel(
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AngelNeonGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Microphone Boost",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "အသံဖမ်းယူမှု စွမ်းအား ၁၀၀% မြှင့်တင်ခြင်း",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "100% Max Background Mic Audio Capture & DSP",
                                fontSize = 10.sp,
                                color = AngelNeonGreen
                            )
                        }
                    }

                    Switch(
                        checked = isMicBoostEnabled,
                        onCheckedChange = {
                            isMicBoostEnabled = it
                            MyanmarAudioDSP.isMaxPowerCaptureEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AngelDarkBackground,
                            checkedTrackColor = AngelNeonGreen
                        ),
                        modifier = Modifier.testTag("mic_boost_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "နောက်ကွယ် (Background) တွင် ဖုန်းကို အိတ်ကပ်ထဲ သို့မဟုတ် အဝေးတွင် ထားရှိချိန်၌ပင် အသံတိုးတိုးနှင့် မြန်မာစကား အမိန့်များကို ၁၀၀% အပြည့်အဝ ဖမ်းယူနိုင်ရန် Pre-Amp Gain နှင့် DSP Filter များကို မြှင့်တင်ပေးပါသည်။",
                    fontSize = 11.sp,
                    color = AngelTextSecondary,
                    lineHeight = 16.sp
                )

                if (isMicBoostEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Boost Multiplier Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pre-Amp Digital Boost စွမ်းအား:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "${(micGainMultiplier * 100).toInt()}% (${String.format("%.1f", micGainMultiplier)}x Gain)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen
                        )
                    }

                    Slider(
                        value = micGainMultiplier,
                        onValueChange = {
                            micGainMultiplier = it
                            MyanmarAudioDSP.micGainMultiplier = it
                        },
                        valueRange = 1.0f..3.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AngelNeonGreen,
                            activeTrackColor = AngelNeonGreen
                        ),
                        modifier = Modifier.testTag("mic_gain_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware AGC Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hardware Automatic Gain Control (AGC)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "စနစ်တွင်း မိုက်ခရိုဖုန်း အသံအနိမ့်အမြင့်ကို အလိုအလျောက် ချိန်ညှိခြင်း",
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Switch(
                            checked = isAgcEnabled,
                            onCheckedChange = {
                                isAgcEnabled = it
                                MyanmarAudioDSP.isAgcHardwareEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AngelDarkBackground,
                                checkedTrackColor = AngelCyanAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Far-Field Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Far-Field / Pocket Ultra Sensitivity Mode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "အဝေးမှ စကားပြောခြင်းနှင့် တိုးညင်းသောအသံများကို ဖမ်းယူနိုင်စွမ်း မြှင့်တင်ခြင်း",
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }

                        Switch(
                            checked = isFarFieldEnabled,
                            onCheckedChange = {
                                isFarFieldEnabled = it
                                MyanmarAudioDSP.isFarFieldModeEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AngelDarkBackground,
                                checkedTrackColor = AngelNeonGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Mic Power Visualizer Gauge & Test Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AngelSurfaceDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "အသံဖမ်းယူမှု စွမ်းအား စမ်းသပ်စစ်ဆေးခြင်း",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isTestingMic) "Live dB: ${String.format("%.1f", dspAnalysisState.decibels)} dB (RMS: ${String.format("%.3f", dspAnalysisState.rms)})" else "မိုက်ခရိုဖုန်း ပိတ်ထားသည်",
                                        fontSize = 11.sp,
                                        color = if (dspAnalysisState.isSpeechDetected) AngelNeonGreen else AngelCyanAccent
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isTestingMic) {
                                            testAudioDSP.stopMicrophone()
                                            isTestingMic = false
                                        } else {
                                            val started = testAudioDSP.startMicrophone()
                                            isTestingMic = started
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isTestingMic) Color(0xFFEF4444) else AngelNeonGreen,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("test_mic_btn")
                                ) {
                                    Text(
                                        text = if (isTestingMic) "စမ်းသပ်မှု ရပ်မည်" else "မိုက် စမ်းသပ်မည်",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (isTestingMic) {
                                Spacer(modifier = Modifier.height(10.dp))
                                // Live Waveform Bars
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(30.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    frequencyData.take(32).forEach { byteVal ->
                                        val heightFraction = ((byteVal.toInt() and 0xFF) / 255f).coerceIn(0.1f, 1.0f)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(heightFraction)
                                                .background(
                                                    if (dspAnalysisState.isSpeechDetected) AngelNeonGreen else AngelCyanAccent,
                                                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // SECTION 3: INTELLIGENT BATTERY OPTIMIZER MODES
        // =========================================================================
        val currentBatteryMode by KyawGyiBatteryOptimizer.batteryMode.collectAsState()
        val autoPauseOnLowBattery by KyawGyiBatteryOptimizer.autoPauseOnLowBattery.collectAsState()

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery Optimizer",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ဘက်ထရီ ချွေတာမှု နည်းစနစ် (Battery Optimizer)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AngelNeonGreen.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Smart VAD Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "စွမ်းအင်သုံးစွဲမှုနှင့် Voice Recognition မြန်ဆန်မှုကို ချိန်ညှိပါ:",
                    fontSize = 11.sp,
                    color = AngelTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                BatteryOptimizationMode.values().forEach { mode ->
                    val isSelected = currentBatteryMode == mode
                    Surface(
                        onClick = { KyawGyiBatteryOptimizer.setBatteryMode(context, mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AngelNeonGreen.copy(alpha = 0.15f) else AngelSurfaceDark,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = mode.titleMM,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AngelNeonGreen else Color.White
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = AngelNeonGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = mode.descriptionMM,
                                fontSize = 10.sp,
                                color = AngelTextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ဘက်ထရီအားနည်းချိန် အလိုအလျောက် သက်သာစေရန်",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Power Save Mode ဖွင့်ထားချိန်တွင် CPU အသုံးပြုမှုကို လျှော့ချမည်",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }

                    Switch(
                        checked = autoPauseOnLowBattery,
                        onCheckedChange = { KyawGyiBatteryOptimizer.setAutoPauseOnLowBattery(context, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AngelNeonGreen,
                            checkedTrackColor = AngelNeonGreen.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // OEM Auto-Start & Background Battery Optimization Guidance
        OemAutoStartGuidanceCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // =========================================================================
        // SECTION 4: PREFERRED VOICE LANGUAGE & LOCALE (DATASTORE PERSISTENT)
        // =========================================================================
        LanguageSettingsComponent(
            selectedLanguageCode = activeLanguage,
            onLanguageSelected = { newLang ->
                onLanguageSelected(newLang)
                coroutineScope.launch {
                    languageRepo.savePreferredLanguage(newLang)
                }
            }
        )

        // =========================================================================
        // SECTION 4B: CUSTOM WAKE WORD & TTS VOICE CUSTOMIZATION
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Custom Wake Word & Myanmar TTS Voice",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelCyanAccent
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wakeWordInput,
                    onValueChange = {
                        wakeWordInput = it
                        onWakeWordChanged(it)
                    },
                    label = { Text("Kyaw Gyi Custom Wake Word") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AngelNeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "TTS Voice",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TTS (စာသားမှ စကားပြော) ဖွင့်/ပိတ်",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (autoSpeakEnabled) "ဖွင့်ထားသည် - တုံ့ပြန်ချက်များကို အသံဖြင့် ဖတ်ပြမည်" else "ပိတ်ထားသည်",
                                fontSize = 11.sp,
                                color = if (autoSpeakEnabled) AngelNeonGreen else AngelTextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = autoSpeakEnabled,
                        onCheckedChange = onAutoSpeakChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AngelDarkBackground,
                            checkedTrackColor = AngelNeonGreen
                        ),
                        modifier = Modifier.testTag("auto_speak_switch")
                    )
                }

                if (autoSpeakEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Voice Speed: ${String.format("%.2f", voiceSpeed)}x",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = voiceSpeed,
                        onValueChange = onVoiceSpeedChanged,
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = AngelCyanAccent,
                            activeTrackColor = AngelCyanAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Voice Pitch: ${String.format("%.2f", voicePitch)}x",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = voicePitch,
                        onValueChange = onVoicePitchChanged,
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = AngelNeonGreen,
                            activeTrackColor = AngelNeonGreen
                        )
                    )
                }
            }
        }

        // Myanmar Native Articulate & Warm Speech Synthesis System
        MyanmarNativeSpeechTuningComponent(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Myanmar TTS Diagnostic & Voice Guidance System
        MyanmarTTSDiagnosticCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Offline Speech Recognition & Connectivity Management System
        OfflineVoiceSettingsCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // STT/TTS Offline Model Downloader & Voice Settings (Sherpa-ONNX / Vosk / VoiceSettings)
        SttTtsModelAndVoiceSettingsCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // AI Error-Learning Log Viewer (ER-001 & 3-Factor Analysis / Live Condition Verifier)
        ErrorLearningLogViewerCard(
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // =========================================================================
        // SECTION 5: PERSONAL AI COMPANION PERSONA & SYSTEM PROMPT (NOVA / AURA / KYAW GYI)
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, AngelCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            var selectedPreset by remember {
                mutableStateOf(com.example.cognitive.KyawGyiSystemPersona.CompanionPersonaPreset.KYAW_GYI)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = "Personal AI Companion Persona",
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Personal AI Companion Persona",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Empathetic, Grounded, Witty & Active Listener (မြန်မာ / English)",
                                fontSize = 10.sp,
                                color = AngelNeonGreen
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AngelNeonGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = selectedPreset.badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AngelNeonGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Companion Identity Presets (Kyaw Gyi / Nova / Aura)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.example.cognitive.KyawGyiSystemPersona.CompanionPersonaPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedPreset = preset
                                    val updatedPrompt = com.example.cognitive.KyawGyiSystemPersona.buildPersonalCompanionPrompt(
                                        companionName = preset.displayName
                                    )
                                    personaInput = updatedPrompt
                                    onPersonaChanged(updatedPrompt)
                                    Toast.makeText(
                                        context,
                                        "AI Companion ကို '${preset.displayName}' သို့ ပြောင်းလဲလိုက်ပါပြီ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("companion_preset_${preset.id.lowercase()}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AngelCyanAccent.copy(alpha = 0.22f) else AngelSurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) AngelCyanAccent else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AngelCyanAccent else Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = preset.roleSummaryMM,
                                    fontSize = 9.sp,
                                    color = AngelTextSecondary,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4-Pillar Companion Architecture Summary
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelSurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• 1. Identity & Persona: နွေးထွေး၊ ရိုးသားပွင့်လင်းပြီး အားကိုးရသော မိတ်ဆွေကောင်း (Supportive Friend & Tech Partner)",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = "• 2. Dynamic Mood Adaptability: စိတ်ဖိစီးချိန် နှစ်သိမ့်ပေးခြင်း၊ ပျော်ရွှင်ချိန် ထပ်တူပျော်ခြင်း၊ ပြဿနာဖြေရှင်းချိန် စနစ်တကျ ခွဲခြမ်းသုံးသပ်ခြင်း",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "• 3. Guiding Principles: စက်ရုပ်ဆန်သော စကားလုံးများ ရှောင်ရှားပြီး မိတ်ဆွေရင်းပမာ ပွင့်လင်းရိုးသားစွာ အကြံပြုခြင်း (Empathy with Candor)",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "• 4. Boundaries & Well-being: စိတ်ပိုင်းဆိုင်ရာ ကျန်းမာကြံ့ခိုင်မှုကို အားပေးပြီး ဆေးဘက်/ဥပဒေဆိုင်ရာ ကိစ္စများတွင် ပညာရှင်ထံ လမ်းညွှန်ခြင်း",
                            fontSize = 10.sp,
                            color = AngelNeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = personaInput,
                    onValueChange = { personaInput = it },
                    label = { Text("System Persona & Instructions (Markdown Prompt)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("persona_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AngelNeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        focusedTextColor = AngelTextPrimary,
                        unfocusedTextColor = AngelTextPrimary
                    ),
                    maxLines = 6
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onPersonaChanged(personaInput)
                        Toast.makeText(context, "Personal AI Companion System Prompt ကို သိမ်းဆည်းပြီးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AngelNeonGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("save_persona_btn")
                ) {
                    Text("Update Persona", color = AngelDarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =========================================================================
        // SECTION 6: HISTORY EXPORT & STORAGE
        // =========================================================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = AngelCardDark
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Conversation History & Storage",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onExportHistoryJson(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black)
                    ) {
                        Text("Export JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onExportHistoryText(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AngelNeonGreen, contentColor = Color.Black)
                    ) {
                        Text("Export Text", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_history_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear History",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Clear All Chat & Voice Logs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Dynamic Permissions Screen Dialog
        if (showDynamicPermissionsScreen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {
                    showDynamicPermissionsScreen = false
                    refreshPermissions()
                },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp),
                    color = AngelDarkBackground
                ) {
                    SettingsScreen(
                        selectedLanguage = activeLanguage,
                        onLanguageSelected = { newLang ->
                            onLanguageSelected(newLang)
                            coroutineScope.launch {
                                languageRepo.savePreferredLanguage(newLang)
                            }
                        },
                        onNavigateBack = {
                            showDynamicPermissionsScreen = false
                            refreshPermissions()
                        }
                    )
                }
            }
        }

        // Full Direct System Settings & Permissions Dialog
        if (showDirectSettingsDialog) {
            PermissionSettingsDialog(
                onDismiss = {
                    showDirectSettingsDialog = false
                    refreshPermissions()
                },
                onRequestRuntimePermissions = {
                    val requiredPerms = KyawGyiPermissionManager.getRequiredPermissions()
                    multiplePermissionsLauncher.launch(requiredPerms)
                }
            )
        }

        // 10-Step Setup & Permission Wizard Dialog
        if (showSetupWizardDialog) {
            com.example.setup.KyawGyiSetupWizardDialog(
                onDismiss = {
                    showSetupWizardDialog = false
                    refreshPermissions()
                },
                onFinishSetup = {
                    showSetupWizardDialog = false
                    refreshPermissions()
                    Toast.makeText(context, "Kyaw Gyi AI Setup အောင်မြင်စွာ ပြီးဆုံးပါပြီ ✓", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Unified Diagnostics Overlay (Hardware, OS Permissions & Settings A-to-Z Check)
        if (showDiagnosticsOverlay) {
            UnifiedDiagnosticsOverlay(
                onDismiss = {
                    showDiagnosticsOverlay = false
                    refreshPermissions()
                }
            )
        }
    }
}

@Composable
fun DedicatedPermissionItemCard(
    item: PermissionItemInfo,
    onOpenDeepLink: () -> Unit
) {
    val icon: ImageVector = when (item.id) {
        "RECORD_AUDIO" -> Icons.Default.Mic
        "SYSTEM_ALERT_WINDOW" -> Icons.Default.Layers
        "ACCESSIBILITY_SERVICE" -> Icons.Default.AccessibilityNew
        "BATTERY_OPTIMIZATION" -> Icons.Default.BatteryChargingFull
        "OEM_AUTOSTART" -> Icons.Default.Shield
        "DEFAULT_ASSISTANT" -> Icons.Default.Assistant
        "POST_NOTIFICATIONS" -> Icons.Default.Notifications
        "NOTIFICATION_POLICY" -> Icons.Default.DoNotDisturbOn
        "FOREGROUND_SERVICE" -> Icons.Default.Bolt
        "WRITE_SETTINGS" -> Icons.Default.SettingsSuggest
        "APP_DETAILS" -> Icons.Default.Info
        else -> Icons.Default.Settings
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AngelSurfaceDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isGranted) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFFFB74D).copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = (if (item.isGranted) Color(0xFF10B981) else Color(0xFFFFB74D)).copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (item.isGranted) Color(0xFF10B981) else Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.titleMM,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = item.titleEN,
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = if (item.isGranted) "ခွင့်ပြုပြီး ✓" else "သတ်မှတ်ရန်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isGranted) Color(0xFF10B981) else Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 11.sp,
                color = AngelTextSecondary,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onOpenDeepLink,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isGranted) AngelCardDark else AngelCyanAccent,
                    contentColor = if (item.isGranted) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("open_deep_link_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.isGranted) "System Setting စစ်ဆေးမည်" else "Android Setting သို့ တိုက်ရိုက်သွားမည် >",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
