package com.waspbrowser.app

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Acki City — Chain Runner. Jogo 3D (Three.js/WebGL) rodando em WebView
 * fullscreen. O WP ganho é gravado direto no localStorage compartilhado
 * (mesma origem file:// das outras páginas do app), nas mesmas chaves do
 * WP Central — ao voltar, a Central detecta a mudança e re-renderiza.
 */
class AckiGameActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AckiGameActivity"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        webView = WebView(this)
        webView.setBackgroundColor(0xFF04050A.toInt())
        setContentView(webView)

        // Imersivo: esconde status/navigation bar durante o jogo
        WindowInsetsControllerCompat(window, webView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(GameBridge(), "AckiGame")

        val lang = java.util.Locale.getDefault().language.lowercase()
        webView.loadUrl("file:///android_asset/game/acki.html?lang=$lang")
    }

    override fun onResume() { super.onResume(); webView.onResume(); webView.resumeTimers() }
    override fun onPause() { super.onPause(); webView.onPause() }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // O jogo credita WP no localStorage; avisa as Centrais abertas para
        // re-renderizarem o saldo sem esperar o poll delas.
        CentralActivity.runJs("if(window.renderAll)window.renderAll();")
        BeeActivity.runJs(
            "(function(){try{" +
            "var f=document.getElementById('wasp-central-frame');" +
            "if(f){var ifr=f.querySelector('iframe');" +
            "if(ifr&&ifr.contentWindow&&ifr.contentWindow.renderAll){ifr.contentWindow.renderAll();}}" +
            "}catch(e){}})();"
        )
        webView.destroy()
        super.onDestroy()
    }

    inner class GameBridge {

        @JavascriptInterface
        fun closeGame() {
            handler.post { finish() }
        }

        @JavascriptInterface
        fun vibrate(ms: Long) {
            try {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(ms)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "vibrate: ${e.message}")
            }
        }

        @JavascriptInterface
        fun toast(m: String) {
            handler.post { WaspToast.show(this@AckiGameActivity, m) }
        }

        @JavascriptInterface
        fun log(m: String) { android.util.Log.d("AckiGameJS", m) }
    }
}
