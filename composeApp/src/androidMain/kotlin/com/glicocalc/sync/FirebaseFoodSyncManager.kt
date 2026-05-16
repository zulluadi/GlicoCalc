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
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var periodicSyncJob: Job? = null
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
        startPeriodicSync()
    }

    fun stop() {
        authStateListener?.let { listener ->
            auth?.removeAuthStateListener(listener)
        }
        authStateListener = null
        periodicSyncJob?.cancel()
        periodicSyncJob = null
    }

    fun restartPeriodicSync() {
        startPeriodicSync()
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
        repository.setFamilyId(null)
        repository.clearAllFamilyMembers()
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

    private fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (true) {
                delay(repository.getSyncIntervalMinutes() * 60 * 1000L)
                val user = auth?.currentUser
                if (isEnabled && user != null && !user.isAnonymous) {
                    requestSync()
                }
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

    fun getCurrentFamilyName(): String? {
        return repository.getFamilyName()
    }

    fun currentUserEmail(): String? {
        return normalizeEmail(auth?.currentUser?.email)
    }

    fun isCurrentUserFamilyOwner(): Boolean {
        val uid = auth?.currentUser?.uid ?: return false
        return repository.getFamilyOwnerUid() == uid
    }

    suspend fun pendingFamilyInviteLabel(): String? {
        return try {
            val currentFamilyId = repository.getFamilyId()
            findPendingInvite(getCurrentUser())?.familyId?.takeIf { it != currentFamilyId }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read pending family invite label.", e)
            null
        }
    }

    suspend fun removeMemberFromFamily(memberFirebaseUid: String) {
        val familyId = repository.getFamilyId() ?: return
        try {
            firestore!!.collection("families").document(familyId)
                .update(
                    FieldPath.of("members", memberFirebaseUid),
                    FieldValue.delete(),
                    FieldPath.of("memberProfiles", memberFirebaseUid),
                    FieldValue.delete()
                )
                .await()
            Log.i(TAG, "Removed member $memberFirebaseUid from family $familyId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove member $memberFirebaseUid from family $familyId", e)
        }
    }

    suspend fun leaveCurrentFamily() {
        val user = getCurrentUser() ?: return
        val oldFamilyId = repository.getFamilyId() ?: return
        if (oldFamilyId == user.uid) return

        if (!removeCurrentUserFromFamily(user, oldFamilyId)) return

        forkToOwnFamily(user, oldFamilyId)
        repository.markAllSyncableDataForSync()
    }

    suspend fun joinPendingFamilyInvite(): Boolean {
        val user = getCurrentUser() ?: return false
        val invite = findPendingInvite(user) ?: return false
        val currentFamilyId = repository.getFamilyId()

        if (currentFamilyId != null && currentFamilyId != invite.familyId) {
            if (!removeCurrentUserFromFamily(user, currentFamilyId)) return false
        }

        acceptInvite(user, invite)
        repository.markAllSyncableDataForSync()
        return true
    }

    suspend fun joinFamilyById(targetFamilyId: String): Boolean {
        val user = getCurrentUser() ?: return false
        val familyId = targetFamilyId.trim().takeIf { it.isNotBlank() } ?: return false
        val currentFamilyId = repository.getFamilyId()
        if (currentFamilyId == familyId) return true

        val normalizedEmail = normalizeEmail(user.email) ?: return false
        val name = try {
            val snapshot = firestore!!.collection("families").document(familyId).get().await()
            val invitedEmails = snapshot.get("invitedEmails") as? Map<*, *>
            if (invitedEmails?.get(normalizedEmail) != true) return false
            val inviteProfile = snapshot.get("invitedProfiles") as? Map<*, *>
            val profile = inviteProfile?.get(normalizedEmail) as? Map<*, *>
            profile?.get("name") as? String
        } catch (e: Exception) {
            Log.w(TAG, "Failed to verify invite for $normalizedEmail in family $familyId", e)
            return false
        }

        if (currentFamilyId != null) {
            if (!removeCurrentUserFromFamily(user, currentFamilyId)) return false
        }

        acceptFamilyInvite(user, familyId, normalizedEmail, name)
        repository.markAllSyncableDataForSync()
        return true
    }

    suspend fun inviteFamilyMember(email: String, name: String) {
        val normalizedEmail = normalizeEmail(email) ?: return
        val user = getCurrentUser() ?: return
        val familyId = getOrCreateFamilyId(user) ?: return

        try {
            firestore!!.collection("families").document(familyId)
                .set(
                    mapOf(
                        "invitedEmails" to mapOf(normalizedEmail to true),
                        "invitedProfiles" to mapOf(normalizedEmail to mapOf("name" to name)),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()

            firestore!!.collection("familyInvites").document(inviteDocumentId(normalizedEmail))
                .set(
                    mapOf(
                        "familyId" to familyId,
                        "email" to normalizedEmail,
                        "name" to name,
                        "createdBy" to user.uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Log.i(TAG, "Invited $normalizedEmail to family $familyId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to invite $normalizedEmail to family $familyId", e)
        }
    }

    suspend fun updateFamilyName(name: String?) {
        val familyId = repository.getFamilyId() ?: return
        val user = getCurrentUser() ?: return
        if (!isCurrentUserFamilyOwner()) return
        val cleanName = name?.trim()?.takeIf { it.isNotBlank() }

        try {
            firestore!!.collection("families").document(familyId)
                .set(
                    mapOf(
                        "name" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
            repository.setFamilyName(cleanName)
            Log.i(TAG, "Updated family name for $familyId by ${user.uid}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update family name for $familyId", e)
        }
    }

    suspend fun removeFamilyInvite(email: String) {
        val normalizedEmail = normalizeEmail(email) ?: return
        val familyId = repository.getFamilyId() ?: return

        try {
            firestore!!.collection("families").document(familyId)
                .update(
                    FieldPath.of("invitedEmails", normalizedEmail),
                    FieldValue.delete(),
                    FieldPath.of("invitedProfiles", normalizedEmail),
                    FieldValue.delete()
                )
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove invited email $normalizedEmail from family $familyId", e)
        }

        try {
            firestore!!.collection("familyInvites").document(inviteDocumentId(normalizedEmail))
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete invite for $normalizedEmail in family $familyId", e)
        }
    }

    suspend fun permanentlyDeleteFood(remoteKey: String) {
        val familyId = repository.getFamilyId() ?: return
        try {
            firestore!!.collection("families").document(familyId)
                .collection("foodDiffs")
                .document(remoteKey)
                .delete()
                .await()
            Log.i(TAG, "Permanently deleted food $remoteKey from family $familyId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to permanently delete food $remoteKey from family $familyId", e)
        }
    }

    suspend fun permanentlyDeleteDish(remoteKey: String) {
        val familyId = repository.getFamilyId() ?: return
        try {
            firestore!!.collection("families").document(familyId)
                .collection("dishes")
                .document(remoteKey)
                .delete()
                .await()
            Log.i(TAG, "Permanently deleted dish $remoteKey from family $familyId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to permanently delete dish $remoteKey from family $familyId", e)
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
                return remoteFamilyId
            }
            val invitedFamilyId = acceptPendingInvite(user)
            if (invitedFamilyId != null) {
                repository.setFamilyId(invitedFamilyId)
                return invitedFamilyId
            }
            userDoc.set(mapOf("familyId" to user.uid)).await()
            repository.setFamilyId(user.uid)
            createOwnFamily(user, user.uid)
            user.uid
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get/create family ID.", e)
            null
        }
    }

    private suspend fun createOwnFamily(user: FirebaseUser, familyId: String) {
        firestore!!.collection("families").document(familyId)
            .set(
                mapOf(
                    "ownerUid" to user.uid,
                    "members" to mapOf(user.uid to true),
                    "memberProfiles" to mapOf(user.uid to memberProfilePayload(user, user.email)),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    private suspend fun isFamilyMember(user: FirebaseUser, familyId: String): Boolean {
        return try {
            val snapshot = firestore!!.collection("families").document(familyId).get().await()
            val members = snapshot.get("members") as? Map<*, *>
            members?.get(user.uid) == true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun acceptPendingInvite(user: FirebaseUser): String? {
        val invite = findPendingInvite(user) ?: return null
        return acceptInvite(user, invite)
    }

    private suspend fun findPendingInvite(user: FirebaseUser?): PendingFamilyInvite? {
        val normalizedEmail = normalizeEmail(user?.email) ?: return null
        val invite = try {
            firestore!!.collection("familyInvites")
                .document(inviteDocumentId(normalizedEmail))
                .get()
                .await()
                .takeIf { it.exists() }
        } catch (e: Exception) {
            Log.w(TAG, "Pending invite lookup is not permitted for $normalizedEmail.", e)
            null
        } ?: return null

        val familyId = invite.getString("familyId") ?: return null
        val name = invite.getString("name")
        return PendingFamilyInvite(
            familyId = familyId,
            email = normalizedEmail,
            name = name,
            documentPath = invite.reference.path
        )
    }

    private suspend fun acceptInvite(user: FirebaseUser, invite: PendingFamilyInvite): String {
        val familyId = acceptFamilyInvite(user, invite.familyId, invite.email, invite.name)
        val inviteReference = firestore!!.document(invite.documentPath)
        try {
            inviteReference.delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete accepted invite for ${invite.email}", e)
        }
        Log.i(TAG, "Accepted invite for ${invite.email} into family $familyId")
        return familyId
    }

    private suspend fun acceptFamilyInvite(
        user: FirebaseUser,
        familyId: String,
        normalizedEmail: String,
        name: String?
    ): String {

        firestore!!.collection("families").document(familyId)
            .set(
                mapOf(
                    "members" to mapOf(user.uid to true),
                    "memberProfiles" to mapOf(user.uid to memberProfilePayload(user, normalizedEmail, name)),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        firestore!!.collection("users").document(user.uid)
            .set(mapOf("familyId" to familyId), SetOptions.merge())
            .await()

        try {
            firestore!!.collection("familyInvites").document(inviteDocumentId(normalizedEmail))
                .delete()
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete direct invite for $normalizedEmail", e)
        }

        repository.setFamilyId(familyId)
        repository.clearAllFamilyMembers()
        repository.addFamilyMember(
            email = normalizedEmail,
            name = name ?: user.displayName ?: normalizedEmail,
            firebaseUid = user.uid,
            isOwner = false
        )
        syncFamilyMemberProfiles(familyId)
        return familyId
    }

    private suspend fun removeCurrentUserFromFamily(user: FirebaseUser, familyId: String): Boolean {
        return try {
            val familyDocument = firestore!!.collection("families").document(familyId)
            val snapshot = familyDocument.get().await()
            val members = snapshot.get("members") as? Map<*, *> ?: emptyMap<Any, Any>()
            val ownerUid = snapshot.getString("ownerUid")
            val nextOwnerUid = if (ownerUid == user.uid) {
                members.keys
                    .filterIsInstance<String>()
                    .firstOrNull { it != user.uid }
            } else {
                null
            }

            if (nextOwnerUid != null) {
                familyDocument.update(
                    FieldPath.of("members", user.uid),
                    FieldValue.delete(),
                    FieldPath.of("memberProfiles", user.uid),
                    FieldValue.delete(),
                    "ownerUid",
                    nextOwnerUid,
                    "updatedAt",
                    FieldValue.serverTimestamp()
                ).await()
            } else {
                familyDocument.update(
                    FieldPath.of("members", user.uid),
                    FieldValue.delete(),
                    FieldPath.of("memberProfiles", user.uid),
                    FieldValue.delete(),
                    "updatedAt",
                    FieldValue.serverTimestamp()
                ).await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove current user ${user.uid} from family $familyId", e)
            false
        }
    }

    private suspend fun forkToOwnFamily(user: FirebaseUser, oldFamilyId: String): String {
        val newFamilyId = user.uid

        createOwnFamily(user, newFamilyId)

        firestore!!.collection("users").document(user.uid)
            .set(mapOf("familyId" to newFamilyId), SetOptions.merge()).await()

        repository.setFamilyId(newFamilyId)
        repository.clearAllFamilyMembers()
        repository.addFamilyMember(
            email = user.email ?: "${user.uid}@family.local",
            name = user.displayName ?: user.email ?: user.uid,
            firebaseUid = user.uid,
            isOwner = true
        )

        Log.i(TAG, "Forked from family $oldFamilyId to new family $newFamilyId")
        return newFamilyId
    }

    private suspend fun runSync() {
        val user = getCurrentUser() ?: return
        var familyId = getOrCreateFamilyId(user) ?: return

        if (!isFamilyMember(user, familyId)) {
            val invitedFamilyId = acceptPendingInvite(user)
            if (invitedFamilyId != null) {
                familyId = invitedFamilyId
            }
        }

        if (!isFamilyMember(user, familyId)) {
            Log.i(TAG, "User ${user.uid} removed from family $familyId. Forking data.")
            familyId = forkToOwnFamily(user, familyId)
        }
        publishCurrentUserProfileIfOwner(user, familyId)
        syncFamilyMemberProfiles(familyId)
        publishPendingFamilyInvites(user, familyId)

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
                document.set(foodPayload(food)).await()
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

        document.set(
            mapOf(
                "name" to remoteDish.name,
                "totalCookedWeight" to remoteDish.totalCookedWeight,
                "isDeleted" to remoteDish.isDeleted,
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

    private suspend fun syncFamilyMemberProfiles(familyId: String) {
        val snapshot = try {
            firestore!!.collection("families").document(familyId).get().await()
        } catch (_: Exception) {
            return
        }
        val profiles = snapshot.get("memberProfiles") as? Map<*, *> ?: return
        val ownerUid = snapshot.getString("ownerUid")
        repository.setFamilyName(snapshot.getString("name")?.trim()?.takeIf { it.isNotBlank() })
        val activeMemberUids = profiles.keys.filterIsInstance<String>().toSet()
        var ownerEmail: String? = null
        profiles.forEach { (rawUid, rawProfile) ->
            val uid = rawUid as? String ?: return@forEach
            val profile = rawProfile as? Map<*, *> ?: return@forEach
            val email = normalizeEmail(profile["email"] as? String) ?: return@forEach
            val name = (profile["name"] as? String)?.takeIf { it.isNotBlank() } ?: email
            val localMember = repository.getFamilyMemberByEmail(email)
            if (localMember == null) {
                repository.addFamilyMember(email = email, name = name, firebaseUid = uid)
            } else {
                if (localMember.firebaseUid != uid) {
                    repository.updateFamilyMemberFirebaseUid(email, uid)
                }
                if (localMember.name != name) {
                    repository.updateFamilyMemberName(email, name)
                }
            }
            if (uid == ownerUid) {
                ownerEmail = email
            }
        }
        repository.getAllFamilyMembers()
            .mapNotNull { it.firebaseUid }
            .filterNot { it in activeMemberUids }
            .forEach(repository::removeFamilyMemberByFirebaseUid)

        if (ownerEmail != null) {
            repository.setFamilyOwner(ownerEmail!!)
        }
    }

    private suspend fun publishCurrentUserProfileIfOwner(user: FirebaseUser, familyId: String) {
        try {
            val familyDocument = firestore!!.collection("families").document(familyId)
            val snapshot = familyDocument.get().await()
            if (snapshot.getString("ownerUid") != user.uid) return

            familyDocument.set(
                mapOf(
                    "members" to mapOf(user.uid to true),
                    "memberProfiles" to mapOf(user.uid to memberProfilePayload(user, user.email)),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish owner profile for family $familyId", e)
        }
    }

    private suspend fun publishPendingFamilyInvites(user: FirebaseUser, familyId: String) {
        val currentEmail = normalizeEmail(user.email)
        repository.getAllFamilyMembers()
            .filter { member -> member.firebaseUid == null && normalizeEmail(member.email) != currentEmail }
            .forEach { member ->
                inviteFamilyMember(member.email, member.name)
            }
    }

    private fun memberProfilePayload(
        user: FirebaseUser,
        emailOverride: String?,
        nameOverride: String? = null
    ): Map<String, Any> {
        val email = normalizeEmail(emailOverride ?: user.email) ?: user.uid
        val name = nameOverride
            ?: user.displayName
            ?: email
        return mapOf(
            "email" to email,
            "name" to name
        )
    }

    private fun normalizeEmail(email: String?): String? {
        return email
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf { it.isNotBlank() && '/' !in it }
    }

    private fun inviteDocumentId(email: String): String {
        return email.replace("/", "_")
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

private data class PendingFamilyInvite(
    val familyId: String,
    val email: String,
    val name: String?,
    val documentPath: String
)
