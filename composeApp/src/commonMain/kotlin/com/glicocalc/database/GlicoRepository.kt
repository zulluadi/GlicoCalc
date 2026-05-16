package com.glicocalc.database

import com.glicocalc.models.DishComponent
import com.glicocalc.models.DishWithComposition
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.flow.Flow

class GlicoRepository(val database: GlicoDatabase, private val driver: SqlDriver? = null) {
    private val queries = database.glicoDatabaseQueries
    var onFoodsChanged: (() -> Unit)? = null

    private companion object {
        const val CALCULATOR_MEAL_DRAFT_KEY = "calculator_meal_draft"
        const val CALCULATOR_MEAL_TYPE_ID_KEY = "calculator_meal_type_id"
        const val SYNC_INTERVAL_MINUTES_KEY = "sync_interval_minutes"
        const val FAMILY_NAME_KEY = "family_name"
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 10
        const val MIN_SYNC_INTERVAL_MINUTES = 1
        const val MAX_SYNC_INTERVAL_MINUTES = 60
    }

    fun getAllBaseFoods(): Flow<List<BaseFood>> {
        return queries.selectAllBaseFoods().asFlow().mapToList()
    }

    fun getBaseFood(id: Long): BaseFood? {
        return queries.selectBaseFoodById(id).executeAsOneOrNull()
    }

    fun insertBaseFood(name: String, carbs: Double, isPacked: Boolean = false, packWeight: Double? = null, packCount: Int? = null) {
        val now = PlatformTime.currentTimeMillis()
        queries.insertBaseFood(
            name = name,
            carbsPer100g = carbs,
            remoteKey = generateCustomFoodRemoteKey(),
            source = FoodSource.CUSTOM.value,
            isDeleted = 0,
            needsSync = 1,
            updatedAt = now,
            isPacked = if (isPacked) 1 else 0,
            packWeight = packWeight,
            packCount = packCount?.toLong()
        )
        notifyLocalDataChanged()
    }

    fun updateBaseFood(id: Long, name: String, carbs: Double, isPacked: Boolean = false, packWeight: Double? = null, packCount: Int? = null) {
        val now = PlatformTime.currentTimeMillis()
        queries.updateBaseFood(name, carbs, if (isPacked) 1 else 0, packWeight, packCount?.toLong(), 1, now, id)
        notifyLocalDataChanged()
    }

    fun deleteBaseFood(id: Long) {
        val now = PlatformTime.currentTimeMillis()
        queries.deleteBaseFood(1, now, id)
        notifyLocalDataChanged()
    }

    fun restoreBaseFood(id: Long) {
        val now = PlatformTime.currentTimeMillis()
        queries.restoreBaseFood(1, now, id)
        notifyLocalDataChanged()
    }

    fun permanentlyDeleteBaseFood(id: Long) {
        queries.deleteBaseFoodPermanently(id)
        notifyLocalDataChanged()
    }

    fun getAllBaseFoodsIncludingDeleted(): List<BaseFood> {
        return queries.selectAllBaseFoodsIncludingDeleted().executeAsList()
    }

    fun getAllBaseFoodsIncludingDeletedFlow(): Flow<List<BaseFood>> {
        return queries.selectAllBaseFoodsIncludingDeleted().asFlow().mapToList()
    }

    fun getBaseFoodsNeedingSync(): List<BaseFood> {
        return queries.selectBaseFoodsNeedingSync().executeAsList()
    }

    fun markBaseFoodSynced(id: Long) {
        queries.markBaseFoodSynced(id)
    }

    fun markAllSyncableDataForSync() {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.markAllBaseFoodsForSync(now)
            queries.markAllDishesForSync(now)
            queries.markSyncableSettingsForSync(now)
        }
    }

    // Default foods are identified by stable remote keys. Looking up the seed lets sync
    // distinguish an unchanged bundled food from a user-specific edit or deletion.
    fun seedFoodFor(baseFood: BaseFood): SeedFood? {
        return baseFood.remoteKey?.let(InitialData::defaultFoodByRemoteKey)
    }

    fun isDefaultFoodAtSeedValue(baseFood: BaseFood): Boolean {
        val seed = seedFoodFor(baseFood) ?: return false
        return baseFood.source == FoodSource.DEFAULT.value &&
            baseFood.isDeleted == 0L &&
            baseFood.name == seed.name &&
            baseFood.carbsPer100g == seed.carbs &&
            baseFood.isPacked == 0L
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
                        updatedAt = remoteFood.updatedAt,
                        isPacked = if (remoteFood.isPacked) 1 else 0,
                        packWeight = remoteFood.packWeight,
                        packCount = remoteFood.packCount?.toLong()
                    )
                } else if (local.needsSync == 0L && remoteFood.updatedAt >= local.updatedAt) {
                    queries.applyRemoteBaseFood(
                        remoteFood.name,
                        remoteFood.carbsPer100g,
                        if (remoteFood.isPacked) 1 else 0,
                        remoteFood.packWeight,
                        remoteFood.packCount?.toLong(),
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
                            queries.applyRemoteBaseFood(seed.name, seed.carbs, 0, null, null, 0, 0, localFood.id)
                        }
                    }

                    FoodSource.CUSTOM.value -> {
                        if (localFood.isDeleted == 0L) {
                            queries.applyRemoteBaseFood(
                                localFood.name,
                                localFood.carbsPer100g,
                                localFood.isPacked,
                                localFood.packWeight,
                                localFood.packCount,
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

    fun getAllDishesIncludingDeletedFlow(): Flow<List<Dish>> {
        return queries.selectAllDishesIncludingDeleted().asFlow().mapToList()
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

    data class DishWithCarbs(val dish: Dish, val carbsPer100g: Double)

    fun getAllDishesWithCarbs(): List<DishWithCarbs> {
        return queries.selectAllDishes().executeAsList().map { dish ->
            val components = queries.selectComponentsByDishId(dish.id).executeAsList()
            val totalCookedWeight = dish.totalCookedWeight
            val carbsPer100g = if (totalCookedWeight != null && totalCookedWeight > 0.0 && components.isNotEmpty()) {
                components.sumOf { it.weightGrams * it.foodCarbs } / totalCookedWeight
            } else {
                0.0
            }
            DishWithCarbs(dish, carbsPer100g)
        }
    }

    fun getAllMealTypes(): Flow<List<MealType>> {
        return queries.selectAllMealTypes().asFlow().mapToList()
    }

    fun insertMealType(name: String, targetCarbs: Double, hourOfDay: Long) {
        queries.insertMealType(name, targetCarbs, hourOfDay)
    }

    fun updateMealType(id: Long, name: String, targetCarbs: Double, hourOfDay: Long) {
        queries.updateMealType(name, targetCarbs, hourOfDay, id)
    }

    fun deleteMealType(id: Long) {
        queries.deleteMealType(id)
    }

    fun insertDishWithComponents(name: String, totalCookedWeight: Double?, components: List<Pair<Long, Double>>) {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.insertDish(
                name = name,
                remoteKey = generateCustomDishRemoteKey(),
                isDeleted = 0,
                needsSync = 1,
                updatedAt = now,
                totalCookedWeight = totalCookedWeight
            )
            val dishId = queries.lastInsertRowId().executeAsOne()
            components.forEach { (foodId, weightGrams) ->
                queries.insertDishComponent(dishId, foodId, weightGrams)
            }
        }
        notifyLocalDataChanged()
    }

    fun updateDishWithComponents(dishId: Long, name: String, totalCookedWeight: Double?, components: List<Pair<Long, Double>>) {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.updateDish(name, totalCookedWeight, 1, now, dishId)
            queries.deleteComponentsByDishId(dishId)
            components.forEach { (foodId, weightGrams) ->
                queries.insertDishComponent(dishId, foodId, weightGrams)
            }
        }
        notifyLocalDataChanged()
    }

    fun deleteDish(dishId: Long) {
        val now = PlatformTime.currentTimeMillis()
        database.transaction {
            queries.deleteComponentsByDishId(dishId)
            queries.deleteDish(1, now, dishId)
        }
        notifyLocalDataChanged()
    }

    fun restoreDish(dishId: Long) {
        val now = PlatformTime.currentTimeMillis()
        queries.restoreDish(1, now, dishId)
        notifyLocalDataChanged()
    }

    fun permanentlyDeleteDish(dishId: Long) {
        database.transaction {
            queries.deleteComponentsByDishId(dishId)
            queries.deleteDishPermanently(dishId)
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
                weightGrams = it.weightGrams,
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
            RemoteDishComponentRecord(foodRemoteKey = foodRemoteKey, weightGrams = component.weightGrams)
        }
        val remoteKey = dish.remoteKey ?: return null
        return RemoteDishRecord(
            remoteKey = remoteKey,
            name = dish.name,
            totalCookedWeight = dish.totalCookedWeight,
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
                    queries.insertDish(remoteDish.name, remoteDish.remoteKey, if (remoteDish.isDeleted) 1 else 0, 0, remoteDish.updatedAt, remoteDish.totalCookedWeight)
                    val newDishId = queries.lastInsertRowId().executeAsOne()
                    if (!remoteDish.isDeleted) {
                        insertRemoteDishComponents(newDishId, remoteDish.components, foodsByRemoteKey)
                    }
                } else if (localDish.needsSync == 0L && remoteDish.updatedAt >= localDish.updatedAt) {
                    queries.applyRemoteDish(remoteDish.name, remoteDish.totalCookedWeight, if (remoteDish.isDeleted) 1 else 0, remoteDish.updatedAt, localDish.id)
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
                    queries.applyRemoteDish(localDish.name, localDish.totalCookedWeight, 1, localDish.updatedAt, localDish.id)
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
            queries.insertDishComponent(dishId, food.id, component.weightGrams)
        }
    }

    fun resetFoodListToDefault() {
        val now = PlatformTime.currentTimeMillis()
        val allFoods = queries.selectAllBaseFoodsIncludingDeleted().executeAsList()

        database.transaction {
            // 1. Mark all existing foods as deleted and needing sync
            allFoods.forEach { food ->
                queries.deleteBaseFood(needsSync = 1, updatedAt = now, id = food.id)
            }

            // 2. Restore or Insert the new defaults
            InitialData.seededFoods.forEach { seed ->
                val existing = queries.selectBaseFoodByRemoteKey(seed.remoteKey).executeAsOneOrNull()
                if (existing != null) {
                    // Restore and update existing record
                    queries.applyRemoteBaseFood(
                        name = seed.name,
                        carbsPer100g = seed.carbs,
                        isPacked = 0,
                        packWeight = null,
                        packCount = null,
                        isDeleted = 0,
                        updatedAt = now,
                        id = existing.id
                    )
                    // Mark as needing sync
                    queries.updateBaseFoodSyncMetadata(seed.remoteKey, FoodSource.DEFAULT.value, 1, now, existing.id)
                } else {
                    // Insert new default food
                    queries.insertBaseFood(
                        name = seed.name,
                        carbsPer100g = seed.carbs,
                        remoteKey = seed.remoteKey,
                        source = FoodSource.DEFAULT.value,
                        isDeleted = 0,
                        needsSync = 1,
                        updatedAt = now,
                        isPacked = 0,
                        packWeight = null,
                        packCount = null
                    )
                }
            }
        }
        notifyLocalDataChanged()
    }

    fun migrateSchemaIfNeeded() {
        val d = driver ?: return
        try {
            d.execute(null, "ALTER TABLE BaseFood ADD COLUMN isPacked INTEGER NOT NULL DEFAULT 0", 0)
        } catch (_: Exception) {
            // Column already exists, ignore
        }
        try {
            d.execute(null, "ALTER TABLE BaseFood ADD COLUMN packWeight REAL", 0)
        } catch (_: Exception) {
            // Column already exists, ignore
        }
        try {
            d.execute(null, "ALTER TABLE BaseFood ADD COLUMN packCount INTEGER", 0)
        } catch (_: Exception) {
            // Column already exists, ignore
        }
        try {
            d.execute(null, "ALTER TABLE Dish ADD COLUMN totalCookedWeight REAL", 0)
        } catch (_: Exception) {
            // Column already exists, ignore
        }
        try {
            d.execute(null, "ALTER TABLE DishComponent ADD COLUMN weightGrams REAL NOT NULL DEFAULT 0", 0)
        } catch (_: Exception) {
            // Column already exists, ignore
        }
    }

    fun seedInitialData() {
        val existingFoods = queries.selectAllBaseFoodsIncludingDeleted().executeAsList()
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
                        updatedAt = 0,
                        isPacked = 0,
                        packWeight = null,
                        packCount = null
                    )
                }
            }
        } else {
            val existingDefaultRemoteKeys = existingFoods.mapNotNull { it.remoteKey }.toSet()
            val missingDefaultFoods = InitialData.seededFoods.filterNot { it.remoteKey in existingDefaultRemoteKeys }
            if (missingDefaultFoods.isNotEmpty()) {
                database.transaction {
                    missingDefaultFoods.forEach {
                        queries.insertBaseFood(
                            name = it.name,
                            carbsPer100g = it.carbs,
                            remoteKey = it.remoteKey,
                            source = FoodSource.DEFAULT.value,
                            isDeleted = 0,
                            needsSync = 0,
                            updatedAt = 0,
                            isPacked = 0,
                            packWeight = null,
                            packCount = null
                        )
                    }
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

    fun getCalculatorMealDraft(): String? {
        return queries.selectSettingByKey(CALCULATOR_MEAL_DRAFT_KEY).executeAsOneOrNull()?.content
    }

    fun saveCalculatorMealDraft(content: String?) {
        queries.applyRemoteSetting(CALCULATOR_MEAL_DRAFT_KEY, content, PlatformTime.currentTimeMillis())
    }

    fun getCalculatorMealTypeId(): Long? {
        return queries.selectSettingByKey(CALCULATOR_MEAL_TYPE_ID_KEY).executeAsOneOrNull()?.content?.toLongOrNull()
    }

    fun saveCalculatorMealTypeId(mealTypeId: Long?) {
        queries.applyRemoteSetting(CALCULATOR_MEAL_TYPE_ID_KEY, mealTypeId?.toString(), PlatformTime.currentTimeMillis())
    }

    fun clearCalculatorDraft() {
        saveCalculatorMealDraft(null)
        saveCalculatorMealTypeId(null)
    }

    fun getSyncIntervalMinutes(): Int {
        val savedValue = queries.selectSettingByKey(SYNC_INTERVAL_MINUTES_KEY)
            .executeAsOneOrNull()
            ?.content
            ?.toIntOrNull()
        return (savedValue ?: DEFAULT_SYNC_INTERVAL_MINUTES)
            .coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)
    }

    fun saveSyncIntervalMinutes(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(MIN_SYNC_INTERVAL_MINUTES, MAX_SYNC_INTERVAL_MINUTES)
        queries.applyRemoteSetting(
            key = SYNC_INTERVAL_MINUTES_KEY,
            content = clampedMinutes.toString(),
            updatedAt = PlatformTime.currentTimeMillis()
        )
    }

    fun getFamilyId(): String? {
        return queries.selectSettingByKey("family_id").executeAsOneOrNull()?.content
    }

    fun setFamilyId(id: String?) {
        queries.applyRemoteSetting("family_id", id, PlatformTime.currentTimeMillis())
    }

    fun getFamilyName(): String? {
        return queries.selectSettingByKey(FAMILY_NAME_KEY)
            .executeAsOneOrNull()
            ?.content
            ?.takeIf { it.isNotBlank() }
    }

    fun setFamilyName(name: String?) {
        queries.applyRemoteSetting(FAMILY_NAME_KEY, name?.takeIf { it.isNotBlank() }, PlatformTime.currentTimeMillis())
    }

    fun getAllFamilyMembers(): List<FamilyMember> {
        return queries.selectAllFamilyMembers().executeAsList()
    }

    fun addFamilyMember(email: String, name: String, firebaseUid: String? = null, isOwner: Boolean = false) {
        val normalizedEmail = normalizeFamilyEmail(email) ?: return
        queries.insertFamilyMember(
            email = normalizedEmail,
            name = name,
            firebaseUid = firebaseUid,
            isOwner = if (isOwner) 1 else 0,
            addedAt = PlatformTime.currentTimeMillis()
        )
    }

    fun removeFamilyMember(email: String) {
        normalizeFamilyEmail(email)?.let(queries::deleteFamilyMemberByEmail)
    }

    fun removeFamilyMemberByFirebaseUid(firebaseUid: String) {
        queries.deleteFamilyMemberByFirebaseUid(firebaseUid)
    }

    fun getFamilyMemberByEmail(email: String): FamilyMember? {
        val normalizedEmail = normalizeFamilyEmail(email) ?: return null
        return queries.selectFamilyMemberByEmail(normalizedEmail).executeAsOneOrNull()
    }

    fun getFamilyMemberByFirebaseUid(uid: String): FamilyMember? {
        return queries.selectFamilyMemberByFirebaseUid(uid).executeAsOneOrNull()
    }

    fun updateFamilyMemberFirebaseUid(email: String, firebaseUid: String?) {
        normalizeFamilyEmail(email)?.let { queries.updateFamilyMemberFirebaseUid(firebaseUid, it) }
    }

    fun updateFamilyMemberName(email: String, name: String) {
        normalizeFamilyEmail(email)?.let { queries.updateFamilyMemberName(name, it) }
    }

    fun getFamilyOwnerUid(): String? {
        return queries.getFamilyOwnerUid().executeAsOneOrNull()
    }

    fun setFamilyOwner(email: String) {
        queries.clearOwner()
        normalizeFamilyEmail(email)?.let(queries::setOwner)
    }

    fun countFamilyMembers(): Long {
        return queries.countFamilyMembers().executeAsOne()
    }

    fun clearAllFamilyMembers() {
        queries.deleteAllFamilyMembers()
    }

    fun getOrCreateCurrentFamilyMember(firebaseUid: String?, email: String?, displayName: String?): FamilyMember {
        val member = firebaseUid?.let { getFamilyMemberByFirebaseUid(it) }
        if (member != null) {
            if (getFamilyOwnerUid() == null) {
                setFamilyOwner(member.email)
            }
            return member
        }
        val normalizedEmail = normalizeFamilyEmail(email)
        val emailMember = normalizedEmail?.let { getFamilyMemberByEmail(it) }
        if (emailMember != null) {
            if (firebaseUid != null) {
                updateFamilyMemberFirebaseUid(emailMember.email, firebaseUid)
            }
            if (getFamilyOwnerUid() == null) {
                setFamilyOwner(emailMember.email)
            }
            return emailMember
        }
        val defaultName = displayName ?: normalizedEmail ?: firebaseUid ?: "Unknown"
        val memberEmail = normalizedEmail ?: normalizeFamilyEmail("${defaultName.trim()}@family.local") ?: "unknown@family.local"
        val isOwner = getFamilyOwnerUid() == null
        addFamilyMember(
            email = memberEmail,
            name = defaultName,
            firebaseUid = firebaseUid,
            isOwner = isOwner
        )
        return queries.selectFamilyMemberByEmail(memberEmail).executeAsOne()
    }

    private fun normalizeFamilyEmail(email: String?): String? {
        return email
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
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
    val updatedAt: Long,
    val isPacked: Boolean = false,
    val packWeight: Double? = null,
    val packCount: Int? = null
)

data class RemoteSettingRecord(
    val key: String,
    val content: String?,
    val updatedAt: Long
)

data class RemoteDishRecord(
    val remoteKey: String,
    val name: String,
    val totalCookedWeight: Double?,
    val isDeleted: Boolean,
    val updatedAt: Long,
    val components: List<RemoteDishComponentRecord>
)

data class RemoteDishComponentRecord(
    val foodRemoteKey: String,
    val weightGrams: Double
)

enum class FoodSource(val value: String) {
    DEFAULT("default"),
    CUSTOM("custom");

    companion object {
        fun fromValue(value: String?): FoodSource = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
