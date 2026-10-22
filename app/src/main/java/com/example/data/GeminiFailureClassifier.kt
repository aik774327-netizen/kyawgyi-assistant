package com.example.data

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * FIX-05 (Phase 1.5 device remediation): distinct, honest classes
 * for Gemini request failures.
 *
 * Device evidence B4: the user's API key IS entered in Settings, yet
 * the app behaved as if Gemini had "no online connection". In code,
 * every non-429 HTTP failure was collapsed twice over: the request
 * layer threw a generic IOException("HTTP $code Error: <body>"), and
 * generateResponse mapped every non-quota exception to a single
 * CONNECTION_ERROR_MESSAGE ("check your key and internet") — so an
 * auth rejection (401/403, e.g. API not enabled / key restricted /
 * region) was indistinguishable from a network timeout, and
 * testKeyConnection labeled even HTTP 500/404 as "Invalid key".
 *
 * This classifier is pure Kotlin so the mapping is unit-testable.
 * It never sees, carries, or logs the API key — only status codes
 * and exception types.
 */

/** An HTTP-level Gemini failure, carrying ONLY the status code. */
class GeminiHttpException(val code: Int) : IOException("Gemini HTTP $code")

enum class GeminiFailureClass {
    /** HTTP 400/401/403 — key rejected, API not enabled, restricted. */
    AUTH,
    /** HTTP 429 — quota / rate limit. */
    QUOTA,
    /** The request timed out (slow network, VPN, server stall). */
    NETWORK_TIMEOUT,
    /** The server could not be reached at all (DNS/connect). */
    NETWORK_UNREACHABLE,
    /** HTTP 5xx — Google-side failure. */
    SERVER,
    /** HTTP 404 — model/endpoint not found. */
    ENDPOINT,
    /** HTTP 200 but no usable reply text. */
    EMPTY_RESPONSE,
    /** No key configured at all (the B4-refuted hypothesis class). */
    NO_KEY,
    UNKNOWN
}

object GeminiFailureClassifier {

    fun classifyHttpCode(code: Int): GeminiFailureClass = when {
        code == 429 -> GeminiFailureClass.QUOTA
        code == 400 || code == 401 || code == 403 -> GeminiFailureClass.AUTH
        code == 404 -> GeminiFailureClass.ENDPOINT
        code in 500..599 -> GeminiFailureClass.SERVER
        else -> GeminiFailureClass.UNKNOWN
    }

    /** Classifies an exception chain (HTTP wrapper first, then network kinds). */
    fun classify(e: Throwable): GeminiFailureClass {
        var current: Throwable? = e
        while (current != null) {
            when (current) {
                is GeminiHttpException -> return classifyHttpCode(current.code)
                is SocketTimeoutException -> return GeminiFailureClass.NETWORK_TIMEOUT
                is UnknownHostException, is ConnectException, is NoRouteToHostException ->
                    return GeminiFailureClass.NETWORK_UNREACHABLE
            }
            current = current.cause
        }
        return GeminiFailureClass.UNKNOWN
    }

    /** One honest, class-specific Burmese user message per failure class. */
    fun userMessageFor(failureClass: GeminiFailureClass, httpCode: Int? = null): String =
        when (failureClass) {
            GeminiFailureClass.AUTH ->
                "Gemini API key ကို ငြင်းပယ်လိုက်ပါသည်${httpCode?.let { " (HTTP $it)" } ?: ""} — key မှန်ကန်မှု၊ Gemini API ဖွင့်ထားမှု နှင့် key ကန့်သတ်ချက်များ စစ်ဆေးပေးပါ"
            GeminiFailureClass.QUOTA ->
                GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE
            GeminiFailureClass.NETWORK_TIMEOUT ->
                "Gemini သို့ ဆက်သွယ်မှု အချိန်ကုန်သွားပါသည် — အင်တာနက် နှေးခြင်း သို့မဟုတ် VPN ကြောင့် ဖြစ်နိုင်ပါသည်။ ခဏစောင့်၍ ပြန်စမ်းပေးပါ"
            GeminiFailureClass.NETWORK_UNREACHABLE ->
                "Gemini server သို့ ဆက်သွယ်၍ မရပါ — အင်တာနက် ချိတ်ဆက်မှု ကို စစ်ဆေးပေးပါ"
            GeminiFailureClass.SERVER ->
                "Gemini server ဘက်မှ အမှား${httpCode?.let { " (HTTP $it)" } ?: ""} ဖြစ်နေပါသည် — ခဏစောင့်၍ ပြန်စမ်းပေးပါ"
            GeminiFailureClass.ENDPOINT ->
                "Gemini model/endpoint မတွေ့ပါ (HTTP 404) — app update လိုအပ်နိုင်ပါသည်"
            GeminiFailureClass.EMPTY_RESPONSE ->
                "Gemini မှ အဖြေ ပြန်မလာပါ (empty response) — ပြန်စမ်းပေးပါ"
            GeminiFailureClass.NO_KEY ->
                GeminiApiService.NO_API_KEY_MESSAGE
            GeminiFailureClass.UNKNOWN ->
                "Gemini ဆက်သွယ်မှု အမှား ဖြစ်ပါသည် — ပြန်စမ်းပေးပါ"
        }
}
