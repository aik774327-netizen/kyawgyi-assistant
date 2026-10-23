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
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

/**
 * §7 Part A regression tests (t18_a_*) — Gemini online STT/TTS at the
 * service layer, through the FIX-05 injectable HTTP seam and the REAL
 * key pool with a dummy key (the GeminiConnectionTest fake-transport
 * pattern). No live API call is ever made; all keys are dummies.
 *
 * Pins: the audio request shapes (inlineData audio part for STT,
 * AUDIO response modality + speechConfig for TTS), defensive response
 * parsing (base64 PCM decode, first usable text part), and the typed
 * error mapping the voice engines rely on (GeminiHttpException /
 * GeminiQuotaExceededException / GeminiEmptyResponseException, all
 * classifiable by GeminiFailureClassifier).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAudioServiceTest {

    private lateinit var context: Context
    private lateinit var quotaManager: ApiKeyQuotaManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        quotaManager = ApiKeyQuotaManager.getRequiredInstance(context)
        runBlocking {
            quotaManager.resetAllQuotasAndCooldowns()
            val toRemove = quotaManager.poolState.value.keysList
                .filter { it.id != ApiKeyQuotaManager.DEFAULT_PRIMARY_ID }
            toRemove.forEach { quotaManager.removeKey(it.id) }
            quotaManager.addOrUpdateKey(
                rawKey = "dummy-key-for-t18-tests",
                label = "T18 Test Key"
            )
            quotaManager.resetAllQuotasAndCooldowns()
        }
    }

    private fun fakeClient(respond: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain -> respond(chain.request()) }
            .build()

    private fun responseFor(request: Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun requestBodyJson(request: Request): JSONObject {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    private fun serviceWith(client: OkHttpClient) = GeminiApiService(context, client)

    private val fakeWav = byteArrayOf(0x52, 0x49, 0x46, 0x46, 1, 2, 3, 4, 0x57, 0x41, 0x56, 0x45)

    // t18_a_01: STT request shape — audio inlineData part + instruction
    // part against the chat model; the transcript text is returned.
    @Test
    fun t18_a_01_transcribeSendsAudioPartAndReturnsTranscript() = runBlocking {
        var captured: Request? = null
        val client = fakeClient { req ->
            captured = req
            responseFor(req, 200, """{"candidates":[{"content":{"parts":[{"text":"မင်္ဂလာပါ"}]}}]}""")
        }
        val transcript = serviceWith(client).transcribeAudio(fakeWav, "my-MM")
        assertEquals("မင်္ဂလာပါ", transcript)

        val url = captured!!.url.toString()
        assertTrue(url.contains("gemini-3.5-flash"))
        assertTrue(url.contains(":generateContent"))

        val parts = requestBodyJson(captured!!)
            .getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())
        val inline = parts.getJSONObject(0).getJSONObject("inlineData")
        assertEquals("audio/wav", inline.getString("mimeType"))
        val decoded = android.util.Base64.decode(inline.getString("data"), android.util.Base64.DEFAULT)
        assertArrayEquals(fakeWav, decoded)
        val instruction = parts.getJSONObject(1).getString("text")
        assertTrue(instruction.contains("Transcribe"))
        assertTrue(instruction.contains("my-MM"))
    }

    // t18_a_02: TTS request shape — TTS model URL, AUDIO modality,
    // speechConfig voice; base64 PCM in the reply is decoded verbatim.
    @Test
    fun t18_a_02_synthesizeSendsAudioModalityAndDecodesPcm() = runBlocking {
        val pcm = byteArrayOf(10, 20, 30, 40, 50, 60, 70, 80)
        val b64 = java.util.Base64.getEncoder().encodeToString(pcm)
        var captured: Request? = null
        val client = fakeClient { req ->
            captured = req
            responseFor(
                req, 200,
                """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;codec=pcm;rate=24000","data":"$b64"}}]}}]}"""
            )
        }
        val audio = serviceWith(client).synthesizeSpeech("မင်္ဂလာပါ", "Kore")
        assertArrayEquals(pcm, audio)

        val url = captured!!.url.toString()
        assertTrue(url.contains(GeminiApiService.TTS_MODEL_NAME))
        val body = requestBodyJson(captured!!)
        val genConfig = body.getJSONObject("generationConfig")
        val modalities = genConfig.getJSONArray("responseModalities")
        assertEquals(1, modalities.length())
        assertEquals("AUDIO", modalities.getString(0))
        val voiceName = genConfig.getJSONObject("speechConfig")
            .getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig")
            .getString("voiceName")
        assertEquals("Kore", voiceName)
        val text = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("မင်္ဂလာပါ", text)
    }

    // t18_a_03: HTTP 403 on an audio call -> GeminiHttpException,
    // classified AUTH (never collapsed into a generic connection error).
    @Test
    fun t18_a_03_transcribeForbiddenIsAuthClass() = runBlocking {
        val client = fakeClient { req -> responseFor(req, 403, """{"error":{}}""") }
        try {
            serviceWith(client).transcribeAudio(fakeWav, "my-MM")
            fail("Expected GeminiHttpException")
        } catch (e: GeminiHttpException) {
            assertEquals(403, e.code)
            assertEquals(GeminiFailureClass.AUTH, GeminiFailureClassifier.classify(e))
            assertEquals(
                GeminiFailureClassifier.userMessageFor(GeminiFailureClass.AUTH, 403),
                GeminiFailureClassifier.userMessageForThrowable(e)
            )
        }
    }

    // t18_a_04: HTTP 500 -> SERVER class; HTTP 404 -> ENDPOINT class.
    @Test
    fun t18_a_04_synthesizeServerAndEndpointClasses() = runBlocking {
        val client500 = fakeClient { req -> responseFor(req, 500, """{"error":{}}""") }
        try {
            serviceWith(client500).synthesizeSpeech("test")
            fail("Expected GeminiHttpException")
        } catch (e: GeminiHttpException) {
            assertEquals(GeminiFailureClass.SERVER, GeminiFailureClassifier.classify(e))
        }
        val client404 = fakeClient { req -> responseFor(req, 404, """{"error":{}}""") }
        try {
            serviceWith(client404).synthesizeSpeech("test")
            fail("Expected GeminiHttpException")
        } catch (e: GeminiHttpException) {
            assertEquals(GeminiFailureClass.ENDPOINT, GeminiFailureClassifier.classify(e))
        }
    }

    // t18_a_05: HTTP 429 -> GeminiQuotaExceededException, and the
    // throwable-level message is the dedicated quota message.
    @Test
    fun t18_a_05_transcribeQuotaIsQuotaClass() = runBlocking {
        val client = fakeClient { req -> responseFor(req, 429, """{"error":{}}""") }
        try {
            serviceWith(client).transcribeAudio(fakeWav, "my-MM")
            fail("Expected GeminiQuotaExceededException")
        } catch (e: GeminiQuotaExceededException) {
            assertEquals(
                GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE,
                GeminiFailureClassifier.userMessageForThrowable(e)
            )
        }
    }

    // t18_a_06: a socket timeout -> NETWORK_TIMEOUT class (the
    // Myanmar-network/VPN case), distinct from every HTTP class.
    @Test
    fun t18_a_06_transcribeTimeoutIsTimeoutClass() = runBlocking {
        val client = fakeClient { throw SocketTimeoutException("read timed out") }
        try {
            serviceWith(client).transcribeAudio(fakeWav, "my-MM")
            fail("Expected SocketTimeoutException")
        } catch (e: SocketTimeoutException) {
            assertEquals(
                GeminiFailureClass.NETWORK_TIMEOUT,
                GeminiFailureClassifier.classify(e)
            )
            assertEquals(
                GeminiFailureClassifier.userMessageFor(GeminiFailureClass.NETWORK_TIMEOUT),
                GeminiFailureClassifier.userMessageForThrowable(e)
            )
        }
    }

    // t18_a_07: HTTP 200 with no usable part -> typed EMPTY_RESPONSE
    // failure for BOTH audio methods (never a silent empty success).
    @Test
    fun t18_a_07_emptyResponsesAreTypedFailures() = runBlocking {
        val noText = fakeClient { req ->
            responseFor(req, 200, """{"candidates":[{"content":{"parts":[{"text":"  "}]}}]}""")
        }
        try {
            serviceWith(noText).transcribeAudio(fakeWav, "my-MM")
            fail("Expected GeminiEmptyResponseException")
        } catch (e: GeminiEmptyResponseException) {
            assertEquals(
                GeminiFailureClass.EMPTY_RESPONSE,
                GeminiFailureClassifier.classify(e)
            )
        }
        val noAudio = fakeClient { req ->
            responseFor(req, 200, """{"candidates":[{"content":{"parts":[{"text":"not audio"}]}}]}""")
        }
        try {
            serviceWith(noAudio).synthesizeSpeech("test")
            fail("Expected GeminiEmptyResponseException")
        } catch (e: GeminiEmptyResponseException) {
            assertEquals(
                GeminiFailureClass.EMPTY_RESPONSE,
                GeminiFailureClassifier.classify(e)
            )
        }
    }

    // t18_a_08: defensive parsing — the transcript is found even when
    // a non-text part precedes it in the reply.
    @Test
    fun t18_a_08_transcribeSkipsNonTextParts() = runBlocking {
        val client = fakeClient { req ->
            responseFor(
                req, 200,
                """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/wav","data":"AA=="}},{"text":"  ဝိုင်ဖိုင် ဖွင့်ပေးပါ  "}]}}]}"""
            )
        }
        val transcript = serviceWith(client).transcribeAudio(fakeWav, "my-MM")
        assertEquals("ဝိုင်ဖိုင် ဖွင့်ပေးပါ", transcript)
    }
}
