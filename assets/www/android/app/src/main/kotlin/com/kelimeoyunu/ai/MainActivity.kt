package com.kelimeoyunu.ai

import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var isContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ Splash Screen API entegrasyonu
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Splash screen'in ne kadar süre kalacağını kontrol eder
        splashScreen.setKeepOnScreenCondition { !isContentReady }

        webView = WebView(this)
        
        // WebViewAssetLoader Yapılandırması
        // Yerel dosyaları güvenli bir https:// domaini üzerinden yükler.
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // WebView Ayarları
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        
        // Güvenlik Ayarları
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
        
        // Donanım hızlandırma - Performans için tekrar aktif edildi
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isContentReady = true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                isContentReady = true
            }
        }

        setContentView(webView)
        
        // Uygulamayı yerel assets üzerinden yükle (Production Modu)
        webView.loadUrl("https://appassets.androidplatform.net/index.html")
        
        // Güvenlik önlemi: Splash screen zaman aşımı
        webView.postDelayed({
            isContentReady = true
        }, 5000)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
