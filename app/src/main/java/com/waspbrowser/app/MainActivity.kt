package com.waspbrowser.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse

class MainActivity : AppCompatActivity() {

    // =========================================================
    // RESET SESSION - only resets gecko, no visibility changes
    // =========================================================

    private fun resetToMainSession() {
        val currentPopup = popupSession
        popupSession = null

        try {
            if (::geckoSession.isInitialized) {
                val activeTab = tabs.getOrNull(activeTabIndex)
                geckoView.setSession(activeTab?.session ?: geckoSession)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try { currentPopup?.close() } catch (_: Exception) {}
    }

    companion object {
        private var geckoRuntime: GeckoRuntime? = null
    }

    // =========================================================
    // TABS
    // =========================================================

    data class WaspTab(
        val session: GeckoSession,
        var url: String = "",
        var title: String = "",
        val id: Int = System.currentTimeMillis().toInt()
    )

    private val tabs = mutableListOf<WaspTab>()
    private var activeTabIndex = 0

    private fun getCurrentTab(): WaspTab? = tabs.getOrNull(activeTabIndex)

    private fun newTab(url: String = "") {
        val session = GeckoSession()
        attachGeckoDelegates(session)
        session.open(geckoRuntime!!)
        val tab = WaspTab(session = session, url = url)
        tabs.add(tab)
        activeTabIndex = tabs.size - 1
        currentUrl = url
        currentTitle = ""
        sslErrorActive = false
        runOnUiThread {
            geckoView.setSession(session)
            urlInput.visibility = View.GONE
            urlDisplay.visibility = View.VISIBLE
            urlTitle.text = ""
            urlDomain.text = if (url.isNotBlank()) getCleanDomain(url) else ""
            updateSecurityIcon(url)
            updateTabIndicator()
        }
        if (url.isNotBlank()) session.loadUri(url)
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        activeTabIndex = index
        val tab = tabs[index]
        geckoView.setSession(tab.session)
        currentUrl = tab.url
        currentTitle = tab.title
        sslErrorActive = false
        runOnUiThread {
            urlInput.visibility = View.GONE
            urlDisplay.visibility = View.VISIBLE
            urlTitle.text = tab.title.ifBlank { "" }
            urlDomain.text = if (tab.url.isNotBlank()) getCleanDomain(tab.url) else ""
            updateSecurityIcon(tab.url)
            updateTabIndicator()
            try { tab.session.setActive(true) } catch (_: Exception) {}
        }
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) { goHome(); return }
        tabs[index].session.close()
        tabs.removeAt(index)
        val newIndex = if (activeTabIndex >= tabs.size) tabs.size - 1 else activeTabIndex
        activeTabIndex = newIndex
        switchToTab(activeTabIndex)
        updateTabIndicator()
    }

    private fun updateTabIndicator() {
        runOnUiThread {
            val count = tabs.size
            val pill = topBar.findViewById<android.widget.LinearLayout>(R.id.btnTabsPill)
            val badge = topBar.findViewById<android.widget.TextView>(R.id.tabsCount)
            pill?.visibility = if (count > 1) View.VISIBLE else View.GONE
            badge?.text = count.toString()
        }
    }

    private fun showTabSwitcher() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#111420"))
            setPadding(0, 0, 0, 32.dp())
        }

        // Handle
        val handleLp = android.widget.LinearLayout.LayoutParams(36.dp(), 4.dp())
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        handleLp.topMargin = 12.dp()
        handleLp.bottomMargin = 8.dp()
        root.addView(android.view.View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 4.dp().toFloat()
                setColor(android.graphics.Color.parseColor("#252A3D"))
            }
        }, handleLp)

        // Header
        val titleRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        }
        titleRow.addView(android.widget.TextView(this).apply {
            text = "${tabs.size} ${if (tabs.size == 1) "aba" else "abas"}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#C8D0E0"))
        }, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        titleRow.addView(android.widget.TextView(this).apply {
            text = "+ Nova aba"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#FFD400"))
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8.dp().toFloat()
                setColor(android.graphics.Color.parseColor("#1A120A"))
                setStroke(1, android.graphics.Color.parseColor("#3D2F00"))
            }
            setOnClickListener {
                dialog.dismiss()
                newTab("https://www.google.com")
                crossfadeToGecko()
                updateTopBarForHome(false)
            }
        })
        root.addView(titleRow)
        root.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#1A1E2E"))
        }, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1))

        // Tab rows
        tabs.forEachIndexed { index, tab ->
            val isActive = index == activeTabIndex
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 10.dp().toFloat()
                    setColor(if (isActive) android.graphics.Color.parseColor("#1A1E2E") else android.graphics.Color.TRANSPARENT)
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.leftMargin = 8.dp(); lp.rightMargin = 8.dp(); lp.topMargin = 2.dp()
                layoutParams = lp
                isClickable = true; isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    switchToTab(index)
                    crossfadeToGecko()
                    updateTopBarForHome(false)
                }
            }

            row.addView(android.view.View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (isActive) android.graphics.Color.parseColor("#FFD400") else android.graphics.Color.TRANSPARENT)
                }
            }, android.widget.LinearLayout.LayoutParams(6.dp(), 6.dp()).also { it.rightMargin = 10.dp() })

            val info = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
            info.addView(android.widget.TextView(this).apply {
                text = tab.title.ifBlank { if (tab.url.isNotBlank()) getCleanDomain(tab.url) else "Nova aba" }
                textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#C8D0E0"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            info.addView(android.widget.TextView(this).apply {
                text = if (tab.url.isNotBlank()) getCleanDomain(tab.url) else "—"
                textSize = 11f; setTextColor(android.graphics.Color.parseColor("#3D4560"))
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, 2.dp(), 0, 0)
            })
            row.addView(info, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(android.widget.TextView(this).apply {
                text = "✕"; textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#3D4560"))
                gravity = android.view.Gravity.CENTER
                setPadding(10.dp(), 6.dp(), 4.dp(), 6.dp())
                setOnClickListener { dialog.dismiss(); closeTab(index) }
            })
            root.addView(row)
        }

        dialog.setContentView(android.widget.ScrollView(this).apply { addView(root) })
        dialog.show()
    }

    // =========================================================
    // VIEWS
    // =========================================================

    private lateinit var topBar: View
    private lateinit var webAppView: WebView
    private lateinit var geckoView: GeckoView
    private var persistentBeeView: android.webkit.WebView? = null
    private var beePanelExpanded = false
    private lateinit var btnBack: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var urlInput: EditText
    private lateinit var pageProgress: ProgressBar
    private lateinit var btnUrl: Button
    private lateinit var iconSecurity: ImageView
    private lateinit var iconFavicon: ImageView
    private lateinit var urlDisplay: View
    private lateinit var urlTitle: TextView
    private lateinit var urlDomain: TextView

    private lateinit var geckoSession: GeckoSession
    private var popupSession: GeckoSession? = null
    private var canGoBackGecko = false
    private var canGoForwardGecko = false

    private var currentUrl: String = ""
    private var currentTitle: String = ""
    private var sslErrorActive = false

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        window.statusBarColor = android.graphics.Color.parseColor("#111420")
        window.navigationBarColor = android.graphics.Color.parseColor("#0b0f1a")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    window.decorView.systemUiVisibility
                            and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                    )
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        bindViews()
        setupWebAppView()
        setupGecko()
        setupTopBar()
        setupUrlInput()
        setupPersistentBee()
        webAppView.loadUrl("file:///android_asset/index.html")
        webAppView.post { webAppView.requestFocus(View.FOCUS_DOWN) }

        // Start with toolbar hidden - home has no toolbar
        topBar.visibility = View.GONE

        // Reseta o estado de mineração ao iniciar o app — a BeeActivity vai notificar
        // via setMiningStatus() se realmente estiver minerando quando aberta.
        // Isso evita que um crash anterior deixe o dot verde para sempre.
        getSharedPreferences("bee_mining", MODE_PRIVATE)
            .edit().putBoolean("mining_active", false).apply()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    fun onMiningStatusChanged(active: Boolean, wallet: String) {
        // Salva estado para persistir entre Activities
        getSharedPreferences("bee_mining", MODE_PRIVATE)
            .edit().putBoolean("mining_active", active).apply()
    }


    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        webAppView.onResume()
        webAppView.resumeTimers()
        if (::webAppView.isInitialized && webAppView.visibility == View.VISIBLE &&
            ::geckoView.isInitialized && geckoView.visibility != View.VISIBLE) {
            webAppView.evaluateJavascript("setBottomTab('main')", null)
        }
        // Garante que a persistentBeeView está no container correto
        val beeContainer = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container)
        if (persistentBeeView != null && beeContainer != null && persistentBeeView?.parent !== beeContainer) {
            (persistentBeeView?.parent as? android.view.ViewGroup)?.removeView(persistentBeeView)
            beeContainer.addView(persistentBeeView, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        persistentBeeView?.post {
            persistentBeeView?.resumeTimers()
            // Não notifica "voltou ao foco" se está saindo do PiP — mineração não parou
            if (!isInPictureInPictureMode) {
                persistentBeeView?.evaluateJavascript(
                    "if(window.onAppResume) window.onAppResume(); else if(window.onWalletReturn) window.onWalletReturn();", null)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Não pausa timers em modo PiP — mineração deve continuar
        if (!isInPictureInPictureMode) {
            webAppView.onPause()
            webAppView.pauseTimers()
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPossible()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            beePanelExpanded -> collapseBeePanel()
            geckoView.visibility == View.VISIBLE -> when {
                popupSession != null -> resetToMainSession()
                canGoBackGecko -> getActiveSession().goBack()
                else -> enterPipIfPossible()
            }
            else -> enterPipIfPossible()
        }
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Só entra em PiP se a mineração estiver ativa
            val miningActive = getSharedPreferences("bee_mining", android.content.Context.MODE_PRIVATE)
                .getBoolean("pip_allowed", false)
            if (!miningActive) return

            try {
                val metrics = resources.displayMetrics
                val pipWidth = (metrics.widthPixels * 0.45f).toInt()
                val pipHeight = (pipWidth * 7f / 16f).toInt()
                val pipLeft = metrics.widthPixels - pipWidth - 16
                val pipTop = 64

                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 7))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setSourceRectHint(android.graphics.Rect(
                        pipLeft, pipTop, pipLeft + pipWidth, pipTop + pipHeight
                    ))
                }

                enterPictureInPictureMode(paramsBuilder.build())
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "PiP error: ${e.message}")
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            // No PiP mostra só o topo colapsado do painel — já é compacto e tech
            webAppView.visibility = android.view.View.GONE
            geckoView.visibility = android.view.View.GONE
            topBar.visibility = android.view.View.GONE
            collapseBeePanel()
            // Expande o container para preencher o PiP inteiro
            val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container)
            container?.let {
                val params = it.layoutParams
                params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                it.layoutParams = params
                it.visibility = android.view.View.VISIBLE
            }
            persistentBeeView?.post {
                persistentBeeView?.evaluateJavascript("""
                    (function(){
                        var el = document.querySelector('.central-wp-btn');
                        if(el) el.style.visibility='hidden';
                        document.body.style.overflow='hidden';
                    })()
                """.trimIndent(), null)
            }
        } else {
            // Voltou do PiP — restaura UI e mantém painel aberto
            webAppView.visibility = android.view.View.VISIBLE
            topBar.visibility = android.view.View.GONE
            // Restaura altura do container
            val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container)
            container?.let {
                val params = it.layoutParams
                val dp56 = (56 * resources.displayMetrics.density).toInt()
                params.height = dp56
                it.layoutParams = params
            }
            openBeePanel()
            persistentBeeView?.post {
                persistentBeeView?.evaluateJavascript("""
                    (function(){
                        var el = document.querySelector('.central-wp-btn');
                        if(el) el.style.visibility='';
                        document.body.style.overflow='';
                        if(window.exitPipMode) window.exitPipMode();
                    })()
                """.trimIndent(), null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { popupSession?.close() } catch (_: Exception) {}
        try { geckoSession.close() } catch (_: Exception) {}
        webAppView.stopLoading()
        webAppView.destroy()
    }

    // =========================================================
    // DIALOGS / TOAST
    // =========================================================

    private var translateBadgeView: TextView? = null

    private fun injectTranslatorJS(js: String) {
        try {
            val loader = GeckoSession.Loader().uri("javascript:(function(){$js})();")
            getActiveSession().load(loader)
        } catch (e: Exception) {
            showWaspToast("Erro ao iniciar tradução")
        }
    }

    private fun updateTranslateBadge(badgeView: TextView?) {
        translateBadgeView = null
        if (badgeView == null) {
            // Restaura domínio original
            urlDomain.text = getCleanDomain(currentUrl)
            urlDomain.setTextColor(android.graphics.Color.parseColor("#6B7A99"))
            urlDomain.background = null
            urlDomain.setPadding(0, 0, 0, 0)
        } else {
            // Reutiliza o urlDomain como badge — sem adicionar views novas
            translateBadgeView = badgeView
            urlDomain.text = badgeView.text
            urlDomain.setTextColor(android.graphics.Color.parseColor("#60A5FA"))
            // Propaga mudanças de texto do badge para o urlDomain
            badgeView.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    urlDomain.text = s
                    if (s?.startsWith("🌐") == true) {
                        urlDomain.setTextColor(android.graphics.Color.parseColor("#22C55E"))
                    }
                }
            })
        }
    }

    private fun showWaspToast(message: String, type: Int = WaspToast.NORMAL) {
        WaspToast.show(this, message, type)
    }

    private fun showWaspConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onCancel: (() -> Unit)? = null,
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
            .setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.background = getDrawable(R.drawable.bg_wasp_button_danger)
        btnConfirm.background = getDrawable(R.drawable.bg_wasp_button_cancel)
        btnCancel.backgroundTintList = null
        btnConfirm.backgroundTintList = null

        btnCancel.setOnClickListener { dialog.dismiss(); onCancel?.invoke() }
        btnConfirm.setOnClickListener { dialog.dismiss(); onConfirm() }
        dialog.show()
    }

    // =========================================================
    // INTENT HANDLING
    // =========================================================

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra("navigate_to")
        if (!navigateTo.isNullOrBlank()) {
            intent.removeExtra("navigate_to")
            runOnUiThread {
                when (navigateTo) {
                    "home"   -> webAppView.evaluateJavascript("resetHome && resetHome()", null)
                    "market" -> webAppView.evaluateJavascript("openMarketTab && openMarketTab()", null)
                    "hive"   -> webAppView.evaluateJavascript("""
                        (function(){
                            if(typeof resetHome==='function') resetHome();
                            setTimeout(function(){ if(typeof openHiveTab==='function') openHiveTab(); },80);
                        })();
                    """.trimIndent(), null)
                }
            }
            return
        }

        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> intent.getStringExtra("open_url")
        } ?: return

        if (url.isBlank()) return
        intent.data = null
        intent.removeExtra("open_url")

        runOnUiThread { openSite(url) }
    }

    private fun getActiveSession(): GeckoSession {
        return popupSession ?: tabs.getOrNull(activeTabIndex)?.session ?: geckoSession
    }

    private fun finishPopupLoginIfNeeded(session: GeckoSession, url: String?) {
        if (session != popupSession) return
        val safeUrl = url?.trim()?.lowercase().orEmpty()
        val shouldClose = safeUrl.isBlank() || safeUrl == "about:blank" ||
                safeUrl.contains("login/success") || safeUrl.contains("login/callback") ||
                safeUrl.contains("auth/success") || safeUrl.contains("auth/callback") ||
                safeUrl.contains("signin/callback") || safeUrl.contains("close") ||
                (safeUrl.contains("accounts.google.com") && safeUrl.contains("postmessage"))
        if (!shouldClose) return
        runOnUiThread {
            val currentPopup = popupSession
            popupSession = null
            try { geckoView.setSession(geckoSession) } catch (e: Exception) { e.printStackTrace() }
            try { currentPopup?.close() } catch (_: Exception) {}
            urlInput.visibility = View.GONE
            urlDisplay.visibility = View.VISIBLE
            pageProgress.visibility = View.GONE
            updateTopBarForHome(false)
            crossfadeToGecko()
            try { geckoSession.reload() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // =========================================================
    // SETUP
    // =========================================================

    private fun bindViews() {
        topBar      = findViewById(R.id.topBar)
        webAppView  = findViewById(R.id.webAppView)
        geckoView   = findViewById(R.id.geckoView)

        webAppView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        webAppView.addJavascriptInterface(
            BeeBridge(this) { url -> runOnUiThread { openSite(url) } },
            "Android"
        )

        btnBack      = topBar.findViewById(R.id.btnBack)
        btnHome      = topBar.findViewById(R.id.btnHome)
        btnMenu      = topBar.findViewById(R.id.btnMenu)
        urlInput     = topBar.findViewById(R.id.urlInput)
        urlDisplay   = topBar.findViewById(R.id.urlDisplay)
        urlTitle     = topBar.findViewById(R.id.urlTitle)
        urlDomain    = topBar.findViewById(R.id.urlDomain)
        btnUrl       = topBar.findViewById(R.id.btnUrl)
        pageProgress = findViewById(R.id.pageProgress)
        iconSecurity = topBar.findViewById(R.id.iconSecurity)
        iconFavicon  = topBar.findViewById(R.id.iconFavicon)
    }

    private fun setupWebAppView() {
        webAppView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webAppView, true)
        }
        webAppView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            isLongClickable = true
            requestFocus(View.FOCUS_DOWN)
            setOnTouchListener { v, _ -> if (!v.hasFocus()) v.requestFocus(View.FOCUS_DOWN); false }
        }
        webAppView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject system bar heights so CSS var(--safe-top/--safe-bottom) work correctly
                val resources = this@MainActivity.resources
                val statusBarResId = resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusBarPx = if (statusBarResId > 0) resources.getDimensionPixelSize(statusBarResId) else 0
                // Convert px to dp for the WebView (WebView uses CSS px = dp on mdpi)
                val density = resources.displayMetrics.density
                val statusBarDp = (statusBarPx / density).toInt()

                view?.evaluateJavascript(
                    "if(window.setStatusBarHeight) window.setStatusBarHeight($statusBarDp);", null
                )
            }
        }
        webAppView.webChromeClient = WebChromeClient()
    }

    private fun setupGecko() {
        if (geckoRuntime == null) {
            geckoRuntime = GeckoRuntime.create(this)
        }
        geckoSession = GeckoSession()
        attachGeckoDelegates(geckoSession)
        geckoSession.open(geckoRuntime!!)
        geckoView.setSession(geckoSession)
        tabs.add(WaspTab(session = geckoSession))
        activeTabIndex = 0
    }

    private fun setupTopBar() {
        btnBack.setOnClickListener {
            if (geckoView.visibility == View.VISIBLE) {
                when {
                    popupSession != null -> {
                        try { popupSession?.close() } catch (_: Exception) {}
                        popupSession = null
                        geckoView.setSession(geckoSession)
                    }
                    canGoBackGecko -> getActiveSession().goBack()
                    else -> goHome()
                }
            } else {
                goHome()
            }
        }

        btnHome.setOnClickListener { goHome() }

        // btnBeePill removido — painel bee acessível pelo rodapé

        topBar.findViewById<android.view.View>(R.id.btnTabsPill)?.setOnClickListener {
            showTabSwitcher()
        }

        btnHome.setOnLongClickListener {
            newTab("https://www.google.com")
            crossfadeToGecko()
            updateTopBarForHome(false)
            showWaspToast("Nova aba aberta")
            true
        }

        urlDisplay.setOnClickListener {
            if (currentUrl.isBlank()) return@setOnClickListener
            urlDisplay.visibility = View.GONE
            urlInput.visibility = View.VISIBLE
            urlInput.setText(currentUrl)
            urlInput.requestFocus()
            urlInput.selectAll()
        }

        btnMenu.setOnClickListener {
            val isFav = FavoritesManager.getAll(this).any { it.url == currentUrl }
            WaspMenuSheet.show(
                context    = this,
                currentUrl = currentUrl,
                pageTitle  = urlTitle.text?.toString() ?: "",
                isFavorite = isFav
            ) { action ->
                when (action) {
                    "favorite"   -> {
                        val t = if (urlTitle.text.isNullOrBlank()) currentUrl else urlTitle.text.toString()
                        FavoritesManager.add(this, t, currentUrl)
                        showWaspToast("Adicionado aos favoritos")
                    }
                    "unfavorite" -> { FavoritesManager.remove(this, currentUrl); showWaspToast("Removido dos favoritos") }
                    "reload"     -> getActiveSession().reload()
                    "forward"    -> if (canGoForwardGecko) getActiveSession().goForward()
                    "share"      -> {
                        if (currentUrl.isBlank()) return@show
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) },
                            "Compartilhar"
                        ))
                    }
                    "newtab"     -> { newTab(currentUrl); showWaspToast("Aba duplicada") }
                    "hive"       -> goHome()
                    "favorites"  -> startActivityFade(Intent(this, FavoritesActivity::class.java))
                    "history"    -> startActivityFade(Intent(this, HistoryActivity::class.java))
                    "downloads"  -> startActivityFade(Intent(this, DownloadsActivity::class.java))
                    "web3"       -> startActivityFade(Intent(this, Web3Activity::class.java))
                    "bee"        -> openBeePanel()
                    "translate"  -> {
                        if (currentUrl.isBlank() || currentUrl.startsWith("about:")) {
                            showWaspToast("Nenhuma página para traduzir")
                        } else {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                WaspTranslator.show(
                                    context        = this,
                                    currentUrl     = currentUrl,
                                    onBadgeRequest = { badgeView -> runOnUiThread { updateTranslateBadge(badgeView) } },
                                    onInjectJS     = { js -> runOnUiThread { injectTranslatorJS(js) } },
                                    onReload       = { runOnUiThread { getActiveSession().reload() } }
                                )
                            }, 250)
                        }
                    }
                    "settings"   -> {
                        // Abre o settingsPanel da home (conteudo rico, visual consistente)
                        geckoView.visibility = android.view.View.GONE
                        topBar.visibility = android.view.View.GONE
                        webAppView.visibility = android.view.View.VISIBLE
                        webAppView.evaluateJavascript("openSettings()", null)
                    }
                    "about" -> startActivityFade(Intent(this, AboutActivity::class.java))
                }
            }
        }
    }

    private fun setupUrlInput() {
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                navigate(urlInput.text.toString())
                true
            } else false
        }
    }

    // =========================================================
    // GECKO DELEGATES
    // =========================================================

    private fun attachGeckoDelegates(session: GeckoSession) {

        session.contentDelegate = object : GeckoSession.ContentDelegate {

            override fun onTitleChange(session: GeckoSession, title: String?) {
                runOnUiThread {
                    if (!title.isNullOrBlank()) {
                        currentTitle = title
                        tabs.getOrNull(activeTabIndex)?.title = title
                        urlTitle.text = title
                        if (currentUrl.isNotBlank()) urlDomain.text = getCleanDomain(currentUrl)
                    }
                }
            }

            override fun onContextMenu(
                session: GeckoSession, screenX: Int, screenY: Int,
                element: GeckoSession.ContentDelegate.ContextElement
            ) {
                val target = element.srcUri?.takeIf { it.startsWith("http") }
                    ?: element.linkUri?.takeIf { it.startsWith("http") } ?: return
                runOnUiThread {
                    showWaspConfirmDialog("Imagem", "Baixar esta imagem?", "Baixar") {
                        downloadImage(target)
                    }
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val url = response.uri ?: return
                runOnUiThread {
                    startDownload(url, null,
                        response.headers["Content-Disposition"],
                        response.headers["Content-Type"]?.substringBefore(";")?.trim()
                    )
                }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {

            override fun onPageStart(session: GeckoSession, url: String) {
                currentUrl = url
                tabs.getOrNull(activeTabIndex)?.url = url
                sslErrorActive = false
                currentTitle = ""
                runOnUiThread {
                    iconFavicon.setImageResource(R.drawable.globe)
                    urlInput.visibility = View.GONE
                    urlDisplay.visibility = View.VISIBLE
                    urlTitle.text = ""
                    urlDomain.text = getCleanDomain(url)
                    pageProgress.visibility = View.VISIBLE
                    pageProgress.progress = 0
                    updateSecurityIcon(url)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                runOnUiThread {
                    if (geckoView.visibility != View.VISIBLE) return@runOnUiThread
                    pageProgress.progress = progress
                    if (progress >= 100) pageProgress.visibility = View.GONE
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                runOnUiThread { pageProgress.visibility = View.GONE }
                if (success && currentUrl.isNotBlank()) {
                    try {
                        val domain = android.net.Uri.parse(currentUrl).host ?: ""
                        val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                        Thread {
                            try {
                                val conn = java.net.URL(faviconUrl).openConnection() as java.net.HttpURLConnection
                                conn.connectTimeout = 3000; conn.readTimeout = 3000
                                val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                                conn.disconnect()
                                runOnUiThread {
                                    if (bmp != null) iconFavicon.setImageBitmap(bmp)
                                    else iconFavicon.setImageResource(R.drawable.globe)
                                }
                            } catch (_: Exception) {
                                runOnUiThread { iconFavicon.setImageResource(R.drawable.globe) }
                            }
                        }.start()
                    } catch (_: Exception) {}
                    runOnUiThread {
                        webAppView.evaluateJavascript("window.addRecentFromSite('${escapeJs(currentUrl)}')", null)
                    }
                    saveRecent(currentTitle.ifBlank { getCleanDomain(currentUrl) }, currentUrl)
                }
            }

            override fun onSecurityChange(session: GeckoSession, securityInfo: GeckoSession.ProgressDelegate.SecurityInformation) {
                runOnUiThread { updateSecurityIcon(currentUrl) }
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {

            override fun onLocationChange(
                session: GeckoSession, url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val safeUrl = url ?: return
                currentUrl = safeUrl
                runOnUiThread {
                    urlDomain.text = getCleanDomain(safeUrl)
                    updateSecurityIcon(safeUrl)
                    WaspTranslator.onNavigate(safeUrl) { badgeView -> updateTranslateBadge(badgeView) }
                }
                finishPopupLoginIfNeeded(session, safeUrl)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (session == getActiveSession()) {
                    canGoBackGecko = canGoBack
                    runOnUiThread { btnBack.alpha = if (canGoBack || popupSession != null) 1f else 0.55f }
                }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (session == getActiveSession()) canGoForwardGecko = canGoForward
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (uri.isNotBlank()) runOnUiThread { openSite(uri) }
                return null
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val url = request.uri ?: return null
                val lowerUrl = url.lowercase()

                val nativeScheme = !url.startsWith("http://") && !url.startsWith("https://") &&
                        !url.startsWith("about:") && !url.startsWith("data:") &&
                        !url.startsWith("blob:") && !url.startsWith("javascript:")

                if (nativeScheme) {
                    runOnUiThread { openExternalApp(url) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                val isAppLink = lowerUrl.contains("play.google.com/store") ||
                        lowerUrl.endsWith(".apk") ||
                        lowerUrl.startsWith("https://t.me/") ||
                        lowerUrl.startsWith("https://telegram.me/")

                if (isAppLink) {
                    runOnUiThread { openExternalApp(url) }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                return null
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                sslErrorActive = true
                val failedUrl = uri ?: "site desconhecido"
                runOnUiThread { updateSecurityIcon(failedUrl) }
                val html = """
                    <html><body style="background:#111;color:white;font-family:sans-serif;text-align:center;padding:60px;">
                    <h2 style="color:#ff4444;">Erro ao carregar</h2>
                    <p>O Wasp nao conseguiu abrir esta pagina.</p>
                    <div style="margin-top:25px;padding:12px;background:#222;border-radius:8px;">${escapeHtml(failedUrl)}</div>
                    </body></html>
                """.trimIndent()
                return GeckoResult.fromValue("data:text/html;charset=utf-8," + Uri.encode(html))
            }
        }

        session.permissionDelegate = object : GeckoSession.PermissionDelegate {

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int> {
                val sensitive = perm.permission == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION
                if (!sensitive) return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

                val result = GeckoResult<Int>()
                runOnUiThread {
                    showWaspConfirmDialog(
                        title = "Permissao de localizacao",
                        message = "${getCleanDomain(currentUrl)} quer acessar sua localizacao",
                        confirmText = "Permitir",
                        onCancel = { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                    ) { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession, uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val what = listOfNotNull(
                    if (!video.isNullOrEmpty()) "camera" else null,
                    if (!audio.isNullOrEmpty()) "microfone" else null
                ).joinToString(" e ")
                runOnUiThread {
                    showWaspConfirmDialog("Acesso de midia", "${getCleanDomain(uri)} quer acessar: $what", "Permitir") {
                        callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                    }
                }
            }
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private fun navigate(input: String) {
        val q = input.trim()
        if (q.isEmpty()) return
        val url = when {
            q.startsWith("http://") || q.startsWith("https://") -> q
            q.contains(".") || q.contains("/") -> "https://$q"
            else -> "https://www.google.com/search?q=${Uri.encode(q)}"
        }
        openSite(url)
        urlInput.visibility = View.GONE
        urlDisplay.visibility = View.VISIBLE
    }

    private fun openSite(url: String) {
        sslErrorActive = false
        currentUrl = url
        try { popupSession?.close() } catch (_: Exception) {}
        popupSession = null

        val tab = tabs.getOrNull(activeTabIndex)
        if (tab != null) {
            tab.url = url
            geckoView.setSession(tab.session)
            try { tab.session.setActive(true) } catch (_: Exception) {}
        } else {
            geckoView.setSession(geckoSession)
            try { geckoSession.setActive(true) } catch (_: Exception) {}
        }

        urlInput.visibility = View.GONE
        urlDisplay.visibility = View.VISIBLE
        pageProgress.visibility = View.VISIBLE
        pageProgress.progress = 0
        updateTopBarForHome(false)
        webAppView.pauseTimers()
        getActiveSession().loadUri(url)
        crossfadeToGecko()
    }

    /** Troca suave: WebView (home) → GeckoView (browser) */
    private fun crossfadeToGecko() {
        if (geckoView.visibility == View.VISIBLE) return
        geckoView.alpha = 0f
        geckoView.visibility = View.VISIBLE
        topBar.alpha = 0f
        topBar.visibility = View.VISIBLE
        geckoView.animate().alpha(1f).setDuration(180).withEndAction {
            webAppView.visibility = View.GONE
        }.start()
        topBar.animate().alpha(1f).setDuration(180).start()
    }

    /** Troca suave: GeckoView (browser) → WebView (home) */
    private fun crossfadeToHome(onEnd: () -> Unit = {}) {
        if (webAppView.visibility == View.VISIBLE) { onEnd(); return }
        webAppView.alpha = 0f
        webAppView.visibility = View.VISIBLE
        webAppView.animate().alpha(1f).setDuration(180).withEndAction {
            geckoView.visibility = View.GONE
            topBar.visibility = View.GONE
            pageProgress.visibility = View.GONE
            onEnd()
        }.start()
    }

    fun goHome() {
        resetToMainSession()
        try { tabs.forEach { it.session.setActive(false) } } catch (_: Exception) {}

        runOnUiThread {
            webAppView.onResume()
            webAppView.resumeTimers()
            urlInput.setText("")
            urlInput.clearFocus()
            webAppView.evaluateJavascript("resetHome()", null)
            crossfadeToHome {
                webAppView.requestFocus(View.FOCUS_DOWN)
            }
        }
    }

    private fun openExternalApp(url: String) {
        if (url.isBlank()) return
        try {
            val intent = when {
                url.startsWith("intent://") -> Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                url.endsWith(".apk", ignoreCase = true) -> { startDownload(url, null, null, "application/vnd.android.package-archive"); return }
                else -> Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addCategory(Intent.CATEGORY_BROWSABLE); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            }
            if (packageManager.resolveActivity(intent, 0) != null) startActivity(intent)
            else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (e: Exception) {
            WaspToast.show(this, "Nenhum app encontrado para este link", WaspToast.NORMAL, false)
        }
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private fun updateTopBarForHome(isHome: Boolean) {
        if (isHome) {
            urlInput.visibility = View.INVISIBLE
            btnBack.visibility = View.INVISIBLE
            btnUrl.visibility = View.INVISIBLE
            iconSecurity.visibility = View.INVISIBLE
        } else {
            urlInput.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
            btnUrl.visibility = View.VISIBLE
            iconSecurity.visibility = View.VISIBLE
        }
    }

    private fun updateSecurityIcon(url: String) {
        iconSecurity.setImageResource(android.R.drawable.ic_lock_lock)
        iconSecurity.setColorFilter(when {
            sslErrorActive                   -> android.graphics.Color.parseColor("#EF4444")
            url.startsWith("https://", true) -> android.graphics.Color.parseColor("#22C55E")
            else                             -> android.graphics.Color.parseColor("#3D4560")
        })
    }

    private fun getCleanDomain(url: String): String {
        return try {
            if (url.contains("google.com/amp/"))
                return url.substringAfter("amp/s/").substringBefore("/").removePrefix("www.")
            var host = Uri.parse(url).host ?: url
            if (host.startsWith("www.")) host = host.substring(4)
            host
        } catch (e: Exception) { url }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    // ── Transição fade para todas as telas ───────────────────────────────────
    private fun startActivityFade(intent: Intent) {
        val opts = android.app.ActivityOptions
            .makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
        startActivity(intent, opts.toBundle())
    }

    fun openBeePanel() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: run {
            // Fallback: se o layout não tem o container, abre como Activity separada
            startActivityFade(Intent(this, BeeActivity::class.java))
            return
        }
        container.visibility = android.view.View.VISIBLE
        val params = container.layoutParams
        params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        container.layoutParams = params
        beePanelExpanded = true
        findViewById<android.widget.FrameLayout>(R.id.main_content_frame)?.visibility = android.view.View.GONE
        topBar.visibility = android.view.View.GONE
        persistentBeeView?.evaluateJavascript("""
            (function(){
                if (window._miniBarInterval) { clearInterval(window._miniBarInterval); window._miniBarInterval = null; }
                var overlay = document.getElementById('wasp-tap-overlay');
                if (overlay) overlay.style.display = 'none';
                document.body.style.overflow = '';
                document.documentElement.style.overflow = '';
                window.scrollTo(0, 0);
            })()
        """.trimIndent(), null)
    }

    fun collapseBeePanel() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: return
        val dp56 = (56 * resources.displayMetrics.density).toInt()
        val params = container.layoutParams
        params.height = dp56
        container.layoutParams = params
        container.visibility = android.view.View.VISIBLE
        beePanelExpanded = false
        findViewById<android.widget.FrameLayout>(R.id.main_content_frame)?.visibility = android.view.View.VISIBLE
        topBar.visibility = if (geckoView.visibility == android.view.View.VISIBLE) android.view.View.VISIBLE else android.view.View.GONE
        persistentBeeView?.evaluateJavascript("""
            (function(){
                if (window._miniBarInterval) { clearInterval(window._miniBarInterval); window._miniBarInterval = null; }
                var central = document.getElementById('wasp-central-frame');
                if (central) central.style.display = 'none';
                var old = document.getElementById('wasp-mini-bar');
                if (old) old.remove();
                var overlay = document.getElementById('wasp-tap-overlay');
                if (!overlay) {
                    overlay = document.createElement('div');
                    overlay.id = 'wasp-tap-overlay';
                    overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:9999;cursor:pointer;background:transparent;';
                    overlay.onclick = function() {
                        try { AndroidBee.openPanel(); } catch(e) {}
                    };
                    document.body.appendChild(overlay);
                }
                overlay.style.display = 'block';
                window.scrollTo(0, 0);
                document.body.style.overflow = 'hidden';
                document.documentElement.style.overflow = 'hidden';
            })()
        """.trimIndent(), null)
    }

    fun showBeeFooter() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: return
        if (container.visibility == android.view.View.GONE) {
            val dp56 = (56 * resources.displayMetrics.density).toInt()
            container.layoutParams.height = dp56
            container.visibility = android.view.View.VISIBLE
        }
    }

    fun hideBeeFooter() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: return
        container.visibility = android.view.View.GONE
        beePanelExpanded = false
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupPersistentBee() {
        val container = findViewById<android.widget.FrameLayout>(R.id.bee_panel_container) ?: return

        val wv = android.webkit.WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        wv.setBackgroundColor(0xFF0B0B0D.toInt())

        val bridge = BeeActivity.createBridge(this, wv)
        wv.addJavascriptInterface(bridge, "AndroidBee")

        wv.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.endsWith(".wasm")) {
                    return try {
                        android.webkit.WebResourceResponse(
                            "application/wasm", "binary", 200, "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            assets.open("bee/bee_sdk_bg.wasm")
                        )
                    } catch (e: Exception) { null }
                }
                return null
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                android.util.Log.d("PersistentBee", "Página carregada — Bee persistente pronta")
                BeeActivity.setPersistentWebView(wv)
            }
        }

        wv.loadUrl("file:///android_asset/bee/index.html")
        container.addView(wv)
        persistentBeeView = wv
        android.util.Log.d("PersistentBee", "Bee WebView persistente criada na MainActivity")
    }

    // =========================================================
    // DOWNLOADS
    // =========================================================

    private fun downloadImage(url: String) {
        try {
            val ext = when {
                url.contains(".png",  ignoreCase = true) -> "png"
                url.contains(".webp", ignoreCase = true) -> "webp"
                url.contains(".gif",  ignoreCase = true) -> "gif"
                else -> "jpg"
            }
            val fileName = "wasp_img_${System.currentTimeMillis()}.$ext"
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName); setDescription("Baixando imagem")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true); setAllowedOverRoaming(true)
                addRequestHeader("Referer", currentUrl)
                setMimeType("image/$ext")
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
            salvarDownloadNoWasp(fileName, url)
            showWaspToast("Download iniciado")
        } catch (e: Exception) { showWaspToast("Erro ao baixar imagem") }
    }

    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (url.isBlank()) return
        val scheme = url.substringBefore("://").lowercase()
        if (scheme == "blob" || scheme == "data" || scheme == "javascript") return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        try {
            var fileName = try { URLUtil.guessFileName(url, contentDisposition, mimeType) } catch (_: Exception) { "file_${System.currentTimeMillis()}" }
            val lowerUrl  = url.lowercase()
            val lowerMime = mimeType?.lowercase() ?: ""
            if (fileName.endsWith(".bin") || !fileName.contains(".")) {
                val ext = when {
                    lowerMime.contains("png")  || lowerUrl.contains(".png")  -> "png"
                    lowerMime.contains("jpeg") || lowerUrl.contains(".jpg")  -> "jpg"
                    lowerMime.contains("webp") || lowerUrl.contains(".webp") -> "webp"
                    lowerMime.contains("gif")  || lowerUrl.contains(".gif")  -> "gif"
                    lowerMime.contains("pdf")  || lowerUrl.contains(".pdf")  -> "pdf"
                    lowerMime.contains("zip")  || lowerUrl.contains(".zip")  -> "zip"
                    lowerMime.contains("apk")  || lowerUrl.contains(".apk")  -> "apk"
                    else -> "bin"
                }
                fileName = "file_${System.currentTimeMillis()}.$ext"
            }
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType ?: "*/*")
                if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Accept", "*/*")
                try { CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotEmpty() }?.let { addRequestHeader("Cookie", it) } } catch (_: Exception) {}
                setTitle(fileName); setDescription("Baixando arquivo...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true); setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            salvarDownloadNoWasp(fileName, url)
            WaspToast.show(this, "Download iniciado", WaspToast.SUCCESS, false)
        } catch (e: Exception) {
            WaspToast.show(this, "Nao foi possivel iniciar o download", WaspToast.ERROR, false)
        }
    }

    private fun salvarDownloadNoWasp(nome: String, url: String) {
        val prefs = getSharedPreferences("wasp_downloads", MODE_PRIVATE)
        val json  = prefs.getString("downloads_json", "[]") ?: "[]"
        val array = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val obj   = JSONObject().apply { put("name", nome); put("url", url); put("time", System.currentTimeMillis()) }
        val newArray = JSONArray()
        newArray.put(obj)
        for (i in 0 until minOf(array.length(), 199)) newArray.put(array.getJSONObject(i))
        prefs.edit().putString("downloads_json", newArray.toString()).apply()
    }

    // =========================================================
    // HISTORY
    // =========================================================

    private fun saveRecent(title: String?, url: String?) {
        if (url.isNullOrBlank() || url.startsWith("about:") || url.contains("google.com/search")) return
        val clean  = cleanUrl(url)
        val prefs  = getSharedPreferences("wasp_recents", MODE_PRIVATE)
        val oldList = JSONArray(prefs.getString("recents_json", "[]"))
        val newList = JSONArray()
        newList.put(JSONObject().apply { put("title", title ?: ""); put("url", clean); put("time", System.currentTimeMillis()) })
        for (i in 0 until oldList.length()) {
            val obj = oldList.getJSONObject(i)
            if (obj.getString("url") != clean) newList.put(obj)
        }
        val finalList = JSONArray()
        for (i in 0 until minOf(100, newList.length())) finalList.put(newList.getJSONObject(i))
        prefs.edit().putString("recents_json", finalList.toString()).apply()
    }

    private fun cleanUrl(raw: String): String {
        return try { Uri.parse(raw).buildUpon().clearQuery().build().toString() } catch (e: Exception) { raw }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private fun escapeJs(text: String) = text.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","")
    private fun escapeHtml(text: String) = text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

    @JavascriptInterface
    fun openPanel() {
        runOnUiThread { openBeePanel() }
    }

    @JavascriptInterface
    fun openSettings() {
        runOnUiThread {
            webAppView.evaluateJavascript("openSettings()", null)
        }
    }

    @JavascriptInterface
    fun openCentral() {
        runOnUiThread {
            val intent = android.content.Intent(this@MainActivity, CentralActivity::class.java)
            val opts = android.app.ActivityOptions
                .makeCustomAnimation(this@MainActivity, R.anim.slide_up, R.anim.fade_out)
            startActivity(intent, opts.toBundle())
        }
    }

    @JavascriptInterface
    fun openCentralWP() {
        // Alias — chamado pelo bee_engine quando falta WP para minerar
        openCentral()
    }

    @JavascriptInterface
    fun startBee() { runOnUiThread { WaspToast.show(this, "Bee Engine Started", WaspToast.SUCCESS, false) } }

    @JavascriptInterface
    fun clearCacheAndCookies() {
        runOnUiThread {
            // Limpa WebView da home
            webAppView.clearCache(true)
            webAppView.clearHistory()
            android.webkit.WebStorage.getInstance().deleteAllData()
            // Limpa cookies
            val cm = android.webkit.CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            // Limpa GeckoView se disponível
            try { geckoSession.purgeHistory() } catch (_: Exception) {}
            runOnUiThread {
                android.widget.Toast.makeText(this, "Cache e cookies apagados ✓", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun setTrackerBlock(enabled: Boolean) {
        getSharedPreferences("wasp_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("block_trackers", enabled).apply()
        android.util.Log.d("Privacy", "Bloquear rastreadores: $enabled")
    }

    @JavascriptInterface
    fun onSettingsClosed() {
        // Chamado quando o usuario fecha o settingsPanel
        // Se o GeckoView estava ativo (browser), restaura ele
        runOnUiThread {
            if (currentUrl.isNotEmpty()) {
                geckoView.visibility = android.view.View.VISIBLE
                topBar.visibility = android.view.View.VISIBLE
                webAppView.visibility = android.view.View.GONE
            }
        }
    }
}