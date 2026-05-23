# Glossary

Single source of vocabulary for Tether. Owned collectively, written through the [`grill-with-docs`](../.claude/skills/grill-with-docs/SKILL.md) skill; never edited outside a grill pass. Mechanism, mount points, and the «one term enters when it appears in two or more long-lived artifacts» rule: [`grilling-and-glossary.md`](engineering/grilling-and-glossary.md).

Sections are in fixed order. Each entry is one line: bold term, 1–2 sentence definition, optional `_Avoid:_` list of near-synonyms, optional `(see <link>)` to the living doc that owns the deeper rule.

## Product

User-facing concepts. The vocabulary [`spec-writer`](../.claude/agents/spec-writer.md) and [`ux-expert`](../.claude/agents/ux-expert.md) use.

- **Transfer** — one user-initiated send of one or more files from one device to another.
- **Pairing** — the one-time consent step where two devices agree to recognise each other for future transfers without re-prompting. _Avoid:_ handshake, trust establishment.
- **Trusted device** — a device that completed pairing and may initiate or accept transfers without re-confirmation. _Avoid:_ known device, friend, contact.
- **Device list** — the user-visible list of trusted devices on a given device.
- **Device name** — the user-chosen display label for a device, shown to peers (see [device-name-bootstrapping](product/features/device-name-bootstrapping/)).

## Technical

Engineering concepts. The vocabulary [`architect`](../.claude/agents/architect.md), `coder`, and the review agents use.

- **Discovery** — the layer that announces a device's presence and finds peers on the local network (see [discovery.md](engineering/discovery.md)). _Avoid:_ announce (verb only).
- **Rendezvous** — the post-discovery `/hello` exchange that establishes a session between two peers that have already found each other through Discovery. Distinct from Discovery itself (see [discovery.md](engineering/discovery.md) §Layer 2).
- **grill-with-docs** — the sub-agent at [`.claude/skills/grill-with-docs/`](../.claude/skills/grill-with-docs/SKILL.md) that interrogates a draft against this glossary, flags drift, and writes new terms in the same pass. The only writer to this file (see [grilling-and-glossary.md](engineering/grilling-and-glossary.md)).
- **Peer** — a device visible through discovery, regardless of pairing status. _Avoid:_ node, neighbour.
- **FileServer** — the per-device HTTP server that accepts incoming transfers. _Avoid:_ receiver, listener.
- **FileClient** — the per-device HTTP client that initiates outgoing transfers. _Avoid:_ sender, uploader.
- **Source set** — a Kotlin Multiplatform compilation source set (`commonMain`, `jvmMain`, `androidMain`, `appleMain`, `desktopMain`, `iosMain`, `macosMain`). See [architecture-principles.md](engineering/architecture-principles.md) for the hierarchy.
- **Composition root** — the single place per platform target where the DI container is built. See [dependency-injection.md](engineering/dependency-injection.md).
- **Container** — the DI container that holds singletons for one process lifetime.

## Platform mapping

Canonical names for platforms and their Kotlin Multiplatform targets. Use the canonical name in product docs, issues, commits, and user-facing copy; use the target name in build files, source-set paths, and code.

| Canonical name | KMP target(s) | Use canonical name when | Use target name when |
|---|---|---|---|
| Android | `androidTarget` | speaking about user-visible behaviour or product scope | referring to Kotlin source-set placement or Gradle tasks |
| Desktop | `jvm("desktop")` | speaking about user-visible behaviour; covers Windows / Linux / macOS desktop | referring to source-set placement or Gradle tasks |
| iOS | `iosArm64`, `iosSimulatorArm64` | speaking about user-visible behaviour | referring to source-set placement or Gradle tasks |
| macOS | `macosArm64` | speaking about user-visible behaviour | referring to source-set placement or Gradle tasks |

_Avoid:_ saying «JVM» when the audience is end-users; mixing canonical and target names within one sentence.
