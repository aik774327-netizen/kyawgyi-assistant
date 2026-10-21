package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AngelNeonGreen
import java.util.Locale

/**
 * A reusable circular progress indicator component to visually display
 * remaining time and progress for focus sessions (Pomodoro, deep work, breaks).
 *
 * @param remainingSeconds Remaining time in seconds.
 * @param totalSeconds Total planned time in seconds.
 * @param isRunning Whether the countdown timer is actively ticking.
 * @param modifier Custom modifier for outer container sizing and styling.
 * @param size Diameter of the circular progress indicator.
 * @param strokeWidth Thickness of the circular progress track.
 * @param primaryColor Main color for progress arc and glow.
 * @param secondaryColor Secondary gradient color for the progress arc.
 * @param trackColor Color for the background circular track.
 * @param categoryName Optional label displayed beneath or inside the ring.
 * @param centerContent Optional custom slot to completely override the interior content.
 */
@Composable
fun CircularFocusProgressIndicator(
    remainingSeconds: Long,
    totalSeconds: Long,
    isRunning: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    strokeWidth: Dp = 12.dp,
    primaryColor: Color = AngelNeonGreen,
    secondaryColor: Color = Color(0xFF00E5FF),
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    categoryName: String? = null,
    centerContent: (@Composable BoxScope.(progress: Float, formattedTime: String) -> Unit)? = null
) {
    val progress = if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Smooth animation for circular progress transition
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "CircularFocusProgress"
    )

    // Gentle pulsing animation when the session is actively running
    val infiniteTransition = rememberInfiniteTransition(label = "TimerPulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isRunning) 0.15f else 0.05f,
        targetValue = if (isRunning) 0.45f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)

    Box(
        modifier = modifier
            .size(size)
            .testTag("circular_focus_progress_indicator"),
        contentAlignment = Alignment.Center
    ) {
        // Background subtle pulse glow when active
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = pulseAlpha))
        )

        // Circular Canvas Arc
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            // Background Circular Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Animated Progress Sweep Arc (ticking down clockwise or counter-clockwise)
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryColor,
                            secondaryColor,
                            primaryColor
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Interior Center Content
        if (centerContent != null) {
            centerContent(animatedProgress, formattedTime)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isRunning) primaryColor else MaterialTheme.colorScheme.outline).copy(alpha = 0.18f),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.PlayArrow else Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isRunning) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isRunning) "FOCUSING" else "READY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Digital Time Display
                Text(
                    text = formattedTime,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("circular_focus_time_text")
                )

                // Category or Percentage remaining
                val percentRemaining = (progress * 100).toInt()
                val subText = categoryName ?: "$percentRemaining% remaining"
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
