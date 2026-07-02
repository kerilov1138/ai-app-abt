package com.example.stream

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class CameraCapturer(private val context: Context) {
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isFrontCamera = false
    private var isVirtualRunning = false
    private var virtualThread: Thread? = null

    interface FrameCallback {
        fun onFrame(jpegBytes: ByteArray)
    }

    private fun isEmulator(): Boolean {
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val hardware = android.os.Build.HARDWARE
        val fingerprint = android.os.Build.FINGERPRINT
        return (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk" == android.os.Build.PRODUCT
                || hardware.contains("goldfish")
                || hardware.contains("ranchu"))
    }

    private fun startVirtualCamera(lowDataMode: Boolean, callback: FrameCallback) {
        if (isVirtualRunning) return
        isVirtualRunning = true
        virtualThread = Thread {
            val width = if (lowDataMode) 320 else 640
            val height = if (lowDataMode) 240 else 480
            val quality = if (lowDataMode) 40 else 75
            
            val paint = Paint()
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = (width / 20).toFloat()
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            val greenPaint = Paint().apply {
                color = 0xFFD0BCFF.toInt() // Theme Lavender (D0BCFF)
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            val accentPaint = Paint().apply {
                color = 0xFF06B6D4.toInt() // Theme Cyan (06B6D4)
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            var angle = 0f

            while (isVirtualRunning) {
                try {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    
                    // Draw dark professional slate background
                    canvas.drawColor(0xFF1C1B1F.toInt())
                    
                    // Draw grids
                    paint.color = 0xFF2B2930.toInt()
                    paint.strokeWidth = 2f
                    val gridSize = width / 10
                    for (i in 0..width step gridSize) {
                        canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), paint)
                    }
                    for (i in 0..height step gridSize) {
                        canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
                    }

                    // Draw pulsing/rotating radar circle in center
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val radius = Math.min(width, height) / 3f
                    canvas.drawCircle(centerX, centerY, radius, greenPaint)
                    
                    // Draw radar sweep line
                    val endX = (centerX + radius * Math.cos(Math.toRadians(angle.toDouble()))).toFloat()
                    val endY = (centerY + radius * Math.sin(Math.toRadians(angle.toDouble()))).toFloat()
                    canvas.drawLine(centerX, centerY, endX, endY, greenPaint)

                    // Draw orbiting satellite
                    val satX = (centerX + radius * 0.7f * Math.cos(Math.toRadians((angle * 1.5).toDouble()))).toFloat()
                    val satY = (centerY + radius * 0.7f * Math.sin(Math.toRadians((angle * 1.5).toDouble()))).toFloat()
                    canvas.drawCircle(satX, satY, 12f, accentPaint)

                    // Text overlay
                    textPaint.color = 0xFFD0BCFF.toInt()
                    textPaint.textSize = (width / 20).toFloat()
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("P2P GÖZCÜ - SANAL KAMERA", centerX, centerY - radius - 20, textPaint)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = (width / 26).toFloat()
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    val timeStr = sdf.format(java.util.Date())
                    canvas.drawText(timeStr, centerX, centerY + radius + 35, textPaint)

                    textPaint.color = Color.GRAY
                    textPaint.textSize = (width / 30).toFloat()
                    canvas.drawText("Sanal Test Yayını (Gözcü Fallback) - FPS: 10", centerX, centerY + radius + 65, textPaint)

                    // Compress to JPEG
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    callback.onFrame(out.toByteArray())

                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e("CameraCapturer", "Error in virtual camera loop", e)
                }

                angle = (angle + 6f) % 360f

                try {
                    Thread.sleep(100) // 10 FPS
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply {
            start()
        }
    }

    private fun stopVirtualCamera() {
        isVirtualRunning = false
        virtualThread?.interrupt()
        virtualThread = null
    }

    fun isVirtualCameraActive(): Boolean {
        return isVirtualRunning
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        lowDataMode: Boolean,
        callback: FrameCallback
    ) {
        start(lifecycleOwner, lowDataMode, null, callback)
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        lowDataMode: Boolean,
        previewView: androidx.camera.view.PreviewView?,
        callback: FrameCallback
    ) {
        stop() // Clean up any active sessions first

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("CameraCapturer", "Kamera izni henüz verilmedi. Başlatılamıyor. Sanal kameraya geçiliyor.")
            startVirtualCamera(lowDataMode, callback)
            return
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    provider.unbindAll()

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    // Check if device actually has any cameras, otherwise fall back to virtual camera
                    if (!provider.hasCamera(cameraSelector)) {
                        val fallbackSelector = if (isFrontCamera) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        if (provider.hasCamera(fallbackSelector)) {
                            isFrontCamera = !isFrontCamera
                            bindCamera(provider, lifecycleOwner, fallbackSelector, lowDataMode, previewView, callback)
                        } else {
                            throw Exception("Cihazda fiziksel kamera bulunamadı.")
                        }
                    } else {
                        bindCamera(provider, lifecycleOwner, cameraSelector, lowDataMode, previewView, callback)
                    }
                } catch (e: Exception) {
                    Log.e("CameraCapturer", "Failed to bind camera use cases, switching to virtual camera", e)
                    startVirtualCamera(lowDataMode, callback)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e("CameraCapturer", "ProcessCameraProvider.getInstance threw exception, switching to virtual camera", e)
            startVirtualCamera(lowDataMode, callback)
        }
    }

    private fun bindCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        lowDataMode: Boolean,
        previewView: androidx.camera.view.PreviewView?,
        callback: FrameCallback
    ) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val quality = if (lowDataMode) 30 else 60

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val jpeg = imageProxyToJpeg(imageProxy, quality)
                if (jpeg != null) {
                    callback.onFrame(jpeg)
                }
            } catch (e: Exception) {
                Log.e("CameraCapturer", "Error analyzing frame", e)
            } finally {
                imageProxy.close()
            }
        }

        if (previewView != null) {
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } else {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        }
    }

    fun stop() {
        stopVirtualCamera()
        try {
            val provider = cameraProvider
            if (provider != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        provider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraCapturer", "Error unbinding camera use cases on main thread", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraCapturer", "Error stopping camera", e)
        }
    }

    fun toggleCameraFace() {
        isFrontCamera = !isFrontCamera
    }

    fun toggleCamera(lifecycleOwner: LifecycleOwner, lowDataMode: Boolean, callback: FrameCallback) {
        toggleCameraFace()
        stop()
        start(lifecycleOwner, lowDataMode, callback)
    }

    private fun imageProxyToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) {
            return null
        }

        val width = image.width
        val height = image.height
        val rotationDegrees = image.imageInfo.rotationDegrees

        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(
            toNV21(image),
            ImageFormat.NV21,
            width,
            height,
            null
        )
        yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
        val jpegBytes = out.toByteArray()

        // Apply rotation and mirroring to make sure the streamed video is upright and correctly mirrored
        if (rotationDegrees != 0 || isFrontCamera) {
            try {
                val originalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (originalBitmap != null) {
                    val matrix = Matrix().apply {
                        if (rotationDegrees != 0) {
                            postRotate(rotationDegrees.toFloat())
                        }
                        if (isFrontCamera) {
                            postScale(-1f, 1f) // Mirror horizontally for front camera
                        }
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    val rotatedOut = ByteArrayOutputStream()
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, rotatedOut)

                    originalBitmap.recycle()
                    if (rotatedBitmap != originalBitmap) {
                        rotatedBitmap.recycle()
                    }
                    return rotatedOut.toByteArray()
                }
            } catch (e: Exception) {
                Log.e("CameraCapturer", "Error rotating image proxy bitmap", e)
            }
        }

        return jpegBytes
    }

    private fun toNV21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = ySize / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        var yRowStride = image.planes[0].rowStride
        var uRowStride = image.planes[1].rowStride
        var vRowStride = image.planes[2].rowStride

        var yPixelStride = image.planes[0].pixelStride
        var uPixelStride = image.planes[1].pixelStride
        var vPixelStride = image.planes[2].pixelStride

        var pos = 0
        if (yRowStride == width && yPixelStride == 1) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        // Interleave U and V for NV21
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = ySize + row * width + col * 2
                val uPos = row * uRowStride + col * uPixelStride
                val vPos = row * vRowStride + col * vPixelStride

                nv21[vuPos] = vBuffer.get(vPos) // V first
                nv21[vuPos + 1] = uBuffer.get(uPos) // U second
            }
        }

        return nv21
    }
}
