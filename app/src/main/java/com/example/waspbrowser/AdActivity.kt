package com.example.waspbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Activity dedicada exclusivamente ao vídeo premiado AdMob.
 *
 * Fluxo:
 *  1. CentralActivity chama startActivityForResult(AdActivity)
 *  2. AdActivity carrega + exibe o rewarded ad
 *  3. Ao terminar (recompensado ou não) chama setResult() + finish()
 *  4. CentralActivity recebe onActivityResult — WebView nunca pausou
 *
 * Resultado retornado via Intent extra "rewarded" (Boolean).
 */
class AdActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AdActivity"
        const val AD_UNIT_ID   = "ca-app-pub-3940256099942544/5224354917"
        const val EXTRA_REWARDED = "rewarded"
        const val REQUEST_CODE   = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewardedAd: RewardedAd? = null
    private var rewarded = false
    private var adShown  = false

    // ── UI simples de loading ─────────────────────────────────────────────
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout mínimo: fundo escuro + texto de status centralizado
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF08090D.toInt())
        }
        loadingText = TextView(this).apply {
            text = "Carregando anúncio..."
            setTextColor(0xFFEEF0F6.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
        root.addView(
            loadingText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        setContentView(root)

        // Configura dispositivos de teste
        val testDeviceIds = listOf("C2BDD20251E0A65AA97DD561F37883A1")
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
        )

        MobileAds.initialize(this) {
            android.util.Log.d(TAG, "AdMob inicializado — carregando rewarded ad")
            loadAd()
        }

        // Timeout de segurança: se em 20s o ad não carregou, desiste
        handler.postDelayed({
            if (!adShown) {
                android.util.Log.w(TAG, "Timeout: ad não carregou em 20s")
                finishWithResult(rewarded = false, unavailable = true)
            }
        }, 20_000)
    }

    // ── Carregar o ad ────────────────────────────────────────────────────
    private fun loadAd() {
        RewardedAd.load(
            this,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {

                override fun onAdLoaded(ad: RewardedAd) {
                    android.util.Log.d(TAG, "Ad carregado — exibindo")
                    rewardedAd = ad
                    showAd(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    android.util.Log.e(TAG, "Falha ao carregar: ${error.message}")
                    finishWithResult(rewarded = false, unavailable = true)
                }
            }
        )
    }

    // ── Exibir o ad ──────────────────────────────────────────────────────
    private fun showAd(ad: RewardedAd) {
        adShown = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {

            override fun onAdShowedFullScreenContent() {
                android.util.Log.d(TAG, "Ad exibido em tela cheia")
            }

            override fun onAdDismissedFullScreenContent() {
                android.util.Log.d(TAG, "Ad fechado — rewarded=$rewarded")
                rewardedAd = null
                // Entrega imediata: neste ponto onUserEarnedReward já foi chamado
                // (o SDK garante que ele dispara ANTES de onAdDismissed)
                finishWithResult(rewarded = rewarded)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                android.util.Log.e(TAG, "Falha ao exibir: ${error.message}")
                rewardedAd = null
                finishWithResult(rewarded = false, unavailable = true)
            }
        }

        // onUserEarnedReward é chamado pelo SDK ANTES de onAdDismissed
        ad.show(this) {
            rewarded = true
            android.util.Log.d(TAG, "✅ onUserEarnedReward — recompensa confirmada")
        }
    }

    // ── Retornar resultado para CentralActivity ──────────────────────────
    private fun finishWithResult(rewarded: Boolean, unavailable: Boolean = false) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_REWARDED, rewarded)
            putExtra("unavailable", unavailable)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        rewardedAd = null
        super.onDestroy()
    }

    // Impede que o back físico feche sem registrar resultado
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ignora back durante o ad — o dismiss do ad cuida do fechamento
        if (!adShown) {
            finishWithResult(rewarded = false)
        }
    }
}
