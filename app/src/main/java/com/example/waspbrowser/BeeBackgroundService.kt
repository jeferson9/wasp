package com.example.waspbrowser

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
import androidx.core.app.NotificationCompat

/**
 * BeeBackgroundService — mantém o processo vivo e o BeeActivity ativo.
 *
 * Android mata WebViews em Services (Android 10+). A solução correta é
 * manter o BeeActivity vivo em background via WakeLock + START_STICKY.
 * O Service não tenta rodar JS — apenas mantém o processo do app vivo
 * e verifica periodicamente se o BeeActivity ainda existe.
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG = "BeeBackgroundService"
        private const val CHANNEL_ID = "bee_mining_channel"
        private const val NOTIF_ID = 42
        private const val TICK_MS = 30_000L

        const val PREFS_BG   = "bee_bg_mining"
        const val KEY_ACTIVE = "bg_active"
        const val KEY_WALLET = "bg_wallet"
        const val KEY_CYCLES = "bg_cycles"
        const val KEY_END_TIME = "bg_end_time"

        const val EXTRA_WALLET      = "wallet_name"
        const val ACTION_STOP       = "com.example.waspbrowser.BEE_STOP"
        const val ACTION_KEEP_ALIVE  = "com.example.waspbrowser.BEE_KEEP_ALIVE"
        const val ACTION_EPOCH_ENDED = "com.example.waspbrowser.BEE_EPOCH_ENDED"
        private const val EPOCH_RESTART_DELAY = 28_000L // 16s slashing + 10s espera + 2s margem

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

        fun runJs(js: String) {
            BeeActivity.runJs(js)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var walletName = ""
    private var tickCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMining()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_EPOCH_ENDED) {
            Log.d(TAG, "Epoch terminou — agendando claim+restart")
            ensureBeeActivityAlive()
            scheduleEpochRestart()
            return START_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET) ?: ""
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName))
        Log.d(TAG, "Service iniciado | wallet=$walletName")
        scheduleTick()
        scheduleTaps()
        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tapRunnable?.let { handler.removeCallbacks(it) }
        epochRestartRunnable?.let { handler.removeCallbacks(it) }
        stopMining()
        super.onDestroy()
    }

    private var epochRestartRunnable: Runnable? = null
    private var tapRunnable: Runnable? = null
    private var tapCount = 0
    private val TAP_INTERVAL = 3_000L  // 1 tap a cada 3s = 100 taps em 5min

    /**
     * Garante que o BeeActivity está vivo.
     * Se instance for null (Android destruiu a Activity), relança silenciosamente.
     * O BeeActivity tem singleInstance — não cria nova instância se já existir.
     */
    private fun ensureBeeActivityAlive() {
        if (BeeActivity.instance?.get() != null) return
        Log.w(TAG, "BeeActivity destruída — relançando para restaurar WebView")
        try {
            val intent = Intent(this, BeeActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra("background_restart", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "ensureBeeActivityAlive erro: ${e.message}")
        }
    }

    private fun scheduleTaps() {
        tapRunnable?.let { handler.removeCallbacks(it) }
        tapRunnable = Runnable {
            // Só tapa se BeeActivity está viva e mining está ativo
            val js = """
                (function(){
                    try {
                        if (window._mining && window._doTap) {
                            window._doTap();
                        }
                    } catch(e) {}
                })()
            """.trimIndent()
            BeeActivity.runJs(js)
            scheduleTaps()
        }
        handler.postDelayed(tapRunnable!!, TAP_INTERVAL)
    }


    private fun scheduleEpochRestart() {
        epochRestartRunnable?.let { handler.removeCallbacks(it) }
        // Após 16s de slashing period, chama _doEpochClaim() que faz reward + restart
        epochRestartRunnable = Runnable {
            Log.d(TAG, "Slashing period passou — chamando _doEpochClaim()")
            val js = """
                (function(){
                    try {
                        if (typeof window._doEpochClaim === 'function') {
                            console.log('[Svc] Chamando _doEpochClaim() via Kotlin Handler');
                            window._doEpochClaim();
                        } else {
                            // _doEpochClaim já foi executado pelo fallback ou não existe
                            // Garante que o miner está rodando
                            var autoMine = false;
                            try {
                                var st = localStorage.getItem('wasp_bee_state_v6');
                                if (st) autoMine = JSON.parse(st).autoMine;
                            } catch(_) {}
                            if (autoMine && !window._mining && typeof window._startMining === 'function') {
                                console.log('[Svc] Fallback: chamando _startMining()');
                                window._startMining();
                            }
                        }
                    } catch(e) { console.error('[Svc] scheduleEpochRestart erro: ' + e); }
                })()
            """.trimIndent()
            BeeActivity.runJs(js)
        }
        handler.postDelayed(epochRestartRunnable!!, 17_000L) // 17s = 16s slashing + 1s margem
    }

    private fun scheduleTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tapRunnable?.let { handler.removeCallbacks(it) }
        epochRestartRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = Runnable {
            tickCount++
            Log.d(TAG, "Tick #$tickCount | BeeActivity viva: ${BeeActivity.instance?.get() != null}")

            // Garante que o BeeActivity está vivo a cada tick
            ensureBeeActivityAlive()

            // O Service é o timer real — não depende do setTimeout do JS
            // que é throttled pelo Android quando em background.
            // Chama startMining() diretamente se autoMine=true e não está minerando.
            val js = """
                (function(){
                    try {
                        var autoMine = false;
                        try {
                            var st = localStorage.getItem('wasp_bee_state_v6');
                            if (st) autoMine = JSON.parse(st).autoMine;
                        } catch(_) {}
                        var isMining = window._mining === true;
                        console.log('[Svc] tick #${"\$"}{$tickCount} autoMine=' + autoMine + ' isMining=' + isMining);
                        if (autoMine && !isMining) {
                            if (typeof window._startMining === 'function') {
                                console.log('[Svc] Chamando _startMining() direto...');
                                window._startMining();
                            } else if (typeof window.onAppResume === 'function') {
                                console.log('[Svc] Chamando onAppResume()...');
                                window.onAppResume();
                            }
                        }
                    } catch(e) { console.error('[Svc] ' + e); }
                })()
            """.trimIndent()

            BeeActivity.runJs(js)
            scheduleTick()
        }
        handler.postDelayed(tickRunnable!!, TICK_MS)
    }

    private fun stopMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Bee Mining", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(wallet: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, BeeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, buildStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("🐝 Minerando NACKL")
            .setContentText(if (wallet.isNotBlank()) "Wallet: $wallet" else "Mineração ativa")
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_bee_tech, "Parar", stop)
            .build()
    }
}
