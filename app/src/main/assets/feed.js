/* =======================================================
   WASP BLOG FEED — feed.js
   Usa JSONP (script tag) para evitar CORS no WebView Android
   Cache de 6h no localStorage
======================================================= */

const FEED_BASE       = "https://waspblogblockchain.blogspot.com/feeds/posts/default";
const FEED_CACHE_KEY  = "wasp_feed_cache_v1";
const FEED_TIME_KEY   = "wasp_feed_time_v1";
const FEED_TTL        = 6 * 60 * 60 * 1000; // 6 horas

/* -------------------------------------------------------
   HELPERS
------------------------------------------------------- */

function feedFormatDate(dateStr) {
    try {
        const d = new Date(dateStr);
        const locale = (new URLSearchParams(location.search).get("lang") || navigator.language || "en").slice(0,2);
        return d.toLocaleDateString(locale, {
            day:   "2-digit",
            month: "short",
            year:  "numeric"
        });
    } catch(e) {
        return "";
    }
}

function feedExtractImage(entry) {
    if (entry.media$thumbnail?.url) {
        return entry.media$thumbnail.url.replace(/\/s\d+-c\//, "/s400/");
    }
    const content = entry.content?.$t || entry.summary?.$t || "";
    const match = content.match(/<img[^>]+src=["']([^"']+)["']/i);
    return match ? match[1] : null;
}

function feedExtractLink(entry) {
    const links = entry.link || [];
    const alt = links.find(l => l.rel === "alternate");
    return alt?.href || "";
}

function feedSafeText(str, maxLen) {
    maxLen = maxLen || 80;
    const raw = (str || "")
        .replace(/<[^>]*>/g, "")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g,  "<")
        .replace(/&gt;/g,  ">")
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .trim();
    return raw.length > maxLen ? raw.slice(0, maxLen) + "\u2026" : raw;
}

/* -------------------------------------------------------
   RENDER
------------------------------------------------------- */

function feedRender(posts) {
    const container = document.getElementById("waspFeedList");
    if (!container) return;

    if (!posts || posts.length === 0) {
        container.innerHTML =
            '<div class="feed-empty">Nenhum post encontrado.<br>' +
            '<a onclick="openNativeUrl(\'https://waspblogblockchain.blogspot.com\')">Abrir blog \u2192</a></div>';
        return;
    }

    container.innerHTML = posts.map(function(post) {
        var title  = feedSafeText(post.title && post.title.$t, 72);
        var date   = feedFormatDate(post.published && post.published.$t);
        var link   = feedExtractLink(post);
        var imgUrl = feedExtractImage(post);

        var imgHTML = imgUrl
            ? '<img class="feed-card-img" src="' + imgUrl + '" alt="" loading="lazy" onerror="this.style.display=\'none\'">'
            : '<div class="feed-card-img feed-card-img--placeholder">\uD83D\uDCF0</div>';

        return (
            '<div class="feed-card" onclick="openNativeUrl(\'' + link + '\')">' +
                imgHTML +
                '<div class="feed-card-body">' +
                    '<div class="feed-card-title">' + title + '</div>' +
                    '<div class="feed-card-date">'  + date  + '</div>' +
                '</div>' +
            '</div>'
        );
    }).join("");
}

function feedShowSkeleton() {
    var container = document.getElementById("waspFeedList");
    if (!container) return;
    var sk =
        '<div class="feed-skeleton">' +
            '<div class="feed-sk-img"></div>' +
            '<div class="feed-sk-lines">' +
                '<div class="feed-sk-line long"></div>' +
                '<div class="feed-sk-line short"></div>' +
            '</div>' +
        '</div>';
    container.innerHTML = sk + sk + sk;
}

function feedShowError() {
    var container = document.getElementById("waspFeedList");
    if (!container) return;
    container.innerHTML =
        '<div class="feed-empty">Could not load.<br>' +
        '<a onclick="loadWaspFeed()">Try again \u2192</a></div>';
}

/* -------------------------------------------------------
   CACHE
------------------------------------------------------- */

function feedLoadCache() {
    try {
        var raw = localStorage.getItem(FEED_CACHE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch(e) {
        return null;
    }
}

function feedSaveCache(posts) {
    try {
        localStorage.setItem(FEED_CACHE_KEY, JSON.stringify(posts));
        localStorage.setItem(FEED_TIME_KEY, String(Date.now()));
    } catch(e) {}
}

function feedCacheExpired() {
    var t = parseInt(localStorage.getItem(FEED_TIME_KEY) || "0", 10);
    return Date.now() - t > FEED_TTL;
}

/* -------------------------------------------------------
   JSONP — evita CORS no WebView Android
   O Blogger suporta ?alt=json&callback=NOME nativamente
------------------------------------------------------- */

function feedFetchJSONP(maxResults) {
    return new Promise(function(resolve, reject) {

        var cbName = "waspFeedCb_" + Date.now();
        var script;
        var timer;

        window[cbName] = function(data) {
            clearTimeout(timer);
            cleanup();
            var posts = (data && data.feed && data.feed.entry) ? data.feed.entry : [];
            resolve(posts);
        };

        function cleanup() {
            try { delete window[cbName]; } catch(e) { window[cbName] = undefined; }
            if (script && script.parentNode) {
                script.parentNode.removeChild(script);
            }
        }

        // Timeout de 12 segundos
        timer = setTimeout(function() {
            cleanup();
            reject(new Error("timeout"));
        }, 12000);

        var url = FEED_BASE +
            "?alt=json" +
            "&max-results=" + (maxResults || 8) +
            "&callback=" + cbName;

        script = document.createElement("script");
        script.src = url;

        script.onerror = function() {
            clearTimeout(timer);
            cleanup();
            reject(new Error("script error"));
        };

        document.head.appendChild(script);
    });
}

/* -------------------------------------------------------
   INIT — chamado pelo app.js no DOMContentLoaded
------------------------------------------------------- */

async function loadWaspFeed() {
    var container = document.getElementById("waspFeedList");
    if (!container) return;

    // Cache válido → renderiza direto sem skeleton
    var cached = feedLoadCache();
    if (cached && !feedCacheExpired()) {
        feedRender(cached);
        return;
    }

    // Skeleton enquanto busca
    feedShowSkeleton();

    try {
        var posts = await feedFetchJSONP(8);
        feedSaveCache(posts);
        feedRender(posts);
    } catch(e) {
        // Fallback para cache antigo se offline
        if (cached && cached.length > 0) {
            feedRender(cached);
        } else {
            feedShowError();
        }
    }
}

window.loadWaspFeed = loadWaspFeed;