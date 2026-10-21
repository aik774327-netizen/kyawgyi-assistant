package com.example.ui.shortcuts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.entity.ShortcutEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomShortcutsScreen(
    viewModel: ShortcutViewModel = viewModel()
) {
    val shortcutList by viewModel.shortcuts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<ShortcutEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကျော်ကြီး အသံတိုလမ်းကြောင်းများ", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Shortcut")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (shortcutList.isEmpty()) {
                Text(
                    text = "စိတ်ကြိုက် အသံတိုလမ်းကြောင်းများ မရှိသေးပါခင်ဗျာ။\n+ ခလုတ်ကို နှိပ်၍ အသစ်ထည့်သွင်းပါ။",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shortcutList, key = { it.id }) { shortcut ->
                        ShortcutItemCard(
                            shortcut = shortcut,
                            onToggle = { viewModel.toggleShortcutStatus(shortcut) },
                            onEdit = { editingShortcut = shortcut },
                            onDelete = { viewModel.deleteShortcut(shortcut) }
                        )
                    }
                }
            }
        }

        // Add Shortcut Dialog Pop-up
        if (showAddDialog) {
            AddShortcutDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { phrase, actionType, payload ->
                    viewModel.addShortcut(phrase, actionType, payload)
                    showAddDialog = false
                }
            )
        }

        // Edit Shortcut Dialog Pop-up
        editingShortcut?.let { shortcutToEdit ->
            AddShortcutDialog(
                initialShortcut = shortcutToEdit,
                onDismiss = { editingShortcut = null },
                onConfirm = { phrase, actionType, payload ->
                    viewModel.updateShortcut(
                        shortcutToEdit.copy(
                            triggerPhrase = phrase.trim().lowercase(),
                            actionType = actionType,
                            payload = payload.trim()
                        )
                    )
                    editingShortcut = null
                }
            )
        }
    }
}

@Composable
fun ShortcutItemCard(
    shortcut: ShortcutEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"${shortcut.triggerPhrase}\"",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Action: ${shortcut.actionType} | Payload: ${shortcut.payload}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = shortcut.isEnabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Shortcut",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Shortcut",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShortcutDialog(
    initialShortcut: ShortcutEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (phrase: String, actionType: String, payload: String) -> Unit
) {
    var triggerPhrase by remember { mutableStateOf(initialShortcut?.triggerPhrase ?: "") }
    var selectedActionType by remember { mutableStateOf(initialShortcut?.actionType ?: "OPEN_APP") }
    var payload by remember { mutableStateOf(initialShortcut?.payload ?: "") }
    var expandedMenu by remember { mutableStateOf(false) }

    val actionTypes = listOf("OPEN_APP", "CALL", "TOGGLE_WIFI", "TOGGLE_FLASH")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialShortcut != null) "အသံတိုလမ်းကြောင်း ပြင်ဆင်ရန်" else "အသံတိုလမ်းကြောင်း အသစ်ထည့်ရန်") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = triggerPhrase,
                    onValueChange = { triggerPhrase = it },
                    label = { Text("အသံစကားလုံး (ဥပမာ- ဖေ့ဘုတ်ဖွင့်)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedMenu,
                    onExpandedChange = { expandedMenu = !expandedMenu }
                ) {
                    OutlinedTextField(
                        value = selectedActionType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("လုပ်ဆောင်မည့် Action") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        actionTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    selectedActionType = type
                                    expandedMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Payload (ဥပမာ- com.facebook.katana သို့ 09xxxxxxxxx)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (triggerPhrase.isNotBlank() && payload.isNotBlank()) {
                        onConfirm(triggerPhrase, selectedActionType, payload)
                    }
                }
            ) {
                Text(if (initialShortcut != null) "ပြင်ဆင်မည်" else "သိမ်းဆည်းမည်")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("မလုပ်တော့ပါ")
            }
        }
    )
}
