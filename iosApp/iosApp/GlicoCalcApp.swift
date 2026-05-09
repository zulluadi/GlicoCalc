import GoogleSignIn
import SwiftUI

@main
struct GlicoCalcApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var googleSignInCoordinator = IosGoogleSignInCoordinator()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(googleSignInCoordinator)
                .ignoresSafeArea(.container, edges: [.bottom])
                .ignoresSafeArea(.keyboard)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
