package com.example.waspbrowser

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class BeeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BeeActivity"
        private const val PREFS_MINING = "bee_mining"
        private const val KEY_MINING_ACTIVE = "mining_active"
    }

    private lateinit var beeWebView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageLoaded = false
    private var isReceiverRegistered = false

    // Keep-Alive: acorda o WebView a cada tick do BeeBackgroundService
    private val keepAliveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BeeBackgroundService.ACTION_KEEP_ALIVE) return
            val remaining = intent.getLongExtra("remaining_ms", 0L)
            Log.d(TAG, "Keep-alive recebido | restam ${remaining / 1000}s")
            evaluateJs("if(window.onAppResume) window.onAppResume();")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_bee)

        beeWebView = findViewById(R.id.beeWebView)
        window.decorView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.alpha = 0f

        configureWebView()
        attachBridge()
        attachClients()
        loadBeePanel()
        registerKeepAlive()
    }

    private fun registerKeepAlive() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(BeeBackgroundService.ACTION_KEEP_ALIVE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keepAliveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(keepAliveReceiver, filter)
        }
        isReceiverRegistered = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(beeWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }
        beeWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    }

    private fun loadBeePanel() {
        beeWebView.loadUrl("file:///android_asset/bee/index.html")
    }

    private fun attachBridge() {
        beeWebView.addJavascriptInterface(BeeBridge(), "AndroidBee")
    }

    private fun attachClients() {
        beeWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                v: WebView?,
                r: WebResourceRequest?
            ): WebResourceResponse? {
                val url = r?.url?.toString() ?: return null
                if (url.endsWith(".wasm")) {
                    return try {
                        WebResourceResponse(
                            "application/wasm", "binary", 200, "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            assets.open("bee/bee_sdk_bg.wasm")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "WASM intercept erro: ${e.message}")
                        null
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!pageLoaded) {
                    pageLoaded = true
                    beeWebView.animate().alpha(1f).setDuration(300).start()
                }
            }
        }
    }

    inner class BeeBridge {
        @JavascriptInterface
        fun toast(m: String) {
            mainHandler.post { Toast.makeText(this@BeeActivity, m, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun openDeepLink(url: String) {
            mainHandler.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Erro deep link: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
        }

        @JavascriptInterface
        fun hasWasm(): Boolean = try {
            assets.open("bee/bee_sdk_bg.wasm").close(); true
        } catch (e: Exception) { false }

        @JavascriptInterface
        fun checkAssets(): String = try {
            assets.list("bee")?.joinToString(", ") ?: "vazio"
        } catch (e: Exception) { "erro: ${e.message}" }

        @JavascriptInterface
        fun openCentral() {
            mainHandler.post {
                val intent = Intent(this@BeeActivity, CentralActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        beeWebView.onResume()
        beeWebView.resumeTimers()
        evaluateJs("if(window.onAppResume) window.onAppResume()")
    }

    override fun onPause() {
        super.onPause()
        // NÃO chamamos beeWebView.onPause() para manter o miner JS rodando em background
        evaluateJs("if(window.onAppPause) window.onAppPause()")
    }

    private fun evaluateJs(js: String) {
        beeWebView.post { beeWebView.evaluateJavascript(js, null) }
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try { unregisterReceiver(keepAliveReceiver) } catch (e: Exception) {}
        }
        if (!BeeBackgroundService.isActive(this)) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, false).apply()
        }
        beeWebView.destroy()
        super.onDestroy()
    }
}
