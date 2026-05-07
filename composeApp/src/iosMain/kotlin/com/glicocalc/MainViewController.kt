package com.glicocalc

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.database.GlicoRepository
import com.glicocalc.sync.IosSyncController
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp
import com.glicocalc.ui.customAppLocale
import com.glicocalc.ui.customFoodLocale
import com.glicocalc.ui.hasLoadedPersistedAppLocale
import com.glicocalc.ui.hasLoadedPersistedFoodLocale

fun MainViewController(syncController: IosSyncController) = ComposeUIViewController {
    val repository = remember {
        val driver = DatabaseDriverFactory().createDriver()
        GlicoRepository(GlicoDatabase(driver), driver).also {
            it.migrateSchemaIfNeeded()
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
        syncAccountLabel = syncController.syncAccountLabel,
        syncAccountStatusMessage = syncController.syncAccountStatusMessage,
        syncStatusMessage = syncController.syncStatusMessage,
        lastSyncedMessage = syncController.lastSyncedMessage,
        onSignInToSync = if (syncController.canSignIn) syncController.onSignInRequested else null,
        onSwitchSyncAccount = if (syncController.canSignIn && syncController.syncAccountLabel != null) {
            syncController.onSwitchAccountRequested
        } else {
            null
        },
        onSignOutFromSync = if (syncController.canSignIn) syncController.onSignOutRequested else null,
        onManualSync = if (syncController.canManualSync) syncController.onManualSyncRequested else null
    )
}
