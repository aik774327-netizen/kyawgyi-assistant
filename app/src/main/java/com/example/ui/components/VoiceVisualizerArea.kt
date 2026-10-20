package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

enum class QuickActionCategory(val title: String) {
  ALL("အားလုံး"),
  FAN("🌀 KENNEDE ပန်ကာ"),
  ECOFLOW("⚡ EcoFlow"),
  SYSTEM("📱 ဖုန်းစနစ်")
}

data class QuickActionItem(
  val label: String,
  val command: String,
  val category: QuickActionCategory,
  val subtext: String,
  val icon: ImageVector
)

@Composable
fun VoiceVisualizerArea(
  isListening: Boolean,
  speechStatus: String,
  lastMessage: String?,
  lastFailureWarning: String? = null,
  volumeLevel: Float,
  isNetworkAvailable: Boolean = true,
  isOfflineFallbackActive: Boolean = false,
  // Audit UI-2: an onToggleOfflineFallback callback used to be declared here
  // and wired by MainScreen, but nothing in this composable ever invoked it.
  // isOfflineFallbackActive is a system STATUS (set by the ViewModel when
  // recognition falls back), not a user preference — the real user control
  // is Offline Mode in the status bar — so the dead callback was removed
  // rather than wired to a switch that would itself have been decorative.
  onMicClick: () -> Unit,
  onSendCommand: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var commandText by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(QuickActionCategory.ALL) }
  var isOfflinePanelExpanded by remember { mutableStateOf(true) }

  val infiniteTransition = rememberInfiniteTransition(label = "ring_glow")
  val ringScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isListening) 1.28f else 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ringScale"
  )

  val quickActions = remember {
    listOf(
      // KENNEDE Fan Commands
      QuickActionItem("ပန်ကာဖွင့်", "ပန်ကာဖွင့်", QuickActionCategory.FAN, "Power ON/OFF", Icons.Default.FlashOn),
      QuickActionItem("ပန်ကာပိတ်", "ပန်ကာပိတ်", QuickActionCategory.FAN, "Power OFF", Icons.Default.FlashOn),
      QuickActionItem("လေအရှိန်မြှင့်", "ပန်ကာ အရှိန်မြှင့်", QuickActionCategory.FAN, "Speed +", Icons.Default.Speed),
      QuickActionItem("လေအရှိန်လျှော့", "ပန်ကာ အရှိန်လျှော့", QuickActionCategory.FAN, "Speed -", Icons.Default.Speed),
      QuickActionItem("မီးဖွင့်/ပိတ်", "ပန်ကာ မီးဖွင့်", QuickActionCategory.FAN, "LED Light", Icons.Default.Lightbulb),
      QuickActionItem("ဘယ်/ညာ လှည့်", "ပန်ကာ လှည့်ပါ", QuickActionCategory.FAN, "Oscillation", Icons.Default.Air),
      QuickActionItem("မုဒ်ပြောင်း", "ပန်ကာ မုဒ်ပြောင်း", QuickActionCategory.FAN, "Natural/Sleep", Icons.Default.Air),
      QuickActionItem("အချိန်မှတ် ၃၀ မိနစ်", "ပန်ကာ အချိန်မှတ် ၃၀ မိနစ်", QuickActionCategory.FAN, "Timer 30m", Icons.Default.Speed),

      // EcoFlow Commands
      QuickActionItem("အေစီဖွင့်ပါ", "အေစီဖွင့်ပါ", QuickActionCategory.ECOFLOW, "AC 220V ON", Icons.Default.FlashOn),
      QuickActionItem("အေစီပိတ်ပါ", "အေစီပိတ်ပါ", QuickActionCategory.ECOFLOW, "AC OFF", Icons.Default.FlashOn),
      QuickActionItem("ဒီစီဖွင့်ပါ", "ဒီစီဖွင့်ပါ", QuickActionCategory.ECOFLOW, "DC 12V ON", Icons.Default.FlashOn),
      QuickActionItem("ဘက်ထရီ အခြေအနေ", "ဘက်ထရီ အခြေအနေ", QuickActionCategory.ECOFLOW, "Battery %", Icons.Default.Info),

      // System Commands
      QuickActionItem("မေမေ့ဆီ ဖုန်းခေါ်", "မေမေ့ဆီ ဖုန်းခေါ်", QuickActionCategory.SYSTEM, "Speed Dial", Icons.Default.PhoneAndroid),
      QuickActionItem("ဝိုင်ဖိုင်ဖွင့်", "ဝိုင်ဖိုင်ဖွင့်", QuickActionCategory.SYSTEM, "Wi-Fi Toggle", Icons.Default.Wifi),
      QuickActionItem("ဖေ့စ်ဘုတ်ဖွင့်", "ဖေ့စ်ဘုတ်ဖွင့်", QuickActionCategory.SYSTEM, "Open Facebook", Icons.Default.PhoneAndroid),
      QuickActionItem("ယူကျုဖွင့်", "ယူကျုဖွင့်", QuickActionCategory.SYSTEM, "Open YouTube", Icons.Default.PhoneAndroid),
      QuickActionItem("ကေပေးဖွင့်", "ကေပေးဖွင့်", QuickActionCategory.SYSTEM, "Open KBZPay", Icons.Default.PhoneAndroid)
    )
  }

  val filteredActions = remember(selectedCategory) {
    if (selectedCategory == QuickActionCategory.ALL) {
      quickActions
    } else {
      quickActions.filter { it.category == selectedCategory }
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top
  ) {
    // 1. Animated Waveform Visualizer (5 reactive bars)
    Row(
      modifier = Modifier
        .height(44.dp)
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      val barHeights = listOf(
        0.3f to 0.7f,
        0.5f to 0.9f,
        0.8f to 1.3f, // center bar
        0.5f to 0.9f,
        0.3f to 0.7f
      )

      barHeights.forEachIndexed { index, (_, multF) ->
        val targetHeight = if (isListening) {
          (14f + (volumeLevel * 36f * multF)).coerceIn(8f, 44f)
        } else {
          8f
        }
        val animatedH by animateFloatAsState(targetValue = targetHeight, label = "bar_$index")

        Box(
          modifier = Modifier
            .width(6.dp)
            .height(animatedH.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(
              if (isListening) {
                Brush.verticalGradient(listOf(EmeraldLight, EmeraldPrimary))
              } else {
                Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
              }
            )
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 2. Status / Failure / Last Message
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(horizontal = 8.dp)
    ) {
      Text(
        text = speechStatus,
        fontSize = if (isListening) 15.sp else 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (isListening) EmeraldLight else TextPrimary,
        textAlign = TextAlign.Center
      )

      // Rule 3 & 4 Warning Banner: "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"
      if (!lastFailureWarning.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33EF4444))
            .border(1.2.dp, RoseError, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("failure_warning_banner")
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Failure Warning",
              tint = AmberAccent,
              modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = lastFailureWarning,
              fontSize = 11.sp,
              color = Color(0xFFFECDD3),
              lineHeight = 16.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      } else if (!lastMessage.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1A10B981))
            .border(1.dp, Color(0x3310B981), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = lastMessage,
            fontSize = 11.sp,
            color = Color(0xFFA7F3D0),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 3. Giant Glowing Mic Button with Pulse Ring
    Box(contentAlignment = Alignment.Center) {
      if (isListening) {
        Box(
          modifier = Modifier
            .size(76.dp)
            .scale(ringScale)
            .clip(CircleShape)
            .background(Color(0x2E10B981))
        )
      }

      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(
            if (isListening) {
              Brush.radialGradient(listOf(Color(0xFF047857), Color(0xFF065F46)))
            } else {
              Brush.radialGradient(listOf(EmeraldLight, EmeraldPrimary))
            }
          )
          .clickable { onMicClick() }
          .border(
            width = 3.dp,
            color = if (isListening) RoseError.copy(alpha = 0.8f) else Color(0x66FFFFFF),
            shape = CircleShape
          )
          .testTag("main_microphone_button"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
          contentDescription = if (isListening) "Stop Listening" else "Start Talking",
          tint = Color.White,
          modifier = Modifier.size(26.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = if (isListening) "နားထောင်နေသည် (ရပ်တန့်ရန် နှိပ်ပါ)" else "စကားသံဖြင့် အမိန့်ပေးရန် နှိပ်ပါ",
      fontSize = 11.sp,
      color = TextMuted,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    // 4. Interactive Voice/Text Command Input Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(SurfaceCard)
        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
        .padding(horizontal = 8.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = commandText,
        onValueChange = { commandText = it },
        placeholder = {
          Text(text = "အမိန့်ပေးလိုသော စကား ရိုက်ထည့်ပါ...", fontSize = 11.sp, color = TextMuted)
        },
        modifier = Modifier
          .weight(1f)
          .testTag("voice_command_input"),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedBorderColor = Color.Transparent,
          unfocusedBorderColor = Color.Transparent,
          cursorColor = EmeraldPrimary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = {
          if (commandText.isNotBlank()) {
            onSendCommand(commandText)
            commandText = ""
          }
        })
      )

      IconButton(
        onClick = {
          if (commandText.isNotBlank()) {
            onSendCommand(commandText)
            commandText = ""
          }
        },
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(EmeraldPrimary)
          .testTag("send_command_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "Send Command",
          tint = Color.Black,
          modifier = Modifier.size(16.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // =========================================================================
    // 5. Offline Graceful Fallback & Quick Action Chips (အချက် ၆)
    // =========================================================================
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
          if (!isNetworkAvailable || isOfflineFallbackActive) Color(0xFF0F172A) else Color(0xFF0B132B)
        )
        .border(
          width = 1.2.dp,
          color = if (!isNetworkAvailable || isOfflineFallbackActive) AmberAccent.copy(alpha = 0.8f) else Color(0xFF1E293B),
          shape = RoundedCornerShape(16.dp)
        )
        .testTag("offline_fallback_banner")
        .padding(12.dp)
    ) {
      Column {
        // Fallback Header Bar with Network Status Pill
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { isOfflinePanelExpanded = !isOfflinePanelExpanded },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                  if (!isNetworkAvailable || isOfflineFallbackActive) Color(0x33F59E0B) else Color(0x3310B981)
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (!isNetworkAvailable) Icons.Default.CloudOff else Icons.Default.TouchApp,
                contentDescription = "Offline Action",
                tint = if (!isNetworkAvailable || isOfflineFallbackActive) AmberAccent else EmeraldLight,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Offline Quick Action Chips (ခလုတ်တိုများ)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (!isNetworkAvailable || isOfflineFallbackActive) AmberAccent else Color(0xFFE2E8F0)
              )
              Text(
                text = if (!isNetworkAvailable || isOfflineFallbackActive)
                  "🟠 အရင်းအမြစ် လိုအပ်ချက်: အင်တာနက်မလိုဘဲ ခလုတ်နှိပ်၍ ခိုင်းစေနိုင်သည်"
                else
                  "အင်တာနက်မလိုဘဲ တစ်ချက်နှိပ်၍ ချက်ချင်းခိုင်းစေနိုင်သည်",
                fontSize = 10.sp,
                color = if (!isNetworkAvailable || isOfflineFallbackActive) AmberAccent.copy(alpha = 0.9f) else TextMuted
              )
            }
          }

          // Online / Offline Status Badge
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (!isNetworkAvailable) Color(0x33EF4444) else Color(0x3310B981))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = if (!isNetworkAvailable) Icons.Default.WifiOff else Icons.Default.Wifi,
                  contentDescription = null,
                  tint = if (!isNetworkAvailable) RoseError else EmeraldLight,
                  modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (!isNetworkAvailable) "OFFLINE" else "ONLINE",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (!isNetworkAvailable) RoseError else EmeraldLight
                )
              }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = if (isOfflinePanelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        AnimatedVisibility(
          visible = isOfflinePanelExpanded,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically()
        ) {
          Column {
            // Offline Guidance & Language Pack Tip Box
            if (!isNetworkAvailable || isOfflineFallbackActive) {
              Spacer(modifier = Modifier.height(8.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color(0xFF1E293B))
                  .border(0.8.dp, Color(0xFF475569), RoundedCornerShape(10.dp))
                  .padding(10.dp)
              ) {
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Default.Info,
                      contentDescription = null,
                      tint = AmberAccent,
                      modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "🟠 အရင်းအမြစ် လိုအပ်ချက် (Resource Constraint) သတိပေးချက်",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = AmberAccent
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "• ဖြစ်ရသည့် အကြောင်းရင်း: Google Speech Recognizer သည် စက်တွင်း အော့ဖ်လိုင်း မြန်မာစကားသံဖိုင် (Offline Pack) သို့မဟုတ် အင်တာနက် လိုင်းမရှိပါက စကားသံဖမ်းယူ၍ မရနိုင်ပါ (စနစ်သည် အတုအယောင် ဟန်ပြဒေတာ လုံးဝ မသုံးပါ)။\n• အစားထိုး ဖြေရှင်းနည်း: အင်တာနက်မလိုဘဲ တိုက်ရိုက်ခိုင်းစေနိုင်သည့် အောက်ပါ Quick Action Chips များကို နှိပ်၍ ခိုင်းစေနိုင်ပါသည်။",
                    fontSize = 10.sp,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 15.sp
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "💡 လမ်းညွှန်: အင်တာနက်မရှိဘဲ စကားသံဖမ်းယူလိုပါက Phone Settings > System > Languages > Voice Typing > Offline Speech Recognition တွင် Myanmar/English Pack ကို ဒေါင်းလုဒ် ရယူထားနိုင်ပါသည်။",
                    fontSize = 9.5.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 14.sp
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Tabs
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              QuickActionCategory.values().forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) EmeraldPrimary else Color(0xFF1E293B))
                    .clickable { selectedCategory = cat }
                    .border(
                      width = 0.8.dp,
                      color = if (isSelected) EmeraldLight else Color(0xFF334155),
                      shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("offline_category_${cat.name}")
                ) {
                  Text(
                    text = cat.title,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color.Black else Color(0xFFCBD5E1)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Action Chips Grid / List
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              filteredActions.chunked(2).forEach { rowItems ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  rowItems.forEach { item ->
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .clickable {
                          onSendCommand(item.command)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("offline_chip_${item.label}")
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                          imageVector = item.icon,
                          contentDescription = null,
                          tint = EmeraldLight,
                          modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                          Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF8FAFC)
                          )
                          Text(
                            text = item.subtext,
                            fontSize = 9.sp,
                            color = TextMuted
                          )
                        }
                      }
                    }
                  }
                  if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
