package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import com.example.stream.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : ComponentActivity() {

    private lateinit var notificationManager: CamLinkNotificationManager
    private lateinit var audioCapturer: AudioCapturer
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var cameraCapturer: CameraCapturer
    private var localStreamServer: LocalStreamServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize core background stream dependencies in the service statically without starting it yet
        com.example.stream.StreamingService.initializeDependencies(applicationContext)

        notificationManager = com.example.stream.StreamingService.activeNotificationManager!!
        audioCapturer = com.example.stream.StreamingService.activeAudioCapturer!!
        audioPlayer = com.example.stream.StreamingService.activeAudioPlayer!!
        cameraCapturer = com.example.stream.StreamingService.activeCameraCapturer!!
        localStreamServer = com.example.stream.StreamingService.activeServer!!

        setContent {
            // Professional Polish Theme
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),      // Lavender
                    onPrimary = Color(0xFF381E72),    // Dark Purple
                    secondary = Color(0xFFE8DEF8),    // Soft Lavender
                    onSecondary = Color(0xFF1D192B),  // Dark text on soft lavender
                    tertiary = Color(0xFF06B6D4),     // Cyan details
                    background = Color(0xFF1C1B1F),   // Deep Plum-Slate Dark Background
                    surface = Color(0xFF2B2930),      // Plum-Slate Dark Surfaces
                    onBackground = Color(0xFFE6E1E5), // Light gray-purple text
                    onSurface = Color(0xFFE6E1E5)     // Light gray-purple text
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CamLinkApp(
                        server = localStreamServer!!,
                        cameraCapturer = cameraCapturer,
                        notificationManager = notificationManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!com.example.stream.StreamingService.isServiceRunning) {
            localStreamServer?.stop()
            cameraCapturer.stop()
            audioCapturer.release()
            audioPlayer.release()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamLinkApp(
    server: LocalStreamServer,
    cameraCapturer: CameraCapturer,
    notificationManager: CamLinkNotificationManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe logs and notification settings
    val logs by notificationManager.logs.collectAsStateWithLifecycle()
    val notificationSettings by notificationManager.settings.collectAsStateWithLifecycle()

    // App Mode (null for Selection/Onboarding, "broadcaster", "viewer")
    var selectedAppMode by rememberSaveable { mutableStateOf<String?>(null) }
    var showBroadcasterConsentDialog by rememberSaveable { mutableStateOf(false) }

    // App Navigation tabs (Yayın & Bağlantı, İzleyici Paneli, Bildirimler)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Permission state
    val requiredPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results[Manifest.permission.CAMERA] == true &&
                results[Manifest.permission.RECORD_AUDIO] == true
        if (permissionsGranted) {
            Toast.makeText(context, "İzinler başarıyla tanımlandı!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Kamera ve mikrofon izinleri gereklidir!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(permissionsGranted, selectedAppMode) {
        if (permissionsGranted && selectedAppMode != null) {
            com.example.stream.StreamingService.serviceLifecycleOwner.resume()
            com.example.stream.StreamingService.startService(context)
        } else {
            com.example.stream.StreamingService.stopService(context)
            com.example.stream.StreamingService.serviceLifecycleOwner.stop()
        }
    }

    // Server State
    var isServerRunning by remember { mutableStateOf(true) }
    var passcode by remember { mutableStateOf(server.passcode) }
    var lowDataMode by remember { mutableStateOf(server.lowDataMode) }
    var cameraMuted by remember { mutableStateOf(server.cameraMuted) }
    var micMuted by remember { mutableStateOf(server.micMuted) }
    var cameraToggleState by remember { mutableStateOf(false) }

    // Live Web Stream previews
    var localPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var webBroadcasterBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activePreviewView by remember { mutableStateOf<androidx.camera.view.PreviewView?>(null) }
    var isVirtualActive by remember { mutableStateOf(false) }

    var isActivityVisible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                isActivityVisible = true
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                isActivityVisible = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Direct Android-to-Android client connection state
    var isDirectConnected by remember { mutableStateOf(false) }
    var isConnectingDirect by remember { mutableStateOf(false) }
    var directIp by remember { mutableStateOf("") }
    var directPort by remember { mutableStateOf("8080") }
    var directPasscode by remember { mutableStateOf("1234") }
    var isWatchingVideo by rememberSaveable { mutableStateOf(true) }
    var receiverQrTypeSelection by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isWatchingVideo) {
        if (!isWatchingVideo) {
            webBroadcasterBitmap = null
        }
    }
    var directJobs by remember { mutableStateOf<List<kotlinx.coroutines.Job>>(emptyList()) }

    // Live status updater using proper Jetpack Compose states & connection callback
    var activeConnections by remember { mutableStateOf(server.isClientConnected.get()) }
    var clientType by remember { mutableStateOf(server.clientType.get()) }

    DisposableEffect(server) {
        server.onConnectionStateChanged = { connected, type ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                activeConnections = connected
                clientType = type
            }
        }
        onDispose {
            server.onConnectionStateChanged = null
        }
    }

    var localIp by remember { mutableStateOf(getLocalIpAddress(context)) }
    var customIpOverride by rememberSaveable { mutableStateOf("") }
    val displayIp = if (customIpOverride.isNotEmpty()) customIpOverride else localIp

    LaunchedEffect(localIp) {
        if (directIp.isEmpty() && localIp.isNotEmpty() && localIp != "192.168.1.1") {
            val parts = localIp.split(".")
            if (parts.size == 4) {
                directIp = "${parts[0]}.${parts[1]}.${parts[2]}."
            }
        }
    }

    // Automatically request permissions on launch
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
        // Periodically refresh IP address
        while (true) {
            localIp = getLocalIpAddress(context)
            kotlinx.coroutines.delay(5000)
        }
    }

    // Feed local camera frames into the server when broadcasting and not cameraMuted
    LaunchedEffect(permissionsGranted, cameraMuted, lowDataMode, selectedAppMode, cameraToggleState, activePreviewView, isActivityVisible) {
        val shouldCapture = permissionsGranted && !cameraMuted && (selectedAppMode == "broadcaster")
        if (shouldCapture) {
            val camLifecycleOwner = com.example.stream.StreamingService.serviceLifecycleOwner
            val previewToUse = if (isActivityVisible) activePreviewView else null
            cameraCapturer.start(camLifecycleOwner, lowDataMode, previewToUse, object : CameraCapturer.FrameCallback {
                override fun onFrame(jpegBytes: ByteArray) {
                    server.latestFrame.set(jpegBytes)
                    // Decode bitmap to show local preview on screen (on main thread)
                    try {
                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            localPreviewBitmap = bmp
                        }
                    } catch (e: Exception) {}
                }
            })
            // Poll and keep isVirtualActive updated
            while (true) {
                isVirtualActive = cameraCapturer.isVirtualCameraActive()
                kotlinx.coroutines.delay(500)
            }
        } else {
            cameraCapturer.stop()
            localPreviewBitmap = null
            isVirtualActive = false
        }
    }

    // Clear incoming stream view if we disconnect or client is no longer web broadcaster
    LaunchedEffect(activeConnections, clientType) {
        if (!activeConnections || clientType != "Yayıncı") {
            webBroadcasterBitmap = null
        }
    }

    // Receive incoming stream from web broadcaster
    DisposableEffect(Unit) {
        server.webVideoCallback = { jpegBytes ->
            try {
                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    webBroadcasterBitmap = bmp
                }
            } catch (e: Exception) {}
        }
        onDispose {
            server.webVideoCallback = null
            directJobs.forEach { it.cancel() }
            server.audioPlayer.stop()
        }
    }

    if (showBroadcasterConsentDialog) {
        AlertDialog(
            onDismissRequest = { showBroadcasterConsentDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Kamera ve Mikrofon İzni", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Text(
                    text = "CamLink, cihazınızın kamerasını ve mikrofonunu kullanarak yerel ağınızdaki diğer cihazlara canlı görüntü ve ses yayını başlatacaktır.\n\nVerileriniz tamamen yerel ağınızda (P2P) şifreli olarak taşınır ve internete yüklenmez.\n\nKamerayı ve yayını başlatmak için onay veriyor musunuz?",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBroadcasterConsentDialog = false
                        selectedAppMode = "broadcaster"
                        selectedTab = 0
                        // Request permissions now that user confirmed
                        permissionLauncher.launch(requiredPermissions)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Onayla ve Başlat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBroadcasterConsentDialog = false }) {
                    Text("Vazgeç", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF2B2930),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (selectedAppMode == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1B1F))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant glowing circle with video camera icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFD0BCFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Logo",
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "CamLink P2P Gözcü",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bu cihazı hangi çalışma modunda başlatmak istersiniz?",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Broadcaster Card
            Card(
                onClick = {
                    showBroadcasterConsentDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2930)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF4F378B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsRemote,
                            contentDescription = "Yayıncı",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Yayıncı Modu (Kamera Paylaş)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bu telefonun kamerasını ve sesini canlı olarak paylaşır. Diğer telefon bu yayını izler.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Viewer Card
            Card(
                onClick = {
                    selectedAppMode = "viewer"
                    selectedTab = 1
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2B2930)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF381E72))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8DEF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "İzleyici",
                            tint = Color(0xFFE8DEF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "İzleyici Modu (Akışı İzle)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Diğer cihazdan gelen canlı kamera ve mikrofon ses akışını izler ve dinler.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFD0BCFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "CamLink",
                                    tint = Color(0xFF381E72),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (selectedAppMode == "broadcaster") "P2P Gözcü (Yayıncı)" else "P2P Gözcü (İzleyici)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFFE6E1E5)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isServerRunning) Color(0xFF4ADE80) else Color(0xFFEF4444))
                                    )
                                    Text(
                                        text = if (isServerRunning) "GÜVENLİ P2P AKTİF" else "SUNUCU KAPALI",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = Color(0xFF938F99)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedAppMode = null
                            }
                        ) {
                            Text(
                                text = "Mod Değiştir",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1C1B1F)
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    if (selectedAppMode == "broadcaster") {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.SettingsRemote, contentDescription = "Yayın & Bağlantı") },
                            label = { Text("Yayıncı", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    } else {
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.Tv, contentDescription = "İzleyici Paneli") },
                            label = { Text("Alıcı", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                            )
                        )
                    }
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Bildirimler") },
                        label = { Text("Bildirim", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.tertiary,
                            unselectedIconColor = Color.Gray,
                            indicatorColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!permissionsGranted) {
                    // Friendly permission request dashboard
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Güvenlik",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Kamera ve Ses İzni Gerekli",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Eski telefonunuzu bir güvenlik kamerası ve mikrofona dönüştürmek için kameraya ve mikrofona erişim sağlamanız gerekmektedir. Verileriniz tamamen çevrimdışı, doğrudan P2P olarak aktarılır.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(requiredPermissions)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("İzinleri Onayla", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> BroadcasterTab(
                            server = server,
                            cameraCapturer = cameraCapturer,
                            isServerRunning = isServerRunning,
                            onServerToggle = { running ->
                                isServerRunning = running
                                if (running) {
                                    server.start()
                                } else {
                                    server.stop()
                                }
                            },
                            passcode = passcode,
                            onPasscodeChange = {
                                passcode = it
                                server.passcode = it
                            },
                            lowDataMode = lowDataMode,
                            onLowDataToggle = {
                                lowDataMode = it
                                server.lowDataMode = it
                            },
                            cameraMuted = cameraMuted,
                            onCameraMuteToggle = {
                                cameraMuted = it
                                server.cameraMuted = it
                            },
                            micMuted = micMuted,
                            onMicMuteToggle = {
                                micMuted = it
                                server.micMuted = it
                            },
                            localIp = displayIp,
                            customIpOverride = customIpOverride,
                            onCustomIpOverrideChange = { customIpOverride = it },
                            activeConnections = activeConnections,
                            clientType = clientType,
                            previewBitmap = localPreviewBitmap,
                            onPreviewFrame = { localPreviewBitmap = it },
                            onCameraSwitchClick = {
                                cameraCapturer.toggleCameraFace()
                                cameraToggleState = !cameraToggleState
                            },
                            onActivePreviewViewChange = { activePreviewView = it },
                            isVirtualActive = isVirtualActive
                        )
                        1 -> ReceiverTab(
                            server = server,
                            cameraCapturer = cameraCapturer,
                            isWatchingVideo = isWatchingVideo,
                            onWatchingVideoToggle = { isWatchingVideo = it },
                            localIp = displayIp,
                            passcode = passcode,
                            webBroadcasterBitmap = webBroadcasterBitmap,
                            activeConnections = activeConnections,
                            clientType = clientType,
                            isDirectConnected = isDirectConnected,
                            isConnectingDirect = isConnectingDirect,
                            directIp = directIp,
                            onDirectIpChange = { directIp = it },
                            directPort = directPort,
                            onDirectPortChange = { directPort = it },
                            directPasscode = directPasscode,
                            onDirectPasscodeChange = { directPasscode = it },
                            cameraMuted = cameraMuted,
                            onCameraMuteToggle = {
                                cameraMuted = it
                                server.cameraMuted = it
                            },
                            micMuted = micMuted,
                            onMicMuteToggle = {
                                micMuted = it
                                server.micMuted = it
                            },
                            previewBitmap = localPreviewBitmap,
                            onCameraSwitchClick = {
                                cameraCapturer.toggleCameraFace()
                                cameraToggleState = !cameraToggleState
                            },
                            qrTypeSelection = receiverQrTypeSelection,
                            onQrTypeSelectionChange = { receiverQrTypeSelection = it },
                            onConnectDirect = {
                                isConnectingDirect = true
                                directJobs.forEach { it.cancel() }
                                val videoUrl = "http://$directIp:$directPort/video?passcode=$directPasscode"
                                val audioUrl = "http://$directIp:$directPort/audio?passcode=$directPasscode"
                                
                                val videoJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        streamMjpeg(videoUrl, { bitmap ->
                                            if (isWatchingVideo) {
                                                webBroadcasterBitmap = bitmap
                                            } else {
                                                webBroadcasterBitmap = null
                                            }
                                            isDirectConnected = true
                                            isConnectingDirect = false
                                        }, { error ->
                                            isConnectingDirect = false
                                            isDirectConnected = false
                                        })
                                    } catch (e: Exception) {
                                        isConnectingDirect = false
                                    }
                                }
                                
                                val audioJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        streamAudio(audioUrl, server.audioPlayer, { error ->
                                            Log.e("CamLink", "Direct audio error", error)
                                        })
                                    } catch (e: Exception) {}
                                }
                                
                                directJobs = listOf(videoJob, audioJob)
                            },
                            onDisconnectDirect = {
                                directJobs.forEach { it.cancel() }
                                directJobs = emptyList()
                                isDirectConnected = false
                                isConnectingDirect = false
                                webBroadcasterBitmap = null
                                server.audioPlayer.stop()
                            }
                        )
                        2 -> SettingsAndLogsTab(
                            logs = logs,
                            settings = notificationSettings,
                            onSettingsChange = { notificationManager.updateSettings(it) },
                            onClearLogs = { notificationManager.clearLogs() }
                        )
                    }
                }
            }
        }
    }
}

// ================== TAB 0: YAYIN VE BAĞLANTI (BROADCASTER SETUP) ==================
@Composable
fun BroadcasterTab(
    server: LocalStreamServer,
    cameraCapturer: CameraCapturer,
    isServerRunning: Boolean,
    onServerToggle: (Boolean) -> Unit,
    passcode: String,
    onPasscodeChange: (String) -> Unit,
    lowDataMode: Boolean,
    onLowDataToggle: (Boolean) -> Unit,
    cameraMuted: Boolean,
    onCameraMuteToggle: (Boolean) -> Unit,
    micMuted: Boolean,
    onMicMuteToggle: (Boolean) -> Unit,
    localIp: String,
    customIpOverride: String,
    onCustomIpOverrideChange: (String) -> Unit,
    activeConnections: Boolean,
    clientType: String,
    previewBitmap: Bitmap?,
    onPreviewFrame: (Bitmap) -> Unit,
    onCameraSwitchClick: () -> Unit,
    onActivePreviewViewChange: (androidx.camera.view.PreviewView?) -> Unit,
    isVirtualActive: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val connectionUrl = "http://$localIp:${server.port}/?passcode=${server.passcode}&role=viewer"
    val qrCodeBitmap = remember(connectionUrl) {
        generateQrCode(connectionUrl)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Server Controller Hero
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isServerRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Red.copy(alpha = 0.05f)
                ),
                border = CardDefaults.outlinedCardBorder(true),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServerRunning) "P2P YAYIN SUNUCUSU AKTİF" else "SUNUCU DEVRE DIŞI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isServerRunning) MaterialTheme.colorScheme.primary else Color.Red
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServerRunning) "Tarayıcılardan doğrudan bağlantı kabul ediliyor." else "Yayın akışını başlatmak için sunucuyu açın.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onServerToggle(!isServerRunning) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerRunning) Color.Red else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isServerRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServerRunning) "Sunucuyu Durdur" else "Sunucuyu Başlat",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Local Preview and Controls (Only visible when server is active and camera not muted)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📹 Android Kamera Önizlemesi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    var isFullScreenPreviewOpen by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .clickable(enabled = !cameraMuted && (isVirtualActive || previewBitmap != null)) {
                                isFullScreenPreviewOpen = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!cameraMuted) {
                            if (isVirtualActive) {
                                if (previewBitmap != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = "Sanal Kamera Önizleme",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                        // A nice visual overlay / badge showing search icon for zoom
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "Zoom",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Tam Ekran",
                                                    color = Color.White,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Stream,
                                            contentDescription = null,
                                            tint = Color.DarkGray,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Yayın başlatılıyor...",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } else {
                                // Real Camera - Use smooth hardware accelerated PreviewView
                                androidx.compose.ui.viewinterop.AndroidView(
                                    factory = { ctx ->
                                        androidx.camera.view.PreviewView(ctx).apply {
                                            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                            onActivePreviewViewChange(this)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    update = { _ -> }
                                )
                                DisposableEffect(Unit) {
                                    onDispose {
                                        onActivePreviewViewChange(null)
                                    }
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Kamera Sessize Alındı",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    if (isFullScreenPreviewOpen && previewBitmap != null) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { isFullScreenPreviewOpen = false },
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.95f))
                                    .clickable { isFullScreenPreviewOpen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Tam Ekran Kamera Önizleme",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                                
                                // Close button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                        .clickable { isFullScreenPreviewOpen = false }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kapat",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Info label
                                Text(
                                    text = "Gerçek Boyut (Kapatmak için dokunun)",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { onCameraMuteToggle(!cameraMuted) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (cameraMuted) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                imageVector = if (cameraMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "Kamera Mute",
                                tint = if (cameraMuted) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { onMicMuteToggle(!micMuted) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (micMuted) Color.Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                imageVector = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mic Mute",
                                tint = if (micMuted) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                cameraCapturer.toggleCameraFace()
                                onCameraSwitchClick()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Kamera Çevir",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 3. QR Code scanner to direct connection
        if (isServerRunning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔗 Bağlantı QR Kodu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "İzlemek veya yayınlamak istediğiniz telefondan (iPhone/Android) bu QR kodu okutun.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        qrCodeBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Bağlantı QR",
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = connectionUrl,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aynı Wi-Fi ağına bağlı olmalıdırlar. İnternetsiz (çevrimdışı) çalışır.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF381E72).copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Bilgi",
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Neden Bağlantı Kurulamıyor olabilir?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD0BCFF)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Şu an uygulama bulut tabanlı bir sanal emülatörde çalışmaktadır. Bu nedenle tespit edilen yerel IP adresi ($localIp), fiziksel telefonunuzun veya bilgisayarınızın bağlı olduğu yerel Wi-Fi ağı ile aynı değildir ve yönlendirilemez.\n\nGerçek bağlantıyı test etmek için sağ üstteki menüden uygulamayı APK olarak indirip gerçek bir Android telefona kurmanız gerekmektedir. Gerçek telefonda her iki cihaz da aynı Wi-Fi ağına bağlandığında P2P yayın saniyeler içinde sorunsuz başlayacaktır.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE6E1E5),
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "🌐 Özel IP / Yönlendirme Girin:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD0BCFF)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customIpOverride,
                                        onValueChange = onCustomIpOverrideChange,
                                        placeholder = { Text("Örn: 192.168.1.100", fontSize = 11.sp, color = Color.Gray) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                                            cursorColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    if (customIpOverride.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { onCustomIpOverrideChange("") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("Sıfırla", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Customizable Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚙️ Güvenlik & Yayın Ayarları",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Passcode input field
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = onPasscodeChange,
                        label = { Text("Erişim Şifresi (Güvenlik)") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Diğer cihazların bağlanabilmesi için bu şifreyi girmeleri gerekir. Uçtan uca güvenlik sağlar.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // 5. Active Connection Monitoring Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔗 Bağlantı İzleme",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeConnections) "Bağlı Cihaz: $clientType" else "Bağlı cihaz bulunmuyor.",
                            fontSize = 12.sp,
                            color = if (activeConnections) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeConnections) MaterialTheme.colorScheme.primary else Color.DarkGray)
                    )
                }
            }
        }
    }
}

// ================== TAB 1: ALICI / İZLEYİCİ EKRANI (RECEIVER SCREEN) ==================
@Composable
fun ReceiverTab(
    server: LocalStreamServer,
    cameraCapturer: CameraCapturer,
    isWatchingVideo: Boolean,
    onWatchingVideoToggle: (Boolean) -> Unit,
    localIp: String,
    passcode: String,
    webBroadcasterBitmap: Bitmap?,
    activeConnections: Boolean,
    clientType: String,
    isDirectConnected: Boolean,
    isConnectingDirect: Boolean,
    directIp: String,
    onDirectIpChange: (String) -> Unit,
    directPort: String,
    onDirectPortChange: (String) -> Unit,
    directPasscode: String,
    onDirectPasscodeChange: (String) -> Unit,
    cameraMuted: Boolean,
    onCameraMuteToggle: (Boolean) -> Unit,
    micMuted: Boolean,
    onMicMuteToggle: (Boolean) -> Unit,
    previewBitmap: Bitmap?,
    onCameraSwitchClick: () -> Unit,
    qrTypeSelection: Int,
    onQrTypeSelectionChange: (Int) -> Unit,
    onConnectDirect: () -> Unit,
    onDisconnectDirect: () -> Unit
) {
    val connectionUrl = "http://$localIp:${server.port}/?passcode=$passcode&role=broadcaster"

    var connectionModeTab by remember { mutableIntStateOf(0) } // 0: Web QR, 1: Direct Android IP
    var showQrScanner by remember { mutableStateOf(false) }

    val isIncomingStreamActive = (activeConnections && clientType == "Yayıncı") || isDirectConnected

    if (showQrScanner) {
        QrScannerDialog(
            cameraCapturer = cameraCapturer,
            onQrScanned = { qrText ->
                val parsed = parseQrCodeUrl(qrText)
                if (parsed != null) {
                    onDirectIpChange(parsed.first)
                    onDirectPortChange(parsed.second)
                    onDirectPasscodeChange(parsed.third)
                    // Trigger asynchronous connection
                    onConnectDirect()
                }
                showQrScanner = false
            },
            onDismiss = {
                showQrScanner = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(true)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Receiver",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bu Cihaz: İZLEYİCİ (ALICI)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = if (isDirectConnected) "Diğer Android telefona bağlı." else "Diğer cihazların kamerasını ve sesini izleyin.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // ================== YENİ: SİSTEM BAĞLANTI / ÖNİZLEME QR KODU KARTI ==================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder(true)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📹 Sistem Bağlantı ve Eşleştirme Bölümü",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bu cihaza başka bir telefonun kamerasını bağlamak için o telefondan aşağıdaki QR kodu okutabilir veya bağlantı adresine tıklayabilirsiniz.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val selectedUrl = "http://$localIp:${server.port}/?passcode=$passcode&role=broadcaster"
                    val selectedQrBitmap = remember(selectedUrl) {
                        generateQrCode(selectedUrl)
                    }

                    var isFullScreenQrOpen by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .clickable {
                                isFullScreenQrOpen = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        selectedQrBitmap?.let {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Sistem Bağlantı QR",
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(8.dp)
                                )
                                // Zoom badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ZoomIn,
                                            contentDescription = "Zoom",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Tam Ekran",
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Kamera Gönderici Adresi (Yayıncı):",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val context = LocalContext.current
                    
                    // Clickable link that goes directly to the browser
                    Text(
                        text = selectedUrl,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    uriHandler.openUri(selectedUrl)
                                    android.widget.Toast.makeText(context, "Tarayıcıda açılıyor...", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Bağlantı açılamadı: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Instant Video and Audio Transmission Window (under the address)
                    Text(
                        text = "📺 Alınan Yayın Önizleme Penceresi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isIncomingStreamActive && isWatchingVideo && webBroadcasterBitmap != null) {
                            Image(
                                bitmap = webBroadcasterBitmap.asImageBitmap(),
                                contentDescription = "Gelen Yayın Akışı",
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Streaming indicator overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Green)
                                    )
                                    Text(
                                        text = "CANLI YAYIN",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = if (!isWatchingVideo) Icons.Default.VisibilityOff else Icons.Default.VideocamOff,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (!isWatchingVideo) "Görüntü Akışı Kapalı" else "Yayıncı Bağlantısı Bekleniyor...",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!isWatchingVideo) "Görüntüyü izlemek için alttaki butonu aktif edin." else "Karşı telefondan QR kodu okutarak yayını başlatın.",
                                    color = Color.DarkGray,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Audio and Control Panel directly under the preview window
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Audio status display
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isIncomingStreamActive) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                tint = if (isIncomingStreamActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isIncomingStreamActive) "Ses Aktif" else "Ses Bağlantısı Yok",
                                color = if (isIncomingStreamActive) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Toggle button for watching video
                        Button(
                            onClick = { onWatchingVideoToggle(!isWatchingVideo) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWatchingVideo) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFC62828).copy(alpha = 0.2f),
                                contentColor = if (isWatchingVideo) Color(0xFF81C784) else Color(0xFFE57373)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isWatchingVideo) Color(0xFF4CAF50).copy(alpha = 0.4f) else Color(0xFFEF5350).copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isWatchingVideo) "Görüntüyü Kapat" else "Görüntüyü Aç",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (isDirectConnected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onDisconnectDirect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red.copy(alpha = 0.2f),
                                contentColor = Color.Red
                            ),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Durdur", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bağlantıyı Kes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isFullScreenQrOpen && selectedQrBitmap != null) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { isFullScreenQrOpen = false },
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.95f))
                                    .clickable { isFullScreenQrOpen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Image(
                                        bitmap = selectedQrBitmap.asImageBitmap(),
                                        contentDescription = "QR Tam Ekran",
                                        modifier = Modifier
                                            .size(300.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White)
                                            .padding(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Kamera Gönderici Adresi (Yayıncı):",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        Text(
                                            text = selectedUrl,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                // Close button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                        .clickable { isFullScreenQrOpen = false }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kapat",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Info label
                                Text(
                                    text = "Bağlantı QR Kodu (Kapatmak için dokunun)",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================== TAB 2: BİLDİRİM VE GÜVENLİK GÜNLÜKLERİ (SETTINGS & LOGS) ==================
@Composable
fun SettingsAndLogsTab(
    logs: List<NotificationLog>,
    settings: NotificationSettings,
    onSettingsChange: (NotificationSettings) -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🛡️ Güvenlik & Bildirim Paneli",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )

        // Customizable Notification Switches
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Bildirim Tercihleri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                NotificationToggle(
                    title = "Bağlantı Bildirimleri",
                    subtitle = "Yeni bir izleyici bağlandığında uyar",
                    checked = settings.notifyOnConnect,
                    onCheckedChange = { onSettingsChange(settings.copy(notifyOnConnect = it)) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

                NotificationToggle(
                    title = "Bağlantı Kesilme Bildirimleri",
                    subtitle = "İzleyiciler ayrıldığında uyar",
                    checked = settings.notifyOnDisconnect,
                    onCheckedChange = { onSettingsChange(settings.copy(notifyOnDisconnect = it)) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

                NotificationToggle(
                    title = "Yetkisiz Erişim Teşebbüsleri",
                    subtitle = "Hatalı şifre denemelerinde acil bildirim yolla",
                    checked = settings.notifyOnAuthFailure,
                    onCheckedChange = { onSettingsChange(settings.copy(notifyOnAuthFailure = it)) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.2f))

                NotificationToggle(
                    title = "Yayın Akışı Durum Değişiklikleri",
                    subtitle = "Kamera/Ses başlatılıp durdurulduğunda bildir",
                    checked = settings.notifyOnStreamStateChange,
                    onCheckedChange = { onSettingsChange(settings.copy(notifyOnStreamStateChange = it)) }
                )
            }
        }

        // Historic Logs Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📋 Güvenlik ve Erişim Günlükleri (${logs.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = "Temizle",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onClearLogs() }
                    .padding(4.dp)
            )
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Herhangi bir güvenlik veya bağlantı günlüğü bulunmuyor.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogItem(log)
                }
            }
        }
    }
}

@Composable
fun NotificationToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun LogItem(log: NotificationLog) {
    val indicatorColor = when (log.type) {
        NotificationType.SUCCESS -> Color(0xFF10B981) // Green
        NotificationType.WARNING -> Color(0xFFF59E0B) // Orange/Yellow
        NotificationType.DANGER -> Color(0xFFEF4444)  // Red
        NotificationType.INFO -> Color(0xFF3B82F6)    // Blue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = log.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = log.time, fontSize = 10.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = log.message, fontSize = 11.sp, color = Color.LightGray)
            }
        }
    }
}

// ================== UTILITIES ==================
fun getLocalIpAddress(context: Context): String {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress
                    if (sAddr != null) {
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) {
                            return sAddr
                        }
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Log.e("IPAddress", "Error getting IP", ex)
    }
    return "192.168.1.1" // Fallback local IP
}

fun generateQrCode(text: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        Log.e("QRCode", "Error generating QR Code", e)
        null
    }
}

// Native streaming helpers
suspend fun streamMjpeg(
    urlStr: String,
    onFrame: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            val inputStream = java.io.BufferedInputStream(connection.inputStream)
            
            val byteStream = java.io.ByteArrayOutputStream()
            var inJpeg = false
            var prevByte = 0
            
            val tempBuf = ByteArray(4096)
            while (isActive) {
                val count = inputStream.read(tempBuf)
                if (count == -1) break
                
                for (i in 0 until count) {
                    val b = tempBuf[i]
                    val bInt = b.toInt() and 0xFF
                    
                    if (!inJpeg) {
                        if (prevByte == 0xFF && bInt == 0xD8) {
                            byteStream.reset()
                            byteStream.write(0xFF)
                            byteStream.write(0xD8)
                            inJpeg = true
                        } else {
                            byteStream.write(bInt)
                        }
                    } else {
                        byteStream.write(bInt)
                        if (prevByte == 0xFF && bInt == 0xD9) {
                            val arr = byteStream.toByteArray()
                            try {
                                val bitmap = BitmapFactory.decodeByteArray(arr, 0, arr.size)
                                if (bitmap != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        onFrame(bitmap)
                                    }
                                }
                            } catch (e: Exception) {}
                            byteStream.reset()
                            inJpeg = false
                        }
                    }
                    prevByte = bInt
                }
            }
        } catch (e: Exception) {
            onError(e)
        } finally {
            connection?.disconnect()
        }
    }
}

suspend fun streamAudio(
    urlStr: String,
    audioPlayer: AudioPlayer,
    onError: (Exception) -> Unit
) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlStr)
            connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 0
            val inputStream = java.io.BufferedInputStream(connection.inputStream)
            
            val lastFourBytes = ByteArray(4)
            var count = 0
            while (isActive) {
                val b = inputStream.read()
                if (b == -1) break
                lastFourBytes[count % 4] = b.toByte()
                count++
                
                if (count >= 4) {
                    val idx1 = (count - 4) % 4
                    val idx2 = (count - 3) % 4
                    val idx3 = (count - 2) % 4
                    val idx4 = (count - 1) % 4
                    if (lastFourBytes[idx1] == '\r'.toByte() && lastFourBytes[idx2] == '\n'.toByte() &&
                        lastFourBytes[idx3] == '\r'.toByte() && lastFourBytes[idx4] == '\n'.toByte()) {
                        break
                    }
                }
            }
            
            val tempBuf = ByteArray(2048)
            audioPlayer.start()
            while (isActive) {
                val read = inputStream.read(tempBuf)
                if (read == -1) break
                val chunk = tempBuf.copyOf(read)
                audioPlayer.write(chunk)
            }
        } catch (e: Exception) {
            onError(e)
        } finally {
            audioPlayer.stop()
            connection?.disconnect()
        }
    }
}

fun parseQrCodeUrl(url: String): Triple<String, String, String>? {
    return try {
        // Expected format: http://192.168.1.105:8080/?passcode=1234&role=viewer
        val uri = java.net.URI(url)
        val host = uri.host ?: return null
        val port = if (uri.port != -1) uri.port.toString() else "8080"
        
        val query = uri.query ?: ""
        var passcode = "1234"
        val params = query.split("&")
        for (param in params) {
            val parts = param.split("=")
            if (parts.size == 2) {
                if (parts[0] == "passcode") {
                    passcode = parts[1]
                }
            }
        }
        Triple(host, port, passcode)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun QrScannerDialog(
    cameraCapturer: CameraCapturer,
    onQrScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Yayıncı QR Kodu Okutun",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Yayıncı telefondaki QR kodu bu kameraya gösterin.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                var scannerFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (scannerFrameBitmap != null) {
                        Image(
                            bitmap = scannerFrameBitmap!!.asImageBitmap(),
                            contentDescription = "Scanner Frame",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                DisposableEffect(Unit) {
                    cameraCapturer.start(lifecycleOwner, true, object : CameraCapturer.FrameCallback {
                        val reader = com.google.zxing.MultiFormatReader()
                        var scanCoolDown = 0L
                        
                        override fun onFrame(jpegBytes: ByteArray) {
                            try {
                                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                if (bmp != null) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        scannerFrameBitmap = bmp
                                    }
                                    
                                    val now = System.currentTimeMillis()
                                    if (now - scanCoolDown > 1000) {
                                        scanCoolDown = now
                                        val width = bmp.width
                                        val height = bmp.height
                                        val pixels = IntArray(width * height)
                                        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                                        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
                                        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
                                        
                                        val result = reader.decode(binaryBitmap)
                                        val qrText = result.text
                                        if (qrText != null && qrText.isNotEmpty()) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                onQrScanned(qrText)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore decode failures
                            }
                        }
                    })
                    onDispose {
                        cameraCapturer.stop()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.2f),
                        contentColor = Color.Red
                    )
                ) {
                    Text("İptal Et")
                }
            }
        }
    }
}
