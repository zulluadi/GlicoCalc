package com.glicocalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

actual object LocalAppLocale {
    private const val languageKey = "AppleLanguages"
    private val defaultLocale =
        ((NSUserDefaults.standardUserDefaults.objectForKey(languageKey) as? List<*>)?.firstOrNull() as? String)
            ?: "en"
    private val appLocale = staticCompositionLocalOf { defaultLocale }

    actual val current: String
        @Composable get() = appLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val newLocale = value ?: defaultLocale
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(languageKey)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(listOf(newLocale), languageKey)
        }
        return appLocale.provides(newLocale)
    }
}
