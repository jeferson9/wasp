package com.example.waspbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AdActivity"
        const val AD_UNIT_ID     = "ca-app-pub-3940256099942544/5224354917"
        const val EXTRA_REWARDED = "rewarded"
        const val REQUEST_CODE   = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var rewarded   = false
    private var resultSent = false   // garante setResult uma unica vez

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, "onCreate")

        // Layout mínimo de loading
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF08090D.toInt()) }
        statusText = TextView(this).apply {
            text = "Carregando anúncio..."
            setTextColor(0xFFEEF0F6.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        setContentView(root)

        // Dispositivos de teste
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("C2BDD20251E0A65AA97DD561F37883A1"))
                .build()
        )

        MobileAds.initialize(this) {
            android.util.Log.d(TAG, "MobileAds inicializado — carregando ad")
            loadAd()
        }

        // Timeout de segurança
        handler.postDelayed({
            if (!resultSent) {
                android.util.Log.w(TAG, "Timeout 20s — finalizando sem recompensa")
                sendResult(rewarded = false, unavailable = true)
            }
        }, 20_000)
    }

    private fun loadAd() {
        android.util.Log.d(TAG, "loadAd — unit=$AD_UNIT_ID")
        RewardedAd.load(
            this,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {

                override fun onAdLoaded(ad: RewardedAd) {
                    android.util.Log.d(TAG, "onAdLoaded ✅")
                    rewardedAd = ad
                    showAd(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    android.util.Log.e(TAG, "onAdFailedToLoad ❌ code=${error.code} msg=${error.message}")
                    sendResult(rewarded = false, unavailable = true)
                }
            }
        )
    }

    private fun showAd(ad: RewardedAd) {
        android.util.Log.d(TAG, "showAd — exibindo")

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdShowedFullScreenContent() {
                android.util.Log.d(TAG, "onAdShowedFullScreenContent")
            }

            override fun onAdDismissedFullScreenContent() {
                // SDK garante: onUserEarnedReward dispara ANTES deste callback
                android.util.Log.d(TAG, "onAdDismissedFullScreenContent — rewarded=$rewarded")
                rewardedAd = null
                sendResult(rewarded = rewarded)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                android.util.Log.e(TAG, "onAdFailedToShowFullScreenContent ❌ ${error.message}")
                rewardedAd = null
                sendResult(rewarded = false, unavailable = true)
            }
        }

        // onUserEarnedReward é chamado ANTES de onAdDismissed pelo SDK
        ad.show(this) {
            rewarded = true
            android.util.Log.d(TAG, "onUserEarnedReward ✅ — recompensa confirmada")
        }
    }

    private fun sendResult(rewarded: Boolean, unavailable: Boolean = false) {
        if (resultSent) return
        resultSent = true

        android.util.Log.d(TAG, "sendResult — rewarded=$rewarded unavailable=$unavailable")

        val intent = Intent().apply {
            putExtra(EXTRA_REWARDED, rewarded)
            putExtra("unavailable", unavailable)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        android.util.Log.d(TAG, "onDestroy")
        handler.removeCallbacksAndMessages(null)
        rewardedAd = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Bloqueia back enquanto o ad está mostrando
        if (!resultSent) {
            sendResult(rewarded = false)
        }
    }
}
