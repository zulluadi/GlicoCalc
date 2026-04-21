package com.glicocalc

import android.os.Bundle
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.glicocalc.database.DatabaseDriverFactory
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.database.GlicoRepository
import com.glicocalc.sync.FirebaseFoodSyncManager
import com.glicocalc.sync.SyncStatus
import com.glicocalc.sync.SyncUiState
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp
import com.glicocalc.ui.customAppLocale
import com.glicocalc.ui.customFoodLocale
import com.glicocalc.ui.hasLoadedPersistedAppLocale
import com.glicocalc.ui.hasLoadedPersistedFoodLocale
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var resumeSignal by mutableStateOf(0)
    private var syncAccountLabel by mutableStateOf<String?>(null)
    private var syncUiState by mutableStateOf(SyncUiState(status = SyncStatus.IDLE, pendingCount = 0, isSignedIn = false))
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: GlicoRepository
    private lateinit var foodSyncManager: FirebaseFoodSyncManager
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialManager = CredentialManager.create(this)

        val driverFactory = DatabaseDriverFactory(this)
        val driver = driverFactory.createDriver()
        val database = GlicoDatabase(driver)
        repository = GlicoRepository(database)

        repository.seedInitialData()
        repository.prepareBaseFoodCatalog()

        foodSyncManager = FirebaseFoodSyncManager(
            context = applicationContext,
            repository = repository,
            scope = syncScope
        )
        repository.onFoodsChanged = foodSyncManager::requestSync
        foodSyncManager.onAccountStateChanged = { label ->
            runOnUiThread {
                syncAccountLabel = label
                syncUiState = foodSyncManager.currentSyncUiState(syncUiState.status)
            }
        }
        foodSyncManager.onSyncStateChanged = { state ->
            runOnUiThread {
                syncUiState = state
            }
        }
        foodSyncManager.start()
        syncAccountLabel = foodSyncManager.currentSyncAccountLabel()
        syncUiState = foodSyncManager.currentSyncUiState()

        customAppLocale = repository.getLanguage()
        customFoodLocale = repository.getFoodLanguage()
        hasLoadedPersistedAppLocale = true
        hasLoadedPersistedFoodLocale = true

        setContent {
            MainApp(
                repository = repository,
                telemetry = NoopTelemetry,
                syncAccountLabel = syncAccountLabel,
                syncAccountStatusMessage = syncAccountStatusMessage(),
                syncStatusMessage = syncStatusMessage(),
                lastSyncedMessage = lastSyncedMessage(),
                onSignInToSync = if (canOfferGoogleSignIn()) ::launchGoogleSignIn else null,
                onSignOutFromSync = if (canOfferGoogleSignIn()) ::signOutFromSync else null,
                onManualSync = if (foodSyncManager.isEnabled) foodSyncManager::requestSync else null,
                resumeSignal = resumeSignal
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeSignal += 1
        syncAccountLabel = foodSyncManager.currentSyncAccountLabel()
        syncUiState = foodSyncManager.currentSyncUiState()
    }

    override fun onDestroy() {
        foodSyncManager.stop()
        syncScope.cancel()
        super.onDestroy()
    }

    private fun canOfferGoogleSignIn(): Boolean {
        return foodSyncManager.isEnabled && googleWebClientId() != null
    }

    private fun syncAccountStatusMessage(): String? {
        if (syncAccountLabel != null) return null
        return when {
            googleWebClientId() == null -> "Google sign-in is unavailable because the Firebase config is missing the web client ID."
            !foodSyncManager.isEnabled -> "Sync sign-in is unavailable until Firebase is configured correctly."
            else -> null
        }
    }

    private fun syncStatusMessage(): String {
        return when {
            !syncUiState.isSignedIn -> "Sign in to sync foods and dishes."
            syncUiState.status == SyncStatus.SYNCING -> "Syncing now..."
            syncUiState.status == SyncStatus.ERROR -> "Last sync failed. You can retry manually."
            syncUiState.status == SyncStatus.UNAVAILABLE -> "Sync sign-in is unavailable until Firebase is configured correctly."
            syncUiState.pendingCount > 0 -> "Pending changes: ${syncUiState.pendingCount}"
            else -> "Up to date"
        }
    }

    private fun lastSyncedMessage(): String? {
        val lastSuccessfulSyncAtMillis = syncUiState.lastSuccessfulSyncAtMillis ?: return null
        val formattedTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(lastSuccessfulSyncAtMillis))
        return "Last synced: $formattedTime"
    }

    private fun googleWebClientId(): String? {
        val resourceId = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (resourceId == 0) return null
        return getString(resourceId).takeIf { it.isNotBlank() }
    }

    private fun launchGoogleSignIn() {
        val serverClientId = googleWebClientId()
        if (serverClientId == null) {
            showToast("Google Sign-In is not configured yet.")
            return
        }

        lifecycleScope.launch {
            try {
                val credential = requestGoogleCredential(serverClientId, true)
                    ?: requestGoogleCredential(serverClientId, false)
                    ?: return@launch

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    foodSyncManager.linkOrSignIn(firebaseCredential)
                    showToast("Sync account connected.")
                } else {
                    showToast("Google Sign-In did not return a valid credential.")
                }
            } catch (exception: GetCredentialException) {
                showToast(exception.localizedMessage ?: "Google Sign-In failed.")
            } catch (exception: Exception) {
                showToast(exception.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    private suspend fun requestGoogleCredential(
        serverClientId: String,
        authorizedOnly: Boolean
    ): Credential? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            credentialManager.getCredential(this, request).credential
        } catch (_: NoCredentialException) {
            null
        }
    }

    private fun signOutFromSync() {
        lifecycleScope.launch {
            try {
                foodSyncManager.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: ClearCredentialException) {
                // Firebase sign-out still succeeded.
            } finally {
                syncAccountLabel = null
                foodSyncManager.requestSync()
                showToast("Sync account disconnected.")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
