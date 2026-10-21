package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.example.ingestion.IngestedAssetEntity
import com.example.ui.AngelViewModel
import com.example.ui.CoreEngine
import com.example.ui.ResponseMode
import com.example.ui.theme.*

/**
 * AI Angel Chatbox & Universal Asset Manager Full Screen Experience
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAngelChatboxScreen(
    viewModel: AngelViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val ingestedAssets by viewModel.ingestedAssets.collectAsState()
    val selectedEngine by viewModel.selectedEngine.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val modelPreset by viewModel.modelPreset.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Chat Conversation, 1: Ingested Asset Vault & Search
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    var currentRecoveryEvent by remember { mutableStateOf<com.example.utils.SpeechRecoveryEvent?>(null) }
    LaunchedEffect(Unit) {
        com.example.utils.MyanmarTTSVoicePackManager.getInstance(context).speechRecoveryEvents.collect { event ->
            currentRecoveryEvent = event
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported by provider
            }
            viewModel.ingestUserFile(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AngelNeonGreen)
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI Angel Chatbox",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = AngelTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AngelNeonGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "V2.5",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AngelNeonGreen,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Universal Data Ingestion & Project Router",
                            fontSize = 11.sp,
                            color = AngelTextSecondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("upload_vault_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = "Ingest File",
                            tint = AngelCyanAccent
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.testTag("clear_chat_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = AngelTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AngelDarkBackground
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AngelDarkBackground)
                .padding(innerPadding)
        ) {
            // Core Engine Navigation Bar
            CoreEngineBar(
                selectedEngine = selectedEngine,
                onEngineSelected = { viewModel.setEngine(it) }
            )

            // Myanmar TTS Speech Recovery Alert Banner
            AnimatedVisibility(visible = currentRecoveryEvent != null) {
                currentRecoveryEvent?.let { recovery ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "စကားသံ မထွက်နိုင်ပါ (အသံဒေတာ လိုအပ်ပါသည်)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { currentRecoveryEvent = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = recovery.reasonMm,
                                color = AngelTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            if (recovery.canDownloadVoicePack) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            com.example.utils.MyanmarTTSVoicePackManager.getInstance(context).launchVoicePackDownload(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Voice Pack ဒေါင်းလုဒ်ဆွဲမည်",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Sub Tab Bar (Chat vs Asset Vault)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { activeSubTab = 0 },
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeSubTab == 0) AngelNeonGreen.copy(alpha = 0.25f) else AngelSurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeSubTab == 0) AngelNeonGreen else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = if (activeSubTab == 0) AngelNeonGreen else AngelTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "စကားပြောခန်း (${messages.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSubTab == 0) AngelNeonGreen else AngelTextSecondary
                        )
                    }
                }

                Surface(
                    onClick = { activeSubTab = 1 },
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeSubTab == 1) AngelCyanAccent.copy(alpha = 0.25f) else AngelSurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeSubTab == 1) AngelCyanAccent else Color.White.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = if (activeSubTab == 1) AngelCyanAccent else AngelTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ဖိုင်တိုက် / Asset Vault (${ingestedAssets.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSubTab == 1) AngelCyanAccent else AngelTextSecondary
                        )
                    }
                }
            }

            if (activeSubTab == 0) {
                // CHAT CONVERSATION TAB
                Box(modifier = Modifier.weight(1f)) {
                    if (messages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "AI Angel နှင့် စကားပြောဆိုမှု မရှိသေးပါခင်ဗျာ",
                                color = AngelTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            reverseLayout = true
                        ) {
                            items(messages.reversed(), key = { it.id }) { msg ->
                                ChatMessageCard(
                                    message = msg,
                                    onSpeak = { viewModel.speakText(it) },
                                    onOpenApiKeySettings = onNavigateToSettings
                                )
                            }
                        }
                    }
                }

                // Response Mode Chips & Input Bar
                ResponseModeChips(
                    selectedMode = selectedMode,
                    onModeSelected = { viewModel.setResponseMode(it) },
                    selectedModelPreset = modelPreset,
                    onModelPresetSelected = { viewModel.setModelPreset(it) },
                    onSendMessage = { text, uri -> viewModel.sendMessage(text, uri) },
                    isGenerating = isGenerating
                )
            } else {
                // ASSET VAULT & LOCAL SEARCH INDEX TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ဖိုင်အမည် သို့မဟုတ် Keyword ဖြင့် ရှာဖွေပါ...", fontSize = 12.sp, color = AngelTextSecondary) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AngelCyanAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = AngelTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AngelSurfaceDark,
                            unfocusedContainerColor = AngelSurfaceDark,
                            focusedBorderColor = AngelCyanAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = AngelTextPrimary,
                            unfocusedTextColor = AngelTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )

                    // Category filters
                    val categories = listOf("ALL", "IMAGE", "AUDIO", "VIDEO", "DOCUMENT", "CODE_DATA")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = cat == selectedCategoryFilter
                            Surface(
                                onClick = { selectedCategoryFilter = cat },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) AngelCyanAccent.copy(alpha = 0.25f) else AngelSurfaceDark,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AngelCyanAccent else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AngelCyanAccent else AngelTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    val filteredAssets = remember(ingestedAssets, searchQuery, selectedCategoryFilter) {
                        ingestedAssets.filter { asset ->
                            val matchesCategory = selectedCategoryFilter == "ALL" || asset.category.equals(selectedCategoryFilter, ignoreCase = true)
                            val matchesQuery = searchQuery.isBlank() ||
                                    asset.fileName.contains(searchQuery, ignoreCase = true) ||
                                    asset.indexedKeywords.contains(searchQuery, ignoreCase = true) ||
                                    (asset.extractedSummary ?: "").contains(searchQuery, ignoreCase = true)
                            matchesCategory && matchesQuery
                        }
                    }

                    if (filteredAssets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.FolderSpecial, contentDescription = null, tint = AngelTextSecondary, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "ရှာဖွေမှုနှင့် ကိုက်ညီသော ဖိုင်မရှိပါ",
                                    color = AngelTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filteredAssets, key = { it.id }) { asset ->
                                IngestedAssetCard(
                                    asset = asset,
                                    onDelete = { viewModel.deleteIngestedAsset(asset) },
                                    onOpen = {
                                        try {
                                            val parsedUri = Uri.parse(asset.fileUri)
                                            val targetUri = if (parsedUri.scheme == "content") {
                                                parsedUri
                                            } else {
                                                val localFile = java.io.File(asset.storedRelativePath)
                                                if (localFile.exists()) {
                                                    androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        localFile
                                                    )
                                                } else {
                                                    parsedUri
                                                }
                                            }
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(targetUri, asset.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "ဖိုင်ဖွင့်ရန် အဆင်မပြေပါခင်ဗျာ", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
fun IngestedAssetCard(
    asset: IngestedAssetEntity,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AngelCardDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = when (asset.category) {
                        "IMAGE" -> Icons.Filled.Image
                        "AUDIO" -> Icons.Filled.MusicNote
                        "VIDEO" -> Icons.Filled.VideoLibrary
                        "DOCUMENT" -> Icons.Filled.Description
                        "CODE_DATA" -> Icons.Filled.Code
                        else -> Icons.AutoMirrored.Filled.InsertDriveFile
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AngelCyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = AngelCyanAccent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = asset.fileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AngelTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${asset.category} • ${asset.sizeBytes / 1024} KB • SHA: ${asset.sha256Hash.take(8)}",
                            fontSize = 10.sp,
                            color = AngelTextSecondary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", tint = AngelCyanAccent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!asset.extractedSummary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = asset.extractedSummary,
                    fontSize = 11.sp,
                    color = AngelTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
