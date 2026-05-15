package com.glicocalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glicocalc.database.FamilyMember

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagerScreen(
    familyMembers: List<FamilyMember>,
    familyId: String?,
    syncStatusMessage: String?,
    lastSyncedMessage: String?,
    isSignedIn: Boolean,
    onAddMember: (email: String, name: String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onManualSync: (() -> Unit)?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.familyAndSync()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings.close()
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item {
                ListItem(
                    headlineContent = { Text(Strings.familyMembers()) },
                    supportingContent = { Text(Strings.familySharingDescription()) }
                )
                HorizontalDivider()
            }

            if (familyMembers.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(Strings.familyNoMembers()) }
                    )
                    HorizontalDivider()
                }
            }

            items(familyMembers, key = { it.email }) { member ->
                val isSignedInMember = member.firebaseUid != null
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name.ifBlank { member.email })
                            if (member.isOwner != 0L) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "(${Strings.ownerBadge()})",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    supportingContent = {
                        Column {
                            Text(member.email)
                            Text(
                                if (isSignedInMember) Strings.signedIn() else Strings.notSignedIn(),
                                color = if (isSignedInMember)
                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                else
                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { onRemoveMember(member.email) }) {
                            Text(Strings.removeMember())
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(Strings.addMember())
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text(Strings.syncStatus()) },
                    supportingContent = {
                        val statusText = buildString {
                            append(syncStatusMessage ?: Strings.syncStatusUpToDate())
                            if (!lastSyncedMessage.isNullOrBlank()) {
                                append('\n')
                                append(lastSyncedMessage)
                            }
                        }
                        Text(statusText)
                    }
                )
            }

            if (familyId != null) {
                item {
                    ListItem(
                        headlineContent = { Text("Family ID") },
                        supportingContent = {
                            Text(
                                text = familyId,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSignedIn) {
                        if (onManualSync != null) {
                            Button(
                                onClick = onManualSync,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(Strings.syncNow())
                            }
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(Strings.signOut())
                        }
                    } else {
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(Strings.signInWithGoogle())
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddDialog) {
        AddMemberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { email, name ->
                onAddMember(email, name)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (email: String, name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.addMember()) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Strings.memberName()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(Strings.memberEmail()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim(), name.trim()) },
                enabled = email.isNotBlank()
            ) {
                Text(Strings.addMember())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
}
