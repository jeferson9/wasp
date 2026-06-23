package com.waspbrowser.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

/**
 * BeeBackgroundService — ForegroundService com WebView headless que minera em background.
 *
 * Quando o app vai para background, este serviço:
 * 1. Cria uma WebView headless (sem UI) que carrega o bee_engine.js
 * 2. Injeta o estado salvo (wallet, chaves) para retomar a participação
 * 3. Mantém o processo vivo via ForegroundService — o Android não mata serviços foreground
 * 4. Ao retornar ao app, a BeeActivity assume e o serviço para a WebView headless
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG = "BeeBackgroundService"
        private const val CHANNEL_ID = "bee_mining_channel"
        private const val NOTIF_ID = 42

        const val PREFS_BG   = "bee_bg_mining"
        const val KEY_ACTIVE = "bg_active"
        const val KEY_WALLET = "bg_wallet"
        const val KEY_CYCLES = "bg_cycles"
        const val KEY_END_TIME = "bg_end_time"

        const val EXTRA_WALLET = "wallet_name"
        const val ACTION_STOP  = "com.waspbrowser.app.BEE_STOP"
        const val ACTION_KEEP_ALIVE = "com.waspbrowser.app.BEE_KEEP_ALIVE"
        const val ACTION_EPOCH_ENDED = "com.waspbrowser.app.BEE_EPOCH_ENDED"

        fun buildStartIntent(context: Context, walletName: String): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                putExtra(EXTRA_WALLET, walletName)
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                action = ACTION_STOP
            }

        fun isActive(context: Context): Boolean {
            val prefActive = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)
            if (!prefActive) return false
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                @Suppress("DEPRECATION")
                val running = manager.getRunningServices(Int.MAX_VALUE)
                    ?.any { it.service.className == BeeBackgroundService::class.java.name } ?: false
                if (!running) {
                    context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_ACTIVE, false).apply()
                }
                running
            } catch (e: Exception) {
                prefActive
            }
        }

        fun remainingMs(context: Context): Long = Long.MAX_VALUE

        fun runJs(js: String) {
            BeeActivity.runJs(js)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var walletName = ""
    private var bgWebView: WebView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBgWebView()
            stopMining()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_EPOCH_ENDED ||
            intent?.action == "com.waspbrowser.app.BEE_EPOCH_STARTED") {
            return START_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET) ?: walletName

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName))
        Log.d(TAG, "Service iniciado | wallet=$walletName")

        // Inicia WebView headless para mineração em background
        handler.post { startBgWebView() }

        return START_STICKY
    }

    override fun onDestroy() {
        stopBgWebView()
        stopMining()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun startBgWebView() {
        if (bgWebView != null) return // já está rodando

        Log.d(TAG, "Iniciando WebView headless para background mining")

        try {
            val wv = WebView(applicationContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
            }

            // Bridge para comunicação JS → Service
            wv.addJavascriptInterface(BgBridge(), "AndroidBee")

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "BgWebView carregada: $url")
                    // Injeta flag para saber que está em background
                    view?.evaluateJavascript("window._isBackgroundMode = true;", null)
                }
            }

            // Carrega o mesmo HTML do painel Bee Engine
            wv.loadUrl("file:///android_asset/bee/index.html")

            bgWebView = wv
            Log.d(TAG, "WebView headless iniciada")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar WebView headless: ${e.message}")
            // Fallback: usa o método antigo de ping via BeeActivity
            scheduleLegacyTick()
        }
    }

    private fun stopBgWebView() {
        try {
            bgWebView?.let { wv ->
                wv.evaluateJavascript("if(window.stopMining) stopMining();", null)
                handler.postDelayed({
                    wv.destroy()
                    bgWebView = null
                    Log.d(TAG, "WebView headless destruída")
                }, 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar WebView headless: ${e.message}")
            bgWebView = null
        }
    }

    // Fallback se WebView headless falhar: ping na BeeActivity
    private fun scheduleLegacyTick() {
        handler.postDelayed({
            if (isActive(this)) {
                BeeActivity.runJs("if(window.onAppResume) window.onAppResume()")
                scheduleLegacyTick()
            }
        }, 30_000L)
    }

    private fun stopMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Bee Participation", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(wallet: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, buildStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle(getString(R.string.notif_mining_title))
            .setContentText(if (wallet.isNotBlank()) "Wallet: $wallet" else getString(R.string.notif_mining_active))
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_bee_tech, getString(R.string.notif_stop), stop)
            .build()
    }

    /**
     * Bridge JS para a WebView headless do serviço de background.
     * Expõe os mesmos métodos necessários pelo bee_engine.js.
     */
    inner class BgBridge {
        @JavascriptInterface
        fun goBack() {
            // Em background não faz nada — não há UI
        }

        @JavascriptInterface
        fun openCentral() {}

        @JavascriptInterface
        fun openUrl(url: String) {}

        @JavascriptInterface
        fun toast(msg: String) {
            Log.d(TAG, "BgBridge toast: $msg")
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.d(TAG, "BgBridge JS: $msg")
        }

        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            Log.d(TAG, "BgBridge setMiningStatus: active=$active wallet=$wallet")
            // Atualiza notificação com status atual
            handler.post {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(wallet))
            }
        }

        @JavascriptInterface
        fun closePanel() {
            // Em background não fecha nada
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active = isActive(this@BeeBackgroundService)
            val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(KEY_CYCLES, 0)
            val wallet = prefs.getString(KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":${Long.MAX_VALUE},"cycles":$cycles,"wallet":"$wallet"}"""
        }
    }
}
