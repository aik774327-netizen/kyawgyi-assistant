package com.example.fallback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.example.data.FailureCategory

/**
 * AlternativeFlowManager (အချက် ၃)
 *
 * အမှားအလိုက် အစားထိုးလမ်းကြောင်းနှင့် လမ်းညွှန်ချက် (Fallback & Alternative Flow) သတ်မှတ်စနစ်။
 *
 * စည်းမျဉ်းများ:
 * ၁။ မအောင်မြင်ခဲ့သော နည်းလမ်းကို ထပ်မံ အကြံမပေးရ (Never repeat failed path)
 * ၂။ အမှားအမျိုးအစားအလိုက် စစ်မှန်သော Fallback Action/Intent ပံ့ပိုးပေးရမည်
 * ၃။ အသုံးပြုသူ နားလည်စေရန် မြန်မာဘာသာဖြင့် ရှင်းလင်းချက်နှင့် အဆင့်ဆင့် လမ်းညွှန်မှု ပြုလုပ်ပေးရမည်
 */
object AlternativeFlowManager {

    private const val TAG = "AlternativeFlowManager"

    /**
     * အမှားအချက်အလက်ပေါ် အခြေခံ၍ အစားထိုးလမ်းကြောင်း (AlternativeFlow) တွက်ချက်ရွေးချယ်ခြင်း
     */
    fun resolveFlow(
        context: Context,
        commandText: String,
        commandType: String,
        target: String? = null,
        failureCategory: FailureCategory,
        failureDetail: String
    ): AlternativeFlow {
        val lowerDetail = failureDetail.lowercase()
        val lowerCmd = commandText.lowercase()
        val lowerTarget = (target ?: "").lowercase()

        return when (failureCategory) {
            FailureCategory.PRAGMATIC_CONTEXTUAL -> resolveContextualAlternative(
                context, lowerCmd, commandType, lowerTarget, lowerDetail
            )
            FailureCategory.RESOURCE -> resolveResourceAlternative(
                context, lowerCmd, commandType, lowerTarget, lowerDetail
            )
            FailureCategory.TECHNICAL -> resolveTechnicalAlternative(
                context, lowerCmd, commandType, lowerTarget, lowerDetail
            )
            FailureCategory.NONE -> AlternativeFlow(
                category = FailureCategory.NONE,
                remediationType = RemediationType.MANUAL_GUIDANCE,
                titleMm = "ပုံမှန်ဆောင်ရွက်မှု",
                explanationMm = "လုပ်ဆောင်ချက် ပုံမှန် အောင်မြင်ပါသည်",
                actionGuidanceMm = "ထပ်မံခိုင်းစေနိုင်ပါသည်",
                avoidedMethodMm = "မရှိပါ"
            )
        }
    }

    /**
     * လက်တွေ့မကိုက်ညီမှု (PRAGMATIC_CONTEXTUAL) အတွက် အစားထိုးလမ်းကြောင်းများ
     */
    private fun resolveContextualAlternative(
        context: Context,
        commandText: String,
        commandType: String,
        target: String,
        lowerDetail: String
    ): AlternativeFlow {
        // 1. Accessibility Service ပိတ်ထားခြင်း
        if (lowerDetail.contains("accessibility") || lowerDetail.contains("service not running")) {
            return AlternativeFlow(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                remediationType = RemediationType.SETTINGS_PERMISSION,
                titleMm = "Accessibility ဝန်ဆောင်မှု ဖွင့်ပေးရန် လိုအပ်ပါသည်",
                explanationMm = "ဖုန်းမျက်နှာပြင်ပေါ်တွင် အမိန့်များကို ကိုယ်စားဆောင်ရွက်ပေးရန် Accessibility ခွင့်ပြုချက် ပိတ်ထားပါသည်",
                actionGuidanceMm = "ဖွင့်ပေးထားသော Accessibility ဆက်တင်ထဲတွင် 'Kyaw Gyi Assistant' ကို ရွေးချယ်ပြီး ဖွင့်ပေးပါခင်ဗျာ",
                avoidedMethodMm = "ခွင့်ပြုချက် မရှိဘဲ မျက်နှာပြင် အလိုအလျောက် နှိပ်ရန် ကြိုးပမ်းခြင်း",
                fallbackIntentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                canAutoExecute = true
            )
        }

        // 2. Overlay (Display over other apps) ခွင့်ပြုချက် မရှိခြင်း
        if (lowerDetail.contains("overlay") || lowerDetail.contains("draw overlay")) {
            return AlternativeFlow(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                remediationType = RemediationType.SETTINGS_PERMISSION,
                titleMm = "Display Over Other Apps ခွင့်ပြုချက် ဖွင့်ပေးရန် လိုအပ်ပါသည်",
                explanationMm = "အခြားအက်ပ်များပေါ်တွင် Floating Widget နှင့် အချက်အလက်များ ပြသနိုင်ရန် Overlay ခွင့်ပြုချက် လိုအပ်ပါသည်",
                actionGuidanceMm = "ဆက်တင်မျက်နှာပြင်တွင် 'Allow display over other apps' ကို ဖွင့်ပေးပါခင်ဗျာ",
                avoidedMethodMm = "Overlay ခွင့်ပြုချက်မရှိဘဲ Floating View ပြသရန် ကြိုးပမ်းခြင်း",
                fallbackIntentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                fallbackIntentUri = "package:${context.packageName}",
                canAutoExecute = true
            )
        }

        // 3. Call Phone ခွင့်ပြုချက် မရှိခြင်း
        if (lowerDetail.contains("call_phone") || lowerDetail.contains("permission") && commandType.contains("CALL")) {
            return AlternativeFlow(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                remediationType = RemediationType.DIRECT_INTENT,
                titleMm = "ဖုန်းခေါ်ဆိုမှု Dialer သို့ တိုက်ရိုက်လွှဲပြောင်းပေးခြင်း",
                explanationMm = "တိုက်ရိုက် ဖုန်းခေါ်ဆိုခွင့် ခွင့်ပြုချက် မရရှိသေးသဖြင့် ဖုန်းခေါ်ဆိုမှု Dialer စာမျက်နှာသို့ နံပါတ်ရိုက်ထည့်ပြီး လွှဲပြောင်းပေးပါသည်",
                actionGuidanceMm = "Dialer မျက်နှာပြင်တွင် အစိမ်းရောင် ဖုန်းခေါ်ခလုတ်ကို နှိပ်၍ အတည်ပြု ခေါ်ဆိုနိုင်ပါသည်",
                avoidedMethodMm = "တိုက်ရိုက် အလိုအလျောက် ဖုန်းခေါ်ဆိုရန် ကြိုးပမ်းခြင်း",
                fallbackIntentAction = Intent.ACTION_DIAL,
                canAutoExecute = true
            )
        }

        // 4. အက်ပ် မရှိခြင်း (App not installed) -> Web Fallback သို့မဟုတ် Play Store
        if (lowerDetail.contains("not installed") || lowerDetail.contains("activitynotfound")) {
            val webUrl = findWebFallbackUrl(target, commandText)
            if (webUrl != null) {
                return AlternativeFlow(
                    category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                    remediationType = RemediationType.WEB_FALLBACK,
                    titleMm = "အက်ပ်မရှိသဖြင့် ဝဘ်ဆိုက်မှတစ်ဆင့် အစားထိုး ဖွင့်လှစ်ခြင်း",
                    explanationMm = "ဖုန်းထဲတွင် တောင်းဆိုထားသော အက်ပ် မရှိသေးသဖြင့် ဝဘ်ဘရောက်ဆာမှတစ်ဆင့် တိုက်ရိုက် ကြည့်ရှုနိုင်ရန် ဖွင့်ပေးထားပါသည်",
                    actionGuidanceMm = "ဝဘ်ဆိုက် စာမျက်နှာတွင် ဆက်လက် ကြည့်ရှု သုံးစွဲနိုင်ပါသည်",
                    avoidedMethodMm = "ဖုန်းတွင်းမရှိသော အက်ပ်ကို အတင်းဖွင့်ရန် ကြိုးပမ်းခြင်း",
                    fallbackIntentAction = Intent.ACTION_VIEW,
                    fallbackIntentUri = webUrl,
                    canAutoExecute = true
                )
            } else {
                val pkgName = findPackageName(target, commandText)
                return AlternativeFlow(
                    category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                    remediationType = RemediationType.PLAY_STORE_INSTALL,
                    titleMm = "Google Play Store တွင် အက်ပ်ရယူရန် လမ်းညွှန်ပေးခြင်း",
                    explanationMm = "တောင်းဆိုထားသော အက်ပ်သည် ဖုန်းထဲတွင် မရှိသေးပါသဖြင့် Play Store မှတစ်ဆင့် တိုက်ရိုက် ဒေါင်းလုဒ် ရယူနိုင်ပါသည်",
                    actionGuidanceMm = "Play Store မျက်နှာပြင်တွင် 'Install' ခလုတ်ကို နှိပ်၍ ရယူနိုင်ပါသည်",
                    avoidedMethodMm = "ထည့်သွင်းမထားသော အက်ပ်ကို ခေါ်ယူခြင်း",
                    fallbackIntentAction = Intent.ACTION_VIEW,
                    fallbackIntentUri = if (pkgName != null) "market://details?id=$pkgName" else "market://search?q=${Uri.encode(target.ifBlank { commandText })}",
                    canAutoExecute = true
                )
            }
        }

        // 5. Smart Home Endpoint မရှိခြင်း
        if (lowerDetail.contains("endpoint") || lowerDetail.contains("not configured") || commandType.contains("SMART_HOME")) {
            return AlternativeFlow(
                category = FailureCategory.PRAGMATIC_CONTEXTUAL,
                remediationType = RemediationType.GOOGLE_ASSISTANT_RELAY,
                titleMm = "Google Assistant / Home သို့ လွှဲပြောင်းထိန်းချုပ်ပေးခြင်း",
                explanationMm = "စမတ်ကိရိယာအတွက် သီးသန့် Local Network Endpoint မရှိသဖြင့် စနစ်၏ Google Assistant မှတစ်ဆင့် ဆက်လက် ထိန်းချုပ်နိုင်ရန် လွှဲပြောင်းပေးပါသည်",
                actionGuidanceMm = "Google Assistant သို့မဟုတ် Google Home အက်ပ်မှတစ်ဆင့် မီးဖွင့်/ပိတ် ဆက်လက် လုပ်ဆောင်နိုင်ပါသည်",
                avoidedMethodMm = "အတည်မပြုနိုင်သော အတုအယောင် ဒေသတွင်း IoT ခေါ်ယူမှု",
                fallbackIntentAction = Intent.ACTION_VOICE_COMMAND,
                canAutoExecute = true
            )
        }

        // Default Contextual
        return AlternativeFlow(
            category = FailureCategory.PRAGMATIC_CONTEXTUAL,
            remediationType = RemediationType.SETTINGS_PERMISSION,
            titleMm = "စနစ်ဆက်တင်များ ပြင်ဆင်ရန် ဖွင့်ပေးခြင်း",
            explanationMm = "လုပ်ဆောင်ချက်အတွက် လိုအပ်သော စနစ်ဆက်တင်နှင့် ခွင့်ပြုချက်များကို ပြင်ဆင်ပေးရန် လိုအပ်ပါသည်",
            actionGuidanceMm = "အက်ပ်အချက်အလက် ဆက်တင်စာမျက်နှာတွင် ခွင့်ပြုချက်များကို စစ်ဆေးဖွင့်ပေးပါ",
            avoidedMethodMm = "အခြေအနေ မကိုက်ညီသော စနစ်ခေါ်ဆိုမှု ပြုလုပ်ခြင်း",
            fallbackIntentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            fallbackIntentUri = "package:${context.packageName}",
            canAutoExecute = true
        )
    }

    /**
     * အရင်းအမြစ် ကန့်သတ်ချက် (RESOURCE) အတွက် အစားထိုးလမ်းကြောင်းများ
     */
    private fun resolveResourceAlternative(
        context: Context,
        commandText: String,
        commandType: String,
        target: String,
        lowerDetail: String
    ): AlternativeFlow {
        // 1. Network Timeout သို့မဟုတ် Offline
        if (lowerDetail.contains("timeout") || lowerDetail.contains("offline") || lowerDetail.contains("network")) {
            return AlternativeFlow(
                category = FailureCategory.RESOURCE,
                remediationType = RemediationType.OFFLINE_FASTPATH,
                titleMm = "အော့ဖ်လိုင်း စက်တွင်း အသံအမိန့် စနစ်သို့ အစားထိုး ပြောင်းလဲခြင်း",
                explanationMm = "အင်တာနက်လိုင်း ချိတ်ဆက်မှု မရှိသဖြင့် အင်တာနက် မလိုသော စက်တွင်း FastPath အမိန့်များဖြင့်သာ တိုက်ရိုက် ဆောင်ရွက်ပေးပါမည်",
                actionGuidanceMm = "ဖုန်းစနစ်ပိုင်း အမိန့်များ (ဓာတ်မီး၊ အသံအတိုးအကျယ်၊ အက်ပ်ဖွင့်) ကို အော့ဖ်လိုင်း ဆက်လက် ခိုင်းစေနိုင်ပါသည်၊ အင်တာနက်ဖွင့်ရန် Wi-Fi ဆက်တင်သို့ သွားနိုင်ပါသည်",
                avoidedMethodMm = "အင်တာနက်လိုင်းလိုအပ်သော Cloud Speech / Gemini API သို့ ချိတ်ဆက်ရန် အကြိမ်ကြိမ် ကြိုးပမ်းခြင်း",
                fallbackIntentAction = Settings.ACTION_WIFI_SETTINGS,
                canAutoExecute = false
            )
        }

        // 2. Memory (RAM) မလုံလောက်ခြင်း
        return AlternativeFlow(
            category = FailureCategory.RESOURCE,
            remediationType = RemediationType.MANUAL_GUIDANCE,
            titleMm = "ယာယီမှတ်ဉာဏ် (RAM) ရှင်းလင်းရန် လမ်းညွှန်ခြင်း",
            explanationMm = "ဖုန်းတွင်း ယာယီမှတ်ဉာဏ် ပြည့်နေသဖြင့် အချို့သော ဝန်ဆောင်မှုများကို ကန့်သတ်ထားရပါသည်",
            actionGuidanceMm = "မလိုလားအပ်သော နောက်ခံအက်ပ်များကို ပိတ်ပြီးမှ အမိန့်ကို ထပ်မံ ပေးပို့ပါခင်ဗျာ",
            avoidedMethodMm = "မှတ်ဉာဏ်ပြည့်နေစဉ် လေးလံသော နောက်ခံပရိုဂရမ်များ ခေါ်ယူခြင်း"
        )
    }

    /**
     * နည်းပညာပိုင်းဆိုင်ရာ ချို့ယွင်းချက် (TECHNICAL) အတွက် အစားထိုးလမ်းကြောင်းများ
     */
    private fun resolveTechnicalAlternative(
        context: Context,
        commandText: String,
        commandType: String,
        target: String,
        lowerDetail: String
    ): AlternativeFlow {
        return AlternativeFlow(
            category = FailureCategory.TECHNICAL,
            remediationType = RemediationType.DIRECT_INTENT,
            titleMm = "တိုက်ရိုက် System Direct Intent ဖြင့် အစားထိုး ဆောင်ရွက်ခြင်း",
            explanationMm = "UI မျက်နှာပြင်ပေါ်တွင် အဆင့်ဆင့် နှိပ်ယူမှု ချို့ယွင်းခဲ့သဖြင့် Android စနစ်၏ တိုက်ရိုက် Action ဖြင့် အစားထိုး ခေါ်ယူပေးပါသည်",
            actionGuidanceMm = "တိုက်ရိုက်အမိန့်ဖြင့် အောင်မြင်စွာ ဆောင်ရွက်နိုင်ရန် စီစဉ်ပေးထားပါသည်",
            avoidedMethodMm = "ချွတ်ချော်မှုဖြစ်ခဲ့သော UI Accessibility Node ရှာဖွေနှိပ်ယူမှု ပုံစံ"
        )
    }

    /**
     * အက်ပ်မရှိပါက Web Fallback URL ရှာဖွေခြင်း
     */
    private fun findWebFallbackUrl(target: String, commandText: String): String? {
        val query = "$target $commandText".lowercase()
        return when {
            query.contains("youtube") || query.contains("ယူကျု") || query.contains("သီချင်း") || query.contains("video") -> "https://www.youtube.com"
            query.contains("facebook") || query.contains("ဖေ့ဘွတ်") || query.contains("fb") -> "https://m.facebook.com"
            query.contains("spotify") -> "https://open.spotify.com"
            query.contains("google") || query.contains("ရှာ") || query.contains("search") -> "https://www.google.com"
            query.contains("map") || query.contains("မြေပုံ") -> "https://maps.google.com"
            query.contains("translate") || query.contains("ဘာသာပြန်") -> "https://translate.google.com"
            query.contains("wikipedia") || query.contains("ဝီကီ") -> "https://my.wikipedia.org"
            else -> null
        }
    }

    /**
     * လူသုံးများသော အက်ပ်များအတွက် Package Name ရှာဖွေခြင်း
     */
    private fun findPackageName(target: String, commandText: String): String? {
        val query = "$target $commandText".lowercase()
        return when {
            query.contains("viber") || query.contains("ဗိုက်ဘာ") -> "com.viber.voip"
            query.contains("telegram") || query.contains("တယ်လီဂရမ်") -> "org.telegram.messenger"
            query.contains("tiktok") || query.contains("တစ်တော့") -> "com.zhiliaoapp.musically"
            query.contains("messenger") || query.contains("မက်ဆင်ဂျာ") -> "com.facebook.orca"
            query.contains("chrome") -> "com.android.chrome"
            query.contains("wave") || query.contains("ဝေ့ဗ်") -> "com.wavemoney.wavepay"
            query.contains("kpay") || query.contains("ကေပေး") -> "com.kbzbank.kpay"
            else -> null
        }
    }

    /**
     * အစားထိုး လမ်းကြောင်း Intent အား အမှန်တကယ် ဖွင့်လှစ်ဆောင်ရွက်ပေးခြင်း
     */
    fun executeRemediation(context: Context, flow: AlternativeFlow): Boolean {
        return try {
            val intent = flow.createIntent()
            if (intent != null) {
                context.startActivity(intent)
                Log.i(TAG, "Successfully executed remediation intent: ${intent.action} for flow: ${flow.remediationType}")
                true
            } else {
                Log.w(TAG, "No valid fallback intent found for flow: ${flow.remediationType}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch remediation intent for flow: ${flow.remediationType}", e)
            false
        }
    }
}
