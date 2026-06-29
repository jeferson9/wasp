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
import android.os.PowerManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

/**
 * BeeBackgroundService — mineração em segundo plano com arquitetura mínima.
 *
 * Diferente da abordagem anterior (JS controla timers), aqui:
 * - WebView mínima carrega apenas o WASM SDK (bee_bg_minimal.html)
 * - Kotlin Handler controla todo o timing (check a cada 60s, reward após 5min)
 * - JS só executa quando Kotlin chama evaluateJavascript()
 * - Sem logs, sem UI, sem timers JS — Android não pode throttlar o que não existe
 * - WakeLock PARTIAL mantém CPU ativo
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG          = "BeeBackgroundService"
        private const val CHANNEL_ID   = "bee_mining_channel"
        private const val NOTIF_ID     = 42
        private const val CHECK_INTERVAL_MS  = 60_000L   // verifica can_start a cada 1 min
        private const val EPOCH_DURATION_MS  = 5 * 60_000L + 15_000L // 5min15s para get_reward

        const val PREFS_BG      = "bee_bg_mining"
        const val KEY_ACTIVE    = "bg_active"
        const val KEY_WALLET    = "bg_wallet"
        const val KEY_CYCLES    = "bg_cycles"
        const val KEY_END_TIME  = "bg_end_time"
        const val KEY_MINER_ADDR = "bg_miner_addr"
        const val KEY_PUBLIC_KEY = "bg_public_key"
        const val KEY_SECRET_KEY = "bg_secret_key"

        const val EXTRA_WALLET      = "wallet_name"
        const val EXTRA_MINER_ADDR  = "miner_address"
        const val EXTRA_PUBLIC_KEY  = "public_key"
        const val EXTRA_SECRET_KEY  = "secret_key"
        const val ACTION_STOP       = "com.waspbrowser.app.BEE_STOP"
        const val ACTION_KEEP_ALIVE = "com.waspbrowser.app.BEE_KEEP_ALIVE"
        const val ACTION_EPOCH_ENDED = "com.waspbrowser.app.BEE_EPOCH_ENDED"

        fun buildStartIntent(context: Context, walletName: String): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                putExtra(EXTRA_WALLET, walletName)
            }

        fun buildStartIntentFull(
            context: Context, walletName: String,
            minerAddress: String, publicKey: String, secretKey: String
        ): Intent = Intent(context, BeeBackgroundService::class.java).apply {
            putExtra(EXTRA_WALLET,     walletName)
            putExtra(EXTRA_MINER_ADDR, minerAddress)
            putExtra(EXTRA_PUBLIC_KEY, publicKey)
            putExtra(EXTRA_SECRET_KEY, secretKey)
        }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, BeeBackgroundService::class.java).apply { action = ACTION_STOP }

        fun isActive(context: Context): Boolean {
            val prefActive = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)
            if (!prefActive) return false
            return try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                @Suppress("DEPRECATION")
                val running = manager.getRunningServices(Int.MAX_VALUE)
                    ?.any { it.service.className == BeeBackgroundService::class.java.name } ?: false
                if (!running) context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ACTIVE, false).apply()
                running
            } catch (e: Exception) { prefActive }
        }

        fun remainingMs(context: Context): Long = Long.MAX_VALUE
        fun runJs(js: String) { BeeActivity.runJs(js) }
    }

    private val handler     = Handler(Looper.getMainLooper())
    private var walletName  = ""
    private var minerAddr   = ""
    private var publicKey   = ""
    private var secretKey   = ""
    private var bgWebView   : WebView? = null
    private var wakeLock    : PowerManager.WakeLock? = null
    private var sdkReady    = false
    private var epochActive = false

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown(); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_EPOCH_ENDED ||
            intent?.action == "com.waspbrowser.app.BEE_EPOCH_STARTED") {
            return START_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET)     ?: walletName
        minerAddr  = intent?.getStringExtra(EXTRA_MINER_ADDR) ?: ""
        publicKey  = intent?.getStringExtra(EXTRA_PUBLIC_KEY) ?: ""
        secretKey  = intent?.getStringExtra(EXTRA_SECRET_KEY) ?: ""

        // Fallback: tenta ler do SharedPreferences se não veio no intent
        val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
        if (minerAddr.isBlank()) minerAddr = prefs.getString(KEY_MINER_ADDR, "") ?: ""
        if (publicKey.isBlank()) publicKey = prefs.getString(KEY_PUBLIC_KEY, "") ?: ""
        if (secretKey.isBlank()) secretKey = prefs.getString(KEY_SECRET_KEY, "") ?: ""

        prefs.edit()
            .putBoolean(KEY_ACTIVE,    true)
            .putString(KEY_WALLET,     walletName)
            .putString(KEY_MINER_ADDR, minerAddr)
            .putString(KEY_PUBLIC_KEY, publicKey)
            .putString(KEY_SECRET_KEY, secretKey)
            .apply()

        startForeground(NOTIF_ID, buildNotification("⏳ Iniciando..."))
        handler.post { startMinimalWebView() }
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ── WebView mínima ───────────────────────────────────────────────────────

    private fun startMinimalWebView() {
        if (bgWebView != null) return
        try {
            val wv = WebView(applicationContext)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            wv.addJavascriptInterface(BgBridge(), "AndroidBgBridge")
            wv.addJavascriptInterface(BgBridge(), "AndroidBee")

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (url.endsWith(".wasm")) {
                        return try {
                            WebResourceResponse("application/wasm", "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"),
                                assets.open("bee/bee_sdk_bg.wasm"))
                        } catch (e: Exception) { null }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    // SDK vai notificar via AndroidBgBridge.onEvent("sdk_ready")
                    Log.d(TAG, "Página mínima carregada")
                }
            }

            // Usa o index.html original — o carregamento de WASM ja funciona la
            wv.loadUrl("file:///android_asset/bee/index.html?bgmode=true")
            bgWebView = wv
            Log.d(TAG, "WebView mínima iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar WebView mínima: ${e.message}")
        }
    }

    // ── Ciclo de mineração (controlado pelo Kotlin) ──────────────────────────

    private fun scheduleNextCheck() {
        handler.postDelayed({ triggerBgCycle() }, CHECK_INTERVAL_MS)
    }

    private fun triggerBgCycle() {
        if (!isActive(this)) return
        if (epochActive) { scheduleNextCheck(); return }
        val js = "window.doBgCycle();"
        bgWebView?.evaluateJavascript(js, null)
            ?: Log.w(TAG, "WebView não disponível para doBgCycle")
    }

    private fun triggerGetReward() {
        if (!isActive(this)) return
        bgWebView?.evaluateJavascript("window.doBgGetReward();", null)
            ?: Log.w(TAG, "WebView não disponível para doBgGetReward")
    }

    private fun onEpochStarted() {
        epochActive = true
        updateNotification("⛏ Minerando...")
        // Kotlin agenda get_reward após duração do epoch
        handler.postDelayed({
            epochActive = false
            triggerGetReward()
        }, EPOCH_DURATION_MS)
    }

    private fun onRewardClaimed() {
        val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
        val cycles = prefs.getInt(KEY_CYCLES, 0) + 1
        prefs.edit().putInt(KEY_CYCLES, cycles).apply()
        updateNotification("✅ Epoch $cycles completo")
        BeeActivity.runJs("if(window.waspAddWP) waspAddWP(0,'bg_epoch');")
        // Aguarda 30s e tenta próximo epoch
        handler.postDelayed({ triggerBgCycle() }, 30_000L)
    }

    // ── Bridge JS → Kotlin ───────────────────────────────────────────────────

    inner class BgBridge {
        @JavascriptInterface
        fun onEvent(event: String) {
            Log.d(TAG, "BgBridge event: $event")
            handler.post {
                when {
                    event == "sdk_ready" -> {
                        sdkReady = true
                        updateNotification("🔑 SDK pronto — iniciando ciclo...")
                        // Injeta chaves explicitamente (garante que bgmode as tem)
                        val keys = "if(window.initBgKeys) window.initBgKeys('${esc(minerAddr)}','${esc(publicKey)}','${esc(secretKey)}');"
                        bgWebView?.evaluateJavascript(keys, null)
                        // Aguarda 2s e dispara primeiro ciclo
                        handler.postDelayed({ triggerBgCycle() }, 2_000L)
                    }
                    event == "epoch_started" -> onEpochStarted()
                    event == "reward_claimed" -> onRewardClaimed()
                    event == "wait" -> {
                        updateNotification("⏳ Aguardando epoch... próxima tentativa em 1min")
                        scheduleNextCheck()
                    }
                    event == "not_ready" -> {
                        updateNotification("⏳ SDK não pronto, aguardando...")
                        handler.postDelayed({ triggerBgCycle() }, 10_000L)
                    }
                    event.startsWith("error") -> {
                        epochActive = false
                        updateNotification("⚠️ ${event.take(60)}")
                        Log.e(TAG, "Erro mining bg: $event")
                        scheduleNextCheck()
                    }
                    event.startsWith("reward_error") -> {
                        epochActive = false
                        updateNotification("⚠️ Reward falhou, tentando novamente...")
                        scheduleNextCheck()
                    }
                    event == "no_keys" -> updateNotification("⚠️ Chaves não encontradas — reabra o painel")
                }
            }
        }

        // Métodos legados — necessários pelo bee_engine.js se carregado
        @JavascriptInterface fun goBack() {}
        @JavascriptInterface fun openCentral() {}
        @JavascriptInterface fun openUrl(url: String) {}
        @JavascriptInterface fun toast(msg: String) { Log.d(TAG, "bg toast: $msg") }
        @JavascriptInterface fun log(msg: String) { Log.d(TAG, "bg js: $msg") }
        @JavascriptInterface fun setMiningStatus(active: Boolean, wallet: String) {}
        @JavascriptInterface fun closePanel() {}
        @JavascriptInterface fun getBgMiningStatus(): String {
            val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
            return """{"active":true,"remainingMs":${Long.MAX_VALUE},"cycles":${prefs.getInt(KEY_CYCLES,0)},"wallet":"${esc(walletName)}"}"""
        }
    }

    private fun esc(s: String) = s.replace("'", "\\'").replace("\\", "\\\\")

    // ── WakeLock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaspBrowser:BgMinerLock")
        wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 horas
    }

    // ── Notificação ──────────────────────────────────────────────────────────

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        try { bgWebView?.destroy(); bgWebView = null } catch (_: Exception) {}
        try { wakeLock?.release(); wakeLock = null } catch (_: Exception) {}
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Bee Participation", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(status: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, buildStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("Wasp · Minerando NACKL")
            .setContentText(if (walletName.isNotBlank()) "$walletName — $status" else status)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_bee_tech, getString(R.string.notif_stop), stop)
            .build()
    }
}
