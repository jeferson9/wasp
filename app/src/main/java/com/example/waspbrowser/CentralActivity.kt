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
        // ID Oficial do Google para Teste de REWARDED AD (Vídeo Premiado)
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var isAdShowing = false
    private var wpRewardEarned = false
    private var adPendingShow = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        webView.setBackgroundColor(0xFF08090D.toInt())
        setContentView(webView)

        configureWebView()  // já faz clearCache(true) + LOAD_NO_CACHE

        val bridge = CentralBridge()
        webView.addJavascriptInterface(bridge, "AndroidBee")
        webView.addJavascriptInterface(bridge, "Android")

        // Configuração de Dispositivo de Teste (importante para evitar erro No Fill)
        val testDeviceIds = listOf("C2BDD20251E0A65AA97DD561F37883A1")
        val configuration = RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        MobileAds.setRequestConfiguration(configuration)

        // Inicializa AdMob e já carrega o primeiro anúncio
        MobileAds.initialize(this) { 
            android.util.Log.d(TAG, "AdMob Inicializado")
            loadRewardedAd() 
        }

        // Carrega DEPOIS do bridge estar registrado
        webView.loadUrl("file:///android_asset/bee/central.html")
    }

    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE  // sempre recarrega assets frescos
        }
        webView.clearCache(true)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun loadRewardedAd() {
        if (rewardedAd != null) return

        RewardedAd.load(this, REWARDED_AD_UNIT_ID, AdRequest.Builder().build(), 
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    android.util.Log.d(TAG, "AdMob: Anúncio carregado e pronto.")
                    if (adPendingShow) {
                        adPendingShow = false
                        showRewardedAd()
                    }
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    rewardedAd = null
                    android.util.Log.e(TAG, "AdMob Erro no Carregamento: ${e.message}")
                    if (adPendingShow) {
                        adPendingShow = false
                        evaluateJs("onWpAdUnavailable")
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
            evaluateJs("onWpAdLoading")
            loadRewardedAd()
            
            // Timeout de 15 segundos para carregar o vídeo
            handler.postDelayed({
                if (adPendingShow && rewardedAd == null) {
                    adPendingShow = false
                    evaluateJs("onWpAdUnavailable")
                }
            }, 15000)
            return
        }

        wpRewardEarned = false
        isAdShowing = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                android.util.Log.d(TAG, "Anúncio fechado. Ganhou pontos? $wpRewardEarned")
                isAdShowing = false
                rewardedAd = null
                loadRewardedAd() // Prepara o próximo vídeo em background
                
                // Sistema de entrega garantida: tenta avisar o JS 3 vezes
                retryRewardEvaluation(0)
            }

            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                isAdShowing = false
                rewardedAd = null
                loadRewardedAd()
                evaluateJs("onWpAdUnavailable")
            }
        }

        ad.show(this) { _: RewardItem ->
            wpRewardEarned = true 
            android.util.Log.d(TAG, "Recompensa confirmada pelo Android!")
            handler.post { Toast.makeText(this, "WP Recebido! 🐝", Toast.LENGTH_SHORT).show() }
        }
    }

    // Tenta enviar o prêmio para o site várias vezes para não falhar
    private fun retryRewardEvaluation(count: Int) {
        if (wpRewardEarned) evaluateJs("onWpAdRewarded")
        else evaluateJs("onWpAdClosed")

        if (count < 2) {
            handler.postDelayed({ retryRewardEvaluation(count + 1) }, 1000)
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

        // ─── Background Mining via WP ─────────────────────────────────────

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) {
            android.util.Log.d(TAG, "startBgMining: ${durationMs/60000}min wallet=$walletName")
            try {
                val intent = BeeBackgroundService.buildStartIntent(
                    this@CentralActivity, durationMs, walletName
                )
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
            try {
                startService(BeeBackgroundService.buildStopIntent(this@CentralActivity))
            } catch (e: Exception) {
                android.util.Log.e(TAG, "stopBgMining error: ${e.message}")
            }
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

    private fun evaluateJs(jsFunc: String) {
        // Lógica de injeção segura: verifica se a função existe no HTML antes de chamar
        val script = "if(typeof window.$jsFunc === 'function') { window.$jsFunc(); } else if(window.$jsFunc) { window.$jsFunc(); }"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val opts = android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.fade_in, R.anim.slide_down)
        // finish com animação via ActivityOptions não é suportado diretamente,
        // então usamos finishAfterTransition + overridePendingTransition
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
        finish()
    }

    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}