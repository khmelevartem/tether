# Engineering Documentation

Implementation-side guidance: how the code is organized, what principles apply, and what rules new code must satisfy.

Product-side docs (vision, audience, features) live in [`docs/product/`](../product/README.md).

## Sections

- [Architecture Principles](architecture-principles.md) — Clean Architecture as a principle, not dogma. What applies and what is explicitly skipped.
- [Modules](modules.md) — current monolith, target module split, and the triggers that move a piece of code into its own module.
- [Dependency Injection](dependency-injection.md) — DI strategy now (manual composition root) and later (Metro). Concrete rules for new code.
- [Presentation Layer](presentation-layer.md) — Decompose-based components; how to write them, subscribe from Compose, and test.
- [Peer Discovery](discovery.md) — layered model (mDNS + rendezvous + HTTP-scan + UDP-broadcast + manual entry), contracts, identity, liveness.
- [Local-network availability](wifi-availability.md) — single common stream gating discovery and the no-local-network UI; per-platform sources (Android `NetworkCallback`, Apple `NWPathMonitor`, Desktop `NetworkInterface` polling).
- [UI Style Guide](ui-style-guide.md) — token tables, `TetherTheme` rule, Tabler Icons usage, motion specs, accessibility checklist.
- [UI Brand Mark](ui-brand-mark.md) — `•—•` geometry, animation states, where the mark appears, and design rationale (why a line, not a knot or arrow).
- [Testing](testing.md) — структура тестов по source sets, стиль (`runTest`/виртуальное время), особенности Apple-таргетов.
- [Logging](logging.md) — KydraLog как единый KMP-фасад; именование `Tether.<Subsystem>`, уровни, DEBUG-гейтинг по платформам, тестовая тишина, политика sensitive-данных.

## Writing style for these guides

These documents codify principles, not the current shape of the codebase — they should age slowly.

- **Code examples on abstract types**, not on real project classes. Examples pinned to concrete names rot at every rename.
- **Don't restate what the code already shows** (hierarchies, signatures, source set layout). The code is the one source of truth that drifts last; link to it instead of copying.
- **Lead with the rule.** Rationale and examples follow.

A starter skeleton with common sections lives in [`_template.md`](_template.md). Copy it as a base for new subsystem docs.

## Architecture Decision Records

ADRs in [`adr/`](adr/) capture the *why* behind one-time architectural choices. Living docs above; ADRs are append-only history. See [`adr/README.md`](adr/README.md) for conventions.

- [Presentation & Navigation](adr/adr-presentation-and-navigation.md) — chose Decompose over Voyager / custom thin layer / Compose Navigation MP / Premo.
- [macOS target — Native over Desktop JVM](adr/adr-macos-native-vs-jvm.md) — chose `macosArm64` Kotlin/Native to share `appleMain` with iOS.
- [Visual Identity](adr/adr-visual-identity.md) — palette, typography, iconography, brand mark `•—•`; dropped Material 3 in favour of custom `TetherTheme`.
- [Network stack](adr/adr-network-stack.md) — chose Ktor CIO across all targets tactically; engine swaps under TLS forced upstream. Living-doc сторона — [`transport.md`](transport.md).
- [Apple FileServer engine](adr/adr-apple-fileserver-engine.md) — chose Ktor CIO Native over hand-rolled HTTP for the Apple-side `FileServer.actual`.
- [Channel encryption](adr/adr-channel-encryption.md) — chose TLS-with-paired-key-pinning + SecureTransport on Apple. Includes 2026-05-16 Amendment with implementation-plan corrections.
- [Hotspot-first Discovery](adr/adr-hotspot-discovery.md) — chose layered mDNS + `/hello` rendezvous + HTTP-subnet-scan + UDP-broadcast over raw-multicast-as-primary or Wi-Fi Direct / NAN. Motivated primarily by phone-hotspot transfer.
- [Logging — KydraLog](adr/adr-logging-kydra.md) — chose KydraLog as the single KMP logging facade; SLF4J handled via `slf4j-simple` on JVM; per-platform DEBUG gates.
- [Screenshot testing](adr/adr-screenshot-testing.md) — chose Roborazzi + ComposablePreviewScanner on the Android target via Robolectric for headless `@Preview`-to-PNG rendering in the agent loop.

