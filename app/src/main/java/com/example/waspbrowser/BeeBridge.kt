package com.example.waspbrowser

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface

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
    fun getMiningStatus(): String {
        val active = (context as? MainActivity)
            ?.getSharedPreferences("bee_mining", android.content.Context.MODE_PRIVATE)
            ?.getBoolean("mining_active", false) ?: false
        return """{"running":$active}"""
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        try { openUrlCallback(url) }
        catch (e: Exception) { }
    }
}
