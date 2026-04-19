package com.glicocalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

var customAppLocale by mutableStateOf<String?>(null)

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun AppEnvironment(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLocale provides customAppLocale,
    ) {
        key(customAppLocale) {
            content()
        }
    }
}

data class AppLanguageOption(
    val code: String?,
    val label: String
)

val appLanguageOptions = listOf(
    AppLanguageOption(null, "System default"),
    AppLanguageOption("en", "English"),
    AppLanguageOption("ro", "Română"),
    AppLanguageOption("es", "Español"),
    AppLanguageOption("fr", "Français"),
    AppLanguageOption("de", "Deutsch"),
    AppLanguageOption("it", "Italiano"),
    AppLanguageOption("pt", "Português"),
    AppLanguageOption("nl", "Nederlands"),
    AppLanguageOption("pl", "Polski"),
    AppLanguageOption("cs", "Čeština"),
    AppLanguageOption("sk", "Slovenčina"),
    AppLanguageOption("hu", "Magyar"),
    AppLanguageOption("bg", "Български"),
    AppLanguageOption("el", "Ελληνικά"),
    AppLanguageOption("tr", "Türkçe"),
    AppLanguageOption("uk", "Українська"),
    AppLanguageOption("ru", "Русский"),
    AppLanguageOption("sr", "Srpski"),
    AppLanguageOption("hr", "Hrvatski"),
    AppLanguageOption("sl", "Slovenščina"),
    AppLanguageOption("da", "Dansk"),
    AppLanguageOption("sv", "Svenska"),
    AppLanguageOption("no", "Norsk"),
    AppLanguageOption("fi", "Suomi"),
    AppLanguageOption("et", "Eesti"),
    AppLanguageOption("lv", "Latviešu"),
    AppLanguageOption("lt", "Lietuvių"),
    AppLanguageOption("ar", "العربية"),
    AppLanguageOption("he", "עברית"),
    AppLanguageOption("zh", "中文")
)
