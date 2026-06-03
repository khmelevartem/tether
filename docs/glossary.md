# Glossary

Single source of vocabulary for Tether. New entries land when [`review-glossary`](../.claude/agents/review-glossary.md) flags an undocumented term in a PR diff and the writing agent adds the entry as part of addressing the finding. Mechanism: [`glossary-discipline.md`](engineering/glossary-discipline.md).

Sections are in fixed order. Each entry is one line: bold term, 1–2 sentence definition, optional `_Avoid:_` list of near-synonyms, optional `(see <link>)` to the living doc that owns the deeper rule.

## Product

User-facing concepts. The vocabulary [`spec-writer`](../.claude/agents/spec-writer.md) and [`ux-expert`](../.claude/agents/ux-expert.md) use.

- **Transfer** — one user-initiated send of one or more files from one device to another.
- **Pairing** — the one-time consent step where two devices agree to recognise each other for future transfers without re-prompting. _Avoid:_ handshake, trust establishment.
- **Trusted device** — a device that completed pairing and may initiate or accept transfers without re-confirmation. _Avoid:_ known device, friend, contact.
- **Device list** — the user-visible list of trusted devices on a given device.
- **Device name** — the user-chosen display label for a device, shown to peers (see [device-name-bootstrapping](product/features/device-name-bootstrapping/)).
- **Hotspot transfer** — a transfer that runs over an ad-hoc Wi-Fi hotspot hosted by one of the devices, used when the peers have no shared Wi-Fi network (see [hotspot-transfer](product/features/hotspot-transfer/)).
- **Peer identity** — the stable visual signal (currently a warm copper hue) bound to a specific peer's device identity, so the same peer is recognisable across screens. _Avoid:_ peer color, device color, identity hue. (see [ui-style-guide.md](engineering/ui-style-guide.md))
- **Auto-send** — a per-peer opt-in that, when enabled and the peer is the sole online paired peer, sends incoming pending files immediately without a device-list tap. Toggle lives in the expanded PeerCard (see [file-transfer](product/features/file-transfer/)). _Avoid:_ quick send, instant send.
- **Pending files** — files staged for a transfer that has not yet started, typically arriving via share-sheet or drag-drop while the user picks the target peer. Surfaced by the pending-outbound banner on the device list (see [file-transfer](product/features/file-transfer/)). _Avoid:_ queued files, staged files, awaiting files.
- **Share-sheet** — the platform-provided UI surface a user invokes from another app to send selected files into Tether (Android share-sheet, iOS Share extension). On Tether's side the selection lands in pending files. _Avoid:_ share menu, share dialog.

## Technical

Engineering concepts. The vocabulary [`architect`](../.claude/agents/architect.md), `coder`, and the review agents use.

- **Discovery** — the layer that announces a device's presence and finds peers on the local network. _Avoid:_ announce (verb only). (see [discovery.md](engineering/discovery.md))
- **Rendezvous** — a post-discovery `/hello` mechanism that resolves asymmetric discovery (one side saw the other but not vice versa), primarily for the hotspot scenario. Distinct from Discovery itself.
- **Peer** — a device visible through discovery, regardless of pairing status. _Avoid:_ node, neighbour; device when pairing status matters (use **Trusted device** then).
- **Outbound** — a transfer this device initiates and sends to a peer. _Avoid:_ upload, send (verb only).
- **Inbound** — a transfer this device receives from a peer. _Avoid:_ download, receive (verb only).
- **FileServer** — the per-device HTTP server that accepts incoming transfers. _Avoid:_ receiver, listener.
- **FileClient** — the per-device HTTP client that initiates outgoing transfers. _Avoid:_ sender, uploader.
- **Canonical name** — the conflict-resolution name form (`… (2)`, `… (3)`, …) that mDNS infrastructure assigns a published service when multiple services share a base name on the same network. Distinct from the raw configured device name. _Avoid:_ suffixed name, disambiguated name. (see [discovery.md](engineering/discovery.md))
- **Fingerprint** — the stable per-device identity carried in discovery announces and `/hello` payloads. Used by every node to recognise its own announces and suppress them, and (once pairing lands) as the trust key two devices agree on at pairing time. _Avoid:_ device id when the cross-network identity property matters; install token. (see [discovery.md §Identity and self-suppression](engineering/discovery.md#identity-and-self-suppression))
- **Source set** — a Kotlin Multiplatform compilation source set; platform-to-target mapping and hierarchy live in [architecture-principles.md](engineering/architecture-principles.md). _Avoid:_ saying «JVM» when the audience is end-users — say *Desktop* instead.
- **UI layer** — the Compose composable layer; renders a state object and fires events upward. Holds no business logic. (see [layering.md](engineering/layering.md))
- **Presentation layer** — the Decompose Components; maps domain state to its screen representation and relays user actions inward. _Avoid:_ view layer, ViewModel layer. (see [layering.md](engineering/layering.md))
- **Domain layer** — pure Kotlin owning the invariants, rules, and state machines; no platform or framework dependencies. The most stable ring. _Avoid:_ protocol layer. (see [layering.md](engineering/layering.md))
- **Data layer** — repository implementations, engines, transport, persistence, discovery, and platform adapters; the most volatile ring, depending inward on Domain by implementing its interfaces. _Avoid:_ infrastructure layer, network layer when the whole ring is meant. (see [layering.md](engineering/layering.md))
- **View state** — the UI-shaped projection a Presentation component exposes for a screen: domain state mapped to display fields plus view-only fields that vanish under a different UI. Distinct from domain state. _Avoid:_ UI model, screen model. (see [layering.md](engineering/layering.md))
- **Composition root** — the platform entry point that constructs the DI container; by extension, the `AppContainer` instance it constructs. (see [dependency-injection.md](engineering/dependency-injection.md))
- **Container** — the DI container that holds singletons for one process lifetime; the construct that lives at the composition root.
- **Session** — the post-rendezvous logical connection between two peers, lasting from `/hello` until either side closes it.
- **Reconnect window** — the bounded time after a connection drop during which an active transfer waits for the peer to reappear and resumes from where it left off; expiry yields a definitive `NetworkLost` failure.
- **Drift** — a usage of a term that contradicts its glossary definition, or absence of a glossary entry for a term that recurs across long-lived artifacts.
- **Living doc** — a `docs/engineering/<name>.md` artifact that captures the present-tense rules for a subsystem; distinct from an ADR (one-time decision) and a knowledge entry (solved-problem note).
- **Long-lived artifact** — any prose surface that outlives the task that birthed it: `CLAUDE.md`, `docs/`, `.claude/skills/**`, `.claude/agents/**`, `.claude/commands/**`, KDoc, inline comments, error messages. Governed by the discipline in [long-lived-artifacts.md](engineering/long-lived-artifacts.md). _Avoid:_ doc, documentation when the lifetime contrast with task-scoped prose (commit message, PR description) is what matters.
- **Bonjour** — Apple's implementation of mDNS/DNS-SD service discovery, used by Tether's macOS Desktop publisher via the system `mDNSResponder` daemon and JNA bindings. _Avoid:_ saying "Bonjour" when meaning any mDNS implementation — say "mDNS" instead.
- **Token** — a named value in `TetherTheme` (color, typography, spacing, shape) read via `TetherTheme.<scale>` from the composition rather than hardcoded. _Avoid:_ constant, raw value, magic number, theme value. (see [ui-style-guide.md](engineering/ui-style-guide.md))
- **UploadStorage** — the per-platform sink the FileServer talks to for resolving destinations, streaming bytes, enforcing the canonical-realisation check, and aborting on failure. _Avoid:_ storage backend, file sink, persistence layer. (see [file-transfer-wire.md](engineering/file-transfer-wire.md))
- **Relative POSIX path** — the file's path inside a transfer, using `/` separators, no leading slash, no `..` segments; for a flat send it is the leaf name. _Avoid:_ filename when nesting matters, relative URL.
- **Path sanitization** — the two-layer boundary that maps an untrusted `name` parameter to a safe on-disk destination: Layer 1 is the lexical sanitizer in the route handler, Layer 2 is the canonical-realisation check in UploadStorage. (see [file-transfer-wire.md](engineering/file-transfer-wire.md))
- **Abort** — UploadStorage's failure-path cleanup: deletes the partial destination file and removes only the empty parent directories this upload created, leaving directories shared with other in-flight uploads alone. _Avoid:_ cancel, rollback.
- **Downloads root** — the per-device root directory into which incoming files are landed; the security boundary every received file must stay inside. _Avoid:_ destination root, downloads folder when the security property matters.
- **Keychain** — the platform-managed secure store for cryptographic keys; on Apple platforms, accessed via Security framework APIs. _Avoid:_ keystore (Android term), secure enclave (a sub-component of the Keychain stack, not the Keychain itself).

