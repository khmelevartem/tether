# Peer Discovery

How Tether nodes find each other on a local network. Captures the layered design committed to in [adr-hotspot-discovery.md](adr/adr-hotspot-discovery.md), which was driven primarily by the phone-hotspot transfer scenario (see [`features/hotspot-transfer/spec.md`](../product/features/hotspot-transfer/spec.md)).

This is a living doc — it states what should be true of any correct implementation. Where the codebase has not yet caught up, treat the gap as an implementation issue, not a contradiction.

## Goal

From the user's standpoint: open Tether on two devices in the same physical place and see each other in the device list within a few seconds. The headline case is one phone sharing Wi-Fi and the other connected to it. The same design also covers two devices on the same home Wi-Fi, captive guest networks, and anything else that delivers a usable L3 path between the two.

From the engineering standpoint: produce a `DiscoveredDevicesStore` consistent on both sides of a pair, in any scenario where at least one direction of L3 unicast works between them.

## Layered model

Discovery runs four layers stacked top to bottom by speed and reach. Each layer feeds the **same** `DiscoveredDevicesStore`. The store is the single source of truth the UI subscribes to.

```
┌──────────────────────────────────────────────────────────────────────┐
│ Layer 1 — mDNS                                                       │
│   Primary. Always running.                                           │
│   Host side: multi-interface bind (incl. tether / AP interfaces).    │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │ feeds
┌─────────────────────────────────▼────────────────────────────────────┐
│ Layer 2 — /hello rendezvous                                          │
│   Triggered whenever any layer discovers a new peer.                 │
│   Symmetrizes one-way visibility.                                    │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │ feeds
┌─────────────────────────────────▼────────────────────────────────────┐
│ Layer 3 — fallback channels (started if store stays empty for N s)   │
│   3a HTTP-subnet-scan: POST /hello to every reachable host.          │
│   3b UDP-broadcast: limited broadcast carrying same InfoDto.         │
│   Run concurrently; both may contribute.                             │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │ feeds
┌─────────────────────────────────▼────────────────────────────────────┐
│ Layer 4 — Out-of-band pairing (QR scan + manual IP entry)            │
│   User-initiated. QR primary; manual entry for camera-less cases.    │
│   Same code path as Layer 2 — InfoDto → store upsert.                │
└──────────────────────────────────────────────────────────────────────┘
```

Layer 4 is product-level, not automatic; it appears here because it uses the same upsert path and shares contracts with the lower layers.

## Layer 1 — mDNS

Service type `_tether._tcp.`. Each device publishes its own service on the port the `FileServer` is bound to, and browses for the same type on every reachable interface.

### Host-side multi-interface bind

When a device acts as the network's access point — Android Wi-Fi hotspot, macOS Internet Sharing, Windows Mobile Hotspot, Linux `hostapd` — its **AP interface is not the system-default route**. A naive `JmDNS.create()` or `NsdManager.registerService` binds only to the default interface and misses the AP. The implementation enumerates non-loopback, non-link-local IPv4 interfaces and either runs an mDNS instance per interface (JmDNS) or, where the system API does not expose this control (Android `NsdManager`), substitutes a JmDNS-based implementation when the AP role is detected.

Network changes (interface up/down, new IP on existing interface) re-trigger enumeration; stale instances are torn down and rebuilt.

### Permissions and runtime locks

OS-mediated grants (manifest entries, Info.plist usage strings, runtime prompts) are owned by [`features/system/permissions/spec.md`](../product/features/system/permissions/spec.md). No new permission is introduced by host-side multi-interface mDNS — the existing mDNS grants cover it on every platform.

Two runtime locks are worth calling out here because they are easy to forget in code review:

- **Android `WifiManager.MulticastLock`** held while the host-side AP-interface mDNS instance is active; released when discovery stops. Without the lock, the OS filters out multicast packets to save battery.
- **iOS multicast network entitlement** (already filed for mDNS) covers both inbound and outbound UDP-broadcast on the fallback path — see Layer 3b below; no separate entitlement is needed.

### Self-suppression

mDNS announces propagate back to the announcing host. The implementation filters by the registered service name compared to the device's own name (matching the existing Android NSD pattern: track the *resolved* name returned by `onServiceRegistered`, since the OS may modify it). Once stable identity exists (see [Identity](#identity-and-self-suppression)), self-suppression migrates to the fingerprint.

## Layer 2 — `POST /hello` rendezvous

A single HTTP endpoint that lets one side tell another "here I am". The HTTP request itself is the announcement.

### Endpoint

```
POST /hello
Content-Type: application/json

{
  "alias":       string,           // user-visible device name
  "fingerprint": string,           // see Identity below; interim placeholder allowed
  "port":        integer,          // sender's FileServer port
  "deviceType":  "mobile"|"desktop"|"web"|"headless"|"server",
  "version":     integer           // protocol version, currently 1
}
```

Response: `200 OK` on accepted upsert. Body content is reserved; current responders return an empty JSON object.

The sender's IP is taken from the TCP connection's remote address — clients never include their own IP in the body. This keeps the contract behind NATs honest and matches LocalSend's `/api/localsend/v2/register` shape so future interop is a small, scoped change.

### Trigger

The HTTP client side fires `POST /hello` to every peer that newly appears in `DiscoveredDevicesStore` and which has not yet acknowledged us, where "acknowledged" means: we have observed that peer learn our address (visible in subsequent mDNS announces, or — pragmatically — we have ever received any HTTP request from them).

The endpoint is idempotent; repeated calls upsert. Mis-firing is harmless. The cost of a redundant `/hello` is one HTTP round-trip on a LAN.

### Receiver behaviour

On receipt: build a `Device` from `(remoteAddress, body.port, body.alias, body.fingerprint)` and upsert into `DiscoveredDevicesStore`. The upsert path is identical to the one mDNS resolves into — entries are not tagged by discovery source.

### Self-suppression

Drop `POST /hello` whose `fingerprint` matches the device's own. Until stable fingerprints exist, drop by `(alias, remoteAddress, port)` matching the device's own announced tuple, with an explicit code-side TODO referencing [#11](https://github.com/khmelevartem/tether/issues/11).

## Layer 3a — HTTP-subnet-scan fallback

Activated when `DiscoveredDevicesStore` stays empty for a configurable window after Layers 1–2 are running (current default: small number of seconds; the exact value is implementation choice and lives in the implementation issue, not here).

Enumerate every connected non-loopback IPv4 interface, compute its `/24` (or smaller) subnet from the interface's IP and netmask, and `POST /hello` to each reachable address on a small port set. The port set is whatever set of well-known Tether ports the implementation reserves; today the receive port is OS-assigned, so the scan port is the well-known UDP/TCP rendezvous port that Layer 3b also uses.

Scan is bounded: one pass per fallback window, with concurrency capped to avoid flooding the LAN. Subsequent passes happen only after the store empties again or after a fresh user retry.

### Why HTTP scan rather than rely on broadcast alone

Some networks (corporate APs with client isolation, certain captive setups) drop broadcast and multicast but permit unicast TCP between clients in the same subnet. LocalSend documents this empirically; the choice carries over.

## Layer 3b — UDP-broadcast fallback

Listener bound on a reserved UDP port on every interface. Pings sent as limited broadcast (`255.255.255.255:<port>`) on each connected interface carry the same `InfoDto` payload as `/hello`.

A host receiving a broadcast ping replies by `POST /hello` (Layer 2) over TCP to the sender — the UDP packet's purpose is purely to elicit the rendezvous; the rendezvous payload is the source of truth. This keeps state outside UDP entirely.

### Why UDP-broadcast in addition to HTTP-scan

Some networks do the opposite filtering — broadcast passes (it is part of normal DHCP behaviour, which APs almost universally allow) but unicast scans are throttled or blackholed by switching hardware. The two fallbacks are complementary, not redundant; running both makes us tolerant of either filtering style.

### Apple platforms

UDP-broadcast reuses the multicast entitlement already covered for mDNS — see Layer 1's "Permissions and runtime locks" note above.

## Layer 4 — Out-of-band pairing (QR scan + manual IP entry)

When automatic discovery fails, the user is offered a way to introduce the two devices without typing.

### QR scan (primary)

One device displays a QR code that encodes its own `InfoDto` (`alias`, `fingerprint`, `port`, `deviceType`, `version`) and host IP. The other scans it with the in-app scanner. The decoded payload is fed into the same upsert path as an inbound `POST /hello` — the resulting peer appears in the device list like any other, and the two sides exchange `/hello` immediately so both ends symmetrise.

QR is the primary out-of-band path because it is unambiguous (no transcription errors), works across language and keyboard layouts, and is the same gesture a user already knows from Wi-Fi password sharing.

### Manual IP entry (fallback for cases without a camera)

The user types a host (and optionally a port — defaulting to the well-known port) in a dedicated surface; the app issues `POST /hello` to that address. The typical case is two desktops where neither has a convenient camera to scan the other's QR; less common but real, so manual entry stays first-class.

### Recent peers

A **recent peers** list surfaces previously paired devices for one-tap reconnect, regardless of how they were originally discovered (mDNS, rendezvous, fallback, QR, manual). All paired devices stay in this list; entries age out only on explicit user removal or when pairing is forgotten on either side. The list is local to one device and never synced anywhere.

In the out-of-band entry UI, recent peers appear as a **secondary** section below the QR scanner and manual-entry field — the input is primary, the history is one tap away.

### Why this is Layer 4

Any combination of failure modes Layers 1–3 cannot overcome, the user works around with a scan or a typed address. This layer is the universal escape hatch.

## Identity and self-suppression

The `fingerprint` field in `/hello` and `InfoDto` carries a stable device identity. Its target is the EC P-256 public key fingerprint produced by [Pairing (#11)](https://github.com/khmelevartem/tether/issues/11) — the same identity used by [channel encryption](../product/security.md#channel-encryption) and pairing.

Until pairing identity lands, the field carries a per-install random opaque string sufficient for self-suppression but not for trust. No code path treats it as authentication; trust gating remains pairing. The interim string is regenerated on app reinstall, matching the user-visible "reinstall produces a new identity" behaviour of pairing.

This dependency is reflected as a `TODO` in the discovery implementation referencing [#11](https://github.com/khmelevartem/tether/issues/11). It is not reflected in this document beyond this section, because the *contract* of the `fingerprint` field does not change between interim and final identity — only the source.

## Liveness and TTL

mDNS provides `serviceLost` notifications (NSD, Bonjour); JmDNS surfaces equivalent events. mDNS-discovered peers are removed from the store when their announce times out.

Peers learned via `/hello` or fallback channels do not have a built-in expiry. They are kept while the device has any reason to believe them present:

- **Active reuse extends life.** A successful `GET /health` or any other request to the peer resets a lightweight last-seen timestamp.
- **Idle expiry.** After a configurable idle window (no successful contact and no recent rediscovery via any layer), the peer is dropped from the store. The user can recover it by triggering rediscovery (any layer) or by selecting it from the recent-peers list (Layer 4).

The idle window is intentionally generous — peers reappear cheaply, and stale entries are a smaller cost than flicker.

## Trust

Discovery is unauthenticated by design across every layer. Any device on a reachable subnet can announce itself over mDNS today; rendezvous, subnet-scan, and broadcast do not widen this surface — they only diversify how an announcement reaches us.

Trust is established exclusively at [pairing](../product/security.md#pairing-flow) time, with the PIN comparison and SPKI-pinned TLS handshake that follow. No file moves between two devices until they have completed pairing. Discovery's job is to populate the device list; the list itself is not a trust claim.

## Cross-layer concerns

### Contracts that span all layers

- `InfoDto` shape (`alias`, `fingerprint`, `port`, `deviceType`, `version`) is identical in `/hello` and in UDP-broadcast payloads. A single deserializer; layers do not see each other.
- All layers feed `DiscoveredDevicesStore` via the same upsert path. Source of discovery is not stored.
- All HTTP requests originating from discovery (`/hello` outgoing) use the existing `FileClient`-derived HTTP client. No new HTTP stack.

### Cancellation and lifecycle

Discovery is a single component started and stopped with the app. Inside, every layer is a coroutine (or set of coroutines) owned by a discovery-scope `CoroutineScope` cancelled on `stop()`. Layer 3 fallbacks observe `DiscoveredDevicesStore` and start themselves; nothing in the UI orchestrates them.

### Source-set placement

Per [modules.md](modules.md), discovery code lives in `commonMain` wherever possible. Concrete placement:

- mDNS implementation has per-platform `actual`s (NSD, JmDNS, NSNetService) and stays as today.
- `/hello` endpoint, client, subnet-scan worker, UDP broadcast listener/sender, manual-entry view model: `commonMain` over Ktor server / Ktor sockets, which now publish for every Tether target (see [adr-apple-fileserver-engine.md](adr/adr-apple-fileserver-engine.md)).

## What this doc does *not* commit to

- **Exact timing constants.** Fallback-activation window, idle-expiry window, scan concurrency. These are implementation choices and live in the implementation issue and code, not here.
- **Exact port number for the rendezvous UDP/TCP fixed port.** A well-known port is chosen in the implementation issue; revisiting it is a small follow-up, not an ADR.
- **Wire-protocol-level interop with LocalSend.** The `/hello` payload follows the LocalSend `/api/localsend/v2/register` shape, but is not declared compatible. Compatibility would warrant its own ADR.
