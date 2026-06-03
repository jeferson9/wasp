package com.waspbrowser.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

/*
 * Gerenciador do Hive (remover sites) — versão NATIVA.
 * Criada para fugir do bug de toque do WebView (long-press + diálogo no
 * Hive em HTML era instável). Aqui a remoção é 100% nativa e confiável.
 *
 * Fluxo: o JS abre esta Activity passando a lista de sites (JSON em EXTRA_SITES);
 * ao tocar em remover, devolve o id em EXTRA_REMOVE_ID via setResult, e o JS
 * remove do localStorage e re-renderiza.
 *
 * Estilo: identidade visual do Wasp (amarelo #FFD400, fundo #111420,
 * cards #1A1E2E, stroke #252A3D). Tudo montado em código (sem XML).
 */
class HiveManagerActivity : Activity() {

    companion object {
        const val EXTRA_SITES = "sites"
        const val EXTRA_REMOVE_ID = "remove_id"
        const val REQUEST_CODE = 9001

        // Paleta Wasp
        private const val WASP_YELLOW   = 0xFFFFD400.toInt()
        private const val WASP_BG       = 0xFF111420.toInt()
        private const val WASP_CARD     = 0xFF1A1E2E.toInt()
        private const val WASP_STROKE   = 0xFF252A3D.toInt()
        private const val WASP_TEXT     = 0xFFC8D0E0.toInt()
        private const val WASP_TEXT_DIM = 0xFF6B7390.toInt()
        private const val WASP_RED      = 0xFFFF4D4D.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = WASP_BG
        window.navigationBarColor = WASP_BG

        val sitesJson = intent.getStringExtra(EXTRA_SITES) ?: "[]"
        val sites = mutableListOf<Pair<String, String>>() // id, name
        try {
            val arr = org.json.JSONArray(sitesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sites.add(obj.optString("id", "") to obj.optString("name", ""))
            }
        } catch (_: Exception) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(WASP_BG)
            setPadding(0, dp(44), 0, 0)
        }

        // ── Header ────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        // "barrinha" amarela à esquerda do título (assinatura Wasp)
        header.addView(View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(WASP_YELLOW)
            }
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(22)).apply {
                rightMargin = dp(10)
            }
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@HiveManagerActivity).apply {
                text = "Gerenciar Hive"
                textSize = 19f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@HiveManagerActivity).apply {
                text = if (sites.isEmpty()) "Nenhum site salvo"
                       else "${sites.size} site(s) • toque em remover"
                textSize = 12f
                setTextColor(WASP_TEXT_DIM)
                setPadding(0, dp(2), 0, 0)
            })
        })
        // Botão fechar (X) redondo
        header.addView(TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(WASP_TEXT)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(WASP_CARD)
                setStroke(dp(1), WASP_STROKE)
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        })
        root.addView(header)

        // ── Lista ───────────────────────────────────────────────────────────
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(80))
        }

        if (sites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "🐝\n\nNenhum site no Hive ainda.\nAdicione sites pelo botão +"
                setTextColor(WASP_TEXT_DIM)
                textSize = 14f
                gravity = Gravity.CENTER
                setLineSpacing(dp(4).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(60) }
            })
        } else {
            sites.forEach { (id, name) ->
                // Card por site
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(WASP_CARD)
                        setStroke(dp(1), WASP_STROKE)
                    }
                    setPadding(dp(16), dp(14), dp(12), dp(14))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(10) }
                }
                // Inicial do site num quadradinho amarelo (avatar)
                card.addView(TextView(this).apply {
                    text = (name.firstOrNull()?.uppercase() ?: "•")
                    textSize = 16f
                    setTextColor(0xFF111420.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(WASP_YELLOW)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                        rightMargin = dp(14)
                    }
                })
                card.addView(TextView(this).apply {
                    text = name
                    textSize = 15f
                    setTextColor(WASP_TEXT)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                // Botão remover (lixeira) — pílula vermelha discreta
                card.addView(TextView(this).apply {
                    text = "🗑  Remover"
                    textSize = 12.5f
                    setTextColor(WASP_RED)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        cornerRadius = dp(20).toFloat()
                        setColor(0x1AFF4D4D.toInt())            // vermelho translúcido
                        setStroke(dp(1), 0x33FF4D4D.toInt())
                    }
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    setOnClickListener {
                        val result = Intent().putExtra(EXTRA_REMOVE_ID, id)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                })
                list.addView(card)
            }
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
