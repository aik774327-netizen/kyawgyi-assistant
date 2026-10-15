package com.kyawgyi.ir.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyawgyi.ir.repository.IrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrLearningScreen(
  viewModel: IrViewModel = viewModel(),
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedButtonKey by remember { mutableStateOf("power") }
  var isDropdownExpanded by remember { mutableStateOf(false) }

  val targetButton = uiState.buttons.find { it.key == selectedButtonKey } ?: uiState.buttons.firstOrNull()

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(600),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("ir_learning_screen"),
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
              text = "IR CODE LEARNING WIZARD",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "KENNEDE KN-5996HRB • 38 kHz Receiver",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8)
            )
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

      // 1. Step-by-Step Instructions Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("learning_instructions_card")
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "အချက်ပြ ဖမ်းယူနည်း အဆင့်ဆင့် (INSTRUCTIONS)",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }

          Divider(color = Color(0xFF334155))

          InstructionStep(
            number = "၁",
            title = "မူရင်းရီမုဒ်ကို အသင့်ပြင်ဆင်ပါ",
            desc = "KENNEDE KN-5996HRB မူရင်းရီမုဒ်ကို ESP32 ၏ VS1838B လက်ခံကိရိယာ (GPIO 15) ရှေ့ ၂-၅ စင်တီမီတာခန့်တွင် ချိန်ထားပါ။"
          )
          InstructionStep(
            number = "၂",
            title = "ဖမ်းယူလိုသော ခလုတ်ကို ရွေးချယ်ပါ",
            desc = "အောက်ပါ Dropdown မှတ်တမ်းမှ POWER, LIGHT, SPEED, OSCILLATION သို့မဟုတ် CENTER ကို ရွေးပါ။"
          )
          InstructionStep(
            number = "၃",
            title = "ဖမ်းယူမှု စတင်ပြီး ရီမုဒ်ကို နှိပ်ပါ",
            desc = "'ဖမ်းယူမည် (Start Learning)' ကို နှိပ်ပြီး ၅ စက္ကန့်အတွင်း မူရင်းရီမုဒ်ခလုတ်ကို ဖိနှိပ်ပါ။ အောင်မြင်ပါက အစိမ်းရောင် အချက်ပြ ပြသပါမည်။"
          )
        }
      }

      // 2. Dropdown to select the button to learn (POWER, LIGHT, SPEED, OSCILLATION, CENTER)
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth().testTag("button_selector_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "ဖမ်းယူမည့် ခလုတ် ရွေးချယ်ပါ (SELECT BUTTON TO LEARN)",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )

          Spacer(modifier = Modifier.height(10.dp))

          Box(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                .clickable { isDropdownExpanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (targetButton?.code?.captured == true) Color(0xFF10B981) else Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "${targetButton?.name} (${targetButton?.burmeseLabel})",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                  Text(
                    text = if (targetButton?.code?.captured == true) "အခြေအနေ: ဖမ်းယူပြီး (LEARNED)" else "အခြေအနေ: မဖမ်းယူရသေးပါ (NOT LEARNED)",
                    color = if (targetButton?.code?.captured == true) Color(0xFF10B981) else Color(0xFFFCA5A5),
                    fontSize = 10.sp
                  )
                }
              }

              Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color(0xFF38BDF8))
            }

            DropdownMenu(
              expanded = isDropdownExpanded,
              onDismissRequest = { isDropdownExpanded = false },
              modifier = Modifier
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155))
            ) {
              uiState.buttons.forEach { btn ->
                DropdownMenuItem(
                  text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Box(
                        modifier = Modifier
                          .size(8.dp)
                          .background(if (btn.code.captured) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        "${btn.name} - ${btn.burmeseLabel}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                      )
                    }
                  },
                  onClick = {
                    selectedButtonKey = btn.key
                    isDropdownExpanded = false
                  }
                )
              }
            }
          }
        }
      }

      // 3. Learning Active Radar State
      AnimatedVisibility(visible = uiState.isLearning) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF0369A1)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("learning_radar_banner")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Sensors,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(32.dp).alpha(pulseAlpha)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "ESP32 သည် ${uiState.activeLearningButton?.uppercase()} အချက်ပြကို စောင့်ဆိုင်းနေပါသည်...",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "၃၅ စက္ကန့် မပြည့်မီ မူရင်းရီမုဒ်မှ ခလုတ်ကို နှိပ်ပါ",
                color = Color(0xFFBAE6FD),
                fontSize = 11.sp
              )
            }
          }
        }
      }

      // 3b. Real-World Mismatch & Error-Learning Failure Banner
      AnimatedVisibility(visible = uiState.failureWarning != null) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
          modifier = Modifier.fillMaxWidth().testTag("learning_error_learning_banner")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = uiState.errorCategory ?: "အမှားသတိပေးချက်",
                color = Color(0xFFFCA5A5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }
            Text(
              text = uiState.failureWarning ?: "",
              color = Color.White,
              fontSize = 11.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      // 4. Learning Control Buttons (Start / Stop)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = { viewModel.startLearning(selectedButtonKey) },
          enabled = !uiState.isLearning,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0284C7),
            disabledContainerColor = Color(0xFF334155)
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("start_learning_button")
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("ဖမ်းယူမှု စတင်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
          onClick = { viewModel.stopLearning() },
          enabled = uiState.isLearning,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
          border = BorderStroke(1.5.dp, if (uiState.isLearning) Color(0xFFEF4444) else Color(0xFF334155)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("stop_learning_button")
        ) {
          Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("ရပ်တန့်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }

      // 5. Captured Signal Details & Test Trigger
      if (targetButton != null && targetButton.code.captured) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.2.dp, Color(0xFF10B981)),
          modifier = Modifier.fillMaxWidth().testTag("captured_signal_card")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "${targetButton.name} အတွက် အချက်ပြ ဖမ်းယူပြီးပါပြီ!",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Text(
              text = "Protocol: ${targetButton.code.protocol ?: "NEC/RAW"} • Command: ${targetButton.code.command ?: "-"} • Address: ${targetButton.code.address ?: "-"}",
              color = Color(0xFFA7F3D0),
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
              onClick = { viewModel.transmitButton(targetButton.key) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("test_transmit_button")
            ) {
              Text("စမ်းသပ် ထုတ်လွှင့်မည် (Test Transmit)", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
fun InstructionStep(number: String, title: String, desc: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(Color(0xFF0284C7)),
      contentAlignment = Alignment.Center
    ) {
      Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      Text(desc, color = Color(0xFF94A3B8), fontSize = 10.5.sp)
    }
  }
}
