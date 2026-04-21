package com.glicocalc.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedLanguage: String?,
    selectedFoodLanguage: String?,
    syncAccountLabel: String?,
    syncAccountStatusMessage: String?,
    syncStatusMessage: String?,
    lastSyncedMessage: String?,
    onOpenLanguagePicker: () -> Unit,
    onOpenFoodLanguagePicker: () -> Unit,
    onSignInToSync: (() -> Unit)?,
    onSignOutFromSync: (() -> Unit)?,
    onManualSync: (() -> Unit)?,
    onOpenMealTypes: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    headlineContent = { Text(Strings.syncAccount()) },
                    supportingContent = {
                        Text(
                            when {
                                syncAccountLabel != null -> Strings.syncSignedInAs(syncAccountLabel)
                                syncAccountStatusMessage != null -> syncAccountStatusMessage
                                else -> Strings.syncAccountDescription()
                            }
                        )
                    },
                    trailingContent = {
                        when {
                            syncAccountLabel != null -> SettingsActionButton(
                                label = Strings.signOut(),
                                onClick = { onSignOutFromSync?.invoke() }
                            )
                            onSignInToSync != null -> SettingsActionButton(
                                label = Strings.signInWithGoogle(),
                                onClick = onSignInToSync
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = syncAccountLabel != null || onSignInToSync != null) {
                        if (syncAccountLabel == null) onSignInToSync?.invoke() else onSignOutFromSync?.invoke()
                    }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(Strings.syncStatus()) },
                    supportingContent = {
                        val statusText = buildString {
                            append(syncStatusMessage ?: Strings.syncStatusNotSignedIn())
                            if (!lastSyncedMessage.isNullOrBlank()) {
                                append('\n')
                                append(lastSyncedMessage)
                            }
                        }
                        Text(statusText)
                    },
                    trailingContent = {
                        when {
                            syncAccountLabel == null && onSignInToSync != null -> SettingsActionButton(
                                label = Strings.signInWithGoogle(),
                                onClick = onSignInToSync
                            )
                            onManualSync != null -> SettingsActionButton(
                                label = Strings.syncNow(),
                                onClick = onManualSync
                            )
                        }
                    },
                    modifier = Modifier.clickable(enabled = onManualSync != null || onSignInToSync != null) {
                        when {
                            syncAccountLabel == null && onSignInToSync != null -> onSignInToSync()
                            onManualSync != null -> onManualSync()
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
        }
    }
}

@Composable
private fun SettingsActionButton(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 40.dp)
    ) {
        Text(
            text = label,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
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
