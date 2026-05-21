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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class CentralActivity : AppCompatActivity() {

    companion object {
        private const val TAG      = "CentralActivity"
        private const val AD_UNIT  = "ca-app-pub-3940256099942544/5224354917"
    }

    private lateinit var webView: WebView
    private var beeView: android.webkit.WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var rewardedAd: RewardedAd? = null
    private var adLoading   = false
    private var wpRewarded  = false

    // ── Lifecycle ────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_central)

        // WebView da Central no container do layout
        val contentFrame = findViewById<android.widget.FrameLayout>(R.id.central_content)
        webView = WebView(this)
        webView.setBackgroundColor(0xFF08090D.toInt())
        contentFrame.addView(webView, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Rodapé Bee — mantém mineração visível na Central também
        setupBeeFooter()

        with(webView.settings) {
            javaScriptEnabled   = true
            domStorageEnabled   = true
            allowFileAccess     = true
            allowContentAccess  = true
            cacheMode           = WebSettings.LOAD_DEFAULT
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

        MobileAds.initialize(this) { loadAd() }
    }

    override fun onResume()  {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        beeView?.onResume()
        beeView?.resumeTimers()
    }
    override fun onPause()   {
        super.onPause()
        webView.onPause()
        // NÃO pausa beeView — mineração precisa continuar
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        // Se beeView for a persistente da MainActivity, devolve ela para lá
        val persistent = BeeActivity.getPersistentWebView()
        if (beeView != null && beeView === persistent) {
            (beeView!!.parent as? android.view.ViewGroup)?.removeView(beeView)
            // MainActivity vai readicioná-la no próximo onResume via setupPersistentBee
        }
        super.onDestroy()
    }

    // ── Bee Footer ───────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBeeFooter() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: return

        // Usa a persistentWebView da MainActivity se disponível
        val persistent = BeeActivity.getPersistentWebView()
        if (persistent != null) {
            // Remove do pai atual e adiciona aqui temporariamente
            (persistent.parent as? android.view.ViewGroup)?.removeView(persistent)
            container.addView(persistent, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
            beeView = persistent
        } else {
            // Cria uma WebView própria para o rodapé
            val wv = android.webkit.WebView(this)
            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            wv.setBackgroundColor(0xFF0B0B0D.toInt())
            val bridge = BeeActivity.createBridge(this, wv)
            wv.addJavascriptInterface(bridge, "AndroidBee")
            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.endsWith(".wasm")) {
                        return try {
                            android.webkit.WebResourceResponse(
                                "application/wasm", "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"),
                                assets.open("bee/bee_sdk_bg.wasm")
                            )
                        } catch (e: Exception) { null }
                    }
                    return null
                }
            }
            wv.loadUrl("file:///android_asset/bee/index.html")
            container.addView(wv, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
            beeView = wv
        }

        // Mostra o container no rodapé (56dp = altura mínima do painel)
        val dp56 = (56 * resources.displayMetrics.density).toInt()
        container.layoutParams.height = dp56
        container.visibility = android.view.View.VISIBLE
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    private fun loadAd() {
        if (adLoading) return
        adLoading = true
        android.util.Log.d(TAG, "loadAd")
        RewardedAd.load(this, AD_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    android.util.Log.d(TAG, "Ad carregado ✅")
                    rewardedAd = ad
                    adLoading  = false
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    android.util.Log.e(TAG, "Ad falhou ao carregar: ${e.message}")
                    rewardedAd = null
                    adLoading  = false
                    // Tenta de novo em 30s
                    handler.postDelayed({ loadAd() }, 30_000)
                }
            }
        )
    }

    private fun showAd() {
        val ad = rewardedAd
        if (ad == null) {
            android.util.Log.w(TAG, "Ad não disponível")
            js("onWpAdUnavailable")
            loadAd()
            return
        }

        wpRewarded = false
        rewardedAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.d(TAG, "Ad fechado — rewarded=$wpRewarded")
                loadAd()
                // Entrega callback com retry
                handler.postDelayed({ retryJs(0) }, 300)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                android.util.Log.e(TAG, "Ad falhou ao mostrar: ${e.message}")
                loadAd()
                js("onWpAdUnavailable")
            }
        }

        ad.show(this) {
            wpRewarded = true
            android.util.Log.e(TAG, "✅ RECOMPENSA CONFIRMADA")
        }
    }

    private fun retryJs(count: Int) {
        val fn = if (wpRewarded) "onWpAdRewarded" else "onWpAdClosed"
        js(fn)
        if (count < 3) handler.postDelayed({ retryJs(count + 1) }, 800)
    }

    private fun js(fn: String) {
        webView.post {
            webView.evaluateJavascript("if(typeof window.$fn==='function'){window.$fn();}", null)
            android.util.Log.d(TAG, "JS → $fn")
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) { android.util.Log.e(TAG, "openExternal: ${e.message}") }
    }

    // ── Bridge JS → Kotlin ───────────────────────────────────────────────

    inner class Bridge {

        @JavascriptInterface
        fun openWpAd() {
            android.util.Log.d(TAG, "openWpAd()")
            handler.post { showAd() }
        }

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
                handler.post { Toast.makeText(this@CentralActivity, "🐝 Minerando!", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { android.util.Log.e(TAG, "startBgMining: ${e.message}") }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try { startService(BeeBackgroundService.buildStopIntent(this@CentralActivity)) }
            catch (e: Exception) { android.util.Log.e(TAG, "stopBgMining: ${e.message}") }
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active  = BeeBackgroundService.isActive(this@CentralActivity)
            val remaining = BeeBackgroundService.remainingMs(this@CentralActivity)
            val prefs   = getSharedPreferences(BeeBackgroundService.PREFS_BG, MODE_PRIVATE)
            val cycles  = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
            val wallet  = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
        }
    }
}
