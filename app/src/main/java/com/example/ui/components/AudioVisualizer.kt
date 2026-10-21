package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AngelCyanAccent
import com.example.ui.theme.AngelNeonGreen

/**
 * Animated Audio Visualizer with 5 rhythmic bars responding to volume/activity.
 * Converted from the imported React Visualizer component.
 */
@Composable
fun AudioVisualizer(
    volume: Float = 0.5f,
    isConnected: Boolean = true,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer_anim")

    val barHeights = listOf(
        infiniteTransition.animateFloat(
            initialValue = 12f, targetValue = if (isConnected) 40f * volume.coerceIn(0.2f, 1.2f) else 8f,
            animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1"
        ),
        infiniteTransition.animateFloat(
            initialValue = 18f, targetValue = if (isConnected) 65f * volume.coerceIn(0.2f, 1.2f) else 8f,
            animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2"
        ),
        infiniteTransition.animateFloat(
            initialValue = 24f, targetValue = if (isConnected) 85f * volume.coerceIn(0.2f, 1.2f) else 8f,
            animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3"
        ),
        infiniteTransition.animateFloat(
            initialValue = 16f, targetValue = if (isConnected) 55f * volume.coerceIn(0.2f, 1.2f) else 8f,
            animationSpec = infiniteRepeatable(tween(310, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h4"
        ),
        infiniteTransition.animateFloat(
            initialValue = 10f, targetValue = if (isConnected) 35f * volume.coerceIn(0.2f, 1.2f) else 8f,
            animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h5"
        )
    )

    val gradientColors = if (isSpeaking) {
        listOf(Color(0xFF818CF8), Color(0xFFC084FC))
    } else {
        listOf(AngelNeonGreen, AngelCyanAccent)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 24.dp)
            .testTag("audio_visualizer_bars"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barHeights.forEach { animatedHeight ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(animatedHeight.value.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(gradientColors))
            )
        }
    }
}
