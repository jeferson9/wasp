package com.waspbrowser.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Application class do Wasp.
 * Garante que o idioma efetivo do app seja SEMPRE o que está salvo em
 * "wasp_settings" / PREF_LANGUAGE (fonte única de verdade), aplicado no
 * startup. Isso evita que o locale persistido pelo AndroidX fique
 * dessincronizado do seletor de idioma (causa de o app "grudar" num idioma).
 */
class WaspApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applySavedLocale()
    }

    private fun applySavedLocale() {
        val code = getSharedPreferences("wasp_settings", MODE_PRIVATE)
            .getString(SettingsActivity.PREF_LANGUAGE, SettingsActivity.LANG_SYSTEM)
            ?: SettingsActivity.LANG_SYSTEM

        val localeList = if (code == SettingsActivity.LANG_SYSTEM) {
            // Segue o idioma do aparelho: lista vazia = sem override.
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(code)
        }

        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
