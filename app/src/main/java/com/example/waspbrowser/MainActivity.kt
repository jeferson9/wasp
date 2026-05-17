package com.example.waspbrowser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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

        // ─── Bee Dock ───────────────────────────────────────────────────────
        private const val BEE_DOCK_TAG = "BeeDock"
        private const val BEE_DOCK_COLLAPSED_HEIGHT_DP = 48
        // ID de teste do AdMob — trocar pelo real em produção (mesmo da BeeActivity)
        private const val BEE_REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
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
    private lateinit var beeMiningIndicator: MiningIndicator
    private lateinit var geckoView: GeckoView
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

    // ─── BEE DOCK PERSISTENTE ───────────────────────────────────────────────
    // WebView do painel Bee fica vivo dentro da MainActivity. Nunca é destruído
    // enquanto a Activity raiz viver — mineração não para ao navegar pelo app.
    private lateinit var mainContentRoot: View
    private lateinit var beeDock: FrameLayout
    private lateinit var beeDockWebView: WebView
    private lateinit var beeDockTapOverlay: View
    private var isBeeDockExpanded = false
    private var beeDockPageLoaded = false
    private var beeDockSetupDone = false

    // AdMob para o dock (reaproveita IDs / fluxo da BeeActivity)
    private var beeRewardedAd: RewardedAd? = null
    private var beeAdMode: String? = null
    private var beeEnergyGranted = false
    private var beeWpGranted = false
    private var isBeeAdShowing = false

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
        setupBeeDock()
        setupWebAppView()
        setupGecko()
        setupTopBar()
        setupUrlInput()

        webAppView.loadUrl("file:///android_asset/index.html")
        webAppView.post { webAppView.requestFocus(View.FOCUS_DOWN) }

        // Start with toolbar hidden - home has no toolbar
        topBar.visibility = View.GONE

        // Reseta o estado de mineração ao iniciar o app — a BeeActivity vai notificar
        // via setMiningStatus() se realmente estiver minerando quando aberta.
        // Isso evita que um crash anterior deixe o dot verde para sempre.
        getSharedPreferences("bee_mining", MODE_PRIVATE)
            .edit().putBoolean("mining_active", false).apply()

        updateMiningIndicator()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    fun onMiningStatusChanged(active: Boolean, wallet: String) {
        if (::beeMiningIndicator.isInitialized) {
            beeMiningIndicator.isMining = active
        }
        // Salva estado para persistir entre Activities
        getSharedPreferences("bee_mining", MODE_PRIVATE)
            .edit().putBoolean("mining_active", active).apply()
        // Reflete imediatamente no dock: ligou → mostra rodapé; desligou → oculta
        if (beeDockSetupDone && !isBeeDockExpanded) {
            collapseBeeDock()
        }
    }

    private fun updateMiningIndicator() {
        try {
            val prefs = getSharedPreferences("bee_mining", MODE_PRIVATE)
            val active = prefs.getBoolean("mining_active", false)
            if (::beeMiningIndicator.isInitialized) {
                beeMiningIndicator.isMining = active
            }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        webAppView.onResume()
        webAppView.resumeTimers()
        // Despertar o WebView do dock — mantém a mineração responsiva ao voltar
        if (::beeDockWebView.isInitialized) {
            beeDockWebView.onResume()
            beeDockWebView.resumeTimers()
            beeDockWebView.post {
                runCatching {
                    beeDockWebView.evaluateJavascript(
                        "if(window.onAppResume) window.onAppResume()", null
                    )
                }
            }
        }
        updateMiningIndicator()
        if (::webAppView.isInitialized && webAppView.visibility == View.VISIBLE &&
            ::geckoView.isInitialized && geckoView.visibility != View.VISIBLE) {
            webAppView.evaluateJavascript("setBottomTab('main')", null)
        }
    }

    override fun onPause() {
        super.onPause()
        webAppView.onPause()
        webAppView.pauseTimers()
        // NÃO chamamos onPause/pauseTimers no beeDockWebView de propósito.
        // bee_engine.js precisa continuar minerando mesmo com o app em background.
        // (Idêntico ao comportamento da BeeActivity stand-alone.)
        if (::beeDockWebView.isInitialized) {
            beeDockWebView.post {
                runCatching {
                    beeDockWebView.evaluateJavascript(
                        "if(window.onAppPause) window.onAppPause()", null
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { popupSession?.close() } catch (_: Exception) {}
        try { geckoSession.close() } catch (_: Exception) {}
        webAppView.stopLoading()
        webAppView.destroy()
        if (::beeDockWebView.isInitialized) {
            runCatching { beeDockWebView.stopLoading() }
            runCatching { beeDockWebView.destroy() }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 1) Bee Dock expandido → colapsa para o rodapé (mantém mineração viva)
        if (isBeeDockExpanded) {
            collapseBeeDock()
            return
        }
        // 2) Fluxo original
        if (geckoView.visibility == View.VISIBLE) {
            when {
                popupSession != null -> resetToMainSession()
                canGoBackGecko -> getActiveSession().goBack()
                else -> goHome()
            }
        } else {
            super.onBackPressed()
        }
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

    private fun showWaspToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.wasp_toast, null)
        layout.findViewById<TextView>(R.id.txtToast).text = message
        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = layout
        toast.setGravity(android.view.Gravity.BOTTOM, 0, 120)
        toast.show()
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
                    "home"      -> webAppView.evaluateJavascript("resetHome && resetHome()", null)
                    "market"    -> webAppView.evaluateJavascript("openMarketTab && openMarketTab()", null)
                    "hive"      -> webAppView.evaluateJavascript("""
                        (function(){
                            if(typeof resetHome==='function') resetHome();
                            setTimeout(function(){ if(typeof openHiveTab==='function') openHiveTab(); },80);
                        })();
                    """.trimIndent(), null)
                    "bee_panel" -> expandBeeDock()
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
        beeMiningIndicator = topBar.findViewById(R.id.beeMiningIndicator)
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

        topBar.findViewById<android.view.View>(R.id.btnBeePill)?.setOnClickListener {
            openBeePanel()
        }

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
                    "settings"   -> startActivityFade(Intent(this, SettingsActivity::class.java))
                    "about"      -> startActivityFade(Intent(this, AboutActivity::class.java))
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
            Toast.makeText(this, "Nenhum app encontrado para este link", Toast.LENGTH_SHORT).show()
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
        // Antes: startActivityFade(Intent(this, BeeActivity::class.java))
        // Agora: expande o dock persistente. O WebView do bee/index.html vive
        // dentro da MainActivity e nunca é destruído enquanto o app rodar.
        expandBeeDock()
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
            Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Nao foi possivel iniciar o download", Toast.LENGTH_SHORT).show()
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
    fun openCentral() {
        runOnUiThread {
            val intent = android.content.Intent(this@MainActivity, CentralActivity::class.java)
            val opts = android.app.ActivityOptions
                .makeCustomAnimation(this@MainActivity, R.anim.slide_up, R.anim.fade_out)
            startActivity(intent, opts.toBundle())
        }
    }

    @JavascriptInterface
    fun startBee() { runOnUiThread { Toast.makeText(this, "Bee Engine Started", Toast.LENGTH_SHORT).show() } }

    // =========================================================================
    //  BEE DOCK PERSISTENTE
    //  WebView do painel Bee mora aqui. Nunca é destruído enquanto a
    //  MainActivity viver — a mineração não para ao navegar pelo app.
    //  ATENÇÃO: NÃO MEXER no bee/*.{js,html,wasm}. Toda a lógica de mineração
    //  permanece no bee_engine.js como antes; aqui é só "casa nova" do WebView.
    // =========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupBeeDock() {
        if (beeDockSetupDone) return
        mainContentRoot = findViewById(R.id.mainContentRoot)
        beeDock = findViewById(R.id.beeDock)
        beeDockWebView = findViewById(R.id.beeDockWebView)
        beeDockTapOverlay = findViewById(R.id.beeDockTapOverlay)

        with(beeDockWebView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
        }
        beeDockWebView.isHapticFeedbackEnabled = false
        beeDockWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        beeDockWebView.setBackgroundColor(0xFF0B0B0D.toInt())

        // Bridge JS — mesmo nome window.AndroidBee da BeeActivity
        beeDockWebView.addJavascriptInterface(BeeDockBridge(this), "AndroidBee")

        // WebViewClient — serve WASM via assets (idêntico à BeeActivity)
        beeDockWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.endsWith(".wasm")) {
                    return try {
                        WebResourceResponse(
                            "application/wasm",
                            "binary",
                            200,
                            "OK",
                            mapOf("Access-Control-Allow-Origin" to "*"),
                            assets.open("bee/bee_sdk_bg.wasm")
                        )
                    } catch (e: Exception) {
                        Log.e(BEE_DOCK_TAG, "WASM serve err: ${e.message}")
                        null
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (beeDockPageLoaded) return
                beeDockPageLoaded = true
                Log.d(BEE_DOCK_TAG, "bee/index.html carregado no dock")
            }
        }

        // Overlay clicável: quando dock está colapsado, qualquer toque expande
        beeDockTapOverlay.setOnClickListener { expandBeeDock() }

        // Inicializar admob (sem ad ainda) e pré-carregar primeiro reward
        runCatching { MobileAds.initialize(this) {} }
        loadBeeRewardedAd()

        // Carrega o painel — bee_engine.js começa a inicializar imediatamente
        beeDockPageLoaded = false
        beeDockWebView.loadUrl("file:///android_asset/bee/index.html")

        beeDockSetupDone = true
        Log.d(BEE_DOCK_TAG, "setupBeeDock concluído — WebView ativo no rodapé")
    }

    /** Expande o dock para tela cheia. WebView continua sendo o mesmo objeto. */
    fun expandBeeDock() {
        if (!beeDockSetupDone) setupBeeDock()
        if (isBeeDockExpanded) return
        beeDock.visibility = View.VISIBLE
        val lp = beeDock.layoutParams
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        beeDock.layoutParams = lp
        beeDockTapOverlay.visibility = View.GONE
        // Em fullscreen o dock cobre tudo — não precisa reservar espaço pro conteúdo
        mainContentRoot.setPadding(0, 0, 0, 0)
        isBeeDockExpanded = true
        Log.d(BEE_DOCK_TAG, "dock expandido (fullscreen)")
    }

    /**
     * Colapsa o dock para a altura de rodapé. Mantém o WebView vivo e visível
     * — o JS continua rodando, mineração não interrompe.
     * Se mineração não está ativa, esconde o dock por completo.
     *
     * IMPORTANTE: ajusta paddingBottom do mainContentRoot para que o bottom-nav
     * da home (e qualquer conteúdo no rodapé) não fique sobreposto pelo dock.
     */
    fun collapseBeeDock() {
        if (!beeDockSetupDone) return
        val mining = getSharedPreferences("bee_mining", MODE_PRIVATE)
            .getBoolean("mining_active", false)
        val lp = beeDock.layoutParams
        val density = resources.displayMetrics.density
        if (mining) {
            // Mineração ativa: mantém faixa de 48dp no rodapé com overlay clicável
            val dockPx = (BEE_DOCK_COLLAPSED_HEIGHT_DP * density).toInt()
            lp.height = dockPx
            beeDock.layoutParams = lp
            beeDock.visibility = View.VISIBLE
            beeDockTapOverlay.visibility = View.VISIBLE
            // Reserva o mesmo espaço no fundo do conteúdo principal — bottom-nav
            // da home e tudo mais sobe pra cima do dock, sem sobreposição
            mainContentRoot.setPadding(0, 0, 0, dockPx)
            // Trava o scroll do WebView no topo para mostrar o cabeçalho do painel
            beeDockWebView.post { beeDockWebView.scrollTo(0, 0) }
            Log.d(BEE_DOCK_TAG, "dock colapsado (rodapé ${BEE_DOCK_COLLAPSED_HEIGHT_DP}dp)")
        } else {
            // Sem mineração: oculta completamente, mas WebView segue vivo
            lp.height = 0
            beeDock.layoutParams = lp
            beeDock.visibility = View.GONE
            beeDockTapOverlay.visibility = View.GONE
            mainContentRoot.setPadding(0, 0, 0, 0)
            Log.d(BEE_DOCK_TAG, "dock oculto (mineração inativa)")
        }
        isBeeDockExpanded = false
    }

    // ── AdMob compartilhado pelo dock (mesmas regras da BeeActivity) ─────────

    private fun loadBeeRewardedAd() {
        RewardedAd.load(
            this,
            BEE_REWARDED_TEST_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { beeRewardedAd = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { beeRewardedAd = null }
            }
        )
    }

    /** Chamado pelo [BeeDockBridge] quando o JS do painel pede um anúncio. */
    fun showBeeRewardedAd(mode: String) {
        val ad = beeRewardedAd
        if (ad == null) {
            Toast.makeText(this, "Anúncio ainda não carregou — tente em instantes", Toast.LENGTH_SHORT).show()
            loadBeeRewardedAd()
            return
        }
        if (isBeeAdShowing) return
        beeAdMode = mode
        beeEnergyGranted = false
        beeWpGranted = false
        isBeeAdShowing = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                beeRewardedAd = null
                isBeeAdShowing = false
                loadBeeRewardedAd()
                when {
                    beeAdMode == "energy" && beeEnergyGranted ->
                        beeDockEvalJs("if(window.onEnergyAdRewarded) window.onEnergyAdRewarded()")
                    beeAdMode == "energy" ->
                        beeDockEvalJs("if(window.onEnergyAdClosed) window.onEnergyAdClosed()")
                    beeAdMode == "wp" && beeWpGranted ->
                        beeDockEvalJs("if(window.onWpAdRewarded) window.onWpAdRewarded()")
                    beeAdMode == "wp" ->
                        beeDockEvalJs("if(window.onWpAdClosed) window.onWpAdClosed()")
                }
                beeAdMode = null
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                beeRewardedAd = null
                isBeeAdShowing = false
                loadBeeRewardedAd()
                beeAdMode = null
            }
        }

        ad.show(this) { _: RewardItem ->
            if (mode == "energy") beeEnergyGranted = true
            else beeWpGranted = true
        }
    }

    private fun beeDockEvalJs(js: String) {
        if (!::beeDockWebView.isInitialized) return
        beeDockWebView.post { runCatching { beeDockWebView.evaluateJavascript(js, null) } }
    }

    /**
     * onMiningStatusChanged já existe acima — quando a mineração começa,
     * deixamos o dock visível no modo colapsado para o usuário saber.
     * Esse método é só um hook para o callback existente expor ao dock.
     */
    fun ensureBeeDockVisibleIfMining() {
        val active = getSharedPreferences("bee_mining", MODE_PRIVATE)
            .getBoolean("mining_active", false)
        if (active && !isBeeDockExpanded && beeDockSetupDone) {
            collapseBeeDock()  // mostra modo rodapé
        }
    }
}