import Foundation
import FirebaseFirestore
import FirebaseAuth
import ComposeApp

@MainActor
final class IosFirebaseSyncManager {
    private let repository: GlicoRepository
    private let syncController: IosSyncController
    private let db = Firestore.firestore()
    private var syncTask: Task<Void, Never>?
    private var periodicSyncTask: Task<Void, Never>?
    private var authListener: AuthStateDidChangeListenerHandle?
    private var lastSuccessfulSyncAtMillis: Int64?

    init(repository: GlicoRepository, syncController: IosSyncController) {
        self.repository = repository
        self.syncController = syncController
    }

    func start() {
        authListener = Auth.auth().addStateDidChangeListener { [weak self] _, _ in
            Task { @MainActor in
                self?.requestSync()
                self?.startPeriodicSync()
            }
        }
        requestSync()
        startPeriodicSync()
    }

    func requestSync() {
        syncTask?.cancel()
        syncTask = Task { @MainActor in
            await runSync()
        }
    }

    private func startPeriodicSync() {
        periodicSyncTask?.cancel()
        periodicSyncTask = Task { @MainActor in
            while !Task.isCancelled {
                let interval = Int(repository.getSyncIntervalMinutes())
                try? await Task.sleep(nanoseconds: UInt64(interval) * 60 * 1_000_000_000)
                if !Task.isCancelled {
                    await runSync()
                }
            }
        }
    }

    private func runSync() async {
        guard let user = Auth.auth().currentUser, !user.isAnonymous else { return }
        
        syncController.setCanManualSync(enabled: false)
        
        do {
            let familyId = try await getOrCreateFamilyId(user: user)
            repository.setFamilyId(id: familyId)
            syncController.setCurrentUserEmail(email: user.email)
            
            try await syncFamilyData(familyId: familyId, user: user)
            
            lastSuccessfulSyncAtMillis = Int64(Date().timeIntervalSince1970 * 1000)
            syncController.setLastSyncedMessage(message: formatLastSynced(lastSuccessfulSyncAtMillis!))
            syncController.setCanManualSync(enabled: true)
        } catch {
            print("Sync failed: \(error)")
            syncController.setError(message: "Sync failed. Will retry later.")
            syncController.setCanManualSync(enabled: true)
        }
    }

    private func getOrCreateFamilyId(user: User) async throws -> String {
        let localFamilyId = repository.getFamilyId()
        if let id = localFamilyId { return id }
        
        let userDoc = db.collection("users").document(user.uid)
        let snapshot = try await userDoc.getDocument()
        
        if let id = snapshot.data()?["familyId"] as? String {
            return id
        }
        
        // Check for pending invite
        if let invitedId = try await acceptPendingInvite(user: user) {
            return invitedId
        }
        
        // Create own family
        let familyId = user.uid
        try await createOwnFamily(user: user, familyId: familyId)
        try await userDoc.setData(["familyId": familyId], merge: true)
        
        return familyId
    }

    private func createOwnFamily(user: User, familyId: String) async throws {
        let profile = memberProfilePayload(user: user, email: user.email ?? "")
        try await db.collection("families").document(familyId).setData([
            "ownerUid": user.uid,
            "members": [user.uid: true],
            "memberProfiles": [user.uid: profile],
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true)
    }

    func acceptPendingInvite(user: User) async throws -> String? {
        guard let email = normalizeEmail(user.email) else { return nil }
        let inviteDoc = db.collection("familyInvites").document(inviteDocumentId(email))
        let snapshot = try await inviteDoc.getDocument()
        
        guard snapshot.exists, let familyId = snapshot.data()?["familyId"] as? String else {
            return nil
        }
        
        let name = snapshot.data()?["name"] as? String
        try await acceptFamilyInvite(user: user, familyId: familyId, email: email, name: name)
        try await inviteDoc.delete()
        
        return familyId
    }

    private func acceptFamilyInvite(user: User, familyId: String, email: String, name: String?) async throws {
        let profile = memberProfilePayload(user: user, email: email, name: name)
        try await db.collection("families").document(familyId).setData([
            "members": [user.uid: true],
            "memberProfiles": [user.uid: profile],
            "updatedAt": FieldValue.serverTimestamp()
        ], merge: true)
        
        try await db.collection("users").document(user.uid).setData(["familyId": familyId], merge: true)
        
        repository.setFamilyId(id: familyId)
        repository.clearAllFamilyMembers()
        repository.addFamilyMember(email: email, name: name ?? user.displayName ?? email, firebaseUid: user.uid, isOwner: false)
    }

    private func syncFamilyData(familyId: String, user: User) async throws {
        let familyDoc = db.collection("families").document(familyId)
        
        // 1. Sync family members
        let snapshot = try await familyDoc.getDocument()
        let ownerUid = snapshot.data()?["ownerUid"] as? String
        syncController.setIsFamilyOwner(isOwner: user.uid == ownerUid)

        if let membersData = snapshot.data()?["memberProfiles"] as? [String: [String: Any]] {
            repository.clearAllFamilyMembers()
            for (uid, profile) in membersData {
                let email = profile["email"] as? String ?? ""
                let name = profile["name"] as? String ?? email
                repository.addFamilyMember(email: email, name: name, firebaseUid: uid, isOwner: uid == ownerUid)
            }
        }
        if let familyName = snapshot.data()?["name"] as? String {
            repository.setFamilyName(name: familyName)
        }
        syncController.refreshFamilyMembers()

        // 2. Fetch and reconcile foods
        let remoteFoods = try await fetchRemoteFoods(collection: familyDoc.collection("foodDiffs"))
        repository.reconcileRemoteFoods(remoteFoods: remoteFoods)
        
        // 3. Fetch and reconcile dishes
        let remoteDishes = try await fetchRemoteDishes(collection: familyDoc.collection("dishes"))
        repository.reconcileRemoteDishes(remoteDishes: remoteDishes)

        let remoteMealTypes = try await fetchRemoteMealTypes(collection: familyDoc.collection("mealTypes"))
        repository.reconcileRemoteMealTypes(remoteMealTypes: remoteMealTypes, preserveLocalChanges: user.uid == ownerUid)
        
        // 4. Fetch and reconcile settings
        let remoteSettings = try await fetchRemoteSettings(collection: familyDoc.collection("settings"))
        repository.reconcileRemoteSettings(remoteSettings: remoteSettings)
        
        // 5. Push local changes
        for food in repository.getBaseFoodsNeedingSync() {
            try await syncFood(collection: familyDoc.collection("foodDiffs"), food: food)
            repository.markBaseFoodSynced(id: food.id)
        }
        
        for dish in repository.getDishesNeedingSync() {
            if let remoteDish = repository.getRemoteDishRecord(dishId: dish.id) {
                try await syncDish(collection: familyDoc.collection("dishes"), dish: remoteDish)
                repository.markDishSynced(id: dish.id)
            }
        }

        if user.uid == ownerUid {
            for mealType in repository.getMealTypesNeedingSync() {
                try await syncMealType(collection: familyDoc.collection("mealTypes"), mealType: mealType)
                repository.markMealTypeSynced(id: mealType.id)
            }
        }
        
        for setting in repository.getSettingsNeedingSync() {
            try await syncSetting(collection: familyDoc.collection("settings"), setting: setting)
            repository.markSettingSynced(key: setting.key)
        }
    }

    private func fetchRemoteFoods(collection: CollectionReference) async throws -> [RemoteFoodRecord] {
        let snapshot = try await collection.getDocuments()
        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let name = data["name"] as? String,
                  let carbs = data["carbsPer100g"] as? Double else { return nil }
            
            return RemoteFoodRecord(
                remoteKey: doc.documentID,
                source: FoodSource.companion.fromValue(value: data["source"] as? String),
                name: name,
                carbsPer100g: carbs,
                isDeleted: data["isDeleted"] as? Bool ?? false,
                updatedAt: data["updatedAt"] as? Int64 ?? 0,
                isPacked: data["isPacked"] as? Bool ?? false,
                packWeight: (data["packWeight"] as? Double).map { KotlinDouble(value: $0) },
                packCount: (data["packCount"] as? Int32).map { KotlinInt(value: $0) }
            )
        }
    }

    private func syncFood(collection: CollectionReference, food: BaseFood) async throws {
        guard let remoteKey = food.remoteKey else { return }
        try await collection.document(remoteKey).setData(foodPayload(food: food), merge: true)
    }

    private func foodPayload(food: BaseFood) -> [String: Any] {
        return [
            "source": food.source,
            "name": food.name,
            "carbsPer100g": food.carbsPer100g,
            "isDeleted": food.isDeleted != 0,
            "updatedAt": food.updatedAt,
            "isPacked": food.isPacked != 0,
            "packWeight": food.packWeight ?? NSNull(),
            "packCount": food.packCount ?? NSNull()
        ]
    }

    private func fetchRemoteDishes(collection: CollectionReference) async throws -> [RemoteDishRecord] {
        let snapshot = try await collection.getDocuments()
        return snapshot.documents.compactMap { doc -> RemoteDishRecord? in
            let data = doc.data()
            guard let name = data["name"] as? String else { return nil }
            
            let components: [RemoteDishComponentRecord] = (data["components"] as? [[String: Any]])?.compactMap { cData in
                guard let foodKey = cData["foodRemoteKey"] as? String,
                      let weight = cData["weightGrams"] as? Double else { return nil }
                return RemoteDishComponentRecord(foodRemoteKey: foodKey, weightGrams: weight)
            } ?? []
            
            return RemoteDishRecord(
                remoteKey: doc.documentID,
                name: name,
                totalCookedWeight: (data["totalCookedWeight"] as? Double).map { KotlinDouble(value: $0) },
                isDeleted: data["isDeleted"] as? Bool ?? false,
                updatedAt: data["updatedAt"] as? Int64 ?? 0,
                components: components
            )
        }
    }

    private func fetchRemoteMealTypes(collection: CollectionReference) async throws -> [RemoteMealTypeRecord] {
        let snapshot = try await collection.getDocuments()
        return snapshot.documents.compactMap { doc -> RemoteMealTypeRecord? in
            let data = doc.data()
            guard let name = data["name"] as? String,
                  let targetCarbs = data["targetCarbs"] as? Double,
                  let hourOfDay = data["hourOfDay"] as? Int64 else { return nil }

            return RemoteMealTypeRecord(
                remoteKey: doc.documentID,
                name: name,
                targetCarbs: targetCarbs,
                hourOfDay: hourOfDay,
                isDeleted: data["isDeleted"] as? Bool ?? false,
                updatedAt: data["updatedAt"] as? Int64 ?? 0
            )
        }
    }

    private func syncDish(collection: CollectionReference, dish: RemoteDishRecord) async throws {
        let components = dish.components.map { [
            "foodRemoteKey": $0.foodRemoteKey,
            "weightGrams": $0.weightGrams
        ] }
        
        try await collection.document(dish.remoteKey).setData([
            "name": dish.name,
            "totalCookedWeight": dish.totalCookedWeight ?? NSNull(),
            "isDeleted": dish.isDeleted,
            "updatedAt": dish.updatedAt,
            "components": components
        ], merge: true)
    }

    private func syncMealType(collection: CollectionReference, mealType: MealType) async throws {
        guard let remoteMealType = repository.getRemoteMealTypeRecord(mealType: mealType) else { return }
        try await collection.document(remoteMealType.remoteKey).setData([
            "name": remoteMealType.name,
            "targetCarbs": remoteMealType.targetCarbs,
            "hourOfDay": remoteMealType.hourOfDay,
            "isDeleted": remoteMealType.isDeleted,
            "updatedAt": remoteMealType.updatedAt
        ], merge: true)
    }

    private func fetchRemoteSettings(collection: CollectionReference) async throws -> [RemoteSettingRecord] {
        let snapshot = try await collection.getDocuments()
        return snapshot.documents.map { doc in
            RemoteSettingRecord(
                key: doc.documentID,
                content: doc.data()["content"] as? String,
                updatedAt: doc.data()["updatedAt"] as? Int64 ?? 0
            )
        }
    }

    private func syncSetting(collection: CollectionReference, setting: Setting) async throws {
        try await collection.document(setting.key).setData([
            "content": setting.content ?? NSNull(),
            "updatedAt": setting.updatedAt
        ], merge: true)
    }

    // Family Management
    func inviteMember(email: String, name: String) async {
        guard let user = Auth.auth().currentUser, let familyId = repository.getFamilyId(), let normalizedEmail = normalizeEmail(email) else { return }
        
        do {
            try await db.collection("families").document(familyId).setData([
                "invitedEmails": [normalizedEmail: true],
                "invitedProfiles": [normalizedEmail: ["name": name]],
                "updatedAt": FieldValue.serverTimestamp()
            ], merge: true)
            
            try await db.collection("familyInvites").document(inviteDocumentId(normalizedEmail)).setData([
                "familyId": familyId,
                "email": normalizedEmail,
                "name": name,
                "createdBy": user.uid,
                "createdAt": FieldValue.serverTimestamp()
            ])
            await runSync()
        } catch {
            print("Failed to invite: \(error)")
        }
    }

    func removeMember(email: String) async {
        guard let familyId = repository.getFamilyId(), let member = repository.getFamilyMemberByEmail(email: email), let uid = member.firebaseUid else { return }
        
        do {
            try await db.collection("families").document(familyId).updateData([
                "members.\(uid)": FieldValue.delete(),
                "memberProfiles.\(uid)": FieldValue.delete()
            ])
            await runSync()
        } catch {
            print("Failed to remove member: \(error)")
        }
    }

    func leaveFamily() async {
        guard let user = Auth.auth().currentUser, let oldFamilyId = repository.getFamilyId(), oldFamilyId != user.uid else { return }
        
        do {
            try await db.collection("families").document(oldFamilyId).updateData([
                "members.\(user.uid)": FieldValue.delete(),
                "memberProfiles.\(user.uid)": FieldValue.delete(),
                "updatedAt": FieldValue.serverTimestamp()
            ])
            
            let newFamilyId = user.uid
            try await createOwnFamily(user: user, familyId: newFamilyId)
            try await db.collection("users").document(user.uid).setData(["familyId": newFamilyId], merge: true)
            
            repository.setFamilyId(id: newFamilyId)
            repository.markAllSyncableDataForSync()
            await runSync()
        } catch {
            print("Failed to leave family: \(error)")
        }
    }

    func updateFamilyName(name: String?) async {
        guard let familyId = repository.getFamilyId() else { return }
        let cleanName = name?.trimmingCharacters(in: .whitespacesAndNewlines)
        
        do {
            try await db.collection("families").document(familyId).setData([
                "name": cleanName ?? NSNull(),
                "updatedAt": FieldValue.serverTimestamp()
            ], merge: true)
            repository.setFamilyName(name: cleanName)
            await runSync()
        } catch {
            print("Failed to update family name: \(error)")
        }
    }

    func refreshPendingInviteLabel() async {
        guard let user = Auth.auth().currentUser, let email = normalizeEmail(user.email) else { return }
        let currentFamilyId = repository.getFamilyId()
        
        do {
            let snapshot = try await db.collection("familyInvites").document(inviteDocumentId(email)).getDocument()
            if snapshot.exists, let familyId = snapshot.data()?["familyId"] as? String, familyId != currentFamilyId {
                syncController.setPendingFamilyInviteLabel(label: familyId)
            } else {
                syncController.setPendingFamilyInviteLabel(label: nil)
            }
        } catch {
            print("Failed to refresh pending invite: \(error)")
        }
    }

    func joinFamilyById(familyId: String) async {
        guard let user = Auth.auth().currentUser, let email = normalizeEmail(user.email) else { return }
        
        do {
            // Verify invite exists in that family
            let snapshot = try await db.collection("families").document(familyId).getDocument()
            let invitedEmails = snapshot.data()?["invitedEmails"] as? [String: Bool]
            if invitedEmails?[email] == true {
                let profiles = snapshot.data()?["invitedProfiles"] as? [String: [String: Any]]
                let name = profiles?[email]?["name"] as? String
                
                // Leave current family if any
                if let currentId = repository.getFamilyId(), currentId != familyId {
                    try? await db.collection("families").document(currentId).updateData([
                        "members.\(user.uid)": FieldValue.delete(),
                        "memberProfiles.\(user.uid)": FieldValue.delete()
                    ])
                }
                
                try await acceptFamilyInvite(user: user, familyId: familyId, email: email, name: name)
                repository.markAllSyncableDataForSync()
                await runSync()
            }
        } catch {
            print("Failed to join by ID: \(error)")
        }
    }

    // Helpers
    private func normalizeEmail(_ email: String?) -> String? {
        return email?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private func inviteDocumentId(_ email: String) -> String {
        return email.replacingOccurrences(of: "/", with: "_")
    }

    private func memberProfilePayload(user: User, email: String, name: String? = nil) -> [String: Any] {
        return [
            "email": email,
            "name": name ?? user.displayName ?? email
        ]
    }

    private func formatLastSynced(_ millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(millis) / 1000)
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return "Last synced: \(formatter.string(from: date))"
    }
}
