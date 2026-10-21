package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BurmesePhoneticLexiconTest {

    @Test
    fun testExactAndFuzzyTermResolution() {
        // 1. Wi-Fi phonetic variants
        val wifi1 = BurmesePhoneticLexicon.resolveTerm("ဝိုင်ဖာ")
        assertNotNull(wifi1)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_WIFI, wifi1?.category)
        assertEquals("ဝိုင်ဖိုင်", wifi1?.canonicalMyanmar)

        val wifi2 = BurmesePhoneticLexicon.resolveTerm("Wi-Fi")
        assertNotNull(wifi2)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_WIFI, wifi2?.category)

        // 2. Bluetooth phonetic variants
        val bt1 = BurmesePhoneticLexicon.resolveTerm("ဘလူးတု")
        assertNotNull(bt1)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_BLUETOOTH, bt1?.category)
        assertEquals("ဘလူးတုသ်", bt1?.canonicalMyanmar)

        val bt2 = BurmesePhoneticLexicon.resolveTerm("blue tooth")
        assertNotNull(bt2)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_BLUETOOTH, bt2?.category)

        // 3. YouTube phonetic variants
        val yt1 = BurmesePhoneticLexicon.resolveTerm("ယူကျူ့")
        assertNotNull(yt1)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.APP_YOUTUBE, yt1?.category)
        assertEquals("ယူကျု", yt1?.canonicalMyanmar)

        // 4. Mobile Data variants
        val data1 = BurmesePhoneticLexicon.resolveTerm("ဒါတာ")
        assertNotNull(data1)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_MOBILE_DATA, data1?.category)
        assertEquals("မိုဘိုင်းဒေတာ", data1?.canonicalMyanmar)

        // 5. Volume variants
        val vol1 = BurmesePhoneticLexicon.resolveTerm("ဗော်လျံ")
        assertNotNull(vol1)
        assertEquals(BurmesePhoneticLexicon.TargetCategory.SETTING_VOLUME, vol1?.category)
        assertEquals("အသံပမာဏ", vol1?.canonicalMyanmar)
    }

    @Test
    fun testNormalizeCodeSwitchingInFullUtterance() {
        assertEquals(
            "ဝိုင်ဖိုင် ဖွင့်ပေးပါ",
            BurmesePhoneticLexicon.normalizeCodeSwitching("ဝိုင်ဖာ ဖွင့်ပေးပါ")
        )

        assertEquals(
            "ဘလူးတုသ် ပိတ်ပါ",
            BurmesePhoneticLexicon.normalizeCodeSwitching("blue tooth ပိတ်ပါ")
        )

        assertEquals(
            "ယူကျု ဖွင့်ပေးပါ",
            BurmesePhoneticLexicon.normalizeCodeSwitching("YouTube ဖွင့်ပေးပါ")
        )

        assertEquals(
            "မိုဘိုင်းဒေတာ ဖွင့်ပါ",
            BurmesePhoneticLexicon.normalizeCodeSwitching("ဒါတာ ဖွင့်ပါ")
        )

        assertEquals(
            "အသံပမာဏ 50% တင်ပါ",
            BurmeseNluPreprocessor.normalize("ဗော်လျံ ငါးဆယ် ရာခိုင်နှုန်း တင်ပါ")
        )
    }
}
