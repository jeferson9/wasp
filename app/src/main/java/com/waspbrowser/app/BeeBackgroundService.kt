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
import androidx.core.app.NotificationCompat

/**
 * BeeBackgroundService — mantém o processo vivo enquanto o Bee Engine minera.
 *
 * Responsabilidades:
 *  - Rodar como ForegroundService com START_STICKY
 *  - Chamar onAppResume() na WebView persistente a cada TICK_MS
 *    para que o bee_engine.js reative a mineração se necessário
 *  - NÃO tentar controlar epochs diretamente — o bee_engine.js
 *    já tem toda a lógica de claim + restart internamente
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
            // Verifica se o serviço realmente está rodando
            // Nota: getRunningServices é deprecated mas ainda funciona para serviços próprios
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
                // Fallback para Xiaomi MIUI que pode restringir getRunningServices
                prefActive
            }
        }

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

        // Ignora epoch actions — o JS controla internamente
        if (intent?.action == ACTION_EPOCH_ENDED ||
            intent?.action == "com.waspbrowser.app.BEE_EPOCH_STARTED") {
            Log.d(TAG, "Epoch event recebido — ignorado (JS controla o restart)")
            return START_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET) ?: walletName

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName))
        Log.d(TAG, "Service iniciado | wallet=$walletName")

        scheduleTick()
        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        stopMining()
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }

    private fun scheduleTick() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = Runnable {
            tickCount++
            Log.d(TAG, "Tick #$tickCount | wallet=$walletName")

            // Apenas acorda o WebView e deixa o bee_engine.js decidir o que fazer
            BeeActivity.runJs("if(window.onAppResume) window.onAppResume()")

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
            .addAction(R.drawable.ic_bee_tech, "Parar", stop)
            .build()
    }
}
