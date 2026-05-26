package com.waspbrowser.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object WaspToast {

    const val NORMAL  = 0
    const val SUCCESS = 1
    const val ERROR   = 2

    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String, type: Int = NORMAL, long: Boolean = false) {
        handler.post {
            try {
                val activity = context as? Activity
                if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    showOverlay(activity, message, type, long)
                } else {
                    showFallback(context, message, long)
                }
            } catch (e: Exception) {
                showFallback(context, message, long)
            }
        }
    }

    private fun showOverlay(activity: Activity, message: String, type: Int, long: Boolean) {
        try {
            val borderColor = when (type) {
                SUCCESS -> Color.parseColor("#00C853")
                ERROR   -> Color.parseColor("#FF5252")
                else    -> Color.parseColor("#FFC107")
            }
            val prefix = when (type) {
                SUCCESS -> "✦  "
                ERROR   -> "✕  "
                else    -> "◈  "
            }

            // Container
            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 16), dp(activity, 11), dp(activity, 20), dp(activity, 11))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F1015"))
                    cornerRadius = dp(activity, 14).toFloat()
                    setStroke(dp(activity, 1), borderColor)
                }
                elevation = dp(activity, 8).toFloat()
            }

            val label = TextView(activity).apply {
                text = "$prefix$message"
                textSize = 13f
                setTextColor(Color.parseColor("#E8E8E8"))
                letterSpacing = 0.01f
            }
            container.addView(label)

            // Adiciona na window
            val wm = activity.windowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(activity, 100)
            }

            wm.addView(container, params)

            // Remove após delay
            val delay = if (long) 3500L else 2000L
            handler.postDelayed({
                try { wm.removeView(container) } catch (_: Exception) {}
            }, delay)

        } catch (e: Exception) {
            showFallback(activity, message, long)
        }
    }

    private fun showFallback(context: Context, message: String, long: Boolean) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}
