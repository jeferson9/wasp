package com.waspbrowser.app

/*
 * ============================================================================
 *  WASP - VIDEO RECOMPENSADO (Start.io)  -  ESTADO E HISTORICO
 * ============================================================================
 *
 *  PARA UM PROXIMO CHAT/DEV: leia isto antes de mexer.
 *
 *  O QUE ESTA TELA FAZ:
 *   - Aberta pela Central WP quando o usuario toca "Assistir anuncio".
 *   - Carrega e exibe um video recompensado do Start.io (App ID 204731691).
 *   - Ao COMPLETAR o video (VideoListener -> rewarded=true), credita 30 WP
 *     via CentralActivity.grantAdReward().
 *
 *  COMO O FLUXO CHEGA AQUI (a integracao TODA ja funciona):
 *   central.html (doWatchAd) -> AndroidBee.openWpAd()
 *     - Se a Central roda como Activity: CentralActivity.openWpAd()
 *     - Se roda como iframe: postMessage -> BeeActivity.openWpAd()
 *   -> abre esta StartioAdActivity. Cooldown (5 min) e NATIVO, em
 *      SharedPreferences "wasp_ads" / chave "wp_ad_last".
 *   O WP vive no localStorage do WebView (chave wasp_wp). grantAdReward()
 *   credita chamando window.waspAddWP(30) na WebView correta.
 *
 *  SITUACAO ATUAL (importante!):
 *   - A integracao esta COMPLETA e funciona ponta a ponta.
 *   - POREM o Start.io NAO esta entregando criativo visual para este App ID
 *     (app ainda nao publicado / conta nova). Sintoma observado em teste:
 *     o ciclo roda e o WP cai, mas nenhum anuncio aparece na tela.
 *   - Isso e FILL/conta, NAO codigo. Deve resolver quando o app for
 *     publicado na Play Store e a conta Start.io receber anuncios.
 *
 *  POR QUE ESTA NO "MODO SEGURO" (NAO remover sem pensar):
 *   - Houve um teste com fallback para anuncio comum (AdMode.AUTOMATIC) que
 *     creditava WP ao fechar. Isso criava BRECHA: usuario ganhava 30 WP SEM
 *     assistir anuncio nenhum (farm de graca, sem monetizacao real).
 *   - REVERTIDO. Agora o WP so e creditado quando o video recompensado e
 *     REALMENTE completado (rewarded==true). Sem video real = sem WP.
 *
 *  CONTEXTO DE NEGOCIO (conversado com o dono):
 *   - Wasp tinha 1 usuario (o proprio dono). Anuncio so gera renda com
 *     VOLUME de usuarios. Monetizacao nao e prioridade ate ter base de
 *     usuarios. Foco atual: estabilidade do app.
 *   - ATENCAO a publicacao na Play Store: a MINERACAO de cripto on-device e
 *     restrita pela politica do Google (minerar no device e proibido; apps
 *     que GERENCIAM mineracao remota sao permitidos). Avaliar o enquadramento
 *     do Bee/Acki Nacki ANTES de submeter.
 *
 *  PARA TESTAR SE A INTEGRACAO FUNCIONA (sem esperar fill de video):
 *   Trocar temporariamente AdMode.REWARDED_VIDEO por AdMode.AUTOMATIC e
 *   creditar no adHidden. Se aparecer anuncio, a integracao esta OK. NAO
 *   deixar isso em producao (brecha de WP gratis).
 *
 *  Toasts "Ad: ..." sao DIAGNOSTICO temporario - remover quando o fill
 *  estiver confirmado e o anuncio aparecendo normalmente.
 * ============================================================================
 */

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

class StartioAdActivity : Activity() {

    companion object {
        const val TAG        = "StartioAdActivity"
        const val EXTRA_MODE  = "ad_mode"
        const val APP_ID     = "204731691"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rewarded = false   // true so quando o video e completado

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fun diag(m: String) {
            Log.d(TAG, m)
            runCatching { android.widget.Toast.makeText(this, "Ad: $m", android.widget.Toast.LENGTH_SHORT).show() }
        }

        diag("preparando")
        // SDK ja inicializado no SplashActivity; init aqui e defensivo/idempotente.
        runCatching {
            StartAppSDK.init(this, APP_ID, false)
            StartAppSDK.setTestAdsEnabled(false)  // false = anuncios reais
        }

        val ad = StartAppAd(this)

        // Marca rewarded so quando o video e assistido ate o fim.
        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "Video completado")
        })

        diag("carregando video...")
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(p: Ad) {
                diag("video carregado, exibindo")
                handler.postDelayed({
                    ad.showAd(object : AdDisplayListener {
                        override fun adHidden(p0: Ad) {
                            // MODO SEGURO: credita 30 WP somente se o video foi
                            // realmente completado. Sem video assistido = sem WP.
                            if (rewarded) {
                                CentralActivity.grantAdReward(applicationContext)
                            }
                            handler.postDelayed({ finish() }, 200)
                        }
                        override fun adDisplayed(p0: Ad) {}
                        override fun adClicked(p0: Ad) {}
                        override fun adNotDisplayed(p0: Ad) {
                            diag("nao exibido")
                            handler.post { finish() }
                        }
                    })
                }, 250)
            }
            override fun onFailedToReceiveAd(p: Ad?) {
                // Sem fill de video recompensado. NAO credita WP, NAO faz fallback
                // (evita brecha de WP gratis). So avisa e fecha.
                diag("nenhum video disponivel agora")
                handler.post { finish() }
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
