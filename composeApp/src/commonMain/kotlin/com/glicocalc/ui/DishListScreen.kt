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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishListScreen(
    dishesWithCarbs: List<GlicoRepository.DishWithCarbs>,
    onAddDish: () -> Unit,
    onEditDish: (Long) -> Unit,
    onDeleteDish: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mâncăruri (Rețete)") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDish) {
                Icon(Icons.Default.Add, contentDescription = "Adaugă Mâncare")
            }
        },
        modifier = modifier
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            LazyColumn {
                items(dishesWithCarbs) { dishWithCarbs ->
                    ListItem(
                        headlineContent = { Text(dishWithCarbs.dish.name) },
                        supportingContent = { Text("${((dishWithCarbs.carbsPer100g * 10).toInt() / 10.0)} g glucide / 100g") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEditDish(dishWithCarbs.dish.id) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editează")
                                }
                                IconButton(onClick = { onDeleteDish(dishWithCarbs.dish.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Șterge", tint = MaterialTheme.colorScheme.error)
                                }
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
