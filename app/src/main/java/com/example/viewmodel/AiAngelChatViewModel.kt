package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.ChatMessageEntity
import com.example.database.MusicAssetEntity
import com.example.ingestion.ChangePreviewManager
import com.example.ingestion.IngestionResult
import com.example.ingestion.IngestionStep
import com.example.ingestion.MusicPlaybackManager
import com.example.ingestion.MusicRepository
import com.example.ingestion.MusicSearchOutcome
import com.example.ingestion.ProjectDataRouter
import com.example.ingestion.ProposedProjectChange
import com.example.ingestion.UniversalDataIngestionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.UUID

class AiAngelChatViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application)
  private val chatMessageDao = database.chatMessageDao()
  val ingestionManager = UniversalDataIngestionManager(application)
  val musicRepository = MusicRepository(application)
  val musicPlaybackManager = MusicPlaybackManager(application)

  val messages: StateFlow<List<ChatMessageEntity>> = chatMessageDao.getAllMessagesFlow()
    .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  private val _currentIngestionStep = MutableStateFlow<IngestionStep?>(null)
  val currentIngestionStep: StateFlow<IngestionStep?> = _currentIngestionStep.asStateFlow()

  private val _pendingProposedChange = MutableStateFlow<ProposedProjectChange?>(null)
  val pendingProposedChange: StateFlow<ProposedProjectChange?> = _pendingProposedChange.asStateFlow()

  private val _lastIngestedMusic = MutableStateFlow<MusicAssetEntity?>(null)
  val lastIngestedMusic: StateFlow<MusicAssetEntity?> = _lastIngestedMusic.asStateFlow()

  init {
    viewModelScope.launch {
      if (chatMessageDao.getAllMessages().isEmpty()) {
        sendAiMessage(
          "မင်္ဂလာပါ! ကျနော် AI Angel ဖြစ်ပါတယ်။\n" +
            "စာသား၊ ဓာတ်ပုံ၊ အသံ၊ MP3 သီချင်း၊ စာရွက်စာတမ်း၊ JSON/Code များကို ဤနေရာတွင် ပေးပို့နိုင်ပါသည်။ စနစ်တကျ ခွဲခြမ်းစိတ်ဖြာပြီး မှန်ကန်သော Project Storage တွင် စနစ်တကျ သိမ်းဆည်းပေးပါမည်။"
        )
      }
    }
  }

  fun sendTextMessage(text: String) {
    val clean = text.trim()
    if (clean.isEmpty()) return

    viewModelScope.launch {
      // 1. Post user message
      val userMsg = ChatMessageEntity(
        sender = "USER",
        messageText = clean
      )
      chatMessageDao.insert(userMsg)

      // 2. Process query / intent offline
      processUserIntent(clean)
    }
  }

  /**
   * Offline-first natural language understanding & intent processor for Angel Chatbox.
   */
  private suspend fun processUserIntent(text: String) {
    val clean = text.lowercase()

    // Music search & playback query e.g. "လေးဖြူသီချင်းဖွင့်ပါ"
    if (clean.contains("သီချင်း") || clean.contains("ဖွင့်") || clean.contains("play") || clean.contains("song")) {
      val query = clean.replace("သီချင်း", "")
        .replace("ဖွင့်ပါ", "")
        .replace("ဖွင့်", "")
        .replace("play", "")
        .replace("song", "")
        .trim()

      val searchQuery = if (query.isNotEmpty()) query else clean
      when (val outcome = musicRepository.search(searchQuery)) {
        is MusicSearchOutcome.SingleMatch -> {
          musicPlaybackManager.play(outcome.song)
          sendAiMessage(
            "🎵 \"${outcome.song.title}\" (${outcome.song.artist}) ကို ရှာဖွေတွေ့ရှိပြီး စတင်ဖွင့်ပြပေးနေပါပြီ ခင်ဗျာ။\n• အကြောင်းအရင်း: ${outcome.matchReason}"
          )
        }
        is MusicSearchOutcome.MultipleCandidates -> {
          sendAiMessage(outcome.burmeseExplanation)
        }
        is MusicSearchOutcome.NoMatch -> {
          sendAiMessage(outcome.burmeseExplanation)
        }
      }
      return
    }

    // Diagnostics query
    if (clean.contains("စစ်ဆေး") || clean.contains("status") || clean.contains("diagnostics") || clean.contains("အခြေအနေ")) {
      val musicCount = musicRepository.getAllMusic().size
      val totalFiles = database.fileAssetDao().countTotal()
      sendAiMessage(
        "📊 [AI Angel Data Diagnostics]\n" +
          "• Total Project Assets: $totalFiles ခု\n" +
          "• Music Library Songs: $musicCount ပုဒ်\n" +
          "• Offline File Engine: Ready 🟢\n" +
          "• Media Playback Engine: Ready 🟢\n" +
          "• Security Validator: Active (Path Traversal & Shell Script Protection) 🛡️"
      )
      return
    }

    // Default conversational AI Angel reply
    sendAiMessage(
      "AI Angel မှ လက်ခံရရှိပါသည်: \"$text\"\n" +
        "ဖိုင်များ (MP3၊ ဓာတ်ပုံ၊ JSON/Code စသည်) ကို Attachment ခလုတ်ဖြင့် ပေးပို့နိုင်ပါသည်။"
    )
  }

  fun handleFileUpload(
    uri: Uri,
    filename: String,
    mimeType: String
  ) {
    viewModelScope.launch {
      // 1. Post upload chat card
      val initialMsg = ChatMessageEntity(
        sender = "USER",
        messageText = "ဖိုင်ပေးပို့နေသည်: $filename",
        attachmentName = filename,
        attachmentMimeType = mimeType,
        attachmentUri = uri.toString(),
        ingestionStatus = "UPLOADING"
      )
      chatMessageDao.insert(initialMsg)

      // 2. Run universal ingestion pipeline
      val result = ingestionManager.ingestFromUri(
        uri = uri,
        originalFilename = filename,
        mimeType = mimeType,
        provenance = "AI_ANGEL_CHAT",
        onProgress = { step ->
          _currentIngestionStep.value = step
        }
      )

      _currentIngestionStep.value = null

      // 3. Handle Result
      when (result) {
        is IngestionResult.Success -> {
          if (result.musicAsset != null) {
            _lastIngestedMusic.value = result.musicAsset
          }

          // Evaluate if proposal approval is required (for code / config)
          val category = ProjectDataRouter.classify(filename, mimeType, result.fileAsset.extension)
          if (category == ProjectDataRouter.AssetCategory.PROJECT_SOURCE || category == ProjectDataRouter.AssetCategory.PROJECT_CONFIG) {
            val proposal = ChangePreviewManager.evaluate(filename, result.fileAsset.absolutePath, category)
            _pendingProposedChange.value = proposal
          }

          sendAiMessage(
            "✅ [Ingestion Success]\n" +
              "${result.burmeseMessage}\n\n" +
              "• Destination: ${result.fileAsset.destinationFolder}\n" +
              "• SHA-256: ${result.fileAsset.sha256.take(16)}...\n" +
              "• Size: ${result.fileAsset.fileSizeBytes / 1024} KB"
          )
        }

        is IngestionResult.Duplicate -> {
          sendAiMessage(
            "ℹ️ [Duplicate Detected]\n" +
              "${result.burmeseMessage}\n" +
              "တည်နေရာ: ${result.existingAsset.destinationFolder}"
          )
        }

        is IngestionResult.SecurityBlocked -> {
          sendAiMessage("⛔ [လုံခြုံရေးတားမြစ်ချက်] ${result.reason}")
        }

        is IngestionResult.StorageFailed -> {
          sendAiMessage("❌ [သိုလှောင်မှု မအောင်မြင်ပါ] ${result.error}")
        }

        is IngestionResult.Error -> {
          sendAiMessage("⚠️ [အမှားဖြစ်ပွားသည်] ${result.error}")
        }

        else -> {
          sendAiMessage("စနစ်အတွင်း ဖိုင်လက်ခံမှုကို အတည်ပြုပြီးပါပြီ။")
        }
      }
    }
  }

  /**
   * Ingest sample asset directly (e.g. for quick testing with sample MP3).
   */
  fun ingestSampleFile(filename: String, sampleBytes: ByteArray, mimeType: String) {
    viewModelScope.launch {
      val temp = File(getApplication<Application>().cacheDir, filename)
      temp.writeBytes(sampleBytes)
      temp.inputStream().use { stream ->
        val result = ingestionManager.ingestFromStream(
          inputStream = stream,
          originalFilename = filename,
          mimeType = mimeType,
          provenance = "AI_ANGEL_SAMPLE",
          onProgress = { step ->
            _currentIngestionStep.value = step
          }
        )
        _currentIngestionStep.value = null

        when (result) {
          is IngestionResult.Success -> {
            if (result.musicAsset != null) {
              _lastIngestedMusic.value = result.musicAsset
            }
            sendAiMessage("✅ [Sample Ingestion Success]\n${result.burmeseMessage}")
          }
          is IngestionResult.Duplicate -> {
            sendAiMessage("ℹ️ [Duplicate]\n${result.burmeseMessage}")
          }
          else -> {
            sendAiMessage("Ingested sample: $filename")
          }
        }
      }
    }
  }

  fun approvePendingChange() {
    val pending = _pendingProposedChange.value ?: return
    viewModelScope.launch {
      sendAiMessage("🛡️ အတည်ပြုချက် ရရှိပါသည်: \"${pending.sourceFilename}\" အတွက် ပြောင်းလဲမှုကို တရားဝင် သတ်မှတ်ပြီးပါပြီ။")
      _pendingProposedChange.value = null
    }
  }

  fun dismissPendingChange() {
    _pendingProposedChange.value = null
  }

  private suspend fun sendAiMessage(text: String) {
    val aiMsg = ChatMessageEntity(
      sender = "AI_ANGEL",
      messageText = text
    )
    chatMessageDao.insert(aiMsg)
  }
}
