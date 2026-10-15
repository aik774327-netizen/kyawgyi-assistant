package com.example.service

import com.example.database.ErrorCategory
import com.example.database.ErrorLogDao
import com.example.database.ErrorLogEntity
import kotlinx.coroutines.flow.Flow

data class LearningCheckResult(
  val hadPreviousFailure: Boolean,
  val warningMessage: String?,
  val speechWarning: String?,
  val failedCategory: ErrorCategory?,
  val categoryExplanation: String?,
  val failureReason: String?,
  val failedMethod: String?,
  val workingAlternative: String?,
  val allAvoidedMethods: List<String> = emptyList()
)

class ErrorLearningEngine(private val errorLogDao: ErrorLogDao) {

  fun getAllErrorLogs(): Flow<List<ErrorLogEntity>> {
    return errorLogDao.getAllLogs()
  }

  suspend fun checkPreviousFailure(actionKey: String, rawCommand: String): LearningCheckResult {
    val previous = errorLogDao.findRecentFailure(actionKey, rawCommand)
    if (previous != null && !previous.isResolved) {
      val category = try {
        ErrorCategory.valueOf(previous.category)
      } catch (_: Exception) {
        ErrorCategory.TECHNICAL
      }

      val failedMethods = errorLogDao.getFailedMethodsForAction(actionKey)
        .filter { it.isNotBlank() }
        .distinct()

      val warning = buildString {
        append("⚠️ ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်!\n")
        append("• အမှားအမျိုးအစား: ${category.burmeseTitle}\n")
        append("• အမျိုးအစားရှင်းလင်းချက်: ${category.explanationBurmese}\n")
        append("• ဖြစ်ရသော အကြောင်းရင်း: ${previous.reasonDetail}\n")
        if (previous.failedMethod.isNotBlank()) {
          append("• မအောင်မြင်ခဲ့သော နည်းလမ်း (ထပ်မံအကြံမပေးပါ): ${previous.failedMethod}\n")
        }
        append("• လက်တွေ့ကျသော အစားထိုးလုပ်ဆောင်ချက်: ${previous.alternativeAdvice}")
      }

      val speech = "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်။ ${category.burmeseTitle} ကြောင့် ${previous.reasonDetail}။ ${previous.alternativeAdvice}"

      return LearningCheckResult(
        hadPreviousFailure = true,
        warningMessage = warning,
        speechWarning = speech,
        failedCategory = category,
        categoryExplanation = category.explanationBurmese,
        failureReason = previous.reasonDetail,
        failedMethod = previous.failedMethod,
        workingAlternative = previous.alternativeAdvice,
        allAvoidedMethods = failedMethods
      )
    }

    return LearningCheckResult(
      hadPreviousFailure = false,
      warningMessage = null,
      speechWarning = null,
      failedCategory = null,
      categoryExplanation = null,
      failureReason = null,
      failedMethod = null,
      workingAlternative = null,
      allAvoidedMethods = emptyList()
    )
  }

  suspend fun recordFailure(
    actionKey: String,
    rawCommand: String,
    category: ErrorCategory,
    reasonDetail: String,
    failedMethod: String = "",
    alternativeAdvice: String
  ): ErrorLogEntity {
    val existing = errorLogDao.findRecentFailure(actionKey, rawCommand)
    if (existing != null) {
      errorLogDao.incrementRetry(existing.id)
      return existing.copy(
        retryCount = existing.retryCount + 1,
        category = category.name,
        categoryBurmese = category.burmeseTitle,
        categoryExplanation = category.explanationBurmese,
        reasonDetail = reasonDetail,
        failedMethod = if (failedMethod.isNotBlank()) failedMethod else existing.failedMethod,
        alternativeAdvice = alternativeAdvice
      )
    }

    val newLog = ErrorLogEntity(
      actionKey = actionKey,
      rawCommand = rawCommand,
      category = category.name,
      categoryBurmese = category.burmeseTitle,
      categoryExplanation = category.explanationBurmese,
      reasonDetail = reasonDetail,
      failedMethod = failedMethod,
      alternativeAdvice = alternativeAdvice,
      retryCount = 1,
      isResolved = false
    )
    val id = errorLogDao.insertLog(newLog)
    return newLog.copy(id = id)
  }

  suspend fun markResolved(actionKey: String) {
    errorLogDao.markResolved(actionKey)
  }

  suspend fun clearLogs() {
    errorLogDao.clearAll()
  }
}
