package com.example.googlehome.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.googlehome.GoogleHomeManager
import com.example.googlehome.auth.GoogleHomeAuthManager
import com.example.googlehome.data.SmartHomeDeviceEntity
import com.example.googlehome.service.ExecutionTier
import com.example.googlehome.service.GoogleHomeDeviceService
import com.example.googlehome.service.SmartHomeExecutionState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Full-featured Google Home Smart Management Screen with:
 * 1. Direct Connection & Intent Router (Google Home App Launch, Google Assistant Voice Bridge)
 * 2. Enterprise-Grade Error Handling & Fallback Strategy with Latency SLA (<500ms) StateFlow Monitoring
 * 3. Google Account OAuth 2.0 Linkage & Scopes management settings section
 * 4. Assistive Touch Floating quick toggles
 * 5. Discovered Room Database Devices listing and dimming/power control
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleHomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authManager = remember { GoogleHomeAuthManager(context) }
    val deviceService = remember { GoogleHomeDeviceService(context) }
    val homeManager = remember { GoogleHomeManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val accountProfile by authManager.accountProfile.collectAsState()
    val devices by deviceService.devicesFlow.collectAsState(initial = emptyList())
    val executionState by deviceService.executionState.collectAsState()
    val lastLatency by deviceService.executionManager.lastLatencyMs.collectAsState()
    val totalExecutions by deviceService.executionManager.totalExecutions.collectAsState()
    val fallbackCount by deviceService.executionManager.fallbackCount.collectAsState()

    var showOAuthDialog by remember { mutableStateOf(false) }
    var inputEmail by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }
    var showAssistiveTouchMenu by remember { mutableStateOf(false) }
    var customAssistantCommand by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        deviceService.syncDiscoveredDevices()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_home_screen"),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Google Home တိုက်ရိုက်ချိတ်ဆက်မှု",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Direct Bridge • SLA <500ms Fallback • Room DB",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAssistiveTouchMenu = !showAssistiveTouchMenu },
                        modifier = Modifier.testTag("toggle_assistive_touch_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = "Assistive Touch",
                            tint = if (showAssistiveTouchMenu) AngelNeonGreen else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Live Latency & Execution StateFlow Monitor Banner
                item {
                    SmartHomeExecutionStateBanner(
                        state = executionState,
                        lastLatencyMs = lastLatency,
                        totalExecutions = totalExecutions,
                        fallbackCount = fallbackCount,
                        onResetState = { deviceService.executionManager.resetState() }
                    )
                }

                // 2. Google Account Link / OAuth 2.0 Settings Section
                item {
                    GoogleAccountSettingsCard(
                        isLinked = accountProfile.isLinked,
                        userEmail = accountProfile.email,
                        displayName = accountProfile.displayName,
                        onLinkClick = {
                            inputEmail = accountProfile.email.ifBlank { "loveandtruth049@gmail.com" }
                            inputName = accountProfile.displayName.ifBlank { "Smart Home Owner" }
                            showOAuthDialog = true
                        },
                        onUnlinkClick = {
                            coroutineScope.launch {
                                authManager.unlinkGoogleAccount()
                                Toast.makeText(context, "Google Account ချိတ်ဆက်မှု ပယ်ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // 3. Direct Google Home & Assistant Bridge Action Panel
                item {
                    DirectGoogleHomeBridgeCard(
                        onOpenGoogleHomeApp = {
                            val result = homeManager.openGoogleHomeApp()
                            Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
                        },
                        customCommand = customAssistantCommand,
                        onCustomCommandChange = { customAssistantCommand = it },
                        onSendAssistantCommand = { cmd ->
                            if (cmd.isNotBlank()) {
                                val result = homeManager.sendCommandToGoogleAssistant(cmd.trim())
                                Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
                                customAssistantCommand = ""
                            }
                        }
                    )
                }

                // 4. Assistive Touch Floating Menu Widget
                if (showAssistiveTouchMenu) {
                    item {
                        GoogleHomeAssistiveTouchMenu(
                            onDismissRequest = { showAssistiveTouchMenu = false }
                        )
                    }
                }

                // 5. Discovered Devices Header with Sync button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ရရှိနိုင်သော Google Home စက်ပစ္စည်းများ (${devices.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Optimistic UI + Auto Rollback on Failure",
                                fontSize = 11.sp,
                                color = AngelNeonGreen
                            )
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val synced = deviceService.syncDiscoveredDevices()
                                    Toast.makeText(context, "စက်ပစ္စည်း ${synced.size} ခု ပြန်လည်ရှာဖွေပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("refresh_devices_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sync Devices",
                                tint = AngelCyanAccent
                            )
                        }
                    }
                }

                // 6. Smart Home Device List Items with Dimming/Power toggles
                items(items = devices, key = { it.deviceId }) { dev ->
                    SmartDeviceRepositoryCard(
                        device = dev,
                        onToggle = { turnOn ->
                            coroutineScope.launch {
                                val res = deviceService.togglePower(dev.deviceId, turnOn)
                                Toast.makeText(context, res.speechFeedback, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBrightnessChange = { newBrightness ->
                            coroutineScope.launch {
                                deviceService.setBrightness(dev.deviceId, newBrightness)
                            }
                        },
                        onTemperatureChange = { newTemp ->
                            coroutineScope.launch {
                                deviceService.setTemperature(dev.deviceId, newTemp)
                            }
                        }
                    )
                }
            }

            // OAuth 2.0 Account Link Dialog
            if (showOAuthDialog) {
                AlertDialog(
                    onDismissRequest = { showOAuthDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = AngelCyanAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Account OAuth 2.0 ချိတ်ဆက်ရန်", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Google HomeGraph & Assistant SDK API အသုံးပြုရန် အကောင့်အချက်အလက်များကို ခွင့်ပြုပေးပါ-",
                                fontSize = 13.sp,
                                color = AngelTextSecondary
                            )

                            OutlinedTextField(
                                value = inputEmail,
                                onValueChange = { inputEmail = it },
                                label = { Text("Google Email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("oauth_email_input")
                            )

                            OutlinedTextField(
                                value = inputName,
                                onValueChange = { inputName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("oauth_name_input")
                            )

                            OutlinedTextField(
                                value = inputToken,
                                onValueChange = { inputToken = it },
                                label = { Text("Google Cloud / HomeGraph Access Token (Optional)") },
                                placeholder = { Text("ya29... (Leave empty for authenticated device session)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("oauth_token_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("တောင်းဆိုထားသော OAuth Scopes:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AngelNeonGreen)
                                    Text("• https://www.googleapis.com/auth/homegraph", fontSize = 10.sp, color = AngelTextSecondary)
                                    Text("• https://www.googleapis.com/auth/assistant-sdk", fontSize = 10.sp, color = AngelTextSecondary)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val success = authManager.linkGoogleAccount(
                                        email = inputEmail,
                                        displayName = inputName,
                                        accessToken = inputToken
                                    )
                                    if (success) {
                                        Toast.makeText(context, "Google Account အောင်မြင်စွာ ချိတ်ဆက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                                        showOAuthDialog = false
                                    } else {
                                        Toast.makeText(context, "အီးမေးလ် လိပ်စာ မမှန်ကန်ပါ သို့မဟုတ် ချိတ်ဆက်မှု မအောင်မြင်ပါ", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AngelNeonGreen),
                            modifier = Modifier.testTag("oauth_confirm_btn")
                        ) {
                            Text("ချိတ်ဆက်မည် (Link)", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOAuthDialog = false }) {
                            Text("ပယ်ဖျက်မည်", color = AngelTextSecondary)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Real-Time Latency & Execution StateFlow Monitor Banner
 */
@Composable
fun SmartHomeExecutionStateBanner(
    state: SmartHomeExecutionState,
    lastLatencyMs: Long,
    totalExecutions: Int,
    fallbackCount: Int,
    onResetState: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("execution_stateflow_banner"),
        colors = CardDefaults.cardColors(containerColor = AngelCardDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with SLA & Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AngelNeonGreen.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Execution SLA",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Home Execution SLA & Fallback",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Latency Threshold: <500ms • Auto Fallback",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                // Latency Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (lastLatencyMs in 1..500) AngelNeonGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (lastLatencyMs > 0) "⚡ ${lastLatencyMs}ms" else "Idle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lastLatencyMs in 1..500) AngelNeonGreen else Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Execution StateFlow Active Visualizer
            when (state) {
                is SmartHomeExecutionState.Idle -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircleOutline,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "အဆင်သင့်ရှိသည် (Direct Matter & Google API Active)",
                            fontSize = 12.sp,
                            color = AngelTextSecondary
                        )
                    }
                }
                is SmartHomeExecutionState.Executing -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AngelCyanAccent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AngelCyanAccent,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "လုပ်ဆောင်နေသည်: ${state.deviceName} (${state.tier.displayName})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = state.tier.descriptionMM,
                                fontSize = 10.sp,
                                color = AngelCyanAccent
                            )
                        }
                    }
                }
                is SmartHomeExecutionState.Success -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AngelNeonGreen.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "အောင်မြင်ပါသည် (${state.route} • ${state.latencyMs}ms)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelNeonGreen
                            )
                            Text(
                                text = state.speechFeedback,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                is SmartHomeExecutionState.FallbackTriggered -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFA000).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Fallback သို့ လွှဲပြောင်းခဲ့သည် (${state.fallbackRoute})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFA000)
                            )
                            Text(
                                text = "${state.reasonMM} • ${state.speechFeedback}",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
                is SmartHomeExecutionState.Failed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "လုပ်ဆောင်မှု မအောင်မြင်ပါ (Optimistic UI Rollback)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red
                            )
                            Text(
                                text = state.errorReasonMM,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metrics footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Commands: $totalExecutions",
                    fontSize = 11.sp,
                    color = AngelTextSecondary
                )
                Text(
                    text = "Fallback Triggered: $fallbackCount",
                    fontSize = 11.sp,
                    color = if (fallbackCount > 0) Color(0xFFFFA000) else AngelTextSecondary
                )
            }
        }
    }
}

/**
 * Direct Google Home & Google Assistant Bridge Action Card
 */
@Composable
fun DirectGoogleHomeBridgeCard(
    onOpenGoogleHomeApp: () -> Unit,
    customCommand: String,
    onCustomCommandChange: (String) -> Unit,
    onSendAssistantCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("direct_google_home_bridge_card"),
        colors = CardDefaults.cardColors(containerColor = AngelCardDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AngelCyanAccent.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Home,
                                contentDescription = "Direct Google Home",
                                tint = AngelCyanAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "တိုက်ရိုက် အမိန့်ပေးပို့လမ်းကြောင်း",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Direct Google Home & Assistant Bridge",
                            fontSize = 11.sp,
                            color = AngelNeonGreen
                        )
                    }
                }

                Button(
                    onClick = onOpenGoogleHomeApp,
                    colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_google_home_app_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("App ဖွင့်", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Voice Command Dispatch Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customCommand,
                    onValueChange = onCustomCommandChange,
                    placeholder = {
                        Text("Google Assistant သို့ အမိန့်ပို့ရန် (ဥပမာ- Turn off lights)...", fontSize = 12.sp, color = AngelTextSecondary)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_command_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AngelNeonGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                IconButton(
                    onClick = { onSendAssistantCommand(customCommand) },
                    modifier = Modifier
                        .background(AngelNeonGreen, RoundedCornerShape(12.dp))
                        .size(48.dp)
                        .testTag("send_assistant_cmd_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Settings Card for Google Account OAuth 2.0 linkage
 */
@Composable
fun GoogleAccountSettingsCard(
    isLinked: Boolean,
    userEmail: String,
    displayName: String,
    onLinkClick: () -> Unit,
    onUnlinkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_account_settings_card"),
        colors = CardDefaults.cardColors(containerColor = AngelCardDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (isLinked) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isLinked) Icons.Filled.CheckCircle else Icons.Filled.AccountCircle,
                                contentDescription = "Google Account",
                                tint = if (isLinked) AngelNeonGreen else AngelCyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isLinked) displayName else "Google အကောင့် ချိတ်ဆက်ရန်",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isLinked) userEmail else "Google HomeGraph API & Assistant OAuth 2.0",
                            fontSize = 12.sp,
                            color = if (isLinked) AngelNeonGreen else AngelTextSecondary
                        )
                    }
                }

                if (isLinked) {
                    OutlinedButton(
                        onClick = onUnlinkClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.testTag("unlink_google_account_btn")
                    ) {
                        Text("Unlink", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onLinkClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                        modifier = Modifier.testTag("link_google_account_btn")
                    ) {
                        Text("ချိတ်ဆက်ရန်", fontSize = 12.sp, color = Color.Black)
                    }
                }
            }
        }
    }
}

/**
 * Smart Device Card for local Room repository representation
 */
@Composable
fun SmartDeviceRepositoryCard(
    device: SmartHomeDeviceEntity,
    onToggle: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onTemperatureChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .testTag("repo_device_card_${device.deviceId}"),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isOn) AngelCardDark else AngelSurfaceDark
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (device.isOn) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (device.deviceType) {
                            "LIGHT" -> Icons.Filled.Lightbulb
                            "AC" -> Icons.Filled.AcUnit
                            "TV" -> Icons.Filled.Tv
                            "FAN" -> Icons.Filled.WindPower
                            "SWITCH" -> Icons.Filled.Power
                            else -> Icons.Filled.Sensors
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = device.name,
                            tint = if (device.isOn) AngelNeonGreen else AngelTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${device.room} • ${device.traitType.substringAfterLast('.')}",
                        fontSize = 11.sp,
                        color = AngelTextSecondary
                    )
                }

                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AngelNeonGreen
                    ),
                    modifier = Modifier.testTag("repo_switch_${device.deviceId}")
                )
            }

            AnimatedVisibility(visible = expanded && device.isOn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (device.deviceType == "AC") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("အပူချိန် (Temperature)", fontSize = 12.sp, color = AngelTextSecondary)
                            Text("${device.temperature}°C", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AngelCyanAccent)
                        }

                        Slider(
                            value = device.temperature.toFloat(),
                            onValueChange = { onTemperatureChange(it.toInt()) },
                            valueRange = 16f..30f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = AngelCyanAccent,
                                activeTrackColor = AngelCyanAccent
                            ),
                            modifier = Modifier.testTag("repo_ac_slider_${device.deviceId}")
                        )
                    } else if (device.deviceType == "LIGHT" || device.traitType.contains("Brightness")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("အလင်းအား / မှိန်ခြင်း (Brightness / Dim)", fontSize = 12.sp, color = AngelTextSecondary)
                            Text("${device.brightness}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AngelNeonGreen)
                        }

                        Slider(
                            value = device.brightness.toFloat(),
                            onValueChange = { onBrightnessChange(it.toInt()) },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = AngelNeonGreen,
                                activeTrackColor = AngelNeonGreen
                            ),
                            modifier = Modifier.testTag("repo_light_slider_${device.deviceId}")
                        )
                    }
                }
            }
        }
    }
}
