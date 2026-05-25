package com.example.waspbrowser

import android.app.Activity
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.startapp.sdk.adsbase.model.AdPreferences

/**
 * Gerenciador único de anúncios rewarded via Start.io.
 * - Inicializa uma vez na MainActivity
 * - show() sempre na MainActivity (Activity raiz)
 * - Callback JS entregue via BeeActivity.runJs() → persistentBeeView
 */
object StartioAdManager {

    private const val TAG = "StartioAdManager"
    private const val APP_ID = "test-app-id" // substituir pelo ID real após cadastro

    private var rewardedAd: StartAppAd? = null
    private var isLoading  = false
    private var isShowing  = false
    private var initialized = false

    private var mainActivityRef: java.lang.ref.WeakReference<Activity>? = null

    // ── Init ─────────────────────────────────────────────────────────────

    fun init(activity: Activity) {
        mainActivityRef = java.lang.ref.WeakReference(activity)
        if (initialized) { loadIfNeeded(activity); return }
        initialized = true
        StartAppSDK.init(activity, APP_ID, false)
        StartAppSDK.setTestAdsEnabled(true) // remover ao ir para produção
        Log.d(TAG, "Start.io SDK inicializado (modo teste)")
        loadIfNeeded(activity)
    }

    // ── Load ─────────────────────────────────────────────────────────────

    fun loadIfNeeded(activity: Activity) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        Log.d(TAG, "Carregando rewarded ad...")

        val ad = StartAppAd(activity)
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(receivedAd: Ad) {
                Log.d(TAG, "Ad carregado ✅")
                rewardedAd = ad
                isLoading  = false
            }
            override fun onFailedToReceiveAd(failedAd: Ad?) {
                Log.e(TAG, "Ad falhou ao carregar: ${failedAd?.errorMessage}")
                rewardedAd = null
                isLoading  = false
            }
        })
    }

    // ── Show ─────────────────────────────────────────────────────────────

    fun show(mode: String) {
        val activity = mainActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "MainActivity não disponível")
            deliverJs(mode, false)
            return
        }
        if (isShowing) {
            Log.w(TAG, "Ad já sendo exibido")
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Ad não pronto — carregando")
            loadIfNeeded(activity)
            deliverJs(mode, false)
            return
        }

        isShowing  = true
        rewardedAd = null
        var rewarded = false

        // Callback de reward — vídeo assistido até o fim
        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
        })

        ad.showAd(object : com.startapp.sdk.adsbase.adlisteners.AdDisplayListener {
            override fun adHidden(hiddenAd: Ad?) {
                Log.d(TAG, "Ad fechado — rewarded=$rewarded")
                isShowing = false
                loadIfNeeded(activity)
                deliverJs(mode, rewarded)
            }
            override fun adDisplayed(displayedAd: Ad?) {
                Log.d(TAG, "Ad exibido")
            }
            override fun adClicked(clickedAd: Ad?) {
                Log.d(TAG, "Ad clicado")
            }
            override fun adNotDisplayed(notDisplayedAd: Ad?) {
                Log.e(TAG, "Ad não exibido: ${notDisplayedAd?.errorMessage}")
                isShowing = false
                loadIfNeeded(activity)
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
