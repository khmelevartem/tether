# Apple Platform — Known Issues & Patterns

Problems we've encountered on Apple targets and how to solve them.
Check here before spending time re-researching a known issue.

For background-networking architectural constraints on iOS (listening sockets,
URLSession-background asymmetric path), see [ios-background-networking.md](ios-background-networking.md).

---

## ObjC delegate GC (silent callback loss)

**Symptom:** callbacks like `netServiceDidPublish`, `netServiceDidResolveAddress` never fire despite the service/browser being created and started. No errors, no logs — just silence.

**Root cause:** ObjC `delegate` properties are declared `weak`. Setting `service.delegate = myDelegate` does **not** retain `myDelegate`. If the only reference to the delegate is through that ObjC weak property, the Kotlin GC collects it before any callback fires.

**Fix:** store an explicit Kotlin strong reference (a class field) alongside every ObjC weak delegate assignment.

```kotlin
// BAD — delegate gets GC'd before callbacks arrive
service.delegate = ServiceDelegate(this)

// GOOD — Kotlin field keeps it alive
private var serviceDelegate: ServiceDelegate? = null

serviceDelegate = ServiceDelegate(this)
service.delegate = serviceDelegate
```

Clean up in `stop()`:
```kotlin
serviceDelegate = null
```

**Affected APIs:** anything using ObjC delegation patterns — `NSNetService.delegate`, `NSNetServiceBrowser.delegate`, `WKWebView.navigationDelegate`, `CLLocationManager.delegate`, etc.

**Reference:** `MdnsDiscovery.apple.kt` — `serviceDelegate`, `browserDelegate`, `resolutionDelegates`.

---

## iOS 14+ Local Network Privacy

**Symptom:** mDNS/Bonjour discovery works in the simulator but silently fails on a physical device. No error, no permission dialog.

**Root cause:** iOS 14 introduced Local Network Privacy. Apps must declare intent to use the local network or the system blocks all multicast/mDNS traffic without user prompt.

**Fix:** add two keys to `iosApp/iosApp/Info.plist`:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>Tether uses the local network to discover and connect to nearby devices for file transfer.</string>
<key>NSBonjourServices</key>
<array>
    <string>_tether._tcp.</string>
</array>
```

`NSBonjourServices` must list every `_service._tcp.` / `_service._udp.` type the app browses or publishes. The permission dialog appears on the first local network access. Without it: simulator works, device silently drops all traffic.

---

## NSRunLoop in tests (callbacks never arrive)

**Symptom:** integration test hangs indefinitely or times out. Bonjour callbacks (`netServiceDidPublish`, `didFind`, `didResolveAddress`) never fire even though the service was started.

**Root cause:** `NSNetService` and `NSNetServiceBrowser` deliver callbacks on the NSRunLoop of the thread that created them. `runBlocking`, `TestCoroutineDispatcher`, and Turbine's `turbineScope` do **not** pump NSRunLoop — they park the current thread waiting on a coroutine, so the run loop never spins and callbacks are never delivered.

**Fix:** use `CFRunLoopRunInMode` to explicitly tick the run loop while polling a condition. Canonical pattern:

```kotlin
@OptIn(ExperimentalForeignApi::class)
private fun awaitCondition(timeoutSec: Double = 10.0, condition: () -> Boolean): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeoutSec.seconds
    while (!condition()) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.05, false)
        if (TimeSource.Monotonic.markNow() > deadline) return false
    }
    return true
}
```

- Tick interval 0.05 s is a good balance: responsive enough (≤50 ms lag) without busy-spinning.
- Read `StateFlow.value` synchronously inside `condition` — no `collect` or coroutine needed.
- `@OptIn(ExperimentalForeignApi::class)` required for `CFRunLoopRunInMode`.

**Reference:** `MdnsDiscoveryTest.kt` — `awaitCondition()` and all integration tests.

---

## Keychain query dicts must be built via CFMutableDictionary

**Symptom:** `SecItemCopyMatching` returns `errSecParam` (`OSStatus=-50`) on a syntactically correct query — first launch where the key doesn't exist yet should return `errSecItemNotFound` (`-25300`), but instead Apple rejects the query outright.

**Root cause:** the query dictionary was built via `NSMutableDictionary` and bridged with `CFBridgingRetain(dict) as CFDictionaryRef`. Even though `NSMutableDictionary` is toll-free with `CFDictionary`, `SecItemCopyMatching` rejects the bridged form whenever `kSecReturnRef` is among the keys. Verified by isolated probe on simulator: every variant of the same query swapped only the dict construction — NS-backed yielded `-50`, raw CF yielded `-25300`. The boolean bridge (`kCFBooleanTrue` vs `NSNumber(bool=true)`) and the tag format are not the trigger; only the dict backing.

`SecKeyCreateRandomKey` is tolerant and accepts NS-backed dicts. The failure surfaces only via `SecItemCopyMatching` (and likely the rest of `SecItem*`).

**Fix:** build queries with `CFDictionaryCreateMutable` + `CFDictionarySetValue` directly. For toll-free-bridged values like `NSData`/`NSNumber`, call `CFBridgingRetain(value)` (+1), `CFDictionarySetValue` (dict retains its own), then `CFRelease` your +1.

```kotlin
val dict = CFDictionaryCreateMutable(
    null, 0,
    kCFTypeDictionaryKeyCallBacks.ptr,
    kCFTypeDictionaryValueCallBacks.ptr,
)!!
CFDictionarySetValue(dict, kSecClass, kSecClassKey)
val tag = CFBridgingRetain(applicationTag.encodeToByteArray().toNSData())
CFDictionarySetValue(dict, kSecAttrApplicationTag, tag)
CFRelease(tag)
CFDictionarySetValue(dict, kSecReturnRef, kCFBooleanTrue)
// ...
val status = SecItemCopyMatching(dict, resultRef.ptr)
CFRelease(dict)
```

**Detectability:** unit tests using `simctl spawn` (no app bundle) hit `errSecNotAvailable` regardless of dict backing, so they can't catch this regression. The bug surfaces only in a real signed app bundle. Smoke on a launched iOS simulator app is the only gate; treat it as the load-bearing test for `SecItem*` paths.

**Reference:** `Keychain.apple.kt` — `buildQuery`, `QueryBuilder`.

---

## `/var` vs `/private/var` path mismatch (NSTemporaryDirectory + folder enumeration)

**Symptom:** relative paths computed by stripping a folder's own path from enumerated child paths produce wrong results or empty strings. Paths look correct in logs but prefix-stripping silently fails.

**Root cause:** `NSTemporaryDirectory()` and `UIDocumentPickerViewController` vend folder URLs whose `.path` resolves through the `/var` symlink (e.g. `/var/folders/…`). `NSFileManager.enumeratorAtURL` returns child URLs whose `.path` resolves through `/private/var`. The two paths share no common string prefix, so a naive `removePrefix(folderPath)` on a child path produces the full path unchanged instead of a relative suffix.

**Fix:** call `realpath()` on the folder's `.path` before creating the enumeration root URL, and use the realpath-derived URL as the strip prefix. `realpath()` resolves the symlink on both sides to `/private/var/…`, making the prefix strip work correctly.

**Scope:** observed on iOS simulator and macOS, where `NSTemporaryDirectory()` resolves under `/var` while `NSFileManager.enumeratorAtURL` resolves item paths via `/private/var`. Real-device behavior is unverified.

---

## Security-scoped resource access for picker-vended folder URLs

**Symptom:** `NSFileManager.enumeratorAtURL` on a folder URL returned by `UIDocumentPickerViewController` returns nothing on a real device even though the folder is non-empty. Simulator and in-sandbox paths work because they don't require a scope grant.

**Root cause:** the system file provider grants access to the folder only while a security scope is held via `startAccessingSecurityScopedResource()`. Once the picker dismisses, access is revoked unless the scope is explicitly started. Failing to call `start` before enumerating yields an empty result with no error.

**Fix:** call `startAccessingSecurityScopedResource()` on the **original picker-vended folder URL** (not the realpath-derived one) before enumeration, and balance it with a matching `stopAccessingSecurityScopedResource()` call after all child sources derived from the enumeration are closed. Child item URLs produced by the enumerator are covered by the folder's scope — they must NOT each call start/stop independently.

**Same quirk for `resourceValuesForKeys` on a picked file URL.** Reading any resource value (e.g. `NSURLFileSizeKey`) on a security-scoped *file* URL from `UIDocumentPickerViewController` also returns `null` on a real device unless the scope is held. On the simulator it returns the value without a scope, so the gap is invisible there. Wrap the read in a `start`/`stop` pair. If skipped, the file's `sizeBytes` is `null` and the transfer shows an indeterminate bar instead of byte progress.

---

## PHPicker photos: lazy materialization + no upfront size

**Constraint:** `PHPickerViewController` exposes no file size before the bytes are exported. The only ways to learn a photo/video size are (1) a full `loadFileRepresentation` export, or (2) resolving a `PHAsset` via `assetIdentifier`, which requires initializing the picker with a `PHPhotoLibrary` and **Photo Library permission** — which we deliberately avoid (PHPicker's value is working without a permission prompt).

**Consequence — deliberate, not a bug:** `LazyPhotoFileSource` exports/copies the file inside `openReadChannel()` (not at pick time, which would freeze the UI for seconds with no feedback), so a photo's `sizeBytes` is `null` until its transfer starts. The size is recovered via argument evaluation order — `PeerFileSender` evaluates `openReadChannel()` before `sizeBytes` in the same call — so the receiver still gets a Content-Length and per-file progress resolves. `BatchSender` re-folds the batch total on each progress emit (not once upfront), so the overall progress bar resolves as files materialize.

**Residual limitations (accepted):**
- The byte-based large-selection warning (`PendingFilesSummary.isLargeSelection`, the `totalBytes > 2 GB` branch) never trips for photos. The **count** branch (`fileCount > 500`) is the safeguard — at ~4 MB/photo that is ≈ 2 GB. A single large video can bypass both checks.
- The aggregate progress bar for a multi-photo batch is fully determinate only once the last file has materialized; per-file bars are determinate throughout.

**Reference:** `LazyPhotoFileSource` and `IosFilePicker.PhotoPickerDelegate` in `transfer/`.

---

## Old-style pbxproj: build-setting values with `$(VAR)` must be quoted

**Symptom:** after a hand-edit to `iosApp.xcodeproj/project.pbxproj`, the entire project becomes unparseable — `xcodebuild` reports the project "is damaged" with a parse error pointing uselessly at line 1. Brace/paren counts stay balanced and a text diff of the edit looks clean, so reading the pbxproj as text does not reveal it.

**Root cause:** the project file is an old-style (NeXT-style) property list, where `(` and `)` are array delimiters. A build-setting value containing a `$(…)` macro — e.g. `PRODUCT_BUNDLE_IDENTIFIER = com.example.App$(TEAM_ID).Ext;` — opens an array mid-value and corrupts the parse of the whole file. The macro contributes a matched paren pair, so paren-balance heuristics and diff review both pass.

**Fix:** wrap every build-setting value containing `$(…)`, `@`, spaces, or other non-word characters in double quotes, mirroring Xcode's own `PRODUCT_NAME = "$(TARGET_NAME)";`. After any hand-edit, confirm the file still parses with `xcodebuild -list -project iosApp/iosApp.xcodeproj` — the only reliable oracle, since `plutil` / `PlistBuddy` reject the `// !$*UTF8*$!` header regardless of validity.

**Scope:** hand-editing pbxproj is the highest-risk part of iOS target work — prefer the Xcode GUI for structural changes. The quoting rule applies to any old-style plist build setting.

---

## `UTType.typeWithFilenameExtension(ext, conformingToType:)` always matches

**Symptom:** classifying a file by extension always returns the first candidate type. Code like "is this a movie? else is it an image?" reports *every* extension — `mp4`, `mov`, `pdf`, `txt` — as the first type tested. A received video then gets routed down the image path (`creationRequestForAssetFromImageAtFileURL`) and PhotoKit rejects it with `PHPhotosErrorDomain` code `3302` (observed; an invalid-resource error — the raw codes are not in Apple's public docs).

**Root cause:** the two-argument `typeWithFilenameExtension(ext, conformingToType:)` **never returns null** for a non-empty extension. When no registered type matches, the OS synthesises a *dynamic* `UTType` (`dyn.…`) declared to conform to the requested supertype — so `typeWithFilenameExtension(ext, conformingToType: imageType) != null` is true for any input, including a video. The discrimination predicate is degenerate.

**Fix:** resolve the canonical type for the extension with the single-arg `typeWithFilenameExtension(ext)` (which *does* return null for unknown extensions), then test `conformsToType` explicitly:

```kotlin
val extType = UTType.typeWithFilenameExtension(ext) ?: return null
when {
    extType.conformsToType(movieType) -> MediaType.Video
    extType.conformsToType(imageType) -> MediaType.Image
    else -> null
}
```

`conformsToType:` is an ObjC category method, so K/N exposes it as a package-level extension function — needs an explicit `import platform.UniformTypeIdentifiers.conformsToType`.

**Not unit-testable:** in the bare K/N test runner (`iosSimulatorArm64Test`) `typeWithIdentifier("public.image")` resolves, but `typeWithFilenameExtension(ext)` returns null for *every* extension — extension→type resolution has no app-bundle type database behind it there. So the real `classifyByUTType` returns null for all input in tests and the bug cannot even reproduce; the regression guard is the iOS-receive smoke block (booted app, real DB), not an `appleTest`. The decorator's injectable `mediaClassifier` seam exists to test the orchestration (auth gate, de-dup, delete-on-success) with a fake — it does not cover `classifyByUTType` itself, which has no unit coverage by design.

**Reference:** `classifyByUTType` in `network/PhotosUploadStorageDecorator.kt`; smoke block `block-6.2-ios-receive.sh`.
