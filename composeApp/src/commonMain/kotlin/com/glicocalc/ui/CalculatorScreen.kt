package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glicocalc.logic.CarbCalculator
import com.glicocalc.logic.removeDiacritics
import com.glicocalc.database.Dish
import com.glicocalc.database.BaseFood
import com.glicocalc.models.DishWithComposition

data class MealItem(
    val selectedDish: DishWithComposition? = null,
    val selectedBaseFood: BaseFood? = null,
    val weightText: String = "",
    val carbsText: String = ""
) {
    val displayName: String get() = selectedDish?.dish?.name ?: selectedBaseFood?.name ?: ""
}

private enum class EditedField {
    Weight,
    Carbs
}

private fun MealItem.carbsPer100g(): Double? {
    val dishCarbs = selectedDish?.let { CarbCalculator.calculateCarbsPercentage(it.components) }
    val baseFoodCarbs = selectedBaseFood?.carbsPer100g
    val carbsPer100g = dishCarbs ?: baseFoodCarbs
    return carbsPer100g?.takeIf { it > 0.0 }
}

private fun formatDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

private fun syncMealItem(
    item: MealItem,
    editedField: EditedField? = null,
    selectedDish: DishWithComposition? = item.selectedDish,
    selectedBaseFood: BaseFood? = item.selectedBaseFood
): MealItem {
    val updatedItem = item.copy(
        selectedDish = selectedDish,
        selectedBaseFood = selectedBaseFood
    )
    val carbsPer100g = updatedItem.carbsPer100g() ?: return updatedItem
    val weight = updatedItem.weightText.toDoubleOrNull()
    val carbs = updatedItem.carbsText.toDoubleOrNull()

    return when (editedField) {
        EditedField.Weight -> {
            val syncedCarbs = weight?.let { formatDecimal(it * carbsPer100g / 100.0) }.orEmpty()
            updatedItem.copy(carbsText = syncedCarbs)
        }
        EditedField.Carbs -> {
            val syncedWeight = carbs?.let { formatDecimal(it * 100.0 / carbsPer100g) }.orEmpty()
            updatedItem.copy(weightText = syncedWeight)
        }
        null -> when {
            weight != null -> updatedItem.copy(carbsText = formatDecimal(weight * carbsPer100g / 100.0))
            carbs != null -> updatedItem.copy(weightText = formatDecimal(carbs * 100.0 / carbsPer100g))
            else -> updatedItem
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    dishes: List<Dish>,
    baseFoods: List<BaseFood>,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?,
    modifier: Modifier = Modifier
) {
    val resolveFoodName = rememberBaseFoodNameResolver()
    val mealItems = remember { mutableStateListOf<MealItem>(MealItem()) }

    val totalCarbs = remember(mealItems.toList()) {
        mealItems.sumOf { it.carbsText.toDoubleOrNull() ?: 0.0 }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Strings.totalCarbs(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${((totalCarbs * 10).toInt() / 10.0)}g",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 54.sp
                )
            }
        }

        Text(
            text = Strings.foodsOnPlate(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(mealItems) { index: Int, item: MealItem ->
                MealItemRow(
                    index = index,
                    item = item,
                    dishes = dishes,
                    baseFoods = baseFoods,
                    resolveFoodName = resolveFoodName,
                    onSelectDish = onSelectDish,
                    onSelectBaseFood = onSelectBaseFood,
                    onUpdate = { updated -> mealItems[index] = updated },
                    onDelete = { if (mealItems.size > 1) mealItems.removeAt(index) }
                )
                if (index < mealItems.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { mealItems.add(MealItem()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.addAnotherFoodToMeal())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealItemRow(
    index: Int,
    item: MealItem,
    dishes: List<Dish>,
    baseFoods: List<BaseFood>,
    resolveFoodName: (String) -> String,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?,
    onUpdate: (MealItem) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(item.displayName) }

    LaunchedEffect(item.selectedDish, item.selectedBaseFood) {
        val newDisplayName = item.selectedDish?.dish?.name
            ?: item.selectedBaseFood?.name?.let(resolveFoodName)
            ?: ""
        if (newDisplayName != searchQuery) {
            searchQuery = newDisplayName
        }
    }

    val normalizedQuery = searchQuery.removeDiacritics()
    val filteredDishes by remember(searchQuery, dishes, baseFoods) {
        derivedStateOf {
            val matchingDishes = if (searchQuery.isEmpty()) dishes else {
                dishes.filter { it.name.removeDiacritics().contains(normalizedQuery, ignoreCase = true) }
            }
            val matchingFoods = if (searchQuery.isEmpty()) baseFoods else {
                baseFoods.filter { food ->
                    matchesBaseFoodQuery(
                        rawName = food.name,
                        localizedName = resolveFoodName(food.name),
                        query = searchQuery
                    )
                }
            }
            matchingDishes to matchingFoods
        }
    }
    val (filteredDishList, filteredFoodList) = filteredDishes

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1.5f)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            expanded = true
                        },
                        label = {
                            Text(
                                text = Strings.mealItemLabel(index + 1),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )

                    if (filteredDishList.isNotEmpty() || filteredFoodList.isNotEmpty()) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier.exposedDropdownSize().heightIn(max = 280.dp)
                        ) {
                            filteredDishList.forEach { dish ->
                                DropdownMenuItem(
                                    text = { Text(dish.name) },
                                    onClick = {
                                        searchQuery = dish.name
                                        onUpdate(
                                            syncMealItem(
                                                item = item,
                                                selectedDish = onSelectDish(dish.id),
                                                selectedBaseFood = null
                                            )
                                        )
                                        expanded = false
                                    }
                                )
                            }
                            filteredFoodList.forEach { food ->
                                DropdownMenuItem(
                                    text = { Text(resolveFoodName(food.name)) },
                                    onClick = {
                                        searchQuery = resolveFoodName(food.name)
                                        onUpdate(
                                            syncMealItem(
                                                item = item,
                                                selectedDish = null,
                                                selectedBaseFood = onSelectBaseFood(food.id)
                                            )
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
                value = item.weightText,
                onValueChange = {
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        onUpdate(syncMealItem(item.copy(weightText = it), EditedField.Weight))
                    }
                },
                label = {
                    Text(
                        text = Strings.weight(),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(0.8f),
                suffix = { Text("g") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = item.carbsText,
                onValueChange = {
                    if (it.all { char -> char.isDigit() || char == '.' }) {
                        onUpdate(syncMealItem(item.copy(carbsText = it), EditedField.Carbs))
                    }
                },
                label = {
                    Text(
                        text = Strings.carbs(),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(0.8f),
                suffix = { Text("g") }
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = Strings.delete(), tint = MaterialTheme.colorScheme.error)
            }
        }
        
        item.selectedDish?.let { composition ->
            Text(
                text = Strings.carbsPercent(((CarbCalculator.calculateCarbsPercentage(composition.components) * 10).toInt() / 10.0).toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        item.selectedBaseFood?.let { food ->
            Text(
                text = Strings.carbsPercent(food.carbsPer100g.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}
