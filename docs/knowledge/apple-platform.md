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
