const APP_ID =
  "0x0000000000000000000000000000000000000000000000000000000000000001";

const ENDPOINTS = ["https://shellnet.ackinacki.org/graphql"];

const statusEl = document.getElementById("status");
const logEl = document.getElementById("log");
const walletInput = document.getElementById("walletNameInput");

let BeeBg = null;
let beeReady = false;
let wasmReady = false;
let beeStarted = false;

let lastGenKeysResult = null;
let lastWalletName = "";
let lastMinerAddress = null;
let minerInstance = null;
let miningActive = false;

// ─────────────────────────────────────────────
// Utils
// ─────────────────────────────────────────────

function log(msg) {
  console.log(msg);
  if (logEl) {
    logEl.textContent += "\n" + msg;
    logEl.scrollTop = logEl.scrollHeight;
  }
}

function clearLog() {
  if (logEl) logEl.textContent = "";
}

function setStatus(msg) {
  if (statusEl) statusEl.textContent = msg;
  log("STATUS: " + msg);
}

function safeErrorMessage(e) {
  if (!e) return "Erro desconhecido";
  if (typeof e === "string") return e;
  if (e.message) return e.message;
  try {
    return JSON.stringify(e);
  } catch {
    return String(e);
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function getWalletName() {
  return walletInput ? walletInput.value.trim() : "";
}

function isTemporaryError(e) {
  const msg = safeErrorMessage(e).toLowerCase();
  return (
    msg.includes("not active") ||
    msg.includes("failed to get miner address") ||
    msg.includes("indexer") ||
    msg.includes("timeout") ||
    msg.includes("network")
  );
}

function androidToast(msg) {
  try {
    if (window.AndroidBee && typeof AndroidBee.toast === "function") {
      AndroidBee.toast(msg);
      return true;
    }
  } catch (e) {
    log("Aviso toast bridge: " + safeErrorMessage(e));
  }
  return false;
}

function openUrl(url) {
  try {
    log("openUrl = " + url);

    if (window.AndroidBee && typeof AndroidBee.openExternalUrl === "function") {
      AndroidBee.openExternalUrl(url);
      return;
    }

    if (window.AndroidBee && typeof AndroidBee.openExternal === "function") {
      AndroidBee.openExternal(url);
      return;
    }

    // fallback
    window.location.href = url;
  } catch (e) {
    log("ERRO openUrl: " + safeErrorMessage(e));
  }
}

// ─────────────────────────────────────────────
// Init SDK
// ─────────────────────────────────────────────

async function initBeeSdkIfNeeded() {
  if (beeReady && wasmReady && BeeBg) {
    log("SDK já inicializado");
    return BeeBg;
  }

  setStatus("Inicializando Bee SDK...");
  log("Importando bee_sdk_bg.js...");

  BeeBg = await import("https://bee.local/bee_sdk_bg.js");
  log("Import ok");

  const wasmResp = await fetch("https://bee.local/bee_sdk_bg.wasm");
  if (!wasmResp.ok) {
    throw new Error("Falha ao buscar wasm: HTTP " + wasmResp.status);
  }

  const wasmBytes = await wasmResp.arrayBuffer();
  log("WASM carregado: " + wasmBytes.byteLength + " bytes");

  const bgImports = {};
  for (const key of Object.keys(BeeBg)) {
    bgImports[key] = BeeBg[key];
  }

  const { instance } = await WebAssembly.instantiate(wasmBytes, {
    "./bee_sdk_bg.js": bgImports
  });

  if (typeof BeeBg.__wbg_set_wasm !== "function") {
    throw new Error("__wbg_set_wasm não encontrado");
  }

  BeeBg.__wbg_set_wasm(instance.exports);

  if (typeof BeeBg.__wbindgen_init_externref_table === "function") {
    BeeBg.__wbindgen_init_externref_table();
  }

  if (typeof instance.exports.__wbindgen_start === "function") {
    instance.exports.__wbindgen_start();
  }

  log(
    "Funções: gen_mining_keys=" + typeof BeeBg.gen_mining_keys +
    " ensure_mining_keys_propagated=" + typeof BeeBg.ensure_mining_keys_propagated +
    " get_miner_address_by_wallet_name=" + typeof BeeBg.get_miner_address_by_wallet_name +
    " Miner=" + typeof BeeBg.Miner
  );

  beeReady = true;
  wasmReady = true;
  setStatus("SDK pronto ✅");
  return BeeBg;
}

// ─────────────────────────────────────────────
// PASSO 1: gerar keys
// ─────────────────────────────────────────────

window.startBee = async function () {
  try {
    if (beeStarted) {
      log("Bee já iniciado — use Reset para recomeçar");
      return;
    }

    await initBeeSdkIfNeeded();

    setStatus("Gerando keys...");
    log("Chamando gen_mining_keys(APP_ID)...");

    const result = await BeeBg.gen_mining_keys(APP_ID);
    lastGenKeysResult = result;

    log("gen_mining_keys OK");
    log("public    = " + result.public);
    log("secret    = " + (result.secret ? "[ok]" : "[vazio]"));
    log("deep_link = " + result.deep_link);

    beeStarted = true;
    setStatus("Keys geradas ✅ — informe wallet_name e clique Conectar Wallet");
  } catch (e) {
    setStatus("Erro ao gerar keys");
    log("ERRO startBee: " + safeErrorMessage(e));
    if (e?.stack) log(e.stack);
  }
};

// ─────────────────────────────────────────────
// PASSO 2: conectar wallet
// VOLTANDO AO DEEP LINK ORIGINAL DA BEE
// ─────────────────────────────────────────────

window.connectWallet = function () {
  try {
    if (!lastGenKeysResult) {
      setStatus("Execute Start Bee primeiro");
      return;
    }

    const walletName = getWalletName();
    if (!walletName) {
      setStatus("Informe o wallet_name");
      log("wallet_name vazio");
      return;
    }

    const deepLink = lastGenKeysResult.deep_link;
    if (!deepLink) {
      setStatus("deep_link ausente");
      log("deep_link não encontrado");
      return;
    }

    // Mantemos o deep_link original da Bee
    // e apenas tentamos injetar wallet_name se o payload permitir.
    let deepLinkFinal = deepLink;

    try {
      const url = new URL(deepLink);
      const payloadRaw = url.searchParams.get("payload");

      if (payloadRaw) {
        const payload = JSON.parse(atob(payloadRaw));
        payload.wallet_name = walletName;
        url.searchParams.set("payload", btoa(JSON.stringify(payload)));
        deepLinkFinal = url.toString();
        log("wallet_name injetado no payload ✅");
      } else {
        log("deep_link sem payload — usando original");
      }
    } catch (e) {
      log("Aviso: não foi possível injetar wallet_name: " + safeErrorMessage(e));
      deepLinkFinal = deepLink;
    }

    log("deep_link final = " + deepLinkFinal);
    setStatus("Abrindo wallet...");

    openUrl(deepLinkFinal);
    androidToast("Confirme a conexão no AN Wallet");
  } catch (e) {
    setStatus("Erro ao abrir wallet");
    log("ERRO connectWallet: " + safeErrorMessage(e));
    if (e?.stack) log(e.stack);
  }
};

// ─────────────────────────────────────────────
// PASSO 3: continuar após confirmar na wallet
// ─────────────────────────────────────────────

window.continueBeeSetup = async function () {
  try {
    if (!BeeBg || !beeReady) {
      setStatus("Execute Start Bee primeiro");
      return;
    }

    if (!lastGenKeysResult) {
      setStatus("Execute Start Bee primeiro");
      return;
    }

    const walletName = getWalletName();
    if (!walletName) {
      setStatus("Informe o wallet_name");
      return;
    }

    lastWalletName = walletName;
    log("wallet_name = " + walletName);

    setStatus("Obtendo miner address...");
    log("Chamando get_miner_address_by_wallet_name...");

    let address = null;

    for (let i = 1; i <= 12; i++) {
      try {
        log("Tentativa " + i + "/12...");
        address = await BeeBg.get_miner_address_by_wallet_name({
          client_config: { network: { endpoints: ENDPOINTS } },
          wallet_name: walletName
        });
        log("miner_address = " + address);
        break;
      } catch (e) {
        log("Falha " + i + ": " + safeErrorMessage(e));

        if (!isTemporaryError(e) || i === 12) {
          throw e;
        }

        log("Aguardando 2500ms...");
        await sleep(2500);
      }
    }

    lastMinerAddress = address;

    setStatus("Propagando mining keys...");
    log("Chamando ensure_mining_keys_propagated...");
    log("miner_address = " + lastMinerAddress);

    await BeeBg.ensure_mining_keys_propagated({
      client_config: { network: { endpoints: ENDPOINTS } },
      miner_address: lastMinerAddress,
      app_id: APP_ID,
      expected_owner_public: lastGenKeysResult.public,
      max_attempts: 30,
      interval_ms: 2000
    });

    log("ensure_mining_keys_propagated OK ✅");

    setStatus("Autorizado ✅ — iniciando mineração...");
    androidToast("Bee autorizado!");

    await startMining();
  } catch (e) {
    setStatus("Erro: " + safeErrorMessage(e).substring(0, 80));
    log("ERRO continueBeeSetup: " + safeErrorMessage(e));
    if (e?.stack) log(e.stack);
  }
};

// ─────────────────────────────────────────────
// Mineração
// ─────────────────────────────────────────────

async function startMining() {
  try {
    if (!lastMinerAddress) {
      log("miner_address ausente");
      return;
    }

    if (!lastGenKeysResult?.public || !lastGenKeysResult?.secret) {
      log("keys ausentes");
      return;
    }

    if (miningActive) {
      log("Mineração já ativa");
      return;
    }

    log("Criando Miner...");
    log("Miner.new(endpoints, app_id, miner_address, public_key, secret_key)");

    minerInstance = await BeeBg.Miner.new(
      ENDPOINTS,
      APP_ID,
      lastMinerAddress,
      lastGenKeysResult.public,
      lastGenKeysResult.secret
    );

    log("Miner criado ✅");

    const canStart = minerInstance.can_start();
    log("can_start = " + canStart);

    if (!canStart) {
      setStatus("Miner criado mas can_start() = false");
      log("Tente aguardar alguns segundos e chamar continueBeeSetup novamente");
      return;
    }

    const DURATION_MS = 300000;
    log("Iniciando mineração por " + (DURATION_MS / 60000) + " minutos...");

    minerInstance.start(DURATION_MS, (event) => {
      log("MINER EVENT: " + JSON.stringify(event));
    });

    miningActive = true;
    setStatus("Mineração ativa ✅");
    log("Mineração iniciada com sucesso!");
    androidToast("Mineração iniciada!");
  } catch (e) {
    setStatus("Erro ao iniciar mineração");
    log("ERRO startMining: " + safeErrorMessage(e));
    if (e?.stack) log(e.stack);
  }
}

window.stopMining = function () {
  try {
    if (!minerInstance) {
      log("Nenhum Miner ativo");
      return;
    }

    minerInstance.stop();
    miningActive = false;
    setStatus("Mineração parada");
    log("miner.stop() ok");
  } catch (e) {
    log("ERRO stopMining: " + safeErrorMessage(e));
  }
};

// ─────────────────────────────────────────────
// Debug / utilitários
// ─────────────────────────────────────────────

window.beePing = function () {
  log("Ping JS ok ✅");
  setStatus("Ping JS ok");
};

window.beeAndroid = function () {
  try {
    if (window.AndroidBee) {
      log("Bridge Android encontrada ✅");
      setStatus("Bridge Android encontrada");
      androidToast("Bridge ok");
    } else {
      log("Bridge Android não encontrada");
      setStatus("Bridge Android não encontrada");
    }
  } catch (e) {
    log("ERRO beeAndroid: " + safeErrorMessage(e));
  }
};

window.debugWallet = function () {
  try {
    if (window.AndroidBee && typeof AndroidBee.listInstalledPackages === "function") {
      log("=== Packages relacionados ===\n" + AndroidBee.listInstalledPackages());
    }

    if (window.AndroidBee && typeof AndroidBee.isWalletInstalled === "function") {
      log("isWalletInstalled = " + AndroidBee.isWalletInstalled());
    }
  } catch (e) {
    log("Erro debugWallet: " + safeErrorMessage(e));
  }
};

window.showBeeState = function () {
  log("--- Estado ---");
  log("beeReady      = " + beeReady);
  log("wasmReady     = " + wasmReady);
  log("beeStarted    = " + beeStarted);
  log("BeeBg         = " + !!BeeBg);
  log("keys          = " + (lastGenKeysResult ? "sim" : "não"));
  log("walletName    = " + (lastWalletName || "(vazio)"));
  log("minerAddress  = " + (lastMinerAddress || "(vazio)"));
  log("minerInstance = " + !!minerInstance);
  log("miningActive  = " + miningActive);
};

window.resetBee = function () {
  BeeBg = null;
  beeReady = false;
  wasmReady = false;
  beeStarted = false;
  lastGenKeysResult = null;
  lastWalletName = "";
  lastMinerAddress = null;
  minerInstance = null;
  miningActive = false;

  if (walletInput) walletInput.value = "";

  setStatus("Resetado");
  log("Estado resetado");
};

window.clearBeeLog = function () {
  clearLog();
  log("Log limpo");
};

window.addEventListener("load", () => {
  setStatus("Bee local carregado");
  log("window.location = " + window.location.href);
  log("app.js carregado");
});