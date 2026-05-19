# Local-network availability

How Tether knows whether the device is on a usable local network — the engineering counterpart of [`features/system/wifi-availability/spec.md`](../product/features/system/wifi-availability/spec.md).

## Goal

A single product invariant — **"the device is on a usable local network"** — exposed to the rest of Tether as a platform-uniform stream. Discovery starts and stops on its edges; the device-list UI swaps between the searching / peers / paired-offline states (network present) and the no-local-network state (network absent) based on the same stream.

"Usable" here means **L2/L3 carrier exists on a non-loopback interface that Tether traffic could ride** — Wi-Fi client, Ethernet, or this device's own AP/hotspot interface. Internet reachability is irrelevant ([spec § Not in this feature](../product/features/system/wifi-availability/spec.md#not-in-this-feature)).

## Common contract

A single component in `commonMain` — call it `LocalNetworkAvailability` — exposes:

```kotlin
interface LocalNetworkAvailability {
    val state: StateFlow<LocalNetworkState>
}

enum class LocalNetworkState {
    Unknown,     // initial, before the first platform sample
    Available,   // at least one usable interface present
    Unavailable, // no usable interface
}
```

Rules the contract commits to:

- **One stream, every platform.** No platform-specific subtypes leak out. The UI never branches on which platform reported `Unavailable`; copy ("Wi-Fi is off" vs. "You're not on a local network") is selected by the UI layer based on device class, not by the availability source.
- **Hotspot is `Available`.** A device acting as the AP for others reports `Available` on the same code path as a device joined to someone else's network — see [Hotspot host case](#hotspot-host-case).
- **`Unknown` is transient.** Sources emit a real value within their first sampling window. UI treats `Unknown` as "do not change current state" (no flicker on cold start).
- **Latency budget: ~5 s end-to-end** ([spec § What "working" looks like](../product/features/system/wifi-availability/spec.md#what-working-looks-like)) covers OS notification + this stream + downstream wiring. Per-platform sampling must stay well inside it.
- **Source-set placement.** Interface and consumers in `commonMain`. Per-platform `actual`s via DI (see [`dependency-injection.md`](dependency-injection.md)) under `androidMain`, `appleMain`, `desktopMain`. No platform header leaks past the interface.

The stream collapses duplicate emissions (`distinctUntilChanged`) so consumers can subscribe naïvely.

## Per-platform implementation

### Android — `ConnectivityManager.NetworkCallback`

`ConnectivityManager.registerNetworkCallback(NetworkRequest, NetworkCallback)` is available on all supported API levels (`minSdk = 24`); no `Build.VERSION` branching needed for the registration path.

Build the request to match **transport, not capability** — and accept both Wi-Fi and Ethernet (Android tablets and Chromebooks on USB-C Ethernet must report `Available`, matching the [Goal](#goal)):

```kotlin
NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
    // Intentionally NOT requiring NET_CAPABILITY_INTERNET — Tether is local-first.
    // Filtering on INTERNET would drop captive-portal Wi-Fi and the AP-host case.
    .build()
```

A `NetworkRequest` with multiple transports matches a `Network` whose capabilities include any of them — Wi-Fi-only devices and Ethernet-only devices both produce `onAvailable`.

Map callbacks to state:

- `onAvailable(network)` → track network in a small set; emit `Available` when the set transitions empty → non-empty.
- `onLost(network)` → remove; emit `Unavailable` when the set transitions non-empty → empty.
- `onCapabilitiesChanged` / `onLinkPropertiesChanged` — observe but do not flip state on their own; they exist so the implementation can detect when a previously-tracked network ceases to satisfy the request.

Quirks per API level:

- **API 24–25:** the three-argument `registerNetworkCallback(NetworkRequest, NetworkCallback, Handler)` overload does not exist yet. Use the two-argument form and explicitly post received callbacks onto a background dispatcher — do NOT do work on the framework-supplied thread directly.
- **API 26+:** `registerNetworkCallback(request, callback, handler)` is the right form — pass a background handler at registration.
- **API 31+ (`S`):** `onCapabilitiesChanged` fires more often; rely on the set-membership transitions above rather than counting callbacks.

#### AP-host (hotspot) sub-case

When this Android device is the AP, the Wi-Fi station transport is *not* what carries Tether traffic; the tether/AP interface does. `NetworkCallback` filtered on `TRANSPORT_WIFI` may not report this interface as a `Network` at all on every OEM/API level (the same root cause behind the JmDNS host path in [`adr-hotspot-discovery.md`](adr/adr-hotspot-discovery.md)).

Fallback: when no `TRANSPORT_WIFI` network is reported but at least one non-loopback IPv4 interface is up (enumerated via `NetworkInterface.getNetworkInterfaces()` — same predicate as [Desktop JVM](#desktop-jvm--networkinterface-polling) below), report `Available`. The enumeration runs on `onLost` → empty transitions and on a low-frequency safety re-check; it does not poll continuously while a `TRANSPORT_WIFI` network is present.

Hotspot-host devices report `Available` and start discovery normally, consistent with [`discovery.md` § Host-side multi-interface bind](discovery.md#host-side-multi-interface-bind).

#### Permissions

`ConnectivityManager.registerNetworkCallback` requires `ACCESS_NETWORK_STATE` (manifest-only, install-time grant — no runtime prompt). The existing [`AndroidManifest.xml`](../../composeApp/src/androidMain/AndroidManifest.xml) declares `ACCESS_WIFI_STATE` for multicast but not `ACCESS_NETWORK_STATE` — the implementation must add it. The Local Network permission story (Android 13+ runtime nearby-devices, etc.) belongs to [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md), not here.

### iOS / macOS — `NWPathMonitor`

`Network.framework`'s `NWPathMonitor` exposes the same primitives on both platforms. One monitor per source-set is enough.

```swift
let monitor = NWPathMonitor(requiredInterfaceType: .wifi)
monitor.pathUpdateHandler = { path in
    // path.status == .satisfied → Available
}
monitor.start(queue: backgroundQueue)
```

State mapping: `path.status == .satisfied` → `Available`; `.unsatisfied` / `.requiresConnection` → `Unavailable`.

`requiredInterfaceType: .wifi` is a **hard filter** — the monitor reports `.satisfied` only when a path is routed over Wi-Fi. If Wi-Fi is absent, the path is unsatisfied regardless of any other interface that may be up. macOS therefore needs the two-monitor pattern below to cover Ethernet.

#### macOS Ethernet

The product spec treats Ethernet on a desktop as the same active state as Wi-Fi ([spec § Desktop without Wi-Fi](../product/features/system/wifi-availability/spec.md#user-flows)). `NWPathMonitor` does not accept multiple `requiredInterfaceType` values, so on macOS the implementation runs **two monitors** — one with `.wifi`, one with `.wiredEthernet` — and reports `Available` if either reports `.satisfied`.

iOS uses the `.wifi` monitor only. iPadOS Ethernet via USB-C is rare enough to defer; if it becomes a real case, add the `.wiredEthernet` monitor on iOS too — the contract does not change.

#### Hotspot host (Personal Hotspot)

iOS Personal Hotspot routes traffic through an internal `bridge100`/`pdp_ip*` interface. `NWPathMonitor` with `.wifi` is expected to report `.satisfied` while the device's Wi-Fi radio is up serving clients ([`adr-hotspot-discovery.md`](adr/adr-hotspot-discovery.md)) — confirm on real hardware before relying on it. If `.satisfied` is not reported with Personal Hotspot on and Wi-Fi otherwise off, fall back to interface enumeration analogous to the Android AP-host path.

#### Permissions

The Local Network entitlement story already in place for mDNS ([`discovery.md` § Permissions and runtime locks](discovery.md#permissions-and-runtime-locks)) covers `NWPathMonitor` use; no new entitlement.

### Desktop JVM — `NetworkInterface` polling

Java has no portable push-style network-change API. The JDK's `NetworkInterface.getNetworkInterfaces()` is the lowest-common-denominator (also used by [`MdnsDiscoveryBonjour.localAddresses()`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/MdnsDiscoveryBonjour.kt)). Availability uses the following predicate:

```kotlin
fun isUsable(iface: NetworkInterface): Boolean =
    iface.isUp &&
    !iface.isLoopback &&
    iface.inetAddresses.asSequence().any { it is Inet4Address && !it.isLoopbackAddress }
```

`Available` ⇔ at least one interface satisfies `isUsable`.

#### Polling

Polling interval: **~2 seconds** — two samples fit inside the ~5 s budget, give the OS a moment to settle on join/leave, and `getNetworkInterfaces()` is cheap. Exact value tunable in code.

The poll runs in a single coroutine on `Dispatchers.IO` owned by the component's scope, cancelled on `stop()`. Repeated identical samples collapse via `distinctUntilChanged` on the output flow.

Push-style alternatives (JNA `IP_ADAPTER_ADDRESSES` on Windows, `SCNetworkReachability` on macOS) are per-OS and not portable across the Desktop JVM's target distros; macOS uses native `NWPathMonitor` ([`adr-macos-native-vs-jvm.md`](adr/adr-macos-native-vs-jvm.md)), not JVM. Polling stays unless the latency budget tightens.

## Wiring into discovery

The discovery component ([`discovery.md`](discovery.md)) gains a single dependency on `LocalNetworkAvailability.state` and gates its own start/stop:

| `state`       | discovery action                                          |
|---------------|-----------------------------------------------------------|
| `Unknown`     | no-op (do not start, do not stop)                         |
| `Available`   | start discovery if not running                            |
| `Unavailable` | stop discovery gracefully if running; clear `DiscoveredDevicesStore` of live entries |

"Stop gracefully" means cancel the discovery-scope `CoroutineScope` ([`discovery.md` § Cancellation and lifecycle](discovery.md#cancellation-and-lifecycle)) and release the Android `MulticastLock` and the WifiLock/WakeLock held during transfers (covered by their own owners — this layer does not touch them directly).

The wiring lives in the composition root, not in the discovery internals: discovery exposes idempotent `start()` / `stop()`; the availability stream drives them. This keeps both components independently testable.

Recovery latency: `Unavailable → Available` must surface peers within ~5 s ([spec § What "working" looks like](../product/features/system/wifi-availability/spec.md#what-working-looks-like)) — the budget is shared by OS notification + this stream + discovery start-up + first mDNS announce; each layer must stay well inside its share.

`DiscoveredDevicesStore` content on `Unavailable`: drop live entries; **keep** paired-offline entries (their source is the trusted-device store, not discovery).

## Hotspot host case

The product invariant: a device sharing its own hotspot is `Available`. Implementation strategies per platform are above; the cross-platform predicate they converge on matches the existing host-side mDNS predicate from [`discovery.md` § Host-side multi-interface bind](discovery.md#host-side-multi-interface-bind):

> at least one non-loopback, non-link-local IPv4 interface is up.

When the platform's native API (Android `NetworkCallback` on `TRANSPORT_WIFI`, iOS `NWPathMonitor` on `.wifi`) does not report the AP/hotspot interface, the implementation falls back to interface enumeration with that predicate. The fallback path is the same code on every platform that needs it.

## Out of scope

- **Trusted-device storage / paired-device persistence** — see [spec § Not in this feature](../product/features/system/wifi-availability/spec.md#not-in-this-feature).
- **UI rendering of the no-local-network state** — owned by the device-list UX brief.
- **Permissions** (manifest entries, Info.plist usage strings, runtime prompts) — owned by [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md). This component assumes everything it needs is already granted.
- **VPN handling.** VPN-over-Wi-Fi is treated as Wi-Fi.
