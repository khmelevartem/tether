# Tech Stack

## Platform Targets

| Platform | Status | Notes |
|----------|--------|-------|
| Android | In progress | mDNS via Android NSD; Ktor CIO server hosted in a background-safe foreground process; Compose UI |
| iOS | In progress | KMP target on iOS device and simulator; Ktor CIO server (Apple actual) — see [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md); discovery via NSNetServiceBrowser + NSNetService |
| macOS | In progress | Ships through the Desktop JVM target (`packageReleaseDistributionForCurrentOS` → `.app`/`.dmg`). Discovery on macOS uses JNA-Bonjour, not JmDNS — see [`macos-mdns-bonjour.md`](../knowledge/macos-mdns-bonjour.md). Why not Kotlin/Native: [`adr-macos-native-vs-jvm.md`](../engineering/adr/adr-macos-native-vs-jvm.md) |
| Windows | In progress | JVM-based via Gradle desktop target. Reference implementation. Hosts `FileServer` and the CLI debug runner |
| Linux | Post-MVP | Ships via the Desktop JVM target after MVP. Not a maybe — a deferred yes |

## Core Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Shared code | Kotlin Multiplatform | One codebase across Android, iOS, macOS, Windows, Linux. See [vision.md](vision.md) |
| UI | Compose Multiplatform | Single UI tree across all supported KMP UI targets (Android, iOS, Desktop JVM). macOS UI is delivered through the Desktop JVM tree — see [adr-macos-native-vs-jvm.md](../engineering/adr/adr-macos-native-vs-jvm.md) |
| HTTP server | Ktor (CIO engine) | Single engine across JVM (Android + Desktop) and Native (iOS). Rationale in [adr-network-stack.md](../engineering/adr/adr-network-stack.md); Apple engine choice in [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md) |
| HTTP client | Ktor (CIO) | Shared client across all targets; common API, no per-platform glue. See [adr-network-stack.md](../engineering/adr/adr-network-stack.md) |
| Service discovery | mDNS, per-platform | Android NSD on Android, JmDNS on Windows / Linux, JNA-Bonjour on macOS, NSNetServiceBrowser + NSNetService on iOS. mDNS is the only cross-platform option installed and reachable on all OSes by default. Layered fallbacks for hotspot and multicast-hostile networks live in [adr-hotspot-discovery.md](../engineering/adr/adr-hotspot-discovery.md) |
| Serialization | kotlinx.serialization (JSON) | Multiplatform, compile-time, no reflection. Used for protocol messages |
| CLI | Clikt (JVM only) | Argument parsing for the desktop debug runner (`--name`, `--port`) |
| Build | Gradle 8 + Kotlin 2.x, Java 21 (Temurin) | Configuration cache and build cache available |

For implementation-side guidance — module layout, layering principles, and DI rules — see [`docs/engineering/`](../engineering/README.md).

## Constraints

- **Same L3 subnet required.** Discovery cannot cross routed boundaries without explicit configuration. A hotspot link counts as one subnet — host and clients can find each other; two devices on opposite sides of a router or a corporate VLAN cannot. See [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md).
- **Adverse networks degrade gracefully, not silently.** Guest Wi-Fi, captive portals, and client-isolated enterprise APs may drop multicast and/or broadcast. The layered discovery model routes around most of these; the user-visible failure mode and the manual-entry escape hatch are specified in [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md).
- **Per-platform permissions** for discovery, notifications, file save, and inbound network — runtime prompts and manifest declarations live in [`features/system/permissions/spec.md`](features/system/permissions/spec.md), not duplicated here.
- **macOS: Apple Silicon only** (`macosArm64` requirement at install time, delivered via the Desktop JVM target). Intel Mac support is deferred until a user reports it.
- **Java 21 (Temurin)** for the JVM targets (Android, Desktop) and the build itself.
- **Compose on macOS is experimental** — accepted; flagged in build configuration.
