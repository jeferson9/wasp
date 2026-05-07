package com.example.waspbrowser

import android.annotation.SuppressLint
import android.content.Intent
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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.core.view.WindowCompat

class BeeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BeeActivity"
        private const val REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val PREFS_BEE_ENERGY = "bee_energy"
        private const val PREFS_MINING      = "bee_mining"
        private const val KEY_MINING_ACTIVE  = "mining_active"
        private const val KEY_ENERGY_READY = "energy_ready"
    }

    private lateinit var beeWebView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var energyRewardGranted = false
    private var wpRewardGranted = false
    private var adMode: String? = null
    private var pageLoaded = false
    private var isAdShowing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_bee)

        beeWebView = findViewById(R.id.beeWebView)
        window.decorView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.alpha = 0f

        MobileAds.initialize(this) {}
        configureWebView()
        attachBridge()
        attachClients()
        loadRewardedAd()
        loadBeePanel()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        returnToMain("home")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(beeWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }
        beeWebView.isHapticFeedbackEnabled = false
        beeWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    }

    private fun loadBeePanel() {
        pageLoaded = false
        beeWebView.loadUrl("file:///android_asset/bee/index.html")
    }

    private fun attachBridge() {
        beeWebView.addJavascriptInterface(BeeBridge(), "AndroidBee")
    }

    private fun attachClients() {
        beeWebView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.endsWith(".wasm")) {
                    return try {
                        WebResourceResponse(
                            "application/wasm",
                            "binary",
                            200,
                            "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            assets.open("bee/bee_sdk_bg.wasm")
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao servir WASM: ${e.message}")
                        null
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (pageLoaded) return
                pageLoaded = true
                beeWebView.animate().alpha(1f).setDuration(200).start()
            }
        }
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) return
        Log.d(TAG, "openExternal: $url")
        try {
            val intent = if (url.startsWith("intent://")) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!url.startsWith("intent://")) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir link: $url — ${e.message}")
            Toast.makeText(this, "Não foi possível abrir este link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadRewardedAd() {
        RewardedAd.load(
            this,
            REWARDED_TEST_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { rewardedAd = null }
            }
        )
    }

    private fun showRewardedAd(mode: String) {
        if (isAdShowing) return
        val ad = rewardedAd ?: run { loadRewardedAd(); return }
        adMode = mode
        isAdShowing = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                isAdShowing = false
                loadRewardedAd()
                when {
                    adMode == "energy" && energyRewardGranted ->
                        evaluateJs("if(window.onEnergyRewarded) window.onEnergyRewarded()")
                    adMode == "energy" ->
                        evaluateJs("if(window.onEnergyAdClosed) window.onEnergyAdClosed()")
                    adMode == "wp" && wpRewardGranted ->
                        evaluateJs("if(window.onWpAdRewarded) window.onWpAdRewarded()")
                    adMode == "wp" ->
                        evaluateJs("if(window.onWpAdClosed) window.onWpAdClosed()")
                }
                adMode = null
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                isAdShowing = false
                loadRewardedAd()
                adMode = null
            }
        }

        ad.show(this) { _: RewardItem ->
            if (mode == "energy") energyRewardGranted = true
            else wpRewardGranted = true
        }
    }

    private fun returnToMain(screen: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("navigate_to", screen)
        }
        val opts = android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
        startActivity(intent, opts.toBundle())
    }

    inner class BeeBridge {

        @JavascriptInterface
        fun ping(): String = "pong"

        @JavascriptInterface
        fun toast(m: String) {
            mainHandler.post { Toast.makeText(this@BeeActivity, m, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun openDeepLink(url: String) {
            Log.d(TAG, "Bridge openDeepLink: ${url.take(100)}")
            mainHandler.post { openExternal(url) }
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            Log.d(TAG, "Bridge openExternalUrl: ${url.take(100)}")
            mainHandler.post { openExternal(url) }
        }

        @JavascriptInterface
        fun openEnergyPage() {
            mainHandler.post { showRewardedAd("energy") }
        }

        @JavascriptInterface
        fun openWpAd() {
            mainHandler.post { showRewardedAd("wp") }
        }

        @JavascriptInterface
        fun isEnergyReady(): Boolean {
            return getSharedPreferences(PREFS_BEE_ENERGY, MODE_PRIVATE)
                .getBoolean(KEY_ENERGY_READY, false)
        }

        @JavascriptInterface
        fun clearEnergyReady() {
            getSharedPreferences(PREFS_BEE_ENERGY, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENERGY_READY, false).apply()
        }

        @JavascriptInterface
        fun navigateTo(screen: String) {
            mainHandler.post { returnToMain(screen) }
        }

        @JavascriptInterface
        fun goBack() {
            mainHandler.post { returnToMain("home") }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.d("BeeBridgeJS", msg)
        }

        // CORREÇÃO: Adicionado método esperado pelo bee_engine.js
        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            Log.d(TAG, "Bridge setMiningStatus: $active")
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
        }

        @JavascriptInterface
        fun setMiningActive(active: Boolean) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
        }

        @JavascriptInterface
        fun getMiningStatus(): String {
            val active = getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .getBoolean(KEY_MINING_ACTIVE, false)
            return """{"running":$active}"""
        }

        @JavascriptInterface
        fun openCentral() {
            mainHandler.post {
                val intent = Intent(this@BeeActivity, CentralActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opts = android.app.ActivityOptions
                    .makeCustomAnimation(this@BeeActivity, R.anim.slide_up, R.anim.fade_out)
                startActivity(intent, opts.toBundle())
            }
        }

        // ─── Background Mining via WP ─────────────────────────────────────────

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) {
            Log.d(TAG, "startBgMining: ${durationMs/60000}min wallet=$walletName")
            try {
                val intent = BeeBackgroundService.buildStartIntent(
                    this@BeeActivity, durationMs, walletName
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startBgMining error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try {
                startService(BeeBackgroundService.buildStopIntent(this@BeeActivity))
            } catch (e: Exception) {
                Log.e(TAG, "stopBgMining error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active = BeeBackgroundService.isActive(this@BeeActivity)
            val remaining = BeeBackgroundService.remainingMs(this@BeeActivity)
            val prefs = getSharedPreferences(BeeBackgroundService.PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
            val wallet = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
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
        evaluateJs("if(window.onAppPause) window.onAppPause()")
    }

    private fun evaluateJs(js: String) {
        beeWebView.post { runCatching { beeWebView.evaluateJavascript(js, null) } }
    }

    override fun onDestroy() {
        // Só limpa o indicador de mining se o bg service também não estiver ativo.
        // Sem essa checagem, fechar a BeeActivity apagava o estado mesmo com o
        // BeeBackgroundService rodando em background.
        val bgStillActive = BeeBackgroundService.isActive(this)
        if (!bgStillActive) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, false).apply()
        }
        beeWebView.destroy()
        super.onDestroy()
    }
}