package com.example.audit

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.CommandLogEntity
import com.example.data.FailureCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KYAW GYI AI — Command Failure Analyzer & Learning Engine (အချက် ၂)
 *
 * အမှားမှ သင်ယူနိုင်သော AI စနစ်:
 * ၁။ မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက်တိုင်းကို Error Log အဖြစ် မှတ်သားခြင်း
 * ၂။ အတုအယောင် ဟန်ပြ လုံးဝ မလုပ်ဆောင်ခြင်း
 * ၃။ အမှားဖြစ်ရသော အကြောင်းရင်းကို
 *     • နည်းပညာပိုင်း (Technical)
 *     • အရင်းအမြစ် (Resource)
 *     • လက်တွေ့မကိုက်ညီမှု (Pragmatic / Contextual)
 *     အဖြစ် ခွဲခြမ်းစိတ်ဖြာခြင်း
 * ၄။ အမှားတစ်ခုတူ ထပ်မံတောင်းဆိုလာပါက
 *     "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်" ဟု သတိပေးခြင်း
 * ၅။ အမှားအမျိုးအစားကို အသုံးပြုသူ နားလည်အောင် ရှင်းပြခြင်း
 * ၆။ မအောင်မြင်ခဲ့သော နည်းလမ်းကို ထပ်မံ အကြံမပေးဘဲ လက်တွေ့ကျသော အစားထိုးနည်းလမ်း ညွှန်ပြခြင်း
 */
object CommandFailureAnalyzer {

    private const val TAG = "CommandFailureAnalyzer"

    data class FailureAnalysisResult(
        val category: FailureCategory,
        val categoryLabelMm: String,
        val reasonMm: String,
        val suggestedAlternativeMm: String,
        val technicalDetails: String,
        val alternativeFlow: com.example.fallback.AlternativeFlow? = null
    )

    data class RepeatFailureWarning(
        val isRepeated: Boolean,
        val previousFailureLog: CommandLogEntity,
        val warningSpeechMm: String,
        val explanationMm: String,
        val alternativeAdviceMm: String,
        val alternativeFlow: com.example.fallback.AlternativeFlow? = null
    )

    /**
     * ခွဲခြမ်းစိတ်ဖြာခြင်း (Root-Cause Classification)
     * Technical, Resource, Pragmatic Contextual အဖြစ် အမျိုးအစားခွဲခြားခြင်း
     */
    fun classifyFailure(
        commandText: String,
        commandType: String,
        exception: Throwable? = null,
        rawDetail: String? = null
    ): FailureAnalysisResult {
        val detail = (rawDetail ?: "") + " " + (exception?.message ?: "") + " " + (exception?.javaClass?.simpleName ?: "")
        val lowerDetail = detail.lowercase()
        val lowerCmd = commandText.lowercase()

        // 1. TECHNICAL (နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက် - Exception / Crash / Parsing bugs)
        val isTechnicalCrash = lowerDetail.contains("nullpointer") ||
            lowerDetail.contains("illegalstate") ||
            lowerDetail.contains("indexoutofbounds") ||
            lowerDetail.contains("classcast") ||
            lowerDetail.contains("nosuchmethod") ||
            lowerDetail.contains("unsupportedoperation") ||
            lowerDetail.contains("crash") ||
            lowerDetail.contains("jsonexception") ||
            lowerDetail.contains("parseexception") ||
            lowerDetail.contains("internal_error")

        if (isTechnicalCrash) {
            val techReason = if (lowerDetail.contains("nullpointer") || lowerDetail.contains("illegalstate")) {
                "Android Framework ပိုင်းဆိုင်ရာ System State သို့မဟုတ် API ချိတ်ဆက်မှု ချို့ယွင်းသွားခြင်းကြောင့် ဖြစ်ပါသည်"
            } else if (lowerDetail.contains("json") || lowerDetail.contains("parse")) {
                "အမိန့်စာသား ဖွဲ့စည်းပုံ ဒေတာ ဖတ်ရှုမှုတွင် နည်းပညာပိုင်း ချွတ်ချော်သွားခြင်းကြောင့် ဖြစ်ပါသည်"
            } else {
                "Android OS ဗားရှင်း ကန့်သတ်ချက် သို့မဟုတ် နည်းပညာပိုင်း ချို့ယွင်းမှုကြောင့် ဖြစ်ပါသည်"
            }

            return FailureAnalysisResult(
                category = FailureCategory.TECHNICAL,
                categoryLabelMm = FailureCategory.TECHNICAL.burmeseLabel,
                reasonMm = techReason,
                suggestedAlternativeMm = "စနစ်ကို ခေတ္တစောင့်ဆိုင်းပြီး ပြန်လည်စမ်းသပ်ပါ၊ သို့မဟုတ် တိကျသော တိုက်ရိုက် အသံအမိန့် (Direct Intent) ဖြင့် အမိန့်ပေးပါ",
                technicalDetails = detail.trim()
            )
        }

        // 2. RESOURCE (အရင်းအမြစ်ဆိုင်ရာ ကန့်သတ်ချက်)
        // Network offline, timeout, low memory, missing voice/model resources
        val isResource = lowerDetail.contains("timeout") ||
            lowerDetail.contains("unknownhost") ||
            lowerDetail.contains("connectexception") ||
            lowerDetail.contains("nonetwork") ||
            lowerDetail.contains("offline") ||
            lowerDetail.contains("socket") ||
            lowerDetail.contains("outofmemory") ||
            lowerDetail.contains("not enough memory") ||
            lowerDetail.contains("no voice data") ||
            lowerDetail.contains("tts_missing") ||
            lowerDetail.contains("resource") ||
            lowerDetail.contains("404") ||
            lowerDetail.contains("503")

        if (isResource) {
            val reason = if (lowerDetail.contains("timeout") || lowerDetail.contains("network") || lowerDetail.contains("offline")) {
                "အင်တာနက်လိုင်း ချိတ်ဆက်မှု မရှိခြင်း သို့မဟုတ် လိုင်းနှေးကွေးပြတ်တောက်နေခြင်းကြောင့် ဖြစ်ပါသည်"
            } else if (lowerDetail.contains("memory") || lowerDetail.contains("outofmemory")) {
                "ဖုန်းတွင်း ယာယီမှတ်ဉာဏ် (RAM) သို့မဟုတ် Storage ပြည့်နေခြင်းကြောင့် ဖြစ်ပါသည်"
            } else {
                "လိုအပ်သော အသံဖိုင်ဒေတာ သို့မဟုတ် ဒေသတွင်း မော်ဒယ်အရင်းအမြစ် မလုံလောက်ခြင်းကြောင့် ဖြစ်ပါသည်"
            }

            val alternative = if (lowerDetail.contains("network") || lowerDetail.contains("offline")) {
                "Wi-Fi သို့မဟုတ် Mobile Data ဖွင့်ထားခြင်း ရှိ/မရှိ စစ်ဆေးပါ၊ သို့မဟုတ် Offline FastPath Command များကိုသာ အသုံးပြုပါ"
            } else {
                "နောက်ခံအက်ပ်များကို ပိတ်၍ RAM ရှင်းလင်းပေးပါ သို့မဟုတ် Local Speech Model ကို ဒေါင်းလုဒ်ရယူပါ"
            }

            return FailureAnalysisResult(
                category = FailureCategory.RESOURCE,
                categoryLabelMm = FailureCategory.RESOURCE.burmeseLabel,
                reasonMm = reason,
                suggestedAlternativeMm = alternative,
                technicalDetails = detail.trim()
            )
        }

        // 2. PRAGMATIC_CONTEXTUAL (လက်တွေ့မကိုက်ညီမှု)
        // Missing runtime permission (Accessibility, Overlay, Call, Microphone), App not installed, Screen locked, etc.
        val isContextual = lowerDetail.contains("securityexception") ||
            lowerDetail.contains("permission") ||
            lowerDetail.contains("denied") ||
            lowerDetail.contains("accessibility") ||
            lowerDetail.contains("overlay") ||
            lowerDetail.contains("not installed") ||
            lowerDetail.contains("activitynotfound") ||
            lowerDetail.contains("blocked_by_policy") ||
            lowerDetail.contains("disabled") ||
            lowerDetail.contains("foreground") ||
            lowerDetail.contains("no device") ||
            lowerDetail.contains("screen off") ||
            lowerDetail.contains("locked") ||
            lowerDetail.contains("endpoint") ||
            lowerDetail.contains("not configured")

        if (isContextual) {
            val reason = if (lowerDetail.contains("accessibility") || lowerDetail.contains("overlay") || lowerDetail.contains("permission")) {
                "လိုအပ်သော စနစ်ခွင့်ပြုချက် (Accessibility သို့မဟုတ် Overlay Permission) ပိတ်ထားခြင်းကြောင့် ဖြစ်ပါသည်"
            } else if (lowerDetail.contains("not installed") || lowerDetail.contains("activitynotfound")) {
                "တောင်းဆိုထားသော အက်ပ်သည် ဤဖုန်းထဲတွင် ထည့်သွင်းထားခြင်း မရှိသေးပါ"
            } else if (lowerDetail.contains("endpoint") || lowerDetail.contains("not configured")) {
                "စမတ်ကိရိယာအတွက် Network Endpoint သို့မဟုတ် API ချိတ်ဆက်မှု လိပ်စာ ထည့်သွင်းထားခြင်း မရှိသေးပါ"
            } else {
                "လက်တွေ့ ဖုန်းမျက်နှာပြင် အခြေအနေ သို့မဟုတ် လုံခြုံရေး မူဝါဒနှင့် ကိုက်ညီမှု မရှိသောကြောင့် ဖြစ်ပါသည်"
            }

            val alternative = if (lowerDetail.contains("permission") || lowerDetail.contains("accessibility")) {
                "ဖုန်း Settings -> Accessibility ထဲတွင် 'Kyaw Gyi Assistant' ခွင့်ပြုချက်ကို ဖွင့်ပေးရန် လိုအပ်ပါသည်"
            } else if (lowerDetail.contains("not installed")) {
                "Google Play Store မှ အက်ပ်ကို ဦးစွာ ဒေါင်းလုဒ် ရယူပေးပါ သို့မဟုတ် ဖုန်းထဲရှိ အခြား အက်ပ်ကို အစားထိုးဖွင့်ပါ"
            } else {
                "ဖုန်း Settings မှ လိုအပ်သော ခွင့်ပြုချက် ဖွင့်ပေးပြီးမှ ထပ်မံ အသုံးပြုနိုင်ပါသည်"
            }

            return FailureAnalysisResult(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                categoryLabelMm = FailureCategory.PRAGMATIC_CONTEXTUAL.burmeseLabel,
                reasonMm = reason,
                suggestedAlternativeMm = alternative,
                technicalDetails = detail.trim()
            )
        }

        // 3. TECHNICAL (နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက်)
        // Default technical failure (Android API Level, NullPointerException, JSON parser, crash)
        val techReason = if (lowerDetail.contains("nullpointer") || lowerDetail.contains("illegalstate")) {
            "Android Framework ပိုင်းဆိုင်ရာ System State သို့မဟုတ် API ချိတ်ဆက်မှု ချို့ယွင်းသွားခြင်းကြောင့် ဖြစ်ပါသည်"
        } else if (lowerDetail.contains("json") || lowerDetail.contains("parse")) {
            "အမိန့်စာသား ဖွဲ့စည်းပုံ ဒေတာ ဖတ်ရှုမှုတွင် နည်းပညာပိုင်း ချွတ်ချော်သွားခြင်းကြောင့် ဖြစ်ပါသည်"
        } else {
            "Android OS ဗားရှင်း ကန့်သတ်ချက် သို့မဟုတ် Internal Action Executor ချို့ယွင်းမှုကြောင့် ဖြစ်ပါသည်"
        }

        val techAlternative = "စနစ်ကို ခေတ္တစောင့်ဆိုင်းပြီး ပြန်လည်စမ်းသပ်ပါ၊ သို့မဟုတ် တိကျသော တိုက်ရိုက် အသံအမိန့် (Direct Intent) ဖြင့် အမိန့်ပေးပါ"

        return FailureAnalysisResult(
            category = FailureCategory.TECHNICAL,
            categoryLabelMm = FailureCategory.TECHNICAL.burmeseLabel,
            reasonMm = techReason,
            suggestedAlternativeMm = techAlternative,
            technicalDetails = detail.trim()
        )
    }

    /**
     * အမှားတစ်ခုတူ ထပ်မံတောင်းဆိုလာပါက စစ်ဆေးသတိပေးခြင်း
     * "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်" ဟု သတိပေးရမည်။
     * မအောင်မြင်ခဲ့သော နည်းလမ်းကို ထပ်မံ အကြံမပေးဘဲ အခြား အစားထိုး နည်းလမ်း ညွှန်ပြခြင်း။
     */
    suspend fun checkPreviousFailure(context: Context, commandText: String): RepeatFailureWarning? = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = commandText.trim()
            if (cleanQuery.isBlank()) return@withContext null

            val db = AppDatabase.getDatabase(context)
            val recentFailure = db.commandLogDao().findRecentFailure(cleanQuery) ?: return@withContext null

            val category = FailureCategory.fromCode(recentFailure.failureCategory)
            val flow = com.example.fallback.AlternativeFlowManager.resolveFlow(
                context = context,
                commandText = cleanQuery,
                commandType = recentFailure.commandType,
                target = null,
                failureCategory = category,
                failureDetail = recentFailure.actionDetail.ifBlank { recentFailure.failureReason }
            )

            val warningSpeech = "ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်ခင်ဗျာ။ အမှားအမျိုးအစားမှာ ${category.burmeseLabel} ဖြစ်ပြီး၊ အကြောင်းရင်းမှာ '${recentFailure.failureReason}' ဖြစ်ပါသည်။ မအောင်မြင်ခဲ့သော နည်းလမ်း ('${flow.avoidedMethodMm}') အား ထပ်မံ အကြံမပြုတော့ဘဲ အစားထိုးအနေဖြင့် ${recentFailure.suggestedAlternative} ကို ဆောင်ရွက်ပေးနိုင်ပါသည်ခင်ဗျာ။"

            val explanation = "အမှားအမျိုးအစား: [${category.burmeseLabel}]\nအကြောင်းရင်း: ${recentFailure.failureReason}\nရှောင်လွှဲခဲ့သော နည်းလမ်း: ${flow.avoidedMethodMm}"
            val alternative = recentFailure.suggestedAlternative.ifBlank { flow.actionGuidanceMm }

            RepeatFailureWarning(
                isRepeated = true,
                previousFailureLog = recentFailure,
                warningSpeechMm = warningSpeech,
                explanationMm = explanation,
                alternativeAdviceMm = alternative,
                alternativeFlow = flow
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking previous failure", e)
            null
        }
    }

    /**
     * မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက်ကို Error Log အဖြစ် Database ထဲတွင် Structured Format ဖြင့် သိမ်းဆည်းခြင်း
     */
    suspend fun recordFailure(
        context: Context,
        commandText: String,
        commandType: String,
        exception: Throwable? = null,
        rawDetail: String? = null,
        isRepeated: Boolean = false
    ): CommandLogEntity = withContext(Dispatchers.IO) {
        val analysis = classifyFailure(commandText, commandType, exception, rawDetail)
        val flow = com.example.fallback.AlternativeFlowManager.resolveFlow(
            context = context,
            commandText = commandText,
            commandType = commandType,
            target = null,
            failureCategory = analysis.category,
            failureDetail = (rawDetail ?: "") + " " + (exception?.message ?: "")
        )

        val entity = CommandLogEntity(
            commandText = commandText,
            commandType = commandType,
            status = "FAILED",
            actionDetail = analysis.technicalDetails.ifBlank { "Failure recorded in ${analysis.category.name}" },
            timestamp = System.currentTimeMillis(),
            failureCategory = analysis.category.code,
            failureReason = analysis.reasonMm,
            suggestedAlternative = analysis.suggestedAlternativeMm,
            isRepeatedFailure = isRepeated,
            remediationAction = flow.fallbackIntentAction ?: "",
            remediationUri = flow.fallbackIntentUri ?: ""
        )

        try {
            val db = AppDatabase.getDatabase(context)
            val id = db.commandLogDao().insertCommandLog(entity)
            Log.w(TAG, "Recorded failure log #$id for '$commandText' -> Category: ${analysis.category.name} | Remediation: ${flow.remediationType}")
            entity.copy(id = id)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist failure log to database", e)
            entity
        }
    }
}
