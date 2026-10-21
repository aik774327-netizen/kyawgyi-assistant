package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.DeviceAudioMediaScanner
import com.example.audio.KyawGyiMusicPlayer
import com.example.audio.LocalAudioTrack
import com.example.discovery.DeviceContact
import com.example.discovery.DeviceContactResolver
import com.example.discovery.DynamicPackageDiscovery
import com.example.discovery.InstalledAppInfo
import com.example.discovery.UniversalAppLauncher
import com.example.router.KyawGyiSmartRouter
import com.example.utils.KyawGyiPermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScannerCard(
    modifier: Modifier = Modifier,
    onVoiceCommandTriggered: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableIntStateOf(0) } // 0: Contacts, 1: Apps, 2: MP3 Music
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    var contactsList by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var appsList by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var musicList by remember { mutableStateOf<List<LocalAudioTrack>>(emptyList()) }

    var callTestResult by remember { mutableStateOf<String?>(null) }
    var isExecutingCall by remember { mutableStateOf(false) }

    val musicPlayer = remember { KyawGyiMusicPlayer.getInstance(context) }
    val playbackState by musicPlayer.playerState.collectAsState()

    var hasContactPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasAudioPermission by remember {
        mutableStateOf(KyawGyiPermissionManager.hasAudioStoragePermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasContactPermission = permissions[Manifest.permission.READ_CONTACTS] == true
        hasCallPermission = permissions[Manifest.permission.CALL_PHONE] == true
        hasAudioPermission = KyawGyiPermissionManager.hasAudioStoragePermission(context)

        if (hasContactPermission) {
            scope.launch {
                isScanning = true
                contactsList = withContext(Dispatchers.IO) {
                    DeviceContactResolver.getInstance(context).getAllContacts(forceRefresh = true)
                }
                isScanning = false
            }
        }
        if (hasAudioPermission) {
            scope.launch {
                isScanning = true
                musicList = withContext(Dispatchers.IO) {
                    DeviceAudioMediaScanner.getInstance(context).getAllAudioTracks(forceRefresh = true)
                }
                isScanning = false
            }
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        scope.launch {
            isScanning = true
            appsList = withContext(Dispatchers.IO) {
                DynamicPackageDiscovery.getInstance(context).getAllLaunchableApps()
            }
            if (hasContactPermission) {
                contactsList = withContext(Dispatchers.IO) {
                    DeviceContactResolver.getInstance(context).getAllContacts(forceRefresh = false)
                }
            }
            musicList = withContext(Dispatchers.IO) {
                DeviceAudioMediaScanner.getInstance(context).getAllAudioTracks(forceRefresh = false)
            }
            isScanning = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_scanner_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Device Scanner",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ဖုန်းတွင်း Apps, Contacts & MP3 တိုက်ရိုက် Scan",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "စကားသံဖြင့် ဖုန်းခေါ်ဆို၊ အက်ပ်ဖွင့် & သီချင်းဖွင့်နိုင်သည်",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            when (selectedMode) {
                                0 -> {
                                    if (!hasContactPermission) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.CALL_PHONE
                                            )
                                        )
                                    } else {
                                        contactsList = withContext(Dispatchers.IO) {
                                            DeviceContactResolver.getInstance(context).getAllContacts(forceRefresh = true)
                                        }
                                    }
                                }
                                1 -> {
                                    appsList = withContext(Dispatchers.IO) {
                                        DynamicPackageDiscovery.getInstance(context).refreshInstalledApps("USER_REFRESH")
                                    }
                                }
                                2 -> {
                                    if (!hasAudioPermission) {
                                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        permissionLauncher.launch(perms)
                                    }
                                    musicList = withContext(Dispatchers.IO) {
                                        DeviceAudioMediaScanner.getInstance(context).getAllAudioTracks(forceRefresh = true)
                                    }
                                }
                            }
                            isScanning = false
                        }
                    },
                    modifier = Modifier.testTag("refresh_scanner_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Refresh",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Mode Selector Tabs (Contacts, Apps, MP3 Music)
            PrimaryTabRow(
                selectedTabIndex = selectedMode,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedMode == 0,
                    onClick = { selectedMode = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Contacts (${contactsList.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_contacts_scanner")
                )
                Tab(
                    selected = selectedMode == 1,
                    onClick = { selectedMode = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Apps (${appsList.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_apps_scanner")
                )
                Tab(
                    selected = selectedMode == 2,
                    onClick = { selectedMode = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("MP3 (${musicList.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    modifier = Modifier.testTag("tab_music_scanner")
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scanner_search_input"),
                placeholder = {
                    Text(
                        when (selectedMode) {
                            0 -> "အမည် သို့မဟုတ် ဖုန်းနံပါတ် ရှာပါ..."
                            1 -> "အက်ပ်အမည် ရှာဖွေပါ..."
                            else -> "သီချင်းအမည် သို့မဟုတ် အဆိုတော် ရှာပါ..."
                        },
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Permission Warning if Contacts selected and permission not granted
            if (selectedMode == 0 && !hasContactPermission) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ဖုန်း Contact app ကို တိုက်ရိုက်ဖတ်ရှုရန် Permission လိုအပ်ပါသည်",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.CALL_PHONE
                                    )
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_contact_permission_button")
                        ) {
                            Text("ခွင့်ပြုမည်", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Permission Warning if Music selected and permission not granted
            if (selectedMode == 2 && !hasAudioPermission) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ဖုန်းထဲရှိ MP3 ဖိုင်များကို ဖတ်ရှုရန် Storage/Audio Permission လိုအပ်ပါသည်",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                } else {
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(perms)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_audio_permission_button")
                        ) {
                            Text("ခွင့်ပြုမည်", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Interactive Voice Call Test Bar (Quick Voice Command Trial)
            if (selectedMode == 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("စကားသံဖြင့် ဖုန်းခေါ်ဆိုမှု နမူနာများ စမ်းသပ်ရန်:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Sample chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sampleCommands = listOf("အမေဆီ ဖုန်းခေါ်ပေးပါ", "မောင်မောင့်ကို ဖုန်းဆက်ပါ", "ဖုန်းခေါ်မယ် 09")
                            sampleCommands.forEach { cmd ->
                                SuggestionChip(
                                    onClick = {
                                        isExecutingCall = true
                                        scope.launch {
                                            val smartRouter = KyawGyiSmartRouter(context)
                                            val resp = withContext(Dispatchers.IO) {
                                                smartRouter.processVoiceInput(cmd)
                                            }
                                            callTestResult = resp
                                            isExecutingCall = false
                                            onVoiceCommandTriggered?.invoke(cmd)
                                        }
                                    },
                                    label = { Text(cmd, fontSize = 10.sp) },
                                    modifier = Modifier.testTag("sample_call_chip_$cmd")
                                )
                            }
                        }

                        AnimatedVisibility(visible = callTestResult != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = callTestResult ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive MP3 Voice Command Test Bar
            if (selectedMode == 2) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Headset, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                            Text("အသံဖြင့် MP3 သီချင်းဖွင့် စမ်းသပ်ရန်:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val sampleMusicCmds = listOf("မာယာသီချင်း ဖွင့်ပေးပါ", "မိုးသီချင်း ဖွင့်ပေးပါ", "သီချင်းခေတ္တရပ်ပါ")
                            sampleMusicCmds.forEach { cmd ->
                                SuggestionChip(
                                    onClick = {
                                        scope.launch {
                                            val smartRouter = KyawGyiSmartRouter(context)
                                            val resp = withContext(Dispatchers.IO) {
                                                smartRouter.processVoiceInput(cmd)
                                            }
                                            callTestResult = resp
                                            onVoiceCommandTriggered?.invoke(cmd)
                                        }
                                    },
                                    label = { Text(cmd, fontSize = 10.sp) },
                                    modifier = Modifier.testTag("sample_music_chip_$cmd")
                                )
                            }
                        }
                    }
                }

                // Active Now Playing Bar
                if (playbackState.isPlaying || playbackState.currentTrack != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = playbackState.currentTrack?.title ?: "ဖွင့်လက်စ သီချင်း",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playbackState.currentTrack?.artist ?: "Unknown"} • ${if (playbackState.isPlaying) "ဖွင့်နေသည် (Playing)" else "ခေတ္တရပ်ထားသည် (Paused)"}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (playbackState.isPlaying) {
                                            musicPlayer.pause()
                                        } else {
                                            musicPlayer.resume()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { musicPlayer.stop() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Results List
            if (selectedMode == 0) {
                // CONTACTS LIST
                val filteredContacts = remember(contactsList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        contactsList.take(30)
                    } else {
                        contactsList.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                            it.phoneNumber.contains(searchQuery) ||
                            it.normalizedName.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!hasContactPermission) "Permission ခွင့်ပြုပြီးပါက အဆက်အသွယ်များ စကင်ဖတ်နိုင်ပါမည်" else "အဆက်အသွယ် ရှာမတွေ့ပါခင်ဗျာ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredContacts.forEach { contact ->
                            ContactItemRow(
                                contact = contact,
                                onCall = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            DeviceContactResolver.getInstance(context).makePhoneCall(
                                                phoneNumber = contact.phoneNumber,
                                                contactName = contact.displayName
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else if (selectedMode == 1) {
                // APPS LIST
                val filteredApps = remember(appsList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        appsList.take(30)
                    } else {
                        appsList.filter {
                            it.applicationLabel.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true) ||
                            it.normalizedLabel.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("အပလီကေးရှင်း ရှာမတွေ့ပါခင်ဗျာ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredApps.forEach { appInfo ->
                            AppItemRow(
                                appInfo = appInfo,
                                onLaunch = {
                                    UniversalAppLauncher.getInstance(context).launchPackageDirectly(
                                        packageName = appInfo.packageName,
                                        applicationLabel = appInfo.applicationLabel
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                // MP3 MUSIC LIST
                val filteredMusic = remember(musicList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        musicList.take(30)
                    } else {
                        musicList.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true) ||
                            it.normalizedTitle.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                if (filteredMusic.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!hasAudioPermission) "Permission ခွင့်ပြုပြီးပါက ဖုန်းတွင်း MP3 ဖိုင်များ စကင်ဖတ်နိုင်ပါမည်" else "MP3 သီချင်း ရှာမတွေ့ပါခင်ဗျာ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredMusic.forEach { track ->
                            MusicItemRow(
                                track = track,
                                isCurrentlyPlaying = playbackState.isPlaying && playbackState.currentTrack?.id == track.id,
                                onPlay = {
                                    musicPlayer.playTrack(track)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItemRow(
    contact: DeviceContact,
    onCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_item_${contact.displayName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.displayName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(
                        text = contact.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .testTag("call_btn_${contact.displayName}")
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call ${contact.displayName}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AppItemRow(
    appInfo: InstalledAppInfo,
    onLaunch: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${appInfo.applicationLabel}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (appInfo.isSystemApp) Icons.Default.SettingsApplications else Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = appInfo.applicationLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = appInfo.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onLaunch,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                    .testTag("launch_btn_${appInfo.applicationLabel}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = "Open ${appInfo.applicationLabel}",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MusicItemRow(
    track: LocalAudioTrack,
    isCurrentlyPlaying: Boolean,
    onPlay: () -> Unit
) {
    val durationFormatted = remember(track.durationMs) {
        val totalSecs = track.durationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        "%d:%02d".format(mins, secs)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("music_item_${track.title}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentlyPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = track.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • $durationFormatted",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .testTag("play_btn_${track.title}")
            ) {
                Icon(
                    imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play ${track.title}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

