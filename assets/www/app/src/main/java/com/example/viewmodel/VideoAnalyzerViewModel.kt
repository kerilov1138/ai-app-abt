package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.SavedAnalysis
import com.example.data.model.ContentItem
import com.example.data.model.Part
import com.example.data.model.TranscriptSegment
import com.example.data.network.GeminiClient
import com.example.data.network.YouTubeFetcher
import com.example.data.repository.AnalysisRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class VideoAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("youtube_analyzer_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = AnalysisRepository(database.analysisDao())

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Key management
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    // Inputs
    val youtubeUrl = MutableStateFlow("")
    
    // Summary Settings
    val summaryLength = MutableStateFlow("short") // "short" veya "long"

    // Search query for Transcript keyword searching
    val transcriptSearchQuery = MutableStateFlow("")

    // Statuses
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _progressText = MutableStateFlow("")
    val progressText: StateFlow<String> = _progressText.asStateFlow()

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent: StateFlow<Float> = _progressPercent.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Q&A statuses
    private val _isChatSending = MutableStateFlow(false)
    val isChatSending: StateFlow<Boolean> = _isChatSending.asStateFlow()

    // Data streams
    val savedAnalyses: StateFlow<List<SavedAnalysis>> = repository.allAnalyses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentAnalysis = MutableStateFlow<SavedAnalysis?>(null)
    val currentAnalysis: StateFlow<SavedAnalysis?> = _currentAnalysis.asStateFlow()

    private val _transcriptSegments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val transcriptSegments: StateFlow<List<TranscriptSegment>> = _transcriptSegments.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Suggestion questions parsed from Gemini summaries
    private val _summarySuggestions = MutableStateFlow<List<String>>(emptyList())
    val summarySuggestions: StateFlow<List<String>> = _summarySuggestions.asStateFlow()

    // Filtered segments reactive stream for Keyword-based searching
    val filteredSegments: StateFlow<List<TranscriptSegment>> = combine(
        _transcriptSegments,
        transcriptSearchQuery
    ) { segments, query ->
        if (query.isBlank()) {
            segments
        } else {
            segments.filter { it.text.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDarkMode = MutableStateFlow(false)

    init {
        // Load stored API Key
        _apiKey.value = sharedPrefs.getString("custom_api_key", "") ?: ""
        isDarkMode.value = sharedPrefs.getBoolean("is_dark_mode", false)
    }

    fun toggleDarkMode() {
        val nextVal = !isDarkMode.value
        sharedPrefs.edit().putBoolean("is_dark_mode", nextVal).apply()
        isDarkMode.value = nextVal
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        sharedPrefs.edit().putString("custom_api_key", trimmed).apply()
        _apiKey.value = trimmed
    }

    fun clearApiKey() {
        sharedPrefs.edit().remove("custom_api_key").apply()
        _apiKey.value = ""
    }

    fun getEffectiveApiKey(): String {
        val customKey = _apiKey.value
        if (customKey.isNotEmpty()) return customKey
        
        // System fallback
        val systemKey = BuildConfig.GEMINI_API_KEY
        return if (systemKey.isNotEmpty() && systemKey != "MY_GEMINI_API_KEY") {
            systemKey
        } else {
            ""
        }
    }

    fun loadAnalysis(analysis: SavedAnalysis) {
        _currentAnalysis.value = analysis
        _transcriptSegments.value = deserializeSegments(analysis.transcriptJson)
        _summarySuggestions.value = deserializeQuestions(analysis.suggestedQuestionsJson)
        
        // Start a fresh Q&A chat for this analysis
        _chatMessages.value = emptyList()
        _analysisError.value = null
    }

    fun deleteAnalysis(analysis: SavedAnalysis) {
        viewModelScope.launch {
            repository.deleteById(analysis.id)
            if (_currentAnalysis.value?.id == analysis.id) {
                _currentAnalysis.value = null
                _transcriptSegments.value = emptyList()
                _summarySuggestions.value = emptyList()
                _chatMessages.value = emptyList()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _currentAnalysis.value = null
            _transcriptSegments.value = emptyList()
            _summarySuggestions.value = emptyList()
            _chatMessages.value = emptyList()
        }
    }

    fun setSummaryLength(length: String) {
        summaryLength.value = length
    }

    /**
     * Conducts entire background analysis loop!
     */
    fun startAnalysis() {
        val url = youtubeUrl.value.trim()
        if (url.isEmpty()) {
            _analysisError.value = "Lütfen geçerli bir YouTube URL'si girin."
            return
        }

        val videoId = YouTubeFetcher.extractVideoId(url)
        if (videoId == null) {
            _analysisError.value = "Geçersiz YouTube URL formatı."
            return
        }

        val key = getEffectiveApiKey()
        if (key.isEmpty()) {
            _analysisError.value = "Lütfen önce geçerli bir API Anahtarı (API Key) girin."
            return
        }

        _isAnalyzing.value = true
        _analysisError.value = null
        _progressText.value = "Başlatılıyor..."
        _progressPercent.value = 0.05f

        viewModelScope.launch(Dispatchers.Main) {
            try {
                // 1. Check if we already have this video analyzed in our Room cache!
                _progressText.value = "Yerel veritabanı kontrol ediliyor..."
                _progressPercent.value = 0.10f
                
                val cached = withContext(Dispatchers.IO) {
                    repository.getAnalysisByVideoId(videoId)
                }

                if (cached != null) {
                    _progressText.value = "Önbellekten yükleniyor..."
                    _progressPercent.value = 0.90f
                    loadAnalysis(cached)
                    _progressPercent.value = 1.0f
                    _isAnalyzing.value = false
                    return@launch
                }

                // 2. Fetch oEmbed details
                _progressText.value = "Video bilgileri alınıyor..."
                _progressPercent.value = 0.20f
                val info = YouTubeFetcher.fetchVideoInfo(url)
                val title = info?.title ?: "Bilinmeyen Video"
                val author = info?.authorName ?: "Bilinmeyen Kanal"
                val thumb = info?.thumbnailUrl ?: "https://img.youtube.com/vi/$videoId/sddefault.jpg"

                // 3. Try to fetch captions/transcript
                _progressText.value = "Altyazılar indiriliyor..."
                _progressPercent.value = 0.40f

                var segments = emptyList<TranscriptSegment>()
                var isTranscriptAvailable = true
                try {
                    segments = YouTubeFetcher.fetchTranscript(videoId)
                } catch (e: Exception) {
                    // Fail gracefully. If no transcript, we toggle isTranscriptAvailable = false
                    isTranscriptAvailable = false
                }

                _progressPercent.value = 0.60f
                _progressText.value = "Yapay zeka analizi hazırlanıyor..."

                // 4. Summarize and parse suggestion questions
                val isLong = summaryLength.value == "long"
                val promptText = if (isTranscriptAvailable) {
                    val fullText = segments.joinToString(" ") { it.text }
                    """
                    Sen bir YouTube Video Analiz Asistanısın. İşte bir YouTube videosunun transkript metni. 
                    Lütfen bu videoya son derece profesyonel, detaylı ve kapsamlı bir özet sun. 
                    Eğer video Türkçe değilse veya başka bir dilde kayıtlıysa bile Türkçe özet çıkart. Markdown formatı kullan.
                    
                    Bölüm başlıkları için ## kullan.
                    Önemli noktaları, terimleri **kalın** formatta yaz.
                    Paragraflar arasında boşluk bırak.
                    Uzunluk tercihi: ${if (isLong) "Detaylı ve uzun bir analiz sun, her bir konuyu detaylandır." else "Kısa ve öz bir özet yap, en önemli 3-5 noktayı vurgula."}

                    Ayrıca özetin en sonuna, kullanıcının bu video hakkında asistanına sorabileceği 3 alternatif soru önerisi ekle.
                    Her soru önerisini mutlaka tam olarak şu formatta yazmalıdır: <<ÖNERİ: soru metni>>
                    Örn: <<ÖNERİ: Videoda bahsedilen projenin amacı nedir?>>

                    ÖNEMLİ: Sadece ve yalnızca transkriptte geçen konular hakkında konuşabilirsin. Transkript dışı konular hakkında bilgi veya asılsız tahminler ekleme.

                    --- TRANSKRİPT BAŞLANGIÇ ---
                    $fullText
                    --- TRANSKRİPT BİTİŞ ---
                    """.trimIndent()
                } else {
                    """
                    Sen bir YouTube Video Analiz Asistanısın. Bu videonun otomatik veya manuel altyazıları bulunmuyor.
                    Aşağıda videoya ait temel bilgiler (Metadata) bulunmaktadır.
                    Lütfen video başlığını, kanal adını ve YouTube'daki genel bilgileri kullanarak ve kendi entelektüel hazineni tarayarak videoya ait olası içeriğin analizini ve tahminini içeren şık bir özet sun. 
                    Ayrıca kullanıcıya bu videoda otomatik altyazı bulunmadığı için analizin video başlığı ve metadata bilgileri üzerinden kavramsal olarak yapıldığını belirten şık bir not ekle. 
                    Türkçe yanıt ver. Markdown formatı kullan.
                    
                    Bölüm başlıkları için ## kullan.
                    Önemli noktaları, terimleri **kalın** formatta yaz.

                    Ayrıca özetin en sonuna, kullanıcının bu video hakkında asistanına sorabileceği 3 alternatif soru önerisi ekle.
                    Her soru önerisini mutlaka tam olarak şu formatta yazmalıdır: <<ÖNERİ: soru metni>>
                    Örn: <<ÖNERİ: Video başlığında geçen konu hangi kitleye hitap ediyor?>>

                    --- VİDEO BİLGİLERİ ---
                    Video Başlığı: $title
                    Kanal Adı: $author
                    Video URL: $url
                    """.trimIndent()
                }

                _progressText.value = "Gemini video içeriğini analiz ediyor..."
                _progressPercent.value = 0.80f

                val systemInstruction = "Sen profesyonel bir YouTube Video Analiz Asistanısın."
                val responseText = GeminiClient.generateContent(
                    apiKey = key,
                    systemInstructionText = systemInstruction,
                    chatHistory = listOf(ContentItem(parts = listOf(Part(text = promptText))))
                )

                _progressText.value = "Sonuçlar kaydediliyor..."
                _progressPercent.value = 0.95f

                val (cleanSummary, suggestions) = parseSuggestions(responseText)

                val suggestionsList = if (suggestions.isNotEmpty()) {
                    suggestions
                } else {
                    generateFallbackSuggestions(title)
                }

                // Create the Room database entity
                val newAnalysis = SavedAnalysis(
                    videoUrl = url,
                    videoId = videoId,
                    videoTitle = title,
                    videoAuthor = author,
                    thumbnailUrl = thumb,
                    summary = cleanSummary,
                    transcriptJson = serializeSegments(segments),
                    suggestedQuestionsJson = serializeQuestions(suggestionsList)
                )

                // Save to Room SQLite Database
                withContext(Dispatchers.IO) {
                    repository.insert(newAnalysis)
                }

                // Automatically load it
                val savedItem = withContext(Dispatchers.IO) {
                    repository.getAnalysisByVideoId(videoId)
                }
                if (savedItem != null) {
                    loadAnalysis(savedItem)
                } else {
                    loadAnalysis(newAnalysis)
                }

                _progressPercent.value = 1.0f
                _isAnalyzing.value = false

            } catch (e: Exception) {
                _analysisError.value = e.localizedMessage ?: "Beklenmedik bir hata oluştu."
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Sends a Q&A chat message to Gemini chatbot regarding the currently active video
     */
    fun sendChatMessage(msgText: String) {
        val userMsgText = msgText.trim()
        if (userMsgText.isEmpty()) return

        val activeAnalysis = _currentAnalysis.value ?: return
        val key = getEffectiveApiKey()
        if (key.isEmpty()) {
            _analysisError.value = "Lütfen önce geçerli bir API Anahtarı girin."
            return
        }

        // Add user bubble
        val userMsgObj = ChatMessage(text = userMsgText, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsgObj
        _isChatSending.value = true

        viewModelScope.launch(Dispatchers.Main) {
            try {
                // Compile background context to supply Gemini
                val fullTranscriptText = if (_transcriptSegments.value.isNotEmpty()) {
                    _transcriptSegments.value.joinToString(" ") { it.text }
                } else {
                    ""
                }

                val systemInst = """
                    Sen bir YouTube Video Analiz Asistanısın. Görevin, SADECE ve YALNIZCA sana verilen video transkriptini/özetini analiz etmek ve kullanıcıya yardımcı olmaktır. 
                    Sadece video başlığında ve transkriptte geçen konular hakkında konuşabilirsin. Transkript dışında hiçbir konuda detaylı bilgi veremez, soru cevaplayamaz veya genel sohbet edemezsin. 
                    Kullanıcıya yardımcı ama tam filtre kurallarına uyan Türkçe yanıtlar ver. Markdown formatı kullan.
                    
                    Önemli terimleri her zaman **kalın** formatla yaz.
                    Paragraflar arasında boşluk bırak.

                    Transkript/Video İçeriği:
                    Başlık: ${activeAnalysis.videoTitle}
                    Kanal: ${activeAnalysis.videoAuthor}
                    Altyazı: ${if (fullTranscriptText.isNotEmpty()) fullTranscriptText else "Altyazı bulunamadı. Lütfen özet üzerinden git."}
                    Özet: ${activeAnalysis.summary}

                    ÖNEMLİ KURALLAR:
                    1) EĞER KULLANICI VİDEO DIŞI/ALAKASIZ BİR SORU SORARSA:
                       Kesinlikle soruyu cevaplama. Kibarca bu sorunun video içeriğiyle ilgili olmadığını belirt. Ardından, "Video içeriğine dayanarak şu soruları sorabilirsiniz:" diyerek video ile %100 ilgili en fazla 3 alternatif soru önerisi ekle.
                    2) EĞER KULLANICI VİDEO İLE İLGİLİ AMA VİDEODA TAM OLARAK CEVABI OLMAYAN BİR SORU SORARSA (veya eksik bir soru sorarsa):
                       Kullanıcıya bu spesifik sorunun cevabının videoda tam olarak geçmediğini açıkla. Ardından "Bunu mu demek istediniz?" diyerek, kullanıcının niyetine en yakın ve SADECE videoda geçen konulara dayalı en fazla 3 alternatif soru önerisi ekle.
                    3) ÖNERİ FORMATI:
                       Her öneriyi mutlaka tam olarak şu formatta yazmalıdır: <<ÖNERİ: alternatif soru metni>>
                """.trimIndent()

                // Map message bubbles to Gemini ContentItem history
                val chatHistory = mutableListOf<ContentItem>()
                
                // Add first turn priming the model
                chatHistory.add(ContentItem(role = "user", parts = listOf(Part(text = "Merhaba asistan. Video ve konuları hakkında sohbet edeceğiz."))))
                chatHistory.add(ContentItem(role = "model", parts = listOf(Part(text = "Elbette! Video içeriğini çok iyi analiz ettim. Sadece videodaki konular üzerinde sorularınızı cevaplamaya ve size yardımcı olmaya hazırım. Lütfen istediğiniz soruyu sorun!"))))

                // Map existing bubbles
                for (msg in _chatMessages.value) {
                    val role = if (msg.isUser) "user" else "model"
                    chatHistory.add(ContentItem(role = role, parts = listOf(Part(text = msg.text))))
                }

                val aiResponseText = GeminiClient.generateContent(
                    apiKey = key,
                    systemInstructionText = systemInst,
                    chatHistory = chatHistory
                )

                val (cleanResponse, suggestions) = parseSuggestions(aiResponseText)

                val aiMsgObj = ChatMessage(
                    text = cleanResponse,
                    isUser = false,
                    suggestions = suggestions
                )
                
                _chatMessages.value = _chatMessages.value + aiMsgObj

            } catch (e: Exception) {
                val errorMsg = "Yapay zeka yanıt üretirken hata oluştu: ${e.localizedMessage}"
                val aiMsgObj = ChatMessage(
                    text = errorMsg,
                    isUser = false,
                    suggestions = generateFallbackSuggestions(activeAnalysis.videoTitle)
                )
                _chatMessages.value = _chatMessages.value + aiMsgObj
            } finally {
                _isChatSending.value = false
            }
        }
    }

    /**
     * Closes current result screen and returns home
     */
    fun closeCurrentAnalysis() {
        _currentAnalysis.value = null
        _transcriptSegments.value = emptyList()
        _summarySuggestions.value = emptyList()
        _chatMessages.value = emptyList()
        _analysisError.value = null
    }

    // --- Helper Parsers ---
    
    private fun parseSuggestions(text: String): Pair<String, List<String>> {
        val regex = Regex("""<<ÖNERİ:\s*(.+?)>>""")
        val matches = regex.findAll(text)
        val suggestions = matches.map { it.groupValues[1].trim() }.toList()
        
        // Strip suggestions completely from display output
        var cleanText = regex.replace(text, "").trim()
        
        // Sometimes LLM might write lists like "Öneriler:" or similar, let's keep them clean
        cleanText = cleanText.replace(Regex("""Olası Sorular:\s*$"""), "")
        cleanText = cleanText.replace(Regex("""Önerilen Sorular:\s*$"""), "")
        
        return Pair(cleanText.trim(), suggestions)
    }

    private fun generateFallbackSuggestions(videoTitle: String): List<String> {
        return listOf(
            "Videonun genel ana fikri nedir?",
            "Videoda bahsedilen en önemli kavramları listeler misin?",
            "Bu videodan çıkarılabilecek dersler/sonuçlar nelerdir?"
        )
    }

    private fun serializeSegments(segments: List<TranscriptSegment>): String {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, TranscriptSegment::class.java)
            val adapter = moshi.adapter<List<TranscriptSegment>>(listType)
            adapter.toJson(segments)
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun deserializeSegments(json: String): List<TranscriptSegment> {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, TranscriptSegment::class.java)
            val adapter = moshi.adapter<List<TranscriptSegment>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeQuestions(questions: List<String>): String {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(listType)
            adapter.toJson(questions)
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun deserializeQuestions(json: String): List<String> {
        return try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
