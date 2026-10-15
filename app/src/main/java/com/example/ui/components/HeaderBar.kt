package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun HeaderBar(
  isListening: Boolean,
  onOpenMemory: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAngelChat: () -> Unit,
  onOpenMusicLibrary: () -> Unit,
  onOpenVoiceStudio: () -> Unit = {},
  onOpenDirectRoutes: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseAlpha"
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Left: Mic Badge + Title
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(if (isListening) Color(0x3310B981) else SurfaceCard)
          .border(
            width = 1.2.dp,
            color = if (isListening) EmeraldPrimary else SurfaceCardBorder,
            shape = RoundedCornerShape(12.dp)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = "Microphone Status",
          tint = if (isListening) EmeraldLight else TextMuted,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Text(
          text = "Kyaw Gyi AI",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          style = androidx.compose.ui.text.TextStyle(
            brush = Brush.horizontalGradient(
              colors = listOf(EmeraldLight, Color(0xFF5EEAD4))
            )
          )
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 2.dp)
        ) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(EmeraldPrimary.copy(alpha = pulseAlpha))
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(
            text = "ACTIVE",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.sp
          )
        }
      }
    }

    // Right Action Buttons: AI Voice Studio, AI Angel, Music, Memory, Settings
    Row(verticalAlignment = Alignment.CenterVertically) {
      // AI Voice Studio (WAV & Myanmar Grammar) Button
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF8B5CF6).copy(alpha = 0.25f), EmeraldPrimary.copy(alpha = 0.25f))
            )
          )
          .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .clickable { onOpenVoiceStudio() }
          .padding(horizontal = 8.dp, vertical = 7.dp)
          .testTag("ai_voice_studio_header_button"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = "AI Voice Studio",
          tint = Color(0xFFA78BFA),
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Studio",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // AI Angel Chat Button
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF0284C7).copy(alpha = 0.25f), EmeraldPrimary.copy(alpha = 0.25f))
            )
          )
          .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
          .clickable { onOpenAngelChat() }
          .padding(horizontal = 10.dp, vertical = 7.dp)
          .testTag("ai_angel_button"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = "AI Angel Chatbox",
          tint = Color(0xFF38BDF8),
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = "Angel",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Music Library Button
      IconButton(
        onClick = onOpenMusicLibrary,
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
          .testTag("music_library_header_button")
      ) {
        Icon(
          imageVector = Icons.Default.Audiotrack,
          contentDescription = "Music Library",
          tint = EmeraldLight,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Memory Button
      IconButton(
        onClick = onOpenMemory,
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
          .testTag("memory_button")
      ) {
        Icon(
          imageVector = Icons.Default.Psychology,
          contentDescription = "Memory Dashboard",
          tint = EmeraldPrimary,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Direct Routes Button
      IconButton(
        onClick = onOpenDirectRoutes,
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
          .testTag("direct_routes_button")
      ) {
        Icon(
          imageVector = Icons.Default.AltRoute,
          contentDescription = "Direct Routes & Permissions",
          tint = Color(0xFF38BDF8),
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      // Settings Icon
      IconButton(
        onClick = onOpenSettings,
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(SurfaceCard)
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
          .testTag("settings_button")
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = "Settings",
          tint = TextMuted,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

