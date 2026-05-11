package com.example.waspbrowser

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * EpochAlertActivity
 * Janela flutuante estilo PiP — aparece sobre qualquer tela
 * quando o epoch termina. Sem WebView, só UI nativa.
 */
class EpochAlertActivity : Activity() {

    companion object {
        private const val CHANNEL_ID = "epoch_alert_channel"
        private const val NOTIF_ID   = 99
        private const val EXTRA_WALLET = "wallet"

        fun notify(context: Context, wallet: String) {
            createChannel(context)
            sendNotification(context, wallet)
            showFloatingAlert(context, wallet)
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Epoch NACKL",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Aviso de epoch concluído"
                    enableLights(true)
                    lightColor = Color.parseColor("#F5C518")
                    enableVibration(true)
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(channel)
            }
        }

        private fun sendNotification(context: Context, wallet: String) {
            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, BeeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bee_tech)
                .setContentTitle("⚡ Epoch NACKL concluído!")
                .setContentText("Reward disponível para $wallet — toque para coletar")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .setColor(Color.parseColor("#F5C518"))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
            } catch (e: SecurityException) {
                android.util.Log.w("EpochAlert", "Sem permissão de notificação")
            }
        }

        private fun showFloatingAlert(context: Context, wallet: String) {
            val intent = Intent(context, EpochAlertActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_WALLET, wallet)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wallet = intent.getStringExtra(EXTRA_WALLET) ?: ""

        // Janela flutuante pequena no topo
        window.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)

            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(dm)
            setLayout((dm.widthPixels * 0.88).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)

            val params = attributes
            params.y = (80 * dm.density).toInt()
            attributes = params
        }

        val dp = resources.displayMetrics.density

        // Layout da janela
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20*dp).toInt(), (18*dp).toInt(), (20*dp).toInt(), (16*dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * dp
                setColor(Color.parseColor("#111620"))
                setStroke((1.5*dp).toInt(), Color.parseColor("#F5C518"))
            }
            elevation = 16 * dp
        }

        // Título
        val title = TextView(this).apply {
            text = "⚡ Epoch NACKL concluído!"
            setTextColor(Color.parseColor("#F5C518"))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        // Subtítulo
        val sub = TextView(this).apply {
            text = if (wallet.isNotEmpty()) "Wallet: $wallet" else "Reward disponível para coleta"
            setTextColor(Color.parseColor("#7A8090"))
            textSize = 12f
            setPadding(0, (4*dp).toInt(), 0, (12*dp).toInt())
        }

        // Botões
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val btnDismiss = Button(this).apply {
            text = "Agora não"
            setTextColor(Color.parseColor("#7A8090"))
            background = null
            textSize = 13f
            setOnClickListener { finish() }
        }

        val btnCollect = Button(this).apply {
            text = "Abrir painel →"
            setTextColor(Color.parseColor("#0d0d0d"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * dp
                setColor(Color.parseColor("#F5C518"))
            }
            textSize = 13f
            setPadding((16*dp).toInt(), (8*dp).toInt(), (16*dp).toInt(), (8*dp).toInt())
            setOnClickListener {
                finish()
                val intent = Intent(this@EpochAlertActivity, BeeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }

        btnRow.addView(btnDismiss)
        btnRow.addView(btnCollect)

        root.addView(title)
        root.addView(sub)
        root.addView(btnRow)

        setContentView(root)

        // Auto-dismiss após 8 segundos
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 8000)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        finish()
        return true
    }
}
