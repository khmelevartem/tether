# Tech Stack & Architecture Decisions

## Platform Targets

| Platform | Status | Notes |
|----------|--------|-------|
| Android | In progress | mDNS via Android NSD; Ktor CIO server hosted in foreground Service; Compose UI in `androidMain` |
| iOS | Stub | KMP target wired (`iosArm64`, `iosSimulatorArm64`); discovery via NSNetService — see issue [#6](https://github.com/) |
| macOS | Basic | `macosArm64` only (Apple Silicon). Discovery shares `appleMain` with iOS |
| Windows | In progress | JVM-based via Gradle desktop target. Reference implementation. Hosts `FileServer` and the CLI debug runner |
| Linux | Post-MVP | Will ship via JVM target after MVP. Not a maybe — a deferred yes |

## Core Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Shared code | Kotlin Multiplatform | One codebase across Android, iOS, macOS, Windows, Linux. Cross-platform parity is part of the product (see [vision.md](vision.md)) |
| UI | Compose Multiplatform | Single UI tree across all targets; matches the "single visual language" choice in [design.md](design.md). Experimental on macOS but viable for our scope |
| HTTP server | Ktor (CIO engine) | Single engine across JVM (Android + Desktop) and Native (iOS + macOS Native) since Ktor 3.0 published `ktor-server-cio` for K/N. Rationale in [adr-network-stack.md](../engineering/adr/adr-network-stack.md) |
| HTTP client | Ktor (CIO) | Shared client across all targets; common API, no per-platform glue. See [adr-network-stack.md](../engineering/adr/adr-network-stack.md) |
| Service discovery | mDNS, per-platform | Android NSD (`androidMain`), JmDNS (`desktopMain` for Windows + Linux), NSNetService via `appleMain` for iOS + macOS. mDNS is the only cross-platform option that's installed and reachable on all OSes by default |
| Serialization | kotlinx.serialization (JSON) | Multiplatform, compile-time, no reflection. Used for protocol messages in `protocol/Device.kt` |
| CLI | Clikt (JVM only) | Argument parsing for the desktop debug runner (`--name`, `--port`) |
| Build | Gradle 8 + Kotlin 2.x, Java 21 (Temurin) | Configuration cache and build cache enabled |

For implementation-side guidance — module layout, layering principles, and DI rules — see [`docs/engineering/`](../engineering/README.md).

## Architecture Decisions

### Every node is both client and server

**Decision:** Each running Tether instance hosts a Ktor HTTP server on a random free port and is also an HTTP client to other peers.

**Why:** The product is symmetric — any device can send to any device. A client/server split would require a designated host, which contradicts the discovery model and the home-network use case.

**Tradeoff:** historically Ktor's server modules were JVM-only. Since Ktor 3.0 (October 2024) `ktor-server-cio` is also published for Kotlin/Native (`iosArm64`, `iosSimulatorArm64`, `macosArm64`), and the project moved the server stack into `commonMain` accordingly — see [adr-network-stack.md](../engineering/adr/adr-network-stack.md) and [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md). The "every node is server and client" symmetry now holds on every supported target.

#### Source set layout

```
commonMain                      ← Ktor server dependencies (CIO, core, content-negotiation)
├── jvmMain                     ← Ktor CIO server actual (Android + Desktop)
│   ├── androidMain             ← NSD discovery
│   └── desktopMain             ← JmDNS discovery, CLI (Windows + Linux)
└── appleMain                   ← Ktor CIO Native server actual, NSNetService discovery (iOS + macOS)
    ├── iosMain
    └── macosMain
```

Linux ships in `desktopMain` alongside Windows — same JVM target, same APIs. No separate Native target for Linux.

### Discovery — hotspot-first, layered around mDNS

**Decision:** Discovery is built to make phone-hotspot transfer work as a first-class scenario, not an edge case. mDNS (Bonjour) is the primary mechanism on every platform; an HTTP rendezvous endpoint symmetrizes one-way visibility; HTTP-subnet-scan and UDP-broadcast act as complementary fallbacks for networks where multicast is dropped; manual IP entry is a universal escape hatch. The whole stack is designed so that "one phone shares Wi-Fi, the other connects to it" works without the user knowing what discovery is.

**Why mDNS as primary:** Works without pairing the underlying transport, requires no special permissions on most platforms, runs over the existing Wi-Fi the user is already on. It is the natural fit when both devices are on the same Wi-Fi.

**Why additional layers:** mDNS alone breaks the hotspot scenario because the AP interface (`ap0`/`wlan1`/`bridge100`/…) is not the device's default route, and naive mDNS bindings miss it. Android-as-host is the worst sub-case — `NsdManager` does not reliably reach the tether interface — and it is the most common real-world configuration. The same layers also cover networks that drop multicast (guest Wi-Fi, captive portals, client-isolated enterprise APs) and one-way visibility, but the hotspot driver is what motivates the design.

**Why not the alternatives:**
- BLE — range and throughput unfit for file transfer.
- Wi-Fi Direct and Wi-Fi Aware (NAN) — Apple exposes no public API on iOS or macOS; using them would break platform parity (see [vision.md](vision.md)). Recorded as an Android-only Pro hypothesis in [monetization.md](monetization.md).
- Hand-typed IPs as the only path — non-starter for the audience (see [audience.md](audience.md)). Acceptable as a fallback layer, not a primary one.
- Raw UDP multicast on a fixed group as the primary discovery (LocalSend's approach) — would mean rewriting working code paths to replace, not extend, mDNS, for marginal gain over adding the rendezvous endpoint on top.

**Tradeoff:** Three additional code paths beyond mDNS (rendezvous endpoint, subnet-scan worker, broadcast listener/sender). Each is small and shares `commonMain` plumbing. Identity in the rendezvous payload is interim (per-install random) until pairing identity ([#11](https://github.com/khmelevartem/tether/issues/11)) lands.

The engineering layout, contracts, and runtime behaviour live in [`docs/engineering/discovery.md`](../engineering/discovery.md); the decision rationale and rejected alternatives in [`adr-hotspot-discovery.md`](../engineering/adr/adr-hotspot-discovery.md). The user-visible side — hotspot scenarios, troubleshooting, manual entry, recent peers — is its own feature spec at [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md).

### Ktor for both server and client

**Decision:** Use Ktor on both sides instead of mixing OkHttp / raw sockets / platform HTTP stacks.

**Why:** One mental model, coroutine-native, multiplatform on both sides. Streaming uploads/downloads are first-class.

### Streaming transfers from day one

**Decision:** Files transfer as streams, not as memory-buffered byte arrays, from MVP.

**Why:** Users have multi-GB videos. Buffering in memory is a non-starter on mobile. Adding streaming later would mean rewriting the transfer code path.

**Tradeoff:** Slightly more complex than a buffered first version.

### Compose Multiplatform on every UI

**Decision:** Single UI codebase in `commonMain`. No SwiftUI / UIKit / native Android XML layouts.

**Why:** Reinforces the single-visual-language choice, halves UI work, and keeps the four platforms genuinely at parity instead of one being "the real one" and the others being ports.

**Tradeoff:** Compose on macOS is experimental; Compose on iOS is officially supported but newer than on Android. We accept potential rough edges in exchange for parity.

## Constraints

- **Same L3 subnet required.** Discovery cannot cross routed boundaries without explicit configuration. A hotspot link counts as one subnet — host and clients can find each other; two devices on opposite sides of a router or a corporate VLAN cannot.
- **Adverse networks degrade gracefully, not silently.** Guest Wi-Fi, captive portals, and client-isolated enterprise APs may drop multicast and/or broadcast. The layered discovery model (see above) routes around most of these; the user-visible failure mode and the manual-entry escape hatch are specified in [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md).
- **iOS Local Network permission.** Required for discovery (mDNS and UDP-broadcast both fall under it); user will see the system prompt on first run.
- **Android `INTERNET` permission** and **`NEARBY_WIFI_DEVICES`** (Android 13+) for host-side multi-interface discovery, plus `WifiManager.MulticastLock` while host-side mDNS is active on the AP interface.
- **macOS:** Apple Silicon only (`macosArm64`). Intel support (`macosX64()`) is cheap to add — one line in `kotlin { ... }`, plus a small permanent build/test/release tax (extra compile cycle, extra artifact in releases). Skipped now because the Intel Mac population among target users is small and shrinking; revisit when an actual user reports it.
- **Java 21 (Temurin)** for Windows (JVM) and the build itself.
- **Compose on macOS is experimental** — accepted; flagged in build configuration.
