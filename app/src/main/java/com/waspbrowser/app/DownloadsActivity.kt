package com.waspbrowser.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.URL

class DownloadsActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var downloads = mutableListOf<List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val urlInput = findViewById<EditText>(R.id.urlInput)
        val btnUrl = findViewById<Button>(R.id.btnUrl)
        val btnClear = findViewById<Button>(R.id.btnClearDownloads)
        listView = findViewById(R.id.listDownloads)

        // ===== TOPO =====

        urlInput.setText("DOWNLOADS")
        urlInput.isEnabled = false

        btnUrl.visibility = View.GONE

        btnBack.setOnClickListener { finish(); @Suppress("DEPRECATION") overridePendingTransition(R.anim.fade_in, R.anim.fade_out) }
        btnClear.setOnClickListener {

            showWaspConfirmDialog(
                title = "Limpar downloads",
                message = "Deseja apagar todos os downloads da lista?",
                confirmText = "Apagar"
            ) {
                val prefs = getSharedPreferences("wasp_downloads", MODE_PRIVATE)
                prefs.edit()
                    .remove("downloads")         // remove old StringSet
                    .putString("downloads_json", "[]")  // clear new JSON
                    .apply()

                Toast.makeText(
                    this,
                    "Downloads apagados",
                    Toast.LENGTH_SHORT
                ).show()

                loadList()
            }
        }
        loadList()
    }
    private fun showWaspConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wasp_confirm, null)

        val txtTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtMessage = dialogView.findViewById<TextView>(R.id.txtDialogMessage)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        txtTitle.text = title
        txtMessage.text = message
        btnConfirm.text = confirmText

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 🔥 garante que não fica roxo
        btnCancel.background = getDrawable(R.drawable.bg_wasp_button_cancel)
        btnConfirm.background = getDrawable(R.drawable.bg_wasp_button_danger)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()
    }
    // =========================
    // CARREGAR DOWNLOADS
    // =========================

    private fun loadList() {

        val prefs = getSharedPreferences("wasp_downloads", MODE_PRIVATE)

        // Fixed: read from JSON array (reliable ordering, no UnsupportedOperationException)
        val json = prefs.getString("downloads_json", "[]") ?: "[]"
        downloads = try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                listOf(
                    obj.optString("name", "arquivo"),
                    obj.optString("url", ""),
                    obj.optString("time", "0")
                )
            }.toMutableList()
        } catch (_: Exception) {
            // Fallback: try old StringSet format migration
            val oldSet = prefs.getStringSet("downloads", emptySet()) ?: emptySet()
            if (oldSet.isNotEmpty()) {
                val migrated = oldSet.map { it.split("|") }.toMutableList()
                // Migrate to JSON
                val arr = org.json.JSONArray()
                migrated.forEach { parts ->
                    val obj = org.json.JSONObject()
                    obj.put("name", parts.getOrNull(0) ?: "")
                    obj.put("url", parts.getOrNull(1) ?: "")
                    obj.put("time", parts.getOrNull(2) ?: "0")
                    arr.put(obj)
                }
                prefs.edit().putString("downloads_json", arr.toString()).remove("downloads").apply()
                migrated
            } else mutableListOf()
        }

        val adapter = object : BaseAdapter() {

            override fun getCount() = downloads.size

            override fun getItem(position: Int) = downloads[position]

            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

                val view = convertView
                    ?: layoutInflater.inflate(R.layout.item_history, parent, false)

                val icon = view.findViewById<ImageView>(R.id.icon)
                val title = view.findViewById<TextView>(R.id.title)
                val urlText = view.findViewById<TextView>(R.id.url)

                val item = downloads[position]

                val name = item.getOrNull(0) ?: "arquivo"
                val url = item.getOrNull(1) ?: ""

                title.text = name
                urlText.text = getDomain(url)

                setFileIcon(name, icon)

                return view
            }
        }

        listView.adapter = adapter
        listView.setOnItemLongClickListener { _, _, position, _ ->

            val item = downloads[position]
            val name = item.getOrNull(0) ?: "arquivo"

            showWaspConfirmDialog(
                title = "Remover download",
                message = "Remover $name da lista?",
                confirmText = "Remover"
            ) {
                val prefs = getSharedPreferences("wasp_downloads", MODE_PRIVATE)
                val json = prefs.getString("downloads_json", "[]") ?: "[]"
                val array = try { org.json.JSONArray(json) } catch (_: Exception) { org.json.JSONArray() }
                val newArray = org.json.JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("name") != name) newArray.put(obj)
                }
                prefs.edit().putString("downloads_json", newArray.toString()).apply()

                loadList()
            }




            true
        }

        // Open downloads folder on item click
        listView.setOnItemClickListener { _, _, _, _ ->
            try {
                // Try Files app first, fallback to generic intent
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"),
                        "vnd.android.document/directory"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: open generic Downloads via ACTION_OPEN_DOCUMENT
                try {
                    val dlIntent = Intent("android.intent.action.VIEW_DOWNLOADS")
                    dlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(dlIntent)
                } catch (e2: Exception) {
                    WaspToast.show(this, "Abra o gerenciador de arquivos para ver downloads", WaspToast.NORMAL, true)
                }
            }
        }
    }

    // =========================
    // ÍCONE POR TIPO DE ARQUIVO
    // =========================

    private fun setFileIcon(name: String, icon: ImageView) {

        val ext = name.substringAfterLast(".", "").lowercase()

        when (ext) {

            "pdf" -> icon.setImageResource(R.drawable.ic_file_pdf)

            "png", "jpg", "jpeg", "webp" ->
                icon.setImageResource(R.drawable.ic_file_image)

            "zip", "rar", "7z" ->
                icon.setImageResource(R.drawable.ic_file_zip)

            "apk" ->
                icon.setImageResource(R.drawable.ic_file_apk)

            "mp3", "wav" ->
                icon.setImageResource(R.drawable.ic_file_audio)

            "mp4", "mkv" ->
                icon.setImageResource(R.drawable.ic_file_video)

            else ->
                icon.setImageResource(R.drawable.ic_file_generic)
        }
    }

    // =========================
    // PEGAR DOMÍNIO DO SITE
    // =========================

    private fun getDomain(url: String): String {

        return try {

            val host = URL(url).host
            host.replace("www.", "")

        } catch (e: Exception) {
            url
        }
    }
}