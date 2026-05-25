package com.example.waspbrowser

import android.app.Activity
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

object StartioAdManager {

    private const val TAG   = "StartioAdManager"
    const val APP_ID        = "test-app-id"

    private var isShowing   = false
    private var initialized = false
    private var mainActivityRef: java.lang.ref.WeakReference<MainActivity>? = null

    fun init(activity: MainActivity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
        if (initialized) return
        initialized = true
        StartAppSDK.init(activity, APP_ID, false)
        StartAppSDK.setTestAdsEnabled(true)
        Log.d(TAG, "Start.io iniciado — modo teste ON")
    }

    fun show(mode: String, callerActivity: Activity? = null) {
        if (isShowing) { Log.w(TAG, "Ad já exibindo"); return }

        val activity = callerActivity ?: mainActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "Nenhuma Activity disponível")
            deliverJs(mode, false)
            return
        }

        isShowing = true
        var rewarded = false

        Log.d(TAG, "Carregando ad mode=$mode")

        val ad = StartAppAd(activity)

        // Listener de display — declarado antes do showAd
        val displayListener = object : AdDisplayListener {
            override fun adHidden(p0: Ad?) {
                Log.d(TAG, "adHidden rewarded=$rewarded")
                isShowing = false
                deliverJs(mode, rewarded)
            }
            override fun adDisplayed(p0: Ad?) { Log.d(TAG, "adDisplayed") }
            override fun adClicked(p0: Ad?)   { Log.d(TAG, "adClicked") }
            override fun adNotDisplayed(p0: Ad?) {
                Log.e(TAG, "adNotDisplayed: ${p0?.errorMessage}")
                isShowing = false
                deliverJs(mode, false)
            }
        }

        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad carregado ✅")
                ad.setVideoListener(VideoListener {
                    rewarded = true
                    Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
                })
                // Registra listener e mostra
                ad.setAdDisplayListener(displayListener)
                ad.showAd()
            }
            override fun onFailedToReceiveAd(failedAd: Ad?) {
                Log.e(TAG, "Falhou: ${failedAd?.errorMessage}")
                isShowing = false
                deliverJs(mode, false)
            }
        })
    }

    fun deliverJs(mode: String, rewarded: Boolean) {
        val js = when {
            mode == "wp"     && rewarded  -> "if(window.onWpAdRewarded)   window.onWpAdRewarded()"
            mode == "wp"     && !rewarded -> "if(window.onWpAdClosed)     window.onWpAdClosed()"
            mode == "energy" && rewarded  -> "if(window.onEnergyRewarded) window.onEnergyRewarded()"
            else                          -> "if(window.onEnergyAdClosed) window.onEnergyAdClosed()"
        }
        Log.d(TAG, "deliverJs → $js")
        BeeActivity.runJs(js)
    }
}
