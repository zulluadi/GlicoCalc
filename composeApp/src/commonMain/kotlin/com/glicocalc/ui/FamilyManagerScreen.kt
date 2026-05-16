package com.glicocalc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.glicocalc.database.FamilyMember

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FamilyManagerScreen(
    familyMembers: List<FamilyMember>,
    familyId: String?,
    familyName: String?,
    currentUserEmail: String?,
    isFamilyOwner: Boolean,
    pendingFamilyInviteLabel: String?,
    syncStatusMessage: String?,
    lastSyncedMessage: String?,
    syncIntervalMinutes: Int,
    isSignedIn: Boolean,
    onAddMember: (email: String, name: String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onUpdateFamilyName: (String?) -> Unit,
    onLeaveFamily: () -> Unit,
    onJoinPendingFamilyInvite: () -> Unit,
    onJoinFamilyById: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onManualSync: (() -> Unit)?,
    onSyncIntervalChanged: (Int) -> Unit,
    onScanFamilyQr: (() -> Unit)?,
    onFamilyQrDialogClosed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showJoinInviteDialog by remember { mutableStateOf(false) }
    var showJoinByIdDialog by remember { mutableStateOf(false) }
    var showFamilyQrDialog by remember { mutableStateOf(false) }
    var showFamilyNameDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var dismissedInviteLabel by remember { mutableStateOf<String?>(null) }
    val normalizedCurrentUserEmail = currentUserEmail?.trim()?.lowercase()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(pendingFamilyInviteLabel) {
        if (pendingFamilyInviteLabel != null && pendingFamilyInviteLabel != dismissedInviteLabel) {
            showJoinInviteDialog = true
        }
    }

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
                    headlineContent = { Text(familyName ?: Strings.familyTitle()) },
                    supportingContent = { Text(Strings.familySharingDescription()) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isFamilyOwner) {
                                IconButton(onClick = { showFamilyNameDialog = true }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = Strings.edit()
                                    )
                                }
                            }
                            if (familyId != null) {
                                IconButton(onClick = { showFamilyQrDialog = true }) {
                                    QrCodeIcon(
                                        contentDescription = Strings.familyQrCode()
                                    )
                                }
                            }
                        }
                    }
                )
            }

            item {
                SectionLabel(Strings.familyMembers())
            }

            if (familyMembers.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (isSignedIn) {
                                        Strings.familyNoMembers()
                                    } else {
                                        Strings.familySignInPrompt()
                                    }
                                )
                            }
                        )
                    }
                }
            } else {
                items(familyMembers, key = { it.email }) { member ->
                    val isCurrentUser = member.email.trim().lowercase() == normalizedCurrentUserEmail
                    FamilyMemberRow(
                        member = member,
                        canRemove = isFamilyOwner && !isCurrentUser,
                        onRemoveMember = onRemoveMember
                    )
                }
            }

            if (isFamilyOwner) {
                item {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(Strings.addMember())
                    }
                }
            }

            item {
                SectionLabel(Strings.syncStatus())
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
                    },
                    trailingContent = {
                        if (isSignedIn && onManualSync != null) {
                            Button(
                                onClick = onManualSync,
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text(Strings.syncNow())
                            }
                        }
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(Strings.syncInterval()) },
                    supportingContent = { Text(Strings.syncIntervalValue(syncIntervalMinutes)) },
                    modifier = Modifier.clickable { showSyncIntervalDialog = true }
                )
                HorizontalDivider()
            }

            item {
                SectionLabel(Strings.actions())
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSignedIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(Strings.signOut())
                            }
                            if (familyId != null && !isFamilyOwner) {
                                OutlinedButton(
                                    onClick = { showLeaveDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(Strings.leaveFamily())
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showJoinByIdDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(Strings.joinByFamilyId())
                        }
                        if (onScanFamilyQr != null) {
                            OutlinedButton(
                                onClick = onScanFamilyQr,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(Strings.scanFamilyQr())
                            }
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

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(Strings.leaveFamily()) },
            text = { Text(Strings.leaveFamilyDescription()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        onLeaveFamily()
                    }
                ) {
                    Text(Strings.leaveFamilyConfirm())
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text(Strings.cancel())
                }
            }
        )
    }

    if (showJoinInviteDialog) {
        val inviteLabel = pendingFamilyInviteLabel
        AlertDialog(
            onDismissRequest = {
                dismissedInviteLabel = inviteLabel
                showJoinInviteDialog = false
            },
            title = { Text(Strings.joinFamily()) },
            text = {
                Text(
                    if (inviteLabel != null) {
                        "${Strings.familyInvitationDescription(inviteLabel)}\n\n${Strings.joinFamilyDescription()}"
                    } else {
                        Strings.joinFamilyDescription()
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showJoinInviteDialog = false
                        onJoinPendingFamilyInvite()
                    }
                ) {
                    Text(Strings.joinFamilyConfirm())
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dismissedInviteLabel = inviteLabel
                        showJoinInviteDialog = false
                    }
                ) {
                    Text(Strings.cancel())
                }
            }
        )
    }

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            currentIntervalMinutes = syncIntervalMinutes,
            onDismiss = { showSyncIntervalDialog = false },
            onConfirm = { minutes ->
                showSyncIntervalDialog = false
                onSyncIntervalChanged(minutes)
            }
        )
    }

    if (showJoinByIdDialog) {
        JoinFamilyByIdDialog(
            onDismiss = { showJoinByIdDialog = false },
            onConfirm = { id ->
                showJoinByIdDialog = false
                onJoinFamilyById(id)
            }
        )
    }

    if (showFamilyQrDialog && familyId != null) {
        FamilyQrDialog(
            familyId = familyId,
            onCopyFamilyId = {
                clipboardManager.setText(AnnotatedString(familyId))
            },
            onDismiss = {
                showFamilyQrDialog = false
                onFamilyQrDialogClosed()
            }
        )
    }

    if (showFamilyNameDialog) {
        FamilyNameDialog(
            currentName = familyName.orEmpty(),
            onDismiss = { showFamilyNameDialog = false },
            onConfirm = { name ->
                showFamilyNameDialog = false
                onUpdateFamilyName(name)
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun QrCodeIcon(
    contentDescription: String,
    modifier: Modifier = Modifier.size(20.dp)
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        val cell = size.minDimension / 7f
        fun square(x: Int, y: Int) {
            drawRect(
                color = color,
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell, cell)
            )
        }

        drawFinder(0, 0, cell, color)
        drawFinder(4, 0, cell, color)
        drawFinder(0, 4, cell, color)
        square(4, 4)
        square(6, 4)
        square(5, 5)
        square(4, 6)
        square(6, 6)
    }
}

private fun DrawScope.drawFinder(
    x: Int,
    y: Int,
    cell: Float,
    color: Color
) {
    val topLeft = Offset(x * cell, y * cell)
    drawRect(
        color = color,
        topLeft = topLeft,
        size = Size(cell * 3f, cell * 3f)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(topLeft.x + cell, topLeft.y + cell),
        size = Size(cell, cell)
    )
}

@Composable
private fun FamilyQrDialog(
    familyId: String,
    onCopyFamilyId: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.familyQrCode()) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FamilyQrCode(
                    payload = familyQrPayload(familyId),
                    modifier = Modifier.size(220.dp)
                )
                Text(
                    text = familyId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onCopyFamilyId)
                )
                Text(
                    text = Strings.familyQrDescription(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.close())
            }
        }
    )
}

@Composable
private fun FamilyNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.familyName()) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                label = { Text(Strings.familyName()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().takeIf { it.isNotBlank() }) }) {
                Text(Strings.save())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
}

@Composable
private fun FamilyMemberRow(
    member: FamilyMember,
    canRemove: Boolean,
    onRemoveMember: (String) -> Unit
) {
    val isSignedInMember = member.firebaseUid != null

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name.ifBlank { member.email },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (member.isOwner != 0L) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(${Strings.ownerBadge()})",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Text(
                    text = member.email,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (isSignedInMember) Strings.signedIn() else Strings.notSignedIn(),
                    color = if (isSignedInMember)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (canRemove) {
                TextButton(onClick = { onRemoveMember(member.email) }) {
                    Text(Strings.removeMember())
                }
            }
        }
    }
}

@Composable
private fun SyncIntervalDialog(
    currentIntervalMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var minutesText by remember(currentIntervalMinutes) {
        mutableStateOf(currentIntervalMinutes.toString())
    }
    val parsedMinutes = minutesText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.syncInterval()) },
        text = {
            Column {
                Text(Strings.syncIntervalDescription())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { value ->
                        minutesText = value.filter(Char::isDigit).take(2)
                    },
                    label = { Text(Strings.syncInterval()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsedMinutes?.coerceIn(1, 60) ?: currentIntervalMinutes) },
                enabled = parsedMinutes != null
            ) {
                Text(Strings.save())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
}

@Composable
private fun JoinFamilyByIdDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var familyId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.joinByFamilyId()) },
        text = {
            OutlinedTextField(
                value = familyId,
                onValueChange = { familyId = it },
                label = { Text(Strings.familyId()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(familyId.trim()) },
                enabled = familyId.isNotBlank()
            ) {
                Text(Strings.joinFamilyConfirm())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel())
            }
        }
    )
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
