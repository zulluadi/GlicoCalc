package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.glicocalc.database.BaseFood
import kotlinx.coroutines.launch

data class ComponentState(
    val foodId: Long? = null,
    val weightGrams: String = "",
    val searchQuery: String = ""
)

private data class ActiveIngredientPicker(
    val componentIndex: Int,
    val initialQuery: String
)

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
    val focusManager = LocalFocusManager.current
    var dishName by remember { mutableStateOf(initialName) }
    var totalCookedWeightText by remember { mutableStateOf(initialTotalCookedWeight?.toString() ?: "") }
    var activeIngredientPicker by remember { mutableStateOf<ActiveIngredientPicker?>(null) }
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
    val foodPickerOptions = remember(allBaseFoods, resolveFoodName) {
        allBaseFoods.map { food ->
            val localizedName = resolveFoodName(food.name)
            FoodPickerOption(
                key = food.id.toString(),
                title = localizedName,
                detail = "${formatDecimal(food.carbsPer100g)}%",
                searchTerms = listOf(food.name, localizedName)
            )
        }
    }

    val canSave = dishName.isNotBlank() && totalCookedWeightText.toDoubleOrNull() != null && totalCookedWeightText.toDoubleOrNull()!! > 0.0 && components.any { it.foodId != null && it.weightGrams.toDoubleOrNull() != null }

    Box(modifier = Modifier.fillMaxSize()) {
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
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(components) { index, component ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { components.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = Strings.deleteRow(), tint = MaterialTheme.colorScheme.error)
                        }

                        Box(modifier = Modifier.weight(1.5f)) {
                            OutlinedTextField(
                                value = component.searchQuery,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(Strings.ingredient()) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (component.searchQuery.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    components[index] = component.copy(foodId = null, searchQuery = "")
                                                }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = Strings.clearText())
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                activeIngredientPicker = ActiveIngredientPicker(index, component.searchQuery)
                                            }
                                        ) {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            activeIngredientPicker = ActiveIngredientPicker(index, component.searchQuery)
                                        }
                                    }
                                    .pointerInput(component.foodId, component.searchQuery) {
                                        detectTapGestures {
                                            activeIngredientPicker = ActiveIngredientPicker(index, component.searchQuery)
                                        }
                                    },
                                singleLine = true
                            )
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
                        onClick = {
                            components.add(ComponentState())
                            val newIndex = components.lastIndex
                            activeIngredientPicker = ActiveIngredientPicker(newIndex, "")
                            scope.launch { listState.scrollToItem(newIndex) }
                        },
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

        activeIngredientPicker?.let { picker ->
            FoodSearchPickerOverlay(
                initialQuery = picker.initialQuery,
                options = foodPickerOptions,
                onSelectOption = { option ->
                    val index = picker.componentIndex
                    val food = option.key.toLongOrNull()?.let { foodId ->
                        allBaseFoods.firstOrNull { it.id == foodId }
                    }
                    if (index in components.indices && food != null) {
                        components[index] = components[index].copy(
                            foodId = food.id,
                            searchQuery = resolveFoodName(food.name)
                        )
                    }
                    activeIngredientPicker = null
                    focusManager.clearFocus()
                },
                onClearSelection = {
                    val index = picker.componentIndex
                    if (index in components.indices) {
                        components[index] = components[index].copy(foodId = null, searchQuery = "")
                    }
                },
                onDismiss = {
                    activeIngredientPicker = null
                    focusManager.clearFocus()
                }
            )
        }
    }
}
