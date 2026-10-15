package com.example.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.database.ChatMessageEntity
import com.example.ingestion.ChangeRisk
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.AiAngelChatViewModel

@Composable
fun AiAngelChatDialog(
  viewModel: AiAngelChatViewModel,
  onDismiss: () -> Unit,
  onOpenMusicLibrary: () -> Unit,
  onOpenDiagnostics: () -> Unit
) {
  val messages by viewModel.messages.collectAsState()
  val currentStep by viewModel.currentIngestionStep.collectAsState()
  val pendingChange by viewModel.pendingProposedChange.collectAsState()
  val isPlaying by viewModel.musicPlaybackManager.isPlaying.collectAsState()
  val currentPlayingSong by viewModel.musicPlaybackManager.currentSong.collectAsState()

  var inputText by remember { mutableStateOf("") }
  var showAttachmentMenu by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()

  // SAF File Pickers for different types without needing dangerous permissions
  val musicPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val name = it.lastPathSegment?.substringAfterLast('/') ?: "audio_${System.currentTimeMillis()}.mp3"
      viewModel.handleFileUpload(it, name, "audio/mpeg")
    }
  }

  val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val name = it.lastPathSegment?.substringAfterLast('/') ?: "image_${System.currentTimeMillis()}.jpg"
      viewModel.handleFileUpload(it, name, "image/jpeg")
    }
  }

  val docPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val name = it.lastPathSegment?.substringAfterLast('/') ?: "document_${System.currentTimeMillis()}.pdf"
      viewModel.handleFileUpload(it, name, "application/pdf")
    }
  }

  val anyFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
    uri?.let {
      val name = it.lastPathSegment?.substringAfterLast('/') ?: "project_file_${System.currentTimeMillis()}"
      viewModel.handleFileUpload(it, name, "application/octet-stream")
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))
        .testTag("ai_angel_chat_screen"),
      color = Color(0xFF0F172A)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {

        // 1. Top App Bar with Angel Avatar, Controls & Close
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
                .size(42.dp)
                .clip(CircleShape)
                .background(
                  Brush.linearGradient(
                    listOf(Color(0xFF06B6D4), EmeraldPrimary)
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Angel",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "AI Angel Chatbox",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
              )
              Text(
                text = "Universal Data Ingestion • Offline-First",
                fontSize = 11.sp,
                color = Color(0xFF10B981)
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onOpenMusicLibrary,
              modifier = Modifier.testTag("open_music_library_button")
            ) {
              Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = "Music Library",
                tint = EmeraldLight
              )
            }

            IconButton(
              onClick = onOpenDiagnostics,
              modifier = Modifier.testTag("open_diagnostics_button")
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Diagnostics",
                tint = Color(0xFF38BDF8)
              )
            }

            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_chat_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = TextMuted
              )
            }
          }
        }

        // 2. Ingestion Progress Banner
        AnimatedVisibility(visible = currentStep != null) {
          currentStep?.let { step ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D9488).copy(alpha = 0.2f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "⚡ Ingestion Pipeline: ${step.burmeseLabel}",
                  color = EmeraldLight,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = step.englishLabel,
                  color = TextMuted,
                  fontSize = 11.sp
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = EmeraldPrimary,
                trackColor = Color(0xFF1E293B)
              )
            }
          }
        }

        // 3. Pending Change Approval Banner (Security confirmation)
        AnimatedVisibility(visible = pendingChange != null) {
          pendingChange?.let { change ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(change.risk.badgeColorHex))
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "⚠️ စနစ်ပြောင်းလဲမှု အတည်ပြုရန် လိုအပ်သည်",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                  Box(
                    modifier = Modifier
                      .background(Color(change.risk.badgeColorHex).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                      .padding(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = change.risk.labelBurmese,
                      color = Color(change.risk.badgeColorHex),
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = change.summaryBurmese,
                  color = TextMuted,
                  fontSize = 12.sp
                )
                Text(
                  text = "Target: ${change.destinationPath}",
                  color = Color(0xFF38BDF8),
                  fontSize = 11.sp,
                  modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End
                ) {
                  OutlinedButton(
                    onClick = { viewModel.dismissPendingChange() },
                    modifier = Modifier.padding(end = 8.dp)
                  ) {
                    Text("ပယ်ဖျက်မည်", color = TextMuted, fontSize = 12.sp)
                  }
                  Button(
                    onClick = { viewModel.approvePendingChange() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                  ) {
                    Text("အတည်ပြုမည် (Approve)", color = Color.White, fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }

        // 4. Messages Area
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(messages) { message ->
            ChatMessageBubble(
              message = message,
              isPlaying = isPlaying && currentPlayingSong?.fileAssetId == message.attachedFileAssetId,
              onPlayClick = {
                message.attachedFileAssetId?.let { id ->
                  // Find song and play
                }
              }
            )
          }
        }

        // 5. Attachment Menu Sheet (when toggled)
        AnimatedVisibility(visible = showAttachmentMenu) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "ဖိုင်အမျိုးအစား ရွေးချယ်ပါ (Select Ingestion Type)",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                AttachmentMenuItem(
                  icon = Icons.Default.Audiotrack,
                  label = "Music MP3",
                  color = EmeraldPrimary,
                  onClick = {
                    showAttachmentMenu = false
                    musicPickerLauncher.launch("audio/*")
                  }
                )
                AttachmentMenuItem(
                  icon = Icons.Default.Image,
                  label = "Images",
                  color = Color(0xFF38BDF8),
                  onClick = {
                    showAttachmentMenu = false
                    imagePickerLauncher.launch("image/*")
                  }
                )
                AttachmentMenuItem(
                  icon = Icons.Default.Description,
                  label = "Document",
                  color = Color(0xFFF59E0B),
                  onClick = {
                    showAttachmentMenu = false
                    docPickerLauncher.launch("*/*")
                  }
                )
                AttachmentMenuItem(
                  icon = Icons.Default.Code,
                  label = "JSON/Code",
                  color = Color(0xFFA855F7),
                  onClick = {
                    showAttachmentMenu = false
                    anyFilePickerLauncher.launch("*/*")
                  }
                )
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Quick Ingest Sample Button (for immediate testing of Lay Phyu MP3 or config JSON)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                OutlinedButton(
                  onClick = {
                    showAttachmentMenu = false
                    // Quick sample Lay Phyu MP3 bytes
                    val sampleBytes = "ID3-MOCK-MP3-HEADER-LAY-PHYU-ROCK-SONG".toByteArray()
                    viewModel.ingestSampleFile("Lay Phyu - Song Name.mp3", sampleBytes, "audio/mpeg")
                  },
                  modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                  Text("🎵 Sample MP3", fontSize = 11.sp, color = EmeraldLight)
                }

                OutlinedButton(
                  onClick = {
                    showAttachmentMenu = false
                    val jsonBytes = "{\"command\":\"fan_power\",\"target\":\"kennede\"}".toByteArray()
                    viewModel.ingestSampleFile("fan_config.json", jsonBytes, "application/json")
                  },
                  modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                  Text("📄 Sample JSON", fontSize = 11.sp, color = Color(0xFF38BDF8))
                }
              }
            }
          }
        }

        // 6. Bottom Input Area
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { showAttachmentMenu = !showAttachmentMenu },
            modifier = Modifier.testTag("attachment_menu_button")
          ) {
            Icon(
              imageVector = Icons.Default.AttachFile,
              contentDescription = "Attach File",
              tint = if (showAttachmentMenu) EmeraldPrimary else TextMuted
            )
          }

          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("AI Angel သို့ မေးမြန်းပါ သို့မဟုတ် ရေးသားပါ...", color = TextMuted, fontSize = 13.sp) },
            modifier = Modifier
              .weight(1f)
              .testTag("chat_input_textfield"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = TextPrimary,
              unfocusedTextColor = TextPrimary,
              focusedBorderColor = EmeraldPrimary,
              unfocusedBorderColor = SurfaceCardBorder,
              focusedContainerColor = Color(0xFF0F172A),
              unfocusedContainerColor = Color(0xFF0F172A)
            ),
            shape = RoundedCornerShape(24.dp),
            maxLines = 3
          )

          Spacer(modifier = Modifier.width(6.dp))

          if (inputText.isBlank()) {
            IconButton(
              onClick = {
                viewModel.sendTextMessage("လေးဖြူသီချင်းဖွင့်ပါ")
              },
              modifier = Modifier.testTag("quick_voice_mic_button")
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Input",
                tint = EmeraldPrimary
              )
            }
          } else {
            IconButton(
              onClick = {
                viewModel.sendTextMessage(inputText)
                inputText = ""
              },
              modifier = Modifier.testTag("send_message_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = EmeraldPrimary
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun AttachmentMenuItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  color: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .background(color.copy(alpha = 0.2f), CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = label, color = TextPrimary, fontSize = 11.sp)
  }
}

@Composable
fun ChatMessageBubble(
  message: ChatMessageEntity,
  isPlaying: Boolean,
  onPlayClick: () -> Unit
) {
  val isUser = message.sender == "USER"
  val bubbleBg = if (isUser) EmeraldPrimary.copy(alpha = 0.85f) else Color(0xFF1E293B)
  val align = if (isUser) Alignment.End else Alignment.Start

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = align
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = bubbleBg),
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 2.dp,
        bottomEnd = if (isUser) 2.dp else 16.dp
      ),
      border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder) else null
    ) {
      Column(modifier = Modifier.padding(12.dp)) {
        if (!isUser) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = Color(0xFF06B6D4),
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "AI Angel",
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = Color(0xFF06B6D4)
            )
          }
        }

        // File Card Preview if attachment exists
        if (!message.attachmentName.isNullOrEmpty()) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val icon = when {
                message.attachmentMimeType?.startsWith("audio") == true -> Icons.Default.Audiotrack
                message.attachmentMimeType?.startsWith("image") == true -> Icons.Default.Image
                else -> Icons.Default.Description
              }
              Icon(imageVector = icon, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(24.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = message.attachmentName,
                  color = TextPrimary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = "${message.attachmentMimeType} • ${message.ingestionStatus ?: "Ingested"}",
                  color = TextMuted,
                  fontSize = 10.sp
                )
              }
            }
          }
        }

        Text(
          text = message.messageText,
          color = Color.White,
          fontSize = 13.sp,
          lineHeight = 18.sp
        )
      }
    }
  }
}
