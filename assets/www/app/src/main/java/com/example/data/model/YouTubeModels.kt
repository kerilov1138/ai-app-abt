package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YouTubeOEmbed(
    val title: String?,
    @Json(name = "author_name") val authorName: String?,
    @Json(name = "thumbnail_url") val thumbnailUrl: String?
)

@JsonClass(generateAdapter = true)
data class CaptionTrack(
    val baseUrl: String,
    val languageCode: String,
    val name: CaptionName? = null
)

@JsonClass(generateAdapter = true)
data class CaptionName(
    val simpleText: String?
)

@JsonClass(generateAdapter = true)
data class TranscriptSegment(
    val start: Double,      // Time in seconds when segment starts
    val duration: Double,   // Duration of segment
    val timestamp: String,  // Formatted like "02:14"
    val text: String        // Plaintext of segment
)
