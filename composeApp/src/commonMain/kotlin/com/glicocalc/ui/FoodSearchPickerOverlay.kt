package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.glicocalc.logic.removeDiacritics

internal data class FoodPickerOption(
    val key: String,
    val title: String,
    val detail: String? = null,
    val searchTerms: List<String>
)

@Composable
internal fun FoodSearchPickerOverlay(
    initialQuery: String,
    options: List<FoodPickerOption>,
    onSelectOption: (FoodPickerOption) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var queryState by remember(initialQuery) {
        mutableStateOf(
            TextFieldValue(
                text = initialQuery,
                selection = TextRange(initialQuery.length)
            )
        )
    }
    var hasSeenKeyboard by remember { mutableStateOf(false) }
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible) {
            hasSeenKeyboard = true
        } else if (hasSeenKeyboard) {
            onDismiss()
        }
    }

    val query = queryState.text
    val normalizedQuery = query.removeDiacritics()
    val filteredOptions by remember(query, options) {
        derivedStateOf {
            if (query.isBlank()) {
                options.take(100)
            } else {
                options
                    .asSequence()
                    .filter { option ->
                        option.searchTerms.any { term ->
                            term.removeDiacritics().contains(normalizedQuery, ignoreCase = true)
                        }
                    }
                    .take(100)
                    .toList()
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = queryState,
                    onValueChange = { queryState = it },
                    label = { Text(Strings.searchFoodPlaceholder()) },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onDismiss() }),
                    suffix = {
                        if (query.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    queryState = TextFieldValue("")
                                    onClearSelection()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = Strings.clearText())
                            }
                        }
                    }
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = Strings.close())
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filteredOptions.isEmpty()) {
                    if (query.isNotBlank()) {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = Strings.noResultsFound(),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            )
                        }
                    }
                } else {
                    items(filteredOptions, key = { it.key }) { option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = option.title,
                                        modifier = Modifier.weight(1f)
                                    )
                                    option.detail?.let { detail ->
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = { onSelectOption(option) }
                        )
                    }
                }
            }
        }
    }
}
