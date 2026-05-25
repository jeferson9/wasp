package com.example.waspbrowser

import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

object StartioAdManager {

    private const val TAG   = "StartioAdManager"
    const val APP_ID        = "204731691"

    private var rewardedAd  : StartAppAd? = null
    private var isLoading   = false
    private var isShowing   = false
    private var initialized = false

    private var mainActivityRef: java.lang.ref.WeakReference<MainActivity>? = null

    // ── Init na MainActivity ──────────────────────────────────────────────

    fun init(activity: MainActivity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
        if (initialized) return
        initialized = true
        StartAppSDK.init(activity, APP_ID, false)
        StartAppSDK.setTestAdsEnabled(true)
        com.startapp.sdk.adsbase.StartAppSDK.setUserConsent(activity, "pas", 0, false)
        Log.d(TAG, "Start.io iniciado — modo teste ON")
        preload(activity)
    }

    // ── Pré-carrega na MainActivity ───────────────────────────────────────

    private fun preload(activity: MainActivity) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        Log.d(TAG, "Pré-carregando ad...")
        val ad = StartAppAd(activity)
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad pré-carregado ✅")
                rewardedAd = ad
                isLoading = false
            }
            override fun onFailedToReceiveAd(failedAd: Ad?) {
                Log.e(TAG, "Falhou pré-load: ${failedAd?.errorMessage}")
                rewardedAd = null
                isLoading = false
            }
        })
    }

    // ── Show — sempre na MainActivity ────────────────────────────────────

    fun show(mode: String) {
        if (isShowing) { Log.w(TAG, "Ad já exibindo"); return }

        val activity = mainActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "MainActivity não disponível")
            deliverJs(mode, false)
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Ad não pronto — tentando carregar")
            preload(activity)
            deliverJs(mode, false)
            return
        }

        isShowing = true
        rewardedAd = null
        var rewarded = false

        Log.d(TAG, "Mostrando ad mode=$mode na MainActivity")

        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
        })

        // Roda na main thread da MainActivity
        activity.runOnUiThread {
            ad.showAd(object : AdDisplayListener {
                override fun adHidden(p0: Ad) {
                    Log.d(TAG, "adHidden rewarded=$rewarded")
                    isShowing = false
                    preload(activity)
                    deliverJs(mode, rewarded)
                }
                override fun adDisplayed(p0: Ad) {
                    Log.d(TAG, "adDisplayed ✅")
                }
                override fun adClicked(p0: Ad) {
                    Log.d(TAG, "adClicked")
                }
                override fun adNotDisplayed(p0: Ad) {
                    Log.e(TAG, "adNotDisplayed: ${p0.errorMessage}")
                    isShowing = false
                    preload(activity)
                    deliverJs(mode, false)
                }
            })
        }
    }

    // ── JS Delivery ───────────────────────────────────────────────────────

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
