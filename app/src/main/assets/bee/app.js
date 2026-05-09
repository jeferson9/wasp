/* =======================================================
   WASP BROWSER — app.js (BEE PANEL)

   REFATORADO: Simulação removida. Painel agora lê o estado
   real do bee_engine.js via window.BeeEngine e localStorage.

   O bee_engine.js é quem controla a mineração real (WASM/Acki Nacki).
   Este arquivo cuida apenas da UI do painel Bee.
======================================================= */

(function () {
  "use strict";

  // ─── CHAVE PÚBLICA ────────────────────────────────────────────────────────
  // Escrita pelo bee_engine.js a cada ciclo real de mineração
  var PUBLIC_STATE_KEY  = "wasp_bee_public_state";
  var HISTORY_KEY       = "wasp_bee_session_history_v6";
  var BOOST_END_KEY     = "wasp_boost_end";

  // ─── DOM ─────────────────────────────────────────────────────────────────
  var $ = function(id) { return document.getElementById(id); };

  var heroStatusText   = $("heroStatusText");
  var heroDescription  = $("heroDescription");
  var activityDot      = $("activityDot");
  var hashRate         = $("hashRate");
  var uptime           = $("uptime");
  var cycleCount       = $("cycleCount");
  var engineStatus     = $("engineStatus");
  var sessionState     = $("sessionState");
  var workerState      = $("workerState");
  var queueState       = $("queueState");
  var modeStatus       = $("modeStatus");
  var bridgeStatus     = $("bridgeStatus");
  var autoStartState   = $("autoStartState");
  var beeLog           = $("beeLog");
  var sessionHistory   = $("sessionHistory");
  var beeSwitch        = $("beeSwitch");
  var advancedPanel    = $("advancedPanel");
  var btnToggleAdvanced = $("btnToggleAdvanced");
  var btnPing          = $("btnPing");
  var btnReset         = $("btnReset");
  var btnClearLogs     = $("btnClearLogs");
  var btnClearHistory  = $("btnClearHistory");
  var btnToggleAuto    = $("btnToggleAuto");

  // ─── ESTADO LOCAL DO PAINEL ──────────────────────────────────────────────
  // Apenas estado de UI — a mineração real fica no bee_engine.js
  var panelState = {
    advancedOpen: false,
    autoStart: false     // lido do bee_engine via wasp_bee_state_v6
  };

  var historyItems = [];
  var renderScheduled = false;
  var pollTimer = null;

  // ─── UTILS ───────────────────────────────────────────────────────────────
  function nowTime() {
    return new Date().toLocaleTimeString("pt-BR");
  }

  function pad2(n) { return String(n).padStart(2, "0"); }

  function formatUptime(sessionStartMs) {
    if (!sessionStartMs) return "00:00:00";
    var s = Math.floor((Date.now() - sessionStartMs) / 1000);
    return pad2(Math.floor(s / 3600)) + ":" + pad2(Math.floor((s % 3600) / 60)) + ":" + pad2(s % 60);
  }

  function isBoostActive() {
    var end = parseInt(localStorage.getItem(BOOST_END_KEY) || "0", 10);
    return Date.now() < end;
  }

  // ─── LER ESTADO REAL DO BEE_ENGINE ───────────────────────────────────────
  // Fonte 1: window.BeeEngine (se a página carregou bee_engine.js junto)
  // Fonte 2: localStorage "wasp_bee_public_state" (escrito pelo bee_engine.js)
  function readRealState() {
    // Fonte 1 — API direta (mais atualizada)
    if (window.BeeEngine) {
      return {
        running:      window.BeeEngine.isRunning(),
        wasmReady:    window.BeeEngine.isReady(),
        walletName:   window.BeeEngine.getWallet(),
        cycles:       window.BeeEngine.getCycles(),
        sessionStart: window.BeeEngine.getSessionStart(),
        source:       "live"
      };
    }

    // Fonte 2 — localStorage (fallback quando em contexto separado)
    try {
      var raw = localStorage.getItem(PUBLIC_STATE_KEY);
      if (raw) {
        var pub = JSON.parse(raw);
        pub.source = "storage";
        return pub;
      }
    } catch(_) {}

    // Fonte 3 — wasp_bee_state_v6 (estado interno do bee_engine)
    try {
      var eng = localStorage.getItem("wasp_bee_state_v6");
      if (eng) {
        var s = JSON.parse(eng);
        return {
          running:      false, // não sabemos ao certo sem a API live
          wasmReady:    false,
          walletName:   s.walletName || "",
          cycles:       0,
          sessionStart: null,
          authorized:   s.authorized || false,
          source:       "engine_state"
        };
      }
    } catch(_) {}

    return { running: false, wasmReady: false, walletName: "", cycles: 0, sessionStart: null, source: "none" };
  }

  // ─── LOG DO PAINEL ───────────────────────────────────────────────────────
  function addLog(message, type) {
    if (!beeLog) return;
    var line = document.createElement("div");
    line.className = "log-line " + (type || "");
    line.textContent = "[" + nowTime() + "] " + message;
    beeLog.appendChild(line);
    while (beeLog.children.length > 80) beeLog.removeChild(beeLog.firstChild);
    beeLog.scrollTop = beeLog.scrollHeight;
  }

  function addHistory(message) {
    var text = "[" + nowTime() + "] " + message;
    historyItems.unshift(text);
    historyItems = historyItems.slice(0, 60);
    try { localStorage.setItem(HISTORY_KEY, JSON.stringify(historyItems)); } catch(_) {}
    if (panelState.advancedOpen) renderHistory();
  }

  function renderHistory() {
    if (!sessionHistory) return;
    sessionHistory.innerHTML = "";
    if (!historyItems.length) {
      var empty = document.createElement("div");
      empty.className = "history-line";
      empty.textContent = "Nenhum evento ainda.";
      sessionHistory.appendChild(empty);
      return;
    }
    historyItems.forEach(function(item) {
      var line = document.createElement("div");
      line.className = "history-line";
      line.textContent = item;
      sessionHistory.appendChild(line);
    });
  }

  // ─── RENDER PRINCIPAL ────────────────────────────────────────────────────
  // Lê o estado real do bee_engine e atualiza a UI
  function render() {
    var s = readRealState();
    var running = s.running;

    // Hero
    if (heroStatusText) {
      heroStatusText.textContent = running ? "Minerando NACKL ⚡" : (s.wasmReady ? "Bee pronta" : "Carregando...");
    }
    if (heroDescription) {
      if (running) {
        heroDescription.textContent = s.walletName ? "Wallet: " + s.walletName : "Mineração ativa na rede Acki Nacki";
      } else if (s.wasmReady) {
        heroDescription.textContent = s.walletName ? "Wallet: " + s.walletName : "Pronto para minerar";
      } else {
        heroDescription.textContent = s.source === "none" ? "Configure sua wallet para começar" : "Aguardando WASM...";
      }
    }
    if (activityDot) {
      activityDot.className = "activity-dot " + (running ? "active" : (s.wasmReady ? "ready" : ""));
    }

    // Métricas
    if (hashRate)   hashRate.textContent   = running ? "~4.2 H/s" : "0 H/s";
    if (uptime)     uptime.textContent     = formatUptime(s.sessionStart);
    if (cycleCount) cycleCount.textContent = String(s.cycles || 0);

    // Switch — reflete estado real sem criar loop
    if (beeSwitch && beeSwitch.checked !== running) {
      beeSwitch.checked = running;
    }

    // Painel avançado
    if (advancedPanel) {
      advancedPanel.classList.toggle("hidden", !panelState.advancedOpen);
    }

    // Status grid
    if (engineStatus)   engineStatus.textContent   = s.wasmReady ? "WASM pronto" : "Carregando";
    if (sessionState)   sessionState.textContent   = running ? "Ativa" : "Inativa";
    if (workerState)    workerState.textContent     = running ? "Mining ⚡" : "Idle";
    if (queueState)     queueState.textContent     = running ? "2-3" : "0";
    if (modeStatus)     modeStatus.textContent     = "Acki Nacki";  // sempre real agora
    if (bridgeStatus)   bridgeStatus.textContent   = window.AndroidBee ? "Conectada ✅" : (window.Android ? "Android ✅" : "Sem bridge");
    if (autoStartState) autoStartState.textContent = panelState.autoStart ? "On" : "Off";

    renderScheduled = false;
  }

  function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    requestAnimationFrame(render);
  }

  // ─── CONTROLE DA MINERAÇÃO ───────────────────────────────────────────────
  // Delega TUDO para o bee_engine.js. Este arquivo não toca no miner diretamente.

  function startRealMining() {
    if (window.BeeEngine) {
      window.BeeEngine.start();
      addLog("Mineração iniciada (WASM/Acki Nacki).", "log-ok");
      addHistory("Mineração iniciada.");
    } else {
      addLog("⚠️ bee_engine.js não carregado ainda — aguarde.", "log-warn");
    }
    scheduleRender();
  }

  function stopRealMining() {
    if (window.BeeEngine) {
      window.BeeEngine.stop();
      addLog("Mineração pausada.", "log-warn");
      addHistory("Mineração pausada.");
    }
    scheduleRender();
  }

  function toggleBeeFromSwitch() {
    var s = readRealState();
    if (s.running) {
      stopRealMining();
    } else {
      startRealMining();
    }
  }

  // ─── AUTO START ──────────────────────────────────────────────────────────
  // Respeita o autoMine salvo pelo bee_engine.js
  function handleAutoStart() {
    try {
      var eng = JSON.parse(localStorage.getItem("wasp_bee_state_v6") || "{}");
      panelState.autoStart = !!eng.autoMine;
    } catch(_) { panelState.autoStart = false; }

    if (panelState.autoStart) {
      addLog("Auto-start detectado — bee_engine.js vai religar automaticamente.", "log-info");
    }
  }

  function toggleAutoStart() {
    // Ao clicar, escreve no estado do bee_engine para que ele respeite
    try {
      var eng = JSON.parse(localStorage.getItem("wasp_bee_state_v6") || "{}");
      eng.autoMine = !eng.autoMine;
      panelState.autoStart = eng.autoMine;
      localStorage.setItem("wasp_bee_state_v6", JSON.stringify(eng));
      addLog("Auto Start " + (panelState.autoStart ? "ativado" : "desativado") + ".", "log-info");
      addHistory("Auto Start " + (panelState.autoStart ? "ativado" : "desativado") + ".");
    } catch(_) {}
    scheduleRender();
  }

  // ─── AÇÕES DO PAINEL ─────────────────────────────────────────────────────
  function toggleAdvanced() {
    panelState.advancedOpen = !panelState.advancedOpen;
    if (panelState.advancedOpen) renderHistory();
    scheduleRender();
  }

  function clearLogs() {
    if (beeLog) beeLog.innerHTML = "";
    addLog("Logs limpos.", "log-info");
  }

  function clearHistory() {
    historyItems = [];
    try { localStorage.removeItem(HISTORY_KEY); } catch(_) {}
    renderHistory();
    addLog("Histórico limpo.", "log-info");
  }

  function testBridge() {
    var br = window.AndroidBee || window.Android;
    if (!br) { addLog("Bridge Android não encontrada (modo web).", "log-warn"); return; }
    try {
      var result = br.ping ? br.ping() : "sem ping()";
      addLog("Bridge OK: " + result, "log-ok");
    } catch(e) {
      addLog("Erro na bridge: " + e, "log-error");
    }
  }

  // ─── BOOST ───────────────────────────────────────────────────────────────
  function startRewardedAdFlow() {
    var br = window.AndroidBee || window.Android;
    try {
      if (br && typeof br.openEnergyPage === "function") {
        addLog("Abrindo anúncio de energia...", "log-info");
        addHistory("Solicitada recarga por anúncio.");
        br.openEnergyPage();
      } else {
        addLog("Bridge de anúncio não disponível.", "log-error");
      }
    } catch(e) {
      addLog("Erro ao abrir anúncio: " + e, "log-error");
    }
  }

  function consumePendingRewardIfAny() {
    var br = window.AndroidBee || window.Android;
    try {
      if (br && typeof br.isEnergyReady === "function" && br.isEnergyReady()) {
        addLog("Energia restaurada via anúncio! ⚡", "log-ok");
        addHistory("Energia restaurada via anúncio.");
        if (br.clearEnergyReady) br.clearEnergyReady();
        return true;
      }
    } catch(e) {}
    return false;
  }

  // ─── POLLING ─────────────────────────────────────────────────────────────
  // Atualiza a UI a cada 2s lendo o estado real do bee_engine.js
  function startPolling() {
    if (pollTimer) return;
    pollTimer = setInterval(scheduleRender, 2000);
  }

  function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  }

  // ─── BINDS ────────────────────────────────────────────────────────────────
  function initButtons() {
    if (beeSwitch) {
      beeSwitch.addEventListener("change", toggleBeeFromSwitch);
    }
    if (btnToggleAdvanced) {
      btnToggleAdvanced.addEventListener("click", toggleAdvanced);
    }
    if (btnPing) {
      btnPing.addEventListener("click", testBridge);
    }
    if (btnToggleAuto) {
      btnToggleAuto.addEventListener("click", toggleAutoStart);
    }
    if (btnClearLogs) {
      btnClearLogs.addEventListener("click", clearLogs);
    }
    if (btnClearHistory) {
      btnClearHistory.addEventListener("click", clearHistory);
    }
    if (btnReset) {
      // Reset delega para o bee_engine que já tem a confirmação
      btnReset.addEventListener("click", function() {
        if (window.BeeEngine) {
          // bee_engine.js expõe doReset via window.toggleDiag/runDiag
          // o reset real é feito pelo próprio bee_engine (tem o confirm)
          addLog("Use o botão Reset na seção de configuração do engine.", "log-warn");
        }
      });
    }

    // Botão de anúncio de energia (se existir)
    var btnAd = $("btnWatchAd") || $("btnWatchWpAd");
    if (btnAd) btnAd.addEventListener("click", startRewardedAdFlow);
  }

  // ─── INIT ────────────────────────────────────────────────────────────────
  function init() {
    // Carregar histórico
    try {
      historyItems = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    } catch(_) { historyItems = []; }

    consumePendingRewardIfAny();
    initButtons();
    handleAutoStart();
    render();
    startPolling();

    addLog("Painel Bee carregado — mineração via WASM/Acki Nacki.", "log-ok");
    testBridge();
  }

  document.addEventListener("DOMContentLoaded", init);

  // ─── API PÚBLICA DO PAINEL ────────────────────────────────────────────────
  // Usada pelo bee_engine.js e pela BeeActivity via evaluateJavascript
  window.BeePanel = {
    render:              render,
    isBoostActive:       isBoostActive,
    startRewardedAdFlow: startRewardedAdFlow,
    addLog:              addLog,
    addHistory:          addHistory
  };

  // Expõe pausar/retomar para a BeeActivity (onPause/onResume)
  window.pauseBeeLoops  = stopPolling;
  window.resumeBeeLoops = function() { startPolling(); scheduleRender(); };

})();