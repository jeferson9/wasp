(function () {
  "use strict";

  // ─── CONFIGURAÇÃO ────────────────────────────────────────────────────────
  // APP_ID oficial da rede Acki Nacki para mineração mobile
  // Fonte: https://docs.ackinacki.com/bee-sdk
  var APP_ID = "0x0000000000000000000000000000000000000000000000000000000000000017";
  var ENDPOINTS = ["https://mainnet-cf.ackinacki.org"];
  var KEY_STATE = "wasp_bee_state_v6";        // v6 — limpa estado corrompido de versões anteriores
  var MINING_DURATION_MS = 5 * 60 * 1000;    // 5 min por sessão (padrão Acki Nacki)

  // ─── WP (WASP POINTS) COMO MOEDA DE MINERAÇÃO ───────────────────────────
  var KEY_WP          = "wasp_wp";              // mesma chave usada pela central.html
  var KEY_WP_HISTORY  = "wasp_wp_history_v1";
  var WP_PER_MINUTE   = 1;                    // 1 WP por minuto de mineração
  var WP_MINING_COST  = WP_PER_MINUTE * Math.round(MINING_DURATION_MS / 60000); // 5 WP/sessão de 5min

  function getWP() {
    try { return Math.max(0, parseInt(localStorage.getItem(KEY_WP) || "0", 10) || 0); }
    catch(e) { return 0; }
  }

  function spendWP(amount, label) {
    try {
      var cur = getWP();
      if (cur < amount) return false;
      localStorage.setItem(KEY_WP, String(cur - amount));
      var hist = [];
      try { hist = JSON.parse(localStorage.getItem(KEY_WP_HISTORY) || "[]"); } catch(_) {}
      hist.unshift("[" + new Date().toLocaleString("en-US") + "] -" + amount + " WP • " + label);
      localStorage.setItem(KEY_WP_HISTORY, JSON.stringify(hist.slice(0, 100)));
      return true;
    } catch(e) { return false; }
  }

  function updateWpDisplay() {
    var el = document.getElementById("wpBalanceBee");
    if (el) el.textContent = getWP() + " WP";
    var btn = document.getElementById("wpMineBtn");
    if (btn) {
      var wp = getWP();
      if (wp >= WP_MINING_COST) {
        btn.textContent = "Minerar (1 WP/min)";
        btn.disabled = false;
        btn.classList.remove("btn-disabled");
      } else {
        btn.textContent = "Precisa de " + WP_MINING_COST + " WP para iniciar";
        btn.disabled = true;
        btn.classList.add("btn-disabled");
      }
    }
  }

  // ─── ESTADO ──────────────────────────────────────────────────────────────
  var saved = {
    walletName: "", publicKey: "", secretKey: "",
    minerAddress: "", authorized: false, propagated: false, firstTapDone: false,
    // autoMine removido
  };

  var wasmReady    = false;
  var miner        = null;
  var mining       = false;
  var claiming     = false; // guard: bloqueia nova epoch durante claim do reward
  var setupRunning = false;
  var rawDeepLink  = null;
  var cycles       = 0;
  var sessionStart = null;
  var uptimeTimer  = null;

  // ─── Refs ao DOM ─────────────────────────────────────────────────────────
  var dotStatus, txtTitle, txtDesc;
  var switchSub, miningSwitch;
  var mEngine, mCycles, mUptime, mWallet;
  var setupCard, walletInput, btnSetup, btnOpenAgain, btnReset;
  var tapSection, btnTap;
  var logBox;
  var n1, n2, n3, n4;

  function byId(id) { return document.getElementById(id); }

  function grabElements() {
    dotStatus    = byId("dotStatus");
    txtTitle     = byId("txtStatusTitle");
    txtDesc      = byId("txtStatusDesc");
    switchSub    = byId("switchSub");
    miningSwitch = byId("miningSwitch");
    mEngine      = byId("mEngine");
    mCycles      = byId("mCycles");
    mUptime      = byId("mUptime");
    mWallet      = byId("mWallet");
    setupCard    = byId("setupCard");
    walletInput  = byId("walletInput");
    btnSetup     = byId("btnSetup");
    btnOpenAgain = byId("btnOpenWalletAgain");
    btnReset     = byId("btnReset");
    tapSection   = byId("tapSection");
    btnTap       = byId("btnTap");
    logBox       = byId("logBox");
    n1 = byId("n1"); n2 = byId("n2"); n3 = byId("n3"); n4 = byId("n4");
  }

  // ─── LOG ─────────────────────────────────────────────────────────────────
  function log(msg, cls) {
    var lb = logBox || byId("logBox");
    if (!lb) { console.log("[Bee] " + msg); return; }
    var t = new Date().toLocaleTimeString("en-US");
    var line = document.createElement("div");
    if (cls) line.className = cls;
    line.textContent = "[" + t + "] " + msg;
    lb.appendChild(line);
    while (lb.children.length > 300) lb.removeChild(lb.firstChild);
    lb.scrollTop = lb.scrollHeight;
    console.log("[Bee] " + msg);
  }

  window.clearLog = function () {
    var lb = logBox || byId("logBox");
    if (lb) lb.innerHTML = "";
  };

  function setStatus(cls, title, desc) {
    if (dotStatus) dotStatus.className = "dot " + (cls || "");
    if (txtTitle)  txtTitle.textContent = title || "";
    if (txtDesc)   txtDesc.textContent  = desc  || "";
  }

  function setStep(s) {
    [n1, n2, n3, n4].forEach(function (el, i) {
      if (!el) return;
      var num = i + 1;
      if (s >= 5)         { el.className = "step-num done";   el.textContent = "✓"; }
      else if (num < s)   { el.className = "step-num done";   el.textContent = "✓"; }
      else if (num === s) { el.className = "step-num active"; el.textContent = String(num); }
      else                { el.className = "step-num";        el.textContent = String(num); }
    });
  }

  function pad2(n) { return String(n).padStart(2, "0"); }

  function getEpochsPaid() {
    try { return parseInt(localStorage.getItem("wasp_bee_epochs_paid") || "0", 10) || 0; }
    catch(_) { return 0; }
  }

  function updateMetrics() {
    if (mEngine) mEngine.textContent = mining ? "Running ⚡" : (wasmReady ? "Ready" : "Starting...");
    // "Ciclos" agora reflete épocas com reward efetivamente pago (métrica real),
    // não eventos de rede. Resolve a confusão entre o painel e o que cai na wallet.
    if (mCycles) mCycles.textContent = String(getEpochsPaid());
    if (mWallet) mWallet.textContent = saved.walletName || "—";
    if (mUptime) {
      if (sessionStart) {
        var s = Math.floor((Date.now() - sessionStart) / 1000);
        mUptime.textContent = pad2(Math.floor(s/3600))+":"+pad2(Math.floor((s%3600)/60))+":"+pad2(s%60);
      } else { mUptime.textContent = "00:00:00"; }
    }
    // Notifica a toolbar da MainActivity sobre estado de mining
    notifyMiningStatus();
  }

  // ─── PERSISTÊNCIA ────────────────────────────────────────────────────────
  function loadSaved() {
    try {
      var r = localStorage.getItem(KEY_STATE);
      if (r) Object.assign(saved, JSON.parse(r));
    } catch (e) { log("Falha ao ler estado: " + e.message, "lwrn"); }
  }

  function saveSaved() {
    try { localStorage.setItem(KEY_STATE, JSON.stringify(saved)); }
    catch (e) { log("Falha ao salvar estado: " + e.message, "lwrn"); }
  }

  function clearSaved() {
    saved = {
      walletName:"", publicKey:"", secretKey:"",
      minerAddress:"", authorized:false, propagated:false, firstTapDone:false
    };
    try { localStorage.removeItem(KEY_STATE); } catch(_) {}
  }

  // ─── BRIDGE ANDROID ──────────────────────────────────────────────────────
  function toast(msg) {
    try {
      if (window.AndroidBee && window.AndroidBee.toast) window.AndroidBee.toast(msg);
    } catch (_) {}
  }

  function openDeepLink(url) {
    if (!url) { log("ERRO: deep link vazio", "lerr"); return; }
    log("Abrindo AN Wallet...", "linf");
    try {
      if (window.AndroidBee && window.AndroidBee.openDeepLink)    { window.AndroidBee.openDeepLink(url); return; }
      if (window.AndroidBee && window.AndroidBee.openExternalUrl) { window.AndroidBee.openExternalUrl(url); return; }
      window.location.href = url;
    } catch (e) { log("Falha ao abrir wallet: " + e.message, "lerr"); }
  }

  // Notifica MainActivity para atualizar o indicador de mining na toolbar
  function notifyMiningStatus() {
    try {
      if (window.AndroidBee && window.AndroidBee.setMiningStatus) {
        window.AndroidBee.setMiningStatus(mining, saved.walletName || "");
      }
    } catch (_) {}
  }

  // ─── UTILS ───────────────────────────────────────────────────────────────
  function sleep(ms) { return new Promise(function(r){ setTimeout(r, ms); }); }

  function randomUserId() {
    try { if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID(); } catch(_){}
    return "user-" + Date.now() + "-" + Math.floor(Math.random()*1e6);
  }

  async function signHmacSha256(keyHex, message) {
    if (window.crypto && window.crypto.subtle) {
      try {
        var bytes = new Uint8Array(keyHex.match(/.{2}/g).map(function(b){ return parseInt(b,16); }));
        var key = await window.crypto.subtle.importKey("raw", bytes, {name:"HMAC",hash:"SHA-256"}, false, ["s..."]);
        var sig = await window.crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
        return Array.from(new Uint8Array(sig)).map(function(b){ return b.toString(16).padStart(2,"0"); }).join("");
      } catch(e) { log("crypto.subtle falhou: "+e.message, "lwrn"); }
    }
    throw new Error("HMAC not available — Android WebView requires API >= 23");
  }

  async function buildManualDeepLink(p) {
    var inner = JSON.stringify({
      app_id: p.appId,
      public: p.publicKey,
      user_id: randomUserId(),
      username: p.walletName
    });
    var b64 = btoa(inner);
    var sig = await signHmacSha256(p.secretKey, b64);
    var outer = JSON.stringify({
      data: b64,
      expire_at: Math.floor(Date.now()/1000) + 3600,
      signature: sig
    });
    return "https://links.gosh.sh/deeplinks/wallet/set-mining-keys?payload=" + btoa(outer);
  }

  // ─── WASM LOAD ───────────────────────────────────────────────────────────
  function loadWasmViaXhr(path) {
    return new Promise(function(resolve, reject) {
      var xhr = new XMLHttpRequest();
      xhr.open("GET", path, true);
      xhr.responseType = "arraybuffer";
      xhr.onload = function() {
        // status 0 = file:// no Android (sucesso), 200 = HTTP
        if (xhr.status === 0 || xhr.status === 200) {
          if (xhr.response && xhr.response.byteLength > 0) {
            resolve(xhr.response);
          } else {
            reject(new Error("WASM vazio em: " + path));
          }
        } else {
          reject(new Error("XHR status " + xhr.status + " para " + path));
        }
      };
      xhr.onerror   = function() { reject(new Error("XHR network error: " + path)); };
      xhr.ontimeout = function() { reject(new Error("XHR timeout: " + path)); };
      xhr.timeout   = 20000;
      xhr.send();
    });
  }

  async function loadSdk() {
    setStatus("warn", "Carregando SDK...", "Inicializando WASM");

    // SDK novo (ES module): WASM ja foi inicializado pelo <script type="module">
    // Aguarda ate 10s pelo evento beeSDKReady ou window._sdkReady
    if (typeof window.BeeSDK !== "undefined" && window._sdkReady === true) {
      log("BeeSDK: ✅ found and ready (ES module)", "lok");
      wasmReady = true;
      onSdkReady();
      return;
    }
    // Aguarda o evento se ainda nao chegou
    if (typeof window.BeeSDK === "undefined" || window._sdkReady !== true) {
      log("Aguardando BeeSDK ES module inicializar...", "linf");
      await new Promise(function(resolve) {
        if (window._sdkReady === true) { resolve(); return; }
        document.addEventListener("beeSDKReady", resolve, { once: true });
        setTimeout(resolve, 10000); // fallback 10s
      });
      if (window._sdkReady === true) {
        log("BeeSDK: ✅ ready (ES module)", "lok");
        wasmReady = true;
        onSdkReady();
        return;
      }
    }

    // FIX: verificar bridge primeiro para diagnóstico antecipado
    if (window.AndroidBee) {
      log("Android Bridge: ✅ connected", "lok");

      // FIX: usar bridge para verificar se WASM está nos assets
      try {
        var hasWasm = window.AndroidBee.hasWasm ? window.AndroidBee.hasWasm() : null;
        if (hasWasm === false) {
          log("❌ WASM NOT found in assets! Place bee_sdk_bg.wasm in app/src/main/assets/bee/", "lerr");
          setStatus("err", "WASM missing from assets", "File bee_sdk_bg.wasm not found");
          return;
        }
        var assetList = window.AndroidBee.checkAssets ? window.AndroidBee.checkAssets() : "—";
        log("Assets em bee/: " + assetList, "linf");
      } catch(e) {
        log("Could not check assets: " + e.message, "lwrn");
      }
    } else {
      log("⚠️ Android Bridge not found (running outside the app?)", "lwrn");
    }

    if (typeof window.BeeSDK === "undefined") {
      log("❌ window.BeeSDK not found!", "lerr");
      setStatus("err", "SDK not found", "Error initializing bee_sdk.js");
      return;
    }

    log("BeeSDK: ✅ found", "lok");

    // Ordem de tentativas — Android prioriza file:// via XHR (interceptado pelo WebViewClient)
    var isAndroid = !!(window.AndroidBee || (navigator.userAgent && navigator.userAgent.indexOf("Android") !== -1));
    var paths = isAndroid
      ? [
          "file:///android_asset/bee/bee_sdk_bg.wasm",  // interceptado pelo WebViewClient → assets
          "./bee_sdk_bg.wasm",
          "bee_sdk_bg.wasm"
        ]
      : [
          "./bee_sdk_bg.wasm",
          "bee_sdk_bg.wasm"
        ];

    for (var i = 0; i < paths.length; i++) {
      var p = paths[i];
      try {
        log("Tentando WASM: " + p, "linf");
        var wasmBytes = await loadWasmViaXhr(p);
        log("WASM downloaded: " + wasmBytes.byteLength + " bytes — initializing...", "linf");
        await window.BeeSDK.init(wasmBytes);
        wasmReady = true;
        log("WASM: ✅ ready! (" + p + ")", "lok");
        onSdkReady();
        return;
      } catch (e) {
        log("Falhou [" + p + "]: " + (e.message || String(e)), "lwrn");
      }
    }

    // Última tentativa: deixar o SDK resolver (funciona em browser desktop)
    try {
      log("Tentativa final: BeeSDK.init() sem argumento...", "linf");
      await window.BeeSDK.init();
      wasmReady = true;
      log("WASM: ✅ ready (default path)", "lok");
      onSdkReady();
    } catch (e) {
      log("❌ All attempts failed: " + (e.message || String(e)), "lerr");
      setStatus("err", "WASM falhou",
        "Confirme: bee_sdk_bg.wasm em app/src/main/assets/bee/ e AllowFileAccess=true");
    }
  }

  function onSdkReady() {
    updateMetrics();
    if (saved.authorized && saved.walletName && saved.minerAddress) {
      setStatus("on", "Bee ready ✅", "Wallet: " + saved.walletName);
      if (setupCard)    setupCard.classList.add("hidden");
      if (btnReset)     btnReset.classList.remove("hidden");
      if (miningSwitch) miningSwitch.disabled = false;
      var _wpNow = getWP();
      if (switchSub) switchSub.textContent = _wpNow >= WP_MINING_COST
        ? "Turn on to mine — 1 WP/min (" + WP_MINING_COST + " WP per session)"
        : "Need " + WP_MINING_COST + " WP to start (you have " + _wpNow + ")";
      updateWpDisplay();
      setStep(5);
      log("Session restored: " + saved.walletName, "lok");
      // Mostrar aviso Mambaboard se não foi dismissado (rewards requerem ativação)
      try {
        var mambaDismissed = localStorage.getItem("wasp_mamba_dismissed");
        var mambaEl = document.getElementById("mambaCard");
        if (mambaEl && !mambaDismissed) mambaEl.classList.remove("hidden");
      } catch(_) {}
      if (!saved.propagated) {
        log("Propagation pending — trying to confirm in background...", "lwrn");
        setTimeout(tryPropagateBackground, 3000);
      }
// Auto-start removido — usuario deve ligar manualmente
    } else {
      setStatus("warn", "Configure a Bee Engine", "Informe o wallet name e autorize na AN Wallet");
      if (setupCard)    setupCard.classList.remove("hidden");
      if (walletInput && saved.walletName) walletInput.value = saved.walletName;
      if (miningSwitch) { miningSwitch.checked = false; miningSwitch.disabled = true; }
      setStep(1);
    }
  }

  // ─── PROPAGAÇÃO EM BACKGROUND ────────────────────────────────────────────
  // Versão robusta: retry automático a cada 30s até confirmar propagação,
  // máximo 10 tentativas (~5 minutos). Log explícito antes do get_reward().
  var _propagateAttempts = 0;
  var _propagateMaxAttempts = 10;
  var _propagateRetryMs = 30000;

  async function tryPropagateBackground() {
    if (saved.propagated || !saved.minerAddress || !saved.publicKey) return;
    _propagateAttempts++;
    log("🔍 Checking propagation in background... (attempt " + _propagateAttempts + "/" + _propagateMaxAttempts + ")", "linf");

    try {
      await window.BeeSDK.ensure_mining_keys_propagated({
        client_config: { network: { endpoints: ENDPOINTS } },
        miner_address: saved.minerAddress,
        app_id: APP_ID,
        expected_owner_public: saved.publicKey,
        max_attempts: 30,
        interval_ms: 1000
      });
      // ✅ Confirmada — só a partir daqui o get_reward() será chamado com segurança
      saved.propagated = true; saveSaved();
      log("✅ Propagation confirmed", "lok");
      var dp = byId("dPropagated"); if (dp) dp.textContent = "✅ Confirmed";
    } catch(e) {
      var em = e && e.message ? e.message.substring(0, 80) : String(e);
      log("⏳ Propagation pending: " + em, "linf");
      if (_propagateAttempts < _propagateMaxAttempts) {
        log("🔁 Retrying in " + (_propagateRetryMs/1000) + "s...", "linf");
        setTimeout(tryPropagateBackground, _propagateRetryMs);
      } else {
        log("⚠️ Propagation not confirmed after " + _propagateMaxAttempts + " attempts — mining may fail on claim.", "lwrn");
      }
    }
  }

  // ─── SETUP ───────────────────────────────────────────────────────────────
  async function runSetup() {
    if (setupRunning) return;
    if (!wasmReady) {
      log("SDK not yet loaded — please wait", "lwrn");
      toast("Aguarde o SDK carregar...");
      return;
    }
    var walletName = walletInput ? walletInput.value.trim() : "";
    if (!walletName) {
      log("Digite seu wallet name", "lerr");
      if (walletInput) walletInput.focus();
      return;
    }

    setupRunning = true;
    if (btnSetup) {
      btnSetup.disabled = true;
      btnSetup.innerHTML = '<span class="spinner"></span>Gerando chaves...';
    }

    try {
      setStep(2);
      setStatus("warn", "Generating keys...", "Creating mining identity");
      log("Chamando gen_mining_keys...", "linf");

      // FIX: walletName precisa ser definido ANTES de gen_mining_keys.
      // Antes, gen_mining_keys recebia saved.walletName ainda vazio na 1ª
      // configuração, gerando chaves dessincronizadas do miner address —
      // o que fazia a wallet pedir "definir chaves de mineração novamente".
      saved.walletName = walletName;
      // Setup novo → propagação tem de ser reconfirmada do zero.
      saved.propagated = false;
      saved.minerAddress = "";
      saveSaved();

      // SDK novo: gen_mining_keys(app_id, username)
      var result = await window.BeeSDK.gen_mining_keys(APP_ID, saved.walletName);
      saved.publicKey  = result.public;
      saved.secretKey  = result.secret;
      saveSaved();

      log("Keys generated ✅ pub=" + result.public.substring(0,20)+"...", "lok");

      // Construir deep link manualmente — formato /set-mining-keys
      // (o result.deep_link do SDK usa path legado /wallet/connect)
      log("Construindo deep link...", "linf");
      rawDeepLink = await buildManualDeepLink({
        appId: APP_ID,
        publicKey: result.public,
        secretKey: result.secret,
        walletName: walletName
      });
      log("Deep link: ✅", "lok");

      setStep(3);
      setStatus("warn", "Abrindo AN Wallet...", "Confirme o registro das chaves");
      openDeepLink(rawDeepLink);

      if (btnSetup) {
        btnSetup.disabled = false;
        btnSetup.textContent = "✅ Já autorizei — continuar";
        btnSetup.onclick = confirmAuthorization;
      }
      if (btnOpenAgain) btnOpenAgain.classList.remove("hidden");
      if (btnReset)     btnReset.classList.remove("hidden");
      setStatus("warn", "Aguardando confirmação", "Autorize na AN Wallet e toque em continuar");
      log("Confirme na AN Wallet e volte aqui", "lwrn");

    } catch (e) {
      var em = e && e.message ? e.message : String(e);
      log("Erro no setup: " + em, "lerr");
      setStatus("err", "Erro no setup", em.substring(0,100));
      if (btnSetup) {
        btnSetup.disabled = false;
        btnSetup.textContent = "Tentar novamente";
        btnSetup.onclick = runSetup;
      }
    }
    setupRunning = false;
  }

  // ─── CONFIRMAR AUTORIZAÇÃO ───────────────────────────────────────────────
  async function confirmAuthorization() {
    if (!wasmReady) { log("SDK não está pronto", "lwrn"); return; }
    if (!saved.walletName) { log("Wallet name não informado", "lerr"); return; }

    if (btnSetup) {
      btnSetup.disabled = true;
      btnSetup.innerHTML = '<span class="spinner"></span>Confirmando...';
    }
    setStep(4);
    setStatus("warn", "Confirmando...", "Buscando miner address na blockchain");

    try {
      var minerAddr = null;
      for (var i = 1; i <= 20; i++) {
        try {
          log("Buscando miner address " + i + "/20...", "linf");
          minerAddr = await window.BeeSDK.get_miner_address_by_wallet_name({
            client_config: { network: { endpoints: ENDPOINTS } },
            wallet_name: saved.walletName
          });
          if (minerAddr) break;
        } catch (e) {
          log("Tentativa " + i + " falhou: " + (e.message||String(e)), "lwrn");
          if (i < 20) await sleep(3000); else throw e;
        }
      }
      if (!minerAddr) {
        throw new Error("Miner address não encontrado — verifique se autorizou na wallet");
      }

      saved.minerAddress = minerAddr; saveSaved();
      log("Miner address: ✅ " + minerAddr, "lok");

      // Libera o usuário imediatamente — propagação roda em background
      saved.authorized = true; saveSaved();
      setStatus("on", "Bee autorizada ✅", "Wallet: " + saved.walletName);
      if (setupCard)    setupCard.classList.add("hidden");
      if (miningSwitch) miningSwitch.disabled = false;
      var _wpNow2 = getWP();
      if (switchSub) switchSub.textContent = _wpNow2 >= WP_MINING_COST
        ? "Turn on to mine — 1 WP/min (" + WP_MINING_COST + " WP per session)"
        : "Precisa de " + WP_MINING_COST + " WP para iniciar (você tem " + _wpNow2 + ")";
      updateWpDisplay();
      setStep(5); updateMetrics();
      log("Bee autorizada! Ligue a mineração.", "lok");
      log("⚠️ IMPORTANTE: Para receber rewards, ative o Mambaboard em Batteries ou Ludo (Telegram).", "lwrn");
      toast("Bee Engine autorizada! ✅");

      // Propagação em background — não bloqueia o usuário
      // O SDK faz o poll internamente com max_attempts=60 e interval_ms=3000 (~3 min total)
      if (!saved.propagated) {
        log("🔄 Confirmando propagação em background (não precisa aguardar)...", "linf");
        window.BeeSDK.ensure_mining_keys_propagated({
          client_config: { network: { endpoints: ENDPOINTS } },
          miner_address: minerAddr,
          app_id: APP_ID,
          expected_owner_public: saved.publicKey,
          max_attempts: 30,
          interval_ms: 1000
        }).then(function() {
          saved.propagated = true; saveSaved();
          log("✅ Propagação confirmada em background!", "lok");
          var dp = byId("dPropagated"); if (dp) dp.textContent = "✅ Confirmed";
        }).catch(function(e) {
          log("Propagação background: " + (e&&e.message?e.message.substring(0,80):String(e)), "lwrn");
          log("Mineração funciona mesmo assim — propagação confirma automaticamente.", "linf");
        });
      }
      // Mostrar aviso Mambaboard se não foi dismissado
      try {
        var dismissed = localStorage.getItem("wasp_mamba_dismissed");
        var mambaCard = document.getElementById("mambaCard");
        if (mambaCard && !dismissed) mambaCard.classList.remove("hidden");
      } catch(_) {}

    } catch (e) {
      var em2 = e && e.message ? e.message : String(e);
      log("Erro na confirmação: " + em2, "lerr");
      setStatus("err", "Falha na confirmação", em2.substring(0,120));
      if (btnSetup) {
        btnSetup.disabled = false;
        btnSetup.textContent = "Tentar verificar novamente";
        btnSetup.onclick = confirmAuthorization;
      }
    }
  }

  // ─── MINERAÇÃO ───────────────────────────────────────────────────────────
  // Controle de WP da sessão atual: o débito só é "confirmado" quando a
  // mineração realmente inicia (doStartMiner). Se a sessão abortar antes
  // disso, o WP é reembolsado para não queimar saldo à toa.
  var _wpDebited = false;
  var _awaitingPropagation = false;

  function refundSessionWP(reason) {
    if (!_wpDebited) return;
    _wpDebited = false;
    try {
      var cur = getWP();
      localStorage.setItem(KEY_WP, String(cur + WP_MINING_COST));
      var hist = [];
      try { hist = JSON.parse(localStorage.getItem(KEY_WP_HISTORY) || "[]"); } catch(_) {}
      hist.unshift("[" + new Date().toLocaleString("en-US") + "] +" + WP_MINING_COST + " WP • Reembolso (" + (reason || "sessão abortada") + ")");
      localStorage.setItem(KEY_WP_HISTORY, JSON.stringify(hist.slice(0, 100)));
    } catch(_) {}
    log("↩️ " + WP_MINING_COST + " WP reembolsados (" + (reason || "sessão abortada") + "). Saldo: " + getWP() + " WP", "lwrn");
    updateWpDisplay();
  }

  async function startMining() {
    if (!wasmReady || !saved.authorized || mining) return;

    // ── FIX corrida claim×restart: se ainda há um claim em andamento, NÃO
    //    abortar silenciosamente (isso perdia a epoch). Reagenda e tenta
    //    de novo quando o claim terminar.
    if (claiming) {
      log("⏳ Claim do reward anterior ainda em andamento — reagendando início em 10s...", "lwrn");
      setTimeout(function() {
        if (miningSwitch && miningSwitch.checked) startMining();
      }, 10000);
      return;
    }

    // ── FIX "primeira sessão não cai": se as chaves ainda não foram
    //    propagadas na blockchain, aguardar a confirmação ANTES de
    //    descontar WP e iniciar. Antes, a 1ª epoch após definir as chaves
    //    rodava com a propagação ainda pendente, o claim falhava e o reward
    //    não caía — só caía ao desligar/religar (quando já havia propagado).
    if (!saved.propagated && saved.minerAddress && saved.publicKey) {
      if (_awaitingPropagation) {
        log("⏳ Já aguardando propagação das chaves — aguarde...", "linf");
        return;
      }
      _awaitingPropagation = true;
      log("🔑 Primeira mineração: confirmando registro das chaves na rede antes de iniciar...", "lwrn");
      setStatus("warn", "Registrando chaves...", "Aguardando a rede confirmar (pode levar ~1 min)");
      if (switchSub) switchSub.textContent = "Registering keys on network...";
      if (miningSwitch) miningSwitch.checked = true; // mantém intenção do usuário
      try {
        await window.BeeSDK.ensure_mining_keys_propagated({
          client_config: { network: { endpoints: ENDPOINTS } },
          miner_address: saved.minerAddress,
          app_id: APP_ID,
          expected_owner_public: saved.publicKey,
          max_attempts: 60,
          interval_ms: 2000
        });
        saved.propagated = true; saveSaved();
        log("✅ Chaves confirmadas na rede! Iniciando mineração — o reward desta epoch já vai cair.", "lok");
        var dp = byId("dPropagated"); if (dp) dp.textContent = "✅ Confirmed";
      } catch (e) {
        log("⚠️ Propagação ainda não confirmou após ~2 min. Iniciando mesmo assim; se o reward não cair, toque em Reautorizar chaves.", "lwrn");
        showReauthPrompt();
      }
      _awaitingPropagation = false;
      // Se o usuário desligou enquanto aguardava, não inicia.
      if (miningSwitch && !miningSwitch.checked) {
        setStatus("on", "Bee ready", "Mining paused");
        if (switchSub) switchSub.textContent = "Turn on to mine";
        return;
      }
      // Cai através para o fluxo normal de início abaixo.
    }

    // ── Verifica e desconta WP ─────────────────────────────────────────────
    var wp = getWP();
    if (wp < WP_MINING_COST) {
      // Sem WP — bloqueia e avisa
      if (miningSwitch) { miningSwitch.checked = false; miningSwitch.disabled = false; }
      if (switchSub) switchSub.textContent = "Need " + WP_MINING_COST + " WP to mine";
      setStatus("err", "WP insuficiente", "Ganhe " + WP_MINING_COST + " WP na Central WP para iniciar mineração");
      log("❌ WP insuficiente para iniciar mineração. Saldo: " + wp + " WP. Necessário: " + WP_MINING_COST + " WP", "lerr");
      updateWpDisplay();
      return;
    }

    // Desconta WP da sessão (provisório — confirmado só em doStartMiner)
    var ok = spendWP(WP_MINING_COST, "Sessão de mineração NACKL");
    if (!ok) {
      if (miningSwitch) miningSwitch.checked = false;
      if (switchSub) switchSub.textContent = "Error debiting WP";
      return;
    }
    _wpDebited = true;
    log("✅ " + WP_MINING_COST + " WP debitados. Saldo restante: " + getWP() + " WP", "lok");
    updateWpDisplay();

    // Reseta taps e incrementa epoch a cada novo inicio
    window._tapCount = 0;
    window._epochCount = (window._epochCount || 0) + 1;
    try {
      setStatus("warn", "Conectando ao Miner...", "Inicializando sessão");

      // Retry com backoff para error 410 (stale session)
      var minerCreated = false;
      var retryDelays = [0, 15000, 20000, 25000, 30000];
      for (var ri = 0; ri < retryDelays.length; ri++) {
        if (retryDelays[ri] > 0) {
          log("⏳ Aguardando " + (retryDelays[ri]/1000) + "s para sessão expirar... (" + (ri+1) + "/5)", "lwrn");
          setStatus("warn", "Aguardando sessão expirar...", "Tentativa " + (ri+1) + " de 5");
          await sleep(retryDelays[ri]);
        }
        try {
          miner = await window.BeeSDK.Miner.new(
            ENDPOINTS, APP_ID, saved.minerAddress, saved.publicKey, saved.secretKey
          );
          minerCreated = true;
          log("Miner criado ✅ (tentativa " + (ri+1) + ")", "lok");
          break;
        } catch(retryErr) {
          var retryMsg = retryErr && retryErr.message ? retryErr.message : String(retryErr);
          var isStale = retryMsg.indexOf("stale") !== -1
                     || retryMsg.indexOf("410") !== -1
                     || retryMsg.indexOf("Cancel") !== -1;
          if (isStale && ri < retryDelays.length - 1) {
            log("⏳ Sessão ainda ativa (stale 410). Aguardando...", "lwrn");
            continue;
          }
          throw retryErr;
        }
      }
      if (!minerCreated) throw new Error("Não foi possível criar o Miner após 5 tentativas");

      var canStart = miner.can_start();
      log("can_start() = " + canStart, "linf");

      if (!canStart) {
        // Meio de epoch — aguarda próximo (até 6 min)
        log("⏳ Aguardando próximo epoch... can_start() vai virar true", "lwrn");
        setStatus("warn", "Aguardando epoch...", "Próximo epoch em até 5.5 minutos");
        if (miningSwitch) miningSwitch.checked = true;

        var epochWait = 0;
        if (window._epochCheckTimer) { clearInterval(window._epochCheckTimer); }
        var epochCheck = setInterval(async function() {
          epochWait += 15;
          try {
            var tmpMiner = await window.BeeSDK.Miner.new(
              ENDPOINTS, APP_ID, saved.minerAddress, saved.publicKey, saved.secretKey
            );
            var cs = tmpMiner.can_start();
            log("Aguardando epoch... " + epochWait + "s | can_start()=" + cs, "linf");
            if (cs) {
              clearInterval(epochCheck); window._epochCheckTimer = null;
              miner = tmpMiner;
              log("✅ Novo epoch! Iniciando mineração...", "lok");
              doStartMiner();
            } else {
              try { tmpMiner.stop(); } catch(_) {}
              if (epochWait >= 360) {
                clearInterval(epochCheck); window._epochCheckTimer = null;
                log("❌ Epoch não iniciou em 6 minutos. Tente resetar.", "lerr");
                setStatus("err", "Timeout de epoch", "Tente resetar a configuração");
                if (miningSwitch) miningSwitch.checked = false;
                miner = null;
                refundSessionWP("epoch não iniciou");
              }
            }
          } catch(e) {
            log("Erro ao checar epoch: " + e.message, "lwrn");
          }
        }, 15000);
        window._epochCheckTimer = epochCheck;
        return;
      }

      doStartMiner();

    } catch (e) {
      var em = e && e.message ? e.message : String(e);
      var isFetch = em.indexOf("205") !== -1 || em.indexOf("Failed to fetch") !== -1 || em.indexOf("fetch") !== -1;
      if (isFetch) {
        // Nó instável — reembolsa WP e tenta novamente em 60s sem desistir
        log("⚠️ Nó instável (fetch error) — tentando novamente em 60s...", "lwrn");
        setStatus("warn", "Nó instável — reconectando...", "Próxima tentativa em 60s");
        miner = null;
        refundSessionWP("nó instável");
        setTimeout(function() {
          if (miningSwitch && miningSwitch.checked) startMining();
        }, 60000);
      } else {
        log("Erro ao iniciar mineração: " + em, "lerr");
        setStatus("err", "Erro ao iniciar", em.substring(0,100));
        if (miningSwitch) miningSwitch.checked = false;
        miner = null;
        refundSessionWP("erro ao iniciar");
      }
    }
  }

  // ─── START EFETIVO ────────────────────────────────────────────────────────
  function doStartMiner() {
    var lastEventTime = Date.now();
    var epochDone = false; // flag para evitar duplo handleEpochEnd

    // Watchdog: reinicia se ficar 90s sem eventos (aumentado — rede pode ser lenta)
    if (window._watchdogTimer) clearInterval(window._watchdogTimer);
    window._watchdogTimer = setInterval(function() {
      if (!mining) { clearInterval(window._watchdogTimer); return; }
      var silence = Date.now() - lastEventTime;
      if (silence > 90000) {
        log("⚠️ Watchdog: " + Math.round(silence/1000) + "s sem eventos — reiniciando...", "lwrn");
        clearInterval(window._watchdogTimer); window._watchdogTimer = null;
        if (window._epochTimer) { clearTimeout(window._epochTimer); window._epochTimer = null; }
        epochDone = true;
        try { if (miner) miner.stop(); } catch(_) {}
        miner = null; mining = false;
        if (miningSwitch) miningSwitch.checked = true;
        setStatus("warn", "Reconectando...", "Watchdog reiniciando miner");
        notifyMiningStatus();
        setTimeout(function() {
          if (miningSwitch && miningSwitch.checked) startMining();
        }, 30000);
      }
    }, 15000);

    // FIX PRINCIPAL: miner.start() é SÍNCRONO e NÃO emite evento de fim de epoch.
    // O epoch termina silenciosamente após duration_ms.
    // Usamos setTimeout para detectar o fim e chamar get_reward().
    if (window._epochTimer) clearTimeout(window._epochTimer);
    window._epochTimer = setTimeout(function() {
      window._epochTimer = null;
      if (epochDone) return;
      epochDone = true;
      log("⏱️ Epoch de 5 minutos concluído — iniciando claim de reward...", "lwrn");
      handleEpochEnd("timeout");
    }, MINING_DURATION_MS + 3000); // +3s de margem após a sessão terminar

    // ─── CLAIM SEGURO ─────────────────────────────────────────────────────
    // Aguarda propagacao (max 90s) e dispara get_reward() mesmo sem confirmar.
    // Propagacao pendente NAO deve bloquear o claim -- a rede aceita mesmo assim.
    function claimRewardSafe(claimMiner) {
      return new Promise(function(resolve) {
        claiming = true;

        function doGetReward(attempt) {
          attempt = attempt || 1;
          log("💰 Chamando get_reward()... (tentativa " + attempt + "/3)", "linf");
          claimMiner.get_reward().then(function() {
            log("✅ get_reward() OK! Reward enviado para a blockchain.", "lok");
            log("💎 Verifique sua AN Wallet — saldo NACKL atualizado em breve.", "lok");
            // Contador real de épocas pagas (persistido) — distingue de "cycles"
            // (eventos de rede) e de "epochCount" (épocas iniciadas).
            try {
              var paid = parseInt(localStorage.getItem("wasp_bee_epochs_paid") || "0", 10) || 0;
              localStorage.setItem("wasp_bee_epochs_paid", String(paid + 1));
            } catch(_) {}
            var el = byId("mReward"); if (el) el.textContent = "Sent ✅";
            updateMetrics();
            toast("Reward NACKL enviado! ✅");
            try { claimMiner.stop(); } catch(_) {}
            claiming = false;
            resolve();
          }).catch(function(e) {
            var errMsg = e && e.message ? e.message.substring(0,120) : String(e);
            log("❌ get_reward() falhou (tentativa " + attempt + "): " + errMsg, "lerr");
            if (attempt < 3) {
              var delay = attempt * 10000; // 10s, 20s
              log("🔄 Tentando novamente em " + (delay/1000) + "s...", "lwrn");
              setTimeout(function() { doGetReward(attempt + 1); }, delay);
            } else {
              log("❌ get_reward() falhou após 3 tentativas. Causas: chaves de mineração não propagadas, Mambaboard inativo, ou rede instável.", "lerr");
              if (!saved.propagated) {
                log("⚠️ AÇÃO NECESSÁRIA: as chaves de mineração ainda não estão registradas na sua AN Wallet. Toque em \"Reautorizar chaves\" e confirme na wallet.", "lerr");
                setStatus("err", "Reautorize na AN Wallet", "Chaves de mineração não propagadas — toque em Reautorizar");
                showReauthPrompt();
                toast("Reautorize as chaves de mineração na AN Wallet");
              }
              try { claimMiner.stop(); } catch(_) {}
              claiming = false;
              resolve();
            }
          });
        }

        // Se já propagado, vai direto
        if (saved.propagated) {
          log("✅ Propagacao confirmada — chamando get_reward()...", "lok");
          doGetReward();
          return;
        }

        // Não propagado: tenta propagar rapidamente antes do claim.
        // Após reconexão/limpeza de dados a propagação demora mais, então
        // usamos mais tentativas para dar tempo da rede registrar as chaves.
        log("🔄 Confirmando propagacao antes do get_reward()...", "linf");
        window.BeeSDK.ensure_mining_keys_propagated({
          client_config: { network: { endpoints: ENDPOINTS } },
          miner_address: saved.minerAddress,
          app_id: APP_ID,
          expected_owner_public: saved.publicKey,
          max_attempts: 30,
          interval_ms: 2000
        }).then(function() {
          saved.propagated = true; saveSaved();
          log("✅ Propagacao confirmada — chamando get_reward()...", "lok");
        }).catch(function(e) {
          log("⚠️ Propagacao falhou — tentando get_reward() mesmo assim...", "lwrn");
        }).finally(function() {
          doGetReward();
        });
      });
    }

    function handleEpochEnd(reason) {
      if (window._watchdogTimer) { clearInterval(window._watchdogTimer); window._watchdogTimer = null; }
      if (window._epochTimer)    { clearTimeout(window._epochTimer);     window._epochTimer    = null; }

      // FIX: guardar a instância que minerou — get_reward() precisa do contexto da sessão
      // NÃO chamar .stop() antes do get_reward() pois invalida o objeto WASM
      var claimMiner = miner;
      miner = null; mining = false;
      sessionStart = null; stopUptimeTimer();
      if (miningSwitch) miningSwitch.checked = true;
      setStatus("warn", "Coletando reward...", "Aguardando slashing period (~16s)");
      if (switchSub) switchSub.textContent = "Collecting reward...";
      if (tapSection) tapSection.classList.add("hidden");
      notifyMiningStatus();

      // Slashing period: aguardar ~16s antes do claim
      setTimeout(function() {
        if (!claimMiner) {
          log("⚠️ Instância do miner perdida — reward não pôde ser coletado", "lerr");
          scheduleRestart();
          return;
        }
        if (!saved.minerAddress || !saved.publicKey || !saved.secretKey) {
          log("⚠️ Missing credentials for get_reward()", "lerr");
          try { claimMiner.stop(); } catch(_) {}
          scheduleRestart();
          return;
        }
        log("💰 Calling get_reward() on the mining instance...", "linf");
        // Garante que a WebView está ativa (importante para PiP)
        if (window.AndroidBee && window.AndroidBee.resumeForClaim) {
          window.AndroidBee.resumeForClaim();
        }
        claimRewardSafe(claimMiner).finally(scheduleRestart);
      }, 16000);

      function scheduleRestart() {
        setStatus("warn", "Waiting for next epoch...", "Reiniciando em ~30s");
        if (switchSub) switchSub.textContent = "Waiting for next epoch...";
        setTimeout(function() {
          if (miningSwitch && miningSwitch.checked) startMining();
        }, 30000);
      }
    }

    miner.start(MINING_DURATION_MS, function(event) {
      cycles++;
      lastEventTime = Date.now();
      updateMetrics();

      var action = ""; var eventStr = "";
      try {
        eventStr = JSON.stringify(event);
        var parsed = (typeof event === "object") ? event : JSON.parse(event);
        action = parsed.action || parsed.type || "";
      } catch(_) { eventStr = String(event); }

      var evLog = "action=" + (action||"?") + " | " + eventStr.substring(0,200);
      // log("[Miner] " + evLog, "linf"); // suprimido
      window._lastMinerEvent = new Date().toLocaleTimeString("en-US") + " — " + evLog;

      // Se o SDK emitir evento de fim de epoch (não garante — mas tratamos)
      var epochEndActions = ["session_end","done","finished","complete","submit_session_root","computation_completed"];
      if (epochEndActions.indexOf(action) !== -1 && !epochDone) {
        epochDone = true;
        // epoch event suprimido
        handleEpochEnd(action);
      }

      if (action === "error" || action === "failed")    log("❌ " + eventStr.substring(0,120), "lerr");
      if (action === "status_updated")                  log("✅ Connected to Acki Nacki network", "lok");
      if (action === "tap_computed")                    log("◈ Action registered on network", "lok");
      if (action === "submit_session_root")             log("◈ Data submitted to blockchain", "lok");
    });

    mining = true; sessionStart = Date.now(); startUptimeTimer();
    // Sessão realmente iniciou — confirma o débito de WP (não reembolsa mais).
    _wpDebited = false;
    if (tapSection) tapSection.classList.remove("hidden");
    if (switchSub)  switchSub.textContent = "Mining NACKL ⚡";
    setStatus("on", "Mining NACKL ⚡", "Wallet: " + saved.walletName);
    updateMetrics();
    log("✅ Mining started — epoch active", "lok");
    toast("NACKL Mining started! ⚡");

    // ─── AUTO-TAP ────────────────────────────────────────────────────────
    // 100 taps por epoch de 5 min = 1 tap a cada 3s
    // Para imediatamente quando mining=false (stop ou fim de epoch)
    if (window._autoTapTimer) clearInterval(window._autoTapTimer);
    var autoTapCount = 0;
    var AUTO_TAP_TOTAL = 100;
    var AUTO_TAP_INTERVAL = Math.floor(MINING_DURATION_MS / AUTO_TAP_TOTAL); // ~3000ms
    window._autoTapTimer = setInterval(async function() {
      if (!mining || !miner) {
        clearInterval(window._autoTapTimer);
        window._autoTapTimer = null;
        return;
      }
      if (autoTapCount >= AUTO_TAP_TOTAL) {
        clearInterval(window._autoTapTimer);
        window._autoTapTimer = null;
        return;
      }
      try {
        await miner.add_tap(
          Math.floor(Math.random() * 300 + 50),
          Math.floor(Math.random() * 300 + 50)
        );
        autoTapCount++;
        window._tapCount = (window._tapCount || 0) + 1;
        if (autoTapCount % 10 === 0) {
          log("◈ Syncing with network... " + autoTapCount + "/" + AUTO_TAP_TOTAL, "linf");
        }
        // Animar botão TAP visualmente
        if (window._tapBtnAnim) window._tapBtnAnim();
      } catch(e) {
        log("⚠️ Sync error: " + e.message, "lwrn");
      }
    }, AUTO_TAP_INTERVAL);
    log("◈ Mining protocol active", "linf");
  }

  // ─── STOP ────────────────────────────────────────────────────────────────
  function stopMining() {
    // Caso o usuário desligue ainda na fase "aguardando epoch" (mining=false
    // mas WP já debitado): reembolsa e limpa o estado intermediário.
    if (!mining) {
      if (_wpDebited) {
        if (window._epochCheckTimer) { clearInterval(window._epochCheckTimer); window._epochCheckTimer = null; }
        try { if (miner) miner.stop(); } catch(_) {}
        miner = null;
        refundSessionWP("desligado antes de iniciar");
        if (switchSub) switchSub.textContent = "Turn on to resume";
        setStatus("on", "Bee ready", "Mining paused");
      }
      return;
    }
    if (window._watchdogTimer) { clearInterval(window._watchdogTimer); window._watchdogTimer = null; }
    if (window._autoTapTimer)  { clearInterval(window._autoTapTimer);  window._autoTapTimer  = null; }
    try { if (miner) miner.stop(); } catch(_) {}
    miner = null; mining = false; sessionStart = null; stopUptimeTimer();
    if (tapSection) tapSection.classList.add("hidden");
    if (switchSub)  switchSub.textContent = "Turn on to resume";
    setStatus("on", "Bee ready", "Mining paused");
    notifyMiningStatus();
    updateMetrics();
    log("Mining paused.", "lwrn");
  }

  // ─── TAP ─────────────────────────────────────────────────────────────────
  async function sendTap() {
    if (!mining || !miner) return;
    try {
      await miner.add_tap(
        Math.floor(Math.random()*300+50),
        Math.floor(Math.random()*300+50)
      );
      window._tapCount = (window._tapCount || 0) + 1;
      log("◈ Action registered (" + window._tapCount + ")", "lok");
      if (window._tapBtnAnim) window._tapBtnAnim();
    } catch(e) { log("Erro no tap: " + e.message, "lerr"); }
  }

  async function doFirstTap() {
    if (!miner) return;
    try {
      await miner.add_tap(
        Math.floor(Math.random()*300+50),
        Math.floor(Math.random()*300+50)
      );
      saved.firstTapDone = true; saveSaved();
      var ftc = byId("firstTapCard"); if (ftc) ftc.classList.add("hidden");
      log("✅ Protocol started!", "lok");
      await startMining();
    } catch(e) { log("Erro no primeiro tap: " + e.message, "lerr"); }
  }

  // ─── REAUTORIZAÇÃO DE CHAVES ─────────────────────────────────────────────
  // Reabre o deep link set-mining-keys para o usuário confirmar na AN Wallet,
  // sem apagar a sessão. Resolve o caso "rewards não caem após reconectar":
  // as chaves locais existem mas não estão propagadas na blockchain/wallet.
  async function reauthorizeKeys() {
    if (!wasmReady) { toast("Aguarde o SDK carregar..."); return; }
    if (!saved.walletName || !saved.publicKey || !saved.secretKey) {
      log("No local keys to reauthorize — complete the full setup.", "lerr");
      doReset();
      return;
    }
    try {
      log("🔁 Reopening set-mining-keys on AN Wallet...", "linf");
      var link = await buildManualDeepLink({
        appId: APP_ID,
        publicKey: saved.publicKey,
        secretKey: saved.secretKey,
        walletName: saved.walletName
      });
      openDeepLink(link);
      toast("Confirm your mining keys on AN Wallet");
      // Após reautorizar, tenta reconfirmar a propagação em background.
      saved.propagated = false; saveSaved();
      _propagateAttempts = 0;
      setTimeout(tryPropagateBackground, 5000);
    } catch (e) {
      log("Erro ao reautorizar: " + (e.message || String(e)), "lerr");
    }
  }
  window.reauthorizeKeys = reauthorizeKeys;

  function showReauthPrompt() {
    var btn = byId("btnReauth");
    if (btn) { btn.classList.remove("hidden"); return; }
    // Cria botão dinamicamente se o HTML não tiver um
    try {
      var host = byId("setupCard") && !byId("setupCard").classList.contains("hidden")
        ? byId("setupCard")
        : (logBox ? logBox.parentNode : document.body);
      btn = document.createElement("button");
      btn.id = "btnReauth";
      btn.className = "btn";
      btn.textContent = "🔁 Reauthorize keys on AN Wallet";
      btn.onclick = reauthorizeKeys;
      if (host) host.appendChild(btn);
    } catch(_) {}
  }

  // ─── RESET ───────────────────────────────────────────────────────────────
  function doReset() {
    if (!window.confirm("Reset Bee Engine? Keys will be deleted.")) return;
    stopMining(); clearSaved(); rawDeepLink = null; cycles = 0;
    if (walletInput)   walletInput.value = "";
    if (setupCard)     setupCard.classList.remove("hidden");
    if (btnOpenAgain)  btnOpenAgain.classList.add("hidden");
    if (btnReset)      btnReset.classList.add("hidden");
    if (btnSetup)      { btnSetup.disabled = false; btnSetup.textContent = "Start setup"; btnSetup.onclick = runSetup; }
    if (miningSwitch)  { miningSwitch.checked = false; miningSwitch.disabled = true; }
    if (tapSection)    tapSection.classList.add("hidden");
    var ftc = byId("firstTapCard"); if (ftc) ftc.classList.add("hidden");
    if (switchSub) switchSub.textContent = "Setup required";
    setStep(1); setStatus("warn", "Resetado", "Configure novamente");
    updateMetrics(); log("Setup reset.", "lwrn");
  }

  // ─── DIAGNÓSTICO ─────────────────────────────────────────────────────────
  window.toggleDiag = function() {
    var p = byId("diagPanel"); if (!p) return;
    p.classList.toggle("hidden");
    if (!p.classList.contains("hidden")) window.runDiag();
  };

  window.runDiag = async function() {
    function el(id) { return byId(id); }
    if (el("dBridge"))  el("dBridge").textContent  = window.AndroidBee ? "✅ Connected" : "❌ Not found";
    if (el("dSdk"))     el("dSdk").textContent     = typeof window.BeeSDK !== "undefined" ? "✅ Found" : "❌ Missing";
    if (el("dWasm"))    el("dWasm").textContent    = wasmReady ? "✅ Initialized" : "⏳ Waiting";
    if (el("dAddr"))    el("dAddr").textContent    = saved.minerAddress || "—";
    if (el("dMining"))  el("dMining").textContent  = mining ? "✅ YES" : "❌ NO";
    if (el("dCycles"))  el("dCycles").textContent  = String(cycles) + " eventos";
    if (el("dPropagated")) el("dPropagated").textContent = saved.propagated ? "✅ Yes" : "⚠️ Pending";
    if (el("dLastEvent"))  el("dLastEvent").textContent  = window._lastMinerEvent || "nenhum";

    // Verificar assets via bridge
    if (window.AndroidBee && window.AndroidBee.checkAssets && el("dAssets")) {
      try { el("dAssets").textContent = window.AndroidBee.checkAssets(); }
      catch(e) { el("dAssets").textContent = "erro: "+e.message; }
    }
    if (window.AndroidBee && window.AndroidBee.hasWasm && el("dHasWasm")) {
      try { el("dHasWasm").textContent = window.AndroidBee.hasWasm() ? "✅ Found" : "❌ Ausente!"; }
      catch(e) { el("dHasWasm").textContent = "erro: "+e.message; }
    }

    if (el("dCanStart")) {
      if (!miner) {
        el("dCanStart").textContent = "— (miner not created)";
      } else if (mining) {
        // Quando minerando, can_start() retorna false por design — sessão em andamento
        el("dCanStart").textContent = "false — normal (active session, epoch running ✅)";
      } else {
        try {
          var cs = miner.can_start();
          el("dCanStart").textContent = String(cs) + (cs ?  " ✅ ready to start" :  " ⚠️ no seeds available");
        }
        catch(e) { el("dCanStart").textContent = "Erro: "+e.message; }
      }
    }
    // Mambaboard
    if (el("dMamba")) {
      try {
        var dismissed = localStorage.getItem("wasp_mamba_dismissed");
        el("dMamba").textContent = dismissed ? "✅ Marked as activated by user" : "⚠️ Not confirmed — required for rewards";
      } catch(_) { el("dMamba").textContent = "—"; }
    }
    // tapSum — verificar contagem de taps
    if (el("dCanStart")) {
      var tapEl = byId("dTapSum");
      if (tapEl) tapEl.textContent = window._tapCount ? String(window._tapCount) + " taps" : "0 taps";
    }
  };

  // ─── CALLBACKS ANDROID ───────────────────────────────────────────────────
  window.onWalletReturn = function() {
    log("Retornou ao app. Toque em continuar se autorizou.", "lwrn");
  };

  window.onAppResume = function() {
    log("App voltou ao foco", "linf");
    if (!wasmReady || !saved.authorized || !saved.minerAddress) return;

// Auto-start no resume removido — usuario relliga manualmente
  };

  window.onAppPause = function() {
    log("App in background — miner continues if WebView is not suspended", "linf");
  };

  // Tenta coletar reward pendente de sessão anterior
  // Chama o callback ao terminar (com ou sem reward)
  async function tryClaimPendingReward(callback) {
    if (!wasmReady || !saved.minerAddress) { if (callback) callback(); return; }
    // FIX: não colidir com um claim já em andamento (guard claiming).
    if (claiming) {
      log("Claim already in progress — skipping pending reward check.", "linf");
      if (callback) callback();
      return;
    }
    var tmpMiner = null;
    claiming = true;
    try {
      // FIX: Miner.new() e async -- precisa de await ou tmpMiner sera uma Promise
      tmpMiner = await window.BeeSDK.Miner.new(
        ENDPOINTS, APP_ID, saved.minerAddress, saved.publicKey, saved.secretKey
      );
      log("Verificando reward pendente...", "linf");
      await tmpMiner.get_reward();
      log("get_reward (resume): executed ✅ -- check your AN Wallet", "lok");
      toast("Pending reward sent to wallet ✅");
      var el = byId("mReward"); if (el) el.textContent = "Sent ✅";
    } catch(e) {
      // Sem reward pendente -- normal apos epoch incompleto
      log("Sem reward pendente: " + (e&&e.message?e.message.substring(0,80):String(e)), "linf");
    } finally {
      try { if (tmpMiner) tmpMiner.stop(); } catch(_) {}
      claiming = false;
      if (callback) callback();
    }
  }

  // ─── TIMERS ──────────────────────────────────────────────────────────────
  function startUptimeTimer() { stopUptimeTimer(); uptimeTimer = setInterval(updateMetrics, 1000); }
  function stopUptimeTimer()  { if (uptimeTimer) { clearInterval(uptimeTimer); uptimeTimer = null; } }

  // ─── BIND EVENTS ─────────────────────────────────────────────────────────
  function bindEvents() {
    if (miningSwitch) {
      miningSwitch.addEventListener("change", function() {
        if (miningSwitch.checked) {
          // Verifica WP antes de qualquer coisa
          var wp = getWP();
          if (wp < WP_MINING_COST) {
            miningSwitch.checked = false;
            if (switchSub) switchSub.textContent = "Need " + WP_MINING_COST + " WP — earn at WP Central";
            setStatus("err", "WP insuficiente",
              "You have " + wp + " WP. Earn " + (WP_MINING_COST - wp) + " more WP to mine.");
            log("❌ No WP to mine. Balance: " + wp + "/" + WP_MINING_COST, "lerr");
            updateWpDisplay();
            // Abre central WP após 1.5s para facilitar
            setTimeout(function() {
              try { if (window.AndroidBee && AndroidBee.openCentralWP) AndroidBee.openCentralWP(); } catch(_) {}
            }, 1500);
            return;
          }
          // autoMine removido
          startMining();
        } else {
          // autoMine removido
          stopMining();
        }
      });
    }
    if (btnSetup)     btnSetup.onclick = runSetup;
    if (btnReset)     btnReset.addEventListener("click", doReset);
    if (btnTap)       btnTap.addEventListener("click", sendTap);
    var ftb = byId("btnFirstTap");
    if (ftb) ftb.addEventListener("click", doFirstTap);
    if (btnOpenAgain) {
      btnOpenAgain.addEventListener("click", function() {
        if (!rawDeepLink) return;
        openDeepLink(rawDeepLink);
      });
    }
    document.addEventListener("visibilitychange", function() {
      updateWpDisplay(); // Atualiza saldo WP sempre que painel fica visível
      // FIX: NÃO iniciar mineração automaticamente ao abrir o painel.
      // Antes, isso disparava startMining() (gastando WP) mesmo quando o
      // usuário havia desligado o switch de propósito. A mineração agora
      // só inicia por ação explícita no switch. Aqui apenas atualizamos
      // o texto de status para refletir o saldo atual.
      if (!document.hidden && saved.authorized && !mining) {
        var _wpV = getWP();
        if (switchSub) switchSub.textContent = _wpV >= WP_MINING_COST
          ? "Turn on to mine — 1 WP/min (" + WP_MINING_COST + " WP per session)"
          : "Need " + WP_MINING_COST + " WP to mine (you have " + _wpV + ")";
      }
    });
  }

  // ─── INIT ────────────────────────────────────────────────────────────────
  function init() {
    grabElements();
    loadSaved();
    updateMetrics();
    updateWpDisplay(); // Mostra saldo WP ao abrir o painel
    bindEvents();
    setStep(1);
    // Pequeno delay para garantir que o DOM e a bridge estão prontos
    setTimeout(loadSdk, 400);
  }

  // Expõe estado de mineração para o header/rodapé
  window.getMiningState = function() {
    return {
      mining: mining,
      wallet: (saved && saved.walletName) ? saved.walletName : "",
      tapCount: window._tapCount || 0,
      tapTotal: 100,
      epochCount: window._epochCount || 0,
      epochsPaid: getEpochsPaid()
    };
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }

})();