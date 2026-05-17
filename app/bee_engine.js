/**
 * bee_engine.js
 * Integração real com Bee Engine SDK da rede Acki Nacki.
 *
 * Fluxo:
 *  1. Carrega SDK (WASM servido pelo Android via intercepção)
 *  2. Usuário digita wallet_name → gera mining keys
 *  3. Abre AN Wallet via deep link para autorizar
 *  4. Aguarda propagação na blockchain (ensure_mining_keys_propagated)
 *  5. Cria Miner e inicia mineração
 *  6. add_tap() a cada toque do usuário
 */

(function () {

  /* ─── CONSTANTES ───────────────────────────────────────────── */

  const APP_ID    = "0x0000000000000000000000000000000000000000000000000000000000000001";
  const ENDPOINTS = ["https://shellnet.ackinacki.org/graphql"];

  // Chave para persistência (localStorage)
  const KEY_STATE = "wasp_bee_state_v2";

  /* ─── ESTADO ────────────────────────────────────────────────── */

  // O que é salvo no localStorage
  let saved = {
    walletName:    "",
    publicKey:     "",
    secretKey:     "",
    minerAddress:  "",
    authorized:    false   // true depois de ensure_mining_keys_propagated
  };

  // Runtime (não salvo)
  let sdk          = null;   // módulo WASM
  let sdkReady     = false;
  let miner        = null;
  let mining       = false;
  let setupRunning = false;
  let deepLink     = null;
  let cycles       = 0;
  let sessionStart = null;
  let uptimeTimer  = null;

  /* ─── ELEMENTOS DOM ─────────────────────────────────────────── */

  const $ = id => document.getElementById(id);

  const dotStatus     = $("dotStatus");
  const txtTitle      = $("txtStatusTitle");
  const txtDesc       = $("txtStatusDesc");
  const switchSub     = $("switchSub");
  const miningSwitch  = $("miningSwitch");
  const mEngine       = $("mEngine");
  const mCycles       = $("mCycles");
  const mUptime       = $("mUptime");
  const mWallet       = $("mWallet");
  const setupCard     = $("setupCard");
  const walletInput   = $("walletInput");
  const btnSetup      = $("btnSetup");
  const btnOpenAgain  = $("btnOpenWalletAgain");
  const btnReset      = $("btnReset");
  const tapSection    = $("tapSection");
  const btnTap        = $("btnTap");
  const logBox        = $("logBox");
  const n1=$("n1"); const n2=$("n2"); const n3=$("n3"); const n4=$("n4");

  /* ─── LOG ───────────────────────────────────────────────────── */

  function log(msg, cls = "") {
    const t = new Date().toLocaleTimeString("pt-BR");
    const line = document.createElement("div");
    if (cls) line.className = cls;
    line.textContent = `[${t}] ${msg}`;
    logBox.appendChild(line);
    while (logBox.children.length > 120) logBox.removeChild(logBox.firstChild);
    logBox.scrollTop = logBox.scrollHeight;
    console.log(`[Bee] ${msg}`);
  }

  window.clearLog = () => { logBox.innerHTML = ""; };

  /* ─── STATUS UI ─────────────────────────────────────────────── */

  function setStatus(cls, title, desc) {
    dotStatus.className = "dot " + cls;
    txtTitle.textContent = title;
    txtDesc.textContent  = desc;
  }

  function setStep(active) {
    [n1,n2,n3,n4].forEach((el,i) => {
      el.className = "step-num" + (i+1 < active ? " done" : i+1 === active ? " active" : "");
      el.textContent = i+1 < active ? "✓" : String(i+1);
    });
  }

  function updateMetrics() {
    mEngine.textContent = mining ? "Rodando" : (sdkReady ? "Pronta" : "Carregando");
    mCycles.textContent = String(cycles);
    mWallet.textContent = saved.walletName || "—";
    if (sessionStart) {
      const s = Math.floor((Date.now() - sessionStart) / 1000);
      const h = String(Math.floor(s/3600)).padStart(2,"0");
      const m = String(Math.floor((s%3600)/60)).padStart(2,"0");
      const sec = String(s%60).padStart(2,"0");
      mUptime.textContent = `${h}:${m}:${sec}`;
    } else {
      mUptime.textContent = "00:00:00";
    }
  }

  /* ─── PERSISTÊNCIA ──────────────────────────────────────────── */

  function loadSaved() {
    try {
      const raw = localStorage.getItem(KEY_STATE);
      if (raw) Object.assign(saved, JSON.parse(raw));
    } catch(_) {}
  }

  function saveSaved() {
    localStorage.setItem(KEY_STATE, JSON.stringify(saved));
  }

  /* ─── CARREGAR SDK ──────────────────────────────────────────── */

  /**
   * O SDK (WASM + JS) é servido pelo Android via shouldInterceptRequest.
   * O Android intercepta requisições para "https://bee.local/*" e serve
   * os arquivos de res/raw/.
   *
   * Aqui usamos dynamic import() para carregar o bundle ES module.
   */
  async function loadSdk() {
    setStatus("warn", "Carregando SDK...", "Inicializando Bee Engine WASM");
    log("Carregando SDK da Bee Engine...", "linf");

    try {
      // O Android intercepta essa URL e serve o bee_sdk_bg_js.js do res/raw
      sdk = await import("https://bee.local/bee_sdk_bg.js");

      // Carrega o WASM
      const wasmResp = await fetch("https://bee.local/bee_sdk_bg.wasm");
      if (!wasmResp.ok) throw new Error("WASM HTTP " + wasmResp.status);
      const wasmBytes = await wasmResp.arrayBuffer();

      log(`WASM carregado: ${(wasmBytes.byteLength / 1024 / 1024).toFixed(1)} MB`, "linf");

      // Monta imports do wasm-bindgen
      const bgImports = {};
      for (const k of Object.keys(sdk)) bgImports[k] = sdk[k];

      const { instance } = await WebAssembly.instantiate(wasmBytes, {
        "./bee_sdk_bg.js": bgImports
      });

      if (typeof sdk.__wbg_set_wasm !== "function")
        throw new Error("__wbg_set_wasm não encontrado no SDK");

      sdk.__wbg_set_wasm(instance.exports);

      if (typeof sdk.__wbindgen_init_externref_table === "function")
        sdk.__wbindgen_init_externref_table();

      if (typeof instance.exports.__wbindgen_start === "function")
        instance.exports.__wbindgen_start();

      sdkReady = true;
      log("SDK pronto ✅", "lok");
      onSdkReady();

    } catch (e) {
      log(`Erro ao carregar SDK: ${e.message}`, "lerr");
      setStatus("err", "Erro no SDK", e.message.substring(0, 80));
    }
  }

  function onSdkReady() {
    updateMetrics();

    if (saved.authorized && saved.walletName && saved.minerAddress) {
      // Já autorizado — pronto para minerar
      setStatus("on", "Bee pronta ✅", `Wallet: ${saved.walletName}`);
      setupCard.classList.add("hidden");
      miningSwitch.disabled = false;
      switchSub.textContent = "Ligue para iniciar mineração NACKL";
      btnReset.classList.remove("hidden");
      setStep(5); // todos feitos
      log(`Sessão restaurada. Wallet: ${saved.walletName}`, "lok");
    } else {
      setStatus("warn", "Configuração necessária", "Autorize a Bee Engine com sua AN Wallet");
      setupCard.classList.remove("hidden");
      setStep(1);
      if (saved.walletName) walletInput.value = saved.walletName;
    }
  }

  /* ─── SETUP: gerar chaves + abrir wallet ─────────────────────── */

  async function runSetup() {
    if (setupRunning) return;
    if (!sdkReady) { log("SDK ainda não carregado", "lwrn"); return; }

    const walletName = walletInput.value.trim();
    if (!walletName) {
      log("Digite seu wallet name primeiro", "lerr");
      walletInput.focus();
      return;
    }

    setupRunning = true;
    btnSetup.disabled = true;
    btnSetup.innerHTML = '<span class="spinner"></span>Gerando chaves...';

    try {
      setStep(2);
      log(`Gerando mining keys para APP_ID...`, "linf");

      const result = await sdk.gen_mining_keys(APP_ID);
      deepLink = result.deep_link;

      saved.walletName = walletName;
      saved.publicKey  = result.public;
      saved.secretKey  = result.secret;
      saveSaved();

      log(`Chaves geradas ✅`, "lok");
      log(`Public: ${result.public.substring(0,16)}...`, "linf");

      // Passo 3: abrir wallet
      setStep(3);
      btnSetup.innerHTML = '<span class="spinner"></span>Abrindo AN Wallet...';
      log(`Abrindo AN Wallet via deep link...`, "linf");

      openDeepLink(deepLink);

      btnSetup.textContent = "Aguardar retorno da Wallet";
      btnSetup.disabled = false;
      btnOpenAgain.classList.remove("hidden");

      // Botão principal agora confirma a autorização
      btnSetup.onclick = confirmAuthorization;

      log("Confirme na AN Wallet e volte aqui para continuar", "lwrn");
      setStatus("warn", "Aguardando AN Wallet", "Confirme o registro na carteira e toque em continuar");

    } catch (e) {
      log(`Erro no setup: ${e.message}`, "lerr");
      setStatus("err", "Erro", e.message.substring(0,80));
      btnSetup.disabled = false;
      btnSetup.textContent = "Tentar novamente";
    }

    setupRunning = false;
  }

  function openDeepLink(url) {
    log(`Deep link: ${url.substring(0,60)}...`, "linf");
    if (window.AndroidBee && typeof AndroidBee.openDeepLink === "function") {
      AndroidBee.openDeepLink(url);
    } else {
      // fallback: tenta abrir como link normal
      window.location.href = url;
    }
  }

  /* ─── CONFIRMAÇÃO PÓS-WALLET ─────────────────────────────────── */

  async function confirmAuthorization() {
    if (!sdkReady) return;

    btnSetup.disabled = true;
    btnSetup.innerHTML = '<span class="spinner"></span>Buscando endereço...';
    setStep(4);

    try {
      log(`Buscando miner address para wallet: ${saved.walletName}`, "linf");

      // Tenta até 12 vezes (2 min total)
      let minerAddr = null;
      for (let i = 1; i <= 12; i++) {
        try {
          log(`Tentativa ${i}/12...`, "linf");
          minerAddr = await sdk.get_miner_address_by_wallet_name({
            client_config: { network: { endpoints: ENDPOINTS } },
            wallet_name: saved.walletName
          });
          break;
        } catch (e) {
          log(`Falha ${i}: ${e.message}`, "lwrn");
          if (i < 12) await sleep(10000);
          else throw e;
        }
      }

      log(`Miner address: ${minerAddr}`, "lok");
      saved.minerAddress = minerAddr;
      saveSaved();

      btnSetup.innerHTML = '<span class="spinner"></span>Verificando blockchain...';
      log("Aguardando propagação das chaves na blockchain...", "linf");

      await sdk.ensure_mining_keys_propagated({
        client_config: { network: { endpoints: ENDPOINTS } },
        miner_address: minerAddr,
        app_id: APP_ID,
        expected_owner_public: saved.publicKey,
        max_attempts: 30,
        interval_ms: 2000
      });

      log("Chaves propagadas ✅", "lok");

      saved.authorized = true;
      saveSaved();

      // Primeiro tap de verificação
      log("Enviando primeiro tap de verificação...", "linf");
      await sendFirstTap(minerAddr);

      onAuthorized();

    } catch (e) {
      log(`Erro na confirmação: ${e.message}`, "lerr");
      setStatus("err", "Falha na autorização", "Toque em 'Tentar novamente'");
      btnSetup.disabled = false;
      btnSetup.textContent = "Tentar novamente";
      btnSetup.onclick = confirmAuthorization;
    }
  }

  async function sendFirstTap(minerAddr) {
    try {
      // Cria miner temporário para o primeiro tap
      const tmpMiner = await sdk.Miner.new(
        ENDPOINTS, APP_ID, minerAddr,
        saved.publicKey, saved.secretKey
      );
      await tmpMiner.add_tap(100, 100);
      log("Primeiro tap enviado ✅", "lok");
    } catch (e) {
      // Não é fatal — a mineração pode funcionar mesmo assim
      log(`Aviso no primeiro tap: ${e.message}`, "lwrn");
    }
  }

  function onAuthorized() {
    setStatus("on", "Bee autorizada ✅", `Wallet: ${saved.walletName}`);
    setupCard.classList.add("hidden");
    btnReset.classList.remove("hidden");
    miningSwitch.disabled = false;
    switchSub.textContent = "Ligue para iniciar mineração NACKL";
    setStep(5);
    log("Configuração concluída! Ligue a mineração.", "lok");
    updateMetrics();

    if (window.AndroidBee && typeof AndroidBee.toast === "function")
      AndroidBee.toast("Bee Engine autorizada! ✅");
  }

  /* ─── MINERAÇÃO ─────────────────────────────────────────────── */

  async function startMining() {
    if (!sdkReady || !saved.authorized) return;
    if (mining) return;

    try {
      log("Criando Miner...", "linf");
      setStatus("warn", "Iniciando...", "Conectando ao contrato Miner");

      miner = await sdk.Miner.new(
        ENDPOINTS, APP_ID,
        saved.minerAddress,
        saved.publicKey,
        saved.secretKey
      );

      const canStart = miner.can_start();
      log(`can_start() = ${canStart}`, "linf");

      if (!canStart) {
        log("Miner criado mas can_start()=false. Aguarde e tente novamente.", "lwrn");
        setStatus("warn", "Aguardando rede", "can_start() retornou false");
        miner = null;
        miningSwitch.checked = false;
        return;
      }

      // Sessão de 5 minutos, renova automaticamente
      const DURATION_MS = 5 * 60 * 1000;

      miner.start(DURATION_MS, (event) => {
        log(`[Miner] ${JSON.stringify(event)}`, "linf");
        cycles++;
        updateMetrics();

        // Auto-restart quando a sessão terminar
        if (event.type === "session_end" || event.type === "done") {
          log("Sessão encerrada. Reiniciando...", "lwrn");
          restartMining();
        }
      });

      mining = true;
      sessionStart = Date.now();

      startUptimeTimer();
      tapSection.classList.remove("hidden");

      setStatus("on", "Minerando NACKL ⚡", `Wallet: ${saved.walletName}`);
      mEngine.textContent = "Rodando";
      switchSub.textContent = "Mineração ativa";

      log("Mineração iniciada! ✅", "lok");

      if (window.AndroidBee && typeof AndroidBee.toast === "function")
        AndroidBee.toast("Mineração iniciada! ⚡");

    } catch (e) {
      log(`Erro ao iniciar mineração: ${e.message}`, "lerr");
      setStatus("err", "Erro na mineração", e.message.substring(0,80));
      miningSwitch.checked = false;
      miner = null;
    }
  }

  async function restartMining() {
    if (!mining) return;
    try {
      miner = null;
      await sleep(2000);
      await startMining();
    } catch(e) {
      log(`Erro ao reiniciar: ${e.message}`, "lerr");
    }
  }

  function stopMining() {
    if (!mining) return;
    try {
      if (miner) {
        miner.stop();
        miner = null;
      }
    } catch (e) {
      log(`Aviso ao parar: ${e.message}`, "lwrn");
    }
    mining = false;
    sessionStart = null;
    stopUptimeTimer();
    tapSection.classList.add("hidden");
    setStatus("on", "Bee pronta", "Mineração pausada");
    mEngine.textContent = "Parada";
    switchSub.textContent = "Ligue para retomar";
    log("Mineração parada.", "lwrn");
    updateMetrics();
  }

  /* ─── TAP ───────────────────────────────────────────────────── */

  window.doTap = async function() {
    if (!mining || !miner) return;
    try {
      await miner.add_tap(
        Math.floor(Math.random() * 300 + 50),
        Math.floor(Math.random() * 300 + 50)
      );
      log("Tap adicionado ⚡", "lok");
      btnTap.style.transform = "scale(0.93)";
      setTimeout(() => { btnTap.style.transform = ""; }, 120);
    } catch (e) {
      log(`Tap erro: ${e.message}`, "lerr");
    }
  };

  if (btnTap) btnTap.onclick = window.doTap;

  /* ─── RESET ─────────────────────────────────────────────────── */

  function doReset() {
    if (!confirm("Resetar toda a configuração da Bee Engine?")) return;
    stopMining();
    saved = { walletName:"", publicKey:"", secretKey:"", minerAddress:"", authorized:false };
    saveSaved();
    walletInput.value = "";
    deepLink = null;
    setupCard.classList.remove("hidden");
    btnReset.classList.add("hidden");
    btnOpenAgain.classList.add("hidden");
    tapSection.classList.add("hidden");
    miningSwitch.disabled = true;
    miningSwitch.checked  = false;
    btnSetup.disabled = false;
    btnSetup.textContent = "Iniciar configuração";
    btnSetup.onclick = runSetup;
    setStep(1);
    setStatus("warn", "Resetado", "Configure novamente");
    log("Estado resetado.", "lwrn");
  }

  /* ─── DIAGNÓSTICO ───────────────────────────────────────────── */

  window.toggleDiag = function() {
    const p = $("diagPanel");
    p.classList.toggle("hidden");
    if (!p.classList.contains("hidden")) runDiag();
  };

  window.runDiag = function() {
    $("dBridge").textContent  = window.AndroidBee ? "Conectada ✅" : "Não encontrada ❌";
    $("dSdk").textContent     = sdkReady ? "Carregado ✅" : "Não carregado ❌";
    $("dAddr").textContent    = saved.minerAddress || "—";
    try {
      $("dCanStart").textContent = (mining && miner) ? String(miner.can_start()) : "—";
    } catch(e) { $("dCanStart").textContent = `Erro: ${e.message}`; }
  };

  /* ─── UPTIME ────────────────────────────────────────────────── */

  function startUptimeTimer() {
    stopUptimeTimer();
    uptimeTimer = setInterval(updateMetrics, 1000);
  }
  function stopUptimeTimer() {
    if (uptimeTimer) { clearInterval(uptimeTimer); uptimeTimer = null; }
  }

  /* ─── UTILS ─────────────────────────────────────────────────── */

  function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

  /* ─── EVENTOS ───────────────────────────────────────────────── */

  miningSwitch.addEventListener("change", () => {
    if (miningSwitch.checked) startMining();
    else stopMining();
  });

  btnSetup.addEventListener("click", runSetup);
  btnOpenAgain.addEventListener("click", () => { if (deepLink) openDeepLink(deepLink); });
  btnReset.addEventListener("click", doReset);

  // Retorno ao app após AN Wallet ou navegação
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && setupRunning === false && deepLink && !saved.authorized) {
      log("App retomado. Verificando autorização...", "linf");
    }
    
    // NOVO: Retoma mineração se estava ativa e foi pausada por navegação
    if (!document.hidden && mining === false && saved.authorized && miningSwitch.checked) {
      log("App retomado. Retomando mineração...", "linf");
      startMining();
    }
    
    // Pausa o timer de uptime quando tira foco
    if (document.hidden && mining) {
      stopUptimeTimer();
    }
  });

  // Pause/resume
  window.pauseBeeLoops  = function() { stopUptimeTimer(); };
  window.resumeBeeLoops = function() { if (mining) startUptimeTimer(); };

  /* ─── INIT ──────────────────────────────────────────────────── */

  function init() {
    loadSaved();
    updateMetrics();
    loadSdk();
  }

  document.addEventListener("DOMContentLoaded", init);

})();
