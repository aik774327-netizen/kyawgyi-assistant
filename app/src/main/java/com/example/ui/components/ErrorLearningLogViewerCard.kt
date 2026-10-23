package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audit.ErrorLearningWorkflowEngine
import com.example.audit.VerificationOutcome
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ErrorLearningLogViewerCard
 *
 * Interactive UI component for the AI Error-Learning & Failure Memory Framework:
 * - Displays recorded Error Logs (ER-001, ER-002, ...) with full 3-Factor Error Analysis:
 *   1. Technical Cause (နည်းပညာပိုင်း အကြောင်းရင်း)
 *   2. Resource / Source Limitation (အရင်းအမြစ် ကန့်သတ်ချက်)
 *   3. Real-World / Practical Mismatch (လက်တွေ့ ပတ်ဝန်းကျင် ကွာဟချက်)
 * - Displays active Failed-Method Memory blocks with the mandatory warning:
 *   "This is a previously unsuccessful action."
 * - Provides a one-tap Live Condition Verifier to re-verify real OS permissions,
 *   network connectivity, and local speech models without fabrication.
 */
@Composable
fun ErrorLearningLogViewerCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ErrorLearningWorkflowEngine.initialize(context)
    }

    val errorLogs by ErrorLearningWorkflowEngine.errorLogsFlow.collectAsState()
    val blockedMethods by ErrorLearningWorkflowEngine.blockedMethodsFlow.collectAsState()

    var selectedLogForDetail by remember { mutableStateOf<ErrorLearningWorkflowEngine.ErrorLogEntry?>(null) }
    var showFullLogDialog by remember { mutableStateOf(false) }
    var liveVerifyStatusBanner by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (blockedMethods.isNotEmpty()) Color(0xFFF59E0B).copy(alpha = 0.6f)
                    else AngelCyanAccent.copy(alpha = 0.35f)
                ),
                RoundedCornerShape(16.dp)
            )
            .testTag("error_learning_log_viewer_card"),
        color = AngelCardDark
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (blockedMethods.isNotEmpty()) Color(0xFFF59E0B).copy(alpha = 0.15f)
                        else AngelNeonGreen.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (blockedMethods.isNotEmpty()) Icons.Default.Warning else Icons.Default.VerifiedUser,
                                contentDescription = "Error-Learning AI Status",
                                tint = if (blockedMethods.isNotEmpty()) Color(0xFFF59E0B) else AngelNeonGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Error-Learning & 3-Factor Log Viewer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ER-001 မှတ်တမ်း • 3-Factor Analysis • Live Condition Verifier",
                            fontSize = 11.sp,
                            color = AngelCyanAccent
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (errorLogs.isEmpty()) AngelNeonGreen.copy(alpha = 0.15f)
                    else Color(0xFFF59E0B).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Logs: ${errorLogs.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (errorLogs.isEmpty()) AngelNeonGreen else Color(0xFFF59E0B),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("error_logs_count_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelSurfaceDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "မှတ်တမ်းတင်ထားသော အမှားများ",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "${errorLogs.size} ခု",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AngelSurfaceDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ပိတ်ပင်ထားသော နည်းလမ်း (Blocked)",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                        Text(
                            text = "${blockedMethods.size} ခု",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (blockedMethods.isEmpty()) AngelNeonGreen else Color(0xFFEF4444),
                            modifier = Modifier.testTag("blocked_methods_count_text")
                        )
                    }
                }
            }

            // Live Condition Verification Banner feedback
            AnimatedVisibility(visible = liveVerifyStatusBanner != null) {
                liveVerifyStatusBanner?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AngelCyanAccent.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("live_verify_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { liveVerifyStatusBanner = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Blocked Methods Warning Section
            if (blockedMethods.isNotEmpty()) {
                Text(
                    text = "Failed-Method Memory (ထပ်မံမသုံးရန် တားမြစ်ထားသော နည်းလမ်းများ):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // ISSUE-046: render EVERY blocked method. This list was
                // silently truncated with .take(2), so with 3+ blocked
                // methods the user could not even see the rest were blocked.
                blockedMethods.entries.forEach { (cmdKey, logEntry) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("blocked_method_item_$cmdKey")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${logEntry.errorId} • Command: \"$cmdKey\"",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFEF4444)
                                ) {
                                    Text(
                                        text = "BLOCKED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ErrorLearningWorkflowEngine.MANDATORY_WARNING_PREFIX,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCD34D)
                            )
                            Text(
                                text = logEntry.simpleExplanationMm,
                                fontSize = 11.sp,
                                color = Color.White,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        // ISSUE-034: run the typed live verifier (off the main
                                        // thread — the TTS probe blocks with bounded waits) and
                                        // show its actual outcome. A record clears only on
                                        // VerificationOutcome.VERIFIED.
                                        scope.launch(Dispatchers.IO) {
                                            val result = ErrorLearningWorkflowEngine.verifyBlockedMethod(
                                                commandKey = cmdKey,
                                                context = context
                                            )
                                            liveVerifyStatusBanner = when (result.outcome) {
                                                VerificationOutcome.VERIFIED ->
                                                    "✓ Live Condition Verifier: '$cmdKey' အတွက် ပတ်ဝန်းကျင်အခြေအနေ ပြန်လည်ကောင်းမွန်ကြောင်း အတည်ပြုပြီး ပိတ်ပင်မှု ဖြေလျှော့ပေးလိုက်ပါပြီ။ (${result.evidenceSource})"
                                                else ->
                                                    "⚠ Live Condition Verifier: '$cmdKey' — ${result.outcome.name}: ${result.detail}"
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("verify_live_condition_btn_$cmdKey"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelNeonGreen),
                                    border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.6f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Live Condition စစ်ဆေးမည်", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { selectedLogForDetail = logEntry },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("inspect_error_log_btn_${logEntry.errorId}"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("3-Factor ကြည့်ရန်", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recent Error Log Preview (Latest 2 entries)
            if (errorLogs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AngelSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_error_logs_state")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = AngelNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "လက်ရှိတွင် မအောင်မြင်သော Error Log (ER-xxx) မရှိသေးပါ။ ချို့ယွင်းချက် ဖြစ်ပေါ်ပါက 3-Factor Analysis ဖြင့် အလိုအလျောက် မှတ်တမ်းတင်ပေးပါမည်။",
                            fontSize = 11.sp,
                            color = AngelTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "နောက်ဆုံး မှတ်တမ်းတင်ထားသော Error Logs:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                errorLogs.take(2).forEach { entry ->
                    ErrorLogCompactItem(
                        entry = entry,
                        onClick = { selectedLogForDetail = entry }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showFullLogDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("open_full_error_log_viewer_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AngelCyanAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ER Logs အားလုံး & 3-Factor စစ်ဆေးရန်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        // ISSUE-034: bulk action = run the typed verifier PER ITEM and
                        // report per-item outcomes. Items clear only on VERIFIED.
                        scope.launch {
                            val currentBlocked = ErrorLearningWorkflowEngine.blockedMethodsFlow.value.keys.toList()
                            if (currentBlocked.isEmpty()) {
                                liveVerifyStatusBanner = "✓ ပိတ်ပင်ထားသော နည်းလမ်း မရှိပါ။ စနစ်အခြေအနေ ကောင်းမွန်ပါသည်။"
                            } else {
                                val results = withContext(Dispatchers.IO) {
                                    currentBlocked.map { key ->
                                        key to ErrorLearningWorkflowEngine.verifyBlockedMethod(
                                            commandKey = key,
                                            context = context
                                        )
                                    }
                                }
                                val clearedCount = results.count { it.second.outcome == VerificationOutcome.VERIFIED }
                                liveVerifyStatusBanner = buildString {
                                    append("Live Condition Verifier — တစ်ခုချင်း ရလဒ် (ဖြေလျှော့ပြီး $clearedCount/${results.size}):")
                                    results.forEach { (key, result) ->
                                        append("\n• $key: ${result.outcome.name} (${result.evidenceSource})")
                                    }
                                }
                            }
                            Toast.makeText(context, liveVerifyStatusBanner, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("run_all_live_condition_verifier_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelNeonGreen),
                    border = BorderStroke(1.dp, AngelNeonGreen.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Verify All",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live Verify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Detailed 3-Factor Inspector Modal for a selected ErrorLogEntry
    selectedLogForDetail?.let { entry ->
        ErrorLogThreeFactorDetailDialog(
            entry = entry,
            onDismiss = { selectedLogForDetail = null }
        )
    }

    // Full Error-Learning Log Viewer Dialog
    if (showFullLogDialog) {
        AlertDialog(
            onDismissRequest = { showFullLogDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AI Error-Learning Log Viewer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mandatory 3-Factor Analysis & Failure Memory",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("full_error_log_dialog_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorLogs.isEmpty()) {
                        Text(
                            text = "မှတ်တမ်းတင်ထားသော Error Log မရှိသေးပါ။",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        errorLogs.forEach { entry ->
                            ErrorLogDetailedCardItem(
                                entry = entry,
                                isCurrentlyBlocked = blockedMethods.containsKey(entry.commandKey),
                                onVerifyCondition = {
                                    // ISSUE-034: typed verifier only; outcome shown verbatim.
                                    scope.launch(Dispatchers.IO) {
                                        val result = ErrorLearningWorkflowEngine.verifyBlockedMethod(
                                            commandKey = entry.commandKey,
                                            context = context
                                        )
                                        val msg = when (result.outcome) {
                                            VerificationOutcome.VERIFIED ->
                                                "Verified: '${entry.commandKey}' condition resolved and unblocked."
                                            else ->
                                                "Still blocked (${result.outcome.name}): ${result.detail}"
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            // ISSUE-034: the former "Resolve All" button was REMOVED. It cleared
            // every blocked record with a hard-coded evidence string and no
            // verification at all. Records now clear only through a typed
            // VerificationResult (live verifier per item, or a verified
            // successful execution) — see the per-item verify actions and the
            // "Live Verify" bulk verifier on the card.
            confirmButton = {
                TextButton(
                    onClick = { showFullLogDialog = false },
                    modifier = Modifier.testTag("close_full_error_log_dialog_btn")
                ) {
                    Text("ပိတ်မည်")
                }
            }
        )
    }
}

@Composable
private fun ErrorLogCompactItem(
    entry: ErrorLearningWorkflowEngine.ErrorLogEntry,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AngelSurfaceDark,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() }
            .testTag("error_log_compact_item_${entry.errorId}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = entry.errorId,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCA5A5),
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#${entry.errorType.id} ${entry.errorType.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AngelCyanAccent
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Task: ${entry.task} (${entry.commandKey})",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = entry.simpleExplanationMm,
                    fontSize = 10.sp,
                    color = AngelTextSecondary,
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Inspect 3-Factor Analysis",
                tint = AngelCyanAccent
            )
        }
    }
}

@Composable
private fun ErrorLogDetailedCardItem(
    entry: ErrorLearningWorkflowEngine.ErrorLogEntry,
    isCurrentlyBlocked: Boolean,
    onVerifyCondition: () -> Unit
) {
    val context = LocalContext.current
    val isTtsRelated = entry.commandKey.contains("tts", ignoreCase = true) ||
        entry.task.contains("TTS", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (isCurrentlyBlocked) Color(0xFFEF4444).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.errorId} • ${entry.dateTime}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCurrentlyBlocked) Color(0xFFEF4444) else Color(0xFF10B981)
                ) {
                    Text(
                        text = if (isCurrentlyBlocked) "BLOCKED" else "RESOLVED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text("Task: ${entry.task}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Error Type: #${entry.errorType.id} ${entry.errorType.label}", fontSize = 11.sp, color = Color(0xFFF59E0B))
            Text("Expected: ${entry.expectedResult}", fontSize = 11.sp)
            Text("Actual: ${entry.actualResult}", fontSize = 11.sp)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("A. Technical Cause:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(entry.threeFactorAnalysis.technicalCause, fontSize = 11.sp)

            Text("B. Resource / Source Limitation:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(entry.threeFactorAnalysis.resourceLimitation, fontSize = 11.sp)

            Text("C. Real-World / Practical Mismatch:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(entry.threeFactorAnalysis.realWorldMismatch, fontSize = 11.sp)

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text("Evidence: ${entry.evidence}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("Attempted Method: ${entry.attemptedMethod}", fontSize = 10.sp)
            Text("Do Not Reuse: ${entry.doNotReuseMethod}", fontSize = 10.sp, color = Color(0xFFEF4444))
            Text("Recovery: ${entry.recoveryAttempt} -> ${entry.recoveryResult}", fontSize = 10.sp)
            Text("Verification: ${entry.verificationStatus}", fontSize = 10.sp, fontWeight = FontWeight.Medium)

            if (isCurrentlyBlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onVerifyCondition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Condition Verifier ဖြင့် ပြန်လည်စစ်ဆေးမည်",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isTtsRelated) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            // Opening the TTS voice settings does not verify
                            // that a voice pack was installed, so the blocked
                            // record is NOT resolved here (FAKE-10, 2026-10-22).
                            // It clears only via a real live-condition check
                            // or the user's explicit resolve action.
                            com.example.utils.MyanmarTTSVoicePackManager.getInstance(context).launchVoicePackDownload(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AngelNeonGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TTS အသံစနစ် တိုက်ရိုက်ဖွင့်ပြီး ဖြေရှင်းမည်",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorLogThreeFactorDetailDialog(
    entry: ErrorLearningWorkflowEngine.ErrorLogEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Error Log: ${entry.errorId} (3-Factor Analysis)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "#${entry.errorType.id} ${entry.errorType.label} • Result: ${entry.result.name}",
                    fontSize = 12.sp,
                    color = Color(0xFFEF4444)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("three_factor_detail_dialog_content"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = ErrorLearningWorkflowEngine.MANDATORY_WARNING_PREFIX,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.simpleExplanationMm,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = entry.toFormattedReport(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_three_factor_detail_btn")
            ) {
                Text("ပိတ်မည်")
            }
        }
    )
}
