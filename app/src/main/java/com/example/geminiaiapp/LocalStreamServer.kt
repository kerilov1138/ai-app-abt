package com.example.geminiaiapp

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

fun getWifiSignalLevel(context: Context): Int {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        if (wifiManager != null) {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            if (info != null && info.rssi != -127) {
                val rssi = info.rssi
                return when {
                    rssi >= -50 -> 4      // Excellent
                    rssi >= -65 -> 3      // Good
                    rssi >= -75 -> 2      // Fair
                    rssi >= -85 -> 1      // Weak
                    else -> 0             // Extremely poor / None
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    return 4
}

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
    var cameraMuted = true
    var micMuted = false
    var androidMode = "broadcaster"
    @Volatile var isIncomingAudioMuted = true

    private var cachedMutedFrame: ByteArray? = null

    private fun getMutedFrameBytes(): ByteArray {
        cachedMutedFrame?.let { return it }
        try {
            val width = 480
            val height = 360
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#121214")
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E0E0E0")
                textSize = 24f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val descPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8E8E93")
                textSize = 16f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            canvas.drawText("📹 Kamera Yayını Duraklatıldı", width / 2f, height / 2f - 15f, textPaint)
            canvas.drawText("Telefondan kamerayı açarak izleyebilirsiniz.", width / 2f, height / 2f + 25f, descPaint)

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
            val bytes = stream.toByteArray()
            cachedMutedFrame = bytes
            return bytes
        } catch (e: Exception) {
            android.util.Log.e("LocalStreamServer", "Error generating muted frame", e)
            return ByteArray(0)
        }
    }

    // Baby Cry and Motion settings/status
    val babyCryActive = AtomicBoolean(false)
    val motionActive = AtomicBoolean(false)
    val notifyBabyCry = AtomicBoolean(true)
    val notifyMotion = AtomicBoolean(true)
    val beepOnBabyCry = AtomicBoolean(true)

    fun triggerBabyCry() {
        if (!notifyBabyCry.get()) return
        if (babyCryActive.compareAndSet(false, true)) {
            notificationManager.addLog(
                "🍼 Bebek Ağlaması Algılandı",
                "Odanın ses seviyesinde ani artış var!",
                NotificationType.DANGER
            )
            Thread {
                Thread.sleep(3000)
                babyCryActive.set(false)
            }.start()
        }
    }

    fun triggerMotion(isWebSource: Boolean = false) {
        if (!notifyMotion.get()) return
        if (motionActive.compareAndSet(false, true)) {
            if (isWebSource || androidMode != "broadcaster") {
                notificationManager.addLog(
                    "🏃 Hareket Algılandı",
                    "Kamerada hareketlilik tespit edildi!",
                    NotificationType.WARNING
                )
            }
            Thread {
                Thread.sleep(3000)
                motionActive.set(false)
            }.start()
        }
    }

    // Web Broadcaster callback
    var webVideoCallback: ((ByteArray) -> Unit)? = null

    // Connection State Callback
    var onConnectionStateChanged: ((Boolean, String) -> Unit)? = null

    // State indicators
    val isClientConnected = AtomicBoolean(false)
    val clientType = AtomicReference<String>("Boşta")
    val isWebBroadcasterConnected = AtomicBoolean(false)
    val isWebViewerConnected = AtomicBoolean(false)
    val webTorchActive = AtomicBoolean(false)
    private val activeConnectionsCount = AtomicInteger(0)
    private var lastUploadTime = 0L

    fun updateConnectionState() {
        val hasBroadcaster = isWebBroadcasterConnected.get()
        val hasViewer = isWebViewerConnected.get()
        
        if (!hasBroadcaster) {
            webTorchActive.set(false)
        }
        
        val connected: Boolean
        val type: String
        if (hasBroadcaster) {
            connected = true
            type = "Yayıncı"
        } else if (hasViewer) {
            connected = true
            type = "İzleyici"
        } else {
            connected = false
            type = "Boşta"
        }
        
        isClientConnected.set(connected)
        clientType.set(type)
        onConnectionStateChanged?.invoke(connected, type)
    }

    fun setConnectionState(connected: Boolean, type: String) {
        if (type == "Yayıncı") {
            isWebBroadcasterConnected.set(connected)
        } else if (type == "İzleyici") {
            isWebViewerConnected.set(connected)
        } else if (type == "Boşta") {
            isWebBroadcasterConnected.set(false)
            isWebViewerConnected.set(false)
        }
        updateConnectionState()
    }

    private fun onConnectionStart(type: String) {
        activeConnectionsCount.incrementAndGet()
        if (type == "İzleyici") {
            isWebViewerConnected.set(true)
        } else if (type == "Yayıncı") {
            isWebBroadcasterConnected.set(true)
        }
        updateConnectionState()
    }

    private fun onConnectionEnd() {
        val count = activeConnectionsCount.decrementAndGet()
        if (count <= 0) {
            activeConnectionsCount.set(0)
            isWebViewerConnected.set(false)
        }
        updateConnectionState()
    }

    val latestFrame = AtomicReference<ByteArray>()

    // Set of active audio connection output streams
    private val audioStreams = Collections.newSetFromMap(ConcurrentHashMap<OutputStream, Boolean>())

    // Implementation of AudioListener to stream raw mic data to all web clients
    private val audioListener = object : AudioCapturer.AudioListener {
        private var aboveThresholdCount = 0

        private fun calculateDbOfChunk(audioData: ByteArray): Double {
            var sum = 0.0
            val numSamples = audioData.size / 2
            if (numSamples == 0) return 0.0
            for (i in 0 until numSamples) {
                // Read 16-bit PCM sample (little endian)
                val sample = ((audioData[i * 2 + 1].toInt() shl 8) or (audioData[i * 2].toInt() and 0xFF)).toShort()
                sum += sample * sample
            }
            val rms = Math.sqrt(sum / numSamples)
            if (rms <= 0.0) return 0.0
            val db = 20 * Math.log10(rms / 32767.0) + 100.0
            return if (db < 0.0) 0.0 else db
        }

        override fun onAudioChunk(bytes: ByteArray) {
            // Sound analysis for Baby Cry Detection
            if (notifyBabyCry.get()) {
                val db = calculateDbOfChunk(bytes)
                val threshold = notificationManager.settings.value.babyCryThresholdDb
                if (db >= threshold) {
                    aboveThresholdCount++
                    if (aboveThresholdCount >= 30) { // ~1 second of continuous sound above threshold
                        triggerBabyCry()
                        aboveThresholdCount = 0 // reset to avoid flood
                    }
                } else {
                    if (aboveThresholdCount > 0) {
                        aboveThresholdCount--
                    }
                }
            }

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
                }
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        
        audioCapturer.registerListener(audioListener)
        activeConnectionsCount.set(0)
        setConnectionState(false, "Boşta")
        lastUploadTime = System.currentTimeMillis()

        // Connection Watchdog for Web Broadcasters (uploading media)
        thread(name = "CamLinkWatchdog") {
            while (isRunning.get()) {
                try {
                    Thread.sleep(1000)
                    if (isWebBroadcasterConnected.get() && System.currentTimeMillis() - lastUploadTime > 3000) {
                        isWebBroadcasterConnected.set(false)
                        updateConnectionState()
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

    private fun readLine(inputStream: java.io.InputStream): String? {
        val bos = java.io.ByteArrayOutputStream()
        while (true) {
            val b = inputStream.read()
            if (b == -1) {
                if (bos.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                bos.write(b)
            }
        }
        return bos.toString("UTF-8")
    }

    private fun handleClient(socket: Socket) {
        val inputStream = socket.getInputStream()
        val outputStream = socket.getOutputStream()
        
        try {
            val reqLine = readLine(inputStream) ?: return
            val parts = reqLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val uri = parts[1]

            // Parse headers
            val headers = mutableMapOf<String, String>()
            var headerLine = readLine(inputStream)
            while (!headerLine.isNullOrEmpty()) {
                val colon = headerLine.indexOf(':')
                if (colon != -1) {
                    val key = headerLine.substring(0, colon).trim().lowercase()
                    val value = headerLine.substring(colon + 1).trim()
                    headers[key] = value
                }
                headerLine = readLine(inputStream)
            }

            // Extract query parameters
            val (path, queryParams) = parseUri(uri)
            val clientPasscode = queryParams["passcode"] ?: ""

            // 1. Serving the core Web Controller page (Passcode can be entered in UI, so serve HTML without blocking)
            if (path == "/" || path == "/index.html") {
                serveHtmlPage(outputStream)
                return
            }

            if (path == "/download-apk" || path == "/app-debug.apk") {
                serveApkFile(outputStream)
                return
            }

            if (path == "/download-python" || path == "/camlink.py") {
                servePythonFile(outputStream)
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
                    var isTorchOn = false
                    if (isWebBroadcasterConnected.get()) {
                        isTorchOn = webTorchActive.get()
                    } else {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isTorchOn = StreamingService.activeCameraCapturer?.isTorchOn() ?: false
                            latch.countDown()
                        }
                        try { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: Exception) {}
                    }
                    val wifiLevel = getWifiSignalLevel(context)
                    val isFrontCam = StreamingService.activeCameraCapturer?.isFrontCameraActive() ?: false
                    val json = """{"cameraMuted":$cameraMuted,"micMuted":$micMuted,"lowDataMode":$lowDataMode,"babyCryActive":${babyCryActive.get()},"motionActive":${motionActive.get()},"beepOnBabyCry":${beepOnBabyCry.get()},"notifyMotion":${notifyMotion.get()},"notifyBabyCry":${notifyBabyCry.get()},"androidMode":"$androidMode","torchActive":$isTorchOn,"wifiSignal":$wifiLevel,"isFrontCamera":$isFrontCam}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/toggle-torch" -> {
                    var newState = false
                    if (isWebBroadcasterConnected.get()) {
                        newState = !webTorchActive.get()
                        webTorchActive.set(newState)
                    } else {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val cap = StreamingService.activeCameraCapturer
                            if (cap != null) {
                                newState = !cap.isTorchOn()
                                cap.setTorchEnabled(newState)
                            }
                            latch.countDown()
                        }
                        try { latch.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: Exception) {}
                    }
                    val json = """{"success":true,"torchActive":$newState}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/set-torch" -> {
                    val enable = queryParams["enabled"] == "true"
                    if (isWebBroadcasterConnected.get()) {
                        webTorchActive.set(enable)
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            StreamingService.activeCameraCapturer?.setTorchEnabled(enable)
                        }
                    }
                    val json = """{"success":true,"torchActive":$enable}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/get-torch" -> {
                    var isTorchOn = false
                    if (isWebBroadcasterConnected.get()) {
                        isTorchOn = webTorchActive.get()
                    } else {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            isTorchOn = StreamingService.activeCameraCapturer?.isTorchOn() ?: false
                            latch.countDown()
                        }
                        try { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (e: Exception) {}
                    }
                    val json = """{"success":true,"torchActive":$isTorchOn}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/disconnect" -> {
                    isWebBroadcasterConnected.set(false)
                    isWebViewerConnected.set(false)
                    webTorchActive.set(false)
                    updateConnectionState()
                    val json = """{"status":"ok"}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/set-low-data" -> {
                    val active = queryParams["active"] == "true"
                    lowDataMode = active
                    val json = """{"success":true,"lowDataMode":$lowDataMode}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/trigger-baby-cry" -> {
                    triggerBabyCry()
                    val json = """{"success":true,"babyCryActive":${babyCryActive.get()}}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/trigger-motion" -> {
                    triggerMotion(isWebSource = true)
                    val json = """{"success":true,"motionActive":${motionActive.get()}}"""
                    sendJsonResponse(outputStream, json)
                }

                path == "/update-settings" -> {
                    val bCry = queryParams["notifyBabyCry"]
                    if (bCry != null) notifyBabyCry.set(bCry == "true")
                    val mot = queryParams["notifyMotion"]
                    if (mot != null) notifyMotion.set(mot == "true")
                    val beep = queryParams["beepOnBabyCry"]
                    if (beep != null) beepOnBabyCry.set(beep == "true")
                    
                    val json = """{"success":true,"notifyBabyCry":${notifyBabyCry.get()},"notifyMotion":${notifyMotion.get()},"beepOnBabyCry":${beepOnBabyCry.get()}}"""
                    sendJsonResponse(outputStream, json)
                }

                // Video stream (MJPEG) from Android -> Web Browser
                path == "/video" -> {
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
                            val frame = if (cameraMuted) {
                                getMutedFrameBytes()
                            } else {
                                latestFrame.get()
                            }
                            if (frame != null) {
                                outputStream.write("--boundary\r\n".toByteArray())
                                outputStream.write("Content-Type: image/jpeg\r\n".toByteArray())
                                outputStream.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                                outputStream.write(frame)
                                outputStream.write("\r\n".toByteArray())
                                outputStream.flush()
                            }
                            // Sleep longer if muted to conserve CPU and network
                            Thread.sleep(if (cameraMuted) 1000L else frameInterval)
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
                        val bodyBytes = readBody(inputStream, contentLength)
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
                        val bodyBytes = readBody(inputStream, contentLength)
                        if (!isIncomingAudioMuted) {
                            audioPlayer.write(bodyBytes)
                        } else {
                            audioPlayer.stop()
                        }
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

    private fun serveApkFile(out: OutputStream) {
        try {
            val apkPath = context.packageCodePath
            val apkFile = java.io.File(apkPath)
            if (apkFile.exists()) {
                val size = apkFile.length()
                val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/vnd.android.package-archive\r\n" +
                        "Content-Length: $size\r\n" +
                        "Content-Disposition: attachment; filename=\"CamLink.apk\"\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(headers.toByteArray())
                
                val fis = java.io.FileInputStream(apkFile)
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                fis.close()
                out.flush()
                notificationManager.addLog(
                    "APK İndirildi",
                    "Uygulama APK dosyası yerel ağ üzerinden bir cihaza başarıyla aktarıldı.",
                    NotificationType.SUCCESS
                )
            } else {
                sendNotFound(out)
            }
        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Error serving APK file", e)
            sendNotFound(out)
        }
    }

    private fun servePythonFile(out: OutputStream) {
        try {
            var localIp = "192.168.1.1"
            try {
                val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = java.util.Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val sAddr = addr.hostAddress
                            if (sAddr != null && sAddr.indexOf(':') < 0) {
                                localIp = sAddr
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            val pyCode = """
# -*- coding: utf-8 -*-
import cv2
import requests
import time
import sys

def main():
    print("=" * 60)
    print("           CAMLINK PYTHON BROADCASTER (Webcam Stream)   ")
    print("=" * 60)
    
    default_ip = "$localIp"
    default_port = $port
    default_passcode = "$passcode"
    
    print("Bu script bilgisayarınızın kamerasını doğrudan Android uygulamasına")
    print("yüksek hızlı bir şekilde aktarır.")
    print("-" * 60)
    
    ip = input(f"Hedef IP [{default_ip}]: ").strip() or default_ip
    port_input = input(f"Hedef Port [{default_port}]: ").strip()
    port = int(port_input) if port_input else default_port
    passcode = input(f"Geçiş Anahtarı [{default_passcode}]: ").strip() or default_passcode
    
    print(f"\nHedefe bağlanılıyor: http://{ip}:{port} (Passcode: {passcode})")
    
    # Initialize OpenCV Camera
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Hata: Kamera açılamadı! OpenCV kamerayı kullanamıyor veya kamera başka bir uygulama tarafından meşgul.")
        sys.exit(1)
        
    print("\n[BAŞARILI] Kamera açıldı. Yayın başlatılıyor...")
    print(" -> Yayını durdurmak için kamera penceresine tıklayıp 'q' tuşuna basın.")
    print(" -> Terminalde yayının durumunu '.' (Başarılı) veya 'x' (Hata) olarak izleyebilirsiniz.")
    print("-" * 60)
    
    url = f"http://{ip}:{port}/upload-video?passcode={passcode}"
    
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                print("\nHata: Kameradan görüntü alınamadı.")
                break
                
            # Resize image to a maximum width of 640 for performance and reliability
            h, w = frame.shape[:2]
            if w > 640:
                frame = cv2.resize(frame, (640, int(640 * h / w)))
                
            # Compress to JPEG with 60% quality
            ret, jpeg = cv2.imencode('.jpg', frame, [int(cv2.IMWRITE_JPEG_QUALITY), 60])
            if not ret:
                continue
                
            # Send frame to CamLink Android app via HTTP POST
            try:
                resp = requests.post(url, data=jpeg.tobytes(), timeout=1.0)
                if resp.status_code == 200:
                    print(".", end="", flush=True)
                elif resp.status_code == 401:
                    print("\nHata: Geçiş Anahtarı (Passcode) geçersiz!")
                    break
                else:
                    print(f"\nHata: Sunucu kodu {resp.status_code}")
            except Exception as e:
                print("x", end="", flush=True)
                
            # Show preview
            cv2.imshow("CamLink - PC Broadcaster", frame)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
                
            time.sleep(0.04) # ~25 FPS
    except KeyboardInterrupt:
        print("\nYayın kullanıcı tarafından durduruldu.")
    finally:
        cap.release()
        cv2.destroyAllWindows()
        print("\nBağlantı kapatıldı.")

if __name__ == "__main__":
    main()
            """.trimIndent()

            val bytes = pyCode.toByteArray(Charsets.UTF_8)
            val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Content-Disposition: attachment; filename=\"camlink.py\"\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(headers.toByteArray())
            out.write(bytes)
            out.flush()
            notificationManager.addLog(
                "Python Script İndirildi",
                "Yüksek performanslı PC webcam yayıncısı (camlink.py) başarıyla indirildi.",
                NotificationType.SUCCESS
            )
        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Error serving Python script", e)
            sendNotFound(out)
        }
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
    <!-- Alert toast container for notifications like baby cry/motion -->
    <div id="alert-toast-container" class="fixed top-4 left-1/2 -translate-x-1/2 z-[10000] w-full max-w-sm px-4 space-y-2 flex flex-col items-center"></div>
    
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
                <p id="android-device-status" class="text-xs text-center mt-1"></p>
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
                </div>
                
                <!-- Info Alert explaining camera permission in Viewer mode -->
                <div class="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl text-xs text-emerald-300 leading-relaxed">
                    ℹ️ <b>İzleyici Modu Bilgisi:</b> İzleyici modunda yalnızca karşıdaki cihazın yayınını izlersiniz. Bu cihazın kamerasını paylaşmadığınız için tarayıcınız <b>Kamera ve Mikrofon izni istemez</b>. Sesi dinlemek için aşağıdaki butona basın.
                </div>

                <div onclick="openFullscreen()" class="relative bg-black rounded-xl overflow-hidden aspect-[4/3] border border-gray-800 flex items-center justify-center cursor-pointer hover:border-emerald-500 transition-all">
                    <div id="stream-wrapper" class="w-full h-full flex items-center justify-center">
                        <img id="stream-img" class="w-full h-full object-cover hidden transition-transform duration-75 origin-center" alt="Stream">
                    </div>
                    <div id="stream-placeholder" class="text-gray-500 text-center absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                        <div class="text-3xl mb-2">📡</div>
                        <div class="text-xs font-semibold">Yayın Bekleniyor...</div>
                        <div class="text-[10px] text-gray-400 mt-1">Android cihazda kameranın açık olduğundan emin olun</div>
                    </div>
                    <!-- Small click indicator -->
                    <div class="absolute bottom-2 right-2 bg-black/60 text-[10px] text-gray-300 px-2 py-0.5 rounded">🔍 Tam Ekran</div>
                </div>

                <!-- Resolution Selection Panel -->
                <div class="flex items-center justify-between bg-[#1c2128] px-3 py-2 rounded-xl border border-gray-800 text-xs">
                    <span class="text-gray-400 font-semibold">Yayın Akıcılığı:</span>
                    <div class="flex items-center gap-2">
                        <span class="text-[10px] text-gray-400">Çözünürlük:</span>
                        <select id="res-select" class="bg-[#21262d] text-xs text-white border border-gray-700 rounded px-2 py-1 outline-none cursor-pointer" onchange="changeResolution(this.value)">
                            <option value="high">Yüksek (HD)</option>
                            <option value="low">Düşük (Akıcı)</option>
                        </select>
                    </div>
                </div>

                <div class="flex items-center justify-center gap-2 flex-wrap bg-[#1c2128] p-3 rounded-xl border border-gray-800">
                    <button id="btn-video-watch" onclick="toggleVideoWatch()" class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5">
                        👁️ İzle
                    </button>
                    <button id="btn-audio-listen" onclick="toggleAudioListen()" class="bg-[#21262d] hover:bg-[#30363d] text-white font-medium px-3 py-2 rounded-lg text-xs flex items-center gap-1.5 border border-gray-700">
                        🔊 Dinle
                    </button>
                    
                    <!-- Volume Bar -->
                    <div class="flex items-center gap-1 bg-[#21262d] border border-gray-700 px-2.5 py-1.5 rounded-lg">
                        <span class="text-[10px] text-gray-400 font-bold">Ses:</span>
                        <input type="range" id="vol-slider" min="0" max="1" step="0.05" value="0.5" class="w-16 accent-emerald-500" oninput="updateVolume(this.value)">
                    </div>
                </div>
            </div>

            <!-- Broadcaster Workspace -->
            <div id="broadcaster-container" class="hidden space-y-4">
                <div class="flex items-center justify-between">
                    <h3 class="font-bold text-white text-sm">📹 Canlı Kamera Paylaşımı</h3>
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

                <div class="flex justify-center gap-2 flex-wrap">
                    <button id="btn-cam-toggle" onclick="toggleLocalCam()" class="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        📷 Kamerayı Kapat
                    </button>
                    <button id="btn-mic-toggle" onclick="toggleLocalMic()" class="bg-teal-600 hover:bg-teal-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        🎙️ Mikrofonu Kapat
                    </button>
                    <button id="btn-camera-switch" onclick="switchWebCamera()" class="bg-amber-600 hover:bg-amber-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        🔄 Kamerayı Çevir
                    </button>
                    <button id="btn-screen-dim" onclick="toggleScreenDim(true)" class="bg-indigo-600 hover:bg-indigo-500 text-white font-semibold px-4 py-2 rounded-lg text-xs">
                        🌙 Ekranı Karart
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

    <!-- Screen Dim Overlay for Broadcaster Power Saving (Always-on Display / Lock Screen Style) -->
    <div id="screen-dim-overlay" onclick="toggleScreenDim(false)" class="hidden fixed inset-0 bg-[#000000] z-[10000] flex flex-col items-center justify-between py-16 cursor-pointer select-none">
        <div></div>
        <div class="text-center space-y-2 opacity-30">
            <div id="dim-clock-time" class="text-white text-7xl font-extralight tracking-widest font-sans">00:00</div>
            <div id="dim-clock-date" class="text-gray-300 text-sm font-medium tracking-wide">Yükleniyor...</div>
            <div class="flex items-center justify-center gap-1.5 text-xs text-gray-500 mt-2">
                <span>🔋</span>
                <span>Güç Tasarrufu Aktif</span>
            </div>
        </div>
        <div class="text-center opacity-25 space-y-2">
            <span class="text-2xl block">🔒</span>
            <p class="text-gray-400 text-[10px] tracking-wider uppercase">Ekran Kilitli</p>
            <p class="text-gray-500 text-[9px]">Açmak için ekrana dokunun</p>
        </div>
    </div>

    <!-- Fullscreen Stream Overlay -->
    <div id="fullscreen-overlay" onclick="if (event.target === this) closeFullscreen()" class="hidden fixed inset-0 bg-black/95 z-[9999] flex flex-col items-center justify-center">
        <!-- Interactive Fullscreen Container -->
        <div class="relative w-full h-full flex flex-col items-center justify-center p-4">
            <div id="fullscreen-wrapper" class="w-full h-full flex items-center justify-center">
                <img id="fullscreen-img" class="max-w-full max-h-full object-contain transition-transform duration-75 origin-center" alt="Stream Fullscreen">
            </div>
            
            <!-- Controls Overlay (Top) -->
            <div class="absolute top-4 left-4 right-4 flex items-center justify-between pointer-events-auto bg-black/60 p-3 rounded-xl border border-white/10">
                <span class="text-xs text-white font-bold">📺 Tam Ekran İzleme</span>
                <div class="flex items-center gap-2">
                    <button onclick="toggleVideoWatch()" class="bg-emerald-600 hover:bg-emerald-500 text-white px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1">
                        👁️ Görüntü
                    </button>
                    <button onclick="toggleAudioListen()" class="bg-emerald-600 hover:bg-emerald-500 text-white px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1">
                        🔊 Ses
                    </button>
                    <button onclick="closeFullscreen()" class="bg-red-600 hover:bg-red-500 text-white p-1.5 rounded-lg text-xs">
                        ❌ Küçült
                    </button>
                </div>
            </div>
            
            <!-- Controls Overlay (Bottom Volume) -->
            <div class="absolute bottom-4 bg-black/80 px-4 py-2 rounded-xl border border-white/10 flex items-center gap-2 pointer-events-auto">
                <span class="text-[10px] text-gray-300 font-bold">Ses:</span>
                <input type="range" min="0" max="1" step="0.05" value="0.5" class="w-24 accent-emerald-500" oninput="updateVolume(this.value)">
                <span class="text-[10px] text-gray-400">🔍 Yakınlaştırmak için çift dokunun / sıkıştırın</span>
            </div>
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
        
        // Talkback (Bas-Konuş) variables
        let talkStream = null;
        let talkAudioContext = null;
        let talkProcessor = null;
        let isTalking = false;
        
        // Audio volume controller
        let gainNode = null;
        
        // Server status polling variables
        let statusPollInterval = null;
        let lastBabyCryStatus = false;
        let lastMotionStatus = false;

        // Local media elements
        let localStream = null;
        let localVideo = document.getElementById('local-video');
        let camMuted = false;
        let micMuted = false;
        let lowDataMode = false;
        let uploadInterval = null;
        let audioProcessor = null;
        let isWebScreenDimmed = false;
        let currentFacingMode = "environment";

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
                
                // Hide Mod Değiştir buttons when accessed via QR code link
                document.querySelectorAll('.reset-role-btn').forEach(btn => btn.style.display = 'none');
                
                // Automatically notify server of disconnect when window is closed
                const handleDisconnect = () => {
                    if (passcode) {
                        navigator.sendBeacon('/disconnect?passcode=' + passcode);
                    }
                };
                window.addEventListener('pagehide', handleDisconnect);
                window.addEventListener('beforeunload', handleDisconnect);
                
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
            if (statusPollInterval) {
                clearInterval(statusPollInterval);
                statusPollInterval = null;
            }
            stopTalkBack();
            
            // Hide workspaces and show role selection
            document.getElementById('viewer-container').classList.add('hidden');
            document.getElementById('broadcaster-container').classList.add('hidden');
            document.getElementById('role-selection').classList.remove('hidden');
            document.getElementById('stream-img').classList.add('hidden');
            document.getElementById('stream-placeholder').classList.remove('hidden');
            document.getElementById('btn-audio-listen').textContent = "🔊 Dinle";
            const videoBtn = document.getElementById('btn-video-watch');
            if (videoBtn) {
                videoBtn.textContent = "👁️ İzle";
                videoBtn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5";
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
                videoBtn.textContent = "👁️ İzle";
                videoBtn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5";
            }
            
            // Start polling status for Baby Cry & Motion alerts
            if (statusPollInterval) clearInterval(statusPollInterval);
            statusPollInterval = setInterval(pollServerStatus, 1000);
            
            // Apply pinch-to-zoom to the main and fullscreen images
            setTimeout(() => {
                makeZoomable(document.getElementById('stream-img'));
                makeZoomable(document.getElementById('fullscreen-img'));
            }, 500);
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
                btn.textContent = "👁️ İzle";
                btn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5";
            } else {
                img.src = '';
                img.classList.add('hidden');
                placeholder.classList.remove('hidden');
                btn.textContent = "👁️ Kapalı";
                btn.className = "bg-red-600 hover:bg-red-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5";
            }
        }

        async function toggleAudioListen() {
            const btn = document.getElementById('btn-audio-listen');
            if (isListeningAudio) {
                isListeningAudio = false;
                btn.textContent = "🔊 Sesi Dinle (Kapalı)";
                btn.className = "bg-[#21262d] hover:bg-[#30363d] text-white font-medium px-3 py-2 rounded-lg text-xs flex items-center gap-1.5 border border-gray-700";
                if (audioStreamReader) audioStreamReader.cancel();
            } else {
                isListeningAudio = true;
                btn.textContent = "🔊 Sesi Dinle (Aktif)";
                btn.className = "bg-emerald-600 hover:bg-emerald-500 text-white font-semibold px-3 py-2 rounded-lg text-xs flex items-center gap-1.5";
                playAudioStream();
            }
        }

        async function playAudioStream() {
            audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
            let nextStartTime = audioContext.currentTime;
            gainNode = null; // reset gainNode for new session
            
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
                        
                        // Volume controller
                        if (!gainNode) {
                            gainNode = audioContext.createGain();
                            const volVal = parseFloat(document.getElementById('vol-slider').value);
                            gainNode.gain.setValueAtTime(volVal, audioContext.currentTime);
                            gainNode.connect(audioContext.destination);
                        }
                        source.connect(gainNode);

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
            // Attempt 1: preferred camera with audio
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { exact: currentFacingMode }, width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 15 } },
                    audio: true
                });
            } catch (err) {
                console.warn("Attempt 1 failed (facingMode + audio):", err);
            }

            // Attempt 2: preferred camera with audio (ideal)
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: currentFacingMode }, width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 15 } },
                    audio: true
                });
            } catch (err) {
                console.warn("Attempt 2 failed (facingMode ideal + audio):", err);
            }

            // Attempt 3: any camera with audio
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: true,
                    audio: true
                });
            } catch (err) {
                console.warn("Attempt 3 failed (any camera + audio):", err);
            }

            // Attempt 4: preferred camera ONLY (no audio)
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: currentFacingMode }, width: { ideal: 640 }, height: { ideal: 480 }, frameRate: { ideal: 15 } },
                    audio: false
                });
            } catch (err) {
                console.warn("Attempt 4 failed (facingMode only):", err);
            }

            // Attempt 5: any camera ONLY (no audio)
            try {
                return await navigator.mediaDevices.getUserMedia({
                    video: true,
                    audio: false
                });
            } catch (err) {
                console.warn("Attempt 5 failed (any camera only):", err);
                throw err;
            }
        }

        async function startBroadcasting() {
            if (role !== 'broadcaster') return;
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
            if (role !== 'broadcaster') return;
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

        let lastMotionTriggerTime = 0;
        let previousGrid = null;
        const gridCols = 12;
        const gridRows = 9;

        function startUploadingMedia() {
            if (role !== 'broadcaster') return;
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            let isVideoUploading = false;
            
            previousGrid = null;
            
            // Video frame compressor & uploader loop with concurrency guard
            if (uploadInterval) clearInterval(uploadInterval);
            uploadInterval = setInterval(() => {
                if (!camMuted && localStream && !isVideoUploading) {
                    canvas.width = 480;
                    canvas.height = 360;
                    if (localVideo) {
                        try {
                            ctx.drawImage(localVideo, 0, 0, canvas.width, canvas.height);
                            
                            // Client-side motion detection
                            const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
                            const data = imgData.data;
                            const currentGrid = [];
                            const cellWidth = canvas.width / gridCols;
                            const cellHeight = canvas.height / gridRows;

                            for (let r = 0; r < gridRows; r++) {
                                for (let c = 0; c < gridCols; c++) {
                                    const x = Math.floor(c * cellWidth + cellWidth / 2);
                                    const y = Math.floor(r * cellHeight + cellHeight / 2);
                                    const index = (y * canvas.width + x) * 4;
                                    const gray = 0.299 * data[index] + 0.587 * data[index + 1] + 0.114 * data[index + 2];
                                    currentGrid.push(gray);
                                }
                            }

                            if (previousGrid) {
                                let diffSum = 0;
                                for (let i = 0; i < currentGrid.length; i++) {
                                    diffSum += Math.abs(currentGrid[i] - previousGrid[i]);
                                }
                                const avgDiff = diffSum / currentGrid.length;
                                
                                // High sensitivity threshold of 2.5 for subtle baby movements
                                if (avgDiff > 2.5) {
                                    const now = Date.now();
                                    if (now - lastMotionTriggerTime > 3000) { // 3-second cooldown
                                        lastMotionTriggerTime = now;
                                        fetch('/trigger-motion?passcode=' + passcode)
                                            .then(r => r.json())
                                            .catch(e => console.error("Error triggering web motion:", e));
                                    }
                                }
                            }
                            previousGrid = currentGrid;
                        } catch (err) {
                            console.warn("Motion analysis error:", err);
                        }
                    }
                    canvas.toBlob((blob) => {
                        if (blob) {
                            isVideoUploading = true;
                            fetch('/upload-video?passcode=' + passcode, {
                                method: 'POST',
                                body: blob
                            }).then(() => {
                                isVideoUploading = false;
                            }).catch(() => {
                                isVideoUploading = false;
                            });
                        }
                    }, 'image/jpeg', 0.6);
                }
            }, 35); // Lower interval (35ms) for near real-time (~28fps) stream sync!
            
            // Safe AudioContext checks and connection with concurrency guard
            try {
                if (localStream && localStream.getAudioTracks().length > 0) {
                    if (!audioContext || audioContext.state === 'closed') {
                        audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
                    }
                    
                    const source = audioContext.createMediaStreamSource(localStream);
                    audioProcessor = audioContext.createScriptProcessor(1024, 1, 1);
                    source.connect(audioProcessor);
                    audioProcessor.connect(audioContext.destination);

                    let isAudioUploading = false;
                    audioProcessor.onaudioprocess = (e) => {
                        if (!micMuted && !isAudioUploading) {
                            const inputData = e.inputBuffer.getChannelData(0);
                            const pcm16 = new Int16Array(inputData.length);
                            for (let i = 0; i < inputData.length; i++) {
                                pcm16[i] = Math.max(-1, Math.min(1, inputData[i])) * 0x7FFF;
                            }
                            isAudioUploading = true;
                            fetch('/upload-audio?passcode=' + passcode, {
                                method: 'POST',
                                body: pcm16.buffer
                            }).then(() => {
                                isAudioUploading = false;
                            }).catch(() => {
                                isAudioUploading = false;
                            });
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

        async function switchWebCamera() {
            if (role !== 'broadcaster') return;
            const btn = document.getElementById('btn-camera-switch');
            if (btn) btn.disabled = true;
            
            // Toggle facing mode
            currentFacingMode = currentFacingMode === "environment" ? "user" : "environment";
            
            // Stop existing tracks to free up the hardware
            if (localStream) {
                localStream.getTracks().forEach(track => track.stop());
            }
            if (audioProcessor) {
                audioProcessor.disconnect();
                audioProcessor = null;
            }
            
            try {
                // Get new stream
                localStream = await getStreamWithFallback();
                localVideo.srcObject = localStream;
                try {
                    await localVideo.play();
                } catch (playErr) {
                    console.warn("Camera switch play error:", playErr);
                }
                
                // Reconnect audio processor if we have active audio tracks
                if (audioContext && localStream.getAudioTracks().length > 0) {
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
                }
            } catch (err) {
                console.error("Camera switch failed:", err);
                alert("Kamera değiştirilemedi: " + err.message);
            } finally {
                if (btn) btn.disabled = false;
            }
        }

        let dimClockInterval = null;

        function updateDimClock() {
            const timeEl = document.getElementById('dim-clock-time');
            const dateEl = document.getElementById('dim-clock-date');
            if (!timeEl || !dateEl) return;

            const now = new Date();
            const hours = String(now.getHours()).padStart(2, '0');
            const minutes = String(now.getMinutes()).padStart(2, '0');
            timeEl.textContent = hours + ":" + minutes;

            const options = { weekday: 'long', day: 'numeric', month: 'long' };
            dateEl.textContent = now.toLocaleDateString('tr-TR', options);
        }

        function toggleScreenDim(forceState) {
            isWebScreenDimmed = (forceState !== undefined) ? forceState : !isWebScreenDimmed;
            const overlay = document.getElementById('screen-dim-overlay');
            if (overlay) {
                if (isWebScreenDimmed) {
                    overlay.classList.remove('hidden');
                    updateDimClock();
                    if (!dimClockInterval) {
                        dimClockInterval = setInterval(updateDimClock, 1000);
                    }
                } else {
                    overlay.classList.add('hidden');
                    if (dimClockInterval) {
                        clearInterval(dimClockInterval);
                        dimClockInterval = null;
                    }
                }
            }
        }

        function updateVolume(val) {
            if (gainNode && audioContext) {
                gainNode.gain.setValueAtTime(parseFloat(val), audioContext.currentTime);
            }
        }

        function changeResolution(res) {
            const active = (res === "low");
            fetch('/set-low-data?passcode=' + passcode + '&active=' + active)
                .then(r => r.json())
                .then(data => {
                    console.log("Resolution updated:", data);
                })
                .catch(err => console.error(err));
        }

        async function startTalkBack() {
            if (isTalking) return;
            const btn = document.getElementById('btn-talk-back');
            btn.className = "bg-red-600 hover:bg-red-500 text-white px-3 py-2 rounded-lg transition-all flex items-center justify-center gap-1 select-none text-xs scale-95 duration-75 border-red-500";
            isTalking = true;

            try {
                talkStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
                talkAudioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 });
                const source = talkAudioContext.createMediaStreamSource(talkStream);
                talkProcessor = talkAudioContext.createScriptProcessor(1024, 1, 1);
                
                source.connect(talkProcessor);
                talkProcessor.connect(talkAudioContext.destination);

                talkProcessor.onaudioprocess = (e) => {
                    if (isTalking) {
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
            } catch (err) {
                console.error("Talkback microphone access error:", err);
                stopTalkBack();
                alert("Mikrofon erişimi engellendi veya desteklenmiyor!");
            }
        }

        function stopTalkBack() {
            if (!isTalking) return;
            isTalking = false;
            const btn = document.getElementById('btn-talk-back');
            if (btn) {
                btn.className = "bg-[#21262d] hover:bg-gray-800 text-gray-400 px-3 py-2 rounded-lg border border-gray-700 transition-all flex items-center justify-center gap-1 select-none text-xs";
            }
            if (talkProcessor) {
                talkProcessor.disconnect();
                talkProcessor = null;
            }
            if (talkStream) {
                talkStream.getTracks().forEach(track => track.stop());
                talkStream = null;
            }
            if (talkAudioContext) {
                talkAudioContext.close();
                talkAudioContext = null;
            }
        }

        let currentWebTorchState = false;
        async function setWebTorch(enabled) {
            if (!localStream) return;
            const videoTracks = localStream.getVideoTracks();
            if (videoTracks.length === 0) return;
            const track = videoTracks[0];
            try {
                // Directly apply torch constraint (best-effort)
                await track.applyConstraints({
                    advanced: [{ torch: enabled }]
                });
                currentWebTorchState = enabled;
                console.log("Web torch constraint applied successfully:", enabled);
            } catch (e) {
                console.warn("Failed to set web torch constraint:", e);
            }
        }

        function pollServerStatus() {
            if (!passcode) return;
            fetch('/status?passcode=' + passcode)
                .then(r => r.json())
                .then(data => {
                    // Control remote torch state if we are the broadcaster
                    if (role === 'broadcaster' && data.torchActive !== undefined) {
                        setWebTorch(data.torchActive);
                    }

                    // Check Baby Cry Status
                    if (data.babyCryActive && !lastBabyCryStatus) {
                        showOnScreenAlert("🍼 BEBEK AĞLAMASI ALGILANDI!", "danger");
                    }
                    lastBabyCryStatus = data.babyCryActive;

                    // Check Motion Status
                    if (data.motionActive && !lastMotionStatus) {
                        showOnScreenAlert("🏃 HAREKET ALGILANDI!", "warning");
                    }
                    lastMotionStatus = data.motionActive;

                    // Check Android device active mode
                    const statusText = document.getElementById('android-device-status');
                    if (statusText && data.androidMode) {
                        if (data.androidMode === 'broadcaster') {
                            statusText.textContent = "📱 Android Cihaz: Yayın Yapıyor (İzleyici Modu Önerilir)";
                            statusText.className = "text-xs text-emerald-400 text-center animate-pulse mt-2 font-semibold";
                        } else {
                            statusText.textContent = "📱 Android Cihaz: Alıcı Modunda (Yayıncı Modu Önerilir)";
                            statusText.className = "text-xs text-teal-400 text-center animate-pulse mt-2 font-semibold";
                        }
                    }

                    // Check cameraMuted change
                    let currentCameraMuted = data.cameraMuted;
                    if (currentCameraMuted !== undefined) {
                        if (window.lastCameraMuted === undefined) {
                            window.lastCameraMuted = currentCameraMuted;
                        } else if (window.lastCameraMuted !== currentCameraMuted) {
                            window.lastCameraMuted = currentCameraMuted;
                            // If it transitioned from muted to unmuted, reload the video image
                            if (!currentCameraMuted && role === 'viewer' && isWatchingVideo) {
                                const img = document.getElementById('stream-img');
                                if (img) {
                                    img.src = '/video?passcode=' + passcode + '&t=' + Date.now();
                                }
                            }
                        }
                    }
                })
                .catch(err => console.error("Poll status error", err));
        }

        function showOnScreenAlert(msg, type) {
            const container = document.getElementById('alert-toast-container');
            if (!container) return;
            const alertDiv = document.createElement('div');
            const bgClass = type === 'danger' ? 'bg-red-600' : 'bg-amber-600';
            alertDiv.className = `${'$'}{bgClass} text-white px-4 py-3 rounded-xl shadow-2xl font-bold text-center border border-white/20 animate-bounce flex items-center justify-center gap-2`;
            alertDiv.innerHTML = `<span>⚠️</span> <span>${'$'}{msg}</span>`;
            container.appendChild(alertDiv);
            setTimeout(() => {
                alertDiv.remove();
            }, 5000); // Remove after 5s
        }

        function playBeep() {
            try {
                const beepContext = new (window.AudioContext || window.webkitAudioContext)();
                const osc = beepContext.createOscillator();
                const gain = beepContext.createGain();
                osc.type = 'sine';
                osc.frequency.setValueAtTime(880, beepContext.currentTime); // high pitched beep
                gain.gain.setValueAtTime(0.5, beepContext.currentTime);
                osc.connect(gain);
                gain.connect(beepContext.destination);
                osc.start();
                
                setTimeout(() => {
                    osc.stop();
                    beepContext.close();
                }, 2000); // 2 second beep
            } catch (err) {
                console.error("Audio beep synthesis failed:", err);
            }
        }

        function makeZoomable(img) {
            if (!img) return;
            let scale = 1;
            let startDist = 0;
            let posX = 0, posY = 0;
            let startX = 0, startY = 0;
            let isDragging = false;

            img.addEventListener('touchstart', (e) => {
                if (e.touches.length === 2) {
                    startDist = Math.hypot(
                        e.touches[0].clientX - e.touches[1].clientX,
                        e.touches[0].clientY - e.touches[1].clientY
                    );
                } else if (e.touches.length === 1) {
                    isDragging = true;
                    startX = e.touches[0].clientX - posX;
                    startY = e.touches[0].clientY - posY;
                }
            });

            img.addEventListener('touchmove', (e) => {
                if (e.touches.length === 2) {
                    const dist = Math.hypot(
                        e.touches[0].clientX - e.touches[1].clientX,
                        e.touches[0].clientY - e.touches[1].clientY
                    );
                    scale = Math.max(1, Math.min(5, (dist / startDist) * scale));
                    img.style.transform = `scale(${'$'}{scale}) translate(${'$'}{posX / scale}px, ${'$'}{posY / scale}px)`;
                    img.style.zIndex = "99";
                    e.preventDefault();
                } else if (e.touches.length === 1 && isDragging && scale > 1) {
                    posX = e.touches[0].clientX - startX;
                    posY = e.touches[0].clientY - startY;
                    img.style.transform = `scale(${'$'}{scale}) translate(${'$'}{posX / scale}px, ${'$'}{posY / scale}px)`;
                    e.preventDefault();
                }
            });

            img.addEventListener('touchend', (e) => {
                isDragging = false;
                if (e.touches.length < 2) startDist = 0;
                if (scale === 1) {
                    img.style.transform = "none";
                    img.style.zIndex = "auto";
                    posX = 0; posY = 0;
                }
            });
            
            // Allow double click to reset
            img.addEventListener('dblclick', () => {
                scale = 1;
                posX = 0; posY = 0;
                img.style.transform = "none";
                img.style.zIndex = "auto";
            });
        }

        function openFullscreen() {
            if (!isWatchingVideo) return;
            const overlay = document.getElementById('fullscreen-overlay');
            const fullImg = document.getElementById('fullscreen-img');
            fullImg.src = '/video?passcode=' + passcode;
            overlay.classList.remove('hidden');
            
            // Apply zoom to fullscreen img too
            setTimeout(() => {
                makeZoomable(fullImg);
            }, 100);
        }

        function closeFullscreen() {
            const overlay = document.getElementById('fullscreen-overlay');
            const fullImg = document.getElementById('fullscreen-img');
            fullImg.src = '';
            overlay.classList.add('hidden');
        }

        // Global tap to start broadcasting if the start overlay is active
        document.addEventListener('click', function globalClickToStart(e) {
            if (role !== 'broadcaster') return;
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
