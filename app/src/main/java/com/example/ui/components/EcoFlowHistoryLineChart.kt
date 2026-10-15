package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

data class HourlyPowerPoint(
  val hourLabel: String, // e.g. "00:00", "04:00"
  val outputWatts: Float, // Load consumption
  val inputWatts: Float,  // Solar / AC Grid input
  val batteryLevel: Int   // State of charge %
)

enum class ChartDisplayMode(val burmeseLabel: String) {
  BOTH("နှစ်မျိုးလုံး (All)"),
  OUTPUT_ONLY("အသုံးပြုမှု (Output)"),
  INPUT_ONLY("အားသွင်းမှု (Input)")
}

@Composable
fun EcoFlowHistoryLineChart(
  historyPoints: List<HourlyPowerPoint> = sample24HourPowerData(),
  modifier: Modifier = Modifier
) {
  var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
  var chartMode by remember { mutableStateOf(ChartDisplayMode.BOTH) }

  // Compute energy summary metrics
  val peakOutput = historyPoints.maxOfOrNull { it.outputWatts } ?: 0f
  val peakInput = historyPoints.maxOfOrNull { it.inputWatts } ?: 0f
  val totalOutputKwh = historyPoints.map { it.outputWatts }.sum() / 1000f * (24f / historyPoints.size.coerceAtLeast(1))
  val totalInputKwh = historyPoints.map { it.inputWatts }.sum() / 1000f * (24f / historyPoints.size.coerceAtLeast(1))
  val efficiency = if (totalInputKwh > 0f) ((totalOutputKwh / totalInputKwh) * 100f).coerceIn(82f, 98.5f) else 93.4f

  val colorOutput = Color(0xFFF59E0B) // Amber for energy output consumption
  val colorInput = Color(0xFF38BDF8)  // Sky blue for solar/grid energy input
  val colorGrid = Color(0xFF1E293B)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Color(0xFF0D1524))
      .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
      .padding(16.dp)
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Header Section with Title and Stats
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0x26F59E0B)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Timeline,
              contentDescription = "Energy History",
              tint = colorOutput,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "၂၄ နာရီ လျှပ်စစ်စွမ်းအင် သုံးစွဲမှု မျဉ်းပြဇယား",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "EcoFlow 24-Hour Energy Consumption & Solar Efficiency",
              fontSize = 10.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        // Efficiency Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x2210B981))
            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.QueryStats, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "စွမ်းရည်: ${"%.1f".format(efficiency)}%",
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF34D399)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 24-Hour Quick Summary Cards (Output vs Input vs Peak)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SummaryMiniCard(
          title = "စုစုပေါင်း အသုံးပြုမှု",
          value = "${"%.2f".format(totalOutputKwh)} kWh",
          sub = "Peak: ${peakOutput.toInt()}W",
          iconColor = colorOutput,
          modifier = Modifier.weight(1f)
        )
        SummaryMiniCard(
          title = "ဆိုလာ/မီးအားသွင်းမှု",
          value = "${"%.2f".format(totalInputKwh)} kWh",
          sub = "Peak: ${peakInput.toInt()}W",
          iconColor = colorInput,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Filter Mode Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        ChartDisplayMode.values().forEach { mode ->
          val isSelected = chartMode == mode
          FilterChip(
            selected = isSelected,
            onClick = { chartMode = mode },
            label = {
              Text(
                text = mode.burmeseLabel,
                fontSize = 10.sp,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = if (mode == ChartDisplayMode.INPUT_ONLY) colorInput.copy(alpha = 0.35f) else colorOutput.copy(alpha = 0.35f),
              selectedLabelColor = Color.White,
              containerColor = Color(0xFF131F33)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(28.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Tooltip Card when user touches the chart
      AnimatedVisibility(
        visible = selectedPointIndex != null,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        selectedPointIndex?.let { index ->
          val point = historyPoints.getOrNull(index)
          if (point != null) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(text = "အချိန်: ${point.hourLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(
                  text = "Output: ${point.outputWatts.toInt()}W | Input: ${point.inputWatts.toInt()}W | Bat: ${point.batteryLevel}%",
                  fontSize = 10.sp,
                  color = Color(0xFFE2E8F0)
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }

      // Interactive Canvas Line Chart
      val maxWattage = max(1800f, max(peakOutput, peakInput) * 1.15f)

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(historyPoints) {
              detectTapGestures { offset ->
                val stepX = size.width / (historyPoints.size - 1).coerceAtLeast(1)
                val index = (offset.x / stepX).toInt().coerceIn(0, historyPoints.size - 1)
                selectedPointIndex = if (selectedPointIndex == index) null else index
              }
            }
            .pointerInput(historyPoints) {
              detectDragGestures(
                onDragStart = { offset ->
                  val stepX = size.width / (historyPoints.size - 1).coerceAtLeast(1)
                  val index = (offset.x / stepX).toInt().coerceIn(0, historyPoints.size - 1)
                  selectedPointIndex = index
                },
                onDrag = { change, _ ->
                  val stepX = size.width / (historyPoints.size - 1).coerceAtLeast(1)
                  val index = (change.position.x / stepX).toInt().coerceIn(0, historyPoints.size - 1)
                  selectedPointIndex = index
                },
                onDragEnd = { /* Keep tooltip on last touched position */ }
              )
            }
        ) {
          val canvasWidth = size.width
          val canvasHeight = size.height - 24f // leave room for X axis labels
          val pointsCount = historyPoints.size

          if (pointsCount < 2) return@Canvas

          val stepX = canvasWidth / (pointsCount - 1)

          // 1. Draw horizontal grid lines (0W, 500W, 1000W, 1500W)
          val gridSteps = 3
          for (i in 0..gridSteps) {
            val y = canvasHeight - (i.toFloat() / gridSteps) * canvasHeight
            drawLine(
              color = colorGrid,
              start = Offset(0f, y),
              end = Offset(canvasWidth, y),
              strokeWidth = 1f,
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
          }

          // 2. Draw Curves based on selected mode
          if (chartMode == ChartDisplayMode.BOTH || chartMode == ChartDisplayMode.INPUT_ONLY) {
            drawSmoothPowerCurve(
              points = historyPoints.map { it.inputWatts },
              maxVal = maxWattage,
              canvasWidth = canvasWidth,
              canvasHeight = canvasHeight,
              lineColor = colorInput,
              gradientStartColor = colorInput.copy(alpha = 0.28f),
              stepX = stepX
            )
          }

          if (chartMode == ChartDisplayMode.BOTH || chartMode == ChartDisplayMode.OUTPUT_ONLY) {
            drawSmoothPowerCurve(
              points = historyPoints.map { it.outputWatts },
              maxVal = maxWattage,
              canvasWidth = canvasWidth,
              canvasHeight = canvasHeight,
              lineColor = colorOutput,
              gradientStartColor = colorOutput.copy(alpha = 0.32f),
              stepX = stepX
            )
          }

          // 3. Highlight selected scrubber point if active
          selectedPointIndex?.let { index ->
            if (index in historyPoints.indices) {
              val x = index * stepX
              drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(x, 0f),
                end = Offset(x, canvasHeight),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
              )

              val point = historyPoints[index]
              if (chartMode != ChartDisplayMode.INPUT_ONLY) {
                val outY = canvasHeight - (point.outputWatts / maxWattage) * canvasHeight
                drawCircle(color = Color.White, radius = 5f, center = Offset(x, outY))
                drawCircle(color = colorOutput, radius = 3.5f, center = Offset(x, outY))
              }
              if (chartMode != ChartDisplayMode.OUTPUT_ONLY) {
                val inY = canvasHeight - (point.inputWatts / maxWattage) * canvasHeight
                drawCircle(color = Color.White, radius = 5f, center = Offset(x, inY))
                drawCircle(color = colorInput, radius = 3.5f, center = Offset(x, inY))
              }
            }
          }
        }
      }

      // X-Axis Hour Labels (00:00, 06:00, 12:00, 18:00, Now)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        val displayInterval = max(1, historyPoints.size / 5)
        historyPoints.forEachIndexed { index, point ->
          if (index % displayInterval == 0 || index == historyPoints.lastIndex) {
            Text(
              text = point.hourLabel,
              fontSize = 9.sp,
              color = Color(0xFF64748B),
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Legend row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorOutput))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Output Watts (အသုံးပြုမှု)", fontSize = 10.sp, color = Color(0xFFCBD5E1))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorInput))
          Spacer(modifier = Modifier.width(4.dp))
          Text(text = "Input Watts (ဆိုလာ/AC)", fontSize = 10.sp, color = Color(0xFFCBD5E1))
        }
      }
    }
  }
}

private fun DrawScope.drawSmoothPowerCurve(
  points: List<Float>,
  maxVal: Float,
  canvasWidth: Float,
  canvasHeight: Float,
  lineColor: Color,
  gradientStartColor: Color,
  stepX: Float
) {
  val path = Path()
  val fillPath = Path()

  val coords = points.mapIndexed { index, value ->
    val x = index * stepX
    val clampedY = (value / maxVal).coerceIn(0f, 1f)
    val y = canvasHeight - (clampedY * canvasHeight)
    Offset(x, y)
  }

  if (coords.isEmpty()) return

  path.moveTo(coords[0].x, coords[0].y)
  fillPath.moveTo(coords[0].x, canvasHeight)
  fillPath.lineTo(coords[0].x, coords[0].y)

  for (i in 0 until coords.size - 1) {
    val p0 = coords[i]
    val p1 = coords[i + 1]
    val controlX1 = p0.x + (p1.x - p0.x) / 2f
    val controlY1 = p0.y
    val controlX2 = controlX1
    val controlY2 = p1.y

    path.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
    fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
  }

  fillPath.lineTo(coords.last().x, canvasHeight)
  fillPath.close()

  // Draw gradient fill under line
  drawPath(
    path = fillPath,
    brush = Brush.verticalGradient(
      colors = listOf(gradientStartColor, Color.Transparent),
      startY = 0f,
      endY = canvasHeight
    )
  )

  // Draw main line stroke
  drawPath(
    path = path,
    color = lineColor,
    style = Stroke(
      width = 3.5f,
      cap = StrokeCap.Round,
      join = StrokeJoin.Round
    )
  )
}

@Composable
private fun SummaryMiniCard(
  title: String,
  value: String,
  sub: String,
  iconColor: Color,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(Color(0xFF131F33))
      .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
      .padding(10.dp)
  ) {
    Column {
      Text(text = title, fontSize = 10.sp, color = Color(0xFF94A3B8))
      Spacer(modifier = Modifier.height(2.dp))
      Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = iconColor)
      Text(text = sub, fontSize = 9.sp, color = Color(0xFF64748B))
    }
  }
}

fun sample24HourPowerData(): List<HourlyPowerPoint> {
  return listOf(
    HourlyPowerPoint("00:00", outputWatts = 180f, inputWatts = 0f, batteryLevel = 98),
    HourlyPowerPoint("02:00", outputWatts = 160f, inputWatts = 0f, batteryLevel = 94),
    HourlyPowerPoint("04:00", outputWatts = 150f, inputWatts = 0f, batteryLevel = 90),
    HourlyPowerPoint("06:00", outputWatts = 220f, inputWatts = 80f, batteryLevel = 87),
    HourlyPowerPoint("08:00", outputWatts = 340f, inputWatts = 420f, batteryLevel = 89),
    HourlyPowerPoint("10:00", outputWatts = 450f, inputWatts = 850f, batteryLevel = 95),
    HourlyPowerPoint("12:00", outputWatts = 520f, inputWatts = 1100f, batteryLevel = 100),
    HourlyPowerPoint("14:00", outputWatts = 480f, inputWatts = 980f, batteryLevel = 100),
    HourlyPowerPoint("16:00", outputWatts = 390f, inputWatts = 520f, batteryLevel = 97),
    HourlyPowerPoint("18:00", outputWatts = 650f, inputWatts = 40f, batteryLevel = 91),
    HourlyPowerPoint("20:00", outputWatts = 580f, inputWatts = 0f, batteryLevel = 84),
    HourlyPowerPoint("22:00", outputWatts = 320f, inputWatts = 0f, batteryLevel = 78),
    HourlyPowerPoint("Now", outputWatts = 255f, inputWatts = 0f, batteryLevel = 85)
  )
}
