package com.glicocalc.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.glicocalc.database.BaseFood
import com.glicocalc.database.Dish
import com.glicocalc.database.FoodSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedItemsScreen(
    deletedFoods: List<BaseFood>,
    deletedDishes: List<Dish>,
    onRestoreFood: (Long) -> Unit,
    onRestoreDish: (Long) -> Unit,
    onPermanentlyDeleteFood: (Long) -> Unit,
    onPermanentlyDeleteDish: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resolveFoodName = rememberBaseFoodNameResolver()
    var foodToDeletePermanently by remember { mutableStateOf<BaseFood?>(null) }
    var dishToDeletePermanently by remember { mutableStateOf<Dish?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.deletedItems()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.close())
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
            if (deletedFoods.isEmpty() && deletedDishes.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(Strings.noDeletedItems()) }
                    )
                }
            }

            if (deletedFoods.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(Strings.deletedFoods()) }
                    )
                    HorizontalDivider()
                }
                items(deletedFoods, key = { "food-${it.id}" }) { food ->
                    ListItem(
                        headlineContent = { Text(resolveFoodName(food.name)) },
                        supportingContent = { Text(Strings.carbsPer100g(food.carbsPer100g.toString())) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onRestoreFood(food.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = Strings.restore())
                                }
                                if (food.source == FoodSource.CUSTOM.value) {
                                    IconButton(onClick = { foodToDeletePermanently = food }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = Strings.deletePermanently()
                                        )
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            if (deletedDishes.isNotEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(Strings.deletedDishes()) }
                    )
                    HorizontalDivider()
                }
                items(deletedDishes, key = { "dish-${it.id}" }) { dish ->
                    ListItem(
                        headlineContent = { Text(dish.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onRestoreDish(dish.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = Strings.restore())
                                }
                                IconButton(onClick = { dishToDeletePermanently = dish }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = Strings.deletePermanently()
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }

        foodToDeletePermanently?.let { food ->
            AlertDialog(
                onDismissRequest = { foodToDeletePermanently = null },
                title = { Text(Strings.deletePermanently()) },
                text = { Text(Strings.deletePermanentlyDescription()) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onPermanentlyDeleteFood(food.id)
                            foodToDeletePermanently = null
                        }
                    ) {
                        Text(Strings.deletePermanentlyConfirm())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { foodToDeletePermanently = null }) {
                        Text(Strings.cancel())
                    }
                }
            )
        }

        dishToDeletePermanently?.let { dish ->
            AlertDialog(
                onDismissRequest = { dishToDeletePermanently = null },
                title = { Text(Strings.deletePermanently()) },
                text = { Text(Strings.deletePermanentlyDescription()) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onPermanentlyDeleteDish(dish.id)
                            dishToDeletePermanently = null
                        }
                    ) {
                        Text(Strings.deletePermanentlyConfirm())
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dishToDeletePermanently = null }) {
                        Text(Strings.cancel())
                    }
                }
            )
        }
    }
}
