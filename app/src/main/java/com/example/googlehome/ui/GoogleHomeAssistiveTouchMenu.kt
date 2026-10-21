package com.example.googlehome.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.googlehome.data.SmartHomeDeviceEntity
import com.example.googlehome.service.GoogleHomeDeviceService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Assistive Touch Floating Quick-Control Menu for Google Home Devices.
 * Lists available devices and provides quick toggles for Power and Dimming / Temperature.
 */
@Composable
fun GoogleHomeAssistiveTouchMenu(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val service = remember { GoogleHomeDeviceService(context) }
    val coroutineScope = rememberCoroutineScope()
    val devices by service.devicesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        service.syncDiscoveredDevices()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("google_home_assistive_touch_menu"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
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
                                imageVector = Icons.Filled.TouchApp,
                                contentDescription = "Assistive Touch",
                                tint = AngelNeonGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Home အမြန်ထိန်းချုပ်မှု",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Assistive Quick Control Panel",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Menu",
                        tint = AngelTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Device Grid
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AngelCyanAccent, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(devices, key = { it.deviceId }) { dev ->
                        AssistiveDeviceChip(
                            device = dev,
                            onToggle = {
                                coroutineScope.launch {
                                    val res = service.togglePower(dev.deviceId, !dev.isOn)
                                    Toast.makeText(context, res.speechFeedback, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed Quick Sliders for active lights / AC
                val activeDimmableDevice = devices.firstOrNull { it.isOn && (it.deviceType == "LIGHT" || it.deviceType == "AC") }
                if (activeDimmableDevice != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AngelCardDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${activeDimmableDevice.name} (${if (activeDimmableDevice.deviceType == "AC") "အပူချိန်" else "မီးအား"})",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (activeDimmableDevice.deviceType == "AC") "${activeDimmableDevice.temperature}°C" else "${activeDimmableDevice.brightness}%",
                                    fontSize = 13.sp,
                                    color = AngelNeonGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (activeDimmableDevice.deviceType == "AC") {
                                Slider(
                                    value = activeDimmableDevice.temperature.toFloat(),
                                    onValueChange = { newVal ->
                                        coroutineScope.launch {
                                            service.setTemperature(activeDimmableDevice.deviceId, newVal.toInt())
                                        }
                                    },
                                    valueRange = 16f..30f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AngelCyanAccent,
                                        activeTrackColor = AngelCyanAccent
                                    ),
                                    modifier = Modifier.testTag("assistive_ac_slider")
                                )
                            } else {
                                Slider(
                                    value = activeDimmableDevice.brightness.toFloat(),
                                    onValueChange = { newVal ->
                                        coroutineScope.launch {
                                            service.setBrightness(activeDimmableDevice.deviceId, newVal.toInt())
                                        }
                                    },
                                    valueRange = 10f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AngelNeonGreen,
                                        activeTrackColor = AngelNeonGreen
                                    ),
                                    modifier = Modifier.testTag("assistive_brightness_slider")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssistiveDeviceChip(
    device: SmartHomeDeviceEntity,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (device.isOn) AngelNeonGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (device.isOn) AngelNeonGreen else Color.White.copy(alpha = 0.1f)
        ),
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .testTag("assistive_chip_${device.deviceId}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (device.deviceType) {
                        "LIGHT" -> Icons.Filled.Lightbulb
                        "AC" -> Icons.Filled.AcUnit
                        "FAN" -> Icons.Filled.WindPower
                        "TV" -> Icons.Filled.Tv
                        else -> Icons.Filled.Power
                    },
                    contentDescription = device.name,
                    tint = if (device.isOn) AngelNeonGreen else AngelTextSecondary,
                    modifier = Modifier.size(20.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = if (device.isOn) AngelNeonGreen else Color.Gray,
                    modifier = Modifier.size(8.dp)
                ) {}
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = device.name.substringBefore(" ("),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (device.isOn) Color.White else AngelTextSecondary,
                maxLines = 1
            )

            Text(
                text = if (device.isOn) "ON" else "OFF",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (device.isOn) AngelNeonGreen else AngelTextSecondary
            )
        }
    }
}
