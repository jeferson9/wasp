package com.waspbrowser.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import android.content.Intent

class WaspSecureMode : AppCompatActivity() {

    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wasp_secure_mode)

        val logo = findViewById<ImageView>(R.id.logoWasp)

        // animação
        val anim = AlphaAnimation(0.3f, 1f)
        anim.duration = 600
        anim.repeatMode = AlphaAnimation.REVERSE
        anim.repeatCount = AlphaAnimation.INFINITE
        logo.startAnimation(anim)

        val url = intent.getStringExtra("url") ?: return

        Handler(Looper.getMainLooper()).postDelayed({

            openCustomTab(url)
            opened = true

        }, 600)
    }

    private fun openCustomTab(url: String) {

        val builder = CustomTabsIntent.Builder()

        builder.setToolbarColor(android.graphics.Color.parseColor("#000000"))
        builder.setShowTitle(true)

        val customTabsIntent = builder.build()

        // manter dentro do navegador
        customTabsIntent.intent.setPackage("com.android.chrome")

        val uri = Uri.parse(url)

        try {
            customTabsIntent.launchUrl(this, uri)
        } catch (e: Exception) {
            // fallback se algo der errado
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        if (opened) {
            finish()
        }
    }
}