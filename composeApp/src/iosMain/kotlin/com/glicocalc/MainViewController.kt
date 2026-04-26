package com.glicocalc

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.database.GlicoRepository
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp
import com.glicocalc.ui.customAppLocale
import com.glicocalc.ui.customFoodLocale
import com.glicocalc.ui.hasLoadedPersistedAppLocale
import com.glicocalc.ui.hasLoadedPersistedFoodLocale

fun MainViewController() = ComposeUIViewController {
    val repository = remember {
        val driver = DatabaseDriverFactory().createDriver()
        GlicoRepository(GlicoDatabase(driver)).also {
            it.seedInitialData()
            it.prepareBaseFoodCatalog()
        }
    }

    remember(repository) {
        customAppLocale = repository.getLanguage()
        customFoodLocale = repository.getFoodLanguage()
        hasLoadedPersistedAppLocale = true
        hasLoadedPersistedFoodLocale = true
    }

    MainApp(
        repository = repository,
        telemetry = NoopTelemetry,
        syncAccountStatusMessage = "Sync is available on Android only.",
        syncStatusMessage = "Local data only"
    )
}
