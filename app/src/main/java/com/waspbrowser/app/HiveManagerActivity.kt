package com.waspbrowser.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class HiveManagerActivity : Activity() {

    companion object {
        const val EXTRA_SITES = "sites"    // JSON array string passado pelo JS
        const val EXTRA_REMOVE_ID = "remove_id"
        const val REQUEST_CODE = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sitesJson = intent.getStringExtra(EXTRA_SITES) ?: "[]"
        val sites = mutableListOf<Pair<String,String>>() // id, name

        try {
            val arr = org.json.JSONArray(sitesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sites.add(obj.optString("id","") to obj.optString("name",""))
            }
        } catch (_: Exception) {}

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF08090D.toInt())
            setPadding(0, dp(48), 0, 0)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        header.addView(TextView(this).apply {
            text = "Gerenciar Hive"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(Button(this).apply {
            text = "Fechar"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x33FFFFFF)
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        })
        root.addView(header)
        root.addView(View(this).apply {
            setBackgroundColor(0x33FFC107)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        })

        // Lista
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(80))
        }

        if (sites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nenhum site no Hive"
                setTextColor(0x99FFFFFF.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(32) }
            })
        } else {
            sites.forEach { (id, name) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(12), dp(4), dp(12))
                }
                row.addView(TextView(this).apply {
                    text = name
                    textSize = 15f
                    setTextColor(0xFFE8E8E8.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(Button(this).apply {
                    text = "Remover"
                    textSize = 12f
                    setTextColor(0xFFFFFFFF.toInt())
                    setBackgroundColor(0xFFFF3B30.toInt())
                    setPadding(dp(12), dp(4), dp(12), dp(4))
                    setOnClickListener {
                        val result = Intent().putExtra(EXTRA_REMOVE_ID, id)
                        setResult(RESULT_OK, result)
                        finish()
                    }
                })
                list.addView(row)
                // Divider
                list.addView(View(this).apply {
                    setBackgroundColor(0x11FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                })
            }
        }

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
