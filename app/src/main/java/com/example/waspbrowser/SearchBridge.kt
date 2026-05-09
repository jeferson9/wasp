package com.example.waspbrowser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.*
import java.net.URL

class SearchBridge(private val webView: WebView) {

    @JavascriptInterface
    fun fetchSuggestions(query: String) {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val url =
                    "https://duckduckgo.com/ac/?q=" +
                            query.replace(" ", "%20")

                val result = URL(url).readText()

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "window.showNativeSuggestions($result)",
                        null
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}