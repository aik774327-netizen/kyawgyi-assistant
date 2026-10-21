package com.example.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenericSystemActionHandlerTest {

    private lateinit var context: Context
    private lateinit var handler: GenericSystemActionHandler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        handler = GenericSystemActionHandler(context)
    }

    @Test
    fun testExtractNumericValue_MyanmarDigits() {
        assertEquals(50, handler.extractNumericValue("အသံ ၅၀ ထား"))
        assertEquals(80, handler.extractNumericValue("brightness ၈၀% တင်"))
        assertEquals(100, handler.extractNumericValue("အသံ ၁၀၀% တင်"))
        assertEquals(25, handler.extractNumericValue("အလင်း ၂၅ ထား"))
    }

    @Test
    fun testExtractNumericValue_MyanmarWordNumbers() {
        assertEquals(50, handler.extractNumericValue("အသံ ငါးဆယ် ထား"))
        assertEquals(50, handler.extractNumericValue("အသံ တစ်ဝက် လျှော့ပါ"))
        assertEquals(100, handler.extractNumericValue("အသံ အပြည့် တင်"))
        assertEquals(0, handler.extractNumericValue("အသံ သုည ထား"))
        assertEquals(20, handler.extractNumericValue("နှစ်ဆယ် တိုး"))
    }

    @Test
    fun testParseVolume_MuteAndMax() {
        val muteAction = handler.parseCommand("အသံ ပိတ်ထားပါ")
        assertNotNull(muteAction)
        assertEquals(SystemSettingTarget.VOLUME, muteAction?.target)
        assertEquals(AdjustmentType.MUTE, muteAction?.adjustmentType)

        val maxAction = handler.parseCommand("အသံ အပြည့် တင်ပါ")
        assertNotNull(maxAction)
        assertEquals(SystemSettingTarget.VOLUME, maxAction?.target)
        assertEquals(AdjustmentType.MAX, maxAction?.adjustmentType)
    }

    @Test
    fun testParseVolume_SetAbsolutePercent() {
        val action = handler.parseCommand("volume ၇၀% ထား")
        assertNotNull(action)
        assertEquals(SystemSettingTarget.VOLUME, action?.target)
        assertEquals(AdjustmentType.SET_ABSOLUTE_PERCENT, action?.adjustmentType)
        assertEquals(70, action?.valuePercent)
    }

    @Test
    fun testParseBrightness_AbsoluteAndRelative() {
        val setAction = handler.parseCommand("မျက်နှာပြင် အလင်းအမှောင် ၆၀ ထား")
        assertNotNull(setAction)
        assertEquals(SystemSettingTarget.BRIGHTNESS, setAction?.target)
        assertEquals(AdjustmentType.SET_ABSOLUTE_PERCENT, setAction?.adjustmentType)
        assertEquals(60, setAction?.valuePercent)

        val increaseAction = handler.parseCommand("အလင်း တိုးပေးပါ")
        assertNotNull(increaseAction)
        assertEquals(SystemSettingTarget.BRIGHTNESS, increaseAction?.target)
        assertEquals(AdjustmentType.INCREASE_DELTA, increaseAction?.adjustmentType)
    }
}
