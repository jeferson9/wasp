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
 * BeeBackgroundService
 *
 * Foreground Service que mantém a mineração NACKL ativa enquanto
 * o usuário navega no Wasp Browser.
 *
 * Ciclo de vida:
 *  - Iniciado via BeeBridge.startBgMining(durationMs)
 *  - Roda por [durationMs] milissegundos (ou até ser parado manualmente)
 *  - A cada TICK_INTERVAL registra um ciclo de mineração em SharedPreferences
 *  - MainActivity / BeeActivity lêem esse estado para sincronizar a UI
 *
 * Integração com WP:
 *  - O tap.js chama AndroidBee.startBgMining(durationMs) ao gastar WP
 *  - O service atualiza o contador de ciclos que o bee_engine.js pode ler
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG = "BeeBackgroundService"
        private const val CHANNEL_ID = "bee_mining_channel"
        private const val NOTIF_ID = 42

        // Intervalo entre "ticks" de mineração (30 segundos)
        private const val TICK_INTERVAL = 15_000L

        // Chaves SharedPreferences (mesmas lidas pelo bee_engine.js via bridge)
        const val PREFS_BG = "bee_bg_mining"
        const val KEY_ACTIVE = "bg_active"
        const val KEY_END_TIME = "bg_end_time"
        const val KEY_CYCLES = "bg_cycles"
        const val KEY_WALLET = "bg_wallet"

        // Extras do Intent
        const val EXTRA_DURATION = "duration_ms"
        const val EXTRA_WALLET = "wallet_name"
        const val ACTION_STOP = "com.example.waspbrowser.BEE_STOP"
        const val ACTION_KEEP_ALIVE = "com.example.waspbrowser.BEE_KEEP_ALIVE"

        fun buildStartIntent(context: Context, durationMs: Long, walletName: String): Intent {
            return Intent(context, BeeBackgroundService::class.java).apply {
                putExtra(EXTRA_DURATION, durationMs)
                putExtra(EXTRA_WALLET, walletName)
            }
        }

        fun buildStopIntent(context: Context): Intent {
            return Intent(context, BeeBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
        }

        /** Lê se o serviço está ativo (pode ser chamado de qualquer lugar) */
        fun isActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
            val endTime = prefs.getLong(KEY_END_TIME, 0L)
            return prefs.getBoolean(KEY_ACTIVE, false) && System.currentTimeMillis() < endTime
        }

        /** Retorna quantos ms restam de bg mining */
        fun remainingMs(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
            return maxOf(0L, prefs.getLong(KEY_END_TIME, 0L) - System.currentTimeMillis())
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var endTime = 0L

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service criado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Parar manualmente
        if (intent?.action == ACTION_STOP) {
            stopBgMining()
            stopSelf()
            return START_NOT_STICKY
        }

        val durationMs = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        val walletName = intent?.getStringExtra(EXTRA_WALLET) ?: ""

        if (durationMs <= 0L) {
            Log.w(TAG, "Duração inválida — parando service")
            stopSelf()
            return START_NOT_STICKY
        }

        endTime = System.currentTimeMillis() + durationMs

        // Persiste estado
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_END_TIME, endTime)
            .putString(KEY_WALLET, walletName)
            .apply()

        // Também sincroniza com o prefs que a MainActivity usa
        getSharedPreferences("bee_mining", MODE_PRIVATE).edit()
            .putBoolean("mining_active", true)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName, durationMs))
        Log.d(TAG, "Bg mining iniciado por ${durationMs / 60000} min | wallet=$walletName")

        scheduleNextTick(walletName)

        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        stopBgMining()
        Log.d(TAG, "Service destruído")
        super.onDestroy()
    }

    // ─── Tick loop ───────────────────────────────────────────────────────────

    private fun scheduleNextTick(walletName: String) {
        tickRunnable?.let { handler.removeCallbacks(it) }

        tickRunnable = Runnable {
            val now = System.currentTimeMillis()
            if (now >= endTime) {
                Log.d(TAG, "Bg mining expirou — parando")
                stopBgMining()
                stopSelf()
                return@Runnable
            }

            // Registra ciclo
            val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(KEY_CYCLES, 0) + 1
            prefs.edit().putInt(KEY_CYCLES, cycles).apply()
            Log.d(TAG, "Tick #$cycles | restam ${(endTime - now) / 1000}s")

            // ── KEEP-ALIVE: executa JS diretamente no WebView via BeeActivity.runJs()
            // Mais confiável que broadcast — funciona mesmo com WebView em background
            sendBroadcast(Intent(ACTION_KEEP_ALIVE).apply {
                setPackage(packageName)
                putExtra("cycles", cycles)
                putExtra("remaining_ms", endTime - now)
                putExtra("wallet", walletName)
            })
            // Apenas desengela os timers JS — deixa o engine decidir o que fazer
            BeeActivity.runJs("window.__bgTick = (window.__bgTick||0)+1;")

            // Atualiza notificação com tempo restante
            val remaining = endTime - now
            val notifManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notifManager.notify(NOTIF_ID, buildNotification(walletName, remaining))

            scheduleNextTick(walletName)
        }

        handler.postDelayed(tickRunnable!!, TICK_INTERVAL)
    }

    private fun stopBgMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
        getSharedPreferences("bee_mining", MODE_PRIVATE).edit()
            .putBoolean("mining_active", false)
            .apply()
    }

    // ─── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bee Mining",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mineração NACKL em segundo plano"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(walletName: String, remainingMs: Long): Notification {
        val mins = remainingMs / 60000
        val secs = (remainingMs % 60000) / 1000
        val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

        val stopIntent = PendingIntent.getService(
            this, 0, buildStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bee_tech)
            .setContentTitle("🐝 Minerando NACKL")
            .setContentText(
                if (walletName.isNotBlank()) "Wallet: $walletName • Restam $timeStr"
                else "Mineração ativa • Restam $timeStr"
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.ic_bee_tech,
                "Parar",
                stopIntent
            )
            .build()
    }
}
