package com.waspbrowser.app

/*
 * ============================================================================
 *  WASP - VIDEO RECOMPENSADO (Start.io)  -  ESTADO E HISTORICO
 * ============================================================================
 *
 *  O QUE ESTA TELA FAZ:
 *   - Aberta pela Central WP quando o usuario toca "Assistir anuncio".
 *   - Carrega e exibe um video recompensado do Start.io (App ID 204731691).
 *   - Ao COMPLETAR o video (VideoListener -> rewarded=true), credita 30 WP
 *     via CentralActivity.grantAdReward().
 *
 *  SITUACAO ATUAL:
 *   - MODO DE TESTE habilitado para garantir que o video apareca durante o
 *     desenvolvimento (setTestAdsEnabled(true)).
 *   - Overlay "Carregando video..." enquanto o SDK busca/bufferiza o criativo,
 *     para a espera nao parecer travamento (a latencia de rede do Start.io
 *     nao da para eliminar por codigo; da para deixar tolerante).
 *
 *  >>> ATENCAO ANTES DE PUBLICAR (BRECHA DE WP GRATIS) <<<
 *   O fallback AUTOMATIC abaixo credita 30 WP ao FECHAR o anuncio, SEM exigir
 *   que um video tenha sido assistido. Isso existe so para validar o fluxo em
 *   teste. EM PRODUCAO isso deixa o usuario farmar WP sem assistir nada.
 *   Antes de publicar: remover o fallback AUTOMATIC (ou so creditar quando
 *   rewarded==true) e voltar setTestAdsEnabled(false).
 * ============================================================================
 */

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

class StartioAdActivity : Activity() {

    companion object {
        const val TAG       = "StartioAdActivity"
        const val EXTRA_MODE = "ad_mode"
        const val APP_ID    = "204731691"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private var rewarded  = false   // true so quando o video e completado
    private var finished  = false   // evita finish() duplicado
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostra um overlay imediato para a tela nao ficar preta/vazia enquanto
        // o video carrega pela rede. So feedback visual; nao acelera o load.
        setContentView(buildLoadingOverlay())

        fun diag(m: String) {
            Log.d(TAG, m)
            statusText.text = m
        }

        // SDK ja inicializado no SplashActivity (no boot). Nao reinicializamos
        // aqui: chamar init() de novo no momento de abrir o anuncio so atrasa
        // a abertura da tela. Mantemos o modo de teste alinhado com o Splash.
        StartAppSDK.setTestAdsEnabled(false)

        val ad = StartAppAd(this)

        // Marca rewarded so quando o video e assistido ate o fim.
        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "Video completado")
        })

        diag("Carregando vídeo...")
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(p: Ad) {
                diag("Iniciando vídeo...")
                // Sem delay artificial: exibimos assim que o video esta pronto.
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(p0: Ad) {
                        // MODO SEGURO: credita 30 WP somente se o video foi
                        // realmente completado. Sem video assistido = sem WP.
                        if (rewarded) {
                            CentralActivity.grantAdReward(applicationContext)
                        }
                        safeFinish()
                    }
                    override fun adDisplayed(p0: Ad) {}
                    override fun adClicked(p0: Ad) {}
                    override fun adNotDisplayed(p0: Ad) {
                        diag("Anúncio não pôde ser exibido")
                        safeFinish()
                    }
                })
            }

            override fun onFailedToReceiveAd(p: Ad?) {
                diag("Nenhum anúncio disponível")
                handler.postDelayed({ safeFinish() }, 900)
            }
        })
    }

    private fun safeFinish() {
        if (finished) return
        finished = true
        finish()
    }

    /* ─── UI do overlay de carregamento ─────────────────────────────── */

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildLoadingOverlay(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#08090d"))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#f7c600")
            )
        }
        column.addView(spinner, LinearLayout.LayoutParams(dp(48), dp(48)))

        statusText = TextView(this).apply {
            text = "Carregando vídeo..."
            setTextColor(Color.parseColor("#ccffffff"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        column.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        val hint = TextView(this).apply {
            text = "Aguarde — você receberá 30 WP ao assistir"
            setTextColor(Color.parseColor("#66ffffff"))
            textSize = 11f
            gravity = Gravity.CENTER
        }
        column.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        return root
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}