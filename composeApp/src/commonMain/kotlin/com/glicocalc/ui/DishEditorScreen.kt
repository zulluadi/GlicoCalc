package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glicocalc.database.BaseFood
import com.glicocalc.logic.removeDiacritics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditorScreen(
    initialName: String = "",
    initialComponents: List<Pair<Long, Double>> = emptyList(),
    allBaseFoods: List<BaseFood>,
    onSave: (String, List<Pair<Long, Double>>) -> Unit,
    onCancel: () -> Unit
) {
    var dishName by remember { mutableStateOf(initialName) }
    val components = remember { mutableStateListOf<ComponentState>().apply { 
        if (initialComponents.isEmpty()) add(ComponentState()) 
        else addAll(initialComponents.map { (foodId, percentage) -> 
            ComponentState(
                foodId = foodId, 
                percentage = percentage.toString(),
                searchQuery = allBaseFoods.find { it.id == foodId }?.name ?: ""
            ) 
        })
    } }

    val canSave = dishName.isNotBlank() && components.any { it.foodId != null && it.percentage.toDoubleOrNull() != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialName.isEmpty()) "Mâncare Nouă" else "Editează Mâncare") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Închide")
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val validComponents = components.mapNotNull {
                                val p = it.percentage.toDoubleOrNull()
                                if (it.foodId != null && p != null) it.foodId to p else null
                            }
                            onSave(dishName, validComponents)
                        }
                    ) {
                        Text(
                            text = "SALVEAZĂ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = dishName,
                onValueChange = { dishName = it },
                label = { Text("Nume Mâncare (ex: Sarmale)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Compoziție (Ingrediente)", fontWeight = FontWeight.Bold)
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(components) { index, component ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box(modifier = Modifier.weight(1.5f)) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                val filteredFoods by remember(component.searchQuery, allBaseFoods) {
                                    val normalizedQuery = component.searchQuery.removeDiacritics()
                                    derivedStateOf {
                                        if (component.searchQuery.isEmpty() || allBaseFoods.any { it.name.removeDiacritics().equals(normalizedQuery, ignoreCase = true) }) {
                                            allBaseFoods
                                        } else {
                                            allBaseFoods.filter { it.name.removeDiacritics().contains(normalizedQuery, ignoreCase = true) }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = component.searchQuery,
                                    onValueChange = { 
                                        components[index] = component.copy(searchQuery = it)
                                        expanded = true
                                    },
                                    label = { Text("Aliment") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                
                                if (filteredFoods.isNotEmpty()) {
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                                        modifier = Modifier.exposedDropdownSize().heightIn(max = 280.dp)
                                    ) {
                                        filteredFoods.forEach { food ->
                                            DropdownMenuItem(
                                                text = { Text(food.name) },
                                                onClick = {
                                                    components[index] = component.copy(
                                                        foodId = food.id,
                                                        searchQuery = food.name
                                                    )
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = component.percentage,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) components[index] = component.copy(percentage = it) },
                            label = { Text("%") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.7f)
                        )

                        IconButton(onClick = { components.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Șterge rând", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = { components.add(ComponentState()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adaugă Ingredient")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Spațiu pentru a nu fi acoperit de tastatură ultimele rânduri
                }
            }
        }
    }
}

data class ComponentState(
    val foodId: Long? = null,
    val percentage: String = "",
    val searchQuery: String = ""
)
