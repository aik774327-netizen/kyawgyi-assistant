package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.FailureCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenOcrEngineTest {

    @Before
    fun setUp() {
        FailureMemoryEngine.clearAll()
    }

    // =========================================================================
    // 1. SCRIPT DETECTION & UNICODE VARIANTS
    // =========================================================================

    @Test
    fun testNativeAndroidMyanmarText_detection() {
        val nativeText = "အကောင့်လက်ကျန်ငွေ - ၅၀၀၀၀ ကျပ်"
        val script = MyanmarScriptDetector.detectScript(nativeText)
        assertEquals(MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE, script)
    }

    @Test
    fun testFlutterMyanmarUI_detection() {
        val flutterUiText = "ဆက်လက်လုပ်ဆောင်ရန် ခလုတ်ကို နှိပ်ပါ"
        val script = MyanmarScriptDetector.detectScript(flutterUiText)
        assertEquals(MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE, script)
    }

    @Test
    fun testCanvasDrawnMyanmarText_detection() {
        val canvasText = "မြန်မာယူနီကုဒ် ဂရပ်ဖစ် ကင်းဗတ်စ်"
        val script = MyanmarScriptDetector.detectScript(canvasText)
        assertEquals(MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE, script)
    }

    @Test
    fun testWebViewMyanmarText_detection() {
        val webViewText = "သတင်းနှင့် မီဒီယာ ကဏ္ဍ"
        val script = MyanmarScriptDetector.detectScript(webViewText)
        assertEquals(MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE, script)
    }

    @Test
    fun testMixedEnglishAndMyanmar_detection() {
        val mixedText = "KBZPay Total Balance: ၂၅၀၀၀၀ MMK"
        val script = MyanmarScriptDetector.detectScript(mixedText)
        assertEquals(MyanmarScriptDetector.ScriptType.MIXED, script)
    }

    @Test
    fun testUnicodeVariants_detection() {
        // Test complex ligatures, medials (ya-pin, ya-yit, wa-hswae, ha-hto), independent vowels
        val complexVariants = listOf(
            "ကြောင်ကလေးများ ချစ်စရာကောင်းသည်",
            "လျှပ်စစ်မီး လာရောက်ပါပြီ",
            "ဥပဒေပညာရှင် များနှင့် ဆွေးနွေးပွဲ",
            "ဣန္ဒြေသိက္ခာ စောင့်ထိန်းကြပါ",
            "ဩဘာသံများ ညံညံဝေ"
        )
        for (variant in complexVariants) {
            val script = MyanmarScriptDetector.detectScript(variant)
            assertTrue("Variant '$variant' should be recognized as Myanmar Unicode",
                script == MyanmarScriptDetector.ScriptType.MYANMAR_UNICODE || script == MyanmarScriptDetector.ScriptType.MIXED)
        }
    }

    @Test
    fun testLatinScript_detection() {
        val latinText = "Settings -> Wi-Fi -> Advanced Options"
        val script = MyanmarScriptDetector.detectScript(latinText)
        assertEquals(MyanmarScriptDetector.ScriptType.LATIN, script)
    }

    @Test
    fun testLatinOcrGarbageApproximation_rejection() {
        // When ML Kit tries to read Burmese circles using a Latin-only model,
        // it produces strings like "o c oo e oco". We must reject this as garbage.
        val garbage1 = "o c oo e oco"
        val garbage2 = "co oc ooo eo"
        val validEnglish = "Settings Wi-Fi Mobile Network"

        assertTrue(MyanmarScriptDetector.isProbableLatinOcrGarbage(garbage1))
        assertTrue(MyanmarScriptDetector.isProbableLatinOcrGarbage(garbage2))
        assertFalse(MyanmarScriptDetector.isProbableLatinOcrGarbage(validEnglish))
    }

    // =========================================================================
    // 2. FAILURE MEMORY ENGINE & ROOT CAUSE AUDITING
    // =========================================================================

    @Test
    fun testFailureMemoryEngine_recordAndRecall() {
        assertFalse(FailureMemoryEngine.hasPreviousFailure("SCREEN_OCR", "OFFLINE_MYANMAR_OCR"))

        // Record a real resource failure
        val record = FailureMemoryEngine.recordFailure(
            component = "SCREEN_OCR",
            operation = "OFFLINE_MYANMAR_OCR",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "On-Device Google ML Kit တွင် မြန်မာယူနီကုဒ် မပါရှိခြင်း",
            technicalDetails = "No on-device Burmese model found in ML Kit Vision",
            suggestedRemediationMm = "အင်တာနက် ဖွင့်၍ Multimodal Vision သုံးပါ"
        )

        assertNotNull(record)
        assertEquals(1, record.occurrences)
        assertTrue(FailureMemoryEngine.hasPreviousFailure("SCREEN_OCR", "OFFLINE_MYANMAR_OCR"))

        // Record the same failure again (should increment occurrence)
        val repeatRecord = FailureMemoryEngine.recordFailure(
            component = "SCREEN_OCR",
            operation = "OFFLINE_MYANMAR_OCR",
            category = FailureCategory.RESOURCE,
            rootCauseMm = "On-Device Google ML Kit တွင် မြန်မာယူနီကုဒ် မပါရှိခြင်း",
            technicalDetails = "No on-device Burmese model found in ML Kit Vision",
            suggestedRemediationMm = "အင်တာနက် ဖွင့်၍ Multimodal Vision သုံးပါ"
        )

        assertEquals(2, repeatRecord.occurrences)

        // Verify formatted Burmese warning for repeated failure
        val warning = FailureMemoryEngine.getRepeatWarningIfFailed("SCREEN_OCR", "OFFLINE_MYANMAR_OCR")
        assertNotNull(warning)
        assertTrue(warning!!.contains("ယခင်က မအောင်မြင်ခဲ့သော လုပ်ဆောင်ချက် ဖြစ်ပါသည်"))
        assertTrue(warning.contains("အရင်းအမြစ်ဆိုင်ရာ ကန့်သတ်ချက်"))
    }

    // =========================================================================
    // 3. ON-DEVICE GLYPH & VISUAL ENGINE (NO FAKE RESULTS)
    // =========================================================================

    @Test
    fun testOnDeviceGlyphEngine_blankScreen_detection() {
        // Create solid black bitmap simulating FLAG_SECURE or screen off
        val blackBitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blackBitmap)
        canvas.drawColor(Color.BLACK)

        val result = OnDeviceMyanmarGlyphEngine.evaluateOffline(blackBitmap)
        assertFalse("Blank screen must not report success", result.isSuccess)
        assertEquals(FailureCategory.PRAGMATIC_CONTEXTUAL, result.failureCategory)
        assertTrue(result.userMessageMm.contains("စာသား သို့မဟုတ် အချက်အလက် မတွေ့ရှိရပါ"))
        assertTrue(FailureMemoryEngine.hasPreviousFailure("SCREEN_OCR", "VISUAL_ANALYSIS"))
    }

    @Test
    fun testOnDeviceGlyphEngine_screenWithContent_truthfulOfflineResourceLimit() {
        // Create a pixel-backed bitmap with alternating stripes simulating UI elements
        val colors = IntArray(400 * 800) { i ->
            val y = i / 400
            val x = i % 400
            if (y in 100..300 && (x % 32 < 16)) Color.BLACK else Color.WHITE
        }
        val testBitmap = Bitmap.createBitmap(colors, 400, 800, Bitmap.Config.ARGB_8888)

        val result = OnDeviceMyanmarGlyphEngine.evaluateOffline(testBitmap)

        // Strict rule: NO FAKE SUCCESS, NO EMPTY BLANK WITHOUT EXPLANATION
        assertFalse("Offline Myanmar OCR without neural model must not pretend success", result.isSuccess)
        assertEquals("Expected RESOURCE but got ${result.failureCategory}: diagnostic=${result.technicalDiagnostic}",
            FailureCategory.RESOURCE, result.failureCategory)
        assertTrue(result.userMessageMm.contains("အော့ဖ်လိုင်း အခြေအနေတွင် မြန်မာယူနီကုဒ် အသေးစိတ် ဖတ်ရှုနိုင်ရန်အတွက် အင်တာနက်လိုင်း လိုအပ်ပါသည်"))
        assertTrue(FailureMemoryEngine.hasPreviousFailure("SCREEN_OCR", "OFFLINE_MYANMAR_OCR"))
    }

    // =========================================================================
    // 4. HYBRID OCR PIPELINE & VOICE SESSION INTEGRATION
    // =========================================================================

    @Test
    fun testHybridScreenOcrEngine_processOfflineCanvasBitmap() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val colors = IntArray(400 * 800) { i ->
            val y = i / 400
            val x = i % 400
            if (y in 100..300 && (x % 32 < 16)) Color.BLACK else Color.WHITE
        }
        val testBitmap = Bitmap.createBitmap(colors, 400, 800, Bitmap.Config.ARGB_8888)

        val result = ScreenOcrEngine.processScreenBitmap(testBitmap, context)

        // In unit test environment without real internet connection to Gemini API,
        // it must safely and truthfully fall back to On-Device Glyph Engine
        assertNotNull(result)
        assertNotNull(result.userMessageMm)
        assertTrue(result.userMessageMm.isNotBlank())
    }
}
