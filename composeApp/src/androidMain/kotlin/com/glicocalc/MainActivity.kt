package com.glicocalc

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
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
import com.glicocalc.database.FamilyMember
import com.glicocalc.database.FoodSource
import com.glicocalc.database.GlicoDatabase
import com.glicocalc.database.GlicoRepository
import com.glicocalc.sync.FirebaseFoodSyncManager
import com.glicocalc.sync.SyncStatus
import com.glicocalc.sync.SyncUiState
import com.glicocalc.telemetry.NoopTelemetry
import com.glicocalc.ui.MainApp
import com.glicocalc.ui.customAppLocale
import com.glicocalc.ui.familyIdFromQrPayload
import com.glicocalc.ui.customFoodLocale
import com.glicocalc.ui.hasLoadedPersistedAppLocale
import com.glicocalc.ui.hasLoadedPersistedFoodLocale
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    private var resumeSignal by mutableStateOf(0)
    private var familyMembers by mutableStateOf<List<FamilyMember>>(emptyList())
    private var familyId by mutableStateOf<String?>(null)
    private var familyName by mutableStateOf<String?>(null)
    private var pendingFamilyInviteLabel by mutableStateOf<String?>(null)
    private var syncIntervalMinutes by mutableStateOf(10)
    private var syncUiState by mutableStateOf(SyncUiState(status = SyncStatus.IDLE, pendingCount = 0, isSignedIn = false))
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var repository: GlicoRepository
    private lateinit var foodSyncManager: FirebaseFoodSyncManager
    private lateinit var credentialManager: CredentialManager
    private lateinit var familyQrScanLauncher: ActivityResultLauncher<ScanOptions>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialManager = CredentialManager.create(this)
        familyQrScanLauncher = registerForActivityResult(ScanContract()) { result ->
            val familyId = result.contents?.let(::familyIdFromQrPayload)
            if (familyId == null) {
                showToast("No family QR code was scanned.")
            } else {
                joinFamilyById(familyId)
            }
        }

        val driverFactory = DatabaseDriverFactory(this)
        val driver = driverFactory.createDriver()
        val database = GlicoDatabase(driver)
        repository = GlicoRepository(database, driver)

        foodSyncManager = FirebaseFoodSyncManager(
            context = applicationContext,
            repository = repository,
            scope = syncScope
        )
        repository.onFoodsChanged = foodSyncManager::requestSync
        foodSyncManager.onAccountStateChanged = { _ ->
            runOnUiThread {
                refreshFamilyMembers()
                refreshPendingFamilyInvite()
                syncUiState = foodSyncManager.currentSyncUiState(syncUiState.status)
            }
        }
        foodSyncManager.onSyncStateChanged = { state ->
            runOnUiThread {
                syncUiState = state
                refreshFamilyMembers()
                refreshPendingFamilyInvite()
            }
        }
        syncUiState = foodSyncManager.currentSyncUiState()

        lifecycleScope.launch(Dispatchers.IO) {
            repository.migrateSchemaIfNeeded()
            repository.seedInitialData()
            repository.prepareBaseFoodCatalog()

            val interval = repository.getSyncIntervalMinutes()
            val language = repository.getLanguage()
            val foodLanguage = repository.getFoodLanguage()
            val members = repository.getAllFamilyMembers()

            runOnUiThread {
                syncIntervalMinutes = interval
                customAppLocale = language
                customFoodLocale = foodLanguage
                hasLoadedPersistedAppLocale = true
                hasLoadedPersistedFoodLocale = true
                familyMembers = members
                familyId = foodSyncManager.getCurrentFamilyId()
                familyName = foodSyncManager.getCurrentFamilyName()
                foodSyncManager.start()
            }
        }

        setContent {
            MainApp(
                repository = repository,
                telemetry = NoopTelemetry,
                familyMembers = familyMembers,
                familyId = foodSyncManager.getCurrentFamilyId(),
                familyName = familyName,
                currentUserEmail = foodSyncManager.currentUserEmail(),
                isFamilyOwner = foodSyncManager.isCurrentUserFamilyOwner(),
                pendingFamilyInviteLabel = pendingFamilyInviteLabel,
                isSignedIn = syncUiState.isSignedIn,
                syncStatusMessage = syncStatusMessage(),
                lastSyncedMessage = lastSyncedMessage(),
                syncIntervalMinutes = syncIntervalMinutes,
                onSignInToSync = if (canOfferGoogleSignIn()) ::launchGoogleSignIn else null,
                onSignOutFromSync = if (canOfferGoogleSignIn()) ::signOutFromSync else null,
                onManualSync = if (foodSyncManager.isEnabled) foodSyncManager::requestSync else null,
                onSyncIntervalChanged = ::updateSyncIntervalMinutes,
                onScanFamilyQr = ::scanFamilyQr,
                onFamilyQrDialogClosed = foodSyncManager::requestSync,
                onAddFamilyMember = ::addFamilyMember,
                onRemoveFamilyMember = ::removeFamilyMember,
                onUpdateFamilyName = ::updateFamilyName,
                onLeaveFamily = ::leaveFamily,
                onJoinPendingFamilyInvite = ::joinPendingFamilyInvite,
                onJoinFamilyById = ::joinFamilyById,
                onPermanentlyDeleteFood = ::permanentlyDeleteFood,
                onPermanentlyDeleteDish = ::permanentlyDeleteDish,
                resumeSignal = resumeSignal
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeSignal += 1
        lifecycleScope.launch(Dispatchers.IO) {
            val members = repository.getAllFamilyMembers()
            runOnUiThread {
                familyMembers = members
            }
        }
        familyId = foodSyncManager.getCurrentFamilyId()
        familyName = foodSyncManager.getCurrentFamilyName()
        refreshPendingFamilyInvite()
        syncUiState = foodSyncManager.currentSyncUiState()
    }

    override fun onDestroy() {
        foodSyncManager.stop()
        syncScope.cancel()
        super.onDestroy()
    }

    private fun refreshFamilyMembers() {
        familyMembers = repository.getAllFamilyMembers()
        familyId = foodSyncManager.getCurrentFamilyId()
        familyName = foodSyncManager.getCurrentFamilyName()
    }

    private fun refreshPendingFamilyInvite() {
        if (!foodSyncManager.isEnabled || foodSyncManager.currentUserEmail() == null) {
            pendingFamilyInviteLabel = null
            return
        }
        syncScope.launch {
            val inviteLabel = try {
                foodSyncManager.pendingFamilyInviteLabel()
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to refresh pending family invite.", exception)
                null
            }
            runOnUiThread {
                pendingFamilyInviteLabel = inviteLabel?.takeIf { it != familyId }
            }
        }
    }

    private fun addFamilyMember(email: String, name: String) {
        if (repository.getFamilyMemberByEmail(email) == null) {
            repository.addFamilyMember(email = email, name = name)
        }
        if (repository.getFamilyOwnerUid() == null) {
            repository.setFamilyOwner(email)
        }
        syncScope.launch {
            foodSyncManager.inviteFamilyMember(email, name)
        }
        refreshFamilyMembers()
    }

    private fun removeFamilyMember(email: String) {
        val member = repository.getFamilyMemberByEmail(email)
        repository.removeFamilyMember(email)
        syncScope.launch {
            member?.firebaseUid?.let { uid ->
                foodSyncManager.removeMemberFromFamily(uid)
            }
            foodSyncManager.removeFamilyInvite(email)
        }
        refreshFamilyMembers()
        foodSyncManager.requestSync()
    }

    private fun updateFamilyName(name: String?) {
        syncScope.launch {
            foodSyncManager.updateFamilyName(name)
            runOnUiThread {
                refreshFamilyMembers()
            }
        }
    }

    private fun leaveFamily() {
        syncScope.launch {
            foodSyncManager.leaveCurrentFamily()
            runOnUiThread {
                refreshFamilyMembers()
                syncUiState = foodSyncManager.currentSyncUiState(SyncStatus.SYNCING)
            }
            foodSyncManager.requestSync()
        }
    }

    private fun joinPendingFamilyInvite() {
        syncScope.launch {
            val joined = try {
                foodSyncManager.joinPendingFamilyInvite()
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to join pending family invite.", exception)
                false
            }
            runOnUiThread {
                refreshFamilyMembers()
                pendingFamilyInviteLabel = null
                syncUiState = foodSyncManager.currentSyncUiState(if (joined) SyncStatus.SYNCING else syncUiState.status)
            }
            if (joined) {
                foodSyncManager.requestSync()
            }
        }
    }

    private fun joinFamilyById(familyId: String) {
        syncScope.launch {
            val joined = try {
                foodSyncManager.joinFamilyById(familyId)
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to join family by ID.", exception)
                false
            }
            runOnUiThread {
                refreshFamilyMembers()
                pendingFamilyInviteLabel = null
                syncUiState = foodSyncManager.currentSyncUiState(if (joined) SyncStatus.SYNCING else syncUiState.status)
            }
            if (joined) {
                foodSyncManager.requestSync()
            }
        }
    }

    private fun scanFamilyQr() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan a GlicoCalc family QR code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        familyQrScanLauncher.launch(options)
    }

    private fun permanentlyDeleteFood(foodId: Long) {
        val food = repository.getBaseFood(foodId) ?: return
        syncScope.launch {
            if (food.source == FoodSource.CUSTOM.value) {
                food.remoteKey?.let { foodSyncManager.permanentlyDeleteFood(it) }
            }
            repository.permanentlyDeleteBaseFood(foodId)
        }
    }

    private fun permanentlyDeleteDish(dishId: Long) {
        val dish = repository.getDishWithComposition(dishId)?.dish ?: return
        syncScope.launch {
            dish.remoteKey?.let { foodSyncManager.permanentlyDeleteDish(it) }
            repository.permanentlyDeleteDish(dishId)
        }
    }

    private fun updateSyncIntervalMinutes(minutes: Int) {
        repository.saveSyncIntervalMinutes(minutes)
        syncIntervalMinutes = repository.getSyncIntervalMinutes()
        foodSyncManager.restartPeriodicSync()
    }

    private fun canOfferGoogleSignIn(): Boolean {
        return foodSyncManager.isEnabled && googleWebClientId() != null
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
                val credential = requestExplicitGoogleSignIn(serverClientId)
                    ?: requestGoogleCredential(serverClientId, false)
                    ?: run {
                        showToast("Google Sign-In could not start. Check the release signing SHA in Firebase.")
                        return@launch
                    }

                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    foodSyncManager.linkOrSignIn(firebaseCredential)
                    refreshFamilyMembers()
                    showToast("Sync account connected.")
                } else {
                    showToast("Google Sign-In did not return a valid credential.")
                }
            } catch (exception: GetCredentialException) {
                Log.w(TAG, "Google credential request failed.", exception)
                showToast(googleSignInErrorMessage(exception))
            } catch (exception: FirebaseException) {
                Log.w(TAG, "Firebase Google sign-in failed.", exception)
                showToast(googleSignInErrorMessage(exception))
            } catch (exception: Exception) {
                Log.w(TAG, "Unexpected Google sign-in failure.", exception)
                showToast(googleSignInErrorMessage(exception))
            }
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
                refreshFamilyMembers()
                foodSyncManager.requestSync()
                showToast("Sync account disconnected.")
            }
        }
    }

    private suspend fun requestExplicitGoogleSignIn(serverClientId: String): Credential? {
        val googleSignInOption = GetSignInWithGoogleOption.Builder(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleSignInOption)
            .build()

        return try {
            credentialManager.getCredential(this, request).credential
        } catch (_: NoCredentialException) {
            null
        }
    }

    private suspend fun requestGoogleCredential(
        serverClientId: String,
        authorizedOnly: Boolean
    ): Credential? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(false)
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

    private fun googleSignInErrorMessage(exception: Throwable): String {
        val details = buildString {
            append(exception.message.orEmpty())
            if (exception.localizedMessage != exception.message) {
                append(' ')
                append(exception.localizedMessage.orEmpty())
            }
            exception.cause?.message?.takeIf { it.isNotBlank() }?.let {
                append(' ')
                append(it)
            }
        }

        val normalizedDetails = details.lowercase()
        return when {
            "not registered to use oauth2.0" in normalizedDetails ||
                "sha-1" in normalizedDetails ||
                "developer console" in normalizedDetails ||
                "caller not whitelisted" in normalizedDetails ||
                "developer_error" in normalizedDetails -> {
                "Google Sign-In is misconfigured for this installed build. Add this release/App Distribution SHA-1 and package name in Firebase, then download a fresh google-services.json."
            }

            "network" in normalizedDetails || "timeout" in normalizedDetails -> {
                "Google Sign-In failed because the network request did not complete."
            }

            "canceled" in normalizedDetails || "cancelled" in normalizedDetails -> {
                "Google Sign-In was cancelled."
            }

            else -> exception.localizedMessage
                ?.takeIf { it.isNotBlank() }
                ?: exception.message?.takeIf { it.isNotBlank() }
                ?: "Google Sign-In failed."
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
