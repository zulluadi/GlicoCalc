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
        syncController.onRefreshFamilyMembersRequested = { repository.getAllFamilyMembers() }
        syncController.onRefreshFamilyIdRequested = { repository.getFamilyId() }
        syncController.onRefreshFamilyNameRequested = { repository.getFamilyName() }
        syncController.refreshFamilyMembers()
    }

    MainApp(
        repository = repository,
        telemetry = NoopTelemetry,
        familyMembers = syncController.familyMembers,
        familyId = syncController.familyId,
        familyName = syncController.familyName,
        isSignedIn = syncController.isSignedIn,
        syncStatusMessage = syncController.syncStatusMessage,
        lastSyncedMessage = syncController.lastSyncedMessage,
        onSignInToSync = if (!syncController.isSignedIn) syncController.onSignInRequested else null,
        onSignOutFromSync = if (syncController.isSignedIn) syncController.onSignOutRequested else null,
        onManualSync = if (syncController.canManualSync) syncController.onManualSyncRequested else null,
        onAddFamilyMember = syncController.onAddFamilyMemberRequested,
        onRemoveFamilyMember = syncController.onRemoveFamilyMemberRequested
    )
}
