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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.RequestConfiguration

class CentralActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CentralActivity"
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var isAdShowing = false
    private var wpRewardEarned = false
    private var adPendingShow = false

    // Controla se a WebView já carregou o HTML ao menos uma vez
    private var webViewReady = false

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

        val testDeviceIds = listOf("C2BDD20251E0A65AA97DD561F37883A1")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)

        MobileAds.initialize(this) {
            android.util.Log.d(TAG, "AdMob Inicializado")
            loadRewardedAd()
        }

        // WebViewClient que notifica quando o HTML terminou de carregar
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                webViewReady = false
                android.util.Log.d(TAG, "WebView recarregando: $url")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webViewReady = true
                android.util.Log.d(TAG, "WebView pronta: $url")
                // Entrega callback pendente (pode ter chegado antes ou durante reload)
                if (pendingRewardCallback != null) {
                    val cb = pendingRewardCallback!!
                    pendingRewardCallback = null
                    handler.postDelayed({ deliverReward(cb) }, 500)
                }
            }
        }

        webView.loadUrl("file:///android_asset/bee/central.html")
    }

    // Callback pendente caso WebView não estivesse pronta quando o ad fechou
    private var pendingRewardCallback: String? = null

    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            // LOAD_DEFAULT preserva o estado JS — não recarrega ao voltar do ad
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        // NÃO chama clearCache aqui — isso destruiria o JS durante o ad
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadRewardedAd() {
        if (rewardedAd != null) return

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    android.util.Log.d(TAG, "AdMob: Anúncio carregado.")
                    if (adPendingShow) {
                        adPendingShow = false
                        showRewardedAd()
                    }
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    rewardedAd = null
                    android.util.Log.e(TAG, "AdMob falhou: ${e.message}")
                    if (adPendingShow) {
                        adPendingShow = false
                        deliverReward("onWpAdUnavailable")
                    }
                }
            }
        )
    }

    private fun showRewardedAd() {
        if (isAdShowing) return

        val ad = rewardedAd
        if (ad == null) {
            adPendingShow = true
            deliverReward("onWpAdLoading")
            loadRewardedAd()
            handler.postDelayed({
                if (adPendingShow && rewardedAd == null) {
                    adPendingShow = false
                    deliverReward("onWpAdUnavailable")
                }
            }, 15000)
            return
        }

        wpRewardEarned = false
        isAdShowing = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.d(TAG, "Ad fechado. Recompensa: $wpRewardEarned")
                isAdShowing = false
                rewardedAd = null
                loadRewardedAd()

                val callback = if (wpRewardEarned) "onWpAdRewarded" else "onWpAdClosed"
                // Guarda como pendente — se a pagina recarregar, onPageFinished entrega
                pendingRewardCallback = callback
                // Tenta entregar apos 1200ms (tempo para voltar ao foreground)
                handler.postDelayed({
                    if (pendingRewardCallback != null) {
                        val cb = pendingRewardCallback!!
                        pendingRewardCallback = null
                        deliverReward(cb)
                    }
                }, 1200)
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                isAdShowing = false
                rewardedAd = null
                loadRewardedAd()
                deliverReward("onWpAdUnavailable")
            }
        }

        ad.show(this) { _: RewardItem ->
            wpRewardEarned = true
            android.util.Log.d(TAG, "Recompensa confirmada!")
            handler.post { Toast.makeText(this, "WP Recebido! 🐝", Toast.LENGTH_SHORT).show() }
        }
    }

    // Entrega o callback JS uma única vez, com retry se a WebView demorar
    private fun deliverReward(jsFunc: String, attempt: Int = 0) {
        val script = "if(typeof window.$jsFunc==='function'){window.$jsFunc();}"
        webView.post {
            webView.evaluateJavascript(script) { result ->
                android.util.Log.d(TAG, "deliverReward: $jsFunc tentativa=$attempt result=$result")
                // Se funcao nao foi encontrada e ainda tem tentativas, tenta de novo
                if ((result == null || result == "null" || result == "false") && attempt < 4) {
                    handler.postDelayed({ deliverReward(jsFunc, attempt + 1) }, 600)
                }
            }
        }
    }

    inner class CentralBridge {
        @JavascriptInterface
        fun openWpAd() { handler.post { showRewardedAd() } }

        @JavascriptInterface
        fun closeCentral() {
            handler.post {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
                finish()
            }
        }

        @JavascriptInterface
        fun toast(m: String) { handler.post { Toast.makeText(this@CentralActivity, m, Toast.LENGTH_SHORT).show() } }

        @JavascriptInterface
        fun log(m: String) { android.util.Log.d("CentralJS", m) }

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) {
            android.util.Log.d(TAG, "startBgMining: ${durationMs/60000}min wallet=$walletName")
            try {
                val intent = BeeBackgroundService.buildStartIntent(this@CentralActivity, walletName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                handler.post { Toast.makeText(this@CentralActivity, "🐝 Minerando em background!", Toast.LENGTH_SHORT).show() }
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
            val active = BeeBackgroundService.isActive(this@CentralActivity)
            val remaining = BeeBackgroundService.remainingMs(this@CentralActivity)
            val prefs = getSharedPreferences(BeeBackgroundService.PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
            val wallet = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
        finish()
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
