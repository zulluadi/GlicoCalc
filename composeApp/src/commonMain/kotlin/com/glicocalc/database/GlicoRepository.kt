package com.glicocalc.database

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.glicocalc.database.GlicoDatabase
import kotlinx.coroutines.flow.Flow
import com.glicocalc.models.DishComponent
import com.glicocalc.models.DishWithComposition

class GlicoRepository(val database: GlicoDatabase) {
    private val queries = database.glicoDatabaseQueries

    fun getAllBaseFoods(): Flow<List<com.glicocalc.database.BaseFood>> {
        return queries.selectAllBaseFoods().asFlow().mapToList()
    }

    fun getBaseFood(id: Long): com.glicocalc.database.BaseFood? {
        return queries.selectAllBaseFoods().executeAsList().find { it.id == id }
    }

    suspend fun insertBaseFood(name: String, carbs: Double) {
        queries.insertBaseFood(name, carbs)
    }

    suspend fun updateBaseFood(id: Long, name: String, carbs: Double) {
        queries.updateBaseFood(name, carbs, id)
    }

    suspend fun deleteBaseFood(id: Long) {
        queries.deleteBaseFood(id)
    }

    fun getAllDishes(): Flow<List<com.glicocalc.database.Dish>> {
        return queries.selectAllDishes().asFlow().mapToList()
    }

    data class DishWithCarbs(val dish: com.glicocalc.database.Dish, val carbsPer100g: Double)

    fun getAllDishesWithCarbs(): List<DishWithCarbs> {
        return queries.selectAllDishes().executeAsList().map { dish ->
            val components = queries.selectComponentsByDishId(dish.id).executeAsList()
            val carbsPer100g = components.sumOf { (it.percentage / 100.0) * it.foodCarbs }
            DishWithCarbs(dish, carbsPer100g)
        }
    }

    fun getAllMealTypes(): Flow<List<com.glicocalc.database.MealType>> {
        return queries.selectAllMealTypes().asFlow().mapToList()
    }

    suspend fun insertMealType(name: String, targetCarbs: Double, hourOfDay: Long) {
        queries.insertMealType(name, targetCarbs, hourOfDay)
    }

    suspend fun updateMealType(id: Long, name: String, targetCarbs: Double, hourOfDay: Long) {
        queries.updateMealType(name, targetCarbs, hourOfDay, id)
    }

    suspend fun deleteMealType(id: Long) {
        queries.deleteMealType(id)
    }

    suspend fun insertDishWithComponents(name: String, components: List<Pair<Long, Double>>) {
        database.transaction {
            queries.insertDish(name)
            val dishId = queries.lastInsertRowId().executeAsOne()
            components.forEach { (foodId, percentage) ->
                queries.insertDishComponent(dishId, foodId, percentage)
            }
        }
    }

    suspend fun updateDishWithComponents(dishId: Long, name: String, components: List<Pair<Long, Double>>) {
        database.transaction {
            queries.updateDish(name, dishId)
            queries.deleteComponentsByDishId(dishId)
            components.forEach { (foodId, percentage) ->
                queries.insertDishComponent(dishId, foodId, percentage)
            }
        }
    }

    suspend fun deleteDish(dishId: Long) {
        database.transaction {
            queries.deleteComponentsByDishId(dishId)
            queries.deleteDish(dishId)
        }
    }

    fun getDishWithComposition(dishId: Long): DishWithComposition? {
        val dish = queries.selectAllDishes().executeAsList().find { it.id == dishId } ?: return null
        val components = queries.selectComponentsByDishId(dishId).executeAsList().map {
            DishComponent(
                id = it.id,
                dishId = it.dishId,
                baseFoodId = it.baseFoodId,
                percentage = it.percentage,
                foodName = it.foodName,
                foodCarbs = it.foodCarbs
            )
        }
        return DishWithComposition(
            dish = dish,
            components = components
        )
    }

    fun seedInitialData() {
        val existingFoods = queries.selectAllBaseFoods().executeAsList()
        val existingMealTypes = queries.selectAllMealTypes().executeAsList()

        if (existingFoods.isEmpty()) {
            database.transaction {
                InitialData.foods.forEach {
                    queries.insertBaseFood(it.name, it.carbs)
                }
            }
        }

        if (existingMealTypes.isEmpty()) {
            database.transaction {
                InitialData.mealTypes.forEach {
                    queries.insertMealType(it.name, it.targetCarbs, it.hourOfDay)
                }
            }
        }
    }

    fun getLanguage(): String? {
        return queries.getLanguage().executeAsOneOrNull()?.content
    }

    fun saveLanguage(languageCode: String?) {
        queries.setLanguage(languageCode)
    }
}
