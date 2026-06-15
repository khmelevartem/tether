import SwiftUI

// Shared with MainViewController.kt (same string literal must match).
let sharedFilesAvailableNotification = "com.tubetoast.tether.sharedFilesAvailable"

@main
struct iOSApp: App {

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { _ in
                    // iPad multitasking: Tether may already be foreground when the
                    // share URL arrives, so UIApplicationDidBecomeActiveNotification
                    // does not fire. Post explicitly so the Kotlin side drains.
                    NotificationCenter.default.post(
                        name: NSNotification.Name(sharedFilesAvailableNotification),
                        object: nil
                    )
                }
        }
    }
}
