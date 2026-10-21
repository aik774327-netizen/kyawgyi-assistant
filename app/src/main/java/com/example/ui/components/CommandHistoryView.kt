package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.example.data.CommandLogEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CommandHistoryView(
    logs: List<CommandLogEntity>,
    onDeleteLog: (id: Long) -> Unit,
    onClearAllLogs: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("command_history_view")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Command History Log",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "အသံအမိန့်များ လုပ်ဆောင်ခဲ့သည့် မှတ်တမ်း",
                    fontSize = 12.sp,
                    color = AngelCyanAccent
                )
            }

            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearAllLogs) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Logs",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("မှတ်တမ်းရှင်းမည်", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (logs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "အသံအမိန့် မှတ်တမ်း မရှိသေးပါ။",
                        color = AngelTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.id }) { log ->
                    val isSuccess = log.status.equals("SUCCESS", ignoreCase = true)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = log.commandText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AngelCyanAccent.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = log.commandType,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AngelCyanAccent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Failure Category Tag (အချက် ၂)
                                    if (!isSuccess && log.failureCategory.isNotBlank() && log.failureCategory != "NONE") {
                                        val (catLabel, catColor) = when (log.failureCategory) {
                                            "TECHNICAL" -> "နည်းပညာပိုင်း" to Color(0xFFFFB74D)
                                            "RESOURCE" -> "အရင်းအမြစ်" to Color(0xFF64B5F6)
                                            "PRAGMATIC_CONTEXTUAL" -> "လက်တွေ့မကိုက်ညီမှု" to Color(0xFFEF5350)
                                            else -> log.failureCategory to Color.Gray
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = catColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = catLabel,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = catColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (log.isRepeatedFailure) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD32F2F).copy(alpha = 0.25f),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF8A80),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (!isSuccess && log.failureReason.isNotBlank()) {
                                    Text(
                                        text = "အကြောင်းရင်း: ${log.failureReason}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFCC80),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                if (!isSuccess && log.suggestedAlternative.isNotBlank()) {
                                    Text(
                                        text = "အစားထိုးအကြံပြုချက်: ${log.suggestedAlternative}",
                                        fontSize = 11.sp,
                                        color = AngelNeonGreen,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }

                                // Interactive Remediation Button (အချက် ၃)
                                if (!isSuccess && log.remediationAction.isNotBlank()) {
                                    val context = LocalContext.current
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = if (log.remediationUri.isNotBlank()) {
                                                    Intent(log.remediationAction, Uri.parse(log.remediationUri))
                                                } else {
                                                    Intent(log.remediationAction)
                                                }
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("CommandHistoryView", "Failed to launch remediation", e)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent.copy(alpha = 0.25f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = AngelCyanAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "အစားထိုး လမ်းကြောင်း ဖွင့်ရန်",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AngelCyanAccent
                                        )
                                    }
                                }

                                Text(
                                    text = log.actionDetail,
                                    fontSize = 10.sp,
                                    color = AngelTextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            IconButton(onClick = { onDeleteLog(log.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Log",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
