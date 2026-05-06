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
| HTTP server | Ktor (CIO engine, JVM-only) | Same async/coroutine model as the rest of the stack; simpler than embedded Jetty/Netty for our use; no Native publication needed since the server side is currently JVM-only |
| HTTP client | Ktor (CIO) | Shared client across all targets; common API, no per-platform glue |
| Service discovery | mDNS, per-platform | Android NSD (`androidMain`), JmDNS (`desktopMain` for Windows + Linux), NSNetService via `appleMain` for iOS + macOS. mDNS is the only cross-platform option that's installed and reachable on all OSes by default |
| Serialization | kotlinx.serialization (JSON) | Multiplatform, compile-time, no reflection. Used for protocol messages in `protocol/Device.kt` |
| CLI | Clikt (JVM only) | Argument parsing for the desktop debug runner (`--name`, `--port`) |
| Build | Gradle 8 + Kotlin 2.x, Java 21 (Temurin) | Configuration cache and build cache enabled |

For implementation-side guidance — module layout, layering principles, and DI rules — see [`docs/engineering/`](../engineering/README.md).

## Architecture Decisions

### Every node is both client and server

**Decision:** Each running Tether instance hosts a Ktor HTTP server on a random free port and is also an HTTP client to other peers.

**Why:** The product is symmetric — any device can send to any device. A client/server split would require a designated host, which contradicts the discovery model and the home-network use case.

**Tradeoff:** Ktor's server modules are JVM-only — `ktor-server-*` has no Kotlin/Native publication. This is a real, load-bearing constraint, not a footnote. Android and Desktop receive via Ktor CIO from `jvmMain`; **iOS / macOS still cannot receive** — that's the remaining MVP gap, tracked separately.

#### Options on the table

1. **Per-platform server `actual` implementations.** Common `expect class FileServer` with platform-specific implementations:
   - Desktop (Windows/Linux) + Android: Ktor CIO, shared via a `jvmMain` intermediate source set. Ktor CIO publishes an Android artifact and runs on ART — no separate library needed.
   - iOS / macOS: GCDWebServer via cinterop, or build on top of Apple's `Network.framework`.

   *Pros:* Ktor on four of five targets (only Apple needs a different implementation), one server codebase for the `jvmMain` family.
   *Cons:* still two codebases for one role (`jvmMain` vs Apple Native), cinterop work for Apple targets.

2. **Custom minimal HTTP server in `commonMain` over `kotlinx-io` / sockets.** Roll our own ~200-500-line HTTP server. We control exactly which features we use (we don't need most of HTTP).

   *Pros:* one codebase for all platforms, no cinterop, no per-platform bugs.
   *Cons:* writing HTTP is famously full of corner cases (chunked encoding, headers, keep-alive). Real risk of subtle interop bugs against curl / browsers / future tooling.

3. **Wait for / track `ktor-server` Kotlin/Native.** The Ktor team has been working toward Native server support. If a stable release exists by the time we reach MVP, prefer it.

   *Pros:* same codebase as JVM, leverages Ktor's correctness.
   *Cons:* timing is out of our hands; cannot block MVP on it.

4. **Drop the symmetry — one direction per pair.** E.g. "phone always sends, laptop always receives." Rejected: contradicts the "any device sends to any device" principle in [vision.md](vision.md).

**Direction (chosen, implemented in #34/#35):** option 1 with the `jvmMain` intermediate source set. Ktor CIO server lives in `jvmMain` and is shared between Android and Desktop. Android hosts it inside a foreground `Service` so the peer stays reachable when the app is backgrounded. Apple targets still need their own `actual` — tracked as a follow-up. If Ktor Native server lands stable before we start the Apple side, option 3 becomes preferable for Apple targets.

#### Source set layout (target state)

```
commonMain
├── jvmMain      ← Ktor CIO server (Android + Desktop)
│   ├── androidMain  ← NSD discovery
│   └── desktopMain  ← JmDNS discovery, CLI (Windows + Linux)
└── appleMain    ← NSNetService discovery, server actual (iOS + macOS Native)
    ├── iosMain
    └── macosMain
```

Linux ships in `desktopMain` alongside Windows — same JVM target, same APIs. No separate Native target for Linux.

### mDNS over BLE / Wi-Fi Direct / hand-typed addresses

**Decision:** Discovery uses mDNS (Bonjour) on every platform.

**Why:** Works without pairing the underlying transport, requires no special permissions on most platforms, runs over the existing Wi-Fi the user is already on. BLE has range/throughput issues; Wi-Fi Direct is poorly supported across our targets; hand-typing IPs is a non-starter for the audience (see [audience.md](audience.md)).

**Tradeoff:** Some networks block mDNS — guest Wi-Fi, enterprise APs with client isolation, certain captive portals. Acceptable: the primary scenario is home Wi-Fi, where mDNS works.

### Ktor for both server and client

**Decision:** Use Ktor on both sides instead of mixing OkHttp / raw sockets / platform HTTP stacks.

**Why:** One mental model, coroutine-native, multiplatform client. Streaming uploads/downloads are first-class.

**Tradeoff:** Ktor server's JVM-only nature creates the asymmetry noted above.

### Streaming transfers from day one

**Decision:** Files transfer as streams, not as memory-buffered byte arrays, from MVP.

**Why:** Users have multi-GB videos. Buffering in memory is a non-starter on mobile. Adding streaming later would mean rewriting the transfer code path.

**Tradeoff:** Slightly more complex than a buffered first version.

### Compose Multiplatform on every UI

**Decision:** Single UI codebase in `commonMain`. No SwiftUI / UIKit / native Android XML layouts.

**Why:** Reinforces the single-visual-language choice, halves UI work, and keeps the four platforms genuinely at parity instead of one being "the real one" and the others being ports.

**Tradeoff:** Compose on macOS is experimental; Compose on iOS is officially supported but newer than on Android. We accept potential rough edges in exchange for parity.

## Constraints

- **Same Wi-Fi network required.** mDNS doesn't cross subnets without explicit configuration.
- **mDNS may be blocked** on guest networks, captive portals, and enterprise APs with client isolation. Tether should detect and explain, not silently fail.
- **iOS Local Network permission.** Required for discovery to work; user will see the system prompt on first run.
- **Android `INTERNET` and local-network permissions.** Standard, but worth being explicit.
- **macOS:** Apple Silicon only (`macosArm64`). Intel support (`macosX64()`) is cheap to add — one line in `kotlin { ... }`, plus a small permanent build/test/release tax (extra compile cycle, extra artifact in releases). Skipped now because the Intel Mac population among target users is small and shrinking; revisit when an actual user reports it.
- **Java 21 (Temurin)** for Windows (JVM) and the build itself.
- **Compose on macOS is experimental** — accepted; flagged in build configuration.
- **Ktor server is JVM-only (Native).** No `ktor-server-*` Kotlin/Native publication exists. Ktor CIO *does* run on Android (ART-compatible). Apple targets (iOS, macOS) need a different server mechanism — see options above.
