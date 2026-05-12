package com.example.waspbrowser

import android.annotation.SuppressLint
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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

/**
 * BeeBackgroundService — cérebro da mineração
 *
 * Tem seu próprio WebView headless que carrega o bee_engine.js.
 * Assim a mineração continua mesmo que o BeeActivity seja destruído.
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

        const val EXTRA_WALLET      = "wallet_name"
        const val ACTION_STOP       = "com.example.waspbrowser.BEE_STOP"
        const val ACTION_KEEP_ALIVE = "com.example.waspbrowser.BEE_KEEP_ALIVE"

        fun buildStartIntent(context: Context, walletName: String): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                putExtra(EXTRA_WALLET, walletName)
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                action = ACTION_STOP
            }

        fun isActive(context: Context): Boolean =
            context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)

        fun remainingMs(context: Context): Long = Long.MAX_VALUE

        // runJs ainda funciona via BeeActivity quando disponível (para atualizar UI)
        fun runJs(js: String) {
            BeeActivity.runJs(js)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceWebView: WebView? = null
    private var walletName = ""
    private var tickCount = 0
    private var tickRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initServiceWebView()
        Log.d(TAG, "Service criado com WebView headless")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initServiceWebView() {
        mainHandler.post {
            try {
                val wv = WebView(applicationContext)
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                wv.addJavascriptInterface(ServiceBridge(), "AndroidBee")

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?, request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (url.endsWith(".wasm")) {
                            return try {
                                WebResourceResponse(
                                    "application/wasm", "binary", 200, "OK",
                                    mapOf("Access-Control-Allow-Origin" to "*"),
                                    assets.open("bee/bee_sdk_bg.wasm")
                                )
                            } catch (e: Exception) { null }
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "Service WebView carregado: $url")
                        // Página carregou — injeta a wallet salva se disponível
                        injectSavedState()
                    }
                }

                wv.loadUrl("file:///android_asset/bee/index.html")
                serviceWebView = wv
                Log.d(TAG, "Service WebView inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao criar service WebView: ${e.message}")
            }
        }
    }

    private fun injectSavedState() {
        // O bee_engine.js lê o localStorage automaticamente via loadSaved()
        // Só precisamos garantir que o autoMine está ativo
        mainHandler.postDelayed({
            serviceWebView?.evaluateJavascript(
                "(function(){ try { var s=localStorage.getItem('wasp_bee_state_v6'); if(s){ var o=JSON.parse(s); if(o.autoMine && o.authorized){ console.log('[SvcWV] Estado carregado — autoMine=true'); } } } catch(e){} })()",
                null
            )
        }, 3000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMining()
            stopSelf()
            return START_NOT_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET) ?: ""

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName, 0))
        Log.d(TAG, "Mineração iniciada | wallet=$walletName")

        scheduleTick()
        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { mainHandler.removeCallbacks(it) }
        mainHandler.post {
            serviceWebView?.destroy()
            serviceWebView = null
        }
        stopMining()
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }

    // ─── Tick loop ───────────────────────────────────────────────────────────

    private fun scheduleTick() {
        tickRunnable?.let { mainHandler.removeCallbacks(it) }
        tickRunnable = Runnable {
            tickCount++
            Log.d(TAG, "Tick #$tickCount")
            checkAndRestartMiner()
            val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notifManager.notify(NOTIF_ID, buildNotification(walletName, tickCount))
            scheduleTick()
        }
        mainHandler.postDelayed(tickRunnable!!, 30_000L)
    }

    private fun checkAndRestartMiner() {
        val js = """
            (function(){
                try {
                    var isMining = window._mining === true;
                    var autoMine = false;
                    try {
                        var st = localStorage.getItem('wasp_bee_state_v6');
                        if (st) autoMine = JSON.parse(st).autoMine;
                    } catch(_) {}
                    console.log('[SvcTick] isMining=' + isMining + ' autoMine=' + autoMine);
                    if (autoMine && !isMining && typeof window.onAppResume === 'function') {
                        console.log('[SvcTick] Reiniciando miner...');
                        window.onAppResume();
                    }
                } catch(e) { console.error('[SvcTick] ' + e); }
            })()
        """.trimIndent()

        // Tenta no WebView do service (sempre disponível)
        mainHandler.post {
            serviceWebView?.evaluateJavascript(js, null)
        }
        // Tenta também no BeeActivity se estiver aberto (atualiza a UI)
        BeeActivity.runJs(js)
    }

    private fun stopMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    // ─── Bridge do Service WebView ──────────────────────────────────────────

    inner class ServiceBridge {
        @JavascriptInterface
        fun ping(): String = "pong-service"

        @JavascriptInterface
        fun toast(m: String) {
            Log.d(TAG, "[ServiceWV toast] $m")
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.d("ServiceWV", msg)
        }

        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            Log.d(TAG, "[ServiceWV] setMiningStatus: $active wallet=$wallet")
            getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, active)
                .apply()
        }

        @JavascriptInterface
        fun setMiningActive(active: Boolean) {
            getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, active)
                .apply()
        }

        @JavascriptInterface
        fun getMiningStatus(): String {
            val active = isActive(applicationContext)
            return """{"running":$active}"""
        }

        @JavascriptInterface
        fun navigateTo(screen: String) { /* no-op no service */ }

        @JavascriptInterface
        fun goBack() { /* no-op no service */ }

        @JavascriptInterface
        fun openDeepLink(url: String) { /* no-op no service */ }

        @JavascriptInterface
        fun openExternalUrl(url: String) { /* no-op no service */ }

        @JavascriptInterface
        fun openEnergyPage() { /* no-op no service */ }

        @JavascriptInterface
        fun openWpAd() { /* no-op no service */ }

        @JavascriptInterface
        fun openCentral() { /* no-op no service */ }

        @JavascriptInterface
        fun isEnergyReady(): Boolean = false

        @JavascriptInterface
        fun clearEnergyReady() { /* no-op */ }

        @JavascriptInterface
        fun hasWasm(): Boolean {
            return try { assets.open("bee/bee_sdk_bg.wasm").close(); true }
            catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun checkAssets(): String {
            return try { assets.list("bee")?.joinToString(", ") ?: "vazio" }
            catch (e: Exception) { "erro: ${e.message}" }
        }

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) { /* já estamos no service */ }

        @JavascriptInterface
        fun stopBgMining() {
            stopSelf()
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active = isActive(applicationContext)
            return """{"active":$active,"remainingMs":999999999,"cycles":$tickCount,"wallet":"$walletName"}"""
        }
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bee Mining", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mineração NACKL em segundo plano"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(wallet: String, ticks: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0, buildStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("🐝 Minerando NACKL")
            .setContentText(
                if (wallet.isNotBlank()) "Wallet: $wallet • rodando em background"
                else "Mineração ativa em background"
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_bee_tech, "Parar", stopIntent)
            .build()
    }
}
