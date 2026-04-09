package com.kelime_oyunu

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isContentLoaded = false
    private val failSafeTimeout = 5000L // 5 seconds fail-safe

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Android 12+ Splash Screen Entegrasyonu
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Uygulama iÃ§eriÄŸi yÃ¼klenene kadar Splash Screen'i ekranda tut
        splashScreen.setKeepOnScreenCondition {
            !isContentLoaded
        }

        // GÃ¼venlik ZamanlayÄ±cÄ±sÄ±: EÄŸer yÃ¼kleme gecikirse Splash'i zorla kapat
        Handler(Looper.getMainLooper()).postDelayed({
            isContentLoaded = true
        }, failSafeTimeout)

        setContentView(View(this)) // GeÃ§ici boÅŸ view

        // 2. Modern WebView YapÄ±landÄ±rmasÄ±
        webView = WebView(this)
        
        // Bellek YÃ¶netimi (Ashmem Fix): DonanÄ±m hÄ±zlandÄ±rmayÄ± WebView seviyesinde zorunlu kÄ±l
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // YÃ¼kleme bittiÄŸinde Splash Screen'i kapat
                isContentLoaded = true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Hata durumunda da Splash'i kapat ki kullanÄ±cÄ± etkileÅŸime geÃ§ebilsin
                isContentLoaded = true
            }
        }

        // Web iÃ§eriÄŸini yÃ¼kle (assets/www/index.html varsayÄ±lan)
        webView.loadUrl("file:///android_asset/www/index.html")
        
        setContentView(webView)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
