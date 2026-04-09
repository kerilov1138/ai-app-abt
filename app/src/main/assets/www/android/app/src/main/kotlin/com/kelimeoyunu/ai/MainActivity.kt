package com.kelimeoyunu.ai

import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var isContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ Splash Screen API entegrasyonu
        val splashScreen = installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Splash screen'in ne kadar süre kalacağını kontrol eder
        // İçerik hazır olana kadar splash screen'i tutar
        splashScreen.setKeepOnScreenCondition { !isContentReady }

        webView = WebView(this)
        
        // WebView Ayarları - Modern bellek yönetimi ve performans için
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        
        // Donanım hızlandırma ayarları - Ashmem hatalarını önlemek için
        // Android Q+ için modern çizim yöntemlerini kullanmaya zorlar
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Sayfa yüklendiğinde splash screen'i kaldır
                isContentReady = true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Hata durumunda da splash screen'de takılı kalmamak için içeriği "hazır" işaretle
                isContentReady = true
            }
        }

        setContentView(webView)
        
        // Uygulama URL'sini yükle
        webView.loadUrl("https://ais-dev-5w2s42u6inuhtstn2exau4-26636727861.europe-west2.run.app")
        
        // Güvenlik önlemi: Eğer 5 saniye içinde sayfa yüklenmezse splash screen'i zorla kapat
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
