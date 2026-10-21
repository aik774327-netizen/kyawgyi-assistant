package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.ui.theme.*
import com.example.utils.DirectSettingShortcut
import com.example.utils.KyawGyiPermissionManager
import com.example.utils.PermissionItemInfo

@Composable
fun PermissionSettingsControlCard(
    onOpenFullSettings: () -> Unit,
    onOpenWizard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionsList by remember { mutableStateOf(KyawGyiPermissionManager.getAllPermissionsStatus(context)) }
    val grantedCount = permissionsList.count { it.isGranted }
    val totalCount = permissionsList.size

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

    Card(
        onClick = onOpenFullSettings,
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_settings_overview_card"),
        colors = CardDefaults.cardColors(
            containerColor = AngelSurfaceDark
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (grantedCount >= 3) AngelNeonGreen.copy(alpha = 0.4f) else Color(0xFFFFB74D).copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = (if (grantedCount >= 3) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Permissions",
                                tint = if (grantedCount >= 3) AngelNeonGreen else Color(0xFFFFB74D),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "စနစ် ခွင့်ပြုချက်နှင့် ဆက်တင် တိုက်ရိုက်လမ်းကြောင်း",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ခွင့်ပြုချက် အခြေအနေ: $grantedCount / $totalCount ခု ပြီးစီး",
                            fontSize = 12.sp,
                            color = if (grantedCount >= 3) AngelNeonGreen else Color(0xFFFFB74D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "မိုက်ခရိုဖုန်း၊ Overlay၊ Accessibility၊ Exact Alarm နှင့် Battery Settings များကို တိုက်ရိုက်သွားရောက် သတ်မှတ်ရန် နှိပ်ပါ",
                fontSize = 11.sp,
                color = AngelTextSecondary,
                lineHeight = 16.sp
            )

            if (onOpenWizard != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("overview_open_wizard_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelCyanAccent.copy(alpha = 0.18f),
                        contentColor = AngelCyanAccent
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("✨ Setup & Permission Wizard ဖွင့်မည် (၁၀ ဆင့်)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSettingsDialog(
    onDismiss: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onOpenWizard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var permissionsList by remember { mutableStateOf(KyawGyiPermissionManager.getAllPermissionsStatus(context)) }
    var shortcutsList by remember { mutableStateOf(KyawGyiPermissionManager.getAllDirectShortcuts(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    fun refreshStatus() {
        permissionsList = KyawGyiPermissionManager.getAllPermissionsStatus(context)
        shortcutsList = KyawGyiPermissionManager.getAllDirectShortcuts(context)
    }

    var pendingFallbackAction by remember { mutableStateOf<((android.content.Context) -> Unit)?>(null) }
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus()
        if (granted) {
            Toast.makeText(context, "ခွင့်ပြုချက် အောင်မြင်စွာ ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            pendingFallbackAction?.invoke(context)
        }
    }

    val requestAllPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshStatus()
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "ခွင့်ပြုချက်များ အားလုံး အောင်မြင်စွာ ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "ခွင့်ပြုချက် အချို့ မရရှိသေးပါ (ဆက်တင်တွင် ထပ်မံဖွင့်နိုင်ပါသည်)", Toast.LENGTH_SHORT).show()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("permission_settings_full_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AngelDarkBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "စနစ် ဆက်တင် တိုက်ရိုက်လမ်းကြောင်းများ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = { refreshStatus() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "Android System Settings နှင့် ခွင့်ပြုချက်များကို တစ်ချက်နှိပ်ရုံဖြင့် တိုက်ရိုက်ဖွင့်လှစ်နိုင်ပါသည်:",
                    fontSize = 11.sp,
                    color = AngelTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ခွင့်ပြုချက်နှင့် Setting ရှာရန်...", fontSize = 12.sp, color = AngelTextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AngelCyanAccent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Category Filter Tabs
                val tabTitles = listOf("အားလုံး", "ခွင့်ပြုချက်များ", "Background & Power", "စနစ် ဖြတ်လမ်း")
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AngelCardDark,
                    contentColor = AngelCyanAccent,
                    edgePadding = 4.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.then(
                                    with(TabRowDefaults) {
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTab])
                                    }
                                ),
                                color = AngelNeonGreen
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) AngelNeonGreen else AngelTextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filtered Content
                val filteredShortcuts = shortcutsList.filter { shortcut ->
                    val matchesCategory = when (selectedTab) {
                        1 -> shortcut.category == "CORE_PERM"
                        2 -> shortcut.category == "POWER_BG" || shortcut.category == "NOTIFY_DND"
                        3 -> shortcut.category == "QUICK_SYS"
                        else -> true
                    }
                    val matchesSearch = searchQuery.isBlank() ||
                            shortcut.titleMM.contains(searchQuery, ignoreCase = true) ||
                            shortcut.titleEN.contains(searchQuery, ignoreCase = true) ||
                            shortcut.descriptionMM.contains(searchQuery, ignoreCase = true)
                    matchesCategory && matchesSearch
                }

                // Scrollable List of Settings
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 2) {
                        OemAutoStartGuidanceCard(modifier = Modifier.padding(bottom = 6.dp))
                    }

                    if (filteredShortcuts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ကိုက်ညီသော Setting ရှာမတွေ့ပါ",
                                fontSize = 12.sp,
                                color = AngelTextSecondary
                            )
                        }
                    } else {
                        filteredShortcuts.forEach { shortcut ->
                            DirectShortcutCard(
                                item = shortcut,
                                onActionClick = {
                                    if (shortcut.isPermission && !shortcut.isGranted && !shortcut.runtimePermissionName.isNullOrBlank()) {
                                        pendingFallbackAction = shortcut.directAction
                                        singlePermissionLauncher.launch(shortcut.runtimePermissionName)
                                    } else {
                                        shortcut.directAction(context)
                                        refreshStatus()
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Primary 1-Tap Request All Button
                Button(
                    onClick = {
                        val requiredPerms = KyawGyiPermissionManager.getRequiredPermissions()
                        requestAllPermissionsLauncher.launch(requiredPerms)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("dialog_request_all_perms_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelNeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("⚡ ခွင့်ပြုချက်များ အားလုံး တောင်းခံမည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (onOpenWizard != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenWizard,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("dialog_open_wizard_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelCyanAccent.copy(alpha = 0.2f),
                            contentColor = AngelCyanAccent
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("✨ အဆင့်ဆင့် Setup Wizard ဖွင့်မည် (၁၀ ဆင့်)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            KyawGyiPermissionManager.openAppDetailsSettings(context)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("အက်ပ် Setting >", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AngelCardDark, contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ပိတ်မည်", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DirectShortcutCard(
    item: DirectSettingShortcut,
    onActionClick: () -> Unit
) {
    val icon: ImageVector = getIconForName(item.iconName)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AngelCardDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.isPermission && !item.isGranted) Color(0xFFFFB74D).copy(alpha = 0.4f) else AngelNeonGreen.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() }
            .testTag("shortcut_card_${item.id}")
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
                        color = (if (item.isPermission && !item.isGranted) Color(0xFFFFB74D) else AngelCyanAccent).copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (item.isPermission && !item.isGranted) Color(0xFFFFB74D) else AngelCyanAccent,
                                modifier = Modifier.size(18.dp)
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

                if (item.isPermission) {
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
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AngelCyanAccent.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "တိုက်ရိုက်ဖွင့်ရန် >",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AngelCyanAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.descriptionMM,
                fontSize = 11.sp,
                color = AngelTextSecondary,
                lineHeight = 15.sp
            )

            if (item.routeActionLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Route: ${item.routeActionLabel}",
                    fontSize = 9.sp,
                    color = AngelCyanAccent.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isPermission && !item.isGranted) AngelCyanAccent else AngelSurfaceDark,
                    contentColor = if (item.isPermission && !item.isGranted) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.isPermission && !item.isGranted) "Setting သို့ တိုက်ရိုက်သွားမည် >" else "Setting ဖွင့်မည်",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    item: PermissionItemInfo,
    onActionClick: () -> Unit
) {
    val icon: ImageVector = when (item.id) {
        "RECORD_AUDIO" -> Icons.Default.Mic
        "READ_CONTACTS" -> Icons.Default.Contacts
        "CALL_PHONE" -> Icons.Default.Phone
        "CAMERA" -> Icons.Default.CameraAlt
        "SYSTEM_ALERT_WINDOW" -> Icons.Default.Layers
        "ACCESSIBILITY_SERVICE" -> Icons.Default.AccessibilityNew
        "BATTERY_OPTIMIZATION" -> Icons.Default.BatteryChargingFull
        "OEM_AUTOSTART" -> Icons.Default.Shield
        "DEFAULT_ASSISTANT" -> Icons.Default.Assistant
        "POST_NOTIFICATIONS" -> Icons.Default.Notifications
        "NOTIFICATION_POLICY" -> Icons.Default.DoNotDisturbOn
        "WRITE_SETTINGS" -> Icons.Default.SettingsSuggest
        "EXACT_ALARM" -> Icons.Default.Alarm
        "APP_DETAILS" -> Icons.Default.Info
        else -> Icons.Default.Settings
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AngelCardDark,
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
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (item.isGranted) Color(0xFF10B981) else Color(0xFFFFB74D),
                                modifier = Modifier.size(18.dp)
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

                // Status Badge
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
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isGranted) AngelSurfaceDark else AngelCyanAccent,
                    contentColor = if (item.isGranted) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("open_setting_btn_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.isGranted) "Setting ပြန်လည်စစ်ဆေးမည်" else "Setting သို့ တိုက်ရိုက်သွားမည် >",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getIconForName(iconName: String): ImageVector {
    return when (iconName) {
        "Info" -> Icons.Default.Info
        "Mic" -> Icons.Default.Mic
        "Contacts" -> Icons.Default.Contacts
        "Phone" -> Icons.Default.Phone
        "Sms" -> Icons.Default.Sms
        "CameraAlt" -> Icons.Default.CameraAlt
        "LibraryMusic" -> Icons.Default.LibraryMusic
        "Layers" -> Icons.Default.Layers
        "AccessibilityNew" -> Icons.Default.AccessibilityNew
        "SettingsSuggest" -> Icons.Default.SettingsSuggest
        "BatteryChargingFull" -> Icons.Default.BatteryChargingFull
        "Shield" -> Icons.Default.Shield
        "Alarm" -> Icons.Default.Alarm
        "Notifications" -> Icons.Default.Notifications
        "Timer" -> Icons.Default.Timer
        "DoNotDisturbOn" -> Icons.Default.DoNotDisturbOn
        "Assistant" -> Icons.Default.Assistant
        "Wifi" -> Icons.Default.Wifi
        "Bluetooth" -> Icons.Default.Bluetooth
        "VolumeUp" -> Icons.AutoMirrored.Filled.VolumeUp
        "BrightnessMedium" -> Icons.Default.BrightnessMedium
        "LocationOn" -> Icons.Default.LocationOn
        "AccessTime" -> Icons.Default.AccessTime
        "Storage" -> Icons.Default.Storage
        "PermDeviceInformation" -> Icons.Default.PermDeviceInformation
        else -> Icons.Default.Settings
    }
}

