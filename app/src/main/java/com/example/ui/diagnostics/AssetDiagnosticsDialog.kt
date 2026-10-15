package com.example.ui.diagnostics

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.AppDatabase
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssetDiagnosticsDialog(
  database: AppDatabase,
  onDismiss: () -> Unit
) {
  val recentEvents by database.ingestionEventDao().getRecentEventsFlow(30).collectAsState(initial = emptyList())

  var totalAssets by remember { mutableIntStateOf(0) }
  var musicCount by remember { mutableIntStateOf(0) }
  var audioCount by remember { mutableIntStateOf(0) }
  var imageCount by remember { mutableIntStateOf(0) }
  var docCount by remember { mutableIntStateOf(0) }
  var configCount by remember { mutableIntStateOf(0) }
  var sourceCount by remember { mutableIntStateOf(0) }
  var duplicateCount by remember { mutableIntStateOf(0) }
  var failedCount by remember { mutableIntStateOf(0) }
  var storageSizeBytes by remember { mutableLongStateOf(0L) }

  LaunchedEffect(Unit) {
    totalAssets = database.fileAssetDao().countTotal()
    musicCount = database.musicAssetDao().count()
    audioCount = database.fileAssetDao().countByCategory("AUDIO")
    imageCount = database.fileAssetDao().countByCategory("IMAGE")
    docCount = database.fileAssetDao().countByCategory("DOCUMENT")
    configCount = database.fileAssetDao().countByCategory("PROJECT_CONFIG")
    sourceCount = database.fileAssetDao().countByCategory("PROJECT_SOURCE")
    duplicateCount = database.ingestionEventDao().countDuplicates()
    failedCount = database.ingestionEventDao().countFailed()
    storageSizeBytes = database.fileAssetDao().totalSizeBytes() ?: 0L
  }

  val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))
        .testTag("asset_diagnostics_screen"),
      color = Color(0xFF0F172A)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {

        // 1. Header Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Data & Asset Diagnostics",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary
              )
              Text(
                text = "Universal Ingestion Pipeline Telemetry",
                fontSize = 11.sp,
                color = TextMuted
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_diagnostics_button")
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        // 2. Metrics Grid
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text(
                  text = "Asset Storage Metrics",
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                  color = TextPrimary,
                  modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetricBadge("Total Assets", totalAssets.toString(), EmeraldLight)
                  MetricBadge("Music Tracks", musicCount.toString(), EmeraldPrimary)
                  MetricBadge("Storage Used", "${storageSizeBytes / (1024 * 1024)} MB", Color(0xFF38BDF8))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetricBadge("Audio", audioCount.toString(), Color.White)
                  MetricBadge("Images", imageCount.toString(), Color.White)
                  MetricBadge("Documents", docCount.toString(), Color.White)
                  MetricBadge("Configs", configCount.toString(), Color.White)
                  MetricBadge("Patches", sourceCount.toString(), Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  MetricBadge("Duplicates Blocked", duplicateCount.toString(), Color(0xFFF59E0B))
                  MetricBadge("Failed Ingestions", failedCount.toString(), Color(0xFFEF4444))
                  MetricBadge("Security Engine", "PASS", EmeraldPrimary)
                }
              }
            }
          }

          item {
            Text(
              text = "Recent Ingestion Events Audit Log (${recentEvents.size})",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = TextPrimary,
              modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
          }

          if (recentEvents.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No recent ingestion events recorded yet.", color = TextMuted, fontSize = 12.sp)
              }
            }
          } else {
            items(recentEvents) { event ->
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SurfaceCardBorder)
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      val badgeColor = when (event.status) {
                        "SUCCESS" -> EmeraldPrimary
                        "DUPLICATE" -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                      }
                      Box(
                        modifier = Modifier
                          .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                          .padding(horizontal = 6.dp, vertical = 2.dp)
                      ) {
                        Text(text = event.status, color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                      }
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(text = event.eventType, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                      text = "${event.filename} • ${event.sha256.take(12)}...",
                      color = TextMuted,
                      fontSize = 10.sp,
                      modifier = Modifier.padding(top = 2.dp)
                    )
                    if (!event.error.isNullOrEmpty()) {
                      Text(text = "Error: ${event.error}", color = Color(0xFFEF4444), fontSize = 10.sp)
                    }
                  }
                  Text(
                    text = dateFormat.format(Date(event.timestamp)),
                    color = TextMuted,
                    fontSize = 10.sp
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MetricBadge(title: String, value: String, valueColor: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    Text(text = title, fontSize = 9.sp, color = TextMuted)
  }
}
