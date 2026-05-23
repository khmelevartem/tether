# Glossary

Single source of vocabulary for Tether. New entries land when [`review-glossary`](../.claude/agents/review-glossary.md) flags an undocumented term in a PR diff and the writing agent adds the entry as part of addressing the finding. Mechanism: [`grilling-and-glossary.md`](engineering/grilling-and-glossary.md).

Sections are in fixed order. Each entry is one line: bold term, 1–2 sentence definition, optional `_Avoid:_` list of near-synonyms, optional `(see <link>)` to the living doc that owns the deeper rule.

## Product

User-facing concepts. The vocabulary [`spec-writer`](../.claude/agents/spec-writer.md) and [`ux-expert`](../.claude/agents/ux-expert.md) use.

- **Transfer** — one user-initiated send of one or more files from one device to another.
- **Pairing** — the one-time consent step where two devices agree to recognise each other for future transfers without re-prompting. _Avoid:_ handshake, trust establishment.
- **Trusted device** — a device that completed pairing and may initiate or accept transfers without re-confirmation. _Avoid:_ known device, friend, contact.
- **Device list** — the user-visible list of trusted devices on a given device.
- **Device name** — the user-chosen display label for a device, shown to peers (see [device-name-bootstrapping](product/features/device-name-bootstrapping/)).
- **Hotspot transfer** — a transfer that runs over an ad-hoc Wi-Fi hotspot hosted by one of the devices, used when the peers have no shared Wi-Fi network (see [hotspot-transfer](product/features/hotspot-transfer/)).

## Technical

Engineering concepts. The vocabulary [`architect`](../.claude/agents/architect.md), `coder`, and the review agents use.

- **Discovery** — the layer that announces a device's presence and finds peers on the local network (see [discovery.md](engineering/discovery.md)). _Avoid:_ announce (verb only).
- **Rendezvous** — a post-discovery `/hello` mechanism that resolves asymmetric discovery (one side saw the other but not vice versa), primarily for the hotspot scenario. Distinct from Discovery itself.
- **Peer** — a device visible through discovery, regardless of pairing status. _Avoid:_ node, neighbour; device when pairing status matters (use **Trusted device** then).
- **FileServer** — the per-device HTTP server that accepts incoming transfers. _Avoid:_ receiver, listener.
- **FileClient** — the per-device HTTP client that initiates outgoing transfers. _Avoid:_ sender, uploader.
- **Source set** — a Kotlin Multiplatform compilation source set; platform-to-target mapping and hierarchy live in [architecture-principles.md](engineering/architecture-principles.md). _Avoid:_ saying «JVM» when the audience is end-users — say *Desktop* instead.
- **Composition root** — the platform entry point that constructs the DI container; by extension, the `AppContainer` instance it constructs. See [dependency-injection.md](engineering/dependency-injection.md).
- **Container** — the DI container that holds singletons for one process lifetime; the construct that lives at the composition root.
- **Session** — the post-rendezvous logical connection between two peers, lasting from `/hello` until either side closes it.
- **Drift** — a usage of a term that contradicts its glossary definition, or absence of a glossary entry for a term that recurs across long-lived artifacts.
- **Living doc** — a `docs/engineering/<name>.md` artifact that captures the present-tense rules for a subsystem; distinct from an ADR (one-time decision) and a knowledge entry (solved-problem note).

