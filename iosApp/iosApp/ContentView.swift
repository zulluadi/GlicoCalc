import ComposeApp
import SwiftUI

struct ContentView: UIViewControllerRepresentable {
    @EnvironmentObject private var googleSignInCoordinator: IosGoogleSignInCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        googleSignInCoordinator.configure()
        return MainViewControllerKt.MainViewController(
            repository: googleSignInCoordinator.repository,
            syncController: googleSignInCoordinator.syncController
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}
