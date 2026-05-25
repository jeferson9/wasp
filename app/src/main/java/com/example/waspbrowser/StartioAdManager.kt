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

        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad carregado ✅")

                ad.setVideoListener(VideoListener {
                    rewarded = true
                    Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
                })

                // showAd sem parametros — forma mais simples e compativel
                val shown = ad.showAd()
                Log.d(TAG, "showAd() retornou: $shown")

                if (!shown) {
                    Log.e(TAG, "showAd retornou false")
                    isShowing = false
                    deliverJs(mode, false)
                    return
                }

                // Polling para detectar quando o ad fechou
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                val checkInterval = 500L
                val maxWait = 120_000L // 2 min max
                var elapsed = 0L

                val checker = object : Runnable {
                    override fun run() {
                        elapsed += checkInterval
                        if (!ad.isShowing || elapsed >= maxWait) {
                            Log.d(TAG, "Ad fechado detectado — rewarded=$rewarded")
                            isShowing = false
                            deliverJs(mode, rewarded)
                        } else {
                            handler.postDelayed(this, checkInterval)
                        }
                    }
                }
                handler.postDelayed(checker, checkInterval)
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
