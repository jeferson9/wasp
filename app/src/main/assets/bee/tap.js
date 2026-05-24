// ============================
// STORAGE KEYS
// ============================
const WP_KEY = "wasp_wp";
const WP_HISTORY_KEY = "wasp_wp_history_v1";
const WP_TOTAL_KEY = "wasp_wp_total_earned";
const DAILY_KEY = "wasp_daily_login";
const DAILY_STREAK_KEY = "wasp_daily_streak";
const TAP_LAST_PLAY_KEY = "wasp_tap_last_play";
const WP_AD_LAST_VIEW_KEY = "wasp_wp_ad_last_view";
const BOOST_END_KEY = "wasp_boost_end";

// ============================
// CONFIG
// ============================
const TAP_MAX = 100;
const TAP_REWARD = 30;
const AD_REWARD = 30;

const TAP_COOLDOWN = 60 * 60 * 1000;
const AD_COOLDOWN = 15 * 60 * 1000;

const BOOST_COST = 20;
const BOOST_DURATION = 15 * 60 * 1000;

// ============================
// STATE
// ============================
let tapCount = 0;
let tapCombo = 1;
let lastTap = 0;
let tapFinished = false;
let wpAdPending = false;
let wpUiTimer = null;
// ============================
// HELPERS
// ============================

function startWpUiLoop() {
  if (wpUiTimer) return;

  wpUiTimer = setInterval(() => {
    updateWpActionStates();
    updateBoostUI();
  }, 1000);
}

function stopWpUiLoop() {
  if (!wpUiTimer) return;
  clearInterval(wpUiTimer);
  wpUiTimer = null;
}

function nowWp() {
  return new Date().toLocaleTimeString("pt-BR");
}

function getNum(key, def = 0) {
  return parseInt(localStorage.getItem(key) || String(def), 10);
}

function setNum(key, value) {
  localStorage.setItem(key, String(value));
}

function getJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback));
  } catch (_) {
    return fallback;
  }
}

function setJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function toastBee(message) {
  try {
    if (window.AndroidBee && typeof window.AndroidBee.toast === "function") {
      window.AndroidBee.toast(message);
    }
  } catch (_) {}
}

function addHistoryLine(text) {
  const list = getJson(WP_HISTORY_KEY, []);
  list.unshift(`[${nowWp()}] ${text}`);
  setJson(WP_HISTORY_KEY, list.slice(0, 80));
}

function getRemainingMs(lastTime, cooldownMs) {
  if (!lastTime) return 0;
  return Math.max(0, cooldownMs - (Date.now() - lastTime));
}

function formatRemaining(ms) {
  const totalSeconds = Math.ceil(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.ceil((totalSeconds % 3600) / 60);

  if (hours > 0) {
    return `${hours}h ${Math.max(0, minutes)}min`;
  }

  return `${Math.max(1, minutes)} min`;
}

function formatBoostRemaining(ms) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

// ============================
// WP CORE
// ============================
function getWpBalance() {
  return getNum(WP_KEY, 0);
}

function setWpBalance(value) {
  setNum(WP_KEY, Math.max(0, value));
}

function getTotalWp() {
  return getNum(WP_TOTAL_KEY, 0);
}

function addWp(amount, source) {
  const current = getWpBalance();
  setWpBalance(current + amount);

  const total = getTotalWp();
  setNum(WP_TOTAL_KEY, total + amount);

  // Acumula WP ganhos hoje (lido pelo app.js no card da home)
  const todayKey = "wasp_wp_today_" + new Date().toDateString();
  const prev = parseInt(localStorage.getItem(todayKey) || "0", 10);
  localStorage.setItem(todayKey, prev + amount);

  addHistoryLine(`+${amount} WP • ${source}`);
  renderWpCentral();
}

function clearWpHistory() {
  localStorage.removeItem(WP_HISTORY_KEY);
  renderWpCentral();
}

// ============================
// LEVEL
// ============================
function getLevelThresholds() {
  return [0, 50, 120, 220, 360, 550, 800, 1100, 1500, 2000, 2600, 3300];
}

function getLevelProgressData() {
  const total = getTotalWp();
  const levels = getLevelThresholds();

  let currentLevel = 1;
  let currentBase = 0;
  let nextTarget = levels[1] || 50;

  for (let i = 0; i < levels.length; i++) {
    if (total >= levels[i]) {
      currentLevel = i + 1;
      currentBase = levels[i];
      nextTarget = levels[i + 1] || (levels[i] + 1000);
    }
  }

  const progressRange = Math.max(1, nextTarget - currentBase);
  const progressValue = total - currentBase;
  const progressPercent = Math.max(0, Math.min(100, (progressValue / progressRange) * 100));
  const remaining = Math.max(0, nextTarget - total);

  return {
    level: currentLevel,
    total,
    progressPercent,
    remaining
  };
}

// ============================
// DAILY
// ============================
function checkDaily() {
  const today = new Date().toDateString();
  const last = localStorage.getItem(DAILY_KEY);

  if (last === today) return;

  let streak = getNum(DAILY_STREAK_KEY, 0);
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);

  if (last === yesterday.toDateString()) {
    streak += 1;
  } else {
    streak = 1;
  }

  setNum(DAILY_STREAK_KEY, streak);
  localStorage.setItem(DAILY_KEY, today);

  const reward = Math.min(25, 5 + (streak - 1) * 2);
  addWp(reward, `Login diário • streak ${streak}`);
  toastBee(`Login diário: +${reward} WP 🔥`);
}

// ============================
// BOOST
// ============================
function getBoostEnd() {
  return getNum(BOOST_END_KEY, 0);
}

function isBoostActive() {
  return Date.now() < getBoostEnd();
}

function getBoostRemainingMs() {
  return Math.max(0, getBoostEnd() - Date.now());
}

function activateBoost() {
  if (isBoostActive()) return;

  const balance = getWpBalance();
  if (balance < BOOST_COST) {
    toastBee("WP insuficiente");
    return;
  }

  setWpBalance(balance - BOOST_COST);
  setNum(BOOST_END_KEY, Date.now() + BOOST_DURATION);

  addHistoryLine(`-${BOOST_COST} WP • Boost 2x + Mineração bg ativados`);
  toastBee("Boost + Mineração bg ativados ⚡🐝");

  // ── Inicia mineração em background via Android Service ──────────────────
  // Mesmo que a Bee Engine não esteja conectada à rede Acki Nacki,
  // o service mantém o estado de mineração ativo na UI e registra ciclos.
  _startAndroidBgMining(BOOST_DURATION);

  renderWpCentral();
}

/**
 * Dispara o BeeBackgroundService via bridge Android.
 * Funciona como "mineração simulada em background" enquanto a integração
 * completa com a rede Acki Nacki ainda está pendente.
 */
function _startAndroidBgMining(durationMs) {
  try {
    // Bridge do BeeActivity (AndroidBee)
    if (window.AndroidBee && typeof window.AndroidBee.startBgMining === "function") {
      const wallet = _getWalletName();
      window.AndroidBee.startBgMining(durationMs, wallet);
      return;
    }
    // Bridge da MainActivity (Android)
    if (window.Android && typeof window.Android.startBgMining === "function") {
      const wallet = _getWalletName();
      window.Android.startBgMining(durationMs, wallet);
      return;
    }
    // Fallback: salva estado no localStorage para ser lido pelo bee_engine.js
    localStorage.setItem("wasp_bg_mining_end", String(Date.now() + durationMs));
    localStorage.setItem("wasp_bg_mining_active", "true");
  } catch (e) {
    console.warn("_startAndroidBgMining fallback:", e);
  }
}

function _getWalletName() {
  try {
    // Tenta ler do estado salvo pelo bee_engine.js
    const state = JSON.parse(localStorage.getItem("bee_engine_state") || "{}");
    return state.walletName || state.minerAddress || "";
  } catch (_) { return ""; }
}

function updateBoostUI() {
  const btn = document.getElementById("btnBoost");
  if (!btn) return;

  if (isBoostActive()) {
    btn.classList.add("active");
    btn.textContent = `Boost ativo ⚡ ${formatBoostRemaining(getBoostRemainingMs())}`;
  } else {
    btn.classList.remove("active");
    btn.textContent = `Ativar Boost 2x (${BOOST_COST} WP)`;
  }
}

// ============================
// CENTRAL WP
// ============================
function openWpCentral() {
  const el = document.getElementById("wpCentral");
  if (el) el.classList.remove("hidden");
  renderWpCentral();
  startWpUiLoop();
}

function closeWpCentral() {
  const el = document.getElementById("wpCentral");
  if (el) el.classList.add("hidden");

  const tapGame = document.getElementById("tapGame");
  const tapOpen = tapGame && tapGame.classList.contains("active");

  if (!tapOpen) {
    stopWpUiLoop();
  }
}
function updateWpActionStates() {
  const tapBtn = document.getElementById("btnPlayTap");
  const tapStatus = document.getElementById("tapStatusText");

  const adBtn = document.getElementById("btnWatchWpAd");
  const adStatus = document.getElementById("wpAdStatusText");

  const tapRemaining = getRemainingMs(getNum(TAP_LAST_PLAY_KEY, 0), TAP_COOLDOWN);
  const adRemaining = getRemainingMs(getNum(WP_AD_LAST_VIEW_KEY, 0), AD_COOLDOWN);

  if (tapBtn && tapStatus) {
    if (tapRemaining <= 0) {
      tapBtn.disabled = false;
      tapBtn.textContent = "Jogar agora";
      tapStatus.textContent = "Disponível agora";
      tapStatus.className = "wp-status-text ready";
    } else {
      tapBtn.disabled = true;
      tapBtn.textContent = "Em cooldown";
      tapStatus.textContent = `Disponível em ${formatRemaining(tapRemaining)}`;
      tapStatus.className = "wp-status-text cooldown";
    }
  }

  if (adBtn && adStatus) {
    if (wpAdPending) {
      adBtn.disabled = true;
      adBtn.textContent = "Carregando anúncio...";
      adStatus.textContent = "Aguardando conclusão do anúncio";
      adStatus.className = "wp-status-text cooldown";
    } else if (adRemaining <= 0) {
      adBtn.disabled = false;
      adBtn.textContent = "Ganhar com anúncio";
      adStatus.textContent = "Disponível agora";
      adStatus.className = "wp-status-text ready";
    } else {
      adBtn.disabled = true;
      adBtn.textContent = "Em cooldown";
      adStatus.textContent = `Disponível em ${formatRemaining(adRemaining)}`;
      adStatus.className = "wp-status-text cooldown";
    }
  }
}

function renderWpHistory() {
  const historyList = document.getElementById("wpHistoryList");
  if (!historyList) return;

  historyList.innerHTML = "";
  const history = getJson(WP_HISTORY_KEY, []);

  if (!history.length) {
    const empty = document.createElement("div");
    empty.className = "wp-history-line";
    empty.textContent = "Nenhum ganho de WP ainda.";
    historyList.appendChild(empty);
    return;
  }

  history.forEach((item) => {
    const line = document.createElement("div");
    line.className = "wp-history-line";
    line.textContent = item;
    historyList.appendChild(line);
  });
}

function renderWpCentral() {
  const balance = getWpBalance();
  const mini = document.getElementById("wpMiniBalance");
  const balanceMain = document.getElementById("wpBalanceMain");

  if (mini) mini.textContent = `${balance} WP`;
  if (balanceMain) balanceMain.textContent = `${balance} WP`;

  const progress = getLevelProgressData();

  const levelMain = document.getElementById("wpLevelMain");
  const totalEarned = document.getElementById("wpTotalEarned");
  const levelFill = document.getElementById("wpLevelFill");
  const levelHint = document.getElementById("wpLevelHint");

  if (levelMain) levelMain.textContent = String(progress.level);
  if (totalEarned) totalEarned.textContent = `${progress.total} WP`;
  if (levelFill) levelFill.style.width = `${progress.progressPercent}%`;
  if (levelHint) levelHint.textContent = `Faltam ${progress.remaining} WP para o próximo nível`;

  const streak = getNum(DAILY_STREAK_KEY, 0);
  const dailyStreak = document.getElementById("wpDailyStreak");
  const dailyHint = document.getElementById("wpDailyHint");

  if (dailyStreak) dailyStreak.textContent = String(streak);
  if (dailyHint) {
    dailyHint.textContent = streak > 0
      ? `Sequência atual: ${streak} dia(s)`
      : "Abra o painel diariamente para manter sua sequência.";
  }

  updateWpActionStates();
  updateBoostUI();
  renderWpHistory();
  updatePainelCards();
}

// ============================
// TAP GAME
// ============================
function getTapMilestoneMessage(count) {
  if (count >= 100) return "Tap concluído. Recompensa liberada.";
  if (count >= 75) return "Insano. Falta muito pouco para os 10 WP.";
  if (count >= 50) return "Metade concluída. Continue pressionando.";
  if (count >= 25) return "Boa. Sua energia já está subindo.";
  if (count >= 10) return "Agora ficou interessante.";
  if (count >= 1) return "Boa. Continue tocando.";
  return "Comece tocando para carregar sua energia.";
}

function spawnTapFloat(text) {
  const zone = document.getElementById("tapFloatingZone");
  if (!zone) return;

  const el = document.createElement("div");
  el.className = "tap-float";
  el.textContent = text;

  const offsetX = Math.floor(Math.random() * 70) - 35;
  el.style.marginLeft = `${offsetX}px`;

  zone.appendChild(el);

  setTimeout(() => {
    el.remove();
  }, 750);
}

function animateTapButton() {
  const btn = document.getElementById("tapButton");
  if (!btn) return;

  btn.classList.remove("is-tapping");
  void btn.offsetWidth;
  btn.classList.add("is-tapping");

  setTimeout(() => {
    btn.classList.remove("is-tapping");
  }, 90);
}

function updateTapUI() {
  const tapCountEl = document.getElementById("tapCount");
  const tapRemainingEl = document.getElementById("tapRemaining");
  const tapComboEl = document.getElementById("tapCombo");
  const tapPercentEl = document.getElementById("tapPercent");
  const tapProgressFill = document.getElementById("tapProgressFill");
  const tapMilestoneText = document.getElementById("tapMilestoneText");

  const percent = Math.min(100, Math.floor((tapCount / TAP_MAX) * 100));
  const remaining = Math.max(0, TAP_MAX - tapCount);

  if (tapCountEl) tapCountEl.innerText = `${tapCount} / ${TAP_MAX}`;
  if (tapRemainingEl) tapRemainingEl.innerText = `${remaining}`;
  if (tapComboEl) tapComboEl.innerText = `x${tapCombo}`;
  if (tapPercentEl) tapPercentEl.innerText = `${percent}%`;
  if (tapProgressFill) tapProgressFill.style.width = `${percent}%`;
  if (tapMilestoneText) tapMilestoneText.innerText = getTapMilestoneMessage(tapCount);
}

function isTapAvailable() {
  return getRemainingMs(getNum(TAP_LAST_PLAY_KEY, 0), TAP_COOLDOWN) <= 0;
}

function openTapGame() {
  if (!isTapAvailable()) {
    const remaining = getRemainingMs(getNum(TAP_LAST_PLAY_KEY, 0), TAP_COOLDOWN);
    toastBee(`Tap disponível em ${formatRemaining(remaining)}`);
    renderWpCentral();
    return;
  }

  tapCount = 0;
  tapCombo = 1;
  lastTap = 0;
  tapFinished = false;
  updateTapUI();

  const tapGame = document.getElementById("tapGame");
  if (tapGame) tapGame.classList.add("active");

  startWpUiLoop();
}

function closeTapGame() {
  const tapGame = document.getElementById("tapGame");
  if (tapGame) tapGame.classList.remove("active");

  const central = document.getElementById("wpCentral");
  const centralOpen = central && !central.classList.contains("hidden");

  if (!centralOpen) {
    stopWpUiLoop();
  }
}

function tapHit() {
  const now = Date.now();
  const diff = now - lastTap;

  if (diff < 900 && lastTap > 0) {
    tapCombo = Math.min(9, tapCombo + 1);
  } else {
    tapCombo = 1;
  }

  lastTap = now;
  tapCount++;

  animateTapButton();
  spawnTapFloat("+1");

  if (navigator.vibrate) {
    navigator.vibrate(8);
  }

  if (tapCount === 25 || tapCount === 50 || tapCount === 75) {
    spawnTapFloat("⚡");
  }

  if (tapCount >= TAP_MAX) {
    updateTapUI();
    finishTapGame();
    return;
  }

  updateTapUI();
}

function finishTapGame() {
  if (tapFinished) return;
  tapFinished = true;
  closeTapGame();
  setNum(TAP_LAST_PLAY_KEY, Date.now());
  addWp(TAP_REWARD, "Tap Game concluído");
  toastBee(`Você ganhou ${TAP_REWARD} WP!`);
  renderWpCentral();
}

// ============================
// WP ADS
// ============================
function isWpAdAvailable() {
  return getRemainingMs(getNum(WP_AD_LAST_VIEW_KEY, 0), AD_COOLDOWN) <= 0;
}

function startWpAdFlow() {
  if (wpAdPending) return;

  if (!isWpAdAvailable()) {
    const remaining = getRemainingMs(getNum(WP_AD_LAST_VIEW_KEY, 0), AD_COOLDOWN);
    toastBee(`Anúncio disponível em ${formatRemaining(remaining)}`);
    renderWpCentral();
    return;
  }

  try {
    if (window.AndroidBee && typeof window.AndroidBee.openWpAd === "function") {
      wpAdPending = true;
      renderWpCentral();
      window.AndroidBee.openWpAd();
    } else {
      toastBee("Anúncio WP não disponível");
    }
  } catch (_) {
    wpAdPending = false;
    renderWpCentral();
  }
}

window.onWpAdRewarded = function () {
  wpAdPending = false;
  setNum(WP_AD_LAST_VIEW_KEY, Date.now());
  addWp(AD_REWARD, "Anúncio recompensado");
  toastBee(`Você ganhou ${AD_REWARD} WP com anúncio!`);
  renderWpCentral();
};

window.onWpAdClosed = function () {
  wpAdPending = false;
  toastBee("Anúncio fechado sem recompensa");
  renderWpCentral();
};


// ============================
// SINCRONIZA PAINEL CARDS
// ============================
function updatePainelCards() {
  // WP saldo no painel
  const bal = getWpBalance();
  const s1 = document.getElementById("panelWpBalance");
  if (s1) s1.textContent = bal;

  // Streak
  const streak = getNum(DAILY_STREAK_KEY, 0);
  const s2 = document.getElementById("panelStreak");
  if (s2) s2.textContent = streak;

  // Nível
  const prog = getLevelProgressData();
  const plv  = document.getElementById("panelLevel");
  const ptot = document.getElementById("panelTotalWp");
  const pfil = document.getElementById("panelLevelFill");
  const phnt = document.getElementById("panelLevelHint");
  if (plv)  plv.textContent  = prog.level;
  if (ptot) ptot.textContent = prog.total + " WP";
  if (pfil) pfil.style.width = prog.progressPercent + "%";
  if (phnt) phnt.textContent = "Faltam " + prog.remaining + " WP para o próximo nível";

  // Tap status card
  const tapRem = getRemainingMs(getNum(TAP_LAST_PLAY_KEY, 0), TAP_COOLDOWN);
  const tapSt  = document.getElementById("panelTapStatus");
  if (tapSt) tapSt.textContent = tapRem <= 0 ? "Disponível agora ✅" : "Disponível em " + formatRemaining(tapRem);

  // Ad status card
  const adRem = getRemainingMs(getNum(WP_AD_LAST_VIEW_KEY, 0), AD_COOLDOWN);
  const adSt  = document.getElementById("panelAdStatus");
  if (adSt) adSt.textContent = adRem <= 0 ? "Disponível agora ✅" : "Disponível em " + formatRemaining(adRem);

  // Boost status card
  const boostSt  = document.getElementById("panelBoostStatus");
  const boostBtn = document.getElementById("panelBoostBtn");
  if (isBoostActive()) {
    if (boostSt)  boostSt.textContent  = "Ativo! " + formatBoostRemaining(getBoostRemainingMs());
    if (boostBtn) boostBtn.textContent = "Ativo ⚡";
  } else {
    if (boostSt)  boostSt.textContent  = "Gasta 20 WP";
    if (boostBtn) boostBtn.textContent = "Ativar";
  }

  // Daily status
  const dailySt  = document.getElementById("panelDailyStatus");
  const dailyBtn = document.getElementById("panelDailyBtn");
  const today = new Date().toDateString();
  const lastDay = localStorage.getItem(DAILY_KEY);
  if (dailySt) dailySt.textContent = "Streak: " + streak + " dia(s)";
  if (dailyBtn) {
    if (lastDay === today) {
      dailyBtn.textContent = "Coletado ✅";
      dailyBtn.className   = "pwp-btn pwp-btn-done";
    } else {
      dailyBtn.textContent = "Coletar";
      dailyBtn.className   = "pwp-btn";
    }
  }

  // History no painel inline
  const hist = getJson(WP_HISTORY_KEY, []);
  const hList = document.getElementById("wpHistoryList");
  if (hList) {
    hList.innerHTML = "";
    (hist.length ? hist.slice(0,10) : ["Nenhum ganho ainda."]).forEach(item => {
      const d = document.createElement("div");
      d.className = "wp-history-line";
      d.textContent = item;
      hList.appendChild(d);
    });
  }

  // History na central overlay (id diferente)
  const hFull = document.getElementById("wpHistoryListFull");
  if (hFull) {
    hFull.innerHTML = "";
    (hist.length ? hist : ["Nenhum ganho ainda."]).forEach(item => {
      const d = document.createElement("div");
      d.className = "wp-history-line";
      d.textContent = item;
      hFull.appendChild(d);
    });
  }
}

function openBeeEngine() {
  // Abre a tela Bee Engine (bee.html via Android bridge ou overlay)
  if (window.Android && typeof window.Android.openBee === "function") {
    window.Android.openBee();
  } else if (typeof openBeePanel === "function") {
    openBeePanel();
  }
}

// ============================
// BIND
// ============================
let _wpEventsBound = false;
function bindWpEvents() {
  if (_wpEventsBound) return;
  _wpEventsBound = true;

  document.getElementById("btnOpenWpCentral")?.addEventListener("click", openWpCentral);
  document.getElementById("btnCloseWpCentral")?.addEventListener("click", closeWpCentral);

  document.getElementById("btnPlayTap")?.addEventListener("click", openTapGame);
  document.getElementById("tapButton")?.addEventListener("click", tapHit);
  document.getElementById("tapCloseBtn")?.addEventListener("click", closeTapGame);

  document.getElementById("btnWatchWpAd")?.addEventListener("click", startWpAdFlow);
  document.getElementById("btnClearWpHistory")?.addEventListener("click", clearWpHistory);
  document.getElementById("btnBoost")?.addEventListener("click", activateBoost);

  document.getElementById("btnNavigationInfo")?.addEventListener("click", () => {
    toastBee("Ganhos por navegação serão ligados na próxima etapa");
  });
}

// ============================
// INIT
// ============================
document.addEventListener("DOMContentLoaded", () => {
  checkDaily();
  bindWpEvents();
  renderWpCentral();
  updateTapUI();
  updatePainelCards();
  setInterval(updatePainelCards, 5000);
});

window.openTapGame = openTapGame;