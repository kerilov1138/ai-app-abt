package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class ContentItem(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class SystemInstruction(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<ContentItem>,
    val systemInstruction: SystemInstruction? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse?
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>?
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String?
)
