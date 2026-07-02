package com.example.stream

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class StreamingService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): StreamingService = this@StreamingService
    }

    private val binder = LocalBinder()
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    companion object {
        var isServiceRunning = false
            private set

        var activeServer: LocalStreamServer? = null
            set
            get

        var activeCameraCapturer: CameraCapturer? = null
            set
            get

        var activeAudioCapturer: AudioCapturer? = null
            set
            get

        var activeAudioPlayer: AudioPlayer? = null
            set
            get

        var activeNotificationManager: CamLinkNotificationManager? = null
            set
            get

        val serviceLifecycleOwner = SimpleLifecycleOwner()

        fun initializeDependencies(context: Context) {
            val appCtx = context.applicationContext
            if (activeNotificationManager == null) {
                activeNotificationManager = CamLinkNotificationManager(appCtx)
            }
            if (activeAudioCapturer == null) {
                activeAudioCapturer = AudioCapturer(appCtx)
            }
            if (activeAudioPlayer == null) {
                activeAudioPlayer = AudioPlayer()
            }
            if (activeCameraCapturer == null) {
                activeCameraCapturer = CameraCapturer(appCtx)
            }
            if (activeServer == null) {
                val server = LocalStreamServer(
                    context = appCtx,
                    notificationManager = activeNotificationManager!!,
                    audioCapturer = activeAudioCapturer!!,
                    audioPlayer = activeAudioPlayer!!,
                    port = 8080
                )
                server.start()
                activeServer = server
            }
        }

        fun startService(context: Context) {
            if (isServiceRunning) return
            val intent = Intent(context, StreamingService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, StreamingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        serviceLifecycleOwner.resume()

        // 1. Start foreground service immediately to ensure we are running as a foreground process for any subsequent background restriction checks
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            startForeground(
                1999,
                createForegroundNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(1999, createForegroundNotification())
        }

        // 2. Acquire partial wake lock to keep CPU running when screen is off/locked after a small delay.
        // This ensures the service has fully transitioned to foreground in AppOps before acquiring.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isServiceRunning) {
                try {
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "CamLink::StreamingWakeLock").apply {
                        acquire()
                    }
                    android.util.Log.i("StreamingService", "WakeLock acquired successfully after delay.")
                } catch (e: Exception) {
                    android.util.Log.e("StreamingService", "Failed to acquire WakeLock", e)
                }

                // 3. Acquire WifiLock to keep Wi-Fi connection high-performance during screen lock
                try {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    wifiLock = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CamLink::WifiLock")
                    } else {
                        @Suppress("DEPRECATION")
                        wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "CamLink::WifiLock")
                    }
                    wifiLock?.acquire()
                    android.util.Log.i("StreamingService", "WifiLock acquired successfully after delay.")
                } catch (e: Exception) {
                    android.util.Log.e("StreamingService", "Failed to acquire WifiLock", e)
                }
            }
        }, 500)

        val context = applicationContext
        initializeDependencies(context)

        // Ensure the server starts again if it was stopped
        activeServer?.start()
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "camlink_background_service"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val systemNM = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                channelId,
                "CamLink Arka Plan Çalışması",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Uygulamanın arka planda kamera ve ses aktarmaya devam etmesini sağlar."
            }
            systemNM.createNotificationChannel(channel)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.example.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("CamLink Gözcü Arka Planda Aktif")
            .setContentText("Kamera ve Ses yayını arka planda çalışıyor.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceLifecycleOwner.stop()

        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    android.util.Log.i("StreamingService", "WakeLock released successfully.")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StreamingService", "Failed to release WakeLock", e)
        }

        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    android.util.Log.i("StreamingService", "WifiLock released successfully.")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StreamingService", "Failed to release WifiLock", e)
        }

        activeServer?.stop()
        activeCameraCapturer?.stop()
        activeAudioCapturer?.release()
        activeAudioPlayer?.release()
    }
}

class SimpleLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
    }

    fun resume() {
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED || lifecycleRegistry.currentState == Lifecycle.State.CREATED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    fun stop() {
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
}
