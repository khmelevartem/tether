//
//  ShareViewController.swift
//  TetherShareExtension
//

import UIKit
import UniformTypeIdentifiers
import UserNotifications

// Must match APP_GROUP_ID in composeApp/src/iosMain/kotlin/com/tubetoast/tether/di/IosAppContainer.kt
private let appGroupID = "group.com.tubetoast.tether"

class ShareViewController: UIViewController {

    private var didComplete = false
    private var safetyTimer: Timer?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        copyAttachments()
    }

    private func copyAttachments() {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            completeExtension()
            return
        }

        let providers: [NSItemProvider] = items.compactMap { $0.attachments }.flatMap { $0 }
        guard !providers.isEmpty else {
            completeExtension()
            return
        }

        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) else {
            completeExtension()
            return
        }

        let batchID = UUID().uuidString
        // Write into a temp dir outside inbox/ so the reader never sees a partial batch.
        // After manifest is written, an atomic rename publishes it to inbox/<batchID>/.
        let tmpURL = container.appendingPathComponent("tmp/\(batchID)", isDirectory: true)
        let inboxURL = container.appendingPathComponent("inbox/\(batchID)", isDirectory: true)

        do {
            try FileManager.default.createDirectory(at: tmpURL, withIntermediateDirectories: true)
        } catch {
            completeExtension()
            return
        }

        let group = DispatchGroup()
        var manifest: [[String: Any]] = []
        let lock = NSLock()

        for provider in providers {
            let typeID = preferredTypeID(for: provider)
            guard let typeID else { continue }

            group.enter()
            provider.loadFileRepresentation(forTypeIdentifier: typeID) { url, error in
                defer { group.leave() }
                guard let url, error == nil else { return }

                let baseName = url.deletingPathExtension().lastPathComponent
                let ext = url.pathExtension

                // Collision-check, uniquify, and copy are serialized under lock so two
                // attachments with the same name don't race-overwrite each other.
                lock.lock()
                defer { lock.unlock() }

                let destURL = uniqueDestURL(in: tmpURL, baseName: baseName, ext: ext)
                do {
                    try FileManager.default.copyItem(at: url, to: destURL)
                    let attrs = try FileManager.default.attributesOfItem(atPath: destURL.path)
                    let size = (attrs[.size] as? NSNumber)?.int64Value ?? 0
                    manifest.append(["name": destURL.lastPathComponent, "size": size])
                } catch {
                    // Skip files that cannot be copied; the batch may still be partially useful.
                }
            }
        }

        group.notify(queue: .main) { [weak self] in
            guard let self else { return }
            if manifest.isEmpty {
                try? FileManager.default.removeItem(at: tmpURL)
                self.completeExtension()
                return
            }
            guard self.publishBatch(manifest: manifest, from: tmpURL, to: inboxURL) else {
                try? FileManager.default.removeItem(at: tmpURL)
                self.completeExtension()
                return
            }
            self.openHostApp(fileCount: manifest.count)
        }
    }

    // Order: responder-chain open → local notification → in-extension confirmation.
    private func openHostApp(fileCount: Int) {
        // Gray-area host-open from a share extension via the responder chain; public API,
        // but Apple discourages it — hence the fallbacks below.
        let url = URL(string: "tether://shared")!
        let ctx = extensionContext
        if let app = responderChainApplication() {
            app.open(url, options: [:]) { [weak self] opened in
                if opened {
                    // Capture ctx so completion fires even if self is deallocated.
                    if let self { self.completeExtension() } else { ctx?.completeRequest(returningItems: [], completionHandler: nil) }
                } else {
                    self?.scheduleNotificationOrShowConfirmation(fileCount: fileCount)
                }
            }
        } else {
            scheduleNotificationOrShowConfirmation(fileCount: fileCount)
        }
    }

    private func responderChainApplication() -> UIApplication? {
        var responder: UIResponder? = self.next
        while let r = responder {
            if let app = r as? UIApplication { return app }
            responder = r.next
        }
        return nil
    }

    private func scheduleNotificationOrShowConfirmation(fileCount: Int) {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            guard let self else { return }
            switch settings.authorizationStatus {
            case .authorized, .provisional:
                DispatchQueue.main.async { self.scheduleNotification(fileCount: fileCount) }
            case .notDetermined:
                UNUserNotificationCenter.current().requestAuthorization(options: [.alert]) { [weak self] granted, _ in
                    guard let self else { return }
                    DispatchQueue.main.async {
                        if granted {
                            self.scheduleNotification(fileCount: fileCount)
                        } else {
                            self.showConfirmation(fileCount: fileCount)
                        }
                    }
                }
            default:
                DispatchQueue.main.async { self.showConfirmation(fileCount: fileCount) }
            }
        }
    }

    private func scheduleNotification(fileCount: Int) {
        let content = UNMutableNotificationContent()
        content.body = "\(fileCount) \(fileCount == 1 ? "file" : "files") ready to send — tap to open Tether"
        let request = UNNotificationRequest(
            identifier: "tether.shared.\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        let ctx = extensionContext
        UNUserNotificationCenter.current().add(request) { [weak self] _ in
            if let self { self.completeExtension() } else { ctx?.completeRequest(returningItems: [], completionHandler: nil) }
        }
    }

    private func showConfirmation(fileCount: Int) {
        let label = UILabel()
        label.text = "✓ \(fileCount) \(fileCount == 1 ? "file" : "files") ready to send. Open Tether and pick a device."
        label.numberOfLines = 0
        label.textAlignment = .center
        label.font = .preferredFont(forTextStyle: .body)
        label.translatesAutoresizingMaskIntoConstraints = false

        let button = UIButton(type: .system)
        button.setTitle("Done", for: .normal)
        button.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(confirmationDoneTapped), for: .touchUpInside)

        let stack = UIStackView(arrangedSubviews: [label, button])
        stack.axis = .vertical
        stack.spacing = 24
        stack.translatesAutoresizingMaskIntoConstraints = false

        view.backgroundColor = .systemBackground
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 32),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -32),
        ])

        safetyTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: false) { [weak self] _ in
            self?.completeExtension()
        }
    }

    @objc private func confirmationDoneTapped() {
        completeExtension()
    }

    /// Writes manifest into the temp dir, then atomically renames it into inbox/.
    /// Returns false if either step fails; caller must clean up tmpURL.
    private func publishBatch(manifest: [[String: Any]], from tmpURL: URL, to inboxURL: URL) -> Bool {
        guard let data = try? JSONSerialization.data(withJSONObject: manifest) else { return false }
        let manifestURL = tmpURL.appendingPathComponent("manifest.json")
        guard (try? data.write(to: manifestURL)) != nil else { return false }
        // Same volume (app-group container) → rename is atomic; reader only sees complete batches.
        guard (try? FileManager.default.moveItem(at: tmpURL, to: inboxURL)) != nil else { return false }
        return true
    }

    private func preferredTypeID(for provider: NSItemProvider) -> String? {
        // Do NOT request public.file-url: loadFileRepresentation for that UTI materializes a
        // tiny file containing the URL string, not the document bytes. Instead use the provider's
        // own concrete content type so we get actual bytes; fall back to public.data.
        for typeID in provider.registeredTypeIdentifiers {
            if typeID == "public.file-url" || typeID == "public.url" { continue }
            return typeID
        }
        return provider.hasItemConformingToTypeIdentifier("public.data") ? "public.data" : nil
    }

    private func completeExtension() {
        guard !didComplete else { return }
        didComplete = true
        safetyTimer?.invalidate()
        safetyTimer = nil
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}

private func uniqueDestURL(in dir: URL, baseName: String, ext: String) -> URL {
    let firstName = ext.isEmpty ? baseName : "\(baseName).\(ext)"
    var candidate = dir.appendingPathComponent(firstName)
    var counter = 2
    while FileManager.default.fileExists(atPath: candidate.path) {
        let name = ext.isEmpty ? "\(baseName) (\(counter))" : "\(baseName) (\(counter)).\(ext)"
        candidate = dir.appendingPathComponent(name)
        counter += 1
    }
    return candidate
}
