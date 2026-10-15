package com.kyawgyi.ir.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyawgyi.ir.repository.IrViewModel
import com.kyawgyi.ir.voice.VoiceIntentProcessor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteControlScreen(
  viewModel: IrViewModel = viewModel(),
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsState()

  // Voice Processor setup
  val voiceProcessor = remember { VoiceIntentProcessor(context, viewModel, coroutineScope) }
  val voiceState by voiceProcessor.state.collectAsState()

  DisposableEffect(Unit) {
    onDispose {
      voiceProcessor.destroy()
    }
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      voiceProcessor.startListening()
    }
  }

  // Visual LED transmitter blink animation
  val ledFlash = remember { Animatable(0f) }
  LaunchedEffect(uiState.lastTransmittedButton) {
    if (uiState.lastTransmittedButton != null) {
      ledFlash.animateTo(1f, animationSpec = tween(80))
      ledFlash.animateTo(0f, animationSpec = tween(250))
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("remote_control_screen"),
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
              text = "KENNEDE REMOTE HANDSET",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "KN-5996HRB • Virtual 5-Button Controller",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8)
            )
          }
        },
        actions = {
          // Voice activation button in top bar
          IconButton(
            onClick = {
              if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                if (voiceState.isListening) voiceProcessor.stopListening() else voiceProcessor.startListening()
              } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            modifier = Modifier.testTag("top_bar_mic_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Voice Control",
              tint = if (voiceState.isListening) Color(0xFFEF4444) else Color(0xFFA855F7)
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
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

      // 1. Voice Intent Banner
      AnimatedVisibility(visible = voiceState.isListening || voiceState.lastRecognizedText.isNotEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF3B0764)),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, Color(0xFFA855F7)),
          modifier = Modifier.fillMaxWidth().testTag("voice_feedback_card")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = null,
              tint = if (voiceState.isListening) Color(0xFFEF4444) else Color(0xFFA855F7),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = if (voiceState.isListening) "နားထောင်နေပါသည်..." else "အသံမှတ်သားချက်:",
                color = Color(0xFFE9D5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = voiceState.feedbackMessage,
                color = Color.White,
                fontSize = 12.sp
              )
            }
          }
        }
      }

      // 2. The Physical Virtual Remote Handset Casing
      Box(
        modifier = Modifier
          .widthIn(max = 320.dp)
          .fillMaxWidth()
          .shadow(24.dp, RoundedCornerShape(36.dp), spotColor = Color(0xFF0284C7))
          .clip(RoundedCornerShape(36.dp))
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A),
                Color(0xFF020617)
              )
            )
          )
          .border(2.dp, Color(0xFF334155), RoundedCornerShape(36.dp))
          .padding(20.dp)
          .testTag("virtual_remote_handset")
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

          // LED IR Blaster Emitter Node
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                  if (ledFlash.value > 0.05f) Color(0xFFFF0055).copy(alpha = ledFlash.value)
                  else Color(0xFF334155)
                )
                .border(1.5.dp, Color(0xFF475569), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (ledFlash.value > 0.05f) "IR TRANSMITTING" else "38 kHz IR TRANSMITTER",
              color = if (ledFlash.value > 0.05f) Color(0xFFFF0055) else Color(0xFF64748B),
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }

          // Branding Tag
          Text(
            text = "KENNEDE",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace
          )

          // 1. POWER Button (Red)
          RemoteKeypadButton(
            name = "POWER",
            burmeseLabel = "ပါဝါ (ဖွင့်/ပိတ်)",
            icon = Icons.Default.PowerSettingsNew,
            baseColor = Color(0xFFDC2626),
            testTag = "remote_btn_power",
            onClick = { viewModel.transmitButton("power") }
          )

          // 2. LIGHT Button (Amber) & 3. SPEED Button (Cyan)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Box(modifier = Modifier.weight(1f)) {
              RemoteKeypadButton(
                name = "LIGHT",
                burmeseLabel = "မီးရောင်",
                icon = Icons.Default.Lightbulb,
                baseColor = Color(0xFFD97706),
                testTag = "remote_btn_light",
                onClick = { viewModel.transmitButton("light") }
              )
            }

            Box(modifier = Modifier.weight(1f)) {
              RemoteKeypadButton(
                name = "SPEED",
                burmeseLabel = "လေအရှိန်",
                icon = Icons.Default.Speed,
                baseColor = Color(0xFF0284C7),
                testTag = "remote_btn_speed",
                onClick = { viewModel.transmitButton("speed") }
              )
            }
          }

          // 4. CENTER Button (Purple) - Central Navigation Node
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            RemoteKeypadButton(
              name = "CENTER",
              burmeseLabel = "အလယ်ခလုတ်",
              icon = Icons.Default.Radio,
              baseColor = Color(0xFF9333EA),
              testTag = "remote_btn_center",
              modifier = Modifier.size(80.dp),
              onClick = { viewModel.transmitButton("center") }
            )
          }

          // 5. OSCILLATION Button (Emerald)
          RemoteKeypadButton(
            name = "OSCILLATION",
            burmeseLabel = "ဘယ်/ညာ လှည့်",
            icon = Icons.Default.Air,
            baseColor = Color(0xFF059669),
            testTag = "remote_btn_oscillation",
            onClick = { viewModel.transmitButton("oscillation") }
          )

          // Embedded Voice Trigger Pad on Handset
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(if (voiceState.isListening) Color(0xFF7E22CE) else Color(0xFF1E293B))
              .border(1.dp, Color(0xFFA855F7), RoundedCornerShape(16.dp))
              .clickable {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                  if (voiceState.isListening) voiceProcessor.stopListening() else voiceProcessor.startListening()
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
              .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (voiceState.isListening) "အသံနားထောင်နေသည်..." else "ဗမာလို အသံဖြင့် အမိန့်ပေးမည်",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Text(
            text = "KN-5996HRB • 38kHz CARRIER",
            color = Color(0xFF475569),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }

      // Status Feedback Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("remote_status_feedback")
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("STATUS FEEDBACK", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
              text = if (uiState.isConnected) "ESP32 ONLINE" else "OFFLINE",
              color = if (uiState.isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = uiState.statusMessage,
            color = Color.White,
            fontSize = 11.sp
          )

          if (!uiState.errorMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = uiState.errorMessage ?: "",
              color = Color(0xFFF87171),
              fontSize = 10.5.sp
            )
          }
        }
      }
    }
  }
}

@Composable
fun RemoteKeypadButton(
  name: String,
  burmeseLabel: String,
  icon: ImageVector,
  baseColor: Color,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val isPressed = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = baseColor)
      .clip(RoundedCornerShape(16.dp))
      .background(
        Brush.verticalGradient(
          colors = listOf(
            baseColor,
            baseColor.copy(alpha = 0.75f)
          )
        )
      )
      .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
      .clickable(interactionSource = isPressed, indication = null) { onClick() }
      .padding(vertical = 12.dp, horizontal = 10.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = name,
        tint = Color.White,
        modifier = Modifier.size(24.dp)
      )
      Text(
        text = name,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
      )
      Text(
        text = burmeseLabel,
        color = Color.White.copy(alpha = 0.85f),
        fontSize = 9.sp,
        textAlign = TextAlign.Center
      )
    }
  }
}
