package com.example.waspbrowser

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class BeeLabActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BeeLabActivity"
        private const val BEE_LAB_URL = "http://192.168.0.10:5173/"
    }

    private lateinit var webView: WebView
    private lateinit var loading: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bee_lab)

        webView = findViewById(R.id.beeLabWebView)
        loading = findViewById(R.id.beeLabLoading)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "onPageFinished: $url")
                loading.visibility = View.GONE
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e(TAG, "onReceivedError: $description url=$failingUrl")
                loading.visibility = View.GONE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(TAG, "console: ${consoleMessage.message()} line=${consoleMessage.lineNumber()}")
                return true
            }
        }

        webView.loadUrl(BEE_LAB_URL)
    }
}