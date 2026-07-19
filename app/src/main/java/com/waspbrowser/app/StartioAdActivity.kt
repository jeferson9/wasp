package com.waspbrowser.app

/*
 * ============================================================================
 *  WASP - ANUNCIOS (Start.io)  -  ESTADO E HISTORICO
 * ============================================================================
 *
 *  TRES MODOS, DUAS REGRAS DE NEGOCIO:
 *   - REWARDED (EXTRA_MODE == "wp"): aberta quando o usuario toca "Assistir
 *     anuncio". O WP (30) SO e creditado se o VideoListener marcar
 *     rewarded=true, ou seja, o video precisa ser assistido ate o fim.
 *     O anuncio E' condicao pra ganhar aqui, de proposito.
 *   - MODE_INTERSTITIAL_BONUS / MODE_TAP_INTERSTITIAL: o WP (quando existe,
 *     como no bonus de 15 WP) e' creditado ANTES de abrir esta tela — o
 *     interstitial e' so um extra exibido depois, nunca condicao pra ganhar.
 *     Se o anuncio falhar ao carregar, a tela fecha sozinha sem afetar nada.
 *
 *  SITUACAO ATUAL:
 *   - MODO DE TESTE habilitado para garantir que o video apareca durante o
 *     desenvolvimento (setTestAdsEnabled(true)). Nesse modo o Start.io serve
 *     sempre o mesmo criativo de teste, entao rewarded e interstitial parecem
 *     visualmente iguais em dev — em producao cada um mostra o formato real.
 *   - Overlay "Carregando video..." enquanto o SDK busca/bufferiza o criativo,
 *     para a espera nao parecer travamento (a latencia de rede do Start.io
 *     nao da para eliminar por codigo; da para deixar tolerante).
 *
 *  >>> ATENCAO ANTES DE PUBLICAR <<<
 *   Desligar setTestAdsEnabled(true) -> false. Sem isso o app so mostra o
 *   criativo de teste do Start.io, nunca anuncios reais.
 * ============================================================================
 */

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
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

class StartioAdActivity : AppCompatActivity() {

    companion object {
        const val TAG                     = "StartioAdActivity"
        const val EXTRA_MODE              = "ad_mode"
        const val APP_ID                  = "204731691"
        const val MODE_INTERSTITIAL_BONUS = "interstitial_bonus"
        const val INTERSTITIAL_BONUS_WP   = 15
        // Interstitial "seco": sem bônus de WP, sem tela de confirmação —
        // usado ao fechar o Tap Game após completar (a recompensa do tap já
        // foi creditada pelo próprio jogo, este anúncio não credita nada).
        const val MODE_TAP_INTERSTITIAL   = "tap_interstitial"
    }

    private val handler  = Handler(Looper.getMainLooper())
    private var rewarded  = false   // true so quando o video e completado
    private var finished  = false   // evita finish() duplicado
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun diag(m: String) {
            Log.d(TAG, m)
            if (::statusText.isInitialized) statusText.text = m
        }

        StartAppSDK.setTestAdsEnabled(true) // MODO TESTE (voltar p/ false antes de publicar)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: ""

        if (mode == MODE_TAP_INTERSTITIAL) {
            // ── MODO INTERSTITIAL DO TAP GAME (sem recompensa) ────────────
            // Dispara direto, sem overlay de "assista e ganhe" e sem tela de
            // confirmação — a recompensa dos 100 taps já caiu antes de abrir.
            setContentView(buildLoadingOverlay(showRewardHint = false))
            val adTap = StartAppAd(this)
            adTap.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(p: Ad) {
                    adTap.showAd(object : AdDisplayListener {
                        override fun adHidden(p0: Ad)        { safeFinish() }
                        override fun adDisplayed(p0: Ad)     {}
                        override fun adClicked(p0: Ad)       {}
                        override fun adNotDisplayed(p0: Ad)  { safeFinish() }
                    })
                }
                override fun onFailedToReceiveAd(p: Ad?) { safeFinish() }
            })
            return
        }

        if (mode == MODE_INTERSTITIAL_BONUS) {
            // ── MODO INTERSTITIAL BÔNUS ───────────────────────────────────
            // WP já foi creditado antes de abrir esta Activity. Vai DIRETO para a
            // tela de confirmação (sem overlay de "assista e ganhe WP"). O anúncio
            // só dispara quando o usuário toca "Voltar ao Wasp".
            setContentView(buildRewardConfirmScreen {
                // Callback do botão "Voltar ao Wasp" — o intersticial abre sem a
                // mensagem de bônus (WP já foi coletado antes).
                setContentView(buildLoadingOverlay(showRewardHint = false))
                diag(getString(R.string.bonus_loading_ad))
                val adBonus = StartAppAd(this)
                adBonus.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                    override fun onReceiveAd(p: Ad) {
                        adBonus.showAd(object : AdDisplayListener {
                            override fun adHidden(p0: Ad)        { safeFinish() }
                            override fun adDisplayed(p0: Ad)     {}
                            override fun adClicked(p0: Ad)       {}
                            override fun adNotDisplayed(p0: Ad)  { safeFinish() }
                        })
                    }
                    override fun onFailedToReceiveAd(p: Ad?) { safeFinish() }
                })
            })
            return
        }

        // ── MODO REWARDED VIDEO (30 WP) — comportamento original ─────────
        // Aqui SIM mostra o overlay com "você receberá WP por assistir", pois
        // neste modo o WP é a recompensa por assistir o vídeo.
        setContentView(buildLoadingOverlay())
        val ad = StartAppAd(this)

        // Marca rewarded so quando o video e assistido ate o fim.
        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "Video completado")
        })

        diag(getString(R.string.ad_loading_video))
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(p: Ad) {
                diag(getString(R.string.ad_starting_video))
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(p0: Ad) {
                        if (rewarded) {
                            CentralActivity.grantAdReward(applicationContext)
                        }
                        safeFinish()
                    }
                    override fun adDisplayed(p0: Ad) {}
                    override fun adClicked(p0: Ad) {}
                    override fun adNotDisplayed(p0: Ad) {
                        diag(getString(R.string.ad_cant_show))
                        safeFinish()
                    }
                })
            }

            override fun onFailedToReceiveAd(p: Ad?) {
                // Vídeo recompensado costuma ter fill baixo (app novo / região).
                // Fallback: mostra um intersticial (que enche fácil) e credita o WP
                // do mesmo jeito — o cooldown de 60 min já limita qualquer abuso.
                // Sem isto, o usuário fica sem como ganhar WP e não consegue minerar.
                diag(getString(R.string.ad_loading_video))
                val fallback = StartAppAd(this@StartioAdActivity)
                fallback.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                    override fun onReceiveAd(p2: Ad) {
                        fallback.showAd(object : AdDisplayListener {
                            override fun adHidden(p0: Ad) {
                                CentralActivity.grantAdReward(applicationContext)
                                safeFinish()
                            }
                            override fun adDisplayed(p0: Ad) {}
                            override fun adClicked(p0: Ad) {}
                            override fun adNotDisplayed(p0: Ad) {
                                diag(getString(R.string.ad_none_available))
                                handler.postDelayed({ safeFinish() }, 900)
                            }
                        })
                    }
                    override fun onFailedToReceiveAd(p2: Ad?) {
                        diag(getString(R.string.ad_none_available))
                        handler.postDelayed({ safeFinish() }, 900)
                    }
                })
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

    private fun buildRewardConfirmScreen(onBack: () -> Unit): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#111420"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        // Ícone ✦
        col.addView(TextView(this).apply {
            text = "✦"
            textSize = 56f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#FFD400"))
        })
        // Título
        col.addView(TextView(this).apply {
            text = getString(R.string.bonus_earned_title, INTERSTITIAL_BONUS_WP)
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(8))
        })
        // Subtítulo
        col.addView(TextView(this).apply {
            text = getString(R.string.bonus_earned_sub)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#99C8D0E0"))
            setPadding(0, 0, 0, dp(40))
        })
        // Botão "Voltar ao Wasp"
        col.addView(TextView(this).apply {
            text = getString(R.string.bonus_back_to_wasp)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#111420"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#FFD400"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(32), dp(14), dp(32), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onBack() }
        })
        root.addView(col)
        return root
    }

    private fun buildLoadingOverlay(showRewardHint: Boolean = true): View {
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
            text = getString(R.string.ad_loading_video)
            setTextColor(Color.parseColor("#ccffffff"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        column.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        if (showRewardHint) {
            val hint = TextView(this).apply {
                text = getString(R.string.ad_wait_reward, 30)
                setTextColor(Color.parseColor("#66ffffff"))
                textSize = 11f
                gravity = Gravity.CENTER
            }
            column.addView(hint, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }

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