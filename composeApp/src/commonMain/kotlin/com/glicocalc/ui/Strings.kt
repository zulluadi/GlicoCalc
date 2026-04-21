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
import glicocalc.composeapp.generated.resources.edit_meal_type_title
import glicocalc.composeapp.generated.resources.food_deleted
import glicocalc.composeapp.generated.resources.food_language
import glicocalc.composeapp.generated.resources.food_name
import glicocalc.composeapp.generated.resources.foods_on_plate
import glicocalc.composeapp.generated.resources.ingredient
import glicocalc.composeapp.generated.resources.language
import glicocalc.composeapp.generated.resources.meal_hour
import glicocalc.composeapp.generated.resources.meal_hour_hint
import glicocalc.composeapp.generated.resources.meal_item_label
import glicocalc.composeapp.generated.resources.meal_target_carbs
import glicocalc.composeapp.generated.resources.meal_type
import glicocalc.composeapp.generated.resources.meal_type_add_remove_delta
import glicocalc.composeapp.generated.resources.meal_type_add_to_match
import glicocalc.composeapp.generated.resources.meal_type_on_target
import glicocalc.composeapp.generated.resources.meal_type_name
import glicocalc.composeapp.generated.resources.meal_type_remove_to_match
import glicocalc.composeapp.generated.resources.meal_type_selector
import glicocalc.composeapp.generated.resources.meal_types
import glicocalc.composeapp.generated.resources.meal_types_care_plan_note
import glicocalc.composeapp.generated.resources.meal_types_description
import glicocalc.composeapp.generated.resources.no_meal_types_configured
import glicocalc.composeapp.generated.resources.nav_calculator
import glicocalc.composeapp.generated.resources.nav_dishes
import glicocalc.composeapp.generated.resources.nav_foods
import glicocalc.composeapp.generated.resources.new_dish_title
import glicocalc.composeapp.generated.resources.save
import glicocalc.composeapp.generated.resources.search_food_placeholder
import glicocalc.composeapp.generated.resources.sign_in_with_google
import glicocalc.composeapp.generated.resources.sign_out
import glicocalc.composeapp.generated.resources.settings
import glicocalc.composeapp.generated.resources.same_as_app_language
import glicocalc.composeapp.generated.resources.add_meal_type
import glicocalc.composeapp.generated.resources.add_meal_type_title
import glicocalc.composeapp.generated.resources.sync_account
import glicocalc.composeapp.generated.resources.sync_account_description
import glicocalc.composeapp.generated.resources.sync_account_missing_google_config
import glicocalc.composeapp.generated.resources.sync_account_unavailable
import glicocalc.composeapp.generated.resources.sync_last_synced
import glicocalc.composeapp.generated.resources.sync_now
import glicocalc.composeapp.generated.resources.sync_signed_in_as
import glicocalc.composeapp.generated.resources.sync_signed_in_google
import glicocalc.composeapp.generated.resources.sync_status
import glicocalc.composeapp.generated.resources.sync_status_failed
import glicocalc.composeapp.generated.resources.sync_status_not_signed_in
import glicocalc.composeapp.generated.resources.sync_status_pending
import glicocalc.composeapp.generated.resources.sync_status_syncing
import glicocalc.composeapp.generated.resources.sync_status_up_to_date
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
    @Composable fun addMealType() = stringResource(Res.string.add_meal_type)
    @Composable fun addMealTypeTitle() = stringResource(Res.string.add_meal_type_title)
    @Composable fun editMealTypeTitle() = stringResource(Res.string.edit_meal_type_title)
    @Composable fun foodName() = stringResource(Res.string.food_name)
    @Composable fun foodLanguage() = stringResource(Res.string.food_language)
    @Composable fun mealTypeName() = stringResource(Res.string.meal_type_name)
    @Composable fun mealTargetCarbs() = stringResource(Res.string.meal_target_carbs)
    @Composable fun mealHour() = stringResource(Res.string.meal_hour)
    @Composable fun mealHourHint() = stringResource(Res.string.meal_hour_hint)
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
    @Composable fun sameAsAppLanguage() = stringResource(Res.string.same_as_app_language)
    @Composable fun systemDefault() = stringResource(Res.string.system_default)
    @Composable fun settings() = stringResource(Res.string.settings)
    @Composable fun syncAccount() = stringResource(Res.string.sync_account)
    @Composable fun syncAccountDescription() = stringResource(Res.string.sync_account_description)
    @Composable fun syncAccountUnavailable() = stringResource(Res.string.sync_account_unavailable)
    @Composable fun syncAccountMissingGoogleConfig() = stringResource(Res.string.sync_account_missing_google_config)
    @Composable fun syncSignedInAs(value: String) = stringResource(Res.string.sync_signed_in_as, value)
    @Composable fun syncStatus() = stringResource(Res.string.sync_status)
    @Composable fun syncStatusNotSignedIn() = stringResource(Res.string.sync_status_not_signed_in)
    @Composable fun syncStatusSyncing() = stringResource(Res.string.sync_status_syncing)
    @Composable fun syncStatusUpToDate() = stringResource(Res.string.sync_status_up_to_date)
    @Composable fun syncStatusPending(value: Int) = stringResource(Res.string.sync_status_pending, value)
    @Composable fun syncStatusFailed() = stringResource(Res.string.sync_status_failed)
    @Composable fun syncLastSynced(value: String) = stringResource(Res.string.sync_last_synced, value)
    @Composable fun syncNow() = stringResource(Res.string.sync_now)
    @Composable fun syncSignedInGoogle() = stringResource(Res.string.sync_signed_in_google)
    @Composable fun signInWithGoogle() = stringResource(Res.string.sign_in_with_google)
    @Composable fun signOut() = stringResource(Res.string.sign_out)
    @Composable fun mealTypes() = stringResource(Res.string.meal_types)
    @Composable fun mealTypesDescription() = stringResource(Res.string.meal_types_description)
    @Composable fun mealTypesCarePlanNote() = stringResource(Res.string.meal_types_care_plan_note)
    @Composable fun noMealTypesConfigured() = stringResource(Res.string.no_meal_types_configured)
    @Composable fun mealType() = stringResource(Res.string.meal_type)
    @Composable fun mealTypeSelector() = stringResource(Res.string.meal_type_selector)
    @Composable fun mealItemLabel(index: Int) = stringResource(Res.string.meal_item_label, index)
    @Composable fun carbsPer100g(value: String) = stringResource(Res.string.carbs_per_100g, value)
    @Composable fun carbsPercent(value: String) = stringResource(Res.string.carbs_percent, value).replace("%%", "%")
    @Composable fun mealTypeAddToMatch(value: String) = stringResource(Res.string.meal_type_add_to_match, value)
    @Composable fun mealTypeRemoveToMatch(value: String) = stringResource(Res.string.meal_type_remove_to_match, value)
    @Composable fun mealTypeAddRemoveDelta(value: String) = stringResource(Res.string.meal_type_add_remove_delta, value)
    @Composable fun mealTypeOnTarget() = stringResource(Res.string.meal_type_on_target)
}
