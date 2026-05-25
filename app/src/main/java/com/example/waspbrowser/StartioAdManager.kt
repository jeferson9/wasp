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

    private const val TAG    = "StartioAdManager"
    const val APP_ID         = "test-app-id" // trocar pelo ID real após cadastro

    private var isShowing    = false
    private var initialized  = false

    private var mainActivityRef: java.lang.ref.WeakReference<MainActivity>? = null

    // ── Init — chamado uma vez na MainActivity ────────────────────────────

    fun init(activity: MainActivity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
        if (initialized) return
        initialized = true
        StartAppSDK.init(activity, APP_ID, false) // false = desativa Return Ads
        StartAppSDK.setTestAdsEnabled(true)       // remover em produção
        Log.d(TAG, "Start.io iniciado — modo teste ON")
    }

    // ── Show — carrega e mostra na mesma chamada ──────────────────────────

    fun show(mode: String, callerActivity: Activity? = null) {
        if (isShowing) { Log.w(TAG, "Ad já exibindo"); return }

        // Usa a Activity que chamou (topo da stack) ou a MainActivity como fallback
        val activity = callerActivity ?: mainActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "Nenhuma Activity disponível")
            deliverJs(mode, false)
            return
        }

        isShowing = true
        var rewarded = false

        Log.d(TAG, "Carregando ad para mode=$mode em ${activity.javaClass.simpleName}")

        val ad = StartAppAd(activity)
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad carregado ✅ — exibindo")

                ad.setVideoListener(VideoListener {
                    rewarded = true
                    Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
                })

                ad.showAd(activity, object : AdDisplayListener {
                    override fun adHidden(hiddenAd: Ad?) {
                        Log.d(TAG, "adHidden — rewarded=$rewarded")
                        isShowing = false
                        deliverJs(mode, rewarded)
                    }
                    override fun adDisplayed(displayedAd: Ad?) {
                        Log.d(TAG, "adDisplayed")
                    }
                    override fun adClicked(clickedAd: Ad?) {
                        Log.d(TAG, "adClicked")
                    }
                    override fun adNotDisplayed(notDisplayedAd: Ad?) {
                        Log.e(TAG, "adNotDisplayed: ${notDisplayedAd?.errorMessage}")
                        isShowing = false
                        deliverJs(mode, false)
                    }
                })
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.e(TAG, "Falhou ao carregar: ${ad?.errorMessage}")
                isShowing = false
                deliverJs(mode, false)
            }
        })
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
