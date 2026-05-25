package com.waspbrowser.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AboutActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.setBackgroundColor(0xFF08090D.toInt())
        setContentView(wv)

        val version = packageManager.getPackageInfo(packageName, 0).versionName

        wv.loadDataWithBaseURL(null, buildHtml(version), "text/html", "UTF-8", null)
    }

    private fun buildHtml(version: String) = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body {
    background: #08090D;
    color: #e8e8e8;
    font-family: -apple-system, sans-serif;
    padding: 48px 24px 40px;
    min-height: 100vh;
  }
  .logo-wrap {
    display: flex; align-items: center; gap: 14px;
    margin-bottom: 6px;
  }
  .logo { font-size: 38px; }
  .app-name {
    font-size: 26px; font-weight: 800;
    color: #ffc107; letter-spacing: -.5px;
  }
  .app-sub {
    font-size: 12px; color: rgba(255,255,255,0.35);
    letter-spacing: .08em; text-transform: uppercase;
    margin-bottom: 32px;
  }
  .desc {
    font-size: 14px; line-height: 1.7;
    color: rgba(255,255,255,0.6);
    margin-bottom: 32px;
    padding-bottom: 32px;
    border-bottom: 1px solid rgba(255,255,255,0.06);
  }
  .section-title {
    font-size: 10px; font-weight: 700;
    color: rgba(255,193,7,0.5);
    letter-spacing: .14em; text-transform: uppercase;
    margin-bottom: 14px;
  }
  .info-row {
    display: flex; justify-content: space-between; align-items: center;
    padding: 13px 0;
    border-bottom: 1px solid rgba(255,255,255,0.05);
    font-size: 14px;
  }
  .info-row:last-child { border-bottom: none; }
  .info-label { color: rgba(255,255,255,0.45); }
  .info-value { color: #e8e8e8; font-weight: 500; }
  .info-value.amber { color: #ffc107; }
  .card {
    background: rgba(255,255,255,0.03);
    border: 1px solid rgba(255,255,255,0.06);
    border-radius: 14px;
    padding: 4px 16px;
    margin-bottom: 28px;
  }
  .engine-row {
    display: flex; align-items: center; gap: 12px;
    padding: 14px 0;
    border-bottom: 1px solid rgba(255,255,255,0.05);
  }
  .engine-row:last-child { border-bottom: none; }
  .engine-icon { font-size: 22px; width: 32px; text-align: center; }
  .engine-info { flex:1; }
  .engine-name { font-size: 13px; font-weight: 600; color: #e8e8e8; }
  .engine-desc { font-size: 11px; color: rgba(255,255,255,0.35); margin-top: 2px; }
  .footer {
    text-align: center; margin-top: 12px;
    font-size: 11px; color: rgba(255,255,255,0.2);
    letter-spacing: .04em;
  }
</style>
</head>
<body>

<div class="logo-wrap">
  <div class="logo">🐝</div>
  <div class="app-name">WASP Browser</div>
</div>
<div class="app-sub">Web3 · Mining · Privacy</div>

<p class="desc">
  Browser Web3 com mineração NACKL integrada. 
  Navegue, mine e acumule Wasp Points enquanto usa a internet.
</p>

<div class="section-title">Informações</div>
<div class="card">
  <div class="info-row">
    <span class="info-label">Versão</span>
    <span class="info-value amber">$version</span>
  </div>
  <div class="info-row">
    <span class="info-label">Package</span>
    <span class="info-value">com.waspbrowser.app</span>
  </div>
  <div class="info-row">
    <span class="info-label">Plataforma</span>
    <span class="info-value">Android</span>
  </div>
</div>

<div class="section-title">Motores</div>
<div class="card">
  <div class="engine-row">
    <div class="engine-icon">🦊</div>
    <div class="engine-info">
      <div class="engine-name">Motor de Navegação</div>
      <div class="engine-desc">GeckoView — Firefox</div>
    </div>
  </div>
  <div class="engine-row">
    <div class="engine-icon">⛏️</div>
    <div class="engine-info">
      <div class="engine-name">Motor de Mineração</div>
      <div class="engine-desc">Bee Engine — Acki Nacki</div>
    </div>
  </div>
</div>

<div class="footer">© 2025 Wasp Browser · Todos os direitos reservados</div>

</body>
</html>
    """.trimIndent()
}
