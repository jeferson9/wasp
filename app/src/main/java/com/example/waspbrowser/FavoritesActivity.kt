package com.example.waspbrowser

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class FavoritesActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var favorites = mutableListOf<Favorite>()
    private val faviconExecutor = Executors.newFixedThreadPool(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        val btnBack  = findViewById<ImageButton>(R.id.btnBack)
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val btnUrl   = findViewById<Button>(R.id.btnUrl)
        val btnClear = findViewById<Button>(R.id.btnClearFavorites)
        listView     = findViewById(R.id.listFavorites)

        urlInput.setText("FAVORITOS")
        urlInput.isEnabled = false
        btnUrl.visibility  = View.GONE
        btnBack.setOnClickListener { finish(); @Suppress("DEPRECATION") overridePendingTransition(R.anim.fade_in, R.anim.fade_out) }

        btnClear.setOnClickListener {
            WaspDialogs.confirm(this, "Limpar favoritos", "Apagar todos os favoritos?", "Apagar") {
                FavoritesManager.clear(this)
                WaspDialogs.toast(this, "Favoritos apagados")
                loadList()
            }
        }

        loadList()
    }

    override fun onDestroy() {
        super.onDestroy()
        faviconExecutor.shutdownNow()
    }

    private fun loadList() {
        favorites = FavoritesManager.getAll(this).toMutableList()

        val adapter = object : BaseAdapter() {
            override fun getCount()               = favorites.size
            override fun getItem(pos: Int): Any   = favorites[pos]
            override fun getItemId(pos: Int)      = pos.toLong()

            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
                val view  = convertView ?: layoutInflater.inflate(R.layout.item_history, parent, false)
                val icon  = view.findViewById<ImageView>(R.id.icon)
                val title = view.findViewById<TextView>(R.id.title)
                val url   = view.findViewById<TextView>(R.id.url)

                val item  = favorites[position]
                title.text = item.title
                url.text   = getDomain(item.url)
                
                icon.setImageResource(R.drawable.globe) // Placeholder
                loadFavicon(item.url, icon)
                return view
            }
        }

        listView.adapter = adapter

        // CLIQUE PARA ABRIR O SITE
        listView.setOnItemClickListener { _, _, position, _ ->
            val url = favorites[position].url
            if (url.isNotBlank()) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("open_url", url)
                }
                startActivity(intent)
                finish()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val fav = favorites[position]
            WaspDialogs.confirm(this, "Remover", "Remover dos favoritos?", "Remover") {
                FavoritesManager.remove(this, fav.url)
                loadList()
            }
            true
        }
    }

    private fun loadFavicon(url: String, imageView: ImageView) {
        faviconExecutor.submit {
            try {
                val domain  = URL(url).host ?: return@submit
                val iconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                val conn    = URL(iconUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                val bitmap  = BitmapFactory.decodeStream(conn.inputStream)
                runOnUiThread { if (bitmap != null) imageView.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }
    }

    private fun getDomain(url: String): String {
        return try { URL(url).host.replace("www.", "") } catch (_: Exception) { url }
    }
}
