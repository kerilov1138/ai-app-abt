package com.example.geminiaiapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class NotificationType {
    INFO, SUCCESS, WARNING, DANGER
}

data class NotificationLog(
    val id: String = UUID.randomUUID().toString(),
    val time: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val title: String,
    val message: String,
    val type: NotificationType
)

data class NotificationSettings(
    val notifyOnConnect: Boolean = true,
    val notifyOnDisconnect: Boolean = true,
    val notifyOnAuthFailure: Boolean = true,
    val notifyOnStreamStateChange: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val notifyBabyCry: Boolean = true,
    val notifyMotion: Boolean = true,
    val beepOnBabyCry: Boolean = true,
    val babyCryThresholdDb: Float = 65f
)

class CamLinkNotificationManager(private val context: Context) {
    private val _settings = MutableStateFlow(NotificationSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    private val _logs = MutableStateFlow<List<NotificationLog>>(emptyList())
    val logs: StateFlow<List<NotificationLog>> = _logs.asStateFlow()

    private val channelId = "camlink_notifications"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CamLink Güvenlik ve Bağlantı Bildirimleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Bağlantı durumları ve yetkilendirme uyarılarını bildirir."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateSettings(newSettings: NotificationSettings) {
        _settings.value = newSettings
        addSystemLog("Ayarlar Güncellendi", "Bildirim yönetim tercihleri kaydedildi.", NotificationType.INFO)
    }

    fun addLog(title: String, message: String, type: NotificationType) {
        val currentSettings = _settings.value
        val isMotion = title.contains("Hareket", ignoreCase = true)

        if (isMotion && currentSettings.notifyMotion) {
            val newLog = NotificationLog(title = title, message = message, type = type)
            _logs.value = listOf(newLog) + _logs.value.take(49) // Keep last 50 logs
            
            // Trigger a system-wide status bar notification!
            showSystemNotification(title, message, type)
        }
    }

    private fun addSystemLog(title: String, message: String, type: NotificationType) {
        val newLog = NotificationLog(title = title, message = message, type = type)
        _logs.value = listOf(newLog) + _logs.value.take(49)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun showSystemNotification(title: String, message: String, type: NotificationType) {
        try {
            val iconRes = when (type) {
                NotificationType.SUCCESS -> android.R.drawable.presence_online
                NotificationType.WARNING -> android.R.drawable.presence_away
                NotificationType.DANGER -> android.R.drawable.presence_busy
                NotificationType.INFO -> android.R.drawable.ic_dialog_info
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val isDetection = title.contains("Bebek", ignoreCase = true) || title.contains("Hareket", ignoreCase = true)
            if (_settings.value.soundEnabled && !isDetection) {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
            }
            if (_settings.value.vibrateEnabled && !isDetection) {
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            }

            notificationManager.notify(UUID.randomUUID().hashCode(), builder.build())
        } catch (e: Exception) {
            // Might lack runtime permission POST_NOTIFICATIONS on Android 13+, but fail gracefully
        }
    }
}
