package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalBurmeseIntentParserTest {

    private lateinit var parser: LocalBurmeseIntentParser

    @Before
    fun setup() {
        parser = LocalBurmeseIntentParser.getInstance()
    }

    @Test
    fun testVolumeControlIntents() {
        val up = parser.parse("အသံတိုးပေးပါ")
        assertEquals(KyawGyiIntent.VOLUME_UP, up.intent)
        assertEquals("+1", up.slots["delta"])

        val down = parser.parse("အသံလျှော့ပါ")
        assertEquals(KyawGyiIntent.VOLUME_DOWN, down.intent)
        assertEquals("-1", down.slots["delta"])

        val mute = parser.parse("အသံပိတ်ပါ")
        assertEquals(KyawGyiIntent.MUTE, mute.intent)
    }

    @Test
    fun testCallIntentAndSlotExtraction() {
        val call = parser.parse("မောင်မောင်ဆီ ဖုန်းခေါ်ပေးပါ")
        assertEquals(KyawGyiIntent.CALL, call.intent)
        assertEquals("ACTION_CALL", call.intentName)
        assertEquals("မောင်မောင်", call.slots["contact_name"])
    }

    @Test
    fun testWifiAndBluetoothSettings() {
        val wifiOn = parser.parse("ဝိုင်ဖိုင် ဖွင့်ပေးပါ")
        assertEquals(KyawGyiIntent.OPEN_SETTINGS, wifiOn.intent)
        assertEquals("WIFI", wifiOn.slots["target"])
        assertEquals("ON", wifiOn.slots["state"])

        val btOff = parser.parse("ဘလူးတုသ် ပိတ်ပါ")
        assertEquals(KyawGyiIntent.OPEN_SETTINGS, btOff.intent)
        assertEquals("BLUETOOTH", btOff.slots["target"])
        assertEquals("OFF", btOff.slots["state"])
    }

    @Test
    fun testMediaControls() {
        val play = parser.parse("သီချင်းဖွင့်ပါ")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, play.intent)

        val stop = parser.parse("ရပ်ပါ")
        assertEquals(KyawGyiIntent.STOP, stop.intent)
    }

    @Test
    fun testFuzzyMatching_ASRVariations() {
        // "ဝိုင်ဖာ ဖွင့်" should fuzzy match "ဝိုင်ဖိုင်"
        val wifiFuzzy = parser.parse("ဝိုင်ဖာ ဖွင့်")
        assertEquals("ACTION_WIFI", wifiFuzzy.intentName)
        assertEquals("ON", wifiFuzzy.slots["state"])
        assertEquals(LocalBurmeseIntentParser.MatchType.FUZZY_MATCH, wifiFuzzy.matchType)

        // "ဖေ့ဘွတ် ဖွင့်" should fuzzy match "Facebook"
        val fbFuzzy = parser.parse("ဖေ့ဘွတ် ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, fbFuzzy.intent)
        assertEquals("Facebook", fbFuzzy.slots["app_name"])
        assertEquals(LocalBurmeseIntentParser.MatchType.FUZZY_MATCH, fbFuzzy.matchType)

        // "တစ်တော ဖွင့်" should fuzzy match "TikTok"
        val tiktokFuzzy = parser.parse("တစ်တော ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, tiktokFuzzy.intent)
        assertEquals("TikTok", tiktokFuzzy.slots["app_name"])
        assertEquals(LocalBurmeseIntentParser.MatchType.FUZZY_MATCH, tiktokFuzzy.matchType)
    }

    @Test
    fun testLevenshteinDistanceCalculation() {
        val dist = parser.levenshteinDistance("ဝိုင်ဖိုင်", "ဝိုင်ဖာ")
        assertTrue("Distance should be small for slight variation", dist in 1..4)

        val sim = parser.calculateSimilarity("Facebook", "facebook")
        // Since Levenshtein is case-sensitive internally:
        assertTrue("Similarity should be high", sim >= 0.85f)
    }
}
