import SwiftUI
import UserNotifications

@main
struct iOSApp: App {

    init() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert]) { _, _ in }
    }

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
