package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.components.HeaderBar
import com.example.ui.components.KennedeIrDialog
import com.example.ui.components.ShortcutsGrid
import com.example.ui.components.StatusPillsBar
import com.example.ui.components.VoiceVisualizerArea
import com.example.ui.chat.AiAngelChatDialog
import com.example.ui.music.MusicLibraryDialog
import com.example.ui.diagnostics.AssetDiagnosticsDialog
import com.example.ui.dialogs.AiVoiceStudioDialog
import com.example.ui.dialogs.DirectRoutesDialog
import com.example.ui.dialogs.EcoFlowBottomSheet
import com.example.ui.dialogs.MemoryManagementDialog
import com.example.ui.dialogs.NotificationManagementDialog
import com.example.ui.dialogs.PermissionsDialog
import com.example.ui.dialogs.SettingsDialog
import com.example.ui.screens.EcoFlowDashboardScreen
import com.example.ui.screens.Esp32MonitoringScreen
import com.example.ui.permissions.rememberRuntimePermissionHandler
import com.example.ui.theme.BackgroundDark
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.AiAngelChatViewModel

@Composable
fun MainScreen(
  viewModel: MainViewModel,
  modifier: Modifier = Modifier
) {
  val isListening by viewModel.isListening.collectAsState()
  val speechStatus by viewModel.speechStatus.collectAsState()
  val lastMessage by viewModel.lastMessage.collectAsState()
  val volumeLevel by viewModel.volumeLevel.collectAsState()
  val selectedLanguage by viewModel.selectedLanguage.collectAsState()
  val isOfflineMode by viewModel.isOfflineMode.collectAsState()
  val ecoFlowState by viewModel.ecoFlowState.collectAsState()
  val bleConnected by viewModel.bleConnected.collectAsState()
  val bleDevices by viewModel.bleDevices.collectAsState()
  val isBleScanning by viewModel.isBleScanning.collectAsState()
  val shortcuts by viewModel.shortcuts.collectAsState()
  val contacts by viewModel.contacts.collectAsState()
  val memoryEntries by viewModel.memoryEntries.collectAsState()
  val nodes by viewModel.nodes.collectAsState()
  val selfHealingLogs by viewModel.selfHealingLogs.collectAsState()
  val errorLogs by viewModel.errorLogs.collectAsState()
  val lastFailureWarning by viewModel.lastFailureWarning.collectAsState()
  val isOfflineFallbackActive by viewModel.isOfflineFallbackActive.collectAsState()

  val showEcoFlowSheet by viewModel.showEcoFlowSheet.collectAsState()
  val ecoFlowConfig by viewModel.ecoFlowConfig.collectAsState()
  val showMemoryModal by viewModel.showMemoryModal.collectAsState()
  val showSettingsModal by viewModel.showSettingsModal.collectAsState()
  val showPermissionsModal by viewModel.showPermissionsModal.collectAsState()
  val showBluetoothModal by viewModel.showBluetoothModal.collectAsState()
  val showAiAngelModal by viewModel.showAiAngelModal.collectAsState()
  val showMusicLibraryModal by viewModel.showMusicLibraryModal.collectAsState()
  val showAssetDiagnosticsModal by viewModel.showAssetDiagnosticsModal.collectAsState()
  val angelChatViewModel: AiAngelChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
  val isBackgroundListening by viewModel.isBackgroundListening.collectAsState()
  val ambientNoiseDb by viewModel.ambientNoiseDb.collectAsState()
  val deviceLocation by viewModel.deviceLocation.collectAsState()

  // Kennede IR Smart Controller states
  val esp32Status by viewModel.esp32Status.collectAsState()
  val kennedeIrDb by viewModel.kennedeIrDb.collectAsState()
  val selectedLearningButton by viewModel.selectedLearningButton.collectAsState()
  val isLearningActive by viewModel.isLearningActive.collectAsState()
  val learningMessage by viewModel.learningMessage.collectAsState()
  val rawSignalReport by viewModel.rawSignalReport.collectAsState()
  val irEventLogs by viewModel.irEventLogs.collectAsState()
  val fanAutomationState by viewModel.fanAutomationState.collectAsState()
  val showKennedeModal by viewModel.showKennedeModal.collectAsState()
  val kennedeIrTabIndex by viewModel.kennedeIrSelectedTabIndex.collectAsState()
  val esp32DiscoveryState by viewModel.esp32DiscoveryEngine.state.collectAsState()
  val showNotificationModal by viewModel.showNotificationModal.collectAsState()
  val showEcoFlowDashboard by viewModel.showEcoFlowDashboard.collectAsState()
  val showAiVoiceStudio by viewModel.showAiVoiceStudio.collectAsState()
  val showApiKeyModal by viewModel.showApiKeyModal.collectAsState()
  val showDirectRoutesModal by viewModel.showDirectRoutesModal.collectAsState()
  val showEsp32MonitoringScreen by viewModel.showEsp32MonitoringScreen.collectAsState()

  // Jetpack Compose Runtime Permission Request Handler
  val permissionHandler = rememberRuntimePermissionHandler { updatedState ->
    if (updatedState.isLocationGranted) {
      viewModel.refreshLocation()
    }
  }

  // Runtime Permission Dialog state (Android 12/13+ Microphone & Bluetooth requirement)
  var showRuntimePermissionDialog by remember { mutableStateOf(false) }

  // Prompt on initial launch if essential permissions (Mic & Bluetooth) are not yet granted
  androidx.compose.runtime.LaunchedEffect(Unit) {
    if (!permissionHandler.permissionState.areAllEssentialGranted) {
      showRuntimePermissionDialog = true
    }
  }

  // Start continuous audio stream listener for wake word when mic permission is granted
  androidx.compose.runtime.LaunchedEffect(permissionHandler.permissionState.isMicrophoneGranted) {
    if (permissionHandler.permissionState.isMicrophoneGranted) {
      viewModel.wakeWordDetector.startContinuousListening()
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(BackgroundDark),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = BackgroundDark
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0F1A2A), BackgroundDark),
            radius = 1200f
          )
        )
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      Column(
        modifier = Modifier.fillMaxSize()
      ) {
        // 1. Top Header Bar
        HeaderBar(
          isListening = isListening,
          onOpenMemory = { viewModel.showMemoryModal.value = true },
          onOpenSettings = { viewModel.showSettingsModal.value = true },
          onOpenAngelChat = { viewModel.showAiAngelModal.value = true },
          onOpenMusicLibrary = { viewModel.showMusicLibraryModal.value = true },
          onOpenVoiceStudio = { viewModel.showAiVoiceStudio.value = true },
          onOpenDirectRoutes = { viewModel.showDirectRoutesModal.value = true }
        )

        // 2. Status Pills Bar
        StatusPillsBar(
          isOfflineMode = isOfflineMode,
          batteryPercentage = ecoFlowState.batteryPercentage,
          bleConnected = bleConnected,
          irReady = esp32Status.esp32,
          onPermissionsClick = { viewModel.showPermissionsModal.value = true },
          onWifiClick = { viewModel.toggleOfflineMode() },
          onBluetoothClick = {
            viewModel.showBluetoothModal.value = true
            viewModel.scanBleDevices()
          },
          onEcoFlowClick = { viewModel.showEcoFlowSheet.value = true },
          onEcoFlowDashboardClick = { viewModel.showEcoFlowDashboard.value = true },
          onNotificationsClick = { viewModel.showNotificationModal.value = true },
          onIrControllerClick = { viewModel.showKennedeModal.value = true },
          onEsp32MonitorClick = { viewModel.showEsp32MonitoringScreen.value = true },
          onApiKeyClick = { viewModel.showApiKeyModal.value = true },
          onDirectRoutesClick = { viewModel.showDirectRoutesModal.value = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Center Voice Visualizer & Animated Mic
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          VoiceVisualizerArea(
            isListening = isListening,
            speechStatus = speechStatus,
            lastMessage = lastMessage,
            lastFailureWarning = lastFailureWarning,
            volumeLevel = volumeLevel,
            isNetworkAvailable = viewModel.isNetworkOnline(),
            isOfflineFallbackActive = isOfflineFallbackActive,
            onToggleOfflineFallback = { viewModel.toggleOfflineFallback() },
            onMicClick = {
              if (!permissionHandler.permissionState.isMicrophoneGranted) {
                showRuntimePermissionDialog = true
                permissionHandler.requestMicrophone()
              } else {
                if (isListening) {
                  viewModel.stopListening()
                } else {
                  viewModel.startHybridVoiceListening()
                }
              }
            },
            onSendCommand = { viewModel.processVoiceCommand(it) },
            modifier = Modifier.fillMaxSize()
          )
        }

        // 4. Bottom Dock Shortcuts Grid
        ShortcutsGrid(
          shortcuts = shortcuts,
          selectedLanguage = selectedLanguage,
          onLanguageSelect = { viewModel.setLanguage(it) },
          onShortcutClick = { sc ->
            if (sc.voiceCommand == "ecoflow") {
              viewModel.showEcoFlowSheet.value = true
            } else if (sc.voiceCommand.contains("kennede") || sc.voiceCommand.contains("ပန်ကာ")) {
              viewModel.showKennedeModal.value = true
            } else if (sc.voiceCommand.contains("api") || sc.voiceCommand.contains("key") || sc.voiceCommand.contains("သော့")) {
              viewModel.showApiKeyModal.value = true
            } else if (sc.voiceCommand.contains("notif") || sc.voiceCommand.contains("သတိပေးချက်") || sc.voiceCommand.contains("အသိပေးချက်")) {
              viewModel.showNotificationModal.value = true
            } else if (sc.voiceCommand.contains("dashboard") || sc.voiceCommand.contains("ဒက်ရှ်ဘုတ်")) {
              viewModel.showEcoFlowDashboard.value = true
            } else {
              viewModel.processVoiceCommand(sc.title)
            }
          }
        )
      }

      // 5. EcoFlow Power Station Bottom Sheet
      if (showEcoFlowSheet) {
        EcoFlowBottomSheet(
          state = ecoFlowState,
          bleDevices = bleDevices,
          isBleScanning = isBleScanning,
          currentConfig = ecoFlowConfig,
          onSaveEcoFlowConfig = { ak, sk, sn, host -> viewModel.saveEcoFlowConfig(ak, sk, sn, host) },
          onDismiss = { viewModel.showEcoFlowSheet.value = false },
          onSetAcPort = { viewModel.setAcPort(it) },
          onSetDcPort = { viewModel.setDcPort(it) },
          onSetUsbPort = { viewModel.setUsbPort(it) },
          onSetMasterPower = { viewModel.setMasterPower(it) },
          onSetChargingSpeed = { viewModel.setChargingSpeed(it) },
          onSetXBoost = { viewModel.setXBoost(it) },
          onSetBeep = { viewModel.setBeepSound(it) },
          onSetTimeout = { viewModel.setAcTimeout(it) },
          onTriggerSafetyHalt = { viewModel.triggerSafetyHalt() },
          onScanBluetooth = { viewModel.scanBleDevices() },
          onVoiceIntentTest = { phrase ->
            viewModel.processVoiceCommand(phrase.replace("\"", ""))
          },
          onConnectBle = { address -> viewModel.connectBleDevice(address) },
          onDisconnectBle = { viewModel.disconnectBleDevice() },
          onOpenFullScanner = { viewModel.showBluetoothModal.value = true },
          onSelectModel = { viewModel.selectEcoFlowModel(it) },
          onToggleLowBatteryCutoff = { viewModel.toggleLowBatteryCutoff(it) },
          onSetLowBatteryThreshold = { viewModel.setLowBatteryCutoffThreshold(it) },
          onToggleOverheatProtection = { viewModel.toggleOverheatProtection(it) },
          onToggleOverloadProtection = { viewModel.toggleOverloadProtection(it) },
          onToggleGridOutageUps = { viewModel.toggleGridOutageUps(it) },
          onTestEmergencyAutomation = { viewModel.testEmergencyAutomation() },
          onSyncCloudTelemetry = { viewModel.fetchEcoFlowCloudTelemetry() },
          onToggleDiagnosticsMode = { viewModel.setDiagnosticsMode(it) }
        )
      }

      // 6. Memory & Operational Health Dialog with Error Learning
      if (showMemoryModal) {
        MemoryManagementDialog(
          memoryEntries = memoryEntries,
          errorLogs = errorLogs,
          nodes = nodes,
          selfHealingLogs = selfHealingLogs,
          onDismiss = { viewModel.showMemoryModal.value = false },
          onAddMemory = { alias, target -> viewModel.addMemory(alias, target) },
          onClearMemory = { viewModel.clearAllMemory() },
          onClearErrorLogs = { viewModel.clearAllErrorLogs() },
          onSimulateError = { viewModel.simulateTestFailure(it) },
          onTriggerSelfHealing = { viewModel.executeSelfHealing() }
        )
      }

      // 6b. Explicit Runtime Permission Modal (Android 12/13+ Microphone & Bluetooth Allow Dialog)
      if (showRuntimePermissionDialog) {
        com.example.ui.permissions.RuntimePermissionModal(
          permissionHandlerState = permissionHandler,
          onDismiss = { showRuntimePermissionDialog = false },
          onOpenSystemSettings = {
            showRuntimePermissionDialog = false
            viewModel.showPermissionsModal.value = true
          },
          onOpenDirectRoutes = {
            showRuntimePermissionDialog = false
            viewModel.showDirectRoutesModal.value = true
          }
        )
      }

      // 7. Settings Dialog
      if (showSettingsModal) {
        SettingsDialog(
          shortcuts = shortcuts,
          contacts = contacts,
          contactAliases = memoryEntries,
          currentConfig = ecoFlowConfig,
          hasContactsPermission = permissionHandler.permissionState.isContactsGranted,
          onRequestContactsPermission = { permissionHandler.requestContacts() },
          permissionState = permissionHandler.permissionState,
          onRequestMicrophone = { permissionHandler.requestMicrophone() },
          onRequestBluetooth = { permissionHandler.requestBluetooth() },
          onRequestLocation = { permissionHandler.requestLocation() },
          onRequestNotification = { permissionHandler.requestNotification() },
          onRequestAllPermissions = { permissionHandler.requestAll() },
          onDismiss = { viewModel.showSettingsModal.value = false },
          onAddShortcut = { title, cmd, target -> viewModel.addCustomShortcut(title, cmd, target) },
          onDeleteShortcut = { id -> viewModel.deleteShortcut(id) },
          onSaveEcoFlowConfig = { ak, sk, sn, host -> viewModel.saveEcoFlowConfig(ak, sk, sn, host) },
          onRefreshContacts = { viewModel.refreshDeviceContacts() },
          onSaveContactAlias = { alias, phone, name -> viewModel.saveContactAlias(alias, phone, name) },
          onDeleteContactAlias = { id -> viewModel.deleteContactAlias(id) },
          onDialPhone = { name, phone -> viewModel.dialContact(name, phone) }
        )
      }

      // 8. System Permissions & Settings Dashboard
      if (showPermissionsModal) {
        androidx.compose.ui.window.Dialog(
          onDismissRequest = { viewModel.showPermissionsModal.value = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
          androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D16)
          ) {
            com.example.permissions.ui.SystemSettingsDashboard(
              viewModel = viewModel.permissionViewModel,
              modifier = Modifier.fillMaxSize(),
              onClose = { viewModel.showPermissionsModal.value = false }
            )
          }
        }
      }

      // 9. KENNEDE KN-5996HRB IR Smart Controller Dialog
      if (showKennedeModal) {
        KennedeIrDialog(
          esp32Status = esp32Status,
          irDb = kennedeIrDb,
          selectedButton = selectedLearningButton,
          isLearning = isLearningActive,
          learningMessage = learningMessage,
          rawReport = rawSignalReport,
          eventLogs = irEventLogs,
          currentHost = viewModel.esp32Client.getHost(),
          hasPhoneIr = viewModel.hasPhoneIrEmitter,
          onLoadPresets = { viewModel.loadKennedePresetCodes() },
          onHostChanged = { viewModel.setEsp32Host(it) },
          onRefreshStatus = { viewModel.refreshEsp32Status() },
          onSelectButton = { viewModel.selectLearningButton(it) },
          onStartLearning = { viewModel.startLearningSelectedButton() },
          onStopLearning = { viewModel.stopLearning() },
          onSendButton = { viewModel.sendKennedeIrButton(it) },
          onFetchRawReport = { viewModel.fetchRawReport() },
          onResetAllCodes = { viewModel.resetAllKennedeCodes() },
          onImportJson = { viewModel.importKennedeCodes(it) },
          automationState = fanAutomationState,
          ecoFlowBatteryPercent = ecoFlowState.batteryPercentage,
          discoveryState = esp32DiscoveryState,
          onStartAutoDiscovery = { viewModel.startEsp32AutoDiscovery() },
          onStopAutoDiscovery = { viewModel.esp32DiscoveryEngine.stopDiscovery() },
          onStartSubnetPingScan = { viewModel.startEsp32SubnetPingScan() },
          onStartSleepTimer = { viewModel.kennedeAutomationEngine.startSleepTimer(it) },
          onCancelSleepTimer = { viewModel.kennedeAutomationEngine.cancelSleepTimer() },
          onToggleEcoFlowPowerLink = { viewModel.kennedeAutomationEngine.setEcoFlowPowerLink(it) },
          onSetLowBatteryThreshold = { viewModel.kennedeAutomationEngine.setLowBatteryThreshold(it) },
          onToggleAutoTurnOffLight = { viewModel.kennedeAutomationEngine.toggleAutoTurnOffLight(it) },
          onToggleAutoReduceSpeed = { viewModel.kennedeAutomationEngine.toggleAutoReduceSpeed(it) },
          onTriggerEcoFlowPowerSaveTest = { viewModel.kennedeAutomationEngine.triggerLowBatteryPowerSave(it) },
          onToggleQuietNightMode = { viewModel.kennedeAutomationEngine.toggleQuietNightMode(it) },
          onStartBatchCalibration = { viewModel.kennedeAutomationEngine.startBatchCalibration() },
          onCancelBatchCalibration = { viewModel.kennedeAutomationEngine.cancelBatchCalibration() },
          onDismiss = { viewModel.showKennedeModal.value = false },
          initialTabIndex = kennedeIrTabIndex
        )
      }

      // 10. Robust API Key Management & Automatic Failover Dialog
      if (showApiKeyModal) {
        androidx.compose.ui.window.Dialog(
          onDismissRequest = { viewModel.showApiKeyModal.value = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
          androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
          ) {
            com.kyawgyi.ir.ui.ApiKeyManagerScreen(
              onNavigateBack = { viewModel.showApiKeyModal.value = false }
            )
          }
        }
      }

      // 11. Notification Management Dialog
      if (showNotificationModal) {
        NotificationManagementDialog(
          notificationHelper = viewModel.notificationHelper,
          onDismiss = { viewModel.showNotificationModal.value = false }
        )
      }

      // 12. EcoFlow Real-time Data Dashboard Screen
      if (showEcoFlowDashboard) {
        androidx.compose.ui.window.Dialog(
          onDismissRequest = { viewModel.showEcoFlowDashboard.value = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
          EcoFlowDashboardScreen(
            state = ecoFlowState,
            lowBatteryThreshold = ecoFlowState.automation.lowBatteryCutoffThreshold,
            onBack = { viewModel.showEcoFlowDashboard.value = false },
            onOpenNotificationSettings = { viewModel.showNotificationModal.value = true },
            onToggleAcPort = { viewModel.setAcPort(it) },
            onToggleDcPort = { viewModel.setDcPort(it) },
            onToggleUsbPort = { viewModel.setUsbPort(it) },
            onToggleMasterPower = { viewModel.setMasterPower(it) },
            onSelectModel = { viewModel.selectEcoFlowModel(it) },
            onToggleDiagnosticsMode = { viewModel.setDiagnosticsMode(it) },
            onSyncCloudTelemetry = { viewModel.fetchEcoFlowCloudTelemetry() },
            onScanBle = { viewModel.scanBleDevices() },
            onToggleLowBatteryCutoff = { viewModel.toggleLowBatteryCutoff(it) },
            onSetLowBatteryThreshold = { viewModel.setLowBatteryCutoffThreshold(it) },
            onToggleOverheatProtection = { viewModel.toggleOverheatProtection(it) },
            onToggleOverloadProtection = { viewModel.toggleOverloadProtection(it) },
            onToggleGridOutageUps = { viewModel.toggleGridOutageUps(it) },
            onTestEmergencyAutomation = { viewModel.testEmergencyAutomation() },
            config = ecoFlowConfig,
            onSaveConfig = { ak, sk, sn, host -> viewModel.saveEcoFlowConfig(ak, sk, sn, host) }
          )
        }
      }

      // 13. Bluetooth Device Discovery & Connection Dialog
      if (showBluetoothModal) {
        com.example.bluetooth.ui.BluetoothDiscoveryDialog(
          bluetoothManager = viewModel.realBluetoothManager,
          onDismiss = { viewModel.showBluetoothModal.value = false }
        )
      }

      // 14. AI Angel Chatbox Dialog
      if (showAiAngelModal) {
        AiAngelChatDialog(
          viewModel = angelChatViewModel,
          onDismiss = { viewModel.showAiAngelModal.value = false },
          onOpenMusicLibrary = { viewModel.showMusicLibraryModal.value = true },
          onOpenDiagnostics = { viewModel.showAssetDiagnosticsModal.value = true }
        )
      }

      // 15. Music Library Dialog
      if (showMusicLibraryModal) {
        MusicLibraryDialog(
          musicRepository = viewModel.musicRepository,
          playbackManager = viewModel.musicPlaybackManager,
          onDismiss = { viewModel.showMusicLibraryModal.value = false }
        )
      }

      // 16. Asset Diagnostics Dialog
      if (showAssetDiagnosticsModal) {
        AssetDiagnosticsDialog(
          database = viewModel.db,
          onDismiss = { viewModel.showAssetDiagnosticsModal.value = false }
        )
      }

      // 17. AI Voice Studio (WAV, STT & Myanmar Grammar Engine)
      if (showAiVoiceStudio) {
        val ttsSettings by viewModel.ttsModelManager.state.collectAsState()
        AiVoiceStudioDialog(
          sttManager = viewModel.myanmarSttManager,
          selectedTtsProvider = ttsSettings.selectedProvider,
          onSelectTtsProvider = { provider -> viewModel.ttsModelManager.setProvider(provider) },
          onPlayTts = { text, provider -> viewModel.playVoiceStudioTts(text, provider) },
          onExecuteCommand = { cmd -> viewModel.processVoiceCommand(cmd) },
          onDismiss = { viewModel.showAiVoiceStudio.value = false }
        )
      }

      // 18. Direct Routes & Permissions Explorer Dialog
      if (showDirectRoutesModal) {
        DirectRoutesDialog(
          onNavigateAppScreen = { routeKey ->
            viewModel.showDirectRoutesModal.value = false
            viewModel.navigateToDirectRoute(routeKey)
          },
          onDismiss = { viewModel.showDirectRoutesModal.value = false }
        )
      }

      // 19. ESP32 & KENNEDE IR Real-Time Monitor Screen
      if (showEsp32MonitoringScreen) {
        androidx.compose.ui.window.Dialog(
          onDismissRequest = { viewModel.showEsp32MonitoringScreen.value = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
          Esp32MonitoringScreen(
            viewModel = viewModel,
            onNavigateBack = { viewModel.showEsp32MonitoringScreen.value = false }
          )
        }
      }
    }
  }
}
