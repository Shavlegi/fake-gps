package com.shavlegi.fakegps

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the UI language (ka / en / ru).
 * AppCompatDelegate persists the choice itself, so it survives restarts.
 */
object LocaleHelper {
    fun applyLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }
}
