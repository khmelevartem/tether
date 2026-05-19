# Local-network availability

How Tether knows whether the device is on a usable local network — the engineering counterpart of [`features/system/wifi-availability/spec.md`](../product/features/system/wifi-availability/spec.md).

This is a living doc: it states what should be true of any correct implementation. Where the codebase has not yet caught up (today there is no `LocalNetworkAvailability` component), treat the gap as an implementation issue, not a contradiction.

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
- **Latency budget: ~5 s end-to-end.** The product spec promises that flipping Wi-Fi on or off propagates to the screen within roughly five seconds ([spec § What "working" looks like](../product/features/system/wifi-availability/spec.md#what-working-looks-like)). That budget covers OS notification + this stream + downstream wiring. Per-platform sampling must respect it (Desktop polling interval picked accordingly — see below).
- **Source-set placement.** Interface and consumers in `commonMain`. Per-platform `actual`s via DI (see [`dependency-injection.md`](dependency-injection.md)) under `androidMain`, `appleMain`, `desktopMain`. No platform header leaks past the interface.

The stream collapses duplicate emissions (`distinctUntilChanged`) so consumers can subscribe naïvely.

## Per-platform implementation

### Android — `ConnectivityManager.NetworkCallback`

Tether's `minSdk = 24`, `compileSdk = 36` (`gradle/libs.versions.toml`). `ConnectivityManager.registerNetworkCallback(NetworkRequest, NetworkCallback)` is available on all supported API levels; no `Build.VERSION` branching needed for the registration path.

Build the request to match **transport, not capability**:

```kotlin
NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    // Intentionally NOT requiring NET_CAPABILITY_INTERNET — Tether is local-first.
    // Filtering on INTERNET would drop captive-portal Wi-Fi and the AP-host case.
    .build()
```

Map callbacks to state:

- `onAvailable(network)` → track network in a small set; emit `Available` when the set transitions empty → non-empty.
- `onLost(network)` → remove; emit `Unavailable` when the set transitions non-empty → empty.
- `onCapabilitiesChanged` / `onLinkPropertiesChanged` — observe but do not flip state on their own; they exist so the implementation can detect when a previously-tracked network ceases to satisfy the request.

Quirks per API level:

- **API 24–25:** the three-argument `registerNetworkCallback(NetworkRequest, NetworkCallback, Handler)` overload does not exist yet. Use the two-argument form and explicitly post received callbacks onto a background dispatcher — do NOT do work on the framework-supplied thread directly.
- **API 26+:** `registerNetworkCallback(request, callback, handler)` is the right form — pass a background handler at registration.
- **API 31+ (`S`):** `onCapabilitiesChanged` fires more often; rely on the set-membership transitions above rather than counting callbacks.

#### AP-host (hotspot) sub-case

When this Android device is the AP, the Wi-Fi station transport is *not* what carries Tether traffic; the tether/AP interface does. `NetworkCallback` filtered on `TRANSPORT_WIFI` may not report this interface as a `Network` at all on every OEM/API level — this is the same root cause that drove [`adr-hotspot-discovery.md`](adr/adr-hotspot-discovery.md) to a JmDNS-based mDNS implementation when the device is the host.

The availability layer mirrors that fallback: when no `TRANSPORT_WIFI` network is reported but at least one non-loopback IPv4 interface is up (enumerated via `NetworkInterface.getNetworkInterfaces()` — same predicate as [Desktop JVM](#desktop-jvm--networkinterface-polling) below), report `Available`. The enumeration runs on `onLost` → empty transitions and on a low-frequency safety re-check; it does not poll continuously while a `TRANSPORT_WIFI` network is present.

This keeps the contract: hotspot-host devices see `Available` and start discovery normally, consistent with [`discovery.md` § Host-side multi-interface bind](discovery.md#host-side-multi-interface-bind).

#### Permissions

`ConnectivityManager.registerNetworkCallback` requires `ACCESS_NETWORK_STATE` (manifest-only, install-time grant — no runtime prompt). The existing [`AndroidManifest.xml`](../../composeApp/src/androidMain/AndroidManifest.xml) currently declares `ACCESS_WIFI_STATE` for multicast but not `ACCESS_NETWORK_STATE`; the implementation issue must add it. The Local Network permission story (Android 13+ runtime nearby-devices, etc.) belongs to [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md), not here.

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

iOS Personal Hotspot routes traffic through an internal `bridge100`/`pdp_ip*` interface. `NWPathMonitor` with `.wifi` typically still reports `.satisfied` because the device's own Wi-Fi radio is up serving clients; this matches the existing [`adr-hotspot-discovery.md`](adr/adr-hotspot-discovery.md) note that Bonjour publishes across interfaces on iOS hotspot. **TBD — verify during implementation** that `.satisfied` is reported on real hardware with Personal Hotspot enabled and Wi-Fi otherwise off; if not, add an interface-enumeration fallback analogous to the Android AP-host path.

#### Permissions

The Local Network entitlement story already in place for mDNS ([`discovery.md` § Permissions and runtime locks](discovery.md#permissions-and-runtime-locks)) covers `NWPathMonitor` use; no new entitlement.

### Desktop JVM — `NetworkInterface` polling

Java has no portable push-style network-change API. The JDK's `NetworkInterface.getNetworkInterfaces()` is the lowest-common-denominator, and that is what the codebase already calls in [`MdnsDiscoveryBonjour.localAddresses()`](../../composeApp/src/desktopMain/kotlin/com/tubetoast/tether/discovery/bonjour/MdnsDiscoveryBonjour.kt) (which today enumerates without filtering — the address shape it returns is good enough for its caller). For availability, add the following predicate alongside it:

```kotlin
fun isUsable(iface: NetworkInterface): Boolean =
    iface.isUp &&
    !iface.isLoopback &&
    iface.inetAddresses.asSequence().any { it is Inet4Address && !it.isLoopbackAddress }
```

`Available` ⇔ at least one interface satisfies `isUsable`. If `MdnsDiscoveryBonjour` ever needs the same filtering itself, factor the predicate out and reuse — until then, do not destabilise its callers.

#### Polling interval

Pick **2 seconds**. Two samples fit comfortably inside the ~5 s product budget, give the OS a moment to settle interface state on a join/leave, and are cheap (`getNetworkInterfaces()` is a JNI call into the OS; a few-millisecond cost). The exact value is implementation-tunable; it lives in code, not here.

The poll runs in a single coroutine on `Dispatchers.IO` owned by the component's scope, cancelled on `stop()`. Repeated identical samples collapse via `distinctUntilChanged` on the output flow.

#### Trade-off vs. OS-specific approaches

JNA hooks into `IP_ADAPTER_ADDRESSES` (Windows) or `route socket` / `SCNetworkReachability` (macOS via JNI) would push instead of poll, but:

- Tether's macOS target is **native, not JVM** ([`adr-macos-native-vs-jvm.md`](adr/adr-macos-native-vs-jvm.md)) — `NWPathMonitor` already covers Mac.
- Desktop JVM ships on Windows and Linux desktops, where push-style APIs differ per OS and would each need their own JNI binding.
- A 2 s poll is well inside the product budget for those platforms.

Portability wins. Revisit if the latency budget tightens.

## Wiring into discovery

The discovery component ([`discovery.md`](discovery.md)) gains a single dependency on `LocalNetworkAvailability.state` and gates its own start/stop:

| `state`       | discovery action                                          |
|---------------|-----------------------------------------------------------|
| `Unknown`     | no-op (do not start, do not stop)                         |
| `Available`   | start discovery if not running                            |
| `Unavailable` | stop discovery gracefully if running; clear `DiscoveredDevicesStore` of live entries |

"Stop gracefully" means cancel the discovery-scope `CoroutineScope` ([`discovery.md` § Cancellation and lifecycle](discovery.md#cancellation-and-lifecycle)) and release the Android `MulticastLock` and the WifiLock/WakeLock held during transfers (covered by their own owners — this layer does not touch them directly).

The wiring lives in the composition root, not in the discovery internals: discovery exposes idempotent `start()` / `stop()`; the availability stream drives them. This keeps both components independently testable.

Recovery latency: the `Unavailable → Available` transition must result in peers appearing within ~5 s ([spec § What "working" looks like](../product/features/system/wifi-availability/spec.md#what-working-looks-like)). Of that budget, the OS notification + this stream consumes its share; discovery start-up + mDNS first announce consumes the rest. Neither layer owns the whole budget — but both must stay well inside their share.

`DiscoveredDevicesStore` content on `Unavailable`: drop live entries; **keep** paired-offline entries (their source is the trusted-device store, not discovery). When the UI takes over with the no-local-network state, it hides the list anyway ([spec § User flows, last paragraph](../product/features/system/wifi-availability/spec.md#user-flows)) — but the data model stays clean.

## Hotspot host case

The product invariant: a device sharing its own hotspot is `Available`. Implementation strategies per platform are above; the cross-platform predicate they converge on matches the existing host-side mDNS predicate from [`discovery.md` § Host-side multi-interface bind](discovery.md#host-side-multi-interface-bind):

> at least one non-loopback, non-link-local IPv4 interface is up.

When the platform's native API (Android `NetworkCallback` on `TRANSPORT_WIFI`, iOS `NWPathMonitor` on `.wifi`) does not report the AP/hotspot interface, the implementation falls back to interface enumeration with that predicate. The fallback path is the same code on every platform that needs it.

## What this doc does *not* commit to

- **Trusted-device storage / paired-device persistence.** Out of scope per [spec § Not in this feature](../product/features/system/wifi-availability/spec.md#not-in-this-feature).
- **UI rendering of the no-local-network state.** Wording per platform, action button vs. plain instruction, visual treatment — owned by the device-list UX brief.
- **Permissions.** Manifest entries, Info.plist usage strings, runtime prompts — owned by [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md). This component assumes everything it needs is already granted.
- **Exact polling interval on Desktop JVM.** 2 s is a recommendation; the value lives in code and the implementation issue.
- **VPN handling.** Per spec, VPN-over-Wi-Fi is treated as Wi-Fi; nothing VPN-specific in the contract. If a VPN interface ever needs to be excluded, that is a follow-up.
