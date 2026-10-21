package com.example.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat

/**
 * KYAW GYI VOICE SESSION
 * Captures screen context (AssistStructure / AssistContent) and routes assistant prompts.
 * Features an AssistStructure.ViewNode recursive parser to extract and read aloud on-screen text.
 */
class KyawGyiVoiceSession(context: Context) : VoiceInteractionSession(context) {

    private val sessionTag = "KyawGyiVoiceSession"

    companion object {
        private const val TAG = "KyawGyiVoiceSession"

        @Volatile
        var activeSessionInstance: KyawGyiVoiceSession? = null
            private set

        @Volatile
        var lastParsedScreenData: AssistScreenDataParser.ParsedScreenData? = null
            private set

        @Volatile
        var cachedScreenshot: Bitmap? = null

        fun getLatestScreenText(): String {
            return lastParsedScreenData?.fullText ?: ""
        }

        fun setParsedScreenDataForTest(data: AssistScreenDataParser.ParsedScreenData?) {
            lastParsedScreenData = data
        }

        /**
         * Reads the current screen text aloud using Myanmar TTS.
         * Falls back to On-Device Google ML Kit Vision OCR when AssistStructure / ViewNode
         * text is empty (e.g. Flutter, Unity, WebView, or Canvas apps).
         * Returns the spoken text or informative message.
         */
        fun readScreenContentAloud(
            context: Context,
            onSpoken: ((String) -> Unit)? = null
        ): String {
            var currentScreenText = getLatestScreenText().ifEmpty {
                KyawGyiAccessibilityService.instance?.extractCurrentScreenText() ?: ""
            }

            // =========================================================
            // SECONDARY FALLBACK: ON-DEVICE GOOGLE ML KIT VISION OCR
            // For Flutter, Unity, Custom Canvas, WebView where Accessibility Nodes are blank
            // =========================================================
            if (currentScreenText.isBlank()) {
                Log.i(TAG, "AssistStructure and Accessibility nodes returned empty text. Triggering On-Device ML Kit OCR fallback...")

                // 1. Try cached screenshot from Assist onHandleScreenshot
                val screenshot = activeSessionInstance?.latestScreenshot ?: cachedScreenshot
                if (screenshot != null) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    var ocrExtracted = ""
                    com.example.ocr.ScreenOcrEngine.extractTextAsync(screenshot, context) { result ->
                        if (result.isSuccess && result.text.isNotBlank()) {
                            ocrExtracted = result.text
                        } else if (result.userMessageMm.isNotBlank()) {
                            ocrExtracted = result.userMessageMm
                        }
                        latch.countDown()
                    }
                    try {
                        latch.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR latch timeout: ${e.message}")
                    }

                    if (ocrExtracted.isNotBlank()) {
                        currentScreenText = ocrExtracted
                        lastParsedScreenData = AssistScreenDataParser.ParsedScreenData(
                            fullText = ocrExtracted,
                            textSnippets = ocrExtracted.lines().filter { it.isNotBlank() },
                            windowTitles = listOf("Canvas / Flutter Screen (ML Kit OCR)"),
                            webDomain = null,
                            nodeCount = ocrExtracted.lines().size
                        )
                    }
                }

                // 2. If still empty, try taking screenshot via AccessibilityService (Android 11+)
                if (currentScreenText.isBlank()) {
                    val accessibilityService = KyawGyiAccessibilityService.instance
                    if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var ocrExtracted = ""
                        accessibilityService.extractScreenTextWithOcrFallback { text ->
                            ocrExtracted = text
                            latch.countDown()
                        }
                        try {
                            latch.await(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } catch (e: Exception) {
                            Log.w(TAG, "Accessibility OCR fallback timeout: ${e.message}")
                        }

                        if (ocrExtracted.isNotBlank()) {
                            currentScreenText = ocrExtracted
                            lastParsedScreenData = AssistScreenDataParser.ParsedScreenData(
                                fullText = ocrExtracted,
                                textSnippets = ocrExtracted.lines().filter { it.isNotBlank() },
                                windowTitles = listOf("Dynamic Screenshot (ML Kit OCR)"),
                                webDomain = null,
                                nodeCount = ocrExtracted.lines().size
                            )
                        }
                    }
                }
            }

            val responseText = if (currentScreenText.isBlank()) {
                "မျက်နှာပြင်ပေါ်တွင် ဖတ်ပြစရာ စာသား မတွေ့ရှိပါခင်ဗျာ"
            } else {
                val readableSnippet = if (currentScreenText.length > 500) {
                    currentScreenText.take(500) + " ... စသည်ဖြင့် မျက်နှာပြင်ပေါ်တွင် ရေးသားထားပါသည်ခင်ဗျာ"
                } else {
                    currentScreenText
                }
                "မျက်နှာပြင်ပေါ်ရှိ စာသားများမှာ အောက်ပါအတိုင်း ဖြစ်ပါသည်ခင်ဗျာ။ $readableSnippet"
            }

            try {
                val tts = com.example.utils.MyanmarTTSEngine.getInstance(context)
                tts.speak(responseText)
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking screen content: ${e.message}")
            }

            onSpoken?.invoke(responseText)
            return responseText
        }
    }

    @Volatile
    var latestScreenshot: Bitmap? = null
        private set

    override fun onCreate() {
        super.onCreate()
        activeSessionInstance = this
        Log.i(sessionTag, "Voice Session created")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeSessionInstance == this) {
            activeSessionInstance = null
        }
        Log.i(sessionTag, "Voice Session destroyed")
    }

    @Deprecated("Deprecated in Java", ReplaceWith("onHandleAssist"))
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        Log.i(sessionTag, "Assist triggered! Screen context received. Data: $data, Content: $content")

        // Parse screen data using AssistStructure.ViewNode recursive parser
        val parsed = AssistScreenDataParser.parse(structure)
        lastParsedScreenData = parsed
        Log.i(
            sessionTag,
            "Parsed screen data: ${parsed.textSnippets.size} text snippets, ${parsed.nodeCount} nodes. Title: ${parsed.windowTitles}"
        )

        // Check if command is requested directly (e.g. from assist bundle)
        val command = data?.getString("command") ?: ""
        if (command.contains("ဖတ်") && (command.contains("မျက်နှာပြင်") || command.contains("screen") || command.contains("စာ"))) {
            readScreenContentAloud(context)
            return
        }

        // Trigger Assistant Voice Controller / Floating Overlay
        try {
            val serviceIntent = Intent(context, KyawGyiFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(sessionTag, "Failed to start floating assistant", e)
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        Log.i(sessionTag, "Screenshot received for screen analysis: ${screenshot != null}")
        latestScreenshot = screenshot
        cachedScreenshot = screenshot

        // If screen nodes are empty (e.g. Flutter, Unity, OpenGL canvas, WebView),
        // perform proactive background OCR extraction so screen text is instantly ready
        if (screenshot != null && (lastParsedScreenData == null || lastParsedScreenData?.fullText.isNullOrBlank())) {
            com.example.ocr.ScreenOcrEngine.extractTextAsync(screenshot, context) { ocrResult ->
                if (ocrResult.isSuccess && ocrResult.text.isNotBlank()) {
                    val ocrText = ocrResult.text
                    Log.i(sessionTag, "Proactive Hybrid OCR extracted ${ocrText.length} characters (${ocrResult.scriptType})")
                    lastParsedScreenData = AssistScreenDataParser.ParsedScreenData(
                        fullText = ocrText,
                        textSnippets = ocrText.lines().filter { it.isNotBlank() },
                        windowTitles = listOf("Dynamic Canvas / Flutter (${ocrResult.engineUsed})"),
                        webDomain = null,
                        nodeCount = ocrText.lines().size
                    )
                }
            }
        }
    }

    /**
     * Reads screen text aloud directly from this session instance
     */
    fun readScreenAloud(ttsCallback: ((String) -> Unit)? = null): String {
        return readScreenContentAloud(context, ttsCallback)
    }
}

/**
 * ASSIST SCREEN DATA PARSER
 * Provides recursive parsing over Android AssistStructure and AssistStructure.ViewNode
 * to reliably extract readable text snippets, hints, titles, and web domains.
 */
object AssistScreenDataParser {

    data class ParsedScreenData(
        val fullText: String,
        val textSnippets: List<String>,
        val windowTitles: List<String>,
        val webDomain: String?,
        val nodeCount: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Parses an AssistStructure into a clean, structured ParsedScreenData object.
     */
    fun parse(structure: AssistStructure?): ParsedScreenData {
        if (structure == null) {
            return ParsedScreenData(
                fullText = "",
                textSnippets = emptyList(),
                windowTitles = emptyList(),
                webDomain = null,
                nodeCount = 0
            )
        }

        val texts = mutableListOf<String>()
        val titles = mutableListOf<String>()
        var webDomain: String? = null
        var totalNodes = 0

        val windowCount = structure.windowNodeCount
        for (i in 0 until windowCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val windowTitle = windowNode.title?.toString()?.trim()
            if (!windowTitle.isNullOrEmpty() && !titles.contains(windowTitle)) {
                titles.add(windowTitle)
            }

            val rootNode = windowNode.rootViewNode
            if (rootNode != null) {
                totalNodes += parseViewNodeRecursive(rootNode, texts, titles) { domain ->
                    if (webDomain == null && !domain.isNullOrBlank()) {
                        webDomain = domain
                    }
                }
            }
        }

        // Deduplicate while preserving reading order
        val distinctSnippets = mutableListOf<String>()
        for (t in texts) {
            val trimmed = t.trim()
            if (trimmed.isNotEmpty() && !distinctSnippets.contains(trimmed)) {
                distinctSnippets.add(trimmed)
            }
        }

        val fullText = distinctSnippets.joinToString(separator = "\n")
        return ParsedScreenData(
            fullText = fullText,
            textSnippets = distinctSnippets,
            windowTitles = titles,
            webDomain = webDomain,
            nodeCount = totalNodes
        )
    }

    /**
     * Recursive parser for AssistStructure.ViewNode hierarchy.
     * Recursively traverses every child view node, extracting visible texts, content descriptions, and hints.
     */
    fun parseViewNodeRecursive(
        node: AssistStructure.ViewNode,
        outTexts: MutableList<String>,
        outTitles: MutableList<String>,
        onDomainFound: (String?) -> Unit = {}
    ): Int {
        var count = 1

        // Check visibility: visible nodes in Android standard view have visibility == View.VISIBLE (0)
        val isVisible = node.visibility == View.VISIBLE

        if (isVisible) {
            val nodeText = node.text?.toString()?.trim()
            if (!nodeText.isNullOrEmpty()) {
                outTexts.add(nodeText)
            }

            val contentDesc = node.contentDescription?.toString()?.trim()
            if (!contentDesc.isNullOrEmpty() && contentDesc != nodeText) {
                outTexts.add(contentDesc)
            }

            val hint = node.hint?.trim()
            if (!hint.isNullOrEmpty() && hint != nodeText && hint != contentDesc) {
                outTexts.add(hint)
            }

            node.webDomain?.let { onDomainFound(it) }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChildAt(i)
            if (child != null) {
                count += parseViewNodeRecursive(child, outTexts, outTitles, onDomainFound)
            }
        }

        return count
    }
}
