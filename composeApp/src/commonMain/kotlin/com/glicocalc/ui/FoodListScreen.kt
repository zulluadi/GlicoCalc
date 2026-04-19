package com.glicocalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glicocalc.database.BaseFood
import com.glicocalc.logic.removeDiacritics
import com.glicocalc.ui.components.FoodEditorDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodListScreen(
    foods: List<BaseFood>,
    onAddFood: (String, Double) -> Unit,
    onEditFood: (Long, String, Double) -> Unit,
    onDeleteFood: (Long) -> Unit,
    onUndeleteFood: (String, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolveFoodName = rememberBaseFoodNameResolver()
    val deletedMessage = Strings.foodDeleted()
    val undoLabel = Strings.undo()
    var showAddDialog by remember { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<BaseFood?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedFood = remember { mutableStateOf<BaseFood?>(null) }
    val scope = rememberCoroutineScope()

    val filteredFoods = remember(searchQuery, foods) {
        if (searchQuery.isBlank()) foods
        else {
            foods.filter { food ->
                matchesBaseFoodQuery(
                    rawName = food.name,
                    localizedName = resolveFoodName(food.name),
                    query = searchQuery
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.baseFoodsTitle()) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = Strings.addFood())
            }
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(Strings.searchFoodPlaceholder()) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn {
                items(filteredFoods) { food ->
                    ListItem(
                        headlineContent = { Text(resolveFoodName(food.name)) },
                        supportingContent = { Text(Strings.carbsPer100g(food.carbsPer100g.toString())) },
                        leadingContent = {
                            IconButton(
                                onClick = {
                                    deletedFood.value = food
                                    onDeleteFood(food.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = deletedMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            deletedFood.value?.let { f ->
                                                onUndeleteFood(f.name, f.carbsPer100g)
                                            }
                                        }
                                        deletedFood.value = null
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = Strings.delete(), tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { foodToEdit = food }) {
                                Icon(Icons.Default.Edit, contentDescription = Strings.edit())
                            }
                        },
                        modifier = Modifier.clickable { foodToEdit = food }
                    )
                    HorizontalDivider()
                }
            }
        }

        if (showAddDialog) {
            FoodEditorDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, carbs -> onAddFood(name, carbs) }
            )
        }

        foodToEdit?.let { food ->
            FoodEditorDialog(
                initialName = food.name,
                initialCarbs = food.carbsPer100g.toString(),
                onDismiss = { foodToEdit = null },
                onConfirm = { name, carbs -> onEditFood(food.id, name, carbs) }
            )
        }
    }
}
