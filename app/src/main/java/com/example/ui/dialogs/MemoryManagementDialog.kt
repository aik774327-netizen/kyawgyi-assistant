package com.example.ui.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.database.ErrorCategory
import com.example.database.ErrorLogEntity
import com.example.models.MemoryEntry
import com.example.models.NodeStatus
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MemoryManagementDialog(
  memoryEntries: List<MemoryEntry>,
  errorLogs: List<ErrorLogEntity> = emptyList(),
  nodes: List<NodeStatus>,
  selfHealingLogs: List<String>,
  onDismiss: () -> Unit,
  onAddMemory: (String, String) -> Unit,
  onClearMemory: () -> Unit,
  onClearErrorLogs: () -> Unit = {},
  onSimulateError: (ErrorCategory) -> Unit = {},
  onTriggerSelfHealing: () -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var newAlias by remember { mutableStateOf("") }
  var newTarget by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.2.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .padding(18.dp)
        .testTag("memory_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x3310B981)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Psychology, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(text = "Memory & Learning Engine", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
              Text(text = "အမှားမှသင်ယူသည့်စနစ် & ကျန်းမာရေး", fontSize = 11.sp, color = EmeraldLight)
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3-Tab Selector
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(3.dp),
          horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
          listOf("မှတ်ဉာဏ်", "အမှားမှသင်ယူမှု (${errorLogs.size})", "Node ကျန်းမာရေး").forEachIndexed { index, title ->
            val isSel = selectedTab == index
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSel) EmeraldPrimary else Color.Transparent)
                .clickable { selectedTab = index }
                .padding(vertical = 7.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSel) Color.Black else TextMuted
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab 0: Memories & Learned Aliases
        if (selectedTab == 0) {
          Text(text = "Learned Burmese Aliases & Preferences", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Spacer(modifier = Modifier.height(8.dp))

          memoryEntries.forEach { entry ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = entry.aliasOrKey, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                Text(text = "-> ${entry.targetValue}", fontSize = 11.sp, color = TextSecondary)
              }
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(0x333B82F6))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(text = entry.category, fontSize = 9.sp, color = IndigoAccent)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(text = "အသစ်ထည့်သွင်းရန် (Add Alias)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = newAlias,
            onValueChange = { newAlias = it },
            label = { Text("အမည် / စကားလုံး (e.g. မေမေ)", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              focusedBorderColor = EmeraldPrimary,
              unfocusedBorderColor = SurfaceCardBorder
            )
          )

          Spacer(modifier = Modifier.height(6.dp))

          OutlinedTextField(
            value = newTarget,
            onValueChange = { newTarget = it },
            label = { Text("ရည်ညွှန်းချက် / ဖုန်းနံပါတ်", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              focusedBorderColor = EmeraldPrimary,
              unfocusedBorderColor = SurfaceCardBorder
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                if (newAlias.isNotBlank() && newTarget.isNotBlank()) {
                  onAddMemory(newAlias, newTarget)
                  newAlias = ""
                  newTarget = ""
                }
              },
              modifier = Modifier.weight(1f),
              colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("သိမ်းဆည်းမည်", color = Color.Black, fontSize = 12.sp)
            }

            Button(
              onClick = onClearMemory,
              colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
              modifier = Modifier.border(1.dp, RoseError.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, tint = RoseError, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("ရှင်းလင်းမည်", color = RoseError, fontSize = 12.sp)
            }
          }
        }

        // Tab 1: Error Logs & Learning Engine (User Rules 1 - 5)
        if (selectedTab == 1) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "ကျရှုံးမှု မှတ်တမ်းနှင့် အမှားမှသင်ယူမှု",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
              Text(
                text = "စည်းမျဉ်း ၅ ရပ်ဖြင့် တိကျစွာ ခွဲခြမ်းစိတ်ဖြာထားသည်",
                fontSize = 10.sp,
                color = EmeraldLight
              )
            }

            if (errorLogs.isNotEmpty()) {
              IconButton(onClick = onClearErrorLogs, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = RoseError)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          if (errorLogs.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "လက်ရှိတွင် မအောင်မြင်သော အမှားမှတ်တမ်း မရှိပါ", fontSize = 12.sp, color = TextPrimary)
                Text(text = "စနစ်အားလုံး အောင်မြင်စွာ လည်ပတ်နေပါသည်", fontSize = 10.sp, color = TextMuted)
              }
            }
          } else {
            errorLogs.forEach { log ->
              val (badgeColor, categoryLabel) = when (log.category) {
                "TECHNICAL" -> RoseError to "နည်းပညာပိုင်း"
                "RESOURCE" -> AmberAccent to "အရင်းအမြစ်"
                "MOCK_DETECTED" -> Color(0xFFA855F7) to "အတုအယောင်/ဟန်ပြ"
                else -> Color(0xFF3B82F6) to "လက်တွေ့မကိုက်ညီမှု"
              }

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Color(0xFF131E2C))
                  .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                  .padding(12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "\"${log.rawCommand}\"",
                      fontSize = 13.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary
                    )
                  }

                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(6.dp))
                      .background(badgeColor.copy(alpha = 0.2f))
                      .border(0.8.dp, badgeColor, RoundedCornerShape(6.dp))
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(text = categoryLabel, fontSize = 9.5.sp, color = badgeColor, fontWeight = FontWeight.Bold)
                  }
                }

                Spacer(modifier = Modifier.height(6.dp))
                if (log.categoryExplanation.isNotBlank()) {
                  Text(
                    text = "• အမျိုးအစား သဘောသဘာဝ: ${log.categoryExplanation}",
                    fontSize = 10.5.sp,
                    color = TextMuted,
                    lineHeight = 15.sp
                  )
                  Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                  text = "• အကြောင်းရင်း: ${log.reasonDetail}",
                  fontSize = 11.5.sp,
                  color = Color(0xFFFECDD3),
                  lineHeight = 16.sp
                )

                if (log.failedMethod.isNotBlank()) {
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = "• မအောင်မြင်သောနည်းလမ်း (ပယ်ဖျက်ပြီး): ${log.failedMethod}",
                    fontSize = 11.sp,
                    color = RoseError.copy(alpha = 0.9f),
                    lineHeight = 15.sp
                  )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "• အကြံပြုအစားထိုးမှု: ${log.alternativeAdvice}",
                  fontSize = 11.sp,
                  color = EmeraldLight,
                  lineHeight = 15.sp
                )

                if (log.retryCount > 1) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "ထပ်မံကြိုးစားမှု အကြိမ်ရေ: ${log.retryCount} ကြိမ် (သတိပေးထားပြီးဖြစ်သည်)",
                    fontSize = 10.sp,
                    color = AmberAccent
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(text = "အမှားအမျိုးအစား ၄ မျိုး စမ်းသပ်စစ်ဆေးရန် (Simulate & Verify):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
          Spacer(modifier = Modifier.height(6.dp))

          // 4 Test Buttons for user verification of the 4 failure categories
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Button(
                onClick = { onSimulateError(ErrorCategory.TECHNICAL) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33EF4444)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("🔴 နည်းပညာပိုင်း", fontSize = 10.sp, color = RoseError)
              }
              Button(
                onClick = { onSimulateError(ErrorCategory.RESOURCE) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33F59E0B)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("🟠 အရင်းအမြစ်", fontSize = 10.sp, color = AmberAccent)
              }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Button(
                onClick = { onSimulateError(ErrorCategory.MOCK_DETECTED) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33A855F7)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("🟣 အတုအယောင်/ဟန်ပြ", fontSize = 10.sp, color = Color(0xFFA855F7))
              }
              Button(
                onClick = { onSimulateError(ErrorCategory.REAL_WORLD_MISMATCH) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x333B82F6)),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("🔵 လက်တွေ့မကိုက်ညီ", fontSize = 10.sp, color = Color(0xFF60A5FA))
              }
            }
          }
        }

        // Tab 2: Node Health & Self-Healing
        if (selectedTab == 2) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = "Multi-Node Health Mesh", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            IconButton(onClick = onTriggerSelfHealing, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Default.Refresh, contentDescription = "Self Healing", tint = EmeraldLight)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          nodes.forEach { node ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (node.status == "ACTIVE") EmeraldPrimary else Color(0xFF3B82F6))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = node.nodeId, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (node.isPrimary) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .background(Color(0x3310B981))
                          .padding(horizontal = 4.dp, vertical = 1.dp)
                      ) {
                        Text(text = "PRIMARY", fontSize = 8.sp, color = EmeraldLight)
                      }
                    }
                  }
                  Text(text = node.role, fontSize = 11.sp, color = TextMuted)
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(text = "${node.latencyMs}ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
                Text(text = "${node.successRate}% OK", fontSize = 9.sp, color = TextSecondary)
              }
            }
          }

          if (selfHealingLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Self-Healing Event Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Spacer(modifier = Modifier.height(6.dp))
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF070E17))
                .padding(10.dp)
            ) {
              selfHealingLogs.forEach { log ->
                Text(text = "> $log", fontSize = 10.sp, color = Color(0xFFA7F3D0), lineHeight = 14.sp)
              }
            }
          }
        }
      }
    }
  }
}
