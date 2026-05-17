package com.example.waspbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * Bridge JavaScript específico do Bee Dock que mora dentro da [MainActivity].
 *
 * Expõe `window.AndroidBee` para `bee/index.html` — exatamente o mesmo nome
 * que a [BeeActivity] expõe. Assim, o `bee_engine.js` funciona sem nenhuma
 * alteração: ele não sabe (nem precisa saber) se está rodando dentro do
 * BeeDock da MainActivity ou da BeeActivity stand-alone.
 *
 * Métodos foram copiados verbatim da inner class `BeeBridge` da [BeeActivity],
 * adaptando apenas as chamadas que envolvem ciclo de vida de Activity
 * (`goBack`, `navigateTo`) para colapsar o dock em vez de iniciar uma nova
 * Activity, e os ads de energia/WP para usarem o RewardedAd da MainActivity.
 */
class BeeDockBridge(private val activity: MainActivity) {

    companion object {
        private const val TAG = "BeeDockBridge"
        private const val PREFS_BEE_ENERGY = "bee_energy"
        private const val PREFS_MINING     = "bee_mining"
        private const val KEY_MINING_ACTIVE = "mining_active"
        private const val KEY_ENERGY_READY = "energy_ready"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun ping(): String = "pong"

    @JavascriptInterface
    fun toast(m: String) {
        mainHandler.post { Toast.makeText(activity, m, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun openDeepLink(url: String) {
        Log.d(TAG, "openDeepLink: ${url.take(100)}")
        mainHandler.post { openExternal(url) }
    }

    @JavascriptInterface
    fun openExternalUrl(url: String) {
        Log.d(TAG, "openExternalUrl: ${url.take(100)}")
        mainHandler.post { openExternal(url) }
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) return
        try {
            val intent = if (url.startsWith("intent://")) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!url.startsWith("intent://")) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openExternal err: ${e.message}")
            mainHandler.post {
                Toast.makeText(activity, "Não foi possível abrir este link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun openEnergyPage() {
        mainHandler.post { activity.showBeeRewardedAd("energy") }
    }

    @JavascriptInterface
    fun openWpAd() {
        mainHandler.post { activity.showBeeRewardedAd("wp") }
    }

    @JavascriptInterface
    fun isEnergyReady(): Boolean {
        return activity.getSharedPreferences(PREFS_BEE_ENERGY, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENERGY_READY, false)
    }

    @JavascriptInterface
    fun clearEnergyReady() {
        activity.getSharedPreferences(PREFS_BEE_ENERGY, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENERGY_READY, false).apply()
    }

    /**
     * Antes ia para a MainActivity via Intent. Agora apenas colapsa o dock,
     * já que estamos NA MainActivity.
     */
    @JavascriptInterface
    fun navigateTo(screen: String) {
        mainHandler.post { activity.collapseBeeDock() }
    }

    /**
     * "Voltar" dentro do painel agora colapsa o dock para o rodapé,
     * mantendo o WebView vivo e a mineração rodando.
     */
    @JavascriptInterface
    fun goBack() {
        mainHandler.post { activity.collapseBeeDock() }
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d("BeeDockBridgeJS", msg)
    }

    /**
     * O bee_engine.js notifica esse callback no início/fim de mineração.
     * Reflete imediatamente no indicador da toolbar e salva nos prefs.
     */
    @JavascriptInterface
    fun setMiningStatus(active: Boolean, wallet: String) {
        Log.d(TAG, "setMiningStatus: $active wallet=$wallet")
        mainHandler.post { activity.onMiningStatusChanged(active, wallet) }
    }

    @JavascriptInterface
    fun setMiningActive(active: Boolean) {
        activity.getSharedPreferences(PREFS_MINING, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
    }

    @JavascriptInterface
    fun getMiningStatus(): String {
        val active = activity.getSharedPreferences(PREFS_MINING, Context.MODE_PRIVATE)
            .getBoolean(KEY_MINING_ACTIVE, false)
        return """{"running":$active}"""
    }

    @JavascriptInterface
    fun openCentral() {
        mainHandler.post {
            val intent = Intent(activity, CentralActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opts = android.app.ActivityOptions
                .makeCustomAnimation(activity, R.anim.slide_up, R.anim.fade_out)
            activity.startActivity(intent, opts.toBundle())
        }
    }

    @JavascriptInterface
    fun hasWasm(): Boolean {
        return try {
            activity.assets.open("bee/bee_sdk_bg.wasm").close()
            true
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun checkAssets(): String {
        return try {
            activity.assets.list("bee")?.joinToString(", ") ?: "vazio"
        } catch (e: Exception) { "erro: ${e.message}" }
    }

    // ─── Background mining via WP (delegado ao BeeBackgroundService) ─────────

    @JavascriptInterface
    fun startBgMining(durationMs: Long, walletName: String) {
        Log.d(TAG, "startBgMining: ${durationMs / 60000}min wallet=$walletName")
        try {
            val intent = BeeBackgroundService.buildStartIntent(activity, durationMs, walletName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent)
            } else {
                activity.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startBgMining error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun stopBgMining() {
        try {
            activity.startService(BeeBackgroundService.buildStopIntent(activity))
        } catch (e: Exception) {
            Log.e(TAG, "stopBgMining error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getBgMiningStatus(): String {
        val active = BeeBackgroundService.isActive(activity)
        val remaining = BeeBackgroundService.remainingMs(activity)
        val prefs = activity.getSharedPreferences(
            BeeBackgroundService.PREFS_BG, Context.MODE_PRIVATE
        )
        val cycles = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
        val wallet = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
        return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
    }
}
