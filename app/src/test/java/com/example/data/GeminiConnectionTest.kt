package com.example.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

/**
 * FIX-05 regression tests — the connection-test action through the
 * injected HTTP seam (FIX-05 added the seam to GeminiApiService
 * precisely so this behavior is testable without a real key or
 * network; all keys here are dummies, never real credentials).
 *
 * Pre-fix behavior being pinned against: every non-429 HTTP failure
 * was reported as "Invalid key or access denied" and every exception
 * as one "Network error" line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiConnectionTest {

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

    private val successJson =
        """{"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}"""

    // T16-05-04: a 200 answer means the (dummy) key is responsive.
    @Test
    fun t16_05_04_successReportsActive() = runBlocking {
        val service = GeminiApiService(null, fakeClient { req -> responseFor(req, 200, successJson) })
        val (ok, message) = service.testKeyConnection("dummy-key-for-test")
        assertTrue(ok)
        assertTrue(message.contains("active"))
    }

    // T16-05-05: HTTP 403 -> the AUTH message (and NOT the quota or
    // the old "HTTP 403: Invalid key..." wording pattern).
    @Test
    fun t16_05_05_forbiddenIsAuthClass() = runBlocking {
        val service = GeminiApiService(null, fakeClient { req -> responseFor(req, 403, """{"error":{}}""") })
        val (ok, message) = service.testKeyConnection("dummy-key-for-test")
        assertFalse(ok)
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.AUTH, 403),
            message
        )
    }

    // T16-05-06: HTTP 500 -> SERVER class message (pre-fix this was
    // mislabeled "Invalid key or access denied").
    @Test
    fun t16_05_06_serverErrorIsNotAuth() = runBlocking {
        val service = GeminiApiService(null, fakeClient { req -> responseFor(req, 500, """{"error":{}}""") })
        val (ok, message) = service.testKeyConnection("dummy-key-for-test")
        assertFalse(ok)
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.SERVER, 500),
            message
        )
    }

    // T16-05-07: HTTP 429 -> the quota message.
    @Test
    fun t16_05_07_quotaStaysQuota() = runBlocking {
        val service = GeminiApiService(null, fakeClient { req -> responseFor(req, 429, """{"error":{}}""") })
        val (ok, message) = service.testKeyConnection("dummy-key-for-test")
        assertFalse(ok)
        assertEquals(GeminiApiService.GEMINI_QUOTA_ERROR_MESSAGE, message)
    }

    // T16-05-08: a socket timeout -> the TIMEOUT message, distinct
    // from every HTTP-class message (the Myanmar-network/VPN case).
    @Test
    fun t16_05_08_timeoutIsTimeoutClass() = runBlocking {
        val service = GeminiApiService(
            null,
            fakeClient { throw SocketTimeoutException("read timed out") }
        )
        val (ok, message) = service.testKeyConnection("dummy-key-for-test")
        assertFalse(ok)
        assertEquals(
            GeminiFailureClassifier.userMessageFor(GeminiFailureClass.NETWORK_TIMEOUT),
            message
        )
    }

    // T16-05-09: blank key short-circuits without any request.
    @Test
    fun t16_05_09_blankKeyRejected() = runBlocking {
        val service = GeminiApiService(
            null,
            fakeClient { req -> responseFor(req, 200, successJson) }
        )
        val (ok, _) = service.testKeyConnection("   ")
        assertFalse(ok)
    }
}
