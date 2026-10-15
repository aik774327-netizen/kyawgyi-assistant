package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RestartAlt
import com.example.realworld.FlashStorageInfo
import com.example.realworld.ImportResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiangel.assistant.data.entity.IrCommandEntity
import com.aiangel.assistant.data.entity.IrEvidenceState
import com.example.models.Esp32Status
import com.example.models.KennedeButton
import com.example.realworld.ConnectionDiagnostic
import com.example.realworld.NetworkEnvironmentInfo
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time monitoring screen displaying:
 * 1. Current connection status of the ESP32 IR bridge hardware.
 * 2. Strict verification state and evidence chain of the five KENNEDE IR physical appliance controls:
 *    POWER, LIGHT, SPEED, OSCILLATION, CENTER.
 *
 * Enforces the strict rule: ESP32 ACK != Physical Appliance Success.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32MonitoringScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    val esp32Status by viewModel.esp32Status.collectAsState()
    val discoveryState by viewModel.esp32DiscoveryEngine.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Observe IR commands from Room database
    val roomCommands by viewModel.db.irCommandDao()
        .observeCommandsForDevice("KENNEDE KN-5996HRB")
        .collectAsState(initial = emptyList())

    // 5 KENNEDE Controls specification
    val targetControls = listOf(
        KennedeControlSpec("POWER", "ပါဝါ (ဖွင့်/ပိတ်)", Icons.Default.PowerSettingsNew, KennedeButton.POWER),
        KennedeControlSpec("LIGHT", "မီးရောင် (ဖွင့်/ပိတ်)", Icons.Default.Lightbulb, KennedeButton.LIGHT),
        KennedeControlSpec("SPEED", "လေအရှိန် (အဆင့်ဆင့်)", Icons.Default.Speed, KennedeButton.SPEED),
        KennedeControlSpec("OSCILLATION", "ဘယ်/ညာ လှည့်ပတ်မှု", Icons.Default.Sync, KennedeButton.OSCILLATION),
        KennedeControlSpec("CENTER", "ဗဟိုလေညွှန် ခလုတ်", Icons.Default.CenterFocusStrong, KennedeButton.CENTER)
    )

    // Ensure initial entities exist in database as NOT_TESTED if not present
    androidx.compose.runtime.LaunchedEffect(roomCommands) {
        if (roomCommands.isEmpty()) {
            val initialEntities = targetControls.map { spec ->
                IrCommandEntity(
                    deviceName = "KENNEDE KN-5996HRB",
                    commandLabel = spec.key,
                    frequencyHz = 38000,
                    pattern = "",
                    brand = "KENNEDE",
                    isVerified = false,
                    evidenceState = IrEvidenceState.NOT_TESTED.stateKey
                )
            }
            viewModel.db.irCommandDao().insertAll(initialEntities)
        }
    }

    val flashStorageInfo by viewModel.flashStorageInfo.collectAsState()
    val lastImportResult by viewModel.lastImportResult.collectAsState()
    val isFlashOperating by viewModel.isFlashOperating.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var selectedControlForWizard by remember { mutableStateOf<KennedeControlSpec?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var networkEnv by remember { mutableStateOf(viewModel.esp32DiscoveryEngine.getNetworkEnvironment()) }
    var customHostInput by remember { mutableStateOf(viewModel.esp32Client.getHost()) }
    var diagnosticResult by remember { mutableStateOf<ConnectionDiagnostic?>(null) }
    var isDiagnosing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshFlashStatus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0F1D),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ESP32 & IR ထိန်းချုပ်မှု စောင့်ကြည့်စနစ်",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Real-Time ESP32 Connection & 5-Control Verification",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("esp32_monitor_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetConfirmDialog = true },
                        modifier = Modifier.testTag("esp32_monitor_reset_all_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset All to NOT_TESTED",
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    IconButton(
                        onClick = {
                            isRefreshing = true
                            networkEnv = viewModel.esp32DiscoveryEngine.getNetworkEnvironment()
                            viewModel.refreshEsp32Status()
                            viewModel.refreshFlashStatus()
                            viewModel.esp32DiscoveryEngine.startSubnetPingScan { _ ->
                                viewModel.refreshEsp32Status()
                            }
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(1200)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.testTag("esp32_monitor_refresh_btn")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Section 1: ESP32 Real-Time Connection Status Card
            item {
                Esp32ConnectionStatusCard(
                    status = esp32Status,
                    isScanning = discoveryState.isScanning,
                    suggestedIp = discoveryState.suggestedFallbackIp ?: "",
                    onTriggerPing = {
                        viewModel.refreshEsp32Status()
                    }
                )
            }

            // Section 2: Network Environment & Subnet Detection
            item {
                NetworkEnvironmentCard(networkEnv = networkEnv)
            }

            // Section 3: Manual Host & Connection Diagnostics
            item {
                ManualHostAndDiagnosticsCard(
                    currentHost = customHostInput,
                    onHostChange = { customHostInput = it },
                    isDiagnosing = isDiagnosing,
                    diagnosticResult = diagnosticResult,
                    onTestPing = {
                        isDiagnosing = true
                        coroutineScope.launch {
                            diagnosticResult = viewModel.esp32Client.diagnoseConnection(customHostInput)
                            isDiagnosing = false
                        }
                    },
                    onSaveHost = {
                        viewModel.setEsp32Host(customHostInput)
                        actionFeedbackMessage = "ESP32 Host ကို $customHostInput သို့ ပြောင်းလဲသတ်မှတ်လိုက်ပါသည်"
                    }
                )
            }

            // Section 4: ESP32 Flash Memory & Dynamic Code Storage
            item {
                FlashMemoryStorageCard(
                    flashInfo = flashStorageInfo,
                    importResult = lastImportResult,
                    isOperating = isFlashOperating,
                    onRefreshStatus = { viewModel.refreshFlashStatus() },
                    onSyncToFlash = { viewModel.syncRoomCodesToEsp32Flash() },
                    onImportFromFlash = { viewModel.importEsp32FlashCodesToRoom() },
                    onClearFlash = { viewModel.clearEsp32FlashStorage() }
                )
            }

            // Section 5: Strict Evidence Chain Discipline Banner
            item {
                StrictDisciplineCard()
            }

            // Section 3: Section Title for the 5 KENNEDE Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "KENNEDE KN-5996HRB ရုပ်ပိုင်းထိန်းချုပ်မှု (၅) ခု",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                    val verifiedCount = roomCommands.count { it.isVerified || it.evidenceState == "APPLIANCE_VERIFIED" }
                    Surface(
                        color = if (verifiedCount == 5) Color(0x2210B981) else Color(0x22EF4444),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (verifiedCount == 5) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        Text(
                            text = "$verifiedCount / 5 VERIFIED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (verifiedCount == 5) Color(0xFF34D399) else Color(0xFFF87171)
                        )
                    }
                }
            }

            // Section 4: Real-time Cards for each of the 5 Controls
            items(targetControls) { spec ->
                val entity = roomCommands.firstOrNull { it.commandLabel.equals(spec.key, ignoreCase = true) }
                KennedeControlVerificationCard(
                    spec = spec,
                    entity = entity,
                    isEsp32Connected = esp32Status.esp32,
                    onTestTransmit = {
                        viewModel.sendKennedeIrButton(spec.button)
                        coroutineScope.launch {
                            val now = System.currentTimeMillis()
                            val nextState = if (esp32Status.esp32) "ACKNOWLEDGED" else "TRANSMITTING"
                            viewModel.db.irCommandDao().updateLifecycleState(
                                deviceName = "KENNEDE KN-5996HRB",
                                label = spec.key,
                                state = nextState,
                                updatedAt = now
                            )
                            actionFeedbackMessage = "${spec.label} ထုတ်လွှင့်ပြီးစီး: အခြေအနေ -> $nextState"
                        }
                    },
                    onOpenWizard = {
                        selectedControlForWizard = spec
                    }
                )
            }

            // Action Feedback Notification
            item {
                AnimatedVisibility(visible = actionFeedbackMessage != null) {
                    actionFeedbackMessage?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Physical Verification Wizard Dialog
    selectedControlForWizard?.let { spec ->
        val entity = roomCommands.firstOrNull { it.commandLabel.equals(spec.key, ignoreCase = true) }
        PhysicalVerificationWizardDialog(
            spec = spec,
            entity = entity,
            isEsp32Connected = esp32Status.esp32,
            onDismiss = { selectedControlForWizard = null },
            onSaveSuccess = { details ->
                coroutineScope.launch {
                    val now = System.currentTimeMillis()
                    viewModel.db.irCommandDao().recordVerificationResult(
                        deviceName = "KENNEDE KN-5996HRB",
                        label = spec.key,
                        state = IrEvidenceState.APPLIANCE_VERIFIED.stateKey,
                        isVerified = true,
                        ackAt = if (esp32Status.esp32) now else null,
                        optAt = now,
                        appAt = now,
                        details = details,
                        error = null,
                        updatedAt = now
                    )
                    selectedControlForWizard = null
                    actionFeedbackMessage = "${spec.label} ရုပ်ပိုင်းဆိုင်ရာ စစ်ဆေးအတည်ပြုခြင်း အောင်မြင်ပါသည် (VERIFIED)"
                }
            },
            onRecordFailure = { category, reason ->
                coroutineScope.launch {
                    val now = System.currentTimeMillis()
                    val errorString = "[$category] $reason"
                    viewModel.db.irCommandDao().recordVerificationResult(
                        deviceName = "KENNEDE KN-5996HRB",
                        label = spec.key,
                        state = IrEvidenceState.NOT_TESTED.stateKey,
                        isVerified = false,
                        ackAt = null,
                        optAt = null,
                        appAt = null,
                        details = null,
                        error = errorString,
                        updatedAt = now
                    )
                    selectedControlForWizard = null
                    actionFeedbackMessage = "သတိပေးချက်: ${spec.label} စစ်ဆေးမှု မအောင်မြင်ကြောင်း မှတ်တမ်းတင်ပြီးပါပြီ"
                }
            }
        )
    }

    // Reset All Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(text = "စစ်ဆေးမှု မှတ်တမ်းများ အစသို့ ပြန်ထားမလား?", color = Color.White) },
            text = {
                Text(
                    text = "ခလုတ် (၅) ခုလုံး၏ စစ်ဆေးအတည်ပြုမှု အခြေအနေများကို NOT_TESTED သို့ ပြန်လည်ပြောင်းလဲပြီး Error Log များကို ရှင်းလင်းပါမည်။ အတုအယောင်ဒေတာများ မပါဝင်စေရန် စည်းမျဉ်းအရ ပြန်လည်စတင်ခြင်း ဖြစ်ပါသည်။",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val now = System.currentTimeMillis()
                            targetControls.forEach { spec ->
                                viewModel.db.irCommandDao().recordVerificationResult(
                                    deviceName = "KENNEDE KN-5996HRB",
                                    label = spec.key,
                                    state = IrEvidenceState.NOT_TESTED.stateKey,
                                    isVerified = false,
                                    ackAt = null,
                                    optAt = null,
                                    appAt = null,
                                    details = null,
                                    error = null,
                                    updatedAt = now
                                )
                            }
                            showResetConfirmDialog = false
                            actionFeedbackMessage = "ခလုတ် (၅) ခုလုံးကို NOT_TESTED သို့ ပြန်လည်သတ်မှတ်ပြီးပါပြီ"
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("ပြန်လည်သတ်မှတ်မည် (Reset)")
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showResetConfirmDialog = false }) {
                    Text("မလုပ်တော့ပါ")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

private data class KennedeControlSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val button: KennedeButton
)

@Composable
private fun Esp32ConnectionStatusCard(
    status: Esp32Status,
    isScanning: Boolean,
    suggestedIp: String,
    onTriggerPing: () -> Unit
) {
    val isOnline = status.esp32
    val statusColor = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444)
    val statusBg = if (isOnline) Color(0x1510B981) else Color(0x15EF4444)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("esp32_connection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
        border = BorderStroke(1.dp, if (isOnline) Color(0x4410B981) else Color(0x3364748B))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) Color(0xFFF59E0B) else statusColor)
                            .alpha(if (isScanning || isOnline) pulseAlpha else 1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOnline) "ESP32 ချိတ်ဆက်ထားသည် (ONLINE)" else if (isScanning) "Subnet စကင်ဖတ်နေဆဲ..." else "ESP32 မချိတ်ဆက်ရသေးပါ (OFFLINE)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isScanning) Color(0xFFF59E0B) else statusColor
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "KENNEDE KN-5996HRB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Technical details grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HardwareDetailPill("IP Address", if (status.ip.isNotEmpty()) status.ip else suggestedIp.ifEmpty { "Not Assigned" }, Icons.Default.Router)
                HardwareDetailPill("Signal (RSSI)", if (status.rssi != 0) "${status.rssi} dBm" else "N/A", Icons.Default.Wifi)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HardwareDetailPill("IR Transmitter", if (status.transmitter) "GPIO 4 (ACTIVE)" else "READY", Icons.Default.Send)
                HardwareDetailPill("IR Receiver", if (status.receiver) "GPIO 15 (ACTIVE)" else "READY", Icons.Default.Air)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HardwareDetailPill("Carrier Freq", "${status.carrierFrequency} Hz (38 kHz)", Icons.Default.Security)
                HardwareDetailPill("Learning Mode", if (status.learning) "ACTIVE (${status.learningTarget})" else "IDLE", Icons.Default.Sync)
            }
        }
    }
}

@Composable
private fun HardwareDetailPill(label: String, value: String, icon: ImageVector) {
    Surface(
        modifier = Modifier.width(165.dp),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
                Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun StrictDisciplineCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1528)),
        border = BorderStroke(1.dp, Color(0x55A855F7))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "စစ်မှန်သော အဆင့်ဆင့် စစ်ဆေးခြင်း စည်းမျဉ်း (Discipline)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE9D5FF)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ESP32 ACK != Physical Appliance Success. အတုအယောင်/ဟန်ပြ အချက်အလက်များ လုံးဝ လက်မခံပါ။ စက်ပစ္စည်းအစစ် မချိတ်ဆက်ရသေးပါက NOT_TESTED သာ ဖြစ်ရမည်။",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFFD8B4FE)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Chain: NOT_TESTED ➔ LEARNING ➔ LEARNED ➔ TRANSMITTING ➔ ACKNOWLEDGED ➔ OPTICAL_VERIFIED ➔ APPLIANCE_VERIFIED",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFA855F7)
            )
        }
    }
}

@Composable
private fun KennedeControlVerificationCard(
    spec: KennedeControlSpec,
    entity: IrCommandEntity?,
    isEsp32Connected: Boolean,
    onTestTransmit: () -> Unit,
    onOpenWizard: () -> Unit
) {
    val currentStateKey = entity?.evidenceState ?: IrEvidenceState.NOT_TESTED.stateKey
    val evidenceState = IrEvidenceState.fromKey(currentStateKey)
    val isApplianceVerified = entity?.isVerified == true || currentStateKey == IrEvidenceState.APPLIANCE_VERIFIED.stateKey
    val stateColor = Color(evidenceState.badgeColorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kennede_control_card_${spec.key.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        border = BorderStroke(1.dp, if (isApplianceVerified) Color(0xFF10B981) else Color(0xFF1F2937))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Icon, Name, and State Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF1F2937),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = spec.icon,
                                contentDescription = spec.key,
                                tint = if (isApplianceVerified) Color(0xFF10B981) else Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = spec.key,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = spec.label,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Evidence State Badge
                Surface(
                    color = stateColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, stateColor.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = evidenceState.stateKey,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stateColor
                        )
                        Text(
                            text = evidenceState.burmeseLabel,
                            fontSize = 9.sp,
                            color = stateColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Warning Banner for Repeat Failures (Discipline Rule)
            if (!entity?.lastError.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0x33DC2626),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entity?.lastError ?: "",
                            fontSize = 10.sp,
                            color = Color(0xFFFECACA),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Signal Info / Timestamps
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Protocol: NEC | 38 kHz",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = if (isApplianceVerified) "STATUS: VERIFIED" else "STATUS: NOT VERIFIED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isApplianceVerified) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }

                    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
                    if (entity?.lastTransmittedAt != null && entity.lastTransmittedAt > 0) {
                        Text(
                            text = "Last Transmit: ${dateFmt.format(Date(entity.lastTransmittedAt))}",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    if (entity?.applianceVerifiedAt != null && entity.applianceVerifiedAt > 0) {
                        Text(
                            text = "Verified At: ${dateFmt.format(Date(entity.applianceVerifiedAt))}",
                            fontSize = 10.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                    if (!entity?.evidenceDetails.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "သက်သေမှတ်တမ်း: ${entity?.evidenceDetails}",
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Transmit, Verify Physical Wizard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestTransmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("test_transmit_${spec.key.lowercase()}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF38BDF8)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF0284C7))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "ထုတ်လွှင့်မည်", fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .testTag("verify_physical_${spec.key.lowercase()}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isApplianceVerified) Color(0x2210B981) else Color(0xFF1E293B),
                        contentColor = if (isApplianceVerified) Color(0xFF34D399) else Color(0xFFE2E8F0)
                    )
                ) {
                    Icon(
                        imageVector = if (isApplianceVerified) Icons.Default.CheckCircle else Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isApplianceVerified) "ပြန်လည်စစ်ဆေးမည်" else "ရုပ်ပိုင်း စစ်ဆေးမည်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PhysicalVerificationWizardDialog(
    spec: KennedeControlSpec,
    entity: IrCommandEntity?,
    isEsp32Connected: Boolean,
    onDismiss: () -> Unit,
    onSaveSuccess: (evidenceNote: String) -> Unit,
    onRecordFailure: (category: String, reason: String) -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Pre-check, 2: Observation, 3: Outcome
    var failureCategory by remember { mutableStateOf("လက်တွေ့မကိုက်ညီမှု") }
    var failureReason by remember { mutableStateOf("") }
    var successNote by remember { mutableStateOf("") }
    var isFailureMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${spec.key} (${spec.label}) ရုပ်ပိုင်းစစ်ဆေးမှု",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Discipline Reminder
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "စစ်ဆေးခြင်း စည်းမျဉ်း (Physical Evidence Mandate):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC084FC)
                        )
                        Text(
                            text = "ESP32 ACK != စက်ပစ္စည်း တုံ့ပြန်မှု။ ပန်ကာမှ အသံမြည်ခြင်း သို့မဟုတ် လည်ပတ်ခြင်းကို မျက်မြင်ကိုယ်တွေ့ အတည်ပြုမှသာ VERIFIED ပြုလုပ်ရမည်။",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 14.sp
                        )
                    }
                }

                if (!isFailureMode) {
                    // Success / Normal Flow
                    Text(
                        text = "အဆင့် ၁။ ESP32 Transmitter (GPIO 4) မှ IR အချက်ပြ ထုတ်လွှင့်ပြီး ပန်ကာရှေ့သို့ ချိန်ထားပါ။",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "အဆင့် ၂။ ဖုန်းကင်မရာ သို့မဟုတ် Optical Sensor ဖြင့် IR LED မီးလင်းခြင်း ရှိ/မရှိ စစ်ဆေးပါ။",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "အဆင့် ၃။ KENNEDE ပန်ကာမှ တုံ့ပြန်မှု (Beep သံ/မီးလင်း/လေတိုက်) ကို စစ်ဆေးပါ။",
                        fontSize = 12.sp,
                        color = Color(0xFFE2E8F0)
                    )

                    OutlinedTextField(
                        value = successNote,
                        onValueChange = { successNote = it },
                        label = { Text("မျက်မြင်သက်သေ မှတ်တမ်း (ဥပမာ- Beep သံကြားပြီး မီးလင်းသည်)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = false,
                        maxLines = 2
                    )
                } else {
                    // Failure Logging Mode (Error Categorization)
                    Surface(
                        color = Color(0x33EF4444),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "အမှားခွဲခြမ်းစိတ်ဖြာမှု သတ်မှတ်ချက်:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5)
                            )
                            Text(
                                text = "မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက်ကို အတုအယောင် မလုပ်ဘဲ Error Log အဖြစ် သိမ်းဆည်းပါမည်။",
                                fontSize = 10.sp,
                                color = Color(0xFFFECACA)
                            )
                        }
                    }

                    Text(
                        text = "အမှားအမျိုးအစား ရွေးချယ်ပါ-",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    val categories = listOf(
                        "လက်တွေ့မကိုက်ညီမှု" to "စက်ပစ္စည်းနှင့် အကွာအဝေးလွဲခြင်း သို့မဟုတ် IR Code မကိုက်ညီခြင်း",
                        "အရင်းအမြစ်" to "ESP32 ပါဝါမရခြင်း သို့မဟုတ် Wi-Fi / ကွန်ရက် ပြတ်တောက်ခြင်း",
                        "နည်းပညာပိုင်း" to "GPIO 4 Signal Pulse ပျက်ယွင်းခြင်း သို့မဟုတ် 38kHz Frequency လွဲခြင်း"
                    )

                    categories.forEach { (cat, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = failureCategory == cat,
                                onClick = { failureCategory = cat },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                            )
                            Column {
                                Text(text = cat, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(text = desc, fontSize = 9.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = failureReason,
                        onValueChange = { failureReason = it },
                        label = { Text("အသေးစိတ် မအောင်မြင်ရသည့် အကြောင်းရင်း") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        singleLine = false,
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            if (!isFailureMode) {
                OutlinedButton(
                    onClick = {
                        val finalNote = if (successNote.isBlank()) "ပန်ကာတုံ့ပြန်မှု မျက်မြင်စစ်ဆေးပြီးစီး" else successNote
                        onSaveSuccess(finalNote)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("စက်တုံ့ပြန်မှု အတည်ပြုသည် (VERIFIED)", fontSize = 11.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val reason = if (failureReason.isBlank()) "စက်ပစ္စည်းမှ တုံ့ပြန်မှု မရရှိပါ" else failureReason
                        onRecordFailure(failureCategory, reason)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Text("မအောင်မြင်မှု မှတ်တမ်းတင်မည်", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            if (!isFailureMode) {
                FilledTonalButton(
                    onClick = { isFailureMode = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0x33EF4444),
                        contentColor = Color(0xFFF87171)
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("တုံ့ပြန်မှု မရှိပါ (မအောင်မြင်)", fontSize = 11.sp)
                }
            } else {
                FilledTonalButton(onClick = { isFailureMode = false }) {
                    Text("နောက်သို့", fontSize = 11.sp)
                }
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
private fun NetworkEnvironmentCard(networkEnv: NetworkEnvironmentInfo) {
    val isWifi = networkEnv.isWifiConnected
    val badgeColor = if (isWifi) Color(0xFF10B981) else Color(0xFFF59E0B)
    val badgeBg = if (isWifi) Color(0x2210B981) else Color(0x22F59E0B)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("network_environment_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10192A)),
        border = BorderStroke(1.dp, if (isWifi) Color(0x3310B981) else Color(0x44F59E0B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ကွန်ရက်ပတ်ဝန်းကျင် အခြေအနေ (Network Environment)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = if (isWifi) "LOCAL WI-FI" else "CLOUD / NON-WIFI",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "စက် IP လိပ်စာ", fontSize = 9.sp, color = Color(0xFF64748B))
                        Text(
                            text = networkEnv.currentIp ?: "N/A (Cloud NAT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF0B1120),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(text = "Detected Subnet", fontSize = 9.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (networkEnv.currentSubnet != null) "${networkEnv.currentSubnet}.x" else "Virtual / Isolated",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC084FC)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isWifi) Color(0x1510B981) else Color(0x15F59E0B),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isWifi) Color(0x3310B981) else Color(0x33F59E0B))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isWifi)
                            "Local Wi-Fi သို့ ချိတ်ဆက်ထားသဖြင့် အိမ်တွင်း Subnet အတွင်းရှိ ESP32 IR Blaster သို့ တိုက်ရိုက် ဆက်သွယ်အသုံးပြုနိုင်ပါသည်။"
                        else
                            "သတိပြုရန်: Cloud Container / Sandbox Runtime အတွင်းမှ အိမ်တွင်း Subnet (192.168.x.x) သို့ NAT ကြောင့် တိုက်ရိုက် Ping မပေါက်နိုင်ပါ။ အက်ပ်ကို Android ဖုန်းပေါ်တွင် သွင်း၍ Wi-Fi တူညီစွာ ချိတ်ဆက်ပါ (သို့မဟုတ်) အောက်ဖော်ပြပါ Manual Host / Bridge URL ထည့်သွင်းစနစ်ကို အသုံးပြုပါ။",
                        fontSize = 10.sp,
                        color = if (isWifi) Color(0xFFA7F3D0) else Color(0xFFFDE68A),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualHostAndDiagnosticsCard(
    currentHost: String,
    onHostChange: (String) -> Unit,
    isDiagnosing: Boolean,
    diagnosticResult: ConnectionDiagnostic?,
    onTestPing: () -> Unit,
    onSaveHost: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_host_diagnostics_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESP32 Host & Ping စစ်ဆေးမှု (Direct Configuration)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = currentHost,
                onValueChange = onHostChange,
                label = { Text("ESP32 IP / Host Address (ဥပမာ: 192.168.1.150)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("esp32_manual_host_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Preset Chips
            Text(text = "လျင်မြန်စွာ ရွေးချယ်နိုင်သော IP များ-", fontSize = 10.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf("192.168.1.150", "192.168.43.150", "192.168.0.150", "esp32-ir.local")
                presets.forEach { preset ->
                    Surface(
                        onClick = { onHostChange(preset) },
                        color = if (currentHost == preset) Color(0x3338BDF8) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (currentHost == preset) Color(0xFF38BDF8) else Color(0xFF334155))
                    ) {
                        Text(
                            text = preset,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (currentHost == preset) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Ping Diagnostic and Save Host
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestPing,
                    enabled = !isDiagnosing && currentHost.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("esp32_diagnose_ping_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                ) {
                    if (isDiagnosing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ping စစ်ဆေးမည်", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = onSaveHost,
                    enabled = currentHost.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("esp32_save_host_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Host သတ်မှတ်မည်", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Diagnostic Result Banner
            if (diagnosticResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val diag = diagnosticResult
                val isSuccess = diag.isSuccess
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSuccess) Color(0x2210B981) else Color(0x22EF4444),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSuccess) "Ping အောင်မြင်ပါသည် (${diag.latencyMs} ms)" else "Ping မအောင်မြင်ပါ (${diag.latencyMs} ms)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuccess) Color(0xFFA7F3D0) else Color(0xFFFCA5A5)
                                )
                            }
                            if (!isSuccess && diag.errorCategory != null) {
                                Surface(
                                    color = Color(0xFF7F1D1D),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = diag.errorCategory,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFECACA),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!isSuccess && !diag.failureReason.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diag.failureReason,
                                fontSize = 10.sp,
                                color = Color(0xFFFECACA),
                                lineHeight = 14.sp
                            )
                        }

                        if (!diag.technicalDetails.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "အသေးစိတ်: ${diag.technicalDetails}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashMemoryStorageCard(
    flashInfo: FlashStorageInfo?,
    importResult: ImportResult?,
    isOperating: Boolean,
    onRefreshStatus: () -> Unit,
    onSyncToFlash: () -> Unit,
    onImportFromFlash: () -> Unit,
    onClearFlash: () -> Unit
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flash_memory_storage_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF475569))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Flash Memory",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ESP32 Flash Storage & Dynamic Code Partition",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                }

                Surface(
                    color = if (flashInfo?.isConnected == true) Color(0x2210B981) else Color(0x22EF4444),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (flashInfo?.isConnected == true) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Text(
                        text = if (flashInfo?.isConnected == true) "NVS: ${flashInfo.nvsStatus}" else "OFFLINE",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (flashInfo?.isConnected == true) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Partition & Storage Metrics Grid
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Column {
                        Text(
                            text = "PARTITION SCHEME",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = flashInfo?.partitionScheme ?: "Custom 4MB (24KB NVS, 1.6MB App0, 1.6MB App1, 704KB SPIFFS)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "FLASH SIZE / HEAP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            val flashMb = (flashInfo?.flashSizeBytes ?: 4194304L) / (1024 * 1024)
                            val freeHeapKb = (flashInfo?.freeHeap ?: 0L) / 1024
                            Text(
                                text = "${flashMb}MB Flash | Free: ${freeHeapKb}KB Heap",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SAVED IR BUTTONS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            val btnCount = flashInfo?.buttonsStored ?: 0
                            Text(
                                text = "$btnCount / 5 Buttons Saved",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (btnCount == 5) Color(0xFF34D399) else Color(0xFFFBBF24)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Sync to Flash & Pull to DB
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSyncToFlash,
                    enabled = !isOperating,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sync_to_flash_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Sync To Flash",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onImportFromFlash,
                    enabled = !isOperating,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pull_from_flash_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pull From Flash",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshStatus,
                    enabled = !isOperating,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("check_flash_status_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = BorderStroke(1.dp, Color(0xFF475569)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "စစ်ဆေးမည်", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    enabled = !isOperating,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_flash_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                    border = BorderStroke(1.dp, Color(0x66EF4444)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Flash ရှင်းလင်းမည်", fontSize = 11.sp)
                }
            }

            // Operation Progress Indicator
            if (isOperating) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF38BDF8),
                    trackColor = Color(0xFF1E293B)
                )
            }

            // Import / Sync Result Card with Categorized Diagnosis
            importResult?.let { res ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (res.isSuccess) Color(0x2210B981) else Color(0x22EF4444),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (res.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (res.isSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (res.isSuccess) "အောင်မြင်သည် (SUCCESS)" else "[${res.errorCategory ?: "အမှား"}]",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (res.isSuccess) Color(0xFF34D399) else Color(0xFFF87171)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.message,
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0)
                        )
                        res.technicalDetails?.let { tech ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tech,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("ESP32 Flash ရှင်းလင်းရန် အတည်ပြုပါ") },
            text = {
                Text("ESP32 Flash Memory (NVS) တွင် သိမ်းဆည်းထားသော KENNEDE IR ကုဒ်များအားလုံးကို ဖျက်ပစ်ပါမည်။ ဆက်လက်လုပ်ဆောင်လိုပါသလား?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearFlash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("သေချာပါသည်၊ ဖျက်မည်")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("မလုပ်တော့ပါ")
                }
            }
        )
    }
}
