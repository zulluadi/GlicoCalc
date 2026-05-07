package com.glicocalc.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class IosSyncController {
    var syncAccountLabel by mutableStateOf<String?>(null)
        private set
    var syncAccountStatusMessage by mutableStateOf<String?>(null)
        private set
    var syncStatusMessage by mutableStateOf("Sign in to link a Google account.")
        private set
    var lastSyncedMessage by mutableStateOf<String?>(null)
        private set
    var canSignIn by mutableStateOf(false)
        private set
    var canManualSync by mutableStateOf(false)
        private set

    var onSignInRequested: (() -> Unit)? = null
    var onSwitchAccountRequested: (() -> Unit)? = null
    var onSignOutRequested: (() -> Unit)? = null
    var onManualSyncRequested: (() -> Unit)? = null

    fun setUnavailable(message: String) {
        syncAccountLabel = null
        syncAccountStatusMessage = message
        syncStatusMessage = "Local data only"
        lastSyncedMessage = null
        canSignIn = false
        canManualSync = false
    }

    fun setSignedOut() {
        syncAccountLabel = null
        syncAccountStatusMessage = null
        syncStatusMessage = "Sign in to link a Google account."
        lastSyncedMessage = null
        canSignIn = true
        canManualSync = false
    }

    fun setSigningIn() {
        syncAccountStatusMessage = null
        syncStatusMessage = "Signing in..."
    }

    fun setSignedIn(label: String) {
        syncAccountLabel = label
        syncAccountStatusMessage = null
        syncStatusMessage = "Google account linked."
        canSignIn = true
        canManualSync = false
    }

    fun setError(message: String) {
        syncAccountStatusMessage = message
        syncStatusMessage = message
    }
}
