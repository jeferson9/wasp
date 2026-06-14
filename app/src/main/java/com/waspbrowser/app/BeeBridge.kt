package com.waspbrowser.app

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
    fun openSettings() {
        (context as? MainActivity)?.runOnUiThread {
            context.openSettingsPanel()
        }
    }

    @JavascriptInterface
    fun openSettings(target: String?) {
        (context as? MainActivity)?.runOnUiThread {
            context.openSettingsPanel()
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
                Log.w("BeeBridge", "startBgMining: invalid duration $durationMs")
                return
            }
            val intent = BeeBackgroundService.buildStartIntent(context, walletName)
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

    @JavascriptInterface
    fun openHiveManager(sitesJson: String) {
        (context as? MainActivity)?.runOnUiThread {
            val intent = android.content.Intent(context, HiveManagerActivity::class.java)
            intent.putExtra(HiveManagerActivity.EXTRA_SITES, sitesJson)
            (context as? MainActivity)?.startActivityForResult(intent, HiveManagerActivity.REQUEST_CODE)
        }
    }

    @JavascriptInterface
    fun openPanel() {
        (context as? MainActivity)?.runOnUiThread {
            context.openBeePanel()
        }
    }

    @JavascriptInterface
    fun clearBrowsingHistory() {
        (context as? MainActivity)?.clearBrowsingHistory()
    }

    @JavascriptInterface
    fun clearCacheAndCookies() {
        (context as? MainActivity)?.clearCacheAndCookies()
    }
}
