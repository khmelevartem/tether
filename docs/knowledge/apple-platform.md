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

```kotlin
val resolvedFolderPath = realpathOf(folderUrl.path ?: return emptyList())
    ?: folderUrl.path ?: return emptyList()
val resolvedFolderUrl = NSURL.fileURLWithPath(resolvedFolderPath)
// enumerate against resolvedFolderUrl; strip resolvedFolderUrl.path from child paths
```

**Scope:** simulator reproduces this (in-sandbox paths go through `/var`). Real device also affected for picker-vended folder URLs.

---

## Security-scoped resource access for picker-vended folder URLs

**Symptom:** `NSFileManager.enumeratorAtURL` on a folder URL returned by `UIDocumentPickerViewController` returns nothing on a real device even though the folder is non-empty. Simulator and in-sandbox paths work because they don't require a scope grant.

**Root cause:** the system file provider grants access to the folder only while a security scope is held via `startAccessingSecurityScopedResource()`. Once the picker dismisses, access is revoked unless the scope is explicitly started. Failing to call `start` before enumerating yields an empty result with no error.

**Fix:** call `startAccessingSecurityScopedResource()` on the **original picker-vended folder URL** (not the realpath-derived one) before enumeration, and balance it with a matching `stopAccessingSecurityScopedResource()` call after all child sources derived from the enumeration are closed. Child item URLs produced by the enumerator are covered by the folder's scope — they must NOT each call start/stop independently.
