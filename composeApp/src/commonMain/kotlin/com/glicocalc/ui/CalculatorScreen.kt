package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glicocalc.database.BaseFood
import com.glicocalc.database.Dish
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.MealType
import com.glicocalc.logic.CarbCalculator
import com.glicocalc.logic.removeDiacritics
import com.glicocalc.models.DishWithComposition

private var nextMealItemId = 0L

data class MealItem(
    val id: Long = nextMealItemId++,
    val selectedDish: DishWithComposition? = null,
    val selectedBaseFood: BaseFood? = null,
    val weightText: String = "",
    val carbsText: String = "",
    val usePieces: Boolean = false
) {
    val displayName: String get() = selectedDish?.dish?.name ?: selectedBaseFood?.name ?: ""
}

private fun BaseFood.weightPerPiece(): Double? {
    if (isPacked == 0L) return null
    val pw = packWeight ?: return null
    val pc = packCount ?: return null
    if (pc <= 0L) return null
    return pw / pc
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
    val carbsText: String = "",
    val usePieces: Boolean = false
)

private fun serializeMealItems(items: List<MealItem>): String {
    return items.joinToString(separator = "\n") { item ->
        val (type, id) = when {
            item.selectedDish != null -> MealItemSelectionType.Dish.name to item.selectedDish.dish.id.toString()
            item.selectedBaseFood != null -> MealItemSelectionType.Food.name to item.selectedBaseFood.id.toString()
            else -> MealItemSelectionType.None.name to ""
        }
        listOf(type, id, item.weightText, item.carbsText, if (item.usePieces) "1" else "0").joinToString(separator = "\t")
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
            if (parts.size < 4) return@mapNotNull null
            val usePieces = parts.size >= 5 && parts[4] == "1"
            val persisted = PersistedMealItem(
                selectionType = MealItemSelectionType.entries.firstOrNull { it.name == parts[0] } ?: MealItemSelectionType.None,
                selectedId = parts[1].toLongOrNull(),
                weightText = parts[2],
                carbsText = parts[3],
                usePieces = usePieces
            )
            when (persisted.selectionType) {
                MealItemSelectionType.Dish -> {
                    val dish = persisted.selectedId?.let(onSelectDish)
                    if (dish != null) {
                        MealItem(
                            selectedDish = dish,
                            weightText = persisted.weightText,
                            carbsText = persisted.carbsText,
                            usePieces = false
                        )
                    } else {
                        MealItem(
                            weightText = persisted.weightText,
                            carbsText = persisted.carbsText
                        )
                    }
                }
                MealItemSelectionType.Food -> {
                    val food = persisted.selectedId?.let(onSelectBaseFood)
                    if (food != null) {
                        MealItem(
                            selectedBaseFood = food,
                            weightText = persisted.weightText,
                            carbsText = persisted.carbsText,
                            usePieces = persisted.usePieces && food.isPacked != 0L
                        )
                    } else {
                        MealItem(
                            weightText = persisted.weightText,
                            carbsText = persisted.carbsText
                        )
                    }
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
    val dishCarbs = selectedDish?.let { CarbCalculator.calculateCarbsPercentage(it.components, it.dish.totalCookedWeight) }
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

    if (updatedItem.usePieces) {
        val baseFood = updatedItem.selectedBaseFood
        if (baseFood == null || baseFood.isPacked == 0L) {
            return updatedItem.copy(usePieces = false)
        }
        val weightPerPiece = baseFood.weightPerPiece() ?: return updatedItem.copy(usePieces = false)

        val weight = updatedItem.weightText.toDoubleOrNull()
        val carbs = updatedItem.carbsText.toDoubleOrNull()

        return when (editedField) {
            EditedField.Weight -> {
                val syncedCarbs = weight?.let { pieces ->
                    val grams = pieces * weightPerPiece
                    formatDecimal(grams * carbsPer100g / 100.0)
                }.orEmpty()
                updatedItem.copy(carbsText = syncedCarbs)
            }
            EditedField.Carbs -> {
                val syncedWeight = carbs?.let { carbsVal ->
                    val grams = carbsVal * 100.0 / carbsPer100g
                    formatDecimal(grams / weightPerPiece)
                }.orEmpty()
                updatedItem.copy(weightText = syncedWeight)
            }
            null -> when {
                weight != null -> {
                    val grams = weight * weightPerPiece
                    updatedItem.copy(carbsText = formatDecimal(grams * carbsPer100g / 100.0))
                }
                carbs != null -> {
                    val grams = carbs * 100.0 / carbsPer100g
                    val pieces = formatDecimal(grams / weightPerPiece)
                    updatedItem.copy(weightText = pieces)
                }
                else -> updatedItem
            }
        }
    } else {
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
}

private fun convertWeightForPieceToggle(
    weightText: String,
    toPieces: Boolean,
    baseFood: BaseFood?
): String {
    val food = baseFood ?: return weightText
    val weightPerPiece = food.weightPerPiece() ?: return weightText
    val value = weightText.toDoubleOrNull() ?: return ""
    return if (toPieces) {
        formatDecimal(value / weightPerPiece)
    } else {
        formatDecimal(value * weightPerPiece)
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
    var selectedMealTypeId by remember { mutableStateOf<Long?>(null) }
    val dishesWithCarbs = remember(dishes, baseFoods) { repository.getAllDishesWithCarbs() }
    val dishCarbsMap = remember(dishesWithCarbs) {
        dishesWithCarbs.associate { it.dish.id to it.carbsPer100g }
    }
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
    val focusManager = LocalFocusManager.current

    LaunchedEffect(resumeSignal, mealTypes) {
        val activeMealTypeId = selectedMealTypeId?.takeIf { activeId ->
            mealTypes.any { it.id == activeId }
        }
        selectedMealTypeId = activeMealTypeId ?: nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
    }

    LaunchedEffect(mealItems.toList()) {
        repository.saveCalculatorMealDraft(serializeMealItems(mealItems))
    }

    LaunchedEffect(selectedMealTypeId) {
        repository.saveCalculatorMealTypeId(selectedMealTypeId)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    focusManager.clearFocus()
                }
            }
    ) {
        val density = LocalDensity.current
        val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
        val isCompactHeight = maxHeight < 620.dp || isKeyboardVisible
        val screenPadding = if (isCompactHeight) 12.dp else 16.dp
        val sectionGap = if (isCompactHeight) 8.dp else 16.dp
        val listGap = if (isCompactHeight) 8.dp else 12.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isKeyboardVisible) {
                TotalCarbsCard(
                    totalCarbs = totalCarbs,
                    compact = isCompactHeight,
                    modifier = Modifier.padding(bottom = sectionGap)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = listGap),
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
                verticalArrangement = Arrangement.spacedBy(listGap)
            ) {
                itemsIndexed(mealItems, key = { _, item -> item.id }) { index: Int, item: MealItem ->
                    MealItemRow(
                        index = index,
                        item = item,
                        dishCarbsMap = dishCarbsMap,
                        searchableDishes = searchableDishes,
                        searchableFoods = searchableFoods,
                        onSelectDish = onSelectDish,
                        onSelectBaseFood = onSelectBaseFood,
                        onUpdate = { updated -> mealItems[index] = updated },
                        canDelete = mealItems.size > 1,
                        onDelete = {
                            if (mealItems.size > 1) {
                                mealItems.removeAt(index)
                            } else {
                                mealItems[index] = MealItem()
                            }
                        }
                    )
                }
            }

            if (!isKeyboardVisible) {
                Spacer(modifier = Modifier.height(listGap))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { mealItems.add(MealItem()) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (isCompactHeight) 44.dp else 48.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (isCompactHeight) 44.dp else 48.dp),
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
                    Spacer(modifier = Modifier.height(sectionGap))
                    MealTypeSelector(
                        mealTypes = mealTypes,
                        selectedMealTypeId = selectedMealTypeId,
                        onMealTypeSelected = { selectedMealTypeId = it },
                        resolveMealTypeName = resolveMealTypeName,
                        compact = isCompactHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalCarbsCard(
    totalCarbs: Double,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 18.dp else 20.dp,
                vertical = if (compact) 12.dp else 20.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Strings.totalCarbs(),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "${((totalCarbs * 10).toInt() / 10.0)}g",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = if (compact) 42.sp else 54.sp
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
    resolveMealTypeName: (String) -> String,
    compact: Boolean
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
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 56.dp else 64.dp)
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
            modifier = Modifier
                .width(if (compact) 104.dp else 112.dp)
                .heightIn(min = if (compact) 56.dp else 64.dp)
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
    dishCarbsMap: Map<Long, Double>,
    searchableDishes: List<SearchableDish>,
    searchableFoods: List<SearchableFood>,
    onSelectDish: (Long) -> DishWithComposition?,
    onSelectBaseFood: (Long) -> BaseFood?,
    onUpdate: (MealItem) -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(item.displayName) }
    var textFieldState by remember { mutableStateOf(TextFieldValue(item.displayName)) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        }
    )

    LaunchedEffect(item.selectedDish, item.selectedBaseFood, searchableFoods) {
        val newDisplayName = item.selectedDish?.dish?.name
            ?: searchableFoods.firstOrNull { it.food.id == item.selectedBaseFood?.id }?.localizedName
            ?: item.selectedBaseFood?.name
            ?: ""
        if (newDisplayName != searchQuery) {
            searchQuery = newDisplayName
            textFieldState = TextFieldValue(newDisplayName)
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

    val selectedBaseFood = item.selectedBaseFood
    val isPackedFood = selectedBaseFood != null && selectedBaseFood.isPacked != 0L

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
            Strings.carbsPercent(formatDecimal(CarbCalculator.calculateCarbsPercentage(composition.components, composition.dish.totalCookedWeight)))
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
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            value = textFieldState,
                            onValueChange = { newValue ->
                                textFieldState = newValue
                                searchQuery = newValue.text
                                expanded = true
                            },
                            label = {
                                Text(
                                    if (carbsInfo != null) carbsInfo
                                    else Strings.mealItemLabel(index + 1)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true,
                            suffix = {
                                if (searchQuery.isNotBlank()) {
                                    ClearTextButton {
                                        searchQuery = ""
                                        textFieldState = TextFieldValue("")
                                        onUpdate(item.copy(selectedDish = null, selectedBaseFood = null, weightText = "", carbsText = "", usePieces = false))
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
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dish.name,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                dishCarbsMap[dish.id]?.let { carbs ->
                                                    Text(
                                                        text = "${formatDecimal(carbs)}%",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            searchQuery = dish.name
                                            textFieldState = TextFieldValue(dish.name)
                                            onUpdate(
                                                syncMealItem(
                                                    item = item.copy(weightText = "", carbsText = "", usePieces = false),
                                                    selectedDish = onSelectDish(dish.id),
                                                    selectedBaseFood = null
                                                )
                                            )
                                            expanded = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                                filteredFoodList.forEach { searchableFood ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = searchableFood.localizedName,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = "${formatDecimal(searchableFood.food.carbsPer100g)}%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            searchQuery = searchableFood.localizedName
                                            textFieldState = TextFieldValue(searchableFood.localizedName)
                                            onUpdate(
                                                syncMealItem(
                                                    item = item.copy(weightText = "", carbsText = "", usePieces = false),
                                                    selectedDish = null,
                                                    selectedBaseFood = onSelectBaseFood(searchableFood.food.id)
                                                )
                                            )
                                            expanded = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isPackedFood) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Strings.usePieces(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = item.usePieces,
                            onCheckedChange = { usePieces ->
                                if (usePieces) {
                                    val newWeightText = convertWeightForPieceToggle(
                                        weightText = item.weightText,
                                        toPieces = true,
                                        baseFood = selectedBaseFood
                                    )
                                    onUpdate(
                                        syncMealItem(
                                            item.copy(
                                                weightText = newWeightText,
                                                usePieces = true
                                            )
                                        )
                                    )
                                } else {
                                    val newWeightText = convertWeightForPieceToggle(
                                        weightText = item.weightText,
                                        toPieces = false,
                                        baseFood = selectedBaseFood
                                    )
                                    onUpdate(
                                        syncMealItem(
                                            item.copy(
                                                weightText = newWeightText,
                                                usePieces = false
                                            )
                                        )
                                    )
                                }
                            }
                        )
                    }
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
                        label = { Text(if (item.usePieces && isPackedFood) Strings.pieces() else Strings.weight()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        suffix = { Text(if (item.usePieces && isPackedFood) Strings.pcs() else "g") },
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
