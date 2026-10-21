package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BurmeseNluPreprocessorTest {

    @Test
    fun testNormalizeInvisibleCharsAndDialects() {
        val raw = "ဟေး\u200Bကျော်ကြီး\u200C ဖွင့်ပေးပါဦး"
        val normalized = BurmeseNluPreprocessor.normalize(raw)

        assertTrue("Should clean zero width characters", !normalized.contains("\u200B"))
        assertTrue("Should normalize ဖွင့် to ဖွင့်", normalized.contains("ဖွင့်"))
        assertTrue("Should normalize ဦး to ဦး/ပါ", normalized.contains("ပိတ်ပါ") || normalized.contains("ဖွင့်ပေးပါ"))
    }

    @Test
    fun testSegmentSyllables() {
        val text = "ဖုန်းခေါ်ပါ"
        val syllables = BurmeseNluPreprocessor.segmentSyllables(text)

        assertTrue("Should segment into syllables", syllables.isNotEmpty())
        assertTrue("Should contain ဖုန်း syllable", syllables.contains("ဖုန်း"))
        assertTrue("Should contain ခေါ် syllable", syllables.contains("ခေါ်"))
    }

    @Test
    fun testExtractSlotsCall() {
        val text = "မောင်မောင် ဆီ ဖုန်းခေါ်ပေးပါ"
        val slots = BurmeseNluPreprocessor.extractSlots(text)

        assertEquals("ACTION_CALL", slots.intentCandidate)
        assertNotNull(slots.contactName)
        assertTrue("Contact name should contain မောင်မောင်", slots.contactName?.contains("မောင်မောင်") == true)
    }

    @Test
    fun testExtractSlotsWifi() {
        val text = "ဝိုင်ဖိုင် ပိတ်ပါ"
        val slots = BurmeseNluPreprocessor.extractSlots(text)

        assertEquals("ACTION_WIFI", slots.intentCandidate)
        assertEquals("OFF", slots.settingValue)
    }

    @Test
    fun testExtractSlotsApp() {
        val text = "YouTube ဖွင့်ပေးပါ"
        val slots = BurmeseNluPreprocessor.extractSlots(text)

        assertEquals("ACTION_OPEN_APP", slots.intentCandidate)
        assertTrue(slots.targetApp?.contains("YouTube") == true)
    }
}
