package com.example.stream

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class AudioCapturer(private val context: android.content.Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    // Thread-safe set of callbacks to stream audio to multiple connected clients simultaneously
    private val listeners = Collections.newSetFromMap(ConcurrentHashMap<AudioListener, Boolean>())

    interface AudioListener {
        fun onAudioChunk(bytes: ByteArray)
    }

    fun registerListener(listener: AudioListener) {
        listeners.add(listener)
        startRecording()
    }

    fun unregisterListener(listener: AudioListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stopRecording()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun startRecording() {
        if (isRecording) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("AudioCapturer", "Ses kayıt izni henüz verilmedi. Başlatılamıyor.")
            return
        }
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioCapturer", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            thread(name = "CamLinkAudioRecordThread") {
                val tempBuffer = ByteArray(1024)
                while (isRecording) {
                    val readBytes = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: -1
                    if (readBytes > 0) {
                        val chunk = tempBuffer.copyOf(readBytes)
                        listeners.forEach { listener ->
                            try {
                                listener.onAudioChunk(chunk)
                            } catch (e: Exception) {
                                Log.e("AudioCapturer", "Error sending audio to listener", e)
                            }
                        }
                    }
                }
            }
            Log.i("AudioCapturer", "Audio recording started successfully")
        } catch (e: Exception) {
            Log.e("AudioCapturer", "Error starting audio recording", e)
        }
    }

    @Synchronized
    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i("AudioCapturer", "Audio recording stopped")
        } catch (e: Exception) {
            Log.e("AudioCapturer", "Error stopping audio recording", e)
        }
    }

    fun release() {
        listeners.clear()
        stopRecording()
    }
}
