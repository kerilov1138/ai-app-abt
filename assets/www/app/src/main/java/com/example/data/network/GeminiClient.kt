package com.example.data.network

import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Sends a request to Google's Gemini-3.5-flash model via direct REST
     */
    suspend fun generateContent(
        apiKey: String,
        systemInstructionText: String?,
        chatHistory: List<ContentItem>
    ): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?key=$apiKey"
        
        val systemInst = systemInstructionText?.let {
            SystemInstruction(parts = listOf(Part(text = it)))
        }

        val requestBodyData = GeminiRequest(
            contents = chatHistory,
            systemInstruction = systemInst,
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        val jsonAdapter = moshi.adapter(GeminiRequest::class.java)
        val jsonString = jsonAdapter.toJson(requestBodyData)

        val responseAdapter = moshi.adapter(GeminiResponse::class.java)

        val request = Request.Builder()
            .url(url)
            .post(jsonString.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: throw IOException("Yapay zekadan gelen yanıt boş.")
                if (!response.isSuccessful) {
                    throw IOException("REST API Hatası (HTTP ${response.code}): $bodyString")
                }
                
                val geminiResponse = responseAdapter.fromJson(bodyString)
                val textResponse = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IOException("Yapay zeka geçerli bir metin yanıtı üretmedi.")
                textResponse
            }
        } catch (e: Exception) {
            throw IOException("Gemini API Bağlantı Hatası: ${e.localizedMessage}")
        }
    }
}
