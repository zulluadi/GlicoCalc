package com.glicocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glicocalc.database.GlicoRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishListScreen(
    dishesWithCarbs: List<GlicoRepository.DishWithCarbs>,
    onAddDish: () -> Unit,
    onEditDish: (Long) -> Unit,
    onDeleteDish: (Long) -> Unit,
    onUndeleteDish: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val deletedMessage = Strings.dishDeleted()
    val undoLabel = Strings.undo()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedDishId = remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.dishesTitle()) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDish) {
                Icon(Icons.Default.Add, contentDescription = Strings.addDish())
            }
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            LazyColumn {
                items(dishesWithCarbs) { dishWithCarbs ->
                    ListItem(
                        headlineContent = { Text(dishWithCarbs.dish.name) },
                        supportingContent = { Text(Strings.carbsPer100g(((dishWithCarbs.carbsPer100g * 10).toInt() / 10.0).toString())) },
                        leadingContent = {
                            IconButton(onClick = {
                                deletedDishId.value = dishWithCarbs.dish.id
                                onDeleteDish(dishWithCarbs.dish.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = deletedMessage,
                                        actionLabel = undoLabel,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        deletedDishId.value?.let(onUndeleteDish)
                                    }
                                    deletedDishId.value = null
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = Strings.delete(), tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onEditDish(dishWithCarbs.dish.id) }) {
                                Icon(Icons.Default.Edit, contentDescription = Strings.edit())
                            }
                        },
                        modifier = Modifier.clickable { onEditDish(dishWithCarbs.dish.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
