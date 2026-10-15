package com.kyawgyi.ir.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyawgyi.ir.db.EventLog
import com.kyawgyi.ir.repository.IrViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventLogScreen(
  viewModel: IrViewModel = viewModel(),
  onNavigateBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsState()
  val roomLogs by viewModel.roomEventLogs.collectAsState()

  // Fall back to memory logs if roomLogs is empty initially
  val displayLogs = remember(roomLogs, uiState.logs) {
    if (roomLogs.isNotEmpty()) {
      roomLogs
    } else {
      uiState.logs.mapIndexed { idx, logText ->
        val isSuccess = !logText.contains("FAILED") && !logText.contains("ERROR")
        val eventType = when {
          logText.contains("SEND") -> if (isSuccess) "TRANSMIT_SUCCESS" else "TRANSMIT_FAILED"
          logText.contains("LEARN") -> if (isSuccess) "LEARN_SUCCESS" else "LEARN_FAILED"
          logText.contains("VOICE") -> "VOICE_COMMAND"
          else -> "SYSTEM_EVENT"
        }
        EventLog(
          id = idx.toLong(),
          eventType = eventType,
          message = logText,
          isSuccess = isSuccess
        )
      }
    }
  }

  var selectedFilter by remember { mutableStateOf("ALL") }

  val filteredLogs = remember(displayLogs, selectedFilter) {
    when (selectedFilter) {
      "TRANSMIT" -> displayLogs.filter { it.eventType.contains("TRANSMIT") || it.eventType.contains("SEND") }
      "LEARN" -> displayLogs.filter { it.eventType.contains("LEARN") }
      "VOICE" -> displayLogs.filter { it.eventType.contains("VOICE") }
      else -> displayLogs
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize().testTag("event_log_screen"),
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
              text = "အဖြစ်အပျက် မှတ်တမ်း (EVENT LOGS)",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )
            Text(
              text = "Transmission & Learning Telemetry (${displayLogs.size} records)",
              fontSize = 11.sp,
              color = Color(0xFF38BDF8)
            )
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.clearDbLogs() },
            modifier = Modifier.testTag("clear_logs_button")
          ) {
            Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = Color(0xFFEF4444))
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // 1. Metric Summary Card
      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("event_metrics_card")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOTAL", color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${displayLogs.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SUCCESS", color = Color(0xFF10B981), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${displayLogs.count { it.isSuccess }}", color = Color(0xFF10B981), fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("FAILED", color = Color(0xFFEF4444), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${displayLogs.count { !it.isSuccess }}", color = Color(0xFFEF4444), fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VOICE", color = Color(0xFFA855F7), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${displayLogs.count { it.eventType.contains("VOICE") }}", color = Color(0xFFA855F7), fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // 2. Filter Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        listOf("ALL" to "အားလုံး", "TRANSMIT" to "ထုတ်လွှင့်မှု", "LEARN" to "ဖမ်းယူမှု", "VOICE" to "အသံအမိန့်").forEach { (key, label) ->
          FilterChip(
            selected = selectedFilter == key,
            onClick = { selectedFilter = key },
            label = { Text(label, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFF0284C7),
              selectedLabelColor = Color.White,
              containerColor = Color(0xFF1E293B),
              labelColor = Color(0xFF94A3B8)
            ),
            modifier = Modifier.testTag("filter_$key")
          )
        }
      }

      // 3. Event List
      if (filteredLogs.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.History,
              contentDescription = null,
              tint = Color(0xFF475569),
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("မှတ်တမ်း မရှိသေးပါ (No events logged)", color = Color(0xFF64748B), fontSize = 13.sp)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(filteredLogs) { log ->
            EventLogItem(log)
          }
        }
      }
    }
  }
}

@Composable
fun EventLogItem(log: EventLog) {
  val (badgeColor, icon) = when {
    log.eventType.contains("VOICE") -> Color(0xFFA855F7) to Icons.Default.Mic
    log.eventType.contains("TRANSMIT") && log.isSuccess -> Color(0xFF10B981) to Icons.Default.Radio
    log.eventType.contains("TRANSMIT") && !log.isSuccess -> Color(0xFFEF4444) to Icons.Default.Error
    log.eventType.contains("LEARN") && log.isSuccess -> Color(0xFF38BDF8) to Icons.Default.Sensors
    log.eventType.contains("LEARN") && !log.isSuccess -> Color(0xFFEF4444) to Icons.Default.Error
    log.isSuccess -> Color(0xFF10B981) to Icons.Default.CheckCircle
    else -> Color(0xFFEF4444) to Icons.Default.Error
  }

  val formattedTime = remember(log.timestamp) {
    SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(log.timestamp))
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth().testTag("log_item_${log.id}")
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(badgeColor.copy(alpha = 0.15f))
          .border(1.dp, badgeColor.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.2f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = log.eventType,
                color = badgeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }

            if (log.buttonKey != null) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "[${log.buttonKey.uppercase()}]",
                color = Color(0xFFBAE6FD),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
            }
          }

          Text(
            text = formattedTime,
            color = Color(0xFF64748B),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = log.message,
          color = Color(0xFFE2E8F0),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )

        if (!log.details.isNullOrEmpty()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = log.details,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
          )
        }
      }
    }
  }
}
