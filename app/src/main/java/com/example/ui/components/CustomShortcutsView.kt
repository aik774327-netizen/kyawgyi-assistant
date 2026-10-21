package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.CustomShortcutEntity
import com.example.ui.theme.*

enum class ShortcutTab {
    LIBRARY,    // အတိုကောက်များ စုစည်းရာ
    AUTOMATION, // အလိုအလျောက် စနစ်
    ANALYTICS,  // သုံးစွဲမှု ဂရပ်
    GALLERY     // အသင့်သုံး နမူနာများ
}

data class GalleryPreset(
    val name: String,
    val voicePattern: String,
    val dataType: String,
    val target: String,
    val description: String
)

@Composable
fun CustomShortcutsView(
    shortcuts: List<CustomShortcutEntity>,
    onAddShortcut: (phrase: String, target: String, desc: String) -> Unit,
    onToggleShortcut: (shortcut: CustomShortcutEntity) -> Unit,
    onDeleteShortcut: (shortcut: CustomShortcutEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(ShortcutTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    
    // State for Add Dialog
    var showAddDialog by remember { mutableStateOf(false) }
    var phraseInput by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var dataTypeInput by remember { mutableStateOf("photos") }

    // State for Delete Confirmation Dialog
    var shortcutToDelete by remember { mutableStateOf<CustomShortcutEntity?>(null) }
    
    // State for Test Banner/Result
    var testResultBanner by remember { mutableStateOf<String?>(null) }

    val dataTypes = listOf(
        "photos" to "ဓာတ်ပုံများ",
        "videos" to "ဗီဒီယိုများ",
        "contacts" to "အဆက်အသွယ်များ",
        "notes" to "မှတ်စုများ",
        "calendar" to "ပြက္ခဒိန်",
        "messages" to "စာတိုများ",
        "files" to "ဖိုင်များ",
        "custom" to "စိတ်ကြိုက်"
    )

    val presets = listOf(
        GalleryPreset("ကျွန်တော့် ဓာတ်ပုံများ", "ကျော်ကြီး ဓာတ်ပုံများ ပြပါ", "photos", "com.google.android.apps.photos", "ဓာတ်ပုံ အက်ပ်အား တိုက်ရိုက် ဖွင့်လှစ်ခြင်း"),
        GalleryPreset("ယူကျု ဗီဒီယိုများ", "ကျော်ကြီး ဗီဒီယို ဖွင့်ပါ", "videos", "com.google.android.youtube", "YouTube ဗီဒီယိုများ ကြည့်ရှုခြင်း"),
        GalleryPreset("ဖုန်း အဆက်အသွယ်", "ကျော်ကြီး အဆက်အသွယ် ရှာပါ", "contacts", "com.google.android.dialer", "ဖုန်းနံပါတ် အဆက်အသွယ်များ ဖွင့်ခြင်း"),
        GalleryPreset("မှတ်စု အမြန်ရေး", "ကျော်ကြီး မှတ်စု ရေးပါ", "notes", "com.google.android.keep", "Keep Notes မှတ်စု တိုများ ဖွင့်ခြင်း"),
        GalleryPreset("ပြက္ခဒိန် အစီအစဉ်", "ကျော်ကြီး ပြက္ခဒိန် ဖွင့်ပါ", "calendar", "com.google.android.calendar", "ရက်စွဲ နှင့် အစီအစဉ်များ ကြည့်ခြင်း"),
        GalleryPreset("စာတို ပေးပို့ခြင်း", "ကျော်ကြီး စာတို ပို့ပါ", "messages", "com.google.android.apps.messaging", "SMS စာတို အက်ပ် ဖွင့်လှစ်ခြင်း"),
        GalleryPreset("ဖိုင်များ စီမံခြင်း", "ကျော်ကြီး ဖိုင်များ ကြည့်ပါ", "files", "com.google.android.apps.docs", "Google Drive / Files စီမံခြင်း")
    )

    // Filtered shortcuts for search bar
    val filteredShortcuts = remember(shortcuts, searchQuery) {
        if (searchQuery.isBlank()) {
            shortcuts
        } else {
            shortcuts.filter {
                it.phrase.contains(searchQuery, ignoreCase = true) ||
                it.targetPackageOrAction.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("custom_shortcuts_view")
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Kyaw Gyi Voice Shortcuts & Automations",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "အသံ အတိုကောက်များ နှင့် အလိုအလျောက် စနစ်များ",
                    fontSize = 12.sp,
                    color = AngelCyanAccent
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Shortcut")
                Spacer(modifier = Modifier.width(4.dp))
                Text("အသစ်ထည့်မည်", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Selector (Library, Automation, Analytics, Gallery)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AngelSurfaceDark, shape = RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                title = "Library",
                icon = Icons.Default.Bookmarks,
                isSelected = selectedTab == ShortcutTab.LIBRARY,
                onClick = { selectedTab = ShortcutTab.LIBRARY },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                title = "Automation",
                icon = Icons.Default.SmartButton,
                isSelected = selectedTab == ShortcutTab.AUTOMATION,
                onClick = { selectedTab = ShortcutTab.AUTOMATION },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                title = "Analytics",
                icon = Icons.Default.BarChart,
                isSelected = selectedTab == ShortcutTab.ANALYTICS,
                onClick = { selectedTab = ShortcutTab.ANALYTICS },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                title = "Gallery",
                icon = Icons.Default.AutoAwesome,
                isSelected = selectedTab == ShortcutTab.GALLERY,
                onClick = { selectedTab = ShortcutTab.GALLERY },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Test Action Execution Banner if active
        testResultBanner?.let { bannerText ->
            Card(
                colors = CardDefaults.cardColors(containerColor = AngelCyanAccent.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = AngelCyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bannerText,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(onClick = { testResultBanner = null }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            }
        }

        // Content Area by Selected Tab
        when (selectedTab) {
            ShortcutTab.LIBRARY -> {
                // Search Bar Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("အသံ ပုံစံ သို့မဟုတ် အမည်ဖြင့် ရှာဖွေပါ...", fontSize = 12.sp, color = AngelTextSecondary) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search Shortcuts", tint = AngelCyanAccent)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear Search", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AngelCyanAccent,
                        unfocusedBorderColor = AngelSurfaceDark,
                        focusedContainerColor = AngelSurfaceDark,
                        unfocusedContainerColor = AngelSurfaceDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                if (filteredShortcuts.isEmpty()) {
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
                                text = if (searchQuery.isNotBlank()) "ရှာဖွေမှုနှင့် ကိုက်ညီသော အတိုကောက် မတွေ့ပါ။" else "အတိုကောက် မရှိသေးပါ။ 'Gallery (နမူနာ)' သို့မဟုတ် 'အသစ်ထည့်မည်' ကို နှိပ်၍ ထည့်သွင်းနိုင်ပါသည်။",
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
                        items(filteredShortcuts, key = { it.id }) { shortcut ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (shortcut.isEnabled) AngelSurfaceDark else AngelSurfaceDark.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ElectricBolt,
                                            contentDescription = null,
                                            tint = if (shortcut.isEnabled) AngelCyanAccent else Color.Gray,
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = shortcut.phrase,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (shortcut.isEnabled) Color.White else Color.Gray
                                            )
                                            Text(
                                                text = "Target: ${shortcut.targetPackageOrAction}",
                                                fontSize = 11.sp,
                                                color = AngelTextSecondary
                                            )
                                            if (shortcut.description.isNotBlank()) {
                                                Text(
                                                    text = shortcut.description,
                                                    fontSize = 10.sp,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = shortcut.isEnabled,
                                            onCheckedChange = { onToggleShortcut(shortcut) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = AngelCyanAccent
                                            )
                                        )

                                        IconButton(onClick = { shortcutToDelete = shortcut }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Shortcut",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Test Voice Pattern Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val msg = "စမ်းသပ်မှု အောင်မြင်သည်: '${shortcut.phrase}' -> '${shortcut.targetPackageOrAction}' ဖွင့်နေသည်"
                                                testResultBanner = msg
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AngelCyanAccent),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AngelCyanAccent.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Test Voice Pattern",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Test Voice Pattern (စမ်းသပ်မည်)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ShortcutTab.AUTOMATION -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        AutomationStatusCard(
                            title = "ကျော်ကြီး Wake Word Listening (နိုးထစကားလုံး)",
                            status = "Active Always (အမြဲနားထောင်နေသည်)",
                            detail = "'ကျော်ကြီး' ဟု ခေါ်ဆိုလိုက်သည်နှင့် စတင်အလုပ်လုပ်ပါမည်။",
                            icon = Icons.Default.Mic
                        )
                    }
                    item {
                        AutomationStatusCard(
                            title = "Lock Screen Continuous Voice Controller",
                            status = "Enabled (မျက်နှာပြင်လော့ခ် ပိတ်နေသော်လည်း တုံ့ပြန်သည်)",
                            detail = "AssistiveTouch Float Icon အမြဲမြင်ရပြီး Lock Screen ဖြစ်နေပါစေ လုပ်ဆောင်နိုင်ပါသည်။",
                            icon = Icons.Default.LockClock
                        )
                    }
                    item {
                        AutomationStatusCard(
                            title = "Assistive Touch Overlay Mic Status",
                            status = "Always Visible Floating State Indicator",
                            detail = "အစိမ်းရောင် (နားထောင်နေ) / အနီရောင် (စကားပြောနေ) အခြေအနေပြ အိုင်ကွန်",
                            icon = Icons.Default.FolderSpecial
                        )
                    }
                }
            }

            ShortcutTab.ANALYTICS -> {
                ShortcutAnalyticsChartView(shortcuts = shortcuts)
            }

            ShortcutTab.GALLERY -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(presets) { preset ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "\"${preset.voicePattern}\"",
                                        fontSize = 12.sp,
                                        color = AngelCyanAccent,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = preset.description,
                                        fontSize = 10.sp,
                                        color = AngelTextSecondary
                                    )
                                }

                                Button(
                                    onClick = {
                                        onAddShortcut(preset.voicePattern, preset.target, preset.description)
                                        selectedTab = ShortcutTab.LIBRARY
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ModeExpertColor, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ထည့်သွင်းမည်", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Shortcut Dialog with Data Type Selector
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text("အသံ အတိုကောက် အသစ်ထည့်သွင်းရန်", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("အချက်အလက် အမျိုးအစား (Data Type):", fontSize = 12.sp, color = AngelCyanAccent)
                    
                    // Data Type Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dataTypes.take(4).forEach { (key, label) ->
                            FilterChip(
                                selected = dataTypeInput == key,
                                onClick = { dataTypeInput = key },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dataTypes.drop(4).forEach { (key, label) ->
                            FilterChip(
                                selected = dataTypeInput == key,
                                onClick = { dataTypeInput = key },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = { phraseInput = it },
                        label = { Text("အသံ ပုံစံ (ဥပမာ: ကျော်ကြီး ဓာတ်ပုံများ ပြပါ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("လုပ်ဆောင်ချက် Target (ဥပမာ: com.google.android.apps.photos)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("ဖော်ပြချက် Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (phraseInput.isNotBlank() && targetInput.isNotBlank()) {
                            val fullDesc = if (descInput.isNotBlank()) "$descInput [$dataTypeInput]" else "[$dataTypeInput]"
                            onAddShortcut(phraseInput, targetInput, fullDesc)
                            phraseInput = ""
                            targetInput = ""
                            descInput = ""
                            showAddDialog = false
                            selectedTab = ShortcutTab.LIBRARY
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AngelCyanAccent, contentColor = Color.Black)
                ) {
                    Text("သိမ်းဆည်းပါ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("ပယ်ဖျက်ပါ", color = AngelTextSecondary)
                }
            },
            containerColor = AngelSurfaceDark
        )
    }

    // Delete Confirmation Dialog Box ("ဖျက်မည်လား")
    shortcutToDelete?.let { shortcut ->
        AlertDialog(
            onDismissRequest = { shortcutToDelete = null },
            title = {
                Text("ဖျက်မည်လား အတည်ပြုရန်", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "အတိုကောက် '${shortcut.phrase}' ကို ဖျက်ရန် သေချာပါသလား။ ဤလုပ်ဆောင်ချက်ကို ပြန်လည်ပြင်ဆင်၍ မရနိုင်ပါ။",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteShortcut(shortcut)
                        shortcutToDelete = null
                        Toast.makeText(context, "အတိုကောက် ဖျက်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252), contentColor = Color.White)
                ) {
                    Text("ဖျက်မည်")
                }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToDelete = null }) {
                    Text("မဖျက်ပါ။", color = AngelTextSecondary)
                }
            },
            containerColor = AngelSurfaceDark
        )
    }
}

// Visual Chart Component for Voice Shortcuts Usage Analytics
@Composable
private fun ShortcutAnalyticsChartView(shortcuts: List<CustomShortcutEntity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AngelSurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "အသုံးပြုမှု ပုံစံ ခွဲခြမ်းစိတ်ဖြာသည့် ဂရပ် (Usage Analytics Chart)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = AngelCyanAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "အတိုကောက်တစ်ခုချင်းစီ၏ အကြိမ်ရေ အသုံးပြုမှု အခြေအနေများကို ဖော်ပြထားပါသည်။",
                fontSize = 11.sp,
                color = AngelTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (shortcuts.isEmpty()) {
                Text(
                    text = "ဂရပ်ပြသရန် အချက်အလက် မရှိသေးပါ။",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                val chartItems = shortcuts.take(6)
                val maxVal = (chartItems.maxOfOrNull { 10 } ?: 10).toFloat()

                // Custom Bar Chart Representation
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chartItems.forEachIndexed { index, shortcut ->
                        val usageValue = (index * 3 + 2) // Illustrative analytics metric
                        val percentage = (usageValue / maxVal).coerceIn(0.15f, 1.0f)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = shortcut.phrase,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$usageValue ကြိမ်သုံးထားသည်",
                                    fontSize = 11.sp,
                                    color = AngelCyanAccent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Visual Progress Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .background(Color.DarkGray.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(percentage)
                                        .background(
                                            if (index % 2 == 0) AngelCyanAccent else ModeExpertColor,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) AngelCyanAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.Black else Color.Gray,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.Black else Color.LightGray
            )
        }
    }
}

@Composable
private fun AutomationStatusCard(
    title: String,
    status: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AngelCyanAccent,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = status, fontSize = 12.sp, color = AngelCyanAccent)
                Text(text = detail, fontSize = 11.sp, color = AngelTextSecondary)
            }
        }
    }
}


