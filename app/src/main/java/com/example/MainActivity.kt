package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.components.OneClickSystemDiagnosticsCard
import com.example.ui.components.OneClickDiagnosticsModal
import com.example.ui.components.QuickActionCenterCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.FocusSessionEntity
import com.example.data.HabitEntity
import com.example.data.Task
import com.example.googlehome.ui.GoogleHomeScreen
import com.example.permission.VoicePermissionHelper
import com.example.ui.components.VoicePermissionRationaleDialog
import com.example.service.KyawGyiFloatingService
import com.example.ui.AngelViewModel
import com.example.ui.components.DashboardActiveFocusCard
import com.example.ui.components.DashboardCategorizedTasksCard
import com.example.ui.components.DeviceScannerCard
import com.example.ui.components.OfflineVoiceAwarenessBanner
import com.example.ui.components.PermissionSettingsControlCard
import com.example.ui.components.PermissionSettingsDialog
import com.example.ui.components.SettingsView
import com.example.ui.components.TaskListView
import com.example.ui.components.VisualProgressRingsCard
import com.example.ui.dictation.DictationScreen
import com.example.ui.shortcuts.CustomShortcutsScreen
import com.example.ui.theme.AngelTheme
import com.example.ui.urlcontrol.UrlControlScreen
import com.example.utils.KyawGyiBatteryOptimizer
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.KyawGyiServiceController
import com.example.utils.OverlayPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Central Voice Permissions Coordinator for Contacts, Media, Query Packages & Mic
    lateinit var voicePermissionCoordinator: com.example.permission.VoicePermissionCoordinator
        private set

    // VoicePermissionHelper instance to verify mic access before starting voice engine
    lateinit var voicePermissionHelper: VoicePermissionHelper
        private set

    // PermissionManager for Microphone and Location runtime permissions
    lateinit var permissionManager: com.kyawgyi.ir.utils.PermissionManager
        private set

    // Runtime Permissions Request Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkOverlayAndStartServices()
        } else {
            val micGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false
            if (!micGranted) {
                // Trigger friendly rationale dialog via VoicePermissionHelper
                voicePermissionHelper.requestPermissionDirectly(this)
            }
            Toast.makeText(
                this,
                "ကျော်ကြီး မိုက်ခရိုဖုန်း ခွင့်ပြုချက် ရရှိမှသာ အဆင်ပြေပြေ အလုပ်လုပ်နိုင်မည်ဖြစ်သည်",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize VoicePermissionHelper
        voicePermissionHelper = VoicePermissionHelper(
            context = this,
            onPermissionGranted = {
                checkOverlayAndStartServices()
            },
            onPermissionDenied = {
                Log.w("MainActivity", "Voice permission denied - friendly rationale shown.")
            }
        )
        voicePermissionHelper.registerLauncher(this)

        // Initialize Central Voice Permissions Coordinator before enabling voice engine
        voicePermissionCoordinator = com.example.permission.VoicePermissionCoordinator(
            activity = this,
            onAllGranted = {
                Log.i("MainActivity", "Central voice permissions verified/granted. Starting VoiceForegroundService.")
                com.kyawgyi.ir.service.VoiceForegroundService.startService(this)
            },
            onPermissionDenied = { deniedList ->
                Log.w("MainActivity", "Central voice permissions denied: $deniedList")
            }
        )

        // Initialize PermissionManager for Microphone and Location
        permissionManager = com.kyawgyi.ir.utils.PermissionManager(
            onAllPermissionsGranted = {
                Log.i("MainActivity", "Microphone and Location permissions granted via PermissionManager.")
                com.kyawgyi.ir.service.VoiceForegroundService.startService(this)
            },
            onPermissionsDenied = { deniedList ->
                Log.w("MainActivity", "Permissions denied via PermissionManager: $deniedList")
            }
        )
        permissionManager.registerLauncher(this)

        // 1. App စဖွင့်သည်နှင့် Battery Optimizer, API Key Quota Manager & Runtime Permissions စစ်ဆေးတောင်းဆိုခြင်း
        KyawGyiBatteryOptimizer.init(this)
        com.example.ocr.FailureMemoryEngine.initialize(this)
        com.example.audit.ErrorLearningWorkflowEngine.initialize(this)
        com.example.security.api.ApiKeyQuotaManager.getRequiredInstance(this)
        checkAndRequestPermissions()

        // 2. Jetpack Compose UI တည်ဆောက်ခြင်း
        setContent {
            val viewModel: AngelViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            AngelTheme(themeMode = themeMode) {
                val showVoiceRationale by voicePermissionHelper.showRationaleDialog.collectAsState()

                if (showVoiceRationale) {
                    VoicePermissionRationaleDialog(
                        onConfirmGrant = {
                            voicePermissionHelper.onRationaleAccepted(this@MainActivity)
                        },
                        onDismiss = {
                            voicePermissionHelper.dismissRationaleDialog()
                        },
                        onOpenSettings = {
                            voicePermissionHelper.dismissRationaleDialog()
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Cannot open app settings", e)
                            }
                        }
                    )
                }

                val showCentralRationale by voicePermissionCoordinator.showRationaleState.collectAsState()
                val centralMissingPermissions by voicePermissionCoordinator.missingPermissionsState.collectAsState()

                if (showCentralRationale) {
                    com.example.permission.CentralVoicePermissionsDialog(
                        missingPermissions = centralMissingPermissions,
                        onGrantClicked = {
                            voicePermissionCoordinator.dismissRationale()
                            voicePermissionCoordinator.executeBeforeEnablingVoiceEngine {
                                com.kyawgyi.ir.service.VoiceForegroundService.startService(this@MainActivity)
                            }
                        },
                        onOpenSettingsClicked = {
                            voicePermissionCoordinator.dismissRationale()
                            com.example.permission.VoicePermissions.openAppSettings(this@MainActivity)
                        },
                        onDismiss = {
                            voicePermissionCoordinator.dismissRationale()
                        }
                    )
                }

                MainAppDashboardScreen(
                    onRequestOverlayPermission = {
                        OverlayPermissionHelper.requestOverlayPermission(this)
                    },
                    voicePermissionHelper = voicePermissionHelper,
                    permissionManager = permissionManager
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (VoicePermissionHelper.hasMicrophonePermission(this) && OverlayPermissionHelper.canDrawOverlays(this)) {
            checkOverlayAndStartServices()
        }
    }

    private fun checkAndRequestPermissions() {
        if (!VoicePermissionHelper.hasMicrophonePermission(this)) {
            voicePermissionHelper.checkAndRequestMicrophonePermission(this) {
                checkOverlayAndStartServices()
            }
        } else if (!KyawGyiPermissionManager.hasAllPermissions(this)) {
            permissionLauncher.launch(KyawGyiPermissionManager.getRequiredPermissions())
        } else {
            checkOverlayAndStartServices()
        }
    }

    private fun checkOverlayAndStartServices() {
        if (!VoicePermissionHelper.hasMicrophonePermission(this)) {
            Log.w("MainActivity", "Microphone access (RECORD_AUDIO) not granted yet.")
            return
        }
        // Foreground Background Engine ကို စတင်မည်
        KyawGyiServiceController.startService(this)

        if (OverlayPermissionHelper.canDrawOverlays(this)) {
            // Screen ပေါ်တွင် အမြဲပေါ်နေမည့် Assistive Touch Floating Mic Window ကို စတင်မည်
            val floatingIntent = Intent(this, KyawGyiFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(floatingIntent)
            } else {
                startService(floatingIntent)
            }
        } else {
            Log.i("MainActivity", "Overlay permission not granted. Skipping overlay service for now.")
        }
    }
}

// -------------------------------------------------------------
// JETPACK COMPOSE MAIN DASHBOARD UI
// -------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppDashboardScreen(
    onRequestOverlayPermission: () -> Unit,
    voicePermissionHelper: VoicePermissionHelper? = null,
    permissionManager: com.kyawgyi.ir.utils.PermissionManager? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val viewModel: AngelViewModel = viewModel()
    val roomTasks by viewModel.roomTasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val plannerItems by viewModel.plannerItems.collectAsState()
    val focusSessions by viewModel.focusSessions.collectAsState()
    val systemPersona by viewModel.systemPersona.collectAsState()
    val autoSpeakEnabled by viewModel.autoSpeakEnabled.collectAsState()
    val voiceSpeed by viewModel.voiceSpeed.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val wakeWord by viewModel.wakeWord.collectAsState()
    val selectedLanguage by viewModel.selectedLanguageCode.collectAsState()
    val lastBackupMetadata by viewModel.lastBackupMetadata.collectAsState()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    var smartControlSubTab by remember { mutableIntStateOf(0) }
    var isChatboxOpen by remember { mutableStateOf(false) }
    var isDictationOpen by remember { mutableStateOf(false) }
    var showOneClickDiagnosticsModal by remember { mutableStateOf(false) }

    // FIX-03: in-app Back behavior (previously there was no
    // BackHandler anywhere, so system Back exited the app from every
    // screen). Policy lives in resolveBackAction (MainBackNavigation):
    // close an open full-screen route first, then fall back to the
    // home tab; only at the root is Back left to the system (exit).
    // Declared before the early returns below so it is composed while
    // the chatbox / dictation routes are showing.
    androidx.activity.compose.BackHandler(
        enabled = resolveBackAction(isDictationOpen, isChatboxOpen, selectedTab) != MainBackAction.EXIT
    ) {
        when (resolveBackAction(isDictationOpen, isChatboxOpen, selectedTab)) {
            MainBackAction.CLOSE_DICTATION -> isDictationOpen = false
            MainBackAction.CLOSE_CHAT -> isChatboxOpen = false
            MainBackAction.GO_HOME_TAB -> selectedTab = 0
            MainBackAction.EXIT -> Unit
        }
    }

    if (isChatboxOpen) {
        com.example.ui.components.AiAngelChatboxScreen(
            viewModel = viewModel,
            onBack = { isChatboxOpen = false },
            onNavigateToSettings = {
                isChatboxOpen = false
                selectedTab = 4
            }
        )
        return
    }

    if (isDictationOpen) {
        DictationScreen(
            onBack = { isDictationOpen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_kyaw_gyi_logo),
                            contentDescription = "Kyaw Gyi AI Voice Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                text = "Kyaw Gyi AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "AI Voice Assistant",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showOneClickDiagnosticsModal = true },
                        modifier = Modifier.testTag("open_one_click_diagnostics_topbar_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = "One-Click System Diagnostics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { selectedTab = 4 },
                        modifier = Modifier.testTag("open_permissions_topbar_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Settings & Permissions",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { isChatboxOpen = true },
                        modifier = Modifier.testTag("open_chatbox_topbar_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Angel Chatbox",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("app_theme_toggle_btn")
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                com.example.data.ThemeMode.DARK -> Icons.Default.DarkMode
                                com.example.data.ThemeMode.LIGHT -> Icons.Default.LightMode
                                com.example.data.ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            },
                            contentDescription = "Toggle Dark/Light Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("ပင်မ") },
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.TaskAlt, contentDescription = "Tasks") },
                    label = { Text("တာဝန်များ") },
                    modifier = Modifier.testTag("nav_tasks")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Sensors, contentDescription = "Smart Controls") },
                    label = { Text("စမတ်ထိန်းချုပ်မှု") },
                    modifier = Modifier.testTag("nav_smart_controls")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Link, contentDescription = "Shortcuts") },
                    label = { Text("အသံတို") },
                    modifier = Modifier.testTag("nav_shortcuts")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("ဆက်တင်") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> DashboardHomeContent(
                    context = context,
                    tasks = roomTasks,
                    habits = habits,
                    focusSessions = focusSessions,
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    onAddTask = { title, cat -> viewModel.addTask(title, cat) },
                    onNavigateToTasks = { selectedTab = 1 },
                    onNavigateToFocus = { selectedTab = 1 },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onNavigateToUrlControls = {
                        smartControlSubTab = 1
                        selectedTab = 2
                    },
                    onNavigateToSettings = { selectedTab = 4 },
                    onNavigateToChatbox = { isChatboxOpen = true },
                    onNavigateToDictation = { isDictationOpen = true },
                    onOpenDiagnosticsModal = { showOneClickDiagnosticsModal = true },
                    voicePermissionHelper = voicePermissionHelper,
                    permissionManager = permissionManager
                )
                1 -> TaskListView(
                    tasks = roomTasks,
                    onToggleTask = { viewModel.toggleTaskCompleted(it) },
                    onDeleteTask = { viewModel.deleteTask(it) },
                    onAddTask = { viewModel.addTask(it) },
                    onAddTaskWithCategory = { title, cat -> viewModel.addTask(title, cat) },
                    onAddTaskWithFullDetails = { title, cat, prio, due, tags -> viewModel.addTask(title, cat, prio, due, tags) },
                    onBackupTasks = { viewModel.backupTasksToDataStore() },
                    onRestoreTasks = { viewModel.restoreTasksFromDataStore() },
                    lastBackupMetadata = lastBackupMetadata,
                    backupStatusMessage = backupStatusMessage,
                    onClearBackupMessage = { viewModel.clearBackupMessage() },
                    habits = habits,
                    onToggleHabit = { viewModel.toggleHabitCompleted(it) },
                    onAddHabit = { title, freq -> viewModel.addHabit(title, freq) },
                    focusSessions = focusSessions,
                    onSaveFocusSession = { name, duration, notes, cat, color -> viewModel.addFocusSession(name, duration, notes, cat, color) },
                    onDeleteFocusSession = { viewModel.deleteFocusSession(it) },
                    plannerItems = plannerItems,
                    onScheduleReminder = { task, delay -> viewModel.scheduleTaskReminder(task, delay) },
                    onTestReminder = { task -> viewModel.triggerTestTaskReminder(task) }
                )
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PrimaryTabRow(selectedTabIndex = smartControlSubTab) {
                            Tab(
                                selected = smartControlSubTab == 0,
                                onClick = { smartControlSubTab = 0 },
                                text = { Text("Google Home & စမတ်ပစ္စည်းများ", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = smartControlSubTab == 1,
                                onClick = { smartControlSubTab = 1 },
                                text = { Text("URL / Webhook Controls", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                        if (smartControlSubTab == 0) {
                            GoogleHomeScreen()
                        } else {
                            UrlControlScreen()
                        }
                    }
                }
                3 -> CustomShortcutsScreen()
                4 -> SettingsView(
                    systemPersona = systemPersona,
                    onPersonaChanged = { viewModel.setSystemPersona(it) },
                    autoSpeakEnabled = autoSpeakEnabled,
                    onAutoSpeakChanged = { viewModel.setAutoSpeakEnabled(it) },
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { viewModel.setSelectedLanguage(it) },
                    voiceSpeed = voiceSpeed,
                    onVoiceSpeedChanged = { viewModel.setVoiceSpeed(it) },
                    voicePitch = voicePitch,
                    onVoicePitchChanged = { viewModel.setVoicePitch(it) },
                    wakeWord = wakeWord,
                    onWakeWordChanged = { viewModel.setWakeWord(it) },
                    onExportHistoryJson = { viewModel.exportChatHistoryJson(it) },
                    onExportHistoryText = { viewModel.exportChatHistoryText(it) },
                    onClearHistory = { viewModel.clearHistory() }
                )
            }
        }
    }

    if (showOneClickDiagnosticsModal) {
        val diagEngine = remember { com.example.diagnostics.OneClickSystemDiagnosticEngine.getInstance(context) }
        val diagReport by diagEngine.reportFlow.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        OneClickDiagnosticsModal(
            report = diagReport,
            onDismiss = { showOneClickDiagnosticsModal = false },
            onReRun = {
                coroutineScope.launch {
                    diagEngine.runFullDiagnostic()
                }
            }
        )
    }
}

@Composable
fun DashboardHomeContent(
    context: Context,
    tasks: List<Task> = emptyList(),
    habits: List<HabitEntity> = emptyList(),
    focusSessions: List<FocusSessionEntity> = emptyList(),
    onToggleTask: (Task) -> Unit = {},
    onDeleteTask: (Task) -> Unit = {},
    onAddTask: (String, String) -> Unit = { _, _ -> },
    onNavigateToTasks: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit,
    onNavigateToUrlControls: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToChatbox: () -> Unit = {},
    onNavigateToDictation: () -> Unit = {},
    onOpenDiagnosticsModal: () -> Unit = {},
    voicePermissionHelper: VoicePermissionHelper? = null,
    permissionManager: com.kyawgyi.ir.utils.PermissionManager? = null
) {
    var isServiceActive by remember { mutableStateOf(true) }
    var testCommandText by remember { mutableStateOf("") }
    var isExecutingTest by remember { mutableStateOf(false) }
    var lastExecutedCommand by remember { mutableStateOf<String?>(null) }
    var lastExecutionResult by remember { mutableStateOf<String?>(null) }
    var lastExecutionRoute by remember { mutableStateOf<String?>(null) }
    var lastExecutionLatency by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var showSetupWizardDialog by remember { mutableStateOf(false) }

    fun runDirectTest(command: String) {
        if (command.isBlank()) return
        isExecutingTest = true
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val smartRouter = com.example.router.KyawGyiSmartRouter(context)
            val response = withContext(Dispatchers.IO) {
                smartRouter.processVoiceInput(command)
            }
            val latency = System.currentTimeMillis() - startTime
            lastExecutedCommand = command
            lastExecutionResult = response
            lastExecutionLatency = latency
            lastExecutionRoute = when {
                response.contains("Webhook") || response.contains("လင့်ခ်") -> "URL / Webhook Control"
                response.contains("ဖွင့်") || response.contains("Application") -> "App DeepLink / Intent"
                response.contains("မီး") || response.contains("Wifi") -> "Native Hardware Action"
                else -> "Smart AI Engine"
            }
            isExecutingTest = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real-time Network & Offline Voice Recognition Awareness Banner
        OfflineVoiceAwarenessBanner()

        // Dedicated Hero Quick Action Center: Permission Request Button & 1-Click System Error Diagnostics
        QuickActionCenterCard(
            onOpenFullDiagnosticsModal = onOpenDiagnosticsModal,
            modifier = Modifier.fillMaxWidth()
        )

        // 1-Click Comprehensive System Error Diagnostics & Subsystem Health Card
        OneClickSystemDiagnosticsCard(
            modifier = Modifier.fillMaxWidth(),
            onOpenFullDialog = onOpenDiagnosticsModal
        )

        // Dedicated Direct Settings & System Permissions Overview Card
        PermissionSettingsControlCard(
            onOpenFullSettings = { showPermissionSettingsDialog = true },
            onOpenWizard = { showSetupWizardDialog = true },
            modifier = Modifier.fillMaxWidth()
        )

        // 1. Visual Progress Rings for Habit Completion & Focus Session Goals
        VisualProgressRingsCard(
            tasks = tasks,
            habits = habits,
            focusSessions = focusSessions,
            onNavigateToTasks = onNavigateToTasks,
            onNavigateToFocus = onNavigateToFocus
        )

        // 2. Active Focus Session & Foreground Service Controller Card
        DashboardActiveFocusCard(
            onNavigateToFullFocus = onNavigateToFocus
        )

        // 3. Categorized Daily Tasks Card with Interactive Filtering & Creation
        DashboardCategorizedTasksCard(
            tasks = tasks,
            onToggleTask = onToggleTask,
            onDeleteTask = onDeleteTask,
            onAddTask = onAddTask,
            onViewAllTasks = onNavigateToTasks
        )

        // 3b. Device Scanner & Direct Voice Call Card (Apps & Contacts)
        DeviceScannerCard(
            onVoiceCommandTriggered = { cmd -> runDirectTest(cmd) }
        )

        // 4. Voice Assistant Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isServiceActive) "ကျော်ကြီး Voice Assistant အဆင်သင့်ရှိပါသည်" else "ကျော်ကြီး Service ပိတ်ထားပါသည်",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isServiceActive) "အနောက်ကွယ်မှ မြန်မာအသံဖမ်းယူမှု & URL ထိန်းချုပ်မှု စနစ် အလုပ်လုပ်နေသည်" else "အသံဖမ်းယူမှုစနစ် ရပ်တန့်နေသည်",
                    fontSize = 12.sp,
                    color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }

        // Direct System Settings & Permissions Hub Card
        PermissionSettingsControlCard(
            onOpenFullSettings = { showPermissionSettingsDialog = true },
            onOpenWizard = { showSetupWizardDialog = true }
        )

        // TTS (Text-to-Speech) Master Toggle Card
        var isTtsEnabledState by remember { mutableStateOf(com.example.utils.MyanmarTTSEngine.masterTtsEnabled) }
        Card(
            modifier = Modifier.fillMaxWidth().testTag("dashboard_tts_toggle_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (isTtsEnabledState) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isTtsEnabledState) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "TTS Toggle Icon",
                        tint = if (isTtsEnabledState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TTS (စာသားမှ စကားပြော) စနစ်",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isTtsEnabledState) "ဖွင့်ထားသည် - လိုအပ်သလို အသံဖြင့် ပြောကြားမည်" else "ပိတ်ထားသည် - အသံထွက်ဆိုခြင်း မရှိပါ",
                            fontSize = 11.sp,
                            color = if (isTtsEnabledState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Switch(
                    checked = isTtsEnabledState,
                    onCheckedChange = { enabled ->
                        isTtsEnabledState = enabled
                        com.example.utils.MyanmarTTSEngine.masterTtsEnabled = enabled
                    },
                    modifier = Modifier.testTag("dashboard_tts_switch")
                )
            }
        }

        // AI Angel Chatbox & Universal Data Ingestion Vault Hero Banner
        Card(
            onClick = onNavigateToChatbox,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_chatbox_banner"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Angel Chatbox",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI Angel Chatbox",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "INGESTION & VAULT",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "စကားပြောခန်း • ဓာတ်ပုံ၊ အသံ၊ PDF၊ ဖိုင်များ ပေးပို့သိမ်းဆည်းခြင်း & Local Search",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Text(
                    text = "ဖွင့်မည် >",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Voice Typing (Dictation) Banner — native dictation screen
        Card(
            onClick = onNavigateToDictation,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_dictation_banner"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Typing",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "အသံနဲ့ စာရိုက်ရန် (Voice Typing)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "မြန်မာအသံမှ စာသား • တည်းဖြတ်မှု • Auto-save • TXT ထုတ်ယူခြင်း",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Text(
                    text = "ဖွင့်မည် >",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quick URL & External App Voice Control Banner
        Card(
            onClick = onNavigateToUrlControls,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Microphone URL & App Voice Control",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "မြန်မာအသံဖြင့် အခြား App / Webhook ချိတ်ဆက်မှုများ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                Text("ဖွင့်ရန် >", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        // Real-Time Control & Verification Tester Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "လက်တွေ့ ထိန်းချုပ်မှု စစ်ဆေးခြင်း (Live Tester)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val focusManager = LocalFocusManager.current

                OutlinedTextField(
                    value = testCommandText,
                    onValueChange = { testCommandText = it },
                    label = { Text("စမ်းသပ်မည့် မြန်မာအသံစကားလုံး ရိုက်ထည့်ပါ") },
                    placeholder = { Text("ဥပမာ- မီးခလုတ်ဖွင့်၊ တယ်လီဂရမ်ဖွင့်၊ သီချင်းရှာဖွင့်") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            runDirectTest(testCommandText)
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                runDirectTest(testCommandText)
                            },
                            enabled = testCommandText.isNotBlank() && !isExecutingTest
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run Test")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "နမူနာ စကားလုံးများ နှိပ်၍ စမ်းသပ်ရန်:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick test chips
                val quickChips = listOf(
                    "မီးခလုတ် ဖွင့်",
                    "တယ်လီဂရမ် ဖွင့်",
                    "သီချင်း ရှာဖွင့်",
                    "စပေါတီဖိုင်း ဖွင့်",
                    "မီးဖွင့်",
                    "အသံအချက်အလက် ပို့"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.take(3).forEach { chip ->
                        AssistChip(
                            onClick = {
                                focusManager.clearFocus()
                                testCommandText = chip
                                runDirectTest(chip)
                            },
                            label = { Text(chip, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickChips.drop(3).forEach { chip ->
                        AssistChip(
                            onClick = {
                                focusManager.clearFocus()
                                testCommandText = chip
                                runDirectTest(chip)
                            },
                            label = { Text(chip, fontSize = 11.sp) }
                        )
                    }
                }

                // Test Execution Diagnostics Result
                if (lastExecutionResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "စစ်ဆေးမှု ရလဒ် (Execution Result):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "${lastExecutionLatency} ms",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "အမိန့်: \"$lastExecutedCommand\"",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "လမ်းကြောင်း: $lastExecutionRoute",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "တုံ့ပြန်ချက်: $lastExecutionResult",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // VOICE FOREGROUND SERVICE & PERMISSION MANAGER CONTROL CARD
        // -------------------------------------------------------------
        val isVoiceFgRunning by com.kyawgyi.ir.service.VoiceForegroundService.isServiceRunning.collectAsState()
        val voiceFgStatus by com.kyawgyi.ir.service.VoiceForegroundService.serviceStatusMessage.collectAsState()
        val voiceFgLastSpeech by com.kyawgyi.ir.service.VoiceForegroundService.lastTranscribedSpeech.collectAsState()

        val hasMicPermission = com.kyawgyi.ir.utils.PermissionManager.hasMicrophonePermission(context)
        val hasLocPermission = com.kyawgyi.ir.utils.PermissionManager.hasLocationPermission(context)
        var isBatteryExempt by remember {
            mutableStateOf(com.example.utils.KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context))
        }
        var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                isBatteryExempt = com.example.utils.KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
                kotlinx.coroutines.delay(1500)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("voice_foreground_service_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(
                1.dp,
                if (isVoiceFgRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
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
                                .size(12.dp)
                                .background(
                                    color = if (isVoiceFgRunning) Color(0xFF10B981) else Color.Gray,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VoiceForegroundService",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isVoiceFgRunning) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (isVoiceFgRunning) "Active (နားထောင်ဆဲ)" else "Inactive",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isVoiceFgRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "အမြဲတမ်း နောက်ကွယ်မှ မိုက်ခရိုဖုန်းဖွင့်၍ အသံနားထောင်မည့် Foreground Service ဖြစ်ပါသည်။ Persistent Notification ဖြင့် အလုပ်လုပ်ပါသည်။",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Permissions Status Row (Microphone & Location & Battery Optimization)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasMicPermission) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasMicPermission) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (hasMicPermission) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Mic: " + if (hasMicPermission) "ရရှိပြီး" else "မရှိသေး",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasLocPermission) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasLocPermission) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (hasLocPermission) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Loc: " + if (hasLocPermission) "ရရှိပြီး" else "မရှိသေး",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBatteryExempt) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFFF9800).copy(alpha = 0.15f),
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable { showBatteryOptimizationDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isBatteryExempt) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isBatteryExempt) Color(0xFF10B981) else Color(0xFFFF9800),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Battery: " + if (isBatteryExempt) "ကင်းလွတ်" else "သတ်မှတ်ရန်",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status & Transcribed text preview
                if (voiceFgLastSpeech.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "နောက်ဆုံး ကြားသိရသောအသံ: \"$voiceFgLastSpeech\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text(
                    text = "အခြေအနေ: $voiceFgStatus",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isVoiceFgRunning) {
                                com.kyawgyi.ir.service.VoiceForegroundService.stopService(context)
                            } else {
                                if (!isBatteryExempt) {
                                    showBatteryOptimizationDialog = true
                                }
                                if (context is MainActivity) {
                                    context.voicePermissionCoordinator.executeBeforeEnablingVoiceEngine {
                                        com.kyawgyi.ir.service.VoiceForegroundService.startService(context)
                                    }
                                } else if (context is ComponentActivity && permissionManager != null) {
                                    permissionManager.checkAndRequestPermissions(context)
                                } else {
                                    com.kyawgyi.ir.utils.PermissionManager.activateVoiceServiceIfPermitted(context)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("toggle_voice_fg_service_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVoiceFgRunning) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isVoiceFgRunning) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isVoiceFgRunning) "Service ရပ်မည်" else "Service စတင်မည်", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            com.kyawgyi.ir.service.VoiceForegroundService.speak(
                                context,
                                "မင်္ဂလာပါ ကျော်ကြီး Voice Foreground Service ပုံမှန် အလုပ်လုပ်နေပါသည်"
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("test_voice_tts_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("my-MM TTS စမ်းရန်", fontSize = 12.sp)
                    }
                }
            }
        }

        if (showBatteryOptimizationDialog) {
            com.example.ui.components.BatteryOptimizationExemptionDialog(
                onDismiss = {
                    showBatteryOptimizationDialog = false
                    isBatteryExempt = com.example.utils.KyawGyiPermissionManager.isIgnoringBatteryOptimizations(context)
                }
            )
        }

        val pendingCommunication by com.example.actions.CallSmsConfirmationCoordinator.getInstance()
            .pendingState.collectAsState()

        pendingCommunication?.let { pending ->
            com.example.ui.components.CallSmsConfirmationDialog(
                pendingAction = pending,
                onConfirm = {
                    com.example.actions.CallSmsConfirmationCoordinator.getInstance().executeConfirmed(context)
                },
                onDismiss = {
                    com.example.actions.CallSmsConfirmationCoordinator.getInstance().cancelPending("User dismissed dialog")
                }
            )
        }

        // Trigger Voice Command Direct Test Button
        Button(
            onClick = {
                if (VoicePermissionHelper.hasMicrophonePermission(context)) {
                    KyawGyiServiceController.startListening(context)
                } else if (context is ComponentActivity && voicePermissionHelper != null) {
                    voicePermissionHelper.checkAndRequestMicrophonePermission(context) {
                        KyawGyiServiceController.startListening(context)
                    }
                } else {
                    Toast.makeText(context, "Microphone ခွင့်ပြုချက် လိုအပ်ပါသည်", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("စကားစတင်ပြောမည် (Microphone Voice)", fontSize = 15.sp)
        }

        // Floating Overlay Permission Request Button
        OutlinedButton(
            onClick = {
                if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                    onRequestOverlayPermission()
                } else {
                    Toast.makeText(context, "Floating Mic Permission ရရှိပြီးဖြစ်ပါသည်", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Floating Bubble Window ခွင့်ပြုချက်တောင်းရန်")
        }

        // Toggle Service Button
        OutlinedButton(
            onClick = {
                if (isServiceActive) {
                    KyawGyiServiceController.stopService(context)
                    context.stopService(Intent(context, KyawGyiFloatingService::class.java))
                    // FIX-06: KyawGyiOverlayService stop removed with
                    // the service itself (never started; dead code).
                    isServiceActive = false
                } else {
                    KyawGyiServiceController.startService(context)
                    if (OverlayPermissionHelper.canDrawOverlays(context)) {
                        val floatingIntent = Intent(context, KyawGyiFloatingService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(floatingIntent)
                        } else {
                            context.startService(floatingIntent)
                        }
                    }
                    isServiceActive = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isServiceActive) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isServiceActive) "Service ပိတ်မည်" else "Service ပြန်ဖွင့်မည်")
        }

        // Error-Learning Log Viewer (ER-001 & 3-Factor Analysis / Live Condition Verifier)
        com.example.ui.components.ErrorLearningLogViewerCard()

        // Interactive STT/TTS Myanmar Engine Inspector & Live Mic Verifier Card
        com.example.ui.components.MyanmarSttTtsEngineInspectorCard()

        // STT/TTS Offline Model Downloader & Voice Settings Card
        com.example.ui.components.SttTtsModelAndVoiceSettingsCard()

        // Accessibility Diagnostic Logs Button
        var showDiagnosticDialog by remember { mutableStateOf(false) }
        val diagnosticLogs by com.example.service.KyawGyiAccessibilityService.diagnosticLogs.collectAsState()

        OutlinedButton(
            onClick = { showDiagnosticDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("diagnostic_logs_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Accessibility Diagnostic Logs စစ်ဆေးရန် (${diagnosticLogs.size})")
        }

        if (showDiagnosticDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosticDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accessibility Event Logs", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { com.example.service.KyawGyiAccessibilityService.clearDiagnosticLogs() }) {
                            Text("ရှင်းမည်", fontSize = 12.sp, color = Color.Red)
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "APP_PACKAGES နှင့် Cross-Reference တိုက်ဆိုင်စစ်ဆေးမှု ရလဒ်များ:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (diagnosticLogs.isEmpty()) {
                            Text(
                                text = "ဖမ်းယူရရှိသော AccessibilityEvent မှတ်တမ်း မရှိသေးပါ။ (အသံဖြင့် အက်ပ်ဖွင့်ကြည့်ပါ)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            diagnosticLogs.reversed().forEach { log ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (log.isIntentMatchConfirmed) Color(0xFF10B981).copy(alpha = 0.12f)
                                    else if (log.isAppPackagesMatch) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = log.eventType,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (log.isIntentMatchConfirmed) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                                            )
                                            if (log.isIntentMatchConfirmed) {
                                                Text(
                                                    text = "MATCH CONFIRMED",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF059669)
                                                )
                                            } else if (log.isAppPackagesMatch) {
                                                Text(
                                                    text = "IN APP_PACKAGES",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Package: ${log.packageName}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        if (log.matchedAppKey != null) {
                                            Text("Matched Key: \"${log.matchedAppKey}\"", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        if (log.matchedBurmeseAliases.isNotEmpty()) {
                                            Text("Aliases: ${log.matchedBurmeseAliases.take(3).joinToString(", ")}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        if (log.intendedAppPackage != null) {
                                            Text("Target Intent: ${log.intendedAppPackage}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(log.confirmationStatusMessage, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDiagnosticDialog = false }) {
                        Text("ပိတ်မည်")
                    }
                }
            )
        }

        if (showPermissionSettingsDialog) {
            PermissionSettingsDialog(
                onDismiss = { showPermissionSettingsDialog = false },
                onRequestRuntimePermissions = {
                    if (!OverlayPermissionHelper.canDrawOverlays(context)) {
                        onRequestOverlayPermission()
                    } else {
                        KyawGyiPermissionManager.openAppDetailsSettings(context)
                    }
                },
                onOpenWizard = {
                    showPermissionSettingsDialog = false
                    showSetupWizardDialog = true
                }
            )
        }

        if (showSetupWizardDialog) {
            com.example.setup.KyawGyiSetupWizardDialog(
                onDismiss = { showSetupWizardDialog = false },
                onFinishSetup = { showSetupWizardDialog = false }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
