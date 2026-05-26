package com.waspbrowser.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object WaspToast {

    const val NORMAL  = 0
    const val SUCCESS = 1
    const val ERROR   = 2

    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String, type: Int = NORMAL, long: Boolean = false) {
        handler.post {
            val prefix = when (type) {
                SUCCESS -> "✓ "
                ERROR   -> "✕ "
                else    -> ""
            }
            Toast.makeText(
                context,
                "$prefix$message",
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }
}
