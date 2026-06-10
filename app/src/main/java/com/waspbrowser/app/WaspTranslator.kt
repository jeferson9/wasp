package com.waspbrowser.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.mozilla.geckoview.GeckoSession

object WaspTranslator {

    var activeTargetLang: String? = null
        private set

    var translatedUrl: String? = null
        private set

    private var badge: TextView? = null

    data class Lang(val flag: String, val name: String, val code: String)

    private val LANGUAGES_BY_REGION: List<Pair<Int, List<Lang>>> = listOf(

        R.string.tr_reg_americas to listOf(
            Lang("🇧🇷", "Português (BR)",          "pt"),
            Lang("🇺🇸", "English",                 "en"),
            Lang("🇪🇸", "Español",                 "es"),
            Lang("🇫🇷", "Français (Caribe)",       "fr"),
            Lang("🇭🇹", "Haitian Creole",          "ht"),
            Lang("🇵🇪", "Quechua",                 "qu"),
            Lang("🇬🇾", "Guarani",                 "gn"),
            Lang("🇲🇽", "Nahuatl",                 "nah"),
            Lang("🇧🇴", "Aymara",                  "ay")
        ),

        R.string.tr_reg_west_europe to listOf(
            Lang("🇩🇪", "Deutsch",                 "de"),
            Lang("🇮🇹", "Italiano",                "it"),
            Lang("🇵🇹", "Português (PT)",          "pt"),
            Lang("🇳🇱", "Nederlands",              "nl"),
            Lang("🇵🇱", "Polski",                  "pl"),
            Lang("🇸🇪", "Svenska",                 "sv"),
            Lang("🇳🇴", "Norsk",                   "no"),
            Lang("🇩🇰", "Dansk",                   "da"),
            Lang("🇫🇮", "Suomi",                   "fi"),
            Lang("🇮🇸", "Íslenska",                "is"),
            Lang("🇱🇺", "Luxembourgish",           "lb"),
            Lang("🇮🇪", "Irish (Gaeilge)",         "ga"),
            Lang("🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scottish Gaelic",         "gd"),
            Lang("🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Welsh (Cymraeg)",         "cy"),
            Lang("🇪🇸", "Català",                  "ca"),
            Lang("🇪🇸", "Galego",                  "gl"),
            Lang("🇪🇸", "Euskara (Basque)",        "eu"),
            Lang("🇲🇹", "Maltese",                 "mt"),
            Lang("🇨🇭", "Romansh",                 "rm")
        ),

        R.string.tr_reg_central_east_europe to listOf(
            Lang("🇦🇱", "Shqip (Albanian)",        "sq"),
            Lang("🇧🇦", "Bosanski",                "bs"),
            Lang("🇭🇷", "Hrvatski",                "hr"),
            Lang("🇸🇮", "Slovenščina",             "sl"),
            Lang("🇸🇰", "Slovenčina",              "sk"),
            Lang("🇨🇿", "Čeština",                 "cs"),
            Lang("🇭🇺", "Magyar",                  "hu"),
            Lang("🇷🇴", "Română",                  "ro"),
            Lang("🇷🇸", "Srpski",                  "sr"),
            Lang("🇲🇰", "Македонски",              "mk"),
            Lang("🇧🇬", "Български",               "bg"),
            Lang("🇬🇷", "Ελληνικά",                "el"),
            Lang("🇱🇹", "Lietuvių",                "lt"),
            Lang("🇱🇻", "Latviešu",                "lv"),
            Lang("🇪🇪", "Eesti",                   "et"),
            Lang("🇧🇾", "Беларуская",              "be"),
            Lang("🇺🇦", "Українська",              "uk"),
            Lang("🇷🇺", "Русский",                 "ru"),
            Lang("🇪🇺", "Esperanto",               "eo"),
            Lang("🇪🇺", "Latin",                   "la")
        ),

        R.string.tr_reg_caucasus_central_asia to listOf(
            Lang("🇦🇿", "Azərbaycan",              "az"),
            Lang("🇬🇪", "ქართული (Georgian)",      "ka"),
            Lang("🇦🇲", "Հայերեն (Armenian)",      "hy"),
            Lang("🇰🇿", "Қазақша (Kazakh)",        "kk"),
            Lang("🇺🇿", "O'zbek (Uzbek)",          "uz"),
            Lang("🇹🇯", "Тоҷикӣ (Tajik)",          "tg"),
            Lang("🇹🇲", "Türkmen",                 "tk"),
            Lang("🇰🇬", "Кыргызча (Kyrgyz)",       "ky")
        ),

        R.string.tr_reg_east_asia to listOf(
            Lang("🇨🇳", "中文 (Simplificado)",      "zh-CN"),
            Lang("🇹🇼", "中文 (Tradicional)",       "zh-TW"),
            Lang("🇯🇵", "日本語",                   "ja"),
            Lang("🇰🇷", "한국어",                   "ko"),
            Lang("🇲🇳", "Монгол (Mongolian)",      "mn"),
            Lang("🇨🇳", "Uyghur",                  "ug"),
            Lang("🇨🇳", "Tibetan",                 "bo")
        ),

        R.string.tr_reg_se_asia to listOf(
            Lang("🇻🇳", "Tiếng Việt",              "vi"),
            Lang("🇹🇭", "ภาษาไทย",                 "th"),
            Lang("🇮🇩", "Bahasa Indonesia",        "id"),
            Lang("🇲🇾", "Bahasa Melayu",           "ms"),
            Lang("🇵🇭", "Filipino (Tagalog)",      "tl"),
            Lang("🇲🇲", "မြန်မာဘာသာ (Burmese)",    "my"),
            Lang("🇰🇭", "ភាសាខ្មែរ (Khmer)",        "km"),
            Lang("🇱🇦", "ລາວ (Lao)",               "lo"),
            Lang("🇱🇰", "සිංහල (Sinhala)",         "si"),
            Lang("🇵🇭", "Cebuano",                 "ceb"),
            Lang("🇵🇭", "Hmong",                   "hmn"),
            Lang("🇮🇩", "Javanese",                "jv"),
            Lang("🇮🇩", "Sundanese",               "su"),
            Lang("🇵🇭", "Ilocano",                 "ilo")
        ),

        R.string.tr_reg_south_asia to listOf(
            Lang("🇮🇳", "हिन्दी (Hindi)",           "hi"),
            Lang("🇮🇳", "বাংলা (Bengali)",          "bn"),
            Lang("🇮🇳", "తెలుగు (Telugu)",          "te"),
            Lang("🇮🇳", "मराठी (Marathi)",          "mr"),
            Lang("🇮🇳", "தமிழ் (Tamil)",            "ta"),
            Lang("🇮🇳", "اردو (Urdu)",             "ur"),
            Lang("🇮🇳", "ગુજરાતી (Gujarati)",       "gu"),
            Lang("🇮🇳", "ಕನ್ನಡ (Kannada)",          "kn"),
            Lang("🇮🇳", "ਪੰਜਾਬੀ (Punjabi)",         "pa"),
            Lang("🇮🇳", "മലയാളം (Malayalam)",       "ml"),
            Lang("🇮🇳", "ଓଡ଼ିଆ (Odia)",             "or"),
            Lang("🇮🇳", "অসমীয়া (Assamese)",       "as"),
            Lang("🇮🇳", "संस्कृत (Sanskrit)",        "sa"),
            Lang("🇳🇵", "नेपाली (Nepali)",          "ne"),
            Lang("🇵🇰", "سنڌي (Sindhi)",           "sd"),
            Lang("🇦🇫", "پښتو (Pashto)",           "ps"),
            Lang("🇮🇷", "فارسی (Persian/Farsi)",   "fa"),
            Lang("🇧🇹", "Dzongkha",                "dz")
        ),

        R.string.tr_reg_middle_east to listOf(
            Lang("🇸🇦", "العربية (Arabic)",         "ar"),
            Lang("🇮🇱", "עברית (Hebrew)",           "iw"),
            Lang("🇹🇷", "Türkçe",                  "tr"),
            Lang("🇮🇶", "Kurdish (Kurmanji)",       "ku"),
            Lang("🇮🇶", "Kurdish (Sorani)",         "ckb")
        ),

        R.string.tr_reg_africa to listOf(
            Lang("🇿🇦", "Zulu",                    "zu"),
            Lang("🇿🇦", "Xhosa",                   "xh"),
            Lang("🇿🇦", "Afrikaans",               "af"),
            Lang("🇿🇦", "Sesotho",                 "st"),
            Lang("🇿🇦", "Tswana",                  "tn"),
            Lang("🇳🇬", "Yoruba",                  "yo"),
            Lang("🇬🇭", "Twi",                     "tw"),
            Lang("🇬🇭", "Akan",                    "ak"),
            Lang("🇳🇬", "Igbo",                    "ig"),
            Lang("🇳🇬", "Hausa",                   "ha"),
            Lang("🇸🇳", "Wolof",                   "wo"),
            Lang("🇸🇳", "Fulani (Fula)",           "ff"),
            Lang("🇰🇪", "Swahili",                 "sw"),
            Lang("🇸🇴", "Soomaali (Somali)",       "so"),
            Lang("🇪🇹", "አማርኛ (Amharic)",          "am"),
            Lang("🇪🇹", "Tigrinya",                "ti"),
            Lang("🇲🇬", "Malagasy",                "mg"),
            Lang("🇷🇼", "Kinyarwanda",             "rw"),
            Lang("🇨🇩", "Lingala",                 "ln"),
            Lang("🇺🇬", "Luganda",                 "lg"),
            Lang("🇲🇼", "Nyanja (Chichewa)",       "ny"),
            Lang("🇿🇼", "Shona",                   "sn"),
            Lang("🇸🇱", "Krio",                    "kri"),
            Lang("🇧🇯", "Ewe",                     "ee"),
            Lang("🇲🇦", "Tamazight (Berber)",      "ber"),
            Lang("🇲🇦", "Darija (Marroquino)",     "ary"),
            Lang("🇸🇩", "Beja",                    "bej")
        ),

        R.string.tr_reg_pacific_regional to listOf(
            Lang("🇵🇬", "Tok Pisin",               "tpi"),
            Lang("🇫🇯", "Fijian",                  "fj"),
            Lang("🇲🇻", "Dhivehi",                 "dv"),
            Lang("🇷🇺", "Tatar",                   "tt"),
            Lang("🇷🇺", "Bashkir",                 "ba"),
            Lang("🇷🇺", "Chuvash",                 "cv"),
            Lang("🇷🇺", "Yakut (Sakha)",           "sah"),
            Lang("🇷🇺", "Buryat",                  "bua"),
            Lang("🇷🇺", "Udmurt",                  "udm"),
            Lang("🇨🇳", "Cantonese (粵語)",         "yue")
        )
    )

    // ─────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────
    fun show(
        context: Context,
        currentUrl: String,
        onBadgeRequest: (TextView?) -> Unit,
        onInjectJS: (String) -> Unit,
        onReload: () -> Unit
    ) {
        if (activeTargetLang != null && translatedUrl == currentUrl) {
            showActiveDialog(context, currentUrl, onBadgeRequest, onInjectJS, onReload)
            return
        }
        showLanguagePicker(context, currentUrl, onBadgeRequest, onInjectJS)
    }

    // ─────────────────────────────────────────────────────
    // PICKER com busca fixa no topo + lista scrollável
    // ─────────────────────────────────────────────────────
    private fun showLanguagePicker(
        context: Context,
        currentUrl: String,
        onBadgeRequest: (TextView?) -> Unit,
        onInjectJS: (String) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val dialog = BottomSheetDialog(context)

        // Outer: NÃO scrollável — busca fica fixa no topo
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111420"))
        }

        // Handle
        val handleLp = LinearLayout.LayoutParams(36.dp(), 4.dp())
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.topMargin = 12.dp(); handleLp.bottomMargin = 4.dp()
        outer.addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dp().toFloat()
                setColor(Color.parseColor("#252A3D"))
            }
        }, handleLp)

        // Título
        outer.addView(TextView(context).apply {
            text = context.getString(R.string.tr_title)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C8D0E0"))
            setPadding(20.dp(), 10.dp(), 20.dp(), 2.dp())
        })

        val total = LANGUAGES_BY_REGION.sumOf { it.second.size }
        outer.addView(TextView(context).apply {
            text = context.getString(R.string.tr_subtitle, total)
            textSize = 11f
            setTextColor(Color.parseColor("#3D4560"))
            setPadding(20.dp(), 0, 20.dp(), 10.dp())
        })

        // Campo de busca — fixo, não sobe com o scroll
        val searchBox = EditText(context).apply {
            hint = "🔍  Buscar idioma..."
            textSize = 13f
            setTextColor(Color.parseColor("#C8D0E0"))
            setHintTextColor(Color.parseColor("#3D4560"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12.dp().toFloat()
                setColor(Color.parseColor("#1A1E2E"))
                setStroke(1, Color.parseColor("#252A3D"))
            }
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
        }
        outer.addView(searchBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = 16.dp(); rightMargin = 16.dp(); bottomMargin = 8.dp() })

        outer.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#1A1E2E"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Lista scrollável
        val listRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 32.dp())
        }
        val scrollView = ScrollView(context).apply { addView(listRoot) }
        outer.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ── Helpers de construção de views ───────────────
        fun buildLangRow(lang: Lang) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 0, 20.dp(), 0)
            isClickable = true; isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 50.dp()
            )
            setOnClickListener {
                dialog.dismiss()
                startTranslation(context, currentUrl, lang.code, lang.name, onBadgeRequest, onInjectJS)
            }
            addView(TextView(context).apply {
                text = lang.flag; textSize = 20f; gravity = Gravity.CENTER
                setPadding(0, 0, 14.dp(), 0)
                layoutParams = LinearLayout.LayoutParams(34.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = lang.name; textSize = 13f
                setTextColor(Color.parseColor("#C8D0E0"))
            })
        }

        fun buildDivider() = View(context).apply {
            setBackgroundColor(Color.parseColor("#16192A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        }

        fun buildSectionHeader(text: String) = TextView(context).apply {
            this.text = text; textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#3D4560"))
            letterSpacing = 0.08f
            setPadding(20.dp(), 16.dp(), 20.dp(), 6.dp())
        }

        // ── Renderiza lista completa com seções ──────────
        fun renderFull() {
            listRoot.removeAllViews()
            LANGUAGES_BY_REGION.forEach { (regionName, langs) ->
                listRoot.addView(buildSectionHeader(context.getString(regionName)))
                langs.forEach { lang ->
                    listRoot.addView(buildLangRow(lang))
                    listRoot.addView(buildDivider())
                }
            }
        }

        // ── Renderiza lista filtrada (sem cabeçalhos) ────
        fun renderFiltered(query: String) {
            listRoot.removeAllViews()
            val q = query.trim().lowercase()
            val results = LANGUAGES_BY_REGION
                .flatMap { it.second }
                .filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }

            if (results.isEmpty()) {
                listRoot.addView(TextView(context).apply {
                    text = "Nenhum idioma encontrado"
                    textSize = 13f
                    setTextColor(Color.parseColor("#3D4560"))
                    gravity = Gravity.CENTER
                    setPadding(20.dp(), 40.dp(), 20.dp(), 40.dp())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            } else {
                results.forEach { lang ->
                    listRoot.addView(buildLangRow(lang))
                    listRoot.addView(buildDivider())
                }
            }
        }

        // Popula lista inicial
        renderFull()

        // Listener de busca em tempo real
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString() ?: ""
                scrollView.scrollTo(0, 0)
                if (q.isBlank()) renderFull() else renderFiltered(q)
            }
        })

        dialog.setContentView(outer)
        dialog.show()

        // Abre teclado no campo de busca automaticamente
        searchBox.postDelayed({
            searchBox.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    // ─────────────────────────────────────────────────────
    // DIALOG — PÁGINA JÁ TRADUZIDA
    // ─────────────────────────────────────────────────────
    private fun showActiveDialog(
        context: Context,
        currentUrl: String,
        onBadgeRequest: (TextView?) -> Unit,
        onInjectJS: (String) -> Unit,
        onReload: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111420"))
            setPadding(0, 0, 0, 24.dp())
        }

        val handleLp = LinearLayout.LayoutParams(36.dp(), 4.dp())
        handleLp.gravity = Gravity.CENTER_HORIZONTAL
        handleLp.topMargin = 12.dp(); handleLp.bottomMargin = 16.dp()
        root.addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dp().toFloat()
                setColor(Color.parseColor("#252A3D"))
            }
        }, handleLp)

        root.addView(TextView(context).apply {
            text = context.getString(R.string.tr_translated_badge)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(20.dp(), 0, 20.dp(), 4.dp())
        })

        val currentLangName = LANGUAGES_BY_REGION
            .flatMap { it.second }
            .firstOrNull { it.code == activeTargetLang }?.name
            ?: activeTargetLang?.uppercase() ?: "—"

        root.addView(TextView(context).apply {
            text = "Traduzida para: $currentLangName"
            textSize = 12f
            setTextColor(Color.parseColor("#3D4560"))
            setPadding(20.dp(), 0, 20.dp(), 20.dp())
        })

        fun actionRow(emoji: String, label: String, sub: String, onClick: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20.dp(), 14.dp(), 20.dp(), 14.dp())
                isClickable = true; isFocusable = true
                setOnClickListener { dialog.dismiss(); onClick() }
            }
            val iconBox = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 10.dp().toFloat()
                    setColor(Color.parseColor("#1A1E2E"))
                    setStroke(1, Color.parseColor("#252A3D"))
                }
            }
            iconBox.addView(TextView(context).apply {
                text = emoji; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#C8D0E0"))
            }, LinearLayout.LayoutParams(40.dp(), 40.dp()))
            row.addView(iconBox)
            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 0, 0, 0)
            }
            info.addView(TextView(context).apply {
                text = label; textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#C8D0E0"))
            })
            info.addView(TextView(context).apply {
                text = sub; textSize = 10f
                setTextColor(Color.parseColor("#3D4560"))
                setPadding(0, 2.dp(), 0, 0)
            })
            row.addView(info)
            root.addView(row)
            root.addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#1A1E2E"))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }

        val total = LANGUAGES_BY_REGION.sumOf { it.second.size }
        actionRow("🔄", context.getString(R.string.tr_other_lang), context.getString(R.string.tr_other_lang_sub, total)) {
            activeTargetLang = null; translatedUrl = null
            showLanguagePicker(context, currentUrl, onBadgeRequest, onInjectJS)
        }
        actionRow("↩️", context.getString(R.string.tr_view_original), context.getString(R.string.tr_view_original_sub)) {
            revertTranslation(onBadgeRequest, onReload)
        }

        dialog.setContentView(root)
        dialog.show()
    }

    // ─────────────────────────────────────────────────────
    // INICIA TRADUÇÃO
    // ─────────────────────────────────────────────────────
    private fun startTranslation(
        context: Context,
        currentUrl: String,
        targetLang: String,
        langName: String,
        onBadgeRequest: (TextView?) -> Unit,
        onInjectJS: (String) -> Unit
    ) {
        activeTargetLang = targetLang
        translatedUrl = currentUrl

        val dp = context.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val b = TextView(context).apply {
            text = "⟳ Traduzindo..."
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 20.dp().toFloat()
                setColor(Color.parseColor("#0A1628"))
                setStroke(1, Color.parseColor("#1A3050"))
            }
            setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            tag = "translate_badge"
        }
        badge = b
        onBadgeRequest(b)

        onInjectJS(buildTranslatorJS(targetLang))

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            badge?.let { bv ->
                bv.text = "🌐 $langName"
                bv.setTextColor(Color.parseColor("#22C55E"))
                bv.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 20.dp().toFloat()
                    setColor(Color.parseColor("#0A1A0F"))
                    setStroke(1, Color.parseColor("#1A4D28"))
                }
            }
        }, 4000)
    }

    // ─────────────────────────────────────────────────────
    // REVERTE
    // ─────────────────────────────────────────────────────
    fun revertTranslation(
        onBadgeRequest: (TextView?) -> Unit,
        onReload: () -> Unit
    ) {
        activeTargetLang = null
        translatedUrl = null
        badge = null
        onBadgeRequest(null)
        onReload()
    }

    fun onNavigate(newUrl: String, onBadgeRequest: (TextView?) -> Unit) {
        if (translatedUrl != null && newUrl != translatedUrl) {
            activeTargetLang = null
            translatedUrl = null
            badge = null
            onBadgeRequest(null)
        }
    }

    // ─────────────────────────────────────────────────────
    // JAVASCRIPT INJETADO NO GECKOVIEW
    // ─────────────────────────────────────────────────────
    private fun buildTranslatorJS(targetLang: String): String = """
(function() {
    window.__waspTranslating = true;

    var TARGET = '$targetLang';

    var SKIP = new Set(['SCRIPT','STYLE','NOSCRIPT','CODE','PRE',
        'TEXTAREA','INPUT','SELECT','OPTION','IFRAME','SVG','MATH','HEAD']);

    function collectNodes(root) {
        var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode: function(n) {
                var p = n.parentElement;
                if (!p || SKIP.has(p.tagName)) return NodeFilter.FILTER_REJECT;
                var t = n.textContent.trim();
                if (t.length < 2) return NodeFilter.FILTER_SKIP;
                if (/^[\d\s.,:\-+%${'$'}\u20ac#@!?*\/\\|()\[\]{}]+${'$'}/.test(t)) return NodeFilter.FILTER_SKIP;
                return NodeFilter.FILTER_ACCEPT;
            }
        });
        var list = [], n;
        while ((n = walker.nextNode())) list.push(n);
        return list;
    }

    async function translateBatch(texts) {
        var q = encodeURIComponent(texts.join('\n||||\n'));
        var url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl='
            + TARGET + '&dt=t&dj=1&q=' + q;
        try {
            var r = await fetch(url);
            var d = await r.json();
            var out = '';
            if (d.sentences) d.sentences.forEach(function(s){ if (s.trans) out += s.trans; });
            return out.split(/\s*\|\|\|\|\s*/);
        } catch(e) { return texts; }
    }

    async function run() {
        var nodes = collectNodes(document.body);
        if (!nodes.length) return;
        var chunks = [], cur = [], len = 0;
        nodes.forEach(function(n) {
            var t = n.textContent.trim();
            if (len + t.length > 4800 || cur.length >= 100) { chunks.push(cur); cur = []; len = 0; }
            cur.push(n); len += t.length;
        });
        if (cur.length) chunks.push(cur);
        for (var i = 0; i < chunks.length; i++) {
            var chunk = chunks[i];
            var txts  = chunk.map(function(n){ return n.textContent.trim(); });
            var trans = await translateBatch(txts);
            for (var j = 0; j < chunk.length; j++) {
                if (trans[j] && trans[j].trim()) {
                    var o = chunk[j].textContent;
                    chunk[j].textContent = o.match(/^\s*/)[0] + trans[j].trim() + o.match(/\s*${'$'}/)[0];
                }
            }
            if (i < chunks.length - 1) await new Promise(function(r){ setTimeout(r, 120); });
        }
    }

    run().catch(function(e){ console.warn('WaspTranslator:', e); });
})();
    """.trimIndent()
}