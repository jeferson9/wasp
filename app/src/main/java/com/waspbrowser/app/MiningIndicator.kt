package com.waspbrowser.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * MiningIndicator — dot estático na toolbar do Wasp.
 *
 * isMining = true  → verde #00FF88 (minerando)
 * isMining = false → cinza #4A5568 (parado)
 *
 * Sem animação — discreto durante a navegação.
 */
class MiningIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var isMining: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val dotPaint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD400")
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        if (w > dp(25f)) {
            // Modo Pílula — fundo amarelo + emoji + dot no canto
            val radius = h / 2f
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, pillPaint)

            val emoji = "🐝"
            textPaint.textSize = h * 0.55f
            val emojiW = textPaint.measureText(emoji)
            val fm = textPaint.fontMetrics
            val emojiX = (w / 2f - emojiW / 2f) - dp(3f)
            val emojiY = h / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(emoji, emojiX, emojiY, textPaint)

            drawDot(canvas, w - dp(7f), dp(7f), dp(4f))
        } else {
            // Modo Toolbar — só o dot centralizado
            drawDot(canvas, w / 2f, h / 2f, w / 3.5f)
        }
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, r: Float) {
        // Borda de contraste para destacar do fundo escuro da toolbar
        borderPaint.color = Color.parseColor("#111420")
        canvas.drawCircle(x, y, r + dp(1f), borderPaint)

        // Dot: verde se minerando, cinza se parado
        dotPaint.color = if (isMining) Color.parseColor("#00FF88")
        else          Color.parseColor("#4A5568")
        canvas.drawCircle(x, y, r, dotPaint)
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density
}