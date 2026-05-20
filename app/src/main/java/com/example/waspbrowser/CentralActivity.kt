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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class CentralActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CentralActivity"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    // ── API moderna de resultado (substitui startActivityForResult) ──────
    // DEVE ser registrado antes de onCreate terminar — por isso é um campo
    private val adLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val data        = result.data
        val rewarded    = data?.getBooleanExtra(AdActivity.EXTRA_REWARDED, false) ?: false
        val unavailable = data?.getBooleanExtra("unavailable", false) ?: false

        android.util.Log.d(TAG, "adLauncher result — rewarded=$rewarded unavailable=$unavailable")

        val jsFunc = when {
            rewarded    -> "onWpAdRewarded"
            unavailable -> "onWpAdUnavailable"
            else        -> "onWpAdClosed"
        }

        // WebView nunca pausou — JS executa direto
        handler.postDelayed({
            webView.evaluateJavascript(
                "if(typeof window.$jsFunc==='function'){ window.$jsFunc(); }",
                null
            )
            android.util.Log.d(TAG, "JS entregue: $jsFunc")
        }, 150)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        webView.setBackgroundColor(0xFF08090D.toInt())
        setContentView(webView)

        configureWebView()

        val bridge = CentralBridge()
        webView.addJavascriptInterface(bridge, "AndroidBee")
        webView.addJavascriptInterface(bridge, "Android")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (!url.startsWith("file://")) {
                    openExternalUrl(url)
                    return true
                }
                return false
            }
        }

        webView.loadUrl("file:///android_asset/bee/central.html")
    }

    // ── WebView helpers ──────────────────────────────────────────────────
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            allowFileAccess      = true
            allowContentAccess   = true
            cacheMode            = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Nao foi possivel abrir URL externa: $url")
        }
    }

    override fun onResume()  { super.onResume();  webView.onResume();  webView.resumeTimers() }
    override fun onPause()   { super.onPause();   webView.onPause();   webView.pauseTimers()  }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }

    // ── Bridge JS → Kotlin ───────────────────────────────────────────────
    inner class CentralBridge {

        @JavascriptInterface
        fun openWpAd() {
            android.util.Log.d(TAG, "openWpAd chamado pelo JS")
            handler.post {
                val intent = Intent(this@CentralActivity, AdActivity::class.java)
                adLauncher.launch(intent)
                android.util.Log.d(TAG, "adLauncher.launch() disparado")
            }
        }

        @JavascriptInterface
        fun closeCentral() {
            handler.post {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
                finish()
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
            android.util.Log.d(TAG, "startBgMining: ${durationMs / 60000}min wallet=$walletName")
            try {
                val intent = BeeBackgroundService.buildStartIntent(this@CentralActivity, walletName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                handler.post {
                    Toast.makeText(this@CentralActivity, "🐝 Minerando em background!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "startBgMining error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try { startService(BeeBackgroundService.buildStopIntent(this@CentralActivity)) }
            catch (e: Exception) { android.util.Log.e(TAG, "stopBgMining error: ${e.message}") }
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
