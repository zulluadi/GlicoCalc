package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditorScreen(
    initialName: String = "",
    initialTotalCookedWeight: Double? = null,
    initialComponents: List<Pair<Long, Double>> = emptyList(),
    allBaseFoods: List<BaseFood>,
    onSave: (String, Double?, List<Pair<Long, Double>>) -> Unit,
    onCancel: () -> Unit
) {
    val resolveFoodName = rememberBaseFoodNameResolver()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dishName by remember { mutableStateOf(initialName) }
    var totalCookedWeightText by remember { mutableStateOf(initialTotalCookedWeight?.toString() ?: "") }
    val components = remember { mutableStateListOf<ComponentState>().apply { 
        if (initialComponents.isEmpty()) add(ComponentState()) 
        else addAll(initialComponents.map { (foodId, weightGrams) -> 
            ComponentState(
                foodId = foodId, 
                weightGrams = weightGrams.toString(),
                searchQuery = allBaseFoods.find { it.id == foodId }?.name?.let(resolveFoodName) ?: ""
            ) 
        })
    } }

    val canSave = dishName.isNotBlank() && totalCookedWeightText.toDoubleOrNull() != null && totalCookedWeightText.toDoubleOrNull()!! > 0.0 && components.any { it.foodId != null && it.weightGrams.toDoubleOrNull() != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialName.isEmpty()) Strings.newDishTitle() else Strings.editDishTitle()) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = Strings.close())
                    }
                },
                actions = {
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            val validComponents = components.mapNotNull {
                                val w = it.weightGrams.toDoubleOrNull()
                                if (it.foodId != null && w != null) it.foodId to w else null
                            }
                            val cookedWeight = totalCookedWeightText.toDoubleOrNull()
                            onSave(dishName, cookedWeight, validComponents)
                        }
                    ) {
                        Text(
                            text = Strings.save().uppercase(),
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
                label = { Text(Strings.dishNameLabel()) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = totalCookedWeightText,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) totalCookedWeightText = it },
                label = { Text(Strings.totalCookedWeight()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            
            Text(Strings.compositionIngredients(), fontWeight = FontWeight.Bold)
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState
            ) {
                itemsIndexed(components) { index, component ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { components.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = Strings.deleteRow(), tint = MaterialTheme.colorScheme.error)
                        }

                        var expanded by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(expanded) {
                            if (expanded) {
                                // Removed aggressive scroll
                            }
                        }
                        
                        Box(modifier = Modifier.weight(1.5f)) {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                val filteredFoods by remember(component.searchQuery, allBaseFoods) {
                                    val normalizedQuery = component.searchQuery.removeDiacritics()
                                    derivedStateOf {
                                        if (component.searchQuery.isEmpty() || allBaseFoods.any {
                                                val localizedName = resolveFoodName(it.name)
                                                it.name.removeDiacritics().equals(normalizedQuery, ignoreCase = true) ||
                                                    localizedName.removeDiacritics().equals(normalizedQuery, ignoreCase = true)
                                            }) {
                                            allBaseFoods.take(50)
                                        } else {
                                            allBaseFoods.filter { food ->
                                                matchesBaseFoodQuery(
                                                    rawName = food.name,
                                                    localizedName = resolveFoodName(food.name),
                                                    query = component.searchQuery
                                                )
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = component.searchQuery,
                                    onValueChange = { 
                                        components[index] = component.copy(searchQuery = it)
                                        expanded = true
                                    },
                                    label = { Text(Strings.ingredient()) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier
                                            .exposedDropdownSize()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                            if (filteredFoods.isEmpty()) {
                                                if (component.searchQuery.isNotBlank()) {
                                                    DropdownMenuItem(
                                                        text = { Text(Strings.noResultsFound(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) },
                                                        onClick = { },
                                                        enabled = false
                                                    )
                                                } else {
                                                    // Keep empty
                                                }
                                            } else {
                                                filteredFoods.forEach { food ->
                                                    DropdownMenuItem(
                                                        text = { Text(resolveFoodName(food.name)) },
                                                        onClick = {
                                                            components[index] = component.copy(
                                                                foodId = food.id,
                                                                searchQuery = resolveFoodName(food.name)
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
                            value = component.weightGrams,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) components[index] = component.copy(weightGrams = it) },
                            label = { Text("g") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                }

                item {
                    TextButton(
                        onClick = { components.add(ComponentState()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Strings.addIngredient())
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
    val weightGrams: String = "",
    val searchQuery: String = ""
)
