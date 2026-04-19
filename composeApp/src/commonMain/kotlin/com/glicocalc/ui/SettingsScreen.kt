package com.glicocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedLanguage: String?,
    onOpenLanguagePicker: () -> Unit,
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
                        Text(currentLanguageLabel(selectedLanguage))
                    },
                    modifier = Modifier.clickable(onClick = onOpenLanguagePicker)
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
private fun currentLanguageLabel(selectedLanguage: String?): String {
    val option = appLanguageOptions.firstOrNull { it.code == selectedLanguage }
    return if (option?.code == null) {
        Strings.systemDefault()
    } else {
        option.label
    }
}
