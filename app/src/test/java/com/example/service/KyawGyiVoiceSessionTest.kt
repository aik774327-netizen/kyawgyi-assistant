package com.example.service

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KyawGyiVoiceSessionTest {

    @Test
    fun testParseReadScreenCommands() {
        // Test primary requested Burmese command
        val cmd1 = MyanmarCommands.parse("မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြပါ")
        assertNotNull(cmd1)
        assertEquals(MyanmarCommandType.READ_SCREEN, cmd1?.type)

        // Test variations
        val cmd2 = MyanmarCommands.parse("မျက်နှာပြင်ပေါ်က စာတွေ ဖတ်ပြပါ")
        assertNotNull(cmd2)
        assertEquals(MyanmarCommandType.READ_SCREEN, cmd2?.type)

        val cmd3 = MyanmarCommands.parse("screen ပေါ်က စာဖတ်ပြပါ")
        assertNotNull(cmd3)
        assertEquals(MyanmarCommandType.READ_SCREEN, cmd3?.type)

        val cmd4 = MyanmarCommands.parse("မျက်နှာပြင် ဖတ်ပြပါ")
        assertNotNull(cmd4)
        assertEquals(MyanmarCommandType.READ_SCREEN, cmd4?.type)

        // Test with wake word
        val cmdWithWake = MyanmarCommands.parse("ကျော်ကြီး မျက်နှာပြင်ပေါ်ရှိ စာများကို ဖတ်ပြပါ")
        assertNotNull(cmdWithWake)
        assertEquals(MyanmarCommandType.READ_SCREEN, cmdWithWake?.type)
        assertTrue(cmdWithWake?.isWake == true)
    }

    @Test
    fun testAssistScreenDataParser_nullStructure() {
        val result = AssistScreenDataParser.parse(null)
        assertNotNull(result)
        assertEquals("", result.fullText)
        assertEquals(0, result.nodeCount)
        assertTrue(result.textSnippets.isEmpty())
        assertTrue(result.windowTitles.isEmpty())
        assertNull(result.webDomain)
    }

    @Test
    fun testKyawGyiVoiceSession_screenDataStorageAndReadAloud() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. When empty
        KyawGyiVoiceSession.setParsedScreenDataForTest(null)
        val emptyResult = KyawGyiVoiceSession.readScreenContentAloud(context)
        assertTrue(emptyResult.contains("ဖတ်ပြစရာ စာသား မတွေ့ရှိပါ"))

        // 2. When screen text is present
        val testData = AssistScreenDataParser.ParsedScreenData(
            fullText = "မင်္ဂလာပါ\nအကောင့်လက်ကျန်ငွေ - ၅၀၀၀၀ ကျပ်\nလွှဲပြောင်းရန် ခလုတ်",
            textSnippets = listOf("မင်္ဂလာပါ", "အကောင့်လက်ကျန်ငွေ - ၅၀၀၀၀ ကျပ်", "လွှဲပြောင်းရန် ခလုတ်"),
            windowTitles = listOf("KBZPay"),
            webDomain = null,
            nodeCount = 5
        )
        KyawGyiVoiceSession.setParsedScreenDataForTest(testData)

        assertEquals("မင်္ဂလာပါ\nအကောင့်လက်ကျန်ငွေ - ၅၀၀၀၀ ကျပ်\nလွှဲပြောင်းရန် ခလုတ်", KyawGyiVoiceSession.getLatestScreenText())

        var spokenCaptured: String? = null
        val spokenResult = KyawGyiVoiceSession.readScreenContentAloud(context) { text ->
            spokenCaptured = text
        }

        assertTrue(spokenResult.contains("မျက်နှာပြင်ပေါ်ရှိ စာသားများမှာ အောက်ပါအတိုင်း ဖြစ်ပါသည်ခင်ဗျာ"))
        assertTrue(spokenResult.contains("အကောင့်လက်ကျန်ငွေ - ၅၀၀၀၀ ကျပ်"))
        assertEquals(spokenResult, spokenCaptured)
    }

    @Test
    fun testKyawGyiVoiceSession_flutterCanvasOcrFallback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Simulate a Flutter / Unity Canvas app where AssistStructure produces no text
        KyawGyiVoiceSession.setParsedScreenDataForTest(null)

        // When cached OCR parsed screen data is set from ML Kit vision
        val ocrParsed = AssistScreenDataParser.ParsedScreenData(
            fullText = "Flutter App Canvas Header\nTotal Balance: 250,000 MMK\nConfirm Payment",
            textSnippets = listOf("Flutter App Canvas Header", "Total Balance: 250,000 MMK", "Confirm Payment"),
            windowTitles = listOf("Canvas / Flutter Screen (ML Kit OCR)"),
            webDomain = null,
            nodeCount = 3
        )
        KyawGyiVoiceSession.setParsedScreenDataForTest(ocrParsed)

        val result = KyawGyiVoiceSession.readScreenContentAloud(context)
        assertTrue(result.contains("Total Balance: 250,000 MMK"))
        assertTrue(result.contains("Flutter App Canvas Header"))
    }
}
