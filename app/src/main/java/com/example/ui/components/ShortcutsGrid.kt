package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.CustomShortcut
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun ShortcutsGrid(
  shortcuts: List<CustomShortcut>,
  selectedLanguage: String,
  onLanguageSelect: (String) -> Unit,
  onShortcutClick: (CustomShortcut) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
      .background(Color(0xFF0D1424))
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
      .padding(horizontal = 20.dp, vertical = 18.dp)
  ) {
    // Header & Language Selector
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "SHORTCUTS များ",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.2.sp
      )

      // Language Switcher (မြန်မာ / ENG / 中文)
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF070B11))
          .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
          .padding(3.dp)
      ) {
        LanguageChip(
          label = "မြန်မာ",
          isSelected = selectedLanguage == "my",
          onClick = { onLanguageSelect("my") }
        )
        LanguageChip(
          label = "ENG",
          isSelected = selectedLanguage == "en",
          onClick = { onLanguageSelect("en") }
        )
        LanguageChip(
          label = "中文",
          isSelected = selectedLanguage == "zh",
          onClick = { onLanguageSelect("zh") }
        )
      }
    }

    // Grid of 4 columns (up to 8 items)
    val rows = shortcuts.chunked(4)
    rows.forEach { rowItems ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        rowItems.forEach { sc ->
          ShortcutButton(
            shortcut = sc,
            onClick = { onShortcutClick(sc) },
            modifier = Modifier.weight(1f)
          )
        }
        // Fill empty cells if row < 4
        if (rowItems.size < 4) {
          repeat(4 - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
fun LanguageChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (isSelected) EmeraldPrimary else Color.Transparent)
      .clickable { onClick() }
      .padding(horizontal = 10.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) Color.Black else TextMuted
    )
  }
}

@Composable
fun ShortcutButton(
  shortcut: CustomShortcut,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val iconData = getShortcutIcon(shortcut.voiceCommand)

  Column(
    modifier = modifier
      .padding(horizontal = 4.dp)
      .clickable { onClick() }
      .testTag("shortcut_${shortcut.voiceCommand}"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(Color(shortcut.colorHex))
        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = iconData,
        contentDescription = shortcut.title,
        tint = Color.White,
        modifier = Modifier.size(26.dp)
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = shortcut.title,
      fontSize = 11.sp,
      color = TextPrimary,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

fun getShortcutIcon(cmd: String): ImageVector {
  return when {
    cmd.contains("ပန်ကာ") || cmd.contains("fan") || cmd.contains("kennede") -> Icons.Default.Air
    cmd.contains("ecoflow") || cmd.contains("ပါဝါ") -> Icons.Default.Bolt
    cmd.contains("wifi") || cmd.contains("ဝိုင်ဖိုင်") -> Icons.Default.Wifi
    cmd.contains("bluetooth") || cmd.contains("ဘလူးတုသ်") -> Icons.Default.Bluetooth
    cmd.contains("facebook") || cmd.contains("ဖေ့စ်ဘုတ်") -> Icons.Default.Facebook
    cmd.contains("youtube") || cmd.contains("ယူကျု") -> Icons.Default.PlayArrow
    cmd.contains("telegram") || cmd.contains("တယ်လီဂရမ်") -> Icons.Default.Send
    cmd.contains("kpay") || cmd.contains("ကေပေး") -> Icons.Default.CreditCard
    cmd.contains("phone") || cmd.contains("ဖုန်း") -> Icons.Default.Phone
    else -> Icons.Default.Bolt
  }
}
