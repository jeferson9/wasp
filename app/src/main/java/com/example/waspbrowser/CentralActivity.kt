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
    private val handler = Handler(Looper.getMainLooper())

    private var rewardedAd: RewardedAd? = null
    private var adLoading   = false
    private var wpRewarded  = false

    // ── Lifecycle ────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        webView.setBackgroundColor(0xFF08090D.toInt())

        // Layout com WebView + rodapé Bee
        val rootLayout = android.widget.RelativeLayout(this)
        rootLayout.setBackgroundColor(0xFF08090D.toInt())

        val webParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply { addRule(android.widget.RelativeLayout.ABOVE, R.id.central_bee_footer) }
        rootLayout.addView(webView, webParams)

        // Rodapé Bee — mostra a WebView persistente da MainActivity
        val beeFooter = android.widget.FrameLayout(this).apply { id = R.id.central_bee_footer }
        val footerParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt()
        ).apply { addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM) }
        rootLayout.addView(beeFooter, footerParams)

        setContentView(rootLayout)

        // Injeta a WebView persistente do Bee no rodapé
        handler.postDelayed({
            val mainActivity = BeeActivity.instance?.get()?.let { null }
            val persistentWv = BeeActivity.persistentWebView?.get()
            if (persistentWv != null) {
                try {
                    (persistentWv.parent as? android.view.ViewGroup)?.removeView(persistentWv)
                } catch (_: Exception) {}
                beeFooter.addView(persistentWv, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
                android.util.Log.d(TAG, "Bee footer adicionado na CentralActivity")
            }
        }, 300)

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

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        // Reanexa Bee footer se necessário
        handler.postDelayed({
            val footer = findViewById<android.widget.FrameLayout>(R.id.central_bee_footer) ?: return@postDelayed
            if (footer.childCount == 0) {
                val pWv = BeeActivity.persistentWebView?.get() ?: return@postDelayed
                try { (pWv.parent as? android.view.ViewGroup)?.removeView(pWv) } catch (_: Exception) {}
                footer.addView(pWv, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }
        }, 200)
    }

    override fun onPause() { super.onPause(); webView.onPause(); webView.pauseTimers() }

    override fun onDestroy() {
        // Devolve a WebView persistente para a MainActivity antes de destruir
        val footer = findViewById<android.widget.FrameLayout?>(R.id.central_bee_footer)
        val pWv = BeeActivity.persistentWebView?.get()
        if (footer != null && pWv != null) {
            try { footer.removeView(pWv) } catch (_: Exception) {}
        }
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }

    // ── Ad ───────────────────────────────────────────────────────────────

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
