package com.example.waspbrowser

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialog

object WaspMenuSheet {

    private val BG_DARK   = Color.parseColor("#111420")
    private val BG_ITEM   = Color.parseColor("#1A1E2E")
    private val BG_WEB3   = Color.parseColor("#1A120A")
    private val BG_BEE    = Color.parseColor("#0A1A10")
    private val STROKE    = Color.parseColor("#252A3D")
    private val TXT_PRI   = Color.parseColor("#C8D0E0")
    private val TXT_SEC   = Color.parseColor("#3D4560")
    private val TXT_MUT   = Color.parseColor("#4B5675")
    private val YELLOW    = Color.parseColor("#FFD400")
    private val GREEN     = Color.parseColor("#22C55E")

    fun show(
        context: Context,
        currentUrl: String,
        pageTitle: String,
        isFavorite: Boolean = false,
        onAction: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val domain = try {
            android.net.Uri.parse(currentUrl).host?.removePrefix("www.") ?: currentUrl
        } catch (_: Exception) { currentUrl }

        val scroll = ScrollView(context).apply {
            setBackgroundColor(BG_DARK)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
        }

        val dp = context.resources.displayMetrics.density

        fun Int.dp() = (this * dp).toInt()

        // Handle bar (single)
        val handleLp = LinearLayout.LayoutParams(36.dp(), 4.dp())
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.topMargin = 12.dp()
        handleLp.bottomMargin = 8.dp()
        root.addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dp().toFloat()
                setColor(Color.parseColor("#252A3D"))
            }
        }, handleLp)

        // URL row
        val urlRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 14.dp(), 14.dp(), 12.dp())
        }
        val urlInfo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        urlInfo.addView(TextView(context).apply {
            text = pageTitle.ifBlank { domain }
            setTextColor(TXT_PRI)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        urlInfo.addView(TextView(context).apply {
            text = "$domain · HTTPS"
            setTextColor(TXT_MUT)
            textSize = 11f
            setPadding(0, 2.dp(), 0, 0)
        })
        urlRow.addView(urlInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        var starred = isFavorite
        val starBtn = TextView(context).apply {
            text = if (starred) "★" else "☆"
            textSize = 20f
            setTextColor(if (starred) YELLOW else TXT_MUT)
            width = 40.dp()
            height = 40.dp()
            gravity = Gravity.CENTER
            setOnClickListener {
                starred = !starred
                text = if (starred) "★" else "☆"
                setTextColor(if (starred) YELLOW else TXT_MUT)
                onAction(if (starred) "favorite" else "unfavorite")
            }
        }
        urlRow.addView(starBtn)
        root.addView(urlRow)

        // Divider
        fun divider() = View(context).apply { setBackgroundColor(Color.parseColor("#1A1E2E")) }
            .also { root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)) }

        divider()

        // Quick row: Recarregar | Avançar | Compartilhar | Hive
        data class Quick(val emoji: String, val label: String, val action: String, val accent: Boolean = false)
        val quicks = listOf(
            Quick("↺", "Recarregar",    "reload"),
            Quick("→", "Avançar",       "forward"),
            Quick("⤴", "Compartilhar",  "share"),
            Quick("+", "Nova aba",      "newtab", true)
        )
        val quickRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        quicks.forEach { q ->
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(6.dp(), 14.dp(), 6.dp(), 12.dp())
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                }
                setOnClickListener { dialog.dismiss(); onAction(q.action) }
            }
            val iconBox = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 11.dp().toFloat()
                    setColor(if (q.accent) Color.parseColor("#1A120A") else BG_ITEM)
                    setStroke(1, if (q.accent) Color.parseColor("#3D2F00") else STROKE)
                }
            }
            iconBox.addView(TextView(context).apply {
                text = q.emoji
                textSize = 18f
                setTextColor(if (q.accent) YELLOW else TXT_MUT)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(38.dp(), 38.dp()))
            col.addView(iconBox)
            col.addView(TextView(context).apply {
                text = q.label
                textSize = 10f
                setTextColor(TXT_MUT)
                gravity = Gravity.CENTER
                setPadding(0, 5.dp(), 0, 0)
            })
            quickRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(quickRow)
        divider()

        // Section label
        fun sectionLabel(text: String) {
            root.addView(TextView(context).apply {
                this.text = text
                textSize = 10f
                setTextColor(Color.parseColor("#2E3550"))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.08f
                setPadding(16.dp(), 12.dp(), 16.dp(), 4.dp())
            })
        }

        // Menu item row
        fun menuItem(
            emoji: String, name: String, sub: String? = null,
            action: String, bgColor: Int = BG_ITEM, strokeColor: Int = STROKE,
            iconColor: Int = TXT_MUT, badge: String? = null, dot: Int? = null
        ) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10.dp(), 0, 10.dp(), 0)
                minimumHeight = 52.dp()
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 10.dp().toFloat()
                    setColor(Color.TRANSPARENT)
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.leftMargin = 6.dp(); lp.rightMargin = 6.dp()
                layoutParams = lp
                setOnClickListener { dialog.dismiss(); onAction(action) }
            }

            val iconBox = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 9.dp().toFloat()
                    setColor(bgColor)
                    setStroke(1, strokeColor)
                }
            }
            iconBox.addView(TextView(context).apply {
                text = emoji; textSize = 15f
                setTextColor(iconColor); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(34.dp(), 34.dp()))
            row.addView(iconBox)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dp(), 0, 0, 0)
            }
            info.addView(TextView(context).apply {
                text = name; textSize = 13f
                setTextColor(TXT_PRI); setTypeface(null, Typeface.BOLD)
            })
            if (sub != null) info.addView(TextView(context).apply {
                text = sub; textSize = 10f; setTextColor(TXT_SEC)
                setPadding(0, 1.dp(), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (badge != null) {
                row.addView(TextView(context).apply {
                    text = badge; textSize = 9f; setTextColor(YELLOW)
                    setTypeface(null, Typeface.BOLD)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 4.dp().toFloat()
                        setColor(Color.parseColor("#1A120A"))
                        setStroke(1, Color.parseColor("#3D2F00"))
                    }
                    setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
                })
            } else if (dot != null) {
                row.addView(View(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(dot)
                    }
                }, LinearLayout.LayoutParams(8.dp(), 8.dp()))
            } else if (sub != null || name == "Favoritos" || name == "Histórico" || name == "Downloads" || name == "Configurações") {
                row.addView(TextView(context).apply {
                    text = "›"; textSize = 18f
                    setTextColor(Color.parseColor("#2E3550"))
                    gravity = Gravity.CENTER
                })
            }

            root.addView(row)
        }

        sectionLabel("NAVEGAÇÃO")
        menuItem("🌐", "Traduzir página", "Google Translate", "translate",
            bgColor = Color.parseColor("#0A0F1A"), strokeColor = Color.parseColor("#1A2A4A"),
            iconColor = Color.parseColor("#60A5FA"))
        menuItem("★", "Favoritos",   "Sites salvos",      "favorites")
        menuItem("◷", "Histórico",   "Páginas visitadas", "history")
        menuItem("↓", "Downloads",   "Arquivos baixados", "downloads")

        divider(); root.addView(View(context), LinearLayout.LayoutParams(1, 6.dp()))
        sectionLabel("WEB3")
        menuItem("◈", "Web3 Hub",    "DApps e carteiras", "web3",
            bgColor = BG_WEB3, strokeColor = Color.parseColor("#3D2F00"),
            iconColor = YELLOW, badge = "NOVO")
        menuItem("⚡", "Bee Engine",  "Mineração ativa",   "bee",
            bgColor = BG_BEE, strokeColor = Color.parseColor("#1A4D28"),
            iconColor = GREEN, dot = GREEN)

        divider(); root.addView(View(context), LinearLayout.LayoutParams(1, 6.dp()))
        menuItem("⚙", "Configurações", null, "settings")
        menuItem("ℹ", "Sobre o Wasp",  null, "about")

        root.addView(View(context), LinearLayout.LayoutParams(1, 24.dp()))

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }
}