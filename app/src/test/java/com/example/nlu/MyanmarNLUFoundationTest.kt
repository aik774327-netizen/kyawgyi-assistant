package com.example.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage 1 - Myanmar Language Understanding Foundation Test Suite
 * Validates 50+ natural Myanmar command variations, colloquial phrasing,
 * polite particles, filler words, code-switching, and entity extraction.
 */
class MyanmarNLUFoundationTest {

    private lateinit var nlu: MyanmarNLUFoundation

    @Before
    fun setUp() {
        nlu = MyanmarNLUFoundation()
    }

    // =========================================================================
    // 1. OPEN_CAMERA VARIATIONS (9 tests)
    // =========================================================================

    @Test
    fun testCamera_directBurmese() {
        val cmd = nlu.process("ကင်မရာဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
        assertTrue(cmd.confidence >= 0.80f)
    }

    @Test
    fun testCamera_politeRequest() {
        val cmd = nlu.process("ကင်မရာဖွင့်ပေး")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
        assertTrue(cmd.confidence >= 0.80f)
    }

    @Test
    fun testCamera_softPolite() {
        val cmd = nlu.process("ကင်မရာလေးဖွင့်ပေးပါ")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_mixedEnglishBurmese() {
        val cmd = nlu.process("Camera ဖွင့်လိုက်")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_formalParticle() {
        val cmd = nlu.process("ကင်မရာကို ဖွင့်ပေးပါဦး")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_withFillerAndGreeting() {
        val cmd = nlu.process("ဟေး ကျော်ကြီး ဟိုလေ ကင်မရာ ဖွင့်ပေးပါဦး")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_takePhotoPhrase() {
        val cmd = nlu.process("ဓာတ်ပုံလေး ရိုက်မယ်")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_englishDirect() {
        val cmd = nlu.process("Open camera")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    @Test
    fun testCamera_launchImperative() {
        val cmd = nlu.process("ကင်မရာ ဖွင့်စမ်းပါ")
        assertEquals(KyawGyiIntent.OPEN_CAMERA, cmd.intent)
    }

    // =========================================================================
    // 2. CALL INTENT & ENTITY EXTRACTION (12 tests)
    // =========================================================================

    @Test
    fun testCall_simplePolite() {
        val cmd = nlu.process("ဖုန်းဆက်ပေးပါ")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
    }

    @Test
    fun testCall_casual() {
        val cmd = nlu.process("ဖုန်းဆက်ပေး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
    }

    @Test
    fun testCall_withEndParticle() {
        val cmd = nlu.process("ဖုန်းဆက်ပေးဦး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
    }

    @Test
    fun testCall_softPolite() {
        val cmd = nlu.process("ဖုန်းလေးဆက်ပေးပါဦး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
    }

    @Test
    fun testCall_withColloquialFiller() {
        val cmd = nlu.process("တစ်ချက်လောက် ဖုန်းဆက်ပေး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
    }

    @Test
    fun testCall_withMotherContact() {
        val cmd = nlu.process("မေမေ့ကို ဖုန်းဆက်ပေးပါ")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("မေမေ", cmd.contact)
        assertTrue(cmd.confidence >= 0.85f)
    }

    @Test
    fun testCall_withMotherContactAndParticles() {
        val cmd = nlu.process("မေမေ့ကို ဖုန်းလေးဆက်ပေးပါဦး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("မေမေ", cmd.contact)
        assertEquals(PolitenessLevel.POLITE, cmd.semanticRepresentation?.politeness)
    }

    @Test
    fun testCall_withFatherContact() {
        val cmd = nlu.process("ဖေဖေ့ဆီ ဖုန်းခေါ်လိုက်")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("ဖေဖေ", cmd.contact)
    }

    @Test
    fun testCall_withPhoneNumber() {
        val cmd = nlu.process("0912345678 ကို ဖုန်းဆက်")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("0912345678", cmd.phoneNumber)
    }

    @Test
    fun testCall_withBurmeseNumerals() {
        val cmd = nlu.process("၀၉၇၉၁၂၃၄၅၆၇ ကို ဖုန်းခေါ်")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("09791234567", cmd.phoneNumber)
    }

    @Test
    fun testCall_mixedEnglishCodeSwitching() {
        val cmd = nlu.process("မေမေ့ကို call လုပ်ပေး")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("မေမေ", cmd.contact)
    }

    @Test
    fun testCall_englishCommand() {
        val cmd = nlu.process("Call Mom")
        assertEquals(KyawGyiIntent.CALL, cmd.intent)
        assertEquals("Mom", cmd.contact)
    }

    // =========================================================================
    // 3. SEND_SMS INTENT & ENTITY EXTRACTION (5 tests)
    // =========================================================================

    @Test
    fun testSendSms_withContactAndBody() {
        val cmd = nlu.process("မေမေ့ကို မနက်ဖြန်တွေ့မယ်လို့ message ပို့")
        assertEquals(KyawGyiIntent.SEND_SMS, cmd.intent)
        assertEquals("မေမေ", cmd.contact)
        assertEquals("မနက်ဖြန်တွေ့မယ်", cmd.messageText)
    }

    @Test
    fun testSendSms_withBrotherContact() {
        val cmd = nlu.process("ညီလေးဆီ sms ပို့ပေး")
        assertEquals(KyawGyiIntent.SEND_SMS, cmd.intent)
        assertEquals("ညီလေး", cmd.contact)
    }

    @Test
    fun testSendSms_genericRequest() {
        val cmd = nlu.process("မက်ဆေ့ပို့ပေးပါ")
        assertEquals(KyawGyiIntent.SEND_SMS, cmd.intent)
    }

    @Test
    fun testSendSms_withPoliteBurmeseParticle() {
        val cmd = nlu.process("မောင်မောင့်ကို စာပို့ပေးပါဦး")
        assertEquals(KyawGyiIntent.SEND_SMS, cmd.intent)
        assertEquals("မောင်မောင်", cmd.contact)
    }

    @Test
    fun testSendSms_englishCommand() {
        val cmd = nlu.process("Send message to Dad")
        assertEquals(KyawGyiIntent.SEND_SMS, cmd.intent)
        assertEquals("Dad", cmd.contact)
    }

    // =========================================================================
    // 4. OPEN_APP VARIATIONS (10 tests)
    // =========================================================================

    @Test
    fun testOpenApp_facebookEnglish() {
        val cmd = nlu.process("Facebook ဖွင့်ပေး")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("Facebook", cmd.appName)
    }

    @Test
    fun testOpenApp_facebookBurmeseLoanword() {
        val cmd = nlu.process("ဖေ့ဘုတ်ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("Facebook", cmd.appName)
    }

    @Test
    fun testOpenApp_tiktokBurmese() {
        val cmd = nlu.process("တစ်တော့ဖွင့်လိုက်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("TikTok", cmd.appName)
    }

    @Test
    fun testOpenApp_tiktokEnglish() {
        val cmd = nlu.process("TikTok ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("TikTok", cmd.appName)
    }

    @Test
    fun testOpenApp_viber() {
        val cmd = nlu.process("Viber ဖွင့်ပေးပါ")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("Viber", cmd.appName)
    }

    @Test
    fun testOpenApp_telegram() {
        val cmd = nlu.process("Telegram ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("Telegram", cmd.appName)
    }

    @Test
    fun testOpenApp_kpayEnglish() {
        val cmd = nlu.process("KPay ဖွင့်ပေးပါဦး")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("KBZPay", cmd.appName)
    }

    @Test
    fun testOpenApp_kpayBurmese() {
        val cmd = nlu.process("ကေပီဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("KBZPay", cmd.appName)
    }

    @Test
    fun testOpenApp_waveMoney() {
        val cmd = nlu.process("Wave Money ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("WavePay", cmd.appName)
    }

    @Test
    fun testOpenApp_youtube() {
        val cmd = nlu.process("YouTube ဖွင့်ပေး")
        assertEquals(KyawGyiIntent.OPEN_APP, cmd.intent)
        assertEquals("YouTube", cmd.appName)
    }

    // =========================================================================
    // 5. PLAY_MUSIC & MEDIA CONTROLS (9 tests)
    // =========================================================================

    @Test
    fun testPlayMusic_simple() {
        val cmd = nlu.process("သီချင်းဖွင့်ပေး")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, cmd.intent)
        assertEquals("MUSIC", cmd.mediaType)
    }

    @Test
    fun testPlayMusic_withPoliteEndings() {
        val cmd = nlu.process("သီချင်းလေးဖွင့်ပါဦး")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, cmd.intent)
    }

    @Test
    fun testPlayMusic_withArtistEntity() {
        val cmd = nlu.process("လေးဖြူသီချင်းဖွင့်")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, cmd.intent)
        assertEquals("လေးဖြူ", cmd.artistName)
    }

    @Test
    fun testPlayMusic_codeSwitching() {
        val cmd = nlu.process("Music play လုပ်ပေး")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, cmd.intent)
    }

    @Test
    fun testPlayMusic_withSongNameEntity() {
        val cmd = nlu.process("အလွမ်းည သီချင်း ဖွင့်ပြပါ")
        assertEquals(KyawGyiIntent.PLAY_MUSIC, cmd.intent)
        assertEquals("အလွမ်းည", cmd.songName)
    }

    @Test
    fun testPauseMedia() {
        val cmd = nlu.process("သီချင်းခဏရပ်")
        assertEquals(KyawGyiIntent.PAUSE_MEDIA, cmd.intent)
    }

    @Test
    fun testResumeMedia() {
        val cmd = nlu.process("ပြန်ဖွင့်ပေး")
        assertEquals(KyawGyiIntent.RESUME_MEDIA, cmd.intent)
    }

    @Test
    fun testNextTrack() {
        val cmd = nlu.process("နောက်တစ်ပုဒ်")
        assertEquals(KyawGyiIntent.NEXT_TRACK, cmd.intent)
    }

    @Test
    fun testPreviousTrack() {
        val cmd = nlu.process("ရှေ့တစ်ပုဒ်")
        assertEquals(KyawGyiIntent.PREVIOUS_TRACK, cmd.intent)
    }

    // =========================================================================
    // 6. SYSTEM UTILITIES & DEVICE CONTROLS (11 tests)
    // =========================================================================

    @Test
    fun testOpenPhotos_burmese() {
        val cmd = nlu.process("ဓာတ်ပုံတွေ ကြည့်မယ်")
        assertEquals(KyawGyiIntent.OPEN_PHOTOS, cmd.intent)
    }

    @Test
    fun testOpenPhotos_gallery() {
        val cmd = nlu.process("Gallery ဖွင့်ပေး")
        assertEquals(KyawGyiIntent.OPEN_PHOTOS, cmd.intent)
    }

    @Test
    fun testOpenMaps_burmese() {
        val cmd = nlu.process("မြေပုံဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_MAPS, cmd.intent)
    }

    @Test
    fun testOpenMessages() {
        val cmd = nlu.process("မက်ဆေ့ app ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_MESSAGES, cmd.intent)
    }

    @Test
    fun testOpenSettings() {
        val cmd = nlu.process("ဆက်တင်ဖွင့်")
        assertEquals(KyawGyiIntent.OPEN_SETTINGS, cmd.intent)
    }

    @Test
    fun testVolumeUp() {
        val cmd = nlu.process("အသံကျယ်")
        assertEquals(KyawGyiIntent.VOLUME_UP, cmd.intent)
    }

    @Test
    fun testVolumeUp_polite() {
        val cmd = nlu.process("အသံလေး မြှင့်ပေးပါ")
        assertEquals(KyawGyiIntent.VOLUME_UP, cmd.intent)
    }

    @Test
    fun testVolumeDown() {
        val cmd = nlu.process("အသံတိုး")
        assertEquals(KyawGyiIntent.VOLUME_DOWN, cmd.intent)
    }

    @Test
    fun testVolumeDown_decrease() {
        val cmd = nlu.process("အသံလျှော့ပါ")
        assertEquals(KyawGyiIntent.VOLUME_DOWN, cmd.intent)
    }

    @Test
    fun testMute() {
        val cmd = nlu.process("အသံပိတ်")
        assertEquals(KyawGyiIntent.MUTE, cmd.intent)
    }

    @Test
    fun testSearch() {
        val cmd = nlu.process("မြန်မာ့သမိုင်း ရှာပေး")
        assertEquals(KyawGyiIntent.SEARCH, cmd.intent)
    }

    // =========================================================================
    // 7. STOP, CANCEL & UNKNOWN (4 tests)
    // =========================================================================

    @Test
    fun testStop_burmese() {
        val cmd = nlu.process("ရပ်လိုက်")
        assertEquals(KyawGyiIntent.STOP, cmd.intent)
    }

    @Test
    fun testStop_doneColloquial() {
        val cmd = nlu.process("တော်ပြီ")
        assertEquals(KyawGyiIntent.STOP, cmd.intent)
    }

    @Test
    fun testCancel_burmese() {
        val cmd = nlu.process("မလုပ်နဲ့တော့")
        assertEquals(KyawGyiIntent.CANCEL, cmd.intent)
    }

    @Test
    fun testUnknownQuery() {
        val cmd = nlu.process("မိုးရွာနေလား")
        assertEquals(KyawGyiIntent.UNKNOWN, cmd.intent)
        assertTrue(cmd.confidence < 0.45f)
        assertEquals(ConfidenceLevel.LOW, cmd.confidenceLevel)
    }
}
