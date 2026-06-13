//
//  ShareViewController.swift
//  TetherShareExtension
//

import UIKit
import UniformTypeIdentifiers

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

                let fileName = url.lastPathComponent
                let destURL = inboxURL.appendingPathComponent(fileName)

                do {
                    if FileManager.default.fileExists(atPath: destURL.path) {
                        try FileManager.default.removeItem(at: destURL)
                    }
                    try FileManager.default.copyItem(at: url, to: destURL)

                    let attrs = try FileManager.default.attributesOfItem(atPath: destURL.path)
                    let size = (attrs[.size] as? Int) ?? 0

                    lock.lock()
                    manifest.append(["name": fileName, "size": size])
                    lock.unlock()
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
