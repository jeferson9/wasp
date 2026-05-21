package com.example.waspbrowser

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Gerenciador único de anúncios rewarded.
 * Só existe UMA instância de RewardedAd no app inteiro.
 * O callback JS sempre vai para a persistentBeeView da MainActivity.
 */
object AdManager {

    private const val TAG     = "AdManager"
    private const val AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null
    private var isLoading  = false
    private var isShowing  = false
    private var initialized = false

    // ── Init ─────────────────────────────────────────────────────────────

    fun init(activity: Activity) {
        if (initialized) { loadIfNeeded(activity); return }
        initialized = true
        MobileAds.initialize(activity) {
            Log.d(TAG, "MobileAds inicializado")
            loadIfNeeded(activity)
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────

    fun loadIfNeeded(activity: Activity) {
        if (rewardedAd != null || isLoading) return
        isLoading = true
        Log.d(TAG, "Carregando ad...")
        RewardedAd.load(activity, AD_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad carregado ✅")
                    rewardedAd = ad
                    isLoading  = false
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.e(TAG, "Ad falhou: ${e.message}")
                    rewardedAd = null
                    isLoading  = false
                }
            }
        )
    }

    // ── Show ─────────────────────────────────────────────────────────────

    /**
     * Mostra o anúncio.
     * @param activity  Activity visível no momento (precisa ser a do topo da stack)
     * @param mode      "wp" ou "energy"
     * @param onResult  chamado com rewarded=true/false quando o ad fechar
     */
    fun show(activity: Activity, mode: String, onResult: (rewarded: Boolean) -> Unit) {
        if (isShowing) { Log.w(TAG, "Ad já sendo exibido"); return }

        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Ad não disponível — carregando e notificando fechamento")
            loadIfNeeded(activity)
            onResult(false)
            return
        }

        isShowing  = true
        rewardedAd = null
        var rewarded = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                Log.d(TAG, ">>> onAdClicked")
            }
            override fun onAdImpression() {
                Log.d(TAG, ">>> onAdImpression — anuncio visivel na tela")
            }
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, ">>> onAdShowedFullScreenContent — tela cheia aberta")
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, ">>> onAdDismissedFullScreenContent — usuario fechou, rewarded=$rewarded")
                isShowing = false
                loadIfNeeded(activity)
                onResult(rewarded)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                Log.e(TAG, ">>> onAdFailedToShowFullScreenContent — erro: ${e.message}")
                isShowing = false
                loadIfNeeded(activity)
                onResult(false)
            }
        }

        ad.show(activity) {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA — mode=$mode")
        }
    }

    // ── JS Delivery ───────────────────────────────────────────────────────

    /**
     * Entrega o resultado do anúncio como callback JS
     * diretamente na persistentBeeView da MainActivity.
     */
    fun deliverJs(mode: String, rewarded: Boolean) {
        val js = when {
            mode == "wp"     && rewarded  -> "if(window.onWpAdRewarded)  window.onWpAdRewarded()"
            mode == "wp"     && !rewarded -> "if(window.onWpAdClosed)    window.onWpAdClosed()"
            mode == "energy" && rewarded  -> "if(window.onEnergyRewarded) window.onEnergyRewarded()"
            else                          -> "if(window.onEnergyAdClosed) window.onEnergyAdClosed()"
        }
        Log.d(TAG, "deliverJs → $js")
        BeeActivity.runJs(js)
    }
}
