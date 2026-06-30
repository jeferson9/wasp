package com.waspbrowser.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * WebView que, quando [keepVisible] está ligado, sempre informa ao motor Chromium
 * que sua janela está VISÍVEL — mesmo com o app minimizado ou a tela apagada.
 *
 * Por que isso é necessário para a mineração em segundo plano:
 * o Chromium congela a fila de tarefas assíncronas (timers, mas também a conclusão
 * de Promises de rede/WASM) quando a página fica "hidden" (document.hidden = true).
 * Isso fazia add_tap()/get_reward() serem *iniciados* mas nunca *concluídos* em
 * background — o reward só caía ao reabrir o app.
 *
 * Forçando a visibilidade da janela, document.visibilityState permanece "visible",
 * não há throttling, e as operações assíncronas completam em tempo real. Deve ser
 * usado em conjunto com um foreground service + PARTIAL_WAKE_LOCK (CPU ativa).
 */
class KeepAliveWebView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Quando true, a página nunca é reportada como oculta. Ligado durante a mineração em bg. */
    var keepVisible: Boolean = false

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(if (keepVisible) View.VISIBLE else visibility)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, if (keepVisible) View.VISIBLE else visibility)
    }
}
