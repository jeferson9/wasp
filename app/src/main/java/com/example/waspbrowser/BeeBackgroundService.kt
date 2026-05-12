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
 * BeeBackgroundService — cérebro da mineração
 *
 * Roda enquanto o usuário tiver a mineração ligada.
 * A cada TICK (30s) verifica se o miner ainda está rodando e,
 * se não estiver, dispara o restart via BeeActivity.runJs().
 *
 * A BeeActivity é apenas interface — quem controla o loop é este Service.
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG = "BeeBackgroundService"
        private const val CHANNEL_ID = "bee_mining_channel"
        private const val NOTIF_ID = 42
        private const val TICK_INTERVAL = 30_000L   // verifica a cada 30s

        const val PREFS_BG   = "bee_bg_mining"
        const val KEY_ACTIVE = "bg_active"
        const val KEY_WALLET   = "bg_wallet"
        const val KEY_CYCLES   = "bg_cycles"    // compatibilidade
        const val KEY_END_TIME = "bg_end_time"  // compatibilidade

        const val EXTRA_WALLET   = "wallet_name"
        const val ACTION_STOP    = "com.example.waspbrowser.BEE_STOP"
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

        // Mantido por compatibilidade com código antigo
        fun remainingMs(context: Context): Long = Long.MAX_VALUE
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

        walletName = intent?.getStringExtra(EXTRA_WALLET) ?: ""

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName, tickCount))
        Log.d(TAG, "Mineração em background iniciada | wallet=$walletName")

        scheduleTick()
        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        stopMining()
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }

    // ─── Tick loop ───────────────────────────────────────────────────────────

    private fun scheduleTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = Runnable {
            tickCount++
            Log.d(TAG, "Tick #$tickCount | wallet=$walletName")

            // Cérebro: verifica se miner está rodando e reinicia se necessário
            checkAndRestartMiner()

            // Atualiza notificação
            val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notifManager.notify(NOTIF_ID, buildNotification(walletName, tickCount))

            scheduleTick()
        }
        handler.postDelayed(tickRunnable!!, TICK_INTERVAL)
    }

    /**
     * Núcleo da solução: o Service verifica via JS se o miner está ativo.
     * Se não estiver (epoch terminou, WebView pausou, qualquer motivo),
     * dispara o restart direto — sem esperar o usuário abrir o painel.
     */
    private fun checkAndRestartMiner() {
        val js = """
            (function(){
                try {
                    // Verifica se miner está rodando
                    var isMining = window._mining === true;
                    var autoMine = false;
                    try {
                        var st = localStorage.getItem('wasp_bee_state_v6');
                        if (st) autoMine = JSON.parse(st).autoMine;
                    } catch(_) {}

                    console.log('[BgService] Tick — isMining=' + isMining + ' autoMine=' + autoMine);

                    if (autoMine && !isMining) {
                        console.log('[BgService] Miner parado detectado — reiniciando...');
                        if (typeof window.onAppResume === 'function') {
                            window.onAppResume();
                        }
                    }
                } catch(e) {
                    console.error('[BgService] checkAndRestart erro: ' + e);
                }
            })()
        """.trimIndent()

        BeeActivity.runJs(js)

        // Também envia broadcast para manter a BeeActivity acordada
        sendBroadcast(Intent(ACTION_KEEP_ALIVE).apply {
            setPackage(packageName)
            putExtra("tick", tickCount)
            putExtra("wallet", walletName)
        })
    }

    private fun stopMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
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

        val epochsStr = if (ticks > 0) " • ${ticks} checks" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("🐝 Minerando NACKL")
            .setContentText(
                if (wallet.isNotBlank()) "Wallet: $wallet$epochsStr"
                else "Mineração ativa$epochsStr"
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_bee_tech, "Parar", stopIntent)
            .build()
    }
}
