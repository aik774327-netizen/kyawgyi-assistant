package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoreEngine
import com.example.ui.theme.*

@Composable
fun CoreEngineBar(
    selectedEngine: CoreEngine,
    onEngineSelected: (CoreEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("core_engine_bar"),
        color = AngelCardHeader
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoreEngine.values().forEach { engine ->
                val isSelected = engine == selectedEngine
                val bgTint by animateColorAsState(
                    if (isSelected) AngelNeonGreen.copy(alpha = 0.18f) else AngelSurfaceDark
                )
                val borderColor by animateColorAsState(
                    if (isSelected) AngelNeonGreen else Color.White.copy(alpha = 0.08f)
                )
                val textColor by animateColorAsState(
                    if (isSelected) AngelNeonGreen else AngelTextSecondary
                )

                Surface(
                    onClick = { onEngineSelected(engine) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("engine_tab_${engine.name}"),
                    shape = RoundedCornerShape(12.dp),
                    color = bgTint,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val (icon, title) = when (engine) {
                            CoreEngine.RESPONSE_ENGINE -> Icons.Filled.AutoAwesome to "Response\nEngine"
                            CoreEngine.BRAIN_CORE -> Icons.Filled.Psychology to "Brain\nCore"
                            CoreEngine.KYAW_GYI_VOICE -> Icons.Filled.Mic to "ကျော်ကြီး\nVoice"
                            CoreEngine.MEMORY_SYSTEM -> Icons.Filled.Memory to "Memory\nSystem"
                            CoreEngine.SETTINGS -> Icons.Filled.Settings to "Settings\nControl"
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = engine.title,
                            tint = textColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor,
                            lineHeight = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
