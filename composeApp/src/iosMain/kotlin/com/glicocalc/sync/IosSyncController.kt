package com.glicocalc.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glicocalc.database.FamilyMember

class IosSyncController {
    var familyMembers by mutableStateOf<List<FamilyMember>>(emptyList())
        private set
    var familyId by mutableStateOf<String?>(null)
        private set
    var familyName by mutableStateOf<String?>(null)
        private set
    var syncStatusMessage by mutableStateOf("Sign in to link a Google account.")
        private set
    var lastSyncedMessage by mutableStateOf<String?>(null)
        private set
    var isSignedIn by mutableStateOf(false)
        private set
    var canManualSync by mutableStateOf(false)
        private set

    var onSignInRequested: (() -> Unit)? = null
    var onSignOutRequested: (() -> Unit)? = null
    var onSwitchAccountRequested: (() -> Unit)? = null
    var onManualSyncRequested: (() -> Unit)? = null
    var onAddFamilyMemberRequested: ((email: String, name: String) -> Unit)? = null
    var onRemoveFamilyMemberRequested: ((email: String) -> Unit)? = null
    var onRefreshFamilyMembersRequested: (() -> List<FamilyMember>)? = null
    var onRefreshFamilyIdRequested: (() -> String?)? = null
    var onRefreshFamilyNameRequested: (() -> String?)? = null

    fun setUnavailable(message: String) {
        familyMembers = emptyList()
        syncStatusMessage = "Local data only"
        lastSyncedMessage = null
        isSignedIn = false
        canManualSync = false
    }

    fun setSignedOut() {
        isSignedIn = false
        syncStatusMessage = "Sign in to link a Google account."
        lastSyncedMessage = null
        canManualSync = false
        refreshFamilyMembers()
    }

    fun setSigningIn() {
        syncStatusMessage = "Signing in..."
    }

    fun setSignedIn(label: String) {
        isSignedIn = true
        syncStatusMessage = "Google account linked."
        canManualSync = false
        refreshFamilyMembers()
    }

    fun setError(message: String) {
        syncStatusMessage = message
    }

    fun refreshFamilyMembers() {
        familyMembers = onRefreshFamilyMembersRequested?.invoke() ?: emptyList()
        familyId = onRefreshFamilyIdRequested?.invoke()
        familyName = onRefreshFamilyNameRequested?.invoke()
    }
}
