package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessageEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationLogsView(
    messages: List<ChatMessageEntity>,
    onReplayAudio: (text: String) -> Unit,
    onDeleteMessage: (id: Long) -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm:ss - dd MMM", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("conversation_logs_view")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Conversation Logs & Timeline",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Room DB မှ ပြီးခဲ့သော စကားပြောဆိုချက် ၅၀ မှတ်တမ်း",
                    fontSize = 12.sp,
                    color = AngelCyanAccent
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AngelCyanAccent.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${messages.size} Messages",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AngelCyanAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (messages.isEmpty()) {
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
                        text = "စကားပြော မှတ်တမ်းများ မရှိသေးပါ။",
                        color = AngelTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Timeline Left Node Indicator
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        if (isUser) AngelCyanAccent else Color(0xFF00E676),
                                        shape = CircleShape
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(60.dp)
                                    .background(Color.DarkGray)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Interaction Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) AngelSurfaceDark else Color(0xFF1E283A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onReplayAudio(msg.content) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (isUser) AngelCyanAccent else Color(0xFF00E676),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isUser) "USER SPEECH" else "KYAW GYI RESPONSE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUser) AngelCyanAccent else Color(0xFF00E676)
                                        )
                                    }

                                    Text(
                                        text = dateFormat.format(Date(msg.timestamp)),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = msg.content,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onReplayAudio(msg.content) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Replay Audio",
                                            tint = AngelCyanAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "အသံပြန်နားထောင်မည် (TTS)",
                                            fontSize = 11.sp,
                                            color = AngelCyanAccent
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteMessage(msg.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Message",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
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
}
