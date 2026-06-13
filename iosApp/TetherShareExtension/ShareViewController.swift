//
//  ShareViewController.swift
//  TetherShareExtension
//

import UIKit
import UniformTypeIdentifiers

// Must match APP_GROUP_ID in composeApp/src/iosMain/kotlin/com/tubetoast/tether/di/IosAppContainer.kt
private let appGroupID = "group.com.tubetoast.tether"

class ShareViewController: UIViewController {

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
        let inboxURL = container.appendingPathComponent("inbox/\(batchID)", isDirectory: true)

        do {
            try FileManager.default.createDirectory(at: inboxURL, withIntermediateDirectories: true)
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

                let destURL = uniqueDestURL(in: inboxURL, baseName: baseName, ext: ext)
                do {
                    try FileManager.default.copyItem(at: url, to: destURL)
                    let attrs = try FileManager.default.attributesOfItem(atPath: destURL.path)
                    let size = (attrs[.size] as? Int) ?? 0
                    manifest.append(["name": destURL.lastPathComponent, "size": size])
                } catch {
                    // Skip files that cannot be copied; the batch may still be partially useful.
                }
            }
        }

        group.notify(queue: .main) { [weak self] in
            guard let self else { return }
            if !manifest.isEmpty {
                self.writeManifest(manifest, to: inboxURL)
            } else {
                try? FileManager.default.removeItem(at: inboxURL)
            }
            self.completeExtension()
        }
    }

    private func preferredTypeID(for provider: NSItemProvider) -> String? {
        let preferred = ["public.file-url", "public.data"]
        for typeID in preferred {
            if provider.hasItemConformingToTypeIdentifier(typeID) {
                return typeID
            }
        }
        return provider.registeredTypeIdentifiers.first as? String
    }

    private func writeManifest(_ entries: [[String: Any]], to batchURL: URL) {
        guard let data = try? JSONSerialization.data(withJSONObject: entries) else { return }
        let manifestURL = batchURL.appendingPathComponent("manifest.json")
        try? data.write(to: manifestURL)
    }

    private func completeExtension() {
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
