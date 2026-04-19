package com.glicocalc.ui

import androidx.compose.runtime.Composable
import glicocalc.composeapp.generated.resources.Res
import glicocalc.composeapp.generated.resources.add_another_food_to_meal
import glicocalc.composeapp.generated.resources.add_ingredient
import glicocalc.composeapp.generated.resources.add_dish
import glicocalc.composeapp.generated.resources.add_food
import glicocalc.composeapp.generated.resources.add_food_title
import glicocalc.composeapp.generated.resources.base_foods_title
import glicocalc.composeapp.generated.resources.cancel
import glicocalc.composeapp.generated.resources.carbs
import glicocalc.composeapp.generated.resources.carbs_percent
import glicocalc.composeapp.generated.resources.carbs_per_100g
import glicocalc.composeapp.generated.resources.carbs_per_100g_label
import glicocalc.composeapp.generated.resources.close
import glicocalc.composeapp.generated.resources.composition_ingredients
import glicocalc.composeapp.generated.resources.delete
import glicocalc.composeapp.generated.resources.delete_row
import glicocalc.composeapp.generated.resources.dish_name_label
import glicocalc.composeapp.generated.resources.dishes_title
import glicocalc.composeapp.generated.resources.edit
import glicocalc.composeapp.generated.resources.edit_dish_title
import glicocalc.composeapp.generated.resources.edit_food_title
import glicocalc.composeapp.generated.resources.food_deleted
import glicocalc.composeapp.generated.resources.food_name
import glicocalc.composeapp.generated.resources.foods_on_plate
import glicocalc.composeapp.generated.resources.ingredient
import glicocalc.composeapp.generated.resources.language
import glicocalc.composeapp.generated.resources.meal_item_label
import glicocalc.composeapp.generated.resources.nav_calculator
import glicocalc.composeapp.generated.resources.nav_dishes
import glicocalc.composeapp.generated.resources.nav_foods
import glicocalc.composeapp.generated.resources.new_dish_title
import glicocalc.composeapp.generated.resources.save
import glicocalc.composeapp.generated.resources.search_food_placeholder
import glicocalc.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.stringResource
import glicocalc.composeapp.generated.resources.total_carbs
import glicocalc.composeapp.generated.resources.undo
import glicocalc.composeapp.generated.resources.weight
import glicocalc.composeapp.generated.resources.system_default

object Strings {
    @Composable fun navCalculator() = stringResource(Res.string.nav_calculator)
    @Composable fun navDishes() = stringResource(Res.string.nav_dishes)
    @Composable fun navFoods() = stringResource(Res.string.nav_foods)
    @Composable fun totalCarbs() = stringResource(Res.string.total_carbs)
    @Composable fun foodsOnPlate() = stringResource(Res.string.foods_on_plate)
    @Composable fun addAnotherFoodToMeal() = stringResource(Res.string.add_another_food_to_meal)
    @Composable fun weight() = stringResource(Res.string.weight)
    @Composable fun carbs() = stringResource(Res.string.carbs)
    @Composable fun delete() = stringResource(Res.string.delete)
    @Composable fun baseFoodsTitle() = stringResource(Res.string.base_foods_title)
    @Composable fun addFood() = stringResource(Res.string.add_food)
    @Composable fun searchFoodPlaceholder() = stringResource(Res.string.search_food_placeholder)
    @Composable fun foodDeleted() = stringResource(Res.string.food_deleted)
    @Composable fun undo() = stringResource(Res.string.undo)
    @Composable fun edit() = stringResource(Res.string.edit)
    @Composable fun addFoodTitle() = stringResource(Res.string.add_food_title)
    @Composable fun editFoodTitle() = stringResource(Res.string.edit_food_title)
    @Composable fun foodName() = stringResource(Res.string.food_name)
    @Composable fun carbsPer100gLabel() = stringResource(Res.string.carbs_per_100g_label)
    @Composable fun save() = stringResource(Res.string.save)
    @Composable fun cancel() = stringResource(Res.string.cancel)
    @Composable fun dishesTitle() = stringResource(Res.string.dishes_title)
    @Composable fun addDish() = stringResource(Res.string.add_dish)
    @Composable fun newDishTitle() = stringResource(Res.string.new_dish_title)
    @Composable fun editDishTitle() = stringResource(Res.string.edit_dish_title)
    @Composable fun close() = stringResource(Res.string.close)
    @Composable fun dishNameLabel() = stringResource(Res.string.dish_name_label)
    @Composable fun compositionIngredients() = stringResource(Res.string.composition_ingredients)
    @Composable fun ingredient() = stringResource(Res.string.ingredient)
    @Composable fun deleteRow() = stringResource(Res.string.delete_row)
    @Composable fun addIngredient() = stringResource(Res.string.add_ingredient)
    @Composable fun language() = stringResource(Res.string.language)
    @Composable fun systemDefault() = stringResource(Res.string.system_default)
    @Composable fun settings() = stringResource(Res.string.settings)
    @Composable fun mealItemLabel(index: Int) = stringResource(Res.string.meal_item_label, index)
    @Composable fun carbsPer100g(value: String) = stringResource(Res.string.carbs_per_100g, value)
    @Composable fun carbsPercent(value: String) = stringResource(Res.string.carbs_percent, value)
}
