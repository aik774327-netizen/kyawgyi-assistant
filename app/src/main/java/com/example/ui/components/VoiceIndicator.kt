package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import com.example.ui.theme.AngelCyanAccent
import com.example.ui.theme.AngelNeonGreen

/**
 * Animated Voice Indicator with Ripple effects and status prompts
 * Converted from the imported Web/React VoiceIndicator component.
 */
@Composable
fun VoiceIndicator(
    isActive: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    listeningLabel: String = "နားထောင်နေသည်...",
    thinkingLabel: String = "စဉ်းစားတွေးခေါ်နေသည်...",
    tapLabel: String = "စတင်ပြောဆိုရန် နှိပ်ပါ",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_ripple")
    
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("voice_indicator_container"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            // Ripple 1
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(rippleScale)
                        .clip(CircleShape)
                        .background(AngelCyanAccent.copy(alpha = rippleAlpha))
                )
                // Ripple 2
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(rippleScale * 0.85f)
                        .clip(CircleShape)
                        .background(AngelNeonGreen.copy(alpha = rippleAlpha * 0.7f))
                )
            }

            // Main Core Voice Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF00E676))
                            )
                        } else if (isProcessing) {
                            Brush.linearGradient(
                                listOf(Color(0xFF673AB7), Color(0xFF3F51B5))
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                    .testTag("voice_indicator_main_btn")
            ) {
                if (isProcessing) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = if (isActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = "Voice State",
                        tint = if (isActive) Color.Black else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Label Text
        Text(
            text = when {
                isActive -> listeningLabel
                isProcessing -> thinkingLabel
                else -> tapLabel
            },
            color = if (isActive) AngelNeonGreen else Color.LightGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("voice_indicator_status_label")
        )
    }
}
