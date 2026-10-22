package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * FIX-05 regression tests — Gemini failure classification.
 *
 * Device evidence B4: the key exists, yet every failure surfaced as
 * one generic "connection" problem. These tests pin the classifier:
 * auth (400/401/403) vs quota (429) vs endpoint (404) vs server (5xx)
 * vs timeout vs unreachable are DISTINCT classes with DISTINCT user
 * messages — an auth failure is never reported as a network problem.
 */
class GeminiFailureClassifierTest {

    // T16-05-01: HTTP status -> class matrix.
    @Test
    fun t16_05_01_httpCodeMatrix() {
        assertEquals(GeminiFailureClass.AUTH, GeminiFailureClassifier.classifyHttpCode(400))
        assertEquals(GeminiFailureClass.AUTH, GeminiFailureClassifier.classifyHttpCode(401))
        assertEquals(GeminiFailureClass.AUTH, GeminiFailureClassifier.classifyHttpCode(403))
        assertEquals(GeminiFailureClass.QUOTA, GeminiFailureClassifier.classifyHttpCode(429))
        assertEquals(GeminiFailureClass.ENDPOINT, GeminiFailureClassifier.classifyHttpCode(404))
        assertEquals(GeminiFailureClass.SERVER, GeminiFailureClassifier.classifyHttpCode(500))
        assertEquals(GeminiFailureClass.SERVER, GeminiFailureClassifier.classifyHttpCode(503))
        assertEquals(GeminiFailureClass.UNKNOWN, GeminiFailureClassifier.classifyHttpCode(418))
    }

    // T16-05-02: throwable -> class matrix, including wrapped causes
    // and the HTTP wrapper carrying its code.
    @Test
    fun t16_05_02_throwableMatrix() {
        assertEquals(
            GeminiFailureClass.NETWORK_TIMEOUT,
            GeminiFailureClassifier.classify(SocketTimeoutException("read timed out"))
        )
        assertEquals(
            GeminiFailureClass.NETWORK_UNREACHABLE,
            GeminiFailureClassifier.classify(UnknownHostException("generativelanguage.googleapis.com"))
        )
        assertEquals(
            GeminiFailureClass.AUTH,
            GeminiFailureClassifier.classify(GeminiHttpException(403))
        )
        assertEquals(
            GeminiFailureClass.NETWORK_TIMEOUT,
            GeminiFailureClassifier.classify(IOException("wrapper", SocketTimeoutException()))
        )
        assertEquals(
            GeminiFailureClass.UNKNOWN,
            GeminiFailureClassifier.classify(IllegalStateException("unrelated"))
        )
    }

    // T16-05-03: the classes the user must be able to tell apart have
    // different messages (no single generic line for everything).
    @Test
    fun t16_05_03_messagesAreDistinct() {
        val auth = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.AUTH, 403)
        val timeout = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.NETWORK_TIMEOUT)
        val unreachable = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.NETWORK_UNREACHABLE)
        val server = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.SERVER, 500)
        val quota = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.QUOTA)
        val noKey = GeminiFailureClassifier.userMessageFor(GeminiFailureClass.NO_KEY)
        val all = listOf(auth, timeout, unreachable, server, quota, noKey)
        assertEquals(all.size, all.toSet().size)
        // The AUTH message names the key/permission problem, and does
        // NOT claim a connection failure.
        assertNotEquals(auth, GeminiApiService.CONNECTION_ERROR_MESSAGE)
    }
}
