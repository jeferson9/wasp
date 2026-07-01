package com.waspbrowser.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

class BeeBridge(
    private val context: Context,
    private val openUrlCallback: (String) -> Unit = {},
    private val activity: Activity? = context as? Activity
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

    @JavascriptInterface
    fun openExternalUrl(url: String) {
        // Abre no app externo (Telegram, browser do sistema) via Intent
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("BeeBridge", "openExternalUrl error: ${e.message}")
        }
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

    @JavascriptInterface
    fun closePanel() {
        Handler(Looper.getMainLooper()).post {
            Log.d("BeeBridge", "closePanel: activity=$activity")
            activity?.finish()
        }
    }

    // ─── Configurações do Bee (menu Configurações → Bee Engine) ──────────────

    private fun beePrefs() =
        context.getSharedPreferences("bee_mining", Context.MODE_PRIVATE)

    /** Estado atual das opções, para o menu renderizar. */
    @JavascriptInterface
    fun getBeeConfig(): String {
        val p = beePrefs()
        val autostart = p.getBoolean("autostart_on_open", false)
        val keepBg    = p.getBoolean("keep_background", false)
        val optimized = try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName).not()
        } catch (_: Exception) { false }
        return """{"autostartOnOpen":$autostart,"keepBackground":$keepBg,"batteryOptimized":$optimized,"aggressiveOem":${isAggressiveOem()}}"""
    }

    @JavascriptInterface
    fun setBeeAutostart(enabled: Boolean) {
        beePrefs().edit().putBoolean("autostart_on_open", enabled).apply()
    }

    @JavascriptInterface
    fun setBeeKeepBackground(enabled: Boolean) {
        beePrefs().edit().putBoolean("keep_background", enabled).apply()
    }

    /** Fabricantes conhecidos por matar apps em background de forma agressiva. */
    @JavascriptInterface
    fun isAggressiveOem(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "realme",
            "oneplus", "huawei", "honor", "meizu", "letv", "iqoo")
            .any { m.contains(it) }
    }

    /** Abre a LISTA de otimização de bateria (sem permissão especial — seguro no Play). */
    @JavascriptInterface
    fun openBatterySettings() {
        Handler(Looper.getMainLooper()).post {
            try {
                context.startActivity(Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                openAppDetails()
            }
        }
    }

    /** Abre a tela de Autostart do fabricante (só Intent — sem risco de política). */
    @JavascriptInterface
    fun openAutostartSettings() {
        Handler(Looper.getMainLooper()).post {
            val components = listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
                "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
            )
            for ((pkg, cls) in components) {
                try {
                    val i = Intent().setComponent(android.content.ComponentName(pkg, cls))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (i.resolveActivity(context.packageManager) != null) {
                        context.startActivity(i); return@post
                    }
                } catch (_: Exception) {}
            }
            openAppDetails() // fallback universal
        }
    }

    private fun openAppDetails() {
        try {
            context.startActivity(Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun startBgMiningFull(walletName: String, minerAddress: String, publicKey: String, secretKey: String) {
        Log.d("BeeBridge", "startBgMiningFull: wallet=$walletName")
        try {
            context.getSharedPreferences(BeeBackgroundService.PREFS_BG, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(BeeBackgroundService.KEY_MINER_ADDR, minerAddress)
                .putString(BeeBackgroundService.KEY_PUBLIC_KEY, publicKey)
                .putString(BeeBackgroundService.KEY_SECRET_KEY, secretKey)
                .apply()
            val intent = BeeBackgroundService.buildStartIntentFull(
                context, walletName, minerAddress, publicKey, secretKey
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e("BeeBridge", "startBgMiningFull error: ${e.message}")
        }
    }
}
