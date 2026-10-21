package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.KyawGyiServiceController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.KyawGyiAccessibilityService
import com.example.service.KyawGyiFloatingService
import com.example.service.MyanmarCommands
import com.example.ui.theme.*

@Composable
fun KyawGyiVoiceControllerView(
    commandLogs: List<com.example.data.CommandLogEntity> = emptyList(),
    customShortcuts: List<com.example.data.CustomShortcutEntity> = emptyList(),
    last50Messages: List<com.example.data.ChatMessageEntity> = emptyList(),
    onAddShortcut: (phrase: String, target: String, desc: String) -> Unit = { _, _, _ -> },
    onToggleShortcut: (shortcut: com.example.data.CustomShortcutEntity) -> Unit = {},
    onDeleteShortcut: (shortcut: com.example.data.CustomShortcutEntity) -> Unit = {},
    onDeleteCommandLog: (id: Long) -> Unit = {},
    onClearCommandLogs: () -> Unit = {},
    onLogCommand: (text: String, type: String, status: String, detail: String) -> Unit = { _, _, _, _ -> },
    onReplayAudio: (text: String) -> Unit = {},
    onDeleteMessage: (id: Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var selectedSubTab by remember { mutableStateOf(0) } // 0: Mic Controller, 1: Command Logs, 2: Custom Shortcuts, 3: Conversation Timeline
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var showSuccessAnimation by remember { mutableStateOf(false) }
    var successCommandMessage by remember { mutableStateOf("") }

    var isFloatingRunning by remember { mutableStateOf(KyawGyiFloatingService.isServiceRunning) }
    var isAccessibilityActive by remember { mutableStateOf(KyawGyiAccessibilityService.instance != null) }
    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isBatteryExempt by remember {
        mutableStateOf(KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context))
    }
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            if (hasOverlayPermission) {
                val intent = Intent(context, KyawGyiFloatingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isFloatingRunning = true
            } else {
                Log.w("KyawGyiVoiceController", "Audio permission granted, but overlay permission is missing. Cannot start floating service yet.")
            }
        }
    }

    var testCommandText by remember { mutableStateOf("ကျော်ကြီး ပင်မမျက်နှာပြင်") }
    var lastExecutionResult by remember { mutableStateOf("အဆင့်သင့်ဖြစ်ပါသည်") }

    // Periodically re-check active statuses
    LaunchedEffect(Unit) {
        while (true) {
            isFloatingRunning = KyawGyiFloatingService.isServiceRunning
            isAccessibilityActive = KyawGyiAccessibilityService.instance != null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
            hasAudioPermission = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            isBatteryExempt = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("kyaw_gyi_controller_view")
    ) {
        // Sub-Navigation Bar
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = AngelSurfaceDark,
            contentColor = AngelCyanAccent,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Mic Controller", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Command Logs (${commandLogs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                text = { Text("Custom Shortcuts (${customShortcuts.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 3,
                onClick = { selectedSubTab = 3 },
                text = { Text("Conversation Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        // Haptic Visual Success Animation Banner
        AnimatedVisibility(visible = showSuccessAnimation) {
            Surface(
                color = Color(0xFF1B5E20),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Voice Command Executed: $successCommandMessage ✓",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        when (selectedSubTab) {
            0 -> {
                // Mic & Floating Controller Tab
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hero Banner Card
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, AngelNeonGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                            color = AngelSurfaceDark
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(AngelNeonGreen.copy(alpha = 0.2f))
                                                .border(1.dp, AngelNeonGreen, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Mic,
                                                contentDescription = "Kyaw Gyi Wake Word",
                                                tint = AngelNeonGreen,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "ကျော်ကြီး (Kyaw Gyi)",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Wake Word & Background Assistive Voice",
                                                fontSize = 12.sp,
                                                color = AngelNeonGreen
                                            )
                                        }
                                    }

                                    // Live Status Pill
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isFloatingRunning) AngelNeonGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isFloatingRunning) AngelNeonGreen else Color.Red
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isFloatingRunning) AngelNeonGreen else Color.Red)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isFloatingRunning) "ACTIVE" else "OFF",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isFloatingRunning) AngelNeonGreen else Color.Red
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "နောက်ခံ (Background) နှင့် လော့ခ်စခရင် (Lock Screen) ပေါ်တွင် \"ကျော်ကြီး\" Wake Word ဖြင့် လက်မသုံးဘဲ (Hands-Free) မြန်မာဘာသာ အသံဖြင့် ထိန်းချုပ်နိုင်ပါသည်။",
                                    fontSize = 13.sp,
                                    color = AngelTextPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Voice State & Rhythmic Visualizer (Imported Features Integrated)
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, AngelCyanAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                            color = AngelSurfaceDark
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VoiceIndicator(
                                    isActive = isFloatingRunning,
                                    isProcessing = false,
                                    onClick = {
                                        val intent = Intent(context, KyawGyiFloatingService::class.java)
                                        if (isFloatingRunning) {
                                            context.stopService(intent)
                                            isFloatingRunning = false
                                        } else {
                                            if (hasAudioPermission && hasOverlayPermission) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(intent)
                                                } else {
                                                    context.startService(intent)
                                                }
                                                isFloatingRunning = true
                                            } else {
                                                Toast.makeText(context, "ခွင့်ပြုချက်များ (Permissions) အရင်ပေးရန် လိုအပ်ပါသည်", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    listeningLabel = "ကျော်ကြီး နားထောင်နေသည်...",
                                    tapLabel = if (isFloatingRunning) "အသံအမိန့်ပေးရန် အဆင်သင့်ဖြစ်ပါပြီ" else "စတင်အသုံးပြုရန် ခလုတ်ကို နှိပ်ပါ"
                                )

                                if (isFloatingRunning) {
                                    AudioVisualizer(
                                        volume = 0.8f,
                                        isConnected = true,
                                        isSpeaking = false
                                    )
                                }
                            }
                        }
                    }

                    // App Shortcuts Grid
                    item {
                        AppGrid(
                            onAppClick = { app ->
                                onLogCommand("အက်ပ်ဖွင့်: ${app.burmeseName}", "OPEN_APP", "SUCCESS", "Launched ${app.name}")
                            }
                        )
                    }

                    // Section 1: Service Controls & Permissions
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                            color = AngelCardDark
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "၁။ ဝန်ဆောင်မှုနှင့် ခွင့်ပြုချက်များ (System Services)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelCyanAccent
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Microphone Record Audio Permission Item
                                StatusPermissionRow(
                                    title = "Microphone Permission (RECORD_AUDIO)",
                                    subtitle = "မြန်မာအသံအမိန့်များ ၁၀၀% ဖမ်းယူနားထောင်နိုင်ရန်",
                                    isGranted = hasAudioPermission,
                                    onGrantClick = {
                                        showPermissionExplanation = true
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Floating Overlay Permission Item
                                StatusPermissionRow(
                                    title = "Display Over Other Apps (Overlay)",
                                    subtitle = "Lock Screen & Floating Mic View ပေါ်စေရန်",
                                    isGranted = hasOverlayPermission,
                                    onGrantClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(intent)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                 // Accessibility Service Item
                                StatusPermissionRow(
                                    title = "Accessibility Control Service",
                                    subtitle = "Home, Back, Recents, Scroll မြန်မာအမိန့်များ လုပ်ဆောင်ရန်",
                                    isGranted = isAccessibilityActive,
                                    onGrantClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Battery Optimization Exemption Item (Android 14 FGS Protection)
                                StatusPermissionRow(
                                    title = "Battery Optimization (ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်)",
                                    subtitle = "Android 14 စခရင်ပိတ်ချိန် နောက်ခံအသံဖမ်း Process အသတ်မခံရစေရန်",
                                    isGranted = isBatteryExempt,
                                    onGrantClick = {
                                        showBatteryOptimizationDialog = true
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Toggle Floating Service Button
                                Button(
                                    onClick = {
                                        val intent = Intent(context, KyawGyiFloatingService::class.java)
                                        if (isFloatingRunning) {
                                            context.stopService(intent)
                                            isFloatingRunning = false
                                        } else {
                                            if (!hasAudioPermission) {
                                                showPermissionExplanation = true
                                            } else if (!hasOverlayPermission) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intentOverlay = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intentOverlay)
                                                }
                                            } else {
                                                if (!isBatteryExempt) {
                                                    showBatteryOptimizationDialog = true
                                                }
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(intent)
                                                } else {
                                                    context.startService(intent)
                                                }
                                                isFloatingRunning = true
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isFloatingRunning) Color.Red.copy(alpha = 0.8f) else AngelNeonGreen
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("toggle_floating_service_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isFloatingRunning) Icons.Filled.Stop else Icons.Filled.PowerSettingsNew,
                                        contentDescription = "Toggle Service",
                                        tint = if (isFloatingRunning) Color.White else AngelDarkBackground
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isFloatingRunning) "Floating Service ပိတ်မည်" else "Assistive Floating Mic စတင်မည်",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFloatingRunning) Color.White else AngelDarkBackground
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Voice Command Tester
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                            color = AngelCardDark
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "၂။ မြန်မာအသံအမိန့် စမ်းသပ်စနစ် (Voice Tester)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelCyanAccent
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = testCommandText,
                                    onValueChange = { testCommandText = it },
                                    label = { Text("စမ်းသပ်လိုသော မြန်မာအမိန့် ရိုက်ထည့်ပါ") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AngelNeonGreen,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedLabelColor = AngelNeonGreen
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val parsed = MyanmarCommands.parseCommand(testCommandText, customShortcuts)
                                        val res = KyawGyiAccessibilityService.instance?.executeVoiceCommand(testCommandText)
                                            ?: "Command parsed: $parsed"

                                        lastExecutionResult = res
                                        onLogCommand(testCommandText, parsed.first.name, "SUCCESS", res)

                                        // Trigger haptic and success animation
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        successCommandMessage = testCommandText
                                        showSuccessAnimation = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Execute",
                                        tint = AngelDarkBackground
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("အမိန့် စမ်းသပ်မည်", color = AngelDarkBackground, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val quickPresets = listOf(
                                    "ကျော်ကြီး",
                                    "ပင်မမျက်နှာပြင်",
                                    "နောက်သို့",
                                    "မကြာသေးမီက",
                                    "ဖေ့ဘုတ် ဖွင့်ပါ"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    quickPresets.take(4).forEach { preset ->
                                        SuggestionChip(
                                            onClick = { testCommandText = preset },
                                            label = { Text(preset, fontSize = 11.sp, color = AngelNeonGreen) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = AngelNeonGreen.copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: STT, TTS & Microphone Audio Engine Card
                    item {
                        MyanmarVoiceTripleEngineCard()
                    }

                    // Section 4: Master System Prompt
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, AngelNeonGreen.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                            color = AngelCardDark
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "၄။ ကျော်ကြီး Master Prompt & System Architecture",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelNeonGreen
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    color = Color.Black.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "[KYAW GYI AI MASTER ENGINE v3.0]\n" +
                                                "• Offline-first Room Database Logs & Command History\n" +
                                                "• Dynamic User Voice Shortcuts Manager\n" +
                                                "• Custom Wake Word and Adjustable Pitch & Cadence\n" +
                                                "• Assistive Touch Gesture Drag & Swipe Controls",
                                        fontSize = 11.sp,
                                        color = AngelCyanAccent,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Command Logs Tab
                CommandHistoryView(
                    logs = commandLogs,
                    onDeleteLog = onDeleteCommandLog,
                    onClearAllLogs = onClearCommandLogs
                )
            }

            2 -> {
                // Custom Shortcuts Tab
                CustomShortcutsView(
                    shortcuts = customShortcuts,
                    onAddShortcut = onAddShortcut,
                    onToggleShortcut = onToggleShortcut,
                    onDeleteShortcut = onDeleteShortcut
                )
            }

            3 -> {
                // Conversation Logs & Timeline
                ConversationLogsView(
                    messages = last50Messages,
                    onReplayAudio = onReplayAudio,
                    onDeleteMessage = onDeleteMessage
                )
            }
        }
    }

    if (showPermissionExplanation) {
        PermissionOverlayDialog(
            onGrantClick = {
                showPermissionExplanation = false
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { showPermissionExplanation = false }
        )
    }

    if (showBatteryOptimizationDialog) {
        BatteryOptimizationExemptionDialog(
            onDismiss = { showBatteryOptimizationDialog = false },
            onExemptionRequested = {
                isBatteryExempt = KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
            }
        )
    }
}

@Composable
private fun StatusPermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (isGranted) AngelNeonGreen else Color.Yellow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = AngelTextSecondary
            )
        }

        if (!isGranted) {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("ခွင့်ပြုမည်", fontSize = 11.sp, color = AngelDarkBackground, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AngelNeonGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "ခွင့်ပြုပြီး ✓",
                    fontSize = 11.sp,
                    color = AngelNeonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CommandGuideItem(category: String, phrase: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AngelCyanAccent)
            Text(text = phrase, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AngelNeonGreen)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = description, fontSize = 11.sp, color = AngelTextSecondary)
    }
}

@Composable
fun MyanmarVoiceTripleEngineCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val orchestrator = remember { com.example.utils.MyanmarVoiceSystemOrchestrator.getInstance(context) }

    val dspState by orchestrator.dsp.analysisState.collectAsState()
    val dspFreqData by orchestrator.dsp.frequencyData.collectAsState()

    var isMicActive by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("မြန်မာအသံ စကားပြောဆိုပါ...") }
    var matchedIntentText by remember { mutableStateOf("အဆင့်သင့်ဖြစ်ပါသည် (>80% Accuracy)") }

    var ttsTextToSpeak by remember { mutableStateOf("ဗျာ... ကျော်ကြီး အသင့်ရှိပါတယ်ခင်ဗျာ") }
    var ttsSpeed by remember { mutableFloatStateOf(0.9f) }
    var ttsPitch by remember { mutableFloatStateOf(1.0f) }
    var isTtsMasterEnabled by remember { mutableStateOf(com.example.utils.MyanmarTTSEngine.masterTtsEnabled) }

    LaunchedEffect(Unit) {
        val sampleCommands = listOf(
            com.example.utils.MyanmarSTEngine.RegisteredCommand("cmd_wake", "ကျော်ကြီး"),
            com.example.utils.MyanmarSTEngine.RegisteredCommand("cmd_home", "ပင်မမျက်နှာပြင်"),
            com.example.utils.MyanmarSTEngine.RegisteredCommand("cmd_back", "နောက်သို့"),
            com.example.utils.MyanmarSTEngine.RegisteredCommand("cmd_recents", "မကြာသေးမီက"),
            com.example.utils.MyanmarSTEngine.RegisteredCommand("cmd_facebook", "ဖေ့ဘွတ်ခ် ဖွင့်ပါ")
        )
        orchestrator.registerCommands(sampleCommands)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, AngelCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "၃။ မြန်မာစကားအသံ စနစ် (STT + TTS + Mic DSP)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelCyanAccent
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMicActive) AngelNeonGreen.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isMicActive) AngelNeonGreen else Color.Gray)
                ) {
                    Text(
                        text = if (isMicActive) "MIC ACTIVE" else "MIC OFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMicActive) AngelNeonGreen else Color.LightGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pillar 1: Microphone & Audio DSP Canvas Spectrum
            Text(
                text = "၁။ မိုက်ခရိုဖုန်း အချက်ပြနှင့် VAD (Audio DSP Spectrum):",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "အသံအနိမ့်အမြင့်: ${String.format("%.1f", dspState.decibels)} dB",
                    fontSize = 11.sp,
                    color = AngelTextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (dspState.isSpeechDetected) AngelNeonGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (dspState.isSpeechDetected) "🎙️ Speech Detected" else "🔇 Silent / Noise",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dspState.isSpeechDetected) AngelNeonGreen else Color.Gray,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 16-bar Animated Spectrum Visualizer Canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val byteData = dspFreqData
                for (i in 0 until 16) {
                    val rawVal = if (byteData.size > i * 4) (byteData[i * 4].toInt() and 0xFF) else 10
                    val normalizedHeight = (rawVal / 255f).coerceIn(0.1f, 1.0f)
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight(normalizedHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (dspState.isSpeechDetected) AngelNeonGreen else AngelCyanAccent.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (isMicActive) {
                        orchestrator.stopSystem()
                        isMicActive = false
                    } else {
                        orchestrator.startSystem(
                            scope = coroutineScope,
                            onTranscriptUI = { result, _ ->
                                recognizedText = result.clean
                            },
                            onIntentMatch = { match ->
                                matchedIntentText = "Matched: ${match.command.phrase} (${match.score}% accuracy)"
                            },
                            onError = { err ->
                                matchedIntentText = "Error: $err"
                            }
                        )
                        isMicActive = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMicActive) Color.Red.copy(alpha = 0.8f) else AngelNeonGreen
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Mic",
                    tint = if (isMicActive) Color.White else AngelDarkBackground
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMicActive) "မိုက်ခရိုဖုန်း အသံဖမ်း ရပ်မည်" else "မိုက်ခရိုဖုန်း စမ်းသပ်နားထောင်မည်",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMicActive) Color.White else AngelDarkBackground
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pillar 2: STT & Intent Matching
            Text(
                text = "၂။ Speech-To-Text (STT) နှင့် ဖုန်းတစ်ပါတည်းအမိန့် နားလည်နိုင်စွမ်း:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(text = "ဖမ်းယူရရှိသော အသံစာသား:", fontSize = 10.sp, color = AngelTextSecondary)
                    Text(text = recognizedText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AngelNeonGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "အမိန့်ကိုက်ညီမှု:", fontSize = 10.sp, color = AngelTextSecondary)
                    Text(text = matchedIntentText, fontSize = 12.sp, color = AngelCyanAccent)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pillar 3: TTS (Text-To-Speech) Master Switch & Engine Controls
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isTtsMasterEnabled) AngelNeonGreen.copy(alpha = 0.12f) else Color.Red.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isTtsMasterEnabled) AngelNeonGreen.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isTtsMasterEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "TTS Status",
                                tint = if (isTtsMasterEnabled) AngelNeonGreen else Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TTS (စာသားမှ စကားပြော) ခလုတ်",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isTtsMasterEnabled)
                                "ဖွင့်ထားသည် - အသံပြန်လည်ပြောကြားမည်"
                            else
                                "ပိတ်ထားသည် - အသံထွက်ဆိုခြင်း ရပ်ဆိုင်းထားသည်",
                            fontSize = 11.sp,
                            color = if (isTtsMasterEnabled) AngelNeonGreen else Color(0xFFFF8080)
                        )
                    }

                    Switch(
                        checked = isTtsMasterEnabled,
                        onCheckedChange = { enabled ->
                            isTtsMasterEnabled = enabled
                            com.example.utils.MyanmarTTSEngine.masterTtsEnabled = enabled
                            orchestrator.tts.isEnabled = enabled
                            if (!enabled) {
                                orchestrator.tts.stop()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AngelDarkBackground,
                            checkedTrackColor = AngelNeonGreen,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("tts_master_toggle_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isTtsMasterEnabled) {
                OutlinedTextField(
                    value = ttsTextToSpeak,
                    onValueChange = { ttsTextToSpeak = it },
                    label = { Text("ထွက်ဆိုမည့် မြန်မာစာသား") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AngelCyanAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = AngelCyanAccent
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "အသံနှုန်း (Speed): ${String.format("%.2f", ttsSpeed)}x", fontSize = 11.sp, color = AngelTextSecondary)
                    Slider(
                        value = ttsSpeed,
                        onValueChange = { ttsSpeed = it },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.width(180.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "အသံအနိမ့်အမြင့် (Pitch): ${String.format("%.2f", ttsPitch)}x", fontSize = 11.sp, color = AngelTextSecondary)
                    Slider(
                        value = ttsPitch,
                        onValueChange = { ttsPitch = it },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.width(180.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        orchestrator.tts.rate = ttsSpeed
                        orchestrator.tts.pitch = ttsPitch
                        orchestrator.respond(ttsTextToSpeak)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("tts_speak_test_button")
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Speak", tint = AngelDarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("စမ်းသပ်အသံထွက် နားထောင်မည် (TTS Speak)", fontSize = 12.sp, color = AngelDarkBackground, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "ℹ️ TTS (စာသားမှ စကားပြော) ကို ပိတ်ထားပါသဖြင့် အသံထွက်ဆိုခြင်း မရှိပါ။ လိုအပ်ပါက အထက်ပါခလုတ်ဖြင့် ပြန်ဖွင့်နိုင်ပါသည်။",
                        fontSize = 11.sp,
                        color = AngelTextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
