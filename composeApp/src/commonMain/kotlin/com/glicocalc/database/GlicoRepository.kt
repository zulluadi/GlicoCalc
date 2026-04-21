package com.glicocalc.database

import com.glicocalc.models.DishComponent
import com.glicocalc.models.DishWithComposition
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.Flow

class GlicoRepository(val database: GlicoDatabase) {
    private val queries = database.glicoDatabaseQueries
    var onFoodsChanged: (() -> Unit)? = null

    fun getAllBaseFoods(): Flow<List<BaseFood>> {
        return queries.selectAllBaseFoods().asFlow().mapToList()
    }

    fun getBaseFood(id: Long): BaseFood? {
        return queries.selectBaseFoodById(id).executeAsOneOrNull()
    }

    suspend fun insertBaseFood(name: String, carbs: Double) {
        val now = PlatformTime.currentTimeMillis()
        queries.insertBaseFood(
            name = name,
            carbsPer100g = carbs,
            remoteKey = generateCustomFoodRemoteKey(),
            source = FoodSource.CUSTOM.value,
            isDeleted = 0,
            needsSync = 1,
            updatedAt = now
        )
        notifyLocalDataChanged()
    }

    suspend fun updateBaseFood(id: Long, name: String, carbs: Double) {
        val now = PlatformTime.currentTimeMillis()
        queries.updateBaseFood(name, carbs, 1, now, id)
        notifyLocalDataChanged()
    }

    suspend fun deleteBaseFood(id: Long) {
        val now = PlatformTime.currentTimeMillis()
        queries.deleteBaseFood(1, now, id)
        notifyLocalDataChanged()
    }

    suspend fun restoreBaseFood(id: Long) {
        val now = PlatformTime.currentTimeMillis()
        queries.restoreBaseFood(1, now, id)
        notifyLocalDataChanged()
    }

    fun getAllBaseFoodsIncludingDeleted(): List<BaseFood> {
        return queries.selectAllBaseFoodsIncludingDeleted().executeAsList()
    }

    fun getBaseFoodsNeedingSync(): List<BaseFood> {
        return queries.selectBaseFoodsNeedingSync().executeAsList()
    }

    fun markBaseFoodSynced(id: Long) {
        queries.markBaseFoodSynced(id)
    }

    fun seedFoodFor(baseFood: BaseFood): SeedFood? {
        return baseFood.remoteKey?.let(InitialData::defaultFoodByRemoteKey)
    }

    fun isDefaultFoodAtSeedValue(baseFood: BaseFood): Boolean {
        val seed = seedFoodFor(baseFood) ?: return false
        return baseFood.source == FoodSource.DEFAULT.value &&
            baseFood.isDeleted == 0L &&
            baseFood.name == seed.name &&
            baseFood.carbsPer100g == seed.carbs
    }

    fun prepareBaseFoodCatalog() {
        val foods = getAllBaseFoodsIncludingDeleted()
        val defaultCount = InitialData.seededFoods.size
        val now = PlatformTime.currentTimeMillis()

        database.transaction {
            foods.forEach { food ->
                if (food.remoteKey != null) return@forEach

                if (food.id in 1L..defaultCount.toLong()) {
                    val seed = InitialData.defaultFoodByIndex(food.id.toInt() - 1) ?: return@forEach
                    val needsSync = if (food.name != seed.name || food.carbsPer100g != seed.carbs) 1L else 0L
                    queries.updateBaseFoodSyncMetadata(seed.remoteKey, FoodSource.DEFAULT.value, needsSync, now, food.id)
                } else {
                    queries.updateBaseFoodSyncMetadata(
                        generateCustomFoodRemoteKey(),
                        FoodSource.CUSTOM.value,
                        1,
                        now,
                        food.id
                    )
                }
            }
        }
    }

    fun reconcileRemoteFoods(remoteFoods: List<RemoteFoodRecord>) {
        val remoteByKey = remoteFoods.associateBy { it.remoteKey }
        val localFoods = getAllBaseFoodsIncludingDeleted()
        val localByKey = localFoods.mapNotNull { food -> food.remoteKey?.let { it to food } }.toMap()

        database.transaction {
            remoteFoods.forEach { remoteFood ->
                val local = localByKey[remoteFood.remoteKey]
                if (local == null) {
                    queries.insertBaseFood(
                        name = remoteFood.name,
                        carbsPer100g = remoteFood.carbsPer100g,
                        remoteKey = remoteFood.remoteKey,
                        source = remoteFood.source.value,
                        isDeleted = if (remoteFood.isDeleted) 1 else 0,
                        needsSync = 0,
                        updatedAt = remoteFood.updatedAt
                    )
                } else if (local.needsSync == 0L && remoteFood.updatedAt >= local.updatedAt) {
                    queries.applyRemoteBaseFood(
                        remoteFood.name,
                        remoteFood.carbsPer100g,
                        if (remoteFood.isDeleted) 1 else 0,
                        remoteFood.updatedAt,
                        local.id
                    )
                }
            }

            localFoods.forEach { localFood ->
                val remoteKey = localFood.remoteKey ?: return@forEach
                if (localFood.needsSync != 0L) return@forEach
                if (remoteByKey.containsKey(remoteKey)) return@forEach

                when (localFood.source) {
                    FoodSource.DEFAULT.value -> {
                        val seed = InitialData.defaultFoodByRemoteKey(remoteKey) ?: return@forEach
                        if (localFood.name != seed.name || localFood.carbsPer100g != seed.carbs || localFood.isDeleted != 0L) {
                            queries.applyRemoteBaseFood(seed.name, seed.carbs, 0, 0, localFood.id)
                        }
                    }

                    FoodSource.CUSTOM.value -> {
                        if (localFood.isDeleted == 0L) {
                            queries.applyRemoteBaseFood(
                                localFood.name,
                                localFood.carbsPer100g,
                                1,
                                localFood.updatedAt,
                                localFood.id
                            )
                        }
                    }
                }
            }
        }
    }

    fun getAllDishes(): Flow<List<Dish>> {
        return queries.selectAllDishes().asFlow().mapToList()
    }

    fun getAllDishesIncludingDeleted(): List<Dish> {
        return queries.selectAllDishesIncludingDeleted().executeAsList()
    }

    fun getDishesNeedingSync(): List<Dish> {
        return queries.selectDishesNeedingSync().executeAsList()
    }

    fun markDishSynced(id: Long) {
        queries.markDishSynced(id)
    }

    fun pendingSyncCount(): Int {
        return getBaseFoodsNeedingSync().size + getDishesNeedingSync().size + getSettingsNeedingSync().size
    }

    fun getSettingsNeedingSync(): List<Setting> {
        return queries.selectSettingsNeedingSync().executeAsList()
    }

    fun markSettingSynced(key: String) {
        queries.markSettingSynced(key)
    }

    fun reconcileRemoteSettings(remoteSettings: List<RemoteSettingRecord>) {
        val remoteByKey = remoteSettings.associateBy { it.key }
        listOf("language", "food_language").forEach { key ->
            val localSetting = queries.selectSettingByKey(key).executeAsOneOrNull()
            val remoteSetting = remoteByKey[key] ?: return@forEach
            if (localSetting == null || localSetting.needsSync == 0L && remoteSetting.updatedAt >= localSetting.updatedAt) {
                queries.applyRemoteSetting(remoteSetting.key, remoteSetting.content, remoteSetting.updatedAt)
            }
        }
    }

    fun prepareDishCatalog() {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            getAllDishesIncludingDeleted().forEach { dish ->
                if (dish.remoteKey != null) return@forEach
                queries.updateDishSyncMetadata(generateCustomDishRemoteKey(), 1, now, dish.id)
            }
        }
    }

    data class DishWithCarbs(val dish: Dish, val carbsPer100g: Double)

    fun getAllDishesWithCarbs(): List<DishWithCarbs> {
        return queries.selectAllDishes().executeAsList().map { dish ->
            val components = queries.selectComponentsByDishId(dish.id).executeAsList()
            val carbsPer100g = components.sumOf { (it.percentage / 100.0) * it.foodCarbs }
            DishWithCarbs(dish, carbsPer100g)
        }
    }

    fun getAllMealTypes(): Flow<List<MealType>> {
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
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.insertDish(
                name = name,
                remoteKey = generateCustomDishRemoteKey(),
                isDeleted = 0,
                needsSync = 1,
                updatedAt = now
            )
            val dishId = queries.lastInsertRowId().executeAsOne()
            components.forEach { (foodId, percentage) ->
                queries.insertDishComponent(dishId, foodId, percentage)
            }
        }
        notifyLocalDataChanged()
    }

    suspend fun updateDishWithComponents(dishId: Long, name: String, components: List<Pair<Long, Double>>) {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.updateDish(name, 1, now, dishId)
            queries.deleteComponentsByDishId(dishId)
            components.forEach { (foodId, percentage) ->
                queries.insertDishComponent(dishId, foodId, percentage)
            }
        }
        notifyLocalDataChanged()
    }

    suspend fun deleteDish(dishId: Long) {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.deleteComponentsByDishId(dishId)
            queries.deleteDish(1, now, dishId)
        }
        notifyLocalDataChanged()
    }

    fun getDishWithComposition(dishId: Long): DishWithComposition? {
        val dish = queries.selectDishById(dishId).executeAsOneOrNull() ?: return null
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
        return DishWithComposition(dish = dish, components = components)
    }

    fun getRemoteDishRecord(dishId: Long): RemoteDishRecord? {
        val dish = queries.selectDishById(dishId).executeAsOneOrNull() ?: return null
        val components = queries.selectComponentsByDishId(dishId).executeAsList().mapNotNull { component ->
            val food = queries.selectBaseFoodById(component.baseFoodId).executeAsOneOrNull() ?: return@mapNotNull null
            val foodRemoteKey = food.remoteKey ?: return@mapNotNull null
            RemoteDishComponentRecord(foodRemoteKey = foodRemoteKey, percentage = component.percentage)
        }
        val remoteKey = dish.remoteKey ?: return null
        return RemoteDishRecord(
            remoteKey = remoteKey,
            name = dish.name,
            isDeleted = dish.isDeleted != 0L,
            updatedAt = dish.updatedAt,
            components = components
        )
    }

    fun reconcileRemoteDishes(remoteDishes: List<RemoteDishRecord>) {
        val remoteByKey = remoteDishes.associateBy { it.remoteKey }
        val localDishes = getAllDishesIncludingDeleted()
        val localByKey = localDishes.mapNotNull { dish -> dish.remoteKey?.let { it to dish } }.toMap()
        val foodsByRemoteKey = getAllBaseFoodsIncludingDeleted().mapNotNull { food -> food.remoteKey?.let { it to food } }.toMap()

        database.transaction {
            remoteDishes.forEach { remoteDish ->
                val localDish = localByKey[remoteDish.remoteKey]
                if (localDish == null) {
                    queries.insertDish(remoteDish.name, remoteDish.remoteKey, if (remoteDish.isDeleted) 1 else 0, 0, remoteDish.updatedAt)
                    val newDishId = queries.lastInsertRowId().executeAsOne()
                    if (!remoteDish.isDeleted) {
                        insertRemoteDishComponents(newDishId, remoteDish.components, foodsByRemoteKey)
                    }
                } else if (localDish.needsSync == 0L && remoteDish.updatedAt >= localDish.updatedAt) {
                    queries.applyRemoteDish(remoteDish.name, if (remoteDish.isDeleted) 1 else 0, remoteDish.updatedAt, localDish.id)
                    queries.deleteComponentsByDishId(localDish.id)
                    if (!remoteDish.isDeleted) {
                        insertRemoteDishComponents(localDish.id, remoteDish.components, foodsByRemoteKey)
                    }
                }
            }

            localDishes.forEach { localDish ->
                val remoteKey = localDish.remoteKey ?: return@forEach
                if (localDish.needsSync != 0L) return@forEach
                if (remoteByKey.containsKey(remoteKey)) return@forEach
                if (localDish.isDeleted == 0L) {
                    queries.applyRemoteDish(localDish.name, 1, localDish.updatedAt, localDish.id)
                    queries.deleteComponentsByDishId(localDish.id)
                }
            }
        }
    }

    private fun insertRemoteDishComponents(
        dishId: Long,
        components: List<RemoteDishComponentRecord>,
        foodsByRemoteKey: Map<String, BaseFood>
    ) {
        components.forEach { component ->
            val food = foodsByRemoteKey[component.foodRemoteKey] ?: return@forEach
            if (food.isDeleted != 0L) return@forEach
            queries.insertDishComponent(dishId, food.id, component.percentage)
        }
    }

    fun seedInitialData() {
        val existingFoods = queries.selectAllBaseFoods().executeAsList()
        val existingMealTypes = queries.selectAllMealTypes().executeAsList()

        if (existingFoods.isEmpty()) {
            database.transaction {
                InitialData.seededFoods.forEach {
                    queries.insertBaseFood(
                        name = it.name,
                        carbsPer100g = it.carbs,
                        remoteKey = it.remoteKey,
                        source = FoodSource.DEFAULT.value,
                        isDeleted = 0,
                        needsSync = 0,
                        updatedAt = 0
                    )
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
        queries.setLanguage(languageCode, 1, PlatformTime.currentTimeMillis())
        notifyLocalDataChanged()
    }

    fun getFoodLanguage(): String? {
        return queries.getFoodLanguage().executeAsOneOrNull()?.content
    }

    fun saveFoodLanguage(languageCode: String?) {
        queries.setFoodLanguage(languageCode, 1, PlatformTime.currentTimeMillis())
        notifyLocalDataChanged()
    }

    private fun notifyLocalDataChanged() {
        onFoodsChanged?.invoke()
    }
}

data class RemoteFoodRecord(
    val remoteKey: String,
    val source: FoodSource,
    val name: String,
    val carbsPer100g: Double,
    val isDeleted: Boolean,
    val updatedAt: Long
)

data class RemoteSettingRecord(
    val key: String,
    val content: String?,
    val updatedAt: Long
)

data class RemoteDishRecord(
    val remoteKey: String,
    val name: String,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val components: List<RemoteDishComponentRecord>
)

data class RemoteDishComponentRecord(
    val foodRemoteKey: String,
    val percentage: Double
)

enum class FoodSource(val value: String) {
    DEFAULT("default"),
    CUSTOM("custom");

    companion object {
        fun fromValue(value: String?): FoodSource = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
