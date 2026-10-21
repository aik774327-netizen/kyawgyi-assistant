package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BurmeseNumberNormalizerTest {

    @Test
    fun testConvertMyanmarDigitsToWestern() {
        val input = "၀၉၇၈၉၅၃၂၁၀"
        val expected = "0978953210"
        assertEquals(expected, BurmeseNumberNormalizer.convertMyanmarDigitsToWestern(input))
    }

    @Test
    fun testNormalizeSpokenDigits() {
        val input = "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည"
        val normalized = BurmeseNumberNormalizer.normalizeSpokenDigits(input)
        assertEquals("0978953210", normalized)
    }

    @Test
    fun testExtractPhoneNumberFromSpokenSpeech() {
        val sentence1 = "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည ကို ဖုန်းခေါ်ပေးပါ"
        val phone1 = BurmeseNumberNormalizer.extractPhoneNumber(sentence1)
        assertEquals("0978953210", phone1)

        val sentence2 = "မောင်မောင် ၀၉-၄၂၀၀၁၂၃၄၅ ဆီ ဖုန်းခေါ်"
        val phone2 = BurmeseNumberNormalizer.extractPhoneNumber(sentence2)
        assertEquals("09420012345", phone2)

        val sentence3 = "+959 789 532 100 ကို ဆက်ပါ"
        val phone3 = BurmeseNumberNormalizer.extractPhoneNumber(sentence3)
        assertEquals("09789532100", phone3)

        val nonPhoneSentence = "မနက်ဖြန် ရာသီဥတု ဘယ်လိုရှိလဲ"
        assertNull(BurmeseNumberNormalizer.extractPhoneNumber(nonPhoneSentence))
    }

    @Test
    fun testParseSpokenCompoundNumbers() {
        assertEquals(10L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("တစ်ဆယ်"))
        assertEquals(150L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("တစ်ရာ့ငါးဆယ်"))
        assertEquals(500L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("ငါးရာ"))
        assertEquals(25L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("နှစ်ဆယ့်ငါး"))
        assertEquals(15000L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("တစ်သောင်း ငါးထောင်"))
        assertEquals(90L, BurmeseNumberNormalizer.parseSpokenCompoundNumber("ကိုးဆယ်"))
    }

    @Test
    fun testNormalizeSpokenUtterancePercentage() {
        val utterance = "အသံ ငါးဆယ် ရာခိုင်နှုန်း တင်ပါ"
        val normalized = BurmeseNumberNormalizer.normalizeSpokenUtterance(utterance)
        assertEquals("အသံ 50% တင်ပါ", normalized)
    }
}
