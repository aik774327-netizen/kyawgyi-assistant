package com.kyawgyi.ir.ui

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyawgyi.ir.models.IrButton
import com.kyawgyi.ir.repository.IRViewModel
import com.kyawgyi.ir.repository.IrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrDashboardScreen(
  viewModel: IRViewModel = viewModel(),
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var hostInput by remember(uiState.host) { mutableStateOf(uiState.host) }
  var isConfigExpanded by remember { mutableStateOf(false) }

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(700),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("ir_dashboard_screen"),
    containerColor = Color(0xFF0B1120),
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF0F172A),
          titleContentColor = Color.White
        ),
        title = {
          Column {
            Text(
              text = "KENNEDE KN-5996HRB",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "ESP32 IR SMART CONTROLLER • DASHBOARD",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8),
              fontWeight = FontWeight.Medium
            )
          }
        },
        navigationIcon = {
          if (onNavigateBack != null) {
            IconButton(
              onClick = onNavigateBack,
              modifier = Modifier.testTag("dashboard_back_button")
            ) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshStatus() },
            modifier = Modifier.testTag("dashboard_refresh_status_button")
          ) {
            if (uiState.isCheckingStatus) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF38BDF8),
                strokeWidth = 2.dp
              )
            } else {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8))
            }
          }
        }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

      // 1. ESP32 Connection Status Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(
            1.5.dp,
            if (uiState.isConnected) Color(0xFF10B981) else Color(0xFFEF4444)
          ),
          modifier = Modifier.fillMaxWidth().testTag("esp32_connection_card")
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
                    .background(if (uiState.isConnected) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = if (uiState.isConnected) "ESP32 ချိတ်ဆက်ထားသည် (CONNECTED)" else "ESP32 ချိတ်ဆက်၍မရပါ (OFFLINE)",
                  color = if (uiState.isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }

              IconButton(
                onClick = { isConfigExpanded = !isConfigExpanded },
                modifier = Modifier.size(28.dp).testTag("toggle_config_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.Code,
                  contentDescription = "Config",
                  tint = Color(0xFF94A3B8),
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Specs grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text("IP / Host", color = Color(0xFF64748B), fontSize = 11.sp)
                Text(
                  uiState.statusResponse?.ip ?: uiState.host,
                  color = Color(0xFFCBD5E1),
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
              Column {
                Text("Carrier Frequency", color = Color(0xFF64748B), fontSize = 11.sp)
                Text("38 kHz (38,000 Hz)", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
              }
              Column {
                Text("Receiver Pin", color = Color(0xFF64748B), fontSize = 11.sp)
                Text("GPIO 15 (VS1838B)", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
              }
            }

            AnimatedVisibility(visible = isConfigExpanded) {
              Column(modifier = Modifier.padding(top = 12.dp)) {
                Divider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = hostInput,
                    onValueChange = { hostInput = it },
                    label = { Text("ESP32 Host / IP Address", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                      focusedBorderColor = Color(0xFF38BDF8),
                      unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.weight(1f).testTag("esp32_host_input")
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Button(
                    onClick = { viewModel.updateHost(hostInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.testTag("apply_esp32_host_btn")
                  ) {
                    Text("Apply")
                  }
                }
              }
            }
          }
        }
      }

      // 2. Learning Active Banner
      if (uiState.isLearning) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0369A1)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("learning_active_banner")
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Sensors,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(24.dp).alpha(pulseAlpha)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "IR SIGNAL CAPTURE IN PROGRESS...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                  )
                  Text(
                    text = "Target: ${uiState.activeLearningButton?.uppercase() ?: "WAITING"} • Point KN-5996HRB remote at VS1838B",
                    color = Color(0xFFBAE6FD),
                    fontSize = 11.sp
                  )
                }
              }

              Button(
                onClick = { viewModel.stopLearning() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.testTag("stop_learning_active_btn")
              ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel", fontSize = 11.sp)
              }
            }
          }
        }
      }

      // 3. Status & Error Feedback Message
      item {
        if (uiState.errorMessage != null) {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.errorMessage ?: "",
                color = Color(0xFFFCA5A5),
                fontSize = 12.sp
              )
            }
          }
        } else {
          Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2642)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color(0x3338BDF8)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.statusMessage,
                color = Color(0xFFBAE6FD),
                fontSize = 12.sp
              )
            }
          }
        }
      }

      // 4. Section Title: Button Learning & Transmission Status
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "ခလုတ်များ၏ သင်ယူမှုအခြေအနေ (BUTTON STATES)",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )

          Text(
            text = "${uiState.buttons.count { it.code.captured }}/5 LEARNED",
            color = if (uiState.buttons.all { it.code.captured }) Color(0xFF10B981) else Color(0xFFF59E0B),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      // 5. Material 3 Cards for the 5 Buttons
      items(uiState.buttons) { button ->
        IrButtonCard(
          button = button,
          isTargetOfLearning = uiState.activeLearningButton == button.key,
          isSystemLearning = uiState.isLearning,
          onLearnClick = { viewModel.startLearning(button.key) },
          onTransmitClick = { viewModel.transmitButton(button.key) }
        )
      }

      // 6. Extra Controls Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Database Reset & Refresh", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              Text("ကုဒ်များအားလုံးကို အစမှ ပြန်လည်ရှင်းလင်းရန်", color = Color(0xFF64748B), fontSize = 10.sp)
            }

            OutlinedButton(
              onClick = { viewModel.resetAllCodes() },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
              border = BorderStroke(1.dp, Color(0xFFEF4444)),
              modifier = Modifier.testTag("reset_all_codes_btn")
            ) {
              Text("Reset All", fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
fun IrButtonCard(
  button: IrButton,
  isTargetOfLearning: Boolean,
  isSystemLearning: Boolean,
  onLearnClick: () -> Unit,
  onTransmitClick: () -> Unit
) {
  val isLearned = button.code.captured
  val icon: ImageVector = when (button.key) {
    "power" -> Icons.Default.PowerSettingsNew
    "light" -> Icons.Default.Lightbulb
    "speed" -> Icons.Default.Speed
    "oscillation" -> Icons.Default.Air
    "center" -> Icons.Default.Radio
    else -> Icons.Default.Sensors
  }

  val accentColor = when (button.key) {
    "power" -> Color(0xFFEF4444)
    "light" -> Color(0xFFF59E0B)
    "speed" -> Color(0xFF38BDF8)
    "oscillation" -> Color(0xFF10B981)
    "center" -> Color(0xFFA855F7)
    else -> Color(0xFF64748B)
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      1.2.dp,
      when {
        isTargetOfLearning -> Color(0xFF38BDF8)
        isLearned -> Color(0xFF10B981)
        else -> Color(0xFF334155)
      }
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("ir_button_card_${button.key}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Icon and Button Name
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(if (isLearned) accentColor.copy(alpha = 0.2f) else Color(0xFF0F172A))
              .border(1.5.dp, if (isLearned) accentColor else Color(0xFF334155), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = icon,
              contentDescription = button.name,
              tint = if (isLearned) accentColor else Color(0xFF64748B),
              modifier = Modifier.size(22.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = button.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.width(8.dp))
              // Learning status badge
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (isLearned) Color(0x3310B981) else Color(0x33EF4444))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (isLearned) "LEARNED" else "UNLEARNED",
                  color = if (isLearned) Color(0xFF10B981) else Color(0xFFEF4444),
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
            Text(
              text = button.burmeseLabel,
              color = Color(0xFF94A3B8),
              fontSize = 11.sp
            )
          }
        }

        // Action Buttons: Send & Learn
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Transmit Button
          Button(
            onClick = onTransmitClick,
            enabled = isLearned && !isSystemLearning,
            colors = ButtonDefaults.buttonColors(
              containerColor = accentColor,
              disabledContainerColor = Color(0xFF334155)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("transmit_btn_${button.key}")
          ) {
            Text(
              text = "ထုတ်လွှင့်မည်",
              fontSize = 11.sp,
              color = if (isLearned) Color.White else Color(0xFF64748B)
            )
          }

          // Learn Button
          OutlinedButton(
            onClick = onLearnClick,
            enabled = !isSystemLearning,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = if (isLearned) Color(0xFF38BDF8) else Color(0xFFF59E0B)
            ),
            border = BorderStroke(
              1.dp,
              if (isLearned) Color(0xFF38BDF8) else Color(0xFFF59E0B)
            ),
            modifier = Modifier.testTag("learn_btn_${button.key}")
          ) {
            Text(
              text = if (isLearned) "ပြန်လည်ဖမ်းယူ" else "ဖမ်းယူမည်",
              fontSize = 11.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Divider(color = Color(0xFF334155), thickness = 0.8.dp)
      Spacer(modifier = Modifier.height(8.dp))

      // Protocol & Raw Hex inspection details
      if (isLearned) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Protocol: ${button.code.protocol ?: "UNKNOWN RAW"}",
            color = Color(0xFF38BDF8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Address: ${button.code.address ?: "-"}",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Cmd: ${button.code.command ?: "-"}",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "Bits: ${button.code.bits ?: button.code.rawTiming.size}",
            color = Color(0xFF10B981),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      } else {
        Text(
          text = "မူရင်း KN-5996HRB ရီမုဒ်မှ အချက်ပြ မဖမ်းယူရသေးပါ (IR CODE NOT LEARNED)",
          color = Color(0xFFEF4444),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }
    }
  }
}
