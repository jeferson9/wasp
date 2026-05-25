package com.waspbrowser.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object WaspToast {

    // Tipos
    const val NORMAL  = 0  // borda amarela — ações gerais
    const val SUCCESS = 1  // borda verde  — reward, mineração iniciada
    const val ERROR   = 2  // borda vermelha — erros

    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String, type: Int = NORMAL, long: Boolean = false) {
        handler.post {
            try {
                val borderColor = when (type) {
                    SUCCESS -> Color.parseColor("#00e676")
                    ERROR   -> Color.parseColor("#ff5252")
                    else    -> Color.parseColor("#ffc107")
                }
                val iconText = when (type) {
                    SUCCESS -> "✦"
                    ERROR   -> "✕"
                    else    -> "◈"
                }

                // Container
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 14), dp(context, 10), dp(context, 18), dp(context, 10))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#0F1015"))
                        cornerRadius = dp(context, 12).toFloat()
                        setStroke(dp(context, 1), borderColor)
                    }
                }

                // Ícone
                val icon = TextView(context).apply {
                    text = iconText
                    textSize = 11f
                    setTextColor(borderColor)
                    setPadding(0, 0, dp(context, 8), 0)
                }

                // Texto
                val label = TextView(context).apply {
                    text = message
                    textSize = 13f
                    setTextColor(Color.parseColor("#E8E8E8"))
                    letterSpacing = 0.02f
                }

                container.addView(icon)
                container.addView(label)

                val toast = Toast(context)
                toast.duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                toast.view = container
                toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(context, 80))
                toast.show()
            } catch (e: Exception) {
                // Fallback para toast padrão
                Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}
