package com.example.waspbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class CentralActivity : AppCompatActivity() {

    companion object { private const val TAG = "CentralActivity" }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_central)

        val contentFrame = findViewById<android.widget.FrameLayout>(R.id.central_content)
        webView = WebView(this)
        webView.setBackgroundColor(0xFF08090D.toInt())
        contentFrame.addView(webView, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))

        with(webView.settings) {
            javaScriptEnabled  = true
            domStorageEnabled  = true
            allowFileAccess    = true
            allowContentAccess = true
            cacheMode          = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!url.startsWith("file://")) { openExternalUrl(url); return true }
                return false
            }
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBee")
        webView.addJavascriptInterface(Bridge(), "Android")
        webView.loadUrl("file:///android_asset/bee/central.html")

        // Garante que o AdManager está pronto com referência à MainActivity
        // A MainActivity já chama AdManager.init(this) no onCreate dela
    }

    override fun onResume()  { super.onResume();  webView.onResume();  webView.resumeTimers() }
    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); webView.destroy(); super.onDestroy() }

    private fun js(fn: String) {
        // Entrega o callback na persistentBeeView via AdManager/BeeActivity.runJs
        BeeActivity.runJs("if(typeof window.$fn==='function'){window.$fn();}")
        android.util.Log.d(TAG, "JS → $fn")
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) { android.util.Log.e(TAG, "openExternal: ${e.message}") }
    }

    inner class Bridge {

        @JavascriptInterface
        fun closeCentral() {
            handler.post {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
            }
        }

        @JavascriptInterface
        fun toast(m: String) {
            handler.post { Toast.makeText(this@CentralActivity, m, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun log(m: String) { android.util.Log.d("CentralJS", m) }

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) {
            try {
                val intent = BeeBackgroundService.buildStartIntent(this@CentralActivity, walletName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    startForegroundService(intent)
                else startService(intent)
            } catch (e: Exception) { android.util.Log.e(TAG, "startBgMining: ${e.message}") }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try { startService(BeeBackgroundService.buildStopIntent(this@CentralActivity)) }
            catch (e: Exception) { android.util.Log.e(TAG, "stopBgMining: ${e.message}") }
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active    = BeeBackgroundService.isActive(this@CentralActivity)
            val remaining = BeeBackgroundService.remainingMs(this@CentralActivity)
            val prefs     = getSharedPreferences(BeeBackgroundService.PREFS_BG, MODE_PRIVATE)
            val cycles    = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
            val wallet    = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
        }
    }
}
