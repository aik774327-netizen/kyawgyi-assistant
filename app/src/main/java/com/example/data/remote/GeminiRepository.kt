package com.example.data.remote

import android.content.Context
import com.example.data.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(
    private val apiKey: String = "",
    private val context: Context? = null
) {

    private val apiService = GeminiApiService(context)

    suspend fun queryGeminiAI(userPrompt: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = com.example.cognitive.KyawGyiSystemPersona.MASTER_SYSTEM_PROMPT

        val (response, _) = apiService.generateResponse(
            prompt = userPrompt,
            mode = "QUICK",
            engine = "Kyaw Gyi Engine",
            systemPrompt = systemPrompt
        )
        response
    }
}


