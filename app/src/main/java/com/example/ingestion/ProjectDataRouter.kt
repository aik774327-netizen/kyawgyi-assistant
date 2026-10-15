package com.example.ingestion

import android.content.Context
import java.io.File

object ProjectDataRouter {

  enum class AssetCategory(val folderName: String, val displayNameBurmese: String, val displayNameEnglish: String) {
    MUSIC("music", "တေးဂီတ (Music)", "Music"),
    AUDIO("audio", "အသံဖိုင် (Audio)", "Audio"),
    IMAGE("images", "ဓာတ်ပုံ/ရုပ်ပုံ (Images)", "Images"),
    VIDEO("video", "ဗီဒီယို (Video)", "Video"),
    DOCUMENT("documents", "စာရွက်စာတမ်း (Documents)", "Documents"),
    PROJECT_CONFIG("configurations", "ပရောဂျက် ဆက်တင် (Configs)", "Project Configurations"),
    PROJECT_SOURCE("source_patches", "ဆော့ဖ်ဝဲလ်ကုဒ် (Source Patches)", "Source Patches"),
    QUARANTINE("quarantine", "လုံခြုံရေးထိန်းသိမ်းခန်း (Quarantine)", "Quarantine")
  }

  data class RouteDecision(
    val category: AssetCategory,
    val targetDirectory: File,
    val relativePath: String,
    val requiresApproval: Boolean,
    val approvalNotice: String? = null
  )

  /**
   * Resolves target destination directory and approval requirement for uploaded file.
   */
  fun route(
    context: Context,
    filename: String,
    mimeType: String,
    extension: String
  ): RouteDecision {
    val category = classify(filename, mimeType, extension)
    val baseDir = File(context.filesDir, "kyaw_gyi_storage/${category.folderName}")
    if (!baseDir.exists()) {
      baseDir.mkdirs()
    }

    val requiresApproval = category == AssetCategory.PROJECT_SOURCE || category == AssetCategory.PROJECT_CONFIG
    val approvalNotice = if (requiresApproval) {
      if (category == AssetCategory.PROJECT_SOURCE) {
        "ဤ Kotlin/Java source code သည် ပရောဂျက်တည်ဆောက်မှုအပေါ် သက်ရောက်နိုင်သဖြင့် လုံခြုံစွာ စစ်ဆေးအတည်ပြုရန် လိုအပ်ပါသည်။ (Never automatically overwrites live code)"
      } else {
        "ဤ Configuration ဖိုင်သည် စနစ်ဆက်တင်များကို ပြောင်းလဲနိုင်သဖြင့် သုံးစွဲသူ အတည်ပြုချက် လိုအပ်ပါသည်။"
      }
    } else null

    return RouteDecision(
      category = category,
      targetDirectory = baseDir,
      relativePath = "kyaw_gyi_storage/${category.folderName}/$filename",
      requiresApproval = requiresApproval,
      approvalNotice = approvalNotice
    )
  }

  fun classify(filename: String, mimeType: String, extension: String): AssetCategory {
    val ext = extension.lowercase()
    val mime = mimeType.lowercase()

    // 1. Music Audio
    if (ext in listOf("mp3", "flac", "ogg") || mime == "audio/mpeg" || mime == "audio/mp3" || mime == "audio/flac") {
      return AssetCategory.MUSIC
    }

    // 2. General Audio / Voice
    if (ext in listOf("wav", "m4a", "aac", "opus", "amr", "wma") || mime.startsWith("audio/")) {
      // If filename has keywords like 'song', 'သီချင်း', treat as MUSIC
      if (filename.contains("song", ignoreCase = true) || filename.contains("သီချင်း")) {
        return AssetCategory.MUSIC
      }
      return AssetCategory.AUDIO
    }

    // 3. Images
    if (ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp") || mime.startsWith("image/")) {
      return AssetCategory.IMAGE
    }

    // 4. Video
    if (ext in listOf("mp4", "mkv", "webm", "avi", "mov") || mime.startsWith("video/")) {
      return AssetCategory.VIDEO
    }

    // 5. Project Source Code (STRICT: staged in source_patches, never live overwritten)
    if (ext in listOf("kt", "java", "gradle", "kts", "c", "cpp")) {
      return AssetCategory.PROJECT_SOURCE
    }

    // 6. Project Configurations
    if (ext in listOf("json", "xml", "yaml", "yml", "properties", "toml", "env")) {
      return AssetCategory.PROJECT_CONFIG
    }

    // 7. Documents
    if (ext in listOf("pdf", "txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "html", "log")) {
      return AssetCategory.DOCUMENT
    }

    return AssetCategory.QUARANTINE
  }
}
