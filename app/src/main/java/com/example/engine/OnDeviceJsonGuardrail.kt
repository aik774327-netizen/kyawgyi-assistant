package com.example.engine

import org.json.JSONArray
import org.json.JSONObject

data class GuardrailParsedResult(
    val isValidJson: Boolean,
    val jsonObject: JSONObject?,
    val jsonArray: JSONArray?,
    val rawCleanText: String,
    val fallbackBurmeseSpeech: String
)

/**
 * Fault-tolerant Guardrail JSON Parser Wrapper for On-Device & Cloud LLMs.
 * Completely isolates the app loop from JSON runtime parse crashes, markdown codeblocks, and unexpected text output.
 */
object OnDeviceJsonGuardrail {

    const val DEFAULT_BURMESE_FALLBACK = "အသံ သေချာ မကြားလိုက်ရလို့ ပြန်ပြောပေးပါနော်"

    /**
     * Clean markdown syntax such as ```json ... ``` or leading/trailing commentary text.
     */
    fun sanitizeMarkdownJson(rawInput: String): String {
        var clean = rawInput.trim()

        // Strip markdown backticks
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }

        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }

        // Extract first '{' or '[' to last '}' or ']'
        val firstBrace = clean.indexOf('{')
        val firstBracket = clean.indexOf('[')

        val lastBrace = clean.lastIndexOf('}')
        val lastBracket = clean.lastIndexOf(']')

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1)
        } else if (firstBracket != -1 && lastBracket != -1 && firstBracket < lastBracket) {
            clean = clean.substring(firstBracket, lastBracket + 1)
        }

        return clean
    }

    /**
     * Safely parse JSON String with 100% exception safety guarantee.
     */
    fun parseSafely(rawInput: String): GuardrailParsedResult {
        if (rawInput.isBlank()) {
            return GuardrailParsedResult(
                isValidJson = false,
                jsonObject = null,
                jsonArray = null,
                rawCleanText = rawInput,
                fallbackBurmeseSpeech = DEFAULT_BURMESE_FALLBACK
            )
        }

        val cleanedJson = sanitizeMarkdownJson(rawInput)

        return try {
            if (cleanedJson.startsWith("{")) {
                val obj = JSONObject(cleanedJson)
                GuardrailParsedResult(
                    isValidJson = true,
                    jsonObject = obj,
                    jsonArray = null,
                    rawCleanText = cleanedJson,
                    fallbackBurmeseSpeech = obj.optString("response", obj.optString("speech", DEFAULT_BURMESE_FALLBACK))
                )
            } else if (cleanedJson.startsWith("[")) {
                val arr = JSONArray(cleanedJson)
                GuardrailParsedResult(
                    isValidJson = true,
                    jsonObject = null,
                    jsonArray = arr,
                    rawCleanText = cleanedJson,
                    fallbackBurmeseSpeech = DEFAULT_BURMESE_FALLBACK
                )
            } else {
                GuardrailParsedResult(
                    isValidJson = false,
                    jsonObject = null,
                    jsonArray = null,
                    rawCleanText = cleanedJson,
                    fallbackBurmeseSpeech = rawInput.ifBlank { DEFAULT_BURMESE_FALLBACK }
                )
            }
        } catch (e: Exception) {
            // Graceful fallback without crashing
            GuardrailParsedResult(
                isValidJson = false,
                jsonObject = null,
                jsonArray = null,
                rawCleanText = cleanedJson,
                fallbackBurmeseSpeech = DEFAULT_BURMESE_FALLBACK
            )
        }
    }
}
