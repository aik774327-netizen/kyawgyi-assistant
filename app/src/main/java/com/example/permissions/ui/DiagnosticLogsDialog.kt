package com.example.permissions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.permissions.DiagnosticLogEntry

@Composable
fun DiagnosticLogsDialog(
  logs: List<DiagnosticLogEntry>,
  onDismiss: () -> Unit,
  onClear: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .height(550.dp)
        .testTag("diagnostic_logs_dialog"),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
      border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = "Terminal",
              tint = Color(0xFF38BDF8),
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Diagnostic Event Log",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "စနစ်လုပ်ဆောင်ချက် မှတ်တမ်း (${logs.size} entries)",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_diagnostic_logs")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = Color(0xFF94A3B8)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logs list
        if (logs.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No diagnostic events recorded yet.",
              color = Color(0xFF64748B),
              fontSize = 14.sp
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(logs) { entry ->
              LogEntryItem(entry = entry)
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Footer Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          OutlinedButton(
            onClick = onClear,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            modifier = Modifier.testTag("clear_diagnostic_logs")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Clear",
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear Logs", fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@Composable
private fun LogEntryItem(entry: DiagnosticLogEntry) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
      .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
      .padding(10.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "[${entry.formatTime()}] ${entry.operation}",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = Color(0xFF38BDF8)
        )
        Text(
          text = entry.setting,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = Color(0xFFFBBF24)
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "${entry.previousState} ➔ ${entry.newState}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = Color(0xFFE2E8F0)
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "Result: ${entry.result}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = if (entry.error == null) Color(0xFF34D399) else Color(0xFFF87171)
      )
      if (entry.error != null) {
        Text(
          text = "Error: ${entry.error}",
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          color = Color(0xFFF87171)
        )
      }
    }
  }
}
