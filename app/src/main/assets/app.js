/* =======================================================
   WASP BROWSER — app.js (CLEAN)
   Bugs corrigidos:
   - resetHome() duplicada removida (mantida com setBottomTab)
   - setTheme() duplicada removida (mantida versão completa)
   - 3 DOMContentLoaded unificados em 1
   - pointerdown duplicado removido (mantido o completo)
   - document.addEventListener("click") duplicado unificado
   - toggleMenu() ligado ao settingsPanel (sem #menu no HTML)
======================================================= */

/* =========================
   SAFE AREA — Android WebView
   Android nativo chama window.setStatusBarHeight(px)
   para injetar a altura real da status bar.
   CSS usa var(--safe-top) nos lugares críticos.
========================= */
(function() {
  document.documentElement.style.setProperty('--safe-top', '0px');
  document.documentElement.style.setProperty('--safe-bottom', '0px');

  window.setStatusBarHeight = function(px) {
    var v = parseInt(px, 10) || 0;
    document.documentElement.style.setProperty('--safe-top', v + 'px');
  };
  window.setNavBarHeight = function(px) {
    var v = parseInt(px, 10) || 0;
    document.documentElement.style.setProperty('--safe-bottom', v + 'px');
  };
})();


/* =========================
   CENTRAL WP — ABRE central.html
========================= */
function openCentralWP() {
  // Via bridge Android (BeeActivity ou MainActivity)
  if (window.Android && typeof window.Android.openCentral === "function") {
    window.Android.openCentral();
    return;
  }
  if (window.AndroidBee && typeof window.AndroidBee.navigateTo === "function") {
    window.AndroidBee.navigateTo("central");
    return;
  }
  // Fallback web
  window.location.href = "central.html";
}

/* Atualiza saldo WP nos elementos do painel */
function syncWpBalanceUI() {
  try {
    const bal = parseInt(localStorage.getItem("wasp_wp") || "0", 10);
    const els = ["wpMiniBalance", "centralWpBal", "panelWpBalance"];
    els.forEach(id => {
      const el = document.getElementById(id);
      if (el) el.textContent = bal + " WP";
    });
  } catch(_) {}
}

/* =========================
   UTILIDADES BASE
========================= */

function $(id){ return document.getElementById(id); }

function safeText(value){
  return String(value ?? "").replace(/[<>]/g, "");
}

/* =========================
   MENU / CONFIGURAÇÕES
   O botão hambúrguer (☰) da home chama toggleMenu(), que abre o painel
   de Configurações (#settingsPanel) via classe .active.
========================= */
function openSettings(){
  const p = $("settingsPanel");
  if(p) p.classList.add("active");
}

function closeSettings(){
  const p = $("settingsPanel");
  if(p) p.classList.remove("active");
}

function toggleMenu(){
  const p = $("settingsPanel");
  if(!p) return;
  if(p.classList.contains("active")) p.classList.remove("active");
  else p.classList.add("active");
}

// Expõe globalmente para os onclick inline do HTML
window.toggleMenu    = toggleMenu;
window.openSettings  = openSettings;
window.closeSettings = closeSettings;

/* =========================
   CORES PADRÃO WASP
========================= */
const WASP_GREEN = "#00c853";
const WASP_RED   = "#d50000";
const WASP_GRAY  = "#999999";

/* =========================
   CONFIG / TEMA
========================= */
const THEME_KEY = "wasp_theme";

function applyTheme(theme){
    document.body.setAttribute("data-theme", theme);
    localStorage.setItem(THEME_KEY, theme);

    const label = $("themeLabel");
    if(label){
        label.textContent = theme === "dark" ? "Dark" : "Light";
    }

    // Remove background no tema claro
    if(theme === "light"){
        document.documentElement.style.setProperty("--wasp-home-bg", "none");
    } else {
        setDailyBackground();
    }
    updateLogoByTheme();
}

function toggleTheme(){
    const current = document.body.getAttribute("data-theme") || "dark";
    applyTheme(current === "dark" ? "light" : "dark");
}

/* =========================
   TEMA — SETTINGS
========================= */

// Versão única e completa de setTheme
function setTheme(theme){
    applyTheme(theme);

    const label = $("themeLabel");
    if(label){
        label.textContent = theme === "dark" ? "Dark" : "Light";
    }

    const menu = $("themeOptions");
    if(menu) menu.classList.remove("active");
}

function toggleThemeOptions(){
    const menu = $("themeOptions");
    if(menu) menu.classList.toggle("active");
}

/* =========================
   URL NORMALIZER
========================= */
function normalizeUrl(input){
  let q = (input || "").trim();
  if(!q) return "";

  const lower = q.toLowerCase();
  if(lower.startsWith("javascript:")) return "";

  if(q.includes(" ")){
    return "https://www.google.com/search?q=" + encodeURIComponent(q);
  }

  if(q.startsWith("http://") || q.startsWith("https://")){
    return q;
  }

  const domainLike =
    q.includes(".") ||
    q.includes("/") ||
    q.startsWith("www.");

  if(domainLike){
    return "https://" + q;
  }

  return "https://www.google.com/search?q=" + encodeURIComponent(q);
}

/* =========================
   OPEN URL (ANDROID)
========================= */
function openNativeUrl(input){
  if(!input) return;

  // Tratar URLs especiais do Hive
  if(input === "bee://engine"){
    if(window.Android && typeof Android.openBeePanel === "function") Android.openBeePanel();
    return;
  }
  if(input === "settings://open"){
    if(window.Android && typeof Android.openSettings === "function") Android.openSettings();
    else openSettings();
    return;
  }

  const url = normalizeUrl(input);
  if(!url) return;

  if(window.Android && typeof Android.openUrl === "function"){
    Android.openUrl(url);
  } else {
    window.location.href = url;
  }
}

/* =========================
   TELAS
========================= */
function openScreen(id){
  document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
  const el = $(id);
  if(el) el.classList.add("active");

  if(id === "home"){
    document.body.classList.add("home-active");
    document.querySelectorAll(".badge").forEach(btn => btn.classList.remove("active"));
    const mainBtn = document.querySelector(".badge");
    if(mainBtn) mainBtn.classList.add("active");
  } else {
    document.body.classList.remove("home-active");
  }

  if(id === "market"){
    waspUpdateMarketAll();
  }
}

/* =========================
   SEARCH
========================= */
function searchFrom(inputId){
    const input = $(inputId);
    if(!input) return;

    const value = input.value.trim();
    if(!value) return;

    addToHistory(value);

    let url;
    const isUrl = value.includes(".") && !value.includes(" ");

    if(value.startsWith("http")){
        url = value;
    } else if(isUrl){
        url = "https://" + value;
    } else {
        url = engines[currentEngine].url + encodeURIComponent(value);
    }

    openNativeUrl(url);
}

/* =========================
   HISTORY SYSTEM
========================= */
const HISTORY_KEY = "wasp_history_v1";

function loadHistory(){
  try {
    return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [];
  } catch {
    return [];
  }
}

function saveHistory(list){
  localStorage.setItem(HISTORY_KEY, JSON.stringify(list));
}

function addToHistory(query){
  if(!query || query.length < 2) return;

  let history = loadHistory();
  let existing = history.find(i => i.q === query);

  if(existing){
    existing.count += 1;
    existing.time = Date.now();
  } else {
    history.push({ q: query, count: 1, time: Date.now() });
  }

  history.sort((a,b) => b.count - a.count || b.time - a.time);
  saveHistory(history.slice(0, 50));
}

function showHistorySuggestions(){
    const history = loadHistory().slice(0, 5);
    const box = $("searchSuggestions");
    if(!box) return;

    box.classList.add("active");
    box.innerHTML = history.map(item => `
        <div class="suggestion-item" data-value="${item.q}">
            <img class="suggestion-icon" src="img/search.webp">
            <span>${item.q}</span>
        </div>
    `).join("");

    box.querySelectorAll(".suggestion-item").forEach(item => {
        item.addEventListener("click", function(){
            selectSuggestion(this.getAttribute("data-value"));
        });
    });
}

/* =========================
   RECENTES
========================= */
window.RECENTS_KEY = "wasp_recents_v1";

function getRecents(){
  try {
    const data = localStorage.getItem(RECENTS_KEY);
    return Array.isArray(JSON.parse(data)) ? JSON.parse(data) : [];
  } catch(e){
    return [];
  }
}

function saveRecents(list){
  try {
    localStorage.setItem(RECENTS_KEY, JSON.stringify(list));
  } catch(e){}
}

function addRecent(title, url){
  if(url.includes("google.com/search?q=")){
    try {
      const u = new URL(url);
      const q = u.searchParams.get("q");
      if(q){
        url = "https://www.google.com/search?q=" + encodeURIComponent(q);
        title = q;
      }
    } catch(e){}
  }

  if(typeof url !== "string" || !url.startsWith("http")) return;
  if(url.startsWith("file://")) return;
  if(!title || title === "Wasp Browser") title = "";

  const list = getRecents();
  if(list[0]?.url === url) return;

  let host = "";
  try {
    host = new URL(url).hostname.replace(/^www\./, "");
  } catch(e){}

  const safeTitle = title || host || url;

  const filtered = list.filter(item => {
    try {
      const h = new URL(item.url).hostname.replace(/^www\./, "");
      return h !== host;
    } catch(e){
      return true;
    }
  });

  filtered.unshift({ title: safeTitle, url, time: Date.now() });
  saveRecents(filtered.slice(0, 30));
  renderRecents();
}

function renderRecents(){
  const container = $("recentList");
  if(!container) return;

  const list = getRecents().slice(0, 7);

  if(list.length === 0){
    container.innerHTML = `<div style="opacity:.6;padding:10px;">Nenhum recente ainda…</div>`;
    return;
  }

  container.innerHTML = list.map(item => {
    let domain = item.url;
    try { domain = new URL(item.url).hostname; } catch(e){}

    const favicon = `https://www.google.com/s2/favicons?domain=${domain}&sz=128`;
    return `
      <div class="recent-item" onclick="openNativeUrl('${item.url}')">
        <img class="recent-favicon" src="${favicon}">
        <div>
          <div class="recent-name">${safeText(item.title)}</div>
          <div class="recent-url">${safeText(domain)}</div>
        </div>
      </div>
    `;
  }).join("");
}

window.addRecentFromSite = function(url){
  if(!url || typeof url !== "string") return;
  if(!url.startsWith("http")) return;

  const list = getRecents();
  if(list[0]?.url === url) return;

  let title = "";
  try {
    const host = new URL(url).hostname
      .replace(/^www\./, "")
      .replace(/^m\./, "")
      .replace(/^mobile\./, "")
      .split(".")[0];
    title = host.charAt(0).toUpperCase() + host.slice(1);
  } catch(e){ title = ""; }

  addRecent(title, url);
};

/* =========================
   MERCADO CRIPTO
========================= */
async function waspFetchJSON(url){
  const res = await fetch(url, { headers: { "accept": "application/json" } });
  if(!res.ok) throw new Error("HTTP " + res.status);
  return await res.json();
}

function waspSetText(id, value){
  const el = $(id);
  if(el) el.textContent = value;
}

function waspFmtMoney(num){
  if(num == null) return "$ --";
  return "$ " + Number(num).toLocaleString("en-US", { maximumFractionDigits: 0 });
}

function waspFmtPrice(num){
  if(num == null) return "$ --";
  return "$ " + Number(num).toLocaleString("en-US", { maximumFractionDigits: 2 });
}

function waspSetFill(id, pct){
  const el = $(id);
  if(!el) return;
  const p = Math.max(0, Math.min(100, Number(pct || 0)));
  el.style.width = p + "%";
}

function setChangeColor(id, value){
  const el = $(id);
  if(!el) return;
  el.style.color = value > 0 ? WASP_GREEN : value < 0 ? WASP_RED : WASP_GRAY;
}

async function waspUpdateOverview(){
  const g = (await waspFetchJSON("https://api.coingecko.com/api/v3/global")).data;
  if(!g) return;

  waspSetText("moCap", waspFmtMoney(g.total_market_cap?.usd));
  waspSetText("moVol", waspFmtMoney(g.total_volume?.usd));
  waspSetText("moBtcDom", (g.market_cap_percentage?.btc || 0).toFixed(2) + "%");
}

async function waspUpdateTop3(){
  const p = await waspFetchJSON(
    "https://api.coingecko.com/api/v3/simple/price" +
    "?ids=bitcoin,ethereum,binancecoin" +
    "&vs_currencies=usd&include_24hr_change=true"
  );

  const btc = p.bitcoin || {};
  const eth = p.ethereum || {};
  const bnb = p.binancecoin || {};

  waspSetText("btcPrice", waspFmtPrice(btc.usd));
  waspSetText("ethPrice", waspFmtPrice(eth.usd));
  waspSetText("bnbPrice", waspFmtPrice(bnb.usd));

  const btcCh = btc.usd_24h_change ?? 0;
  const ethCh = eth.usd_24h_change ?? 0;
  const bnbCh = bnb.usd_24h_change ?? 0;

  waspSetText("btcChange", btcCh.toFixed(2) + "%");
  waspSetText("ethChange", ethCh.toFixed(2) + "%");
  waspSetText("bnbChange", bnbCh.toFixed(2) + "%");

  setChangeColor("btcChange", btcCh);
  setChangeColor("ethChange", ethCh);
  setChangeColor("bnbChange", bnbCh);
}

async function waspUpdateFearGreed(){
  const fg = (await waspFetchJSON("https://api.alternative.me/fng/?limit=1")).data?.[0];
  if(!fg) return;

  waspSetText("fgValue", fg.value);
  waspSetText("fgLabel", fg.value_classification);
  waspSetFill("fgFill", fg.value);
}

async function waspUpdateTrending(){
  const trendList = $("trendList");
  if(!trendList) return;

  try {
    const tr = await waspFetchJSON("https://api.coingecko.com/api/v3/search/trending");
    const coins = tr?.coins || [];
    const ids = coins.map(c => c.item.id).join(",");
    const prices = await waspFetchJSON(
      `https://api.coingecko.com/api/v3/simple/price?ids=${ids}&vs_currencies=usd`
    );

    trendList.innerHTML = coins.slice(0, 10).map(c => {
      const i = c.item;
      return `
        <div class="trend-card" onclick="openNativeUrl('https://www.coingecko.com/en/coins/${i.id}')">
          <div class="trend-top">
            <div class="trend-symbol">${safeText(i.symbol)}</div>
            <div style="opacity:.7;font-weight:900;">#${i.market_cap_rank || "--"}</div>
          </div>
          <div class="trend-name">${safeText(i.name)}</div>
          <div class="trend-price">${waspFmtPrice(prices[i.id]?.usd)}</div>
        </div>
      `;
    }).join("");
  } catch(e){}
}

let lastGainerUpdate = 0;
let cachedGainersHTML = "";
let cachedLosersHTML = "";

async function waspUpdateGainersLosers(){
  const now = Date.now();

  if(now - lastGainerUpdate < 5 * 60 * 1000){
    if(cachedGainersHTML) $("gainerList").innerHTML = cachedGainersHTML;
    if(cachedLosersHTML)  $("loserList").innerHTML  = cachedLosersHTML;
    return;
  }

  const gainerList = $("gainerList");
  const loserList  = $("loserList");
  if(!gainerList || !loserList) return;

  try {
    const list = await waspFetchJSON(
      "https://api.coingecko.com/api/v3/coins/markets" +
      "?vs_currency=usd&order=market_cap_desc&per_page=100&page=1" +
      "&price_change_percentage=24h"
    );

    const valid = list.filter(c => c.price_change_percentage_24h != null);

    const gainers = [...valid]
      .sort((a,b) => b.price_change_percentage_24h - a.price_change_percentage_24h)
      .slice(0, 5);

    const losers = [...valid]
      .sort((a,b) => a.price_change_percentage_24h - b.price_change_percentage_24h)
      .slice(0, 5);

    cachedGainersHTML = gainers.map(c => `
      <div class="gain-card" onclick="openNativeUrl('https://www.coingecko.com/en/coins/${c.id}')">
        <div class="gain-top">
          <div class="gain-symbol">${safeText(c.symbol.toUpperCase())}</div>
          <div class="gain-change" style="color:${WASP_GREEN}">+${c.price_change_percentage_24h.toFixed(2)}%</div>
        </div>
        <div class="gain-name">${safeText(c.name)}</div>
        <div class="gain-price">${waspFmtPrice(c.current_price)}</div>
      </div>
    `).join("");

    cachedLosersHTML = losers.map(c => `
      <div class="lose-card" onclick="openNativeUrl('https://www.coingecko.com/en/coins/${c.id}')">
        <div class="lose-top">
          <div class="lose-symbol">${safeText(c.symbol.toUpperCase())}</div>
          <div class="lose-change" style="color:${WASP_RED}">${c.price_change_percentage_24h.toFixed(2)}%</div>
        </div>
        <div class="lose-name">${safeText(c.name)}</div>
        <div class="lose-price">${waspFmtPrice(c.current_price)}</div>
      </div>
    `).join("");

    gainerList.innerHTML = cachedGainersHTML;
    loserList.innerHTML  = cachedLosersHTML;
    lastGainerUpdate = now;

  } catch(e){
    if(cachedGainersHTML) gainerList.innerHTML = cachedGainersHTML;
    if(cachedLosersHTML)  loserList.innerHTML  = cachedLosersHTML;
  }
}

/* -------- NACKL TICKER (home) -------- */
// Cache para não sumir ao falhar
const _nacklCache = { price: null, change: null, mcap: null };

async function waspUpdateNackl(){
  try {
    const data = await waspFetchJSON(
      "https://api.coingecko.com/api/v3/simple/price" +
      "?ids=acki-nacki&vs_currencies=usd&include_24hr_change=true&include_market_cap=true"
    );

    const coin = data["acki-nacki"] || {};
    const price  = coin.usd ?? 0;
    const change = coin.usd_24h_change ?? 0;
    const mcap   = coin.usd_market_cap ?? 0;

    const priceStr = price === 0
      ? "$ 0.00"
      : price < 0.0001
        ? "$ " + price.toFixed(6)
        : "$ " + price.toFixed(4);

    const changeStr = (change >= 0 ? "+" : "") + change.toFixed(2) + "%";

    let mcapStr = "";
    if(mcap >= 1e9)       mcapStr = "$ " + (mcap / 1e9).toFixed(2) + "B";
    else if(mcap >= 1e6)  mcapStr = "$ " + (mcap / 1e6).toFixed(2) + "M";
    else if(mcap > 0)     mcapStr = "$ " + mcap.toFixed(0);
    else                  mcapStr = "Pré-lançamento";

    // Salva cache
    _nacklCache.price  = priceStr;
    _nacklCache.change = changeStr;
    _nacklCache.mcap   = mcapStr;
    _nacklCache.chgVal = change;

    waspSetText("nacklPrice",  priceStr);
    waspSetText("nacklChange", changeStr);
    waspSetText("nacklMcap",   mcapStr);

    const changeEl = $("nacklChange");
    if(changeEl) changeEl.style.color = change >= 0 ? WASP_GREEN : WASP_RED;

  } catch(e){
    // Mantém último valor válido se existir
    if(_nacklCache.price){
      waspSetText("nacklPrice",  _nacklCache.price);
      waspSetText("nacklChange", _nacklCache.change);
      waspSetText("nacklMcap",   _nacklCache.mcap);
      const changeEl = $("nacklChange");
      if(changeEl) changeEl.style.color = (_nacklCache.chgVal||0) >= 0 ? WASP_GREEN : WASP_RED;
    }
    // Se não tem cache ainda, mantém o "--" que já está
  }
}

async function waspUpdateMarketAll(){
  try {
    await waspUpdateNackl();
    await waspUpdateOverview();
    await waspUpdateTop3();
    await waspUpdateFearGreed();
    await waspUpdateTrending();
    await waspUpdateGainersLosers();
  } catch(e){}
}

/* =========================
   GOOGLE SUGGESTIONS
========================= */
function fetchGoogleSuggestions(query){
    return new Promise((resolve) => {
        const callbackName = "googleSuggest_" + Date.now();

        window[callbackName] = function(data){
            try {
                const suggestions = data[1] || [];
                resolve(suggestions.map(s => ({ phrase: s })));
            } catch(e){
                resolve([]);
            }
            delete window[callbackName];
            script.remove();
        };

        const script = document.createElement("script");
        script.src =
          "https://suggestqueries.google.com/complete/search?client=chrome&q=" +
          encodeURIComponent(query) +
          "&callback=" + callbackName;
        script.onerror = () => resolve([]);
        document.body.appendChild(script);
    });
}

/* =========================
   MOTOR DE BUSCA
========================= */
let currentEngine = localStorage.getItem("searchEngine") || "google";

const engines = {
    google: { icon: "img/google.webp", url: "https://www.google.com/search?q=" },
    brave:  { icon: "img/brave.webp",  url: "https://search.brave.com/search?q=" },
    bing:   { icon: "img/bing.webp",   url: "https://www.bing.com/search?q=" },
    duck:   { icon: "img/duck.webp",   url: "https://duckduckgo.com/?q=" }
};

function initEngine(){
    const icon = $("engineIcon");
    if(icon) icon.src = engines[currentEngine].icon;
}

function toggleEngineOptions(){
    const menu = document.getElementById("engineOptions");
    if(menu) menu.classList.toggle("active");
}

function setEngineFromSettings(engine){
    setEngine(engine);
    // Atualiza label nas configurações
    initEngineSettingsLabel();
    // Fecha submenu
    const menu = document.getElementById("engineOptions");
    if(menu) menu.classList.remove("active");
}

function initEngineSettingsLabel(){
    const names = { google:"Google", brave:"Brave", bing:"Bing", duck:"DuckDuckGo" };
    const icons = { google:"img/google.webp", brave:"img/brave.webp", bing:"img/bing.webp", duck:"img/duck.webp" };
    const label = document.getElementById("currentEngineName");
    if(label) label.innerHTML = '<img src="' + (icons[currentEngine]||"") + '" style="width:16px;height:16px;object-fit:contain;vertical-align:middle;margin-right:4px;"> ' + (names[currentEngine] || currentEngine);
}

function toggleEngineMenu(){
    console.log("toggleEngineMenu chamado");
    const menu  = $("engineMenu");
    const arrow = document.querySelector(".engine-arrow");
    if(menu)  menu.classList.toggle("active");
    if(arrow) arrow.classList.toggle("open");
    console.log("engineMenu active:", menu ? menu.classList.contains("active") : "nao encontrado");
}

function setEngine(engine){
    currentEngine = engine;
    localStorage.setItem("searchEngine", engine);

    const icon  = $("engineIcon");
    const menu  = $("engineMenu");
    const arrow = document.querySelector(".engine-arrow");

    if(icon)  icon.src = engines[engine].icon;
    if(menu)  menu.classList.remove("active");
    if(arrow) arrow.classList.remove("open");
}

/* =========================
   SUGESTÕES DE BUSCA
========================= */
function showSuggestions(list){
    const box = $("searchSuggestions");
    if(!box) return;

    box.innerHTML = "";

    if(!list || list.length === 0){
        box.classList.remove("active");
        return;
    }

    list.forEach(text => {
        const item = document.createElement("div");
        item.className = "suggestion-item";
        item.textContent = text;
        item.onclick = () => selectSuggestion(text);
        box.appendChild(item);
    });

    box.classList.add("active");
}

function hideSuggestions(){
    const box = $("searchSuggestions");
    if(box) box.classList.remove("active");
}

function showNativeSuggestions(data){
    const box = $("searchSuggestions");
    if(!box) return;

    const suggestions = data.map(item => item.phrase);

    box.innerHTML = suggestions.map(s => {
        let favicon = "img/search.webp";
        const match = s.match(/([a-z0-9-]+\.[a-z]{2,})/i);
        if(match){
            favicon = "https://www.google.com/s2/favicons?domain=" + match[1] + "&sz=64";
        }
        return `
            <div class="suggestion-item" data-value="${s}">
                <img class="suggestion-icon" src="${favicon}">
                <span>${s}</span>
            </div>
        `;
    }).join("");

    box.querySelectorAll(".suggestion-item").forEach(item => {
        item.addEventListener("click", function(){
            selectSuggestion(this.getAttribute("data-value"));
        });
    });

    box.classList.add("active");
}

function selectSuggestion(text){
    const input = $("homeInput");
    if(input) input.value = text;
    hideSuggestions();
    searchFrom("homeInput");
}

/* =========================
   ABAS / NAV
========================= */
function setBottomTab(mode){
    document.querySelectorAll(".bottom-tab").forEach(tab => {
        tab.classList.toggle("active", tab.dataset.mode === mode);
    });
}

function openMarketTab(){
    closeHive();
    openScreen("market");
    setBottomTab("market");
}

function openHiveTab(){
    setBottomTab("hive");
    // Garante que abre — nunca fecha ao clicar na aba
    const panel = $("hivePanel");
    if(!panel) return;
    if(!panel.classList.contains("active")){
        toggleHive();
    }
}

function openBeePanel(){
    if(window.Android && Android.openBeePanel){
        Android.openBeePanel();
    } else {
        openScreen("browser");
        setBottomTab("browser");
    }
}

function goNativeHome(){
    if(window.Android && typeof Android.goHome === "function"){
        Android.goHome();
    } else {
        resetHome();
    }
}

/* =========================
   HOME / RESET
========================= */

// Versão única de resetHome — com setBottomTab correto
function resetHome(){
    openScreen("home");
    var si = document.getElementById("urlInput") || document.querySelector(".url-input");
    if(si) si.value = "";

    document.querySelectorAll(".badge").forEach(btn => btn.classList.remove("active"));
    const firstBadge = document.querySelector(".badge");
    if(firstBadge) firstBadge.classList.add("active");

    setBottomTab("main");
}

/* =========================
   RECENTES — LIMPAR
========================= */
window.clearRecents = function(){
  try {
    localStorage.removeItem(window.RECENTS_KEY);
    localStorage.setItem(window.RECENTS_KEY, JSON.stringify([]));

    const list = $("recentList");
    if(list){
      list.innerHTML = `<div style="opacity:.6;padding:10px;">Nenhum recente ainda…</div>`;
    }
  } catch(e){
    console.error("Erro ao limpar recents", e);
  }
};

/* =========================
   HIVE HANDLE
========================= */
function hideHiveHandle(){
    const h = document.querySelector(".hive-handle");
    if(h) h.style.opacity = "0";
}

function showHiveHandle(){
    const h = document.querySelector(".hive-handle");
    if(h) h.style.opacity = "1";
}

function restoreHiveHandleLater(){
    setTimeout(() => {
        const panel = $("hivePanel");
        if(panel && !panel.classList.contains("active")){
            showHiveHandle();
        }
    }, 60);
}

/* =========================
   HIVE — CONTROLES
========================= */
function toggleHive(){
    renderHive();
    const panel = $("hivePanel");
    if(!panel) return;

    panel.classList.toggle("active");

    if(panel.classList.contains("active")){
        hideHiveHandle();
    } else {
        showHiveHandle();
    }
}

function closeHive(){
    const panel = $("hivePanel");
    if(panel) panel.classList.remove("active");
    showHiveHandle();
    // Volta aba ativa para home quando fecha o Hive
    setBottomTab("main");
}

/* =========================
   HIVE — STORAGE
========================= */
const HIVE_KEY = "wasp_hive_items";

function loadHive(){
    try {
        return JSON.parse(localStorage.getItem(HIVE_KEY)) || [];
    } catch {
        return [];
    }
}

function saveHive(list){
    localStorage.setItem(HIVE_KEY, JSON.stringify(list));
}






/* =========================
   HIVE — RENDER
========================= */
function hiveRemoveById(id){
    var list = loadHive().filter(function(i){ return i.id !== id; });
    saveHive(list);
}
function renderHive(){
    const grid = $("hiveGrid");
    if(!grid) return;

    // ── 1. Carrega lista do usuário e garante ID único em cada item ───────────
    let list = loadHive();
    if(!Array.isArray(list)) list = [];

    const SEED_SITES = [
        { name:"Acki Nacki", url:"https://ackinacki.com" },
        { name:"dex.do",     url:"https://dex.do" },
        { name:"Google",     url:"https://google.com" },
    ];

    // Versão do seed: ao subir o número, força recarregar a lista padrão
    // (descarta os sites antigos pré-carregados). Sites que o USUÁRIO
    // adicionou manualmente são preservados.
    const SEED_VERSION = "v2_min";
    if(localStorage.getItem("waspHiveSeedVer") !== SEED_VERSION){
        const userAdded = list.filter(i => i && i.url && i._userAdded);
        list = SEED_SITES.map(s => ({ ...s, uses:0, lastUsed:0 })).concat(userAdded);
        localStorage.setItem("waspHiveSeeded", "1");
        localStorage.setItem("waspHiveSeedVer", SEED_VERSION);
        saveHive(list);
    }

    // Primeira vez (Hive nunca foi semeado): não deixar vazio
    if(list.length === 0 && !localStorage.getItem("waspHiveSeeded")){
        list = SEED_SITES.map(s => ({ ...s, uses:0, lastUsed:0 }));
        localStorage.setItem("waspHiveSeeded", "1");
    }

    let changed = false;
    list = list.filter(i => i && i.url && i.url !== "__config__" && i.url !== "__panel__");
    list.forEach(item => {
        if(typeof item.uses !== "number"){ item.uses = 0; changed = true; }
        if(!item.lastUsed){ item.lastUsed = 0; changed = true; }
        if(!item.id){
            item.id = "s_" + Date.now().toString(36) + Math.random().toString(36).slice(2,7);
            changed = true;
        }
    });
    if(changed || !localStorage.getItem("waspHiveSeeded")){
        saveHive(list);
        localStorage.setItem("waspHiveSeeded", "1");
    }

    // ── 2. Atalhos fixos do sistema (Painel e Config) ─────────────────────────
    const PINNED = [
        { id:"__panel__",  name:"Painel", pinned:true, icon:"file:///android_asset/img/ic_panel_hive.svg" },
        { id:"__config__", name:"Config", pinned:true, icon:"file:///android_asset/img/ic_settings_hive.svg" },
    ];

    // ── 3. "Mais usados": top 4 por uso/recência (atalhos fixos não contam) ────
    const now = Date.now();
    const ranked = [...list].sort((a,b) => {
        const sa = (a.uses||0)*2 - (now-(a.lastUsed||0))/86400000;
        const sb = (b.uses||0)*2 - (now-(b.lastUsed||0))/86400000;
        return sb - sa;
    });
    const top = ranked.filter(i => (i.uses||0) > 0).slice(0, 4);

    // ── 4. Lista completa em ordem alfabética (todos os sites + fixos) ─────────
    const alpha = [...list].sort((a,b) =>
        a.name.localeCompare(b.name, "pt-BR", { sensitivity:"base" })
    );

    // ── 5. Renderiza ──────────────────────────────────────────────────────────
    grid.innerHTML = "";

    function iconHTML(item){
        if(item.pinned){
            return '<img class="hive-icon" src="' + item.icon + '">';
        }
        let domain = "";
        try { domain = new URL(item.url).hostname; } catch { domain = item.url; }
        return '<img class="hive-icon" data-fallback="1" ' +
               'src="https://www.google.com/s2/favicons?domain=' + domain + '&sz=64">';
    }

    function cell(item){
    const div = document.createElement('div');
    div.className = 'hive-item' + (item.pinned ? ' hive-config' : '');
    div.innerHTML = iconHTML(item) + '<span>' + item.name + '</span>';
    div.onclick = function(){ hiveOpenItem(item.id); };
    return div;
}

    // Seção "MAIS USADOS" (aparece no topo; os mesmos ícones continuam embaixo)
    if(top.length > 0){
        const title = document.createElement("div");
        title.className = "hive-title";
        title.textContent = "MAIS USADOS";
        grid.appendChild(title);
        top.forEach(item => grid.appendChild(cell(item)));

        const divider = document.createElement("div");
        divider.className = "hive-divider";
        grid.appendChild(divider);
    }

    // Lista completa (todos os sites em ordem alfabética) + atalhos fixos no fim
    alpha.forEach(item => grid.appendChild(cell(item)));
    PINNED.forEach(item => grid.appendChild(cell(item)));


    // Trata fallback de favicon que não carrega
    grid.querySelectorAll('img[data-fallback="1"]').forEach(img => {
        img.onload  = function(){ if(img.naturalWidth < 32) img.src = "file:///android_asset/img/globe.webp"; };
        img.onerror = function(){ img.src = "file:///android_asset/img/globe.webp"; };
    });
}

/* =========================
   HIVE — INTERAÇÃO
   Listeners por item (criados em cell()), abordagem estável.
========================= */


function hiveFindItemById(id){
    if(id === "__panel__" || id === "__config__"){
        return { id:id, pinned:true, name: id === "__panel__" ? "Painel" : "Config" };
    }
    const list = loadHive();
    return list.find(i => i.id === id) || null;
}

function hiveOpenItem(id){
    if(id === "__config__"){
        closeHive();
        if(window.Android && typeof Android.openSettings === "function") Android.openSettings();
        else openSettings();
        return;
    }
    if(id === "__panel__"){
        closeHive();
        if(window.Android && typeof Android.openPanel === "function") Android.openPanel();
        return;
    }
    const list = loadHive();
    const item = list.find(i => i.id === id);
    if(!item) return;
    item.uses = (item.uses || 0) + 1;
    item.lastUsed = Date.now();
    saveHive(list);
    closeHive();
    openNativeUrl(item.url);
}

function bindHiveGrid(){ /* listeners são criados por item em cell() */ }

/* =========================
   HIVE — DIÁLOGOS
========================= */
let _hiveDialogsBound = false;

function bindHiveDialogButtons(){
    bindHiveGrid();
    // Botões dos diálogos usam onclick inline no HTML (abordagem estável).
    // Aqui só garantimos o fechamento ao tocar no fundo escuro.
    if(_hiveDialogsBound) return;
    _hiveDialogsBound = true;
    const rd = $("removeDialog");
    if(rd) rd.addEventListener("click", function(e){ if(e.target === rd) closeRemoveDialog(); });
    const ad = $("addDialog");
    if(ad) ad.addEventListener("click", function(e){ if(e.target === ad) closeAddDialog(); });
}

function openRemoveDialog(id){
    // Painel e Config são acesso essencial — não podem ser removidos
    if(id === "__panel__" || id === "__config__") return;
    const item = hiveFindItemById(id);
    if(!item) return;
    hiveRemoveTarget = item;
    const txt = document.querySelector("#removeDialog .remove-text");
    if(txt) txt.textContent = 'Remover "' + (item.name || "este site") + '" do Hive?';
    const dialog = $("removeDialog");
    if(dialog) dialog.style.display = "flex";
}

function closeRemoveDialog(){
    const dialog = $("removeDialog");
    if(dialog){
        dialog.style.display = "none";
        dialog.style.pointerEvents = "";
    }
    if(hiveRemoveDiv){ hiveRemoveDiv.style.opacity = ""; hiveRemoveDiv = null; }
    hiveRemoveTarget = null;
}

function openAddDialog(){
    const dialog = $("addDialog");
    const nameInput = $("addName");
    const urlInput  = $("addUrl");
    if(nameInput) nameInput.value = "";
    if(urlInput)  urlInput.value  = "";
    if(dialog) dialog.style.display = "flex";
}

function closeAddDialog(){
    const dialog = $("addDialog");
    if(dialog) dialog.style.display = "none";
}

function confirmAddSite(){
    const nameInput = $("addName");
    const urlInput  = $("addUrl");
    const name = nameInput ? nameInput.value.trim() : "";
    let url = urlInput ? urlInput.value.trim() : "";
    if(!name || !url){ if(typeof toast==="function") toast("Preencha nome e URL"); else alert("Preencha nome e URL"); return; }
    if(!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;

    let list = loadHive();
    if(!Array.isArray(list)) list = [];
    list.push({
        id: "s_" + Date.now().toString(36) + Math.random().toString(36).slice(2,7),
        name, url, uses:0, lastUsed:0, _userAdded:true
    });
    saveHive(list);
    closeAddDialog();
    renderHive();
}

function confirmRemove(){
    if(!hiveRemoveTarget){ closeRemoveDialog(); return; }
    const target = hiveRemoveTarget;
    if(target.pinned){ closeRemoveDialog(); return; } // proteção extra
    let list = loadHive();
    list = list.filter(i => i.id !== target.id);
    saveHive(list);
    closeRemoveDialog();
    renderHive();
}

function addHiveSite(){
    openAddDialog();
}

/* =========================
   BACKGROUND DIÁRIO
========================= */
function setDailyBackground(){
    const backgrounds = [
        "img/backgrounds/bg1.webp",
        "img/backgrounds/bg2.webp",
        "img/backgrounds/bg3.webp"
    ];
    const day = new Date().getDate();
    const bg  = backgrounds[day % backgrounds.length];
    document.documentElement.style.setProperty("--wasp-home-bg", `url('${bg}')`);
}

/* =========================
   LOGO POR TEMA
========================= */
function updateLogoByTheme(){
    const isLight = document.body.getAttribute("data-theme") === "light";
    const darkLogo  = document.querySelector(".logo-topbar-dark");
    const lightLogo = document.querySelector(".logo-topbar-light");
    if(darkLogo)  darkLogo.style.display  = isLight ? "none"  : "block";
    if(lightLogo) lightLogo.style.display = isLight ? "block" : "none";
}

/* =========================
   EVENT LISTENERS GLOBAIS
   (unificados — sem duplicatas)
========================= */

// Único handler de click para fechar menus ao clicar fora
document.addEventListener("click", (e) => {
    const bottomNav = document.querySelector(".bottom-nav");

    // Fechar engine menu
    const engineMenu   = $("engineMenu");
    const engineButton = document.querySelector(".search-engine");
    if(engineMenu && engineButton){
        if(engineMenu.classList.contains("active") &&
           !engineMenu.contains(e.target) &&
           !engineButton.contains(e.target)){
            engineMenu.classList.remove("active");
        }
    }

    // Fechar hive ao clicar fora — ignora cliques na bottom nav
    const hivePanel = $("hivePanel");
    if(hivePanel &&
       hivePanel.classList.contains("active") &&
       !hivePanel.contains(e.target) &&
       !(bottomNav && bottomNav.contains(e.target))){
        closeHive();
    }
});

// Único handler de pointerdown — engine menu, suggestions, hive
document.addEventListener("pointerdown", function(e){
    const addDialog     = $("addDialog");
    const removeDialog  = $("removeDialog");
    const settingsPanel = $("settingsPanel");
    const bottomNav     = document.querySelector(".bottom-nav");

    // Não fechar nada se estiver em dialog/settings/nav
    if(addDialog     && addDialog.contains(e.target))     return;
    if(removeDialog  && removeDialog.contains(e.target))  return;
    if(bottomNav     && bottomNav.contains(e.target))     return;
    if(settingsPanel && settingsPanel.classList.contains("active") && settingsPanel.contains(e.target)) return;

    const target = e.target;

    // Engine menu
    const engineMenu   = $("engineMenu");
    const engineButton = document.querySelector(".search-engine");
    if(engineMenu && engineButton){
        if(engineMenu.classList.contains("active") &&
           !engineMenu.contains(target) &&
           !engineButton.contains(target)){
            engineMenu.classList.remove("active");
        }
    }

    // Search suggestions
    const suggestions = $("searchSuggestions");
    const searchArea  = document.querySelector(".home-search");
    if(suggestions && searchArea){
        if(suggestions.classList.contains("active") && !searchArea.contains(target)){
            suggestions.classList.remove("active");
        }
    }

    // Hive panel — não fecha se o clique veio da nav
    const hivePanel = $("hivePanel");
    if(hivePanel){
        if(hivePanel.classList.contains("active") && !hivePanel.contains(target)){
            hivePanel.classList.remove("active");
            setBottomTab("main");
        }
    }
});

// Swipe para fechar o Hive (só fecha, nunca abre por gesto)
function _hiveDialogOpen(){
    const rd = $("removeDialog"), ad = $("addDialog");
    return (rd && rd.style.display === "flex") || (ad && ad.style.display === "flex");
}
document.addEventListener("touchstart", function(e){
    const panel = $("hivePanel");
    if(!panel || !panel.classList.contains("active")) return;
    if(_hiveDialogOpen()){ window._hiveSwipeStartY = null; return; } // diálogo aberto: ignora swipe
    const grid = $("hiveGrid");
    if(grid && grid.contains(e.target)) return;
    window._hiveSwipeStartY = e.touches[0].clientY;
});

document.addEventListener("touchend", function(e){
    if(window._hiveSwipeStartY == null) return;
    if(_hiveDialogOpen()){ window._hiveSwipeStartY = null; return; }
    const panel = $("hivePanel");
    if(!panel || !panel.classList.contains("active")){
        window._hiveSwipeStartY = null;
        return;
    }
    const diff = window._hiveSwipeStartY - e.changedTouches[0].clientY;
    window._hiveSwipeStartY = null;
    // Só fecha se swipe para baixo (diff negativo > 80px)
    if(diff < -80){
        closeHive();
    }
});

// Stoppers de propagação
(function(){
    const engineMenu = $("engineMenu");
    if(engineMenu) engineMenu.addEventListener("pointerdown", e => e.stopPropagation());

    const hivePanel = $("hivePanel");
    if(hivePanel) hivePanel.addEventListener("click", e => e.stopPropagation());
})();

/* =========================
   SAUDAÇÃO PERSONALIZADA
========================= */
const USER_NAME_KEY = "wasp_user_name";

function getUserName(){
    // 1. Nome definido nas settings
    const name = localStorage.getItem(USER_NAME_KEY);
    if(name && name.trim()) return name.trim();

    // 2. Fallback: wallet name da Bee Engine
    try {
        const bee = localStorage.getItem("wasp_bee_panel_switch_v4");
        if(bee){
            const state = JSON.parse(bee);
            if(state.walletName && state.walletName.trim()){
                return state.walletName.trim();
            }
        }
    } catch(e){}

    // 3. Sem nome
    return null;
}

function saveUserName(value){
    localStorage.setItem(USER_NAME_KEY, value.trim());
    initGreeting(); // atualiza saudação em tempo real
}

function initGreeting(){
    const el    = $("greetingText");
    const emoji = $("greetingEmoji");
    if(!el) return;

    const h = new Date().getHours();
    let saudacao, em;

    if(h >= 5  && h < 12){ saudacao = "Bom dia";    em = "☀️"; }
    else if(h >= 12 && h < 18){ saudacao = "Boa tarde";  em = "🌤"; }
    else if(h >= 18 && h < 23){ saudacao = "Boa noite";  em = "🌙"; }
    else                       { saudacao = "Olá";        em = "⭐"; }

    const nome = getUserName();
    el.textContent = nome ? saudacao + ", " + nome : saudacao + ", Wasp";
    if(emoji) emoji.textContent = em;
}

/* =========================
   CARD BEE ENGINE NA HOME
========================= */
function initBeeHomeCard(){
    updateBeeHomeCard();
    setInterval(updateBeeHomeCard, 30000);
}

function updateBeeHomeCard(){
    const statusEl = $("beeHomeStatus");
    const wpEl     = $("beeHomeWP");
    const saldoEl  = $("beeHomeSaldo") || $("beeHomeUptime");
    const card     = document.querySelector(".bee-home-card");

    if(!statusEl) return;

    // Saldo WP sempre do localStorage (Central WP)
    const saldo   = parseInt(localStorage.getItem("wasp_wp") || "0", 10);
    if(saldoEl) saldoEl.textContent = saldo;

    // WP ganhos hoje — lê chave diária simples
    function getWpToday(){
        try {
            const todayKey = "wasp_wp_today_" + new Date().toDateString();
            return parseInt(localStorage.getItem(todayKey) || "0", 10);
        } catch(_){ return 0; }
    }

    // Tenta bridge Android
    if(window.AndroidBee && typeof AndroidBee.getMiningStatus === "function"){
        try {
            const raw    = AndroidBee.getMiningStatus();
            const status = raw ? JSON.parse(raw) : null;

            if(status && status.running){
                statusEl.textContent = "Minerando ⚡";
                statusEl.style.color = "#37d67a";
                if(card) card.classList.add("bee-card-active");
                if(wpEl){
                    const wp = (status.wpToday || 0) + getWpToday();
                    wpEl.textContent = wp > 0 ? "+" + wp : "0";
                }
            } else {
                statusEl.textContent = "Toque para abrir";
                statusEl.style.color = "rgba(255,255,255,0.50)";
                if(card) card.classList.remove("bee-card-active");
                if(wpEl) wpEl.textContent = getWpToday() || "0";
            }
            return;
        } catch(e){}
    }

    // Sem bridge — mostra WP do localStorage
    statusEl.textContent = "Toque para abrir";
    statusEl.style.color = "rgba(255,255,255,0.50)";
    if(card) card.classList.remove("bee-card-active");
    if(wpEl) wpEl.textContent = getWpToday() || "0";
}

/* =========================
   SEARCH FOCUS OVERLAY
========================= */
function initSearchFocus(){
    const input = $("homeInput");
    const wrap  = $("homeSearchWrap");

    if(!input) return;

    input.addEventListener("focus", () => {
        if(wrap) wrap.classList.add("search-focused");
    });

    input.addEventListener("blur", () => {
        setTimeout(() => {
            if(wrap) wrap.classList.remove("search-focused");
        }, 150);
    });

    // Sticky search ao rolar — suave com rAF
    const homeEl = $("home");
    const hero   = document.querySelector(".home-hero");
    if(homeEl && hero){
        let ticking = false;
        let isScrolled = false;

        homeEl.addEventListener("scroll", () => {
            if(!ticking){
                requestAnimationFrame(() => {
                    const scrolled = homeEl.scrollTop > 60;
                    if(scrolled !== isScrolled){
                        isScrolled = scrolled;
                        hero.classList.toggle("scrolled", scrolled);
                    }
                    ticking = false;
                });
                ticking = true;
            }
        }, { passive: true });
    }
}

function blurSearch(){
    const input = $("homeInput");
    if(input) input.blur();
    const wrap = $("homeSearchWrap");
    if(wrap) wrap.classList.remove("search-focused");
    hideSuggestions();
}

/* =========================
   ÚNICO DOMContentLoaded
========================= */

/* =========================
   PRIVACIDADE
========================= */
const TRACKER_KEY = "wasp_block_trackers";

function togglePrivacyOptions(){
    const menu = $("privacyOptions");
    if(menu) menu.classList.toggle("active");
    // Sincroniza toggle com estado salvo
    const tog = $("trackerToggle");
    if(tog) tog.checked = localStorage.getItem(TRACKER_KEY) === "1";
}

function setTrackerBlock(enabled){
    localStorage.setItem(TRACKER_KEY, enabled ? "1" : "0");
    if(window.Android && typeof Android.setTrackerBlock === "function"){
        Android.setTrackerBlock(enabled);
    }
    showToast(enabled ? "🛡️ Rastreadores bloqueados" : "Bloqueio desativado");
}

function clearBrowsingHistory(){
    if(!confirm("Limpar todo o histórico de navegação?")) return;
    localStorage.removeItem(HISTORY_KEY);
    renderRecents();
    if(window.Android && typeof Android.clearBrowsingHistory==="function"){
        Android.clearBrowsingHistory();
    } else {
        showToast("🗑️ Histórico apagado");
    }
}

function clearCacheAndCookies(){
    if(!confirm("Limpar cache e cookies?")) return;
    if(window.Android && typeof Android.clearCacheAndCookies === "function"){
        Android.clearCacheAndCookies();
    }
    showToast("🍪 Cache e cookies apagados");
}

function showToast(msg){
    let t = document.getElementById("wasp-js-toast");
    if(!t){
        t = document.createElement("div");
        t.id = "wasp-js-toast";
        t.style.cssText = "position:fixed;bottom:100px;left:50%;transform:translateX(-50%);background:rgba(20,22,30,0.95);color:#fff;padding:10px 20px;border-radius:20px;font-size:13px;font-weight:600;z-index:999999;border:1px solid rgba(255,193,7,0.3);pointer-events:none;transition:opacity 0.3s;";
        document.body.appendChild(t);
    }
    t.textContent = msg;
    t.style.opacity = "1";
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.style.opacity = "0"; }, 2500);
}

document.addEventListener("DOMContentLoaded", () => {

    // sticky search removido

    // Saudação personalizada
    initGreeting();

    // Card Bee Engine na home
    initBeeHomeCard();

    // Overlay de foco da busca
    initSearchFocus();


    // Tema salvo
    const savedTheme = localStorage.getItem(THEME_KEY) || "dark";
    applyTheme(savedTheme);
    updateLogoByTheme();

    // Inicializações
    initEngine();
    initEngineSettingsLabel();

    // Corrige botões dos diálogos do Hive (remover/adicionar) que não
    // respondiam ao toque após long-press em alguns WebViews
    bindHiveDialogButtons();

    // Idioma atual do sistema
    (function(){
        const label = document.getElementById("currentLang");
        if(!label) return;
        const lang = navigator.language || navigator.userLanguage || "—";
        const names = {
            "pt":"Português","pt-BR":"Português (Brasil)","pt-PT":"Português (Portugal)",
            "en":"English","en-US":"English (US)","en-GB":"English (UK)",
            "es":"Español","fr":"Français","de":"Deutsch","it":"Italiano",
            "ja":"日本語","ko":"한국어","zh":"中文","ar":"العربية"
        };
        label.textContent = names[lang] || names[lang.split("-")[0]] || lang;
    })();

    // Bind engine buttons via addEventListener (capture) para evitar bloqueio
    const engineBtn = document.getElementById("engineBtn");
    if (engineBtn) {
        engineBtn.addEventListener("click", function(e){
            e.stopPropagation();
            toggleEngineMenu();
        }, true);
    }

    renderHive();
    renderRecents();
    openScreen("home");
    // Só aplica background se tema escuro
    if((localStorage.getItem(THEME_KEY) || "dark") !== "light"){
        setDailyBackground();
    }

    // Sugestões de busca
    const input = $("homeInput");
    if(input){
        function renderFromHistory(filter = ""){
            let history = loadHistory();
            if(filter){
                history = history.filter(item =>
                    item.q.toLowerCase().includes(filter.toLowerCase())
                );
            }
            history = history.slice(0, 5);
            if(history.length === 0){ hideSuggestions(); return; }
            showNativeSuggestions(history.map(h => ({ phrase: h.q })));
        }

        input.addEventListener("focus", () => renderFromHistory());

        input.addEventListener("input", async function(){
            const value = this.value.trim();
            if(value.length === 0){ renderFromHistory(); return; }

            let history = loadHistory()
                .filter(item => item.q.toLowerCase().includes(value.toLowerCase()))
                .slice(0, 3)
                .map(h => ({ phrase: h.q }));

            let google = [];
            try { google = await fetchGoogleSuggestions(value); } catch(e){}

            const combined = [...history, ...google].slice(0, 6);
            if(combined.length > 0){
                showNativeSuggestions(combined);
            } else {
                hideSuggestions();
            }
        });
    }

    // Mercado (carga inicial + intervalo)
    waspUpdateMarketAll();
    setInterval(waspUpdateMarketAll, 60000);

    // Wasp Blog feed (cache de 6h)
    if (typeof loadWaspFeed === "function") {
        loadWaspFeed();
    }

    // Tap game (se existir)
    if(typeof startWaspTapGame === "function"){
        startWaspTapGame();
    }

    // Hints Hive
    showHiveHint();
    demoHiveFirstTime();
});

/* =========================
   LOAD EVENTS
========================= */
window.addEventListener("load", () => {
    document.body.style.opacity = "1";
});