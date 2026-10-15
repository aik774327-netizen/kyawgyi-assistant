package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ModeFanOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.KennedeButton
import com.example.models.KennedeIrDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * High-fidelity Virtual IR Remote Control UI for KENNEDE KN-5996HRB.
 * Features dedicated buttons for Power, Volume Controls (Vol+, Vol-, Mute),
 * and Mode Controls (Mode, Speed, Oscillation, Light, Timer, Center).
 */
@Composable
fun KennedeVirtualRemoteControl(
  irDb: KennedeIrDatabase,
  onSendButton: (KennedeButton) -> Unit,
  modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()
  var lastSentButton by remember { mutableStateOf<KennedeButton?>(null) }
  var isTransmitting by remember { mutableStateOf(false) }

  // Animated feedback function
  fun triggerButtonSend(button: KennedeButton) {
    coroutineScope.launch {
      lastSentButton = button
      isTransmitting = true
      onSendButton(button)
      delay(400)
      isTransmitting = false
    }
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .shadow(16.dp, RoundedCornerShape(28.dp))
      .testTag("kennede_virtual_remote_casing"),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    border = BorderStroke(1.5.dp, Color(0xFF334155))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
          )
        )
        .padding(18.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // 1. Remote Head with IR Emitter LED
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "KENNEDE",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFCBD5E1),
            letterSpacing = 2.sp
          )
          Text(
            text = "KN-5996HRB IR Controller",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
          )
        }

        // IR Transmitter Emitter LED
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (isTransmitting) "TRANSMITTING..." else "IR READY",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isTransmitting) Color(0xFFEF4444) else Color(0xFF10B981)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Box(
            modifier = Modifier
              .size(14.dp)
              .clip(CircleShape)
              .background(if (isTransmitting) Color(0xFFEF4444) else Color(0xFF1E3A5F))
              .border(
                1.5.dp,
                if (isTransmitting) Color(0xFFFCA5A5) else Color(0xFF38BDF8),
                CircleShape
              )
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // 2. POWER & MUTE Primary Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Power Button
        RemoteHapticButton(
          button = KennedeButton.POWER,
          icon = Icons.Default.PowerSettingsNew,
          label = "POWER",
          burmeseLabel = "ပါဝါ (ဖွင့်/ပိတ်)",
          primaryColor = Color(0xFFEF4444),
          isLearned = irDb.buttons[KennedeButton.POWER.key]?.captured == true,
          onClick = { triggerButtonSend(KennedeButton.POWER) },
          size = 64.dp
        )

        // Light Button
        RemoteHapticButton(
          button = KennedeButton.LIGHT,
          icon = Icons.Default.Lightbulb,
          label = "LIGHT",
          burmeseLabel = "မီးရောင်",
          primaryColor = Color(0xFFF59E0B),
          isLearned = irDb.buttons[KennedeButton.LIGHT.key]?.captured == true,
          onClick = { triggerButtonSend(KennedeButton.LIGHT) },
          size = 64.dp
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 3. VOLUME CONTROLS SECTION (Vol+, Vol-, Mute)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF111E33))
          .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
          .padding(12.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "VOLUME CONTROLS (အသံထိန်းချုပ်စနစ်)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8),
            letterSpacing = 1.sp
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Volume Down
            RemotePillButton(
              button = KennedeButton.VOLUME_DOWN,
              icon = Icons.Default.VolumeDown,
              label = "VOL -",
              color = Color(0xFF38BDF8),
              isLearned = irDb.buttons[KennedeButton.VOLUME_DOWN.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.VOLUME_DOWN) }
            )

            // Mute Button
            RemotePillButton(
              button = KennedeButton.MUTE,
              icon = Icons.Default.VolumeMute,
              label = "MUTE",
              color = Color(0xFFEC4899),
              isLearned = irDb.buttons[KennedeButton.MUTE.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.MUTE) }
            )

            // Volume Up
            RemotePillButton(
              button = KennedeButton.VOLUME_UP,
              icon = Icons.Default.VolumeUp,
              label = "VOL +",
              color = Color(0xFF38BDF8),
              isLearned = irDb.buttons[KennedeButton.VOLUME_UP.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.VOLUME_UP) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 4. MODE & FAN D-PAD CONTROLS SECTION
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF111E33))
          .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
          .padding(12.dp)
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "MODE & FAN D-PAD (မုဒ်နှင့် လေအရှိန် ထိန်းချုပ်စနစ်)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF10B981),
            letterSpacing = 1.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          // Top of D-pad: MODE
          RemoteHapticButton(
            button = KennedeButton.MODE,
            icon = Icons.Default.Tune,
            label = "MODE",
            burmeseLabel = "မုဒ်ရွေးချယ်",
            primaryColor = Color(0xFF10B981),
            isLearned = irDb.buttons[KennedeButton.MODE.key]?.captured == true,
            onClick = { triggerButtonSend(KennedeButton.MODE) },
            size = 56.dp
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Middle row of D-pad: SPEED - CENTER - OSCILLATION
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Speed
            RemoteHapticButton(
              button = KennedeButton.SPEED,
              icon = Icons.Default.Speed,
              label = "SPEED",
              burmeseLabel = "လေအရှိန်",
              primaryColor = Color(0xFF06B6D4),
              isLearned = irDb.buttons[KennedeButton.SPEED.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.SPEED) },
              size = 54.dp
            )

            // Center Key
            RemoteHapticButton(
              button = KennedeButton.CENTER,
              icon = Icons.Default.Waves,
              label = "OK / CENTER",
              burmeseLabel = "အလယ်ခလုတ်",
              primaryColor = Color(0xFFA855F7),
              isLearned = irDb.buttons[KennedeButton.CENTER.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.CENTER) },
              size = 56.dp
            )

            // Oscillation (Swing)
            RemoteHapticButton(
              button = KennedeButton.OSCILLATION,
              icon = Icons.Default.Air,
              label = "SWING",
              burmeseLabel = "ဘယ်/ညာလှည့်",
              primaryColor = Color(0xFF10B981),
              isLearned = irDb.buttons[KennedeButton.OSCILLATION.key]?.captured == true,
              onClick = { triggerButtonSend(KennedeButton.OSCILLATION) },
              size = 54.dp
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Bottom of D-pad: TIMER
          RemoteHapticButton(
            button = KennedeButton.TIMER,
            icon = Icons.Default.Timer,
            label = "TIMER",
            burmeseLabel = "အချိန်မှတ်စနစ်",
            primaryColor = Color(0xFFF97316),
            isLearned = irDb.buttons[KennedeButton.TIMER.key]?.captured == true,
            onClick = { triggerButtonSend(KennedeButton.TIMER) },
            size = 54.dp
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Last transmission toast indicator
      AnimatedVisibility(
        visible = lastSentButton != null,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        lastSentButton?.let { btn ->
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF0F2D1F))
              .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${btn.burmeseLabel} (${btn.label}) ပို့ဆောင်ပြီးပါပြီ",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF34D399)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RemoteHapticButton(
  button: KennedeButton,
  icon: ImageVector,
  label: String,
  burmeseLabel: String,
  primaryColor: Color,
  isLearned: Boolean,
  onClick: () -> Unit,
  size: androidx.compose.ui.unit.Dp
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable { onClick() }
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(
          brush = Brush.radialGradient(
            colors = listOf(primaryColor.copy(alpha = 0.28f), Color(0xFF1E293B))
          )
        )
        .border(1.5.dp, primaryColor.copy(alpha = 0.7f), CircleShape)
        .testTag("remote_btn_${button.key}"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = primaryColor,
        modifier = Modifier.size(size * 0.46f)
      )
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = label,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Text(
      text = burmeseLabel,
      fontSize = 9.sp,
      color = Color(0xFF94A3B8)
    )
  }
}

@Composable
private fun RemotePillButton(
  button: KennedeButton,
  icon: ImageVector,
  label: String,
  color: Color,
  isLearned: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF1E293B))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .testTag("remote_pill_${button.key}"),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
    }
  }
}
