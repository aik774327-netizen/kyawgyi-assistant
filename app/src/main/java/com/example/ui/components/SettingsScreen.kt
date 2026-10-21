package com.example.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.LanguagePreferencesRepository
import com.example.data.VoiceLanguage
import com.example.ui.theme.*
import com.example.utils.AppPermission
import com.example.utils.MyanmarTTSEngine
import com.example.utils.PermissionCategory
import com.example.utils.PermissionManager
import kotlinx.coroutines.launch
import android.util.Log

/**
 * SettingsScreen component that displays a list of required app permissions
 * (like microphone, notification access, and overlay) and provides toggle buttons
 * to check and request these permissions dynamically.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    selectedLanguage: String? = null,
    onLanguageSelected: ((String) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val languageRepo = remember { LanguagePreferencesRepository.getInstance(context) }
    val dataStoreLang by languageRepo.selectedLanguageState.collectAsState()

    // Local state tracking selected language preference (my-MM or en-US) with DataStore backing
    var localSelectedLanguage by remember(selectedLanguage, dataStoreLang) {
        mutableStateOf(selectedLanguage ?: dataStoreLang)
    }
    val activeLanguage = localSelectedLanguage
    var isTestingTts by remember { mutableStateOf(false) }

    // Ensure the voice pipeline initializes with the chosen locale on startup and when local state updates
    LaunchedEffect(localSelectedLanguage) {
        val targetLocale = languageRepo.createLocale(localSelectedLanguage)
        try {
            MyanmarTTSEngine.getInstance(context).setLanguage(targetLocale)
            Log.i("SettingsScreen", "Voice pipeline successfully initialized with locale: ${targetLocale.toLanguageTag()}")
        } catch (e: Exception) {
            Log.w("SettingsScreen", "Voice pipeline initialization note: ${e.message}")
        }
    }

    var permissions by remember { mutableStateOf(PermissionManager.getRequiredPermissionsList(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var showDirectSettingsDialog by remember { mutableStateOf(false) }
    var showSetupWizardDialog by remember { mutableStateOf(false) }
    var showDiagnosticsOverlay by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        permissions = PermissionManager.getRequiredPermissionsList(context)
    }

    // Auto-refresh when returning from background/system settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ActivityResultLaunchers for Runtime Permissions
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissions()
        val msg = if (isGranted) "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် ရရှိပါပြီ ✓" else "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် ငြင်းပယ်ခံရပါသည်"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissions()
        val msg = if (isGranted) "Notification ခွင့်ပြုချက် ရရှိပါပြီ ✓" else "Notification ခွင့်ပြုချက် ငြင်းပယ်ခံရပါသည်"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissions()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "ခွင့်ပြုချက်များ အားလုံး ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "အချို့ ခွင့်ပြုချက်များ မရရှိသေးပါ", Toast.LENGTH_SHORT).show()
        }
    }

    val totalCount = permissions.size
    val grantedCount = permissions.count { it.isGranted }
    val allCriticalGranted = permissions.filter { it.isCritical }.all { it.isGranted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AngelDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen_component"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Column {
                    Text(
                        text = "အက်ပ် ဆက်တင်များနှင့် ဘာသာစကား",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Voice Pipeline Locale • Jetpack DataStore • Permissions",
                        fontSize = 11.sp,
                        color = AngelCyanAccent
                    )
                }
            }

            IconButton(onClick = { refreshPermissions() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Settings",
                    tint = AngelNeonGreen
                )
            }
        }

        // =========================================================================
        // ON-DEVICE WAKE-WORD MODEL ("ကျော်ကြီး" နှိုးစကားလုံးစနစ်) SETTING
        // =========================================================================
        if (selectedCategoryIndex == 0 || selectedCategoryIndex == 1 || selectedCategoryIndex == 4) {
            WakeWordSettingCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // =========================================================================
        // PREFERRED VOICE LANGUAGE & DYNAMIC LOCALE (DATASTORE PERSISTENT)
        // =========================================================================
        if (selectedCategoryIndex == 0 || selectedCategoryIndex == 1) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, AngelCyanAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("settings_screen_language_section"),
                color = AngelCardDark
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AngelCyanAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Language Settings",
                                    tint = AngelCyanAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "စကားပြောနှင့် အသံဘာသာစကား (Voice Language)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Jetpack DataStore ဖြင့် အမြဲသိမ်းဆည်းပြီး Voice Pipeline သို့ Dynamic Locale ထည့်သွင်းပေးပါသည်",
                                fontSize = 11.sp,
                                color = AngelTextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Locale Banner
                    val supportedLanguages = LanguagePreferencesRepository.SUPPORTED_LANGUAGES
                    val currentVoiceLang = supportedLanguages.firstOrNull { it.code.equals(activeLanguage, ignoreCase = true) }
                        ?: supportedLanguages.first()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AngelNeonGreen.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = AngelNeonGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "လက်ရှိ အသံ: ${currentVoiceLang.flagEmoji} ${currentVoiceLang.displayNameMM} (${currentVoiceLang.code})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AngelNeonGreen
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AngelCyanAccent.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "DATASTORE PERSISTENT",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AngelCyanAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Supported Languages List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        supportedLanguages.forEach { lang ->
                            val isSelected = lang.code.equals(activeLanguage, ignoreCase = true)
                            val tagSuffix = lang.code.lowercase().replace('-', '_')

                            Surface(
                                onClick = {
                                    localSelectedLanguage = lang.code
                                    coroutineScope.launch {
                                        languageRepo.savePreferredLanguage(lang.code)
                                        try {
                                            val loc = languageRepo.createLocale(lang.code)
                                            MyanmarTTSEngine.getInstance(context).setLanguage(loc)
                                        } catch (e: Exception) {
                                            Log.w("SettingsScreen", "TTS locale error: ${e.message}")
                                        }
                                    }
                                    onLanguageSelected?.invoke(lang.code)
                                    Toast.makeText(
                                        context,
                                        "အသံဘာသာစကားကို '${lang.displayNameMM}' သို့ ပြောင်းလဲပြီး Voice Pipeline ကို စတင်သတ်မှတ်ပါပြီ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("settings_screen_language_option_$tagSuffix"),
                                color = if (isSelected) AngelDarkBackground.copy(alpha = 0.85f) else AngelDarkBackground.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = lang.flagEmoji,
                                            fontSize = 22.sp
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = lang.displayNameMM,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)
                                                )
                                                if (lang.isDefault) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = AngelCyanAccent.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "DEFAULT",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = AngelCyanAccent,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "${lang.displayNameEN} • ${lang.nativeName}",
                                                fontSize = 11.sp,
                                                color = AngelTextSecondary
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) AngelNeonGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                                            )
                                        ) {
                                            Text(
                                                text = lang.code,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) AngelNeonGreen else AngelTextSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                if (!isSelected) {
                                                    localSelectedLanguage = lang.code
                                                    coroutineScope.launch {
                                                        languageRepo.savePreferredLanguage(lang.code)
                                                        try {
                                                            val loc = languageRepo.createLocale(lang.code)
                                                            MyanmarTTSEngine.getInstance(context).setLanguage(loc)
                                                        } catch (e: Exception) {
                                                            Log.w("SettingsScreen", "TTS locale error: ${e.message}")
                                                        }
                                                    }
                                                    onLanguageSelected?.invoke(lang.code)
                                                }
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = AngelNeonGreen,
                                                unselectedColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.testTag("language_option_$tagSuffix")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Pipeline Locale Dynamic Diagnostics & Speech Test Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AngelDarkBackground.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = AngelCyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Voice Pipeline Dynamic Locale Status",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "Ready ✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelNeonGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "• STT Language: $activeLanguage",
                                        fontSize = 11.sp,
                                        color = AngelTextSecondary
                                    )
                                    Text(
                                        text = "• TTS Voice Locale: $activeLanguage",
                                        fontSize = 11.sp,
                                        color = AngelTextSecondary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "• DataStore: Preferences",
                                        fontSize = 11.sp,
                                        color = AngelCyanAccent
                                    )
                                    Text(
                                        text = "• Dynamic Sync: Enabled",
                                        fontSize = 11.sp,
                                        color = AngelNeonGreen
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Test TTS Audio Output Button
                            Button(
                                onClick = {
                                    val greeting = languageRepo.getSampleGreeting(activeLanguage)
                                    val targetLocale = languageRepo.createLocale(activeLanguage)
                                    val ttsEngine = MyanmarTTSEngine.getInstance(context)
                                    ttsEngine.setLanguage(targetLocale)
                                    isTestingTts = true
                                    ttsEngine.speak(greeting) {
                                        isTestingTts = false
                                    }
                                    Toast.makeText(context, "အသံစမ်းသပ်နေပါသည်: '$greeting'", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AngelCyanAccent.copy(alpha = 0.2f),
                                    contentColor = AngelCyanAccent
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_voice_locale_button")
                            ) {
                                Icon(
                                    imageVector = if (isTestingTts) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.PlayArrow,
                                    contentDescription = "Test Voice",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isTestingTts) "အသံထွက်နေပါသည် (Speaking...)" else "ရွေးချယ်ထားသော ဘာသာစကားဖြင့် အသံထွက် စမ်းသပ်မည် (Test TTS Voice)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // STT / TTS MYANMAR ENGINE LIVE INSPECTOR & PIPELINE TESTBENCH
        // =========================================================================
        if (selectedCategoryIndex == 0 || selectedCategoryIndex == 1 || selectedCategoryIndex == 4) {
            MyanmarSttTtsEngineInspectorCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // =========================================================================
        // ADAPTIVE ACOUSTIC SENSING & POWER-SAVING MICROPHONE PANEL
        // =========================================================================
        if (selectedCategoryIndex == 0 || selectedCategoryIndex == 1 || selectedCategoryIndex == 4) {
            AdaptiveMicrophoneSensingPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // =========================================================================
        // DIRECT SETTINGS & PERMISSION ROUTING HUB CARD (တိုက်ရိုက်လမ်းကြောင်း သတ်မှတ်မှု)
        // =========================================================================
        DirectSettingsPermissionHubCard(
            onOpenFullDialog = { showDirectSettingsDialog = true },
            onOpenWizard = { showSetupWizardDialog = true },
            onOpenDiagnostics = { showDiagnosticsOverlay = true },
            modifier = Modifier.fillMaxWidth()
        )

        // Status Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    if (allCriticalGranted) AngelNeonGreen.copy(alpha = 0.4f) else Color(0xFFFFB74D).copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                ),
            color = AngelSurfaceDark
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
                            color = (if (allCriticalGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (allCriticalGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (allCriticalGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (allCriticalGranted) "ခွင့်ပြုချက်များ အဆင်သင့်ဖြစ်ပါသည်" else "အရေးကြီး ခွင့်ပြုချက်များ လိုအပ်ပါသည်",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "ရရှိပြီး: $grantedCount / $totalCount ခု",
                                fontSize = 12.sp,
                                color = if (allCriticalGranted) AngelNeonGreen else Color(0xFFFFB74D)
                            )
                        }
                    }

                    // Request all button
                    if (!allCriticalGranted) {
                        Button(
                            onClick = {
                                val requiredList = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requiredList.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                multiplePermissionsLauncher.launch(requiredList.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AngelNeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("request_all_critical_button")
                        ) {
                            Text("အားလုံးဖွင့်မည်", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) grantedCount.toFloat() / totalCount.toFloat() else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (allCriticalGranted) AngelNeonGreen else AngelCyanAccent,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ခွင့်ပြုချက် ရှာရန် (Search permissions...)", fontSize = 12.sp, color = AngelTextSecondary) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AngelCyanAccent, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = AngelTextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AngelCyanAccent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedContainerColor = AngelSurfaceDark,
                unfocusedContainerColor = AngelSurfaceDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("settings_search_field")
        )

        // Category Filter Chips
        val categoryTitles = listOf("အားလုံး", "Voice Language", "Core", "Notifications", "Power & Background", "System")
        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            containerColor = AngelCardDark,
            contentColor = AngelCyanAccent,
            edgePadding = 4.dp,
            indicator = { tabPositions ->
                if (selectedCategoryIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.then(
                            with(TabRowDefaults) {
                                Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex])
                            }
                        ),
                        color = AngelNeonGreen
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
        ) {
            categoryTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedCategoryIndex == index) AngelNeonGreen else AngelTextSecondary
                        )
                    }
                )
            }
        }

        // Filter permissions based on search and category
        val filteredList = permissions.filter { perm ->
            val matchesCategory = when (selectedCategoryIndex) {
                1 -> false // Focused on Voice Language settings
                2 -> perm.category == PermissionCategory.CORE
                3 -> perm.category == PermissionCategory.NOTIFICATION
                4 -> perm.category == PermissionCategory.BACKGROUND_POWER
                5 -> perm.category == PermissionCategory.SYSTEM_CONTROL
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    perm.titleMM.contains(searchQuery, ignoreCase = true) ||
                    perm.titleEN.contains(searchQuery, ignoreCase = true) ||
                    perm.descriptionMM.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        // Permissions Dynamic List with Toggle Switches
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filteredList.forEach { perm ->
                PermissionToggleCard(
                    permission = perm,
                    onToggle = { isChecked ->
                        handlePermissionToggle(
                            permission = perm,
                            shouldEnable = isChecked,
                            context = context,
                            onAudioRequest = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onNotificationRequest = {
                                PermissionManager.requestPostNotificationsPermission(
                                    launcher = notificationPermissionLauncher,
                                    onAlreadyGranted = {
                                        Toast.makeText(context, "Notification ခွင့်ပြုချက် ရရှိထားပြီးဖြစ်ပါသည်", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onRefresh = { refreshPermissions() }
                        )
                    },
                    onRequestClick = {
                        handlePermissionToggle(
                            permission = perm,
                            shouldEnable = true,
                            context = context,
                            onAudioRequest = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onNotificationRequest = {
                                PermissionManager.requestPostNotificationsPermission(
                                    launcher = notificationPermissionLauncher,
                                    onAlreadyGranted = {
                                        PermissionManager.openNotificationSettings(context)
                                    }
                                )
                            },
                            onRefresh = { refreshPermissions() }
                        )
                    }
                )
            }
        }

        // Bottom App Settings Quick Action Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { PermissionManager.openAppSettings(context) }
                .testTag("open_all_app_settings_card"),
            color = AngelSurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = AngelCyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Android App Info & Advanced Settings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ဖုန်း၏ ပင်မ App ဆက်တင် စာမျက်နှာသို့ သွားရောက်ရန်",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "Open",
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(18.dp)
                )
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
                    val requiredPerms = com.example.utils.KyawGyiPermissionManager.getRequiredPermissions()
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

/**
 * Interactive card component with dynamic toggle switch and action trigger.
 */
@Composable
fun PermissionToggleCard(
    permission: AppPermission,
    onToggle: (Boolean) -> Unit,
    onRequestClick: () -> Unit
) {
    val icon = getPermissionIcon(permission.id)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (permission.isGranted) AngelNeonGreen.copy(alpha = 0.25f) else Color(0xFFFFB74D).copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            )
            .testTag("permission_toggle_card_${permission.id}"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = (if (permission.isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (permission.isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = permission.titleMM,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = permission.titleEN,
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                // Dynamic Toggle Switch
                Switch(
                    checked = permission.isGranted,
                    onCheckedChange = { isChecked ->
                        onToggle(isChecked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AngelNeonGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                        uncheckedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.testTag("switch_${permission.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = permission.descriptionMM,
                fontSize = 11.sp,
                color = AngelTextSecondary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action / Status Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (permission.isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (permission.isGranted) "ခွင့်ပြုထားပြီး ✓" else if (permission.isCritical) "မဖြစ်မနေ လိုအပ်သည် !" else "သတ်မှတ်ရန် လိုအပ်သည်",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (permission.isGranted) Color(0xFF10B981) else Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Request / Configure Action
                OutlinedButton(
                    onClick = onRequestClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (permission.isGranted) AngelCyanAccent else AngelNeonGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (permission.isGranted) AngelCyanAccent.copy(alpha = 0.4f) else AngelNeonGreen.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = if (permission.isGranted) Icons.AutoMirrored.Filled.Launch else Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (permission.isGranted) "Setting ဖွင့်ရန်" else "ခွင့်ပြုမည် >",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun handlePermissionToggle(
    permission: AppPermission,
    shouldEnable: Boolean,
    context: Context,
    onAudioRequest: () -> Unit,
    onNotificationRequest: () -> Unit,
    onRefresh: () -> Unit
) {
    when (permission.id) {
        "RECORD_AUDIO" -> {
            if (!permission.isGranted || shouldEnable) {
                onAudioRequest()
            } else {
                PermissionManager.openAppSettings(context)
            }
        }
        "POST_NOTIFICATIONS" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (!permission.isGranted || shouldEnable)) {
                onNotificationRequest()
            } else {
                PermissionManager.openNotificationSettings(context)
            }
        }
        "SYSTEM_ALERT_WINDOW" -> {
            PermissionManager.requestOverlayPermission(context)
        }
        "ACCESSIBILITY_SERVICE" -> {
            PermissionManager.requestAccessibilityPermission(context)
        }
        "WRITE_SETTINGS" -> {
            PermissionManager.requestWriteSettingsPermission(context)
        }
        "SCHEDULE_EXACT_ALARM" -> {
            PermissionManager.requestExactAlarmPermission(context)
        }
        "IGNORE_BATTERY_OPTIMIZATIONS" -> {
            PermissionManager.requestIgnoreBatteryOptimization(context)
        }
        else -> {
            PermissionManager.openAppSettings(context)
        }
    }
    onRefresh()
}

private fun getPermissionIcon(id: String): ImageVector {
    return when (id) {
        "RECORD_AUDIO" -> Icons.Default.Mic
        "POST_NOTIFICATIONS" -> Icons.Default.Notifications
        "SYSTEM_ALERT_WINDOW" -> Icons.Default.Layers
        "ACCESSIBILITY_SERVICE" -> Icons.Default.AccessibilityNew
        "WRITE_SETTINGS" -> Icons.Default.SettingsSuggest
        "SCHEDULE_EXACT_ALARM" -> Icons.Default.Alarm
        "IGNORE_BATTERY_OPTIMIZATIONS" -> Icons.Default.BatteryChargingFull
        else -> Icons.Default.Security
    }
}
