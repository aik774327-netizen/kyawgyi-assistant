package com.aiangel.assistant.hardware.ecoflow

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implements EcoFlow's official Developer API request signing:
 *
 * 1. Sort request params by ASCII key order, join as "k1=v1&k2=v2"
 *    (nested/object params would be flattened with dot-notation keys —
 *    not needed for the quota/command endpoints this app uses, which are flat)
 * 2. Append "&accessKey=...&nonce=...&timestamp=..."
 * 3. HMAC-SHA256 the resulting string with secretKey
 * 4. Hex-encode the digest — that hex string is the "sign" header value
 *
 * Reference: https://developer-eu.ecoflow.com/us/document/introduction
 */
object EcoFlowSigner {

    data class SignedHeaders(
        val accessKey: String,
        val nonce: String,
        val timestamp: String,
        val sign: String
    )

    /**
     * @param params flat request/query params (sn, cmdCode, etc) — NOT including
     *               accessKey/nonce/timestamp, those are appended automatically
     */
    fun sign(
        accessKey: String,
        secretKey: String,
        params: Map<String, String> = emptyMap(),
        nonce: String = generateNonce(),
        timestamp: String = System.currentTimeMillis().toString()
    ): SignedHeaders {
        val sortedParamString = params.toSortedMap()
            .entries
            .joinToString("&") { (key, value) -> "$key=$value" }

        val authString = "accessKey=$accessKey&nonce=$nonce&timestamp=$timestamp"

        val fullString = if (sortedParamString.isEmpty()) {
            authString
        } else {
            "$sortedParamString&$authString"
        }

        val sign = hmacSha256Hex(fullString, secretKey)

        return SignedHeaders(accessKey = accessKey, nonce = nonce, timestamp = timestamp, sign = sign)
    }

    private fun generateNonce(): String = (100000 + (Math.random() * 899999)).toInt().toString()

    private fun hmacSha256Hex(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
