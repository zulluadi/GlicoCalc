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
import com.google.firebase.firestore.FirebaseFirestore
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
        val providerLabel = user.providerData
            .asSequence()
            .filter { it.providerId != "firebase" }
            .mapNotNull { providerUser ->
                providerUser.email ?: providerUser.displayName ?: providerUser.phoneNumber
            }
            .firstOrNull()

        return user.email
            ?: user.displayName
            ?: user.phoneNumber
            ?: providerLabel
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
        syncMutex.withLock {
            runSync()
        }
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

    private fun isConfigurationFailure(exception: FirebaseException): Boolean {
        val message = exception.message.orEmpty()
        return "CONFIGURATION_NOT_FOUND" in message || "API key not valid" in message
    }

    private suspend fun runSync() {
        val userId = ensureSignedIn() ?: return
        val userDocument = firestore!!.collection("users").document(userId)
        val foodsCollection = userDocument.collection("foodDiffs")
        val dishesCollection = userDocument.collection("dishes")
        val settingsCollection = userDocument.collection("settings")

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

    private suspend fun ensureSignedIn(): String? {
        val user = auth?.currentUser ?: return null
        if (user.isAnonymous) return null
        return user.uid
    }

    private suspend fun fetchRemoteFoods(
        collection: com.google.firebase.firestore.CollectionReference
    ): List<RemoteFoodRecord> {
        return collection.get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val source = FoodSource.fromValue(data["source"] as? String)
            val name = data["name"] as? String ?: return@mapNotNull null
            val carbs = (data["carbsPer100g"] as? Number)?.toDouble() ?: return@mapNotNull null
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            val isDeleted = data["isDeleted"] as? Boolean ?: false
            RemoteFoodRecord(
                remoteKey = document.id,
                source = source,
                name = name,
                carbsPer100g = carbs,
                isDeleted = isDeleted,
                updatedAt = updatedAt
            )
        }
    }

    private suspend fun syncFood(
        collection: com.google.firebase.firestore.CollectionReference,
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
        collection: com.google.firebase.firestore.CollectionReference
    ): List<RemoteDishRecord> {
        return collection.get().await().documents.mapNotNull { document ->
            val data = document.data ?: return@mapNotNull null
            val name = data["name"] as? String ?: return@mapNotNull null
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
            val isDeleted = data["isDeleted"] as? Boolean ?: false
            val components = (data["components"] as? List<*>)?.mapNotNull { rawComponent ->
                val componentMap = rawComponent as? Map<*, *> ?: return@mapNotNull null
                val foodRemoteKey = componentMap["foodRemoteKey"] as? String ?: return@mapNotNull null
                val percentage = (componentMap["percentage"] as? Number)?.toDouble() ?: return@mapNotNull null
                RemoteDishComponentRecord(foodRemoteKey = foodRemoteKey, percentage = percentage)
            }.orEmpty()

            RemoteDishRecord(
                remoteKey = document.id,
                name = name,
                isDeleted = isDeleted,
                updatedAt = updatedAt,
                components = components
            )
        }
    }

    private suspend fun fetchRemoteSettings(
        collection: com.google.firebase.firestore.CollectionReference
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
        collection: com.google.firebase.firestore.CollectionReference,
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
                "isDeleted" to false,
                "updatedAt" to remoteDish.updatedAt,
                "components" to remoteDish.components.map { component ->
                    mapOf(
                        "foodRemoteKey" to component.foodRemoteKey,
                        "percentage" to component.percentage
                    )
                }
            )
        ).await()
    }

    private suspend fun syncSetting(
        collection: com.google.firebase.firestore.CollectionReference,
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
        return mapOf(
            "source" to food.source,
            "name" to food.name,
            "carbsPer100g" to food.carbsPer100g,
            "isDeleted" to (food.isDeleted != 0L),
            "updatedAt" to food.updatedAt
        )
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
