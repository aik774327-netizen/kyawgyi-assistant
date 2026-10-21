package com.example.ui.urlcontrol

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.actions.UrlExecutionResult
import com.example.data.local.entity.UrlControlEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlControlScreen(
    viewModel: UrlControlViewModel = viewModel()
) {
    val context = LocalContext.current
    val urlControls by viewModel.urlControls.collectAsState()
    val globalRemoteMicUrl by viewModel.globalRemoteMicUrl.collectAsState()
    val isGlobalForwardingEnabled by viewModel.isGlobalForwardingEnabled.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<UrlControlEntity?>(null) }
    var showGlobalSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "URL Link အသံဖြင့် ထိန်းချုပ်မှု",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Microphone URL & External App Voice Control",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGlobalSettingsDialog = true },
                        modifier = Modifier.testTag("global_mic_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsRemote,
                            contentDescription = "Remote Mic URL Configuration",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.AddLink, contentDescription = "Add URL Link") },
                text = { Text("URL Link အသစ်ထည့်မည်") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_url_fab")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            // Section 1: Banner & Remote Mic Stream Link Card
            item {
                RemoteMicBannerCard(
                    globalRemoteMicUrl = globalRemoteMicUrl,
                    isGlobalForwardingEnabled = isGlobalForwardingEnabled,
                    onConfigureClick = { showGlobalSettingsDialog = true },
                    onTestStream = {
                        if (globalRemoteMicUrl.isNotBlank()) {
                            viewModel.testRemoteMicStreamUrl(globalRemoteMicUrl)
                        } else {
                            Toast.makeText(context, "Microphone URL Link အရင်ထည့်သွင်းပါ", Toast.LENGTH_SHORT).show()
                        }
                    },
                    isTesting = isTesting
                )
            }

            // Section 2: Quick Template Preset Generator
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ချိတ်ဆက်ထားသော URL Links (${urlControls.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = {
                            viewModel.loadPresetTemplates()
                            Toast.makeText(context, "နမူနာ နည်းစနစ်များ ထည့်သွင်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("နမူနာ ပုံစံများထည့်ရန်", fontSize = 12.sp)
                    }
                }
            }

            // Section 3: List of Configured URL Controls
            if (urlControls.isEmpty()) {
                item {
                    EmptyUrlControlState(onAddClick = { showAddDialog = true })
                }
            } else {
                items(urlControls, key = { it.id }) { control ->
                    UrlControlItemCard(
                        control = control,
                        onToggle = { viewModel.toggleStatus(control) },
                        onEdit = { editingEntity = control },
                        onDelete = { viewModel.deleteUrlControl(control) },
                        onTest = {
                            viewModel.testUrlControl(control)
                        },
                        isTesting = isTesting
                    )
                }
            }
        }

        // Test Result Feedback Dialog
        testResult?.let { result ->
            TestResultDialog(
                result = result,
                onDismiss = { viewModel.clearTestResult() }
            )
        }

        // Add Dialog
        if (showAddDialog) {
            AddOrEditUrlDialog(
                initial = null,
                onDismiss = { showAddDialog = false },
                onSave = { name, trigger, url, actionType, body, headers, successSpeech, failureSpeech ->
                    viewModel.addUrlControl(
                        name = name,
                        triggerPhrase = trigger,
                        url = url,
                        actionType = actionType,
                        requestBody = body,
                        headers = headers,
                        successSpeech = successSpeech,
                        failureSpeech = failureSpeech
                    )
                    showAddDialog = false
                    Toast.makeText(context, "URL Link အသစ် အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Edit Dialog
        editingEntity?.let { entityToEdit ->
            AddOrEditUrlDialog(
                initial = entityToEdit,
                onDismiss = { editingEntity = null },
                onSave = { name, trigger, url, actionType, body, headers, successSpeech, failureSpeech ->
                    viewModel.updateUrlControl(
                        entityToEdit.copy(
                            name = name,
                            triggerPhrase = trigger,
                            url = url,
                            actionType = actionType,
                            requestBody = body,
                            headers = headers,
                            successSpeech = successSpeech,
                            failureSpeech = failureSpeech
                        )
                    )
                    editingEntity = null
                    Toast.makeText(context, "URL Link ပြင်ဆင်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Global Remote Mic Forwarding Configuration Dialog
        if (showGlobalSettingsDialog) {
            GlobalRemoteMicDialog(
                currentUrl = globalRemoteMicUrl,
                currentEnabled = isGlobalForwardingEnabled,
                onDismiss = { showGlobalSettingsDialog = false },
                onSave = { newUrl, enabled ->
                    viewModel.saveGlobalForwarding(newUrl, enabled)
                    showGlobalSettingsDialog = false
                    Toast.makeText(context, "Microphone Link ဆက်တင်များ သိမ်းဆည်းပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// COMPONENTS
// ---------------------------------------------------------------------------------

@Composable
fun RemoteMicBannerCard(
    globalRemoteMicUrl: String,
    isGlobalForwardingEnabled: Boolean,
    onConfigureClick: () -> Unit,
    onTestStream: () -> Unit,
    isTesting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MicExternalOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Microphone Stream URL ချိတ်ဆက်မှု",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = if (isGlobalForwardingEnabled && globalRemoteMicUrl.isNotBlank()) "အသံလွှင့်စနစ် ဖွင့်ထားပါသည် (Active)" else "ချိတ်ဆက်မှု မပြုလုပ်ထားသေးပါ",
                            fontSize = 11.sp,
                            color = if (isGlobalForwardingEnabled && globalRemoteMicUrl.isNotBlank()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = onConfigureClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Remote Mic URL",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (globalRemoteMicUrl.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = globalRemoteMicUrl,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Text(
                    text = "မိုက်ခရိုဖုန်း အသံဒေတာများကို တခြား App/Server Webhook သို့ တိုက်ရိုက်ပို့ရန် URL link ထည့်သွင်းနိုင်ပါသည်",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onTestStream,
                    enabled = !isTesting,
                    modifier = Modifier.height(36.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("စမ်းသပ်ချိတ်ဆက်မည်", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun UrlControlItemCard(
    control: UrlControlEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    isTesting: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("url_item_${control.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (control.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ActionTypeBadge(control.actionType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = control.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = control.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("switch_${control.id}")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Trigger Voice Phrase Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "အသံအမိန့်: \"${control.triggerPhrase}\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Target URL display
            Text(
                text = control.url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (control.lastExecutedAt > 0) {
                val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }
                val timeStr = dateFormat.format(Date(control.lastExecutedAt))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (control.lastStatus == "SUCCESS") Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "နောက်ဆုံး စမ်းသပ်/အလုပ်လုပ်ချိန်: $timeStr (${control.lastStatus})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Actions: Test Button, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTest,
                    enabled = !isTesting && control.isEnabled,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("test_btn_${control.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("စမ်းသပ်မည်", fontSize = 12.sp)
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionTypeBadge(actionType: String) {
    val (bgColor, textColor, label) = when (actionType.uppercase()) {
        "DEEP_LINK" -> Triple(Color(0xFF818CF8).copy(alpha = 0.2f), Color(0xFF4F46E5), "App DeepLink")
        "WEBHOOK_POST" -> Triple(Color(0xFF34D399).copy(alpha = 0.2f), Color(0xFF059669), "POST Webhook")
        "WEBHOOK_GET" -> Triple(Color(0xFF60A5FA).copy(alpha = 0.2f), Color(0xFF2563EB), "GET Webhook")
        "REMOTE_MIC_STREAM" -> Triple(Color(0xFFF472B6).copy(alpha = 0.2f), Color(0xFFDB2777), "Mic Stream")
        else -> Triple(Color(0xFFFBBF24).copy(alpha = 0.2f), Color(0xFFD97706), "Web URL")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyUrlControlState(onAddClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "URL Link ချိတ်ဆက်မှုများ မရှိသေးပါခင်ဗျာ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "အောက်ပါ ခလုတ်ကို နှိပ်၍ မြန်မာအသံဖြင့် ထိန်းချုပ်မည့် Link အသစ်ထည့်ပါ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Link အသစ်ထည့်ရန်")
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// DIALOGS
// ---------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditUrlDialog(
    initial: UrlControlEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        triggerPhrase: String,
        url: String,
        actionType: String,
        requestBody: String,
        headers: String,
        successSpeech: String,
        failureSpeech: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var triggerPhrase by remember { mutableStateOf(initial?.triggerPhrase ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var actionType by remember { mutableStateOf(initial?.actionType ?: "DEEP_LINK") }
    var requestBody by remember { mutableStateOf(initial?.requestBody ?: "") }
    var headers by remember { mutableStateOf(initial?.headers ?: "") }
    var successSpeech by remember { mutableStateOf(initial?.successSpeech ?: "") }
    var failureSpeech by remember { mutableStateOf(initial?.failureSpeech ?: "") }
    var expandedTypeMenu by remember { mutableStateOf(false) }

    val actionTypes = listOf(
        "DEEP_LINK" to "App DeepLink (tg://, spotify://, etc.)",
        "OPEN_URL" to "Web URL Browser Link (https://...)",
        "WEBHOOK_POST" to "HTTP POST Webhook (JSON Payload)",
        "WEBHOOK_GET" to "HTTP GET Webhook (Query Params)",
        "REMOTE_MIC_STREAM" to "Remote Microphone Stream Link"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial != null) "URL Link ပြင်ဆင်ရန်" else "URL Link အသစ် ထည့်သွင်းရန်",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("အမည် (Name)") },
                        placeholder = { Text("ဥပမာ- Home Assistant မီးဖွင့်") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = triggerPhrase,
                        onValueChange = { triggerPhrase = it },
                        label = { Text("မြန်မာဘာသာစကား အသံစကားလုံး (Trigger Phrase)") },
                        placeholder = { Text("ဥပမာ- မီးခလုတ်ဖွင့်ပါ၊ တယ်လီဂရမ်ပို့") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedTypeMenu,
                        onExpandedChange = { expandedTypeMenu = !expandedTypeMenu }
                    ) {
                        OutlinedTextField(
                            value = actionTypes.firstOrNull { it.first == actionType }?.second ?: actionType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("ချိတ်ဆက်မည့် အမျိုးအစား (Type)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedTypeMenu,
                            onDismissRequest = { expandedTypeMenu = false }
                        ) {
                            actionTypes.forEach { (typeKey, typeLabel) ->
                                DropdownMenuItem(
                                    text = { Text(typeLabel) },
                                    onClick = {
                                        actionType = typeKey
                                        expandedTypeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL Link / Webhook / DeepLink") },
                        placeholder = { Text("https://..., tg://..., http://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (actionType == "WEBHOOK_POST" || actionType == "REMOTE_MIC_STREAM") {
                    item {
                        OutlinedTextField(
                            value = requestBody,
                            onValueChange = { requestBody = it },
                            label = { Text("JSON Body Payload Template (Optional)") },
                            placeholder = { Text("{\"command\":\"{voice_text}\", \"timestamp\":{timestamp}}") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        label = { Text("Custom HTTP Headers (Optional)") },
                        placeholder = { Text("Authorization: Bearer my_api_key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = successSpeech,
                        onValueChange = { successSpeech = it },
                        label = { Text("အောင်မြင်ပါက အသံထွက်ပြောမည့် စကားလုံး") },
                        placeholder = { Text("ဥပမာ- မီးခလုတ် ဖွင့်ပေးလိုက်ပါပြီ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && triggerPhrase.isNotBlank() && url.isNotBlank()) {
                        onSave(name, triggerPhrase, url, actionType, requestBody, headers, successSpeech, failureSpeech)
                    }
                },
                enabled = name.isNotBlank() && triggerPhrase.isNotBlank() && url.isNotBlank()
            ) {
                Text(if (initial != null) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("မလုပ်တော့ပါ")
            }
        }
    )
}

@Composable
fun GlobalRemoteMicDialog(
    currentUrl: String,
    currentEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (url: String, enabled: Boolean) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var enabled by remember { mutableStateOf(currentEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Microphone Stream URL ဆက်တင်",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "မိုက်ခရိုဖုန်းမှ ဖမ်းယူရရှိသော မြန်မာစကားသံ text/stream များကို အခြား Application ဆာဗာ သို့မဟုတ် Webhook endpoint သို့ တိုက်ရိုက် stream လွှင့်ပို့မည့် URL ဖြစ်ပါသည်",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Remote Stream / Webhook URL") },
                    placeholder = { Text("http://192.168.1.50:8080/mic/stream") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Forwarding အသံလွှင့်စနစ် ဖွင့်မည်", fontSize = 13.sp)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url, enabled) }) {
                Text("သိမ်းဆည်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ပိတ်မည်")
            }
        }
    )
}

@Composable
fun TestResultDialog(
    result: UrlExecutionResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (result.isSuccess) "ချိတ်ဆက်မှု အောင်မြင်ပါသည်" else "ချိတ်ဆက်မှု မအောင်မြင်ပါ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "အသံတုံ့ပြန်မှု: \"${result.speechFeedback}\"",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Status: HTTP ${result.httpStatusCode} | ကြာချိန်: ${result.latencyMs} ms",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                if (result.responseBody.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.responseBody,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("နားလည်ပါပြီ")
            }
        }
    )
}
