package com.example.stream

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class LocalStreamServer(
    private val context: Context,
    private val notificationManager: CamLinkNotificationManager,
    private val audioCapturer: AudioCapturer,
    val audioPlayer: AudioPlayer,
    val port: Int = 8080
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    
    // Security Passcode
    var passcode = "1234"
    
    // System Settings
    var lowDataMode = false
    var cameraMuted = false
    var micMuted = false

    // Web Broadcaster callback
    var webVideoCallback: ((ByteArray) -> Unit)? = null

    // Connection State Callback
    var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null

    // State indicators
    val isClientConnected = AtomicBoolean(false)
    val clientType = AtomicReference<String>("Boşta")
    private val activeConnectionsCount = AtomicInteger(0)
    private var lastUploadTime = 0L

    fun setConnectionState(connected: Boolean, type: String) {
        isClientConnected.set(connected)
        clientType.set(type)
        onConnectionStateChanged?.invoke(connected, type)
    }

    private fun onConnectionStart(type: String) {
        activeConnectionsCount.incrementAndGet()
        setConnectionState(true, type)
    }

    private fun onConnectionEnd() {
        val count = activeConnectionsCount.decrementAndGet()
        if (count <= 0) {
            activeConnectionsCount.set(0)
            setConnectionState(false, "Boşta")
        }
    }

    val latestFrame = AtomicReference<ByteArray>()

    // Set of active audio connection output streams
    private val audioStreams = Collections.newSetFromMap(ConcurrentHashMap<OutputStream, Boolean>())

    // Implementation of AudioListener to stream raw mic data to all web clients
    private val audioListener = object : AudioCapturer.AudioListener {
        override fun onAudioChunk(bytes: ByteArray) {
            if (micMuted) return
            val iterator = audioStreams.iterator()
            while (iterator.hasNext()) {
                val out = iterator.next()
                try {
                    // Send raw PCM bytes wrapped in simple HTTP chunks or direct bytes
                    out.write(bytes)
                    out.flush()
                } catch (e: Exception) {
                    iterator.remove()
                    if (audioStreams.isEmpty()) {
                        audioCapturer.unregisterListener(this)
                    }
                }
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        
        activeConnectionsCount.set(0)
        setConnectionState(false, "Boşta")
        lastUploadTime = System.currentTimeMillis()

        // Connection Watchdog for Web Broadcasters (uploading media)
        thread(name = "CamLinkWatchdog") {
            while (isRunning.get()) {
                try {
                    Thread.sleep(1000)
                    if (clientType.get() == "Yayıncı" && System.currentTimeMillis() - lastUploadTime > 3000) {
                        setConnectionState(false, "Boşta")
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {}
            }
        }

        thread(name = "CamLinkServerThread") {
            try {
                serverSocket = ServerSocket(port)
                notificationManager.addLog(
                    "Sunucu Başlatıldı",
                    "Uygulama sunucusu port $port üzerinde yayında. Çevrimdışı çalışmaya hazır.",
                    NotificationType.INFO
                )
                
                while (isRunning.get()) {
                    val socket = serverSocket?.accept() ?: break
                    thread(name = "CamLinkClientHandler") {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalStreamServer", "Server socket exception", e)
                notificationManager.addLog(
                    "Sunucu Hatası",
                    "Sunucu başlatılamadı veya durduruldu: ${e.localizedMessage}",
                    NotificationType.DANGER
                )
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
            serverSocket = null
            audioStreams.clear()
            audioCapturer.unregisterListener(audioListener)
            audioPlayer.stop()
            setConnectionState(false, "Boşta")
            notificationManager.addLog(
                "Sunucu Durduruldu",
                "Kamera ve ses sunucusu güvenli bir şekilde kapatıldı.",
                NotificationType.WARNING
            )
        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Error closing server socket", e)
        }
    }

    private fun handleClient(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val outputStream = socket.getOutputStream()
        
        try {
            val reqLine = reader.readLine() ?: return
            val parts = reqLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val uri = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                val colon = headerLine.indexOf(':')
                if (colon != -1) {
                    val key = headerLine.substring(0, colon).trim().lowercase()
                    val value = headerLine.substring(colon + 1).trim()
                    headers[key] = value
                }
                headerLine = reader.readLine()
            }

            // Extract query parameters
            val (path, queryParams) = parseUri(uri)
            val clientPasscode = queryParams["passcode"] ?: ""

            // 1. Serving the core Web Controller page (Passcode can be entered in UI, so serve HTML without blocking)
            if (path == "/" || path == "/index.html") {
                serveHtmlPage(outputStream)
                return
            }

            // 2. Security Auth check for API / Streaming endpoints
            if (clientPasscode != passcode) {
                sendUnauthorized(outputStream)
                notificationManager.addLog(
                    "Yetkisiz Erişim Engellendi",
                    "IP ${socket.inetAddress.hostAddress} hatalı şifre ile bağlanmaya çalıştı.",
                    NotificationType.DANGER
                )
                return
            }

            // Route endpoints
            when {
                path == "/status" -> {
                    val json = """{"cameraMuted":$cameraMuted,"micMuted":$micMuted,"lowDataMode":$lowDataMode}"""
                    sendJsonResponse(outputStream, json)
                }

                // Video stream (MJPEG) from Android -> Web Browser
                path == "/video" -> {
                    if (cameraMuted) {
                        sendJsonResponse(outputStream, """{"error":"Kamera sessize alınmış"}""")
                        return
                    }
                    onConnectionStart("İzleyici")
                    notificationManager.addLog(
                        "İzleyici Bağlandı",
                        "Yeni cihaz görüntüyü izlemeye başladı.",
                        NotificationType.SUCCESS
                    )

                    sendMjpegHeaders(outputStream)
                    val frameInterval = if (lowDataMode) 150L else 40L // Lower frame rate in low data mode
                    
                    try {
                        while (isRunning.get()) {
                            val frame = latestFrame.get()
                            if (frame != null) {
                                outputStream.write("--boundary\r\n".toByteArray())
                                outputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
                                outputStream.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                                outputStream.write(frame)
                                outputStream.write("\r\n".toByteArray())
                                outputStream.flush()
                            }
                            Thread.sleep(frameInterval)
                        }
                    } catch (e: Exception) {
                        // Connection closed
                    } finally {
                        onConnectionEnd()
                        notificationManager.addLog(
                            "İzleyici Ayrıldı",
                            "İzleyici bağlantıyı kapattı.",
                            NotificationType.WARNING
                        )
                    }
                }

                // Audio stream (raw PCM) from Android -> Web Browser
                path == "/audio" -> {
                    onConnectionStart("İzleyici")
                    sendAudioHeaders(outputStream)
                    audioStreams.add(outputStream)
                    audioCapturer.registerListener(audioListener)
                    
                    // Keep socket open
                    try {
                        while (isRunning.get() && audioStreams.contains(outputStream)) {
                            Thread.sleep(1000)
                        }
                    } catch (e: Exception) {
                        // Closed
                    } finally {
                        audioStreams.remove(outputStream)
                        onConnectionEnd()
                    }
                }

                // Video frame upload from Web Browser -> Android App
                path == "/upload-video" -> {
                    lastUploadTime = System.currentTimeMillis()
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        val bodyBytes = readBody(socket.getInputStream(), contentLength)
                        webVideoCallback?.invoke(bodyBytes)
                        if (clientType.get() != "Yayıncı") {
                            setConnectionState(true, "Yayıncı")
                        }
                    }
                    sendJsonResponse(outputStream, """{"status":"ok"}""")
                }

                // Audio frame upload from Web Browser -> Android App
                path == "/upload-audio" -> {
                    lastUploadTime = System.currentTimeMillis()
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        val bodyBytes = readBody(socket.getInputStream(), contentLength)
                        audioPlayer.write(bodyBytes)
                        if (clientType.get() != "Yayıncı") {
                            setConnectionState(true, "Yayıncı")
                        }
                    }
                    sendJsonResponse(outputStream, """{"status":"ok"}""")
                }

                else -> {
                    sendNotFound(outputStream)
                }
            }

        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Client handling exception", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun parseUri(uri: String): Pair<String, Map<String, String>> {
        val questionMark = uri.indexOf('?')
        if (questionMark == -1) return Pair(uri, emptyMap())
        
        val path = uri.substring(0, questionMark)
        val query = uri.substring(questionMark + 1)
        val params = mutableMapOf<String, String>()
        
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq != -1) {
                val key = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                val value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                params[key] = value
            }
        }
        return Pair(path, params)
    }

    private fun readBody(inputStream: java.io.InputStream, length: Int): ByteArray {
        val bodyBytes = ByteArray(length)
        var bytesRead = 0
        while (bytesRead < length) {
            val read = inputStream.read(bodyBytes, bytesRead, length - bytesRead)
            if (read == -1) break
            bytesRead += read
        }
        return bodyBytes
    }

    private fun serveHtmlPage(out: OutputStream) {
        val html = getHtmlContent()
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" +
                html
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendJsonResponse(out: OutputStream, json: String) {
        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${json.toByteArray().size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n" +
                json
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendUnauthorized(out: OutputStream) {
        val body = """{"error":"Yetkisiz erişim. Geçersiz şifre."}"""
        val response = "HTTP/1.1 401 Unauthorized\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n" +
                body
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendNotFound(out: OutputStream) {
        val body = "Sayfa Bulunamadı"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" +
                body
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendMjpegHeaders(out: OutputStream) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=boundary\r\n" +
                "Cache-Control: no-cache, private\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: keep-alive\r\n\r\n"
        out.write(headers.toByteArray())
        out.flush()
    }

    private fun sendAudioHeaders(out: OutputStream) {
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: audio/l16; rate=16000; channels=1\r\n" +
                "Cache-Control: no-cache\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: keep-alive\r\n\r\n"
        out.write(headers.toByteArray())
        out.flush()
    }

    private fun getHtmlContent(): String {
        return """
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CamLink Web Kontrol Merkezi</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body {
            background-color: #0d1117;
            color: #c9d1d9;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
        }
        .glowing-dot {
            box-shadow: 0 0 8px rgba(34, 197, 94, 0.8);
        }
    </style>
</head>
<body class="min-h-screen flex flex-col items-center justify-between p-4">
    
    <!-- Header -->
    <header class="w-full max-w-lg mb-4 text-center">
        <h1 class="text-3xl font-extrabold text-white tracking-wider bg-gradient-to-r from-emerald-400 to-teal-500 bg-clip-text text-transparent">
            CamLink P2P
        </h1>
        <p class="text-sm text-gray-400 mt-1">Uçtan Uca Doğrudan Bağlantı Platformu</p>
    </header>

    <!-- Main Card -->
    <main class="w-full max-w-lg bg-[#161b22] border border-gray-800 rounded-2xl p-6 shadow-2xl flex-1 flex flex-col justify-center gap-6">
        
        <!-- Auto-Connect One-Tap Screen -->
        <div id="autoconnect-section" class="hidden space-y-6 text-center py-4">
            <div class="w-16 h-16 bg-emerald-600/20 border border-emerald-500/30 rounded-full flex items-center justify-center mx-auto mb-2 animate-bounce">
                <span id="autoconnect-icon" class="text-3xl">📡</span>
            </div>
            <h2 class="text-lg font-extrabold text-white">CamLink Hızlı Bağlantı</h2>
            <p id="autoconnect-info" class="text-xs text-gray-400 max-w-sm mx-auto leading-relaxed">
                Diğer telefonla eşleşmek üzere hazır. Canlı ses ve görüntü akışını başlatmak için lütfen aşağıdaki butona basın.
            </p>
            <button onclick="startAutoConnectFlow()" class="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-3 px-6 rounded-xl shadow-lg transition-all text-sm tracking-wide flex items-center justify-center gap-2">
                🚀 BAĞLANTIYI BAŞLAT
            </button>
        </div>

        <!-- Auth Screen -->
        <div id="auth-section" class="space-y-4">
            <h2 class="text-lg font-bold text-white text-center">🔐 Güvenlik Doğrulaması</h2>
            <p class="text-xs text-gray-400 text-center">Lütfen Android cihazda görüntülenen güvenlik şifresini girin.</p>
            <div class="flex gap-2">
                <input type="password" id="passcode-input" placeholder="Güvenlik Şifresi" class="flex-1 bg-[#21262d] border border-gray-700 rounded-lg px-4 py-2 text-white focus:outline-none focus:border-emerald-500 text-center tracking-widest text-lg">
                <button onclick="verifyAuth()" class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-6 py-2 rounded-lg transition-colors">Doğrula</button>
            </div>
            <p id="auth-error" class="text-red-500 text-xs text-center hidden">Hatalı şifre! Lütfen tekrar deneyin.</p>
        </div>

        <!-- Main Workspace (Hidden before auth) -->
        <div id="workspace-section" class="hidden space-y-6">
            
            <!-- Connection Status Badge -->
            <div class="flex items-center justify-between bg-[#21262d] border border-gray-800 rounded-xl p-3">
                <span class="text-sm font-medium">Bağlantı Durumu:</span>
                <span class="flex items-center gap-2 text-emerald-400 text-sm font-semibold">
                    <span class="w-3 h-3 bg-emerald-500 rounded-full glowing-dot animate-pulse"></span>
                    Aktif P2P
                </span>
            </div>

            <!-- Choose Mode -->
            <div id="role-selection" class="space-y-4">
                <h3 class="font-bold text-white text-sm text-center">Lütfen bir Çalışma Modu Seçin:</h3>
                <div class="grid grid-cols-2 gap-3">
                    <button onclick="selectRole('viewer')" class="bg-gradient-to-br from-[#21262d] to-[#161b22] border-2 border-transparent hover:border-emerald-500 rounded-xl p-4 text-center transition-all">
                        <div class="text-2xl mb-1">👁️</div>
                        <div class="font-bold text-white text-sm">İzleyici Modu</div>
                        <div class="text-[10px] text-gray-400 mt-1">Karşı telefonun kamerası & sesini izleyin</div>
                    </button>
                    <button onclick="selectRole('broadcaster')" class="bg-gradient-to-br from-[#21262d] to-[#161b22] border-2 border-transparent hover:border-teal-500 rounded-xl p-4 text-center transition-all">
                        <div class="text-2xl mb-1">📹</div>
                        <div class="font-bold text-white text-sm">Yayıncı Modu</div>
                        <div class="text-[10px] text-gray-400 mt-1">Bu cihazın kamerasını karşıya aktarın</div>
                    </button>
                </div>
            </div>

            <!-- Viewer Workspace -->
            <div id="viewer-container" class="hidden space-y-4">
                <div class="flex items-center justify-between">
                    <h3 class="font-bold text-white text-sm">📺 Canlı Kamera Akışı</h3>
                    <button onclick="resetRole()" class="text-xs text-emerald-400 underline hover:text-emerald-300">Mod Değiştir</button>
                </div>
                
                <!-- Info Alert explaining camera permission in Viewer mode -->
                <div class="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl text-xs text-emerald-300 leading-relaxed">
                    ℹ️ <b>İzleyici Modu Bilgisi:</b> İzleyici modunda yalnızca karşıdaki cihazın yayınını izlersiniz. Bu cihazın kamerasını paylaşmadığınız için tarayıcınız <b>Kamera ve Mikrofon izni istemez</b>. Sesi dinlemek için aşağıdaki butona basın.
                </div>

                <div onclick="openFullscreen()" class="relative bg-black rounded-xl overflow-hidden aspect-[4/3] border border-gray-800 flex items-center justify-center cursor-pointer hover:border-emerald-500 transition-all">
                    <img id="stream-img" class="w-full h-full object-cover hidden" alt="Stream">
                    <div id="stream-placeholder" class="text-gray-500 text-center">
                        <div class="text-3xl mb-2">📡</div>
                        <div class="text-xs font-semibold">Yayın Bekleniyor...</div>
                        <div class="text-[10px] text-gray-400 mt-1">Android cihazda kameranın açık olduğundan emin olun</div>
                    </div>
                    <!-- Small click indicator -->
                    <div class="absolute bottom-2 right-2 bg-black/60 text-[10px] text-gray-300 px-2 py-0.5 rounded">🔍 Tam Ekran</div>
                </div>

                <div class="flex justify-center gap-2 flex-wrap">
                    <button id="btn-video-watch" onclick="toggleVideoWatch()" class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs flex items-center gap-2">
                        👁️ Görüntüyü İzle (Aktif)
                    </button>
                    <button id="btn-audio-listen" onclick="toggleAudioListen()" class="bg-[#21262d] hover:bg-[#30363d] text-white font-medium px-4 py-2 rounded-lg text-xs flex items-center gap-2 border border-gray-700">
                        🔊 Sesi Dinle (Kapalı)
                    </button>
                </div>
            </div>

            <!-- Broadcaster Workspace -->
            <div id="broadcaster-container" class="hidden space-y-4">
                <div class="flex items-center justify-between">
                    <h3 class="font-bold text-white text-sm">📹 Canlı Kamera Paylaşımı</h3>
                    <button onclick="resetRole()" class="text-xs text-teal-400 underline hover:text-teal-300">Mod Değiştir</button>
                </div>

                <!-- Info Alert explaining camera/mic rules for Broadcaster -->
                <div class="p-3 bg-teal-950/40 border border-teal-800/60 rounded-xl text-xs text-teal-300 leading-relaxed">
                    ⚠️ <b>Yayıncı Modu Bilgisi:</b> Bu mod cihazınızın kamerasını karşıya aktarır. Tarayıcının kamera ve mikrofon isteklerini onaylamanız şarttır. QR tarayıcı içi tarayıcılarda çalışmaz ise lütfen bu sayfayı kopyalayıp <b>Safari</b> veya <b>Chrome</b>'da açın.
                </div>

                <div class="relative bg-black rounded-xl overflow-hidden aspect-[4/3] border border-gray-800 flex items-center justify-center">
                    <video id="local-video" class="w-full h-full object-cover" autoplay playsinline muted></video>
                    <!-- Gesture-based permission and broadcast starter overlay -->
                    <div id="broadcaster-start-overlay" onclick="startBroadcastingWithGesture()" class="absolute inset-0 bg-black/90 flex flex-col items-center justify-center p-6 text-center cursor-pointer hover:bg-black/95 transition-all">
                        <div class="w-16 h-16 bg-teal-600/20 border border-teal-500/30 rounded-full flex items-center justify-center mb-3 animate-pulse">
                            <span class="text-3xl">📹</span>
                        </div>
                        <h4 class="text-white font-bold text-sm">Yayını Başlat</h4>
                        <p class="text-gray-400 text-[11px] mt-1 max-w-[280px]">
                            Kameranızı ve sesinizi anlık olarak paylaşmak için lütfen buraya dokunun ve izin verin.
                        </p>
                    </div>
                </div>

                <div class="flex justify-center gap-2">
                    <button id="btn-cam-toggle" onclick="toggleLocalCam()" class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        📷 Kamerayı Kapat
                    </button>
                    <button id="btn-mic-toggle" onclick="toggleLocalMic()" class="bg-teal-600 hover:bg-teal-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        🎙️ Mikrofonu Kapat
                    </button>
                </div>
            </div>

            <!-- Dynamic Compatibility Helper Panel -->
            <div id="permission-helper-panel" class="space-y-2 pt-2 border-t border-gray-800">
                <!-- Will be injected via Javascript -->
            </div>

        </div>
    </main>

    <!-- Footer -->
    <footer class="w-full text-center text-[10px] text-gray-500 mt-6 border-t border-gray-900 pt-3">
        CamLink &copy; 2026 | Tamamen Yerel ve Güvenli Veri Akışı
    </footer>

    <!-- Fullscreen Stream Overlay -->
    <div id="fullscreen-overlay" onclick="closeFullscreen()" class="hidden fixed inset-0 bg-black/95 z-[9999] flex flex-col items-center justify-center cursor-pointer">
        <img id="fullscreen-img" class="max-w-full max-h-full object-contain" alt="Stream Fullscreen">
        <div class="absolute top-4 right-4 bg-black/60 hover:bg-black/80 text-white rounded-full p-2">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
        </div>
        <div class="absolute bottom-4 bg-black/60 text-white px-4 py-2 rounded-lg text-xs tracking-wider">
            Gerçek Boyut (Kapatmak için dokunun)
        </div>
    </div>

    <!-- Script Block -->
    <script>
        let passcode = "";
        let role = "";
        let audioContext = null;
        let isListeningAudio = false;
        let isWatchingVideo = true;
        let audioStreamReader = null;

        // Local media elements
        let localStream = null;
        let localVideo = document.getElementById('local-video');
        let camMuted = false;
        let micMuted = false;
        let lowDataMode = false;
        let uploadInterval = null;
        let audioProcessor = null;

        async function checkPermissions() {
            const panel = document.getElementById('permission-helper-panel');
            if (!panel) return;

            let isSecure = window.isSecureContext;
            let hasMediaDevices = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);

            let html = `
                <div class="p-3 bg-[#1c2128] border border-gray-800 rounded-xl text-xs space-y-2">
                    <div class="font-bold text-gray-300 flex items-center gap-1">🔧 Tarayıcı İzin & Uyumluluk Kontrolü</div>
            `;

            if (isSecure) {
                html += `<div class="text-emerald-400">✓ Güvenli Bağlantı (HTTPS/Localhost)</div>`;
            } else {
                html += `<div class="text-amber-400">⚠️ <b>HTTP Bağlantısı:</b> Modern tarayıcılar (Safari, Chrome) güvenlik gereği güvensiz bağlantılarda Kamera/Mikrofon erişimini kısıtlar. Yayıncı modu çalışmayabilir ama İzleyici modu sorunsuz çalışır.</div>`;
            }

            if (hasMediaDevices) {
                html += `<div class="text-emerald-400">✓ Tarayıcı Kamera/Mikrofon API'sini Destekliyor</div>`;
            } else {
                html += `
                    <div class="text-red-400">
                        ❌ <b>Kamera/Mikrofon Desteği Engellendi:</b> 
                        Bunun nedeni muhtemelen bir uygulama içi (In-App) QR okuyucu tarayıcısındasınız (Instagram, WhatsApp vb.) ya da tarayıcınız çok eski.
                        <br/><br/>
                        <b>Nasıl Çözülür?</b>
                        <br/>
                        1. Bu sayfanın adresini üst kısımdan kopyalayın.
                        <br/>
                        2. Telefonunuzun ana tarayıcısını açın (iPhone'lar için <b>Safari</b>, Android'ler için <b>Chrome</b>).
                        <br/>
                        3. Adresi yapıştırıp açın. İzinlerin sorunsuz çalıştığını göreceksiniz!
                    </div>
                `;
            }

            try {
                const devices = await navigator.mediaDevices.enumerateDevices();
                const hasCam = devices.some(d => d.kind === 'videoinput');
                const hasMic = devices.some(d => d.kind === 'audioinput');
                html += '<div class="text-gray-400 text-[10px] mt-1">Cihaz Donanımı: ' + (hasCam ? '📷 Kamera Mevcut' : '❌ Kamera Yok') + ' | ' + (hasMic ? '🎙️ Mikrofon Mevcut' : '❌ Mikrofon Yok') + '</div>';
            } catch(e) {}

            html += `</div>`;
            panel.innerHTML = html;
        }

        function verifyAuth() {
            const input = document.getElementById('passcode-input').value;
            fetch('/status?passcode=' + input)
                .then(r => {
                    if (r.status === 200) {
                        passcode = input;
                        document.getElementById('auth-section').classList.add('hidden');
                        document.getElementById('workspace-section').classList.remove('hidden');
                        checkPermissions();
                    } else {
                        throw new Error("unauth");
                    }
                })
                .catch(e => {
                    document.getElementById('auth-error').classList.remove('hidden');
                });
        }

        function startAutoConnectFlow() {
            try {
                audioContext = new (window.AudioContext || window.webkitAudioContext)();
            } catch(e) {
                console.log("AudioContext init error:", e);
            }
            
            const urlParams = new URLSearchParams(window.location.search);
            const urlPasscode = urlParams.get('passcode') || "";
            const urlRole = urlParams.get('role') || "";
            
            if (urlPasscode) {
                passcode = urlPasscode;
                fetch('/status?passcode=' + passcode)
                    .then(r => {
                        if (r.status === 200) {
                            document.getElementById('autoconnect-section').classList.add('hidden');
                            document.getElementById('workspace-section').classList.remove('hidden');
                            checkPermissions();
                            
                            if (urlRole === 'viewer') {
                                selectRole('viewer');
                            } else if (urlRole === 'broadcaster') {
                                selectRole('broadcaster');
                            } else {
                                document.getElementById('role-selection').classList.remove('hidden');
                            }
                        } else {
                            alert("Şifre doğrulanamadı! Lütfen manuel olarak giriş yapın.");
                            showManualAuth();
                        }
                    })
                    .catch(e => {
                        alert("Bağlantı hatası! Lütfen manuel girişi deneyin.");
                        showManualAuth();
                    });
            } else {
                showManualAuth();
            }
        }
        
        function showManualAuth() {
            document.getElementById('autoconnect-section').classList.add('hidden');
            document.getElementById('auth-section').classList.remove('hidden');
        }

        function selectRole(selectedRole) {
            role = selectedRole;
            document.getElementById('role-selection').classList.add('hidden');
            
            if (role === 'viewer') {
                document.getElementById('viewer-container').classList.remove('hidden');
                startViewing();
            } else {
                document.getElementById('broadcaster-container').classList.remove('hidden');
                startBroadcasting();
            }
        }

        window.onload = function() {
            const urlParams = new URLSearchParams(window.location.search);
            const urlPasscode = urlParams.get('passcode');
            const urlRole = urlParams.get('role');
            
            if (urlPasscode && urlRole) {
                document.getElementById('auth-section').classList.add('hidden');
                document.getElementById('role-selection').classList.add('hidden');
                document.getElementById('autoconnect-section').classList.add('hidden');
                
                // Instantly try to connect and start streaming/broadcasting
                passcode = urlPasscode;
                fetch('/status?passcode=' + passcode)
                    .then(r => {
                        if (r.status === 200) {
                            document.getElementById('workspace-section').classList.remove('hidden');
                            checkPermissions();
                            selectRole(urlRole);
                        } else {
                            showManualAuth();
                        }
                    })
                    .catch(e => {
                        showManualAuth();
                    });
            } else {
                document.getElementById('auth-section').classList.remove('hidden');
            }
        };

        function resetRole() {
            // Stop active broadcast if any
            if (uploadInterval) {
                clearInterval(uploadInterval);
                uploadInterval = null;
            }
            if (audioProcessor) {
                audioProcessor.disconnect();
                audioProcessor = null;
            }
            if (localStream) {
                localStream.getTracks().forEach(track => track.stop());
                localStream = null;
            }
            if (audioContext) {
                audioContext.close();
                audioContext = null;
            }
            isListeningAudio = false;
            isWatchingVideo = true;
            if (audioStreamReader) {
                audioStreamReader.cancel();
                audioStreamReader = null;
            }
            
            // Hide workspaces and show role selection
            document.getElementById('viewer-container').classList.add('hidden');
            document.getElementById('broadcaster-container').classList.add('hidden');
            document.getElementById('role-selection').classList.remove('hidden');
            document.getElementById('stream-img').classList.add('hidden');
            document.getElementById('stream-placeholder').classList.remove('hidden');
            document.getElementById('btn-audio-listen').textContent = "🔊 Sesi Dinle (Kapalı)";
            const videoBtn = document.getElementById('btn-video-watch');
            if (videoBtn) {
                videoBtn.textContent = "👁️ Görüntüyü İzle (Aktif)";
                videoBtn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs flex items-center gap-2";
            }
        }

        // ================== VIEWER LOGIC ==================
        function startViewing() {
            isWatchingVideo = true;
            const img = document.getElementById('stream-img');
            img.src = '/video?passcode=' + passcode;
            img.classList.remove('hidden');
            document.getElementById('stream-placeholder').classList.add('hidden');
            const videoBtn = document.getElementById('btn-video-watch');
            if (videoBtn) {
                videoBtn.textContent = "👁️ Görüntüyü İzle (Aktif)";
                videoBtn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs flex items-center gap-2";
            }
        }

        function toggleVideoWatch() {
            isWatchingVideo = !isWatchingVideo;
            const img = document.getElementById('stream-img');
            const placeholder = document.getElementById('stream-placeholder');
            const btn = document.getElementById('btn-video-watch');
            
            if (isWatchingVideo) {
                img.src = '/video?passcode=' + passcode;
                img.classList.remove('hidden');
                placeholder.classList.add('hidden');
                btn.textContent = "👁️ Görüntüyü İzle (Aktif)";
                btn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs flex items-center gap-2";
            } else {
                img.src = '';
                img.classList.add('hidden');
                placeholder.classList.remove('hidden');
                btn.textContent = "👁️ Görüntüyü İzle (Kapalı)";
                btn.className = "bg-red-600 hover:bg-red-500 text-white font-semibold px-4 py-2 rounded-lg text-xs flex items-center gap-2";
            }
        }

        async function toggleAudioListen() {
            const btn = document.getElementById('btn-audio-listen');
            if (isListeningAudio) {
                isListeningAudio = false;
                btn.textContent = "🔊 Sesi Dinle (Kapalı)";
                if (audioStreamReader) audioStreamReader.cancel();
            } else {
                isListeningAudio = true;
                btn.textContent = "🔊 Sesi Dinle (Aktif)";
                playAudioStream();
            }
        }

        async function playAudioStream() {
            audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
            let nextStartTime = audioContext.currentTime;
            
            try {
                const response = await fetch('/audio?passcode=' + passcode);
                audioStreamReader = response.body.getReader();
                const bufferSize = 2048;
                let leftOver = new Int16Array(0);

                while (isListeningAudio) {
                    const { value, done } = await audioStreamReader.read();
                    if (done) break;

                    const data = new Int16Array(value.buffer, value.byteOffset, value.byteLength / 2);
                    const combined = new Int16Array(leftOver.length + data.length);
                    combined.set(leftOver);
                    combined.set(data, leftOver.length);

                    let offset = 0;
                    while (offset + bufferSize <= combined.length) {
                        const chunk = combined.subarray(offset, offset + bufferSize);
                        offset += bufferSize;

                        const f32 = new Float32Array(bufferSize);
                        for (let i = 0; i < bufferSize; i++) {
                            f32[i] = chunk[i] / 32768.0;
                        }

                        const audioBuffer = audioContext.createBuffer(1, bufferSize, 16000);
                        audioBuffer.getChannelData(0).set(f32);

                        const source = audioContext.createBufferSource();
                        source.buffer = audioBuffer;
                        source.connect(audioContext.destination);

                        const now = audioContext.currentTime;
                        if (nextStartTime < now) {
                            nextStartTime = now + 0.05;
                        }
                        source.start(nextStartTime);
                        nextStartTime += audioBuffer.duration;
                    }
                    leftOver = combined.subarray(offset);
                }
            } catch (e) {
                console.error("Audio playback error", e);
            }
        }

        // ================== BROADCASTER LOGIC ==================
        async function getStreamWithFallback() {
            // Attempt 1: back camera with audio
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: "environment" }, width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 15 } },
                    audio: true
                });
            } catch (err) {
                console.warn("Attempt 1 failed (back camera + audio):", err);
            }

            // Attempt 2: any camera with audio
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: true,
                    audio: true
                });
            } catch (err) {
                console.warn("Attempt 2 failed (any camera + audio):", err);
            }

            // Attempt 3: back camera ONLY (no audio)
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: "environment" }, width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 15 } },
                    audio: false
                });
            } catch (err) {
                console.warn("Attempt 3 failed (back camera only):", err);
            }

            // Attempt 4: any camera ONLY (no audio)
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: true,
                    audio: false
                });
            } catch (err) {
                console.warn("Attempt 4 failed (any camera only):", err);
                throw err;
            }
        }

        async function startBroadcasting() {
            localVideo = document.getElementById('local-video');
            try {
                localStream = await getStreamWithFallback();
                localVideo.srcObject = localStream;
                try {
                    await localVideo.play();
                } catch (playErr) {
                    console.warn("Auto-play blocked", playErr);
                }
                
                const overlay = document.getElementById('broadcaster-start-overlay');
                if (overlay) overlay.classList.add('hidden');
                
                startUploadingMedia();
            } catch (e) {
                console.warn("Auto startBroadcasting blocked or failed. User gesture will be required:", e);
                const overlay = document.getElementById('broadcaster-start-overlay');
                if (overlay) overlay.classList.remove('hidden');
            }
        }

        async function startBroadcastingWithGesture() {
            localVideo = document.getElementById('local-video');
            const overlay = document.getElementById('broadcaster-start-overlay');
            
            // Show custom visual loading cue
            if (overlay) {
                overlay.innerHTML = `
                    <div class="flex flex-col items-center">
                        <div class="w-12 h-12 border-4 border-teal-500 border-t-transparent rounded-full animate-spin mb-4"></div>
                        <h4 class="text-white font-bold text-sm">Bağlantı Kuruluyor...</h4>
                        <p class="text-gray-400 text-[10px] mt-1">Lütfen kamera ve mikrofon erişimine izin verin.</p>
                    </div>
                `;
            }
            
            try {
                // Initialize and resume AudioContext immediately on user gesture to prevent browser blocking
                try {
                    audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
                    if (audioContext.state === 'suspended') {
                        await audioContext.resume();
                    }
                } catch (audioErr) {
                    console.warn("AudioContext init on gesture failed:", audioErr);
                }

                // Request camera & audio stream using the robust helper
                localStream = await getStreamWithFallback();
                
                localVideo.srcObject = localStream;
                try {
                    await localVideo.play();
                } catch (playErr) {
                    console.warn("Video playback autoplay blocked on gesture", playErr);
                }
                
                if (overlay) overlay.classList.add('hidden');
                startUploadingMedia();
            } catch (fallbackErr) {
                console.error("Camera/Mic permissions fully denied:", fallbackErr);
                // Reset overlay state so they can retry
                if (overlay) {
                    overlay.innerHTML = `
                        <div class="w-16 h-16 bg-red-600/20 border border-red-500/30 rounded-full flex items-center justify-center mb-3">
                            <span class="text-3xl">⚠️</span>
                        </div>
                        <h4 class="text-red-400 font-bold text-sm">İzin Alınamadı</h4>
                        <p class="text-gray-400 text-[11px] mt-1 max-w-[280px]">
                            Kamera ve mikrofon izni engellendi. Lütfen tarayıcı ayarlarından izinleri aktif edip tekrar dokunun.
                        </p>
                    `;
                }
                alert("Kamera ve Mikrofon izni gereklidir! Lütfen tarayıcınızın adres çubuğundaki kilit simgesine dokunarak izinlerin verildiğinden emin olun.");
            }
        }

        function startUploadingMedia() {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            
            // Video frame compressor & uploader loop
            if (uploadInterval) clearInterval(uploadInterval);
            uploadInterval = setInterval(() => {
                if (!camMuted && localStream) {
                    canvas.width = 480;
                    canvas.height = 360;
                    if (localVideo) {
                        ctx.drawImage(localVideo, 0, 0, canvas.width, canvas.height);
                    }
                    canvas.toBlob((blob) => {
                        if (blob) {
                            fetch('/upload-video?passcode=' + passcode, {
                                method: 'POST',
                                body: blob
                            }).catch(() => {});
                        }
                    }, 'image/jpeg', 0.6);
                }
            }, 70);
            
            // Safe AudioContext checks and connection
            try {
                if (localStream && localStream.getAudioTracks().length > 0) {
                    if (!audioContext || audioContext.state === 'closed') {
                        audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
                    }
                    
                    const source = audioContext.createMediaStreamSource(localStream);
                    audioProcessor = audioContext.createScriptProcessor(1024, 1, 1);
                    source.connect(audioProcessor);
                    audioProcessor.connect(audioContext.destination);

                    audioProcessor.onaudioprocess = (e) => {
                        if (!micMuted) {
                            const inputData = e.inputBuffer.getChannelData(0);
                            const pcm16 = new Int16Array(inputData.length);
                            for (let i = 0; i < inputData.length; i++) {
                                pcm16[i] = Math.max(-1, Math.min(1, inputData[i])) * 0x7FFF;
                            }
                            fetch('/upload-audio?passcode=' + passcode, {
                                method: 'POST',
                                body: pcm16.buffer
                            }).catch(() => {});
                        }
                    };
                } else {
                    console.warn("Audio tracks are not available. Audio streaming is disabled.");
                }
            } catch (audioErr) {
                console.error("Could not initialize audio processor on broadcast:", audioErr);
            }
        }

        function toggleLocalCam() {
            camMuted = !camMuted;
            const btn = document.getElementById('btn-cam-toggle');
            if (btn && localVideo && localVideo.srcObject) {
                const tracks = localVideo.srcObject.getVideoTracks();
                if (tracks && tracks.length > 0) {
                    tracks[0].enabled = !camMuted;
                }
            }
            if (btn) {
                if (camMuted) {
                    btn.textContent = "📷 Kamerayı Aç";
                    btn.className = "bg-red-600 hover:bg-red-500 text-white font-semibold px-4 py-2 rounded-lg text-xs";
                } else {
                    btn.textContent = "📷 Kamerayı Kapat";
                    btn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs";
                }
            }
        }

        function toggleLocalMic() {
            micMuted = !micMuted;
            const btn = document.getElementById('btn-mic-toggle');
            if (btn) {
                if (micMuted) {
                    btn.textContent = "🎙️ Mikrofonu Aç";
                    btn.className = "bg-red-600 hover:bg-red-500 text-white font-semibold px-4 py-2 rounded-lg text-xs";
                } else {
                    btn.textContent = "🎙️ Mikrofonu Kapat";
                    btn.className = "bg-teal-600 hover:bg-teal-500 text-white font-semibold px-4 py-2 rounded-lg text-xs";
                }
            }
        }

        function openFullscreen() {
            if (!isWatchingVideo) return;
            const overlay = document.getElementById('fullscreen-overlay');
            const fullImg = document.getElementById('fullscreen-img');
            fullImg.src = '/video?passcode=' + passcode;
            overlay.classList.remove('hidden');
        }

        function closeFullscreen() {
            const overlay = document.getElementById('fullscreen-overlay');
            const fullImg = document.getElementById('fullscreen-img');
            fullImg.src = '';
            overlay.classList.add('hidden');
        }

        // Global tap to start broadcasting if the start overlay is active
        document.addEventListener('click', function globalClickToStart(e) {
            const overlay = document.getElementById('broadcaster-start-overlay');
            if (overlay && !overlay.classList.contains('hidden')) {
                startBroadcastingWithGesture();
                document.removeEventListener('click', globalClickToStart);
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}
