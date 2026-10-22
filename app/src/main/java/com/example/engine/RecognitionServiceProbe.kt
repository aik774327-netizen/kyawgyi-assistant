package com.example.engine

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log

/**
 * FIX-01: asks the device's default recognition service which languages
 * it supports, using the strongest standard mechanism Android offers —
 * the [RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS] ordered broadcast.
 * The service answers with [RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES]
 * in the result extras.
 *
 * Stated limits (this mechanism is a probe, not a guarantee):
 * - Some OEM recognition services never answer the broadcast. The query
 *   therefore has a timeout and reports `null` (= verdict UNKNOWN); the
 *   caller must then proceed optimistically and rely on recognizer error
 *   codes 12/13 as the definitive in-session signal.
 * - The answer describes the *default* recognition service. If the user
 *   later switches the default voice-input provider, the cached verdict
 *   must be re-queried — callers re-probe per process, not forever.
 */
object RecognitionServiceProbe {

    private const val TAG = "RecognitionProbe"
    private const val QUERY_TIMEOUT_MS = 1500L

    /**
     * Queries supported languages asynchronously. [onResult] is invoked
     * exactly once, on the main thread, with the reported language tags
     * or `null` when the service did not answer in time / errored.
     */
    fun querySupportedLanguages(context: Context, onResult: (List<String>?) -> Unit) {
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var delivered = false

        fun deliver(languages: List<String>?) {
            if (delivered) return
            delivered = true
            handler.post { onResult(languages) }
        }

        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val languages = try {
                    intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                } catch (e: Exception) {
                    null
                }
                Log.d(TAG, "Language-details answer: ${languages?.size ?: 0} languages")
                deliver(languages)
            }
        }

        handler.postDelayed({ deliver(null) }, QUERY_TIMEOUT_MS)

        try {
            val query = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
            appContext.sendOrderedBroadcast(
                query,
                null,
                resultReceiver,
                handler,
                Activity.RESULT_OK,
                null,
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Language-details query failed: ${e.message}")
            deliver(null)
        }
    }
}
