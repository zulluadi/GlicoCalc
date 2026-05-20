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

    BoxWithConstraints {
        val useLandscapePicker = maxWidth > maxHeight
        val contentPadding = if (useLandscapePicker) 8.dp else 16.dp
        val contentGap = if (useLandscapePicker) 8.dp else 12.dp

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            color = MaterialTheme.colorScheme.surface
        ) {
            if (useLandscapePicker) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.spacedBy(contentGap)
                ) {
                    FoodSearchControls(
                        queryState = queryState,
                        query = query,
                        compact = true,
                        focusRequester = focusRequester,
                        onQueryChange = { queryState = it },
                        onClearSelection = {
                            queryState = TextFieldValue("")
                            onClearSelection()
                        },
                        onDismiss = onDismiss,
                        modifier = Modifier
                            .widthIn(min = 220.dp, max = 300.dp)
                            .fillMaxHeight()
                    )
                    FoodSearchResults(
                        query = query,
                        filteredOptions = filteredOptions,
                        onSelectOption = onSelectOption,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(contentGap)
                ) {
                    FoodSearchControls(
                        queryState = queryState,
                        query = query,
                        compact = false,
                        focusRequester = focusRequester,
                        onQueryChange = { queryState = it },
                        onClearSelection = {
                            queryState = TextFieldValue("")
                            onClearSelection()
                        },
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FoodSearchResults(
                        query = query,
                        filteredOptions = filteredOptions,
                        onSelectOption = onSelectOption,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodSearchControls(
    queryState: TextFieldValue,
    query: String,
    compact: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (TextFieldValue) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        OutlinedTextField(
            value = queryState,
            onValueChange = onQueryChange,
            label = if (compact) null else { { Text(Strings.searchFoodPlaceholder()) } },
            placeholder = if (compact) { { Text(Strings.searchFoodPlaceholder()) } } else null,
            modifier = Modifier
                .focusRequester(focusRequester)
                .weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDismiss() }),
            suffix = {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = Strings.clearText())
                    }
                }
            }
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = Strings.close())
        }
    }
}

@Composable
private fun FoodSearchResults(
    query: String,
    filteredOptions: List<FoodPickerOption>,
    onSelectOption: (FoodPickerOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxHeight()) {
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
