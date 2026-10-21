package com.example.googlehome

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Google Home Smart Device Dashboard Screen in Jetpack Compose
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleHomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val googleHomeManager = remember { GoogleHomeManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var deviceList by remember { mutableStateOf(GoogleHomeManager.DEFAULT_SMART_DEVICES) }
    var selectedDeviceForDetails by remember { mutableStateOf<GoogleHomeDevice?>(null) }
    var customAssistantCommand by remember { mutableStateOf("") }
    var isExecutingAction by remember { mutableStateOf(false) }

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
                            text = "Google Home ချိတ်ဆက်မှု",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Smart Home & IoT Voice Automation",
                            fontSize = 12.sp,
                            color = AngelCyanAccent
                        )
                    }
                },
                actions = {
                    // Direct Google Home App Launcher button
                    FilledTonalButton(
                        onClick = {
                            val res = googleHomeManager.openGoogleHomeApp()
                            Toast.makeText(context, res.userMessage, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AngelNeonGreen.copy(alpha = 0.2f),
                            contentColor = AngelNeonGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_google_home_app_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Open Google Home App",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "App ဖွင့်ပါ", fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Hero Integration Banner Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_home_banner_card"),
                    colors = CardDefaults.cardColors(containerColor = AngelCardDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = AngelCyanAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Sensors,
                                        contentDescription = "IoT Hub",
                                        tint = AngelCyanAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "စမတ်အိမ်သုံး ပစ္စည်းများ ထိန်းချုပ်မှု",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "ကျော်ကြီး မှတစ်ဆင့် အသံဖြင့် အမိန့်ပေးနိုင်ပါသည်",
                                    fontSize = 12.sp,
                                    color = AngelTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "နမူနာ အသံအမိန့်များ: \"ဧည့်ခန်းမီး ဖွင့်\", \"အဲကွန်း ၂၄ ဒီဂရီထား\", \"တီဗွီ ဖွင့်\", \"မီးဖိုချောင်ပလပ် ပိတ်\"",
                            fontSize = 12.sp,
                            color = AngelNeonGreen,
                            modifier = Modifier
                                .background(AngelNeonGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Quick Voice Assistant Command Dispatcher
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assistant_quick_dispatch_card"),
                    colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Google Assistant သို့ တိုက်ရိုက် အမိန့်ပေးပို့ခြင်း",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customAssistantCommand,
                                onValueChange = { customAssistantCommand = it },
                                placeholder = {
                                    Text("ဥပမာ - Turn on living room light", fontSize = 13.sp)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("assistant_command_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AngelCyanAccent,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Button(
                                onClick = {
                                    if (customAssistantCommand.isNotBlank()) {
                                        val res = googleHomeManager.sendCommandToGoogleAssistant(customAssistantCommand.trim())
                                        Toast.makeText(context, res.userMessage, Toast.LENGTH_SHORT).show()
                                        customAssistantCommand = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("send_assistant_command_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            // Device List Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ချိတ်ဆက်ထားသော Smart Devices များ (${deviceList.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Smart Devices Grid / List
            items(
                items = deviceList,
                key = { it.id }
            ) { device ->
                SmartDeviceCard(
                    device = device,
                    onToggle = { turnOn ->
                        coroutineScope.launch {
                            isExecutingAction = true
                            val (success, feedback) = googleHomeManager.executeSmartDeviceAction(device, turnOn)
                            Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                            // Trigger state recomposition
                            deviceList = deviceList.map { if (it.id == device.id) it.copy(isOn = turnOn) else it }
                            isExecutingAction = false
                        }
                    },
                    onAdjustValue = { newValue ->
                        coroutineScope.launch {
                            val (success, feedback) = googleHomeManager.executeSmartDeviceAction(
                                device,
                                turnOn = true,
                                targetValue = newValue
                            )
                            Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                            deviceList = deviceList.map {
                                if (it.id == device.id) {
                                    if (device.type == SmartDeviceType.AIR_CONDITIONER) it.copy(temperature = newValue, isOn = true)
                                    else it.copy(brightness = newValue, isOn = true)
                                } else it
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SmartDeviceCard(
    device: GoogleHomeDevice,
    onToggle: (Boolean) -> Unit,
    onAdjustValue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .testTag("device_card_${device.id}"),
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
                // Device Icon
                Surface(
                    shape = CircleShape,
                    color = if (device.isOn) AngelNeonGreen.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (device.type) {
                            SmartDeviceType.LIGHT -> Icons.Filled.Lightbulb
                            SmartDeviceType.AIR_CONDITIONER -> Icons.Filled.AcUnit
                            SmartDeviceType.TV -> Icons.Filled.Tv
                            SmartDeviceType.FAN -> Icons.Filled.WindPower
                            SmartDeviceType.SPEAKER -> Icons.Filled.Speaker
                            SmartDeviceType.DOOR_LOCK -> Icons.Filled.Lock
                            else -> Icons.Filled.Power
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = device.name,
                            tint = if (device.isOn) AngelNeonGreen else AngelTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Device Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${device.room} • ${device.type.burmeseName}",
                        fontSize = 12.sp,
                        color = AngelTextSecondary
                    )
                }

                // Toggle Switch
                Switch(
                    checked = device.isOn,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = AngelNeonGreen,
                        uncheckedThumbColor = AngelTextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.testTag("device_switch_${device.id}")
                )
            }

            // Expanded Controls for Air Conditioner / Light Brightness
            AnimatedVisibility(visible = expanded && device.isOn) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (device.type == SmartDeviceType.AIR_CONDITIONER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "အပူချိန် သတ်မှတ်ချက် (Temperature)",
                                fontSize = 13.sp,
                                color = AngelTextSecondary
                            )
                            Text(
                                text = "${device.temperature}°C",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelCyanAccent
                            )
                        }

                        Slider(
                            value = device.temperature.toFloat(),
                            onValueChange = { onAdjustValue(it.toInt()) },
                            valueRange = 16f..30f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = AngelCyanAccent,
                                activeTrackColor = AngelCyanAccent
                            ),
                            modifier = Modifier.testTag("ac_slider_${device.id}")
                        )
                    } else if (device.type == SmartDeviceType.LIGHT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "မီးအလင်းအား (Brightness)",
                                fontSize = 13.sp,
                                color = AngelTextSecondary
                            )
                            Text(
                                text = "${device.brightness}%",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AngelNeonGreen
                            )
                        }

                        Slider(
                            value = device.brightness.toFloat(),
                            onValueChange = { onAdjustValue(it.toInt()) },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = AngelNeonGreen,
                                activeTrackColor = AngelNeonGreen
                            ),
                            modifier = Modifier.testTag("light_slider_${device.id}")
                        )
                    }
                }
            }
        }
    }
}
