package com.example.ui.dictation

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val LANGUAGE_LABELS: List<Pair<String, String>> = listOf(
    "my-MM" to "မြန်မာ (my-MM)",
    "en-US" to "English (US)",
    "th-TH" to "ไทย (th-TH)"
)

/**
 * Native Voice Typing (dictation) screen — a first-class Compose
 * re-implementation of the standalone "Kyawgyi Myanmar Voice Typing v2.1"
 * HTML page. No WebView is involved: recognition is driven by the Android
 * system SpeechRecognizer through [DictationViewModel], so the feature
 * works fully offline-of-the-web and the text lives in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(
    onBack: () -> Unit,
    viewModel: DictationViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val loadedDocument by viewModel.loadedDocument.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var editorValue by remember { mutableStateOf(TextFieldValue()) }
    var restored by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val currentEditorValue = rememberUpdatedState(editorValue)

    // Restore the persisted document once, without clobbering fresh typing.
    LaunchedEffect(loadedDocument) {
        val doc = loadedDocument
        if (doc != null && !restored) {
            restored = true
            if (editorValue.text.isEmpty() && doc.text.isNotEmpty()) {
                editorValue = TextFieldValue(doc.text, TextRange(doc.text.length))
            }
        }
    }

    // Final transcripts that reach this flow have already been routed by
    // the ViewModel: segments the command pipeline executed never arrive
    // here; the rest are inserted at the caret. Interim text is only
    // shown in the "ယာယီစာသား" strip and never touches the document.
    LaunchedEffect(viewModel) {
        viewModel.finalTranscripts.collect { final ->
            val current = currentEditorValue.value
            val insertion = DictationText.insertTranscript(
                document = current.text,
                selectionStart = current.selection.start,
                selectionEnd = current.selection.end,
                rawTranscript = final.text
            )
            if (insertion != null) {
                editorValue = TextFieldValue(insertion.newText, TextRange(insertion.newCaret))
                viewModel.onDocumentEdited(insertion.newText)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Microphone permission, following the app's established
    // ActivityResultContracts.RequestPermission pattern.
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    LaunchedEffect(permissionGranted) { viewModel.onMicPermissionResult(permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        viewModel.onMicPermissionResult(granted)
        if (granted) viewModel.startListening()
        else viewModel.notifyUser("မိုက်ခွင့်ပြုချက် မရပါ။ ဖုန်း Settings မှာ Microphone ခွင့်ပြုပါ။")
    }

    // TXT export via the system document picker — no storage permission.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = currentEditorValue.value.text
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    // UTF-8 BOM, exactly like the HTML v2.1 TXT export.
                    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "TXT ဖိုင်ကို သိမ်းဆည်းပြီးပါပြီ။", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "သိမ်းမရပါ။", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.stopListening() }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("စာသားဖျက်ရန်") },
            text = { Text("စာသားအားလုံးကို ဖျက်မည်မှာ သေချာပါသလား?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        editorValue = TextFieldValue("")
                        viewModel.onDocumentEdited("")
                        Toast.makeText(context, "စာသားရှင်းပြီးပါပြီ။", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("ဖျက်မည်") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("မဖျက်ပါ") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header: back + title + status chip (HTML v2.1 layout parity)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("dictation_back_btn")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "နောက်သို့"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ကျော်ကြီး — AUTO VOICE TYPING",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = "မြန်မာအသံမှ စာသား • စာသားတည်းဖြတ်မှု",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val chipColor = when (state.statusKind) {
                DictationStatusKind.LISTENING -> MaterialTheme.colorScheme.error
                DictationStatusKind.RESTARTING -> MaterialTheme.colorScheme.primary
                DictationStatusKind.ERROR, DictationStatusKind.UNAVAILABLE -> MaterialTheme.colorScheme.error
                DictationStatusKind.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("dictation_status_chip")
            ) {
                Text(
                    text = state.statusMessage,
                    fontSize = 12.sp,
                    color = chipColor,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar: clear / copy / TXT + count & save state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (editorValue.text.isNotBlank()) showClearConfirm = true
                            },
                            modifier = Modifier.testTag("dictation_clear_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ဖျက်မည်", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val text = editorValue.text
                                if (text.isBlank()) {
                                    Toast.makeText(context, "ကူးယူရန် စာသားမရှိပါ။", Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "စာသားကူးယူပြီးပါပြီ။", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("dictation_copy_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ကူးယူမည်", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                if (editorValue.text.isBlank()) {
                                    Toast.makeText(context, "သိမ်းရန် စာသားမရှိပါ။", Toast.LENGTH_SHORT).show()
                                } else {
                                    exportLauncher.launch("kyawgyi-voice-note.txt")
                                }
                            },
                            modifier = Modifier.testTag("dictation_export_btn")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TXT သိမ်းမည်", fontSize = 13.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${DictationText.countCharacters(editorValue.text)} စာလုံး",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("dictation_count_label")
                        )
                        Text(
                            text = dictationSaveStateLabel(state.saveState, state.autoSave),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interim (partial) transcript strip — display only.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "ယာယီစာသား — " + state.interimText.ifEmpty { "စကားပြောသည့်အခါ ဤနေရာတွင် ပေါ်မည်။" },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("dictation_interim_label")
                    )
                }

                // Editor
                OutlinedTextField(
                    value = editorValue,
                    onValueChange = { newValue ->
                        editorValue = newValue
                        viewModel.onDocumentEdited(newValue.text)
                    },
                    placeholder = {
                        Text("ဤနေရာတွင် စာရိုက်နိုင်သည်၊ သို့မဟုတ် မိုက်ခရိုဖုန်းကို နှိပ်ပြီး ပြောပါ။")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .testTag("dictation_editor"),
                    minLines = 8
                )

                // Error guidance, when the recognizer reported a problem.
                state.guidanceMessage?.let { guidance ->
                    Text(
                        text = guidance,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("dictation_guidance_label")
                    )
                }

                // Settings row: language + auto-restart + auto-save
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = languageMenuExpanded,
                        onExpandedChange = { languageMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = LANGUAGE_LABELS.firstOrNull { it.first == state.languageCode }?.second
                                ?: state.languageCode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("အသံဘာသာစကား", fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("dictation_language_field")
                        )
                        ExposedDropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            LANGUAGE_LABELS.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        viewModel.setLanguage(code)
                                    }
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = state.autoRestart,
                            onCheckedChange = { viewModel.setAutoRestart(it) },
                            modifier = Modifier.testTag("dictation_autorestart_switch")
                        )
                        Text("ပြန်စရန်", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = state.autoSave,
                            onCheckedChange = { viewModel.setAutoSave(it) },
                            modifier = Modifier.testTag("dictation_autosave_switch")
                        )
                        Text("Auto-save", fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = state.commandCheckEnabled,
                            onCheckedChange = { viewModel.setCommandCheckEnabled(it) },
                            modifier = Modifier.testTag("dictation_command_check_switch")
                        )
                        Text("အမိန့်အဖြစ် စစ်ရန်", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Big mic button (HTML v2.1 parity: tap to start, tap again to stop)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    when {
                        state.wantsToListen -> viewModel.stopListening()
                        !state.isRecognitionAvailable -> viewModel.startListening()
                        permissionGranted -> viewModel.startListening()
                        else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = state.isRecognitionAvailable,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.wantsToListen) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .size(76.dp)
                    .testTag("dictation_mic_btn")
            ) {
                Icon(
                    imageVector = if (state.wantsToListen) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (state.wantsToListen) "အသံဖမ်းရပ်ရန်" else "အသံဖမ်းစတင်ရန်",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "အသုံးပြုနည်း: မိုက်ခရိုဖုန်းကို နှိပ်၍ စတင်ပါ၊ ထပ်နှိပ်၍ ရပ်ပါ။ စာသားကို တိုက်ရိုက်ပြင်ဆင်နိုင်သည်။\n" +
                "\"အမိန့်အဖြစ် စစ်ရန်\" ဖွင့်ထားပါက ပြောလိုက်သော စာသားက အမိန့် (ဥပမာ \"Facebook ဖွင့်ပေးပါ\") ဖြစ်နေလျှင် စာသားအဖြစ် မထည့်ဘဲ ချက်ချင်း လုပ်ဆောင်ပေးသည်။\n" +
                "မှတ်ချက်: အသံမှစာသားပြောင်းခြင်းကို ဖုန်း၏ Speech Recognition ဝန်ဆောင်မှုက ပံ့ပိုးရမည်ဖြစ်ပြီး အချို့ ဖုန်းများတွင် အင်တာနက်လိုနိုင်သည်။ " +
                "မြန်မာဘာသာကို ဖုန်းဝန်ဆောင်မှုက မပံ့ပိုးပါက ဘာသာစကားရွေးချယ်ခြင်းတစ်ခုတည်းဖြင့် မဖြေရှင်းနိုင်ပါ။",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
