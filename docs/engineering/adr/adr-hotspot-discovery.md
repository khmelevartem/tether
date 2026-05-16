# Hotspot-first discovery — layered mDNS + rendezvous + HTTP-scan + UDP-broadcast

**Status:** Accepted — 2026-05-17
**Issue:** [#170](https://github.com/khmelevartem/tether/issues/170)

## Context

The motivating scenario is **transfer over one device's Wi-Fi hotspot**: a user wants to send a file to someone who has no shared Wi-Fi available, so one of them turns on their phone's hotspot and the other connects to it. This is the dominant mobile-first case — a phone in the AP role is more common in practice than two devices joined to the same home Wi-Fi. The corresponding product feature is [`features/hotspot-transfer/spec.md`](../../product/features/hotspot-transfer/spec.md).

[docs/product/tech-stack.md](../../product/tech-stack.md) commits Tether to mDNS for peer discovery. mDNS works on the hotspot's L3 segment (host and clients share a subnet — the AP's own `192.168.x.0/24`). It does **not** work reliably out of the box because the AP interface is not the device's default route, and naive mDNS bindings miss it:

- **Android-as-host (worst sub-case).** `NsdManager` binds to the system-default network and does not reliably announce or browse on the tether interface (`ap0`/`wlan1`). This is the case LocalSend has not solved either — see [their #270](https://github.com/localsend/localsend/issues/270).
- **Desktop-as-host.** JmDNS by default binds to the OS-default interface, not the AP interface created by Windows Mobile Hotspot / macOS Internet Sharing / `hostapd`.
- **iPhone-as-host (Personal Hotspot).** Bonjour publishes across interfaces and is usually fine, but needs verification on `bridge100`.

Two adjacent scenarios fall out of the same root design naturally, and we want them covered while we are in the area:

- **Multicast-blocked networks.** Guest Wi-Fi, captive portals, and enterprise APs with client isolation drop mDNS multicast outright. Not the dominant case for Tether's audience but a real one.
- **Asymmetric visibility.** One side sees the other but not vice versa — common during hotspot rollouts where mDNS responses propagate one direction but not the other.

Current mDNS-only discovery handles none of these.

## Decision drivers

| Criterion | mDNS only (today) | Raw UDP multicast as primary (LocalSend) | mDNS + rendezvous endpoint | mDNS + HTTP-subnet-scan | mDNS + UDP-broadcast | Wi-Fi Direct / NAN |
|---|---|---|---|---|---|---|
| Home Wi-Fi (baseline) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Hotspot, Desktop host | ⚠️ default-iface bug | ✅ | ✅ if either direction sees | ✅ | ✅ | ✅ |
| Hotspot, Android host | ❌ NSD on tether iface | ✅ if iface enumerated | ✅ if either direction sees | ✅ | ✅ | ✅ |
| Multicast-blocked LAN | ❌ | ❌ | ❌ alone | ✅ | ⚠️ broadcast often also blocked | ❌ different transport |
| Asymmetric visibility | ❌ | ❌ alone | ✅ symmetrizes for free | ✅ | ✅ | n/a |
| Cross-platform parity | ✅ | ✅ | ✅ | ✅ | ⚠️ iOS needs multicast entitlement | ❌ **no Apple public API** |
| New permissions required | minimal | multicast | none beyond mDNS | none | multicast/broadcast entitlement on iOS | major (Android 13+ `NEARBY_WIFI_DEVICES`, Android-only) |
| Trust surface increase | baseline | same | same — pairing remains gate | same — pairing remains gate | same | new transport surface |
| Implementation complexity | already shipped | replace mDNS code paths entirely | one HTTP endpoint + one client trigger | one HTTP scan worker | one UDP listener + sender | platform-specific (Android only) |

## Decision

**Layered discovery.** mDNS remains the primary mechanism. Three additional layers stack on top, each closing a class of failures the previous layer cannot:

| Layer | Mechanism | Closes |
|---|---|---|
| 1 | mDNS (NSD / JmDNS / NSNetService) with multi-interface bind on the host side | Home Wi-Fi, hotspot when default-interface bind happens to work |
| 2 | `POST /hello` rendezvous endpoint — when one side learns of another (through any layer), it POSTs its own `InfoDto` to that peer | Asymmetric visibility; gives both sides the same peer list from a single one-way contact |
| 3a | HTTP-subnet-scan fallback — when no peers learned for *N* seconds, POST `/hello` to every reachable host on each connected subnet | Multicast-blocked networks where unicast TCP is permitted |
| 3b | UDP-broadcast fallback — limited broadcast `255.255.255.255:<port>` carrying the same `InfoDto` payload | Networks where broadcast survives but multicast does not |
| 4 | Manual IP entry + recent peers list | Universal escape hatch — any failure mode, including ones we have not anticipated |

Layer 3a and 3b run independently and complementarily; they are not redundant — they cover different filtering behaviours seen in real APs. Order and concurrency of activation is an engineering detail captured in [docs/engineering/discovery.md](../discovery.md).

The `/hello` payload follows the field shape of LocalSend's [`POST /api/localsend/v2/register`](https://github.com/localsend/protocol) — `alias`, `fingerprint`, `port`, `deviceType`, `version`. This costs nothing now and leaves the door open to future interop without committing to it.

## Considered and rejected

### Raw UDP multicast as primary (drop mDNS)

LocalSend's design: send a UDP packet to a fixed multicast group (`224.0.0.167:53317`) with the device info, listeners reply over HTTP. mDNS is only declared on Apple to satisfy iOS Local Network permission gating.

Rejected for Tether because mDNS already works in our codebase, ships under standard OS APIs (`NsdManager`, `NSNetService`) with no fixed group choice that could conflict with other apps, and is the natural primary mechanism for the home-Wi-Fi case which still dominates. Replacing it would be a large rewrite for marginal gain over adding the rendezvous endpoint on top.

### Wi-Fi Direct / Wi-Fi Aware (NAN)

Wi-Fi Alliance peer-to-peer standards. Could enable discovery and transfer with no shared Wi-Fi at all (truly offline, no router, no hotspot).

Rejected as a primary mechanism: **Apple exposes no public API for either on iOS or macOS.** Using them would break cross-platform parity, which is a product principle ([vision.md](../../product/vision.md)). On Android they would work but produce an Android-only feature surface — an asymmetric capability inconsistent with Tether's promise. Recorded as a Pro-tier hypothesis (Android↔Android offline mode) in [monetization.md](../../product/monetization.md), not a discovery-layer choice.

### Manual entry only (no automatic fallback)

Cheap and universal. Rejected because Tether's audience ([audience.md](../../product/audience.md)) cannot be expected to type IP addresses. Manual entry is an escape hatch for failure cases, not a primary discovery path.

### Drop the symmetry — designate one side as initiator

Wired into LocalSend's API split (upload mode vs download mode). Rejected: the symmetry decision in [tech-stack.md](../../product/tech-stack.md#every-node-is-both-client-and-server) is load-bearing for the product.

## Consequences

**Positive**
- Closes the hotspot scenario as a first-class user case through a single product spec ([hotspot-transfer/spec.md](../../product/features/hotspot-transfer/spec.md)) and a coherent engineering surface, with adjacent failure classes (multicast-blocked, asymmetric) covered by the same layers.
- Layers degrade gracefully: home Wi-Fi continues to be served by mDNS alone; users in adverse networks pay a few seconds of fallback time but still succeed.
- Android-hotspot-as-host — which LocalSend has not solved as of [their #270](https://github.com/localsend/localsend/issues/270) — becomes a Tether differentiator if Layer 1's host-side multi-interface bind work lands cleanly.
- `/hello` contract leaves a low-cost path to LocalSend interop.

**Negative**
- Three additional code paths to maintain on the discovery surface (rendezvous endpoint, subnet-scan worker, UDP listener/sender). Each is small and shares the existing discovered-peers upsert path.
- Identity in `/hello` cannot be a stable fingerprint until [#11 — Pairing UI](https://github.com/khmelevartem/tether/issues/11) lands the keypair flow. Interim identity is per-install random.
- UDP-broadcast on iOS reuses the multicast network entitlement already required by mDNS — no new entitlement filing.
- Android 13+ requires `NEARBY_WIFI_DEVICES` permission for host-side multi-interface mDNS work.

**Neutral**
- Trust model is unchanged. Any device on a reachable subnet could already announce itself over mDNS; subnet-scan and broadcast do not widen this surface. Pairing remains the trust gate before any file moves. See [security.md](../../product/security.md).

## When to revisit

- If we add a transport that does not require a shared L3 subnet (e.g. Bluetooth, Wi-Fi Aware as Pro Android↔Android), the layer model accommodates it as a new Layer with its own row in the table; the ADR remains valid.
- If LocalSend interop becomes a stated goal, the `/hello` contract elevates from "field-shape compatible" to a versioned protocol commitment — that warrants its own ADR.
- If a Layer 3 fallback proves dead weight in real-world telemetry (always wins or always loses), it can be retired with a small follow-up ADR rather than reopening this one.
