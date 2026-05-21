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
import java.lang.ref.WeakReference

/**
 * Gerenciador único de anúncios rewarded.
 * - Uma única instância de RewardedAd no app inteiro
 * - ad.show() SEMPRE na MainActivity (Activity raiz, nunca coberta)
 * - Callback JS sempre entregue na persistentBeeView via BeeActivity.runJs()
 */
object AdManager {

    private const val TAG     = "AdManager"
    private const val AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

    private var rewardedAd: RewardedAd? = null
    private var isLoading   = false
    private var isShowing   = false
    private var initialized = false

    // Referência fraca para a MainActivity — única Activity usada para show()
    private var mainActivityRef: WeakReference<MainActivity>? = null

    // ── Init ─────────────────────────────────────────────────────────────

    fun init(activity: MainActivity) {
        // Sempre atualiza a referência para a MainActivity
        mainActivityRef = WeakReference(activity)
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
                    Log.e(TAG, "Ad falhou ao carregar: ${e.message}")
                    rewardedAd = null
                    isLoading  = false
                }
            }
        )
    }

    // ── Show ─────────────────────────────────────────────────────────────

    /**
     * Mostra o anúncio SEMPRE na MainActivity.
     * Qualquer Activity pode chamar isso — o show() usa a MainActivity.
     */
    fun show(mode: String, onResult: (rewarded: Boolean) -> Unit) {
        val mainActivity = mainActivityRef?.get()
        if (mainActivity == null) {
            Log.e(TAG, "MainActivity não disponível!")
            onResult(false)
            return
        }
        if (isShowing) {
            Log.w(TAG, "Ad já sendo exibido")
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Ad não disponível — carregando")
            loadIfNeeded(mainActivity)
            onResult(false)
            return
        }

        isShowing  = true
        rewardedAd = null
        var rewarded = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, ">>> onAdShowedFullScreenContent")
            }
            override fun onAdImpression() {
                Log.d(TAG, ">>> onAdImpression")
            }
            override fun onAdClicked() {
                Log.d(TAG, ">>> onAdClicked")
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, ">>> onAdDismissedFullScreenContent rewarded=$rewarded")
                isShowing = false
                loadIfNeeded(mainActivity)
                onResult(rewarded)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                Log.e(TAG, ">>> onAdFailedToShowFullScreenContent: ${e.message}")
                isShowing = false
                loadIfNeeded(mainActivity)
                onResult(false)
            }
        }

        // SEMPRE mostra na MainActivity — nunca em Activity filha
        ad.show(mainActivity) {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA mode=$mode")
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
