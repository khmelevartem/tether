# Tech Stack & Architecture Decisions

## Platform Targets

| Platform | Status | Notes |
|----------|--------|-------|
| Android | In progress | mDNS via Android NSD; Compose UI in `androidMain` |
| iOS | Stub | KMP target wired (`iosArm64`, `iosSimulatorArm64`); discovery via NSNetService — see issue [#6](https://github.com/) |
| macOS | Basic | `macosArm64` only (Apple Silicon). Discovery shares `appleMain` with iOS |
| Windows | In progress | JVM-based via Gradle desktop target. Reference implementation. Hosts `FileServer` and the CLI debug runner |
| Linux | Excluded for MVP | Possible later via JVM target |

## Core Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Shared code | Kotlin Multiplatform | One codebase across Android, iOS, macOS, Windows. Cross-platform parity is part of the product (see [vision.md](vision.md)) |
| UI | Compose Multiplatform | Single UI tree across all four targets; matches the "single visual language" choice in [design.md](design.md). Experimental on macOS but viable for our scope |
| HTTP server | Ktor (CIO engine, JVM-only) | Same async/coroutine model as the rest of the stack; simpler than embedded Jetty/Netty for our use; no Native publication needed since the server side is currently JVM-only |
| HTTP client | Ktor (CIO) | Shared client across all targets; common API, no per-platform glue |
| Service discovery | mDNS, per-platform | Android NSD (`androidMain`), JmDNS (`jvmMain`), NSNetService via `appleMain` for iOS + macOS. mDNS is the only cross-platform option that's installed and reachable on all four OSes by default |
| Serialization | kotlinx.serialization (JSON) | Multiplatform, compile-time, no reflection. Used for protocol messages in `protocol/Device.kt` |
| CLI | Clikt (JVM only) | Argument parsing for the desktop debug runner (`--name`, `--port`) |
| Build | Gradle 8 + Kotlin 2.x, Java 21 (Temurin) | Configuration cache and build cache enabled |

## Architecture Decisions

### Every node is both client and server

**Decision:** Each running Tether instance hosts a Ktor HTTP server on a random free port and is also an HTTP client to other peers.

**Why:** The product is symmetric — any device can send to any device. A client/server split would require a designated host, which contradicts the discovery model and the home-network use case.

**Tradeoff:** Ktor server is currently JVM-only (covers Windows, but not Android/iOS). Receive flow on Android and iOS needs a different solution (likely embedded server via Kotlin/Native or platform APIs) before MVP closes. Acceptable because send/receive in MVP can ship asymmetrically per-platform during development.

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
- **macOS:** Apple Silicon only (`macosArm64`). Intel support can be added with `macosX64()` if a real need appears.
- **Java 21 (Temurin)** for Windows (JVM) and the build itself.
- **Compose on macOS is experimental** — accepted; flagged in build configuration.
- **Ktor server is JVM-only.** No `ktor-server-*` Kotlin/Native publication exists. Receiver implementation on Android and iOS needs a different mechanism.
