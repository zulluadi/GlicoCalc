import ComposeApp
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import SwiftUI
import UIKit

@MainActor
final class IosGoogleSignInCoordinator: ObservableObject {
    let repository: GlicoRepository = RepositoryFactory.shared.create()
    let syncController = IosSyncController()
    private lazy var syncManager = IosFirebaseSyncManager(repository: repository, syncController: syncController)

    private var isConfigured = false

    func configure() {
        guard !isConfigured else {
            refreshAuthState()
            return
        }

        isConfigured = true
        syncController.onSignInRequested = { [weak self] in
            Task { @MainActor in
                self?.signIn()
            }
        }
        syncController.onSwitchAccountRequested = { [weak self] in
            Task { @MainActor in
                self?.switchAccount()
            }
        }
        syncController.onSignOutRequested = { [weak self] in
            Task { @MainActor in
                self?.signOut()
            }
        }
        
        syncController.onRefreshFamilyMembersRequested = { [weak self] in
            self?.repository.getAllFamilyMembers() ?? []
        }
        syncController.onRefreshFamilyIdRequested = { [weak self] in
            self?.repository.getFamilyId()
        }
        syncController.onRefreshFamilyNameRequested = { [weak self] in
            self?.repository.getFamilyName()
        }
        
        syncController.onManualSyncRequested = { [weak self] in
            self?.syncManager.requestSync()
        }
        
        syncController.onAddFamilyMemberRequested = { [weak self] email, name in
            Task { @MainActor in
                await self?.syncManager.inviteMember(email: email, name: name)
            }
        }
        
        syncController.onRemoveFamilyMemberRequested = { [weak self] email in
            Task { @MainActor in
                await self?.syncManager.removeMember(email: email)
            }
        }
        
        syncController.onLeaveFamilyRequested = { [weak self] in
            Task { @MainActor in
                await self?.syncManager.leaveFamily()
            }
        }
        
        syncController.onJoinFamilyByIdRequested = { [weak self] familyId in
            Task { @MainActor in
                await self?.syncManager.joinFamilyById(familyId: familyId)
            }
        }
        
        syncController.onJoinPendingFamilyInviteRequested = { [weak self] in
            Task { @MainActor in
                _ = try? await self?.syncManager.acceptPendingInvite(user: Auth.auth().currentUser!)
                await self?.syncManager.requestSync()
            }
        }
        
        syncController.onUpdateFamilyNameRequested = { [weak self] name in
            Task { @MainActor in
                await self?.syncManager.updateFamilyName(name: name)
            }
        }
        
        syncController.onRefreshPendingInviteLabelRequested = { [weak self] in
            Task { @MainActor in
                await self?.syncManager.refreshPendingInviteLabel()
            }
        }

        syncManager.start()
        refreshAuthState()
    }

    private func refreshAuthState() {
        guard isFirebaseConfigured() else {
            syncController.setUnavailable(
                message: "Sync sign-in is unavailable until Firebase is configured correctly."
            )
            return
        }

        if let user = Auth.auth().currentUser, !user.isAnonymous {
            syncController.setSignedIn(label: accountLabel(for: user))
        } else {
            syncController.setSignedOut()
        }
    }

    private func signIn() {
        guard isFirebaseConfigured(), let clientID = FirebaseApp.app()?.options.clientID else {
            syncController.setUnavailable(
                message: "Google sign-in is unavailable because GoogleService-Info.plist is missing or incomplete."
            )
            return
        }

        guard let presenter = topViewController() else {
            syncController.setError(message: "Google Sign-In could not start.")
            return
        }

        syncController.setSigningIn()
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { [weak self] result, error in
            Task { @MainActor in
                self?.handleSignInResult(result: result, error: error)
            }
        }
    }

    private func switchAccount() {
        signOut(updateState: false)
        signIn()
    }

    private func signOut(updateState: Bool = true) {
        do {
            try Auth.auth().signOut()
            GIDSignIn.sharedInstance.signOut()
            if updateState {
                syncController.setSignedOut()
            }
        } catch {
            syncController.setError(message: googleSignInErrorMessage(error))
        }
    }

    private func handleSignInResult(result: GIDSignInResult?, error: Error?) {
        if let error {
            syncController.setError(message: googleSignInErrorMessage(error))
            return
        }

        guard
            let user = result?.user,
            let idToken = user.idToken?.tokenString
        else {
            syncController.setError(message: "Google Sign-In did not return a valid credential.")
            return
        }

        let credential = GoogleAuthProvider.credential(
            withIDToken: idToken,
            accessToken: user.accessToken.tokenString
        )
        Auth.auth().signIn(with: credential) { [weak self] authResult, error in
            Task { @MainActor in
                if let error {
                    self?.syncController.setError(message: self?.googleSignInErrorMessage(error) ?? "Google Sign-In failed.")
                    return
                }

                guard let firebaseUser = authResult?.user else {
                    self?.syncController.setError(message: "Google Sign-In did not return a Firebase user.")
                    return
                }

                self?.syncController.setSignedIn(label: self?.accountLabel(for: firebaseUser) ?? "Google account linked")
            }
        }
    }

    private func isFirebaseConfigured() -> Bool {
        if FirebaseApp.app() == nil, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
        return FirebaseApp.app() != nil
    }

    private func accountLabel(for user: User) -> String {
        user.email?.isEmpty == false ? user.email! :
            user.displayName?.isEmpty == false ? user.displayName! :
            "Google account linked"
    }

    private func googleSignInErrorMessage(_ error: Error) -> String {
        let details = error.localizedDescription.lowercased()
        if details.contains("canceled") || details.contains("cancelled") {
            return "Google Sign-In was cancelled."
        }
        if details.contains("network") || details.contains("timed out") {
            return "Google Sign-In failed because the network request did not complete."
        }
        if details.contains("client") || details.contains("url scheme") || details.contains("oauth") {
            return "Google Sign-In is misconfigured for this installed build. Add the iOS app to Firebase, enable Google sign-in, and download a fresh GoogleService-Info.plist."
        }
        return error.localizedDescription.isEmpty ? "Google Sign-In failed." : error.localizedDescription
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        let root = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        return topViewController(from: root)
    }

    private func topViewController(from viewController: UIViewController?) -> UIViewController? {
        if let navigationController = viewController as? UINavigationController {
            return topViewController(from: navigationController.visibleViewController)
        }
        if let tabBarController = viewController as? UITabBarController {
            return topViewController(from: tabBarController.selectedViewController)
        }
        if let presented = viewController?.presentedViewController {
            return topViewController(from: presented)
        }
        return viewController
    }
}
