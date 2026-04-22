package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glicocalc.database.BaseFood
import com.glicocalc.database.Dish
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.MealType
import com.glicocalc.logic.CarbCalculator
import com.glicocalc.logic.removeDiacritics
import com.glicocalc.models.DishWithComposition

data class MealItem(
    val selectedDish: DishWithComposition? = null,
    val selectedBaseFood: BaseFood? = null,
    val weightText: String = "",
    val carbsText: String = ""
) {
    val displayName: String get() = selectedDish?.dish?.name ?: selectedBaseFood?.name ?: ""
}

private data class SearchableDish(
    val dish: Dish,
    val normalizedName: String
)

private data class SearchableFood(
    val food: BaseFood,
    val localizedName: String,
    val normalizedRawName: String,
    val normalizedLocalizedName: String
)

private enum class EditedField {
    Weight,
    Carbs
}

private enum class MealItemSelectionType {
    Dish,
    Food,
    None
}

private data class PersistedMealItem(
    val selectionType: MealItemSelectionType,
    val selectedId: Long? = null,
    val weightText: String = "",
    val carbsText: String = ""
)

private fun serializeMealItems(items: List<MealItem>): String {
    return items.joinToString(separator = "\n") { item ->
        val (type, id) = when {
            item.selectedDish != null -> MealItemSelectionType.Dish.name to item.selectedDish.dish.id.toString()
            item.selectedBaseFood != null -> MealItemSelectionType.Food.name to item.selectedBaseFood.id.toString()
            else -> MealItemSelectionType.None.name to ""
        }
        listOf(type, id, item.weightText, item.carbsText).joinToString(separator = "\t")
    }
}

private fun deserializeMealItems(
    serialized: String,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?
): List<MealItem> {
    return serialized
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@mapNotNull null
            val persisted = PersistedMealItem(
                selectionType = MealItemSelectionType.entries.firstOrNull { it.name == parts[0] } ?: MealItemSelectionType.None,
                selectedId = parts[1].toLongOrNull(),
                weightText = parts[2],
                carbsText = parts[3]
            )
            when (persisted.selectionType) {
                MealItemSelectionType.Dish -> {
                    val dish = persisted.selectedId?.let(onSelectDish) ?: return@mapNotNull null
                    MealItem(
                        selectedDish = dish,
                        weightText = persisted.weightText,
                        carbsText = persisted.carbsText
                    )
                }
                MealItemSelectionType.Food -> {
                    val food = persisted.selectedId?.let(onSelectBaseFood) ?: return@mapNotNull null
                    MealItem(
                        selectedBaseFood = food,
                        weightText = persisted.weightText,
                        carbsText = persisted.carbsText
                    )
                }
                MealItemSelectionType.None -> MealItem(
                    weightText = persisted.weightText,
                    carbsText = persisted.carbsText
                )
            }
        }
        .toList()
}

private fun isMeaningfulMealItem(item: MealItem): Boolean {
    return item.selectedDish != null ||
        item.selectedBaseFood != null ||
        item.weightText.isNotBlank() ||
        item.carbsText.isNotBlank()
}

@Composable
private fun ClearTextButton(
    onClear: () -> Unit
) {
    IconButton(onClick = onClear) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = Strings.clearText()
        )
    }
}

private fun MealItem.carbsPer100g(): Double? {
    val dishCarbs = selectedDish?.let { CarbCalculator.calculateCarbsPercentage(it.components) }
    val baseFoodCarbs = selectedBaseFood?.carbsPer100g
    val carbsPer100g = dishCarbs ?: baseFoodCarbs
    return carbsPer100g?.takeIf { it > 0.0 }
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
    repository: GlicoRepository,
    dishes: List<Dish>,
    baseFoods: List<BaseFood>,
    mealTypes: List<MealType>,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?,
    resumeSignal: Int,
    modifier: Modifier = Modifier
) {
    val resolveFoodName = rememberBaseFoodNameResolver()
    val resolveMealTypeName = rememberMealTypeNameResolver()
    val initialMealItems = remember {
        deserializeMealItems(
            serialized = repository.getCalculatorMealDraft().orEmpty(),
            onSelectDish = onSelectDish,
            onSelectBaseFood = onSelectBaseFood
        ).ifEmpty { listOf(MealItem()) }
    }
    val mealItems = remember { mutableStateListOf<MealItem>().apply { addAll(initialMealItems) } }
    val persistedMealTypeId = remember { repository.getCalculatorMealTypeId() }
    var selectedMealTypeId by remember { mutableStateOf<Long?>(null) }
    val searchableDishes = remember(dishes) {
        dishes.map { dish ->
            SearchableDish(
                dish = dish,
                normalizedName = dish.name.removeDiacritics()
            )
        }
    }
    val searchableFoods = remember(baseFoods, resolveFoodName) {
        baseFoods.map { food ->
            val localizedName = resolveFoodName(food.name)
            SearchableFood(
                food = food,
                localizedName = localizedName,
                normalizedRawName = food.name.removeDiacritics(),
                normalizedLocalizedName = localizedName.removeDiacritics()
            )
        }
    }

    val totalCarbs = remember(mealItems.toList()) {
        mealItems.sumOf { it.carbsText.toDoubleOrNull() ?: 0.0 }
    }
    val selectedMealType = remember(selectedMealTypeId, mealTypes) {
        mealTypes.firstOrNull { it.id == selectedMealTypeId }
    }
    val hasEditableMeal = remember(mealItems.toList()) { mealItems.any(::isMeaningfulMealItem) }

    LaunchedEffect(resumeSignal, mealTypes) {
        val restoredMealTypeId = persistedMealTypeId?.takeIf { savedId ->
            mealTypes.any { it.id == savedId }
        }
        val activeMealTypeId = selectedMealTypeId?.takeIf { activeId ->
            mealTypes.any { it.id == activeId }
        }
        selectedMealTypeId = activeMealTypeId ?: restoredMealTypeId ?: nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
    }

    LaunchedEffect(mealItems.toList()) {
        repository.saveCalculatorMealDraft(serializeMealItems(mealItems))
    }

    LaunchedEffect(selectedMealTypeId) {
        repository.saveCalculatorMealTypeId(selectedMealTypeId)
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

        if (mealTypes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.foodsOnPlate(),
                style = MaterialTheme.typography.titleMedium
            )
            selectedMealType?.let { mealType ->
                val remainingCarbs = totalCarbs - mealType.targetCarbs
                val remainingText = when {
                    kotlin.math.abs(remainingCarbs) < 0.05 -> "0g"
                    remainingCarbs > 0 -> "+${formatDecimal(remainingCarbs)}g"
                    else -> "-${formatDecimal(kotlin.math.abs(remainingCarbs))}g"
                }
                val remainingColor = when {
                    kotlin.math.abs(remainingCarbs) < 0.05 -> MaterialTheme.colorScheme.primary
                    remainingCarbs > 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = remainingColor
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(mealItems) { index: Int, item: MealItem ->
                MealItemRow(
                    index = index,
                    item = item,
                    searchableDishes = searchableDishes,
                    searchableFoods = searchableFoods,
                    onSelectDish = onSelectDish,
                    onSelectBaseFood = onSelectBaseFood,
                    onUpdate = { updated -> mealItems[index] = updated },
                    canDelete = mealItems.size > 1,
                    onDelete = { if (mealItems.size > 1) mealItems.removeAt(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { mealItems.add(MealItem()) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.addAnotherFoodToMeal())
            }

            OutlinedButton(
                onClick = {
                    mealItems.clear()
                    mealItems.add(MealItem())
                    selectedMealTypeId = nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
                    repository.clearCalculatorDraft()
                },
                modifier = Modifier.weight(1f),
                enabled = hasEditableMeal,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.clearMeal())
            }
        }

        if (mealTypes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            MealTypeSelector(
                mealTypes = mealTypes,
                selectedMealTypeId = selectedMealTypeId,
                onMealTypeSelected = { selectedMealTypeId = it },
                resolveMealTypeName = resolveMealTypeName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTypeSelector(
    mealTypes: List<MealType>,
    selectedMealTypeId: Long?,
    onMealTypeSelected: (Long) -> Unit,
    resolveMealTypeName: (String) -> String
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedMealType = mealTypes.firstOrNull { it.id == selectedMealTypeId } ?: mealTypes.first()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = "${resolveMealTypeName(selectedMealType.name)} • ${formatHour(selectedMealType.hourOfDay.toInt())}",
                onValueChange = {},
                readOnly = true,
                label = { Text(Strings.mealTypeSelector()) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                mealTypes.forEach { mealType ->
                    DropdownMenuItem(
                        text = { Text("${resolveMealTypeName(mealType.name)} • ${formatHour(mealType.hourOfDay.toInt())}") },
                        onClick = {
                            onMealTypeSelected(mealType.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = "${formatDecimal(selectedMealType.targetCarbs)}g",
            onValueChange = {},
            readOnly = true,
            label = { Text(Strings.carbs()) },
            textStyle = LocalTextStyle.current.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.width(112.dp)
        )
    }
}

private fun nextMealTypeForHour(mealTypes: List<MealType>, currentHour: Int): MealType? {
    if (mealTypes.isEmpty()) return null
    return mealTypes.firstOrNull { it.hourOfDay.toInt() >= currentHour } ?: mealTypes.first()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealItemRow(
    index: Int,
    item: MealItem,
    searchableDishes: List<SearchableDish>,
    searchableFoods: List<SearchableFood>,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?,
    onUpdate: (MealItem) -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(item.displayName) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart && canDelete) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(item.selectedDish, item.selectedBaseFood) {
        val newDisplayName = item.selectedDish?.dish?.name
            ?: searchableFoods.firstOrNull { it.food.id == item.selectedBaseFood?.id }?.localizedName
            ?: ""
        if (newDisplayName != searchQuery) {
            searchQuery = newDisplayName
        }
    }

    val normalizedQuery = searchQuery.removeDiacritics()
    val filteredResults by remember(searchQuery, searchableDishes, searchableFoods) {
        derivedStateOf {
            val matchingDishes = if (searchQuery.isBlank()) {
                searchableDishes.take(16).map { it.dish }
            } else {
                searchableDishes
                    .asSequence()
                    .filter { it.normalizedName.contains(normalizedQuery, ignoreCase = true) }
                    .map { it.dish }
                    .take(20)
                    .toList()
            }
            val matchingFoods = if (searchQuery.isBlank()) {
                searchableFoods.take(24)
            } else {
                searchableFoods
                    .asSequence()
                    .filter {
                        it.normalizedRawName.contains(normalizedQuery, ignoreCase = true) ||
                            it.normalizedLocalizedName.contains(normalizedQuery, ignoreCase = true)
                    }
                    .take(24)
                    .toList()
            }
            matchingDishes to matchingFoods
        }
    }
    val (filteredDishList, filteredFoodList) = filteredResults

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (canDelete) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canDelete) Strings.delete() else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (canDelete) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (canDelete) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = Strings.delete(),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        val carbsInfo = item.selectedDish?.let { composition ->
            Strings.carbsPercent(formatDecimal(CarbCalculator.calculateCarbsPercentage(composition.components)))
        } ?: item.selectedBaseFood?.let { food ->
            Strings.carbsPercent(formatDecimal(food.carbsPer100g))
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                expanded = true
                            },
                            label = { Text(Strings.mealItemLabel(index + 1)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true,
                            maxLines = 1,
                            suffix = {
                                if (searchQuery.isNotBlank()) {
                                    ClearTextButton {
                                        searchQuery = ""
                                        onUpdate(item.copy(selectedDish = null, selectedBaseFood = null))
                                    }
                                }
                            }
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
                                filteredFoodList.forEach { searchableFood ->
                                    DropdownMenuItem(
                                        text = { Text(searchableFood.localizedName) },
                                        onClick = {
                                            searchQuery = searchableFood.localizedName
                                            onUpdate(
                                                syncMealItem(
                                                    item = item,
                                                    selectedDish = null,
                                                    selectedBaseFood = onSelectBaseFood(searchableFood.food.id)
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

                carbsInfo?.let { info ->
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = item.weightText,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() || char == '.' }) {
                                onUpdate(syncMealItem(item.copy(weightText = it), EditedField.Weight))
                            }
                        },
                        label = { Text(Strings.weight()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        suffix = { Text("g") },
                        singleLine = true,
                        trailingIcon = {
                            if (item.weightText.isNotBlank()) {
                                ClearTextButton {
                                    onUpdate(syncMealItem(item.copy(weightText = ""), EditedField.Weight))
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.carbsText,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() || char == '.' }) {
                                    onUpdate(syncMealItem(item.copy(carbsText = it), EditedField.Carbs))
                                }
                            },
                            label = { Text(Strings.carbs()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            suffix = { Text("g") },
                            singleLine = true,
                            trailingIcon = {
                                if (item.carbsText.isNotBlank()) {
                                    ClearTextButton {
                                        onUpdate(syncMealItem(item.copy(carbsText = ""), EditedField.Carbs))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
