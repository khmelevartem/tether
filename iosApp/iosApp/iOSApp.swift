import SwiftUI

@main
struct iOSApp: App {

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { _ in
                    // No-op: opening the app foregrounds it, which fires
                    // UIApplicationDidBecomeActiveNotification → drainSharedFiles().
                }
        }
    }
}
