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
    fun isFrontCameraActive(): Boolean = isFrontCamera
    private var isVirtualRunning = false
    private var virtualThread: Thread? = null
    private var isCaptureRunning = false
    private var activeCamera: androidx.camera.core.Camera? = null
    private var isTorchEnabled = false

    private var previousGrid: ByteArray? = null
    private val gridWidth = 32
    private val gridHeight = 24

    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var lastPhoneMoveTime = 0L

    private val sensorListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (event == null) return
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            if (lastAccelX != 0f || lastAccelY != 0f || lastAccelZ != 0f) {
                val dx = x - lastAccelX
                val dy = y - lastAccelY
                val dz = z - lastAccelZ
                val delta = Math.sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()
                
                // If acceleration change is above 0.35 m/s², the phone is physically moving!
                if (delta > 0.35f) {
                    lastPhoneMoveTime = System.currentTimeMillis()
                }
            }
            lastAccelX = x
            lastAccelY = y
            lastAccelZ = z
        }
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun isPhoneMovingRecently(): Boolean {
        // If the phone was physically moved within the last 2 seconds (2000 ms), return true
        return (System.currentTimeMillis() - lastPhoneMoveTime) < 2000
    }

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
        isCaptureRunning = true

        // Register accelerometer to detect physical movement
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
            accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(sensorListener, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Exception) {
            Log.e("CameraCapturer", "Error registering accelerometer: ", e)
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w("CameraCapturer", "Kamera izni henüz verilmedi. Başlatılamıyor. Sanal kameraya geçiliyor.")
            startVirtualCamera(lowDataMode, callback)
            return
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                if (!isCaptureRunning) {
                    try {
                        val provider = cameraProviderFuture.get()
                        provider.unbindAll()
                    } catch (e: Exception) {}
                    return@addListener
                }
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
        val targetSize = if (lowDataMode) android.util.Size(320, 240) else android.util.Size(640, 480)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(targetSize)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val quality = if (lowDataMode) 30 else 60

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                // Perform real-time motion detection on downsampled grid
                val planes = imageProxy.planes
                if (planes.isNotEmpty()) {
                    val yBuffer = planes[0].buffer
                    val yWidth = imageProxy.width
                    val yHeight = imageProxy.height
                    val yRowStride = planes[0].rowStride
                    
                    val currentGrid = ByteArray(gridWidth * gridHeight)
                    val cellWidth = yWidth / gridWidth
                    val cellHeight = yHeight / gridHeight
                    
                    if (cellWidth > 0 && cellHeight > 0) {
                        for (gy in 0 until gridHeight) {
                            for (gx in 0 until gridWidth) {
                                val py = gy * cellHeight + cellHeight / 2
                                val px = gx * cellWidth + cellWidth / 2
                                val index = py * yRowStride + px
                                if (index < yBuffer.capacity()) {
                                    currentGrid[gy * gridWidth + gx] = yBuffer.get(index)
                                }
                            }
                        }
                        
                        val prev = previousGrid
                        if (prev != null) {
                            var diffSum = 0
                            for (i in currentGrid.indices) {
                                val prevVal = prev[i].toInt() and 0xFF
                                val currVal = currentGrid[i].toInt() and 0xFF
                                diffSum += Math.abs(currVal - prevVal)
                            }
                            val avgDiff = diffSum.toFloat() / currentGrid.size
                            
                            // A difference threshold of 2.5 units is highly sensitive for subtle baby movements.
                            if (avgDiff > 2.5f) {
                                // Exclude the camera's own movement!
                                if (!isPhoneMovingRecently()) {
                                    StreamingService.activeServer?.triggerMotion()
                                }
                            }
                        }
                        previousGrid = currentGrid
                    }
                }

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
            activeCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } else {
            activeCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        isTorchEnabled = enabled
        try {
            activeCamera?.cameraControl?.enableTorch(enabled)
        } catch (e: Exception) {
            Log.e("CameraCapturer", "Error setting torch to $enabled: ", e)
        }
    }

    fun isTorchOn(): Boolean {
        return isTorchEnabled
    }

    fun stop() {
        isCaptureRunning = false
        activeCamera = null
        stopVirtualCamera()

        // Unregister accelerometer
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (e: Exception) {}
        sensorManager = null
        accelerometer = null
        previousGrid = null

        try {
            val provider = cameraProvider
            if (provider != null) {
                val stopAction = Runnable {
                    try {
                        provider.unbindAll()
                    } catch (e: Exception) {
                        Log.e("CameraCapturer", "Error unbinding camera use cases", e)
                    }
                }
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    stopAction.run()
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post(stopAction)
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

        val nv21Bytes = toNV21(image)
        val rotatedBytes = rotateNV21(nv21Bytes, width, height, rotationDegrees, isFrontCamera)

        val is90or270 = rotationDegrees == 90 || rotationDegrees == 270
        val newWidth = if (is90or270) height else width
        val newHeight = if (is90or270) width else height

        val out = ByteArrayOutputStream()
        val yuvImage = YuvImage(
            rotatedBytes,
            ImageFormat.NV21,
            newWidth,
            newHeight,
            null
        )
        yuvImage.compressToJpeg(Rect(0, 0, newWidth, newHeight), quality, out)
        return out.toByteArray()
    }

    private fun rotateNV21(data: ByteArray, width: Int, height: Int, rotation: Int, mirror: Boolean): ByteArray {
        val normalizedRotation = when (rotation) {
            90, 180, 270 -> rotation
            else -> 0
        }
        if (normalizedRotation == 0 && !mirror) return data

        val rotated = ByteArray(data.size)
        val ySize = width * height
        
        val is90or270 = normalizedRotation == 90 || normalizedRotation == 270
        val newWidth = if (is90or270) height else width
        val newHeight = if (is90or270) width else height

        when (normalizedRotation) {
            90 -> {
                // 90 degrees clockwise with horizontal mirroring support
                for (y in 0 until height) {
                    val yStride = y * width
                    for (x in 0 until width) {
                        val srcIdx = yStride + x
                        val destX = if (mirror) y else height - 1 - y
                        val destY = x
                        rotated[destY * newWidth + destX] = data[srcIdx]
                    }
                }
                
                // 90 degrees UV with horizontal mirroring support
                val halfWidth = width / 2
                val halfHeight = height / 2
                for (y in 0 until halfHeight) {
                    val yStride = ySize + y * width
                    for (x in 0 until halfWidth) {
                        val srcIdx = yStride + x * 2
                        val destX = if (mirror) y else halfHeight - 1 - y
                        val destY = x
                        val destIdx = ySize + destY * newWidth + destX * 2
                        rotated[destIdx] = data[srcIdx]
                        rotated[destIdx + 1] = data[srcIdx + 1]
                    }
                }
            }
            180 -> {
                // 180 degrees with horizontal mirroring support
                for (y in 0 until height) {
                    val yStride = y * width
                    val destY = height - 1 - y
                    val destYStride = destY * newWidth
                    for (x in 0 until width) {
                        val srcIdx = yStride + x
                        val destX = if (mirror) x else width - 1 - x
                        rotated[destYStride + destX] = data[srcIdx]
                    }
                }
                
                // 180 degrees UV with horizontal mirroring support
                val halfWidth = width / 2
                val halfHeight = height / 2
                for (y in 0 until halfHeight) {
                    val yStride = ySize + y * width
                    val destY = halfHeight - 1 - y
                    val destYStride = ySize + destY * newWidth
                    for (x in 0 until halfWidth) {
                        val srcIdx = yStride + x * 2
                        val destX = if (mirror) x else halfWidth - 1 - x
                        val destIdx = destYStride + destX * 2
                        rotated[destIdx] = data[srcIdx]
                        rotated[destIdx + 1] = data[srcIdx + 1]
                    }
                }
            }
            270 -> {
                // 270 degrees with horizontal mirroring support
                for (y in 0 until height) {
                    val yStride = y * width
                    for (x in 0 until width) {
                        val srcIdx = yStride + x
                        val destX = if (mirror) height - 1 - y else y
                        val destY = width - 1 - x
                        rotated[destY * newWidth + destX] = data[srcIdx]
                    }
                }
                
                // 270 degrees UV with horizontal mirroring support
                val halfWidth = width / 2
                val halfHeight = height / 2
                for (y in 0 until halfHeight) {
                    val yStride = ySize + y * width
                    for (x in 0 until halfWidth) {
                        val srcIdx = yStride + x * 2
                        val destX = if (mirror) halfHeight - 1 - y else y
                        val destY = halfWidth - 1 - x
                        val destIdx = ySize + destY * newWidth + destX * 2
                        rotated[destIdx] = data[srcIdx]
                        rotated[destIdx + 1] = data[srcIdx + 1]
                    }
                }
            }
            else -> {
                // 0 degrees with mirror
                for (y in 0 until height) {
                    val yStride = y * width
                    for (x in 0 until width) {
                        val srcIdx = yStride + x
                        val destX = width - 1 - x
                        rotated[y * newWidth + destX] = data[srcIdx]
                    }
                }
                
                // 0 degrees UV with mirror
                val halfWidth = width / 2
                val halfHeight = height / 2
                for (y in 0 until halfHeight) {
                    val yStride = ySize + y * width
                    for (x in 0 until halfWidth) {
                        val srcIdx = yStride + x * 2
                        val destX = halfWidth - 1 - x
                        val destIdx = ySize + y * newWidth + destX * 2
                        rotated[destIdx] = data[srcIdx]
                        rotated[destIdx + 1] = data[srcIdx + 1]
                    }
                }
            }
        }
        
        return rotated
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
