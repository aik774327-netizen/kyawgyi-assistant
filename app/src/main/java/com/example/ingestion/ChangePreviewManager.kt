package com.example.ingestion

enum class ChangeOperation {
  ADD,
  UPDATE,
  REPLACE,
  DELETE
}

enum class ChangeRisk(val labelBurmese: String, val badgeColorHex: Long) {
  LOW("အန္တရာယ်နိမ့် (Low Risk)", 0xFF10B981),
  MEDIUM("သတိပြုရန် (Medium Risk)", 0xFFF59E0B),
  HIGH("မြင့်မားသော သက်ရောက်မှု (High Risk)", 0xFFEF4444)
}

data class ProposedProjectChange(
  val changeId: String,
  val operation: ChangeOperation,
  val sourceFilename: String,
  val destinationPath: String,
  val risk: ChangeRisk,
  val affectedFiles: List<String>,
  val summaryBurmese: String,
  val summaryEnglish: String,
  val requiresExplicitApproval: Boolean = true
)

object ChangePreviewManager {

  fun evaluate(filename: String, destinationPath: String, category: ProjectDataRouter.AssetCategory): ProposedProjectChange {
    val isSource = category == ProjectDataRouter.AssetCategory.PROJECT_SOURCE
    val isConfig = category == ProjectDataRouter.AssetCategory.PROJECT_CONFIG

    val risk = when {
      isSource -> ChangeRisk.HIGH
      isConfig -> ChangeRisk.MEDIUM
      else -> ChangeRisk.LOW
    }

    val burmeseSummary = when (risk) {
      ChangeRisk.HIGH -> "ဆော့ဖ်ဝဲလ်ကုဒ် ဖိုင်ဖြစ်ပါသည် ($filename)။ အတည်ပြုချက်မရှိဘဲ live code အား မည်သည့်အခါမျှ အလိုအလျောက် overwrite မလုပ်ပါ။"
      ChangeRisk.MEDIUM -> "စနစ်ဆက်တင်/Config ဖိုင်ဖြစ်ပါသည် ($filename)။ အတည်ပြုချက် ရယူရန် လိုအပ်ပါသည်။"
      ChangeRisk.LOW -> "ဖိုင်အသစ် ထည့်သွင်းခြင်း ဖြစ်ပါသည် ($filename)။"
    }

    return ProposedProjectChange(
      changeId = "CHG_${System.currentTimeMillis()}",
      operation = ChangeOperation.ADD,
      sourceFilename = filename,
      destinationPath = destinationPath,
      risk = risk,
      affectedFiles = listOf(destinationPath),
      summaryBurmese = burmeseSummary,
      summaryEnglish = "Evaluation of $filename routing to $destinationPath",
      requiresExplicitApproval = risk != ChangeRisk.LOW
    )
  }
}
