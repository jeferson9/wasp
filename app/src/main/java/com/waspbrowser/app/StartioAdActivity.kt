package com.waspbrowser.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

class StartioAdActivity : Activity() {

    companion object {
        const val TAG       = "StartioAdActivity"
        const val EXTRA_MODE = "ad_mode"
        const val APP_ID    = "204731691"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewarded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: "wp"

        fun diag(m: String) {
            Log.d(TAG, m)
            runCatching { android.widget.Toast.makeText(this, "Ad: $m", android.widget.Toast.LENGTH_SHORT).show() }
        }

        diag("preparando")
        // SDK já inicializado no SplashActivity; init aqui é defensivo/idempotente.
        runCatching {
            StartAppSDK.init(this, APP_ID, false)
            StartAppSDK.setTestAdsEnabled(true)
        }

        val ad = StartAppAd(this)

        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "✅ Video completado")
        })

        diag("carregando video...")
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(p: Ad) {
                diag("carregado, exibindo")
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(p0: Ad) {
                        Log.d(TAG, "adHidden rewarded=$rewarded")
                        if (rewarded) {
                            // Crédito robusto: grava cooldown nativo + credita WP via JS
                            CentralActivity.grantAdReward(applicationContext)
                        }
                        handler.postDelayed({ finish() }, 200)
                    }
                    override fun adDisplayed(p0: Ad) {}
                    override fun adClicked(p0: Ad) {}
                    override fun adNotDisplayed(p0: Ad) {
                        diag("não exibido")
                        handler.post { finish() }
                    }
                })
            }
            override fun onFailedToReceiveAd(p: Ad?) {
                diag("sem anúncio disponível")
                handler.post { finish() }
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
