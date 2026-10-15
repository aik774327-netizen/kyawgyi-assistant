package com.example.ui.dialogs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.service.AiGatewaySttResult
import com.example.service.MyanmarSttManager
import com.example.service.TtsProviderType
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun AiVoiceStudioDialog(
  sttManager: MyanmarSttManager,
  selectedTtsProvider: TtsProviderType,
  onSelectTtsProvider: (TtsProviderType) -> Unit,
  onPlayTts: (String, TtsProviderType) -> Unit,
  onExecuteCommand: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val isRecording by sttManager.isRecognizing.collectAsState()
  val rmsLevel by sttManager.rmsLevel.collectAsState()
  val useWavAiGateway by sttManager.useWavAiGateway.collectAsState()
  val rawTranscript by sttManager.lastRawTranscript.collectAsState()
  val correctedText by sttManager.lastCorrectedText.collectAsState()
  val resultDetails by sttManager.lastResultDetails.collectAsState()

  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  Dialog(onDismissRequest = {
    if (isRecording) sttManager.stopListening()
    onDismiss()
  }) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .padding(20.dp)
        .testTag("ai_voice_studio_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Top Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(EmeraldPrimary, IndigoAccent))),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Gateway",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
            }
            Column {
              Text(
                text = "AI Gateway Voice Studio",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "WAV STT • မြန်မာစာလုံးပေါင်း & သဒ္ဒါပြင်ဆင်မှု",
                color = EmeraldLight,
                fontSize = 11.sp
              )
            }
          }

          IconButton(
            onClick = {
              if (isRecording) sttManager.stopListening()
              onDismiss()
            },
            modifier = Modifier.testTag("close_voice_studio_button")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        // Mode switch: AI Gateway WAV vs Android Recognizer
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "WAV Format & AI Gateway STT",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = if (useWavAiGateway)
                  "16kHz WAV မိုက်သံကို AI Gateway ဖြင့် စာသားပြောင်းပြီး သဒ္ဒါအမှား ပြင်မည်"
                else
                  "Android On-Device Speech Recognizer ကို အသုံးပြုနေပါသည်",
                color = TextMuted,
                fontSize = 11.sp
              )
            }
            Switch(
              checked = useWavAiGateway,
              onCheckedChange = { sttManager.setUseWavAiGateway(it) },
              colors = SwitchDefaults.colors(
                checkedThumbColor = EmeraldPrimary,
                checkedTrackColor = EmeraldPrimary.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceCardBorder
              ),
              modifier = Modifier.testTag("toggle_wav_gateway_switch")
            )
          }
        }

        // Recording Wave & Mic Button Card
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            // Live RMS wave visualizer
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D1117))
                .padding(horizontal = 8.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              val barCount = 18
              for (i in 0 until barCount) {
                val scaleFactor = if (isRecording) {
                  val dist = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
                  (1f - dist * 0.5f) * (0.2f + rmsLevel * 0.8f)
                } else 0.1f

                Box(
                  modifier = Modifier
                    .width(4.dp)
                    .height((36 * scaleFactor).coerceIn(4f, 34f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                      if (isRecording)
                        Brush.verticalGradient(listOf(EmeraldLight, IndigoAccent))
                      else
                        Brush.verticalGradient(listOf(SurfaceCardBorder, SurfaceCardBorder))
                    )
                )
              }
            }

            // Big Record Button
            Box(
              modifier = Modifier
                .size(72.dp)
                .then(
                  if (isRecording) Modifier.scale(pulseScale) else Modifier
                )
                .clip(CircleShape)
                .background(
                  if (isRecording) Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                  else Brush.linearGradient(listOf(EmeraldPrimary, IndigoAccent))
                )
                .clickable {
                  if (isRecording) {
                    sttManager.stopListening()
                  } else {
                    sttManager.startListening("my-MM")
                  }
                }
                .testTag("voice_studio_record_button"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Stop Recording" else "Record Audio",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
              )
            }

            Text(
              text = if (isRecording)
                "အသံဖမ်းယူနေပါသည်... စကားပြောပြီးပါက ခလုတ်ကို နှိပ်ပါ"
              else
                "မိုက်ခရိုဖုန်းကို နှိပ်၍ မြန်မာလို စကားပြောပါ",
              color = if (isRecording) Color(0xFFF87171) else TextMuted,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Output Comparison: Raw Transcript vs Corrected Myanmar Text
        if (rawTranscript.isNotEmpty() || correctedText.isNotEmpty()) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Raw Transcript Box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = "🎙️ မူရင်း အသံထွက် စာသား (Raw STT):",
                  color = TextMuted,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = rawTranscript.ifEmpty { "အသံဖမ်းယူမှု စောင့်ဆိုင်းဆဲ..." },
                  color = TextPrimary.copy(alpha = 0.85f),
                  fontSize = 14.sp
                )
              }
            }

            // AI Gateway Corrected Text Box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EmeraldPrimary.copy(alpha = 0.12f))
                .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "✨ AI Gateway မြန်မာသဒ္ဒါနှင့် စာလုံးပေါင်း ပြင်ဆင်ချက်:",
                    color = EmeraldLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                  if (resultDetails?.isCached == true) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AmberAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = "⚡ Cache Hit (0 Credit)",
                        color = AmberAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
                Text(
                  text = correctedText.ifEmpty { "သဒ္ဒါစိစစ်ချက် မရှိသေးပါ" },
                  color = Color.White,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }

            // Corrections breakdown
            resultDetails?.let { details ->
              if (details.corrections.isNotEmpty()) {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = "သတ်ပုံနှင့် သဒ္ဒါ ပြင်ဆင်ချက်များ:",
                    color = TextMuted,
                    fontSize = 11.sp
                  )
                  details.corrections.forEach { item ->
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2533))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = "• ${item.original} ➔ ${item.corrected}",
                          color = EmeraldLight,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Medium
                        )
                        Text(
                          text = item.reason,
                          color = TextMuted,
                          fontSize = 11.sp
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // Credit Saver & Protection Details Card
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1522))
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                Icons.Default.Savings,
                contentDescription = "Credit Saver",
                tint = AmberAccent,
                modifier = Modifier.size(20.dp)
              )
              Column {
                Text(
                  text = "Credit Saver & Rate Limit",
                  color = TextPrimary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "SHA-256 Audio Hash Caching + Retry Backoff",
                  color = TextMuted,
                  fontSize = 10.sp
                )
              }
            }

            val metrics = sttManager.aiGatewayClient.getMetrics()
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(EmeraldPrimary.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = "${metrics.savedApiRequests} calls saved",
                color = EmeraldLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        // TTS Selection Section
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "TTS ပြန်ဖတ်မည့် အသံ Model ရွေးချယ်မှု:",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            TtsProviderType.values().forEach { provider ->
              val isSelected = selectedTtsProvider == provider
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .background(if (isSelected) EmeraldPrimary else SurfaceCard)
                  .border(
                    1.dp,
                    if (isSelected) EmeraldLight else SurfaceCardBorder,
                    RoundedCornerShape(10.dp)
                  )
                  .clickable { onSelectTtsProvider(provider) }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = provider.displayName,
                  color = if (isSelected) Color.White else TextMuted,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              }
            }
          }
        }

        // Action Buttons: Listen via TTS & Execute Command
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              val speechText = correctedText.ifEmpty { rawTranscript }
              if (speechText.isNotEmpty()) {
                onPlayTts(speechText, selectedTtsProvider)
              }
            },
            enabled = correctedText.isNotEmpty() || rawTranscript.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldLight),
            modifier = Modifier
              .weight(1f)
              .testTag("tts_playback_button")
          ) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Play TTS", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("TTS ဖြင့် ဖတ်ပြပါ", fontSize = 12.sp)
          }

          Button(
            onClick = {
              val commandText = correctedText.ifEmpty { rawTranscript }
              if (commandText.isNotEmpty()) {
                onExecuteCommand(commandText)
                onDismiss()
              }
            },
            enabled = correctedText.isNotEmpty() || rawTranscript.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = EmeraldPrimary,
              contentColor = Color.White
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("execute_voice_command_button")
          ) {
            Icon(Icons.Default.ElectricBolt, contentDescription = "Execute", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("စနစ်ကို အမိန့်ပေးမည်", fontSize = 12.sp)
          }
        }
      }
    }
  }
}
