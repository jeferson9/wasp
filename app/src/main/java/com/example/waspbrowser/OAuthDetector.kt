package com.example.waspbrowser

object OAuthDetector {

    private val loginKeywords = listOf(
        "accounts.google.com",
        "oauth",
        "login",
        "signin",
        "auth",
        "telegram",
        "facebook.com/login",
        "appleid",
        "coinmarketcap.com/login",
        "binance.com/login"
    )

    fun isLoginUrl(url: String?): Boolean {

        if (url == null) return false

        val lower = url.lowercase()

        return loginKeywords.any { lower.contains(it) }
    }
}