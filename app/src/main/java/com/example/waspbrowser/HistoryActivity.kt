package com.example.waspbrowser

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class HistoryActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnClear: Button

    private var allRecents = mutableListOf<Triple<String, String, Long>>() // title, url, timestamp
    private var filtered  = mutableListOf<Triple<String, String, Long>>()

    // Bounded thread pool - max 4 concurrent favicon loads (fixes 50-thread explosion)
    private val faviconExecutor = Executors.newFixedThreadPool(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val btnBack  = findViewById<ImageButton>(R.id.btnBack)
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val btnUrl   = findViewById<Button>(R.id.btnUrl)
        listView     = findViewById(R.id.listHistory)
        btnClear     = findViewById(R.id.btnClearHistory)

        urlInput.setText("Histórico")
        urlInput.isEnabled = false
        btnUrl.visibility  = View.GONE
        btnBack.setOnClickListener { finish(); @Suppress("DEPRECATION") overridePendingTransition(R.anim.fade_in, R.anim.fade_out) }

        loadList()
        btnClear.setOnClickListener { confirmClearHistory() }
    }

    override fun onDestroy() {
        super.onDestroy()
        faviconExecutor.shutdownNow()
    }

    private fun loadList() {
        allRecents = loadRecents().toMutableList()
        filtered   = allRecents.toMutableList()
        renderList()
    }

    private fun renderList() {
        val adapter = object : BaseAdapter() {
            override fun getCount()                    = filtered.size
            override fun getItem(position: Int)        = filtered[position]
            override fun getItemId(position: Int)      = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup?): View {
                val view  = convertView ?: layoutInflater.inflate(R.layout.item_history, parent, false)
                val icon  = view.findViewById<ImageView>(R.id.icon)
                val title = view.findViewById<TextView>(R.id.title)
                val url   = view.findViewById<TextView>(R.id.url)

                val item = filtered[position]
                title.text = item.first
                url.text   = formatEntry(item.second, item.third)
                loadFavicon(item.second, icon)
                return view
            }
        }

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("open_url", filtered[position].second)
            startActivity(intent)
            finish()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteItem(position)
            true
        }
    }

    private fun formatEntry(url: String, timestamp: Long): String {
        val domain = getDomain(url)
        if (timestamp <= 0) return domain
        val now  = System.currentTimeMillis()
        val diff = now - timestamp
        val age  = when {
            diff < 60_000L             -> "agora mesmo"
            diff < 3_600_000L          -> "${diff / 60_000}min atrás"
            diff < 86_400_000L         -> "${diff / 3_600_000}h atrás"
            diff < 86_400_000L * 7     -> "${diff / 86_400_000}d atrás"
            else                       -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestamp))
        }
        return "$domain  ·  $age"
    }

    private fun loadRecents(): List<Triple<String, String, Long>> {
        val prefs = getSharedPreferences("wasp_recents", MODE_PRIVATE)
        val json  = prefs.getString("recents_json", "[]") ?: "[]"
        val list  = mutableListOf<Triple<String, String, Long>>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj  = array.getJSONObject(i)
                val t    = obj.optString("title", "")
                val u    = obj.optString("url", "")
                val ts   = obj.optLong("time", 0L)
                if (u.isNotBlank()) list.add(Triple(t.ifBlank { getDomain(u) }, u, ts))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun confirmClearHistory() {
        WaspDialogs.confirm(this, "Limpar histórico", "Apagar todo o histórico?", "Apagar") {
            getSharedPreferences("wasp_recents", MODE_PRIVATE)
                .edit().putString("recents_json", "[]").apply()
            WaspDialogs.toast(this, "Histórico apagado")
            loadList()
        }
    }

    private fun confirmDeleteItem(position: Int) {
        WaspDialogs.confirm(this, "Remover", "Remover este item do histórico?", "Remover") {
            allRecents.remove(filtered[position])
            filtered.removeAt(position)
            saveRecents(allRecents)
            renderList()
        }
    }

    private fun saveRecents(list: List<Triple<String, String, Long>>) {
        val array = JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("title", item.first)
            obj.put("url",   item.second)
            obj.put("time",  item.third)
            array.put(obj)
        }
        getSharedPreferences("wasp_recents", MODE_PRIVATE)
            .edit().putString("recents_json", array.toString()).apply()
    }

    private fun loadFavicon(url: String, imageView: ImageView) {
        // Use bounded executor (fixes: 50 threads launched simultaneously)
        faviconExecutor.submit {
            try {
                val domain  = URL(url).host ?: return@submit
                val iconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                val conn    = URL(iconUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout    = 3000
                val bitmap  = BitmapFactory.decodeStream(conn.inputStream)
                conn.disconnect()
                runOnUiThread { if (bitmap != null) imageView.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }
    }

    private fun getDomain(url: String): String {
        return try { URL(url).host.replace("www.", "") } catch (_: Exception) { url }
    }
}
