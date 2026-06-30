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
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * BeeBackgroundService — mantém a mineração viva com o app minimizado / tela apagada.
 *
 * ARQUITETURA (uma única WebView):
 * - NÃO cria WebView própria. A mineração acontece na WebView persistente do
 *   painel (MainActivity / BeeActivity), que nunca é destruída.
 * - Este serviço apenas: (1) roda em foreground com WakeLock para o processo
 *   não ser suspenso, (2) a cada tick chama resumeTimers()/onResume() na WebView
 *   persistente para os timers JS (epoch, auto-tap, get_reward) continuarem
 *   rodando em background, e (3) atualiza a notificação com o estado real lido
 *   de window.getMiningState().
 *
 * Isso elimina o conflito de "duas sessões no mesmo miner_address" (stale 410)
 * e a sobrecarga de carregar o WASM duas vezes — que era o que congelava o painel.
 */
class BeeBackgroundService : Service() {

    companion object {
        private const val TAG          = "BeeBackgroundService"
        private const val CHANNEL_ID   = "bee_mining_channel"
        private const val NOTIF_ID     = 42
        private const val TICK_MS      = 10_000L   // keep-alive a cada 10s

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

        // Mantido por compatibilidade com chamadas existentes. As chaves não são
        // mais necessárias aqui (a WebView do painel já as tem) — apenas o wallet
        // name é usado para o texto da notificação.
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

    private val handler    = Handler(Looper.getMainLooper())
    private var walletName = ""
    private var wakeLock   : PowerManager.WakeLock? = null
    private var ticking    = false

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
        // Ações legadas de epoch viraram no-op — o timing agora é 100% da WebView.
        if (intent?.action == ACTION_EPOCH_ENDED ||
            intent?.action == "com.waspbrowser.app.BEE_EPOCH_STARTED") {
            return START_STICKY
        }

        walletName = intent?.getStringExtra(EXTRA_WALLET)?.takeIf { it.isNotBlank() }
            ?: getSharedPreferences(PREFS_BG, MODE_PRIVATE).getString(KEY_WALLET, "") ?: ""

        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_WALLET, walletName)
            .apply()

        startForeground(NOTIF_ID, buildNotification("Mantendo mineração ativa…"))
        startTicking()
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ── Keep-alive: mantém os timers JS da WebView persistente vivos ──────────

    private fun startTicking() {
        if (ticking) return
        ticking = true
        scheduleTick()
    }

    private fun scheduleTick() {
        handler.postDelayed({
            if (!ticking) return@postDelayed
            keepAliveTick()
            scheduleTick()
        }, TICK_MS)
    }

    private fun keepAliveTick() {
        val wv = BeeActivity.getPersistentWebView()
        if (wv == null) {
            // App foi fechado/swiped — a WebView que minera não existe mais.
            updateNotification("Toque para abrir o Wasp e continuar minerando")
            return
        }
        wv.post {
            runCatching {
                wv.resumeTimers()
                wv.onResume()
                // Lê o estado real da mineração e atualiza a notificação.
                wv.evaluateJavascript(
                    "(function(){try{return (window.getMiningState&&window.getMiningState())||{};}catch(e){return {};}})()"
                ) { result -> handler.post { updateNotificationFromState(result) } }
            }
        }
    }

    private fun updateNotificationFromState(json: String?) {
        val text = try {
            // evaluateJavascript devolve o objeto já em JSON (ex.: {"mining":true,...})
            val clean = json?.takeIf { it.isNotBlank() && it != "null" } ?: "{}"
            val o = JSONObject(clean)
            val mining     = o.optBoolean("mining", false)
            val wallet     = o.optString("wallet", walletName)
            val taps       = o.optInt("tapCount", 0)
            val tapTotal   = o.optInt("tapTotal", 80)
            val epochsPaid = o.optInt("epochsPaid", 0)
            if (wallet.isNotBlank()) walletName = wallet
            when {
                mining     -> "⛏ Minerando • $taps/$tapTotal • epochs pagos: $epochsPaid"
                else       -> "⏳ Aguardando próximo epoch • epochs pagos: $epochsPaid"
            }
        } catch (e: Exception) {
            "Mantendo mineração ativa…"
        }
        updateNotification(text)
    }

    // ── WakeLock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaspBrowser:BgMinerLock")
        wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 horas
    }

    // ── Shutdown ─────────────────────────────────────────────────────────────

    private fun shutdown() {
        ticking = false
        handler.removeCallbacksAndMessages(null)
        try { wakeLock?.release(); wakeLock = null } catch (_: Exception) {}
        getSharedPreferences(PREFS_BG, MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    // ── Notificação ──────────────────────────────────────────────────────────

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
                putExtra("navigate_to", "bee")
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
