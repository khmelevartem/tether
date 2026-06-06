# Engineering Documentation

Implementation-side guidance: how the code is organized, what principles apply, and what rules new code must satisfy.

Product-side docs (vision, audience, features) live in [`docs/product/`](../product/README.md).

## Sections

- [Architecture Principles](architecture-principles.md) — Clean Architecture as a principle, not dogma. What applies and what is explicitly skipped.
- [Layering](layering.md) — the four layers (UI / Presentation / Domain / Data): what each owns, may import, must not import, and how they talk to neighbours.
- [Modules](modules.md) — current monolith, target module split, and the triggers that move a piece of code into its own module.
- [Dependency Injection](dependency-injection.md) — DI strategy now (manual composition root) and later (Metro). Concrete rules for new code.
- [Presentation Layer](presentation-layer.md) — Decompose-based components; how to write them, subscribe from Compose, and test.
- [Peer Discovery](discovery.md) — layered model (mDNS + rendezvous + HTTP-scan + UDP-broadcast + manual entry), contracts, identity, liveness.
- [File transfer wire contract](file-transfer-wire.md) — `POST /upload?name=<relative-path>` shape, two-layer path sanitization (lexical in route + canonical-realisation in `UploadStorage`), per-platform storage seam.
- [Local-network availability](wifi-availability.md) — single common stream gating discovery and the no-local-network UI; per-platform sources (Android `NetworkCallback`, Apple `NWPathMonitor`, Desktop `NetworkInterface` polling).
- [UI Style Guide](ui-style-guide.md) — token tables, `TetherTheme` rule, Tabler Icons usage, motion specs, accessibility checklist.
- [Testing](testing.md) — test structure by source set, style (`runTest` / virtual time), Apple-target specifics.
- [Logging](logging.md) — KydraLog as the single KMP façade; `Tether.<Subsystem>` naming, levels, per-platform DEBUG gating, test silence, sensitive-data policy.
- [Persistence](persistence.md) — key-value store contract and DataStore backend.
- [Device identity](device-identity.md) — per-install keypair, the root of trust for pairing and TLS.
- [Security analysis](../security/README.md) — threat model, attack tree, and pentest suite; the per-component STRIDE attack surface and the SAS pairing correctness conditions.
- [Platform concerns](platform-concerns.md) — recurring stumbling points for PRs touching platform source sets; checklist used by `review-platform`.
- [Glossary discipline](glossary-discipline.md) — `docs/glossary.md` as the single dictionary (product / technical); `review-glossary` as a subagent that catches drift and undocumented terms in a PR diff.
- [Long-lived artifacts](long-lived-artifacts.md) — writing discipline for prose that outlives the task. Applies to `CLAUDE.md`, `docs/`, `.claude/`, KDoc, comments, error messages.

## Writing style for these guides

These documents codify principles, not the current shape of the codebase — they should age slowly.

- **Code examples on abstract types**, not on real project classes. Examples pinned to concrete names rot at every rename.
- **Don't restate what the code already shows** (hierarchies, signatures, source set layout). The code is the one source of truth that drifts last; link to it instead of copying.
- **Lead with the rule.** Rationale and examples follow.
- **No engineering artifact is the default outcome** of a task. A new or extended `docs/engineering/<name>.md` is justified only when at least one rule from the task is engineering-layer (mechanism / library / lifecycle / cross-platform invariant) and not already captured by a sibling. Tasks that apply an existing pattern, fix a bug at code level, or live entirely inside the product / interaction / code layer do not produce an engineering artifact. Length is not the test; layer fit is. Rule-promotion to [`architecture-principles.md`](architecture-principles.md) is retro-driven — a separate PR after the rule bites a second time — not in-flight during the task that first hit it.

A starter skeleton with common sections lives in [`_template.md`](_template.md). Copy it as a base for new subsystem docs.

## Architecture Decision Records

ADRs in [`adr/`](adr/) capture the *why* behind one-time architectural choices. Living docs above; ADRs are append-only history. See [`adr/README.md`](adr/README.md) for conventions.

- [Compose Multiplatform UI](adr/adr-compose-multiplatform-ui.md) — chose Compose-MP as the single rendering layer on every platform over per-platform native UI (SwiftUI/UIKit, native Android) or a Compose+SwiftUI hybrid.
- [Presentation & Navigation](adr/adr-presentation-and-navigation.md) — chose Decompose over Voyager / custom thin layer / Compose Navigation MP / Premo.
- [macOS target — Desktop JVM (reversed from Native)](adr/adr-macos-native-vs-jvm.md) — originally chose `macosArm64` Kotlin/Native; reversed 2026-05-25 to Desktop JVM because Compose-MP macOS-native UI is unsupported.
- [Visual Identity](adr/adr-visual-identity.md) — palette, typography, iconography; dropped Material 3 in favour of custom `TetherTheme`.
- [Network stack](adr/adr-network-stack.md) — chose Ktor CIO across all targets tactically; engine swaps under TLS forced upstream. Living-doc side — [`file-transfer-wire.md`](file-transfer-wire.md).
- [Apple FileServer engine](adr/adr-apple-fileserver-engine.md) — chose Ktor CIO Native over hand-rolled HTTP for the Apple-side `FileServer.actual`.
- [Channel encryption](adr/adr-channel-encryption.md) — chose TLS-with-paired-key-pinning + SecureTransport on Apple. Includes 2026-05-16 Amendment with implementation-plan corrections.
- [Hotspot-first Discovery](adr/adr-hotspot-discovery.md) — chose layered mDNS + `/hello` rendezvous + HTTP-subnet-scan + UDP-broadcast over raw-multicast-as-primary or Wi-Fi Direct / NAN. Motivated primarily by phone-hotspot transfer.
- [Logging — KydraLog](adr/adr-logging-kydra.md) — chose KydraLog as the single KMP logging facade; SLF4J handled via `slf4j-simple` on JVM; per-platform DEBUG gates.
- [Screenshot testing](adr/adr-screenshot-testing.md) — chose Roborazzi + ComposablePreviewScanner on the Android target via Robolectric for headless `@Preview`-to-PNG rendering in the agent loop.
- [Key-value persistence — DataStore](adr/adr-persistence-key-value.md) — chose `androidx.datastore-preferences-core` direct over wrapper libraries and per-store actuals.
- [Sheet and modal primitives](adr/adr-sheet-modal-primitives.md) — chose Compose Unstyled over ad-hoc Compose Foundation overlay, Material 3, and Android-only sheet libraries.
- [SAS pairing protocol](adr/adr-sas-pairing-protocol.md) — chose to authenticate the static identity keys (SAS over both keys plus a per-handshake nonce) over app-level ephemeral ECDH in pairing; session confidentiality comes from the pinned TLS channel.

