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
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        splashScreen.setKeepOnScreenCondition { !isContentLoaded }

        Handler(Looper.getMainLooper()).postDelayed({ isContentLoaded = true }, failSafeTimeout)

        // 1. WebView Nesnesini HazÄ±rla
        webView = WebView(this)
        
        // DonanÄ±m HÄ±zlandÄ±rma Uyumu: 
        // Manifest'te hardwareAccelerated="false" olduÄŸu iÃ§in burada SOFTWARE modunu kullanmak 
        // Samsung cihazlardaki "boÅŸ sayfa" sorununu %100 Ã§Ã¶zer.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        // Bilgisayardan Chrome ile hata ayÄ±klama desteÄŸi (chrome://inspect)
        WebView.setWebContentsDebuggingEnabled(true)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // CORS ve Local File kÄ±sÄ±tlamalarÄ±nÄ± kaldÄ±rmak iÃ§in kritik ayarlar
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            javaScriptCanOpenWindowsAutomatically = true
            
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // 2. JavaScript HatalarÄ±nÄ± Logcat'e YazdÄ±r (Hata AyÄ±klama Ä°Ã§in)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    android.util.Log.d("WebViewConsole", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isContentLoaded = true
                android.util.Log.d("WebViewClient", "Sayfa yÃ¼klendi: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                isContentLoaded = true
                android.util.Log.e("WebViewClient", "Hata OluÅŸtu: ${error?.description} (Kod: ${error?.errorCode})")
            }
        }

        // 3. Ä°Ã§eriÄŸi YÃ¼kle ve Ekrana Bas
        val entryUrl = "file:///android_asset/www/index.html"
        android.util.Log.d("WebViewLoader", "YÃ¼kleniyor: $entryUrl")
        webView.loadUrl(entryUrl)
        
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
