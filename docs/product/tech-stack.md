# Tech Stack

## Platform Targets

| Platform | Status | Notes |
|----------|--------|-------|
| Android | In progress | mDNS via Android NSD; Ktor CIO server hosted in a background-safe foreground process; Compose UI |
| iOS | In progress | KMP target on iOS device and simulator; Ktor CIO server (Apple actual) — see [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md); discovery via NSNetServiceBrowser + NSNetService |
| macOS | In progress | Ships through the Desktop JVM target, packaged as a native `.app`/`.dmg`. Discovery on macOS uses JNA-Bonjour, not JmDNS — see [`macos-mdns-bonjour.md`](../knowledge/macos-mdns-bonjour.md). Why not Kotlin/Native: [`adr-macos-native-vs-jvm.md`](../engineering/adr/adr-macos-native-vs-jvm.md) |
| Windows | In progress | JVM-based via Gradle desktop target. Reference implementation. Hosts the file server and the CLI debug runner |
| Linux | Post-MVP | Ships via the Desktop JVM target after MVP. Not a maybe — a deferred yes |

## Core Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| Shared code | Kotlin Multiplatform | One codebase across Android, iOS, macOS, Windows, Linux. Cross-platform parity is part of the product (see [vision.md](vision.md)) |
| UI | Compose Multiplatform | Single UI tree across all supported KMP UI targets (Android, iOS, Desktop JVM). macOS UI is delivered through the Desktop JVM tree — see [adr-macos-native-vs-jvm.md](../engineering/adr/adr-macos-native-vs-jvm.md) |
| HTTP server | Ktor (CIO engine) | Single engine across JVM (Android + Desktop) and Native (iOS). Rationale in [adr-network-stack.md](../engineering/adr/adr-network-stack.md); Apple engine choice in [adr-apple-fileserver-engine.md](../engineering/adr/adr-apple-fileserver-engine.md) |
| HTTP client | Ktor (CIO) | Shared client across all targets; common API, no per-platform glue. See [adr-network-stack.md](../engineering/adr/adr-network-stack.md) |
| Service discovery | mDNS, per-platform | Android NSD on Android, JmDNS on Windows / Linux, JNA-Bonjour on macOS, NSNetServiceBrowser + NSNetService on iOS. mDNS is the primary mechanism — installed and reachable on all OSes by default. Layered fallbacks for hotspot and multicast-hostile networks live in [adr-hotspot-discovery.md](../engineering/adr/adr-hotspot-discovery.md) |
| Serialization | kotlinx.serialization (JSON) | Multiplatform, compile-time, no reflection. Used for protocol messages |
| CLI | Clikt (JVM only) | Argument parsing for the desktop debug runner (`--name`, `--port`) |
| Build | Gradle 8 + Kotlin 2.x, Java 21 (Temurin) | Configuration cache and build cache available |

For implementation-side guidance — module layout, layering principles, and DI rules — see [`docs/engineering/`](../engineering/README.md).

## Constraints

- **Local network only.** Tether moves files between devices on the same local network; it does not relay through the internet or bridge across routed boundaries. A phone hotspot counts as a local network, so the two devices on it can reach each other. See [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md).
- **Adverse networks degrade gracefully, not silently.** On networks that block automatic discovery, the user gets a manual escape hatch rather than a dead end. The failure mode and that fallback are specified in [`features/hotspot-transfer/spec.md`](features/hotspot-transfer/spec.md); the engineering model is in [`discovery.md`](../engineering/discovery.md).
- **Per-platform permissions.** Tether requests OS permissions for discovery, notifications, file save, and inbound network; specifics live in [`features/system/permissions/spec.md`](features/system/permissions/spec.md).
- **macOS: Apple Silicon only.** The distributed `.dmg` bundles an ARM JRE (built on Apple-Silicon CI), so it does not run on Intel Macs; an Intel build is deferred until a user reports the need. See [adr-macos-native-vs-jvm.md](../engineering/adr/adr-macos-native-vs-jvm.md).
