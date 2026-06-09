package com.waspbrowser.app

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0

    private val steps = listOf(
        0  to "Inicializando motor...",
        20 to "Carregando engine Web3...",
        40 to "Verificando segurança...",
        60 to "Sincronizando Bee Engine...",
        80 to "Otimizando performance...",
        95 to "Pronto para navegar..."
    )

    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvPercent: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.setBackgroundColor(Color.parseColor("#08090d"))
        window.statusBarColor     = Color.parseColor("#08090d")
        window.navigationBarColor = Color.parseColor("#08090d")

        super.onCreate(savedInstanceState)

        // Inicializa o Start.io SDK cedo (no boot)
        runCatching {
            com.startapp.sdk.adsbase.StartAppSDK.init(
                applicationContext, "204731691", false
            )
            // HABILITADO MODO DE TESTE PARA DESENVOLVIMENTO
            com.startapp.sdk.adsbase.StartAppSDK.setTestAdsEnabled(false)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        val root = buildLayout()
        setContentView(root)

        val logo = root.findViewWithTag<View>("logo")
        logo?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)
            ?.setDuration(500)?.setInterpolator(AccelerateDecelerateInterpolator())?.start()

        val tagline = root.findViewWithTag<View>("tagline")
        tagline?.animate()?.alpha(1f)?.setStartDelay(350)?.setDuration(400)?.start()

        handler.postDelayed({ startProgress() }, 400)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildLayout(): FrameLayout {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#08090d"))

        // Centro: logo + tagline
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Logo: wasp-topbar-dark.webp dos assets
        val logoImage = android.widget.ImageView(this).apply {
            tag = "logo"
            alpha = 0f
            scaleX = 0.88f
            scaleY = 0.88f
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            try {
                val stream = assets.open("img/wasp-topbar-dark.webp")
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                stream.close()
                setImageBitmap(bitmap)
            } catch (_: Exception) {
                // fallback silencioso — asset não encontrado
            }
        }
        center.addView(logoImage, LinearLayout.LayoutParams(dp(300), dp(80)))

        val line = View(this).apply {
            setBackgroundColor(Color.parseColor("#33ffc107"))
        }
        val lineLp = LinearLayout.LayoutParams(dp(80), dp(1)).apply {
            topMargin = dp(8); bottomMargin = dp(12)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        center.addView(line, lineLp)

        val tagline = TextView(this).apply {
            tag = "tagline"
            text = "Browser Web3 • Bee Engine"
            setTextColor(Color.parseColor("#55ffffff"))
            textSize = 12f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            alpha = 0f
        }
        center.addView(tagline, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val centerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER; topMargin = -dp(60) }
        root.addView(center, centerLp)

        // Bottom: status + progress
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(36), 0, dp(36), dp(52))
        }

        tvStatus = TextView(this).apply {
            text = "Inicializando motor..."
            setTextColor(Color.parseColor("#88ffffff"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        bottom.addView(tvStatus, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable = buildProgressDrawable()
        }
        bottom.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(4)
        ))

        tvPercent = TextView(this).apply {
            text = "0%"
            setTextColor(Color.parseColor("#f7c600"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        bottom.addView(tvPercent, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        val bottomLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        root.addView(bottom, bottomLp)

        val version = TextView(this).apply {
            text = "v1.0 • Powered by Wasp"
            setTextColor(Color.parseColor("#22ffffff"))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(version, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        return root
    }

    private fun buildProgressDrawable(): android.graphics.drawable.LayerDrawable {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#1affc107"))
            cornerRadius = 999f
        }
        val fill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            colors = intArrayOf(Color.parseColor("#ffc107"), Color.parseColor("#ffd54f"))
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            cornerRadius = 999f
        }
        val clip = android.graphics.drawable.ClipDrawable(
            fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL
        )
        val layer = android.graphics.drawable.LayerDrawable(arrayOf(bg, clip))
        layer.setId(0, android.R.id.background)
        layer.setId(1, android.R.id.progress)
        return layer
    }

    private fun startProgress() { animateProgress() }

    private fun animateProgress() {
        val delay = when {
            progress < 30 -> 18L
            progress < 60 -> 22L
            progress < 85 -> 30L
            progress < 95 -> 45L
            else          -> 65L
        }
        handler.postDelayed({
            if (progress < 100) {
                progress++
                progressBar.progress = progress
                tvPercent.text = "$progress%"
                steps.lastOrNull { it.first <= progress }?.let { tvStatus.text = it.second }
                animateProgress()
            } else {
                tvStatus.text = "Bem-vindo ao Wasp \uD83D\uDC1D"
                handler.postDelayed({
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }, 350)
            }
        }, delay)
    }

    override fun onBackPressed() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}