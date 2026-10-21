package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.router.DirectSettingsRouter
import com.example.router.SettingsDestination
import com.example.ui.theme.*
import com.example.utils.KyawGyiPermissionManager

/**
 * KYAW GYI AI — Direct Settings & Permission Routes Hub
 * (ပရောဂျက်အတွင်းထည့်သွင်းရမဲ့ Setting နှင့် Permission များ တိုက်ရိုက်လမ်းကြောင်း စီမံခန့်ခွဲမှု)
 */
@Composable
fun DirectSettingsPermissionHubCard(
    onOpenFullDialog: () -> Unit,
    onOpenWizard: (() -> Unit)? = null,
    onOpenDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val router = remember { DirectSettingsRouter.getInstance(context) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var lastRouteStatusMessage by remember { mutableStateOf<String?>(null) }

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

    val requestMultipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshTrigger++
        val allGranted = results.values.all { it }
        if (allGranted) {
            lastRouteStatusMessage = "Runtime ခွင့်ပြုချက်များ အားလုံး အောင်မြင်စွာ ရရှိပါပြီ ✓"
            Toast.makeText(context, "ခွင့်ပြုချက်များ အားလုံး အောင်မြင်စွာ ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else {
            lastRouteStatusMessage = "ခွင့်ပြုချက် အချို့ မရရှိသေးပါ — သက်ဆိုင်ရာ လမ်းကြောင်းခလုတ်ကို နှိပ်၍ တိုက်ရိုက်ဖွင့်နိုင်ပါသည်"
            Toast.makeText(context, "ခွင့်ပြုချက် အချို့ မရရှိသေးပါ (Setting တွင် တိုက်ရိုက်ဖွင့်နိုင်ပါသည်)", Toast.LENGTH_SHORT).show()
        }
    }

    var pendingDestinationForFallback by remember { mutableStateOf<SettingsDestination?>(null) }
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTrigger++
        val targetDest = pendingDestinationForFallback
        if (granted) {
            lastRouteStatusMessage = "${targetDest?.titleMM ?: "ခွင့်ပြုချက်"} အောင်မြင်စွာ ရရှိပါပြီ ✓"
            Toast.makeText(context, "${targetDest?.titleMM ?: "ခွင့်ပြုချက်"} ရရှိပါပြီ ✓", Toast.LENGTH_SHORT).show()
        } else if (targetDest != null) {
            val report = router.openSettingsWithReport(targetDest)
            lastRouteStatusMessage = report.messageMM
            Toast.makeText(context, report.messageMM, Toast.LENGTH_SHORT).show()
        }
    }

    data class DirectRoute(
        val destination: SettingsDestination,
        val icon: ImageVector,
        val titleMM: String,
        val subtitleMM: String,
        val filterGroup: String, // RUNTIME, SPECIAL_OS, BG_OEM, VOICE_SYS
        val testTag: String
    )

    val allProjectRoutes = remember(refreshTrigger) {
        listOf(
            // 1. Runtime Permissions (Direct Runtime Prompt + Package Deep-Link)
            DirectRoute(
                destination = SettingsDestination.MICROPHONE_PERMISSION,
                icon = Icons.Default.Mic,
                titleMM = "မိုက်ခရိုဖုန်း ခွင့်ပြုချက် (RECORD_AUDIO)",
                subtitleMM = "အသံအမိန့်များနှင့် 'ကျော်ကြီး' Wake Word ဖမ်းယူရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_mic"
            ),
            DirectRoute(
                destination = SettingsDestination.CALL_PHONE_PERMISSION,
                icon = Icons.Default.Phone,
                titleMM = "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့် (CALL_PHONE)",
                subtitleMM = "အသံဖြင့် ဖုန်းနံပါတ်နှင့် Contact များသို့ တိုက်ရိုက်ခေါ်ဆိုရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_call_phone"
            ),
            DirectRoute(
                destination = SettingsDestination.SEND_SMS_PERMISSION,
                icon = Icons.Default.Sms,
                titleMM = "SMS စာတို ပေးပို့ခွင့် (SEND_SMS)",
                subtitleMM = "အသံအမိန့်ဖြင့် မက်ဆေ့ခ်ျ ရေးသားပေးပို့ရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_send_sms"
            ),
            DirectRoute(
                destination = SettingsDestination.CONTACTS_PERMISSION,
                icon = Icons.Default.Contacts,
                titleMM = "ဖုန်းအဆက်အသွယ် ဖတ်ရှုခွင့် (READ_CONTACTS)",
                subtitleMM = "Contact အမည်များဖြင့် ဖုန်းခေါ်ဆိုရန်နှင့် SMS ပို့ရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_contacts"
            ),
            DirectRoute(
                destination = SettingsDestination.CAMERA_PERMISSION,
                icon = Icons.Default.CameraAlt,
                titleMM = "ကင်မရာနှင့် ဓာတ်မီး ခွင့်ပြုချက် (CAMERA)",
                subtitleMM = "အသံဖြင့် ဓာတ်မီး (Flashlight) နှင့် OCR အသုံးပြုရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_camera"
            ),
            DirectRoute(
                destination = SettingsDestination.AUDIO_STORAGE_PERMISSION,
                icon = Icons.Default.LibraryMusic,
                titleMM = "သီချင်းနှင့် အသံဖိုင် ဖတ်ရှုခွင့် (MEDIA_AUDIO)",
                subtitleMM = "ဖုန်းထဲရှိ MP3 သီချင်းများကို အသံဖြင့် ရှာဖွေဖွင့်လှစ်ရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_audio_storage"
            ),
            DirectRoute(
                destination = SettingsDestination.BLUETOOTH_PERMISSION,
                icon = Icons.Default.Bluetooth,
                titleMM = "ဘလူးတုသ် ချိတ်ဆက်ခွင့် (BLUETOOTH_CONNECT)",
                subtitleMM = "Android 12+ ဘလူးတုသ် နားကြပ်နှင့် ကိရိယာများ ထိန်းချုပ်ရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_bluetooth_perm"
            ),
            DirectRoute(
                destination = SettingsDestination.LOCATION_PERMISSION,
                icon = Icons.Default.LocationOn,
                titleMM = "တည်နေရာ ခွင့်ပြုချက် (FINE_LOCATION)",
                subtitleMM = "တည်နေရာနှင့် မြေပုံ လမ်းညွှန်အမိန့်များ အသုံးပြုရန်",
                filterGroup = "RUNTIME",
                testTag = "direct_route_location_perm"
            ),
            // 2. Special OS Permissions (Package-URI Direct Intent Routes)
            DirectRoute(
                destination = SettingsDestination.OVERLAY_PERMISSION,
                icon = Icons.Default.Layers,
                titleMM = "မျက်နှာပြင်ပေါ် အမြဲပေါ်ခွင့် (Overlay)",
                subtitleMM = "အခြား App သုံးနေစဉ် Floating Mic Ball ပေါ်စေရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_overlay"
            ),
            DirectRoute(
                destination = SettingsDestination.ACCESSIBILITY,
                icon = Icons.Default.AccessibilityNew,
                titleMM = "Accessibility အလိုအလျောက် ထိန်းချုပ်ခွင့်",
                subtitleMM = "စနစ်တွင်း အမိန့်များ (Back/Home/Recents) အလိုအလျောက် ဖွင့်ရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_accessibility"
            ),
            DirectRoute(
                destination = SettingsDestination.WRITE_SETTINGS,
                icon = Icons.Default.SettingsSuggest,
                titleMM = "စနစ် ဆက်တင် ပြင်ဆင်ခွင့် (WRITE_SETTINGS)",
                subtitleMM = "အလင်းအမှောင်၊ အသံအတိုးအကျယ်နှင့် စနစ်ပြင်ဆင်ရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_write_settings"
            ),
            DirectRoute(
                destination = SettingsDestination.APP_NOTIFICATIONS,
                icon = Icons.Default.Notifications,
                titleMM = "အက်ပ် အသိပေးချက် ဆက်တင်များ",
                subtitleMM = "Foreground Service & Alarm အသိပေးရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_notifications"
            ),
            DirectRoute(
                destination = SettingsDestination.NOTIFICATION_POLICY,
                icon = Icons.Default.DoNotDisturbOn,
                titleMM = "အနှောင့်အယှက် မပေးရ (DND) ခွင့်ပြုချက်",
                subtitleMM = "အသံတိတ် Mute စနစ်နှင့် စနစ်အသံ ထိန်းချုပ်ရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_dnd"
            ),
            DirectRoute(
                destination = SettingsDestination.NOTIFICATION_LISTENER,
                icon = Icons.Default.MarkChatRead,
                titleMM = "အသိပေးချက်များ ဖတ်ရှုခွင့်",
                subtitleMM = "ဝင်လာသော စာတိုများကို အသံဖြင့် ဖတ်ကြားစေရန်",
                filterGroup = "SPECIAL_OS",
                testTag = "direct_route_notif_listener"
            ),
            // 3. Background Power & OEM Protection Routes
            DirectRoute(
                destination = SettingsDestination.BATTERY_OPTIMIZATION,
                icon = Icons.Default.BatteryChargingFull,
                titleMM = "ဘက်ထရီ ချွေတာမှု ကင်းလွတ်ခွင့်",
                subtitleMM = "Deep Sleep အိပ်မောကျချိန် Foreground အလုပ်လုပ်ရန်",
                filterGroup = "BG_OEM",
                testTag = "direct_route_battery"
            ),
            DirectRoute(
                destination = SettingsDestination.OEM_AUTOSTART,
                icon = Icons.Default.Shield,
                titleMM = "OEM Auto-Start စောင့်ရှောက်မှု",
                subtitleMM = "Xiaomi / Samsung / Oppo / Vivo Background ကာကွယ်မှု",
                filterGroup = "BG_OEM",
                testTag = "direct_route_oem"
            ),
            DirectRoute(
                destination = SettingsDestination.EXACT_ALARM,
                icon = Icons.Default.Alarm,
                titleMM = "တိကျသော Alarm & Reminders",
                subtitleMM = "Pomodoro နှင့် အချိန်ကိုက် နှိုးစက်များအတွက်",
                filterGroup = "BG_OEM",
                testTag = "direct_route_alarm"
            ),
            // 4. Voice, Assistant & System Control Routes
            DirectRoute(
                destination = SettingsDestination.TTS_SETTINGS,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                titleMM = "မြန်မာ အသံထွက် (TTS) ဆက်တင်",
                subtitleMM = "Android စနစ် TTS အသံထွက် Engine စီမံရန်",
                filterGroup = "VOICE_SYS",
                testTag = "direct_route_tts"
            ),
            DirectRoute(
                destination = SettingsDestination.DEFAULT_ASSISTANT,
                icon = Icons.Default.Assistant,
                titleMM = "အသံသွင်း Voice Input & Assistant",
                subtitleMM = "Google Voice Typing & Default Assistant",
                filterGroup = "VOICE_SYS",
                testTag = "direct_route_voice_input"
            ),
            DirectRoute(
                destination = SettingsDestination.APPLICATION_DETAILS,
                icon = Icons.Default.Info,
                titleMM = "အက်ပ် အသေးစိတ်နှင့် ပင်မ ခွင့်ပြုချက် စာမျက်နှာ",
                subtitleMM = "package:${context.packageName} တိုက်ရိုက် App Info လမ်းကြောင်း",
                filterGroup = "VOICE_SYS",
                testTag = "direct_route_app_details"
            )
        )
    }

    val filteredRoutes = remember(allProjectRoutes, selectedFilter, refreshTrigger) {
        if (selectedFilter == "ALL") {
            allProjectRoutes
        } else {
            allProjectRoutes.filter { it.filterGroup == selectedFilter }
        }
    }

    val grantedCount = allProjectRoutes.count { router.isCapabilityGranted(it.destination) }
    val totalCount = allProjectRoutes.size

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(AngelCyanAccent.copy(alpha = 0.7f), AngelNeonGreen.copy(alpha = 0.6f))
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .testTag("direct_settings_permission_hub_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = AngelCyanAccent.copy(alpha = 0.18f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Direct Settings",
                                tint = AngelCyanAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "စနစ် ဆက်တင်နှင့် ခွင့်ပြုချက် တိုက်ရိုက်လမ်းကြောင်း",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ခွင့်ပြုချက် အခြေအနေ: $grantedCount / $totalCount ခု အဆင်သင့်ရှိသည်",
                            fontSize = 11.sp,
                            color = if (grantedCount >= totalCount / 2) AngelNeonGreen else Color(0xFFFFB74D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = { refreshTrigger++ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Status",
                        tint = AngelNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ပရောဂျက်အတွင်း လိုအပ်သော Runtime Permission များနှင့် Android System Settings လမ်းကြောင်းများကို တစ်ချက်နှိပ်ရုံဖြင့် တိုက်ရိုက်ဖွင့်လှစ်နိုင်ပါသည်:",
                fontSize = 11.sp,
                color = AngelTextSecondary,
                lineHeight = 16.sp
            )

            lastRouteStatusMessage?.let { statusMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelCyanAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMsg,
                        fontSize = 11.sp,
                        color = AngelCyanAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1-Tap Request All Core Permissions Master Button
            Button(
                onClick = {
                    val perms = KyawGyiPermissionManager.getRequiredPermissions()
                    requestMultipleLauncher.launch(perms)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("hub_request_all_perms_btn"),
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
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ အဓိက Runtime ခွင့်ပြုချက်များ အားလုံး တိုက်ရိုက်တောင်းခံမည်",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Systematic Category Filter Bar
            val filterOptions = listOf(
                "ALL" to "အားလုံး (${allProjectRoutes.size})",
                "RUNTIME" to "Runtime ခွင့်ပြုချက်",
                "SPECIAL_OS" to "အထူး OS ခွင့်ပြုချက်",
                "BG_OEM" to "Background & OEM",
                "VOICE_SYS" to "Voice & စနစ်"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filterOptions.forEach { (key, label) ->
                    val selected = selectedFilter == key
                    FilterChip(
                        selected = selected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AngelCyanAccent.copy(alpha = 0.22f),
                            selectedLabelColor = AngelCyanAccent,
                            containerColor = AngelDarkBackground.copy(alpha = 0.5f),
                            labelColor = AngelTextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct Routes Grid List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredRoutes.forEach { route ->
                    val isGranted = router.isCapabilityGranted(route.destination)
                    val runtimePerm = route.destination.runtimePermissionName

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AngelDarkBackground.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (isGranted) AngelNeonGreen.copy(alpha = 0.35f) else Color(0xFFFFB74D).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isGranted && !runtimePerm.isNullOrBlank()) {
                                    pendingDestinationForFallback = route.destination
                                    singlePermissionLauncher.launch(runtimePerm)
                                } else {
                                    val report = router.openSettingsWithReport(route.destination)
                                    lastRouteStatusMessage = report.messageMM
                                    if (report.dispatchSucceeded) {
                                        Toast.makeText(
                                            context,
                                            "${route.titleMM} ဆက်တင်သို့ တိုက်ရိုက်ဖွင့်လှစ်နေပါသည်...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            .testTag(route.testTag)
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
                                    color = (if (isGranted) AngelNeonGreen else Color(0xFFFFB74D)).copy(alpha = 0.15f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = route.icon,
                                            contentDescription = null,
                                            tint = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = route.titleMM,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = route.subtitleMM,
                                        fontSize = 10.sp,
                                        color = AngelTextSecondary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (route.destination.requiresPackageUri) {
                                            "Route: ${route.destination.intentAction.substringAfterLast('.')} | package:${context.packageName}"
                                        } else {
                                            "Route: ${route.destination.intentAction.substringAfterLast('.')}"
                                        },
                                        fontSize = 9.sp,
                                        color = AngelCyanAccent.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isGranted) AngelNeonGreen.copy(alpha = 0.18f) else Color(0xFFFFB74D).copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = if (isGranted) {
                                            "ခွင့်ပြုပြီး ✓"
                                        } else if (!runtimePerm.isNullOrBlank()) {
                                            "တောင်းခံရန် >"
                                        } else {
                                            "ဖွင့်ရန် >"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isGranted) AngelNeonGreen else Color(0xFFFFB74D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        val report = router.openSettingsWithReport(route.destination)
                                        lastRouteStatusMessage = report.messageMM
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Launch,
                                        contentDescription = "Open Direct Setting",
                                        tint = AngelCyanAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (onOpenWizard != null) {
                Button(
                    onClick = onOpenWizard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("hub_open_wizard_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelCyanAccent.copy(alpha = 0.2f),
                        contentColor = AngelCyanAccent
                    ),
                    border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "✨ အဆင့်ဆင့် Setup & Permission Wizard ဖွင့်မည် (၁၀ ဆင့်)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onOpenDiagnostics != null) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("hub_open_diagnostics_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelNeonGreen.copy(alpha = 0.18f),
                        contentColor = AngelNeonGreen
                    ),
                    border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🛡️ စနစ် အလုံးစုံ စစ်ဆေးမှု (A-to-Z Diagnostics Run မည်)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Open Full Dialog Button
            OutlinedButton(
                onClick = onOpenFullDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("hub_open_full_dialog_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                border = BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "စနစ် ဆက်တင် အသေးစိတ် လမ်းကြောင်းအားလုံး ကြည့်ရှုရန် >",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
