package com.example.ui

import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.SavedAnalysis
import com.example.data.model.TranscriptSegment
import com.example.ui.theme.*
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.VideoAnalyzerViewModel

private val CupertinoTextGreenValue = Color(0xFF64D2FF)

@Composable
fun AnalyzerScreen(
    viewModel: VideoAnalyzerViewModel,
    modifier: Modifier = Modifier
) {
    val currentAnalysis by viewModel.currentAnalysis.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val isChatSending by viewModel.isChatSending.collectAsStateWithLifecycle()
    val progressText by viewModel.progressText.collectAsStateWithLifecycle()
    val progressPercent by viewModel.progressPercent.collectAsStateWithLifecycle()
    val analysisError by viewModel.analysisError.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 768

    // Animated Theme Transition states
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isAnimatingTheme by remember { mutableStateOf(false) }
    var animatingToDark by remember { mutableStateOf(false) }
    val themeAnimProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    val onToggleTheme = {
        coroutineScope.launch {
            if (!isAnimatingTheme) {
                val nextIsDark = !isDark
                animatingToDark = nextIsDark
                isAnimatingTheme = true
                themeAnimProgress.snapTo(0f)
                
                // Swift and fluid 1200ms animation of the sun rising/falling with smooth deceleration
                themeAnimProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 1200,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
                
                // Finalize theme switch
                viewModel.toggleDarkMode()
                isAnimatingTheme = false
            }
        }
        Unit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(CosmicDarkBg, CosmicMutedBg, CosmicDarkBg2),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(1000f, 1500f)
                )
            )
    ) {
        if (currentAnalysis == null) {
            HomeContent(
                viewModel = viewModel,
                isAnalyzing = isAnalyzing,
                progressText = progressText,
                progressPercent = progressPercent,
                analysisError = analysisError,
                isWideScreen = isWideScreen,
                isDark = isDark,
                onThemeToggle = onToggleTheme
            )
        } else {
            ResultContent(
                viewModel = viewModel,
                analysis = currentAnalysis!!,
                isChatSending = isChatSending,
                isWideScreen = isWideScreen,
                isDark = isDark,
                onThemeToggle = onToggleTheme
            )
        }

        // Custom Sun setting/rising overlay on top of everything
        if (isAnimatingTheme) {
            ThemeTransitionOverlay(
                progress = themeAnimProgress.value,
                isToDark = animatingToDark
            )
        }
    }
}

@Composable
fun HomeContent(
    viewModel: VideoAnalyzerViewModel,
    isAnalyzing: Boolean,
    progressText: String,
    progressPercent: Float,
    analysisError: String?,
    isWideScreen: Boolean,
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    val youtubeUrl by viewModel.youtubeUrl.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val summaryLength by viewModel.summaryLength.collectAsStateWithLifecycle()
    val savedAnalyses by viewModel.savedAnalyses.collectAsStateWithLifecycle()
    
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var inputKeyText by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text(
                    "API Key Ayarı",
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Google AI Studio'dan aldığınız Gemini API Key kodunu girin. Ayarlamazsanız sistem varsayılan anahtarını dener.",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                    OutlinedTextField(
                        value = inputKeyText,
                        onValueChange = { inputKeyText = it },
                        placeholder = null,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                val clipboardManager = LocalClipboardManager.current
                                Text(
                                    text = "Yapıştır",
                                    color = PurplePrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(PurplePrimary.copy(alpha = 0.12f))
                                        .clickable {
                                            clipboardManager.getText()?.text?.let { text ->
                                                inputKeyText = text
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Anahtar Görünürlüğü",
                                        tint = TextWhite
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = PurplePrimary,
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = BorderColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveApiKey(inputKeyText)
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    Text("Kaydet", color = CosmicDarkBg2, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("İptal", color = TextGray)
                }
            },
            containerColor = CosmicCardOuter
        )
    }

    if (isWideScreen) {
        // Dual column layout for wide desktop/tablet screen
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Analyzer Controls
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppHeader(isDark = isDark, onThemeToggle = onThemeToggle)
                
                ApiKeySection(
                    apiKey = apiKey,
                    onEditClick = {
                        inputKeyText = apiKey
                        showApiKeyDialog = true
                    },
                    onDeleteClick = {
                        viewModel.clearApiKey()
                    }
                )

                InputSection(
                    youtubeUrl = youtubeUrl,
                    onUrlChange = { viewModel.youtubeUrl.value = it },
                    summaryLength = summaryLength,
                    onLengthChange = { viewModel.setSummaryLength(it) },
                    isAnalyzing = isAnalyzing,
                    progressText = progressText,
                    progressPercent = progressPercent,
                    analysisError = analysisError,
                    onAnalyzeClick = { viewModel.startAnalysis() }
                )
            }

            // Right Column: Library / History
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(CosmicCardBg, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                LibraryHeader(
                    hasHistory = savedAnalyses.isNotEmpty(),
                    onClearAll = { viewModel.clearAllHistory() }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                HistoryList(
                    savedAnalyses = savedAnalyses,
                    onItemClick = { viewModel.loadAnalysis(it) },
                    onDeleteClick = { viewModel.deleteAnalysis(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // Single column scrollable portrait list for standard phones
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                AppHeader(isDark = isDark, onThemeToggle = onThemeToggle)
            }

            item {
                ApiKeySection(
                    apiKey = apiKey,
                    onEditClick = {
                        inputKeyText = apiKey
                        showApiKeyDialog = true
                    },
                    onDeleteClick = {
                        viewModel.clearApiKey()
                    }
                )
            }

            item {
                InputSection(
                    youtubeUrl = youtubeUrl,
                    onUrlChange = { viewModel.youtubeUrl.value = it },
                    summaryLength = summaryLength,
                    onLengthChange = { viewModel.setSummaryLength(it) },
                    isAnalyzing = isAnalyzing,
                    progressText = progressText,
                    progressPercent = progressPercent,
                    analysisError = analysisError,
                    onAnalyzeClick = { viewModel.startAnalysis() }
                )
            }

            if (savedAnalyses.isNotEmpty()) {
                item {
                    LibraryHeader(
                        hasHistory = true,
                        onClearAll = { viewModel.clearAllHistory() }
                    )
                }

                items(savedAnalyses, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { viewModel.loadAnalysis(item) },
                        onDeleteClick = { viewModel.deleteAnalysis(item) }
                    )
                }
            } else {
                item {
                    EmptyHistoryPlaceholder()
                }
            }
        }
    }
}

@Composable
fun AppHeader(isDark: Boolean, onThemeToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Yapay Zeka",
                    tint = PurplePrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    "YouTube",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                )
            }
            Text(
                "Video Analizör & Yorumlayıcı",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextWhite,
                    letterSpacing = 0.5.sp
                )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(PurplePrimary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "Design by Kerem Akşahin",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PurplePrimary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Custom drawn switch button
        IconButton(
            onClick = onThemeToggle,
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(PurplePrimary.copy(alpha = 0.12f))
                .testTag("theme_toggle_button")
        ) {
            if (isDark) {
                MoonIcon(tint = PurplePrimary)
            } else {
                SunIcon(tint = Color(0xFFF57C00))
            }
        }
    }
}

@Composable
fun ApiKeySection(
    apiKey: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurplePrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "API Anahtarı",
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Gemini API Anahtarı",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                )
                if (apiKey.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(GreenActive)
                        )
                        Text(
                            "Özel Key Aktif (••••${if (apiKey.length > 4) apiKey.takeLast(4) else ""})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GreenActive,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else {
                    Text(
                        "Girilmedi (Varsayılan key denenecek)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextGray
                        )
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_api_key")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Değiştir",
                        tint = PurplePrimary
                    )
                }
                if (apiKey.isNotEmpty()) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_api_key")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Sil",
                        tint = Color.Red.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
}

@Composable
fun InputSection(
    youtubeUrl: String,
    onUrlChange: (String) -> Unit,
    summaryLength: String,
    onLengthChange: (String) -> Unit,
    isAnalyzing: Boolean,
    progressText: String,
    progressPercent: Float,
    analysisError: String?,
    onAnalyzeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Analiz",
                    tint = PurplePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Video Analiz",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                )
            }

            OutlinedTextField(
                value = youtubeUrl,
                onValueChange = onUrlChange,
                label = { Text("YouTube Video URL'si", color = TextGray) },
                placeholder = { Text("https://www.youtube.com/watch?v=...", color = Color.Gray) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Create,
                        contentDescription = "Bağlantı",
                        tint = PurplePrimary
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        val clipboardManager = LocalClipboardManager.current
                        Text(
                            text = "Yapıştır",
                            color = PurplePrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PurplePrimary.copy(alpha = 0.12f))
                                .clickable {
                                    clipboardManager.getText()?.text?.let { text ->
                                        onUrlChange(text)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        if (youtubeUrl.isNotEmpty()) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Temizle",
                                    tint = TextWhite
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (!isAnalyzing) onAnalyzeClick() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = PurplePrimary,
                    focusedBorderColor = PurplePrimary,
                    unfocusedBorderColor = BorderColor,
                    focusedLabelColor = PurplePrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("youtube_url_input")
            )

            // Length select segments (Typographic Clean style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PurplePrimary.copy(alpha = 0.08f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val isShort = summaryLength == "short"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isShort) PurplePrimary else Color.Transparent)
                        .clickable { onLengthChange("short") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Kısa Özet",
                        color = if (isShort) Color.White else TextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isShort) PurplePrimary else Color.Transparent)
                        .clickable { onLengthChange("long") }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Uzun Özet",
                        color = if (!isShort) Color.White else TextGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (analysisError != null) {
                Text(
                    text = analysisError,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isAnalyzing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = PurplePrimary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = progressText,
                            color = TextGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "%${(progressPercent * 100).toInt()}",
                            color = PurplePrimary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = PurplePrimary,
                        trackColor = CosmicDarkBg2,
                    )
                }
            } else {
                Button(
                    onClick = onAnalyzeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("analyze_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = CosmicDarkBg2
                        )
                        Text(
                            "ANALİZ ET",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CosmicDarkBg2
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryHeader(
    hasHistory: Boolean,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = PurplePrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            "Geçmiş Analizler",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextWhite
            ),
            modifier = Modifier.weight(1f)
        )
        if (hasHistory) {
            TextButton(
                onClick = onClearAll,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text(
                    "Tümünü Temizle",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = TextGray.copy(alpha = 0.4f),
            modifier = Modifier.size(60.dp)
        )
        Text(
            "Henüz hiç video analiz edilmedi.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextGray)
        )
    }
}

@Composable
fun HistoryList(
    savedAnalyses: List<SavedAnalysis>,
    onItemClick: (SavedAnalysis) -> Unit,
    onDeleteClick: (SavedAnalysis) -> Unit,
    modifier: Modifier = Modifier
) {
    if (savedAnalyses.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHistoryPlaceholder()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(savedAnalyses, key = { it.id }) { item ->
                HistoryItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onDeleteClick = { onDeleteClick(item) }
                )
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: SavedAnalysis,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.videoTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.videoTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.videoAuthor,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Kaydı Sil",
                    tint = Color.Gray.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// RESULT SCREEN LAYOUT
// ═══════════════════════════════════════════

@Composable
fun ResultContent(
    viewModel: VideoAnalyzerViewModel,
    analysis: SavedAnalysis,
    isChatSending: Boolean,
    isWideScreen: Boolean,
    isDark: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val segments by viewModel.transcriptSegments.collectAsStateWithLifecycle()
    val summarySuggestions by viewModel.summarySuggestions.collectAsStateWithLifecycle()
 
    var activeTab by remember { mutableStateOf(0) } // 0 = Transkript, 1 = Soru-Cevap
 
    fun copyToClipboard(text: String) {
        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
        android.widget.Toast.makeText(context, "Kopyalandı!", android.widget.Toast.LENGTH_SHORT).show()
    }
 
    if (isWideScreen) {
        // Dual page split layout for desktop/tablet result page, summary on left, panels on right
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Video Info & Summary Card
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ResultHeader(
                    isDark = isDark,
                    onThemeToggle = onThemeToggle,
                    onBackClick = { viewModel.closeCurrentAnalysis() },
                    onCopyClick = {
                        copyToClipboard(analysis.summary)
                    },
                    onShareClick = {
                        val shareText = "*${analysis.videoTitle}*\nYazar: ${analysis.videoAuthor}\n\nAI ÖZETİ:\n${analysis.summary}\n\nKaynak: ${analysis.videoUrl}"
                        copyToClipboard(shareText)
                    }
                )

                ResultCoverCard(analysis = analysis)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = PurplePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Yapay Zeka Özet Sonucu",
                                    fontWeight = FontWeight.Bold,
                                    color = PurplePrimary,
                                    fontSize = 16.sp
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    MarkdownText(text = analysis.summary)
                                }
                                
                                if (summarySuggestions.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            "Asistana Sormak İçin Tıklayın:",
                                            color = PurplePrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            for (sug in summarySuggestions) {
                                                SuggestionChip(
                                                    text = sug,
                                                    onClick = {
                                                        activeTab = 1 // Switch to Q&A Chat automatically
                                                        viewModel.sendChatMessage(sug)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: Transkript Arama or Chat Q&A Frame
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(CosmicCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            ) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = PurplePrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = PurplePrimary
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Transkript & Arama", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("AI Soru-Cevap", fontWeight = FontWeight.Bold) }
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (activeTab == 0) {
                        TranscriptSearchPanel(
                            viewModel = viewModel,
                            segments = segments
                        )
                    } else {
                        ChatRoomPanel(
                            viewModel = viewModel,
                            isChatSending = isChatSending
                        )
                    }
                }
            }
        }
    } else {
        // Single column mobile scroll layout
        var showDetailsByTab by remember { mutableStateOf(0) } // 0 = Özet, 1 = Transkript, 2 = AI Karşılıklı Sohbet

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .systemBarsPadding()
        ) {
            ResultHeader(
                isDark = isDark,
                onThemeToggle = onThemeToggle,
                onBackClick = { viewModel.closeCurrentAnalysis() },
                onCopyClick = {
                    val copyStr = when (showDetailsByTab) {
                        0 -> analysis.summary
                        1 -> segments.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
                        else -> "YouTube Analizör Sohbet Odası"
                    }
                    copyToClipboard(copyStr)
                },
                onShareClick = {
                    val shareText = "*${analysis.videoTitle}*\nYazar: ${analysis.videoAuthor}\n\nAI ÖZETİ:\n${analysis.summary}\n\nKaynak: ${analysis.videoUrl}"
                    copyToClipboard(shareText)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            ResultCoverCard(analysis = analysis)

            Spacer(modifier = Modifier.height(14.dp))

            // Navigation tabs row
            ScrollableTabRow(
                selectedTabIndex = showDetailsByTab,
                containerColor = Color.Transparent,
                contentColor = PurplePrimary,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[showDetailsByTab]),
                        color = PurplePrimary
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = showDetailsByTab == 0,
                    onClick = { showDetailsByTab = 0 },
                    text = { Text("AI Özet", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
                Tab(
                    selected = showDetailsByTab == 1,
                    onClick = { showDetailsByTab = 1 },
                    text = { Text("Transkript", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
                Tab(
                    selected = showDetailsByTab == 2,
                    onClick = { showDetailsByTab = 2 },
                    text = { Text("Soru-Cevap", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (showDetailsByTab) {
                    0 -> {
                        // Summary Block view
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 60.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        MarkdownText(text = analysis.summary)
                                        
                                        if (summarySuggestions.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                "Asistana Sormak İçin Tıklayın:",
                                                color = PurplePrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                for (sug in summarySuggestions) {
                                                    SuggestionChip(
                                                        text = sug,
                                                        onClick = {
                                                            showDetailsByTab = 2 // Switch tab
                                                            viewModel.sendChatMessage(sug)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Transcript block view
                        TranscriptSearchPanel(viewModel = viewModel, segments = segments)
                    }
                    2 -> {
                        // AI Q&A panel
                        ChatRoomPanel(viewModel = viewModel, isChatSending = isChatSending)
                    }
                }
            }
        }
    }
}

@Composable
fun ResultHeader(
    isDark: Boolean,
    onThemeToggle: () -> Unit,
    onBackClick: () -> Unit,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("back_button")
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Geri Dön",
                tint = PurplePrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            "Video Analiz Sonucu",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextWhite,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onCopyClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("copy_result")
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Kopyala",
                tint = TextGray
            )
        }
        IconButton(
            onClick = onShareClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("share_result")
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Paylaş",
                tint = TextGray
            )
        }
        // Custom theme toggle inside ResultHeader
        IconButton(
            onClick = onThemeToggle,
            modifier = Modifier
                .size(38.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(PurplePrimary.copy(alpha = 0.12f))
                .testTag("result_theme_toggle_button")
        ) {
            if (isDark) {
                MoonIcon(tint = PurplePrimary, modifier = Modifier.size(20.dp))
            } else {
                SunIcon(tint = Color(0xFFF57C00), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun ResultCoverCard(analysis: SavedAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicCardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = analysis.thumbnailUrl,
                contentDescription = analysis.videoTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 110.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = analysis.videoTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = analysis.videoAuthor,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
// SUB PANELS FOR TABS
// ═══════════════════════════════════════════

@Composable
fun TranscriptSearchPanel(
    viewModel: VideoAnalyzerViewModel,
    segments: List<TranscriptSegment>
) {
    val searchWord by viewModel.transcriptSearchQuery.collectAsStateWithLifecycle()
    val filtered by viewModel.filteredSegments.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchWord,
            onValueChange = { viewModel.transcriptSearchQuery.value = it },
            placeholder = { Text("Altyazılarda ya da videoda geçen kelimeleri ara...", color = TextGray) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = PurplePrimary
                )
            },
            trailingIcon = {
                if (searchWord.isNotEmpty()) {
                    IconButton(onClick = { viewModel.transcriptSearchQuery.value = "" }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Temizle",
                            tint = TextWhite
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                cursorColor = PurplePrimary,
                focusedBorderColor = PurplePrimary,
                unfocusedBorderColor = BorderColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("transcript_search_input")
        )

        if (segments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = TextGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        "Bu videoda altyazı datası bulunmamaktadır.",
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("transcript_search_results"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { "${it.timestamp}_${it.text.hashCode()}" }) { seg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmicCardOuter.copy(alpha = 0.4f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "[${seg.timestamp}]",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary
                            )
                        )
                        
                        Text(
                            text = searchWord.let { word ->
                                if (word.isBlank()) {
                                    AnnotatedString(seg.text)
                                } else {
                                    buildAnnotatedString {
                                        var startIndex = 0
                                        while (true) {
                                            val index = seg.text.indexOf(word, startIndex, ignoreCase = true)
                                            if (index == -1) {
                                                append(seg.text.substring(startIndex))
                                                break
                                            }
                                            append(seg.text.substring(startIndex, index))
                                            withStyle(SpanStyle(background = PurplePrimary.copy(alpha = 0.2f), color = PurplePrimary, fontWeight = FontWeight.Bold)) {
                                                append(seg.text.substring(index, index + word.length))
                                            }
                                            startIndex = index + word.length
                                        }
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = TextGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "Yapılan aramaya uygun eşleşme bulunamadı.",
                                color = TextGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatRoomPanel(
    viewModel: VideoAnalyzerViewModel,
    isChatSending: Boolean
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    var inputChatMsgText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isChatSending) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (chatMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = PurplePrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "Video Sohbet Odası",
                        color = CupertinoTextGreenValue,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Gelişmiş Yapay Zeka video içeriğine hakimdir.\nSadece videodaki konularla ilgili sorular sorun.",
                        color = TextGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        msg = msg,
                        onSuggestionClick = { s -> viewModel.sendChatMessage(s) }
                    )
                }

                if (isChatSending) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = PurplePrimary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Gemini yanıt yazıyor...",
                                color = TextGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Send chat field
        Card(
            colors = CardDefaults.cardColors(containerColor = CosmicDarkBg2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("input_form")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputChatMsgText,
                    onValueChange = { inputChatMsgText = it },
                    placeholder = { Text("Video hakkında her şeyi sorun...", color = TextGray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputChatMsgText.isNotBlank()) {
                                viewModel.sendChatMessage(inputChatMsgText)
                                inputChatMsgText = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            val clipboardManager = LocalClipboardManager.current
                            Text(
                                text = "Yapıştır",
                                color = PurplePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PurplePrimary.copy(alpha = 0.12f))
                                    .clickable {
                                        clipboardManager.getText()?.text?.let { text ->
                                            inputChatMsgText = text
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            if (inputChatMsgText.isNotEmpty()) {
                                IconButton(onClick = { inputChatMsgText = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Temizle",
                                        tint = TextWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = PurplePrimary,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text")
                )

                IconButton(
                    onClick = {
                        if (inputChatMsgText.isNotBlank()) {
                            viewModel.sendChatMessage(inputChatMsgText)
                            inputChatMsgText = ""
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PurplePrimary)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Gönder",
                        tint = CosmicDarkBg2,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    msg: ChatMessage,
    onSuggestionClick: (String) -> Unit
) {
    val isUser = msg.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) UserBubbleBg else AiBubbleBg
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isUser) 16.dp else 4.dp, 
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (isUser) PurplePrimary.copy(alpha = 0.3f) else BorderColor.copy(alpha = 0.4f)
            )
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        text = msg.text,
                        color = TextWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(text = msg.text)
                }
            }
        }

        // Copy button row underneath the message bubble
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val clipboardManager = LocalClipboardManager.current
            var showCopiedIndicator by remember { mutableStateOf(false) }

            LaunchedEffect(showCopiedIndicator) {
                if (showCopiedIndicator) {
                    kotlinx.coroutines.delay(2000)
                    showCopiedIndicator = false
                }
            }

            Text(
                text = if (showCopiedIndicator) "✓ Kopyalandı!" else "Kopyala",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (showCopiedIndicator) GreenActive else TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                        showCopiedIndicator = true
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Render dynamic suggest chip queries if AI response returned suggestions
        if (!isUser && msg.suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (s in msg.suggestions) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PurplePrimary.copy(alpha = 0.08f))
                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                            .clickable { onSuggestionClick(s) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = PurplePrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = s,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PurplePrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(PurplePrimary.copy(alpha = 0.08f))
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = PurplePrimary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = PurplePrimary,
                fontSize = 11.sp
            )
        )
    }
}

// ═══════════════════════════════════════════
// LIGHTWEIGHT MARKDOWN ENGINE IN COMPOSE
// ═══════════════════════════════════════════

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.substring(3).trim(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.substring(2).trim(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimary
                        ),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", color = PurplePrimary, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatAnnotatedText(trimmed.substring(2).trim()),
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextWhite),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatAnnotatedText(trimmed),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextWhite,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun formatAnnotatedText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        var lastIdx = 0
        val matches = boldRegex.findAll(text)
        for (m in matches) {
            val startIdx = m.range.first
            val endIdx = m.range.last + 1
            if (startIdx > lastIdx) {
                append(text.substring(lastIdx, startIdx))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PurplePrimary)) {
                append(m.groupValues[1])
            }
            lastIdx = endIdx
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

@Composable
fun SunIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        // Draw sun circle
        drawCircle(color = tint, radius = size.minDimension / 4.2f)
        // Draw rays of the sun
        val rayCount = 8
        val center = center
        val innerRadius = size.minDimension / 3.4f
        val outerRadius = size.minDimension / 2f
        val strokeWidth = 2.dp.toPx()
        for (i in 0 until rayCount) {
            val angle = i * (2 * Math.PI / rayCount)
            val startX = center.x + innerRadius * Math.cos(angle).toFloat()
            val startY = center.y + innerRadius * Math.sin(angle).toFloat()
            val endX = center.x + outerRadius * Math.cos(angle).toFloat()
            val endY = center.y + outerRadius * Math.sin(angle).toFloat()
            drawLine(
                color = tint,
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun MoonIcon(modifier: Modifier = Modifier, tint: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.75f, height * 0.15f)
            cubicTo(
                width * 0.4f, height * 0.15f,
                width * 0.15f, height * 0.4f,
                width * 0.15f, height * 0.75f
            )
            cubicTo(
                width * 0.15f, height * 0.9f,
                width * 0.22f, height * 0.95f,
                width * 0.33f, height * 0.98f
            )
            cubicTo(
                width * 0.15f, height * 0.75f,
                width * 0.38f, height * 0.35f,
                width * 0.75f, height * 0.35f
            )
            cubicTo(
                width * 0.85f, height * 0.35f,
                width * 0.92f, height * 0.42f,
                width * 0.95f, height * 0.48f
            )
            cubicTo(
                width * 0.82f, height * 0.22f,
                width * 0.8f, height * 0.15f,
                width * 0.75f, height * 0.15f
            )
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun ThemeTransitionOverlay(
    progress: Float,
    isToDark: Boolean
) {
    // Intercept all touches while animation is running
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true, onClickLabel = null, role = null, onClick = {})
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            
            val screenWidthPx = with(density) { screenWidth.toPx() }
            val screenHeightPx = with(density) { screenHeight.toPx() }
            
            val sunXPx = screenWidthPx / 2f
            
            // Sunset (To Dark): sun starts off-screen top and goes off-screen bottom
            // Sunrise (To Light): sun starts off-screen bottom and rises to upper resting position
            val sunYPx = if (isToDark) {
                val startY = -120.dp.value * density.density
                val endY = screenHeightPx + 150.dp.value * density.density
                startY + (endY - startY) * progress
            } else {
                val startY = screenHeightPx + 120.dp.value * density.density
                val endY = 120.dp.value * density.density
                startY + (endY - startY) * progress
            }
            
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val sunRadius = 45.dp.toPx()
                val glowRadius = 240.dp.toPx()
                
                if (isToDark) {
                    // Light -> Dark
                    // Background dims progressively. At progress >= 0.90f, it snaps pitch dark.
                    val darkAlpha = if (progress < 0.90f) {
                        progress * 0.90f
                    } else {
                        1.0f
                    }
                    
                    drawRect(color = Color.Black.copy(alpha = darkAlpha))
                    
                    // Radial glow illuminating surroundings
                    if (progress < 0.90f) {
                        val glowAlpha = (1f - progress) * 0.8f + 0.2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFEE58).copy(alpha = 0.65f * glowAlpha),
                                    Color(0xFFFFA726).copy(alpha = 0.35f * glowAlpha),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx),
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                        
                        // Sun orb
                        drawCircle(
                            color = Color(0xFFFFCA28),
                            radius = sunRadius,
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = sunRadius * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                    }
                } else {
                    // Dark -> Light
                    // Background brightens up
                    val lightAlpha = if (progress < 0.90f) {
                        progress * 0.88f
                    } else {
                        1.0f
                    }
                    
                    drawRect(color = Color(0xFFFDF8FD).copy(alpha = lightAlpha))
                    
                    // Sun rise glow and orb
                    if (progress < 1.0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFEE58).copy(alpha = 0.7f),
                                    Color(0xFFFFA726).copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx),
                                radius = glowRadius * (0.6f + 0.4f * progress)
                            ),
                            radius = glowRadius * (0.6f + 0.4f * progress),
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                        
                        drawCircle(
                            color = Color(0xFFFFCA28),
                            radius = sunRadius,
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = sunRadius * 0.55f,
                            center = androidx.compose.ui.geometry.Offset(sunXPx, sunYPx)
                        )
                    }
                }
            }
        }
    }
}
