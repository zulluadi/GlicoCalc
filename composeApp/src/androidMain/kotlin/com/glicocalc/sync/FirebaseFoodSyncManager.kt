package com.glicocalc.sync

import android.content.Context
import android.util.Log
import com.glicocalc.database.BaseFood
import com.glicocalc.database.FoodSource
import com.glicocalc.database.GlicoRepository
import com.glicocalc.database.RemoteDishComponentRecord
import com.glicocalc.database.RemoteDishRecord
import com.glicocalc.database.RemoteFoodRecord
import com.glicocalc.database.RemoteSettingRecord
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.FirebaseException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class FirebaseFoodSyncManager(
    context: Context,
    private val repository: GlicoRepository,
    private val scope: CoroutineScope
) {
    private companion object {
        const val TAG = "FirebaseFoodSync"
    }

    private val firebaseApp = FirebaseApp.initializeApp(context)
    private val auth = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val firestore = firebaseApp?.let { FirebaseFirestore.getInstance(it) }
    private val syncMutex = Mutex()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    var onAccountStateChanged: ((String?) -> Unit)? = null
    var onSyncStateChanged: ((SyncUiState) -> Unit)? = null
    @Volatile
    private var syncAvailable = true
    @Volatile
    private var lastSuccessfulSyncAtMillis: Long? = null

    val isEnabled: Boolean
        get() = firebaseApp != null && auth != null && firestore != null && syncAvailable

    fun start() {
        authStateListener = FirebaseAuth.AuthStateListener {
            onAccountStateChanged?.invoke(currentSyncAccountLabel())
            onSyncStateChanged?.invoke(currentSyncUiState())
            requestSync()
        }
        auth?.let { authStateListener?.let(it::addAuthStateListener) }
        onAccountStateChanged?.invoke(currentSyncAccountLabel())
        onSyncStateChanged?.invoke(currentSyncUiState())
        requestSync()
    }

    fun stop() {
        authStateListener?.let { listener ->
            auth?.removeAuthStateListener(listener)
        }
        authStateListener = null
    }

    fun currentSyncAccountLabel(): String? {
        val user = auth?.currentUser ?: return null
        if (user.isAnonymous) return null
        return user.email
            ?: user.displayName
            ?: user.phoneNumber
            ?: "Google account linked"
    }

    suspend fun linkOrSignIn(credential: AuthCredential) {
        val currentUser = auth?.currentUser
        if (currentUser != null && currentUser.isAnonymous) {
            try {
                currentUser.linkWithCredential(credential).await()
            } catch (_: FirebaseAuthUserCollisionException) {
                auth?.signInWithCredential(credential)?.await()
            }
        } else {
            auth?.signInWithCredential(credential)?.await()
        }
        autoAddCurrentUserToFamily()
        syncMutex.withLock {
            runSync()
        }
    }

    private fun autoAddCurrentUserToFamily() {
        val user = auth?.currentUser ?: return
        if (user.isAnonymous) return
        repository.getOrCreateCurrentFamilyMember(
            firebaseUid = user.uid,
            email = user.email,
            displayName = user.displayName
        )
    }

    suspend fun signOut() {
        auth?.signOut()
        onAccountStateChanged?.invoke(currentSyncAccountLabel())
        onSyncStateChanged?.invoke(currentSyncUiState())
    }

    fun currentUser(): FirebaseUser? = auth?.currentUser

    fun requestSync() {
        if (!isEnabled) return
        scope.launch {
            onSyncStateChanged?.invoke(currentSyncUiState(status = SyncStatus.SYNCING))
            try {
                syncMutex.withLock {
                    runSync()
                }
                lastSuccessfulSyncAtMillis = System.currentTimeMillis()
                onSyncStateChanged?.invoke(currentSyncUiState(status = SyncStatus.UP_TO_DATE))
            } catch (exception: FirebaseException) {
                if (isConfigurationFailure(exception)) {
                    syncAvailable = false
                    Log.e(TAG, "Disabling Firebase food sync due to Firebase configuration failure.", exception)
                    onSyncStateChanged?.invoke(currentSyncUiState(status = SyncStatus.UNAVAILABLE))
                } else {
                    Log.w(TAG, "Firebase food sync failed; will retry later.", exception)
                    onSyncStateChanged?.invoke(currentSyncUiState(status = SyncStatus.ERROR))
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Firebase food sync failed; will retry later.", exception)
                onSyncStateChanged?.invoke(currentSyncUiState(status = SyncStatus.ERROR))
            }
        }
    }

    fun currentSyncUiState(status: SyncStatus = SyncStatus.IDLE): SyncUiState {
        val user = auth?.currentUser
        return SyncUiState(
            status = status,
            pendingCount = repository.pendingSyncCount(),
            isSignedIn = user != null && !user.isAnonymous,
            lastSuccessfulSyncAtMillis = lastSuccessfulSyncAtMillis
        )
    }

    fun getCurrentFamilyId(): String? {
        return repository.getFamilyId()
    }

    suspend fun removeMemberFromFamily(memberFirebaseUid: String) {
        val familyId = repository.getFamilyId() ?: return
        try {
            firestore!!.collection("families").document(familyId)
                .update("members.$memberFirebaseUid", FieldValue.delete())
                .await()
            Log.i(TAG, "Removed member $memberFirebaseUid from family $familyId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove member $memberFirebaseUid from family $familyId", e)
        }
    }

    private fun isConfigurationFailure(exception: FirebaseException): Boolean {
        val message = exception.message.orEmpty()
        return "CONFIGURATION_NOT_FOUND" in message || "API key not valid" in message
    }

    private fun getCurrentUser(): FirebaseUser? {
        val user = auth?.currentUser ?: return null
        if (user.isAnonymous) return null
        return user
    }

    private suspend fun getOrCreateFamilyId(user: FirebaseUser): String? {
        val localFamilyId = repository.getFamilyId()
        if (localFamilyId != null) return localFamilyId

        return try {
            val userDoc = firestore!!.collection("users").document(user.uid)
            val snapshot = userDoc.get().await()
            val remoteFamilyId = snapshot.getString("familyId")
            if (remoteFamilyId != null) {
                repository.setFamilyId(remoteFamilyId)
                ensureFamilyMemberDoc(user, remoteFamilyId)
                return remoteFamilyId
            }
            userDoc.set(mapOf("familyId" to user.uid)).await()
            repository.setFamilyId(user.uid)
            ensureFamilyMemberDoc(user, user.uid)
            user.uid
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get/create family ID.", e)
            null
        }
    }

    private suspend fun ensureFamilyMemberDoc(user: FirebaseUser, familyId: String) {
        try {
            firestore!!.collection("families").document(familyId)
                .update("members.${user.uid}", true)
                .await()
        } catch (_: Exception) {
            try {
                firestore!!.collection("families").document(familyId)
                    .set(mapOf("members" to mapOf(user.uid to true)))
                    .await()
            } catch (_: Exception) { }
        }
    }

    private suspend fun checkFamilyMembership(user: FirebaseUser, familyId: String): Boolean {
        return try {
            firestore!!.collection("families").document(familyId)
                .update("members.${user.uid}", true)
                .await()
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun forkToOwnFamily(user: FirebaseUser, oldFamilyId: String): String {
        val newFamilyId = user.uid

        val newFamilyDoc = firestore!!.collection("families").document(newFamilyId)
        newFamilyDoc.set(mapOf("members" to mapOf(user.uid to true))).await()

        firestore!!.collection("users").document(user.uid)
            .set(mapOf("familyId" to newFamilyId)).await()

        repository.setFamilyId(newFamilyId)
        repository.clearAllFamilyMembers()
        repository.getOrCreateCurrentFamilyMember(
            firebaseUid = user.uid,
            email = user.email,
            displayName = user.displayName
        )

        Log.i(TAG, "Forked from family $oldFamilyId to new family $newFamilyId")
        return newFamilyId
    }

    private suspend fun runSync() {
        val user = getCurrentUser() ?: return
        var familyId = getOrCreateFamilyId(user) ?: return

        if (!checkFamilyMembership(user, familyId)) {
            Log.i(TAG, "User ${user.uid} removed from family $familyId. Forking data.")
            familyId = forkToOwnFamily(user, familyId)
        }

        val familyDoc = firestore!!.collection("families").document(familyId)
        val foodsCollection = familyDoc.collection("foodDiffs")
        val dishesCollection = familyDoc.collection("dishes")
        val settingsCollection = familyDoc.collection("settings")

        migrateFromOldPathIfNeeded(user.uid, foodsCollection, dishesCollection, settingsCollection)

        repository.reconcileRemoteFoods(fetchRemoteFoods(foodsCollection))
        repository.reconcileRemoteDishes(fetchRemoteDishes(dishesCollection))
        repository.reconcileRemoteSettings(fetchRemoteSettings(settingsCollection))

        repository.getBaseFoodsNeedingSync().forEach { food ->
            syncFood(foodsCollection, food)
            repository.markBaseFoodSynced(food.id)
        }

        repository.getDishesNeedingSync().forEach { dish ->
            syncDish(dishesCollection, dish.id)
            repository.markDishSynced(dish.id)
        }

        repository.getSettingsNeedingSync().forEach { setting ->
            syncSetting(settingsCollection, setting)
            repository.markSettingSynced(setting.key)
        }

        repository.reconcileRemoteFoods(fetchRemoteFoods(foodsCollection))
        repository.reconcileRemoteDishes(fetchRemoteDishes(dishesCollection))
        repository.reconcileRemoteSettings(fetchRemoteSettings(settingsCollection))
    }

    private suspend fun migrateFromOldPathIfNeeded(
        uid: String,
        foodsCollection: CollectionReference,
        dishesCollection: CollectionReference,
        settingsCollection: CollectionReference
    ) {
        val hasFamilyFoods = try {
            !foodsCollection.limit(1).get().await().isEmpty
        } catch (_: Exception) { false }
        if (hasFamilyFoods) return

        val oldDoc = firestore!!.collection("users").document(uid)
        try {
            val oldFoods = fetchRemoteFoods(oldDoc.collection("foodDiffs"))
            oldFoods.forEach { food ->
                foodsCollection.document(food.remoteKey).set(
                    mapOf(
                        "source" to food.source.value,
                        "name" to food.name,
                        "carbsPer100g" to food.carbsPer100g,
                        "isDeleted" to food.isDeleted,
                        "updatedAt" to food.updatedAt,
                        "isPacked" to food.isPacked,
                        "packWeight" to food.packWeight,
                        "packCount" to food.packCount
                    )
                ).await()
            }
            val oldDishes = fetchRemoteDishes(oldDoc.collection("dishes"))
            oldDishes.forEach { dish ->
                dishesCollection.document(dish.remoteKey).set(
                    mapOf(
                        "name" to dish.name,
                        "totalCookedWeight" to dish.totalCookedWeight,
                        "isDeleted" to dish.isDeleted,
                        "updatedAt" to dish.updatedAt,
                        "components" to dish.components.map { c ->
                            mapOf("foodRemoteKey" to c.foodRemoteKey, "weightGrams" to c.weightGrams)
                        }
                    )
                ).await()
            }
            val oldSettings = fetchRemoteSettings(oldDoc.collection("settings"))
            oldSettings.forEach { setting ->
                settingsCollection.document(setting.key).set(
                    mapOf("content" to setting.content, "updatedAt" to setting.updatedAt)
                ).await()
            }
        } catch (_: Exception) { }
    }

    private suspend fun fetchRemoteFoods(
        collection: CollectionReference
    ): List<RemoteFoodRecord> {
        return collection.get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val source = FoodSource.fromValue(data["source"] as? String)
            val name = data["name"] as? String ?: return@mapNotNull null
            val carbs = (data["carbsPer100g"] as? Number)?.toDouble() ?: return@mapNotNull null
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            val isDeleted = data["isDeleted"] as? Boolean ?: false
            val isPacked = data["isPacked"] as? Boolean ?: false
            val packWeight = (data["packWeight"] as? Number)?.toDouble()
            val packCount = (data["packCount"] as? Number)?.toInt()
            RemoteFoodRecord(
                remoteKey = document.id,
                source = source,
                name = name,
                carbsPer100g = carbs,
                isDeleted = isDeleted,
                updatedAt = updatedAt,
                isPacked = isPacked,
                packWeight = packWeight,
                packCount = packCount
            )
        }
    }

    private suspend fun syncFood(
        collection: CollectionReference,
        food: BaseFood
    ) {
        val remoteKey = food.remoteKey ?: return
        val document = collection.document(remoteKey)
        val source = FoodSource.fromValue(food.source)

        when (source) {
            FoodSource.DEFAULT -> {
                if (repository.isDefaultFoodAtSeedValue(food)) {
                    document.delete().await()
                } else {
                    document.set(foodPayload(food)).await()
                }
            }

            FoodSource.CUSTOM -> {
                if (food.isDeleted != 0L) {
                    document.delete().await()
                } else {
                    document.set(foodPayload(food)).await()
                }
            }
        }
    }

    private suspend fun fetchRemoteDishes(
        collection: CollectionReference
    ): List<RemoteDishRecord> {
        return collection.get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val name = data["name"] as? String ?: return@mapNotNull null
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            val isDeleted = data["isDeleted"] as? Boolean ?: false
            val components = (data["components"] as? List<*>)?.mapNotNull { rawComponent ->
                val componentMap = rawComponent as? Map<*, *> ?: return@mapNotNull null
                val foodRemoteKey = componentMap["foodRemoteKey"] as? String ?: return@mapNotNull null
                val weightGrams = (componentMap["weightGrams"] as? Number)?.toDouble() ?: return@mapNotNull null
                RemoteDishComponentRecord(foodRemoteKey = foodRemoteKey, weightGrams = weightGrams)
            }.orEmpty()
            val totalCookedWeight = (data["totalCookedWeight"] as? Number)?.toDouble()

            RemoteDishRecord(
                remoteKey = document.id,
                name = name,
                totalCookedWeight = totalCookedWeight,
                isDeleted = isDeleted,
                updatedAt = updatedAt,
                components = components
            )
        }
    }

    private suspend fun fetchRemoteSettings(
        collection: CollectionReference
    ): List<RemoteSettingRecord> {
        return collection.get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            RemoteSettingRecord(
                key = document.id,
                content = data["content"] as? String,
                updatedAt = updatedAt
            )
        }
    }

    private suspend fun syncDish(
        collection: CollectionReference,
        dishId: Long
    ) {
        val remoteDish = repository.getRemoteDishRecord(dishId) ?: return
        val document = collection.document(remoteDish.remoteKey)
        if (remoteDish.isDeleted) {
            document.delete().await()
            return
        }

        document.set(
            mapOf(
                "name" to remoteDish.name,
                "totalCookedWeight" to remoteDish.totalCookedWeight,
                "isDeleted" to false,
                "updatedAt" to remoteDish.updatedAt,
                "components" to remoteDish.components.map { component ->
                    mapOf(
                        "foodRemoteKey" to component.foodRemoteKey,
                        "weightGrams" to component.weightGrams
                    )
                }
            )
        ).await()
    }

    private suspend fun syncSetting(
        collection: CollectionReference,
        setting: com.glicocalc.database.Setting
    ) {
        collection.document(setting.key).set(
            mapOf(
                "content" to setting.content,
                "updatedAt" to setting.updatedAt
            )
        ).await()
    }

    private fun foodPayload(food: BaseFood): Map<String, Any> {
        val payload = mutableMapOf<String, Any>(
            "source" to food.source,
            "name" to food.name,
            "carbsPer100g" to food.carbsPer100g,
            "isDeleted" to (food.isDeleted != 0L),
            "updatedAt" to food.updatedAt,
            "isPacked" to (food.isPacked != 0L)
        )
        if (food.packWeight != null) {
            payload["packWeight"] = food.packWeight
        }
        if (food.packCount != null) {
            payload["packCount"] = food.packCount
        }
        return payload
    }
}

data class SyncUiState(
    val status: SyncStatus,
    val pendingCount: Int,
    val isSignedIn: Boolean,
    val lastSuccessfulSyncAtMillis: Long? = null
)

enum class SyncStatus {
    IDLE,
    SYNCING,
    UP_TO_DATE,
    ERROR,
    UNAVAILABLE
}
