package com.example.waspbrowser

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
        private const val TAG = "StartioAdActivity"
        const val EXTRA_MODE  = "ad_mode"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewarded = false
    private var mode = "wp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: "wp"
        Log.d(TAG, "onCreate mode=$mode")

        // Tela transparente
        window.setBackgroundDrawableResource(android.R.color.transparent)

        StartAppSDK.setTestAdsEnabled(true)

        val ad = StartAppAd(this)

        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA")
        })

        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad carregado — exibindo")
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(p0: Ad) {
                        Log.d(TAG, "adHidden rewarded=$rewarded")
                        deliver()
                    }
                    override fun adDisplayed(p0: Ad) {
                        Log.d(TAG, "adDisplayed ✅")
                    }
                    override fun adClicked(p0: Ad) {}
                    override fun adNotDisplayed(p0: Ad) {
                        Log.e(TAG, "adNotDisplayed: ${p0.errorMessage}")
                        deliver()
                    }
                })
            }
            override fun onFailedToReceiveAd(failedAd: Ad?) {
                Log.e(TAG, "Falhou: ${failedAd?.errorMessage}")
                deliver()
            }
        })
    }

    private fun deliver() {
        val js = when {
            mode == "wp"     && rewarded  -> "if(window.onWpAdRewarded)   window.onWpAdRewarded()"
            mode == "wp"     && !rewarded -> "if(window.onWpAdClosed)     window.onWpAdClosed()"
            mode == "energy" && rewarded  -> "if(window.onEnergyRewarded) window.onEnergyRewarded()"
            else                          -> "if(window.onEnergyAdClosed) window.onEnergyAdClosed()"
        }
        handler.postDelayed({
            BeeActivity.runJs(js)
            finish()
        }, 300)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
