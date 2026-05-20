package com.glicocalc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glicocalc.database.BaseFood
import com.glicocalc.database.Dish
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.MealType
import com.glicocalc.logic.CarbCalculator
import com.glicocalc.logic.removeDiacritics
import com.glicocalc.models.DishWithComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private data class ActiveFoodPicker(
    val itemId: Long,
    val initialQuery: String
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
    val mealItems = remember { mutableStateListOf<MealItem>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedMealTypeId by remember { mutableStateOf<Long?>(null) }
    var dishesWithCarbs by remember(dishes, baseFoods) { mutableStateOf(emptyList<GlicoRepository.DishWithCarbs>()) }
    var activeFoodPicker by remember { mutableStateOf<ActiveFoodPicker?>(null) }
    var hasLoadedCalculatorDraft by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (draft, draftMealTypeId) = withContext(Dispatchers.IO) {
            repository.getCalculatorMealDraft() to repository.getCalculatorMealTypeId()
        }
        val items = deserializeMealItems(
            serialized = draft.orEmpty(),
            onSelectDish = onSelectDish,
            onSelectBaseFood = onSelectBaseFood
        ).ifEmpty { listOf(MealItem()) }
        mealItems.addAll(items)
        selectedMealTypeId = draftMealTypeId
        hasLoadedCalculatorDraft = true
    }
    LaunchedEffect(dishes, baseFoods) {
        dishesWithCarbs = withContext(Dispatchers.Default) {
            repository.getAllDishesWithCarbs()
        }
    }
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
    val foodPickerOptions = remember(searchableDishes, searchableFoods, dishCarbsMap) {
        searchableDishes.map { searchableDish ->
            FoodPickerOption(
                key = "dish-${searchableDish.dish.id}",
                title = searchableDish.dish.name,
                detail = dishCarbsMap[searchableDish.dish.id]?.let { "${formatDecimal(it)}%" },
                searchTerms = listOf(searchableDish.dish.name)
            )
        } + searchableFoods.map { searchableFood ->
            FoodPickerOption(
                key = "food-${searchableFood.food.id}",
                title = searchableFood.localizedName,
                detail = "${formatDecimal(searchableFood.food.carbsPer100g)}%",
                searchTerms = listOf(searchableFood.food.name, searchableFood.localizedName)
            )
        }
    }

    val totalCarbs = remember(mealItems.toList()) {
        mealItems.sumOf { it.carbsText.toDoubleOrNull() ?: 0.0 }
    }
    val selectedMealType = remember(selectedMealTypeId, mealTypes) {
        mealTypes.firstOrNull { it.id == selectedMealTypeId }
    }
    val hasEditableMeal = remember(mealItems.toList()) { mealItems.any(::isMeaningfulMealItem) || mealItems.size > 1 }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(resumeSignal, mealTypes, hasLoadedCalculatorDraft) {
        if (!hasLoadedCalculatorDraft) return@LaunchedEffect
        val activeMealTypeId = selectedMealTypeId?.takeIf { activeId ->
            mealTypes.any { it.id == activeId }
        }
        selectedMealTypeId = activeMealTypeId ?: nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
    }

    LaunchedEffect(mealItems.toList(), hasLoadedCalculatorDraft) {
        if (!hasLoadedCalculatorDraft) return@LaunchedEffect
        repository.saveCalculatorMealDraft(serializeMealItems(mealItems))
    }

    LaunchedEffect(selectedMealTypeId, hasLoadedCalculatorDraft) {
        if (!hasLoadedCalculatorDraft) return@LaunchedEffect
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
        val useLandscapeLayout = maxWidth > maxHeight && maxWidth >= 600.dp && !isKeyboardVisible
        val isCompactHeight = maxHeight < 620.dp || isKeyboardVisible
        val screenPadding = if (isCompactHeight) 12.dp else 16.dp
        val sectionGap = if (isCompactHeight) 8.dp else 16.dp
        val listGap = if (isCompactHeight) 8.dp else 12.dp

        if (useLandscapeLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenPadding),
                horizontalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 340.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(sectionGap, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TotalCarbsCard(
                        totalCarbs = totalCarbs,
                        compact = isCompactHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CalculatorActions(
                        compact = isCompactHeight,
                        hasEditableMeal = hasEditableMeal,
                        stacked = true,
                        onAddFood = {
                            val newItem = MealItem()
                            mealItems.add(0, newItem)
                            activeFoodPicker = ActiveFoodPicker(itemId = newItem.id, initialQuery = "")
                            scope.launch { listState.scrollToItem(0) }
                        },
                        onClearMeal = {
                            mealItems.clear()
                            mealItems.add(MealItem())
                            selectedMealTypeId = nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
                            repository.clearCalculatorDraft()
                        }
                    )
                    if (mealTypes.isNotEmpty()) {
                        MealTypeSelector(
                            mealTypes = mealTypes,
                            selectedMealTypeId = selectedMealTypeId,
                            onMealTypeSelected = { selectedMealTypeId = it },
                            resolveMealTypeName = resolveMealTypeName,
                            compact = isCompactHeight
                        )
                    }
                }

                MealItemsList(
                    mealItems = mealItems,
                    listState = listState,
                    searchableFoods = searchableFoods,
                    selectedMealType = selectedMealType,
                    totalCarbs = totalCarbs,
                    listGap = listGap,
                    compact = isCompactHeight,
                    onEditFood = { item ->
                        activeFoodPicker = ActiveFoodPicker(
                            itemId = item.id,
                            initialQuery = item.selectedDish?.dish?.name
                                ?: searchableFoods.firstOrNull { it.food.id == item.selectedBaseFood?.id }?.localizedName
                                ?: item.selectedBaseFood?.name
                                ?: ""
                        )
                    },
                    onUpdate = { index, updated -> mealItems[index] = updated },
                    onDelete = { index ->
                        if (mealItems.size > 1) {
                            mealItems.removeAt(index)
                        } else {
                            mealItems[index] = MealItem()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
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

                MealItemsList(
                    mealItems = mealItems,
                    listState = listState,
                    searchableFoods = searchableFoods,
                    selectedMealType = selectedMealType,
                    totalCarbs = totalCarbs,
                    listGap = listGap,
                    compact = isCompactHeight,
                    onEditFood = { item ->
                        activeFoodPicker = ActiveFoodPicker(
                            itemId = item.id,
                            initialQuery = item.selectedDish?.dish?.name
                                ?: searchableFoods.firstOrNull { it.food.id == item.selectedBaseFood?.id }?.localizedName
                                ?: item.selectedBaseFood?.name
                                ?: ""
                        )
                    },
                    onUpdate = { index, updated -> mealItems[index] = updated },
                    onDelete = { index ->
                        if (mealItems.size > 1) {
                            mealItems.removeAt(index)
                        } else {
                            mealItems[index] = MealItem()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                if (!isKeyboardVisible) {
                    Spacer(modifier = Modifier.height(listGap))
                    CalculatorActions(
                        compact = isCompactHeight,
                        hasEditableMeal = hasEditableMeal,
                        stacked = false,
                        onAddFood = {
                            val newItem = MealItem()
                            mealItems.add(0, newItem)
                            activeFoodPicker = ActiveFoodPicker(itemId = newItem.id, initialQuery = "")
                            scope.launch { listState.scrollToItem(0) }
                        },
                        onClearMeal = {
                            mealItems.clear()
                            mealItems.add(MealItem())
                            selectedMealTypeId = nextMealTypeForHour(mealTypes, DeviceTime.currentHour24())?.id
                            repository.clearCalculatorDraft()
                        }
                    )

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

        activeFoodPicker?.let { picker ->
            FoodSearchPickerOverlay(
                initialQuery = picker.initialQuery,
                options = foodPickerOptions,
                onSelectOption = { option ->
                    val itemIndex = mealItems.indexOfFirst { it.id == picker.itemId }
                    val selectedDish = option.key
                        .takeIf { it.startsWith("dish-") }
                        ?.removePrefix("dish-")
                        ?.toLongOrNull()
                        ?.let(onSelectDish)
                    val selectedFood = option.key
                        .takeIf { it.startsWith("food-") }
                        ?.removePrefix("food-")
                        ?.toLongOrNull()
                        ?.let(onSelectBaseFood)
                    if (itemIndex >= 0 && (selectedDish != null || selectedFood != null)) {
                        mealItems[itemIndex] = syncMealItem(
                            item = mealItems[itemIndex].copy(weightText = "", carbsText = "", usePieces = false),
                            selectedDish = selectedDish,
                            selectedBaseFood = selectedFood
                        )
                    }
                    activeFoodPicker = null
                    focusManager.clearFocus()
                },
                onClearSelection = {
                    val itemIndex = mealItems.indexOfFirst { it.id == picker.itemId }
                    if (itemIndex >= 0) {
                        mealItems[itemIndex] = mealItems[itemIndex].copy(
                            selectedDish = null,
                            selectedBaseFood = null,
                            weightText = "",
                            carbsText = "",
                            usePieces = false
                        )
                    }
                },
                onDismiss = {
                    activeFoodPicker = null
                    focusManager.clearFocus()
                }
            )
        }
    }
}

@Composable
private fun MealItemsList(
    mealItems: List<MealItem>,
    listState: LazyListState,
    searchableFoods: List<SearchableFood>,
    selectedMealType: MealType?,
    totalCarbs: Double,
    listGap: Dp,
    compact: Boolean,
    onEditFood: (MealItem) -> Unit,
    onUpdate: (index: Int, item: MealItem) -> Unit,
    onDelete: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = listGap),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.foodsOnPlate(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            state = listState,
            verticalArrangement = Arrangement.spacedBy(listGap)
        ) {
            itemsIndexed(mealItems, key = { _, item -> item.id }) { index: Int, item: MealItem ->
                MealItemRow(
                    index = index,
                    item = item,
                    searchableFoods = searchableFoods,
                    onUpdate = { updated -> onUpdate(index, updated) },
                    canDelete = mealItems.size > 1,
                    compact = compact,
                    onEditFood = { onEditFood(item) },
                    onDelete = { onDelete(index) }
                )
            }
        }
    }
}

@Composable
private fun CalculatorActions(
    compact: Boolean,
    hasEditableMeal: Boolean,
    stacked: Boolean,
    onAddFood: () -> Unit,
    onClearMeal: () -> Unit
) {
    val buttonHeight = if (compact) 44.dp else 48.dp
    val content: @Composable RowScope.() -> Unit = {
        Button(
            onClick = onAddFood,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = buttonHeight),
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
            onClick = onClearMeal,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = buttonHeight),
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

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAddFood,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = buttonHeight),
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
                onClick = onClearMeal,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = buttonHeight),
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
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 18.dp else 20.dp,
                    vertical = if (compact) 12.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Strings.totalCarbs(),
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "${((totalCarbs * 10).toInt() / 10.0)}g",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = if (compact) 42.sp else 54.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
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
    searchableFoods: List<SearchableFood>,
    onUpdate: (MealItem) -> Unit,
    canDelete: Boolean,
    compact: Boolean,
    onEditFood: () -> Unit,
    onDelete: () -> Unit
) {
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

    val selectedBaseFood = item.selectedBaseFood
    val isPackedFood = selectedBaseFood != null && selectedBaseFood.isPacked != 0L
    val displayName = item.selectedDish?.dish?.name
        ?: searchableFoods.firstOrNull { it.food.id == item.selectedBaseFood?.id }?.localizedName
        ?: item.selectedBaseFood?.name
        ?: ""

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
                    .padding(
                        horizontal = if (compact) 12.dp else 14.dp,
                        vertical = if (compact) 6.dp else 8.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                if (carbsInfo != null) carbsInfo
                                else Strings.mealItemLabel(index + 1)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = onEditFood) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onEditFood()
                                }
                            }
                            .pointerInput(item.id) {
                                detectTapGestures { onEditFood() }
                            },
                        singleLine = true,
                        suffix = {
                            if (displayName.isNotBlank()) {
                                ClearTextButton {
                                    onUpdate(item.copy(selectedDish = null, selectedBaseFood = null, weightText = "", carbsText = "", usePieces = false))
                                }
                            }
                        }
                    )
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
