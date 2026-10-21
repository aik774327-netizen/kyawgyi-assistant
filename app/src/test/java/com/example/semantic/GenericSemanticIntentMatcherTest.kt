package com.example.semantic

import com.example.nlu.KyawGyiIntent
import org.junit.Assert.*
import org.junit.Test

class GenericSemanticIntentMatcherTest {

    @Test
    fun testSemanticMatch_CameraIntent() {
        val result = GenericSemanticIntentMatcher.match("ဓာတ်ပုံလေးတွေ ရိုက်ချင်တယ်")
        assertNotNull(result)
        assertEquals(KyawGyiIntent.OPEN_CAMERA, result?.intent)
        assertTrue((result?.confidence ?: 0f) >= 0.65f)
    }

    @Test
    fun testSemanticMatch_PhotosIntent() {
        val result = GenericSemanticIntentMatcher.match("ဓာတ်ပုံတွေ ပြပေးပါဦး")
        assertNotNull(result)
        assertEquals(KyawGyiIntent.OPEN_PHOTOS, result?.intent)
    }

    @Test
    fun testSemanticMatch_MusicIntent() {
        val result = GenericSemanticIntentMatcher.match("သီချင်းကောင်းလေးတွေ ဖွင့်ပြပါ")
        assertNotNull(result)
        assertEquals(KyawGyiIntent.PLAY_MUSIC, result?.intent)
    }

    @Test
    fun testSemanticMatch_VolumeUpIntent() {
        val result = GenericSemanticIntentMatcher.match("အသံ ကျယ်ကျယ် ချဲ့ပေးပါ")
        assertNotNull(result)
        assertEquals(KyawGyiIntent.VOLUME_UP, result?.intent)
    }

    @Test
    fun testSemanticMatch_Unknown_HonestRejection() {
        val result = GenericSemanticIntentMatcher.match("xyz random meaningless query 12345")
        assertNull(result)
    }
}
