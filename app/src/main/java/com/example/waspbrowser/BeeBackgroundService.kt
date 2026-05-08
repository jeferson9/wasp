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
 * Foreground Service que mantém a mineração NACKL ativa via Keep-Alive broadcasts.
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG = "BeeBackgroundService"
        private const val CHANNEL_ID = "bee_mining_channel"
        private const val NOTIF_ID = 42

        // 15s agressivo para combater suspensão do WebView pelo Android
        private const val TICK_INTERVAL = 15_000L

        const val PREFS_BG   = "bee_bg_mining"
        const val KEY_ACTIVE   = "bg_active"
        const val KEY_END_TIME = "bg_end_time"
        const val KEY_CYCLES   = "bg_cycles"
        const val KEY_WALLET   = "bg_wallet"

        const val EXTRA_DURATION = "duration_ms"
        const val EXTRA_WALLET   = "wallet_name"
        const val ACTION_STOP       = "com.example.waspbrowser.BEE_STOP"
        const val ACTION_KEEP_ALIVE = "com.example.waspbrowser.BEE_KEEP_ALIVE"

        fun buildStartIntent(context: Context, durationMs: Long, walletName: String): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                putExtra(EXTRA_DURATION, durationMs)
                putExtra(EXTRA_WALLET, walletName)
            }

        fun buildStopIntent(context: Context): Intent =
            Intent(context, BeeBackgroundService::class.java).apply {
                action = ACTION_STOP
            }

        fun isActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
            val endTime = prefs.getLong(KEY_END_TIME, 0L)
            return prefs.getBoolean(KEY_ACTIVE, false) && System.currentTimeMillis() < endTime
        }

        fun remainingMs(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_BG, Context.MODE_PRIVATE)
            return maxOf(0L, prefs.getLong(KEY_END_TIME, 0L) - System.currentTimeMillis())
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var endTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBgMining(); stopSelf()
            return START_NOT_STICKY
        }

        val durationMs = intent?.getLongExtra(EXTRA_DURATION, 0L) ?: 0L
        val walletName = intent?.getStringExtra(EXTRA_WALLET) ?: ""

        if (durationMs <= 0L) { stopSelf(); return START_NOT_STICKY }

        endTime = System.currentTimeMillis() + durationMs

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_END_TIME, endTime)
            .putString(KEY_WALLET, walletName)
            .putInt(KEY_CYCLES, 0)
            .apply()

        getSharedPreferences("bee_mining", MODE_PRIVATE).edit()
            .putBoolean("mining_active", true)
            .apply()

        startForeground(NOTIF_ID, buildNotification(walletName, durationMs))
        scheduleNextTick(walletName)

        return START_STICKY
    }

    override fun onDestroy() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun scheduleNextTick(walletName: String) {
        tickRunnable?.let { handler.removeCallbacks(it) }

        tickRunnable = Runnable {
            val now = System.currentTimeMillis()
            if (now >= endTime) {
                Log.d(TAG, "Bg mining expirou — parando")
                stopBgMining(); stopSelf()
                return@Runnable
            }

            val prefs = getSharedPreferences(PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(KEY_CYCLES, 0) + 1
            prefs.edit().putInt(KEY_CYCLES, cycles).apply()

            val remaining = endTime - now
            Log.d(TAG, "Tick #$cycles | restam ${remaining / 1000}s")

            // Broadcast keep-alive para acordar BeeActivity/WebView
            sendBroadcast(Intent(ACTION_KEEP_ALIVE).apply {
                setPackage(packageName)
                putExtra("cycles", cycles)
                putExtra("remaining_ms", remaining)
                putExtra("wallet", walletName)
            })

            // Atualiza notificação
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(walletName, remaining))

            scheduleNextTick(walletName)
        }
        handler.postDelayed(tickRunnable!!, TICK_INTERVAL)
    }

    private fun stopBgMining() {
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false).apply()
        getSharedPreferences("bee_mining", MODE_PRIVATE).edit()
            .putBoolean("mining_active", false).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Bee Mining", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
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
            .setContentText("Wallet: $walletName • Restam $timeStr")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_bee_tech, "Parar", stopIntent)
            .build()
    }
}
