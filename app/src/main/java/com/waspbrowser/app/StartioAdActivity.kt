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

        diag("init SDK")
        StartAppSDK.init(this, APP_ID, false)
        StartAppSDK.setTestAdsEnabled(true)

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
                        val js = if (rewarded)
                            "if(window.onWpAdRewarded) window.onWpAdRewarded()"
                        else
                            "if(window.onWpAdClosed) window.onWpAdClosed()"
                        handler.postDelayed({ CentralActivity.runJs(js); finish() }, 300)
                    }
                    override fun adDisplayed(p0: Ad) {}
                    override fun adClicked(p0: Ad) {}
                    override fun adNotDisplayed(p0: Ad) {
                        diag("adNotDisplayed")
                        handler.post { CentralActivity.runJs("if(window.onWpAdClosed) window.onWpAdClosed()"); finish() }
                    }
                })
            }
            override fun onFailedToReceiveAd(p: Ad?) {
                diag("FALHOU ao carregar")
                handler.post { CentralActivity.runJs("if(window.onWpAdClosed) window.onWpAdClosed()"); finish() }
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
