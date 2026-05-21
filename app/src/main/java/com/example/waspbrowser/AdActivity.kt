package com.example.waspbrowser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Activity dedicada EXCLUSIVAMENTE ao anúncio rewarded.
 * Não faz mais nada. Abre, mostra o ad, entrega o resultado e fecha.
 */
class AdActivity : AppCompatActivity() {

    companion object {
        private const val TAG     = "AdActivity"
        private const val AD_UNIT = "ca-app-pub-3940256099942544/5224354917"
        const val EXTRA_MODE      = "ad_mode" // "wp" ou "energy"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var rewarded = false
    private var mode = "wp"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tela transparente — sem layout, sem flash
        setTheme(android.R.style.Theme_Translucent_NoTitleBar)

        mode = intent.getStringExtra(EXTRA_MODE) ?: "wp"
        Log.d(TAG, "onCreate mode=$mode")

        MobileAds.initialize(this) {
            Log.d(TAG, "MobileAds init OK — carregando ad")
            loadAd()
        }
    }

    private fun loadAd() {
        RewardedAd.load(this, AD_UNIT, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad carregado ✅ — mostrando")
                    rewardedAd = ad
                    showAd()
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.e(TAG, "Ad falhou ao carregar: ${e.message}")
                    deliver(false)
                }
            }
        )
    }

    private fun showAd() {
        val ad = rewardedAd ?: run { deliver(false); return }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, ">>> onAdShowedFullScreenContent")
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, ">>> onAdDismissedFullScreenContent rewarded=$rewarded")
                deliver(rewarded)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                Log.e(TAG, ">>> onAdFailedToShowFullScreenContent: ${e.message}")
                deliver(false)
            }
        }

        ad.show(this) {
            rewarded = true
            Log.d(TAG, "✅ RECOMPENSA CONFIRMADA")
        }
    }

    private fun deliver(rewarded: Boolean) {
        val js = when {
            mode == "wp"     && rewarded  -> "if(window.onWpAdRewarded)   window.onWpAdRewarded()"
            mode == "wp"     && !rewarded -> "if(window.onWpAdClosed)     window.onWpAdClosed()"
            mode == "energy" && rewarded  -> "if(window.onEnergyRewarded) window.onEnergyRewarded()"
            else                          -> "if(window.onEnergyAdClosed) window.onEnergyAdClosed()"
        }
        Log.d(TAG, "deliver → $js")
        handler.postDelayed({
            BeeActivity.runJs(js)
            finish()
        }, 300)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        rewardedAd = null
        super.onDestroy()
    }
}
