package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BurmeseTtsTextNormalizerTest {

    @Test
    fun testNumberToBurmeseWordsWithCreakyToneSandhi() {
        assertEquals("သုည", BurmeseTtsTextNormalizer.numberToBurmeseWords(0))
        assertEquals("တစ်ဆယ်", BurmeseTtsTextNormalizer.numberToBurmeseWords(10))
        assertEquals("တစ်ဆယ့်နှစ်", BurmeseTtsTextNormalizer.numberToBurmeseWords(12))
        assertEquals("ရှစ်ဆယ့်ငါး", BurmeseTtsTextNormalizer.numberToBurmeseWords(85))
        assertEquals("တစ်ရာ", BurmeseTtsTextNormalizer.numberToBurmeseWords(100))
        assertEquals("တစ်ရာ့ငါးဆယ်", BurmeseTtsTextNormalizer.numberToBurmeseWords(150))
        assertEquals("ငါးထောင်", BurmeseTtsTextNormalizer.numberToBurmeseWords(5000))
        assertEquals("တစ်ထောင့်ငါးရာ", BurmeseTtsTextNormalizer.numberToBurmeseWords(1500))
        assertEquals("တစ်သောင်းငါးထောင်", BurmeseTtsTextNormalizer.numberToBurmeseWords(15000))
        assertEquals("တစ်သိန်း", BurmeseTtsTextNormalizer.numberToBurmeseWords(100000))
    }

    @Test
    fun testPercentageNormalization() {
        assertEquals(
            "ရှစ်ဆယ့်ငါး ရာခိုင်နှုန်း",
            BurmeseTtsTextNormalizer.normalizeForTts("85%")
        )
        assertEquals(
            "ဘက်ထရီ ရှစ်ဆယ့်ငါး ရာခိုင်နှုန်း ကျန်ရှိပါသည်",
            BurmeseTtsTextNormalizer.normalizeForTts("ဘက်ထရီ ၈၅% ကျန်ရှိပါသည်")
        )
    }

    @Test
    fun testTimeExpressionNormalization() {
        assertEquals(
            "မွန်းလွဲ တစ်ဆယ့်နှစ် နာရီ သုံးဆယ် မိနစ်",
            BurmeseTtsTextNormalizer.normalizeForTts("12:30 PM")
        )
        assertEquals(
            "မွန်းလွဲ တစ်ဆယ့်နှစ် နာရီ သုံးဆယ် မိနစ်",
            BurmeseTtsTextNormalizer.normalizeForTts("နေ့လည် ၁၂:၃၀")
        )
        assertEquals(
            "နံနက် ကိုး နာရီ တစ်ဆယ့်ငါး မိနစ်",
            BurmeseTtsTextNormalizer.normalizeForTts("09:15 AM")
        )
    }

    @Test
    fun testCurrencyNormalization() {
        assertEquals(
            "ငါးထောင် ကျပ်",
            BurmeseTtsTextNormalizer.normalizeForTts("5,000 MMK")
        )
        assertEquals(
            "တစ်သောင်းငါးထောင် ကျပ်",
            BurmeseTtsTextNormalizer.normalizeForTts("15000 Ks")
        )
    }

    @Test
    fun testPhoneNumberDigitByDigitExpansion() {
        assertEquals(
            "သုည ကိုး ခုနစ် ရှစ် ကိုး ငါး သုံး နှစ် တစ် သုည ထံသို့ ဖုန်းခေါ်ဆိုရန်",
            BurmeseTtsTextNormalizer.normalizeForTts("0978953210 ထံသို့ ဖုန်းခေါ်ဆိုရန်")
        )
    }

    @Test
    fun testAcronymG2pExpansion() {
        assertEquals(
            "ဝိုင်ဖိုင် နှင့် ဘလူးတုသ် ကို ဖွင့်ထားပါသည်",
            BurmeseTtsTextNormalizer.normalizeForTts("Wi-Fi နှင့် Bluetooth ကို ဖွင့်ထားပါသည်")
        )
    }
}
