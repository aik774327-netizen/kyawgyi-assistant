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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.models.ContactItem
import com.example.models.CustomShortcut
import com.example.models.MemoryEntry
import com.example.permissions.BackgroundExecutionManager
import com.example.ui.permissions.RuntimePermissionState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
  shortcuts: List<CustomShortcut>,
  contacts: List<ContactItem>,
  contactAliases: List<MemoryEntry> = emptyList(),
  currentConfig: com.example.database.EcoFlowConfigEntity = com.example.database.EcoFlowConfigEntity(),
  hasContactsPermission: Boolean = false,
  onRequestContactsPermission: () -> Unit = {},
  permissionState: RuntimePermissionState = RuntimePermissionState(),
  onRequestMicrophone: () -> Unit = {},
  onRequestBluetooth: () -> Unit = {},
  onRequestLocation: () -> Unit = {},
  onRequestNotification: () -> Unit = {},
  onRequestAllPermissions: () -> Unit = {},
  onDismiss: () -> Unit,
  onAddShortcut: (String, String, String) -> Unit,
  onDeleteShortcut: (String) -> Unit,
  // Audit UI-1: sound/haptic are real persisted settings owned by the
  // ViewModel (consumed in handleActionResult), not dialog-local state.
  soundEnabled: Boolean = true,
  hapticEnabled: Boolean = true,
  onSoundEnabledChange: (Boolean) -> Unit = {},
  onHapticEnabledChange: (Boolean) -> Unit = {},
  onSaveEcoFlowConfig: (String, String, String, String) -> Unit = { _, _, _, _ -> },
  onRefreshContacts: () -> Unit = {},
  onSaveContactAlias: (String, String, String) -> Unit = { _, _, _ -> },
  onDeleteContactAlias: (String) -> Unit = {},
  onDialPhone: (String, String) -> Unit = { _, _ -> }
) {
  val context = LocalContext.current
  val bgManager = remember { BackgroundExecutionManager(context) }
  var isBatteryExempted by remember { mutableStateOf(bgManager.isBatteryOptimizationExempted()) }

  var selectedTab by remember { mutableIntStateOf(0) }
  // (sound/haptic state moved to ViewModel-backed params — audit UI-1)

  // Add-shortcut form state (audit UI-3: onAddShortcut was wired by
  // MainScreen but never invoked — there was no way to add a shortcut)
  var newShortcutTitle by remember { mutableStateOf("") }
  var newShortcutCommand by remember { mutableStateOf("") }

  // EcoFlow hardware keys state
  var ecoflowAccessKey by remember { mutableStateOf(currentConfig.accessKey) }
  var ecoflowSecretKey by remember { mutableStateOf(currentConfig.secretKey) }
  var ecoflowDeviceSn by remember { mutableStateOf(currentConfig.deviceSn) }
  var ecoflowHost by remember { mutableStateOf(if (currentConfig.hostUrl.isNotBlank()) currentConfig.hostUrl else "https://api-a.ecoflow.com") }

  // Contact alias form state
  var newAliasName by remember { mutableStateOf("") }
  var newAliasPhone by remember { mutableStateOf("") }
  var contactSearchQuery by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(BackgroundDark)
        .border(1.2.dp, SurfaceCardBorder, RoundedCornerShape(24.dp))
        .padding(18.dp)
        .testTag("settings_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Top Header
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
                .background(Color(0x333B82F6)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(text = "System Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
              Text(text = "အသံအမိန့်ပေးချက်နှင့် ဆက်တင်များ", fontSize = 11.sp, color = TextMuted)
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs (General, Contacts, Permissions, EcoFlow API)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          listOf("General", "Contacts", "Permissions", "EcoFlow API").forEachIndexed { index, title ->
            val isSel = selectedTab == index
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSel) EmeraldPrimary else Color.Transparent)
                .clickable { selectedTab = index }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSel) Color.Black else TextMuted
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
          0 -> {
            // General Settings (Audio & Haptics & Shortcuts)
            Text(text = "Audio & Haptic Feedback", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            SettingToggleRow(
              title = "System Sound Effects",
              sub = "လုပ်ဆောင်ချက် အောင်မြင်/သတိပေး အသံများ ဖွင့်ရန်",
              isChecked = soundEnabled,
              onCheckedChange = { onSoundEnabledChange(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingToggleRow(
              title = "Haptic Vibration Feedback",
              sub = "လုပ်ဆောင်ချက် အောင်မြင်မှု တုန်ခါမှု အချက်ပြစနစ်",
              isChecked = hapticEnabled,
              onCheckedChange = { onHapticEnabledChange(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Shortcuts
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(text = "Custom Voice Shortcuts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
              Text(text = "${shortcuts.size} items", fontSize = 10.sp, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(8.dp))

            shortcuts.forEach { sc ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 3.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(SurfaceCard)
                  .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(text = sc.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                  Text(text = "အမိန့်ပေးစကား: \"${sc.voiceCommand}\"", fontSize = 10.sp, color = TextMuted)
                }
                IconButton(
                  onClick = { onDeleteShortcut(sc.id) },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
              }
            }

            // Add-shortcut form (audit UI-3)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Shortcut အသစ် ထည့်ရန်", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = newShortcutTitle,
                onValueChange = { newShortcutTitle = it },
                placeholder = { Text("အမည် (ဥပမာ: မီးဖွင့်)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary,
                  focusedBorderColor = EmeraldPrimary,
                  unfocusedBorderColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
              )
              OutlinedTextField(
                value = newShortcutCommand,
                onValueChange = { newShortcutCommand = it },
                placeholder = { Text("အမိန့်စကား (ဥပမာ: မီးဖွင့်ပေး)", fontSize = 10.sp) },
                modifier = Modifier.weight(1.2f),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary,
                  focusedBorderColor = EmeraldPrimary,
                  unfocusedBorderColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
              )
              Button(
                onClick = {
                  if (newShortcutTitle.isNotBlank() && newShortcutCommand.isNotBlank()) {
                    onAddShortcut(newShortcutTitle, newShortcutCommand, "custom_voice")
                    newShortcutTitle = ""
                    newShortcutCommand = ""
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text("ထည့်မည်", fontSize = 11.sp)
              }
            }
          }

          1 -> {
            // Contacts & Voice Calling Alias Tab
            Text(text = "ဖုန်းခေါ်ဆိုမှုနှင့် အဆက်အသွယ်များ (Contacts & Calling)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldLight)
            Text(text = "\"အမေ့ဆီ ဖုန်းခေါ်ပါ\"၊ \"မိတ်ဆွေဆီ ဖုန်းခေါ်ပါ\" အစရှိသော အသံဖြင့် ခေါ်ဆိုရန် ချိတ်ဆက်မှု", fontSize = 10.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Permission & Sync Status Box
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (hasContactsPermission) Color(0x1A10B981) else Color(0x1AF59E0B))
                .border(1.dp, if (hasContactsPermission) EmeraldPrimary else AmberAccent, RoundedCornerShape(12.dp))
                .padding(12.dp)
            ) {
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      if (hasContactsPermission) Icons.Default.Check else Icons.Default.Warning,
                      contentDescription = null,
                      tint = if (hasContactsPermission) EmeraldPrimary else AmberAccent,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (hasContactsPermission) "READ_CONTACTS: ခွင့်ပြုချက် ရရှိပြီး" else "READ_CONTACTS: ခွင့်ပြုချက် မပေးရသေးပါ",
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (hasContactsPermission) EmeraldPrimary else AmberAccent
                    )
                  }

                  if (!hasContactsPermission) {
                    OutlinedButton(
                      onClick = onRequestContactsPermission,
                      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Text("ခွင့်ပြုချက်ပေးရန်", fontSize = 10.sp, color = AmberAccent)
                    }
                  }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "စနစ်ဖုန်းစာအုပ်မှ ရယူထားမှု: ${contacts.size} ခု",
                    fontSize = 10.sp,
                    color = TextMuted
                  )
                  OutlinedButton(
                    onClick = onRefreshContacts,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text("Sync Contacts", fontSize = 10.sp, color = EmeraldPrimary)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Voice Calling Aliases Section
            Text(
              text = "အသံဖြင့်ခေါ်ဆိုရန် အမည်ဝှက်များ (Voice Calling Aliases)",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            Text(
              text = "အသံဖြင့် \"အမေ့ဆီ\"၊ \"မိတ်ဆွေဆီ\" ဟု ခေါ်ဆိုရာတွင် အသုံးပြုမည့် အစစ်အမှန် ဖုန်းနံပါတ်များ",
              fontSize = 10.sp,
              color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Quick Burmese Suggestion Chips
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf("အမေ", "မိတ်ဆွေ", "အဖေ", "ရုံး", "သူငယ်ချင်း").forEach { chip ->
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .clickable { newAliasName = chip }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                  Text(text = chip, fontSize = 9.sp, color = EmeraldLight)
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add Alias Form
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = newAliasName,
                onValueChange = { newAliasName = it },
                placeholder = { Text("အမည်ဝှက် (ဥပမာ: အမေ)", fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary,
                  focusedBorderColor = EmeraldPrimary,
                  unfocusedBorderColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
              )

              OutlinedTextField(
                value = newAliasPhone,
                onValueChange = { newAliasPhone = it },
                placeholder = { Text("ဖုန်းနံပါတ် (09...)", fontSize = 10.sp) },
                modifier = Modifier.weight(1.2f),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = TextPrimary,
                  unfocusedTextColor = TextPrimary,
                  focusedBorderColor = EmeraldPrimary,
                  unfocusedBorderColor = SurfaceCardBorder
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
              )

              Button(
                onClick = {
                  if (newAliasName.isNotBlank() && newAliasPhone.isNotBlank()) {
                    onSaveContactAlias(newAliasName, newAliasPhone, newAliasName)
                    newAliasName = ""
                    newAliasPhone = ""
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(16.dp))
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Display List of saved aliases from contactAliases / memories
            val activeAliases = contactAliases.filter {
              it.category == "CONTACT_ALIAS" || it.category == "ALIAS" || it.targetValue.matches(Regex("^[0-9+]+$"))
            }

            if (activeAliases.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(SurfaceCard)
                  .padding(10.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "အမည်ဝှက် ချိတ်ဆက်ထားခြင်း မရှိသေးပါ။ အပေါ်ရှိ Form တွင် ထည့်သွင်းသိမ်းဆည်းပါ",
                  fontSize = 10.sp,
                  color = TextMuted
                )
              }
            } else {
              activeAliases.forEach { mem ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x3310B981)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(Icons.Default.Phone, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                      Text(text = mem.aliasOrKey, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                      Text(text = mem.targetValue, fontSize = 10.sp, color = EmeraldLight)
                    }
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // Direct Dial Test Button (Android ACTION_DIAL)
                    IconButton(
                      onClick = { onDialPhone(mem.aliasOrKey, mem.targetValue) },
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(Icons.Default.Phone, contentDescription = "Call", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }

                    // Delete Alias Button
                    IconButton(
                      onClick = { onDeleteContactAlias(mem.id) },
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Android System Contacts Provider List
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(text = "ဖုန်းထဲရှိ အဆက်အသွယ်များ (Device Contacts)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "ContactsContract မှ ရယူထားသော စာရင်း", fontSize = 10.sp, color = TextMuted)
              }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Search textfield
            OutlinedTextField(
              value = contactSearchQuery,
              onValueChange = { contactSearchQuery = it },
              placeholder = { Text("အဆက်အသွယ် ရှာဖွေရန်...", fontSize = 10.sp) },
              leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = SurfaceCardBorder
              ),
              shape = RoundedCornerShape(8.dp),
              singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))

            val filteredContacts = contacts.filter {
              val q = contactSearchQuery.trim().lowercase()
              q.isEmpty() || it.name.lowercase().contains(q) || it.phoneNumber.contains(q) || it.relationship.lowercase().contains(q)
            }

            if (filteredContacts.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(SurfaceCard)
                  .padding(12.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (!hasContactsPermission) "READ_CONTACTS ခွင့်ပြုချက် မပေးရသေးပါ" else "အဆက်အသွယ် ရှာမတွေ့ပါ",
                  fontSize = 10.sp,
                  color = TextMuted
                )
              }
            } else {
              filteredContacts.take(20).forEach { c ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = c.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = c.phoneNumber, fontSize = 10.sp, color = TextMuted)
                    if (c.relationship.isNotBlank()) {
                      Text(text = "ဆက်နွယ်မှု: ${c.relationship}", fontSize = 9.sp, color = EmeraldLight)
                    }
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Map button: auto fills alias form
                    OutlinedButton(
                      onClick = {
                        newAliasName = c.relationship.ifEmpty { c.name }
                        newAliasPhone = c.phoneNumber
                      },
                      contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text("Map Alias", fontSize = 9.sp, color = EmeraldLight)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Test Dial Button
                    IconButton(
                      onClick = { onDialPhone(c.name, c.phoneNumber) },
                      modifier = Modifier.size(28.dp)
                    ) {
                      Icon(Icons.Default.Phone, contentDescription = "Call", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                    }
                  }
                }
              }
            }
          }

          2 -> {
            // Permissions & Background Reliability Overview
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "System Permissions Checklist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(text = "Android 12/13+ မူဝါဒအရ ခွင့်ပြုချက် အခြေအနေများ", fontSize = 10.sp, color = TextMuted)
              }
              if (!permissionState.areAllEssentialGranted || !hasContactsPermission) {
                OutlinedButton(
                  onClick = onRequestAllPermissions,
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text("အားလုံး ခွင့်ပြုမည်", fontSize = 9.sp, color = EmeraldLight)
                }
              }
            }
            Spacer(modifier = Modifier.height(8.dp))

            PermissionStatusRow(
              title = "Microphone (Record Audio)",
              sub = "အသံဖြင့် အမိန့်ပေးရန်နှင့် နားထောင်ရန်",
              isGranted = permissionState.isMicrophoneGranted,
              onRequest = onRequestMicrophone
            )
            PermissionStatusRow(
              title = "Bluetooth Scan & Connect",
              sub = "EcoFlow Hardware နှင့် BLE စက်များ ရှာဖွေချိတ်ဆက်ရန်",
              isGranted = permissionState.isBluetoothGranted,
              onRequest = onRequestBluetooth
            )
            PermissionStatusRow(
              title = "Push Notifications",
              sub = "Foreground Service သတိပေးချက်များ ပြသရန် (Android 13+)",
              isGranted = permissionState.isNotificationGranted,
              onRequest = onRequestNotification
            )
            PermissionStatusRow(
              title = "Location & GPS",
              sub = "မိုးလေဝသနှင့် အနီးဆုံးသတင်းအချက်အလက်",
              isGranted = permissionState.isLocationGranted,
              onRequest = onRequestLocation
            )
            PermissionStatusRow(
              title = "Contacts (Read Contacts)",
              sub = "အမေ၊ အဖေ၊ မိတ်ဆွေများဆီသို့ ဖုန်းခေါ်ရန်",
              isGranted = hasContactsPermission,
              onRequest = onRequestContactsPermission
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Background Execution & Screen Off Reliability
            Text(
              text = "နောက်ခံနှင့် စခရင်ပိတ်ထားချိန် အလုပ်လုပ်နိုင်စွမ်း",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = EmeraldLight
            )
            Text(
              text = "စခရင်ပိတ်ထားချိန် \"Hey Kyaw Gyi\" အသံကြားနိုင်ရန် ဘက်ထရီချွင်းချက်နှင့် OEM Autostart လိုအပ်ပါသည်",
              fontSize = 10.sp,
              color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Battery Optimization Row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .border(1.dp, if (isBatteryExempted) Color(0x3310B981) else Color(0x33F59E0B), RoundedCornerShape(10.dp))
                .padding(10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Battery Optimization",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Text(
                  text = if (isBatteryExempted) "ဘက်ထရီကန့်သတ်မှု ကင်းလွတ်ခွင့် ရရှိပြီး (Exempted)" else "ဘက်ထရီ ချွေတာမှုဖြင့် နောက်ခံကို ပိတ်ဆို့နိုင်သည် (Optimized)",
                  fontSize = 9.sp,
                  color = if (isBatteryExempted) EmeraldLight else AmberAccent
                )
              }
              OutlinedButton(
                onClick = {
                  bgManager.openBatteryOptimizationSettings()
                  isBatteryExempted = bgManager.isBatteryOptimizationExempted()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = if (isBatteryExempted) "စစ်ဆေးပြီး" else "ချွင်းချက်ပေးရန်",
                  fontSize = 9.sp,
                  color = if (isBatteryExempted) EmeraldLight else AmberAccent
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OEM Autostart Card
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCard)
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                .padding(10.dp)
            ) {
              Column {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "OEM Autostart: ${bgManager.getOemName()}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldLight
                  )
                  OutlinedButton(
                    onClick = { bgManager.openOemAutoStartSettings() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text("စက်ဆက်တင်ဖွင့်ရန်", fontSize = 9.sp, color = EmeraldLight)
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                bgManager.getOemGuidanceBurmese().forEach { step ->
                  Text(
                    text = "• $step",
                    fontSize = 9.sp,
                    color = TextSecondary,
                    lineHeight = 13.sp
                  )
                }
              }
            }
          }

          3 -> {
            // EcoFlow Hardware API
            Text(text = "EcoFlow Open API Credentials (Cloud REST)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AmberAccent)
            Text(text = "developer.ecoflow.com မှ ရယူထားသော Open API Key များကို ထည့်သွင်းပါ", fontSize = 10.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(10.dp))

            // Region Selection
            Text(text = "API Region / Server", fontSize = 11.sp, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              val isAsia = ecoflowHost.contains("api-a.ecoflow.com")
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isAsia) Color(0x3310B981) else SurfaceCard)
                  .border(1.dp, if (isAsia) EmeraldPrimary else SurfaceCardBorder, RoundedCornerShape(8.dp))
                  .clickable { ecoflowHost = "https://api-a.ecoflow.com" }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Asia & Americas\n(api-a.ecoflow.com)",
                  fontSize = 10.sp,
                  color = if (isAsia) EmeraldPrimary else TextMuted,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
              }

              val isEurope = ecoflowHost.contains("api-e.ecoflow.com")
              Box(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isEurope) Color(0x3310B981) else SurfaceCard)
                  .border(1.dp, if (isEurope) EmeraldPrimary else SurfaceCardBorder, RoundedCornerShape(8.dp))
                  .clickable { ecoflowHost = "https://api-e.ecoflow.com" }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "Europe Region\n(api-e.ecoflow.com)",
                  fontSize = 10.sp,
                  color = if (isEurope) EmeraldPrimary else TextMuted,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = ecoflowAccessKey,
              onValueChange = { ecoflowAccessKey = it },
              label = { Text("Access Key", fontSize = 11.sp) },
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmberAccent,
                unfocusedBorderColor = SurfaceCardBorder
              ),
              shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = ecoflowSecretKey,
              onValueChange = { ecoflowSecretKey = it },
              label = { Text("Secret Key", fontSize = 11.sp) },
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmberAccent,
                unfocusedBorderColor = SurfaceCardBorder
              ),
              shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = ecoflowDeviceSn,
              onValueChange = { ecoflowDeviceSn = it },
              label = { Text("Device Serial Number (SN)", fontSize = 11.sp) },
              modifier = Modifier.fillMaxWidth(),
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AmberAccent,
                unfocusedBorderColor = SurfaceCardBorder
              ),
              shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
              onClick = {
                onSaveEcoFlowConfig(ecoflowAccessKey, ecoflowSecretKey, ecoflowDeviceSn, ecoflowHost)
                onDismiss()
              },
              modifier = Modifier.fillMaxWidth(),
              colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("Save API Credentials", fontWeight = FontWeight.Bold, color = Color.Black)
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingToggleRow(
  title: String,
  sub: String,
  isChecked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Text(text = sub, fontSize = 10.sp, color = TextMuted)
    }

    Switch(
      checked = isChecked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = EmeraldPrimary,
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color(0xFF1E293B)
      )
    )
  }
}

@Composable
fun PermissionStatusRow(
  title: String,
  sub: String,
  isGranted: Boolean,
  onRequest: (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(SurfaceCard)
      .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
      .padding(10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
      Text(text = sub, fontSize = 10.sp, color = TextMuted)
    }

    if (isGranted) {
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0x3310B981))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = EmeraldPrimary,
            modifier = Modifier.size(11.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "GRANTED",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = EmeraldPrimary
          )
        }
      }
    } else {
      if (onRequest != null) {
        OutlinedButton(
          onClick = onRequest,
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(text = "ခွင့်ပြုမည်", fontSize = 9.sp, color = AmberAccent)
        }
      } else {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x33EF4444))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Close,
              contentDescription = null,
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "DENIED",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEF4444)
            )
          }
        }
      }
    }
  }
}
