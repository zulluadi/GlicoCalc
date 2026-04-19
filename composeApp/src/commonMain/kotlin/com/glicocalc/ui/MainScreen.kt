package com.glicocalc.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.glicocalc.database.GlicoRepository
import com.glicocalc.models.DishWithComposition
import com.glicocalc.ui.theme.GlicoCalcTheme
import kotlinx.coroutines.launch

@Composable
fun MainApp(repository: GlicoRepository) {
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Calculator) }
    var editingDishId by remember { mutableStateOf<Long?>(null) }

    val baseFoods by repository.getAllBaseFoods().collectAsState(initial = emptyList())
    val dishes by repository.getAllDishes().collectAsState(initial = emptyList())

    GlicoCalcTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Calculator,
                        onClick = { currentScreen = Screen.Calculator },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        label = { Text("Calculator") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Dishes || currentScreen == Screen.DishEditor,
                        onClick = { currentScreen = Screen.Dishes },
                        icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                        label = { Text("Mâncăruri") }
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.FoodList,
                        onClick = { currentScreen = Screen.FoodList },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Alimente") }
                    )
                }
            }
        ) { padding ->
            val modifier = Modifier.padding(padding)
            
            when (currentScreen) {
                Screen.Calculator -> CalculatorScreen(
                    dishes = dishes.map { com.glicocalc.database.Dish(it.id, it.name) },
                    baseFoods = baseFoods.map { com.glicocalc.database.BaseFood(it.id, it.name, it.carbsPer100g) },
                    onSelectDish = { repository.getDishWithComposition(it) },
                    onSelectBaseFood = { repository.getBaseFood(it) },
                    modifier = modifier
                )
                Screen.FoodList -> FoodListScreen(
                    foods = baseFoods,
                    onAddFood = { name, carbs -> scope.launch { repository.insertBaseFood(name, carbs) } },
                    onEditFood = { id, name, carbs -> scope.launch { repository.updateBaseFood(id, name, carbs) } },
                    onDeleteFood = { scope.launch { repository.deleteBaseFood(it) } },
                    onUndeleteFood = { name, carbs -> scope.launch { repository.insertBaseFood(name, carbs) } },
                    modifier = modifier
                )
                Screen.Dishes -> {
                    val dishesWithCarbs = remember { repository.getAllDishesWithCarbs() }
                    DishListScreen(
                        dishesWithCarbs = dishesWithCarbs.map { com.glicocalc.database.GlicoRepository.DishWithCarbs(it.dish, it.carbsPer100g) },
                        onAddDish = { currentScreen = Screen.DishEditor; editingDishId = null },
                        onEditDish = { id -> currentScreen = Screen.DishEditor; editingDishId = id },
                        onDeleteDish = { scope.launch { repository.deleteDish(it) } },
                        modifier = modifier
                    )
                }
                Screen.DishEditor -> {
                    val initialDish = editingDishId?.let { repository.getDishWithComposition(it) }
                     DishEditorScreen(
                        initialName = initialDish?.dish?.name ?: "",
                        initialComponents = initialDish?.components?.map { it.baseFoodId to it.percentage } ?: emptyList(),
                        allBaseFoods = baseFoods.map { com.glicocalc.database.BaseFood(it.id, it.name, it.carbsPer100g) },
                        onCancel = { currentScreen = Screen.Dishes },
                        onSave = { name, components ->
                            scope.launch {
                                if (editingDishId == null) {
                                    repository.insertDishWithComponents(name, components)
                                } else {
                                    repository.updateDishWithComponents(editingDishId!!, name, components)
                                }
                                currentScreen = Screen.Dishes
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class Screen {
    Calculator, FoodList, Dishes, DishEditor
}
