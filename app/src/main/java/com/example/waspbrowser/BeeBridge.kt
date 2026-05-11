package com.example.waspbrowser

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

class BeeBridge(
    private val context: Context,
    private val openUrlCallback: (String) -> Unit = {}
) {

    @JavascriptInterface
    fun openSettings(target: String? = null) {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (target == "language") {
                    putExtra("show_language_picker", true)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("BeeBridge", "openSettings: ${e.message}")
        }
    }

    @JavascriptInterface
    fun goHome() {
        (context as? MainActivity)?.runOnUiThread { context.goHome() }
    }

    @JavascriptInterface
    fun openBeePanel() {
        (context as? MainActivity)?.runOnUiThread { context.openBeePanel() }
    }

    @JavascriptInterface
    fun onEpochEnd(wallet: String) {
        // Só alerta se o painel não está visível — se estiver aberto o usuário já vê tudo
        if (!BeeActivity.isVisible) {
            EpochAlertActivity.notify(context, wallet)
        }
    }

    @JavascriptInterface
    fun getMiningStatus(): String {
        val bgActive = BeeBackgroundService.isActive(context)
        val beeActive = context
            .getSharedPreferences("bee_mining", Context.MODE_PRIVATE)
            .getBoolean("mining_active", false)
        val active = bgActive || beeActive
        val remaining = BeeBackgroundService.remainingMs(context)
        return """{"running":$active,"bgMining":$bgActive,"remainingMs":$remaining}"""
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        try { openUrlCallback(url) }
        catch (e: Exception) { }
    }

    // ─── Bg Mining via WP ────────────────────────────────────────────────────

    /**
     * Chamado pelo tap.js quando o usuário gasta WP para ativar mineração bg.
     * @param durationMs duração em milissegundos (ex: 15 * 60 * 1000 = 15 min)
     * @param walletName nome da wallet para mostrar na notificação
     */
    @JavascriptInterface
    fun startBgMining(durationMs: Long, walletName: String) {
        try {
            if (durationMs <= 0L) {
                Log.w("BeeBridge", "startBgMining: duração inválida $durationMs")
                return
            }
            val intent = BeeBackgroundService.buildStartIntent(context, durationMs, walletName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d("BeeBridge", "startBgMining: ${durationMs / 60000} min | wallet=$walletName")
        } catch (e: Exception) {
            Log.e("BeeBridge", "startBgMining error: ${e.message}")
        }
    }

    /**
     * Para a mineração em background imediatamente.
     */
    @JavascriptInterface
    fun stopBgMining() {
        try {
            context.startService(BeeBackgroundService.buildStopIntent(context))
            Log.d("BeeBridge", "stopBgMining chamado")
        } catch (e: Exception) {
            Log.e("BeeBridge", "stopBgMining error: ${e.message}")
        }
    }

    /**
     * Retorna JSON com estado do bg mining para o JS consultar.
     */
    @JavascriptInterface
    fun getBgMiningStatus(): String {
        val active = BeeBackgroundService.isActive(context)
        val remaining = BeeBackgroundService.remainingMs(context)
        val prefs = context.getSharedPreferences(BeeBackgroundService.PREFS_BG, Context.MODE_PRIVATE)
        val cycles = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
        val wallet = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
        return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    @JavascriptInterface
    fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d("BeeBridge", msg)
    }

    // ─── Mining status sync ──────────────────────────────────────────────────

    @JavascriptInterface
    fun setMiningStatus(active: Boolean, wallet: String) {
        (context as? MainActivity)?.runOnUiThread {
            context.onMiningStatusChanged(active, wallet)
        }
    }
}
