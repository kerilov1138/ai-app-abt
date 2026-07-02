package com.example.stream

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioPlayer {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val bufferSize = AudioTrack.getMinBufferSize(
        16000,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ) * 2

    @Synchronized
    fun start() {
        if (isPlaying) return
        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("AudioPlayer", "AudioTrack initialization failed")
                return
            }

            audioTrack?.play()
            isPlaying = true
            Log.i("AudioPlayer", "AudioPlayer started successfully")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error starting AudioTrack", e)
        }
    }

    @Synchronized
    fun write(bytes: ByteArray) {
        if (!isPlaying || audioTrack == null) {
            start()
        }
        try {
            audioTrack?.write(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error writing raw audio to track", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            Log.i("AudioPlayer", "AudioPlayer stopped")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping AudioPlayer", e)
        }
    }

    fun release() {
        stop()
    }
}
