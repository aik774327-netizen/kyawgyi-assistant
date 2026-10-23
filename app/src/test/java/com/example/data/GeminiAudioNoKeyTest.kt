package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.api.ApiKeyQuotaManager
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §7 Part A (t18_a_09) — with NO key configured, the audio methods
 * must fail honestly with [NoApiKeyConfiguredException] before any
 * request is attempted (never a fabricated 429, never a silent
 * success). Kept in its own class so the pool is guaranteed empty:
 * setUp removes every pooled key, including any seeded default.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAudioNoKeyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val quotaManager = ApiKeyQuotaManager.getRequiredInstance(context)
        runBlocking {
            quotaManager.resetAllQuotasAndCooldowns()
            quotaManager.poolState.value.keysList.forEach { quotaManager.removeKey(it.id) }
        }
    }

    private fun okClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("fake")
                    .body(
                        """{"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

    @Test
    fun t18_a_09_noKeyConfigured_audioMethodsThrowNoKey() = runBlocking {
        val service = GeminiApiService(context, okClient())
        try {
            service.transcribeAudio(byteArrayOf(1, 2, 3), "my-MM")
            fail("Expected NoApiKeyConfiguredException")
        } catch (e: NoApiKeyConfiguredException) {
            assertEquals(
                GeminiApiService.NO_API_KEY_MESSAGE,
                GeminiFailureClassifier.userMessageForThrowable(e)
            )
        }
        try {
            service.synthesizeSpeech("မင်္ဂလာပါ")
            fail("Expected NoApiKeyConfiguredException")
        } catch (e: NoApiKeyConfiguredException) {
            // Expected: the TTS method fails the same honest way.
        }
    }
}
