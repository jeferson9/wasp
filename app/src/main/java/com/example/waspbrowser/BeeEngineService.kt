package com.example.waspbrowser

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.webkit.*
import androidx.core.app.NotificationCompat

/**
 * BeeEngineService
 *
 * ForegroundService com WebView invisível própria.
 * Roda bee_engine.js + WASM em background permanente,
 * independente do BeeActivity estar visível ou não.
 *
 * A UI do BeeActivity lê o estado via SharedPreferences
 * e via window globals quando está visível.
 */
class BeeEngineService : Service() {

    companion object {
        private const val TAG = "BeeEngineService"
        private const val CHANNEL_ID = "bee_engine_channel"
        private const val NOTIF_ID = 43
        const val ACTION_START = "com.example.waspbrowser.ENGINE_START"
        const val ACTION_STOP  = "com.example.waspbrowser.ENGINE_STOP"
        const val PREFS_ENGINE = "bee_engine_service"
        const val KEY_RUNNING  = "engine_running"
        const val KEY_STATUS   = "engine_status"
        const val KEY_CYCLES   = "engine_cycles"

        fun isRunning(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_ENGINE, Context.MODE_PRIVATE)
                .getBoolean(KEY_RUNNING, false)
        }

        fun start(context: Context) {
            val intent = Intent(context, BeeEngineService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BeeEngineService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var engineWebView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Iniciando..."))
        handler.post { initWebView() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEngine()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopEngine()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        try {
            engineWebView = WebView(applicationContext).apply {
                settings.apply {
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

                addJavascriptInterface(EngineAndroidBridge(), "AndroidBee")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "Engine WebView carregada: $url")
                        getSharedPreferences(PREFS_ENGINE, MODE_PRIVATE).edit()
                            .putBoolean(KEY_RUNNING, true).apply()
                        startStatusPolling()
                    }
                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        Log.e(TAG, "Engine WebView erro: ${error?.description}")
                    }
                }

                // Intercepta .wasm igual ao BeeActivity
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "Engine carregada")
                        getSharedPreferences(PREFS_ENGINE, MODE_PRIVATE).edit()
                            .putBoolean(KEY_RUNNING, true).apply()
                        startStatusPolling()
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null
                        if (url.contains("bee_sdk_bg.wasm")) {
                            return try {
                                val stream = assets.open("bee/bee_sdk_bg.wasm")
                                WebResourceResponse(
                                    "application/wasm", null, stream
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao servir WASM: ${e.message}")
                                null
                            }
                        }
                        return null
                    }
                }

                loadUrl("file:///android_asset/bee/index.html")
            }

            Log.d(TAG, "Engine WebView inicializada")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar Engine WebView: ${e.message}")
        }
    }

    private fun startStatusPolling() {
        statusCheckRunnable?.let { handler.removeCallbacks(it) }
        statusCheckRunnable = object : Runnable {
            override fun run() {
                engineWebView?.evaluateJavascript(
                    """
                    (function(){
                        var cycles = 0;
                        var status = 'inativo';
                        try {
                            var el = document.getElementById('mCycles');
                            if (el) cycles = parseInt(el.textContent) || 0;
                            var st = document.getElementById('txtStatusTitle');
                            if (st) status = st.textContent || 'inativo';
                        } catch(e){}
                        return JSON.stringify({cycles: cycles, status: status, mining: window._mining || false});
                    })()
                    """.trimIndent()
                ) { result ->
                    try {
                        val clean = result?.trim()?.removeSurrounding("\"")
                            ?.replace("\\\"", "\"") ?: return@evaluateJavascript
                        val prefs = getSharedPreferences(PREFS_ENGINE, MODE_PRIVATE)
                        // Extrai cycles e status do JSON
                        val cyclesMatch = Regex("\"cycles\":(\\d+)").find(clean)
                        val statusMatch = Regex("\"status\":\"([^\"]+)\"").find(clean)
                        val miningMatch = Regex("\"mining\":(true|false)").find(clean)
                        val cycles = cyclesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        val status = statusMatch?.groupValues?.get(1) ?: "inativo"
                        val mining = miningMatch?.groupValues?.get(1) == "true"
                        prefs.edit()
                            .putInt(KEY_CYCLES, cycles)
                            .putString(KEY_STATUS, status)
                            .putBoolean(KEY_RUNNING, mining)
                            .apply()
                        // Atualiza notificação
                        val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notifManager.notify(NOTIF_ID, buildNotification(
                            if (mining) "Minerando · $cycles ciclos" else status
                        ))
                    } catch (e: Exception) {
                        Log.w(TAG, "Polling erro: ${e.message}")
                    }
                }
                handler.postDelayed(this, 15_000L)
            }
        }
        handler.post(statusCheckRunnable!!)
    }

    private fun stopEngine() {
        statusCheckRunnable?.let { handler.removeCallbacks(it) }
        handler.post {
            engineWebView?.destroy()
            engineWebView = null
        }
        getSharedPreferences(PREFS_ENGINE, MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, false).apply()
        stopForeground(true)
        stopSelf()
    }

    // Bridge mínima para o engine funcionar sem a UI
    inner class EngineAndroidBridge {
        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            Log.d(TAG, "Engine status: active=$active wallet=$wallet")
            getSharedPreferences(PREFS_ENGINE, MODE_PRIVATE).edit()
                .putBoolean(KEY_RUNNING, active).apply()
            val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val msg = if (active) "Minerando NACKL · $wallet" else "Aguardando próximo epoch..."
            notifManager.notify(NOTIF_ID, buildNotification(msg))
        }

        @JavascriptInterface
        fun log(msg: String, level: String) {
            Log.d(TAG, "[$level] $msg")
        }

        @JavascriptInterface
        fun openUrl(url: String) { /* ignora em background */ }

        @JavascriptInterface
        fun openCentral() { /* ignora em background */ }

        @JavascriptInterface
        fun startBgMining(durationMs: Long, wallet: String) { /* já está em bg */ }

        @JavascriptInterface
        fun getWpBalance(): Int = 0

        @JavascriptInterface
        fun showRewardedAd() { /* sem UI */ }

        @JavascriptInterface
        fun toast(msg: String) { Log.d(TAG, "Toast: $msg") }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bee Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mineração NACKL em background"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("🐝 Bee Engine")
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()
    }
}
