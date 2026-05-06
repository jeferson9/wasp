package com.example.waspbrowser

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_LANGUAGE = "wasp_language"
        const val LANG_SYSTEM   = "system"

        val SUPPORTED_LANGUAGES = listOf(
            Triple(LANG_SYSTEM, "Follow system",        "🌐"),
            Triple("en",        "English",              "🇺🇸"),
            Triple("pt",        "Português",            "🇧🇷"),
            Triple("es",        "Español",              "🇪🇸"),
            Triple("fr",        "Français",             "🇫🇷"),
            Triple("de",        "Deutsch",              "🇩🇪"),
            Triple("it",        "Italiano",             "🇮🇹"),
            Triple("ru",        "Русский",              "🇷🇺"),
            Triple("zh-CN",     "中文 (简体)",           "🇨🇳"),
            Triple("ja",        "日本語",                "🇯🇵"),
            Triple("ko",        "한국어",                "🇰🇷"),
            Triple("hi",        "हिन्दी",                "🇮🇳"),
            Triple("id",        "Bahasa Indonesia",     "🇮🇩"),
            Triple("tr",        "Türkçe",               "🇹🇷"),
            Triple("vi",        "Tiếng Việt",           "🇻🇳"),
            Triple("pl",        "Polski",               "🇵🇱"),
            Triple("nl",        "Nederlands",           "🇳🇱"),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#111420"))
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 40.dp())
        }

        // ── Header ───────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 12.dp())
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(Color.parseColor("#C8D0E0"))
            setPadding(0, 0, 16.dp(), 0)
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C8D0E0"))
        })
        content.addView(header)
        content.addView(divider())

        // ── Seção: Aparência ─────────────────────────────
        content.addView(sectionLabel(getString(R.string.settings_section_appearance)))

        content.addView(settingsRow(
            title = getString(R.string.settings_theme),
            subtitle = getCurrentThemeLabel(),
            emoji = "🎨"
        ) { toggleTheme() })

        content.addView(divider())

        // ── Seção: Idioma ─────────────────────────────────
        content.addView(sectionLabel(getString(R.string.settings_section_language)))

        val currentLang = getCurrentLanguageCode()
        val currentLangEntry = SUPPORTED_LANGUAGES.find { it.first == currentLang } ?: SUPPORTED_LANGUAGES[0]
        val currentLangLabel = if (currentLang == LANG_SYSTEM) getString(R.string.settings_language_system) else currentLangEntry.second
        val currentLangFlag  = currentLangEntry.third

        content.addView(settingsRow(
            title    = getString(R.string.settings_language),
            subtitle = "$currentLangFlag  $currentLangLabel",
            emoji    = "🌍"
        ) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                showLanguagePicker()
            }, 150)
        })

        content.addView(divider())

        // ── Seção: Sobre ──────────────────────────────────
        content.addView(sectionLabel(getString(R.string.settings_section_about)))

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "1.0" }

        content.addView(settingsRow(
            title    = getString(R.string.settings_version),
            subtitle = "Wasp Browser v$versionName",
            emoji    = "ℹ",
            clickable = false
        ) {})

        root.addView(content)
        setContentView(root)

        // Abre o seletor automaticamente se solicitado via Intent
        if (intent.getBooleanExtra("show_language_picker", false)) {
            showLanguagePicker()
        }
    }

    private fun showLanguagePicker() {
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val dialog = BottomSheetDialog(this)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111420"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Handle
        val handleLp = LinearLayout.LayoutParams(36.dp(), 4.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 12.dp()
            bottomMargin = 8.dp()
        }
        outer.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dp().toFloat()
                setColor(Color.parseColor("#252A3D"))
            }
        }, handleLp)

        outer.addView(TextView(this).apply {
            text = getString(R.string.settings_language)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C8D0E0"))
            setPadding(20.dp(), 12.dp(), 20.dp(), 4.dp())
        })

        outer.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#1A1E2E"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))

        val listRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val currentLang = getCurrentLanguageCode()

        SUPPORTED_LANGUAGES.forEach { (code, name, flag) ->
            val isSelected = code == currentLang
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20.dp(), 12.dp(), 20.dp(), 12.dp())
                isClickable = true
                isFocusable = true
                if (isSelected) setBackgroundColor(Color.parseColor("#1A1E2E"))
                setOnClickListener {
                    dialog.dismiss()
                    applyLanguage(code)
                }
            }

            row.addView(TextView(this).apply {
                text = flag; textSize = 20f
                setPadding(0, 0, 16.dp(), 0)
            })

            row.addView(TextView(this).apply {
                text = if (code == LANG_SYSTEM) getString(R.string.settings_language_system) else name
                textSize = 14f
                setTextColor(if (isSelected) Color.parseColor("#FFD400") else Color.parseColor("#C8D0E0"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (isSelected) {
                row.addView(TextView(this).apply {
                    text = "✓"; textSize = 16f
                    setTextColor(Color.parseColor("#FFD400"))
                })
            }

            listRoot.addView(row)
        }

        val scroll = ScrollView(this).apply {
            addView(listRoot)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400.dp())
        }
        outer.addView(scroll)

        dialog.setContentView(outer)
        dialog.show()
    }

    private fun applyLanguage(code: String) {
        getSharedPreferences("wasp_settings", MODE_PRIVATE)
            .edit().putString(PREF_LANGUAGE, code).apply()

        val localeList = if (code == LANG_SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }

        AppCompatDelegate.setApplicationLocales(localeList)
        recreate()
    }

    private fun getCurrentLanguageCode(): String {
        return getSharedPreferences("wasp_settings", MODE_PRIVATE)
            .getString(PREF_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("wasp_settings", MODE_PRIVATE)
        val current = prefs.getString("theme", "dark") ?: "dark"
        val next = if (current == "dark") "light" else "dark"
        prefs.edit().putString("theme", next).apply()
        recreate()
    }

    private fun getCurrentThemeLabel(): String {
        val theme = getSharedPreferences("wasp_settings", MODE_PRIVATE)
            .getString("theme", "dark") ?: "dark"
        return if (theme == "dark") getString(R.string.settings_theme_dark)
        else getString(R.string.settings_theme_light)
    }

    private fun divider(): View {
        val dp = resources.displayMetrics.density
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#1A1E2E"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            )
        }
    }

    private fun sectionLabel(text: String): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#3D4560"))
            letterSpacing = 0.1f
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (6 * dp).toInt())
        }
    }

    private fun settingsRow(
        title: String,
        subtitle: String? = null,
        emoji: String,
        clickable: Boolean = true,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
            minimumHeight = 56.dp()
            isClickable = clickable
            isFocusable = clickable
            if (clickable) {
                setOnClickListener { onClick() }
            }

            val iconBox = LinearLayout(this@SettingsActivity).apply {
                gravity = Gravity.CENTER
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 10.dp().toFloat()
                    setColor(Color.parseColor("#1A1E2E"))
                    setStroke(1, Color.parseColor("#252A3D"))
                }
            }
            iconBox.addView(TextView(this@SettingsActivity).apply {
                text = emoji; textSize = 16f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#C8D0E0"))
            }, LinearLayout.LayoutParams(36.dp(), 36.dp()))
            addView(iconBox)

            val info = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp(), 0, 0, 0)
            }
            info.addView(TextView(this@SettingsActivity).apply {
                text = title; textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#C8D0E0"))
            })
            if (subtitle != null) {
                info.addView(TextView(this@SettingsActivity).apply {
                    text = subtitle; textSize = 11f
                    setTextColor(Color.parseColor("#3D4560"))
                    setPadding(0, 2.dp(), 0, 0)
                })
            }
            addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (clickable) {
                addView(TextView(this@SettingsActivity).apply {
                    text = "›"; textSize = 20f
                    setTextColor(Color.parseColor("#2E3550"))
                    gravity = Gravity.CENTER
                })
            }
        }
    }
}