package com.waspbrowser.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class BeeActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val TAG = "BeeActivity"
        private const val PREFS_BEE_ENERGY = "bee_energy"
        private const val PREFS_MINING      = "bee_mining"
        private const val KEY_MINING_ACTIVE  = "mining_active"
        private const val KEY_ENERGY_READY = "energy_ready"

        var instance: java.lang.ref.WeakReference<BeeActivity>? = null

        // WebView persistente na MainActivity — nunca é destruída
        var persistentWebView: java.lang.ref.WeakReference<android.webkit.WebView>? = null

        fun setPersistentWebView(wv: android.webkit.WebView) {
            persistentWebView = java.lang.ref.WeakReference(wv)
            Log.d(TAG, "persistentWebView registrada")
        }

        fun getPersistentWebView(): android.webkit.WebView? = persistentWebView?.get()

        fun runJs(js: String) {
            // Tenta primeiro na WebView persistente da MainActivity
            val persistent = persistentWebView?.get()
            if (persistent != null) {
                persistent.post {
                    runCatching {
                        persistent.resumeTimers()
                        persistent.evaluateJavascript(js, null)
                    }
                }
                return
            }
            // Fallback: BeeActivity se ainda existir
            val activity = instance?.get()
            if (activity == null) {
                Log.w(TAG, "runJs: sem WebView disponível — JS não executado")
            } else {
                activity.evaluateJs(js)
            }
        }

        // Cria a bridge para ser usada pela WebView persistente na MainActivity
        fun createBridge(context: android.content.Context, wv: android.webkit.WebView): Any {
            // Retorna uma instância da bridge interna
            // A MainActivity vai usar essa bridge com addJavascriptInterface
            return object {
                @android.webkit.JavascriptInterface
                fun ping(): String = "pong-persistent"

                @android.webkit.JavascriptInterface
                fun setMiningStatus(active: Boolean, wallet: String) {
                    Log.d(TAG, "[PersistentBee] setMiningStatus: $active wallet=$wallet")
                    context.getSharedPreferences("bee_mining", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("mining_active", active)
                        .putBoolean("pip_allowed", active) // PiP só quando minerando
                        .apply()
                    try {
                        if (active) {
                            val intent = BeeBackgroundService.buildStartIntent(context, wallet)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else { context.startService(intent) }
                        }
                    } catch (e: Exception) { Log.e(TAG, "setMiningStatus error: ${e.message}") }
                }

                @android.webkit.JavascriptInterface
                fun onEpochStarted() {
                    try {
                        context.startService(Intent(context, BeeBackgroundService::class.java).apply {
                            action = "com.waspbrowser.app.BEE_EPOCH_STARTED"
                        })
                    } catch (_: Exception) {}
                }

                @android.webkit.JavascriptInterface
                fun onEpochEnded() {
                    try {
                        context.startService(Intent(context, BeeBackgroundService::class.java).apply {
                            action = BeeBackgroundService.ACTION_EPOCH_ENDED
                        })
                    } catch (_: Exception) {}
                }

                @android.webkit.JavascriptInterface
                fun stopBgMining() {
                    try { context.startService(BeeBackgroundService.buildStopIntent(context)) }
                    catch (_: Exception) {}
                }

                @android.webkit.JavascriptInterface
                fun resumeForClaim() {
                    // Garante que WebView está ativa antes do get_reward() — crítico em PiP
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val wv = persistentWebView?.get()
                        wv?.resumeTimers()
                        wv?.onResume()
                    }
                }

                @android.webkit.JavascriptInterface
                fun toast(msg: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val type = when {
                            msg.contains("WP", true) || msg.contains("recompensa", true) ||
                            msg.contains("mineraç", true) || msg.contains("iniciada", true) ||
                            msg.contains("reward", true) -> WaspToast.SUCCESS
                            msg.contains("erro", true) || msg.contains("falhou", true) -> WaspToast.ERROR
                            else -> WaspToast.NORMAL
                        }
                        WaspToast.show(context, msg, type)
                    }
                }

                @android.webkit.JavascriptInterface
                fun openPanel() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        (context as? MainActivity)?.openBeePanel()
                    }
                }

                @android.webkit.JavascriptInterface
                fun navigateTo(screen: String) {}

                @android.webkit.JavascriptInterface
                fun goBack() {
                    // Usuário clicou voltar no painel Bee → minimiza para rodapé
                    // Não chama goHome() para preservar o site/tela que estava aberta
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        (context as? MainActivity)?.collapseBeePanel()
                    }
                }

                @android.webkit.JavascriptInterface
                fun hasWasm(): Boolean = try {
                    context.assets.open("bee/bee_sdk_bg.wasm").close(); true
                } catch (_: Exception) { false }

                @android.webkit.JavascriptInterface
                fun checkAssets(): String = try {
                    context.assets.list("bee")?.joinToString(", ") ?: "vazio"
                } catch (e: Exception) { "erro: ${e.message}" }

                @android.webkit.JavascriptInterface
                fun startBgMining(durationMs: Long, walletName: String) {
                    setMiningStatus(true, walletName)
                }

                @android.webkit.JavascriptInterface
                fun getBgMiningStatus(): String {
                    val active = BeeBackgroundService.isActive(context)
                    return """{"active":$active,"remainingMs":999999999,"cycles":0,"wallet":""}"""
                }

                @android.webkit.JavascriptInterface
                fun getMiningStatus(): String = """{"running":${BeeBackgroundService.isActive(context)}}"""

                @android.webkit.JavascriptInterface
                fun openDeepLink(url: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "openDeepLink erro: ${e.message}")
                        }
                    }
                }

                @android.webkit.JavascriptInterface
                fun openExternalUrl(url: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "openExternalUrl erro: ${e.message}")
                        }
                    }
                }
                @android.webkit.JavascriptInterface
                fun openCentral() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        wv.evaluateJavascript("""
                            (function(){
                                // Listener para fechar Central via postMessage
                                if (!window._centralMsgListener) {
                                    window._centralMsgListener = true;
                                    window.addEventListener('message', function(e) {
                                        if (e.data && e.data.type === 'closeCentral') {
                                            var overlay = document.getElementById('wasp-central-frame');
                                            if (overlay) overlay.style.display = 'none';
                                        }
                                        // Iframe da Central não tem a bridge nativa; o pai a tem.
                                        if (e.data && e.data.type === 'openWpAd') {
                                            if (window.AndroidBee && window.AndroidBee.openWpAd) window.AndroidBee.openWpAd();
                                            else if (window.Android && window.Android.openWpAd) window.Android.openWpAd();
                                        }
                                    });
                                }
                                var existing = document.getElementById('wasp-central-frame');
                                if (existing) { existing.style.display='flex'; return; }
                                var overlay = document.createElement('div');
                                overlay.id = 'wasp-central-frame';
                                overlay.style.cssText = 'position:fixed;top:0;left:0;right:0;bottom:0;z-index:8888;background:#0B0B0D;display:flex;flex-direction:column;';
                                var iframe = document.createElement('iframe');
                                iframe.src = 'file:///android_asset/bee/central.html';
                                iframe.style.cssText = 'width:100%;height:100%;border:none;flex:1;';
                                overlay.appendChild(iframe);
                                document.body.appendChild(overlay);
                            })()
                        """.trimIndent(), null)
                    }
                }

                @android.webkit.JavascriptInterface
                fun closeCentral() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // Esconde o overlay — index.html continua vivo com estado preservado
                        wv.evaluateJavascript("""
                            (function(){
                                var overlay = document.getElementById('wasp-central-frame');
                                if (overlay) overlay.style.display = 'none';
                            })()
                        """.trimIndent(), null)
                    }
                }

                @android.webkit.JavascriptInterface
                fun openCentralWP() {
                    // Alias de openCentral() — chamado pelo bee_engine.js quando falta WP
                    openCentral()
                }
                @android.webkit.JavascriptInterface
                fun setPipEnabled(enabled: Boolean) {
                    // Escolha do usuário: ligar/desligar a janela flutuante (PiP)
                    // ao sair do app. Guardado em SharedPreferences.
                    context.getSharedPreferences("bee_mining", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("pip_user_enabled", enabled).apply()
                }
                @android.webkit.JavascriptInterface
                fun isPipEnabled(): Boolean {
                    return context.getSharedPreferences("bee_mining", android.content.Context.MODE_PRIVATE)
                        .getBoolean("pip_user_enabled", false)  // default: desligado (não invasivo)
                }
                @android.webkit.JavascriptInterface
                fun openWpAd() {
                    // Chamado quando a Central roda como iframe nesta WebView.
                    // Cooldown nativo + abre a Activity de vídeo recompensado.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val prefs = context.getSharedPreferences("wasp_ads", android.content.Context.MODE_PRIVATE)
                        val last  = prefs.getLong("wp_ad_last", 0L)
                        val now   = System.currentTimeMillis()
                        val remaining = CentralActivity.AD_COOLDOWN_MS - (now - last)
                        if (remaining > 0) {
                            val min = (remaining / 60000) + 1
                            android.widget.Toast.makeText(context, "Próximo anúncio em ~$min min", android.widget.Toast.LENGTH_SHORT).show()
                            return@post
                        }
                        val i = Intent(context, StartioAdActivity::class.java)
                        i.putExtra(StartioAdActivity.EXTRA_MODE, "wp")
                        if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    }
                }
                @android.webkit.JavascriptInterface
                fun isEnergyReady(): Boolean {
                    return context.getSharedPreferences("bee_energy", android.content.Context.MODE_PRIVATE)
                        .getBoolean("energy_ready", false)
                }

                @android.webkit.JavascriptInterface
                fun clearEnergyReady() {
                    context.getSharedPreferences("bee_energy", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("energy_ready", false).apply()
                }
            }
        }
    }

    private lateinit var beeWebView: WebView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageLoaded = false
    private var isReceiverRegistered = false

    // ─── Keep-Alive Receiver ────────────────────────────────────────────────
    // Recebe o broadcast do BeeBackgroundService a cada tick e garante que
    // o WebView / miner continuam ativos sem serem suspensos pelo Android.
    private val keepAliveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BeeBackgroundService.ACTION_KEEP_ALIVE) return
            val tick = intent.getIntExtra("tick", 0)
            Log.d(TAG, "Keep-alive recebido | tick=$tick")
            // Mantém o WebView responsivo — o Service já cuida do restart via runJs
            beeWebView.resumeTimers()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_bee)

        instance = java.lang.ref.WeakReference(this)

        val action = intent?.getStringExtra("action")

        // Sem action de anuncio: redireciona para o painel persistente na MainActivity
        if (action == null && intent?.getBooleanExtra("background_restart", false) != true) {
            Log.d(TAG, "Sem action -> redirecionando para MainActivity/painel persistente")
            val i = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("navigate_to", "bee")
            }
            startActivity(i)
            finish()
            return
        }

        // Relancado pelo Service em background
        if (intent?.getBooleanExtra("background_restart", false) == true) {
            Log.d(TAG, "Relancado pelo Service em background")
            moveTaskToBack(true)
        }

        beeWebView = findViewById(R.id.beeWebView)
        window.decorView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.setBackgroundColor(0xFF0B0B0D.toInt())
        beeWebView.alpha = 0f

        configureWebView()
        attachBridge()
        attachClients()
        loadBeePanel()

        // Registra o receiver aqui para que funcione mesmo quando a activity estiver em background
        registerKeepAlive()
    }

    private fun registerKeepAlive() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(BeeBackgroundService.ACTION_KEEP_ALIVE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keepAliveReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(keepAliveReceiver, filter)
        }
        isReceiverRegistered = true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        returnToMain("home")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(beeWebView.settings) {
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
        beeWebView.isHapticFeedbackEnabled = false
        beeWebView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    }

    private fun loadBeePanel() {
        // Só carrega se ainda não inicializou — evita reiniciar o miner ao voltar
        if (pageLoaded) return
        val theme = intent.getStringExtra("wasp_theme")
            ?: getSharedPreferences("wasp_settings", android.content.Context.MODE_PRIVATE)
                .getString("theme", "dark") ?: "dark"
        beeWebView.loadUrl("file:///android_asset/bee/index.html")
    }

    private fun attachBridge() {
        beeWebView.addJavascriptInterface(BeeBridge(), "AndroidBee")
    }

    private fun attachClients() {
        beeWebView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
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
                        Log.e(TAG, "Erro ao servir WASM: ${e.message}")
                        null
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Aplica tema
                val theme = intent.getStringExtra("wasp_theme")
                    ?: getSharedPreferences("wasp_settings", android.content.Context.MODE_PRIVATE)
                        .getString("theme", "dark") ?: "dark"
                view?.evaluateJavascript(
                    "document.documentElement.setAttribute('data-theme','$theme');document.body&&document.body.setAttribute('data-theme','$theme');",
                    null
                )
                if (theme == "light") {
                    view?.evaluateJavascript("""
                        (function(){
                            var h = document.getElementById('bee-header');
                            if(h) h.style.background = 'rgba(240,242,248,0.97)';
                            if(h) h.style.borderBottomColor = 'rgba(0,0,0,0.08)';
                            document.body.style.background = '#F0F2F8';
                            document.body.style.color = '#1A1E2E';
                        })()
                    """.trimIndent(), null)
                }
                if (!pageLoaded) {
                    pageLoaded = true
                    beeWebView.animate().alpha(1f).setDuration(200).start()
                } else {
                    Log.d(TAG, "onPageFinished: recarga detectada — reiniciando mineração")
                    mainHandler.postDelayed({
                        evaluateJs("if(window.onAppResume) window.onAppResume()")
                    }, 3000)
                }
            }
        }
    }

    private fun openExternal(url: String) {
        if (url.isBlank()) return
        Log.d(TAG, "openExternal: $url")
        try {
            val intent = if (url.startsWith("intent://")) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!url.startsWith("intent://")) {
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir link: $url — ${e.message}")
            WaspToast.show(this, "Não foi possível abrir este link", WaspToast.ERROR, false)
        }
    }

    private fun returnToMain(screen: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("navigate_to", screen)
        }
        startActivity(intent)
        // Sem animacao ao sair — evita slide branco entre tasks
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    inner class BeeBridge {

        @JavascriptInterface
        fun ping(): String = "pong"

        @JavascriptInterface
        fun toast(m: String) {
            mainHandler.post { WaspToast.show(this@BeeActivity, m) }
        }

        @JavascriptInterface
        fun openDeepLink(url: String) {
            Log.d(TAG, "Bridge openDeepLink: ${url.take(100)}")
            mainHandler.post { openExternal(url) }
        }

        @JavascriptInterface
        fun openExternalUrl(url: String) {
            Log.d(TAG, "Bridge openExternalUrl: ${url.take(100)}")
            mainHandler.post { openExternal(url) }
        }

        @JavascriptInterface
        fun isEnergyReady(): Boolean {
            return getSharedPreferences(PREFS_BEE_ENERGY, MODE_PRIVATE)
                .getBoolean(KEY_ENERGY_READY, false)
        }

        @JavascriptInterface
        fun clearEnergyReady() {
            getSharedPreferences(PREFS_BEE_ENERGY, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENERGY_READY, false).apply()
        }

        @JavascriptInterface
        fun navigateTo(screen: String) {
            mainHandler.post { returnToMain(screen) }
        }

        @JavascriptInterface
        fun goBack() {
            mainHandler.post { returnToMain("home") }
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.d("BeeBridgeJS", msg)
        }

        @JavascriptInterface
        fun setMiningStatus(active: Boolean, wallet: String) {
            Log.d(TAG, "Bridge setMiningStatus: $active wallet=$wallet")
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
            try {
                if (active) {
                    // Mineração ligou — inicia Service e mostra overlay
                    val intent = BeeBackgroundService.buildStartIntent(this@BeeActivity, wallet)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } else {
                    // Usuário desligou — limpa flag
                    getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                        .edit().putBoolean(KEY_MINING_ACTIVE, false).apply()
                    Log.d(TAG, "setMiningStatus(false) — mineração desligada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "setMiningStatus service error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun setMiningActive(active: Boolean) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, active).apply()
        }

        @JavascriptInterface
        fun getMiningStatus(): String {
            val active = getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .getBoolean(KEY_MINING_ACTIVE, false)
            return """{"running":$active}"""
        }

        @JavascriptInterface
        fun openCentral() {
            mainHandler.post {
                val intent = Intent(this@BeeActivity, CentralActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opts = android.app.ActivityOptions
                    .makeCustomAnimation(this@BeeActivity, R.anim.slide_up, R.anim.fade_out)
                startActivity(intent, opts.toBundle())
            }
        }

        // Métodos de diagnóstico solicitados pelo JS
        @JavascriptInterface
        fun hasWasm(): Boolean {
            return try {
                assets.open("bee/bee_sdk_bg.wasm").close()
                true
            } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun checkAssets(): String {
            return try {
                assets.list("bee")?.joinToString(", ") ?: "vazio"
            } catch (e: Exception) { "erro: ${e.message}" }
        }

        // ─── Background Mining via WP ─────────────────────────────────────────

        @JavascriptInterface
        fun startBgMining(durationMs: Long, walletName: String) {
            Log.d(TAG, "startBgMining: wallet=$walletName (service roda indefinidamente)")
            try {
                val intent = BeeBackgroundService.buildStartIntent(
                    this@BeeActivity, walletName
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startBgMining error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onEpochStarted() {
            Log.d(TAG, "onEpochStarted: novo epoch iniciado")
            try {
                val intent = Intent(this@BeeActivity, BeeBackgroundService::class.java).apply {
                    action = "com.waspbrowser.app.BEE_EPOCH_STARTED"
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "onEpochStarted error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun onEpochEnded() {
            // JS avisou que epoch terminou — Service agenda restart via Kotlin Handler
            // (não depende do setTimeout do JS que é throttled em background)
            Log.d(TAG, "onEpochEnded: epoch terminou, notificando Service")
            try {
                val intent = BeeBackgroundService.buildStartIntent(this@BeeActivity, "")
                    .apply { action = BeeBackgroundService.ACTION_EPOCH_ENDED }
                startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "onEpochEnded error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun stopBgMining() {
            try {
                startService(BeeBackgroundService.buildStopIntent(this@BeeActivity))
                getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                    .edit().putBoolean(KEY_MINING_ACTIVE, false).apply()
            } catch (e: Exception) {
                Log.e(TAG, "stopBgMining error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getBgMiningStatus(): String {
            val active = BeeBackgroundService.isActive(this@BeeActivity)
            val remaining = BeeBackgroundService.remainingMs(this@BeeActivity)
            val prefs = getSharedPreferences(BeeBackgroundService.PREFS_BG, MODE_PRIVATE)
            val cycles = prefs.getInt(BeeBackgroundService.KEY_CYCLES, 0)
            val wallet = prefs.getString(BeeBackgroundService.KEY_WALLET, "") ?: ""
            return """{"active":$active,"remainingMs":$remaining,"cycles":$cycles,"wallet":"$wallet"}"""
        }
    }



    override fun onResume() {
        super.onResume()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        beeWebView.onResume()
        beeWebView.resumeTimers()
        evaluateJs("if(window.onAppResume) window.onAppResume()")
        // WakeLock: mantém CPU ativo para o JS do epoch continuar em background
        if (wakeLock == null || wakeLock?.isHeld == false) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaspBrowser:MinerWakeLock")
            wakeLock?.acquire(6 * 60 * 1000L) // 6 min (epoch 5min + margem)
        }
        // Eleva prioridade do thread principal para reduzir throttling em background
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        evaluateJs("if(window.onAppPause) window.onAppPause()")
        // NÃO pausar timers aqui — o JS do epoch precisa continuar rodando
        // mesmo quando o usuário navega para outro app
    }

    override fun onStop() {
        super.onStop()
        // Mantém o WebView ativo em background: não chamar beeWebView.onPause()
        // pois isso suspenderia o JS e interromperia o epoch
    }

    internal fun evaluateJs(js: String) {
        mainHandler.post {
            runCatching {
                beeWebView.resumeTimers()
                beeWebView.evaluateJavascript(js, null)
            }
        }
    }

    // Verifica se o JS ainda está vivo e reinicia o WebView se necessário
    fun checkJsAlive(callback: (Boolean) -> Unit) {
        mainHandler.post {
            try {
                beeWebView.resumeTimers()
                beeWebView.evaluateJavascript("(function(){ return window._mining !== undefined ? 'alive' : 'dead'; })()") { result ->
                    val alive = result?.contains("alive") == true
                    callback(alive)
                }
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    fun reloadWebView() {
        mainHandler.post {
            Log.w(TAG, "Recarregando WebView — contexto JS morto")
            try {
                beeWebView.resumeTimers()
                beeWebView.reload()
            } catch (e: Exception) {
                Log.e(TAG, "reloadWebView erro: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            try { unregisterReceiver(keepAliveReceiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        val bgStillActive = BeeBackgroundService.isActive(this)
        if (!bgStillActive) {
            getSharedPreferences(PREFS_MINING, MODE_PRIVATE)
                .edit().putBoolean(KEY_MINING_ACTIVE, false).apply()
        }
        // Libera o WakeLock ao destruir a Activity
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        instance = null
        beeWebView.destroy()
        super.onDestroy()
    }
}
