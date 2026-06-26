package com.waspbrowser.app

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

    companion object {
        private const val TAG = "CentralActivity"
        const val AD_COOLDOWN_MS = 5 * 60 * 1000L  // 5 min entre anúncios
        const val AD_REWARD_WP   = 30              // WP por vídeo assistido
        // Referência à WebView ativa da Central, para callbacks de anúncio
        // (a StartioAdActivity precisa devolver o reward para o central.html,
        // que roda AQUI — não na BeeActivity).
        var activeWebView: java.lang.ref.WeakReference<WebView>? = null
        fun runJs(js: String) {
            val wv = activeWebView?.get() ?: return
            wv.post { runCatching { wv.evaluateJavascript(js, null) } }
        }
        // Credita o WP do anúncio (chamado pela StartioAdActivity) e grava o
        // cooldown. Faz tudo no nativo e só usa o JS para escrever no
        // localStorage do WebView, que é a única via possível para isso.
        fun grantAdReward(ctx: android.content.Context) {
            ctx.getSharedPreferences("wasp_ads", android.content.Context.MODE_PRIVATE)
                .edit().putLong("wp_ad_last", System.currentTimeMillis()).apply()
            runJs("if(window.waspAddWP) window.waspAddWP($AD_REWARD_WP, 'Ad watched');")
            BeeActivity.runJs(
                "(function(){try{" +
                "var f=document.getElementById('wasp-central-frame');" +
                "if(f){var ifr=f.querySelector('iframe');" +
                "if(ifr&&ifr.contentWindow&&ifr.contentWindow.waspAddWP){" +
                "ifr.contentWindow.waspAddWP($AD_REWARD_WP,'Ad watched');}}" +
                "}catch(e){}})();"
            )
        }

        // Credita 15 WP do interstitial bônus (chamado pela StartioAdActivity).
        // Cooldown separado de 30 min — independente do rewarded video.
        const val INTERSTITIAL_BONUS_WP        = 15
        const val INTERSTITIAL_COOLDOWN_MS     = 30 * 60 * 1000L
        fun grantInterstitialBonus(ctx: android.content.Context) {
            ctx.getSharedPreferences("wasp_ads", android.content.Context.MODE_PRIVATE)
                .edit().putLong("wp_interstitial_last", System.currentTimeMillis()).apply()
            runJs("if(window.waspAddWP) window.waspAddWP($INTERSTITIAL_BONUS_WP, 'Bonus ad');")
            BeeActivity.runJs(
                "(function(){try{" +
                "var f=document.getElementById('wasp-central-frame');" +
                "if(f){var ifr=f.querySelector('iframe');" +
                "if(ifr&&ifr.contentWindow&&ifr.contentWindow.waspAddWP){" +
                "ifr.contentWindow.waspAddWP($INTERSTITIAL_BONUS_WP,'Bonus ad');}}" +
                "}catch(e){}})();"
            )
        }
    }

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

        val bridge = Bridge()
        webView.addJavascriptInterface(bridge, "AndroidBee")
        webView.addJavascriptInterface(bridge, "Android")
        // i18n: passa o idioma efetivo do app (escolha do usuário OU sistema) para o WebView.
        // Locale.getDefault() já reflete o que o SettingsActivity aplicou via setApplicationLocales().
        val lang = java.util.Locale.getDefault().language.lowercase()
        webView.loadUrl("file:///android_asset/bee/central.html?lang=$lang")
        activeWebView = java.lang.ref.WeakReference(webView)
    }

    override fun onResume()  { super.onResume();  webView.onResume();  webView.resumeTimers() }
    override fun onPause()   { super.onPause();   webView.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); webView.destroy(); super.onDestroy() }

    private fun js(fn: String) {
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
        fun openWpAd() {
            android.util.Log.d(TAG, "openWpAd chamado!")
            handler.post {
                // Cooldown controlado no nativo (não no JS): mais confiável.
                val prefs = getSharedPreferences("wasp_ads", MODE_PRIVATE)
                val last  = prefs.getLong("wp_ad_last", 0L)
                val now   = System.currentTimeMillis()
                val remaining = AD_COOLDOWN_MS - (now - last)
                if (remaining > 0) {
                    val min = (remaining / 60000) + 1
                    Toast.makeText(this@CentralActivity,
                        getString(R.string.ad_next_in, min.toInt()), Toast.LENGTH_SHORT).show()
                    return@post
                }
                val i = android.content.Intent(this@CentralActivity, StartioAdActivity::class.java)
                i.putExtra(StartioAdActivity.EXTRA_MODE, "wp")
                startActivity(i)
            }
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
                // Marca PiP como habilitado
                getSharedPreferences("bee_pip", MODE_PRIVATE)
                    .edit().putBoolean("pip_enabled", true).apply()
                val intent = BeeBackgroundService.buildStartIntent(this@CentralActivity, walletName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                    startForegroundService(intent)
                else startService(intent)
            } catch (e: Exception) { android.util.Log.e(TAG, "startBgMining: ${e.message}") }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try {
                // Remove flag PiP
                getSharedPreferences("bee_pip", MODE_PRIVATE)
                    .edit().putBoolean("pip_enabled", false).apply()
                startService(BeeBackgroundService.buildStopIntent(this@CentralActivity))
            } catch (e: Exception) { android.util.Log.e(TAG, "stopBgMining: ${e.message}") }
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
