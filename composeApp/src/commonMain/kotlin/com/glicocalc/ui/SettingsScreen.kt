package com.glicocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glicocalc.database.FamilyMember

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedLanguage: String?,
    selectedFoodLanguage: String?,
    familyMembers: List<FamilyMember>,
    syncStatusMessage: String?,
    lastSyncedMessage: String?,
    isSignedIn: Boolean,
    onOpenLanguagePicker: () -> Unit,
    onOpenFoodLanguagePicker: () -> Unit,
    onSignInToSync: (() -> Unit)?,
    onOpenFamilyManager: () -> Unit,
    onOpenMealTypes: () -> Unit,
    onOpenDeletedItems: () -> Unit,
    onResetFoodList: () -> Unit,
    onReplaceSharedDefaults: (() -> Unit)? = null,
    isFamilyOwner: Boolean,
    modifier: Modifier = Modifier,
    nightscoutContent: @Composable () -> Unit = {}
) {
    var showReplaceDefaultsDialog by remember { mutableStateOf(false) }
    var showResetDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.settings()) }
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
                nightscoutContent()
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.language()) },
                    supportingContent = {
                        Text(currentLanguageLabel(selectedLanguage, appLanguageOptions, Strings.systemDefault()))
                    },
                    modifier = Modifier.clickable(onClick = onOpenLanguagePicker)
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.foodLanguage()) },
                    supportingContent = {
                        Text(currentLanguageLabel(selectedFoodLanguage, foodLanguageOptions, Strings.sameAsAppLanguage()))
                    },
                    modifier = Modifier.clickable(onClick = onOpenFoodLanguagePicker)
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.familyAndSync()) },
                    supportingContent = {
                        Column {
                            if (familyMembers.isEmpty()) {
                                Text(
                                    if (isSignedIn) Strings.syncAccountDescription()
                                    else Strings.syncStatusNotSignedIn()
                                )
                            } else {
                                val membersText = buildString {
                                    append(familyMembers.take(2).joinToString(", ") { member ->
                                        member.name.ifBlank { member.email }
                                    })
                                    if (familyMembers.size > 2) {
                                        append(", +${familyMembers.size - 2} more")
                                    }
                                }
                                Text(membersText)
                            }
                            if (isSignedIn) {
                                val statusText = buildString {
                                    if (syncStatusMessage != null) {
                                        append(syncStatusMessage)
                                    }
                                    if (!lastSyncedMessage.isNullOrBlank()) {
                                        if (isNotEmpty()) append(" \u2022 ")
                                        append(lastSyncedMessage)
                                    }
                                }
                                if (statusText.isNotBlank()) {
                                    Text(
                                        text = statusText,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    trailingContent = {
                        if (isSignedIn) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        } else if (onSignInToSync != null) {
                            TextButton(
                                onClick = onSignInToSync,
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text(
                                    text = Strings.signInWithGoogle(),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        if (isSignedIn) {
                            onOpenFamilyManager()
                        } else {
                            onSignInToSync?.invoke()
                        }
                    }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.mealTypes()) },
                    supportingContent = { Text(Strings.mealTypesDescription()) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onOpenMealTypes)
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.deletedItems()) },
                    supportingContent = { Text(Strings.deletedItemsDescription()) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onOpenDeletedItems)
                )
                HorizontalDivider()
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showReplaceDefaultsDialog = true },
                            enabled = isFamilyOwner && isSignedIn && onReplaceSharedDefaults != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(Strings.resetDefaultFoods()) }
                        Text(Strings.resetDefaultFoodsSummary(), style = MaterialTheme.typography.bodySmall)
                        if (isFamilyOwner && isSignedIn && onReplaceSharedDefaults == null) {
                            Text(Strings.defaultFoodsResetUnavailable(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            enabled = isFamilyOwner,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(Strings.resetAllFoods()) }
                        Text(Strings.resetAllFoodsSummary(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!isFamilyOwner) {
                    Text(Strings.resetFoodListOwnerOnly(), modifier = Modifier.padding(horizontal = 16.dp))
                }
                HorizontalDivider()
            }
        }
    }

    if (showReplaceDefaultsDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReplaceDefaultsDialog = false },
            title = { Text(Strings.resetDefaultFoods()) },
            text = { Text(Strings.replaceSharedDefaultsDescription()) },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceDefaultsDialog = false
                    onReplaceSharedDefaults?.invoke()
                }) { Text(Strings.resetDefaultFoods()) }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDefaultsDialog = false }) { Text(Strings.cancel()) }
            }
        )
    }

    if (showResetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(Strings.resetFoodListConfirm()) },
            text = { Text(Strings.resetFoodListWarning()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetFoodList()
                        showResetDialog = false
                    }
                ) {
                    Text(
                        text = Strings.reset(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(Strings.cancel())
                }
            }
        )
    }
}

@Composable
private fun currentLanguageLabel(
    selectedLanguage: String?,
    options: List<AppLanguageOption>,
    fallbackLabel: String
): String {
    val option = options.firstOrNull { it.code == selectedLanguage }
    return if (option?.code == null) {
        fallbackLabel
    } else {
        option.label
    }
}
