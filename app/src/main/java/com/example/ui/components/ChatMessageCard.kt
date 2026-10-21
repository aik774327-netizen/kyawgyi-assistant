package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessageEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatMessageCard(
    message: ChatMessageEntity,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenApiKeySettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val isQuotaError = !isUser && message.content.contains("Gemini API Quota ပြည့်သွားပါသဖြင့်")
    var isThoughtExpanded by remember { mutableStateOf(false) }

    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("chat_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isQuotaError) Color(0xFFEF5350).copy(alpha = 0.2f) else AngelNeonGreen.copy(alpha = 0.2f))
                        .border(1.dp, if (isQuotaError) Color(0xFFEF5350) else AngelNeonGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isQuotaError) Icons.Filled.WarningAmber else Icons.Filled.AutoAwesome,
                        contentDescription = if (isQuotaError) "Quota Alert" else "AI Angel",
                        tint = if (isQuotaError) Color(0xFFEF5350) else AngelNeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = when {
                    isUser -> AngelNeonGreen.copy(alpha = 0.15f)
                    isQuotaError -> Color(0xFF2D1616)
                    else -> AngelCardDark
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isUser -> AngelNeonGreen.copy(alpha = 0.4f)
                        isQuotaError -> Color(0xFFEF5350).copy(alpha = 0.6f)
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                ),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth()
                ) {
                    // Header Tag for AI Angel Message
                    if (!isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isQuotaError) "API QUOTA ALERT" else "AI ANGEL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isQuotaError) Color(0xFFEF5350) else AngelNeonGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isQuotaError) Color(0xFFEF5350).copy(alpha = 0.2f) else AngelCyanAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isQuotaError) "429 EXHAUSTED" else message.responseMode,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isQuotaError) Color(0xFFEF5350) else AngelCyanAccent,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = formattedTime,
                                fontSize = 10.sp,
                                color = AngelTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Attached File / Media Badge
                    if (!message.imageUri.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AngelCyanAccent.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AttachFile,
                                    contentDescription = "Attachment",
                                    tint = AngelCyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "တွဲထားသောဖိုင်: ${message.imageUri}",
                                    fontSize = 11.sp,
                                    color = AngelCyanAccent,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Thought Chain Collapsible Panel (Brain Core)
                    if (!message.thoughtChain.isNullOrBlank()) {
                        Surface(
                            onClick = { isThoughtExpanded = !isThoughtExpanded },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = ModeExpertColor.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ModeExpertColor.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Psychology,
                                            contentDescription = "Thought Chain",
                                            tint = ModeExpertColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Brain Core Reasoning Steps",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ModeExpertColor
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isThoughtExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Toggle thought chain",
                                        tint = ModeExpertColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isThoughtExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Text(
                                        text = message.thoughtChain,
                                        fontSize = 11.sp,
                                        color = AngelTextSecondary,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Text Content
                    val formattedText = formatMarkdownText(message.content)
                    Text(
                        text = formattedText,
                        fontSize = 14.sp,
                        color = if (isQuotaError) Color(0xFFFFCDD2) else AngelTextPrimary,
                        fontWeight = if (isQuotaError) FontWeight.SemiBold else FontWeight.Normal,
                        lineHeight = 20.sp
                    )

                    // 429 Quota Exhausted Callout & Quick Settings Access
                    if (isQuotaError) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF5350).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Quota Error",
                                        tint = Color(0xFFEF5350),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Gemini API Quota ပြည့်သွားပါပြီ (HTTP 429)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF5350)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "ခေတ္တစောင့်ဆိုင်းပါ သို့မဟုတ် မိမိ၏ ကိုယ်ပိုင် Gemini API Key ထည့်သွင်း၍ ဆက်လက်အသုံးပြုနိုင်ပါသည်။",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFCDD2),
                                    lineHeight = 16.sp
                                )
                                if (onOpenApiKeySettings != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onOpenApiKeySettings,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(
                                            text = "API Key စီမံခန့်ခွဲရန် ဖွင့်ပါ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Message Action Bar (Copy & TTS Speaker)
                    if (!isUser) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSpeak(message.content) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("speak_btn_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = AngelNeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AI Angel Message", message.content))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("copy_btn_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Text",
                                    tint = AngelTextSecondary,
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

// Simple Helper to parse bold markdown **text**
@Composable
fun formatMarkdownText(text: String) = buildAnnotatedString {
    val parts = text.split("**")
    var isBold = false
    for (part in parts) {
        if (isBold) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = AngelNeonGreen)) {
                append(part)
            }
        } else {
            append(part)
        }
        isBold = !isBold
    }
}
