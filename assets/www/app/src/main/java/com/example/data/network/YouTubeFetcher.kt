package com.example.data.network

import android.text.Html
import com.example.data.model.CaptionTrack
import com.example.data.model.TranscriptSegment
import com.example.data.model.YouTubeOEmbed
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

object YouTubeFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Extracts YouTube 11-char Video ID from various types of video links
     */
    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("""(?:v=|/v/|/embed/|/shorts/|youtu\.be/|\.be/|\/watch\?v=)([^"&?/\s]{11})"""),
            Regex("""youtube\.com/watch\?v=([^"&?/\s]{11})"""),
            Regex("""youtu\.be/([^"&?/\s]{11})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Fetches details of the YouTube video using public oEmbed API
     */
    suspend fun fetchVideoInfo(url: String): YouTubeOEmbed? = withContext(Dispatchers.IO) {
        val requestUrl = "https://www.youtube.com/oembed?url=$url&format=json"
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val adapter = moshi.adapter(YouTubeOEmbed::class.java)
                adapter.fromJson(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetches transcript/captions for a YouTube Video ID.
     * Searches watch page for "captionTracks" configurations, downloads XML, and parses it.
     */
    suspend fun fetchTranscript(videoId: String): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val request = Request.Builder()
            .url(watchUrl)
            // Add a common desktop User-Agent to ensure standard desktop page structure is returned by YouTube
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val htmlBody = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("YouTube watch sayfası yüklenemedi: HTTP ${response.code}")
                response.body?.string() ?: throw IOException("Boş yanıt gövdesi")
            }
        } catch (e: Exception) {
            throw IOException("Sunucuya bağlanılamadı: ${e.localizedMessage}")
        }

        // Search watch HTML for captionTracks JSON configurations
        val captionTracksRegex = Regex(""""captionTracks"\s*:\s*(\[.*?\])""")
        val match = captionTracksRegex.find(htmlBody)
            ?: throw IOException("Bu videoda altyazı desteği bulunamadı. Lütfen açıklamalı bir video deneyin.")

        val jsonTracks = match.groupValues[1]
        
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, CaptionTrack::class.java)
        val adapter = moshi.adapter<List<CaptionTrack>>(listType)
        val tracks = try {
            adapter.fromJson(jsonTracks)
        } catch (e: Exception) {
            throw IOException("Altyazı ayarları çözümlenemedi.")
        } ?: emptyList()

        if (tracks.isEmpty()) {
            throw IOException("Bu videonun altyazı kanalı bulunamadı.")
        }

        // Prioritize Turkish (tr), then English (en), then any first track
        val preferredTrack = tracks.firstOrNull { it.languageCode.lowercase() == "tr" }
            ?: tracks.firstOrNull { it.languageCode.lowercase().startsWith("tr") }
            ?: tracks.firstOrNull { it.languageCode.lowercase() == "en" }
            ?: tracks.firstOrNull { it.languageCode.lowercase().startsWith("en") }
            ?: tracks.firstOrNull()
            ?: throw IOException("Uygun altyazı kanalı bulunamadı.")

        // Download the XML subtitles
        val xmlUrl = preferredTrack.baseUrl
        val xmlRequest = Request.Builder()
            .url(xmlUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val xmlString = try {
            client.newCall(xmlRequest).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Altyazı dosyası indirilemedi.")
                response.body?.string() ?: throw IOException("Boş altyazı dosyası")
            }
        } catch (e: Exception) {
            throw IOException("Altyazı indirilirken hata oluştu: ${e.localizedMessage}")
        }

        // Parse XML tags <text start="X" dur="Y">caption</text>
        // Note: YouTube timedtext occasionally uses start="X" alone, dur="Y" might represent duration but is optional, text might have some parameters.
        val textRegex = Regex("""<text start="([\d\.]+)"(?: dur="([\d\.]+)")?[^>]*>([^<]*)</text>""")
        val matches = textRegex.findAll(xmlString)
        val segments = mutableListOf<TranscriptSegment>()

        for (m in matches) {
            val start = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val duration = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val rawText = m.groupValues[3]
            val decodedText = unescapeHtml(rawText)
            val timestamp = formatTimestamp(start)

            segments.add(TranscriptSegment(start, duration, timestamp, decodedText))
        }

        if (segments.isEmpty()) {
            throw IOException("Altyazı dosyası boş veya formatı uyumsuz.")
        }

        segments
    }

    private fun unescapeHtml(text: String): String {
        return try {
            val unescaped = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(text).toString()
            }
            unescaped.trim()
        } catch (e: Exception) {
            text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#039;", "'")
                .trim()
        }
    }

    private fun formatTimestamp(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }
}
