package com.kyawgyi.ir.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyawgyi.ir.models.IrButton
import com.kyawgyi.ir.repository.IrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawSignalViewer(
  viewModel: IrViewModel = viewModel(),
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()

  var selectedButtonKey by remember { mutableStateOf("power") }
  val selectedButton = uiState.buttons.find { it.key == selectedButtonKey } ?: uiState.buttons.firstOrNull()

  // Default sample NEC timings if empty for visual preview
  val displayTimings = remember(selectedButton?.code?.rawTiming) {
    val timings = selectedButton?.code?.rawTiming
    if (!timings.isNullOrEmpty()) {
      timings
    } else {
      listOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 1690, 560)
    }
  }

  val isSample = selectedButton?.code?.rawTiming.isNullOrEmpty()

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("raw_signal_viewer_screen"),
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
              text = "IR RAW SIGNAL WAVEFORM",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "38 kHz Carrier Pulse Timing Inspector",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8)
            )
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.fetchCodes() },
            modifier = Modifier.testTag("refresh_signal_btn")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF38BDF8))
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Button Selection Chips
      Text(
        text = "ခလုတ် ရွေးချယ်ပါ (SELECT BUTTON)",
        color = Color(0xFF94A3B8),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        uiState.buttons.forEach { btn ->
          val isSelected = btn.key == selectedButtonKey
          val isCaptured = btn.code.captured
          FilterChip(
            selected = isSelected,
            onClick = { selectedButtonKey = btn.key },
            label = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .background(
                      if (isCaptured) Color(0xFF10B981) else Color(0xFFEF4444),
                      CircleShape
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(btn.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
              }
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFF0284C7),
              selectedLabelColor = Color.White,
              containerColor = Color(0xFF1E293B),
              labelColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier.testTag("chip_${btn.key}")
          )
        }
      }

      // 2. Waveform Canvas Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030712)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E293B)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("waveform_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${selectedButton?.name} SIGNAL PULSE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }

            if (isSample) {
              Text(
                text = "SAMPLE PREVIEW (NOT LEARNED)",
                color = Color(0xFFF59E0B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
            } else {
              Text(
                text = "${displayTimings.size} TICKS • ${(displayTimings.sum() / 1000.0).format(1)} ms",
                color = Color(0xFF10B981),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Scrollable Waveform Canvas
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .background(Color(0xFF090D16), RoundedCornerShape(10.dp))
              .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
              .horizontalScroll(rememberScrollState())
              .padding(8.dp)
          ) {
            val totalMicros = displayTimings.sum().coerceAtLeast(1000)
            val canvasWidthDp = (displayTimings.size * 18).coerceAtLeast(400)

            Canvas(
              modifier = Modifier
                .width(canvasWidthDp.dp)
                .height(160.dp)
                .testTag("signal_canvas")
            ) {
              val width = size.width
              val height = size.height

              val highY = height * 0.22f
              val lowY = height * 0.78f

              // Draw background grid
              drawLine(Color(0xFF1E293B), Offset(0f, highY), Offset(width, highY), strokeWidth = 1f)
              drawLine(Color(0xFF1E293B), Offset(0f, lowY), Offset(width, lowY), strokeWidth = 1f)

              val path = Path()
              var currentX = 10f
              var isHigh = true

              path.moveTo(currentX, lowY)

              displayTimings.forEachIndexed { index, duration ->
                val segmentWidth = ((duration.toFloat() / totalMicros) * (width - 40f)).coerceAtLeast(6f)
                val targetY = if (isHigh) highY else lowY

                // Vertical transition edge
                path.lineTo(currentX, targetY)
                // Horizontal pulse duration line
                currentX += segmentWidth
                path.lineTo(currentX, targetY)

                // Alternate between mark (carrier ON) and space (carrier OFF)
                isHigh = !isHigh
              }

              // Draw waveform pulse line
              drawPath(
                path = path,
                color = Color(0xFF38BDF8),
                style = Stroke(
                  width = 2.5f,
                  cap = StrokeCap.Round,
                  join = StrokeJoin.Miter
                )
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Timing stats
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("HIGH: MARK (38kHz carrier)", color = Color(0xFF38BDF8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("LOW: SPACE", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text("RESOLUTION: 1 µs", color = Color(0xFF10B981), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
          }
        }
      }

      // 3. Technical Parameters Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "ကုဒ် အချက်အလက် (CODE METADATA)",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Protocol:", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text(selectedButton?.code?.protocol ?: "NEC / RAW", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Address Hex:", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text(selectedButton?.code?.address ?: "0x00FF", color = Color(0xFF38BDF8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Command Hex:", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text(selectedButton?.code?.command ?: "0x00", color = Color(0xFF38BDF8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Carrier Frequency:", color = Color(0xFF94A3B8), fontSize = 11.sp)
            Text("38,000 Hz (38 kHz)", color = Color(0xFF10B981), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
          }
        }
      }

      // 4. Copy as JSON & Copy as Hex Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = {
            val jsonString = """{"button":"${selectedButton?.key}","timings":${displayTimings},"protocol":"${selectedButton?.code?.protocol ?: "RAW"}"}"""
            copyToClipboard(context, "IR_Signal_JSON", jsonString)
            Toast.makeText(context, "JSON Data ကို Clipboard သို့ ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("copy_json_button")
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy as JSON", fontSize = 11.sp)
        }

        OutlinedButton(
          onClick = {
            val hexString = selectedButton?.code?.command ?: selectedButton?.code?.rawData ?: displayTimings.joinToString(separator = "") { it.toString(16).padStart(4, '0') }
            copyToClipboard(context, "IR_Signal_Hex", hexString)
            Toast.makeText(context, "Hex String ကို Clipboard သို့ ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("copy_hex_button")
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy as Hex", fontSize = 11.sp)
        }
      }
    }
  }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  val clip = ClipData.newPlainText(label, text)
  clipboard.setPrimaryClip(clip)
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
